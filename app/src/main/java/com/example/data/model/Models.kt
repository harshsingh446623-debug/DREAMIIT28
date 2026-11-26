package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class PrivacyStatus(val label: String, val apiValue: String) {
    PUBLIC("Public", "public"),
    UNLISTED("Unlisted", "unlisted"),
    PRIVATE("Private", "private");

    companion object {
        fun fromRaw(raw: String?): PrivacyStatus =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: PUBLIC
    }
}

enum class StreamingProfile(
    val id: String,
    val label: String,
    val resolution: String,
    val width: Int,
    val height: Int,
    val fps: Int,
    val videoBitrateKbps: Int,
    val audioBitrateKbps: Int,
    val videoCodec: String = "H.264",
    val audioCodec: String = "AAC",
    val protocol: String = "RTMPS"
) {
    SD_480P_30(
        id = "480p30_2M",
        label = "480p SD • 30 FPS • 2.5 Mbps",
        resolution = "854 × 480",
        width = 854,
        height = 480,
        fps = 30,
        videoBitrateKbps = 2500,
        audioBitrateKbps = 128
    ),
    HD_720P_30(
        id = "720p30_4M",
        label = "720p HD • 30 FPS • 4.5 Mbps (V20 Default)",
        resolution = "1280 × 720",
        width = 1280,
        height = 720,
        fps = 30,
        videoBitrateKbps = 4500,
        audioBitrateKbps = 128
    ),
    HD_1080P_30(
        id = "1080p30_6M",
        label = "1080p Full HD • 30 FPS • 6 Mbps",
        resolution = "1920 × 1080",
        width = 1920,
        height = 1080,
        fps = 30,
        videoBitrateKbps = 6000,
        audioBitrateKbps = 192
    ),
    HD_1080P_60(
        id = "1080p60_9M",
        label = "1080p Full HD • 60 FPS • 9 Mbps",
        resolution = "1920 × 1080",
        width = 1920,
        height = 1080,
        fps = 60,
        videoBitrateKbps = 9000,
        audioBitrateKbps = 192
    );

    companion object {
        val DEFAULT_V20: StreamingProfile = HD_720P_30

        fun fromId(id: String?): StreamingProfile =
            entries.firstOrNull { it.id == id } ?: DEFAULT_V20
    }
}

enum class ScheduleRecurrence(val label: String) {
    ONCE("One-time"),
    DAILY("Daily"),
    WEEKLY("Weekly");

    companion object {
        fun fromRaw(raw: String?): ScheduleRecurrence =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: ONCE
    }
}

enum class ScheduleStatus {
    SCHEDULED,
    PREPARING,
    LIVE,
    COMPLETED,
    CANCELLED,
    FAILED;

    companion object {
        fun fromRaw(raw: String?): ScheduleStatus =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: SCHEDULED
    }
}

enum class UploadStatus {
    QUEUED,
    UPLOADING,
    READY,
    UPLOADED,
    FAILED;

    companion object {
        fun fromRaw(raw: String?): UploadStatus =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: QUEUED
    }
}

enum class UploadScheduleStatus {
    SCHEDULED,
    UPLOADING,
    PUBLISHED,
    FAILED,
    CANCELLED;

    companion object {
        fun fromRaw(raw: String?): UploadScheduleStatus =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: SCHEDULED
    }
}

enum class TeamRole(val label: String, val summary: String) {
    OWNER("Owner", "Full Studio, Billing, Branding, Team & Live control"),
    EDITOR("Editor", "Upload, Schedule, Media Lab, AI Studio, Chat & Comments"),
    VIEWER("Viewer", "Read-only Analytics, Calendar, Activity Log & Health");

    fun canMutateContent(): Boolean = this == OWNER || this == EDITOR
    fun canManageSensitiveOps(): Boolean = this == OWNER

    companion object {
        fun fromRaw(raw: String?): TeamRole =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: OWNER
    }
}

enum class DashboardWidgetId(val title: String) {
    HERO_CHANNEL("Channel Connection Card"),
    CREATE_CTA("+ CREATE VIDEO Button"),
    LIVE_COUNTDOWN("Live Countdown Timer"),
    CHANNEL_PERFORMANCE("YouTube Channel Performance"),
    CLOUD_HEALTH_API("Cloud Health, Expenses & Quota"),
    SECURITY_COPYRIGHT_ALERTS("Security & Copyright Alerts"),
    QUICK_TOOLS("V20.0 Quick Tools"),
    STATS_GRID("Channel & Automation Stats"),
    UPCOMING_LIVE("Upcoming Cloud Broadcasts"),
    STORAGE_STATUS("Cloud Storage Alert Meter"),
    RECENT_ACTIVITY("Recent Studio Activity")
}

val DEFAULT_DASHBOARD_WIDGETS = DashboardWidgetId.entries.map { it.name }

@Keep
@IgnoreExtraProperties
data class UserSettings(
    @DocumentId val id: String = "",
    val userId: String = "",
    val ownerName: String = "Harsh Singh",
    val defaultPrivacy: String = "PUBLIC",
    val defaultResolution: String = "720p",
    val defaultLiveDurationMin: Int = 60,
    val timezone: String = "Asia/Kolkata",
    val language: String = "en",
    val monthlyBudgetUsd: Double = 50.0,
    val notificationsEnabled: Boolean = true,
    val autoRecoveryEnabled: Boolean = true,
    val workerBaseUrl: String? = null,
    val youtubeOAuthClientId: String? = null,
    val activeChannelId: String? = null,
    val lastLoginDevice: String? = null,
    // V3.0 optional persisted preferences (matched 1:1 with firestore.rules)
    val dashboardWidgets: List<String>? = null,
    val hiddenWidgets: List<String>? = null,
    val autoBackupEnabled: Boolean? = null,
    val autoBackupFrequency: String? = null,
    val storageQuotaGb: Double? = null,
    val storageAlert80Sent: Boolean? = null,
    val storageAlert90Sent: Boolean? = null,
    val activeRolePreview: String? = null,
    // V4.1 optional persisted preferences (matched 1:1 with firestore.rules)
    val twoFactorConfirmed: Boolean? = null,
    val twoFactorRemindedAtMs: Long? = null,
    val knownDevices: List<String>? = null,
    val billingConnectedUrl: String? = null,
    val apiQuotaDailyLimit: Int? = null,
    val apiUnitsUsedToday: Int? = null,
    val apiQuotaResetDate: String? = null,
    val autoStreamRetryEnabled: Boolean? = null,
    // V20.0 optional persisted preferences (matched 1:1 with firestore.rules)
    val themeMode: String? = null,
    val infraCostApproved: Boolean? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "userId" to userId,
        "ownerName" to ownerName.take(80).ifBlank { "Harsh Singh" },
        "defaultPrivacy" to defaultPrivacy,
        "defaultResolution" to defaultResolution,
        "defaultLiveDurationMin" to defaultLiveDurationMin.coerceIn(5, 720),
        "timezone" to timezone.take(64).ifBlank { "Asia/Kolkata" },
        "language" to if (language == "hi") "hi" else "en",
        "monthlyBudgetUsd" to monthlyBudgetUsd.coerceIn(0.0, 100000.0),
        "notificationsEnabled" to notificationsEnabled,
        "autoRecoveryEnabled" to autoRecoveryEnabled,
        "workerBaseUrl" to workerBaseUrl?.take(300)?.takeIf { it.isNotBlank() },
        "youtubeOAuthClientId" to youtubeOAuthClientId?.take(200)?.takeIf { it.isNotBlank() },
        "activeChannelId" to activeChannelId?.take(128)?.takeIf { it.isNotBlank() },
        "lastLoginDevice" to lastLoginDevice?.take(120)?.takeIf { it.isNotBlank() },
        "dashboardWidgets" to dashboardWidgets?.take(24),
        "hiddenWidgets" to hiddenWidgets?.take(24),
        "autoBackupEnabled" to autoBackupEnabled,
        "autoBackupFrequency" to autoBackupFrequency?.takeIf { it in listOf("OFF", "DAILY", "WEEKLY") },
        "storageQuotaGb" to storageQuotaGb?.coerceIn(1.0, 5000.0),
        "storageAlert80Sent" to storageAlert80Sent,
        "storageAlert90Sent" to storageAlert90Sent,
        "activeRolePreview" to activeRolePreview?.takeIf { it in listOf("OWNER", "EDITOR", "VIEWER") },
        "twoFactorConfirmed" to twoFactorConfirmed,
        "twoFactorRemindedAtMs" to twoFactorRemindedAtMs?.coerceAtLeast(0L),
        "knownDevices" to knownDevices?.take(20)?.filter { it.isNotBlank() },
        "billingConnectedUrl" to billingConnectedUrl?.take(300)?.takeIf { it.isNotBlank() },
        "apiQuotaDailyLimit" to apiQuotaDailyLimit?.coerceIn(100, 10_000_000),
        "apiUnitsUsedToday" to apiUnitsUsedToday?.coerceAtLeast(0),
        "apiQuotaResetDate" to apiQuotaResetDate?.take(32)?.takeIf { it.isNotBlank() },
        "autoStreamRetryEnabled" to autoStreamRetryEnabled,
        "themeMode" to themeMode?.takeIf { it in listOf("DARK", "LIGHT") },
        "infraCostApproved" to infraCostApproved,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    ).filterValues { it != null } as Map<String, Any>
}

@Keep
@IgnoreExtraProperties
data class YouTubeChannel(
    @DocumentId val id: String = "",
    val userId: String = "",
    val channelId: String = "",
    val title: String = "",
    val handle: String? = null,
    val thumbnailUrl: String? = null,
    val subscriberCount: Long = 0L,
    val viewCount: Long = 0L,
    val videoCount: Long = 0L,
    val activeChannel: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "userId" to userId,
        "channelId" to channelId.take(128),
        "title" to title.take(120).ifBlank { "YouTube Channel" },
        "handle" to handle?.take(80)?.takeIf { it.isNotBlank() },
        "thumbnailUrl" to thumbnailUrl?.take(500)?.takeIf { it.isNotBlank() },
        "subscriberCount" to subscriberCount.coerceAtLeast(0L),
        "viewCount" to viewCount.coerceAtLeast(0L),
        "videoCount" to videoCount.coerceAtLeast(0L),
        "activeChannel" to activeChannel,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    ).filterValues { it != null } as Map<String, Any>
}

@Keep
@IgnoreExtraProperties
data class CloudVideo(
    @DocumentId val id: String = "",
    val userId: String = "",
    val channelId: String = "",
    val title: String = "",
    val description: String = "",
    val categoryId: String = "27",
    val privacyStatus: String = "PUBLIC",
    val sourceWidth: Int = 1920,
    val sourceHeight: Int = 1080,
    val sourceFps: Int = 30,
    val durationSec: Long = 0L,
    val fileSizeBytes: Long = 0L,
    val uploadStatus: String = "READY",
    val qualityVerified1080p: Boolean = true,
    val tags: List<String>? = null,
    val localUri: String? = null,
    val storageUrl: String? = null,
    val thumbnailUrl: String? = null,
    val youtubeVideoId: String? = null,
    val qualityWarning: String? = null,
    // V3.0 optional media & AI fields
    val transcriptText: String? = null,
    val subtitlesEnSrt: String? = null,
    val subtitlesHiSrt: String? = null,
    val chaptersText: String? = null,
    val clipSourceVideoId: String? = null,
    val audioDbPeak: Double? = null,
    val audioNormalized: Boolean? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "userId" to userId,
        "channelId" to channelId.take(128).ifBlank { "unassigned" },
        "title" to title.take(150).ifBlank { "Untitled Video" },
        "description" to description.take(5000),
        "categoryId" to categoryId.take(20).ifBlank { "27" },
        "privacyStatus" to PrivacyStatus.fromRaw(privacyStatus).name,
        "sourceWidth" to sourceWidth.coerceIn(0, 7680),
        "sourceHeight" to sourceHeight.coerceIn(0, 4320),
        "sourceFps" to sourceFps.coerceIn(0, 240),
        "durationSec" to durationSec.coerceIn(0L, 86400L),
        "fileSizeBytes" to fileSizeBytes.coerceAtLeast(0L),
        "uploadStatus" to UploadStatus.fromRaw(uploadStatus).name,
        "qualityVerified1080p" to qualityVerified1080p,
        "tags" to tags?.take(15)?.filter { it.isNotBlank() }?.takeIf { it.isNotEmpty() },
        "localUri" to localUri?.take(500)?.takeIf { it.isNotBlank() },
        "storageUrl" to storageUrl?.take(500)?.takeIf { it.isNotBlank() },
        "thumbnailUrl" to thumbnailUrl?.take(500)?.takeIf { it.isNotBlank() },
        "youtubeVideoId" to youtubeVideoId?.take(64)?.takeIf { it.isNotBlank() },
        "qualityWarning" to qualityWarning?.take(300)?.takeIf { it.isNotBlank() },
        "transcriptText" to transcriptText?.take(40000)?.takeIf { it.isNotBlank() },
        "subtitlesEnSrt" to subtitlesEnSrt?.take(40000)?.takeIf { it.isNotBlank() },
        "subtitlesHiSrt" to subtitlesHiSrt?.take(40000)?.takeIf { it.isNotBlank() },
        "chaptersText" to chaptersText?.take(10000)?.takeIf { it.isNotBlank() },
        "clipSourceVideoId" to clipSourceVideoId?.take(128)?.takeIf { it.isNotBlank() },
        "audioDbPeak" to audioDbPeak?.coerceIn(-120.0, 24.0),
        "audioNormalized" to audioNormalized,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    ).filterValues { it != null } as Map<String, Any>
}

@Keep
@IgnoreExtraProperties
data class LiveSchedule(
    @DocumentId val id: String = "",
    val userId: String = "",
    val channelId: String = "",
    val videoId: String = "",
    val videoTitle: String = "",
    val liveTitle: String = "",
    val liveDescription: String = "",
    val privacyStatus: String = "PUBLIC",
    val scheduledStartIso: String = "",
    val scheduledStartEpochMs: Long = 0L,
    val timezone: String = "Asia/Kolkata",
    val recurrence: String = "ONCE",
    val targetProfile: String = StreamingProfile.DEFAULT_V20.id,
    val status: String = "SCHEDULED",
    val retryCount: Int = 0,
    val maxRetries: Int = 5,
    val autoEndOnFinish: Boolean = true,
    val thumbnailUrl: String? = null,
    val youtubeBroadcastId: String? = null,
    val youtubeStreamId: String? = null,
    val youtubeWatchUrl: String? = null,
    val rtmpsIngestUrl: String? = null,
    val lastError: String? = null,
    val workerJobId: String? = null,
    // V20.0 live engine controls & recovery telemetry
    val maxDurationMinutes: Int? = null,
    val repeatPlayback: Boolean? = null,
    val pausedState: Boolean? = null,
    val lastSafePositionSec: Long? = null,
    val droppedFrames: Int? = null,
    val currentBitrateKbps: Int? = null,
    val youtubeConfirmedLive: Boolean? = null,
    val recoveryState: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "userId" to userId,
        "channelId" to channelId.take(128).ifBlank { "unassigned" },
        "videoId" to videoId.take(128).ifBlank { "manual" },
        "videoTitle" to videoTitle.take(150).ifBlank { liveTitle.take(150) },
        "liveTitle" to liveTitle.take(150).ifBlank { "DREAMIIT28 Live" },
        "liveDescription" to liveDescription.take(5000),
        "privacyStatus" to PrivacyStatus.fromRaw(privacyStatus).name,
        "scheduledStartIso" to scheduledStartIso.take(64),
        "scheduledStartEpochMs" to scheduledStartEpochMs.coerceAtLeast(1L),
        "timezone" to timezone.take(64).ifBlank { "Asia/Kolkata" },
        "recurrence" to ScheduleRecurrence.fromRaw(recurrence).name,
        "targetProfile" to StreamingProfile.fromId(targetProfile).id,
        "status" to ScheduleStatus.fromRaw(status).name,
        "retryCount" to retryCount.coerceIn(0, 10),
        "maxRetries" to maxRetries.coerceIn(0, 10),
        "autoEndOnFinish" to autoEndOnFinish,
        "thumbnailUrl" to thumbnailUrl?.take(500)?.takeIf { it.isNotBlank() },
        "youtubeBroadcastId" to youtubeBroadcastId?.take(128)?.takeIf { it.isNotBlank() },
        "youtubeStreamId" to youtubeStreamId?.take(128)?.takeIf { it.isNotBlank() },
        "youtubeWatchUrl" to youtubeWatchUrl?.take(300)?.takeIf { it.isNotBlank() },
        "rtmpsIngestUrl" to rtmpsIngestUrl?.take(300)?.takeIf { it.isNotBlank() },
        "lastError" to lastError?.take(500)?.takeIf { it.isNotBlank() },
        "workerJobId" to workerJobId?.take(128)?.takeIf { it.isNotBlank() },
        "maxDurationMinutes" to maxDurationMinutes?.coerceIn(60, 180),
        "repeatPlayback" to repeatPlayback,
        "pausedState" to pausedState,
        "lastSafePositionSec" to lastSafePositionSec?.coerceIn(0L, 86400L),
        "droppedFrames" to droppedFrames?.coerceAtLeast(0),
        "currentBitrateKbps" to currentBitrateKbps?.coerceIn(0, 50000),
        "youtubeConfirmedLive" to youtubeConfirmedLive,
        "recoveryState" to recoveryState?.take(120)?.takeIf { it.isNotBlank() },
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    ).filterValues { it != null } as Map<String, Any>
}

@Keep
@IgnoreExtraProperties
data class UploadSchedule(
    @DocumentId val id: String = "",
    val userId: String = "",
    val channelId: String = "",
    val title: String = "",
    val description: String = "",
    val categoryId: String = "27",
    val privacyStatus: String = "PUBLIC",
    val tags: List<String>? = null,
    val localUri: String? = null,
    val scheduledPublishIso: String = "",
    val scheduledPublishEpochMs: Long = 0L,
    val timezone: String = "Asia/Kolkata",
    val status: String = "SCHEDULED",
    val youtubeVideoId: String? = null,
    val lastError: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "userId" to userId,
        "channelId" to channelId.take(128).ifBlank { "unassigned" },
        "title" to title.take(150).ifBlank { "Scheduled Upload" },
        "description" to description.take(5000),
        "categoryId" to categoryId.take(20).ifBlank { "27" },
        "privacyStatus" to PrivacyStatus.fromRaw(privacyStatus).name,
        "tags" to tags?.take(15)?.filter { it.isNotBlank() }?.takeIf { it.isNotEmpty() },
        "localUri" to localUri?.take(500)?.takeIf { it.isNotBlank() },
        "scheduledPublishIso" to scheduledPublishIso.take(64),
        "scheduledPublishEpochMs" to scheduledPublishEpochMs.coerceAtLeast(1L),
        "timezone" to timezone.take(64).ifBlank { "Asia/Kolkata" },
        "status" to UploadScheduleStatus.fromRaw(status).name,
        "youtubeVideoId" to youtubeVideoId?.take(64)?.takeIf { it.isNotBlank() },
        "lastError" to lastError?.take(500)?.takeIf { it.isNotBlank() },
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    ).filterValues { it != null } as Map<String, Any>
}

@Keep
@IgnoreExtraProperties
data class LivePlaylist(
    @DocumentId val id: String = "",
    val userId: String = "",
    val channelId: String = "",
    val title: String = "",
    val videoIds: List<String> = emptyList(),
    val videoTitles: List<String> = emptyList(),
    val endBroadcastOnFinish: Boolean = true,
    val loopPlaylist: Boolean = false,
    val currentIndex: Int = 0,
    val status: String = "IDLE",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "userId" to userId,
        "channelId" to channelId.take(128).ifBlank { "unassigned" },
        "title" to title.take(120).ifBlank { "Live Playlist" },
        "videoIds" to videoIds.take(50),
        "videoTitles" to videoTitles.take(50),
        "endBroadcastOnFinish" to endBroadcastOnFinish,
        "loopPlaylist" to loopPlaylist,
        "currentIndex" to currentIndex.coerceIn(0, 50),
        "status" to status,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class WorkerJob(
    @DocumentId val id: String = "",
    val userId: String = "",
    val scheduleId: String = "",
    val jobType: String = "RTMPS_STREAM",
    val status: String = "PENDING",
    val attemptNumber: Int = 1,
    val message: String = "",
    val cpuPercent: Double = 0.0,
    val memoryMb: Int = 0,
    val bitrateKbps: Int = 0,
    val droppedFrames: Int = 0,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "userId" to userId,
        "scheduleId" to scheduleId.take(128).ifBlank { "system" },
        "jobType" to jobType.take(60).ifBlank { "RTMPS_STREAM" },
        "status" to status,
        "attemptNumber" to attemptNumber.coerceIn(1, 10),
        "message" to message.take(400),
        "cpuPercent" to cpuPercent.coerceIn(0.0, 100.0),
        "memoryMb" to memoryMb.coerceIn(0, 65536),
        "bitrateKbps" to bitrateKbps.coerceIn(0, 50000),
        "droppedFrames" to droppedFrames.coerceAtLeast(0),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class SmartNotification(
    @DocumentId val id: String = "",
    val userId: String = "",
    val eventType: String = "INFO",
    val title: String = "",
    val body: String = "",
    val severity: String = "INFO",
    val readState: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "userId" to userId,
        "eventType" to eventType.take(64).ifBlank { "INFO" },
        "title" to title.take(120).ifBlank { "Notification" },
        "body" to body.take(500).ifBlank { "Update recorded." },
        "severity" to if (severity in listOf("INFO", "SUCCESS", "WARNING", "ERROR")) severity else "INFO",
        "readState" to readState,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class SecurityAuditLog(
    @DocumentId val id: String = "",
    val userId: String = "",
    val eventType: String = "",
    val deviceName: String = "",
    val status: String = "SUCCESS",
    val details: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "userId" to userId,
        "eventType" to eventType.take(64).ifBlank { "AUTH_EVENT" },
        "deviceName" to deviceName.take(120).ifBlank { "Android Device" },
        "status" to status.take(40).ifBlank { "SUCCESS" },
        "details" to details.take(400),
        "createdAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class BackupRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val label: String = "",
    val videoCount: Int = 0,
    val scheduleCount: Int = 0,
    val playlistCount: Int = 0,
    val retentionDays: Int = 30,
    val snapshotSummary: String = "",
    val settingsJson: String? = null,
    val schedulesJson: String? = null,
    val checksumSha256: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "userId" to userId,
        "label" to label.take(120).ifBlank { "Metadata Snapshot" },
        "videoCount" to videoCount.coerceAtLeast(0),
        "scheduleCount" to scheduleCount.coerceAtLeast(0),
        "playlistCount" to playlistCount.coerceAtLeast(0),
        "retentionDays" to retentionDays.coerceIn(1, 3650),
        "snapshotSummary" to snapshotSummary.take(2000),
        "settingsJson" to settingsJson?.take(8000)?.takeIf { it.isNotBlank() },
        "schedulesJson" to schedulesJson?.take(32000)?.takeIf { it.isNotBlank() },
        "checksumSha256" to checksumSha256?.take(128)?.takeIf { it.isNotBlank() },
        "createdAt" to FieldValue.serverTimestamp()
    ).filterValues { it != null } as Map<String, Any>
}

@Keep
@IgnoreExtraProperties
data class TeamMember(
    @DocumentId val id: String = "",
    val userId: String = "",
    val memberName: String = "",
    val memberEmail: String = "",
    val role: String = "VIEWER",
    val activeMember: Boolean = true,
    val addedBy: String = "Harsh Singh",
    val notes: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "userId" to userId,
        "memberName" to memberName.take(80).ifBlank { "Team Member" },
        "memberEmail" to memberEmail.take(150).ifBlank { "member@example.com" },
        "role" to TeamRole.fromRaw(role).name,
        "activeMember" to activeMember,
        "addedBy" to addedBy.take(120).ifBlank { "Harsh Singh" },
        "notes" to notes?.take(300)?.takeIf { it.isNotBlank() },
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    ).filterValues { it != null } as Map<String, Any>
}

@Keep
@IgnoreExtraProperties
data class ActivityLog(
    @DocumentId val id: String = "",
    val userId: String = "",
    val category: String = "ACCOUNT",
    val action: String = "",
    val actorName: String = "Harsh Singh",
    val actorRole: String = "OWNER",
    val details: String = "",
    val timestampEpochMs: Long = System.currentTimeMillis(),
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "userId" to userId,
        "category" to if (category in listOf(
                "AUTH", "UPLOAD", "LIVE", "ACCOUNT", "TEAM", "MEDIA", "BACKUP", "SECURITY", "CLOUD", "COPYRIGHT"
            )
        ) category else "ACCOUNT",
        "action" to action.take(120).ifBlank { "Action" },
        "actorName" to actorName.take(80).ifBlank { "Harsh Singh" },
        "actorRole" to if (actorRole in listOf("OWNER", "EDITOR", "VIEWER", "SYSTEM")) actorRole else "OWNER",
        "details" to details.take(500),
        "timestampEpochMs" to timestampEpochMs.coerceAtLeast(1L),
        "createdAt" to FieldValue.serverTimestamp()
    )
}

// --- V3.0 UI & API Domain Models ---

data class CountdownState(
    val schedule: LiveSchedule,
    val remainingMs: Long,
    val days: Long,
    val hours: Long,
    val minutes: Long,
    val seconds: Long
)

data class LiveChatMessage(
    val id: String,
    val authorChannelId: String,
    val authorName: String,
    val authorAvatarUrl: String?,
    val messageText: String,
    val publishedAt: String,
    val isModerator: Boolean = false,
    val isOwner: Boolean = false
)

data class ChannelViewerStats(
    val concurrentViewers: Long? = null,
    val activeLiveChatId: String? = null,
    val statusText: String = "Unavailable"
)

data class CommentThreadItem(
    val threadId: String,
    val topCommentId: String,
    val videoId: String,
    val authorName: String,
    val authorAvatarUrl: String?,
    val text: String,
    val likeCount: Long,
    val replyCount: Long,
    val publishedAt: String,
    val updatedAt: String,
    val canReply: Boolean = true,
    val isUnread: Boolean = false
)

data class ChannelBrandingInfo(
    val channelId: String = "",
    val title: String = "",
    val description: String = "",
    val keywords: String = "",
    val profileImageUrl: String? = null,
    val bannerImageUrl: String? = null,
    val statusMessage: String = "Connect YouTube OAuth 2.0 in Settings to load channel branding."
)

data class ComparedVideoMetric(
    val videoId: String,
    val title: String,
    val thumbnailUrl: String?,
    val publishedAt: String,
    val viewCount: Long,
    val likeCount: Long,
    val commentCount: Long,
    val watchTimeMinutes: Long? = null
)

data class RevenueDayPoint(
    val date: String,
    val estimatedRevenueUsd: Double,
    val adRevenueUsd: Double,
    val monetizedPlaybacks: Long
)

data class RevenueSnapshot(
    val authorized: Boolean = false,
    val dateRangeDays: Int = 28,
    val estimatedRevenueUsd: Double? = null,
    val adRevenueUsd: Double? = null,
    val redPartnerRevenueUsd: Double? = null,
    val cpmUsd: Double? = null,
    val monetizedPlaybacks: Long? = null,
    val dailyPoints: List<RevenueDayPoint> = emptyList(),
    val statusMessage: String = "Requires YouTube Partner Program monetization & yt-analytics-monetary.readonly OAuth scope."
)

data class AudioAnalysisReport(
    val durationMs: Long,
    val sampleRateHz: Int,
    val channelCount: Int,
    val peakDb: Double,
    val rmsDb: Double,
    val recommendedGainLinear: Float,
    val issues: List<String>,
    val summary: String
)

data class SeoAnalysisPack(
    val scoreOutOf100: Int,
    val titleFeedback: String,
    val descriptionFeedback: String,
    val keywords: List<String>,
    val suggestedTitles: List<String>,
    val suggestedDescription: String,
    val hashtags: List<String>,
    val recommendations: List<String>,
    val disclaimer: String
)

data class TranscriptSegment(
    val startTimestamp: String,
    val endTimestamp: String,
    val text: String
)

data class TranscriptResult(
    val language: String,
    val fullTranscript: String,
    val segments: List<TranscriptSegment>
)

data class VideoChapter(
    val timestamp: String,
    val title: String
)

data class RepurposeSuggestion(
    val startSec: Long,
    val endSec: Long,
    val reason: String,
    val suggestedShortsTitle: String,
    val suggestedShortsDescription: String
)

data class StorageAlertReport(
    val usedBytes: Long,
    val usedGb: Double,
    val quotaGb: Double,
    val availableGb: Double,
    val usagePercent: Double,
    val alert80Triggered: Boolean,
    val alert90Triggered: Boolean
)

data class YouTubeAnalyticsSnapshot(
    val authorized: Boolean = false,
    val dateRangeDays: Int = 28,
    val views: Long? = null,
    val watchTimeMinutes: Long? = null,
    val subscribersGained: Long? = null,
    val averageViewDurationSec: Long? = null,
    val likes: Long? = null,
    val comments: Long? = null,
    val statusMessage: String = "Connect and authorize YouTube Analytics scope to load real metrics."
)

data class CloudCostBreakdown(
    val storageGb: Double,
    val transcodedMinutes: Double,
    val streamingHours: Double,
    val storageCostUsd: Double,
    val transcodingCostUsd: Double,
    val streamingCostUsd: Double,
    val egressCostUsd: Double,
    val totalMonthlyUsd: Double,
    val costPerStreamUsd: Double,
    val budgetLimitUsd: Double,
    val budgetExceeded: Boolean
)

data class AiTitlePack(
    val titles: List<String>,
    val description: String,
    val shortPromo: String,
    val tags: List<String>,
    val hashtags: List<String>,
    val category: String
)

data class VideoQualityReport(
    val width: Int,
    val height: Int,
    val fps: Int,
    val durationSec: Long,
    val fileSizeBytes: Long,
    val isGenuine1080p: Boolean,
    val warningMessage: String?,
    val isValidMp4: Boolean = true,
    val validationError: String? = null
)

// --- V4.1 Firestore Models (Competitors, Active Sessions, AI Copyright Risk Reports) ---

@Keep
@IgnoreExtraProperties
data class CompetitorChannel(
    @DocumentId val id: String = "",
    val userId: String = "",
    val channelId: String = "",
    val title: String = "",
    val handle: String? = null,
    val thumbnailUrl: String? = null,
    val subscriberCount: Long = 0L,
    val viewCount: Long = 0L,
    val videoCount: Long = 0L,
    val lastSyncedEpochMs: Long = System.currentTimeMillis(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "userId" to userId,
        "channelId" to channelId.take(128).ifBlank { "UC_UNKNOWN" },
        "title" to title.take(120).ifBlank { "Public Channel" },
        "handle" to handle?.take(80)?.takeIf { it.isNotBlank() },
        "thumbnailUrl" to thumbnailUrl?.take(500)?.takeIf { it.isNotBlank() },
        "subscriberCount" to subscriberCount.coerceAtLeast(0L),
        "viewCount" to viewCount.coerceAtLeast(0L),
        "videoCount" to videoCount.coerceAtLeast(0L),
        "lastSyncedEpochMs" to lastSyncedEpochMs.coerceAtLeast(1L),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    ).filterValues { it != null } as Map<String, Any>
}

@Keep
@IgnoreExtraProperties
data class UserSession(
    @DocumentId val id: String = "",
    val userId: String = "",
    val deviceName: String = "",
    val platform: String = "Android",
    val sessionTokenHash: String = "",
    val lastActiveEpochMs: Long = System.currentTimeMillis(),
    val revoked: Boolean = false,
    val trustedDevice: Boolean = true,
    val networkHint: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "userId" to userId,
        "deviceName" to deviceName.take(120).ifBlank { "Android Device" },
        "platform" to platform.take(60).ifBlank { "Android" },
        "sessionTokenHash" to sessionTokenHash.take(128).let { if (it.length < 8) it.padEnd(8, '0') else it },
        "lastActiveEpochMs" to lastActiveEpochMs.coerceAtLeast(1L),
        "revoked" to revoked,
        "trustedDevice" to trustedDevice,
        "networkHint" to networkHint?.take(120)?.takeIf { it.isNotBlank() },
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    ).filterValues { it != null } as Map<String, Any>
}

@Keep
@IgnoreExtraProperties
data class CopyrightRiskReport(
    @DocumentId val id: String = "",
    val userId: String = "",
    val videoId: String = "",
    val videoTitle: String = "",
    val riskLevel: String = "LOW",
    val summary: String = "",
    val triggers: List<String> = emptyList(),
    val mitigations: List<String> = emptyList(),
    val policyFlags: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "userId" to userId,
        "videoId" to videoId.take(128).ifBlank { "manual" },
        "videoTitle" to videoTitle.take(150).ifBlank { "Untitled Video" },
        "riskLevel" to if (riskLevel in listOf("LOW", "MEDIUM", "HIGH")) riskLevel else "LOW",
        "summary" to summary.take(1500).ifBlank { "AI copyright pre-check complete." },
        "triggers" to triggers.take(15),
        "mitigations" to mitigations.take(15),
        "policyFlags" to policyFlags.take(15),
        "createdAt" to FieldValue.serverTimestamp()
    )
}

// --- V4.1 UI & API Domain Models ---

data class VideoPerformanceDetail(
    val videoId: String,
    val title: String,
    val publishedAt: String,
    val thumbnailUrl: String?,
    val privacyStatus: String,
    val uploadStatus: String,
    val madeForKids: Boolean,
    val license: String,
    val rejectionReason: String?,
    val viewCount: Long,
    val likeCount: Long,
    val commentCount: Long,
    val engagementRatePercent: Double,
    val watchTimeMinutes: Long? = null,
    val policyAlerts: List<String> = emptyList()
)

data class YouTubePolicyAlert(
    val severity: String,
    val source: String,
    val message: String,
    val actionHint: String
)

data class StreamHealthReport(
    val broadcastId: String,
    val lifeCycleStatus: String,
    val healthStatus: String,
    val configIssues: List<String> = emptyList(),
    val checkedEpochMs: Long = System.currentTimeMillis()
)

data class ApiUsageReport(
    val unitsUsedToday: Int,
    val dailyQuotaLimit: Int,
    val geminiCallsSession: Int,
    val usagePercent: Double,
    val warning80Triggered: Boolean,
    val quotaResetDate: String
)

data class BillingLineItem(
    val label: String,
    val amountUsd: Double
)

data class ConnectedBillingReport(
    val connected: Boolean = false,
    val currency: String = "USD",
    val totalMonthlyUsd: Double? = null,
    val billingPeriod: String? = null,
    val source: String = "Unconfigured",
    val lineItems: List<BillingLineItem> = emptyList(),
    val statusMessage: String = "Billing API Not Connected. Connect GCP/Cloud Billing JSON feed or Worker /api/billing in Settings."
)

data class SecurityAlertSummary(
    val twoFactorVerified: Boolean,
    val twoFactorReminderDue: Boolean,
    val unknownDeviceDetected: Boolean,
    val currentDeviceName: String,
    val activeSessionCount: Int,
    val revokedSessionDetected: Boolean
)

// --- V20.0 Setup Readiness Checklist Domain Models ---

data class SetupChecklistItem(
    val key: String,
    val title: String,
    val detail: String,
    val completed: Boolean,
    val requiredForLive: Boolean = true
)

data class StreamSetupStatus(
    val items: List<SetupChecklistItem> = emptyList(),
    val readyForRealLive: Boolean = false
)

data class AiMusicTrack(
    val title: String,
    val genre: String,
    val durationSec: Int,
    val arrangementSummary: String,
    val chords: String,
    val modelName: String
)

