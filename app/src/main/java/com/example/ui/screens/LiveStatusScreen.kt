package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.example.data.model.LiveSchedule
import com.example.data.model.ScheduleStatus
import com.example.data.model.StreamHealthReport
import com.example.data.model.StreamingProfile
import com.example.data.model.WorkerJob
import com.example.data.network.WorkerHealthResponse
import com.example.ui.components.StatusBadge
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ElevatedCard
import com.example.ui.theme.LiveCrimson
import com.example.ui.theme.SuccessEmerald
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarningAmber
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LiveStatusScreen(
    schedules: List<LiveSchedule>,
    jobs: List<WorkerJob>,
    workerHealth: WorkerHealthResponse,
    ytStreamHealth: StreamHealthReport?,
    liveConnectionAlert: String?,
    liveElapsedSec: Long,
    onRefreshHealth: () -> Unit,
    onProbeStream: () -> Unit
) {
    val activeSchedule = schedules.firstOrNull { it.status == ScheduleStatus.LIVE.name }
        ?: schedules.firstOrNull { it.status == ScheduleStatus.PREPARING.name }
        ?: schedules.firstOrNull()

    val profile = StreamingProfile.fromId(activeSchedule?.targetProfile)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("live_status_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.MonitorHeart, contentDescription = null, tint = ElectricCyan)
                    Spacer(Modifier.width(8.dp))
                    Text("Live Status & Real-Time Telemetry", style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = FontWeight.Bold)
                }
                IconButton(onClick = onRefreshHealth, modifier = Modifier.testTag("refresh_live_status_btn")) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = ElectricCyan)
                }
            }
        }

        if (!liveConnectionAlert.isNullOrBlank()) {
            item {
                Card(
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

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = ElevatedCard),
                border = BorderStroke(1.5.dp, if (activeSchedule?.status == ScheduleStatus.LIVE.name) LiveCrimson else ElectricBlue.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("ACTIVE BROADCAST ENGINE", style = MaterialTheme.typography.labelMedium, color = ElectricCyan)
                        StatusBadge(
                            text = activeSchedule?.status ?: "STANDBY",
                            color = if (activeSchedule?.status == ScheduleStatus.LIVE.name) LiveCrimson else ElectricBlue
                        )
                    }

                    if (activeSchedule == null) {
                        Text("No active or scheduled broadcast found.", style = MaterialTheme.typography.titleMedium, color = Color.White)
                        Text("Schedule a broadcast in Live Hub to view real-time Firestore streaming listeners.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                    } else {
                        Text(activeSchedule.liveTitle, style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = FontWeight.Bold)
                        Text("Source Video: ${activeSchedule.videoTitle}", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)

                        Spacer(Modifier.height(4.dp))

                        MetricRow("Target Resolution", profile.resolution, Icons.Default.LiveTv, ElectricCyan)
                        MetricRow("Bitrate (CBR)", "${profile.videoBitrateKbps / 1000} Mbps (${profile.videoCodec} / ${profile.audioCodec})", Icons.Default.Speed, SuccessEmerald)
                        MetricRow("Frame Rate & GOP", "${profile.fps} FPS • 2s Keyframes", Icons.Default.Speed, ElectricBlue)
                        MetricRow("Dropped Frames", "${activeSchedule.droppedFrames ?: 0} frames", Icons.Default.Warning, if ((activeSchedule.droppedFrames ?: 0) > 20) WarningAmber else SuccessEmerald)
                        MetricRow("Elapsed Duration", formatDuration(liveElapsedSec), Icons.Default.CheckCircle, Color.White)
                        MetricRow("YouTube Confirmed Live", if (activeSchedule.youtubeConfirmedLive == true) "Yes (Verified liveBroadcasts.list)" else "Awaiting YouTube Live", Icons.Default.CheckCircle, if (activeSchedule.youtubeConfirmedLive == true) SuccessEmerald else WarningAmber)
                        MetricRow("Recovery State", activeSchedule.recoveryState ?: "HEALTHY", Icons.Default.MonitorHeart, ElectricCyan)

                        Spacer(Modifier.height(4.dp))
                        Button(
                            onClick = onProbeStream,
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                            modifier = Modifier.fillMaxWidth().testTag("probe_youtube_stream_health_btn")
                        ) {
                            Icon(Icons.Default.MonitorHeart, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Probe YouTube & Worker Health Now")
                        }
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = ElevatedCard),
                border = BorderStroke(1.dp, if (workerHealth.reachable) SuccessEmerald else WarningAmber),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (workerHealth.reachable) Icons.Default.CloudDone else Icons.Default.CloudOff,
                                contentDescription = null,
                                tint = if (workerHealth.reachable) SuccessEmerald else WarningAmber
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Dedicated Cloud VPS / Worker Status", style = MaterialTheme.typography.titleMedium, color = Color.White)
                        }
                        StatusBadge(workerHealth.status, if (workerHealth.reachable) SuccessEmerald else WarningAmber)
                    }

                    Text(workerHealth.message, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                    MetricRow("Worker CPU Usage", "${workerHealth.cpuPercent}%", Icons.Default.Speed, ElectricCyan)
                    MetricRow("Worker RAM Usage", "${workerHealth.memoryMb} MB", Icons.Default.Speed, ElectricBlue)
                    MetricRow("Network Throughput", "${workerHealth.networkMbps} Mbps", Icons.Default.Speed, SuccessEmerald)
                    MetricRow("Round-Trip Latency", "${workerHealth.latencyMs} ms", Icons.Default.MonitorHeart, ElectricCyan)
                    MetricRow("FFmpeg Encoder Status", if (workerHealth.ffmpegAvailable) "Ready (H.264 / AAC CBR)" else "Offline", Icons.Default.CheckCircle, if (workerHealth.ffmpegAvailable) SuccessEmerald else WarningAmber)

                    if (workerHealth.cpuPercent > 85.0) {
                        Surface(
                            color = WarningAmber.copy(alpha = 0.14f),
                            border = BorderStroke(1.dp, WarningAmber),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "Warning: Worker CPU usage is high. Consider switching to a lower encoding profile or restarting worker.",
                                color = WarningAmber,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }
            }
        }

        item {
            Text("Real-Time Worker Job & Recovery Log", style = MaterialTheme.typography.titleMedium, color = Color.White)
        }

        if (jobs.isEmpty()) {
            item {
                Text("No background worker jobs recorded yet.", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            items(jobs.take(10), key = { it.id }) { job ->
                val timeStr = job.createdAt?.toDate()?.let {
                    SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(it)
                } ?: "Recent"
                Card(
                    colors = CardDefaults.cardColors(containerColor = ElevatedCard),
                    border = BorderStroke(1.dp, BorderSubtle),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
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
                        Text("[$timeStr] ${job.message}", style = MaterialTheme.typography.bodyMedium, color = TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
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
}

@Composable
private fun MetricRow(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        }
        Text(value, style = MaterialTheme.typography.labelMedium, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
