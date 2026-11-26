package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.LiveSchedule
import com.example.data.model.UploadSchedule
import com.example.ui.components.StatusBadge
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ElevatedCard
import com.example.ui.theme.LiveCrimson
import com.example.ui.theme.SuccessEmerald
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarningAmber
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private sealed class CalendarTask(val epochMs: Long, val id: String) {
    data class Live(val schedule: LiveSchedule) : CalendarTask(schedule.scheduledStartEpochMs, "L_${schedule.id}")
    data class Upload(val upload: UploadSchedule) : CalendarTask(upload.scheduledPublishEpochMs, "U_${upload.id}")
}

@Composable
fun ContentCalendarTab(
    schedules: List<LiveSchedule>,
    uploadSchedules: List<UploadSchedule>,
    onOpenNewLiveSchedule: () -> Unit,
    onOpenNewUploadSchedule: () -> Unit,
    onRescheduleLive: (LiveSchedule, Long) -> Unit,
    onDeleteLive: (String) -> Unit,
    onRescheduleUpload: (UploadSchedule, Long) -> Unit,
    onDeleteUpload: (String) -> Unit
) {
    var mode by remember { mutableIntStateOf(0) } // 0=Monthly, 1=Weekly, 2=Daily
    var selectedDayMs by remember { mutableLongStateOf(startOfDayMs(System.currentTimeMillis())) }
    var monthOffset by remember { mutableIntStateOf(0) }

    val allTasks = remember(schedules, uploadSchedules) {
        (schedules.filter { it.status != "CANCELLED" }.map { CalendarTask.Live(it) } +
            uploadSchedules.filter { it.status != "CANCELLED" }.map { CalendarTask.Upload(it) })
            .sortedBy { it.epochMs }
    }

    val monthCal = remember(monthOffset) {
        Calendar.getInstance().apply {
            add(Calendar.MONTH, monthOffset)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }
    val monthTitle = remember(monthCal) {
        SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(monthCal.time)
    }

    val filteredTasks = remember(allTasks, mode, selectedDayMs) {
        when (mode) {
            2 -> {
                val endDay = selectedDayMs + 86_400_000L
                allTasks.filter { it.epochMs in selectedDayMs until endDay }
            }
            1 -> {
                val endWeek = selectedDayMs + 7 * 86_400_000L
                allTasks.filter { it.epochMs in selectedDayMs until endWeek }
            }
            else -> {
                val endDay = selectedDayMs + 86_400_000L
                val dayItems = allTasks.filter { it.epochMs in selectedDayMs until endDay }
                dayItems.ifEmpty { allTasks.take(15) }
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Monthly", "Weekly", "Daily").forEachIndexed { idx, label ->
                    FilterChip(
                        selected = mode == idx,
                        onClick = { mode = idx },
                        label = { Text(label) },
                        modifier = Modifier.testTag("calendar_mode_$idx")
                    )
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = onOpenNewLiveSchedule,
                    colors = ButtonDefaults.buttonColors(containerColor = LiveCrimson),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.LiveTv, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("+ Live")
                }
                Button(
                    onClick = onOpenNewUploadSchedule,
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("+ Auto-Upload")
                }
            }
        }

        if (mode == 0) {
            item {
                val daysInMonth = monthCal.getActualMaximum(Calendar.DAY_OF_MONTH)
                val firstDayOffset = (monthCal.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY).coerceIn(0, 6)
                Card(
                    colors = CardDefaults.cardColors(containerColor = ElevatedCard),
                    border = BorderStroke(1.dp, BorderSubtle),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { monthOffset-- }) {
                                Icon(Icons.Default.ChevronLeft, contentDescription = "Previous Month", tint = ElectricCyan)
                            }
                            Text(monthTitle, style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
                            IconButton(onClick = { monthOffset++ }) {
                                Icon(Icons.Default.ChevronRight, contentDescription = "Next Month", tint = ElectricCyan)
                            }
                        }
                        Row(modifier = Modifier.fillMaxWidth()) {
                            listOf("S", "M", "T", "W", "T", "F", "S").forEach { d ->
                                Text(d, color = TextSecondary, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                            }
                        }
                        val totalCells = firstDayOffset + daysInMonth
                        val rows = (totalCells + 6) / 7
                        for (r in 0 until rows) {
                            Row(modifier = Modifier.fillMaxWidth()) {
                                for (c in 0 until 7) {
                                    val cellIdx = r * 7 + c
                                    val dayNum = cellIdx - firstDayOffset + 1
                                    if (dayNum in 1..daysInMonth) {
                                        val dayStart = (monthCal.clone() as Calendar).apply {
                                            set(Calendar.DAY_OF_MONTH, dayNum)
                                        }.timeInMillis
                                        val dayEnd = dayStart + 86_400_000L
                                        val hasLive = schedules.any { it.scheduledStartEpochMs in dayStart until dayEnd && it.status != "CANCELLED" }
                                        val hasUpload = uploadSchedules.any { it.scheduledPublishEpochMs in dayStart until dayEnd && it.status != "CANCELLED" }
                                        val isSelected = dayStart == selectedDayMs
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(42.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (isSelected) ElectricBlue.copy(alpha = 0.28f) else Color.Transparent)
                                                .clickable { selectedDayMs = dayStart }
                                        ) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Text(
                                                    text = dayNum.toString(),
                                                    color = if (isSelected) ElectricCyan else Color.White,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                                )
                                                if (hasLive || hasUpload) {
                                                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                                        if (hasLive) Box(Modifier.size(5.dp).clip(CircleShape).background(LiveCrimson))
                                                        if (hasUpload) Box(Modifier.size(5.dp).clip(CircleShape).background(ElectricCyan))
                                                    }
                                                }
                                            }
                                        }
                                    } else {
                                        Spacer(Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            item {
                val rangeLabel = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(selectedDayMs))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { selectedDayMs -= if (mode == 1) 7 * 86_400_000L else 86_400_000L }) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = "Previous", tint = ElectricCyan)
                    }
                    Text(
                        text = if (mode == 1) "Week from $rangeLabel" else "Day: $rangeLabel",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                    IconButton(onClick = { selectedDayMs += if (mode == 1) 7 * 86_400_000L else 86_400_000L }) {
                        Icon(Icons.Default.ChevronRight, contentDescription = "Next", tint = ElectricCyan)
                    }
                }
            }
        }

        item {
            Text(
                text = "Scheduled Tasks (${filteredTasks.size}) • Conflict Prevention Active (15m buffer)",
                style = MaterialTheme.typography.labelLarge,
                color = ElectricCyan
            )
        }

        if (filteredTasks.isEmpty()) {
            item {
                Text("No scheduled uploads or live broadcasts for this period.", color = TextSecondary)
            }
        } else {
            items(filteredTasks, key = { it.id }) { task ->
                val timeStr = SimpleDateFormat("EEE, dd MMM • hh:mm a", Locale.getDefault()).format(Date(task.epochMs))
                when (task) {
                    is CalendarTask.Live -> {
                        val s = task.schedule
                        Card(
                            colors = CardDefaults.cardColors(containerColor = ElevatedCard),
                            border = BorderStroke(1.dp, LiveCrimson.copy(alpha = 0.45f)),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("LIVE: ${s.liveTitle}", color = Color.White, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                    StatusBadge(s.status, LiveCrimson)
                                }
                                Text("$timeStr (${s.timezone})", color = ElectricCyan, style = MaterialTheme.typography.bodyMedium)
                                if (s.status == "SCHEDULED") {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(onClick = { onRescheduleLive(s, s.scheduledStartEpochMs + 3600_000L) }) { Text("+1h") }
                                        OutlinedButton(onClick = { onRescheduleLive(s, s.scheduledStartEpochMs + 86_400_000L) }) { Text("+1d") }
                                        if (s.status != "LIVE") {
                                            var confirmDeleteLive by remember(s.id) { mutableStateOf(false) }
                                            if (confirmDeleteLive) {
                                                AlertDialog(
                                                    onDismissRequest = { confirmDeleteLive = false },
                                                    title = { Text("Delete Live Schedule?") },
                                                    text = { Text("Remove '${s.liveTitle}' from calendar?") },
                                                    confirmButton = {
                                                        Button(
                                                            onClick = {
                                                                onDeleteLive(s.id)
                                                                confirmDeleteLive = false
                                                            },
                                                            colors = ButtonDefaults.buttonColors(containerColor = LiveCrimson)
                                                        ) { Text("Delete") }
                                                    },
                                                    dismissButton = {
                                                        TextButton(onClick = { confirmDeleteLive = false }) { Text("Cancel") }
                                                    }
                                                )
                                            }
                                            IconButton(onClick = { confirmDeleteLive = true }) {
                                                Icon(Icons.Default.Delete, contentDescription = "Delete Live Schedule", tint = LiveCrimson)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    is CalendarTask.Upload -> {
                        val u = task.upload
                        Card(
                            colors = CardDefaults.cardColors(containerColor = ElevatedCard),
                            border = BorderStroke(1.dp, ElectricBlue.copy(alpha = 0.45f)),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("UPLOAD: ${u.title}", color = Color.White, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                    StatusBadge(u.status, ElectricBlue)
                                }
                                Text("$timeStr (${u.timezone}) • ${u.privacyStatus}", color = ElectricCyan, style = MaterialTheme.typography.bodyMedium)
                                if (u.status == "SCHEDULED") {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(onClick = { onRescheduleUpload(u, u.scheduledPublishEpochMs + 3600_000L) }) { Text("+1h") }
                                        OutlinedButton(onClick = { onRescheduleUpload(u, u.scheduledPublishEpochMs + 86_400_000L) }) { Text("+1d") }
                                        var confirmDeleteUpload by remember(u.id) { mutableStateOf(false) }
                                        if (confirmDeleteUpload) {
                                            AlertDialog(
                                                onDismissRequest = { confirmDeleteUpload = false },
                                                title = { Text("Delete Upload Schedule?") },
                                                text = { Text("Remove '${u.title}' from calendar?") },
                                                confirmButton = {
                                                    Button(
                                                        onClick = {
                                                            onDeleteUpload(u.id)
                                                            confirmDeleteUpload = false
                                                        },
                                                        colors = ButtonDefaults.buttonColors(containerColor = LiveCrimson)
                                                    ) { Text("Delete") }
                                                },
                                                dismissButton = {
                                                    TextButton(onClick = { confirmDeleteUpload = false }) { Text("Cancel") }
                                                }
                                            )
                                        }
                                        IconButton(onClick = { confirmDeleteUpload = true }) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete Upload Schedule", tint = LiveCrimson)
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
}

@Composable
fun ScheduledUploadsTab(
    uploadSchedules: List<UploadSchedule>,
    onScheduleNewUpload: () -> Unit,
    onTriggerNow: (UploadSchedule) -> Unit,
    onReschedule: (UploadSchedule, Long) -> Unit,
    onCancel: (UploadSchedule) -> Unit,
    onDelete: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Button(
                onClick = onScheduleNewUpload,
                colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().testTag("new_scheduled_upload_btn")
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Schedule Automatic Video Upload")
            }
        }

        if (uploadSchedules.isEmpty()) {
            item {
                Text(
                    "No scheduled video uploads yet. Pick a video from gallery and choose 'Schedule Auto-Upload'.",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            items(uploadSchedules, key = { it.id }) { item ->
                val dateText = SimpleDateFormat("EEE, dd MMM • hh:mm a", Locale.getDefault())
                    .format(Date(item.scheduledPublishEpochMs))
                val badgeColor = when (item.status) {
                    "PUBLISHED" -> SuccessEmerald
                    "UPLOADING" -> ElectricCyan
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
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(item.title, color = Color.White, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            StatusBadge(item.status, badgeColor)
                        }
                        Text("$dateText (${item.timezone}) • ${item.privacyStatus}", color = ElectricCyan, style = MaterialTheme.typography.bodyMedium)
                        item.youtubeVideoId?.takeIf { it.isNotBlank() }?.let { ytId ->
                            Text("Published YouTube ID: $ytId", color = SuccessEmerald, style = MaterialTheme.typography.labelMedium)
                        }
                        item.lastError?.takeIf { it.isNotBlank() }?.let { err ->
                            Text(err, color = WarningAmber, style = MaterialTheme.typography.bodyMedium)
                        }
                        if (item.status == "SCHEDULED" || item.status == "FAILED") {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { onTriggerNow(item) },
                                    colors = ButtonDefaults.buttonColors(containerColor = SuccessEmerald, contentColor = Color.Black)
                                ) {
                                    Text("Publish Now", fontWeight = FontWeight.Bold)
                                }
                                OutlinedButton(onClick = { onReschedule(item, item.scheduledPublishEpochMs + 3600_000L) }) {
                                    Text("+1h")
                                }
                                TextButton(onClick = { onCancel(item) }) {
                                    Text("Cancel", color = LiveCrimson)
                                }
                            }
                        } else {
                            var confirmRemove by remember(item.id) { mutableStateOf(false) }
                            if (confirmRemove) {
                                AlertDialog(
                                    onDismissRequest = { confirmRemove = false },
                                    title = { Text("Remove Upload Record?") },
                                    text = { Text("Remove '${item.title}' from scheduled uploads?") },
                                    confirmButton = {
                                        Button(
                                            onClick = {
                                                onDelete(item.id)
                                                confirmRemove = false
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = LiveCrimson)
                                        ) { Text("Remove") }
                                    },
                                    dismissButton = {
                                        TextButton(onClick = { confirmRemove = false }) { Text("Cancel") }
                                    }
                                )
                            }
                            TextButton(onClick = { confirmRemove = true }) {
                                Text("Remove", color = TextSecondary)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun startOfDayMs(epochMs: Long): Long {
    val cal = Calendar.getInstance().apply {
        timeInMillis = epochMs
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    return cal.timeInMillis
}
