-- DREAMIIT28 V20.0 PostgreSQL Cloud Worker Schema
-- Preserves V1-V4 tables and adds all V20.0 streaming, logging, broadcast, and metric tables.
-- Security rule: Never store plaintext YouTube stream keys or unencrypted OAuth tokens.

CREATE TABLE IF NOT EXISTS users (
  id TEXT PRIMARY KEY,
  owner_name TEXT NOT NULL DEFAULT 'Harsh Singh',
  email TEXT UNIQUE NOT NULL,
  default_privacy TEXT NOT NULL DEFAULT 'PUBLIC',
  default_resolution TEXT NOT NULL DEFAULT '720p',
  timezone TEXT NOT NULL DEFAULT 'Asia/Kolkata',
  language TEXT NOT NULL DEFAULT 'en',
  theme_mode TEXT NOT NULL DEFAULT 'DARK',
  infra_cost_approved BOOLEAN NOT NULL DEFAULT FALSE,
  monthly_budget_usd NUMERIC(10, 2) NOT NULL DEFAULT 50.00,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS youtube_channels (
  id TEXT PRIMARY KEY,
  user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  title TEXT NOT NULL,
  handle TEXT,
  subscriber_count BIGINT NOT NULL DEFAULT 0,
  view_count BIGINT NOT NULL DEFAULT 0,
  video_count BIGINT NOT NULL DEFAULT 0,
  encrypted_refresh_token TEXT,
  is_active BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS cloud_videos (
  id TEXT PRIMARY KEY,
  user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  channel_id TEXT NOT NULL,
  title TEXT NOT NULL,
  storage_object_url TEXT NOT NULL,
  source_width INT NOT NULL,
  source_height INT NOT NULL,
  source_fps INT NOT NULL,
  duration_sec INT NOT NULL,
  file_size_bytes BIGINT NOT NULL,
  quality_verified_1080p BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS live_schedules (
  id TEXT PRIMARY KEY,
  user_id TEXT NOT NULL,
  video_id TEXT NOT NULL,
  live_title TEXT NOT NULL,
  scheduled_start_iso TIMESTAMPTZ NOT NULL,
  target_profile TEXT NOT NULL DEFAULT '720p30_4M',
  rtmps_ingest_url TEXT,
  auto_end_on_finish BOOLEAN NOT NULL DEFAULT TRUE,
  max_duration_minutes INT NOT NULL DEFAULT 60,
  repeat_playback BOOLEAN NOT NULL DEFAULT FALSE,
  paused_state BOOLEAN NOT NULL DEFAULT FALSE,
  last_safe_position_sec BIGINT NOT NULL DEFAULT 0,
  dropped_frames INT NOT NULL DEFAULT 0,
  current_bitrate_kbps INT NOT NULL DEFAULT 0,
  youtube_confirmed_live BOOLEAN NOT NULL DEFAULT FALSE,
  recovery_state TEXT NOT NULL DEFAULT 'HEALTHY',
  retry_count INT NOT NULL DEFAULT 0,
  max_retries INT NOT NULL DEFAULT 5,
  status TEXT NOT NULL DEFAULT 'SCHEDULED',
  last_error TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS worker_jobs (
  id BIGSERIAL PRIMARY KEY,
  schedule_id TEXT NOT NULL REFERENCES live_schedules(id) ON DELETE CASCADE,
  status TEXT NOT NULL,
  attempt_number INT NOT NULL DEFAULT 1,
  cpu_percent NUMERIC(5, 2) DEFAULT 0,
  memory_mb INT DEFAULT 0,
  bitrate_kbps INT DEFAULT 0,
  dropped_frames INT DEFAULT 0,
  message TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS upload_schedules (
  id TEXT PRIMARY KEY,
  user_id TEXT NOT NULL,
  channel_id TEXT NOT NULL,
  title TEXT NOT NULL,
  description TEXT NOT NULL DEFAULT '',
  category_id TEXT NOT NULL DEFAULT '27',
  privacy_status TEXT NOT NULL DEFAULT 'PUBLIC',
  scheduled_publish_iso TIMESTAMPTZ NOT NULL,
  status TEXT NOT NULL DEFAULT 'SCHEDULED',
  youtube_video_id TEXT,
  last_error TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS activity_logs (
  id BIGSERIAL PRIMARY KEY,
  user_id TEXT NOT NULL,
  category TEXT NOT NULL,
  action TEXT NOT NULL,
  actor_name TEXT NOT NULL DEFAULT 'Harsh Singh',
  details TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- =========================================================================
-- V20.0 PROFESSIONAL YOUTUBE LIVE STREAMING ENGINE TABLES
-- =========================================================================

CREATE TABLE IF NOT EXISTS youtube_accounts (
  id TEXT PRIMARY KEY,
  user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  channel_id TEXT NOT NULL,
  channel_title TEXT NOT NULL,
  encrypted_access_token TEXT NOT NULL,
  encrypted_refresh_token TEXT,
  token_expires_at TIMESTAMPTZ,
  scopes TEXT NOT NULL DEFAULT '',
  is_active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS uploaded_videos (
  id TEXT PRIMARY KEY,
  user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  channel_id TEXT NOT NULL,
  title TEXT NOT NULL,
  description TEXT NOT NULL DEFAULT '',
  storage_object_url TEXT NOT NULL,
  thumbnail_url TEXT,
  mime_type TEXT NOT NULL DEFAULT 'video/mp4',
  duration_sec INT NOT NULL CHECK (duration_sec > 0),
  file_size_bytes BIGINT NOT NULL CHECK (file_size_bytes > 0),
  width INT NOT NULL DEFAULT 1280,
  height INT NOT NULL DEFAULT 720,
  fps INT NOT NULL DEFAULT 30,
  is_valid_mp4 BOOLEAN NOT NULL DEFAULT TRUE,
  replaced_from_video_id TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS streaming_jobs (
  id TEXT PRIMARY KEY,
  schedule_id TEXT NOT NULL,
  user_id TEXT NOT NULL,
  video_id TEXT NOT NULL,
  input_url TEXT NOT NULL,
  profile_id TEXT NOT NULL DEFAULT '720p30_4M',
  status TEXT NOT NULL DEFAULT 'QUEUED',
  max_duration_minutes INT NOT NULL DEFAULT 60 CHECK (max_duration_minutes BETWEEN 60 AND 180),
  repeat_playback BOOLEAN NOT NULL DEFAULT FALSE,
  paused_state BOOLEAN NOT NULL DEFAULT FALSE,
  last_safe_position_sec BIGINT NOT NULL DEFAULT 0,
  elapsed_sec BIGINT NOT NULL DEFAULT 0,
  retry_attempt INT NOT NULL DEFAULT 0,
  max_retries INT NOT NULL DEFAULT 5,
  recovery_state TEXT NOT NULL DEFAULT 'HEALTHY',
  encrypted_rtmps_url TEXT NOT NULL,
  stream_key_masked TEXT NOT NULL,
  last_error TEXT,
  started_at TIMESTAMPTZ,
  ended_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS live_broadcasts (
  id TEXT PRIMARY KEY,
  schedule_id TEXT NOT NULL,
  user_id TEXT NOT NULL,
  channel_id TEXT NOT NULL,
  youtube_broadcast_id TEXT NOT NULL,
  youtube_stream_id TEXT NOT NULL,
  youtube_live_url TEXT NOT NULL,
  lifecycle_status TEXT NOT NULL DEFAULT 'ready',
  youtube_confirmed_live BOOLEAN NOT NULL DEFAULT FALSE,
  stream_key_masked TEXT NOT NULL,
  encrypted_rtmps_url TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS streaming_settings (
  user_id TEXT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
  default_profile_id TEXT NOT NULL DEFAULT '720p30_4M',
  default_max_duration_minutes INT NOT NULL DEFAULT 60 CHECK (default_max_duration_minutes BETWEEN 60 AND 180),
  default_repeat_playback BOOLEAN NOT NULL DEFAULT FALSE,
  auto_recovery_enabled BOOLEAN NOT NULL DEFAULT TRUE,
  max_retry_attempts INT NOT NULL DEFAULT 5,
  theme_mode TEXT NOT NULL DEFAULT 'DARK',
  language TEXT NOT NULL DEFAULT 'en',
  infra_cost_approved BOOLEAN NOT NULL DEFAULT FALSE,
  monthly_budget_usd NUMERIC(10, 2) NOT NULL DEFAULT 50.00,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS streaming_logs (
  id BIGSERIAL PRIMARY KEY,
  schedule_id TEXT,
  user_id TEXT,
  level TEXT NOT NULL DEFAULT 'INFO',
  event_type TEXT NOT NULL,
  message TEXT NOT NULL,
  metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS server_metrics (
  id BIGSERIAL PRIMARY KEY,
  schedule_id TEXT,
  cpu_percent NUMERIC(5, 2) NOT NULL DEFAULT 0,
  memory_mb INT NOT NULL DEFAULT 0,
  network_mbps NUMERIC(7, 2) NOT NULL DEFAULT 0,
  bitrate_kbps INT NOT NULL DEFAULT 0,
  fps NUMERIC(5, 2) NOT NULL DEFAULT 0,
  dropped_frames INT NOT NULL DEFAULT 0,
  active_streams INT NOT NULL DEFAULT 0,
  recorded_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS scheduled_streams (
  id TEXT PRIMARY KEY,
  user_id TEXT NOT NULL,
  channel_id TEXT NOT NULL,
  video_id TEXT NOT NULL,
  video_title TEXT NOT NULL,
  live_title TEXT NOT NULL,
  scheduled_start_iso TIMESTAMPTZ NOT NULL,
  target_profile TEXT NOT NULL DEFAULT '720p30_4M',
  max_duration_minutes INT NOT NULL DEFAULT 60 CHECK (max_duration_minutes BETWEEN 60 AND 180),
  repeat_playback BOOLEAN NOT NULL DEFAULT FALSE,
  auto_end_on_finish BOOLEAN NOT NULL DEFAULT TRUE,
  status TEXT NOT NULL DEFAULT 'SCHEDULED',
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_streaming_jobs_status ON streaming_jobs(status, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_streaming_logs_schedule ON streaming_logs(schedule_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_server_metrics_recorded ON server_metrics(recorded_at DESC);
