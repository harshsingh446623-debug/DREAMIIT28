const express = require("express");
const os = require("os");
const { spawn } = require("child_process");
const { Pool } = require("pg");
const {
  DEFAULT_PROFILE_ID,
  QUALITY_PROFILES,
  getQualityProfile,
  validateDurationMinutes,
  computeBackoffMs,
  computeResumePositionSec,
  maskRtmpsUrl,
  buildFfmpegArgs,
  validateMp4Upload,
  encryptSecret,
  decryptSecret,
  isBearerAuthorized,
  parseFfmpegProgressChunk,
} = require("./engine");

const app = express();
app.use(express.json({ limit: "2mb" }));

const PORT = process.env.PORT || 8080;
const WORKER_API_TOKEN = process.env.WORKER_API_TOKEN || "";
const ALLOW_UNAUTHENTICATED_WORKER = process.env.ALLOW_UNAUTHENTICATED_WORKER === "true";
const TOKEN_ENCRYPTION_KEY = process.env.TOKEN_ENCRYPTION_KEY || WORKER_API_TOKEN || "";
const pool = process.env.DATABASE_URL ? new Pool({ connectionString: process.env.DATABASE_URL }) : null;

// Active FFmpeg child processes keyed by scheduleId (duplicate-stream protection)
const activeStreams = new Map();
const recentLogs = [];

function recordWorkerLog(scheduleId, level, eventType, message, metadata = {}) {
  const safeMessage = String(message || "").replace(/rtmps?:\/\/[^\s/]+\/[^\s/]+\/[^\s]+/gi, (m) => maskRtmpsUrl(m));
  recentLogs.unshift({
    scheduleId: scheduleId || null,
    level,
    eventType,
    message: safeMessage,
    timestamp: new Date().toISOString(),
  });
  if (recentLogs.length > 50) recentLogs.length = 50;

  if (pool) {
    pool
      .query(
        `INSERT INTO streaming_logs (schedule_id, level, event_type, message, metadata_json)
         VALUES ($1, $2, $3, $4, $5::jsonb)`,
        [scheduleId || null, level, eventType, safeMessage, JSON.stringify(metadata)]
      )
      .catch(() => {});
  }
}

function requireWorkerAuth(req, res, next) {
  const ok = isBearerAuthorized(req.headers.authorization, WORKER_API_TOKEN, ALLOW_UNAUTHENTICATED_WORKER);
  if (!ok) {
    return res.status(401).json({
      error: "Unauthorized worker request. Configure WORKER_API_TOKEN and pass Authorization: Bearer <token>.",
    });
  }
  return next();
}

function clearStreamTimers(entry) {
  if (!entry) return;
  if (entry.durationTimer) {
    clearTimeout(entry.durationTimer);
    entry.durationTimer = null;
  }
  if (entry.retryTimer) {
    clearTimeout(entry.retryTimer);
    entry.retryTimer = null;
  }
}

function buildPublicStreamStatus(scheduleId, entry) {
  const now = Date.now();
  const runningDeltaSec = !entry.paused && entry.segmentStartedAtMs
    ? Math.floor((now - entry.segmentStartedAtMs) / 1000)
    : 0;
  const elapsedSec = entry.accumulatedPlaySec + runningDeltaSec;
  const maxSec = (entry.maxDurationMinutes || 60) * 60;
  const remainingSec = Math.max(0, maxSec - elapsedSec);
  return {
    scheduleId,
    active: true,
    paused: Boolean(entry.paused),
    profileId: entry.profileId,
    maxDurationMinutes: entry.maxDurationMinutes,
    repeatPlayback: Boolean(entry.repeatPlayback),
    elapsedSec,
    remainingSec,
    lastSafePositionSec: entry.lastSafePositionSec,
    currentBitrateKbps: entry.currentBitrateKbps,
    fps: entry.fps,
    droppedFrames: entry.droppedFrames,
    retryAttempt: entry.retryAttempt,
    maxRetries: entry.maxRetries,
    recoveryState: entry.recoveryState,
    maskedIngestUrl: entry.maskedIngestUrl,
  };
}

function spawnManagedFfmpeg(scheduleId, entry, resumeFromSec) {
  clearStreamTimers(entry);
  const maxSec = (entry.maxDurationMinutes || 60) * 60;
  const remainingSec = Math.max(1, maxSec - entry.accumulatedPlaySec);
  const rawIngest = entry.encryptedRtmpsUrl && TOKEN_ENCRYPTION_KEY
    ? decryptSecret(entry.encryptedRtmpsUrl, TOKEN_ENCRYPTION_KEY)
    : entry.rawRtmpsUrl;

  const args = buildFfmpegArgs({
    inputPath: entry.inputUrl,
    rtmpsIngestUrl: rawIngest,
    profileId: entry.profileId,
    resumeFromSec,
    repeatPlayback: entry.repeatPlayback,
    maxDurationSec: remainingSec,
  });

  const child = spawn("ffmpeg", args, { stdio: ["ignore", "pipe", "pipe"] });
  entry.child = child;
  entry.paused = false;
  entry.manualStop = false;
  entry.segmentStartedAtMs = Date.now();
  entry.recoveryState = entry.retryAttempt > 0 ? "RECOVERED" : "HEALTHY";

  // Graceful auto-stop at configured 1-3h ceiling
  entry.durationTimer = setTimeout(() => {
    entry.manualStop = true;
    entry.recoveryState = "COMPLETED";
    if (entry.child) {
      entry.child.kill("SIGTERM");
    }
    activeStreams.delete(scheduleId);
    recordWorkerLog(scheduleId, "INFO", "DURATION_REACHED", `Auto-stopped after reaching ${entry.maxDurationMinutes}m duration limit.`);
  }, remainingSec * 1000);

  const onProgress = (chunk) => {
    const parsed = parseFfmpegProgressChunk(chunk);
    if (typeof parsed.outTimeSec === "number") {
      entry.lastSafePositionSec = computeResumePositionSec(
        resumeFromSec + parsed.outTimeSec,
        entry.videoDurationSec,
        entry.repeatPlayback
      );
    }
    if (typeof parsed.bitrateKbps === "number") entry.currentBitrateKbps = parsed.bitrateKbps;
    if (typeof parsed.fps === "number") entry.fps = parsed.fps;
    if (typeof parsed.droppedFrames === "number") entry.droppedFrames = parsed.droppedFrames;
  };

  child.stdout.on("data", onProgress);
  child.stderr.on("data", onProgress);

  child.on("exit", (code) => {
    if (entry.durationTimer) {
      clearTimeout(entry.durationTimer);
      entry.durationTimer = null;
    }
    if (entry.segmentStartedAtMs) {
      entry.accumulatedPlaySec += Math.floor((Date.now() - entry.segmentStartedAtMs) / 1000);
      entry.segmentStartedAtMs = null;
    }

    if (entry.manualStop || entry.paused) {
      return;
    }

    if (code === 0) {
      activeStreams.delete(scheduleId);
      recordWorkerLog(scheduleId, "INFO", "STREAM_COMPLETE", "FFmpeg finished playback cleanly.");
      return;
    }

    recordWorkerLog(scheduleId, "ERROR", "FFMPEG_EXIT", `FFmpeg exited unexpectedly with code ${code}`);
    if (entry.retryAttempt < entry.maxRetries) {
      const backoffMs = computeBackoffMs(entry.retryAttempt);
      entry.retryAttempt += 1;
      entry.recoveryState = "RECOVERING";
      const resumeAt = computeResumePositionSec(
        entry.lastSafePositionSec,
        entry.videoDurationSec,
        entry.repeatPlayback
      );
      recordWorkerLog(
        scheduleId,
        "WARN",
        "AUTO_RECOVERY_SCHEDULED",
        `Attempt ${entry.retryAttempt}/${entry.maxRetries} scheduled in ${Math.round(backoffMs / 1000)}s from ${resumeAt}s`
      );
      entry.retryTimer = setTimeout(() => {
        if (!activeStreams.has(scheduleId) || entry.manualStop || entry.paused) return;
        spawnManagedFfmpeg(scheduleId, entry, resumeAt);
      }, backoffMs);
    } else {
      entry.recoveryState = "FAILED";
      activeStreams.delete(scheduleId);
      recordWorkerLog(scheduleId, "ERROR", "RECOVERY_EXHAUSTED", "Exceeded maximum recovery attempts.");
    }
  });
}

app.get("/api/health", (req, res) => {
  const load = os.loadavg()[0] || 0;
  const totalMem = os.totalmem();
  const freeMem = os.freemem();
  const usedMb = Math.round((totalMem - freeMem) / (1024 * 1024));
  let totalBitrateKbps = 0;
  let totalDropped = 0;
  for (const entry of activeStreams.values()) {
    totalBitrateKbps += entry.currentBitrateKbps || getQualityProfile(entry.profileId).videoBitrateKbps;
    totalDropped += entry.droppedFrames || 0;
  }
  res.json({
    status: "ONLINE",
    cpuPercent: Number(Math.min(100, load * 25).toFixed(1)),
    memoryMb: usedMb,
    networkMbps: Number((totalBitrateKbps / 1000).toFixed(2)),
    droppedFrames: totalDropped,
    activeStreams: activeStreams.size,
    activeStreamIds: Array.from(activeStreams.keys()),
    uptimeSec: Math.round(process.uptime()),
    ffmpegAvailable: true,
    authEnforced: Boolean(WORKER_API_TOKEN),
    supportedProfiles: Object.keys(QUALITY_PROFILES),
    recentErrors: recentLogs.filter((l) => l.level === "ERROR").slice(0, 10),
    timestamp: new Date().toISOString(),
  });
});

app.get("/api/billing", async (req, res) => {
  const billingExportUrl = process.env.GCP_BILLING_JSON_URL;
  const configuredTotal = process.env.CLOUD_MONTHLY_EXPENSE_USD;
  if (billingExportUrl) {
    try {
      const upstream = await fetch(billingExportUrl);
      if (!upstream.ok) {
        return res.status(502).json({
          connected: false,
          message: `Billing export endpoint returned HTTP ${upstream.status}`,
        });
      }
      const data = await upstream.json();
      return res.json({ connected: true, source: "Connected Cloud Billing Export", ...data });
    } catch (err) {
      return res.status(502).json({
        connected: false,
        message: err.message || "Failed to fetch connected billing export",
      });
    }
  }
  if (configuredTotal !== undefined && configuredTotal !== "") {
    const amount = Number(configuredTotal);
    return res.json({
      connected: true,
      currency: "USD",
      totalMonthlyUsd: Number.isFinite(amount) ? amount : 0,
      billingPeriod: new Date().toISOString().slice(0, 7),
      source: "Configured Cloud Billing Feed",
      lineItems: [{ label: "Cloud Compute & FFmpeg Worker", amountUsd: Number.isFinite(amount) ? amount : 0 }],
    });
  }
  return res.json({
    connected: false,
    message: "Cloud billing export is not configured on worker (set GCP_BILLING_JSON_URL).",
  });
});

app.post("/api/videos/validate-upload", requireWorkerAuth, (req, res) => {
  const { filename, mimeType, fileSizeBytes, durationSec } = req.body || {};
  const result = validateMp4Upload({ filename, mimeType, fileSizeBytes, durationSec });
  if (!result.valid) {
    return res.status(400).json({ valid: false, error: result.error });
  }
  return res.json({ valid: true });
});

app.post("/api/schedules/dispatch", requireWorkerAuth, async (req, res) => {
  const {
    scheduleId,
    userId,
    videoId,
    liveTitle,
    scheduledStartIso,
    targetProfile,
    rtmpsIngestUrl,
    autoEndOnFinish,
    maxDurationMinutes,
    repeatPlayback,
  } = req.body || {};
  if (!scheduleId || !liveTitle) {
    return res.status(400).json({ error: "scheduleId and liveTitle are required" });
  }
  const durCheck = validateDurationMinutes(maxDurationMinutes);
  if (!durCheck.valid) {
    return res.status(400).json({ error: durCheck.error });
  }
  const maskedUrl = maskRtmpsUrl(rtmpsIngestUrl || "");
  if (pool) {
    await pool.query(
      `INSERT INTO live_schedules (id, user_id, video_id, live_title, scheduled_start_iso, target_profile, rtmps_ingest_url, auto_end_on_finish, max_duration_minutes, repeat_playback, status)
       VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, 'SCHEDULED')
       ON CONFLICT (id) DO UPDATE SET scheduled_start_iso = EXCLUDED.scheduled_start_iso, target_profile = EXCLUDED.target_profile, max_duration_minutes = EXCLUDED.max_duration_minutes, repeat_playback = EXCLUDED.repeat_playback, status = 'SCHEDULED'`,
      [
        scheduleId,
        userId || "owner",
        videoId || "",
        liveTitle,
        scheduledStartIso,
        targetProfile || DEFAULT_PROFILE_ID,
        maskedUrl,
        Boolean(autoEndOnFinish),
        durCheck.minutes,
        Boolean(repeatPlayback),
      ]
    );
  }
  recordWorkerLog(scheduleId, "INFO", "SCHEDULE_DISPATCHED", `Queued schedule (${durCheck.minutes}m, ${targetProfile || DEFAULT_PROFILE_ID})`);
  res.json({ queued: true, scheduleId, maxDurationMinutes: durCheck.minutes });
});

app.post("/api/streams/:scheduleId/start", requireWorkerAuth, (req, res) => {
  const { scheduleId } = req.params;
  const {
    inputUrl,
    rtmpsIngestUrl,
    profile,
    maxDurationMinutes,
    repeatPlayback,
    resumeFromSec,
    videoDurationSec,
  } = req.body || {};

  if (activeStreams.has(scheduleId)) {
    return res.status(409).json({ error: "Duplicate stream prevented: schedule is already active." });
  }
  if (!rtmpsIngestUrl || !inputUrl) {
    return res.status(400).json({ error: "rtmpsIngestUrl and inputUrl are required to spawn FFmpeg." });
  }
  const durCheck = validateDurationMinutes(maxDurationMinutes);
  if (!durCheck.valid) {
    return res.status(400).json({ error: durCheck.error });
  }

  const profileObj = getQualityProfile(profile || DEFAULT_PROFILE_ID);
  const maskedIngestUrl = maskRtmpsUrl(rtmpsIngestUrl);
  const entry = {
    scheduleId,
    inputUrl,
    profileId: profileObj.id,
    maxDurationMinutes: durCheck.minutes,
    repeatPlayback: Boolean(repeatPlayback),
    videoDurationSec: Math.max(0, Math.floor(Number(videoDurationSec) || 0)),
    paused: false,
    manualStop: false,
    accumulatedPlaySec: 0,
    segmentStartedAtMs: null,
    lastSafePositionSec: Math.max(0, Math.floor(Number(resumeFromSec) || 0)),
    currentBitrateKbps: profileObj.videoBitrateKbps,
    fps: profileObj.fps,
    droppedFrames: 0,
    retryAttempt: 0,
    maxRetries: 5,
    recoveryState: "HEALTHY",
    maskedIngestUrl,
    encryptedRtmpsUrl: TOKEN_ENCRYPTION_KEY ? encryptSecret(rtmpsIngestUrl, TOKEN_ENCRYPTION_KEY) : null,
    rawRtmpsUrl: TOKEN_ENCRYPTION_KEY ? null : rtmpsIngestUrl,
    child: null,
    durationTimer: null,
    retryTimer: null,
  };

  activeStreams.set(scheduleId, entry);
  try {
    spawnManagedFfmpeg(scheduleId, entry, entry.lastSafePositionSec);
  } catch (err) {
    activeStreams.delete(scheduleId);
    return res.status(400).json({ error: err.message });
  }

  recordWorkerLog(scheduleId, "INFO", "STREAM_STARTED", `Started ${profileObj.id} CBR RTMPS (${durCheck.minutes}m max) -> ${maskedIngestUrl}`);
  return res.json({
    started: true,
    scheduleId,
    profile: profileObj.id,
    maxDurationMinutes: durCheck.minutes,
    repeatPlayback: entry.repeatPlayback,
    maskedIngestUrl,
  });
});

app.get("/api/streams/:scheduleId/status", requireWorkerAuth, (req, res) => {
  const entry = activeStreams.get(req.params.scheduleId);
  if (!entry) {
    return res.json({ scheduleId: req.params.scheduleId, active: false, recoveryState: "IDLE" });
  }
  return res.json(buildPublicStreamStatus(req.params.scheduleId, entry));
});

app.post("/api/streams/:scheduleId/pause", requireWorkerAuth, (req, res) => {
  const entry = activeStreams.get(req.params.scheduleId);
  if (!entry) {
    return res.status(404).json({ error: "No active stream found to pause." });
  }
  entry.paused = true;
  entry.recoveryState = "PAUSED";
  clearStreamTimers(entry);
  if (entry.segmentStartedAtMs) {
    entry.accumulatedPlaySec += Math.floor((Date.now() - entry.segmentStartedAtMs) / 1000);
    entry.segmentStartedAtMs = null;
  }
  if (entry.child) {
    entry.child.kill("SIGTERM");
    entry.child = null;
  }
  recordWorkerLog(req.params.scheduleId, "INFO", "STREAM_PAUSED", `Paused cleanly at ${entry.lastSafePositionSec}s`);
  return res.json({ paused: true, ...buildPublicStreamStatus(req.params.scheduleId, entry) });
});

app.post("/api/streams/:scheduleId/resume", requireWorkerAuth, (req, res) => {
  const entry = activeStreams.get(req.params.scheduleId);
  if (!entry) {
    return res.status(404).json({ error: "No paused stream entry found to resume." });
  }
  if (!entry.paused && entry.child) {
    return res.json({ resumed: true, ...buildPublicStreamStatus(req.params.scheduleId, entry) });
  }
  const resumeAt = computeResumePositionSec(entry.lastSafePositionSec, entry.videoDurationSec, entry.repeatPlayback);
  spawnManagedFfmpeg(req.params.scheduleId, entry, resumeAt);
  recordWorkerLog(req.params.scheduleId, "INFO", "STREAM_RESUMED", `Resumed playback from ${resumeAt}s`);
  return res.json({ resumed: true, ...buildPublicStreamStatus(req.params.scheduleId, entry) });
});

app.post("/api/streams/:scheduleId/stop", requireWorkerAuth, (req, res) => {
  const entry = activeStreams.get(req.params.scheduleId);
  if (entry) {
    entry.manualStop = true;
    clearStreamTimers(entry);
    if (entry.child) entry.child.kill("SIGTERM");
    activeStreams.delete(req.params.scheduleId);
    recordWorkerLog(req.params.scheduleId, "INFO", "STREAM_STOPPED", "Broadcast stopped by operator.");
  }
  res.json({ stopped: true, scheduleId: req.params.scheduleId });
});

app.post("/api/streams/:scheduleId/emergency-stop", requireWorkerAuth, (req, res) => {
  const entry = activeStreams.get(req.params.scheduleId);
  if (entry) {
    entry.manualStop = true;
    clearStreamTimers(entry);
    if (entry.child) entry.child.kill("SIGKILL");
    activeStreams.delete(req.params.scheduleId);
    recordWorkerLog(req.params.scheduleId, "WARN", "EMERGENCY_STOP", "Broadcast killed immediately via Emergency Stop.");
  }
  res.json({ emergencyStopped: true, scheduleId: req.params.scheduleId });
});

app.get("/api/logs", requireWorkerAuth, (req, res) => {
  res.json({ logs: recentLogs.slice(0, 30) });
});

app.post("/api/uploads/schedule", requireWorkerAuth, async (req, res) => {
  const { uploadId, userId, channelId, title, description, categoryId, privacyStatus, scheduledPublishIso } = req.body || {};
  if (!uploadId || !title || !scheduledPublishIso) {
    return res.status(400).json({ error: "uploadId, title, and scheduledPublishIso are required" });
  }
  if (pool) {
    await pool.query(
      `INSERT INTO upload_schedules (id, user_id, channel_id, title, description, category_id, privacy_status, scheduled_publish_iso, status)
       VALUES ($1, $2, $3, $4, $5, $6, $7, $8, 'SCHEDULED')
       ON CONFLICT (id) DO UPDATE SET scheduled_publish_iso = EXCLUDED.scheduled_publish_iso, title = EXCLUDED.title, status = 'SCHEDULED'`,
      [uploadId, userId || "owner", channelId || "", title, description || "", categoryId || "27", privacyStatus || "PUBLIC", scheduledPublishIso]
    );
  }
  res.json({ queued: true, uploadId, scheduledPublishIso });
});

app.post("/api/media/trim", requireWorkerAuth, (req, res) => {
  const { inputUrl, outputPath, startSec, endSec } = req.body || {};
  if (!inputUrl || !outputPath || startSec == null || endSec == null || endSec <= startSec) {
    return res.status(400).json({ error: "Valid inputUrl, outputPath, startSec, and endSec > startSec are required." });
  }
  const args = ["-y", "-ss", String(startSec), "-to", String(endSec), "-i", inputUrl, "-c:v", "libx264", "-preset", "fast", "-c:a", "aac", outputPath];
  const proc = spawn("ffmpeg", args, { stdio: ["ignore", "pipe", "pipe"] });
  proc.on("exit", (code) => recordWorkerLog(null, code === 0 ? "INFO" : "ERROR", "MEDIA_TRIM", `FFmpeg trim exit code ${code}`));
  res.json({ started: true, operation: "TRIM", outputPath });
});

app.post("/api/media/audio-normalize", requireWorkerAuth, (req, res) => {
  const { inputUrl, outputPath, gainDb } = req.body || {};
  if (!inputUrl || !outputPath) {
    return res.status(400).json({ error: "inputUrl and outputPath are required." });
  }
  const filter = typeof gainDb === "number" ? `volume=${gainDb}dB,loudnorm=I=-14:TP=-1.5:LRA=11` : "loudnorm=I=-14:TP=-1.5:LRA=11";
  const args = ["-y", "-i", inputUrl, "-c:v", "copy", "-af", filter, "-c:a", "aac", "-b:a", "192k", outputPath];
  const proc = spawn("ffmpeg", args, { stdio: ["ignore", "pipe", "pipe"] });
  proc.on("exit", (code) => recordWorkerLog(null, code === 0 ? "INFO" : "ERROR", "MEDIA_AUDIO", `FFmpeg audio normalize exit code ${code}`));
  res.json({ started: true, operation: "AUDIO_NORMALIZE", outputPath });
});

app.post("/api/media/repurpose-vertical", requireWorkerAuth, (req, res) => {
  const { inputUrl, outputPath, startSec, endSec } = req.body || {};
  if (!inputUrl || !outputPath) {
    return res.status(400).json({ error: "inputUrl and outputPath are required." });
  }
  const trimArgs = startSec != null && endSec != null ? ["-ss", String(startSec), "-to", String(endSec)] : [];
  const args = [
    "-y",
    ...trimArgs,
    "-i",
    inputUrl,
    "-vf",
    "scale=1080:1920:force_original_aspect_ratio=increase,crop=1080:1920",
    "-c:v",
    "libx264",
    "-preset",
    "fast",
    "-b:v",
    "6000k",
    "-c:a",
    "aac",
    "-b:a",
    "192k",
    outputPath,
  ];
  const proc = spawn("ffmpeg", args, { stdio: ["ignore", "pipe", "pipe"] });
  proc.on("exit", (code) => recordWorkerLog(null, code === 0 ? "INFO" : "ERROR", "MEDIA_REPURPOSE", `FFmpeg 9:16 repurpose exit code ${code}`));
  res.json({ started: true, operation: "REPURPOSE_9_16", outputPath });
});

async function recoverInterruptedJobs() {
  if (!pool || !TOKEN_ENCRYPTION_KEY) return 0;
  const result = await pool.query(
    `SELECT schedule_id, input_url, profile_id, max_duration_minutes, repeat_playback, last_safe_position_sec, encrypted_rtmps_url, stream_key_masked
     FROM streaming_jobs
     WHERE status IN ('LIVE', 'RECOVERING') AND paused_state = FALSE
     ORDER BY updated_at DESC
     LIMIT 10`
  );
  let restored = 0;
  for (const row of result.rows) {
    if (activeStreams.has(row.schedule_id)) continue;
    const entry = {
      scheduleId: row.schedule_id,
      inputUrl: row.input_url,
      profileId: row.profile_id || DEFAULT_PROFILE_ID,
      maxDurationMinutes: row.max_duration_minutes || 60,
      repeatPlayback: Boolean(row.repeat_playback),
      videoDurationSec: 0,
      paused: false,
      manualStop: false,
      accumulatedPlaySec: 0,
      segmentStartedAtMs: null,
      lastSafePositionSec: Number(row.last_safe_position_sec || 0),
      currentBitrateKbps: getQualityProfile(row.profile_id).videoBitrateKbps,
      fps: getQualityProfile(row.profile_id).fps,
      droppedFrames: 0,
      retryAttempt: 1,
      maxRetries: 5,
      recoveryState: "RECOVERED",
      maskedIngestUrl: row.stream_key_masked,
      encryptedRtmpsUrl: row.encrypted_rtmps_url,
      rawRtmpsUrl: null,
      child: null,
      durationTimer: null,
      retryTimer: null,
    };
    activeStreams.set(row.schedule_id, entry);
    spawnManagedFfmpeg(row.schedule_id, entry, entry.lastSafePositionSec);
    restored += 1;
  }
  return restored;
}

if (require.main === module) {
  app.listen(PORT, () => {
    console.log(`DREAMIIT28 V20.0 Cloud Streaming Worker listening on port ${PORT}`);
    recoverInterruptedJobs().catch((err) => {
      console.error("Startup recovery check skipped:", err.message);
    });
  });
}

module.exports = { app, recoverInterruptedJobs };
