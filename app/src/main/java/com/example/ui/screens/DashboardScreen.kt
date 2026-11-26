package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Comment
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Copyright
import androidx.compose.material.icons.filled.DashboardCustomize
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.ActivityLog
import com.example.data.model.ApiUsageReport
import com.example.data.model.CloudVideo
import com.example.data.model.ConnectedBillingReport
import com.example.data.model.CopyrightRiskReport
import com.example.data.model.CountdownState
import com.example.data.model.DEFAULT_DASHBOARD_WIDGETS
import com.example.data.model.DashboardWidgetId
import com.example.data.model.LiveSchedule
import com.example.data.model.ScheduleStatus
import com.example.data.model.SecurityAlertSummary
import com.example.data.model.StorageAlertReport
import com.example.data.model.TeamRole
import com.example.data.model.UploadSchedule
import com.example.data.model.UploadScheduleStatus
import com.example.data.model.UserSettings
import com.example.data.model.VideoPerformanceDetail
import com.example.data.model.YouTubeAnalyticsSnapshot
import com.example.data.model.YouTubeChannel
import com.example.data.network.WorkerHealthResponse
import com.example.localization.LocalAppStrings
import com.example.ui.components.CreateVideoPrimaryButton
import com.example.ui.components.StatTileCard
import com.example.ui.components.StatusBadge
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ElevatedCard
import com.example.ui.theme.ElevatedCardHigh
import com.example.ui.theme.LiveCrimson
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.SuccessEmerald
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VoidBlack
import com.example.ui.theme.WarningAmber
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class QuickToolShortcut(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val accent: Color
)

@Composable
fun DashboardScreen(
    activeChannel: YouTubeChannel?,
    analytics: YouTubeAnalyticsSnapshot,
    videos: List<CloudVideo>,
    schedules: List<LiveSchedule>,
    uploadSchedules: List<UploadSchedule>,
    activityLogs: List<ActivityLog>,
    settings: UserSettings?,
    countdown: CountdownState?,
    storageAlert: StorageAlertReport,
    activeRole: TeamRole,
    liveElapsedSec: Long,
    workerHealth: WorkerHealthResponse = WorkerHealthResponse(),
    apiUsage: ApiUsageReport = ApiUsageReport(0, 10_000, 0, 0.0, false, ""),
    connectedBilling: ConnectedBillingReport = ConnectedBillingReport(),
    securitySummary: SecurityAlertSummary = SecurityAlertSummary(false, false, false, "Android", 1, false),
    copyrightReports: List<CopyrightRiskReport> = emptyList(),
    videoPerformanceList: List<VideoPerformanceDetail> = emptyList(),
    liveConnectionAlert: String? = null,
    onEmergencyStopLive: (LiveSchedule) -> Unit = {},
    onCreateVideoClick: () -> Unit,
    onConnectChannelClick: () -> Unit,
    onSyncChannelsClick: () -> Unit,
    onOpenLiveControl: () -> Unit,
    onSaveDashboardLayout: (order: List<String>, hidden: List<String>) -> Unit,
    onQuickToolSelected: (String) -> Unit
) {
    val strings = LocalAppStrings.current
    val nf = NumberFormat.getIntegerInstance()
    var showCustomizeDialog by remember { mutableStateOf(false) }
    var confirmEmergencyStopTarget by remember { mutableStateOf<LiveSchedule?>(null) }

    val widgetOrder = remember(settings?.dashboardWidgets) {
        val raw = settings?.dashboardWidgets
        if (raw.isNullOrEmpty()) DEFAULT_DASHBOARD_WIDGETS
        else (raw + DEFAULT_DASHBOARD_WIDGETS.filterNot { raw.contains(it) }).distinct()
    }
    val hiddenWidgets = remember(settings?.hiddenWidgets) {
        settings?.hiddenWidgets.orEmpty().toSet()
    }
    val visibleWidgets = remember(widgetOrder, hiddenWidgets) {
        widgetOrder.filterNot { hiddenWidgets.contains(it) }
    }

    if (showCustomizeDialog) {
        CustomDashboardDialog(
            currentOrder = widgetOrder,
            currentHidden = hiddenWidgets.toList(),
            onSave = onSaveDashboardLayout,
            onDismiss = { showCustomizeDialog = false }
        )
    }

    val scheduledCount = schedules.count {
        it.status == ScheduleStatus.SCHEDULED.name || it.status == ScheduleStatus.PREPARING.name
    }
    val scheduledUploadCount = uploadSchedules.count { it.status == UploadScheduleStatus.SCHEDULED.name }
    val liveNowCount = schedules.count { it.status == ScheduleStatus.LIVE.name }
    val completedCount = schedules.count { it.status == ScheduleStatus.COMPLETED.name }
    val activeBroadcast = schedules.firstOrNull { it.status == ScheduleStatus.LIVE.name }
    val upcoming = schedules.filter {
        it.status == ScheduleStatus.SCHEDULED.name || it.status == ScheduleStatus.PREPARING.name
    }.take(5)

    confirmEmergencyStopTarget?.let { sched ->
        AlertDialog(
            onDismissRequest = { confirmEmergencyStopTarget = null },
            title = { Text("Confirm Emergency Stop") },
            text = { Text("Stop live broadcast '${sched.liveTitle}' immediately? This will terminate the RTMPS stream.") },
            confirmButton = {
                Button(
                    onClick = {
                        onEmergencyStopLive(sched)
                        confirmEmergencyStopTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = LiveCrimson)
                ) { Text("Emergency Stop") }
            },
            dismissButton = {
                TextButton(onClick = { confirmEmergencyStopTarget = null }) { Text("Keep Streaming") }
            }
        )
    }

    val quickTools = remember(strings) {
        listOf(
            QuickToolShortcut("analytics", strings.analyticsTab, Icons.Default.TrendingUp, ElectricCyan),
            QuickToolShortcut("competitors", strings.competitorsTab, Icons.Default.Leaderboard, ElectricBlue),
            QuickToolShortcut("copyright", strings.copyrightPolicyTab, Icons.Default.Copyright, WarningAmber),
            QuickToolShortcut("cloud_ops", strings.cloudOpsTab, Icons.Default.CloudDone, SuccessEmerald),
            QuickToolShortcut("security", strings.securityTab, Icons.Default.Security, LiveCrimson),
            QuickToolShortcut("calendar", strings.calendarTab, Icons.Default.CalendarMonth, ElectricCyan),
            QuickToolShortcut("media_lab", strings.mediaLabTab, Icons.Default.ContentCut, ElectricBlue),
            QuickToolShortcut("subtitles", strings.subtitlesTab, Icons.Default.Subtitles, SuccessEmerald),
            QuickToolShortcut("seo", strings.seoAssistantTab, Icons.Default.TrendingUp, WarningAmber),
            QuickToolShortcut("live_chat", strings.liveChatTab, Icons.Default.Chat, LiveCrimson),
            QuickToolShortcut("comments", strings.commentsTab, Icons.Default.Comment, ElectricCyan),
            QuickToolShortcut("branding", strings.brandingTab, Icons.Default.Palette, ElectricBlue),
            QuickToolShortcut("compare", strings.compareVideosTab, Icons.Default.CompareArrows, SuccessEmerald),
            QuickToolShortcut("revenue", strings.revenueTab, Icons.Default.AttachMoney, WarningAmber),
            QuickToolShortcut("team", strings.teamAccessTab, Icons.Default.Group, ElectricCyan),
            QuickToolShortcut("activity", strings.activityLogTab, Icons.Default.History, ElectricBlue)
        )
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWide = maxWidth >= 600.dp

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 1200.dp)
                .align(Alignment.TopCenter),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Top Customize + Active Role Bar
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatusBadge(
                        text = "ROLE: ${activeRole.name} • V4.1",
                        color = if (activeRole == TeamRole.OWNER) SuccessEmerald else WarningAmber
                    )
                    TextButton(
                        onClick = { showCustomizeDialog = true },
                        modifier = Modifier.testTag("customize_dashboard_btn")
                    ) {
                        Icon(Icons.Default.DashboardCustomize, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(strings.customizeDashboard, color = ElectricCyan)
                    }
                }
            }

            if (!liveConnectionAlert.isNullOrBlank()) {
                item {
                    Card(
                        onClick = onOpenLiveControl,
                        colors = CardDefaults.cardColors(containerColor = WarningAmber.copy(alpha = 0.14f)),
                        border = BorderStroke(1.dp, WarningAmber),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = WarningAmber)
                            Text(liveConnectionAlert, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            if (activeBroadcast != null) {
                item {
                    Card(
                        onClick = onOpenLiveControl,
                        colors = CardDefaults.cardColors(containerColor = LiveCrimson.copy(alpha = 0.14f)),
                        border = BorderStroke(1.5.dp, LiveCrimson),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "● LIVE NOW • ${formatDuration(liveElapsedSec)}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = LiveCrimson,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(activeBroadcast.liveTitle, style = MaterialTheme.typography.titleMedium, color = Color.White)
                                    Text(
                                        "${activeBroadcast.targetProfile} • Cloud RTMPS Autonomous Stream",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextSecondary
                                    )
                                }
                                Icon(Icons.Default.PlayCircle, contentDescription = "Open Control Center", tint = LiveCrimson, modifier = Modifier.size(32.dp))
                            }
                            Button(
                                onClick = { confirmEmergencyStopTarget = activeBroadcast },
                                colors = ButtonDefaults.buttonColors(containerColor = LiveCrimson),
                                modifier = Modifier.fillMaxWidth().testTag("dashboard_emergency_stop_btn")
                            ) {
                                Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(strings.emergencyStop, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            items(visibleWidgets, key = { it }) { widgetKey ->
                when (widgetKey) {
                    DashboardWidgetId.HERO_CHANNEL.name -> {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = ElevatedCard),
                            border = BorderStroke(1.dp, ElectricBlue.copy(alpha = 0.45f)),
                            shape = RoundedCornerShape(22.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(modifier = Modifier.fillMaxWidth()) {
                                Image(
                                    painter = painterResource(id = R.drawable.img_studio_hero),
                                    contentDescription = "DREAMIIT28 Broadcast Studio",
                                    contentScale = ContentScale.Crop,
                                    alpha = 0.55f,
                                    modifier = Modifier.fillMaxWidth().height(172.dp)
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(172.dp)
                                        .background(
                                            Brush.verticalGradient(
                                                listOf(VoidBlack.copy(alpha = 0.25f), ObsidianSurface.copy(alpha = 0.96f))
                                            )
                                        )
                                )
                                Column(modifier = Modifier.fillMaxWidth().padding(18.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                            if (!activeChannel?.thumbnailUrl.isNullOrBlank()) {
                                                AsyncImage(
                                                    model = activeChannel?.thumbnailUrl,
                                                    contentDescription = "Connected YouTube Channel",
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.size(48.dp).clip(CircleShape)
                                                )
                                            } else {
                                                Image(
                                                    painter = painterResource(id = R.drawable.img_creator_avatar),
                                                    contentDescription = "Creator Avatar",
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.size(48.dp).clip(CircleShape)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column {
                                                Text(
                                                    text = activeChannel?.title ?: strings.channelNotConnected,
                                                    style = MaterialTheme.typography.titleLarge,
                                                    color = Color.White,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = if (activeChannel != null) {
                                                        "${activeChannel.handle ?: activeChannel.channelId} • OAuth 2.0 Verified"
                                                    } else {
                                                        "Real YouTube API metrics appear once authorized"
                                                    },
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = TextSecondary,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                        StatusBadge(
                                            text = if (liveNowCount > 0) "ON AIR" else "1080p READY",
                                            color = if (liveNowCount > 0) LiveCrimson else ElectricCyan
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                        if (activeChannel == null) {
                                            Button(
                                                onClick = onConnectChannelClick,
                                                colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                                                shape = RoundedCornerShape(12.dp),
                                                modifier = Modifier.testTag("dashboard_connect_channel_btn")
                                            ) {
                                                Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(18.dp))
                                                Spacer(Modifier.width(6.dp))
                                                Text(strings.connectChannelCta)
                                            }
                                        } else {
                                            OutlinedButton(
                                                onClick = onSyncChannelsClick,
                                                border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.6f)),
                                                shape = RoundedCornerShape(12.dp),
                                                modifier = Modifier.testTag("dashboard_sync_channel_btn")
                                            ) {
                                                Text("Sync YouTube Stats", color = ElectricCyan)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    DashboardWidgetId.CREATE_CTA.name -> {
                        CreateVideoPrimaryButton(onClick = onCreateVideoClick)
                    }

                    DashboardWidgetId.LIVE_COUNTDOWN.name -> {
                        LiveCountdownCard(
                            countdown = countdown,
                            title = strings.liveCountdownTitle,
                            onOpenLiveControl = onOpenLiveControl
                        )
                    }

                    DashboardWidgetId.QUICK_TOOLS.name -> {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("PRO 4.1 Creator Tools", style = MaterialTheme.typography.titleMedium, color = Color.White)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                quickTools.forEach { tool ->
                                    AssistChip(
                                        onClick = { onQuickToolSelected(tool.id) },
                                        label = { Text(tool.label, color = Color.White) },
                                        leadingIcon = {
                                            Icon(tool.icon, contentDescription = tool.label, tint = tool.accent, modifier = Modifier.size(18.dp))
                                        },
                                        colors = AssistChipDefaults.assistChipColors(containerColor = ElevatedCardHigh),
                                        border = BorderStroke(1.dp, tool.accent.copy(alpha = 0.45f)),
                                        modifier = Modifier.testTag("quick_tool_${tool.id}")
                                    )
                                }
                            }
                        }
                    }

                    DashboardWidgetId.STATS_GRID.name -> {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Channel & Automation Telemetry", style = MaterialTheme.typography.titleMedium, color = Color.White)
                            if (isWide) {
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                    StatTileCard(strings.totalSubscribers, activeChannel?.let { nf.format(it.subscriberCount) } ?: strings.unavailableLabel, Icons.Default.People, ElectricBlue, Modifier.weight(1f))
                                    StatTileCard(strings.totalViews, activeChannel?.let { nf.format(it.viewCount) } ?: strings.unavailableLabel, Icons.Default.Visibility, ElectricCyan, Modifier.weight(1f))
                                    StatTileCard(strings.totalWatchTime, analytics.watchTimeMinutes?.let { "${nf.format(it / 60)} hrs" } ?: strings.unavailableLabel, Icons.Default.Timer, WarningAmber, Modifier.weight(1f))
                                    StatTileCard(strings.uploadedVideos, activeChannel?.let { nf.format(it.videoCount) } ?: nf.format(videos.size), Icons.Default.VideoLibrary, ElectricBlue, Modifier.weight(1f))
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                    StatTileCard(strings.scheduledLive, scheduledCount.toString(), Icons.Default.Schedule, ElectricCyan, Modifier.weight(1f))
                                    StatTileCard(strings.scheduledUploadsTab, scheduledUploadCount.toString(), Icons.Default.CloudQueue, ElectricBlue, Modifier.weight(1f))
                                    StatTileCard(strings.liveNow, liveNowCount.toString(), Icons.Default.LiveTv, LiveCrimson, Modifier.weight(1f))
                                    StatTileCard(strings.completedLive, completedCount.toString(), Icons.Default.CheckCircle, SuccessEmerald, Modifier.weight(1f))
                                }
                            } else {
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                    StatTileCard(strings.totalSubscribers, activeChannel?.let { nf.format(it.subscriberCount) } ?: strings.unavailableLabel, Icons.Default.People, ElectricBlue, Modifier.weight(1f))
                                    StatTileCard(strings.totalViews, activeChannel?.let { nf.format(it.viewCount) } ?: strings.unavailableLabel, Icons.Default.Visibility, ElectricCyan, Modifier.weight(1f))
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                    StatTileCard(strings.totalWatchTime, analytics.watchTimeMinutes?.let { "${nf.format(it / 60)} hrs" } ?: strings.unavailableLabel, Icons.Default.Timer, WarningAmber, Modifier.weight(1f))
                                    StatTileCard(strings.uploadedVideos, activeChannel?.let { nf.format(it.videoCount) } ?: nf.format(videos.size), Icons.Default.VideoLibrary, ElectricBlue, Modifier.weight(1f))
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                    StatTileCard(strings.scheduledLive, scheduledCount.toString(), Icons.Default.Schedule, ElectricCyan, Modifier.weight(1f))
                                    StatTileCard(strings.liveNow, liveNowCount.toString(), Icons.Default.LiveTv, LiveCrimson, Modifier.weight(1f))
                                    StatTileCard(strings.completedLive, completedCount.toString(), Icons.Default.CheckCircle, SuccessEmerald, Modifier.weight(1f))
                                }
                            }
                        }
                    }

                    DashboardWidgetId.UPCOMING_LIVE.name -> {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Upcoming Cloud Live Broadcasts", style = MaterialTheme.typography.titleMedium, color = Color.White)
                                Text("1080p RTMPS • $scheduledUploadCount Auto-Uploads", style = MaterialTheme.typography.labelMedium, color = ElectricCyan)
                            }
                            if (upcoming.isEmpty()) {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = ElevatedCard),
                                    border = BorderStroke(1.dp, BorderSubtle),
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.CloudQueue, contentDescription = null, tint = ElectricBlue, modifier = Modifier.size(30.dp))
                                        Spacer(Modifier.width(12.dp))
                                        Column {
                                            Text("No upcoming automated live streams", style = MaterialTheme.typography.titleMedium, color = Color.White)
                                            Text("Tap + CREATE VIDEO → Live Video to schedule a 1080p cloud broadcast.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                                        }
                                    }
                                }
                            } else {
                                upcoming.forEach { sched ->
                                    val timeStr = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(sched.scheduledStartEpochMs))
                                    Card(
                                        onClick = onOpenLiveControl,
                                        colors = CardDefaults.cardColors(containerColor = ElevatedCard),
                                        border = BorderStroke(1.dp, BorderSubtle),
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(14.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(sched.liveTitle, style = MaterialTheme.typography.titleMedium, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                Text("$timeStr (${sched.timezone}) • ${sched.recurrence}", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                                            }
                                            StatusBadge(text = sched.status, color = ElectricBlue)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    DashboardWidgetId.STORAGE_STATUS.name -> {
                        val barColor = when {
                            storageAlert.alert90Triggered -> LiveCrimson
                            storageAlert.alert80Triggered -> WarningAmber
                            else -> ElectricBlue
                        }
                        Card(
                            onClick = { onQuickToolSelected("library") },
                            colors = CardDefaults.cardColors(containerColor = ElevatedCard),
                            border = BorderStroke(1.dp, barColor.copy(alpha = 0.55f)),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Storage, contentDescription = null, tint = barColor)
                                        Spacer(Modifier.width(8.dp))
                                        Text(strings.storageAlertsTitle, style = MaterialTheme.typography.titleMedium, color = Color.White)
                                    }
                                    StatusBadge(
                                        text = "${String.format(Locale.US, "%.0f", storageAlert.usagePercent)}%",
                                        color = barColor
                                    )
                                }
                                LinearProgressIndicator(
                                    progress = { (storageAlert.usagePercent / 100.0).toFloat().coerceIn(0f, 1f) },
                                    color = barColor,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Text(
                                    text = String.format(
                                        Locale.US,
                                        "Used %.2f GB of %.0f GB • %.2f GB Available",
                                        storageAlert.usedGb,
                                        storageAlert.quotaGb,
                                        storageAlert.availableGb
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary
                                )
                            }
                        }
                    }

                    DashboardWidgetId.CHANNEL_PERFORMANCE.name -> {
                        val avgEng = remember(videoPerformanceList) {
                            if (videoPerformanceList.isEmpty()) null else videoPerformanceList.map { it.engagementRatePercent }.average()
                        }
                        Card(
                            onClick = { onQuickToolSelected("analytics") },
                            colors = CardDefaults.cardColors(containerColor = ElevatedCard),
                            border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth().testTag("dashboard_channel_perf_card")
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text("YouTube Channel Performance", style = MaterialTheme.typography.titleMedium, color = Color.White)
                                    Text("Open Analytics →", style = MaterialTheme.typography.labelMedium, color = ElectricCyan)
                                }
                                Text(
                                    text = "Views (${analytics.dateRangeDays}d): ${analytics.views?.let { nf.format(it) } ?: strings.unavailableLabel} • Avg Engagement: ${avgEng?.let { String.format(Locale.US, "%.2f%%", it) } ?: strings.unavailableLabel} • Likes: ${analytics.likes?.let { nf.format(it) } ?: strings.unavailableLabel}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary
                                )
                            }
                        }
                    }

                    DashboardWidgetId.CLOUD_HEALTH_API.name -> {
                        val workerColor = if (workerHealth.reachable) SuccessEmerald else WarningAmber
                        Card(
                            onClick = { onQuickToolSelected("cloud_ops") },
                            colors = CardDefaults.cardColors(containerColor = ElevatedCard),
                            border = BorderStroke(1.dp, workerColor.copy(alpha = 0.55f)),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth().testTag("dashboard_cloud_health_card")
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            if (workerHealth.reachable) Icons.Default.CloudDone else Icons.Default.CloudOff,
                                            contentDescription = null,
                                            tint = workerColor
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text("Cloud Health, API Quota & Billing", style = MaterialTheme.typography.titleMedium, color = Color.White)
                                    }
                                    StatusBadge(workerHealth.status, workerColor)
                                }
                                Text(
                                    text = if (workerHealth.reachable) "Worker Online (${workerHealth.latencyMs}ms • CPU ${workerHealth.cpuPercent}%)"
                                    else "Cloud Worker: ${workerHealth.status} • API Units: ${apiUsage.unitsUsedToday}/${apiUsage.dailyQuotaLimit}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary
                                )
                                Text(
                                    text = if (connectedBilling.connected && connectedBilling.totalMonthlyUsd != null) {
                                        String.format(Locale.US, "Monthly Connected Expense: %s %.2f", connectedBilling.currency, connectedBilling.totalMonthlyUsd)
                                    } else {
                                        "Monthly API Expenses: Billing API Not Connected"
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (connectedBilling.connected) SuccessEmerald else ElectricCyan
                                )
                                LinearProgressIndicator(
                                    progress = { (apiUsage.usagePercent / 100.0).toFloat().coerceIn(0f, 1f) },
                                    color = if (apiUsage.warning80Triggered) WarningAmber else ElectricCyan,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                    DashboardWidgetId.SECURITY_COPYRIGHT_ALERTS.name -> {
                        val flaggedRisk = copyrightReports.firstOrNull { it.riskLevel == "HIGH" || it.riskLevel == "MEDIUM" }
                        val hasSecurityAlert = securitySummary.unknownDeviceDetected || securitySummary.twoFactorReminderDue || flaggedRisk != null
                        Card(
                            colors = CardDefaults.cardColors(containerColor = ElevatedCard),
                            border = BorderStroke(1.dp, if (hasSecurityAlert) WarningAmber else SuccessEmerald.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth().testTag("dashboard_security_copyright_card")
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text("Security & Copyright Notifications", style = MaterialTheme.typography.titleMedium, color = Color.White)
                                    StatusBadge(
                                        text = if (hasSecurityAlert) "ACTION NEEDED" else "PROTECTED",
                                        color = if (hasSecurityAlert) WarningAmber else SuccessEmerald
                                    )
                                }
                                Text(
                                    text = buildString {
                                        append("2SV: ${if (securitySummary.twoFactorVerified) "Confirmed" else "Reminder Active"} • ")
                                        append("Sessions: ${securitySummary.activeSessionCount} active")
                                        if (securitySummary.unknownDeviceDetected) append(" • Unknown device: ${securitySummary.currentDeviceName}")
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary
                                )
                                if (flaggedRisk != null) {
                                    Text(
                                        text = "⚠ Copyright Alert (${flaggedRisk.riskLevel}): '${flaggedRisk.videoTitle}' — ${flaggedRisk.summary.take(90)}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = WarningAmber
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = { onQuickToolSelected("security") }, modifier = Modifier.weight(1f)) {
                                        Text("Security Center", maxLines = 1)
                                    }
                                    OutlinedButton(onClick = { onQuickToolSelected("copyright") }, modifier = Modifier.weight(1f)) {
                                        Text("Copyright Scans", maxLines = 1)
                                    }
                                }
                            }
                        }
                    }

                    DashboardWidgetId.RECENT_ACTIVITY.name -> {
                        Card(
                            onClick = { onQuickToolSelected("activity") },
                            colors = CardDefaults.cardColors(containerColor = ElevatedCard),
                            border = BorderStroke(1.dp, BorderSubtle),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    Text("Recent Studio Activity", style = MaterialTheme.typography.titleMedium, color = Color.White)
                                    Text("View All →", style = MaterialTheme.typography.labelMedium, color = ElectricCyan)
                                }
                                if (activityLogs.isEmpty()) {
                                    Text("No recent studio events recorded yet.", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                                } else {
                                    activityLogs.take(3).forEach { log ->
                                        val ts = SimpleDateFormat("dd MMM HH:mm", Locale.getDefault()).format(Date(log.timestampEpochMs))
                                        Text(
                                            text = "[$ts] ${log.action} — ${log.details}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = TextSecondary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LiveCountdownCard(
    countdown: CountdownState?,
    title: String,
    onOpenLiveControl: () -> Unit
) {
    Card(
        onClick = onOpenLiveControl,
        colors = CardDefaults.cardColors(containerColor = ElevatedCard),
        border = BorderStroke(1.dp, if (countdown != null) ElectricCyan.copy(alpha = 0.65f) else BorderSubtle),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("live_countdown_card")
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(title.uppercase(Locale.US), style = MaterialTheme.typography.labelMedium, color = ElectricCyan, fontWeight = FontWeight.Bold)
                if (countdown != null) {
                    StatusBadge("REMINDERS ON", SuccessEmerald)
                }
            }
            if (countdown == null) {
                Text("No scheduled live broadcast pending countdown.", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
            } else {
                Text(countdown.schedule.liveTitle, style = MaterialTheme.typography.titleMedium, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    CountdownUnitBox(value = countdown.days, unit = "DAYS", modifier = Modifier.weight(1f))
                    CountdownUnitBox(value = countdown.hours, unit = "HRS", modifier = Modifier.weight(1f))
                    CountdownUnitBox(value = countdown.minutes, unit = "MIN", modifier = Modifier.weight(1f))
                    CountdownUnitBox(value = countdown.seconds, unit = "SEC", modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun CountdownUnitBox(value: Long, unit: String, modifier: Modifier = Modifier) {
    Surface(
        color = ElevatedCardHigh,
        border = BorderStroke(1.dp, ElectricBlue.copy(alpha = 0.45f)),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(vertical = 10.dp)
        ) {
            Text(
                text = String.format(Locale.US, "%02d", value),
                style = MaterialTheme.typography.headlineSmall,
                color = ElectricCyan,
                fontWeight = FontWeight.Bold
            )
            Text(text = unit, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        }
    }
}

fun formatDuration(totalSec: Long): String {
    val hrs = totalSec / 3600
    val mins = (totalSec % 3600) / 60
    val secs = totalSec % 60
    return if (hrs > 0) String.format(Locale.US, "%02d:%02d:%02d", hrs, mins, secs)
    else String.format(Locale.US, "%02d:%02d", mins, secs)
}
