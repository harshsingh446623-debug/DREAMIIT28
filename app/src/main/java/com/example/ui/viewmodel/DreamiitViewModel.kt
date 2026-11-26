package com.example.ui.viewmodel

import android.content.Context
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.R
import com.example.data.ai.DreamiitAiRepository
import com.example.data.firestore.DreamiitRepository
import com.example.data.local.OAuthCredentials
import com.example.data.local.OAuthTokenStore
import com.example.data.model.ActivityLog
import com.example.data.model.ApiUsageReport
import com.example.data.model.AudioAnalysisReport
import com.example.data.model.BackupRecord
import com.example.data.model.ChannelBrandingInfo
import com.example.data.model.ChannelViewerStats
import com.example.data.model.CloudCostBreakdown
import com.example.data.model.CloudVideo
import com.example.data.model.CommentThreadItem
import com.example.data.model.ComparedVideoMetric
import com.example.data.model.CompetitorChannel
import com.example.data.model.ConnectedBillingReport
import com.example.data.model.CopyrightRiskReport
import com.example.data.model.CountdownState
import com.example.data.model.LiveChatMessage
import com.example.data.model.LivePlaylist
import com.example.data.model.LiveSchedule
import com.example.data.model.PrivacyStatus
import com.example.data.model.RevenueSnapshot
import com.example.data.model.ScheduleRecurrence
import com.example.data.model.ScheduleStatus
import com.example.data.model.SecurityAlertSummary
import com.example.data.model.SecurityAuditLog
import com.example.data.model.SetupChecklistItem
import com.example.data.model.SmartNotification
import com.example.data.model.StorageAlertReport
import com.example.data.model.StreamHealthReport
import com.example.data.model.StreamSetupStatus
import com.example.data.model.StreamingProfile
import com.example.data.model.TeamMember
import com.example.data.model.TeamRole
import com.example.data.model.UploadSchedule
import com.example.data.model.UploadScheduleStatus
import com.example.data.model.UserSession
import com.example.data.model.UserSettings
import com.example.data.model.VideoPerformanceDetail
import com.example.data.model.VideoQualityReport
import com.example.data.model.WorkerJob
import com.example.data.model.YouTubeAnalyticsSnapshot
import com.example.data.model.YouTubeChannel
import com.example.data.model.YouTubePolicyAlert
import com.example.data.network.CloudWorkerClient
import com.example.data.network.WorkerHealthResponse
import com.example.data.network.WorkerStreamStatus
import com.example.data.network.YouTubeApiClient
import com.example.media.MediaEditorEngine
import com.example.media.MediaInspector
import com.example.media.NotificationHelper
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

sealed interface DataState<out T> {
    data object Loading : DataState<Nothing>
    data class Success<T>(val data: T) : DataState<T>
    data class Error(val message: String) : DataState<Nothing>
}

data class UploadProgressState(
    val active: Boolean = false,
    val progress: Float = 0f,
    val statusText: String = "",
    val error: String? = null
)

data class MediaLabState(
    val active: Boolean = false,
    val progress: Float = 0f,
    val statusText: String = "",
    val audioReport: AudioAnalysisReport? = null,
    val lastExportedUri: Uri? = null,
    val lastExportedLabel: String? = null,
    val error: String? = null
)

class DreamiitViewModel(
    private val repository: DreamiitRepository,
    private val oauthStore: OAuthTokenStore,
    private val youtubeClient: YouTubeApiClient,
    private val workerClient: CloudWorkerClient,
    private val appContext: Context,
    val currentUserId: String
) : ViewModel() {

    private val _bannerMessage = MutableStateFlow<String?>(null)
    val bannerMessage: StateFlow<String?> = _bannerMessage.asStateFlow()

    private val _uploadState = MutableStateFlow(UploadProgressState())
    val uploadState: StateFlow<UploadProgressState> = _uploadState.asStateFlow()

    private val _mediaLabState = MutableStateFlow(MediaLabState())
    val mediaLabState: StateFlow<MediaLabState> = _mediaLabState.asStateFlow()

    private val _analyticsState = MutableStateFlow(YouTubeAnalyticsSnapshot())
    val analyticsState: StateFlow<YouTubeAnalyticsSnapshot> = _analyticsState.asStateFlow()

    private val _revenueState = MutableStateFlow(RevenueSnapshot())
    val revenueState: StateFlow<RevenueSnapshot> = _revenueState.asStateFlow()

    private val _workerHealth = MutableStateFlow(WorkerHealthResponse())
    val workerHealth: StateFlow<WorkerHealthResponse> = _workerHealth.asStateFlow()

    private val _liveElapsedSec = MutableStateFlow(0L)
    val liveElapsedSec: StateFlow<Long> = _liveElapsedSec.asStateFlow()

    private val _countdownState = MutableStateFlow<CountdownState?>(null)
    val countdownState: StateFlow<CountdownState?> = _countdownState.asStateFlow()

    // Live Chat Monitor states (#7)
    private val _liveChatMessages = MutableStateFlow<List<LiveChatMessage>>(emptyList())
    val liveChatMessages: StateFlow<List<LiveChatMessage>> = _liveChatMessages.asStateFlow()
    private val _liveChatStats = MutableStateFlow(ChannelViewerStats())
    val liveChatStats: StateFlow<ChannelViewerStats> = _liveChatStats.asStateFlow()
    private var liveChatPollJob: Job? = null
    private var liveChatPageToken: String? = null

    // Comment Manager states (#8)
    private val _comments = MutableStateFlow<List<CommentThreadItem>>(emptyList())
    val comments: StateFlow<List<CommentThreadItem>> = _comments.asStateFlow()
    private val _commentsStatus = MutableStateFlow("Tap Refresh to load real comments from your YouTube channel.")
    val commentsStatus: StateFlow<String> = _commentsStatus.asStateFlow()
    private val _commentsLoading = MutableStateFlow(false)
    val commentsLoading: StateFlow<Boolean> = _commentsLoading.asStateFlow()

    // Channel Branding Manager (#9)
    private val _brandingInfo = MutableStateFlow(ChannelBrandingInfo())
    val brandingInfo: StateFlow<ChannelBrandingInfo> = _brandingInfo.asStateFlow()
    private val _brandingBusy = MutableStateFlow(false)
    val brandingBusy: StateFlow<Boolean> = _brandingBusy.asStateFlow()

    // Video Comparison (#10)
    private val _comparedVideos = MutableStateFlow<List<ComparedVideoMetric>>(emptyList())
    val comparedVideos: StateFlow<List<ComparedVideoMetric>> = _comparedVideos.asStateFlow()
    private val _comparisonStatus = MutableStateFlow("Load your YouTube videos to compare views, likes, comments & watch time.")
    val comparisonStatus: StateFlow<String> = _comparisonStatus.asStateFlow()

    // --- V4.1 Module 1, 2, 3, 4 StateFlows ---
    private val _videoPerformanceList = MutableStateFlow<List<VideoPerformanceDetail>>(emptyList())
    val videoPerformanceList: StateFlow<List<VideoPerformanceDetail>> = _videoPerformanceList.asStateFlow()
    private val _videoPerformanceStatus = MutableStateFlow("Tap Refresh to load real YouTube video analytics & policy checks.")
    val videoPerformanceStatus: StateFlow<String> = _videoPerformanceStatus.asStateFlow()

    private val _policyAlerts = MutableStateFlow<List<YouTubePolicyAlert>>(emptyList())
    val policyAlerts: StateFlow<List<YouTubePolicyAlert>> = _policyAlerts.asStateFlow()

    private val _ytStreamHealth = MutableStateFlow<StreamHealthReport?>(null)
    val ytStreamHealth: StateFlow<StreamHealthReport?> = _ytStreamHealth.asStateFlow()

    private val _connectedBilling = MutableStateFlow(ConnectedBillingReport())
    val connectedBilling: StateFlow<ConnectedBillingReport> = _connectedBilling.asStateFlow()

    private val _geminiCallsSession = MutableStateFlow(0)
    private val _competitorBusy = MutableStateFlow(false)
    val competitorBusy: StateFlow<Boolean> = _competitorBusy.asStateFlow()

    private val _copyrightScanBusy = MutableStateFlow(false)
    val copyrightScanBusy: StateFlow<Boolean> = _copyrightScanBusy.asStateFlow()

    // V20.0 Worker per-stream live telemetry & backoff tracking
    private val _workerStreamStatus = MutableStateFlow<WorkerStreamStatus?>(null)
    val workerStreamStatus: StateFlow<WorkerStreamStatus?> = _workerStreamStatus.asStateFlow()
    private val nextRetryAllowedAtMs = mutableMapOf<String, Long>()
    private var autoStoppedScheduleId: String? = null

    private val currentDeviceName: String = "${Build.MANUFACTURER} ${Build.MODEL}".trim().ifBlank { "Android Device" }
    val currentSessionId: String = java.security.MessageDigest.getInstance("SHA-256")
        .digest("$currentUserId|$currentDeviceName".toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
        .take(28)

    private val sentReminderKeys = mutableSetOf<String>()
    private val autoRetriedScheduleKeys = mutableSetOf<String>()
    private var lastAutoBackupCheckMs = 0L
    private var lastHealthPollMs = 0L
    private var lastSecurityCheckDeviceAlertSent = false

    val oauthCredentials: StateFlow<OAuthCredentials> = oauthStore.credentialsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), OAuthCredentials())

    private fun <T> Flow<T>.toDataState(label: String): StateFlow<DataState<T>> =
        map<T, DataState<T>> { DataState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing $label", rootError)
                emit(DataState.Error(rootError.message ?: "Failed to load $label"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = DataState.Loading
            )

    val settingsState: StateFlow<DataState<UserSettings?>> =
        repository.observeSettings(currentUserId).toDataState("settings")

    val channelsState: StateFlow<DataState<List<YouTubeChannel>>> =
        repository.observeChannels(currentUserId).toDataState("channels")

    val videosState: StateFlow<DataState<List<CloudVideo>>> =
        repository.observeVideos(currentUserId).toDataState("videos")

    val schedulesState: StateFlow<DataState<List<LiveSchedule>>> =
        repository.observeSchedules(currentUserId).toDataState("schedules")

    val uploadSchedulesState: StateFlow<DataState<List<UploadSchedule>>> =
        repository.observeUploadSchedules(currentUserId).toDataState("uploadSchedules")

    val playlistsState: StateFlow<DataState<List<LivePlaylist>>> =
        repository.observePlaylists(currentUserId).toDataState("playlists")

    val jobsState: StateFlow<DataState<List<WorkerJob>>> =
        repository.observeJobs(currentUserId).toDataState("jobs")

    val notificationsState: StateFlow<DataState<List<SmartNotification>>> =
        repository.observeNotifications(currentUserId).toDataState("notifications")

    val auditLogsState: StateFlow<DataState<List<SecurityAuditLog>>> =
        repository.observeAuditLogs(currentUserId).toDataState("auditLogs")

    val backupsState: StateFlow<DataState<List<BackupRecord>>> =
        repository.observeBackups(currentUserId).toDataState("backups")

    val teamMembersState: StateFlow<DataState<List<TeamMember>>> =
        repository.observeTeamMembers(currentUserId).toDataState("teamMembers")

    val activityLogsState: StateFlow<DataState<List<ActivityLog>>> =
        repository.observeActivityLogs(currentUserId).toDataState("activityLogs")

    val competitorsState: StateFlow<DataState<List<CompetitorChannel>>> =
        repository.observeCompetitors(currentUserId).toDataState("competitors")

    val sessionsState: StateFlow<DataState<List<UserSession>>> =
        repository.observeSessions(currentUserId).toDataState("sessions")

    val copyrightReportsState: StateFlow<DataState<List<CopyrightRiskReport>>> =
        repository.observeCopyrightReports(currentUserId).toDataState("copyrightReports")

    val activeRole: StateFlow<TeamRole> = settingsState.map { ds ->
        val s = (ds as? DataState.Success)?.data
        TeamRole.fromRaw(s?.activeRolePreview)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), TeamRole.OWNER)

    val storageAlertReport: StateFlow<StorageAlertReport> = combine(
        videosState,
        settingsState
    ) { videosDs, settingsDs ->
        val videos = (videosDs as? DataState.Success)?.data.orEmpty()
        val settings = (settingsDs as? DataState.Success)?.data
        DreamiitStudioHelpers.computeStorageAlertReport(videos, settings?.storageQuotaGb ?: 50.0)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000L),
        StorageAlertReport(0L, 0.0, 50.0, 50.0, 0.0, false, false)
    )

    val costBreakdown: StateFlow<CloudCostBreakdown> = combine(
        videosState,
        schedulesState,
        settingsState
    ) { videosDs, schedulesDs, settingsDs ->
        val videos = (videosDs as? DataState.Success)?.data.orEmpty()
        val schedules = (schedulesDs as? DataState.Success)?.data.orEmpty()
        val settings = (settingsDs as? DataState.Success)?.data
        DreamiitStudioHelpers.computeCostBreakdown(videos, schedules, settings)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = CloudCostBreakdown(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.34, 50.0, false)
    )

    val apiUsageReport: StateFlow<ApiUsageReport> = combine(
        settingsState,
        _geminiCallsSession
    ) { settingsDs, geminiCalls ->
        val s = (settingsDs as? DataState.Success)?.data
        val today = currentUtcDateString()
        val used = if (s?.apiQuotaResetDate == today) (s.apiUnitsUsedToday ?: 0) else 0
        DreamiitStudioHelpers.computeApiUsageReport(
            unitsUsed = used,
            dailyLimit = s?.apiQuotaDailyLimit ?: 10_000,
            geminiCalls = geminiCalls,
            resetDate = today
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000L),
        ApiUsageReport(0, 10_000, 0, 0.0, false, currentUtcDateString())
    )

    val liveConnectionAlert: StateFlow<String?> = combine(
        schedulesState,
        _workerHealth,
        _ytStreamHealth
    ) { schedulesDs, workerHealth, ytHealth ->
        val activeLive = (schedulesDs as? DataState.Success)?.data
            ?.firstOrNull { it.status == ScheduleStatus.LIVE.name }
        DreamiitStudioHelpers.evaluateLiveConnectionAlert(activeLive, workerHealth, ytHealth)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    val securityAlertSummary: StateFlow<SecurityAlertSummary> = combine(
        settingsState,
        sessionsState
    ) { settingsDs, sessionsDs ->
        val s = (settingsDs as? DataState.Success)?.data
        val sessions = (sessionsDs as? DataState.Success)?.data.orEmpty()
        val unknown = DreamiitStudioHelpers.isUnknownDevice(currentDeviceName, s?.knownDevices)
        val currentRevoked = sessions.firstOrNull { it.id == currentSessionId }?.revoked == true
        SecurityAlertSummary(
            twoFactorVerified = s?.twoFactorConfirmed == true,
            twoFactorReminderDue = DreamiitStudioHelpers.isTwoStepReminderDue(
                s?.twoFactorConfirmed,
                s?.twoFactorRemindedAtMs,
                System.currentTimeMillis()
            ),
            unknownDeviceDetected = unknown,
            currentDeviceName = currentDeviceName,
            activeSessionCount = sessions.count { !it.revoked },
            revokedSessionDetected = currentRevoked
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000L),
        SecurityAlertSummary(
            twoFactorVerified = false,
            twoFactorReminderDue = false,
            unknownDeviceDetected = false,
            currentDeviceName = currentDeviceName,
            activeSessionCount = 1,
            revokedSessionDetected = false
        )
    )

    val setupStatus: StateFlow<StreamSetupStatus> = combine(
        oauthCredentials,
        channelsState,
        settingsState,
        _workerHealth
    ) { creds, channelsDs, settingsDs, workerHealth ->
        val channels = (channelsDs as? DataState.Success)?.data.orEmpty()
        val settings = (settingsDs as? DataState.Success)?.data
        DreamiitStudioHelpers.evaluateSetupChecklist(
            isGoogleSignedIn = currentUserId.isNotBlank(),
            hasYouTubeToken = creds.hasValidToken || creds.refreshToken.isNotBlank(),
            hasActiveChannel = channels.any { it.activeChannel } || !settings?.activeChannelId.isNullOrBlank(),
            workerBaseUrl = settings?.workerBaseUrl,
            workerReachable = workerHealth.reachable,
            infraCostApproved = settings?.infraCostApproved
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), StreamSetupStatus())

    init {
        initializeUserSession()
        startStudioBackgroundLoops()
        observeStorageThresholds()
    }

    private fun currentUtcDateString(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }.format(Date())

    private fun initializeUserSession() {
        viewModelScope.launch {
            repository.ensureDefaultSettings(currentUserId, "Harsh Singh", currentDeviceName)
            val existingSettings = (settingsState.value as? DataState.Success)?.data
            val known = existingSettings?.knownDevices
            val isUnknown = if (known == null) {
                repository.updateSettings(mapOf("knownDevices" to listOf(currentDeviceName.take(120))))
                false
            } else {
                DreamiitStudioHelpers.isUnknownDevice(currentDeviceName, known)
            }

            repository.upsertSession(
                currentSessionId,
                UserSession(
                    userId = currentUserId,
                    deviceName = currentDeviceName,
                    platform = "Android ${Build.VERSION.RELEASE}",
                    sessionTokenHash = currentSessionId,
                    lastActiveEpochMs = System.currentTimeMillis(),
                    revoked = false,
                    trustedDevice = !isUnknown,
                    networkHint = "Google Credential Manager"
                )
            )

            repository.logAuditEvent(
                eventType = if (isUnknown) "UNKNOWN_DEVICE_LOGIN" else "SESSION_ACTIVE",
                deviceName = currentDeviceName,
                status = if (isUnknown) "WARNING" else "SUCCESS",
                details = if (isUnknown) {
                    "Unrecognized device '$currentDeviceName' signed in. Review Active Sessions."
                } else {
                    "Authenticated via Google Sign-In (Credential Manager)"
                }
            )
            repository.logActivity(
                category = "SECURITY",
                action = if (isUnknown) "Unknown Device Login Detected" else "Creator Studio Session Started",
                actorName = "Harsh Singh",
                actorRole = "OWNER",
                details = "Signed in on $currentDeviceName"
            )
            if (isUnknown && !lastSecurityCheckDeviceAlertSent) {
                lastSecurityCheckDeviceAlertSent = true
                recordEvent(
                    "UNKNOWN_LOGIN_ALERT",
                    "Unknown Login Device Detected",
                    "Signed in from unrecognized device '$currentDeviceName'. Trust device or revoke session in Security Center.",
                    "WARNING"
                )
            }
        }
    }

    private fun observeStorageThresholds() {
        viewModelScope.launch {
            storageAlertReport.collect { report ->
                val settings = (settingsState.value as? DataState.Success)?.data ?: return@collect
                if (report.alert90Triggered && settings.storageAlert90Sent != true) {
                    repository.updateSettings(mapOf("storageAlert90Sent" to true, "storageAlert80Sent" to true))
                    recordEvent(
                        "STORAGE_90_ALERT",
                        "Cloud Storage Critical (90%+)",
                        "Used ${String.format(Locale.US, "%.2f", report.usedGb)} GB of ${report.quotaGb.toInt()} GB quota. Clean up unused drafts or expand quota.",
                        "ERROR"
                    )
                } else if (report.alert80Triggered && settings.storageAlert80Sent != true) {
                    repository.updateSettings(mapOf("storageAlert80Sent" to true))
                    recordEvent(
                        "STORAGE_80_ALERT",
                        "Cloud Storage Warning (80%+)",
                        "Used ${String.format(Locale.US, "%.2f", report.usedGb)} GB of ${report.quotaGb.toInt()} GB quota.",
                        "WARNING"
                    )
                } else if (!report.alert80Triggered && (settings.storageAlert80Sent == true || settings.storageAlert90Sent == true)) {
                    repository.updateSettings(mapOf("storageAlert80Sent" to false, "storageAlert90Sent" to false))
                }
            }
        }
    }

    private fun startStudioBackgroundLoops() {
        viewModelScope.launch {
            var tickCounter = 0L
            while (true) {
                delay(1000L)
                tickCounter++
                val now = System.currentTimeMillis()
                val schedules = (schedulesState.value as? DataState.Success)?.data.orEmpty()

                // Live Elapsed Duration + 1-3h auto-stop ceiling
                val live = schedules.firstOrNull { it.status == ScheduleStatus.LIVE.name }
                if (live != null) {
                    if (live.pausedState != true) {
                        _liveElapsedSec.value += 1L
                    }
                    val maxMins = DreamiitStudioHelpers.clampLiveDurationMinutes(live.maxDurationMinutes ?: 60)
                    if (live.autoEndOnFinish && _liveElapsedSec.value >= maxMins * 60L && autoStoppedScheduleId != live.id) {
                        autoStoppedScheduleId = live.id
                        stopActiveLive(live, emergency = false)
                    }
                } else {
                    _liveElapsedSec.value = 0L
                    autoStoppedScheduleId = null
                }

                // Live Countdown Timer + Reminder Alerts (#1)
                val countdown = DreamiitStudioHelpers.computeNextCountdown(schedules, now)
                _countdownState.value = countdown
                if (countdown != null) {
                    val minsLeft = countdown.remainingMs / 60_000L
                    val key15 = "${countdown.schedule.id}_15m"
                    val key5 = "${countdown.schedule.id}_5m"
                    if (minsLeft in 6..15 && sentReminderKeys.add(key15)) {
                        recordEvent(
                            "LIVE_REMINDER_15M",
                            "Live Starts in ~${minsLeft}m",
                            "'${countdown.schedule.liveTitle}' begins soon at ${countdown.schedule.targetProfile}.",
                            "INFO"
                        )
                    } else if (minsLeft in 0..5 && sentReminderKeys.add(key5)) {
                        recordEvent(
                            "LIVE_REMINDER_5M",
                            "Live Starting Imminently (${minsLeft}m)",
                            "Cloud worker preparing '${countdown.schedule.liveTitle}' for RTMPS broadcast.",
                            "WARNING"
                        )
                    }
                }

                // Check due Automatic Video Uploads, Auto-Backups, Live Stream Health & Auto-Retry every 15s
                if (tickCounter % 15L == 0L) {
                    processDueScheduledUploads(now)
                    checkAndRunAutomaticBackup(now)
                    checkAutoStreamRetryAndRecovery(now, schedules)
                }
            }
        }
    }

    private suspend fun checkAutoStreamRetryAndRecovery(nowMs: Long, schedules: List<LiveSchedule>) {
        val settings = (settingsState.value as? DataState.Success)?.data ?: return
        val autoRetryEnabled = settings.autoStreamRetryEnabled != false
        val autoRecoveryEnabled = settings.autoRecoveryEnabled
        val bearerToken = oauthStore.getSnapshot().workerApiToken

        // Poll Cloud Worker + YouTube Live Health when configured or live/preparing
        if (nowMs - lastHealthPollMs >= 15_000L) {
            lastHealthPollMs = nowMs
            val workerUrl = settings.workerBaseUrl
            if (!workerUrl.isNullOrBlank()) {
                val health = workerClient.fetchHealth(workerUrl)
                _workerHealth.value = health
                if (!health.reachable && schedules.any { it.status == ScheduleStatus.LIVE.name }) {
                    recordEvent(
                        "LIVE_CONNECTION_ALERT",
                        "Live Connection Alert",
                        "Cloud Worker became unreachable (${health.status}) during active live broadcast.",
                        "ERROR"
                    )
                }
                val monitored = schedules.firstOrNull {
                    it.status == ScheduleStatus.LIVE.name || it.status == ScheduleStatus.PREPARING.name
                }
                if (monitored != null && health.reachable) {
                    workerClient.fetchStreamStatus(workerUrl, monitored.id, bearerToken)
                        .onSuccess { st ->
                            _workerStreamStatus.value = st
                            if (st.active) {
                                repository.updateSchedule(
                                    monitored.id,
                                    mapOf(
                                        "droppedFrames" to st.droppedFrames.coerceAtLeast(0),
                                        "currentBitrateKbps" to st.currentBitrateKbps.coerceIn(0, 50000),
                                        "lastSafePositionSec" to st.lastSafePositionSec.coerceIn(0L, 86400L),
                                        "pausedState" to st.paused,
                                        "recoveryState" to st.recoveryState.take(120)
                                    )
                                )
                            }
                        }
                } else {
                    _workerStreamStatus.value = null
                }
            }
            val activeOrPrep = schedules.firstOrNull {
                it.status == ScheduleStatus.LIVE.name || it.status == ScheduleStatus.PREPARING.name
            }
            if (activeOrPrep != null && (!activeOrPrep.youtubeBroadcastId.isNullOrBlank() || !activeOrPrep.youtubeStreamId.isNullOrBlank())) {
                val token = getValidAccessToken()
                if (token != null) {
                    youtubeClient.fetchLiveStreamHealth(token, activeOrPrep.youtubeBroadcastId, activeOrPrep.youtubeStreamId)
                        .onSuccess { report ->
                            _ytStreamHealth.value = report
                            if (activeOrPrep.status == ScheduleStatus.PREPARING.name &&
                                DreamiitStudioHelpers.isConfirmedYouTubeLive(report.lifeCycleStatus)
                            ) {
                                repository.updateSchedule(
                                    activeOrPrep.id,
                                    mapOf(
                                        "status" to ScheduleStatus.LIVE.name,
                                        "youtubeConfirmedLive" to true,
                                        "recoveryState" to "HEALTHY"
                                    )
                                )
                            }
                        }
                }
            }
        }

        // Auto Stream Retry & Recovery with Exponential Backoff for FAILED streams within maxRetries
        val failedCandidates = schedules.filter {
            DreamiitStudioHelpers.shouldAutoRetryStream(it, autoRetryEnabled, autoRecoveryEnabled)
        }
        for (failed in failedCandidates) {
            val allowedAt = nextRetryAllowedAtMs[failed.id] ?: 0L
            if (nowMs < allowedAt) continue
            val attemptKey = "${failed.id}_${failed.retryCount}"
            if (!autoRetriedScheduleKeys.add(attemptKey)) continue
            val nextRetry = failed.retryCount + 1
            val backoffMs = DreamiitStudioHelpers.computeExponentialBackoffMs(failed.retryCount)
            nextRetryAllowedAtMs[failed.id] = nowMs + backoffMs
            repository.updateSchedule(
                failed.id,
                mapOf(
                    "status" to ScheduleStatus.PREPARING.name,
                    "retryCount" to nextRetry,
                    "recoveryState" to "RECOVERING_BACKOFF_${backoffMs / 1000}S",
                    "lastError" to ""
                )
            )
            repository.logWorkerJob(
                WorkerJob(
                    userId = currentUserId,
                    scheduleId = failed.id,
                    jobType = "AUTO_STREAM_RETRY",
                    status = "RETRYING",
                    attemptNumber = nextRetry.coerceIn(1, 10),
                    message = "Automatic stream recovery attempt $nextRetry/${failed.maxRetries} (backoff ${backoffMs / 1000}s) for '${failed.liveTitle}'",
                    cpuPercent = 0.0,
                    memoryMb = 0,
                    bitrateKbps = StreamingProfile.fromId(failed.targetProfile).videoBitrateKbps,
                    droppedFrames = 0
                )
            )
            recordActivity("LIVE", "Auto Stream Recovery Triggered", "'${failed.liveTitle}' attempt $nextRetry/${failed.maxRetries}")
            recordEvent(
                "AUTO_STREAM_RETRY",
                "Automatic Stream Recovery",
                "Retrying '${failed.liveTitle}' automatically (attempt $nextRetry of ${failed.maxRetries}).",
                "WARNING"
            )
            startScheduledLiveNow(failed.copy(retryCount = nextRetry, status = ScheduleStatus.PREPARING.name))
        }
    }

    private suspend fun processDueScheduledUploads(nowMs: Long) {
        val scheduled = (uploadSchedulesState.value as? DataState.Success)?.data.orEmpty()
        val due = scheduled.filter {
            it.status == UploadScheduleStatus.SCHEDULED.name && it.scheduledPublishEpochMs <= nowMs
        }
        for (item in due) {
            executeScheduledUploadNow(item)
        }
    }

    private suspend fun checkAndRunAutomaticBackup(nowMs: Long) {
        if (nowMs - lastAutoBackupCheckMs < 60_000L) return
        lastAutoBackupCheckMs = nowMs
        val settings = (settingsState.value as? DataState.Success)?.data ?: return
        if (settings.autoBackupEnabled != true) return
        val backups = (backupsState.value as? DataState.Success)?.data.orEmpty()
        if (!DreamiitStudioHelpers.isAutoBackupDue(settings.autoBackupFrequency, backups, nowMs)) return

        val videos = (videosState.value as? DataState.Success)?.data.orEmpty()
        val schedules = (schedulesState.value as? DataState.Success)?.data.orEmpty()
        val playlists = (playlistsState.value as? DataState.Success)?.data.orEmpty()
        repository.createBackupSnapshot(
            label = "Auto Backup (${settings.autoBackupFrequency ?: "DAILY"})",
            videos = videos,
            schedules = schedules,
            playlists = playlists,
            retentionDays = 30,
            settings = settings
        ).onSuccess {
            recordActivity("BACKUP", "Automatic Backup Created", "Scheduled ${settings.autoBackupFrequency} snapshot with SHA-256 checksum.")
            recordEvent("AUTO_BACKUP", "Automatic Backup Completed", "Saved ${videos.size} videos and ${schedules.size} schedules.", "SUCCESS")
        }
    }

    private fun requirePermission(requiresOwner: Boolean = false, requiresEditor: Boolean = true): Boolean {
        val role = activeRole.value
        val allowed = DreamiitStudioHelpers.canPerformAction(role, requiresOwner = requiresOwner, requiresEditor = requiresEditor)
        if (!allowed) {
            val needed = if (requiresOwner) "OWNER" else "EDITOR or OWNER"
            _bannerMessage.value = "Blocked by Team Role ($role): This action requires $needed access."
        }
        return allowed
    }

    fun clearBanner() {
        _bannerMessage.value = null
    }

    fun showBanner(msg: String) {
        _bannerMessage.value = msg
    }

    // --- YouTube OAuth 2.0 PKCE ---
    suspend fun prepareYouTubeOAuthUrl(clientId: String): Uri {
        val verifier = youtubeClient.generatePkceVerifier()
        oauthStore.savePkceVerifier(verifier)
        return youtubeClient.buildAuthorizationUrl(clientId.trim(), verifier)
    }

    fun handleOAuthRedirectUri(uri: Uri) {
        val code = uri.getQueryParameter("code")
        val error = uri.getQueryParameter("error")
        if (!error.isNullOrBlank()) {
            _bannerMessage.value = "YouTube OAuth cancelled or denied: $error"
            return
        }
        if (code.isNullOrBlank()) return

        viewModelScope.launch {
            val creds = oauthStore.getSnapshot()
            val settings = (settingsState.value as? DataState.Success)?.data
            val clientId = settings?.youtubeOAuthClientId?.trim().orEmpty()
            if (clientId.isBlank() || creds.codeVerifier.isBlank()) {
                _bannerMessage.value = "OAuth Client ID or PKCE verifier missing. Enter OAuth Client ID in Settings first."
                return@launch
            }
            try {
                val tokenResp = youtubeClient.exchangeCode(code, clientId, creds.codeVerifier)
                if (!tokenResp.access_token.isNullOrBlank()) {
                    oauthStore.saveTokens(
                        accessToken = tokenResp.access_token,
                        refreshToken = tokenResp.refresh_token,
                        expiresInSec = tokenResp.expires_in ?: 3600L,
                        scope = tokenResp.scope.orEmpty()
                    )
                    syncAuthorizedChannels(tokenResp.access_token)
                    recordActivity("ACCOUNT", "YouTube Channel Authorized", "OAuth 2.0 PKCE scopes linked and channels synced.")
                    recordEvent(
                        eventType = "YOUTUBE_CONNECTED",
                        title = "YouTube Channel Authorized",
                        body = "Official OAuth 2.0 tokens stored securely and channels synced.",
                        severity = "SUCCESS"
                    )
                } else {
                    _bannerMessage.value = tokenResp.error_description ?: tokenResp.error ?: "Token exchange failed."
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _bannerMessage.value = "OAuth exchange failed: ${e.localizedMessage}"
            }
        }
    }

    private suspend fun getValidAccessToken(): String? {
        val creds = oauthStore.getSnapshot()
        if (creds.hasValidToken) return creds.accessToken
        val settings = (settingsState.value as? DataState.Success)?.data
        val clientId = settings?.youtubeOAuthClientId?.trim().orEmpty()
        if (creds.refreshToken.isNotBlank() && clientId.isNotBlank()) {
            return try {
                val refreshed = youtubeClient.refreshToken(creds.refreshToken, clientId)
                if (!refreshed.access_token.isNullOrBlank()) {
                    oauthStore.saveTokens(
                        accessToken = refreshed.access_token,
                        refreshToken = refreshed.refresh_token ?: creds.refreshToken,
                        expiresInSec = refreshed.expires_in ?: 3600L,
                        scope = refreshed.scope ?: creds.scope
                    )
                    refreshed.access_token
                } else null
            } catch (e: Exception) {
                recordEvent(
                    eventType = "TOKEN_EXPIRED",
                    title = "YouTube OAuth Token Expired",
                    body = "Automatic token refresh failed. Re-authorize YouTube in Settings.",
                    severity = "WARNING"
                )
                null
            }
        }
        return null
    }

    fun refreshChannelsFromApi() {
        viewModelScope.launch {
            val token = getValidAccessToken()
            if (token == null) {
                _bannerMessage.value = "Connect a YouTube channel via OAuth 2.0 first."
                return@launch
            }
            try {
                syncAuthorizedChannels(token)
                recordActivity("ACCOUNT", "Synced YouTube Stats", "Refreshed subscriber, view, and video counts from YouTube API.")
                _bannerMessage.value = "Channel metrics synced from YouTube API."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _bannerMessage.value = "Failed to sync channels: ${e.localizedMessage}"
            }
        }
    }

    private suspend fun syncAuthorizedChannels(accessToken: String) {
        val fetched = youtubeClient.fetchAuthorizedChannels(accessToken, currentUserId)
        for (ch in fetched) {
            repository.upsertChannel(ch)
        }
        fetched.firstOrNull()?.let { first ->
            repository.updateSettings(mapOf("activeChannelId" to first.channelId))
        }
    }

    fun switchChannel(channelId: String) {
        if (!requirePermission(requiresOwner = false, requiresEditor = true)) return
        viewModelScope.launch {
            repository.switchActiveChannel(channelId)
                .onSuccess {
                    recordActivity("ACCOUNT", "Switched Active Channel", "Active channel set to $channelId")
                    _bannerMessage.value = "Switched active YouTube channel."
                }
                .onFailure { _bannerMessage.value = it.localizedMessage }
        }
    }

    fun disconnectChannel(channelDocId: String) {
        if (!requirePermission(requiresOwner = true)) return
        viewModelScope.launch {
            repository.disconnectChannel(channelDocId)
                .onSuccess {
                    recordActivity("ACCOUNT", "Disconnected Channel", "Removed channel $channelDocId")
                    _bannerMessage.value = "Disconnected YouTube channel."
                }
                .onFailure { _bannerMessage.value = it.localizedMessage }
        }
    }

    fun disconnectAllYouTubeTokens() {
        if (!requirePermission(requiresOwner = true)) return
        viewModelScope.launch {
            oauthStore.clearAll()
            repository.logAuditEvent(
                eventType = "OAUTH_DISCONNECTED",
                deviceName = "${Build.MANUFACTURER} ${Build.MODEL}",
                status = "SUCCESS",
                details = "Cleared YouTube OAuth access & refresh tokens from device"
            )
            recordActivity("AUTH", "Revoked YouTube OAuth Tokens", "Cleared OAuth access & refresh tokens from device")
            _bannerMessage.value = "YouTube OAuth credentials disconnected."
        }
    }

    // --- Video Inspection, MP4 Validation, Upload & Library Replacement ---
    suspend fun inspectSelectedVideo(uri: Uri): VideoQualityReport =
        MediaInspector.inspectVideo(appContext, uri)

    fun uploadVideoToLibraryAndYouTube(
        videoUri: Uri,
        title: String,
        description: String,
        categoryId: String,
        privacyStatus: PrivacyStatus,
        tags: List<String>,
        qualityReport: VideoQualityReport,
        thumbnailBytes: ByteArray?,
        publishToYouTubeNow: Boolean
    ) {
        if (!requirePermission(requiresEditor = true)) return
        if (!qualityReport.isValidMp4) {
            _uploadState.value = UploadProgressState(
                active = false,
                error = qualityReport.validationError ?: "Invalid MP4 video file."
            )
            _bannerMessage.value = qualityReport.validationError ?: "Invalid MP4 video file."
            return
        }
        viewModelScope.launch {
            val channels = (channelsState.value as? DataState.Success)?.data.orEmpty()
            val activeChannel = channels.firstOrNull { it.activeChannel } ?: channels.firstOrNull()
            val channelId = activeChannel?.channelId ?: "local_vault"

            _uploadState.value = UploadProgressState(
                active = true,
                progress = 0.1f,
                statusText = "Saving video metadata to DREAMIIT28 Cloud Library..."
            )

            var ytVideoId: String? = null
            var uploadStatus = "READY"
            var failureReason: String? = null

            if (publishToYouTubeNow) {
                val token = getValidAccessToken()
                if (token == null) {
                    uploadStatus = "FAILED"
                    failureReason = "YouTube OAuth not connected. Saved to Cloud Library only; authorize YouTube to publish."
                } else {
                    _uploadState.value = UploadProgressState(
                        active = true,
                        progress = 0.25f,
                        statusText = "Uploading to YouTube via Resumable Upload API..."
                    )
                    val uploadResult = youtubeClient.uploadVideoResumable(
                        accessToken = token,
                        contentResolver = appContext.contentResolver,
                        videoUri = videoUri,
                        fileSizeBytes = qualityReport.fileSizeBytes,
                        title = title,
                        description = description,
                        categoryId = categoryId,
                        privacyStatus = privacyStatus.apiValue,
                        tags = tags,
                        onProgress = { p ->
                            _uploadState.value = _uploadState.value.copy(
                                progress = p,
                                statusText = "Uploading bytes (${(p * 100).toInt()}%)..."
                            )
                        }
                    )
                    uploadResult.onSuccess { id ->
                        ytVideoId = id
                        uploadStatus = "UPLOADED"
                        if (thumbnailBytes != null && id.isNotBlank()) {
                            youtubeClient.uploadCustomThumbnail(token, id, thumbnailBytes)
                        }
                    }.onFailure { err ->
                        uploadStatus = "FAILED"
                        failureReason = err.localizedMessage ?: "YouTube upload failed"
                    }
                }
            }

            val video = CloudVideo(
                userId = currentUserId,
                channelId = channelId,
                title = title,
                description = description,
                categoryId = categoryId,
                privacyStatus = privacyStatus.name,
                sourceWidth = qualityReport.width,
                sourceHeight = qualityReport.height,
                sourceFps = qualityReport.fps,
                durationSec = qualityReport.durationSec,
                fileSizeBytes = qualityReport.fileSizeBytes,
                uploadStatus = uploadStatus,
                qualityVerified1080p = qualityReport.isGenuine1080p,
                tags = tags,
                localUri = videoUri.toString(),
                youtubeVideoId = ytVideoId,
                qualityWarning = qualityReport.warningMessage
            )

            repository.addVideo(video)
                .onSuccess {
                    val reason = failureReason
                    if (reason != null) {
                        _uploadState.value = UploadProgressState(active = false, error = reason)
                        recordEvent("UPLOAD_FAILED", "Upload Warning", reason, "WARNING")
                        recordActivity("UPLOAD", "Upload Failed", "'$title': $reason")
                    } else {
                        _uploadState.value = UploadProgressState(active = false, progress = 1f, statusText = "Done")
                        recordEvent(
                            "UPLOAD_SUCCESS",
                            "Video Ready",
                            "'$title' saved (${qualityReport.width}×${qualityReport.height}).",
                            "SUCCESS"
                        )
                        recordActivity("UPLOAD", if (ytVideoId != null) "Published Video to YouTube" else "Saved Video to Cloud Library", "'$title'")
                        _bannerMessage.value = if (ytVideoId != null) {
                            "Published '$title' to YouTube ($ytVideoId)"
                        } else {
                            "Saved '$title' to Cloud Video Library."
                        }
                    }
                }
                .onFailure { err ->
                    _uploadState.value = UploadProgressState(active = false, error = err.localizedMessage)
                }
        }
    }

    fun scheduleVideoUpload(
        videoUri: Uri,
        title: String,
        description: String,
        categoryId: String,
        privacyStatus: PrivacyStatus,
        tags: List<String>,
        qualityReport: VideoQualityReport,
        scheduledPublishEpochMs: Long,
        timezoneId: String
    ) {
        if (!requirePermission(requiresEditor = true)) return
        viewModelScope.launch {
            val channels = (channelsState.value as? DataState.Success)?.data.orEmpty()
            val activeChannel = channels.firstOrNull { it.activeChannel } ?: channels.firstOrNull()
            val channelId = activeChannel?.channelId ?: "unassigned"
            val existingUploads = (uploadSchedulesState.value as? DataState.Success)?.data.orEmpty()
            val existingLives = (schedulesState.value as? DataState.Success)?.data.orEmpty()

            val conflict = DreamiitStudioHelpers.detectScheduleConflict(
                channelId = channelId,
                targetEpochMs = scheduledPublishEpochMs,
                liveSchedules = existingLives,
                uploadSchedules = existingUploads
            )
            if (conflict != null) {
                _bannerMessage.value = conflict
                return@launch
            }

            val isoFmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val publishIso = isoFmt.format(Date(scheduledPublishEpochMs))

            // Save local source metadata to library as QUEUED
            repository.addVideo(
                CloudVideo(
                    userId = currentUserId,
                    channelId = channelId,
                    title = title,
                    description = description,
                    categoryId = categoryId,
                    privacyStatus = privacyStatus.name,
                    sourceWidth = qualityReport.width,
                    sourceHeight = qualityReport.height,
                    sourceFps = qualityReport.fps,
                    durationSec = qualityReport.durationSec,
                    fileSizeBytes = qualityReport.fileSizeBytes,
                    uploadStatus = "QUEUED",
                    qualityVerified1080p = qualityReport.isGenuine1080p,
                    tags = tags,
                    localUri = videoUri.toString(),
                    qualityWarning = qualityReport.warningMessage
                )
            )

            val sched = UploadSchedule(
                userId = currentUserId,
                channelId = channelId,
                title = title,
                description = description,
                categoryId = categoryId,
                privacyStatus = privacyStatus.name,
                tags = tags,
                localUri = videoUri.toString(),
                scheduledPublishIso = publishIso,
                scheduledPublishEpochMs = scheduledPublishEpochMs,
                timezone = timezoneId,
                status = UploadScheduleStatus.SCHEDULED.name
            )
            repository.createUploadSchedule(sched, existingUploads, existingLives)
                .onSuccess { uploadId ->
                    val workerUrl = (settingsState.value as? DataState.Success)?.data?.workerBaseUrl
                    val bearerToken = oauthStore.getSnapshot().workerApiToken
                    if (!workerUrl.isNullOrBlank()) {
                        workerClient.dispatchCommand(
                            workerUrl,
                            "/api/uploads/schedule",
                            JSONObject().apply {
                                put("uploadId", uploadId)
                                put("title", title)
                                put("scheduledPublishIso", publishIso)
                                put("privacyStatus", privacyStatus.apiValue)
                            },
                            bearerToken
                        )
                    }
                    recordActivity("UPLOAD", "Scheduled Auto-Upload", "'$title' scheduled for $publishIso")
                    recordEvent("UPLOAD_SCHEDULED", "Video Upload Scheduled", "'$title' queued for automatic publish.", "SUCCESS")
                    _bannerMessage.value = "Scheduled automatic upload for '$title'."
                }
                .onFailure { _bannerMessage.value = it.localizedMessage }
        }
    }

    fun triggerScheduledUploadNow(upload: UploadSchedule) {
        if (!requirePermission(requiresEditor = true)) return
        viewModelScope.launch { executeScheduledUploadNow(upload) }
    }

    private suspend fun executeScheduledUploadNow(upload: UploadSchedule) {
        val token = getValidAccessToken()
        val uriStr = upload.localUri
        if (token == null || uriStr.isNullOrBlank()) {
            repository.updateUploadSchedule(
                upload.id,
                mapOf(
                    "status" to UploadScheduleStatus.FAILED.name,
                    "lastError" to if (token == null) "YouTube OAuth not connected." else "Local video URI missing."
                )
            )
            return
        }
        repository.updateUploadSchedule(upload.id, mapOf("status" to UploadScheduleStatus.UPLOADING.name))
        val uri = Uri.parse(uriStr)
        val report = MediaInspector.inspectVideo(appContext, uri)
        val result = youtubeClient.uploadVideoResumable(
            accessToken = token,
            contentResolver = appContext.contentResolver,
            videoUri = uri,
            fileSizeBytes = report.fileSizeBytes,
            title = upload.title,
            description = upload.description,
            categoryId = upload.categoryId,
            privacyStatus = PrivacyStatus.fromRaw(upload.privacyStatus).apiValue,
            tags = upload.tags.orEmpty(),
            onProgress = {}
        )
        result.onSuccess { ytId ->
            repository.updateUploadSchedule(
                upload.id,
                mapOf(
                    "status" to UploadScheduleStatus.PUBLISHED.name,
                    "youtubeVideoId" to ytId,
                    "lastError" to ""
                )
            )
            recordActivity("UPLOAD", "Auto-Published Scheduled Video", "'${upload.title}' -> $ytId")
            recordEvent("AUTO_UPLOAD_PUBLISHED", "Scheduled Upload Published", "'${upload.title}' is now live on YouTube ($ytId).", "SUCCESS")
        }.onFailure { err ->
            repository.updateUploadSchedule(
                upload.id,
                mapOf(
                    "status" to UploadScheduleStatus.FAILED.name,
                    "lastError" to (err.localizedMessage ?: "Upload failed")
                )
            )
            recordEvent("AUTO_UPLOAD_FAILED", "Scheduled Upload Failed", "'${upload.title}': ${err.localizedMessage}", "ERROR")
        }
    }

    fun rescheduleUpload(upload: UploadSchedule, newEpochMs: Long) {
        if (!requirePermission(requiresEditor = true)) return
        viewModelScope.launch {
            val uploads = (uploadSchedulesState.value as? DataState.Success)?.data.orEmpty()
            val lives = (schedulesState.value as? DataState.Success)?.data.orEmpty()
            val conflict = DreamiitStudioHelpers.detectScheduleConflict(
                channelId = upload.channelId,
                targetEpochMs = newEpochMs,
                liveSchedules = lives,
                uploadSchedules = uploads,
                excludeUploadId = upload.id
            )
            if (conflict != null) {
                _bannerMessage.value = conflict
                return@launch
            }
            val isoFmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            repository.updateUploadSchedule(
                upload.id,
                mapOf(
                    "scheduledPublishEpochMs" to newEpochMs,
                    "scheduledPublishIso" to isoFmt.format(Date(newEpochMs)),
                    "status" to UploadScheduleStatus.SCHEDULED.name
                )
            ).onSuccess {
                recordActivity("UPLOAD", "Rescheduled Upload", "'${upload.title}' moved in calendar")
                _bannerMessage.value = "Upload rescheduled."
            }.onFailure { _bannerMessage.value = it.localizedMessage }
        }
    }

    fun cancelUploadSchedule(upload: UploadSchedule) {
        if (!requirePermission(requiresEditor = true)) return
        viewModelScope.launch {
            repository.updateUploadSchedule(upload.id, mapOf("status" to UploadScheduleStatus.CANCELLED.name))
                .onSuccess {
                    recordActivity("UPLOAD", "Cancelled Scheduled Upload", "'${upload.title}'")
                    _bannerMessage.value = "Scheduled upload cancelled."
                }
                .onFailure { _bannerMessage.value = it.localizedMessage }
        }
    }

    fun deleteUploadSchedule(uploadId: String) {
        if (!requirePermission(requiresEditor = true)) return
        viewModelScope.launch {
            repository.deleteUploadSchedule(uploadId)
                .onSuccess { _bannerMessage.value = "Upload schedule removed from calendar." }
                .onFailure { _bannerMessage.value = it.localizedMessage }
        }
    }

    fun updateVideoMetadata(
        videoId: String,
        title: String,
        description: String,
        privacyStatus: String,
        tags: List<String>
    ) {
        if (!requirePermission(requiresEditor = true)) return
        viewModelScope.launch {
            repository.updateVideoDetails(videoId, title, description, privacyStatus, tags)
                .onSuccess {
                    recordActivity("MEDIA", "Updated Video Metadata", "'$title'")
                    _bannerMessage.value = "Video details updated."
                }
                .onFailure { _bannerMessage.value = it.localizedMessage }
        }
    }

    fun deleteVideo(videoId: String) {
        if (!requirePermission(requiresEditor = true)) return
        viewModelScope.launch {
            val schedules = (schedulesState.value as? DataState.Success)?.data.orEmpty()
            repository.deleteVideoGuarded(videoId, schedules)
                .onSuccess {
                    recordActivity("MEDIA", "Deleted Video", "Removed video $videoId from Cloud Library")
                    _bannerMessage.value = "Video removed from Cloud Library."
                }
                .onFailure { _bannerMessage.value = it.localizedMessage }
        }
    }

    fun replaceVideoInLibrary(existingVideo: CloudVideo, newUri: Uri) {
        if (!requirePermission(requiresEditor = true)) return
        viewModelScope.launch {
            val schedules = (schedulesState.value as? DataState.Success)?.data.orEmpty()
            val isStreaming = schedules.any {
                it.videoId == existingVideo.id && it.status in listOf(ScheduleStatus.LIVE.name, ScheduleStatus.PREPARING.name)
            }
            if (isStreaming) {
                _bannerMessage.value = "Cannot replace MP4 while this video is actively streaming."
                return@launch
            }
            val report = MediaInspector.inspectVideo(appContext, newUri)
            if (!report.isValidMp4) {
                _bannerMessage.value = report.validationError ?: "Selected replacement is not a valid MP4 file."
                return@launch
            }
            val updates = mutableMapOf<String, Any>(
                "localUri" to newUri.toString().take(500),
                "sourceWidth" to report.width.coerceIn(0, 7680),
                "sourceHeight" to report.height.coerceIn(0, 4320),
                "sourceFps" to report.fps.coerceIn(0, 240),
                "durationSec" to report.durationSec.coerceIn(0L, 86400L),
                "fileSizeBytes" to report.fileSizeBytes.coerceAtLeast(0L),
                "qualityVerified1080p" to report.isGenuine1080p,
                "uploadStatus" to "READY"
            )
            if (!report.warningMessage.isNullOrBlank()) {
                updates["qualityWarning"] = report.warningMessage.take(300)
            }
            repository.updateVideoFields(existingVideo.id, updates)
                .onSuccess {
                    recordActivity("MEDIA", "Replaced Library Video MP4", "'${existingVideo.title}' -> ${report.width}x${report.height} (${report.durationSec}s)")
                    _bannerMessage.value = "Replaced video file for '${existingVideo.title}' (${report.width}×${report.height})."
                }
                .onFailure { _bannerMessage.value = it.localizedMessage }
        }
    }

    fun cleanupUnlinkedDraftVideos() {
        if (!requirePermission(requiresEditor = true)) return
        viewModelScope.launch {
            val videos = (videosState.value as? DataState.Success)?.data.orEmpty()
            val schedules = (schedulesState.value as? DataState.Success)?.data.orEmpty()
            val candidates = videos.filter { vid ->
                vid.youtubeVideoId.isNullOrBlank() &&
                    vid.uploadStatus in listOf("FAILED", "READY") &&
                    schedules.none { it.videoId == vid.id && it.status in listOf("SCHEDULED", "PREPARING", "LIVE") }
            }
            var removed = 0
            for (vid in candidates) {
                if (repository.deleteVideoGuarded(vid.id, schedules).isSuccess) {
                    removed++
                }
            }
            recordActivity("MEDIA", "Storage Cleanup", "Removed $removed unlinked draft/failed videos")
            _bannerMessage.value = if (removed > 0) "Cleaned up $removed unlinked videos." else "No unlinked draft videos to clean up."
        }
    }

    // --- Stage 2: Media Lab (Trimmer, Audio Manager, Vertical 9:16 Repurposing) ---
    fun resetMediaLabState() {
        _mediaLabState.value = MediaLabState()
    }

    fun analyzeAudioForVideo(uri: Uri) {
        viewModelScope.launch {
            _mediaLabState.value = _mediaLabState.value.copy(
                active = true,
                progress = 0.3f,
                statusText = "Decoding PCM samples & measuring peak/RMS dBFS...",
                error = null
            )
            val report = MediaEditorEngine.analyzeAudio(appContext, uri)
            _mediaLabState.value = _mediaLabState.value.copy(
                active = false,
                progress = 1f,
                statusText = report.summary,
                audioReport = report
            )
        }
    }

    fun trimAndSaveVideoSeparately(
        sourceUri: Uri,
        sourceVideo: CloudVideo?,
        startMs: Long,
        endMs: Long,
        newTitle: String
    ) {
        if (!requirePermission(requiresEditor = true)) return
        viewModelScope.launch {
            _mediaLabState.value = _mediaLabState.value.copy(
                active = true,
                progress = 0.05f,
                statusText = "Trimming video cleanly (original preserved)...",
                error = null
            )
            try {
                val safeName = "trim_${System.currentTimeMillis()}.mp4"
                val outFile = MediaEditorEngine.trimVideo(appContext, sourceUri, startMs, endMs, safeName) { p ->
                    _mediaLabState.value = _mediaLabState.value.copy(progress = p, statusText = "Exporting trimmed MP4 (${(p * 100).toInt()}%)...")
                }
                val outUri = MediaEditorEngine.uriForFile(appContext, outFile)
                val qr = MediaInspector.inspectVideo(appContext, outUri)
                repository.addVideo(
                    CloudVideo(
                        userId = currentUserId,
                        channelId = sourceVideo?.channelId ?: "local_vault",
                        title = newTitle.ifBlank { "${sourceVideo?.title ?: "Video"} (Trimmed)" },
                        description = sourceVideo?.description.orEmpty(),
                        privacyStatus = sourceVideo?.privacyStatus ?: "PUBLIC",
                        sourceWidth = qr.width,
                        sourceHeight = qr.height,
                        sourceFps = qr.fps,
                        durationSec = qr.durationSec,
                        fileSizeBytes = qr.fileSizeBytes,
                        uploadStatus = "READY",
                        qualityVerified1080p = qr.isGenuine1080p,
                        localUri = outUri.toString(),
                        clipSourceVideoId = sourceVideo?.id,
                        qualityWarning = qr.warningMessage
                    )
                )
                recordActivity("MEDIA", "Trimmed Video Saved Separately", "'$newTitle' (${qr.durationSec}s)")
                _mediaLabState.value = _mediaLabState.value.copy(
                    active = false,
                    progress = 1f,
                    statusText = "Saved separate trimmed video (${qr.durationSec}s). Original untouched.",
                    lastExportedUri = outUri,
                    lastExportedLabel = outFile.name
                )
                _bannerMessage.value = "Trimmed video saved separately to Cloud Library."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _mediaLabState.value = _mediaLabState.value.copy(
                    active = false,
                    error = "Trim failed: ${e.localizedMessage}"
                )
            }
        }
    }

    fun normalizeAndSaveVideoAudio(
        sourceUri: Uri,
        sourceVideo: CloudVideo?,
        gainLinear: Float,
        normalized: Boolean,
        newTitle: String
    ) {
        if (!requirePermission(requiresEditor = true)) return
        viewModelScope.launch {
            _mediaLabState.value = _mediaLabState.value.copy(
                active = true,
                progress = 0.05f,
                statusText = "Applying PCM audio gain (${String.format(Locale.US, "%.2fx", gainLinear)})...",
                error = null
            )
            try {
                val safeName = "audio_${System.currentTimeMillis()}.mp4"
                val outFile = MediaEditorEngine.adjustAndNormalizeAudio(appContext, sourceUri, gainLinear, safeName) { p ->
                    _mediaLabState.value = _mediaLabState.value.copy(progress = p, statusText = "Encoding normalized audio (${(p * 100).toInt()}%)...")
                }
                val outUri = MediaEditorEngine.uriForFile(appContext, outFile)
                val qr = MediaInspector.inspectVideo(appContext, outUri)
                val postReport = MediaEditorEngine.analyzeAudio(appContext, outUri)
                repository.addVideo(
                    CloudVideo(
                        userId = currentUserId,
                        channelId = sourceVideo?.channelId ?: "local_vault",
                        title = newTitle.ifBlank { "${sourceVideo?.title ?: "Video"} (Audio Mastered)" },
                        description = sourceVideo?.description.orEmpty(),
                        privacyStatus = sourceVideo?.privacyStatus ?: "PUBLIC",
                        sourceWidth = qr.width,
                        sourceHeight = qr.height,
                        sourceFps = qr.fps,
                        durationSec = qr.durationSec,
                        fileSizeBytes = qr.fileSizeBytes,
                        uploadStatus = "READY",
                        qualityVerified1080p = qr.isGenuine1080p,
                        localUri = outUri.toString(),
                        clipSourceVideoId = sourceVideo?.id,
                        audioDbPeak = postReport.peakDb,
                        audioNormalized = normalized
                    )
                )
                recordActivity("MEDIA", "Audio Normalized & Saved", "'$newTitle' (Peak ${String.format(Locale.US, "%.1f", postReport.peakDb)} dBFS)")
                _mediaLabState.value = _mediaLabState.value.copy(
                    active = false,
                    progress = 1f,
                    statusText = "Audio processed & saved separately (${postReport.summary}).",
                    audioReport = postReport,
                    lastExportedUri = outUri,
                    lastExportedLabel = outFile.name
                )
                _bannerMessage.value = "Normalized video saved separately to Library."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _mediaLabState.value = _mediaLabState.value.copy(
                    active = false,
                    error = "Audio export failed: ${e.localizedMessage}"
                )
            }
        }
    }

    fun createVertical916ShortClip(
        sourceUri: Uri,
        sourceVideo: CloudVideo?,
        startMs: Long,
        endMs: Long,
        shortsTitle: String,
        shortsDescription: String
    ) {
        if (!requirePermission(requiresEditor = true)) return
        viewModelScope.launch {
            _mediaLabState.value = _mediaLabState.value.copy(
                active = true,
                progress = 0.05f,
                statusText = "Rendering vertical 9:16 Shorts clip...",
                error = null
            )
            try {
                val safeName = "short_9x16_${System.currentTimeMillis()}.mp4"
                val outFile = MediaEditorEngine.createVertical916Clip(appContext, sourceUri, startMs, endMs, safeName) { p ->
                    _mediaLabState.value = _mediaLabState.value.copy(progress = p, statusText = "Rendering 9:16 Short (${(p * 100).toInt()}%)...")
                }
                val outUri = MediaEditorEngine.uriForFile(appContext, outFile)
                val qr = MediaInspector.inspectVideo(appContext, outUri)
                repository.addVideo(
                    CloudVideo(
                        userId = currentUserId,
                        channelId = sourceVideo?.channelId ?: "local_vault",
                        title = shortsTitle.ifBlank { "${sourceVideo?.title ?: "Short"} #Shorts" },
                        description = shortsDescription.ifBlank { "#Shorts" },
                        privacyStatus = sourceVideo?.privacyStatus ?: "PUBLIC",
                        sourceWidth = qr.width,
                        sourceHeight = qr.height,
                        sourceFps = qr.fps,
                        durationSec = qr.durationSec,
                        fileSizeBytes = qr.fileSizeBytes,
                        uploadStatus = "READY",
                        qualityVerified1080p = false,
                        localUri = outUri.toString(),
                        clipSourceVideoId = sourceVideo?.id
                    )
                )
                recordActivity("MEDIA", "Created 9:16 Vertical Short", "'$shortsTitle' (${qr.durationSec}s)")
                _mediaLabState.value = _mediaLabState.value.copy(
                    active = false,
                    progress = 1f,
                    statusText = "Vertical 9:16 Short saved separately (${qr.width}×${qr.height}).",
                    lastExportedUri = outUri,
                    lastExportedLabel = outFile.name
                )
                _bannerMessage.value = "Vertical 9:16 Shorts clip saved!"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _mediaLabState.value = _mediaLabState.value.copy(
                    active = false,
                    error = "Vertical clip export failed: ${e.localizedMessage}"
                )
            }
        }
    }

    // --- Stage 2 AI Cloud Saves & Captions/Chapters Publishing ---
    fun saveTranscriptToCloudVideo(videoId: String, transcript: String) {
        if (!requirePermission(requiresEditor = true)) return
        viewModelScope.launch {
            repository.updateVideoFields(videoId, mapOf("transcriptText" to transcript.take(40000)))
                .onSuccess {
                    recordActivity("MEDIA", "Saved AI Transcript", "Saved speech-to-text transcript to Cloud Video $videoId")
                    _bannerMessage.value = "Transcript saved to Cloud Storage / Firestore."
                }
                .onFailure { _bannerMessage.value = it.localizedMessage }
        }
    }

    fun saveSubtitlesAndOptionallyUploadYouTube(
        video: CloudVideo,
        languageCode: String,
        srtContent: String,
        uploadToYouTube: Boolean
    ) {
        if (!requirePermission(requiresEditor = true)) return
        viewModelScope.launch {
            val fieldKey = if (languageCode == "hi") "subtitlesHiSrt" else "subtitlesEnSrt"
            repository.updateVideoFields(video.id, mapOf(fieldKey to srtContent.take(40000)))
            if (uploadToYouTube) {
                val ytId = video.youtubeVideoId
                val token = getValidAccessToken()
                if (ytId.isNullOrBlank()) {
                    _bannerMessage.value = "Saved SRT to Cloud Library; publish this video to YouTube first to attach captions."
                    return@launch
                }
                if (token == null) {
                    _bannerMessage.value = "Saved SRT locally; authorize YouTube OAuth in Settings to upload captions."
                    return@launch
                }
                youtubeClient.uploadCaptionsSrt(
                    accessToken = token,
                    youtubeVideoId = ytId,
                    languageCode = languageCode,
                    trackName = if (languageCode == "hi") "Hindi (DREAMIIT28)" else "English (DREAMIIT28)",
                    srtContent = srtContent
                ).onSuccess {
                    recordActivity("UPLOAD", "Uploaded YouTube Captions", "Video $ytId ($languageCode .srt)")
                    _bannerMessage.value = "Captions ($languageCode) uploaded to YouTube!"
                }.onFailure {
                    _bannerMessage.value = it.localizedMessage
                }
            } else {
                recordActivity("MEDIA", "Saved Subtitles (.srt)", "Saved $languageCode captions to Cloud Library")
                _bannerMessage.value = "Subtitles saved to Cloud Library."
            }
        }
    }

    fun applyChaptersToVideo(video: CloudVideo, chaptersBlock: String, updateYouTubeNow: Boolean) {
        if (!requirePermission(requiresEditor = true)) return
        viewModelScope.launch {
            val cleanChapters = chaptersBlock.trim()
            val mergedDesc = buildString {
                append(video.description.trim())
                if (isNotEmpty()) append("\n\n")
                append("Chapters:\n")
                append(cleanChapters)
            }.take(5000)
            repository.updateVideoFields(
                video.id,
                mapOf(
                    "chaptersText" to cleanChapters.take(10000),
                    "description" to mergedDesc
                )
            )
            if (updateYouTubeNow && !video.youtubeVideoId.isNullOrBlank()) {
                val token = getValidAccessToken()
                if (token == null) {
                    _bannerMessage.value = "Saved chapters to Cloud Library; authorize YouTube OAuth to sync to YouTube."
                    return@launch
                }
                youtubeClient.updateYouTubeVideoDescription(token, video.youtubeVideoId, mergedDesc)
                    .onSuccess {
                        recordActivity("UPLOAD", "Synced Chapters to YouTube", "Updated description for ${video.title}")
                        _bannerMessage.value = "Chapters added to YouTube video description!"
                    }
                    .onFailure { _bannerMessage.value = it.localizedMessage }
            } else {
                recordActivity("MEDIA", "Added Chapters to Description", "Saved chapters to '${video.title}'")
                _bannerMessage.value = "Chapters added to video description."
            }
        }
    }

    // --- Automated V20.0 Cloud Live Streaming & Scheduler ---
    fun scheduleLiveBroadcast(
        videoId: String,
        videoTitle: String,
        liveTitle: String,
        liveDescription: String,
        privacyStatus: PrivacyStatus,
        scheduledStartEpochMs: Long,
        timezoneId: String,
        recurrence: ScheduleRecurrence,
        profile: StreamingProfile,
        autoEndOnFinish: Boolean,
        maxDurationMinutes: Int = 60,
        repeatPlayback: Boolean = false
    ) {
        if (!requirePermission(requiresEditor = true)) return
        viewModelScope.launch {
            val channels = (channelsState.value as? DataState.Success)?.data.orEmpty()
            val activeChannel = channels.firstOrNull { it.activeChannel } ?: channels.firstOrNull()
            val channelId = activeChannel?.channelId ?: "unassigned"
            val existingLives = (schedulesState.value as? DataState.Success)?.data.orEmpty()
            val existingUploads = (uploadSchedulesState.value as? DataState.Success)?.data.orEmpty()
            val safeDurationMin = DreamiitStudioHelpers.clampLiveDurationMinutes(maxDurationMinutes)

            val isoFmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val startIso = isoFmt.format(Date(scheduledStartEpochMs))

            var ytBroadcastId: String? = null
            var ytStreamId: String? = null
            var rawRtmpsUrl: String? = null
            var watchUrl: String? = null
            var infoNote = "Scheduled in Firestore queue (${profile.resolution}, ${safeDurationMin}m)."

            val token = getValidAccessToken()
            if (token != null) {
                try {
                    val res = youtubeClient.provisionLiveBroadcastAndStream(
                        accessToken = token,
                        title = liveTitle,
                        description = liveDescription,
                        scheduledStartIso = startIso,
                        privacyStatus = privacyStatus.apiValue,
                        autoEnd = autoEndOnFinish,
                        resolution = when {
                            profile.height >= 1080 -> "1080p"
                            profile.height >= 720 -> "720p"
                            else -> "480p"
                        },
                        frameRate = "${profile.fps}fps"
                    )
                    ytBroadcastId = res.first
                    ytStreamId = res.second
                    rawRtmpsUrl = res.third
                    watchUrl = "https://youtube.com/watch?v=${res.first}"
                    infoNote = "YouTube Live Broadcast provisioned (stream key masked in Firestore)."
                } catch (e: Exception) {
                    infoNote = "Saved schedule; YouTube Live API provisioning pending (${e.localizedMessage})."
                }
            }

            // Security invariant: never persist raw stream keys in Firestore!
            val maskedRtmps = rawRtmpsUrl?.let { DreamiitStudioHelpers.maskStreamKeyUrl(it) }

            val schedule = LiveSchedule(
                userId = currentUserId,
                channelId = channelId,
                videoId = videoId,
                videoTitle = videoTitle,
                liveTitle = liveTitle,
                liveDescription = liveDescription,
                privacyStatus = privacyStatus.name,
                scheduledStartIso = startIso,
                scheduledStartEpochMs = scheduledStartEpochMs,
                timezone = timezoneId,
                recurrence = recurrence.name,
                targetProfile = profile.id,
                status = ScheduleStatus.SCHEDULED.name,
                retryCount = 0,
                maxRetries = 5,
                autoEndOnFinish = autoEndOnFinish,
                youtubeBroadcastId = ytBroadcastId,
                youtubeStreamId = ytStreamId,
                youtubeWatchUrl = watchUrl,
                rtmpsIngestUrl = maskedRtmps,
                maxDurationMinutes = safeDurationMin,
                repeatPlayback = repeatPlayback,
                pausedState = false,
                lastSafePositionSec = 0L,
                droppedFrames = 0,
                currentBitrateKbps = profile.videoBitrateKbps,
                youtubeConfirmedLive = false,
                recoveryState = "HEALTHY"
            )

            repository.createSchedule(schedule, existingLives, existingUploads)
                .onSuccess { schedId ->
                    if (!rawRtmpsUrl.isNullOrBlank()) {
                        oauthStore.saveScheduleIngestUrl(schedId, rawRtmpsUrl)
                    }
                    val settings = (settingsState.value as? DataState.Success)?.data
                    val workerUrl = settings?.workerBaseUrl
                    val bearerToken = oauthStore.getSnapshot().workerApiToken
                    if (!workerUrl.isNullOrBlank()) {
                        workerClient.dispatchCommand(
                            workerUrl,
                            "/api/schedules/dispatch",
                            JSONObject().apply {
                                put("scheduleId", schedId)
                                put("userId", currentUserId)
                                put("videoId", videoId)
                                put("liveTitle", liveTitle)
                                put("scheduledStartIso", startIso)
                                put("targetProfile", profile.id)
                                put("rtmpsIngestUrl", rawRtmpsUrl.orEmpty())
                                put("autoEndOnFinish", autoEndOnFinish)
                                put("maxDurationMinutes", safeDurationMin)
                                put("repeatPlayback", repeatPlayback)
                            },
                            bearerToken
                        )
                    }
                    repository.logWorkerJob(
                        WorkerJob(
                            userId = currentUserId,
                            scheduleId = schedId,
                            jobType = "SCHEDULE_RTMPS_${profile.id}",
                            status = "PENDING",
                            attemptNumber = 1,
                            message = infoNote,
                            cpuPercent = 0.0,
                            memoryMb = 0,
                            bitrateKbps = profile.videoBitrateKbps,
                            droppedFrames = 0
                        )
                    )
                    recordActivity("LIVE", "Scheduled ${profile.width}x${profile.height} Live", "'$liveTitle' at $startIso (${safeDurationMin}m)")
                    recordEvent("LIVE_SCHEDULED", "Live Broadcast Scheduled", "'$liveTitle' (${profile.label}) — $infoNote", "SUCCESS")
                    _bannerMessage.value = "Scheduled '$liveTitle'. $infoNote"
                }
                .onFailure { err ->
                    _bannerMessage.value = err.localizedMessage
                }
        }
    }

    fun startScheduledLiveNow(schedule: LiveSchedule) {
        if (!requirePermission(requiresEditor = true)) return
        viewModelScope.launch {
            val schedules = (schedulesState.value as? DataState.Success)?.data.orEmpty()
            if (DreamiitStudioHelpers.isDuplicateStreamBlocked(schedule.id, schedules)) {
                _bannerMessage.value = "Duplicate stream blocked: another broadcast is already LIVE or PREPARING."
                return@launch
            }

            val settings = (settingsState.value as? DataState.Success)?.data
            if (settings?.infraCostApproved != true) {
                _bannerMessage.value = "Administrator cloud infrastructure approval is required before launching paid streaming jobs. Approve in Live Hub or Settings."
                return@launch
            }

            val workerUrl = settings.workerBaseUrl?.trim().orEmpty()
            if (workerUrl.isBlank()) {
                _bannerMessage.value = "Cloud Streaming Worker URL is missing. Complete the Setup Checklist in Settings before going live."
                return@launch
            }

            val creds = oauthStore.getSnapshot()
            val bearerToken = creds.workerApiToken
            val profile = StreamingProfile.fromId(schedule.targetProfile)
            val videos = (videosState.value as? DataState.Success)?.data.orEmpty()
            val linkedVideo = videos.firstOrNull { it.id == schedule.videoId }
            val inputMediaUrl = linkedVideo?.storageUrl?.takeIf { it.isNotBlank() }
                ?: linkedVideo?.localUri?.takeIf { it.isNotBlank() }
                ?: ""

            val rawIngest = oauthStore.getScheduleIngestUrl(schedule.id)
                ?: schedule.rtmpsIngestUrl?.takeIf { it.isNotBlank() && !it.contains("•") }
                ?: ""

            if (inputMediaUrl.isBlank() || rawIngest.isBlank()) {
                val missing = buildList {
                    if (inputMediaUrl.isBlank()) add("cloud MP4 video URL/file")
                    if (rawIngest.isBlank()) add("YouTube RTMPS ingest stream key (connect OAuth & recreate/rebind broadcast)")
                }.joinToString(" & ")
                val msg = "Cannot start real RTMPS stream: missing $missing."
                repository.updateSchedule(
                    schedule.id,
                    mapOf("status" to ScheduleStatus.FAILED.name, "lastError" to msg.take(500), "youtubeConfirmedLive" to false)
                )
                _bannerMessage.value = msg
                return@launch
            }

            repository.updateSchedule(
                schedule.id,
                mapOf(
                    "status" to ScheduleStatus.PREPARING.name,
                    "pausedState" to false,
                    "youtubeConfirmedLive" to false,
                    "recoveryState" to "DISPATCHING_FFMPEG"
                )
            )

            val maxDuration = DreamiitStudioHelpers.clampLiveDurationMinutes(schedule.maxDurationMinutes ?: 60)
            val dispatchResult = workerClient.dispatchCommand(
                workerUrl,
                "/api/streams/${schedule.id}/start",
                JSONObject().apply {
                    put("scheduleId", schedule.id)
                    put("inputUrl", inputMediaUrl)
                    put("rtmpsIngestUrl", rawIngest)
                    put("profile", profile.id)
                    put("maxDurationMinutes", maxDuration)
                    put("repeatPlayback", schedule.repeatPlayback == true)
                    put("resumeFromSec", schedule.lastSafePositionSec ?: 0L)
                    put("videoDurationSec", linkedVideo?.durationSec ?: 0L)
                },
                bearerToken
            )

            if (dispatchResult.isFailure) {
                val errMsg = dispatchResult.exceptionOrNull()?.localizedMessage ?: "Worker start failed"
                val nextRetry = (schedule.retryCount + 1).coerceAtMost(schedule.maxRetries)
                repository.updateSchedule(
                    schedule.id,
                    mapOf(
                        "status" to ScheduleStatus.FAILED.name,
                        "retryCount" to nextRetry,
                        "lastError" to errMsg.take(500),
                        "youtubeConfirmedLive" to false,
                        "recoveryState" to "FAILED_DISPATCH"
                    )
                )
                repository.logWorkerJob(
                    WorkerJob(
                        userId = currentUserId,
                        scheduleId = schedule.id,
                        jobType = "RTMPS_START",
                        status = if (nextRetry < schedule.maxRetries) "RETRYING" else "FAILED",
                        attemptNumber = nextRetry.coerceAtLeast(1),
                        message = errMsg,
                        cpuPercent = 0.0,
                        memoryMb = 0,
                        bitrateKbps = 0,
                        droppedFrames = 0
                    )
                )
                recordEvent("SERVER_ERROR", "Cloud Worker Error", errMsg, "ERROR")
                recordActivity("LIVE", "Live Start Failed", "'${schedule.liveTitle}': $errMsg")
                _bannerMessage.value = errMsg
                return@launch
            }

            // Never mark LIVE until YouTube Data API liveBroadcasts.list confirms lifeCycleStatus == "live"
            val token = getValidAccessToken()
            var confirmedLive = false
            var lifeCycle = "unverified"
            if (token != null && !schedule.youtubeBroadcastId.isNullOrBlank()) {
                try {
                    youtubeClient.transitionLiveBroadcast(token, schedule.youtubeBroadcastId, "live")
                } catch (_: Exception) {
                }
                repeat(2) { attempt ->
                    val statusRes = youtubeClient.verifyBroadcastLiveStatus(token, schedule.youtubeBroadcastId)
                    statusRes.onSuccess { lc ->
                        lifeCycle = lc
                        confirmedLive = DreamiitStudioHelpers.isConfirmedYouTubeLive(lc)
                    }
                    if (!confirmedLive && attempt == 0) delay(2500L)
                }
            }

            val nextStatus = if (confirmedLive) ScheduleStatus.LIVE.name else ScheduleStatus.PREPARING.name
            val nextRecovery = if (confirmedLive) "HEALTHY" else "WAITING_YOUTUBE_LIVE ($lifeCycle)"
            repository.updateSchedule(
                schedule.id,
                mapOf(
                    "status" to nextStatus,
                    "youtubeConfirmedLive" to confirmedLive,
                    "recoveryState" to nextRecovery,
                    "lastError" to ""
                )
            )
            repository.logWorkerJob(
                WorkerJob(
                    userId = currentUserId,
                    scheduleId = schedule.id,
                    jobType = "RTMPS_STREAM",
                    status = "RUNNING",
                    attemptNumber = 1,
                    message = if (confirmedLive) {
                        "Verified LIVE on YouTube (${profile.resolution} @ ${profile.fps}fps CBR ${profile.videoBitrateKbps}k)"
                    } else {
                        "FFmpeg CBR pushed to RTMPS; waiting for YouTube liveBroadcasts.status == 'live' (current: $lifeCycle)"
                    },
                    cpuPercent = _workerHealth.value.cpuPercent.coerceIn(0.0, 100.0),
                    memoryMb = _workerHealth.value.memoryMb.coerceIn(0, 65536),
                    bitrateKbps = profile.videoBitrateKbps,
                    droppedFrames = 0
                )
            )
            recordActivity("LIVE", if (confirmedLive) "Verified YouTube LIVE" else "Started Stream (Awaiting YT Live)", "'${schedule.liveTitle}' ($lifeCycle)")
            if (confirmedLive) {
                recordEvent("LIVE_STARTED", "Broadcast Confirmed LIVE", "'${schedule.liveTitle}' is confirmed LIVE on YouTube (${profile.resolution}).", "SUCCESS")
                _bannerMessage.value = "'${schedule.liveTitle}' confirmed LIVE on YouTube!"
            } else {
                _bannerMessage.value = "RTMPS worker active; YouTube status is '$lifeCycle'. Polling until YouTube confirms LIVE."
            }
        }
    }

    fun pauseActiveStream(schedule: LiveSchedule) {
        if (!requirePermission(requiresEditor = true)) return
        viewModelScope.launch {
            val workerUrl = (settingsState.value as? DataState.Success)?.data?.workerBaseUrl
            val bearerToken = oauthStore.getSnapshot().workerApiToken
            workerClient.pauseStream(workerUrl, schedule.id, bearerToken)
                .onSuccess {
                    repository.updateSchedule(
                        schedule.id,
                        mapOf("pausedState" to true, "recoveryState" to "PAUSED")
                    )
                    recordActivity("LIVE", "Paused Live Playback", "'${schedule.liveTitle}'")
                    _bannerMessage.value = "Paused playback for '${schedule.liveTitle}' cleanly."
                }
                .onFailure { _bannerMessage.value = it.localizedMessage }
        }
    }

    fun resumeActiveStream(schedule: LiveSchedule) {
        if (!requirePermission(requiresEditor = true)) return
        viewModelScope.launch {
            val workerUrl = (settingsState.value as? DataState.Success)?.data?.workerBaseUrl
            val bearerToken = oauthStore.getSnapshot().workerApiToken
            workerClient.resumeStream(workerUrl, schedule.id, bearerToken)
                .onSuccess {
                    repository.updateSchedule(
                        schedule.id,
                        mapOf("pausedState" to false, "recoveryState" to "HEALTHY")
                    )
                    recordActivity("LIVE", "Resumed Live Playback", "'${schedule.liveTitle}'")
                    _bannerMessage.value = "Resumed playback for '${schedule.liveTitle}'."
                }
                .onFailure { _bannerMessage.value = it.localizedMessage }
        }
    }

    fun swapScheduleVideoBeforeStart(schedule: LiveSchedule, newVideo: CloudVideo) {
        if (!requirePermission(requiresEditor = true)) return
        if (schedule.status == ScheduleStatus.LIVE.name) {
            _bannerMessage.value = "Stop the active live broadcast before changing the source video."
            return
        }
        viewModelScope.launch {
            repository.updateSchedule(
                schedule.id,
                mapOf(
                    "videoId" to newVideo.id.take(128),
                    "videoTitle" to newVideo.title.take(150)
                )
            ).onSuccess {
                recordActivity("LIVE", "Swapped Schedule Video", "'${schedule.liveTitle}' -> '${newVideo.title}'")
                _bannerMessage.value = "Changed pre-recorded video to '${newVideo.title}'."
            }.onFailure { _bannerMessage.value = it.localizedMessage }
        }
    }

    fun updateScheduleStreamOptions(
        schedule: LiveSchedule,
        profile: StreamingProfile,
        maxDurationMinutes: Int,
        repeatPlayback: Boolean
    ) {
        if (!requirePermission(requiresEditor = true)) return
        if (schedule.status == ScheduleStatus.LIVE.name) {
            _bannerMessage.value = "Stop active stream to change resolution or duration settings."
            return
        }
        val safeDur = DreamiitStudioHelpers.clampLiveDurationMinutes(maxDurationMinutes)
        viewModelScope.launch {
            repository.updateSchedule(
                schedule.id,
                mapOf(
                    "targetProfile" to profile.id,
                    "maxDurationMinutes" to safeDur,
                    "repeatPlayback" to repeatPlayback
                )
            ).onSuccess {
                recordActivity("LIVE", "Updated Stream Profile", "'${schedule.liveTitle}' -> ${profile.id}, ${safeDur}m, repeat=$repeatPlayback")
                _bannerMessage.value = "Updated stream settings (${profile.resolution}, ${safeDur}m)."
            }.onFailure { _bannerMessage.value = it.localizedMessage }
        }
    }

    fun stopActiveLive(schedule: LiveSchedule, emergency: Boolean) {
        if (!requirePermission(requiresEditor = true)) return
        viewModelScope.launch {
            val settings = (settingsState.value as? DataState.Success)?.data
            val workerUrl = settings?.workerBaseUrl
            val bearerToken = oauthStore.getSnapshot().workerApiToken
            if (!workerUrl.isNullOrBlank()) {
                val endpoint = if (emergency) "/api/streams/${schedule.id}/emergency-stop" else "/api/streams/${schedule.id}/stop"
                workerClient.dispatchCommand(workerUrl, endpoint, JSONObject().put("scheduleId", schedule.id), bearerToken)
            }
            val token = getValidAccessToken()
            if (token != null && !schedule.youtubeBroadcastId.isNullOrBlank()) {
                try {
                    youtubeClient.transitionLiveBroadcast(token, schedule.youtubeBroadcastId, "complete")
                } catch (_: Exception) {
                }
            }
            oauthStore.clearScheduleIngestUrl(schedule.id)
            val newStatus = if (emergency) ScheduleStatus.CANCELLED.name else ScheduleStatus.COMPLETED.name
            repository.updateSchedule(
                schedule.id,
                mapOf(
                    "status" to newStatus,
                    "pausedState" to false,
                    "youtubeConfirmedLive" to false,
                    "recoveryState" to if (emergency) "EMERGENCY_STOPPED" else "COMPLETED"
                )
            )
            repository.logWorkerJob(
                WorkerJob(
                    userId = currentUserId,
                    scheduleId = schedule.id,
                    jobType = if (emergency) "EMERGENCY_STOP" else "AUTO_COMPLETE",
                    status = "COMPLETED",
                    attemptNumber = 1,
                    message = if (emergency) "Emergency stop executed" else "Broadcast completed cleanly",
                    cpuPercent = 2.0,
                    memoryMb = 64,
                    bitrateKbps = 0,
                    droppedFrames = 0
                )
            )
            recordActivity("LIVE", if (emergency) "Emergency Stop Executed" else "Stopped Live Broadcast", "'${schedule.liveTitle}'")
            recordEvent(
                "LIVE_COMPLETED",
                if (emergency) "Emergency Stop Triggered" else "Live Broadcast Completed",
                "'${schedule.liveTitle}' ended cleanly.",
                if (emergency) "WARNING" else "SUCCESS"
            )
            _bannerMessage.value = "'${schedule.liveTitle}' stopped."
        }
    }

    fun cancelSchedule(schedule: LiveSchedule) {
        if (!requirePermission(requiresEditor = true)) return
        viewModelScope.launch {
            repository.updateSchedule(schedule.id, mapOf("status" to ScheduleStatus.CANCELLED.name))
                .onSuccess {
                    recordActivity("LIVE", "Cancelled Live Schedule", "'${schedule.liveTitle}'")
                    recordEvent("LIVE_CANCELLED", "Schedule Cancelled", "'${schedule.liveTitle}' was cancelled.", "INFO")
                    _bannerMessage.value = "Schedule cancelled."
                }
                .onFailure { _bannerMessage.value = it.localizedMessage }
        }
    }

    fun deleteLiveSchedule(scheduleId: String) {
        if (!requirePermission(requiresEditor = true)) return
        viewModelScope.launch {
            repository.deleteSchedule(scheduleId)
                .onSuccess {
                    recordActivity("LIVE", "Deleted Live Schedule", "Removed $scheduleId from calendar")
                    _bannerMessage.value = "Broadcast removed from calendar."
                }
                .onFailure { _bannerMessage.value = it.localizedMessage }
        }
    }

    fun rescheduleBroadcast(schedule: LiveSchedule, newEpochMs: Long) {
        if (!requirePermission(requiresEditor = true)) return
        viewModelScope.launch {
            val lives = (schedulesState.value as? DataState.Success)?.data.orEmpty()
            val uploads = (uploadSchedulesState.value as? DataState.Success)?.data.orEmpty()
            val conflict = DreamiitStudioHelpers.detectScheduleConflict(
                channelId = schedule.channelId,
                targetEpochMs = newEpochMs,
                liveSchedules = lives,
                uploadSchedules = uploads,
                excludeLiveId = schedule.id
            )
            if (conflict != null) {
                _bannerMessage.value = conflict
                return@launch
            }
            val isoFmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            repository.updateSchedule(
                schedule.id,
                mapOf(
                    "scheduledStartEpochMs" to newEpochMs,
                    "scheduledStartIso" to isoFmt.format(Date(newEpochMs)),
                    "status" to ScheduleStatus.SCHEDULED.name
                )
            ).onSuccess {
                recordActivity("LIVE", "Rescheduled Live Broadcast", "'${schedule.liveTitle}'")
                _bannerMessage.value = "Broadcast rescheduled."
            }.onFailure {
                _bannerMessage.value = it.localizedMessage
            }
        }
    }

    fun retryFailedSchedule(schedule: LiveSchedule) {
        if (!requirePermission(requiresEditor = true)) return
        viewModelScope.launch {
            if (schedule.retryCount >= schedule.maxRetries) {
                _bannerMessage.value = "Max retry limit (${schedule.maxRetries}) reached to prevent duplicate streams."
                return@launch
            }
            repository.updateSchedule(
                schedule.id,
                mapOf(
                    "status" to ScheduleStatus.SCHEDULED.name,
                    "retryCount" to (schedule.retryCount + 1),
                    "lastError" to ""
                )
            ).onSuccess {
                _bannerMessage.value = "Re-queued '${schedule.liveTitle}' (attempt ${schedule.retryCount + 1}/${schedule.maxRetries})."
            }
        }
    }

    // --- Stage 3 Feature #7: Live Chat Monitor ---
    fun startLiveChatPolling(broadcastId: String) {
        liveChatPollJob?.cancel()
        liveChatPageToken = null
        liveChatPollJob = viewModelScope.launch {
            while (true) {
                refreshLiveChatOnce(broadcastId)
                delay(6000L)
            }
        }
    }

    fun stopLiveChatPolling() {
        liveChatPollJob?.cancel()
        liveChatPollJob = null
    }

    fun refreshLiveChatNow(broadcastId: String) {
        viewModelScope.launch { refreshLiveChatOnce(broadcastId) }
    }

    private suspend fun refreshLiveChatOnce(broadcastId: String) {
        val token = getValidAccessToken()
        if (token == null) {
            _liveChatStats.value = ChannelViewerStats(statusText = "Authorize YouTube OAuth 2.0 in Settings to load Live Chat.")
            return
        }
        val statsRes = youtubeClient.fetchBroadcastChatAndViewers(token, broadcastId)
        statsRes.onSuccess { stats ->
            _liveChatStats.value = stats
            val chatId = stats.activeLiveChatId ?: return
            youtubeClient.fetchLiveChatMessages(token, chatId, liveChatPageToken).onSuccess { (msgs, next) ->
                liveChatPageToken = next ?: liveChatPageToken
                if (msgs.isNotEmpty()) {
                    val combined = (_liveChatMessages.value + msgs).distinctBy { it.id }.takeLast(100)
                    _liveChatMessages.value = combined
                }
            }.onFailure { err ->
                _liveChatStats.value = stats.copy(statusText = err.localizedMessage ?: "Failed to fetch messages")
            }
        }.onFailure { err ->
            _liveChatStats.value = ChannelViewerStats(statusText = err.localizedMessage ?: "Live chat unavailable")
        }
    }

    fun sendLiveChatMessage(text: String) {
        if (!requirePermission(requiresEditor = true)) return
        val chatId = _liveChatStats.value.activeLiveChatId
        if (chatId.isNullOrBlank()) {
            _bannerMessage.value = "No active liveChatId connected."
            return
        }
        viewModelScope.launch {
            val token = getValidAccessToken() ?: return@launch
            youtubeClient.sendLiveChatMessage(token, chatId, text)
                .onSuccess {
                    recordActivity("LIVE", "Sent Live Chat Message", text.take(80))
                    val msgsRes = youtubeClient.fetchLiveChatMessages(token, chatId, null)
                    msgsRes.onSuccess { (msgs, _) -> _liveChatMessages.value = msgs }
                }
                .onFailure { _bannerMessage.value = it.localizedMessage }
        }
    }

    fun deleteLiveChatMessage(messageId: String) {
        if (!requirePermission(requiresEditor = true)) return
        viewModelScope.launch {
            val token = getValidAccessToken() ?: return@launch
            youtubeClient.deleteLiveChatMessage(token, messageId)
                .onSuccess {
                    _liveChatMessages.value = _liveChatMessages.value.filterNot { it.id == messageId }
                    recordActivity("LIVE", "Deleted Live Chat Message", messageId)
                    _bannerMessage.value = "Chat message deleted."
                }
                .onFailure { _bannerMessage.value = it.localizedMessage }
        }
    }

    fun timeoutLiveChatUser(authorChannelId: String) {
        if (!requirePermission(requiresEditor = true)) return
        val chatId = _liveChatStats.value.activeLiveChatId ?: return
        viewModelScope.launch {
            val token = getValidAccessToken() ?: return@launch
            youtubeClient.banLiveChatUser(token, chatId, authorChannelId)
                .onSuccess {
                    recordActivity("LIVE", "Timed Out Chat User", authorChannelId)
                    _bannerMessage.value = "User timed out for 5 minutes."
                }
                .onFailure { _bannerMessage.value = it.localizedMessage }
        }
    }

    // --- Stage 3 Feature #8: Comment Manager ---
    fun loadComments(searchQuery: String? = null) {
        viewModelScope.launch {
            val token = getValidAccessToken()
            val channels = (channelsState.value as? DataState.Success)?.data.orEmpty()
            val activeChannel = channels.firstOrNull { it.activeChannel } ?: channels.firstOrNull()
            if (token == null || activeChannel == null) {
                _commentsStatus.value = "Authorize and select a YouTube channel in Settings to manage real comments."
                return@launch
            }
            _commentsLoading.value = true
            youtubeClient.fetchChannelComments(token, activeChannel.channelId, searchQuery)
                .onSuccess { list ->
                    _comments.value = list
                    _commentsStatus.value = "Loaded ${list.size} comment threads (${list.count { it.isUnread }} unreplied)."
                }
                .onFailure { err ->
                    _commentsStatus.value = err.localizedMessage ?: "Failed to load comments."
                }
            _commentsLoading.value = false
        }
    }

    fun replyToComment(parentCommentId: String, replyText: String) {
        if (!requirePermission(requiresEditor = true)) return
        viewModelScope.launch {
            val token = getValidAccessToken() ?: return@launch
            youtubeClient.replyToComment(token, parentCommentId, replyText)
                .onSuccess {
                    _comments.value = _comments.value.map {
                        if (it.topCommentId == parentCommentId) it.copy(replyCount = it.replyCount + 1, isUnread = false)
                        else it
                    }
                    recordActivity("ACCOUNT", "Replied to YouTube Comment", replyText.take(80))
                    _bannerMessage.value = "Reply posted to YouTube."
                }
                .onFailure { _bannerMessage.value = it.localizedMessage }
        }
    }

    fun moderateOrDeleteComment(commentId: String, deletePermanently: Boolean) {
        if (!requirePermission(requiresEditor = true)) return
        viewModelScope.launch {
            val token = getValidAccessToken() ?: return@launch
            val result = if (deletePermanently) {
                youtubeClient.deleteComment(token, commentId)
            } else {
                youtubeClient.moderateComment(token, commentId, "heldForReview")
            }
            result.onSuccess {
                _comments.value = _comments.value.filterNot { it.topCommentId == commentId }
                recordActivity("ACCOUNT", if (deletePermanently) "Deleted Comment" else "Held Comment for Review", commentId)
                _bannerMessage.value = if (deletePermanently) "Comment deleted." else "Comment held for review."
            }.onFailure { _bannerMessage.value = it.localizedMessage }
        }
    }

    // --- Stage 3 Feature #9: Channel Branding Manager ---
    fun loadChannelBranding() {
        viewModelScope.launch {
            val token = getValidAccessToken()
            if (token == null) {
                _brandingInfo.value = ChannelBrandingInfo(statusMessage = "Authorize YouTube OAuth 2.0 in Settings to inspect & edit branding.")
                return@launch
            }
            _brandingBusy.value = true
            youtubeClient.fetchChannelBranding(token)
                .onSuccess { _brandingInfo.value = it }
                .onFailure { err ->
                    _brandingInfo.value = ChannelBrandingInfo(statusMessage = err.localizedMessage ?: "Branding fetch failed.")
                }
            _brandingBusy.value = false
        }
    }

    fun saveChannelBranding(description: String, keywords: String, bannerJpegBytes: ByteArray?) {
        if (!requirePermission(requiresOwner = true)) return
        val channelId = _brandingInfo.value.channelId
        if (channelId.isBlank()) {
            _bannerMessage.value = "Load connected channel branding first."
            return
        }
        viewModelScope.launch {
            val token = getValidAccessToken() ?: return@launch
            _brandingBusy.value = true
            youtubeClient.uploadBannerAndUpdateBranding(token, channelId, description, keywords, bannerJpegBytes)
                .onSuccess { newBanner ->
                    _brandingInfo.value = _brandingInfo.value.copy(
                        description = description,
                        keywords = keywords,
                        bannerImageUrl = newBanner ?: _brandingInfo.value.bannerImageUrl,
                        statusMessage = "Channel branding updated on YouTube."
                    )
                    recordActivity("ACCOUNT", "Updated Channel Branding", "Updated YouTube banner/description for $channelId")
                    _bannerMessage.value = "Channel branding uploaded to YouTube!"
                }
                .onFailure { _bannerMessage.value = it.localizedMessage }
            _brandingBusy.value = false
        }
    }

    // --- Stage 3 Feature #10: Video Comparison ---
    fun loadVideosForComparison() {
        viewModelScope.launch {
            val token = getValidAccessToken()
            if (token == null) {
                _comparisonStatus.value = "Authorize YouTube OAuth 2.0 in Settings to compare real YouTube video stats."
                return@launch
            }
            _comparisonStatus.value = "Loading real YouTube video metrics..."
            youtubeClient.fetchChannelVideosForComparison(token)
                .onSuccess { list ->
                    _comparedVideos.value = list
                    _comparisonStatus.value = if (list.isEmpty()) "No published YouTube videos returned for comparison."
                    else "Loaded ${list.size} YouTube videos with real views, likes, comments & watch time."
                }
                .onFailure { err ->
                    _comparisonStatus.value = err.localizedMessage ?: "Comparison fetch failed."
                }
        }
    }

    // --- Live Playlists (Module 8) ---
    fun createPlaylist(title: String, videos: List<CloudVideo>, endOnFinish: Boolean, loop: Boolean) {
        if (!requirePermission(requiresEditor = true)) return
        viewModelScope.launch {
            val channels = (channelsState.value as? DataState.Success)?.data.orEmpty()
            val activeChannelId = channels.firstOrNull { it.activeChannel }?.channelId ?: "unassigned"
            val pl = LivePlaylist(
                userId = currentUserId,
                channelId = activeChannelId,
                title = title,
                videoIds = videos.map { it.id },
                videoTitles = videos.map { it.title },
                endBroadcastOnFinish = endOnFinish,
                loopPlaylist = loop,
                currentIndex = 0,
                status = "IDLE"
            )
            repository.createPlaylist(pl)
                .onSuccess {
                    recordActivity("LIVE", "Created Live Playlist", "'$title' (${videos.size} videos)")
                    _bannerMessage.value = "Created live playlist '$title'."
                }
                .onFailure { _bannerMessage.value = it.localizedMessage }
        }
    }

    fun movePlaylistItem(playlist: LivePlaylist, fromIndex: Int, toIndex: Int) {
        if (!requirePermission(requiresEditor = true)) return
        if (toIndex !in playlist.videoIds.indices || fromIndex !in playlist.videoIds.indices) return
        val ids = playlist.videoIds.toMutableList()
        val titles = playlist.videoTitles.toMutableList()
        val idItem = ids.removeAt(fromIndex)
        val titleItem = titles.removeAt(fromIndex)
        ids.add(toIndex, idItem)
        titles.add(toIndex, titleItem)
        viewModelScope.launch {
            repository.updatePlaylist(playlist.id, mapOf("videoIds" to ids, "videoTitles" to titles))
        }
    }

    fun removePlaylistItem(playlist: LivePlaylist, index: Int) {
        if (!requirePermission(requiresEditor = true)) return
        if (index !in playlist.videoIds.indices) return
        val ids = playlist.videoIds.toMutableList().apply { removeAt(index) }
        val titles = playlist.videoTitles.toMutableList().apply { removeAt(index) }
        viewModelScope.launch {
            repository.updatePlaylist(playlist.id, mapOf("videoIds" to ids, "videoTitles" to titles))
        }
    }

    fun togglePlaylistPlayback(playlist: LivePlaylist) {
        if (!requirePermission(requiresEditor = true)) return
        val nextStatus = if (playlist.status == "PLAYING") "IDLE" else "PLAYING"
        viewModelScope.launch {
            repository.updatePlaylist(playlist.id, mapOf("status" to nextStatus))
        }
    }

    fun advancePlaylistIndex(playlist: LivePlaylist) {
        if (!requirePermission(requiresEditor = true)) return
        if (playlist.videoIds.isEmpty()) return
        val nextIdx = playlist.currentIndex + 1
        viewModelScope.launch {
            if (nextIdx >= playlist.videoIds.size) {
                if (playlist.loopPlaylist) {
                    repository.updatePlaylist(playlist.id, mapOf("currentIndex" to 0))
                } else {
                    repository.updatePlaylist(
                        playlist.id,
                        mapOf(
                            "currentIndex" to (playlist.videoIds.size - 1),
                            "status" to if (playlist.endBroadcastOnFinish) "COMPLETED" else "IDLE"
                        )
                    )
                }
            } else {
                repository.updatePlaylist(playlist.id, mapOf("currentIndex" to nextIdx))
            }
        }
    }

    fun deletePlaylist(playlistId: String) {
        if (!requirePermission(requiresEditor = true)) return
        viewModelScope.launch {
            repository.deletePlaylist(playlistId)
        }
    }

    // --- Health, Analytics, Revenue (#15), Backups (#16), Team (#18), Dashboard (#20), Settings ---
    fun refreshWorkerHealth() {
        viewModelScope.launch {
            val url = (settingsState.value as? DataState.Success)?.data?.workerBaseUrl
            _workerHealth.value = workerClient.fetchHealth(url)
        }
    }

    fun loadAnalytics(days: Int) {
        viewModelScope.launch {
            val token = getValidAccessToken()
            if (token == null) {
                _analyticsState.value = YouTubeAnalyticsSnapshot(
                    authorized = false,
                    dateRangeDays = days,
                    statusMessage = "Unavailable — authorize your YouTube channel via OAuth 2.0 in Settings to view real YouTube Analytics."
                )
                return@launch
            }
            _analyticsState.value = youtubeClient.fetchAnalyticsReport(token, days)
        }
    }

    fun loadRevenue(days: Int) {
        if (!requirePermission(requiresOwner = true)) return
        viewModelScope.launch {
            val token = getValidAccessToken()
            if (token == null) {
                _revenueState.value = RevenueSnapshot(
                    authorized = false,
                    dateRangeDays = days,
                    statusMessage = "Unavailable — authorize YouTube OAuth 2.0 (including yt-analytics-monetary.readonly) in Settings."
                )
                return@launch
            }
            _revenueState.value = youtubeClient.fetchRevenueReport(token, days)
        }
    }

    fun createBackupSnapshot(label: String, retentionDays: Int) {
        if (!requirePermission(requiresOwner = true)) return
        viewModelScope.launch {
            val videos = (videosState.value as? DataState.Success)?.data.orEmpty()
            val schedules = (schedulesState.value as? DataState.Success)?.data.orEmpty()
            val playlists = (playlistsState.value as? DataState.Success)?.data.orEmpty()
            val settings = (settingsState.value as? DataState.Success)?.data
            repository.createBackupSnapshot(label, videos, schedules, playlists, retentionDays, settings)
                .onSuccess {
                    recordActivity("BACKUP", "Created Manual Backup", "$label (SHA-256 verified)")
                    _bannerMessage.value = "Metadata & schedule backup snapshot saved with SHA-256 checksum."
                }
                .onFailure { _bannerMessage.value = it.localizedMessage }
        }
    }

    fun restoreBackup(backup: BackupRecord) {
        if (!requirePermission(requiresOwner = true)) return
        viewModelScope.launch {
            val schedules = (schedulesState.value as? DataState.Success)?.data.orEmpty()
            repository.restoreFromBackup(backup, schedules)
                .onSuccess { count ->
                    recordActivity("BACKUP", "Restored Backup", "Restored '${backup.label}' ($count missing schedules recovered)")
                    _bannerMessage.value = "Restored settings & recovered $count schedule(s) from '${backup.label}'."
                }
                .onFailure { _bannerMessage.value = it.localizedMessage }
        }
    }

    fun saveDashboardLayout(widgetOrder: List<String>, hiddenWidgets: List<String>) {
        viewModelScope.launch {
            repository.updateSettings(
                mapOf(
                    "dashboardWidgets" to widgetOrder,
                    "hiddenWidgets" to hiddenWidgets
                )
            ).onSuccess {
                recordActivity("ACCOUNT", "Customized Dashboard", "${widgetOrder.size - hiddenWidgets.size} widgets visible")
                _bannerMessage.value = "Dashboard layout saved."
            }.onFailure { _bannerMessage.value = it.localizedMessage }
        }
    }

    fun updateUserSettings(updates: Map<String, Any>) {
        if (!requirePermission(requiresOwner = true)) return
        viewModelScope.launch {
            repository.updateSettings(updates)
                .onSuccess {
                    recordActivity("ACCOUNT", "Updated Platform Settings", updates.keys.joinToString(", "))
                    _bannerMessage.value = "Settings saved."
                }
                .onFailure { _bannerMessage.value = it.localizedMessage }
        }
    }

    fun saveWorkerApiToken(token: String) {
        if (!requirePermission(requiresOwner = true)) return
        viewModelScope.launch {
            oauthStore.saveWorkerApiToken(token)
            recordActivity("SECURITY", "Updated Worker API Bearer Token", if (token.isBlank()) "Cleared token" else "Saved locally in encrypted DataStore")
            _bannerMessage.value = if (token.isBlank()) "Worker API token cleared." else "Worker API Bearer token saved locally."
        }
    }

    fun setThemeMode(mode: String) {
        val safe = if (mode == "LIGHT") "LIGHT" else "DARK"
        viewModelScope.launch {
            repository.updateSettings(mapOf("themeMode" to safe))
                .onSuccess {
                    _bannerMessage.value = if (safe == "LIGHT") "Switched to Light Mode." else "Switched to Dark Mode."
                }
        }
    }

    fun setInfraCostApproved(approved: Boolean) {
        if (!requirePermission(requiresOwner = true)) return
        viewModelScope.launch {
            repository.updateSettings(mapOf("infraCostApproved" to approved))
                .onSuccess {
                    recordActivity("CLOUD", "Admin Cloud Infrastructure Approval", "infraCostApproved=$approved")
                    _bannerMessage.value = if (approved) {
                        "Cloud streaming infrastructure approved by Owner."
                    } else {
                        "Cloud streaming infrastructure approval revoked."
                    }
                }
        }
    }

    fun addTeamMember(name: String, email: String, role: TeamRole, notes: String) {
        if (!requirePermission(requiresOwner = true)) return
        if (email.equals("harshsingh223388@gmail.com", ignoreCase = true)) {
            _bannerMessage.value = "Harsh Singh is already the permanent Primary Owner."
            return
        }
        viewModelScope.launch {
            repository.addTeamMember(
                TeamMember(
                    userId = currentUserId,
                    memberName = name.trim(),
                    memberEmail = email.trim(),
                    role = role.name,
                    activeMember = true,
                    addedBy = "Harsh Singh",
                    notes = notes.trim().takeIf { it.isNotBlank() }
                )
            ).onSuccess {
                recordActivity("TEAM", "Added Team Member", "$name ($email) as ${role.name}")
                _bannerMessage.value = "Added $name as ${role.label}."
            }.onFailure { _bannerMessage.value = it.localizedMessage }
        }
    }

    fun updateTeamMember(member: TeamMember, newRole: TeamRole, active: Boolean) {
        if (!requirePermission(requiresOwner = true)) return
        viewModelScope.launch {
            repository.updateTeamMember(
                member.id,
                mapOf("role" to newRole.name, "activeMember" to active)
            ).onSuccess {
                recordActivity("TEAM", "Updated Team Role", "${member.memberName} -> ${newRole.name} (active=$active)")
                _bannerMessage.value = "Updated ${member.memberName}'s access."
            }.onFailure { _bannerMessage.value = it.localizedMessage }
        }
    }

    fun removeTeamMember(member: TeamMember) {
        if (!requirePermission(requiresOwner = true)) return
        viewModelScope.launch {
            repository.deleteTeamMember(member.id)
                .onSuccess {
                    recordActivity("TEAM", "Removed Team Member", "${member.memberName} (${member.memberEmail})")
                    _bannerMessage.value = "Removed ${member.memberName} from team."
                }
                .onFailure { _bannerMessage.value = it.localizedMessage }
        }
    }

    fun setActiveRolePreview(role: TeamRole) {
        // Switching back to OWNER must always be permitted so the owner isn't locked out after testing EDITOR/VIEWER!
        viewModelScope.launch {
            repository.updateSettings(mapOf("activeRolePreview" to role.name))
                .onSuccess {
                    recordActivity("TEAM", "Switched Active Role Context", "Active role set to ${role.name}")
                    _bannerMessage.value = "Active role mode: ${role.label}"
                }
                .onFailure { _bannerMessage.value = it.localizedMessage }
        }
    }

    fun markNotificationRead(id: String) {
        viewModelScope.launch { repository.markNotificationRead(id) }
    }

    fun uploadThumbnailToYouTubeVideo(videoId: String, jpegBytes: ByteArray) {
        if (!requirePermission(requiresEditor = true)) return
        viewModelScope.launch {
            val token = getValidAccessToken()
            if (token == null) {
                _bannerMessage.value = "Connect YouTube OAuth in Settings to upload thumbnails directly to YouTube."
                return@launch
            }
            youtubeClient.uploadCustomThumbnail(token, videoId, jpegBytes)
                .onSuccess {
                    recordActivity("UPLOAD", "Uploaded Custom Thumbnail", "Video $videoId")
                    recordEvent("THUMBNAIL_UPLOADED", "Thumbnail Published", "Uploaded custom thumbnail to YouTube video $videoId.", "SUCCESS")
                    _bannerMessage.value = "Thumbnail uploaded to YouTube ($videoId)!"
                }
                .onFailure { _bannerMessage.value = it.localizedMessage }
        }
    }

    suspend fun exportTextArtifact(subdir: String, fileName: String, content: String): Pair<File, Uri> {
        val res = MediaEditorEngine.exportTextFile(appContext, subdir, fileName, content)
        recordActivity("MEDIA", "Exported File", fileName)
        return res
    }

    // --- V4.1 Module 1: Video Performance, Policy Alerts, Competitor Tracker & AI Copyright Risk ---
    fun loadDetailedVideoPerformance() {
        viewModelScope.launch {
            val token = getValidAccessToken()
            if (token == null) {
                _videoPerformanceStatus.value = "Authorize YouTube OAuth 2.0 in Settings to load real Video Performance & Policy Alerts."
                return@launch
            }
            _videoPerformanceStatus.value = "Fetching video stats, engagement & policy status..."
            youtubeClient.fetchDetailedVideosAndPolicies(token)
                .onSuccess { list ->
                    _videoPerformanceList.value = list
                    val derivedAlerts = mutableListOf<YouTubePolicyAlert>()
                    list.forEach { vid ->
                        vid.policyAlerts.forEach { msg ->
                            derivedAlerts.add(
                                YouTubePolicyAlert(
                                    severity = if (!vid.rejectionReason.isNullOrBlank()) "ERROR" else "WARNING",
                                    source = vid.title,
                                    message = msg,
                                    actionHint = "Inspect video visibility/licensing in YouTube Studio"
                                )
                            )
                        }
                    }
                    _policyAlerts.value = derivedAlerts
                    _videoPerformanceStatus.value = if (list.isEmpty()) {
                        "No uploaded videos found on connected YouTube channel."
                    } else {
                        "Loaded ${list.size} video performance reports (${derivedAlerts.size} policy alert(s))."
                    }
                }
                .onFailure { err ->
                    _videoPerformanceStatus.value = err.localizedMessage ?: "Failed to load video performance."
                }
        }
    }

    fun addCompetitorChannel(query: String) {
        if (!requirePermission(requiresEditor = true)) return
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            _bannerMessage.value = "Enter a YouTube @handle, channel URL, or UC... channel ID."
            return
        }
        viewModelScope.launch {
            _competitorBusy.value = true
            val token = getValidAccessToken()
            youtubeClient.fetchPublicCompetitorChannel(token, trimmed, currentUserId)
                .onSuccess { comp ->
                    repository.upsertCompetitor(comp)
                        .onSuccess {
                            recordActivity("ACCOUNT", "Tracked Competitor Channel", "${comp.title} (${comp.subscriberCount} subs)")
                            _bannerMessage.value = "Added '${comp.title}' to Competitor Tracker."
                        }
                        .onFailure { _bannerMessage.value = it.localizedMessage }
                }
                .onFailure { _bannerMessage.value = it.localizedMessage }
            _competitorBusy.value = false
        }
    }

    fun refreshCompetitors() {
        viewModelScope.launch {
            val token = getValidAccessToken()
            if (token == null) {
                _bannerMessage.value = "Authorize YouTube OAuth 2.0 in Settings to refresh competitor metrics."
                return@launch
            }
            val list = (competitorsState.value as? DataState.Success)?.data.orEmpty()
            if (list.isEmpty()) return@launch
            _competitorBusy.value = true
            var updated = 0
            for (comp in list) {
                youtubeClient.fetchPublicCompetitorChannel(token, comp.channelId, currentUserId)
                    .onSuccess { fresh ->
                        if (repository.upsertCompetitor(fresh).isSuccess) updated++
                    }
            }
            _competitorBusy.value = false
            _bannerMessage.value = "Refreshed $updated competitor channel(s) from YouTube API."
        }
    }

    fun removeCompetitor(competitorId: String) {
        if (!requirePermission(requiresEditor = true)) return
        viewModelScope.launch {
            repository.deleteCompetitor(competitorId)
                .onSuccess {
                    recordActivity("ACCOUNT", "Removed Competitor Channel", competitorId)
                    _bannerMessage.value = "Competitor channel removed."
                }
                .onFailure { _bannerMessage.value = it.localizedMessage }
        }
    }

    fun runCopyrightRiskScan(
        videoId: String,
        videoTitle: String,
        description: String,
        tags: List<String>,
        audioNotes: String,
        transcriptOrNotes: String
    ) {
        if (!requirePermission(requiresEditor = true)) return
        if (videoTitle.isBlank()) {
            _bannerMessage.value = "Enter a video title or pick a video to scan for copyright & policy risk."
            return
        }
        viewModelScope.launch {
            _copyrightScanBusy.value = true
            _geminiCallsSession.value += 1
            try {
                val ai = DreamiitAiRepository()
                val res = ai.analyzeCopyrightAndPolicyRisk(
                    videoTitle = videoTitle.trim(),
                    description = description.trim(),
                    tags = tags,
                    audioNotes = audioNotes.trim(),
                    transcriptOrNotes = transcriptOrNotes.trim()
                )
                val report = CopyrightRiskReport(
                    userId = currentUserId,
                    videoId = videoId.ifBlank { "manual" },
                    videoTitle = videoTitle.trim(),
                    riskLevel = res.riskLevel,
                    summary = res.summary,
                    triggers = res.triggers,
                    mitigations = res.mitigations,
                    policyFlags = res.policyFlags
                )
                repository.addCopyrightReport(report)
                recordActivity("COPYRIGHT", "AI Copyright Risk Scan", "'$videoTitle' -> ${res.riskLevel}")
                if (res.riskLevel in listOf("HIGH", "MEDIUM")) {
                    recordEvent(
                        "COPYRIGHT_RISK_${res.riskLevel}",
                        "Copyright Risk Alert (${res.riskLevel})",
                        "'$videoTitle': ${res.summary.take(180)}",
                        if (res.riskLevel == "HIGH") "ERROR" else "WARNING"
                    )
                }
                _bannerMessage.value = "Copyright pre-check complete: ${res.riskLevel} risk."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _bannerMessage.value = "Copyright scan failed: ${e.localizedMessage}"
            } finally {
                _copyrightScanBusy.value = false
            }
        }
    }

    fun deleteCopyrightReport(reportId: String) {
        if (!requirePermission(requiresEditor = true)) return
        viewModelScope.launch {
            repository.deleteCopyrightReport(reportId)
                .onSuccess { _bannerMessage.value = "Copyright report deleted." }
                .onFailure { _bannerMessage.value = it.localizedMessage }
        }
    }

    // --- V4.1 Module 2: Live Stream Health Poll & Auto-Retry Toggle ---
    fun refreshLiveStreamHealth() {
        viewModelScope.launch {
            refreshWorkerHealth()
            val schedules = (schedulesState.value as? DataState.Success)?.data.orEmpty()
            val target = schedules.firstOrNull { it.status == ScheduleStatus.LIVE.name }
                ?: schedules.firstOrNull { !it.youtubeBroadcastId.isNullOrBlank() }
            if (target == null) {
                _bannerMessage.value = "No active or YouTube-bound broadcast to probe."
                return@launch
            }
            val token = getValidAccessToken()
            if (token == null) {
                _bannerMessage.value = "Authorize YouTube OAuth 2.0 in Settings to probe YouTube ingest health."
                return@launch
            }
            youtubeClient.fetchLiveStreamHealth(token, target.youtubeBroadcastId, target.youtubeStreamId)
                .onSuccess {
                    _ytStreamHealth.value = it
                    _bannerMessage.value = "Stream health: ${it.healthStatus} (broadcast: ${it.lifeCycleStatus})."
                }
                .onFailure { _bannerMessage.value = it.localizedMessage }
        }
    }

    fun toggleAutoStreamRetry(enabled: Boolean) {
        if (!requirePermission(requiresOwner = true)) return
        viewModelScope.launch {
            repository.updateSettings(mapOf("autoStreamRetryEnabled" to enabled))
                .onSuccess {
                    recordActivity("LIVE", "Updated Auto Stream Retry", "autoStreamRetryEnabled=$enabled")
                    _bannerMessage.value = if (enabled) "Auto Stream Retry enabled." else "Auto Stream Retry disabled."
                }
        }
    }

    // --- V4.1 Module 3: Security Management (2SV, Known Devices, Active Sessions, Secure Logout) ---
    fun trustCurrentDevice() {
        if (!requirePermission(requiresOwner = true)) return
        viewModelScope.launch {
            val s = (settingsState.value as? DataState.Success)?.data
            val updatedList = (s?.knownDevices.orEmpty() + currentDeviceName).distinct().take(20)
            repository.updateSettings(mapOf("knownDevices" to updatedList))
            repository.upsertSession(
                currentSessionId,
                UserSession(
                    userId = currentUserId,
                    deviceName = currentDeviceName,
                    platform = "Android ${Build.VERSION.RELEASE}",
                    sessionTokenHash = currentSessionId,
                    lastActiveEpochMs = System.currentTimeMillis(),
                    revoked = false,
                    trustedDevice = true,
                    networkHint = "Verified by Owner"
                )
            )
            repository.logAuditEvent("TRUSTED_DEVICE_ADDED", currentDeviceName, "SUCCESS", "Marked device trusted")
            recordActivity("SECURITY", "Trusted Current Device", currentDeviceName)
            _bannerMessage.value = "'$currentDeviceName' added to trusted devices."
        }
    }

    fun confirmTwoFactorVerified(verified: Boolean) {
        if (!requirePermission(requiresOwner = true)) return
        viewModelScope.launch {
            repository.updateSettings(
                mapOf(
                    "twoFactorConfirmed" to verified,
                    "twoFactorRemindedAtMs" to System.currentTimeMillis()
                )
            ).onSuccess {
                recordActivity("SECURITY", "2SV Verification Status Updated", "twoFactorConfirmed=$verified")
                _bannerMessage.value = if (verified) {
                    "Two-Step Verification confirmed for this Google Account."
                } else {
                    "Two-Step Verification marked pending."
                }
            }
        }
    }

    fun snoozeTwoFactorReminder() {
        viewModelScope.launch {
            repository.updateSettings(mapOf("twoFactorRemindedAtMs" to System.currentTimeMillis()))
            _bannerMessage.value = "2SV reminder snoozed for 7 days."
        }
    }

    fun revokeSession(sessionId: String) {
        if (!requirePermission(requiresOwner = true)) return
        viewModelScope.launch {
            repository.revokeSession(sessionId)
                .onSuccess {
                    repository.logAuditEvent("SESSION_REVOKED", currentDeviceName, "WARNING", "Revoked session $sessionId")
                    recordActivity("SECURITY", "Revoked Active Session", sessionId)
                    _bannerMessage.value = "Session revoked."
                }
                .onFailure { _bannerMessage.value = it.localizedMessage }
        }
    }

    fun revokeAllOtherSessions() {
        if (!requirePermission(requiresOwner = true)) return
        viewModelScope.launch {
            val others = (sessionsState.value as? DataState.Success)?.data.orEmpty()
                .filter { it.id != currentSessionId && !it.revoked }
                .map { it.id }
            repository.revokeSessions(others)
                .onSuccess { count ->
                    repository.logAuditEvent("REVOKE_OTHER_SESSIONS", currentDeviceName, "WARNING", "Revoked $count other session(s)")
                    recordActivity("SECURITY", "Signed Out Other Devices", "Revoked $count active session(s)")
                    _bannerMessage.value = if (count > 0) "Revoked $count other session(s)." else "No other active sessions found."
                }
        }
    }

    suspend fun prepareSecureLogout() {
        try {
            repository.revokeSession(currentSessionId)
            repository.logAuditEvent("SECURE_LOGOUT", currentDeviceName, "SUCCESS", "User triggered secure logout")
            repository.logActivity("SECURITY", "Secure Logout", "Harsh Singh", activeRole.value.name, "Signed out on $currentDeviceName")
            oauthStore.clearAll()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }

    // --- V4.1 Module 4: Connected Billing & API Quota Tracking ---
    fun refreshCloudBilling() {
        viewModelScope.launch {
            val s = (settingsState.value as? DataState.Success)?.data
            val report = workerClient.fetchBilling(s?.workerBaseUrl, s?.billingConnectedUrl)
            _connectedBilling.value = report
        }
    }

    fun trackGeminiInvocation() {
        _geminiCallsSession.value += 1
    }

    fun recordYouTubeQuotaUnits(units: Int) {
        if (units <= 0) return
        viewModelScope.launch {
            val s = (settingsState.value as? DataState.Success)?.data
            val today = currentUtcDateString()
            val prevUsed = if (s?.apiQuotaResetDate == today) (s.apiUnitsUsedToday ?: 0) else 0
            val newUsed = (prevUsed + units).coerceAtLeast(0)
            repository.updateSettings(
                mapOf(
                    "apiUnitsUsedToday" to newUsed,
                    "apiQuotaResetDate" to today
                )
            )
            val limit = s?.apiQuotaDailyLimit ?: 10_000
            if (newUsed * 100 >= limit * 80 && prevUsed * 100 < limit * 80) {
                recordEvent(
                    "API_QUOTA_80",
                    "YouTube API Quota Warning (80%+)",
                    "Used $newUsed / $limit daily quota units today ($today UTC).",
                    "WARNING"
                )
            }
        }
    }

    fun updateApiQuotaLimit(limit: Int) {
        if (!requirePermission(requiresOwner = true)) return
        viewModelScope.launch {
            val safe = limit.coerceIn(100, 10_000_000)
            repository.updateSettings(mapOf("apiQuotaDailyLimit" to safe))
                .onSuccess {
                    recordActivity("CLOUD", "Updated API Quota Limit", "Daily limit set to $safe units")
                    _bannerMessage.value = "Daily YouTube API quota limit saved ($safe units)."
                }
        }
    }

    private suspend fun recordActivity(category: String, action: String, details: String) {
        repository.logActivity(
            category = category,
            action = action,
            actorName = "Harsh Singh",
            actorRole = activeRole.value.name,
            details = details
        )
    }

    private suspend fun recordEvent(eventType: String, title: String, body: String, severity: String) {
        repository.addNotification(eventType, title, body, severity)
        val enabled = (settingsState.value as? DataState.Success)?.data?.notificationsEnabled ?: true
        if (enabled) {
            NotificationHelper.showSystemNotification(appContext, eventType.hashCode(), title, body)
        }
    }

    override fun onCleared() {
        liveChatPollJob?.cancel()
        super.onCleared()
    }

    companion object {
        private const val TAG = "DreamiitVM"

        fun provideFactory(context: Context, userId: String): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    val appCtx = context.applicationContext
                    val db = FirebaseFirestore.getInstance(
                        appCtx.getString(R.string.firestore_database_id)
                    )
                    var vmHolder: DreamiitViewModel? = null
                    val ytClient = YouTubeApiClient { units ->
                        vmHolder?.recordYouTubeQuotaUnits(units)
                    }
                    DreamiitViewModel(
                        repository = DreamiitRepository(db),
                        oauthStore = OAuthTokenStore(appCtx),
                        youtubeClient = ytClient,
                        workerClient = CloudWorkerClient(),
                        appContext = appCtx,
                        currentUserId = userId
                    ).also { vmHolder = it }
                }
            }
    }
}
