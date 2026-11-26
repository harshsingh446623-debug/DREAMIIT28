package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Save
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
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.CloudVideo
import com.example.localization.LocalAppStrings
import com.example.media.ThumbnailRenderer
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ElevatedCard
import com.example.ui.theme.LiveCrimson
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.SuccessEmerald
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.AiConceptUiState
import com.example.ui.viewmodel.AiCopyUiState
import com.example.ui.viewmodel.AiImageUiState
import com.example.ui.viewmodel.AiStudioViewModel

@Composable
fun AiStudioScreen(
    aiViewModel: AiStudioViewModel,
    uploadedVideos: List<CloudVideo>,
    requestedTab: Int? = null,
    onConsumeRequestedTab: () -> Unit = {},
    onUploadThumbnailToYouTube: (videoId: String, jpegBytes: ByteArray) -> Unit,
    onSavedThumbnailNotice: (String) -> Unit,
    onExportTextArtifact: (fileName: String, content: String) -> Unit = { _, _ -> },
    onSaveTranscript: (videoId: String, transcript: String) -> Unit = { _, _ -> },
    onSaveSubtitles: (video: CloudVideo, languageCode: String, srtContent: String, uploadToYouTube: Boolean) -> Unit = { _, _, _, _ -> },
    onApplyChapters: (video: CloudVideo, chaptersBlock: String, syncYouTube: Boolean) -> Unit = { _, _, _ -> },
    onOpenMediaLab: (String?) -> Unit = {}
) {
    val strings = LocalAppStrings.current
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf(
        strings.aiThumbnailTab,
        strings.aiCopyTab,
        strings.subtitlesTab,
        strings.transcriptionTab,
        strings.chaptersTab,
        strings.seoAssistantTab,
        strings.aiMusicTab
    )

    LaunchedEffect(requestedTab) {
        if (requestedTab != null && requestedTab in tabs.indices) {
            selectedTab = requestedTab
            onConsumeRequestedTab()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("AI & Media Suite 3.0", style = MaterialTheme.typography.labelLarge, color = ElectricCyan)
            OutlinedButton(
                onClick = { onOpenMediaLab(null) },
                modifier = Modifier.testTag("open_media_lab_from_ai_btn")
            ) {
                Icon(Icons.Default.ContentCut, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(strings.mediaLabTab, maxLines = 1)
            }
        }

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
                    modifier = Modifier.testTag("ai_tab_$index")
                )
            }
        }

        when (selectedTab) {
            0 -> AiThumbnailTab(
                aiViewModel = aiViewModel,
                uploadedVideos = uploadedVideos.filter { !it.youtubeVideoId.isNullOrBlank() },
                onUploadThumbnailToYouTube = onUploadThumbnailToYouTube,
                onSavedThumbnailNotice = onSavedThumbnailNotice
            )
            1 -> AiCopyAssistantTab(aiViewModel = aiViewModel)
            2 -> AiSubtitlesTab(
                aiViewModel = aiViewModel,
                videos = uploadedVideos,
                onExportSrtFile = onExportTextArtifact,
                onSaveSubtitles = onSaveSubtitles
            )
            3 -> AiTranscriptionTab(
                aiViewModel = aiViewModel,
                videos = uploadedVideos,
                onExportTxtFile = onExportTextArtifact,
                onSaveTranscript = onSaveTranscript
            )
            4 -> AiChaptersTab(
                aiViewModel = aiViewModel,
                videos = uploadedVideos,
                onApplyChapters = onApplyChapters
            )
            5 -> AiSeoAssistantTab(aiViewModel = aiViewModel)
            6 -> AiMusicTab(aiViewModel = aiViewModel, onExportTextArtifact = onExportTextArtifact)
        }
    }
}

@Composable
private fun AiThumbnailTab(
    aiViewModel: AiStudioViewModel,
    uploadedVideos: List<CloudVideo>,
    onUploadThumbnailToYouTube: (videoId: String, jpegBytes: ByteArray) -> Unit,
    onSavedThumbnailNotice: (String) -> Unit
) {
    val context = LocalContext.current
    val conceptState by aiViewModel.conceptState.collectAsStateWithLifecycle()
    val imageState by aiViewModel.imageState.collectAsStateWithLifecycle()

    var topic by remember { mutableStateOf("") }
    var overlayText by remember { mutableStateOf("") }
    var badgeText by remember { mutableStateOf("DREAMIIT28 • 1080p LIVE") }
    var selectedStyle by remember { mutableStateOf("Cinematic Electric Tech") }
    var personalPhoto by remember { mutableStateOf<Bitmap?>(null) }
    var selectedYtVideoId by remember(uploadedVideos) {
        mutableStateOf(uploadedVideos.firstOrNull()?.youtubeVideoId.orEmpty())
    }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            @Suppress("DEPRECATION")
            val bmp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, _, _ ->
                    decoder.isMutableRequired = true
                }
            } else {
                MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
            }
            personalPhoto = bmp
            aiViewModel.applyUploadedPhotoDirectly(bmp, overlayText, badgeText)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            OutlinedTextField(
                value = topic,
                onValueChange = { topic = it },
                label = { Text("Video Topic / Scene Prompt") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ai_thumb_topic_input")
            )
        }

        item {
            OutlinedTextField(
                value = overlayText,
                onValueChange = {
                    overlayText = it
                    aiViewModel.updateOverlayPreview(it, badgeText)
                },
                label = { Text("Custom Thumbnail Headline Text (EN / हिन्दी)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Cinematic Electric Tech", "Bold Education Studio", "Minimal Obsidian Neon").forEach { style ->
                    FilterChip(
                        selected = selectedStyle == style,
                        onClick = { selectedStyle = style },
                        label = { Text(style) }
                    )
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = {
                        photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Image, contentDescription = null, tint = ElectricCyan)
                    Spacer(Modifier.width(6.dp))
                    Text(if (personalPhoto == null) "Personal Photo" else "Photo Ready")
                }

                OutlinedButton(
                    onClick = { aiViewModel.generateThumbnailConcept(topic, selectedStyle, overlayText) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("AI Concepts")
                }
            }
        }

        item {
            Button(
                onClick = {
                    aiViewModel.generateThumbnailImage(topic, selectedStyle, overlayText, badgeText, personalPhoto)
                },
                colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("ai_generate_thumbnail_button")
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Generate 16:9 AI Thumbnail", fontWeight = FontWeight.Bold)
            }
        }

        when (val cs = conceptState) {
            is AiConceptUiState.Loading -> item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = ElectricCyan)
                    Spacer(Modifier.width(8.dp))
                    Text("Generating honest thumbnail concepts...", color = TextSecondary)
                }
            }
            is AiConceptUiState.Success -> item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = ElevatedCard),
                    border = BorderStroke(1.dp, BorderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(cs.text, color = Color.White, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(14.dp))
                }
            }
            is AiConceptUiState.Error -> item {
                Text(cs.message, color = LiveCrimson, style = MaterialTheme.typography.bodyMedium)
            }
            else -> Unit
        }

        when (val st = imageState) {
            is AiImageUiState.Loading -> item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = ElevatedCard),
                    border = BorderStroke(1.dp, ElectricBlue.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = ElectricCyan)
                            Spacer(Modifier.height(10.dp))
                            Text("Synthesizing 16:9 thumbnail (30-60s)...", color = TextSecondary)
                        }
                    }
                }
            }
            is AiImageUiState.Error -> item {
                Surface(
                    color = LiveCrimson.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, LiveCrimson),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(st.message, color = LiveCrimson, modifier = Modifier.padding(14.dp))
                }
            }
            is AiImageUiState.Success -> {
                item {
                    Card(
                        border = BorderStroke(1.5.dp, ElectricBlue),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                    ) {
                        Image(
                            bitmap = st.previewBitmap.asImageBitmap(),
                            contentDescription = "Generated Thumbnail Preview",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = {
                                val file = ThumbnailRenderer.saveToCacheFile(
                                    context,
                                    st.previewBitmap,
                                    "dreamiit28_thumb_${System.currentTimeMillis()}.jpg"
                                )
                                onSavedThumbnailNotice("Saved thumbnail to ${file.name}")
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, tint = SuccessEmerald)
                            Spacer(Modifier.width(6.dp))
                            Text("Save 1280×720")
                        }
                        Button(
                            onClick = {
                                val bytes = ThumbnailRenderer.toJpegBytes(st.previewBitmap)
                                if (selectedYtVideoId.isNotBlank()) {
                                    onUploadThumbnailToYouTube(selectedYtVideoId, bytes)
                                } else {
                                    onSavedThumbnailNotice("Upload a video to YouTube first to attach this thumbnail directly.")
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.CloudUpload, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Upload to YT")
                        }
                    }
                }
            }
            is AiImageUiState.Idle -> item {
                Text(
                    "Enter a topic or pick a personal photo to create an honest, high-contrast 16:9 thumbnail.",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun AiCopyAssistantTab(aiViewModel: AiStudioViewModel) {
    val copyState by aiViewModel.copyState.collectAsStateWithLifecycle()

    var topic by remember { mutableStateOf("") }
    var languageCode by remember { mutableStateOf("en") }
    var isLiveStream by remember { mutableStateOf(true) }

    var editableTitle by remember { mutableStateOf("") }
    var editableDesc by remember { mutableStateOf("") }
    var editableTags by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            OutlinedTextField(
                value = topic,
                onValueChange = { topic = it },
                label = { Text("Video / Live Topic") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ai_copy_topic_input")
            )
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FilterChip(
                    selected = languageCode == "en",
                    onClick = { languageCode = "en" },
                    label = { Text("English") }
                )
                FilterChip(
                    selected = languageCode == "hi",
                    onClick = { languageCode = "hi" },
                    label = { Text("हिन्दी (Hindi)") }
                )
                Spacer(Modifier.weight(1f))
                Text("Live?", color = TextSecondary)
                Switch(checked = isLiveStream, onCheckedChange = { isLiveStream = it })
            }
        }

        item {
            Button(
                onClick = { aiViewModel.generateTitleAndDescription(topic, languageCode, isLiveStream) },
                colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("ai_generate_copy_button")
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Generate Titles, Description & Tags", fontWeight = FontWeight.Bold)
            }
        }

        when (val st = copyState) {
            is AiCopyUiState.Loading -> item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(color = ElectricCyan, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Generating bilingual YouTube metadata...", color = TextSecondary)
                }
            }
            is AiCopyUiState.Error -> item {
                Text(st.message, color = LiveCrimson, style = MaterialTheme.typography.bodyMedium)
            }
            is AiCopyUiState.Success -> {
                val pack = st.pack
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = ElevatedCard),
                        border = BorderStroke(1.dp, ElectricBlue.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Suggested Titles (Tap to load into editor)", style = MaterialTheme.typography.labelLarge, color = ElectricCyan)
                            pack.titles.forEach { t ->
                                FilterChip(
                                    selected = editableTitle == t,
                                    onClick = {
                                        editableTitle = t
                                        editableDesc = "${pack.shortPromo}\n\n${pack.description}\n\n${pack.hashtags.joinToString(" ")}"
                                        editableTags = pack.tags.joinToString(", ")
                                    },
                                    label = { Text(t) }
                                )
                            }
                            Text("Category: ${pack.category}", style = MaterialTheme.typography.labelMedium, color = SuccessEmerald)
                            Text("Hashtags: ${pack.hashtags.joinToString(" ")}", style = MaterialTheme.typography.bodyMedium, color = ElectricCyan)
                        }
                    }
                }
                item {
                    Text("Manual Review & Edit (Never published without confirmation)", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = editableTitle,
                        onValueChange = { editableTitle = it },
                        label = { Text("Editable Title") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editableDesc,
                        onValueChange = { editableDesc = it },
                        label = { Text("Editable Description") },
                        minLines = 4,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editableTags,
                        onValueChange = { editableTags = it },
                        label = { Text("Editable Tags") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            is AiCopyUiState.Idle -> item {
                Text(
                    "Enter a topic and select English or हिन्दी to generate structured YouTube metadata.",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
