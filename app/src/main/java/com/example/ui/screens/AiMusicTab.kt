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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.MusicNote
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.StatusBadge
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ElevatedCard
import com.example.ui.theme.LiveCrimson
import com.example.ui.theme.SuccessEmerald
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarningAmber
import com.example.ui.viewmodel.AiMusicUiState
import com.example.ui.viewmodel.AiStudioViewModel

@Composable
fun AiMusicTab(
    aiViewModel: AiStudioViewModel,
    onExportTextArtifact: (fileName: String, content: String) -> Unit
) {
    val musicState by aiViewModel.musicState.collectAsStateWithLifecycle()

    var musicPrompt by remember { mutableStateOf("") }
    var selectedGenre by remember { mutableStateOf("Cinematic Electronic Tech") }
    var isShortClip by remember { mutableStateOf(true) } // lyria-3-clip-preview vs pro-preview

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("ai_music_tab"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Surface(
                color = ElevatedCard,
                border = BorderStroke(1.dp, ElectricBlue.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.MusicNote, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("AI Music Generator (Gemini 3.5 Flash Lite)", style = MaterialTheme.typography.titleMedium, color = Color.White)
                        Text(
                            text = if (isShortClip) "Lyria-3-clip-preview (Short 15-30s jingle/intro)" else "Lyria-3-pro-preview (Full-length background track)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }
            }
        }

        item {
            OutlinedTextField(
                value = musicPrompt,
                onValueChange = { musicPrompt = it },
                label = { Text("Music Vibe / Prompt (e.g. upbeat electric tech intro)") },
                minLines = 2,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("music_prompt_input")
            )
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Cinematic Electric Tech", "Lo-Fi Study Beat", "Ambient Chill", "Energetic EDM").forEach { genre ->
                    FilterChip(
                        selected = selectedGenre == genre,
                        onClick = { selectedGenre = genre },
                        label = { Text(genre) }
                    )
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isShortClip) "Format: Short Clip (30s max)" else "Format: Full Soundtrack",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White
                )
                androidx.compose.material3.Switch(
                    checked = isShortClip,
                    onCheckedChange = { isShortClip = it },
                    modifier = Modifier.testTag("music_clip_mode_switch")
                )
            }
        }

        item {
            Button(
                onClick = {
                    aiViewModel.generateMusicTrack(
                        prompt = musicPrompt.ifBlank { selectedGenre },
                        genre = selectedGenre,
                        isShortClip = isShortClip
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("generate_music_button")
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Generate AI Music Track", fontWeight = FontWeight.Bold)
            }
        }

        when (val st = musicState) {
            is AiMusicUiState.Loading -> item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(color = ElectricCyan, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Composing AI music arrangement (gemini-3.5-flash-lite)...", color = TextSecondary)
                }
            }
            is AiMusicUiState.Error -> item {
                Text(st.message, color = LiveCrimson, style = MaterialTheme.typography.bodyMedium)
            }
            is AiMusicUiState.Success -> item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = ElevatedCard),
                    border = BorderStroke(1.dp, SuccessEmerald),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text(st.track.title, style = MaterialTheme.typography.titleMedium, color = Color.White)
                            StatusBadge(text = st.track.modelName, color = SuccessEmerald)
                        }
                        Text("Genre: ${st.track.genre} • Duration: ${st.track.durationSec}s", style = MaterialTheme.typography.bodyMedium, color = ElectricCyan)
                        Text("Arrangement Notes:\n${st.track.arrangementSummary}", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                        Spacer(Modifier.height(6.dp))
                        OutlinedButton(
                            onClick = {
                                val content = """
                                    DREAMIIT28 AI Music Track
                                    Title: ${st.track.title}
                                    Genre: ${st.track.genre}
                                    Duration: ${st.track.durationSec}s
                                    Model: ${st.track.modelName}
                                    Arrangement:
                                    ${st.track.arrangementSummary}
                                    Chords / Progression:
                                    ${st.track.chords}
                                """.trimIndent()
                                onExportTextArtifact("music_cue_${System.currentTimeMillis()}.txt", content)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, tint = ElectricCyan)
                            Spacer(Modifier.width(6.dp))
                            Text("Export Music Cue Sheet (.txt)")
                        }
                    }
                }
            }
            is AiMusicUiState.Idle -> item {
                Text(
                    "Enter a vibe or select a genre to compose royalty-free creator music using Gemini 3.5 Flash Lite.",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
