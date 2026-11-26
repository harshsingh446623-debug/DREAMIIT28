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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.CloudVideo
import com.example.data.model.PrivacyStatus
import com.example.data.model.StorageAlertReport
import com.example.ui.components.StatusBadge
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ElevatedCard
import com.example.ui.theme.LiveCrimson
import com.example.ui.theme.SuccessEmerald
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarningAmber
import java.util.Locale

@Composable
fun LibraryScreen(
    videos: List<CloudVideo>,
    storageAlert: StorageAlertReport = StorageAlertReport(
        usedBytes = videos.sumOf { it.fileSizeBytes },
        usedGb = videos.sumOf { it.fileSizeBytes } / (1024.0 * 1024.0 * 1024.0),
        quotaGb = 50.0,
        availableGb = (50.0 - videos.sumOf { it.fileSizeBytes } / (1024.0 * 1024.0 * 1024.0)).coerceAtLeast(0.0),
        usagePercent = 0.0,
        alert80Triggered = false,
        alert90Triggered = false
    ),
    onUpdateQuotaGb: (Double) -> Unit = {},
    onCleanupDrafts: () -> Unit = {},
    onOpenInMediaLab: (CloudVideo) -> Unit = {},
    onUploadClick: () -> Unit,
    onEditVideo: (videoId: String, title: String, description: String, privacy: String, tags: List<String>) -> Unit,
    onDeleteVideo: (videoId: String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var sortBySize by remember { mutableStateOf(false) }
    var editingVideo by remember { mutableStateOf<CloudVideo?>(null) }
    var pendingDeleteVideo by remember { mutableStateOf<CloudVideo?>(null) }
    var showCleanupConfirm by remember { mutableStateOf(false) }

    val filtered = remember(videos, searchQuery, sortBySize) {
        val list = videos.filter {
            searchQuery.isBlank() ||
                it.title.contains(searchQuery, ignoreCase = true) ||
                it.description.contains(searchQuery, ignoreCase = true)
        }
        if (sortBySize) list.sortedByDescending { it.fileSizeBytes } else list
    }

    editingVideo?.let { vid ->
        var editTitle by remember(vid.id) { mutableStateOf(vid.title) }
        var editDesc by remember(vid.id) { mutableStateOf(vid.description) }
        var editPrivacy by remember(vid.id) { mutableStateOf(PrivacyStatus.fromRaw(vid.privacyStatus)) }
        var editTags by remember(vid.id) { mutableStateOf(vid.tags?.joinToString(", ").orEmpty()) }

        AlertDialog(
            onDismissRequest = { editingVideo = null },
            title = { Text("Edit Video Details") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = editTitle,
                        onValueChange = { editTitle = it },
                        label = { Text("Title") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = editDesc,
                        onValueChange = { editDesc = it },
                        label = { Text("Description") },
                        minLines = 2,
                        maxLines = 4
                    )
                    OutlinedTextField(
                        value = editTags,
                        onValueChange = { editTags = it },
                        label = { Text("Tags") },
                        singleLine = true
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PrivacyStatus.entries.forEach { p ->
                            FilterChip(
                                selected = editPrivacy == p,
                                onClick = { editPrivacy = p },
                                label = { Text(p.label) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onEditVideo(
                            vid.id,
                            editTitle,
                            editDesc,
                            editPrivacy.name,
                            editTags.split(",").map { it.trim() }.filter { it.isNotBlank() }
                        )
                        editingVideo = null
                    },
                    enabled = editTitle.isNotBlank()
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { editingVideo = null }) { Text("Cancel") }
            }
        )
    }

    pendingDeleteVideo?.let { vid ->
        AlertDialog(
            onDismissRequest = { pendingDeleteVideo = null },
            title = { Text("Delete Video?") },
            text = { Text("Remove '${vid.title}' from DREAMIIT28 Cloud Library? Videos linked to active live schedules are protected.") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteVideo(vid.id)
                        pendingDeleteVideo = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = LiveCrimson)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteVideo = null }) { Text("Cancel") }
            }
        )
    }

    if (showCleanupConfirm) {
        AlertDialog(
            onDismissRequest = { showCleanupConfirm = false },
            title = { Text("Confirm Storage Cleanup") },
            text = { Text("Delete all unlinked READY/FAILED videos that are not uploaded to YouTube or scheduled for live streaming?") },
            confirmButton = {
                Button(
                    onClick = {
                        onCleanupDrafts()
                        showCleanupConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = LiveCrimson)
                ) { Text("Clean Up Files") }
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
        item {
            val barColor = when {
                storageAlert.alert90Triggered -> LiveCrimson
                storageAlert.alert80Triggered -> WarningAmber
                else -> ElectricBlue
            }
            Card(
                colors = CardDefaults.cardColors(containerColor = ElevatedCard),
                border = BorderStroke(1.dp, barColor.copy(alpha = 0.55f)),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth().testTag("storage_alert_card")
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Storage, contentDescription = null, tint = barColor)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = String.format(Locale.US, "Used %.2f GB / %.0f GB", storageAlert.usedGb, storageAlert.quotaGb),
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White
                            )
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
                        text = String.format(Locale.US, "Available Space: %.2f GB", storageAlert.availableGb),
                        style = MaterialTheme.typography.bodyMedium,
                        color = ElectricCyan
                    )

                    if (storageAlert.alert90Triggered || storageAlert.alert80Triggered) {
                        Surface(
                            color = barColor.copy(alpha = 0.14f),
                            border = BorderStroke(1.dp, barColor),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = barColor)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = if (storageAlert.alert90Triggered) {
                                        "CRITICAL (90%+): Storage almost full! Clean up unlinked drafts or expand quota."
                                    } else {
                                        "WARNING (80%+): Cloud storage crossed 80% threshold."
                                    },
                                    color = barColor,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(10.0, 25.0, 50.0, 100.0).forEach { q ->
                            FilterChip(
                                selected = storageAlert.quotaGb == q,
                                onClick = { onUpdateQuotaGb(q) },
                                label = { Text("${q.toInt()} GB") }
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = { showCleanupConfirm = true },
                        modifier = Modifier.fillMaxWidth().testTag("cleanup_storage_btn")
                    ) {
                        Icon(Icons.Default.CleaningServices, contentDescription = null, tint = ElectricCyan)
                        Spacer(Modifier.width(6.dp))
                        Text("Clean Up Unlinked Draft & Failed Videos")
                    }
                }
            }
        }

        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search cloud videos...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("library_search_input")
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !sortBySize,
                        onClick = { sortBySize = false },
                        label = { Text("Sort: Date") }
                    )
                    FilterChip(
                        selected = sortBySize,
                        onClick = { sortBySize = true },
                        label = { Text("Sort: File Size") }
                    )
                }
                TextButton(onClick = onUploadClick) {
                    Text("+ Upload", color = ElectricCyan, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (filtered.isEmpty()) {
            item {
                Text(
                    text = "No videos in Cloud Library yet. Tap + Upload to add a video.",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            items(filtered, key = { it.id }) { vid ->
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
                                text = vid.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            StatusBadge(
                                text = if (vid.qualityVerified1080p) "1080p" else "${vid.sourceHeight}p",
                                color = if (vid.qualityVerified1080p) SuccessEmerald else WarningAmber
                            )
                        }
                        Text(
                            text = "${vid.sourceWidth}×${vid.sourceHeight} @ ${vid.sourceFps}fps • ${formatDuration(vid.durationSec)} • ${vid.fileSizeBytes / (1024 * 1024)} MB • ${vid.privacyStatus}",
                            style = MaterialTheme.typography.labelSmall,
                            color = ElectricCyan
                        )
                        if (!vid.qualityWarning.isNullOrBlank()) {
                            Text(vid.qualityWarning, style = MaterialTheme.typography.bodyMedium, color = WarningAmber)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            StatusBadge(text = vid.uploadStatus, color = ElectricBlue)
                            Row {
                                IconButton(onClick = { onOpenInMediaLab(vid) }) {
                                    Icon(Icons.Default.ContentCut, contentDescription = "Open in Media Lab", tint = SuccessEmerald)
                                }
                                IconButton(onClick = { editingVideo = vid }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit Video", tint = ElectricCyan)
                                }
                                IconButton(onClick = { pendingDeleteVideo = vid }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete Video", tint = LiveCrimson)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
