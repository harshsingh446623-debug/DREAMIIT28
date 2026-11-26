package com.example.ui.viewmodel

import com.example.data.model.ApiUsageReport
import com.example.data.model.BackupRecord
import com.example.data.model.CloudCostBreakdown
import com.example.data.model.CloudVideo
import com.example.data.model.CountdownState
import com.example.data.model.LiveSchedule
import com.example.data.model.ScheduleStatus
import com.example.data.model.SetupChecklistItem
import com.example.data.model.StorageAlertReport
import com.example.data.model.StreamHealthReport
import com.example.data.model.StreamSetupStatus
import com.example.data.model.TeamRole
import com.example.data.model.UploadSchedule
import com.example.data.model.UploadStatus
import com.example.data.model.UserSettings
import com.example.data.network.WorkerHealthResponse
import kotlin.math.abs
import kotlin.math.min

object DreamiitStudioHelpers {

    fun computeNextCountdown(
        schedules: List<LiveSchedule>,
        nowMs: Long
    ): CountdownState? {
        val next = schedules
            .filter {
                (it.status == ScheduleStatus.SCHEDULED.name || it.status == ScheduleStatus.PREPARING.name) &&
                    it.scheduledStartEpochMs > nowMs
            }
            .minByOrNull { it.scheduledStartEpochMs } ?: return null

        val diff = (next.scheduledStartEpochMs - nowMs).coerceAtLeast(0L)
        val totalSeconds = diff / 1000L
        val days = totalSeconds / 86_400L
        val hours = (totalSeconds % 86_400L) / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return CountdownState(
            schedule = next,
            remainingMs = diff,
            days = days,
            hours = hours,
            minutes = minutes,
            seconds = seconds
        )
    }

    fun detectScheduleConflict(
        channelId: String,
        targetEpochMs: Long,
        liveSchedules: List<LiveSchedule>,
        uploadSchedules: List<UploadSchedule>,
        excludeLiveId: String? = null,
        excludeUploadId: String? = null,
        windowMinutes: Int = 15
    ): String? {
        val windowMs = windowMinutes * 60_000L
        val liveConflict = liveSchedules.firstOrNull {
            it.id != excludeLiveId &&
                it.channelId == channelId &&
                it.status in listOf("SCHEDULED", "PREPARING", "LIVE") &&
                abs(it.scheduledStartEpochMs - targetEpochMs) < windowMs
        }
        if (liveConflict != null) {
            return "Conflict with Live Broadcast '${liveConflict.liveTitle}' within $windowMinutes minutes."
        }
        val uploadConflict = uploadSchedules.firstOrNull {
            it.id != excludeUploadId &&
                it.channelId == channelId &&
                it.status in listOf("SCHEDULED", "UPLOADING") &&
                abs(it.scheduledPublishEpochMs - targetEpochMs) < windowMs
        }
        if (uploadConflict != null) {
            return "Conflict with Scheduled Upload '${uploadConflict.title}' within $windowMinutes minutes."
        }
        return null
    }

    fun computeStorageAlertReport(
        videos: List<CloudVideo>,
        quotaGb: Double
    ): StorageAlertReport {
        val safeQuota = quotaGb.coerceIn(1.0, 5000.0)
        val usedBytes = videos.sumOf { it.fileSizeBytes.coerceAtLeast(0L) }
        val usedGb = usedBytes / (1024.0 * 1024.0 * 1024.0)
        val availableGb = (safeQuota - usedGb).coerceAtLeast(0.0)
        val pct = ((usedGb / safeQuota) * 100.0).coerceIn(0.0, 999.0)
        return StorageAlertReport(
            usedBytes = usedBytes,
            usedGb = usedGb,
            quotaGb = safeQuota,
            availableGb = availableGb,
            usagePercent = pct,
            alert80Triggered = pct >= 80.0,
            alert90Triggered = pct >= 90.0
        )
    }

    fun computeCostBreakdown(
        videos: List<CloudVideo>,
        schedules: List<LiveSchedule>,
        settings: UserSettings?
    ): CloudCostBreakdown {
        val storageBytes = videos.sumOf { it.fileSizeBytes }
        val storageGb = storageBytes / (1024.0 * 1024.0 * 1024.0)
        val totalVideoMinutes = videos.sumOf { it.durationSec } / 60.0
        val completedOrLiveSchedules = schedules.filter {
            it.status == ScheduleStatus.COMPLETED.name || it.status == ScheduleStatus.LIVE.name
        }
        val streamingHours = completedOrLiveSchedules.sumOf { s ->
            val mins = s.maxDurationMinutes ?: settings?.defaultLiveDurationMin ?: 60
            mins.toDouble() / 60.0
        }
        val completedOrLiveStreams = completedOrLiveSchedules.size

        val storageCost = storageGb * 0.020
        val transcodeCost = totalVideoMinutes * 0.015
        val streamCost = streamingHours * 0.12
        val egressCost = streamingHours * 2.7 * 0.08
        val total = storageCost + transcodeCost + streamCost + egressCost
        val perStream = if (completedOrLiveStreams > 0) total / completedOrLiveStreams else 0.34
        val budget = settings?.monthlyBudgetUsd ?: 50.0

        return CloudCostBreakdown(
            storageGb = storageGb,
            transcodedMinutes = totalVideoMinutes,
            streamingHours = streamingHours,
            storageCostUsd = storageCost,
            transcodingCostUsd = transcodeCost,
            streamingCostUsd = streamCost,
            egressCostUsd = egressCost,
            totalMonthlyUsd = total,
            costPerStreamUsd = perStream,
            budgetLimitUsd = budget,
            budgetExceeded = total > budget
        )
    }

    fun isAutoBackupDue(
        frequency: String?,
        backups: List<BackupRecord>,
        nowMs: Long
    ): Boolean {
        val freq = frequency ?: "OFF"
        if (freq == "OFF") return false
        val intervalMs = when (freq) {
            "DAILY" -> 24 * 60 * 60 * 1000L
            "WEEKLY" -> 7 * 24 * 60 * 60 * 1000L
            else -> return false
        }
        val latestMs = backups.maxOfOrNull { it.createdAt?.toDate()?.time ?: 0L } ?: 0L
        if (latestMs == 0L) return true
        return (nowMs - latestMs) >= intervalMs
    }

    fun canPerformAction(activeRole: TeamRole, requiresOwner: Boolean, requiresEditor: Boolean): Boolean {
        return when {
            requiresOwner -> activeRole.canManageSensitiveOps()
            requiresEditor -> activeRole.canMutateContent()
            else -> true
        }
    }

    // --- V4.1 Helpers ---

    fun computeEngagementRatePercent(views: Long, likes: Long, comments: Long): Double {
        if (views <= 0L) return 0.0
        val interactions = (likes.coerceAtLeast(0L) + comments.coerceAtLeast(0L)).toDouble()
        return ((interactions / views.toDouble()) * 100.0).coerceIn(0.0, 100.0)
    }

    fun computeApiUsageReport(
        unitsUsed: Int,
        dailyLimit: Int,
        geminiCalls: Int,
        resetDate: String
    ): ApiUsageReport {
        val safeLimit = dailyLimit.coerceIn(100, 10_000_000)
        val safeUsed = unitsUsed.coerceAtLeast(0)
        val pct = ((safeUsed.toDouble() / safeLimit.toDouble()) * 100.0).coerceIn(0.0, 999.0)
        return ApiUsageReport(
            unitsUsedToday = safeUsed,
            dailyQuotaLimit = safeLimit,
            geminiCallsSession = geminiCalls.coerceAtLeast(0),
            usagePercent = pct,
            warning80Triggered = pct >= 80.0,
            quotaResetDate = resetDate.ifBlank { "UTC-Today" }
        )
    }

    fun isUnknownDevice(currentDevice: String, knownDevices: List<String>?): Boolean {
        if (currentDevice.isBlank()) return false
        if (knownDevices.isNullOrEmpty()) return false
        return knownDevices.none { it.equals(currentDevice.trim(), ignoreCase = true) }
    }

    fun isTwoStepReminderDue(
        twoFactorConfirmed: Boolean?,
        remindedAtMs: Long?,
        nowMs: Long
    ): Boolean {
        if (twoFactorConfirmed == true) return false
        val lastReminded = remindedAtMs ?: 0L
        if (lastReminded <= 0L) return true
        val sevenDaysMs = 7L * 24L * 60L * 60L * 1000L
        return (nowMs - lastReminded) >= sevenDaysMs
    }

    fun shouldAutoRetryStream(
        schedule: LiveSchedule,
        autoRetryEnabled: Boolean,
        autoRecoveryEnabled: Boolean
    ): Boolean {
        if (!autoRetryEnabled || !autoRecoveryEnabled) return false
        if (schedule.status != ScheduleStatus.FAILED.name) return false
        return schedule.retryCount < schedule.maxRetries
    }

    fun collectCleanupCandidates(
        videos: List<CloudVideo>,
        schedules: List<LiveSchedule>
    ): List<CloudVideo> {
        val activeVideoIds = schedules
            .filter {
                it.status == ScheduleStatus.SCHEDULED.name ||
                    it.status == ScheduleStatus.PREPARING.name ||
                    it.status == ScheduleStatus.LIVE.name
            }
            .map { it.videoId }
            .toSet()

        return videos.filter { v ->
            v.id !in activeVideoIds && (
                v.uploadStatus == UploadStatus.FAILED.name ||
                    (v.uploadStatus == UploadStatus.READY.name && v.youtubeVideoId.isNullOrBlank()) ||
                    (v.uploadStatus == UploadStatus.QUEUED.name && v.youtubeVideoId.isNullOrBlank())
                )
        }
    }

    fun evaluateLiveConnectionAlert(
        activeLive: LiveSchedule?,
        workerHealth: WorkerHealthResponse,
        ytStreamHealth: StreamHealthReport?
    ): String? {
        if (activeLive == null) return null
        if (!workerHealth.reachable) {
            return "Live connection warning: Cloud Worker is ${workerHealth.status}. Active stream '${activeLive.liveTitle}' cannot be monitored remotely."
        }
        if (ytStreamHealth != null && ytStreamHealth.healthStatus.equals("bad", ignoreCase = true)) {
            val detail = ytStreamHealth.configIssues.firstOrNull() ?: "YouTube reports bad ingest stream health."
            return "YouTube Stream Health Alert: $detail"
        }
        if (ytStreamHealth != null && ytStreamHealth.healthStatus.equals("revoked", ignoreCase = true)) {
            return "YouTube Stream Key revoked during live broadcast."
        }
        if (activeLive.retryCount > 0 && activeLive.status == ScheduleStatus.LIVE.name) {
            return "Stream recovered after ${activeLive.retryCount} automatic retry attempt(s)."
        }
        return null
    }

    // --- V20.0 Live Streaming Engine Helpers (Pure JVM) ---

    fun clampLiveDurationMinutes(minutes: Int): Int = minutes.coerceIn(60, 180)

    fun computeRemainingLiveSec(maxDurationMinutes: Int, elapsedSec: Long): Long {
        val totalSec = clampLiveDurationMinutes(maxDurationMinutes) * 60L
        return (totalSec - elapsedSec.coerceAtLeast(0L)).coerceAtLeast(0L)
    }

    fun computeExponentialBackoffMs(attempt: Int): Long {
        val safeAttempt = attempt.coerceIn(0, 10)
        return min(300_000L, 5_000L shl safeAttempt)
    }

    fun computeResumePlaybackSec(
        lastSafeSec: Long,
        videoDurationSec: Long,
        repeatPlayback: Boolean
    ): Long {
        val safe = lastSafeSec.coerceAtLeast(0L)
        if (safe == 0L) return 0L
        if (videoDurationSec <= 0L) return safe
        return if (repeatPlayback) {
            safe % videoDurationSec
        } else {
            safe.coerceAtMost((videoDurationSec - 2L).coerceAtLeast(0L))
        }
    }

    fun maskStreamKeyUrl(rawUrl: String): String {
        val trimmed = rawUrl.trim()
        if (trimmed.isEmpty()) return ""
        val slash = trimmed.lastIndexOf('/')
        if (slash <= 0 || slash >= trimmed.length - 1) return "••••"
        val prefix = trimmed.substring(0, slash + 1)
        val key = trimmed.substring(slash + 1)
        if (key.contains("•")) return trimmed
        val tail = if (key.length > 4) key.takeLast(4) else "****"
        return "${prefix}••••$tail"
    }

    fun validateMp4Metadata(
        fileName: String,
        mimeType: String?,
        fileSizeBytes: Long,
        durationSec: Long
    ): Pair<Boolean, String?> {
        if (fileSizeBytes <= 0L) {
            return false to "Video file is empty (0 bytes)."
        }
        val maxBytes = 128L * 1024L * 1024L * 1024L
        if (fileSizeBytes > maxBytes) {
            return false to "Video exceeds YouTube's 128 GB file limit."
        }
        val cleanName = fileName.trim().lowercase()
        val cleanMime = mimeType?.trim()?.lowercase().orEmpty()
        val validExt = cleanName.endsWith(".mp4")
        val validMime = cleanMime.isBlank() || cleanMime == "video/mp4" || cleanMime == "application/mp4"
        if (!validExt || !validMime) {
            return false to "Only valid MP4 video files (.mp4 / video/mp4) are accepted in V20.0."
        }
        if (durationSec <= 0L) {
            return false to "Invalid or zero video duration."
        }
        return true to null
    }

    fun isDuplicateStreamBlocked(
        targetScheduleId: String,
        schedules: List<LiveSchedule>
    ): Boolean = schedules.any {
        it.id != targetScheduleId &&
            (it.status == ScheduleStatus.LIVE.name || it.status == ScheduleStatus.PREPARING.name)
    }

    fun isConfirmedYouTubeLive(lifeCycleStatus: String?): Boolean =
        lifeCycleStatus.equals("live", ignoreCase = true)

    fun evaluateSetupChecklist(
        isGoogleSignedIn: Boolean,
        hasYouTubeToken: Boolean,
        hasActiveChannel: Boolean,
        workerBaseUrl: String?,
        workerReachable: Boolean,
        infraCostApproved: Boolean?
    ): StreamSetupStatus {
        val workerConfigured = !workerBaseUrl.isNullOrBlank()
        val items = listOf(
            SetupChecklistItem(
                key = "google_auth",
                title = "Google Account Signed In",
                detail = if (isGoogleSignedIn) "Authenticated via Credential Manager." else "Sign in with Google.",
                completed = isGoogleSignedIn
            ),
            SetupChecklistItem(
                key = "youtube_oauth",
                title = "YouTube Live API v3 OAuth Connected",
                detail = if (hasYouTubeToken) "OAuth 2.0 access token ready." else "Authorize YouTube in Settings -> OAuth 2.0.",
                completed = hasYouTubeToken
            ),
            SetupChecklistItem(
                key = "active_channel",
                title = "Active YouTube Channel Selected",
                detail = if (hasActiveChannel) "Target channel bound." else "Sync and select a YouTube channel.",
                completed = hasActiveChannel
            ),
            SetupChecklistItem(
                key = "cloud_worker",
                title = "Cloud Streaming Worker Reachable",
                detail = when {
                    !workerConfigured -> "Set Cloud Worker HTTPS URL in Settings."
                    workerReachable -> "Worker /api/health ONLINE."
                    else -> "Configured worker URL is unreachable. Verify server status."
                },
                completed = workerConfigured && workerReachable
            ),
            SetupChecklistItem(
                key = "infra_approval",
                title = "Administrator Infrastructure Approval",
                detail = if (infraCostApproved == true) "Approved by Harsh Singh." else "Confirm cloud compute/egress approval before streaming.",
                completed = infraCostApproved == true
            )
        )
        return StreamSetupStatus(
            items = items,
            readyForRealLive = items.filter { it.requiredForLive }.all { it.completed }
        )
    }
}
