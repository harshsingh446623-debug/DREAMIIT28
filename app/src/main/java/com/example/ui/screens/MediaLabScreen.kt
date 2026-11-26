package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.CloudVideo
import com.example.media.ExoPlayerVideoPreview
import com.example.media.MediaInspector
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
import com.example.ui.viewmodel.AiRepurposeUiState
import com.example.ui.viewmodel.AiStudioViewModel
import com.example.ui.viewmodel.MediaLabState
import java.util.Locale
import kotlin.math.roundToLong

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaLabScreen(
    videos: List<CloudVideo>,
    initialVideoId: String?,
    mediaLabState: MediaLabState,
    aiViewModel: AiStudioViewModel,
    onAnalyzeAudio: (Uri) -> Unit,
    onTrimVideo: (sourceUri: Uri, sourceVideo: CloudVideo?, startMs: Long, endMs: Long, newTitle: String) -> Unit,
    onNormalizeAudio: (sourceUri: Uri, sourceVideo: CloudVideo?, gainLinear: Float, normalized: Boolean, newTitle: String) -> Unit,
    onCreateVerticalShort: (sourceUri: Uri, sourceVideo: CloudVideo?, startMs: Long, endMs: Long, shortsTitle: String, shortsDesc: String) -> Unit,
    onResetMediaLab: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    val repurposeState by aiViewModel.repurposeState.collectAsStateWithLifecycle()

    val editableCloudVideos = remember(videos) {
        videos.filter { !it.localUri.isNullOrBlank() }
    }
    var selectedCloudVideo by remember(editableCloudVideos, initialVideoId) {
        mutableStateOf(
            editableCloudVideos.firstOrNull { it.id == initialVideoId }
                ?: editableCloudVideos.firstOrNull()
        )
    }
    var pickedGalleryUri by remember { mutableStateOf<Uri?>(null) }
    var pickedDurationSec by remember { mutableStateOf(0L) }
    var pickedLabel by remember { mutableStateOf("") }

    val activeUri: Uri? = remember(pickedGalleryUri, selectedCloudVideo) {
        pickedGalleryUri ?: selectedCloudVideo?.localUri?.let { Uri.parse(it) }
    }
    val activeTitle = pickedLabel.ifBlank { selectedCloudVideo?.title ?: "Selected Video" }
    val activeDurationSec = remember(pickedDurationSec, selectedCloudVideo, mediaLabState.audioReport) {
        val raw = when {
            pickedGalleryUri != null && pickedDurationSec > 0L -> pickedDurationSec
            (selectedCloudVideo?.durationSec ?: 0L) > 0L -> selectedCloudVideo!!.durationSec
            (mediaLabState.audioReport?.durationMs ?: 0L) > 0L -> mediaLabState.audioReport!!.durationMs / 1000L
            else -> 60L
        }
        raw.coerceAtLeast(5L)
    }

    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            pickedGalleryUri = uri
            pickedLabel = MediaInspector.getFileName(context, uri)
            onResetMediaLab()
        }
    }

    LaunchedEffect(pickedGalleryUri) {
        val u = pickedGalleryUri
        if (u != null) {
            val qr = MediaInspector.inspectVideo(context, u)
            pickedDurationSec = qr.durationSec.coerceAtLeast(5L)
        }
    }

    Scaffold(
        containerColor = VoidBlack,
        topBar = {
            TopAppBar(
                title = { Text("PRO Media Lab", color = Color.White, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("media_lab_back_btn")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = ElectricCyan)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ObsidianSurface)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = ObsidianSurface,
                contentColor = ElectricCyan
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Trimmer") },
                    modifier = Modifier.testTag("media_lab_tab_trim")
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Audio") },
                    modifier = Modifier.testTag("media_lab_tab_audio")
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("9:16 Shorts") },
                    modifier = Modifier.testTag("media_lab_tab_shorts")
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Source Selector Card
                item {
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
                                Text("Source Video (Original Preserved)", style = MaterialTheme.typography.titleMedium, color = Color.White)
                                OutlinedButton(
                                    onClick = {
                                        videoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                                    },
                                    modifier = Modifier.testTag("media_lab_pick_video_btn")
                                ) {
                                    Icon(Icons.Default.VideoFile, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Gallery")
                                }
                            }
                            if (editableCloudVideos.isNotEmpty()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    editableCloudVideos.take(10).forEach { vid ->
                                        FilterChip(
                                            selected = pickedGalleryUri == null && selectedCloudVideo?.id == vid.id,
                                            onClick = {
                                                pickedGalleryUri = null
                                                pickedLabel = ""
                                                selectedCloudVideo = vid
                                                onResetMediaLab()
                                            },
                                            label = { Text(vid.title.take(24), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                                        )
                                    }
                                }
                            }
                            Text(
                                text = if (activeUri != null) "Loaded: $activeTitle (${activeDurationSec}s)" else "Select a Cloud Library video or pick one from mobile gallery.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (activeUri != null) ElectricCyan else TextSecondary
                            )
                        }
                    }
                }

                // Export / Progress Card
                if (mediaLabState.active || mediaLabState.error != null || mediaLabState.lastExportedUri != null) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (mediaLabState.error != null) LiveCrimson.copy(alpha = 0.14f) else ElevatedCard
                            ),
                            border = BorderStroke(
                                1.dp,
                                when {
                                    mediaLabState.error != null -> LiveCrimson
                                    mediaLabState.lastExportedUri != null -> SuccessEmerald
                                    else -> ElectricBlue
                                }
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (mediaLabState.active) {
                                    Text(mediaLabState.statusText, color = ElectricCyan, style = MaterialTheme.typography.bodyMedium)
                                    LinearProgressIndicator(
                                        progress = { mediaLabState.progress.coerceIn(0f, 1f) },
                                        color = ElectricCyan,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                                mediaLabState.error?.let { err ->
                                    Text(err, color = LiveCrimson, style = MaterialTheme.typography.bodyMedium)
                                }
                                mediaLabState.lastExportedUri?.let { exported ->
                                    if (!mediaLabState.active) {
                                        Text(mediaLabState.statusText, color = SuccessEmerald, style = MaterialTheme.typography.bodyMedium)
                                        OutlinedButton(
                                            onClick = {
                                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                                    setDataAndType(exported, "video/mp4")
                                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                }
                                                runCatching { context.startActivity(intent) }
                                            }
                                        ) {
                                            Icon(Icons.Default.OpenInNew, contentDescription = null, tint = ElectricCyan)
                                            Spacer(Modifier.width(6.dp))
                                            Text("Preview Exported MP4 (${mediaLabState.lastExportedLabel ?: "video.mp4"})")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (activeUri == null) {
                    item {
                        Text(
                            "Pick a local/library video above to start trimming, audio mastering, or creating 9:16 vertical Shorts.",
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                } else {
                    when (selectedTab) {
                        0 -> item {
                            VideoTrimmerSection(
                                videoUri = activeUri,
                                initialTitle = "$activeTitle (Trimmed)",
                                durationSec = activeDurationSec,
                                busy = mediaLabState.active,
                                onSaveTrim = { startMs, endMs, newTitle ->
                                    onTrimVideo(activeUri, selectedCloudVideo, startMs, endMs, newTitle)
                                }
                            )
                        }
                        1 -> item {
                            AudioManagerSection(
                                videoUri = activeUri,
                                initialTitle = "$activeTitle (Audio Mastered)",
                                mediaLabState = mediaLabState,
                                onAnalyzeAudio = { onAnalyzeAudio(activeUri) },
                                onSaveNormalized = { gain, normalized, newTitle ->
                                    onNormalizeAudio(activeUri, selectedCloudVideo, gain, normalized, newTitle)
                                }
                            )
                        }
                        2 -> item {
                            VerticalRepurposeSection(
                                videoUri = activeUri,
                                videoTitle = activeTitle,
                                initialNotes = selectedCloudVideo?.transcriptText ?: selectedCloudVideo?.description.orEmpty(),
                                durationSec = activeDurationSec,
                                busy = mediaLabState.active,
                                repurposeState = repurposeState,
                                onSuggestClips = { notes ->
                                    aiViewModel.suggestRepurposeClips(activeTitle, notes, activeDurationSec)
                                },
                                onCreateVerticalClip = { startMs, endMs, shortsTitle, shortsDesc ->
                                    onCreateVerticalShort(activeUri, selectedCloudVideo, startMs, endMs, shortsTitle, shortsDesc)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoTrimmerSection(
    videoUri: Uri,
    initialTitle: String,
    durationSec: Long,
    busy: Boolean,
    onSaveTrim: (startMs: Long, endMs: Long, newTitle: String) -> Unit
) {
    val maxSec = durationSec.coerceAtLeast(5L).toFloat()
    var trimRange by remember(videoUri, maxSec) { mutableStateOf(0f..maxSec) }
    var editedTitle by remember(initialTitle) { mutableStateOf(initialTitle) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(
            colors = CardDefaults.cardColors(containerColor = ElevatedCard),
            border = BorderStroke(1.dp, ElectricBlue.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
            ) {
                ExoPlayerVideoPreview(videoUri = videoUri, modifier = Modifier.fillMaxSize())
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = ElevatedCard),
            border = BorderStroke(1.dp, BorderSubtle),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Trim Start & End Timestamps", style = MaterialTheme.typography.titleMedium, color = Color.White)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Start: ${formatDuration(trimRange.start.roundToLong())}", color = ElectricCyan)
                    Text("Clip: ${formatDuration((trimRange.endInclusive - trimRange.start).roundToLong().coerceAtLeast(1L))}", color = SuccessEmerald)
                    Text("End: ${formatDuration(trimRange.endInclusive.roundToLong())}", color = ElectricCyan)
                }
                RangeSlider(
                    value = trimRange,
                    onValueChange = { range ->
                        if (range.endInclusive - range.start >= 2f) {
                            trimRange = range
                        }
                    },
                    valueRange = 0f..maxSec,
                    modifier = Modifier.testTag("trim_range_slider")
                )
                OutlinedTextField(
                    value = editedTitle,
                    onValueChange = { editedTitle = it },
                    label = { Text("New Edited Video Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = {
                        onSaveTrim(
                            (trimRange.start * 1000f).roundToLong(),
                            (trimRange.endInclusive * 1000f).roundToLong(),
                            editedTitle
                        )
                    },
                    enabled = !busy && trimRange.endInclusive - trimRange.start >= 1f,
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("save_trimmed_video_btn")
                ) {
                    Icon(Icons.Default.ContentCut, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Save Trimmed Video Separately")
                }
            }
        }
    }
}

@Composable
private fun AudioManagerSection(
    videoUri: Uri,
    initialTitle: String,
    mediaLabState: MediaLabState,
    onAnalyzeAudio: () -> Unit,
    onSaveNormalized: (gainLinear: Float, normalized: Boolean, newTitle: String) -> Unit
) {
    var previewGain by remember(videoUri) { mutableFloatStateOf(1.0f) }
    var normalizedApplied by remember(videoUri) { mutableStateOf(false) }
    var editedTitle by remember(initialTitle) { mutableStateOf(initialTitle) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(
            colors = CardDefaults.cardColors(containerColor = ElevatedCard),
            border = BorderStroke(1.dp, ElectricBlue.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
            ) {
                ExoPlayerVideoPreview(
                    videoUri = videoUri,
                    volume = previewGain.coerceIn(0f, 1.5f),
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = ElevatedCard),
            border = BorderStroke(1.dp, BorderSubtle),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Audio Inspection & Normalization", style = MaterialTheme.typography.titleMedium, color = Color.White)
                    OutlinedButton(
                        onClick = onAnalyzeAudio,
                        enabled = !mediaLabState.active,
                        modifier = Modifier.testTag("analyze_audio_btn")
                    ) {
                        Icon(Icons.Default.GraphicEq, contentDescription = null, tint = ElectricCyan)
                        Spacer(Modifier.width(6.dp))
                        Text("Analyze")
                    }
                }

                mediaLabState.audioReport?.let { report ->
                    Surface(
                        color = ObsidianSurface,
                        border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.35f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(report.summary, color = ElectricCyan, style = MaterialTheme.typography.labelLarge)
                            report.issues.forEach { issue ->
                                Text("• $issue", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }

                Text(
                    text = String.format(Locale.US, "Volume / Gain Adjustment: %.2fx (Live Preview Active)", previewGain),
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium
                )
                Slider(
                    value = previewGain,
                    onValueChange = {
                        previewGain = it
                        normalizedApplied = false
                    },
                    valueRange = 0.25f..3.0f,
                    modifier = Modifier.testTag("audio_gain_slider")
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = {
                            val target = mediaLabState.audioReport?.recommendedGainLinear ?: 1.25f
                            previewGain = target
                            normalizedApplied = true
                        },
                        modifier = Modifier.weight(1f).testTag("normalize_audio_btn")
                    ) {
                        Text("Auto-Normalize (${String.format(Locale.US, "%.2fx", mediaLabState.audioReport?.recommendedGainLinear ?: 1.25f)})")
                    }
                }

                OutlinedTextField(
                    value = editedTitle,
                    onValueChange = { editedTitle = it },
                    label = { Text("Saved Audio-Mastered Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    onClick = { onSaveNormalized(previewGain, normalizedApplied, editedTitle) },
                    enabled = !mediaLabState.active,
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("save_audio_master_btn")
                ) {
                    Icon(Icons.Default.Save, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Save Mastered Video Separately")
                }
            }
        }
    }
}

@Composable
private fun VerticalRepurposeSection(
    videoUri: Uri,
    videoTitle: String,
    initialNotes: String,
    durationSec: Long,
    busy: Boolean,
    repurposeState: AiRepurposeUiState,
    onSuggestClips: (notes: String) -> Unit,
    onCreateVerticalClip: (startMs: Long, endMs: Long, shortsTitle: String, shortsDesc: String) -> Unit
) {
    val maxSec = durationSec.coerceAtLeast(10L).toFloat()
    var notes by remember(initialNotes) { mutableStateOf(initialNotes) }
    var clipRange by remember(videoUri, maxSec) { mutableStateOf(0f..minOf(45f, maxSec)) }
    var shortsTitle by remember(videoTitle) { mutableStateOf("$videoTitle #Shorts") }
    var shortsDesc by remember(videoTitle) { mutableStateOf("Highlight clip from $videoTitle #Shorts") }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                    Text("AI Shorts Clip Finder", style = MaterialTheme.typography.titleMedium, color = Color.White)
                    StatusBadge("9:16 VERTICAL", ElectricCyan)
                }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Transcript / Video Highlights Notes") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedButton(
                    onClick = { onSuggestClips(notes) },
                    modifier = Modifier.fillMaxWidth().testTag("ai_suggest_shorts_btn")
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = ElectricCyan)
                    Spacer(Modifier.width(6.dp))
                    Text("Identify Engaging Shorts Moments")
                }

                when (repurposeState) {
                    is AiRepurposeUiState.Loading -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = ElectricCyan)
                            Spacer(Modifier.width(8.dp))
                            Text("Analyzing engaging clip boundaries...", color = TextSecondary)
                        }
                    }
                    is AiRepurposeUiState.Error -> {
                        Text(repurposeState.message, color = LiveCrimson)
                    }
                    is AiRepurposeUiState.Success -> {
                        repurposeState.suggestions.forEach { clip ->
                            Card(
                                onClick = {
                                    val s = clip.startSec.toFloat().coerceIn(0f, maxSec - 2f)
                                    val e = clip.endSec.toFloat().coerceIn(s + 2f, maxSec)
                                    clipRange = s..e
                                    shortsTitle = clip.suggestedShortsTitle
                                    shortsDesc = clip.suggestedShortsDescription
                                },
                                colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
                                border = BorderStroke(1.dp, ElectricBlue.copy(alpha = 0.5f)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        "${formatDuration(clip.startSec)} – ${formatDuration(clip.endSec)} • ${clip.suggestedShortsTitle}",
                                        color = ElectricCyan,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(clip.reason, color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                    AiRepurposeUiState.Idle -> Unit
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = ElevatedCard),
            border = BorderStroke(1.dp, BorderSubtle),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Vertical 9:16 Clip Preview & Export", style = MaterialTheme.typography.titleMedium, color = Color.White)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                ) {
                    ExoPlayerVideoPreview(videoUri = videoUri, modifier = Modifier.fillMaxSize())
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("In: ${formatDuration(clipRange.start.roundToLong())}", color = ElectricCyan)
                    Text("Len: ${formatDuration((clipRange.endInclusive - clipRange.start).roundToLong())}", color = WarningAmber)
                    Text("Out: ${formatDuration(clipRange.endInclusive.roundToLong())}", color = ElectricCyan)
                }
                RangeSlider(
                    value = clipRange,
                    onValueChange = { r -> if (r.endInclusive - r.start >= 2f) clipRange = r },
                    valueRange = 0f..maxSec
                )
                OutlinedTextField(
                    value = shortsTitle,
                    onValueChange = { shortsTitle = it },
                    label = { Text("Shorts Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = shortsDesc,
                    onValueChange = { shortsDesc = it },
                    label = { Text("Shorts Description") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = {
                        onCreateVerticalClip(
                            (clipRange.start * 1000f).roundToLong(),
                            (clipRange.endInclusive * 1000f).roundToLong(),
                            shortsTitle,
                            shortsDesc
                        )
                    },
                    enabled = !busy,
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("export_vertical_short_btn")
                ) {
                    Icon(Icons.Default.MovieCreation, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Create 9:16 Vertical Short Separately")
                }
            }
        }
    }
}
