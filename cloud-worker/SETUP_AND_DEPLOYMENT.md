# DREAMIIT28 V20.0 Setup, Deployment & Testing Guide

## 1. Google Cloud Console & YouTube API Setup
1. Open Google Cloud Console for project `gen-lang-client-0542617375`.
2. Enable **YouTube Data API v3** and **YouTube Analytics API**.
3. Configure OAuth Consent Screen and add scopes:
   - `https://www.googleapis.com/auth/youtube`
   - `https://www.googleapis.com/auth/youtube.upload`
   - `https://www.googleapis.com/auth/youtube.force-ssl`
   - `https://www.googleapis.com/auth/yt-analytics.readonly`
4. Create an OAuth 2.0 Client ID (Android/Web PKCE) with redirect URI:
   `com.aistudio.dreamiit28.hskxmw:/oauth2redirect`
5. Paste the Client ID into **DREAMIIT28 -> Settings -> YouTube OAuth 2.0 Client ID** and tap **Authorize YouTube**.

## 2. Cloud Streaming Worker Deployment (V20.0 720p Default / 480p / 1080p RTMPS)
The Android app delegates long-running (1–3 hour) CBR RTMPS broadcasts to `/cloud-worker/` so streams continue autonomously with automatic crash recovery:

```bash
cd cloud-worker
psql "$DATABASE_URL" -f schema.sql
docker build -t dreamiit28-worker:20.0 .
docker run -d -p 8080:8080 \
  -e DATABASE_URL="postgres://..." \
  -e WORKER_API_TOKEN="replace-with-strong-random-bearer-token" \
  -e TOKEN_ENCRYPTION_KEY="replace-with-32-byte-aes-secret" \
  dreamiit28-worker:20.0
```

Deploy to Google Cloud Run (`--no-cpu-throttling --min-instances=1`) or a Linux VPS with FFmpeg, then configure inside **DREAMIIT28 -> Settings**:
- **Cloud Streaming Worker URL** (`https://...`)
- **Worker API Bearer Token** (stored locally in DataStore; never saved to Firestore)
- Enable **Administrator Cloud Infrastructure Approval** before launching live worker broadcasts.

## 3. Verification Checklist
- Run cloud worker tests:
  `node --check cloud-worker/server.js && node --test cloud-worker/server.test.js`
- Run Firestore Security Rules unit tests:
  `FIRESTORE_EMULATOR_HOST="127.0.0.1:8085" node --test firestore.test.js`
- Run Android JVM unit & Robolectric tests:
  `gradle :app:testDebugUnitTest`
