package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.ScheduleSend
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.CloudVideo
import com.example.data.model.PrivacyStatus
import com.example.data.model.ScheduleRecurrence
import com.example.data.model.StreamingProfile
import com.example.data.model.VideoQualityReport
import com.example.media.MediaInspector
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ElevatedCard
import com.example.ui.theme.LiveCrimson
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.SuccessEmerald
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarningAmber
import com.example.ui.viewmodel.UploadProgressState
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadVideoBottomSheet(
    uploadState: UploadProgressState,
    defaultPrivacy: PrivacyStatus,
    defaultTimezone: String = "Asia/Kolkata",
    onInspectVideo: suspend (Uri) -> VideoQualityReport,
    onSubmitUpload: (
        videoUri: Uri,
        title: String,
        description: String,
        categoryId: String,
        privacyStatus: PrivacyStatus,
        tags: List<String>,
        qualityReport: VideoQualityReport,
        thumbnailBytes: ByteArray?,
        publishToYouTubeNow: Boolean
    ) -> Unit,
    onScheduleUpload: (
        videoUri: Uri,
        title: String,
        description: String,
        categoryId: String,
        privacyStatus: PrivacyStatus,
        tags: List<String>,
        qualityReport: VideoQualityReport,
        scheduledPublishEpochMs: Long,
        timezoneId: String
    ) -> Unit = { _, _, _, _, _, _, _, _, _ -> },
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var fileName by remember { mutableStateOf("") }
    var qualityReport by remember { mutableStateOf<VideoQualityReport?>(null) }
    var thumbnailBytes by remember { mutableStateOf<ByteArray?>(null) }
    var thumbnailLabel by remember { mutableStateOf("") }

    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var tagsRaw by remember { mutableStateOf("dreamiit28, live, education") }
    var categoryId by remember { mutableStateOf("27") }
    var privacy by remember { mutableStateOf(defaultPrivacy) }
    var publishToYouTubeNow by remember { mutableStateOf(true) }

    // V3.0 Feature #2: Automatic Video Upload Scheduler toggle + delay selection
    var scheduleForLater by remember { mutableStateOf(false) }
    var scheduleDelayMinutes by remember { mutableIntStateOf(60) }
    val timezoneId = remember(defaultTimezone) { defaultTimezone.ifBlank { TimeZone.getDefault().id } }
    val scheduledEpochMs = remember(scheduleDelayMinutes) {
        System.currentTimeMillis() + (scheduleDelayMinutes * 60_000L)
    }
    val scheduledFormatted = remember(scheduledEpochMs, timezoneId) {
        SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).apply {
            timeZone = TimeZone.getTimeZone(timezoneId)
        }.format(Date(scheduledEpochMs))
    }

    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            selectedUri = uri
            fileName = MediaInspector.getFileName(context, uri)
            if (title.isBlank()) {
                title = fileName.substringBeforeLast('.').take(90)
            }
            scope.launch {
                qualityReport = onInspectVideo(uri)
            }
        }
    }

    val thumbPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            thumbnailLabel = MediaInspector.getFileName(context, uri)
            thumbnailBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = ObsidianSurface,
        contentColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Upload Video • Resumable & Scheduled",
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !scheduleForLater,
                    onClick = { scheduleForLater = false },
                    label = { Text("Upload Now") },
                    modifier = Modifier.testTag("mode_upload_now_chip")
                )
                FilterChip(
                    selected = scheduleForLater,
                    onClick = { scheduleForLater = true },
                    label = { Text("Schedule Auto-Upload") },
                    modifier = Modifier.testTag("mode_schedule_upload_chip")
                )
            }

            OutlinedButton(
                onClick = {
                    videoPicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                    )
                },
                border = BorderStroke(1.dp, ElectricBlue),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("pick_video_gallery_button")
            ) {
                Icon(Icons.Default.VideoFile, contentDescription = null, tint = ElectricCyan)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (selectedUri == null) "Select Video from Mobile Gallery" else fileName,
                    color = Color.White
                )
            }

            qualityReport?.let { qr ->
                Surface(
                    color = if (qr.isGenuine1080p) SuccessEmerald.copy(alpha = 0.12f) else WarningAmber.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, if (qr.isGenuine1080p) SuccessEmerald else WarningAmber),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (qr.isGenuine1080p) Icons.Default.HighQuality else Icons.Default.Warning,
                                contentDescription = null,
                                tint = if (qr.isGenuine1080p) SuccessEmerald else WarningAmber
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Detected: ${qr.width}×${qr.height} @ ${qr.fps}fps • ${qr.durationSec}s • ${qr.fileSizeBytes / (1024 * 1024)} MB",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White
                            )
                        }
                        if (qr.warningMessage != null) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = qr.warningMessage,
                                style = MaterialTheme.typography.bodyMedium,
                                color = WarningAmber
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Video Title") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("upload_title_input")
            )

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description") },
                minLines = 3,
                maxLines = 5,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("upload_desc_input")
            )

            OutlinedTextField(
                value = tagsRaw,
                onValueChange = { tagsRaw = it },
                label = { Text("Tags (comma separated)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("27" to "Education", "28" to "Tech", "22" to "People/Blog", "24" to "Entertainment").forEach { (id, label) ->
                    FilterChip(
                        selected = categoryId == id,
                        onClick = { categoryId = id },
                        label = { Text(label) }
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrivacyStatus.entries.forEach { p ->
                    FilterChip(
                        selected = privacy == p,
                        onClick = { privacy = p },
                        label = { Text(p.label) }
                    )
                }
            }

            if (scheduleForLater) {
                Surface(
                    color = ElevatedCard,
                    border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Publish Time: $scheduledFormatted ($timezoneId)", color = ElectricCyan, style = MaterialTheme.typography.labelLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(15 to "+15m", 60 to "+1h", 360 to "+6h", 1440 to "+1d", 2880 to "+2d").forEach { (mins, label) ->
                                FilterChip(
                                    selected = scheduleDelayMinutes == mins,
                                    onClick = { scheduleDelayMinutes = mins },
                                    label = { Text(label) }
                                )
                            }
                        }
                        Text(
                            "Saved to Cloud Scheduler & Conflict Checker. Automatically publishes when due.",
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            } else {
                OutlinedButton(
                    onClick = {
                        thumbPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    border = BorderStroke(1.dp, BorderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Image, contentDescription = null, tint = ElectricCyan)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (thumbnailLabel.isBlank()) "Attach Custom Thumbnail (Optional)" else "Thumbnail: $thumbnailLabel",
                        color = Color.White
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = publishToYouTubeNow,
                        onCheckedChange = { publishToYouTubeNow = it }
                    )
                    Text(
                        text = "Publish immediately to connected YouTube channel (resumable upload)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White
                    )
                }
            }

            if (uploadState.active) {
                LinearProgressIndicator(
                    progress = { uploadState.progress.coerceIn(0f, 1f) },
                    color = ElectricCyan,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = uploadState.statusText,
                    style = MaterialTheme.typography.labelMedium,
                    color = ElectricCyan
                )
            }
            uploadState.error?.let { err ->
                Text(text = err, color = LiveCrimson, style = MaterialTheme.typography.bodyMedium)
            }

            Button(
                onClick = {
                    val uri = selectedUri ?: return@Button
                    val qr = qualityReport ?: VideoQualityReport(0, 0, 30, 0, 0, false, null)
                    val tags = tagsRaw.split(",").map { it.trim() }.filter { it.isNotBlank() }
                    if (scheduleForLater) {
                        onScheduleUpload(
                            uri,
                            title.trim().ifBlank { "DREAMIIT28 Scheduled Video" },
                            description.trim(),
                            categoryId,
                            privacy,
                            tags,
                            qr,
                            scheduledEpochMs,
                            timezoneId
                        )
                        onDismiss()
                    } else {
                        onSubmitUpload(
                            uri,
                            title.trim().ifBlank { "DREAMIIT28 Video" },
                            description.trim(),
                            categoryId,
                            privacy,
                            tags,
                            qr,
                            thumbnailBytes,
                            publishToYouTubeNow
                        )
                    }
                },
                enabled = selectedUri != null && title.isNotBlank() && !uploadState.active,
                colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("submit_upload_video_button")
            ) {
                Icon(
                    imageVector = if (scheduleForLater) Icons.Default.ScheduleSend else Icons.Default.CloudUpload,
                    contentDescription = null
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = when {
                        scheduleForLater -> "Schedule Automatic Upload"
                        publishToYouTubeNow -> "Upload & Save to Cloud"
                        else -> "Save to Cloud Library"
                    },
                    fontWeight = FontWeight.Bold
                )
            }

            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                Text("Close", color = TextSecondary)
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveSchedulerBottomSheet(
    videos: List<CloudVideo>,
    defaultPrivacy: PrivacyStatus,
    defaultProfile: StreamingProfile,
    defaultTimezone: String,
    onScheduleConfirmed: (
        videoId: String,
        videoTitle: String,
        liveTitle: String,
        liveDescription: String,
        privacyStatus: PrivacyStatus,
        scheduledStartEpochMs: Long,
        timezoneId: String,
        recurrence: ScheduleRecurrence,
        profile: StreamingProfile,
        autoEndOnFinish: Boolean
    ) -> Unit,
    onOpenUploadFirst: () -> Unit,
    onDismiss: () -> Unit
) {
    var selectedVideo by remember(videos) { mutableStateOf(videos.firstOrNull()) }
    var liveTitle by remember { mutableStateOf(selectedVideo?.title ?: "") }
    var liveDescription by remember { mutableStateOf("Automated 1080p Full HD cloud live stream powered by DREAMIIT28.") }
    var privacy by remember { mutableStateOf(defaultPrivacy) }
    var profile by remember { mutableStateOf(defaultProfile) }
    var recurrence by remember { mutableStateOf(ScheduleRecurrence.ONCE) }
    var autoEnd by remember { mutableStateOf(true) }
    var delayMinutes by remember { mutableIntStateOf(15) }
    val timezoneId by remember { mutableStateOf(defaultTimezone.ifBlank { TimeZone.getDefault().id }) }

    val startEpochMs = remember(delayMinutes) {
        System.currentTimeMillis() + (delayMinutes * 60_000L)
    }
    val formattedTime = remember(startEpochMs, timezoneId) {
        SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).apply {
            timeZone = TimeZone.getTimeZone(timezoneId)
        }.format(Date(startEpochMs))
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = ObsidianSurface,
        contentColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Schedule Autonomous 1080p Live",
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White
            )

            Surface(
                color = ElevatedCard,
                border = BorderStroke(1.dp, ElectricBlue.copy(alpha = 0.45f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Cloud RTMPS Worker runs independently — your phone can be closed or powered off during the broadcast.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ElectricCyan,
                    modifier = Modifier.padding(12.dp)
                )
            }

            if (videos.isEmpty()) {
                Surface(
                    color = WarningAmber.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, WarningAmber),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "No prerecorded videos in Cloud Library yet.",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White
                        )
                        Spacer(Modifier.height(6.dp))
                        Button(
                            onClick = {
                                onDismiss()
                                onOpenUploadFirst()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)
                        ) {
                            Text("Select & Upload Video First")
                        }
                    }
                }
            } else {
                Text("Select Prerecorded Cloud Video", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    videos.take(5).forEach { vid ->
                        FilterChip(
                            selected = selectedVideo?.id == vid.id,
                            onClick = {
                                selectedVideo = vid
                                if (liveTitle.isBlank()) liveTitle = vid.title
                            },
                            label = {
                                Text("${vid.title} (${vid.sourceWidth}×${vid.sourceHeight})")
                            }
                        )
                    }
                }
                if (selectedVideo != null && !selectedVideo!!.qualityVerified1080p) {
                    Text(
                        text = "Quality Notice: Source video is ${selectedVideo!!.sourceWidth}×${selectedVideo!!.sourceHeight}. Cloud encoder will output a 1080p container, without claiming genuine 1080p source detail.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = WarningAmber
                    )
                }
            }

            OutlinedTextField(
                value = liveTitle,
                onValueChange = { liveTitle = it },
                label = { Text("Live Broadcast Title") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("live_title_input")
            )

            OutlinedTextField(
                value = liveDescription,
                onValueChange = { liveDescription = it },
                label = { Text("Live Description") },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth()
            )

            Text("Future Start Time ($formattedTime)", style = MaterialTheme.typography.labelLarge, color = ElectricCyan)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(5 to "+5m", 15 to "+15m", 60 to "+1h", 360 to "+6h", 1440 to "+24h").forEach { (mins, label) ->
                    FilterChip(
                        selected = delayMinutes == mins,
                        onClick = { delayMinutes = mins },
                        label = { Text(label) }
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ScheduleRecurrence.entries.forEach { r ->
                    FilterChip(
                        selected = recurrence == r,
                        onClick = { recurrence = r },
                        label = { Text(r.label) }
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrivacyStatus.entries.forEach { p ->
                    FilterChip(
                        selected = privacy == p,
                        onClick = { privacy = p },
                        label = { Text(p.label) }
                    )
                }
            }

            Text("Encoding Profile (H.264 / AAC / RTMPS)", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                StreamingProfile.entries.forEach { prof ->
                    FilterChip(
                        selected = profile == prof,
                        onClick = { profile = prof },
                        label = { Text(prof.label) }
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Auto-end YouTube broadcast when video finishes", style = MaterialTheme.typography.bodyMedium, color = Color.White, modifier = Modifier.weight(1f))
                Switch(checked = autoEnd, onCheckedChange = { autoEnd = it })
            }

            Button(
                onClick = {
                    val vid = selectedVideo ?: return@Button
                    onScheduleConfirmed(
                        vid.id,
                        vid.title,
                        liveTitle.trim().ifBlank { vid.title },
                        liveDescription.trim(),
                        privacy,
                        startEpochMs,
                        timezoneId,
                        recurrence,
                        profile,
                        autoEnd
                    )
                    onDismiss()
                },
                enabled = selectedVideo != null && liveTitle.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = LiveCrimson),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("confirm_live_schedule_button")
            ) {
                Text("Confirm & Schedule 1080p Live", fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}
