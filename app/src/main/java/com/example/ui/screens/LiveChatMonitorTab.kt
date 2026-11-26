package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import com.example.data.model.ChannelViewerStats
import com.example.data.model.LiveChatMessage
import com.example.data.model.LiveSchedule
import com.example.ui.components.StatusBadge
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ElevatedCard
import com.example.ui.theme.LiveCrimson
import com.example.ui.theme.SuccessEmerald
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarningAmber

@Composable
fun LiveChatMonitorTab(
    schedules: List<LiveSchedule>,
    chatMessages: List<LiveChatMessage>,
    chatStats: ChannelViewerStats,
    onStartPolling: (broadcastId: String) -> Unit,
    onStopPolling: () -> Unit,
    onRefreshChat: (broadcastId: String) -> Unit,
    onSendChatMessage: (String) -> Unit,
    onDeleteChatMessage: (String) -> Unit,
    onTimeoutUser: (String) -> Unit
) {
    val broadcastsWithId = remember(schedules) {
        schedules.filter { !it.youtubeBroadcastId.isNullOrBlank() }
    }
    var selectedBroadcast by remember(broadcastsWithId) {
        mutableStateOf(
            broadcastsWithId.firstOrNull { it.status == "LIVE" } ?: broadcastsWithId.firstOrNull()
        )
    }
    var replyText by remember { mutableStateOf("") }

    LaunchedEffect(selectedBroadcast?.youtubeBroadcastId) {
        val bid = selectedBroadcast?.youtubeBroadcastId
        if (!bid.isNullOrBlank()) {
            onStartPolling(bid)
        }
    }

    DisposableEffect(Unit) {
        onDispose { onStopPolling() }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = ElevatedCard),
                    border = BorderStroke(1.dp, ElectricBlue.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("YOUTUBE LIVE CHAT MONITOR", style = MaterialTheme.typography.labelMedium, color = ElectricCyan)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                StatusBadge(
                                    text = chatStats.concurrentViewers?.let { "$it Watching" } ?: "Viewers: Unavailable",
                                    color = if (chatStats.concurrentViewers != null) LiveCrimson else TextSecondary
                                )
                                selectedBroadcast?.youtubeBroadcastId?.let { bid ->
                                    IconButton(onClick = { onRefreshChat(bid) }) {
                                        Icon(Icons.Default.Refresh, contentDescription = "Refresh Chat", tint = ElectricCyan)
                                    }
                                }
                            }
                        }
                        Text(chatStats.statusText, style = MaterialTheme.typography.bodyMedium, color = Color.White)
                        if (broadcastsWithId.isEmpty()) {
                            Text(
                                "No YouTube-provisioned broadcasts found yet. Authorize YouTube in Settings and schedule a Live broadcast first.",
                                color = WarningAmber,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                broadcastsWithId.take(4).forEach { s ->
                                    FilterChip(
                                        selected = selectedBroadcast?.id == s.id,
                                        onClick = { selectedBroadcast = s },
                                        label = { Text(s.liveTitle.take(20)) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (chatMessages.isEmpty()) {
                item {
                    Text(
                        "Incoming YouTube Live Chat messages will appear automatically here every 6 seconds when a broadcast chat is active.",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                items(chatMessages, key = { it.id }) { msg ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = ElevatedCard),
                        border = BorderStroke(1.dp, BorderSubtle),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = msg.authorName,
                                        color = if (msg.isOwner) ElectricCyan else if (msg.isModerator) SuccessEmerald else Color.White,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (msg.isOwner) StatusBadge("OWNER", ElectricCyan)
                                    else if (msg.isModerator) StatusBadge("MOD", SuccessEmerald)
                                }
                                Text(msg.messageText, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                            }
                            IconButton(onClick = { onDeleteChatMessage(msg.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete Message", tint = LiveCrimson, modifier = Modifier.size(18.dp))
                            }
                            if (!msg.isOwner && msg.authorChannelId.isNotBlank()) {
                                IconButton(onClick = { onTimeoutUser(msg.authorChannelId) }) {
                                    Icon(Icons.Default.Block, contentDescription = "Timeout User 5m", tint = WarningAmber, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        if (!chatStats.activeLiveChatId.isNullOrBlank()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = replyText,
                    onValueChange = { replyText = it },
                    placeholder = { Text("Send message as channel...") },
                    singleLine = true,
                    modifier = Modifier.weight(1f).testTag("live_chat_input")
                )
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        if (replyText.isNotBlank()) {
                            onSendChatMessage(replyText.trim())
                            replyText = ""
                        }
                    },
                    modifier = Modifier.testTag("live_chat_send_btn")
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send Chat", tint = ElectricCyan)
                }
            }
        }
    }
}
