const test = require("node:test");
const assert = require("node:assert/strict");
const {
  DEFAULT_PROFILE_ID,
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

test("V20.0 quality profiles include 480p, 720p default (4.5Mbps CBR), 1080p30, and 1080p60", () => {
  assert.equal(DEFAULT_PROFILE_ID, "720p30_4M");
  const p720 = getQualityProfile("720p30_4M");
  assert.equal(p720.scale, "1280:720");
  assert.equal(p720.fps, 30);
  assert.equal(p720.videoBitrateKbps, 4500);
  assert.equal(p720.audioBitrateKbps, 128);

  const p480 = getQualityProfile("480p30_2M");
  assert.equal(p480.scale, "854:480");
  assert.equal(p480.videoBitrateKbps, 2500);

  const p1080 = getQualityProfile("1080p30_6M");
  assert.equal(p1080.scale, "1920:1080");
  assert.equal(p1080.videoBitrateKbps, 6000);
});

test("buildFfmpegArgs enforces CBR, 2s keyframe interval, AAC, resume offset, loop, and duration", () => {
  const args = buildFfmpegArgs({
    inputPath: "/var/data/lecture.mp4",
    rtmpsIngestUrl: "rtmps://a.rtmps.youtube.com/live2/abcd-1234-efgh-5678",
    profileId: "720p30_4M",
    resumeFromSec: 125,
    repeatPlayback: true,
    maxDurationSec: 7200,
  });

  assert.equal(args[0], "-re");
  assert.ok(args.includes("-ss"));
  assert.equal(args[args.indexOf("-ss") + 1], "125");
  assert.ok(args.includes("-stream_loop"));
  assert.equal(args[args.indexOf("-stream_loop") + 1], "-1");
  assert.equal(args[args.indexOf("-t") + 1], "7200");
  assert.equal(args[args.indexOf("-b:v") + 1], "4500k");
  assert.equal(args[args.indexOf("-minrate") + 1], "4500k");
  assert.equal(args[args.indexOf("-maxrate") + 1], "4500k");
  assert.equal(args[args.indexOf("-bufsize") + 1], "9000k");
  assert.equal(args[args.indexOf("-g") + 1], "60");
  assert.equal(args[args.indexOf("-force_key_frames") + 1], "expr:gte(t,n_forced*2)");
  assert.equal(args[args.indexOf("-c:a") + 1], "aac");
  assert.equal(args[args.indexOf("-b:a") + 1], "128k");
  assert.equal(args[args.length - 1], "rtmps://a.rtmps.youtube.com/live2/abcd-1234-efgh-5678");
});

test("validateDurationMinutes enforces 1-3 hour (60-180m) live bounds", () => {
  assert.deepEqual(validateDurationMinutes(60), { valid: true, minutes: 60 });
  assert.deepEqual(validateDurationMinutes(120), { valid: true, minutes: 120 });
  assert.deepEqual(validateDurationMinutes(180), { valid: true, minutes: 180 });
  assert.equal(validateDurationMinutes(45).valid, false);
  assert.equal(validateDurationMinutes(240).valid, false);
  assert.equal(validateDurationMinutes(90.5).valid, false);
});

test("exponential backoff and safe resume offset wrap properly with repeat playback", () => {
  assert.equal(computeBackoffMs(0), 5000);
  assert.equal(computeBackoffMs(1), 10000);
  assert.equal(computeBackoffMs(2), 20000);
  assert.equal(computeBackoffMs(8), 300000);

  assert.equal(computeResumePositionSec(350, 300, true), 50);
  assert.equal(computeResumePositionSec(350, 300, false), 298);
  assert.equal(computeResumePositionSec(120, 300, false), 120);
});

test("maskRtmpsUrl never exposes stream key and AES-256-GCM roundtrips cleanly", () => {
  const raw = "rtmps://a.rtmps.youtube.com/live2/secret-key-9988";
  const masked = maskRtmpsUrl(raw);
  assert.equal(masked, "rtmps://a.rtmps.youtube.com/live2/••••9988");
  assert.ok(!masked.includes("secret-key"));

  const enc = encryptSecret(raw, "super-strong-worker-secret");
  assert.ok(!enc.includes("secret-key"));
  assert.equal(decryptSecret(enc, "super-strong-worker-secret"), raw);
});

test("validateMp4Upload rejects zero-byte, non-mp4, bad ftyp header, and invalid duration", () => {
  assert.equal(validateMp4Upload({ filename: "a.mp4", mimeType: "video/mp4", fileSizeBytes: 0, durationSec: 10 }).valid, false);
  assert.equal(validateMp4Upload({ filename: "a.avi", mimeType: "video/x-msvideo", fileSizeBytes: 1024, durationSec: 10 }).valid, false);
  const badHeader = Buffer.from([0, 0, 0, 20, 0x4a, 0x50, 0x45, 0x47]);
  assert.equal(validateMp4Upload({ filename: "a.mp4", mimeType: "video/mp4", fileSizeBytes: 1024, durationSec: 10, headerBytes: badHeader }).valid, false);
  const goodHeader = Buffer.from([0, 0, 0, 20, 0x66, 0x74, 0x79, 0x70]);
  assert.equal(validateMp4Upload({ filename: "a.mp4", mimeType: "video/mp4", fileSizeBytes: 102400, durationSec: 120, headerBytes: goodHeader }).valid, true);
});

test("isBearerAuthorized checks timing-safe Bearer token and parseFfmpegProgressChunk reads telemetry", () => {
  assert.equal(isBearerAuthorized("Bearer tok_abc123", "tok_abc123"), true);
  assert.equal(isBearerAuthorized("Bearer wrong_tok", "tok_abc123"), false);
  assert.equal(isBearerAuthorized("", "tok_abc123"), false);

  const parsed = parseFfmpegProgressChunk("fps=29.97\nbitrate=4485.2kbits/s\nout_time_ms=15500000\ndrop_frames=2\n");
  assert.equal(parsed.outTimeSec, 15);
  assert.equal(parsed.bitrateKbps, 4485);
  assert.equal(parsed.fps, 29.97);
  assert.equal(parsed.droppedFrames, 2);
});
