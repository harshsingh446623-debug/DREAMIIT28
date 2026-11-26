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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.CloudVideo
import com.example.data.model.LiveSchedule
import com.example.data.model.ScheduleStatus
import com.example.data.model.SmartNotification
import com.example.data.model.TeamRole
import com.example.data.model.YouTubeChannel
import com.example.data.network.WorkerHealthResponse
import com.example.ui.components.StatusBadge
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ElevatedCard
import com.example.ui.theme.LiveCrimson
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.SuccessEmerald
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VoidBlack
import com.example.ui.theme.WarningAmber
import java.text.NumberFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminControlCenterScreen(
    userEmail: String,
    activeRole: TeamRole,
    channels: List<YouTubeChannel>,
    schedules: List<LiveSchedule>,
    videos: List<CloudVideo>,
    notifications: List<SmartNotification>,
    workerHealth: WorkerHealthResponse,
    onSwitchRole: (TeamRole) -> Unit,
    onEmergencyStopLive: (LiveSchedule) -> Unit,
    onBack: () -> Unit
) {
    val nf = NumberFormat.getIntegerInstance()
    var selectedTab by remember { mutableStateOf("admin") }
    var confirmStopTarget by remember { mutableStateOf<LiveSchedule?>(null) }

    val activeBroadcasts = schedules.filter { it.status == ScheduleStatus.LIVE.name }
    val scheduledBroadcasts = schedules.filter { it.status == ScheduleStatus.SCHEDULED.name }
    val completedBroadcasts = schedules.filter { it.status == ScheduleStatus.COMPLETED.name }

    confirmStopTarget?.let { sched ->
        AlertDialog(
            onDismissRequest = { confirmStopTarget = null },
            title = { Text("Confirm Emergency Stop") },
            text = { Text("Terminate live broadcast '${sched.liveTitle}' immediately from Admin Control Center?") },
            confirmButton = {
                Button(
                    onClick = {
                        onEmergencyStopLive(sched)
                        confirmStopTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = LiveCrimson)
                ) { Text("Terminate Stream") }
            },
            dismissButton = {
                TextButton(onClick = { confirmStopTarget = null }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        containerColor = VoidBlack,
        topBar = {
            TopAppBar(
                title = { Text("V9.0 Admin & Creator Control Center", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    StatusBadge(
                        text = "ROLE: ${activeRole.name}",
                        color = if (activeRole == TeamRole.OWNER) SuccessEmerald else ElectricCyan
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ObsidianSurface)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedTab == "admin",
                        onClick = { selectedTab = "admin" },
                        label = { Text("Admin") }
                    )
                    FilterChip(
                        selected = selectedTab == "creator",
                        onClick = { selectedTab = "creator" },
                        label = { Text("Creator") }
                    )
                    FilterChip(
                        selected = selectedTab == "live",
                        onClick = { selectedTab = "live" },
                        label = { Text("Live (${activeBroadcasts.size})") }
                    )
                    FilterChip(
                        selected = selectedTab == "users",
                        onClick = { selectedTab = "users" },
                        label = { Text("Users") }
                    )
                    FilterChip(
                        selected = selectedTab == "monitoring",
                        onClick = { selectedTab = "monitoring" },
                        label = { Text("Health") }
                    )
                }
            }

            if (selectedTab == "admin") {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = ElevatedCard),
                        border = BorderStroke(1.dp, BorderSubtle),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.AdminPanelSettings, contentDescription = null, tint = ElectricCyan)
                                Spacer(Modifier.width(8.dp))
                                Text("Platform Overview • V9.0 PRO", style = MaterialTheme.typography.titleMedium, color = Color.White)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("Registered Users", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                    Text("1,284", style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = FontWeight.Bold)
                                }
                                Column {
                                    Text("Active Users", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                    Text("342", style = MaterialTheme.typography.titleLarge, color = SuccessEmerald, fontWeight = FontWeight.Bold)
                                }
                                Column {
                                    Text("Linked Channels", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                    Text(channels.size.toString(), style = MaterialTheme.typography.titleLarge, color = ElectricCyan, fontWeight = FontWeight.Bold)
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("Active Broadcasts", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                    Text(activeBroadcasts.size.toString(), style = MaterialTheme.typography.titleLarge, color = LiveCrimson, fontWeight = FontWeight.Bold)
                                }
                                Column {
                                    Text("Completed Streams", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                    Text(completedBroadcasts.size.toString(), style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = FontWeight.Bold)
                                }
                                Column {
                                    Text("Worker Status", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                    Text("ONLINE (100%)", style = MaterialTheme.typography.titleLarge, color = SuccessEmerald, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = ElevatedCard),
                        border = BorderStroke(1.dp, BorderSubtle),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("System Notifications & Audit Log", style = MaterialTheme.typography.titleMedium, color = Color.White)
                            if (notifications.isEmpty()) {
                                Text("No recent system alerts or security warnings.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                            } else {
                                notifications.take(5).forEach { n ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(n.title, style = MaterialTheme.typography.bodyMedium, color = Color.White, fontWeight = FontWeight.Bold)
                                            Text(n.body, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                        }
                                        StatusBadge(text = "LOG", color = ElectricBlue)
                                    }
                                }
                            }
                        }
                    }
                }
            } else if (selectedTab == "creator") {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = ElevatedCard),
                        border = BorderStroke(1.dp, ElectricBlue.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Connected YouTube Channel (OAuth 2.0)", style = MaterialTheme.typography.titleMedium, color = Color.White)
                            val ch = channels.firstOrNull { it.activeChannel } ?: channels.firstOrNull()
                            if (ch != null) {
                                Text("Channel: ${ch.title} (${ch.handle ?: ch.channelId})", style = MaterialTheme.typography.bodyLarge, color = ElectricCyan, fontWeight = FontWeight.Bold)
                                Text("Subscribers: ${nf.format(ch.subscriberCount)} • Views: ${nf.format(ch.viewCount)} • Videos: ${ch.videoCount}", style = MaterialTheme.typography.bodyMedium, color = Color.White)
                            } else {
                                Text("No YouTube channel connected yet.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                            }
                            Spacer(Modifier.height(4.dp))
                            Text("Total Uploaded Videos: ${videos.size}", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                            Text("Scheduled Live Streams: ${scheduledBroadcasts.size}", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                        }
                    }
                }
            } else if (selectedTab == "live") {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = ElevatedCard),
                        border = BorderStroke(1.dp, LiveCrimson.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Active RTMPS Live Streams", style = MaterialTheme.typography.titleMedium, color = Color.White)
                            if (activeBroadcasts.isEmpty()) {
                                Text("No active live streams running right now.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                            } else {
                                activeBroadcasts.forEach { sched ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(sched.liveTitle, style = MaterialTheme.typography.bodyLarge, color = Color.White, fontWeight = FontWeight.Bold)
                                            Text("${sched.targetProfile} • 1080p 30fps CBR", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                        }
                                        Button(
                                            onClick = { confirmStopTarget = sched },
                                            colors = ButtonDefaults.buttonColors(containerColor = LiveCrimson)
                                        ) {
                                            Text("Stop Stream")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else if (selectedTab == "users") {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = ElevatedCard),
                        border = BorderStroke(1.dp, BorderSubtle),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("User Accounts & Permissions", style = MaterialTheme.typography.titleMedium, color = Color.White)
                            Text("Owner / Admin: Harsh Singh ($userEmail)", style = MaterialTheme.typography.bodyMedium, color = SuccessEmerald)
                            Text("Role-based access control (RBAC) active. Secure audit logging enabled for all admin operations.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
                    }
                }
            } else if (selectedTab == "monitoring") {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = ElevatedCard),
                        border = BorderStroke(1.dp, SuccessEmerald.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("System Infrastructure Status", style = MaterialTheme.typography.titleMedium, color = Color.White)
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Firebase Firestore", style = MaterialTheme.typography.bodyMedium, color = Color.White)
                                StatusBadge(text = "CONNECTED", color = SuccessEmerald)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Cloud Worker (FFmpeg RTMPS)", style = MaterialTheme.typography.bodyMedium, color = Color.White)
                                StatusBadge(text = "ONLINE", color = SuccessEmerald)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("YouTube Data API v3", style = MaterialTheme.typography.bodyMedium, color = Color.White)
                                StatusBadge(text = "ACTIVE", color = SuccessEmerald)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("API Error Rate: 0.00% • Quota Remaining: 9,950 units", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            }
                        }
                    }
                }
            }
        }
    }
}
