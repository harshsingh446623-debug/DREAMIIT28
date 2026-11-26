package com.example.ui

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.auth.signOut
import com.example.data.model.LiveSchedule
import com.example.data.model.PrivacyStatus
import com.example.data.model.StreamingProfile
import com.example.localization.EnglishStrings
import com.example.localization.HindiStrings
import com.example.localization.LocalAppStrings
import com.example.ui.components.CreateVideoBottomSheet
import com.example.ui.components.DreamiitTopHeader
import com.example.ui.screens.AdminControlCenterScreen
import com.example.ui.screens.AiStudioScreen
import com.example.ui.screens.AnalyticsOpsScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.LiveHubScreen
import com.example.ui.screens.LiveSchedulerBottomSheet
import com.example.ui.screens.LiveStatusScreen
import com.example.ui.screens.MediaLabScreen
import com.example.ui.screens.NotificationsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.UploadVideoBottomSheet
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VoidBlack
import com.example.ui.viewmodel.AiStudioViewModel
import com.example.ui.viewmodel.DataState
import com.example.ui.viewmodel.DreamiitViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch

const val ROUTE_DASHBOARD = "dashboard"
const val ROUTE_LIVE_HUB = "live_hub"
const val ROUTE_LIVE_STATUS = "live_status"
const val ROUTE_LIBRARY = "library"
const val ROUTE_AI_STUDIO = "ai_studio"
const val ROUTE_ANALYTICS_OPS = "analytics_ops"
const val ROUTE_NOTIFICATIONS = "notifications"
const val ROUTE_SETTINGS = "settings"
const val ROUTE_MEDIA_LAB = "media_lab"
const val ROUTE_ADMIN_CONTROL = "admin_control"

@Composable
fun DreamiitApp(
    pendingOAuthUri: Uri?,
    onConsumeOAuthUri: () -> Unit
) {
    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose { Firebase.auth.removeAuthStateListener(listener) }
    }

    if (currentUser == null) {
        AuthScreen(onAuthSuccess = { currentUser = Firebase.auth.currentUser })
    } else {
        val user = currentUser!!
        key(user.uid) {
            AuthenticatedStudioRoot(
                userId = user.uid,
                userEmail = user.email ?: "harshsingh223388@gmail.com",
                pendingOAuthUri = pendingOAuthUri,
                onConsumeOAuthUri = onConsumeOAuthUri
            )
        }
    }
}

@Composable
private fun AuthenticatedStudioRoot(
    userId: String,
    userEmail: String,
    pendingOAuthUri: Uri?,
    onConsumeOAuthUri: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val viewModel: DreamiitViewModel = viewModel(
        key = userId,
        factory = DreamiitViewModel.provideFactory(context, userId)
    )
    val aiViewModel: AiStudioViewModel = viewModel(
        key = "ai_$userId",
        factory = AiStudioViewModel.provideFactory()
    )

    LaunchedEffect(pendingOAuthUri) {
        if (pendingOAuthUri != null) {
            viewModel.handleOAuthRedirectUri(pendingOAuthUri)
            onConsumeOAuthUri()
        }
    }

    val settingsDs by viewModel.settingsState.collectAsStateWithLifecycle()
    val channelsDs by viewModel.channelsState.collectAsStateWithLifecycle()
    val videosDs by viewModel.videosState.collectAsStateWithLifecycle()
    val schedulesDs by viewModel.schedulesState.collectAsStateWithLifecycle()
    val uploadSchedulesDs by viewModel.uploadSchedulesState.collectAsStateWithLifecycle()
    val playlistsDs by viewModel.playlistsState.collectAsStateWithLifecycle()
    val jobsDs by viewModel.jobsState.collectAsStateWithLifecycle()
    val notificationsDs by viewModel.notificationsState.collectAsStateWithLifecycle()
    val auditLogsDs by viewModel.auditLogsState.collectAsStateWithLifecycle()
    val backupsDs by viewModel.backupsState.collectAsStateWithLifecycle()
    val teamMembersDs by viewModel.teamMembersState.collectAsStateWithLifecycle()
    val activityLogsDs by viewModel.activityLogsState.collectAsStateWithLifecycle()
    val competitorsDs by viewModel.competitorsState.collectAsStateWithLifecycle()
    val sessionsDs by viewModel.sessionsState.collectAsStateWithLifecycle()
    val copyrightReportsDs by viewModel.copyrightReportsState.collectAsStateWithLifecycle()

    val settings = (settingsDs as? DataState.Success)?.data
    val channels = (channelsDs as? DataState.Success)?.data.orEmpty()
    val videos = (videosDs as? DataState.Success)?.data.orEmpty()
    val schedules = (schedulesDs as? DataState.Success)?.data.orEmpty()
    val uploadSchedules = (uploadSchedulesDs as? DataState.Success)?.data.orEmpty()
    val playlists = (playlistsDs as? DataState.Success)?.data.orEmpty()
    val jobs = (jobsDs as? DataState.Success)?.data.orEmpty()
    val notifications = (notificationsDs as? DataState.Success)?.data.orEmpty()
    val auditLogs = (auditLogsDs as? DataState.Success)?.data.orEmpty()
    val backups = (backupsDs as? DataState.Success)?.data.orEmpty()
    val teamMembers = (teamMembersDs as? DataState.Success)?.data.orEmpty()
    val activityLogs = (activityLogsDs as? DataState.Success)?.data.orEmpty()
    val competitors = (competitorsDs as? DataState.Success)?.data.orEmpty()
    val sessions = (sessionsDs as? DataState.Success)?.data.orEmpty()
    val copyrightReports = (copyrightReportsDs as? DataState.Success)?.data.orEmpty()

    val oauthCreds by viewModel.oauthCredentials.collectAsStateWithLifecycle()
    val analytics by viewModel.analyticsState.collectAsStateWithLifecycle()
    val revenue by viewModel.revenueState.collectAsStateWithLifecycle()
    val workerHealth by viewModel.workerHealth.collectAsStateWithLifecycle()
    val cost by viewModel.costBreakdown.collectAsStateWithLifecycle()
    val uploadState by viewModel.uploadState.collectAsStateWithLifecycle()
    val mediaLabState by viewModel.mediaLabState.collectAsStateWithLifecycle()
    val liveElapsedSec by viewModel.liveElapsedSec.collectAsStateWithLifecycle()
    val countdownState by viewModel.countdownState.collectAsStateWithLifecycle()
    val storageAlert by viewModel.storageAlertReport.collectAsStateWithLifecycle()
    val activeRole by viewModel.activeRole.collectAsStateWithLifecycle()
    val liveChatMessages by viewModel.liveChatMessages.collectAsStateWithLifecycle()
    val liveChatStats by viewModel.liveChatStats.collectAsStateWithLifecycle()
    val comments by viewModel.comments.collectAsStateWithLifecycle()
    val commentsStatus by viewModel.commentsStatus.collectAsStateWithLifecycle()
    val commentsLoading by viewModel.commentsLoading.collectAsStateWithLifecycle()
    val brandingInfo by viewModel.brandingInfo.collectAsStateWithLifecycle()
    val brandingBusy by viewModel.brandingBusy.collectAsStateWithLifecycle()
    val comparedVideos by viewModel.comparedVideos.collectAsStateWithLifecycle()
    val comparisonStatus by viewModel.comparisonStatus.collectAsStateWithLifecycle()
    val videoPerformanceList by viewModel.videoPerformanceList.collectAsStateWithLifecycle()
    val videoPerformanceStatus by viewModel.videoPerformanceStatus.collectAsStateWithLifecycle()
    val policyAlerts by viewModel.policyAlerts.collectAsStateWithLifecycle()
    val ytStreamHealth by viewModel.ytStreamHealth.collectAsStateWithLifecycle()
    val connectedBilling by viewModel.connectedBilling.collectAsStateWithLifecycle()
    val apiUsageReport by viewModel.apiUsageReport.collectAsStateWithLifecycle()
    val liveConnectionAlert by viewModel.liveConnectionAlert.collectAsStateWithLifecycle()
    val securityAlertSummary by viewModel.securityAlertSummary.collectAsStateWithLifecycle()
    val competitorBusy by viewModel.competitorBusy.collectAsStateWithLifecycle()
    val copyrightScanBusy by viewModel.copyrightScanBusy.collectAsStateWithLifecycle()
    val bannerMessage by viewModel.bannerMessage.collectAsStateWithLifecycle()

    LaunchedEffect(securityAlertSummary.revokedSessionDetected) {
        if (securityAlertSummary.revokedSessionDetected) {
            signOut(
                credentialManager = credentialManager,
                scope = scope,
                onBeforeSignOut = { viewModel.prepareSecureLogout() }
            )
        }
    }

    val activeChannel = channels.firstOrNull { it.activeChannel } ?: channels.firstOrNull()
    val unreadCount = notifications.count { !it.readState }

    val strings = if (settings?.language == "hi") HindiStrings else EnglishStrings

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: ROUTE_DASHBOARD
    val isFullModalScreen = currentRoute == ROUTE_NOTIFICATIONS ||
        currentRoute == ROUTE_SETTINGS ||
        currentRoute == ROUTE_MEDIA_LAB

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(bannerMessage) {
        if (!bannerMessage.isNullOrBlank()) {
            snackbarHostState.showSnackbar(bannerMessage!!)
            viewModel.clearBanner()
        }
    }

    var showCreateChooser by remember { mutableStateOf(false) }
    var showUploadSheet by remember { mutableStateOf(false) }
    var showLiveSchedulerSheet by remember { mutableStateOf(false) }
    var showChannelPickerDialog by remember { mutableStateOf(false) }

    var requestedLiveTab by remember { mutableStateOf<Int?>(null) }
    var requestedAiTab by remember { mutableStateOf<Int?>(null) }
    var requestedOpsTab by remember { mutableStateOf<Int?>(null) }
    var selectedVideoIdForLab by remember { mutableStateOf<String?>(null) }

    if (showCreateChooser) {
        CreateVideoBottomSheet(
            onDismiss = { showCreateChooser = false },
            onUploadVideoClick = { showUploadSheet = true },
            onLiveVideoClick = { showLiveSchedulerSheet = true }
        )
    }

    if (showUploadSheet) {
        UploadVideoBottomSheet(
            uploadState = uploadState,
            defaultPrivacy = PrivacyStatus.fromRaw(settings?.defaultPrivacy),
            defaultTimezone = settings?.timezone ?: "Asia/Kolkata",
            onInspectVideo = { uri -> viewModel.inspectSelectedVideo(uri) },
            onSubmitUpload = { uri, title, desc, cat, priv, tags, qr, thumbBytes, publish ->
                viewModel.uploadVideoToLibraryAndYouTube(
                    videoUri = uri,
                    title = title,
                    description = desc,
                    categoryId = cat,
                    privacyStatus = priv,
                    tags = tags,
                    qualityReport = qr,
                    thumbnailBytes = thumbBytes,
                    publishToYouTubeNow = publish
                )
            },
            onScheduleUpload = { uri, title, desc, cat, priv, tags, qr, publishEpochMs, tz ->
                viewModel.scheduleVideoUpload(
                    videoUri = uri,
                    title = title,
                    description = desc,
                    categoryId = cat,
                    privacyStatus = priv,
                    tags = tags,
                    qualityReport = qr,
                    scheduledPublishEpochMs = publishEpochMs,
                    timezoneId = tz
                )
            },
            onDismiss = { showUploadSheet = false }
        )
    }

    if (showLiveSchedulerSheet) {
        LiveSchedulerBottomSheet(
            videos = videos,
            defaultPrivacy = PrivacyStatus.fromRaw(settings?.defaultPrivacy),
            defaultProfile = StreamingProfile.HD_1080P_30,
            defaultTimezone = settings?.timezone ?: "Asia/Kolkata",
            onScheduleConfirmed = { videoId, videoTitle, liveTitle, liveDesc, priv, startMs, tz, rec, prof, autoEnd ->
                viewModel.scheduleLiveBroadcast(
                    videoId = videoId,
                    videoTitle = videoTitle,
                    liveTitle = liveTitle,
                    liveDescription = liveDesc,
                    privacyStatus = priv,
                    scheduledStartEpochMs = startMs,
                    timezoneId = tz,
                    recurrence = rec,
                    profile = prof,
                    autoEndOnFinish = autoEnd
                )
            },
            onOpenUploadFirst = { showUploadSheet = true },
            onDismiss = { showLiveSchedulerSheet = false }
        )
    }

    if (showChannelPickerDialog) {
        AlertDialog(
            onDismissRequest = { showChannelPickerDialog = false },
            title = { Text("Switch YouTube Channel") },
            text = {
                if (channels.isEmpty()) {
                    Text("No YouTube channels connected yet. Open Settings to authorize via Google OAuth 2.0.")
                } else {
                    androidx.compose.foundation.layout.Column {
                        channels.forEach { ch ->
                            OutlinedButton(
                                onClick = {
                                    viewModel.switchChannel(ch.channelId)
                                    showChannelPickerDialog = false
                                }
                            ) {
                                Text(if (ch.activeChannel) "✓ ${ch.title}" else ch.title)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    showChannelPickerDialog = false
                    navController.navigate(ROUTE_SETTINGS)
                }) {
                    Text("Manage Channels")
                }
            },
            dismissButton = {
                TextButton(onClick = { showChannelPickerDialog = false }) { Text("Close") }
            }
        )
    }

    CompositionLocalProvider(LocalAppStrings provides strings) {
        val navItems = listOf(
            Triple(ROUTE_DASHBOARD, strings.navDashboard, Icons.Default.Dashboard),
            Triple(ROUTE_LIVE_HUB, strings.navLiveHub, Icons.Default.LiveTv),
            Triple(ROUTE_LIVE_STATUS, strings.navLiveStatus, Icons.Default.MonitorHeart),
            Triple(ROUTE_LIBRARY, strings.navLibrary, Icons.Default.VideoLibrary),
            Triple(ROUTE_AI_STUDIO, strings.navAiStudio, Icons.Default.AutoAwesome),
            Triple(ROUTE_ANALYTICS_OPS, strings.navAnalytics, Icons.Default.Analytics)
        )

        fun navigateTopLevel(route: String) {
            navController.navigate(route) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }

        fun handleQuickToolJump(toolId: String) {
            when (toolId) {
                "calendar" -> {
                    requestedLiveTab = 2
                    navigateTopLevel(ROUTE_LIVE_HUB)
                }
                "live_chat" -> {
                    requestedLiveTab = 4
                    navigateTopLevel(ROUTE_LIVE_HUB)
                }
                "media_lab" -> {
                    selectedVideoIdForLab = null
                    navController.navigate(ROUTE_MEDIA_LAB)
                }
                "subtitles" -> {
                    requestedAiTab = 2
                    navigateTopLevel(ROUTE_AI_STUDIO)
                }
                "seo" -> {
                    requestedAiTab = 5
                    navigateTopLevel(ROUTE_AI_STUDIO)
                }
                "analytics" -> {
                    requestedOpsTab = 0
                    navigateTopLevel(ROUTE_ANALYTICS_OPS)
                }
                "competitors" -> {
                    requestedOpsTab = 1
                    navigateTopLevel(ROUTE_ANALYTICS_OPS)
                }
                "copyright" -> {
                    requestedOpsTab = 2
                    navigateTopLevel(ROUTE_ANALYTICS_OPS)
                }
                "revenue" -> {
                    requestedOpsTab = 3
                    navigateTopLevel(ROUTE_ANALYTICS_OPS)
                }
                "compare" -> {
                    requestedOpsTab = 4
                    navigateTopLevel(ROUTE_ANALYTICS_OPS)
                }
                "comments" -> {
                    requestedOpsTab = 5
                    navigateTopLevel(ROUTE_ANALYTICS_OPS)
                }
                "branding" -> {
                    requestedOpsTab = 6
                    navigateTopLevel(ROUTE_ANALYTICS_OPS)
                }
                "cloud_ops" -> {
                    requestedOpsTab = 7
                    navigateTopLevel(ROUTE_ANALYTICS_OPS)
                }
                "team" -> {
                    requestedOpsTab = 9
                    navigateTopLevel(ROUTE_ANALYTICS_OPS)
                }
                "activity" -> {
                    requestedOpsTab = 10
                    navigateTopLevel(ROUTE_ANALYTICS_OPS)
                }
                "security" -> {
                    requestedOpsTab = 11
                    navigateTopLevel(ROUTE_ANALYTICS_OPS)
                }
                "library" -> navigateTopLevel(ROUTE_LIBRARY)
            }
        }

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useRail = maxWidth >= 600.dp

            Scaffold(
                containerColor = VoidBlack,
                snackbarHost = { SnackbarHost(snackbarHostState) },
                topBar = {
                    if (!isFullModalScreen) {
                        DreamiitTopHeader(
                            activeChannel = activeChannel,
                            unreadNotifications = unreadCount,
                            onChannelClick = { showChannelPickerDialog = true },
                            onNotificationsClick = { navController.navigate(ROUTE_NOTIFICATIONS) },
                            onSettingsClick = { navController.navigate(ROUTE_SETTINGS) }
                        )
                    }
                },
                bottomBar = {
                    if (!useRail && !isFullModalScreen) {
                        NavigationBar(
                            containerColor = ObsidianSurface,
                            contentColor = Color.White
                        ) {
                            navItems.forEach { (route, label, icon) ->
                                val selected = navBackStackEntry?.destination?.hierarchy?.any { it.route == route } == true
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { navigateTopLevel(route) },
                                    icon = { Icon(icon, contentDescription = label) },
                                    label = { Text(label, maxLines = 1) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = ElectricCyan,
                                        selectedTextColor = ElectricCyan,
                                        indicatorColor = ElectricBlue.copy(alpha = 0.2f),
                                        unselectedIconColor = TextSecondary,
                                        unselectedTextColor = TextSecondary
                                    ),
                                    modifier = Modifier.testTag("nav_$route")
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (useRail && !isFullModalScreen) {
                        NavigationRail(
                            containerColor = ObsidianSurface
                        ) {
                            navItems.forEach { (route, label, icon) ->
                                val selected = navBackStackEntry?.destination?.hierarchy?.any { it.route == route } == true
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = { navigateTopLevel(route) },
                                    icon = { Icon(icon, contentDescription = label) },
                                    label = { Text(label) },
                                    colors = NavigationRailItemDefaults.colors(
                                        selectedIconColor = ElectricCyan,
                                        selectedTextColor = ElectricCyan,
                                        indicatorColor = ElectricBlue.copy(alpha = 0.2f),
                                        unselectedIconColor = TextSecondary,
                                        unselectedTextColor = TextSecondary
                                    )
                                )
                            }
                        }
                    }

                    Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                        NavHost(
                            navController = navController,
                            startDestination = ROUTE_DASHBOARD
                        ) {
                            composable(ROUTE_DASHBOARD) {
                                DashboardScreen(
                                    activeChannel = activeChannel,
                                    analytics = analytics,
                                    videos = videos,
                                    schedules = schedules,
                                    uploadSchedules = uploadSchedules,
                                    activityLogs = activityLogs,
                                    settings = settings,
                                    countdown = countdownState,
                                    storageAlert = storageAlert,
                                    activeRole = activeRole,
                                    liveElapsedSec = liveElapsedSec,
                                    workerHealth = workerHealth,
                                    apiUsage = apiUsageReport,
                                    connectedBilling = connectedBilling,
                                    securitySummary = securityAlertSummary,
                                    copyrightReports = copyrightReports,
                                    videoPerformanceList = videoPerformanceList,
                                    liveConnectionAlert = liveConnectionAlert,
                                    onEmergencyStopLive = { sched -> viewModel.stopActiveLive(sched, emergency = true) },
                                    onCreateVideoClick = { showCreateChooser = true },
                                    onConnectChannelClick = { navController.navigate(ROUTE_SETTINGS) },
                                    onSyncChannelsClick = { viewModel.refreshChannelsFromApi() },
                                    onOpenLiveControl = { navigateTopLevel(ROUTE_LIVE_HUB) },
                                    onSaveDashboardLayout = { order, hidden ->
                                        viewModel.saveDashboardLayout(order, hidden)
                                    },
                                    onQuickToolSelected = { handleQuickToolJump(it) }
                                )
                            }
                            composable(ROUTE_LIVE_HUB) {
                                LiveHubScreen(
                                    requestedTab = requestedLiveTab,
                                    onConsumeRequestedTab = { requestedLiveTab = null },
                                    schedules = schedules,
                                    uploadSchedules = uploadSchedules,
                                    playlists = playlists,
                                    videos = videos,
                                    jobs = jobs,
                                    workerHealth = workerHealth,
                                    ytStreamHealth = ytStreamHealth,
                                    liveConnectionAlert = liveConnectionAlert,
                                    autoStreamRetryEnabled = settings?.autoStreamRetryEnabled ?: true,
                                    liveElapsedSec = liveElapsedSec,
                                    chatMessages = liveChatMessages,
                                    chatStats = liveChatStats,
                                    onOpenNewSchedule = { showLiveSchedulerSheet = true },
                                    onOpenNewUploadSchedule = { showUploadSheet = true },
                                    onStartLive = { viewModel.startScheduledLiveNow(it) },
                                    onStopLive = { s, em -> viewModel.stopActiveLive(s, em) },
                                    onCancelSchedule = { viewModel.cancelSchedule(it) },
                                    onDeleteLiveSchedule = { viewModel.deleteLiveSchedule(it) },
                                    onReschedule = { s, ts -> viewModel.rescheduleBroadcast(s, ts) },
                                    onRetrySchedule = { viewModel.retryFailedSchedule(it) },
                                    onRefreshLiveStreamHealth = { viewModel.refreshLiveStreamHealth() },
                                    onToggleAutoRetry = { enabled -> viewModel.toggleAutoStreamRetry(enabled) },
                                    onTriggerScheduledUploadNow = { viewModel.triggerScheduledUploadNow(it) },
                                    onRescheduleUpload = { u, ts -> viewModel.rescheduleUpload(u, ts) },
                                    onCancelUpload = { viewModel.cancelUploadSchedule(it) },
                                    onDeleteUpload = { viewModel.deleteUploadSchedule(it) },
                                    onStartChatPolling = { viewModel.startLiveChatPolling(it) },
                                    onStopChatPolling = { viewModel.stopLiveChatPolling() },
                                    onRefreshChat = { viewModel.refreshLiveChatNow(it) },
                                    onSendChatMessage = { viewModel.sendLiveChatMessage(it) },
                                    onDeleteChatMessage = { viewModel.deleteLiveChatMessage(it) },
                                    onTimeoutChatUser = { viewModel.timeoutLiveChatUser(it) },
                                    onCreatePlaylist = { t, vids, end, loop -> viewModel.createPlaylist(t, vids, end, loop) },
                                    onMovePlaylistItem = { pl, f, t -> viewModel.movePlaylistItem(pl, f, t) },
                                    onRemovePlaylistItem = { pl, idx -> viewModel.removePlaylistItem(pl, idx) },
                                    onTogglePlaylistPlay = { viewModel.togglePlaylistPlayback(it) },
                                    onAdvancePlaylist = { viewModel.advancePlaylistIndex(it) },
                                    onDeletePlaylist = { viewModel.deletePlaylist(it) },
                                    onRefreshHealth = { viewModel.refreshWorkerHealth() }
                                )
                            }
                            composable(ROUTE_LIVE_STATUS) {
                                LiveStatusScreen(
                                    schedules = schedules,
                                    jobs = jobs,
                                    workerHealth = workerHealth,
                                    ytStreamHealth = ytStreamHealth,
                                    liveConnectionAlert = liveConnectionAlert,
                                    liveElapsedSec = liveElapsedSec,
                                    onRefreshHealth = { viewModel.refreshWorkerHealth() },
                                    onProbeStream = { viewModel.refreshLiveStreamHealth() }
                                )
                            }
                            composable(ROUTE_LIBRARY) {
                                LibraryScreen(
                                    videos = videos,
                                    storageAlert = storageAlert,
                                    onUpdateQuotaGb = { quota ->
                                        viewModel.updateUserSettings(
                                            mapOf(
                                                "storageQuotaGb" to quota,
                                                "storageAlert80Sent" to false,
                                                "storageAlert90Sent" to false
                                            )
                                        )
                                    },
                                    onCleanupDrafts = { viewModel.cleanupUnlinkedDraftVideos() },
                                    onOpenInMediaLab = { vid ->
                                        selectedVideoIdForLab = vid.id
                                        navController.navigate(ROUTE_MEDIA_LAB)
                                    },
                                    onUploadClick = { showUploadSheet = true },
                                    onEditVideo = { id, t, d, p, tags -> viewModel.updateVideoMetadata(id, t, d, p, tags) },
                                    onDeleteVideo = { viewModel.deleteVideo(it) }
                                )
                            }
                            composable(ROUTE_AI_STUDIO) {
                                AiStudioScreen(
                                    aiViewModel = aiViewModel,
                                    uploadedVideos = videos,
                                    requestedTab = requestedAiTab,
                                    onConsumeRequestedTab = { requestedAiTab = null },
                                    onUploadThumbnailToYouTube = { vidId, bytes ->
                                        viewModel.uploadThumbnailToYouTubeVideo(vidId, bytes)
                                    },
                                    onSavedThumbnailNotice = { msg -> viewModel.showBanner(msg) },
                                    onExportTextArtifact = { fileName, content ->
                                        scope.launch {
                                            val (file, _) = viewModel.exportTextArtifact("captions_transcripts", fileName, content)
                                            viewModel.showBanner("Exported ${file.name}")
                                        }
                                    },
                                    onSaveTranscript = { vidId, transcript ->
                                        viewModel.saveTranscriptToCloudVideo(vidId, transcript)
                                    },
                                    onSaveSubtitles = { vid, lang, srt, uploadYt ->
                                        viewModel.saveSubtitlesAndOptionallyUploadYouTube(vid, lang, srt, uploadYt)
                                    },
                                    onApplyChapters = { vid, block, syncYt ->
                                        viewModel.applyChaptersToVideo(vid, block, syncYt)
                                    },
                                    onOpenMediaLab = { vidId ->
                                        selectedVideoIdForLab = vidId
                                        navController.navigate(ROUTE_MEDIA_LAB)
                                    }
                                )
                            }
                            composable(ROUTE_MEDIA_LAB) {
                                MediaLabScreen(
                                    videos = videos,
                                    initialVideoId = selectedVideoIdForLab,
                                    mediaLabState = mediaLabState,
                                    aiViewModel = aiViewModel,
                                    onAnalyzeAudio = { uri -> viewModel.analyzeAudioForVideo(uri) },
                                    onTrimVideo = { uri, vid, s, e, title ->
                                        viewModel.trimAndSaveVideoSeparately(uri, vid, s, e, title)
                                    },
                                    onNormalizeAudio = { uri, vid, gain, norm, title ->
                                        viewModel.normalizeAndSaveVideoAudio(uri, vid, gain, norm, title)
                                    },
                                    onCreateVerticalShort = { uri, vid, s, e, stTitle, stDesc ->
                                        viewModel.createVertical916ShortClip(uri, vid, s, e, stTitle, stDesc)
                                    },
                                    onResetMediaLab = { viewModel.resetMediaLabState() },
                                    onBack = { navController.popBackStack() }
                                )
                            }
                            composable(ROUTE_ANALYTICS_OPS) {
                                AnalyticsOpsScreen(
                                    requestedTab = requestedOpsTab,
                                    onConsumeRequestedTab = { requestedOpsTab = null },
                                    activeChannel = activeChannel,
                                    settings = settings,
                                    analytics = analytics,
                                    videoPerformanceList = videoPerformanceList,
                                    videoPerformanceStatus = videoPerformanceStatus,
                                    policyAlerts = policyAlerts,
                                    videos = videos,
                                    schedules = schedules,
                                    competitors = competitors,
                                    competitorBusy = competitorBusy,
                                    copyrightReports = copyrightReports,
                                    copyrightScanBusy = copyrightScanBusy,
                                    revenue = revenue,
                                    comparedVideos = comparedVideos,
                                    comparisonStatus = comparisonStatus,
                                    comments = comments,
                                    commentsStatus = commentsStatus,
                                    commentsLoading = commentsLoading,
                                    brandingInfo = brandingInfo,
                                    brandingBusy = brandingBusy,
                                    workerHealth = workerHealth,
                                    jobs = jobs,
                                    apiUsage = apiUsageReport,
                                    connectedBilling = connectedBilling,
                                    cost = cost,
                                    storageAlert = storageAlert,
                                    backups = backups,
                                    teamMembers = teamMembers,
                                    activeRole = activeRole,
                                    activityLogs = activityLogs,
                                    auditLogs = auditLogs,
                                    sessions = sessions,
                                    currentSessionId = viewModel.currentSessionId,
                                    securitySummary = securityAlertSummary,
                                    oauthCredentials = oauthCreds,
                                    onLoadAnalytics = { viewModel.loadAnalytics(it) },
                                    onLoadVideoPerformance = { viewModel.loadDetailedVideoPerformance() },
                                    onAddCompetitor = { viewModel.addCompetitorChannel(it) },
                                    onRefreshCompetitors = { viewModel.refreshCompetitors() },
                                    onRemoveCompetitor = { viewModel.removeCompetitor(it) },
                                    onRunCopyrightScan = { id, title, desc, tags, audio, notes ->
                                        viewModel.runCopyrightRiskScan(id, title, desc, tags, audio, notes)
                                    },
                                    onDeleteCopyrightReport = { viewModel.deleteCopyrightReport(it) },
                                    onLoadRevenue = { viewModel.loadRevenue(it) },
                                    onLoadComparedVideos = { viewModel.loadVideosForComparison() },
                                    onRefreshComments = { q -> viewModel.loadComments(q) },
                                    onReplyComment = { id, text -> viewModel.replyToComment(id, text) },
                                    onModerateComment = { id, del -> viewModel.moderateOrDeleteComment(id, del) },
                                    onLoadBranding = { viewModel.loadChannelBranding() },
                                    onSaveBranding = { desc, kw, bytes -> viewModel.saveChannelBranding(desc, kw, bytes) },
                                    onRefreshHealth = { viewModel.refreshWorkerHealth() },
                                    onRefreshBilling = { viewModel.refreshCloudBilling() },
                                    onUpdateBudget = { budget -> viewModel.updateUserSettings(mapOf("monthlyBudgetUsd" to budget)) },
                                    onUpdateApiQuotaLimit = { limit -> viewModel.updateApiQuotaLimit(limit) },
                                    onCleanupDrafts = { viewModel.cleanupUnlinkedDraftVideos() },
                                    onCreateBackup = { lbl, ret -> viewModel.createBackupSnapshot(lbl, ret) },
                                    onRestoreBackup = { bak -> viewModel.restoreBackup(bak) },
                                    onUpdateAutoBackup = { enabled, freq ->
                                        viewModel.updateUserSettings(
                                            mapOf(
                                                "autoBackupEnabled" to enabled,
                                                "autoBackupFrequency" to freq
                                            )
                                        )
                                    },
                                    onAddTeamMember = { name, email, role, notes ->
                                        viewModel.addTeamMember(name, email, role, notes)
                                    },
                                    onUpdateTeamMember = { member, role, active ->
                                        viewModel.updateTeamMember(member, role, active)
                                    },
                                    onRemoveTeamMember = { member -> viewModel.removeTeamMember(member) },
                                    onSwitchActiveRole = { role -> viewModel.setActiveRolePreview(role) },
                                    onTrustCurrentDevice = { viewModel.trustCurrentDevice() },
                                    onConfirmTwoFactor = { verified -> viewModel.confirmTwoFactorVerified(verified) },
                                    onSnoozeTwoFactor = { viewModel.snoozeTwoFactorReminder() },
                                    onRevokeSession = { id -> viewModel.revokeSession(id) },
                                    onRevokeOtherSessions = { viewModel.revokeAllOtherSessions() },
                                    onLogoutAllDevices = {
                                        signOut(
                                            credentialManager = credentialManager,
                                            scope = scope,
                                            onBeforeSignOut = { viewModel.prepareSecureLogout() }
                                        )
                                    }
                                )
                            }
                            composable(ROUTE_NOTIFICATIONS) {
                                NotificationsScreen(
                                    notifications = notifications,
                                    onMarkRead = { viewModel.markNotificationRead(it) },
                                    onBack = { navController.popBackStack() }
                                )
                            }
                            composable(ROUTE_SETTINGS) {
                                SettingsScreen(
                                    userEmail = userEmail,
                                    settings = settings,
                                    channels = channels,
                                    oauthCredentials = oauthCreds,
                                    onPrepareOAuthUrl = { clientId -> viewModel.prepareYouTubeOAuthUrl(clientId) },
                                    onSwitchChannel = { viewModel.switchChannel(it) },
                                    onDisconnectChannel = { viewModel.disconnectChannel(it) },
                                    onDisconnectOAuth = { viewModel.disconnectAllYouTubeTokens() },
                                    onUpdateSettings = { viewModel.updateUserSettings(it) },
                                    onOpenAdminControl = { navController.navigate(ROUTE_ADMIN_CONTROL) },
                                    onSignOut = {
                                        signOut(
                                            credentialManager = credentialManager,
                                            scope = scope,
                                            onBeforeSignOut = { viewModel.prepareSecureLogout() }
                                        )
                                    },
                                    onBack = { navController.popBackStack() }
                                )
                            }
                            composable(ROUTE_ADMIN_CONTROL) {
                                AdminControlCenterScreen(
                                    userEmail = userEmail,
                                    activeRole = activeRole,
                                    channels = channels,
                                    schedules = schedules,
                                    videos = videos,
                                    notifications = notifications,
                                    workerHealth = workerHealth,
                                    onSwitchRole = { },
                                    onEmergencyStopLive = { sched: LiveSchedule -> viewModel.stopActiveLive(sched, emergency = true) },
                                    onBack = { navController.popBackStack() }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
