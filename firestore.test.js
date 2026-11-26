const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "harsh_singh_uid_123";
const BOB_UID = "other_user_uid_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user cannot read or write any DREAMIIT28 collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("schedules").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("videos").doc("vid_1").set({
      userId: ALICE_UID,
      title: "Hack",
    })
  );
});

test("Owner can create and partially update UserSettings; cross-user and shadow fields denied", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date(Date.now() - 5000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      ownerName: "Harsh Singh",
      defaultPrivacy: "PUBLIC",
      defaultResolution: "1080p",
      defaultLiveDurationMin: 60,
      timezone: "Asia/Kolkata",
      language: "en",
      monthlyBudgetUsd: 50.0,
      notificationsEnabled: true,
      autoRecoveryEnabled: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Partial update succeeds
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      language: "hi",
      monthlyBudgetUsd: 75.0,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Shadow/ghost field rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Immutable createdAt mutation rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Cross-user read/write rejected
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).update({
      ownerName: "Bob",
      updatedAt: new Date(Date.now() - 500),
    })
  );
});

test("Channels, CloudVideos, and LivePlaylists enforce ownership and schema parity", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date(Date.now() - 5000);

  // Channel create & update (no @DocumentId 'id' field)
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("channels").doc("UC_12345").set({
      userId: ALICE_UID,
      channelId: "UC_12345",
      title: "DREAMIIT28 Official",
      handle: "@dreamiit28",
      subscriberCount: 1200,
      viewCount: 45000,
      videoCount: 32,
      activeChannel: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Reject extra 'id' field
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("channels").doc("UC_999").set({
      id: "UC_999",
      userId: ALICE_UID,
      channelId: "UC_999",
      title: "Bad",
      subscriberCount: 0,
      viewCount: 0,
      videoCount: 0,
      activeChannel: false,
      createdAt: now,
      updatedAt: now,
    })
  );

  // CloudVideo create & list
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("videos").doc("vid_1").set({
      userId: ALICE_UID,
      channelId: "UC_12345",
      title: "1080p Lecture Stream",
      description: "Full HD recorded session",
      categoryId: "27",
      privacyStatus: "PUBLIC",
      sourceWidth: 1920,
      sourceHeight: 1080,
      sourceFps: 30,
      durationSec: 1800,
      fileSizeBytes: 524288000,
      uploadStatus: "READY",
      qualityVerified1080p: true,
      tags: ["jee", "dreamiit28"],
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("videos").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("videos").get());

  // Playlist create
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("playlists").doc("pl_1").set({
      userId: ALICE_UID,
      channelId: "UC_12345",
      title: "Morning Marathon",
      videoIds: ["vid_1"],
      videoTitles: ["1080p Lecture Stream"],
      endBroadcastOnFinish: true,
      loopPlaylist: false,
      currentIndex: 0,
      status: "IDLE",
      createdAt: now,
      updatedAt: now,
    })
  );
});

test("LiveSchedule enforces terminal state locking and blocks deleting LIVE broadcasts", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date(Date.now() - 5000);
  const schedRef = aliceDb.collection("users").doc(ALICE_UID).collection("schedules").doc("sched_1");

  await assertSucceeds(
    schedRef.set({
      userId: ALICE_UID,
      channelId: "UC_12345",
      videoId: "vid_1",
      videoTitle: "1080p Lecture Stream",
      liveTitle: "DREAMIIT28 Live Session",
      liveDescription: "Automated 1080p RTMPS stream",
      privacyStatus: "PUBLIC",
      scheduledStartIso: "2026-11-26T10:00:00Z",
      scheduledStartEpochMs: Date.now() + 3600000,
      timezone: "Asia/Kolkata",
      recurrence: "ONCE",
      targetProfile: "1080p30_6M",
      status: "SCHEDULED",
      retryCount: 0,
      maxRetries: 3,
      autoEndOnFinish: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Transition to LIVE succeeds
  await assertSucceeds(
    schedRef.update({
      status: "LIVE",
      updatedAt: new Date(Date.now() - 2000),
    })
  );

  // Deleting an active LIVE schedule is blocked by rules
  await assertFails(schedRef.delete());

  // Transition to COMPLETED succeeds
  await assertSucceeds(
    schedRef.update({
      status: "COMPLETED",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Terminal state lock: updating COMPLETED schedule is rejected
  await assertFails(
    schedRef.update({
      status: "SCHEDULED",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Deleting COMPLETED schedule is allowed
  await assertSucceeds(schedRef.delete());
});

test("Jobs, Notifications, AuditLogs, and Backups enforce immutability and ownership", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date(Date.now() - 5000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("jobs").doc("job_1").set({
      userId: ALICE_UID,
      scheduleId: "sched_1",
      jobType: "RTMPS_STREAM",
      status: "RUNNING",
      attemptNumber: 1,
      message: "FFmpeg streaming at 1920x1080 30fps 6000kbps",
      cpuPercent: 38.5,
      memoryMb: 640,
      bitrateKbps: 6020,
      droppedFrames: 0,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("notifications").doc("notif_1").set({
      userId: ALICE_UID,
      eventType: "LIVE_SCHEDULED",
      title: "Live Scheduled",
      body: "DREAMIIT28 Live Session scheduled for 1080p RTMPS.",
      severity: "INFO",
      readState: false,
      createdAt: now,
      updatedAt: now,
    })
  );

  const auditRef = aliceDb.collection("users").doc(ALICE_UID).collection("auditLogs").doc("log_1");
  await assertSucceeds(
    auditRef.set({
      userId: ALICE_UID,
      eventType: "GOOGLE_SIGN_IN",
      deviceName: "Pixel 8 Pro",
      status: "SUCCESS",
      details: "Signed in via Credential Manager",
      createdAt: now,
    })
  );
  // Audit logs are immutable
  await assertFails(auditRef.update({ status: "TAMPERED" }));

  const backupRef = aliceDb.collection("users").doc(ALICE_UID).collection("backups").doc("bak_1");
  await assertSucceeds(
    backupRef.set({
      userId: ALICE_UID,
      label: "Pre-Stream Config Backup",
      videoCount: 3,
      scheduleCount: 2,
      playlistCount: 1,
      retentionDays: 30,
      snapshotSummary: "3 videos, 2 schedules, 1 playlist",
      createdAt: now,
    })
  );
  await assertFails(backupRef.update({ label: "Tampered" }));
});

test("V3.0 uploadSchedules, teamMembers, activityLogs, and optional fields enforce owner bounds", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const now = new Date(Date.now() - 5000);

  // UserSettings with V3.0 dashboardWidgets + storageQuotaGb
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      ownerName: "Harsh Singh",
      defaultPrivacy: "PUBLIC",
      defaultResolution: "1080p",
      defaultLiveDurationMin: 60,
      timezone: "Asia/Kolkata",
      language: "en",
      monthlyBudgetUsd: 50.0,
      notificationsEnabled: true,
      autoRecoveryEnabled: true,
      dashboardWidgets: ["hero", "live_countdown", "channel_stats"],
      hiddenWidgets: [],
      autoBackupEnabled: true,
      autoBackupFrequency: "DAILY",
      storageQuotaGb: 100.0,
      activeRolePreview: "OWNER",
      createdAt: now,
      updatedAt: now,
    })
  );

  // UploadSchedule create + update + terminal lock
  const upRef = aliceDb.collection("users").doc(ALICE_UID).collection("uploadSchedules").doc("up_1");
  await assertSucceeds(
    upRef.set({
      userId: ALICE_UID,
      channelId: "UC_12345",
      title: "Scheduled JEE Physics Lecture",
      description: "Auto-scheduled upload",
      categoryId: "27",
      privacyStatus: "PUBLIC",
      tags: ["jee", "physics"],
      localUri: "content://media/external/video/media/42",
      scheduledPublishIso: "2026-11-28T14:00:00Z",
      scheduledPublishEpochMs: Date.now() + 7200000,
      timezone: "Asia/Kolkata",
      status: "SCHEDULED",
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertSucceeds(
    upRef.update({
      status: "PUBLISHED",
      youtubeVideoId: "yt_vid_99",
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  // Terminal PUBLISHED lock
  await assertFails(
    upRef.update({
      status: "SCHEDULED",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // TeamMember create + update + cross-user denied
  const tmRef = aliceDb.collection("users").doc(ALICE_UID).collection("teamMembers").doc("tm_1");
  await assertSucceeds(
    tmRef.set({
      userId: ALICE_UID,
      memberName: "Aarav Editor",
      memberEmail: "aarav@dreamiit28.com",
      role: "EDITOR",
      activeMember: true,
      addedBy: "Harsh Singh",
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertSucceeds(
    tmRef.update({
      role: "VIEWER",
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("teamMembers").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("uploadSchedules").get());

  // ActivityLog create + immutability
  const actRef = aliceDb.collection("users").doc(ALICE_UID).collection("activityLogs").doc("act_1");
  await assertSucceeds(
    actRef.set({
      userId: ALICE_UID,
      category: "TEAM",
      action: "TEAM_MEMBER_ADDED",
      actorName: "Harsh Singh",
      actorRole: "OWNER",
      details: "Added Aarav Editor as EDITOR",
      timestampEpochMs: Date.now() - 2000,
      createdAt: now,
    })
  );
  await assertFails(actRef.update({ action: "TAMPERED" }));
});

test("V4.1 competitors, sessions, copyrightReports, and V4 UserSettings enforce owner-only schema", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const now = new Date(Date.now() - 5000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      ownerName: "Harsh Singh",
      defaultPrivacy: "PUBLIC",
      defaultResolution: "1080p",
      defaultLiveDurationMin: 60,
      timezone: "Asia/Kolkata",
      language: "en",
      monthlyBudgetUsd: 50.0,
      notificationsEnabled: true,
      autoRecoveryEnabled: true,
      twoFactorConfirmed: true,
      knownDevices: ["Pixel 8 Pro"],
      apiQuotaDailyLimit: 10000,
      apiUnitsUsedToday: 120,
      apiQuotaResetDate: "2026-11-25",
      autoStreamRetryEnabled: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Competitor channel tracker
  const compRef = aliceDb.collection("users").doc(ALICE_UID).collection("competitors").doc("UC_COMP_1");
  await assertSucceeds(
    compRef.set({
      userId: ALICE_UID,
      channelId: "UC_COMP_1",
      title: "Physics Wallah",
      handle: "@PhysicsWallah",
      subscriberCount: 12500000,
      viewCount: 950000000,
      videoCount: 4200,
      lastSyncedEpochMs: Date.now() - 1000,
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("competitors").get());

  // UserSession manager
  const sessRef = aliceDb.collection("users").doc(ALICE_UID).collection("sessions").doc("sess_1");
  await assertSucceeds(
    sessRef.set({
      userId: ALICE_UID,
      deviceName: "Pixel 8 Pro",
      platform: "Android",
      sessionTokenHash: "sha256_session_token_abc123",
      lastActiveEpochMs: Date.now() - 1000,
      revoked: false,
      trustedDevice: true,
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertSucceeds(
    sessRef.update({
      revoked: true,
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("sessions").get());

  // CopyrightRiskReport immutable log
  const repRef = aliceDb.collection("users").doc(ALICE_UID).collection("copyrightReports").doc("rep_1");
  await assertSucceeds(
    repRef.set({
      userId: ALICE_UID,
      videoId: "vid_1",
      videoTitle: "JEE Revision Lecture",
      riskLevel: "LOW",
      summary: "Original educational voiceover detected; low risk.",
      triggers: ["None detected"],
      mitigations: ["Keep original screen recording"],
      policyFlags: ["MONETIZATION_SAFE"],
      createdAt: now,
    })
  );
  await assertFails(repRef.update({ riskLevel: "HIGH" }));
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("copyrightReports").get());
});

test("V20.0 profiles, Dark/Light themeMode, infraCostApproved, 1-3h duration bounds, and pre-start video swap", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date(Date.now() - 5000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      ownerName: "Harsh Singh",
      defaultPrivacy: "PUBLIC",
      defaultResolution: "720p",
      defaultLiveDurationMin: 60,
      timezone: "Asia/Kolkata",
      language: "en",
      monthlyBudgetUsd: 50.0,
      notificationsEnabled: true,
      autoRecoveryEnabled: true,
      themeMode: "LIGHT",
      infraCostApproved: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Reject invalid themeMode
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      themeMode: "NEON",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  const schedRef = aliceDb.collection("users").doc(ALICE_UID).collection("schedules").doc("sched_v20");
  await assertSucceeds(
    schedRef.set({
      userId: ALICE_UID,
      channelId: "UC_12345",
      videoId: "vid_1",
      videoTitle: "720p Lecture",
      liveTitle: "V20 720p Default Live",
      liveDescription: "CBR 4.5 Mbps 2s keyframe",
      privacyStatus: "PUBLIC",
      scheduledStartIso: "2026-11-26T10:00:00Z",
      scheduledStartEpochMs: Date.now() + 3600000,
      timezone: "Asia/Kolkata",
      recurrence: "ONCE",
      targetProfile: "720p30_4M",
      status: "SCHEDULED",
      retryCount: 0,
      maxRetries: 5,
      autoEndOnFinish: true,
      maxDurationMinutes: 120,
      repeatPlayback: true,
      pausedState: false,
      lastSafePositionSec: 0,
      droppedFrames: 0,
      currentBitrateKbps: 4500,
      youtubeConfirmedLive: false,
      recoveryState: "HEALTHY",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Allow switching video & 480p profile before starting
  await assertSucceeds(
    schedRef.update({
      videoId: "vid_2",
      videoTitle: "Replaced Lecture MP4",
      targetProfile: "480p30_2M",
      pausedState: true,
      lastSafePositionSec: 420,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject maxDurationMinutes outside 60..180
  await assertFails(
    schedRef.update({
      maxDurationMinutes: 240,
      updatedAt: new Date(Date.now() - 500),
    })
  );
});


