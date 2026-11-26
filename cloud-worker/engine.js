const crypto = require("crypto");

const DEFAULT_PROFILE_ID = "720p30_4M";
const MIN_DURATION_MINUTES = 60;
const MAX_DURATION_MINUTES = 180;
const MAX_UPLOAD_BYTES = 128 * 1024 * 1024 * 1024; // 128 GB YouTube ceiling

const QUALITY_PROFILES = Object.freeze({
  "480p30_2M": {
    id: "480p30_2M",
    label: "480p SD (30 FPS)",
    scale: "854:480",
    fps: 30,
    videoBitrateKbps: 2500,
    audioBitrateKbps: 128,
  },
  "720p30_4M": {
    id: "720p30_4M",
    label: "720p HD (30 FPS • V20 Default)",
    scale: "1280:720",
    fps: 30,
    videoBitrateKbps: 4500,
    audioBitrateKbps: 128,
  },
  "1080p30_6M": {
    id: "1080p30_6M",
    label: "1080p Full HD (30 FPS)",
    scale: "1920:1080",
    fps: 30,
    videoBitrateKbps: 6000,
    audioBitrateKbps: 192,
  },
  "1080p60_9M": {
    id: "1080p60_9M",
    label: "1080p60 High Motion (60 FPS)",
    scale: "1920:1080",
    fps: 60,
    videoBitrateKbps: 9000,
    audioBitrateKbps: 192,
  },
});

function getQualityProfile(profileId) {
  return QUALITY_PROFILES[profileId] || QUALITY_PROFILES[DEFAULT_PROFILE_ID];
}

function validateDurationMinutes(minutes) {
  if (minutes === undefined || minutes === null || minutes === "") {
    return { valid: true, minutes: MIN_DURATION_MINUTES };
  }
  const num = Number(minutes);
  if (!Number.isFinite(num) || !Number.isInteger(num)) {
    return { valid: false, error: "maxDurationMinutes must be an integer between 60 and 180 minutes." };
  }
  if (num < MIN_DURATION_MINUTES || num > MAX_DURATION_MINUTES) {
    return { valid: false, error: "Live duration must be between 1 and 3 hours (60 to 180 minutes)." };
  }
  return { valid: true, minutes: num };
}

function computeBackoffMs(attempt) {
  const safeAttempt = Math.max(0, Math.min(10, Math.floor(Number(attempt) || 0)));
  return Math.min(300_000, 5000 * Math.pow(2, safeAttempt));
}

function computeResumePositionSec(lastSafePositionSec, videoDurationSec, repeatPlayback) {
  const safePos = Math.max(0, Math.floor(Number(lastSafePositionSec) || 0));
  const vidDur = Math.max(0, Math.floor(Number(videoDurationSec) || 0));
  if (safePos === 0) return 0;
  if (vidDur <= 0) return safePos;
  if (repeatPlayback) {
    return safePos % vidDur;
  }
  return Math.min(safePos, Math.max(0, vidDur - 2));
}

function maskRtmpsUrl(rawUrl) {
  if (!rawUrl || typeof rawUrl !== "string") return "";
  const trimmed = rawUrl.trim();
  if (!trimmed) return "";
  const slashIdx = trimmed.lastIndexOf("/");
  if (slashIdx <= 0 || slashIdx >= trimmed.length - 1) return "••••";
  const prefix = trimmed.slice(0, slashIdx + 1);
  const key = trimmed.slice(slashIdx + 1);
  if (key.includes("•")) return trimmed;
  const tail = key.length > 4 ? key.slice(-4) : "****";
  return `${prefix}••••${tail}`;
}

function validateStreamEndpoints(inputPath, rtmpsIngestUrl) {
  if (!inputPath || typeof inputPath !== "string" || /[\0\r\n]/.test(inputPath)) {
    throw new Error("Invalid inputPath for FFmpeg.");
  }
  if (!rtmpsIngestUrl || typeof rtmpsIngestUrl !== "string" || /[\0\r\n]/.test(rtmpsIngestUrl)) {
    throw new Error("Invalid rtmpsIngestUrl for FFmpeg.");
  }
  if (!/^rtmps?:\/\//i.test(rtmpsIngestUrl.trim())) {
    throw new Error("Ingest endpoint must use rtmps:// or rtmp:// protocol.");
  }
}

function buildFfmpegArgs({
  inputPath,
  rtmpsIngestUrl,
  profileId = DEFAULT_PROFILE_ID,
  resumeFromSec = 0,
  repeatPlayback = false,
  maxDurationSec = 0,
}) {
  validateStreamEndpoints(inputPath, rtmpsIngestUrl);
  const profile = getQualityProfile(profileId);
  const keyframeInterval = profile.fps * 2;
  const videoBitrate = `${profile.videoBitrateKbps}k`;
  const bufsize = `${profile.videoBitrateKbps * 2}k`;
  const audioBitrate = `${profile.audioBitrateKbps}k`;

  const args = ["-re"];

  const safeResume = Math.max(0, Math.floor(Number(resumeFromSec) || 0));
  if (safeResume > 0) {
    args.push("-ss", String(safeResume));
  }
  if (repeatPlayback === true) {
    args.push("-stream_loop", "-1");
  }

  args.push("-i", inputPath.trim());

  const safeDuration = Math.max(0, Math.floor(Number(maxDurationSec) || 0));
  if (safeDuration > 0) {
    args.push("-t", String(safeDuration));
  }

  args.push(
    "-vf",
    `scale=${profile.scale}:force_original_aspect_ratio=decrease,pad=${profile.scale}:(ow-iw)/2:(oh-ih)/2`,
    "-r",
    String(profile.fps),
    "-c:v",
    "libx264",
    "-preset",
    "veryfast",
    "-tune",
    "zerolatency",
    "-b:v",
    videoBitrate,
    "-minrate",
    videoBitrate,
    "-maxrate",
    videoBitrate,
    "-bufsize",
    bufsize,
    "-pix_fmt",
    "yuv420p",
    "-g",
    String(keyframeInterval),
    "-keyint_min",
    String(keyframeInterval),
    "-sc_threshold",
    "0",
    "-force_key_frames",
    "expr:gte(t,n_forced*2)",
    "-c:a",
    "aac",
    "-b:a",
    audioBitrate,
    "-ar",
    "44100",
    "-progress",
    "pipe:1",
    "-f",
    "flv",
    rtmpsIngestUrl.trim()
  );

  return args;
}

function validateMp4Upload({ filename, mimeType, fileSizeBytes, durationSec, headerBytes }) {
  const size = Number(fileSizeBytes);
  if (!Number.isFinite(size) || size <= 0) {
    return { valid: false, error: "File is empty or unreadable (0 bytes)." };
  }
  if (size > MAX_UPLOAD_BYTES) {
    return { valid: false, error: "File exceeds YouTube's maximum 128 GB upload limit." };
  }
  if (filename && !/\.mp4$/i.test(String(filename).trim())) {
    return { valid: false, error: "Only .mp4 pre-recorded video files are supported in V20.0." };
  }
  if (mimeType) {
    const normalizedMime = String(mimeType).toLowerCase().trim();
    if (normalizedMime !== "video/mp4" && normalizedMime !== "application/mp4") {
      return { valid: false, error: `Unsupported video MIME type: ${mimeType}. Expected video/mp4.` };
    }
  }
  if (durationSec !== undefined && durationSec !== null) {
    const dur = Number(durationSec);
    if (!Number.isFinite(dur) || dur <= 0) {
      return { valid: false, error: "Video duration must be greater than 0 seconds." };
    }
  }
  if (headerBytes) {
    const buf = Buffer.isBuffer(headerBytes) ? headerBytes : Buffer.from(headerBytes);
    if (buf.length < 8 || buf.toString("ascii", 4, 8) !== "ftyp") {
      return { valid: false, error: "Invalid MP4 container header (missing ISO BMFF 'ftyp' box)." };
    }
  }
  return { valid: true };
}

function deriveAesKey(secretMaterial) {
  const raw = String(secretMaterial || "").trim();
  if (!raw) {
    throw new Error("TOKEN_ENCRYPTION_KEY must be configured before encrypting credentials.");
  }
  return crypto.createHash("sha256").update(raw, "utf8").digest();
}

function encryptSecret(plaintext, secretMaterial) {
  const key = deriveAesKey(secretMaterial);
  const iv = crypto.randomBytes(12);
  const cipher = crypto.createCipheriv("aes-256-gcm", key, iv);
  const encrypted = Buffer.concat([cipher.update(String(plaintext), "utf8"), cipher.final()]);
  const tag = cipher.getAuthTag();
  return `v1:${iv.toString("base64")}:${tag.toString("base64")}:${encrypted.toString("base64")}`;
}

function decryptSecret(payload, secretMaterial) {
  const parts = String(payload || "").split(":");
  if (parts.length !== 4 || parts[0] !== "v1") {
    throw new Error("Invalid encrypted credential envelope.");
  }
  const key = deriveAesKey(secretMaterial);
  const iv = Buffer.from(parts[1], "base64");
  const tag = Buffer.from(parts[2], "base64");
  const ciphertext = Buffer.from(parts[3], "base64");
  const decipher = crypto.createDecipheriv("aes-256-gcm", key, iv);
  decipher.setAuthTag(tag);
  return Buffer.concat([decipher.update(ciphertext), decipher.final()]).toString("utf8");
}

function isBearerAuthorized(authHeader, expectedToken, allowUnauthenticated = false) {
  if (!expectedToken) {
    return allowUnauthenticated === true;
  }
  if (!authHeader || typeof authHeader !== "string") return false;
  const prefix = "Bearer ";
  if (!authHeader.startsWith(prefix)) return false;
  const provided = Buffer.from(authHeader.slice(prefix.length).trim(), "utf8");
  const expected = Buffer.from(String(expectedToken).trim(), "utf8");
  if (provided.length !== expected.length || provided.length === 0) return false;
  return crypto.timingSafeEqual(provided, expected);
}

function parseFfmpegProgressChunk(chunk) {
  const text = String(chunk || "");
  const out = {};
  const outTimeMatch = text.match(/out_time_ms=(\d+)/);
  if (outTimeMatch) {
    out.outTimeSec = Math.floor(Number(outTimeMatch[1]) / 1_000_000);
  }
  const bitrateMatch = text.match(/bitrate=\s*([\d.]+)kbits\/s/);
  if (bitrateMatch) {
    out.bitrateKbps = Math.round(Number(bitrateMatch[1]));
  }
  const fpsMatch = text.match(/fps=\s*([\d.]+)/);
  if (fpsMatch) {
    out.fps = Number(fpsMatch[1]);
  }
  const dropMatch = text.match(/drop_frames=(\d+)/) || text.match(/drop=\s*(\d+)/);
  if (dropMatch) {
    out.droppedFrames = Number(dropMatch[1]);
  }
  return out;
}

module.exports = {
  DEFAULT_PROFILE_ID,
  MIN_DURATION_MINUTES,
  MAX_DURATION_MINUTES,
  MAX_UPLOAD_BYTES,
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
};
