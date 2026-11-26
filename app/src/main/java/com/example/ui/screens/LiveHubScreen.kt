package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.ChannelViewerStats
import com.example.data.model.CloudVideo
import com.example.data.model.LiveChatMessage
import com.example.data.model.LivePlaylist
import com.example.data.model.LiveSchedule
import com.example.data.model.ScheduleStatus
import com.example.data.model.StreamHealthReport
import com.example.data.model.StreamingProfile
import com.example.data.model.UploadSchedule
import com.example.data.model.WorkerJob
import com.example.data.network.WorkerHealthResponse
import com.example.localization.LocalAppStrings
import com.example.media.ExoPlayerVideoPreview
import com.example.ui.components.StatusBadge
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ElevatedCard
import com.example.ui.theme.LiveCrimson
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.SuccessEmerald
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarningAmber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LiveHubScreen(
    requestedTab: Int? = null,
    onConsumeRequestedTab: () -> Unit = {},
    schedules: List<LiveSchedule>,
    uploadSchedules: List<UploadSchedule>,
    playlists: List<LivePlaylist>,
    videos: List<CloudVideo>,
    jobs: List<WorkerJob>,
    workerHealth: WorkerHealthResponse,
    ytStreamHealth: StreamHealthReport? = null,
    liveConnectionAlert: String? = null,
    autoStreamRetryEnabled: Boolean = true,
    liveElapsedSec: Long,
    chatMessages: List<LiveChatMessage>,
    chatStats: ChannelViewerStats,
    onOpenNewSchedule: () -> Unit,
    onOpenNewUploadSchedule: () -> Unit,
    onStartLive: (LiveSchedule) -> Unit,
    onStopLive: (LiveSchedule, Boolean) -> Unit,
    onCancelSchedule: (LiveSchedule) -> Unit,
    onDeleteLiveSchedule: (String) -> Unit,
    onReschedule: (LiveSchedule, Long) -> Unit,
    onRetrySchedule: (LiveSchedule) -> Unit,
    onRefreshLiveStreamHealth: () -> Unit = {},
    onToggleAutoRetry: (Boolean) -> Unit = {},
    onTriggerScheduledUploadNow: (UploadSchedule) -> Unit,
    onRescheduleUpload: (UploadSchedule, Long) -> Unit,
    onCancelUpload: (UploadSchedule) -> Unit,
    onDeleteUpload: (String) -> Unit,
    onStartChatPolling: (String) -> Unit,
    onStopChatPolling: () -> Unit,
    onRefreshChat: (String) -> Unit,
    onSendChatMessage: (String) -> Unit,
    onDeleteChatMessage: (String) -> Unit,
    onTimeoutChatUser: (String) -> Unit,
    onCreatePlaylist: (String, List<CloudVideo>, Boolean, Boolean) -> Unit,
    onMovePlaylistItem: (LivePlaylist, Int, Int) -> Unit,
    onRemovePlaylistItem: (LivePlaylist, Int) -> Unit,
    onTogglePlaylistPlay: (LivePlaylist) -> Unit,
    onAdvancePlaylist: (LivePlaylist) -> Unit,
    onDeletePlaylist: (String) -> Unit,
    onRefreshHealth: () -> Unit
) {
    val strings = LocalAppStrings.current
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf(
        strings.controlCenterTab,
        strings.schedulerTab,
        strings.calendarTab,
        strings.scheduledUploadsTab,
        strings.liveChatTab,
        strings.playlistTab,
        strings.healthTab
    )

    LaunchedEffect(requestedTab) {
        if (requestedTab != null && requestedTab in tabs.indices) {
            selectedTab = requestedTab
            onConsumeRequestedTab()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            containerColor = ObsidianSurface,
            contentColor = ElectricCyan,
            edgePadding = 12.dp
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    modifier = Modifier.testTag("live_hub_tab_$index")
                )
            }
        }

        when (selectedTab) {
            0 -> ControlCenterTab(
                schedules = schedules,
                videos = videos,
                workerHealth = workerHealth,
                ytStreamHealth = ytStreamHealth,
                liveConnectionAlert = liveConnectionAlert,
                autoStreamRetryEnabled = autoStreamRetryEnabled,
                liveElapsedSec = liveElapsedSec,
                onStartLive = onStartLive,
                onStopLive = onStopLive,
                onCancelSchedule = onCancelSchedule,
                onRefreshLiveStreamHealth = onRefreshLiveStreamHealth,
                onToggleAutoRetry = onToggleAutoRetry,
                onOpenNewSchedule = onOpenNewSchedule
            )
            1 -> SchedulerTab(
                schedules = schedules,
                onOpenNewSchedule = onOpenNewSchedule,
                onReschedule = onReschedule,
                onCancelSchedule = onCancelSchedule,
                onRetrySchedule = onRetrySchedule
            )
            2 -> ContentCalendarTab(
                schedules = schedules,
                uploadSchedules = uploadSchedules,
                onOpenNewLiveSchedule = onOpenNewSchedule,
                onOpenNewUploadSchedule = onOpenNewUploadSchedule,
                onRescheduleLive = onReschedule,
                onDeleteLive = onDeleteLiveSchedule,
                onRescheduleUpload = onRescheduleUpload,
                onDeleteUpload = onDeleteUpload
            )
            3 -> ScheduledUploadsTab(
                uploadSchedules = uploadSchedules,
                onScheduleNewUpload = onOpenNewUploadSchedule,
                onTriggerNow = onTriggerScheduledUploadNow,
                onReschedule = onRescheduleUpload,
                onCancel = onCancelUpload,
                onDelete = onDeleteUpload
            )
            4 -> LiveChatMonitorTab(
                schedules = schedules,
                chatMessages = chatMessages,
                chatStats = chatStats,
                onStartPolling = onStartChatPolling,
                onStopPolling = onStopChatPolling,
                onRefreshChat = onRefreshChat,
                onSendChatMessage = onSendChatMessage,
                onDeleteChatMessage = onDeleteChatMessage,
                onTimeoutUser = onTimeoutChatUser
            )
            5 -> PlaylistTab(
                playlists = playlists,
                videos = videos,
                onCreatePlaylist = onCreatePlaylist,
                onMoveItem = onMovePlaylistItem,
                onRemoveItem = onRemovePlaylistItem,
                onTogglePlay = onTogglePlaylistPlay,
                onAdvance = onAdvancePlaylist,
                onDelete = onDeletePlaylist
            )
            6 -> HealthMonitorTab(
                workerHealth = workerHealth,
                ytStreamHealth = ytStreamHealth,
                jobs = jobs,
                onRefreshHealth = onRefreshLiveStreamHealth
            )
        }
    }
}

@Composable
private fun ControlCenterTab(
    schedules: List<LiveSchedule>,
    videos: List<CloudVideo>,
    workerHealth: WorkerHealthResponse,
    ytStreamHealth: StreamHealthReport?,
    liveConnectionAlert: String?,
    autoStreamRetryEnabled: Boolean,
    liveElapsedSec: Long,
    onStartLive: (LiveSchedule) -> Unit,
    onStopLive: (LiveSchedule, Boolean) -> Unit,
    onCancelSchedule: (LiveSchedule) -> Unit,
    onRefreshLiveStreamHealth: () -> Unit,
    onToggleAutoRetry: (Boolean) -> Unit,
    onOpenNewSchedule: () -> Unit
) {
    val strings = LocalAppStrings.current
    val context = LocalContext.current
    val active = schedules.firstOrNull { it.status == ScheduleStatus.LIVE.name }
    val nextScheduled = schedules.firstOrNull { it.status == ScheduleStatus.SCHEDULED.name }
    val target = active ?: nextScheduled
    val linkedVideo = remember(target, videos) {
        target?.let { sched -> videos.firstOrNull { it.id == sched.videoId } }
    }
    var confirmStopTarget by remember { mutableStateOf<Pair<LiveSchedule, Boolean>?>(null) }
    var showPreviewPlayer by remember { mutableStateOf(false) }

    confirmStopTarget?.let { (sched, emergency) ->
        AlertDialog(
            onDismissRequest = { confirmStopTarget = null },
            title = { Text(if (emergency) "Confirm Emergency Stop" else "Confirm Stop Live") },
            text = {
                Text("Stop active broadcast '${sched.liveTitle}'? This will terminate the cloud RTMPS encoder and transition the YouTube broadcast.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onStopLive(sched, emergency)
                        confirmStopTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = LiveCrimson)
                ) {
                    Text(if (emergency) "Emergency Stop" else "Stop Live")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmStopTarget = null }) { Text("Keep Streaming") }
            }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (!liveConnectionAlert.isNullOrBlank()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = WarningAmber.copy(alpha = 0.14f)),
                    border = BorderStroke(1.dp, WarningAmber),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().testTag("live_connection_alert_banner")
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

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = ElevatedCard),
                border = BorderStroke(1.dp, if (active != null) LiveCrimson else ElectricBlue.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("LIVE CONTROL CENTER", style = MaterialTheme.typography.labelMedium, color = ElectricCyan)
                        StatusBadge(
                            text = target?.status ?: "STANDBY",
                            color = if (active != null) LiveCrimson else ElectricBlue
                        )
                    }

                    if (target == null) {
                        Text("No active or scheduled stream", style = MaterialTheme.typography.titleLarge, color = Color.White)
                        Text("Schedule a prerecorded video to control its 1080p RTMPS broadcast here.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                        Button(
                            onClick = onOpenNewSchedule,
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Schedule 1080p Live")
                        }
                    } else {
                        val profile = StreamingProfile.fromId(target.targetProfile)
                        Text(target.liveTitle, style = MaterialTheme.typography.titleLarge, color = Color.White)
                        Text("Source Video: ${target.videoTitle}", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)

                        TelemetryRow("Resolution", profile.resolution)
                        TelemetryRow("Frame Rate", "${profile.fps} FPS (${profile.videoCodec} / ${profile.audioCodec})")
                        TelemetryRow("Bitrate", "${profile.videoBitrateKbps / 1000} Mbps + ${profile.audioBitrateKbps} Kbps Audio")
                        TelemetryRow("Worker Server", if (workerHealth.reachable) "${workerHealth.status} (${workerHealth.latencyMs}ms)" else workerHealth.status)
                        TelemetryRow("YouTube Ingest Health", ytStreamHealth?.let { "${it.healthStatus.uppercase(Locale.US)} (${it.lifeCycleStatus})" } ?: "Not probed yet")
                        TelemetryRow("Auto Retries Used", "${target.retryCount} / ${target.maxRetries}")
                        TelemetryRow("Stream Duration", if (active != null) formatDuration(liveElapsedSec) else "00:00")
                        TelemetryRow("YouTube Live URL", target.youtubeWatchUrl ?: "Pending broadcast creation")

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = onRefreshLiveStreamHealth,
                                modifier = Modifier.weight(1f).testTag("probe_stream_health_btn")
                            ) {
                                Icon(Icons.Default.MonitorHeart, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Probe Health", color = ElectricCyan)
                            }
                            if (!target.youtubeWatchUrl.isNullOrBlank()) {
                                OutlinedButton(
                                    onClick = {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(target.youtubeWatchUrl)))
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Watch Live")
                                }
                            }
                        }

                        if (!linkedVideo?.localUri.isNullOrBlank()) {
                            TextButton(onClick = { showPreviewPlayer = !showPreviewPlayer }) {
                                Text(
                                    if (showPreviewPlayer) "Hide Local Source Preview" else "Preview Source Video (16:9)",
                                    color = ElectricCyan
                                )
                            }
                            if (showPreviewPlayer) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(16f / 9f)
                                        .clip(RoundedCornerShape(12.dp))
                                ) {
                                    ExoPlayerVideoPreview(
                                        videoUri = Uri.parse(linkedVideo!!.localUri),
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(4.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            if (active == null) {
                                Button(
                                    onClick = { onStartLive(target) },
                                    colors = ButtonDefaults.buttonColors(containerColor = SuccessEmerald, contentColor = Color.Black),
                                    modifier = Modifier.weight(1f).testTag("control_start_live_btn")
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                                    Spacer(Modifier.width(4.dp))
                                    Text(strings.startLive, fontWeight = FontWeight.Bold)
                                }
                                OutlinedButton(
                                    onClick = { onCancelSchedule(target) },
                                    border = BorderStroke(1.dp, WarningAmber),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Cancel, contentDescription = null, tint = WarningAmber)
                                    Spacer(Modifier.width(4.dp))
                                    Text(strings.cancelSchedule, color = WarningAmber)
                                }
                            } else {
                                Button(
                                    onClick = { confirmStopTarget = active to false },
                                    colors = ButtonDefaults.buttonColors(containerColor = WarningAmber, contentColor = Color.Black),
                                    modifier = Modifier.weight(1f).testTag("control_stop_live_btn")
                                ) {
                                    Icon(Icons.Default.Stop, contentDescription = null)
                                    Spacer(Modifier.width(4.dp))
                                    Text(strings.stopLive, fontWeight = FontWeight.Bold)
                                }
                                Button(
                                    onClick = { confirmStopTarget = active to true },
                                    colors = ButtonDefaults.buttonColors(containerColor = LiveCrimson),
                                    modifier = Modifier.weight(1f).testTag("control_emergency_stop_btn")
                                ) {
                                    Icon(Icons.Default.Warning, contentDescription = null)
                                    Spacer(Modifier.width(4.dp))
                                    Text(strings.emergencyStop, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Auto Stream Retry & Stream Quality Monitor Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = ElevatedCard),
                border = BorderStroke(1.dp, BorderSubtle),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(strings.autoRetryLabel, style = MaterialTheme.typography.titleMedium, color = Color.White)
                            Text(
                                "Automatically retries failed cloud streams (up to 3 attempts) with worker recovery.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                        Switch(
                            checked = autoStreamRetryEnabled,
                            onCheckedChange = onToggleAutoRetry,
                            modifier = Modifier.testTag("auto_stream_retry_switch")
                        )
                    }
                    if (ytStreamHealth != null && ytStreamHealth.configIssues.isNotEmpty()) {
                        Text(
                            "Stream Config Issues: ${ytStreamHealth.configIssues.joinToString(" • ")}",
                            color = WarningAmber,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TelemetryRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        Text(value, style = MaterialTheme.typography.labelMedium, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SchedulerTab(
    schedules: List<LiveSchedule>,
    onOpenNewSchedule: () -> Unit,
    onReschedule: (LiveSchedule, Long) -> Unit,
    onCancelSchedule: (LiveSchedule) -> Unit,
    onRetrySchedule: (LiveSchedule) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Button(
                onClick = onOpenNewSchedule,
                colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Schedule New Broadcast")
            }
        }

        if (schedules.isEmpty()) {
            item {
                Text("No scheduled broadcasts yet.", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            items(schedules, key = { it.id }) { sched ->
                val dateText = SimpleDateFormat("EEE, dd MMM • hh:mm a", Locale.getDefault())
                    .format(Date(sched.scheduledStartEpochMs))
                val statusColor = when (sched.status) {
                    "LIVE" -> LiveCrimson
                    "COMPLETED" -> SuccessEmerald
                    "FAILED" -> WarningAmber
                    "CANCELLED" -> TextSecondary
                    else -> ElectricBlue
                }
                Card(
                    colors = CardDefaults.cardColors(containerColor = ElevatedCard),
                    border = BorderStroke(1.dp, BorderSubtle),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = sched.liveTitle,
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                modifier = Modifier.weight(1f)
                            )
                            StatusBadge(text = sched.status, color = statusColor)
                        }
                        Text("$dateText (${sched.timezone}) • ${sched.recurrence}", style = MaterialTheme.typography.bodyMedium, color = ElectricCyan)
                        Text("Profile: ${sched.targetProfile} • Retries: ${sched.retryCount}/${sched.maxRetries}", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        if (!sched.lastError.isNullOrBlank()) {
                            Text(sched.lastError, style = MaterialTheme.typography.bodyMedium, color = WarningAmber)
                        }

                        if (sched.status == "SCHEDULED" || sched.status == "FAILED") {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = { onReschedule(sched, sched.scheduledStartEpochMs + 3600_000L) }
                                ) {
                                    Text("+1 Hr Reschedule")
                                }
                                if (sched.status == "FAILED") {
                                    Button(
                                        onClick = { onRetrySchedule(sched) },
                                        colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)
                                    ) {
                                        Text("Retry")
                                    }
                                } else {
                                    TextButton(onClick = { onCancelSchedule(sched) }) {
                                        Text("Cancel", color = LiveCrimson)
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
private fun PlaylistTab(
    playlists: List<LivePlaylist>,
    videos: List<CloudVideo>,
    onCreatePlaylist: (String, List<CloudVideo>, Boolean, Boolean) -> Unit,
    onMoveItem: (LivePlaylist, Int, Int) -> Unit,
    onRemoveItem: (LivePlaylist, Int) -> Unit,
    onTogglePlay: (LivePlaylist) -> Unit,
    onAdvance: (LivePlaylist) -> Unit,
    onDelete: (String) -> Unit
) {
    var showCreate by remember { mutableStateOf(false) }
    var playlistTitle by remember { mutableStateOf("") }
    var endOnFinish by remember { mutableStateOf(true) }
    var loopPlaylist by remember { mutableStateOf(false) }
    val selectedVideos = remember { mutableStateListOf<CloudVideo>() }

    if (showCreate) {
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text("Create Sequential Live Playlist") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = playlistTitle,
                        onValueChange = { playlistTitle = it },
                        label = { Text("Playlist Name") },
                        singleLine = true
                    )
                    Text("Select Videos (${selectedVideos.size} chosen)", style = MaterialTheme.typography.labelLarge)
                    videos.take(6).forEach { vid ->
                        val checked = selectedVideos.any { it.id == vid.id }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { isChecked ->
                                    if (isChecked) selectedVideos.add(vid) else selectedVideos.removeAll { it.id == vid.id }
                                }
                            )
                            Text(vid.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("End broadcast after playlist", style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = endOnFinish, onCheckedChange = { endOnFinish = it })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Loop playlist (explicit only)", style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = loopPlaylist, onCheckedChange = { loopPlaylist = it })
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onCreatePlaylist(playlistTitle.ifBlank { "Live Queue" }, selectedVideos.toList(), endOnFinish, loopPlaylist)
                        selectedVideos.clear()
                        playlistTitle = ""
                        showCreate = false
                    },
                    enabled = selectedVideos.isNotEmpty()
                ) { Text("Save Playlist") }
            },
            dismissButton = {
                TextButton(onClick = { showCreate = false }) { Text("Cancel") }
            }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Button(
                onClick = { showCreate = true },
                colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("New Sequential Live Playlist")
            }
        }

        items(playlists, key = { it.id }) { pl ->
            Card(
                colors = CardDefaults.cardColors(containerColor = ElevatedCard),
                border = BorderStroke(1.dp, BorderSubtle),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(pl.title, style = MaterialTheme.typography.titleMedium, color = Color.White)
                        StatusBadge(text = pl.status, color = if (pl.status == "PLAYING") LiveCrimson else ElectricCyan)
                    }
                    val currentTitle = pl.videoTitles.getOrNull(pl.currentIndex) ?: "None"
                    val nextTitle = pl.videoTitles.getOrNull(pl.currentIndex + 1) ?: if (pl.loopPlaylist) pl.videoTitles.firstOrNull() ?: "End" else "End Broadcast"
                    Text("Now: $currentTitle  •  Next: $nextTitle", style = MaterialTheme.typography.bodyMedium, color = ElectricCyan)
                    Text("Auto-end: ${pl.endBroadcastOnFinish} • Loop: ${pl.loopPlaylist}", style = MaterialTheme.typography.labelSmall, color = TextSecondary)

                    pl.videoTitles.forEachIndexed { idx, title ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "${idx + 1}. $title",
                                color = if (idx == pl.currentIndex) ElectricCyan else Color.White,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            IconButton(onClick = { onMoveItem(pl, idx, idx - 1) }, enabled = idx > 0) {
                                Icon(Icons.Default.ArrowUpward, contentDescription = "Move Up", tint = TextSecondary, modifier = Modifier.size(18.dp))
                            }
                            IconButton(onClick = { onMoveItem(pl, idx, idx + 1) }, enabled = idx < pl.videoTitles.lastIndex) {
                                Icon(Icons.Default.ArrowDownward, contentDescription = "Move Down", tint = TextSecondary, modifier = Modifier.size(18.dp))
                            }
                            IconButton(onClick = { onRemoveItem(pl, idx) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Remove", tint = LiveCrimson, modifier = Modifier.size(18.dp))
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { onTogglePlay(pl) },
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)
                        ) {
                            Text(if (pl.status == "PLAYING") "Pause Queue" else "Start Queue")
                        }
                        OutlinedButton(onClick = { onAdvance(pl) }) {
                            Icon(Icons.Default.SkipNext, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("Next Video")
                        }
                        IconButton(onClick = { onDelete(pl.id) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Playlist", tint = LiveCrimson)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HealthMonitorTab(
    workerHealth: WorkerHealthResponse,
    ytStreamHealth: StreamHealthReport?,
    jobs: List<WorkerJob>,
    onRefreshHealth: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = ElevatedCard),
                border = BorderStroke(1.dp, if (workerHealth.reachable) SuccessEmerald else WarningAmber),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("CLOUD STREAMING HEALTH", style = MaterialTheme.typography.labelMedium, color = ElectricCyan)
                        IconButton(onClick = onRefreshHealth) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh Worker Health", tint = ElectricCyan)
                        }
                    }
                    Text(workerHealth.message, style = MaterialTheme.typography.bodyMedium, color = Color.White)
                    TelemetryRow("Server CPU", if (workerHealth.reachable) "${workerHealth.cpuPercent}%" else "Unavailable")
                    TelemetryRow("Server Memory", if (workerHealth.reachable) "${workerHealth.memoryMb} MB" else "Unavailable")
                    TelemetryRow("Network Throughput", if (workerHealth.reachable) "${workerHealth.networkMbps} Mbps" else "Unavailable")
                    TelemetryRow("Round-Trip Latency", if (workerHealth.reachable) "${workerHealth.latencyMs} ms" else "Unavailable")
                    TelemetryRow("Worker Uptime", if (workerHealth.reachable) "${workerHealth.uptimeSec}s" else "Unavailable")
                    TelemetryRow("FFmpeg Encoder", if (workerHealth.reachable && workerHealth.ffmpegAvailable) "Ready (H.264/AAC)" else workerHealth.status)
                    TelemetryRow("Active RTMPS Streams", workerHealth.activeStreams.toString())
                    if (ytStreamHealth != null) {
                        TelemetryRow("YouTube Broadcast State", ytStreamHealth.lifeCycleStatus)
                        TelemetryRow("YouTube Ingest Health", ytStreamHealth.healthStatus)
                    }
                }
            }
        }

        if (workerHealth.recentErrors.isNotEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = LiveCrimson.copy(alpha = 0.14f)),
                    border = BorderStroke(1.dp, LiveCrimson),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Server Error Alerts", style = MaterialTheme.typography.titleMedium, color = LiveCrimson)
                        workerHealth.recentErrors.take(5).forEach { err ->
                            Text("• $err", style = MaterialTheme.typography.bodyMedium, color = Color.White)
                        }
                    }
                }
            }
        }

        item {
            Text("Background Worker & Recovery History", style = MaterialTheme.typography.titleMedium, color = Color.White)
        }

        items(jobs, key = { it.id }) { job ->
            Card(
                colors = CardDefaults.cardColors(containerColor = ElevatedCard),
                border = BorderStroke(1.dp, BorderSubtle),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("${job.jobType} (Attempt #${job.attemptNumber})", style = MaterialTheme.typography.titleMedium, color = Color.White)
                        StatusBadge(
                            text = job.status,
                            color = when (job.status) {
                                "RUNNING" -> LiveCrimson
                                "COMPLETED" -> SuccessEmerald
                                "FAILED" -> WarningAmber
                                else -> ElectricBlue
                            }
                        )
                    }
                    Text(job.message, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                    Text(
                        text = "Bitrate: ${job.bitrateKbps} kbps • Dropped Frames: ${job.droppedFrames} • CPU: ${job.cpuPercent}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = ElectricCyan
                    )
                }
            }
        }
    }
}

