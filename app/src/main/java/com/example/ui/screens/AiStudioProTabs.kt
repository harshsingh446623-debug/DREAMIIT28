package com.example.ui.screens

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.example.ui.viewmodel.AiChaptersUiState
import com.example.ui.viewmodel.AiSeoUiState
import com.example.ui.viewmodel.AiStudioViewModel
import com.example.ui.viewmodel.AiTranscriptUiState

@Composable
private fun CloudVideoSelectorRow(
    videos: List<CloudVideo>,
    selectedVideo: CloudVideo?,
    onSelect: (CloudVideo) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Select Cloud Library Video", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
        if (videos.isEmpty()) {
            Text("No videos in Cloud Library yet — you can still type notes/title below.", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                videos.take(12).forEach { vid ->
                    FilterChip(
                        selected = selectedVideo?.id == vid.id,
                        onClick = { onSelect(vid) },
                        label = { Text(vid.title.take(24), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    )
                }
            }
        }
    }
}

/**
 * Feature #5: Automatic Subtitles (Hindi & English .srt generation, manual edit, export, YouTube captions upload).
 */
@Composable
fun AiSubtitlesTab(
    aiViewModel: AiStudioViewModel,
    videos: List<CloudVideo>,
    onExportSrtFile: (fileName: String, content: String) -> Unit,
    onSaveSubtitles: (video: CloudVideo, languageCode: String, srtContent: String, uploadToYouTube: Boolean) -> Unit
) {
    val context = LocalContext.current
    val transcriptState by aiViewModel.transcriptState.collectAsStateWithLifecycle()

    var selectedVideo by remember(videos) { mutableStateOf(videos.firstOrNull()) }
    var languageCode by remember { mutableStateOf("en") }
    var notes by remember(selectedVideo) { mutableStateOf(selectedVideo?.description.orEmpty()) }
    var editableSrt by remember(selectedVideo, languageCode) {
        mutableStateOf(
            if (languageCode == "hi") selectedVideo?.subtitlesHiSrt.orEmpty()
            else selectedVideo?.subtitlesEnSrt.orEmpty()
        )
    }

    LaunchedEffect(transcriptState) {
        val st = transcriptState as? AiTranscriptUiState.Success
        if (st != null) {
            editableSrt = st.srtContent
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            CloudVideoSelectorRow(videos, selectedVideo) { selectedVideo = it }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = languageCode == "en",
                    onClick = { languageCode = "en" },
                    label = { Text("English (.srt)") }
                )
                FilterChip(
                    selected = languageCode == "hi",
                    onClick = { languageCode = "hi" },
                    label = { Text("हिन्दी Subtitles (.srt)") }
                )
            }
        }
        item {
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Audio Context / Script Notes (Optional)") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            Button(
                onClick = {
                    val uri = selectedVideo?.localUri?.let { Uri.parse(it) }
                    aiViewModel.generateSubtitlesOrTranscript(
                        context = context,
                        videoUri = uri,
                        videoTitle = selectedVideo?.title ?: "DREAMIIT28 Video",
                        videoNotes = notes,
                        durationSec = selectedVideo?.durationSec ?: 60L,
                        languageCode = languageCode
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("generate_subtitles_btn")
            ) {
                Icon(Icons.Default.Subtitles, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Generate ${if (languageCode == "hi") "Hindi" else "English"} Subtitles")
            }
        }

        if (transcriptState is AiTranscriptUiState.Loading) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = ElectricCyan)
                    Spacer(Modifier.width(8.dp))
                    Text("Extracting audio & generating timed .srt captions...", color = TextSecondary)
                }
            }
        } else if (transcriptState is AiTranscriptUiState.Error) {
            item {
                Text((transcriptState as AiTranscriptUiState.Error).message, color = LiveCrimson)
            }
        }

        item {
            OutlinedTextField(
                value = editableSrt,
                onValueChange = { editableSrt = it },
                label = { Text("Editable SRT Captions") },
                minLines = 8,
                maxLines = 14,
                modifier = Modifier.fillMaxWidth().testTag("srt_editor_input")
            )
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = {
                        val fileName = "subtitles_${languageCode}_${System.currentTimeMillis()}.srt"
                        onExportSrtFile(fileName, editableSrt)
                    },
                    enabled = editableSrt.isNotBlank(),
                    modifier = Modifier.weight(1f).testTag("export_srt_btn")
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, tint = ElectricCyan)
                    Spacer(Modifier.width(6.dp))
                    Text("Export .srt")
                }
                Button(
                    onClick = {
                        selectedVideo?.let { vid ->
                            onSaveSubtitles(vid, languageCode, editableSrt, !vid.youtubeVideoId.isNullOrBlank())
                        }
                    },
                    enabled = selectedVideo != null && editableSrt.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                    modifier = Modifier.weight(1f).testTag("upload_captions_yt_btn")
                ) {
                    Icon(Icons.Default.CloudUpload, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (selectedVideo?.youtubeVideoId.isNullOrBlank()) "Save SRT" else "Upload to YT")
                }
            }
        }
    }
}

/**
 * Feature #12: AI Video Transcription (EN/HI speech-to-text, timestamps, edit, export .txt, save to Cloud).
 */
@Composable
fun AiTranscriptionTab(
    aiViewModel: AiStudioViewModel,
    videos: List<CloudVideo>,
    onExportTxtFile: (fileName: String, content: String) -> Unit,
    onSaveTranscript: (videoId: String, transcript: String) -> Unit
) {
    val context = LocalContext.current
    val transcriptState by aiViewModel.transcriptState.collectAsStateWithLifecycle()

    var selectedVideo by remember(videos) { mutableStateOf(videos.firstOrNull()) }
    var languageCode by remember { mutableStateOf("en") }
    var notes by remember(selectedVideo) { mutableStateOf(selectedVideo?.description.orEmpty()) }
    var editableTranscript by remember(selectedVideo) { mutableStateOf(selectedVideo?.transcriptText.orEmpty()) }

    LaunchedEffect(transcriptState) {
        val st = transcriptState as? AiTranscriptUiState.Success
        if (st != null) {
            val formatted = buildString {
                if (st.result.segments.isNotEmpty()) {
                    st.result.segments.forEach { seg ->
                        append("[${seg.startTimestamp} - ${seg.endTimestamp}] ${seg.text}\n")
                    }
                } else {
                    append(st.result.fullTranscript)
                }
            }
            editableTranscript = formatted
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            CloudVideoSelectorRow(videos, selectedVideo) { selectedVideo = it }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = languageCode == "en",
                    onClick = { languageCode = "en" },
                    label = { Text("English Speech") }
                )
                FilterChip(
                    selected = languageCode == "hi",
                    onClick = { languageCode = "hi" },
                    label = { Text("हिन्दी Speech") }
                )
            }
        }
        item {
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Speaker Context / Topic Notes") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            Button(
                onClick = {
                    val uri = selectedVideo?.localUri?.let { Uri.parse(it) }
                    aiViewModel.generateSubtitlesOrTranscript(
                        context = context,
                        videoUri = uri,
                        videoTitle = selectedVideo?.title ?: "DREAMIIT28 Video",
                        videoNotes = notes,
                        durationSec = selectedVideo?.durationSec ?: 60L,
                        languageCode = languageCode
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("transcribe_video_btn")
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Convert Speech to Timestamped Transcript")
            }
        }

        if (transcriptState is AiTranscriptUiState.Loading) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = ElectricCyan)
                    Spacer(Modifier.width(8.dp))
                    Text("Transcribing audio via Firebase AI Logic...", color = TextSecondary)
                }
            }
        }

        item {
            OutlinedTextField(
                value = editableTranscript,
                onValueChange = { editableTranscript = it },
                label = { Text("Timestamped Transcript (Editable)") },
                minLines = 8,
                maxLines = 16,
                modifier = Modifier.fillMaxWidth().testTag("transcript_editor_input")
            )
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = {
                        onExportTxtFile("transcript_${System.currentTimeMillis()}.txt", editableTranscript)
                    },
                    enabled = editableTranscript.isNotBlank(),
                    modifier = Modifier.weight(1f).testTag("download_transcript_btn")
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, tint = ElectricCyan)
                    Spacer(Modifier.width(6.dp))
                    Text("Download .txt")
                }
                Button(
                    onClick = {
                        selectedVideo?.let { vid -> onSaveTranscript(vid.id, editableTranscript) }
                    },
                    enabled = selectedVideo != null && editableTranscript.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                    modifier = Modifier.weight(1f).testTag("save_transcript_cloud_btn")
                ) {
                    Icon(Icons.Default.Save, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Save to Cloud")
                }
            }
        }
    }
}

/**
 * Feature #13: Automatic Video Chapters (Analyze content, suggest 00:00 chapters, manual edit, confirm before adding to description).
 */
@Composable
fun AiChaptersTab(
    aiViewModel: AiStudioViewModel,
    videos: List<CloudVideo>,
    onApplyChapters: (video: CloudVideo, chaptersBlock: String, syncYouTube: Boolean) -> Unit
) {
    val chaptersState by aiViewModel.chaptersState.collectAsStateWithLifecycle()
    var selectedVideo by remember(videos) { mutableStateOf(videos.firstOrNull()) }
    var transcriptInput by remember(selectedVideo) {
        mutableStateOf(selectedVideo?.transcriptText ?: selectedVideo?.description.orEmpty())
    }
    var editableChapters by remember(selectedVideo) { mutableStateOf(selectedVideo?.chaptersText.orEmpty()) }
    var syncYouTube by remember { mutableStateOf(true) }
    var showConfirmDialog by remember { mutableStateOf(false) }

    LaunchedEffect(chaptersState) {
        val st = chaptersState as? AiChaptersUiState.Success
        if (st != null) {
            editableChapters = st.chapters.joinToString("\n") { "${it.timestamp} ${it.title}" }
        }
    }

    if (showConfirmDialog && selectedVideo != null) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text("Confirm Adding Chapters") },
            text = {
                Text("Append these chapters to '${selectedVideo!!.title}' description${if (syncYouTube && !selectedVideo!!.youtubeVideoId.isNullOrBlank()) " and update on YouTube" else ""}?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onApplyChapters(selectedVideo!!, editableChapters, syncYouTube)
                        showConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)
                ) { Text("Confirm & Apply") }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) { Text("Cancel") }
            }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            CloudVideoSelectorRow(videos, selectedVideo) { selectedVideo = it }
        }
        item {
            OutlinedTextField(
                value = transcriptInput,
                onValueChange = { transcriptInput = it },
                label = { Text("Video Transcript / Section Outline") },
                minLines = 3,
                maxLines = 6,
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            Button(
                onClick = {
                    aiViewModel.generateVideoChapters(
                        videoTitle = selectedVideo?.title ?: "DREAMIIT28 Video",
                        transcriptOrNotes = transcriptInput,
                        durationSec = selectedVideo?.durationSec ?: 180L
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("generate_chapters_btn")
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Suggest YouTube Chapters (00:00+)")
            }
        }

        if (chaptersState is AiChaptersUiState.Loading) {
            item { CircularProgressIndicator(color = ElectricCyan) }
        } else if (chaptersState is AiChaptersUiState.Error) {
            item { Text((chaptersState as AiChaptersUiState.Error).message, color = LiveCrimson) }
        }

        item {
            OutlinedTextField(
                value = editableChapters,
                onValueChange = { editableChapters = it },
                label = { Text("Editable Chapters (00:00 Intro ...)") },
                minLines = 6,
                maxLines = 12,
                modifier = Modifier.fillMaxWidth().testTag("chapters_editor_input")
            )
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Also update YouTube video description", color = TextSecondary)
                Switch(checked = syncYouTube, onCheckedChange = { syncYouTube = it })
            }
        }
        item {
            Button(
                onClick = { showConfirmDialog = true },
                enabled = selectedVideo != null && editableChapters.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = SuccessEmerald, contentColor = Color.Black),
                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("apply_chapters_btn")
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Add Chapters to Video Description", fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * Feature #11: YouTube SEO Assistant (Keywords, titles, descriptions, hashtags, analyzer, EN/HI, NO guaranteed rankings/views).
 */
@Composable
fun AiSeoAssistantTab(aiViewModel: AiStudioViewModel) {
    val seoState by aiViewModel.seoState.collectAsStateWithLifecycle()
    var currentTitle by remember { mutableStateOf("") }
    var currentDesc by remember { mutableStateOf("") }
    var topicContext by remember { mutableStateOf("") }
    var languageCode by remember { mutableStateOf("en") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Surface(
                color = ObsidianSurface,
                border = BorderStroke(1.dp, WarningAmber.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = WarningAmber)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "SEO Assistant provides clarity & metadata suggestions only — YouTube rankings, CTR, and views cannot be guaranteed.",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = languageCode == "en", onClick = { languageCode = "en" }, label = { Text("English SEO") })
                FilterChip(selected = languageCode == "hi", onClick = { languageCode = "hi" }, label = { Text("हिन्दी SEO") })
            }
        }
        item {
            OutlinedTextField(
                value = currentTitle,
                onValueChange = { currentTitle = it },
                label = { Text("Current Video Title") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("seo_title_input")
            )
        }
        item {
            OutlinedTextField(
                value = currentDesc,
                onValueChange = { currentDesc = it },
                label = { Text("Current Video Description") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth().testTag("seo_desc_input")
            )
        }
        item {
            OutlinedTextField(
                value = topicContext,
                onValueChange = { topicContext = it },
                label = { Text("Target Topic / Audience") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            Button(
                onClick = { aiViewModel.analyzeSeo(currentTitle, currentDesc, topicContext, languageCode) },
                colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("analyze_seo_btn")
            ) {
                Icon(Icons.Default.TrendingUp, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Analyze Title & Suggest SEO")
            }
        }

        when (val st = seoState) {
            is AiSeoUiState.Loading -> item {
                CircularProgressIndicator(color = ElectricCyan)
            }
            is AiSeoUiState.Error -> item {
                Text(st.message, color = LiveCrimson)
            }
            is AiSeoUiState.Success -> {
                val seo = st.seo
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = ElevatedCard),
                        border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Metadata Clarity Score", style = MaterialTheme.typography.titleMedium, color = Color.White)
                                StatusBadge(
                                    text = "${seo.scoreOutOf100}/100",
                                    color = if (seo.scoreOutOf100 >= 75) SuccessEmerald else WarningAmber
                                )
                            }
                            Text("Title Feedback: ${seo.titleFeedback}", color = TextSecondary)
                            Text("Description Feedback: ${seo.descriptionFeedback}", color = TextSecondary)
                            Text("Keywords: ${seo.keywords.joinToString(", ")}", color = ElectricCyan)
                            Text("Hashtags: ${seo.hashtags.joinToString(" ")}", color = SuccessEmerald)
                            Text("Suggested Titles:", color = Color.White, fontWeight = FontWeight.Bold)
                            seo.suggestedTitles.forEach { title ->
                                FilterChip(
                                    selected = currentTitle == title,
                                    onClick = {
                                        currentTitle = title
                                        currentDesc = seo.suggestedDescription
                                    },
                                    label = { Text(title) }
                                )
                            }
                            Text("Recommendations:", color = Color.White, fontWeight = FontWeight.Bold)
                            seo.recommendations.forEach { rec ->
                                Text("• $rec", color = TextSecondary)
                            }
                            Text(seo.disclaimer, color = WarningAmber, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
            AiSeoUiState.Idle -> Unit
        }
    }
}
