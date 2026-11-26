package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.GppGood
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.local.OAuthCredentials
import com.example.data.model.ApiUsageReport
import com.example.data.model.CloudCostBreakdown
import com.example.data.model.CloudVideo
import com.example.data.model.CompetitorChannel
import com.example.data.model.ConnectedBillingReport
import com.example.data.model.CopyrightRiskReport
import com.example.data.model.LiveSchedule
import com.example.data.model.ScheduleStatus
import com.example.data.model.SecurityAlertSummary
import com.example.data.model.SecurityAuditLog
import com.example.data.model.StorageAlertReport
import com.example.data.model.UserSession
import com.example.data.model.VideoPerformanceDetail
import com.example.data.model.WorkerJob
import com.example.data.model.YouTubeAnalyticsSnapshot
import com.example.data.model.YouTubeChannel
import com.example.data.model.YouTubePolicyAlert
import com.example.data.network.WorkerHealthResponse
import com.example.localization.LocalAppStrings
import com.example.media.ExoPlayerVideoPreview
import com.example.ui.components.StatTileCard
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
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AnalyticsV4ReportTab(
    activeChannel: YouTubeChannel?,
    analytics: YouTubeAnalyticsSnapshot,
    videoPerformanceList: List<VideoPerformanceDetail>,
    videoPerformanceStatus: String,
    policyAlerts: List<YouTubePolicyAlert>,
    cloudVideos: List<CloudVideo>,
    schedules: List<LiveSchedule>,
    onLoadAnalytics: (Int) -> Unit,
    onLoadVideoPerformance: () -> Unit
) {
    val strings = LocalAppStrings.current
    val context = LocalContext.current
    val nf = NumberFormat.getIntegerInstance()
    var days by remember { mutableIntStateOf(analytics.dateRangeDays) }
    var previewVideoId by remember { mutableStateOf<String?>(null) }
    val previewableVideos = remember(cloudVideos) {
        cloudVideos.filter { !it.localUri.isNullOrBlank() }
    }
    val selectedPreviewVideo = remember(previewVideoId, previewableVideos) {
        previewableVideos.firstOrNull { it.id == previewVideoId } ?: previewableVideos.firstOrNull()
    }
    val activeLive = schedules.firstOrNull { it.status == ScheduleStatus.LIVE.name }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWide = maxWidth >= 720.dp
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 1100.dp)
                .align(Alignment.TopCenter),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(7, 28, 90).forEach { d ->
                            FilterChip(
                                selected = days == d,
                                onClick = {
                                    days = d
                                    onLoadAnalytics(d)
                                },
                                label = { Text("${d}d") }
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { onLoadAnalytics(days) }) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("Channel API")
                        }
                        Button(
                            onClick = onLoadVideoPerformance,
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                            modifier = Modifier.testTag("fetch_video_performance_btn")
                        ) {
                            Icon(Icons.Default.QueryStats, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("Video Report")
                        }
                    }
                }
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = ElevatedCard),
                    border = BorderStroke(1.dp, if (analytics.authorized) SuccessEmerald else BorderSubtle),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = if (analytics.authorized) "AUTHORIZED YOUTUBE ANALYTICS" else "ANALYTICS STATUS",
                            style = MaterialTheme.typography.labelMedium,
                            color = ElectricCyan
                        )
                        Text(analytics.statusMessage, style = MaterialTheme.typography.bodyMedium, color = Color.White)
                    }
                }
            }

            // Channel & Engagement KPI Grid
            item {
                val avgEngagement = remember(videoPerformanceList) {
                    if (videoPerformanceList.isEmpty()) null
                    else videoPerformanceList.map { it.engagementRatePercent }.average()
                }
                if (isWide) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        StatTileCard("Subscribers", activeChannel?.let { nf.format(it.subscriberCount) } ?: strings.unavailableLabel, Icons.Default.TrendingUp, ElectricBlue, Modifier.weight(1f))
                        StatTileCard("Period Views (${days}d)", analytics.views?.let { nf.format(it) } ?: strings.unavailableLabel, Icons.Default.TrendingUp, ElectricCyan, Modifier.weight(1f))
                        StatTileCard("Watch Time (min)", analytics.watchTimeMinutes?.let { nf.format(it) } ?: strings.unavailableLabel, Icons.Default.TrendingUp, WarningAmber, Modifier.weight(1f))
                        StatTileCard("Avg Engagement", avgEngagement?.let { String.format(Locale.US, "%.2f%%", it) } ?: strings.unavailableLabel, Icons.Default.QueryStats, SuccessEmerald, Modifier.weight(1f))
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            StatTileCard("Subscribers", activeChannel?.let { nf.format(it.subscriberCount) } ?: strings.unavailableLabel, Icons.Default.TrendingUp, ElectricBlue, Modifier.weight(1f))
                            StatTileCard("Period Views (${days}d)", analytics.views?.let { nf.format(it) } ?: strings.unavailableLabel, Icons.Default.TrendingUp, ElectricCyan, Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            StatTileCard("Watch Time (min)", analytics.watchTimeMinutes?.let { nf.format(it) } ?: strings.unavailableLabel, Icons.Default.TrendingUp, WarningAmber, Modifier.weight(1f))
                            StatTileCard("Likes / Comments", if (analytics.likes != null) "${nf.format(analytics.likes)} / ${nf.format(analytics.comments ?: 0)}" else strings.unavailableLabel, Icons.Default.QueryStats, SuccessEmerald, Modifier.weight(1f))
                        }
                    }
                }
            }

            // Live Preview Section (Module 1)
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = ElevatedCard),
                    border = BorderStroke(1.dp, if (activeLive != null) LiveCrimson else ElectricBlue.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().testTag("analytics_live_preview_card")
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.PlayCircle, contentDescription = null, tint = ElectricCyan)
                                Spacer(Modifier.width(8.dp))
                                Text("Live Preview & Playback Monitor", style = MaterialTheme.typography.titleMedium, color = Color.White)
                            }
                            if (activeLive != null) {
                                StatusBadge("ON AIR", LiveCrimson)
                            }
                        }

                        if (activeLive != null && !activeLive.youtubeWatchUrl.isNullOrBlank()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(activeLive.liveTitle, color = Color.White, fontWeight = FontWeight.Bold)
                                    Text(activeLive.youtubeWatchUrl, color = ElectricCyan, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                OutlinedButton(onClick = {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(activeLive.youtubeWatchUrl)))
                                }) {
                                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Watch Live")
                                }
                            }
                        }

                        if (selectedPreviewVideo != null && !selectedPreviewVideo.localUri.isNullOrBlank()) {
                            if (previewableVideos.size > 1) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    previewableVideos.take(4).forEach { v ->
                                        FilterChip(
                                            selected = v.id == selectedPreviewVideo.id,
                                            onClick = { previewVideoId = v.id },
                                            label = { Text(v.title.take(16), maxLines = 1) }
                                        )
                                    }
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(16f / 9f)
                                    .clip(RoundedCornerShape(12.dp))
                            ) {
                                ExoPlayerVideoPreview(
                                    videoUri = Uri.parse(selectedPreviewVideo.localUri),
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        } else {
                            Text(
                                "Select or upload a local video in Library to preview 16:9 playback directly inside Analytics.",
                                color = TextSecondary,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }

            // YouTube Policy Alerts (Module 1)
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = ElevatedCard),
                    border = BorderStroke(1.dp, if (policyAlerts.isNotEmpty()) WarningAmber else BorderSubtle),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().testTag("youtube_policy_alert_card")
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Policy, contentDescription = null, tint = if (policyAlerts.isNotEmpty()) WarningAmber else SuccessEmerald)
                            Spacer(Modifier.width(8.dp))
                            Text("YouTube Policy Alerts (${policyAlerts.size})", style = MaterialTheme.typography.titleMedium, color = Color.White)
                        }
                        if (policyAlerts.isEmpty()) {
                            Text(
                                "No active video rejections, upload failures, or COPPA/licensing alerts detected across fetched videos.",
                                color = TextSecondary,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        } else {
                            policyAlerts.forEach { alert ->
                                Surface(
                                    color = (if (alert.severity == "ERROR") LiveCrimson else WarningAmber).copy(alpha = 0.14f),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text("${alert.source} • ${alert.severity}", color = WarningAmber, fontWeight = FontWeight.Bold)
                                        Text(alert.message, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                                        Text(alert.actionHint, color = TextSecondary, style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Video Performance Report List (Module 1)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Video Performance Report", style = MaterialTheme.typography.titleMedium, color = Color.White)
                    Text(videoPerformanceStatus, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                }
            }

            items(videoPerformanceList, key = { it.videoId }) { vp ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = ElevatedCard),
                    border = BorderStroke(1.dp, if (vp.policyAlerts.isNotEmpty()) WarningAmber.copy(alpha = 0.6f) else BorderSubtle),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(vp.title, color = Color.White, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            StatusBadge(vp.privacyStatus, ElectricCyan)
                        }
                        Text(
                            text = "Views: ${nf.format(vp.viewCount)} • Likes: ${nf.format(vp.likeCount)} • Comments: ${nf.format(vp.commentCount)} • Engagement: ${String.format(Locale.US, "%.2f%%", vp.engagementRatePercent)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = ElectricCyan
                        )
                        Text(
                            text = "Upload: ${vp.uploadStatus} • Watch Time: ${vp.watchTimeMinutes?.let { "$it min" } ?: "N/A"} • License: ${vp.license}",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                        vp.policyAlerts.forEach { flag ->
                            Text("⚠ $flag", color = WarningAmber, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CompetitorsTrackerTab(
    competitors: List<CompetitorChannel>,
    busy: Boolean,
    onAddCompetitor: (String) -> Unit,
    onRefreshCompetitors: () -> Unit,
    onRemoveCompetitor: (String) -> Unit
) {
    val nf = NumberFormat.getIntegerInstance()
    var query by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<CompetitorChannel?>(null) }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Remove Competitor Channel?") },
            text = { Text("Stop tracking '${target.title}' (${target.channelId})?") },
            confirmButton = {
                Button(
                    onClick = {
                        onRemoveCompetitor(target.id)
                        deleteTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = LiveCrimson)
                ) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = ElevatedCard),
                border = BorderStroke(1.dp, ElectricBlue.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("PUBLIC COMPETITOR CHANNEL TRACKER", style = MaterialTheme.typography.labelMedium, color = ElectricCyan)
                    Text(
                        "Uses official YouTube Data API v3 (channels.list) for public subscriber, view & upload benchmarks.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text("@handle, Channel URL, or UC... ID") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("competitor_query_input")
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = {
                                onAddCompetitor(query)
                                query = ""
                            },
                            enabled = !busy && query.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                            modifier = Modifier.weight(1f).testTag("add_competitor_btn")
                        ) {
                            if (busy) CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                            else {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("Track Channel")
                            }
                        }
                        OutlinedButton(
                            onClick = onRefreshCompetitors,
                            enabled = !busy && competitors.isNotEmpty()
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("Sync All")
                        }
                    }
                }
            }
        }

        if (competitors.isEmpty()) {
            item {
                Text("No competitor channels tracked yet. Add any public @handle above.", color = TextSecondary)
            }
        } else {
            items(competitors, key = { it.id }) { comp ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = ElevatedCard),
                    border = BorderStroke(1.dp, BorderSubtle),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!comp.thumbnailUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = comp.thumbnailUrl,
                                contentDescription = comp.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(44.dp).clip(CircleShape)
                            )
                            Spacer(Modifier.width(12.dp))
                        }
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(comp.title, color = Color.White, style = MaterialTheme.typography.titleMedium)
                            Text(comp.handle ?: comp.channelId, color = ElectricCyan, style = MaterialTheme.typography.labelSmall)
                            Text(
                                "Subs: ${nf.format(comp.subscriberCount)} • Views: ${nf.format(comp.viewCount)} • Videos: ${nf.format(comp.videoCount)}",
                                color = TextSecondary,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        IconButton(onClick = { deleteTarget = comp }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Competitor", tint = LiveCrimson)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CopyrightRiskTab(
    videos: List<CloudVideo>,
    reports: List<CopyrightRiskReport>,
    busy: Boolean,
    onRunScan: (videoId: String, title: String, desc: String, tags: List<String>, audioNotes: String, transcriptNotes: String) -> Unit,
    onDeleteReport: (String) -> Unit
) {
    var selectedVideo by remember { mutableStateOf<CloudVideo?>(null) }
    var titleInput by remember { mutableStateOf("") }
    var descInput by remember { mutableStateOf("") }
    var audioNotes by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<CopyrightRiskReport?>(null) }

    deleteTarget?.let { rep ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete Copyright Report?") },
            text = { Text("Remove AI copyright risk scan for '${rep.videoTitle}'?") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteReport(rep.id)
                        deleteTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = LiveCrimson)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = ElevatedCard),
                border = BorderStroke(1.dp, WarningAmber.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("AI COPYRIGHT & POLICY RISK SCANNER", style = MaterialTheme.typography.labelMedium, color = WarningAmber)
                    Text(
                        "Pre-checks music references, third-party clip reuse, and YouTube policy risks using Gemini AI before upload.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    if (videos.isNotEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            videos.take(3).forEach { vid ->
                                FilterChip(
                                    selected = selectedVideo?.id == vid.id,
                                    onClick = {
                                        selectedVideo = vid
                                        titleInput = vid.title
                                        descInput = vid.description
                                    },
                                    label = { Text(vid.title.take(18), maxLines = 1) }
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = titleInput,
                        onValueChange = { titleInput = it },
                        label = { Text("Video Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("copyright_title_input")
                    )
                    OutlinedTextField(
                        value = descInput,
                        onValueChange = { descInput = it },
                        label = { Text("Description & Credits") },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = audioNotes,
                        onValueChange = { audioNotes = it },
                        label = { Text("Music / Audio / Clip Sources Used (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = {
                            onRunScan(
                                selectedVideo?.id.orEmpty(),
                                titleInput,
                                descInput,
                                selectedVideo?.tags.orEmpty(),
                                audioNotes,
                                selectedVideo?.transcriptText.orEmpty()
                            )
                        },
                        enabled = !busy && titleInput.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                        modifier = Modifier.fillMaxWidth().testTag("run_copyright_scan_btn")
                    ) {
                        if (busy) {
                            CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("Scanning with Gemini AI...")
                        } else {
                            Text("Run AI Copyright Risk Check")
                        }
                    }
                }
            }
        }

        items(reports, key = { it.id }) { rep ->
            val riskColor = when (rep.riskLevel) {
                "HIGH" -> LiveCrimson
                "MEDIUM" -> WarningAmber
                else -> SuccessEmerald
            }
            Card(
                colors = CardDefaults.cardColors(containerColor = ElevatedCard),
                border = BorderStroke(1.dp, riskColor.copy(alpha = 0.65f)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(rep.videoTitle, color = Color.White, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        StatusBadge("${rep.riskLevel} RISK", riskColor)
                        IconButton(onClick = { deleteTarget = rep }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Report", tint = LiveCrimson)
                        }
                    }
                    Text(rep.summary, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                    if (rep.triggers.isNotEmpty()) {
                        Text("Triggers: ${rep.triggers.joinToString(" • ")}", color = WarningAmber, style = MaterialTheme.typography.bodyMedium)
                    }
                    if (rep.mitigations.isNotEmpty()) {
                        Text("Mitigations: ${rep.mitigations.joinToString(" • ")}", color = ElectricCyan, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
fun CloudManagementV4Tab(
    workerHealth: WorkerHealthResponse,
    jobs: List<WorkerJob>,
    apiUsage: ApiUsageReport,
    billing: ConnectedBillingReport,
    cost: CloudCostBreakdown,
    storageAlert: StorageAlertReport,
    cleanupCandidateCount: Int,
    onRefreshHealth: () -> Unit,
    onRefreshBilling: () -> Unit,
    onUpdateBudget: (Double) -> Unit,
    onUpdateApiQuotaLimit: (Int) -> Unit,
    onCleanupDrafts: () -> Unit
) {
    var budgetText by remember(cost.budgetLimitUsd) { mutableStateOf(cost.budgetLimitUsd.toInt().toString()) }
    var quotaText by remember(apiUsage.dailyQuotaLimit) { mutableStateOf(apiUsage.dailyQuotaLimit.toString()) }
    var showCleanupConfirm by remember { mutableStateOf(false) }

    if (showCleanupConfirm) {
        AlertDialog(
            onDismissRequest = { showCleanupConfirm = false },
            title = { Text("Confirm Storage Cleanup") },
            text = {
                Text("Delete $cleanupCandidateCount unlinked draft/failed video record(s)? Videos currently bound to active or scheduled live streams will be preserved.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onCleanupDrafts()
                        showCleanupConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = LiveCrimson)
                ) { Text("Delete Unlinked Drafts") }
            },
            dismissButton = {
                TextButton(onClick = { showCleanupConfirm = false }) { Text("Cancel") }
            }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Real Cloud Server Status
        item {
            val statusColor = if (workerHealth.reachable) SuccessEmerald else WarningAmber
            Card(
                colors = CardDefaults.cardColors(containerColor = ElevatedCard),
                border = BorderStroke(1.dp, statusColor.copy(alpha = 0.7f)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().testTag("cloud_server_status_card")
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (workerHealth.reachable) Icons.Default.CloudDone else Icons.Default.CloudOff,
                                contentDescription = null,
                                tint = statusColor
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("CLOUD SERVER STATUS", style = MaterialTheme.typography.labelMedium, color = ElectricCyan)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StatusBadge(workerHealth.status, statusColor)
                            IconButton(onClick = onRefreshHealth) {
                                Icon(Icons.Default.Refresh, contentDescription = "Refresh Server Status", tint = ElectricCyan)
                            }
                        }
                    }
                    Text(workerHealth.message, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = if (workerHealth.reachable) {
                            "Latency: ${workerHealth.latencyMs}ms • Uptime: ${workerHealth.uptimeSec}s • CPU: ${workerHealth.cpuPercent}% • RAM: ${workerHealth.memoryMb} MB • Active Streams: ${workerHealth.activeStreams}"
                        } else {
                            "Actual probe state: ${workerHealth.status} (never simulated)"
                        },
                        color = TextSecondary,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }

        // 2. Server Error Alerts
        item {
            val failedJobs = jobs.filter { it.status == "FAILED" || it.status == "RETRYING" }
            val totalErrors = workerHealth.recentErrors.size + failedJobs.size
            Card(
                colors = CardDefaults.cardColors(containerColor = ElevatedCard),
                border = BorderStroke(1.dp, if (totalErrors > 0) LiveCrimson else BorderSubtle),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Server Error Alerts ($totalErrors)", style = MaterialTheme.typography.titleMedium, color = Color.White)
                    if (totalErrors == 0) {
                        Text("No recent worker errors or failed FFmpeg jobs reported.", color = TextSecondary)
                    } else {
                        workerHealth.recentErrors.take(4).forEach { err ->
                            Text("• Worker: $err", color = LiveCrimson, style = MaterialTheme.typography.bodyMedium)
                        }
                        failedJobs.take(4).forEach { job ->
                            Text("• Job ${job.jobType} [${job.status}]: ${job.message}", color = WarningAmber, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }

        // 3. API Usage Monitor
        item {
            val apiColor = if (apiUsage.warning80Triggered) WarningAmber else ElectricCyan
            Card(
                colors = CardDefaults.cardColors(containerColor = ElevatedCard),
                border = BorderStroke(1.dp, apiColor.copy(alpha = 0.6f)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().testTag("api_usage_monitor_card")
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Speed, contentDescription = null, tint = apiColor)
                            Spacer(Modifier.width(8.dp))
                            Text("API Usage Monitor", style = MaterialTheme.typography.titleMedium, color = Color.White)
                        }
                        StatusBadge("${String.format(Locale.US, "%.1f", apiUsage.usagePercent)}%", apiColor)
                    }
                    LinearProgressIndicator(
                        progress = { (apiUsage.usagePercent / 100.0).toFloat().coerceIn(0f, 1f) },
                        color = apiColor,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "YouTube Data API v3: ${apiUsage.unitsUsedToday} / ${apiUsage.dailyQuotaLimit} units (${apiUsage.quotaResetDate} UTC) • Gemini AI calls this session: ${apiUsage.geminiCallsSession}",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = quotaText,
                            onValueChange = { quotaText = it.filter { ch -> ch.isDigit() } },
                            label = { Text("Daily Quota Limit") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = { onUpdateApiQuotaLimit(quotaText.toIntOrNull() ?: 10_000) },
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)
                        ) { Text("Set Limit") }
                    }
                }
            }
        }

        // 4. Connected Monthly Expense Report
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = ElevatedCard),
                border = BorderStroke(1.dp, if (billing.connected) SuccessEmerald else WarningAmber),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().testTag("monthly_expense_report_card")
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AttachMoney, contentDescription = null, tint = ElectricCyan)
                            Spacer(Modifier.width(6.dp))
                            Text("MONTHLY EXPENSE REPORT", style = MaterialTheme.typography.labelMedium, color = ElectricCyan)
                        }
                        OutlinedButton(onClick = onRefreshBilling) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Fetch Billing")
                        }
                    }
                    if (billing.connected && billing.totalMonthlyUsd != null) {
                        Text(
                            text = String.format(Locale.US, "Actual Connected Spend: %s %.2f", billing.currency, billing.totalMonthlyUsd),
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        billing.billingPeriod?.let { Text("Period: $it • Source: ${billing.source}", color = SuccessEmerald) }
                        billing.lineItems.forEach { item ->
                            Text(String.format(Locale.US, "• %s: $%.2f", item.label, item.amountUsd), color = TextSecondary)
                        }
                    } else {
                        StatusBadge("BILLING API NOT CONNECTED", WarningAmber)
                        Text(billing.statusMessage, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        // 5. Local Rate Estimator & Budget Limit (Clearly labeled as Estimator)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = ElevatedCard),
                border = BorderStroke(1.dp, if (cost.budgetExceeded) LiveCrimson else BorderSubtle),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Workload Rate Estimator (Reference Only)", style = MaterialTheme.typography.titleMedium, color = Color.White)
                    Text(String.format(Locale.US, "Storage (%.2f GB): $%.3f | Transcoding (%.1f min): $%.3f | Streaming (%.1f hrs): $%.3f", cost.storageGb, cost.storageCostUsd, cost.transcodedMinutes, cost.transcodingCostUsd, cost.streamingHours, cost.streamingCostUsd + cost.egressCostUsd), color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = budgetText,
                            onValueChange = { budgetText = it.filter { ch -> ch.isDigit() } },
                            label = { Text("Monthly Budget Alert (USD)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = { onUpdateBudget(budgetText.toDoubleOrNull() ?: 50.0) },
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)
                        ) { Text("Save") }
                    }
                }
            }
        }

        // 6. Cloud Storage Usage & Cleanup Manager
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = ElevatedCard),
                border = BorderStroke(1.dp, BorderSubtle),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Storage, contentDescription = null, tint = ElectricCyan)
                        Spacer(Modifier.width(8.dp))
                        Text("Cloud Storage Usage & Cleanup", style = MaterialTheme.typography.titleMedium, color = Color.White)
                    }
                    Text(
                        String.format(Locale.US, "Used %.2f GB / %.0f GB (%.1f%%) • %d cleanup candidate(s)", storageAlert.usedGb, storageAlert.quotaGb, storageAlert.usagePercent, cleanupCandidateCount),
                        color = TextSecondary
                    )
                    Button(
                        onClick = { showCleanupConfirm = true },
                        enabled = cleanupCandidateCount > 0,
                        colors = ButtonDefaults.buttonColors(containerColor = WarningAmber, contentColor = Color.Black),
                        modifier = Modifier.fillMaxWidth().testTag("cloud_ops_cleanup_btn")
                    ) {
                        Icon(Icons.Default.CleaningServices, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Clean Up $cleanupCandidateCount Unlinked Draft/Failed Video(s)", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun SecurityManagementV4Tab(
    auditLogs: List<SecurityAuditLog>,
    sessions: List<UserSession>,
    currentSessionId: String,
    securitySummary: SecurityAlertSummary,
    oauthCredentials: OAuthCredentials,
    onTrustCurrentDevice: () -> Unit,
    onConfirmTwoFactor: (Boolean) -> Unit,
    onSnoozeTwoFactor: () -> Unit,
    onRevokeSession: (String) -> Unit,
    onRevokeOtherSessions: () -> Unit,
    onSecureLogoutConfirmed: () -> Unit
) {
    val context = LocalContext.current
    var pendingRevokeSession by remember { mutableStateOf<UserSession?>(null) }
    var showRevokeOthersDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }

    pendingRevokeSession?.let { sess ->
        AlertDialog(
            onDismissRequest = { pendingRevokeSession = null },
            title = { Text("Revoke Active Session?") },
            text = { Text("Revoke session for '${sess.deviceName}' (${sess.platform})?") },
            confirmButton = {
                Button(
                    onClick = {
                        onRevokeSession(sess.id)
                        pendingRevokeSession = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = LiveCrimson)
                ) { Text("Revoke Session") }
            },
            dismissButton = {
                TextButton(onClick = { pendingRevokeSession = null }) { Text("Cancel") }
            }
        )
    }

    if (showRevokeOthersDialog) {
        AlertDialog(
            onDismissRequest = { showRevokeOthersDialog = false },
            title = { Text("Sign Out All Other Devices?") },
            text = { Text("All other active sessions for your account will be marked revoked.") },
            confirmButton = {
                Button(
                    onClick = {
                        onRevokeOtherSessions()
                        showRevokeOthersDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = LiveCrimson)
                ) { Text("Revoke Others") }
            },
            dismissButton = {
                TextButton(onClick = { showRevokeOthersDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Confirm Secure Logout") },
            text = { Text("This will revoke the active device session, clear YouTube OAuth tokens from this device, and sign out of DREAMIIT28.") },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutDialog = false
                        onSecureLogoutConfirmed()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = LiveCrimson)
                ) { Text("Secure Logout") }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) { Text("Cancel") }
            }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Unknown Login Alert
        if (securitySummary.unknownDeviceDetected) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = LiveCrimson.copy(alpha = 0.14f)),
                    border = BorderStroke(1.5.dp, LiveCrimson),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().testTag("unknown_login_alert_card")
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = LiveCrimson)
                            Spacer(Modifier.width(8.dp))
                            Text("UNKNOWN LOGIN ALERT", style = MaterialTheme.typography.titleMedium, color = LiveCrimson, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            "Signed in from unrecognized device '${securitySummary.currentDeviceName}'. Verify this login or secure your account.",
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = onTrustCurrentDevice,
                                colors = ButtonDefaults.buttonColors(containerColor = SuccessEmerald, contentColor = Color.Black)
                            ) { Text("Trust This Device", fontWeight = FontWeight.Bold) }
                            OutlinedButton(onClick = { showLogoutDialog = true }) {
                                Text("Secure Logout", color = LiveCrimson)
                            }
                        }
                    }
                }
            }
        }

        // 2. Two-Step Verification Reminder
        item {
            val verified = securitySummary.twoFactorVerified
            Card(
                colors = CardDefaults.cardColors(containerColor = ElevatedCard),
                border = BorderStroke(1.dp, if (verified) SuccessEmerald else WarningAmber),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().testTag("two_step_reminder_card")
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = if (verified) SuccessEmerald else WarningAmber)
                            Spacer(Modifier.width(8.dp))
                            Text("Two-Step Verification (2SV)", style = MaterialTheme.typography.titleMedium, color = Color.White)
                        }
                        StatusBadge(if (verified) "CONFIRMED" else "REMINDER", if (verified) SuccessEmerald else WarningAmber)
                    }
                    Text(
                        if (verified) "2-Step Verification confirmed on your Google Owner account."
                        else "Protect your YouTube channel and Google account by enabling 2-Step Verification in Google Account Security.",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://myaccount.google.com/security")))
                        }) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Google 2SV")
                        }
                        if (!verified) {
                            Button(
                                onClick = { onConfirmTwoFactor(true) },
                                colors = ButtonDefaults.buttonColors(containerColor = SuccessEmerald, contentColor = Color.Black)
                            ) { Text("I Enabled 2SV", fontWeight = FontWeight.Bold) }
                            TextButton(onClick = onSnoozeTwoFactor) { Text("Later") }
                        }
                    }
                }
            }
        }

        // 3. Security Overview & Secure Logout
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = ElevatedCard),
                border = BorderStroke(1.dp, ElectricBlue.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Security, contentDescription = null, tint = SuccessEmerald)
                        Spacer(Modifier.width(8.dp))
                        Text("SECURITY CENTER", style = MaterialTheme.typography.labelMedium, color = SuccessEmerald)
                    }
                    Text(
                        text = if (oauthCredentials.accessToken.isNotBlank()) "YouTube OAuth 2.0 PKCE Token Active (Encrypted DataStore, no passwords stored)"
                        else "YouTube OAuth 2.0: Not Connected (No passwords stored)",
                        color = ElectricCyan,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { showRevokeOthersDialog = true },
                            modifier = Modifier.weight(1f)
                        ) { Text("Sign Out Others") }
                        Button(
                            onClick = { showLogoutDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = LiveCrimson),
                            modifier = Modifier.weight(1f).testTag("logout_all_devices_btn")
                        ) {
                            Icon(Icons.Default.Logout, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Secure Logout")
                        }
                    }
                }
            }
        }

        // 4. Active Session Manager & Login Device History
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Devices, contentDescription = null, tint = ElectricCyan)
                Spacer(Modifier.width(8.dp))
                Text("Active Sessions & Device History (${sessions.size})", style = MaterialTheme.typography.titleMedium, color = Color.White)
            }
        }

        items(sessions, key = { it.id }) { sess ->
            val isCurrent = sess.id == currentSessionId
            val timeStr = SimpleDateFormat("dd MMM HH:mm", Locale.getDefault()).format(Date(sess.lastActiveEpochMs))
            Card(
                colors = CardDefaults.cardColors(containerColor = ElevatedCard),
                border = BorderStroke(1.dp, if (isCurrent && !sess.revoked) ElectricCyan else BorderSubtle),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(sess.deviceName, color = Color.White, style = MaterialTheme.typography.titleMedium)
                            if (isCurrent) StatusBadge("THIS DEVICE", ElectricCyan)
                            if (sess.revoked) StatusBadge("REVOKED", LiveCrimson)
                            else if (sess.trustedDevice) StatusBadge("TRUSTED", SuccessEmerald)
                        }
                        Text("${sess.platform} • Last active: $timeStr", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                    }
                    if (!sess.revoked) {
                        OutlinedButton(onClick = { pendingRevokeSession = sess }) {
                            Text("Revoke", color = LiveCrimson)
                        }
                    }
                }
            }
        }

        // 5. Login Activity Monitor (Audit Logs)
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.GppGood, contentDescription = null, tint = SuccessEmerald)
                Spacer(Modifier.width(8.dp))
                Text("Login Activity Monitor (${auditLogs.size})", style = MaterialTheme.typography.titleMedium, color = Color.White)
            }
        }

        items(auditLogs, key = { it.id }) { log ->
            Card(
                colors = CardDefaults.cardColors(containerColor = ElevatedCard),
                border = BorderStroke(1.dp, BorderSubtle),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(log.eventType, style = MaterialTheme.typography.titleMedium, color = Color.White)
                        StatusBadge(
                            text = log.status,
                            color = if (log.status == "WARNING" || log.status == "ERROR") WarningAmber else SuccessEmerald
                        )
                    }
                    Text("${log.deviceName} • ${log.details}", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                }
            }
        }
    }
}
