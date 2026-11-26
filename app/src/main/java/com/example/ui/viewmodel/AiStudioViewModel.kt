package com.example.ui.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.ai.DreamiitAiRepository
import com.example.data.model.AiMusicTrack
import com.example.data.model.AiTitlePack
import com.example.data.model.RepurposeSuggestion
import com.example.data.model.SeoAnalysisPack
import com.example.data.model.TranscriptResult
import com.example.data.model.VideoChapter
import com.example.media.MediaEditorEngine
import com.example.media.ThumbnailRenderer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface AiCopyUiState {
    data object Idle : AiCopyUiState
    data object Loading : AiCopyUiState
    data class Success(val pack: AiTitlePack) : AiCopyUiState
    data class Error(val message: String) : AiCopyUiState
}

sealed interface AiConceptUiState {
    data object Idle : AiConceptUiState
    data object Loading : AiConceptUiState
    data class Success(val text: String) : AiConceptUiState
    data class Error(val message: String) : AiConceptUiState
}

sealed interface AiImageUiState {
    data object Idle : AiImageUiState
    data object Loading : AiImageUiState
    data class Success(val rawBitmap: Bitmap, val previewBitmap: Bitmap) : AiImageUiState
    data class Error(val message: String) : AiImageUiState
}

sealed interface AiTranscriptUiState {
    data object Idle : AiTranscriptUiState
    data object Loading : AiTranscriptUiState
    data class Success(val result: TranscriptResult, val srtContent: String) : AiTranscriptUiState
    data class Error(val message: String) : AiTranscriptUiState
}

sealed interface AiChaptersUiState {
    data object Idle : AiChaptersUiState
    data object Loading : AiChaptersUiState
    data class Success(val chapters: List<VideoChapter>) : AiChaptersUiState
    data class Error(val message: String) : AiChaptersUiState
}

sealed interface AiRepurposeUiState {
    data object Idle : AiRepurposeUiState
    data object Loading : AiRepurposeUiState
    data class Success(val suggestions: List<RepurposeSuggestion>) : AiRepurposeUiState
    data class Error(val message: String) : AiRepurposeUiState
}

sealed interface AiSeoUiState {
    data object Idle : AiSeoUiState
    data object Loading : AiSeoUiState
    data class Success(val seo: SeoAnalysisPack) : AiSeoUiState
    data class Error(val message: String) : AiSeoUiState
}

sealed interface AiMusicUiState {
    data object Idle : AiMusicUiState
    data object Loading : AiMusicUiState
    data class Success(val track: AiMusicTrack) : AiMusicUiState
    data class Error(val message: String) : AiMusicUiState
}

class AiStudioViewModel(
    private val aiRepository: DreamiitAiRepository
) : ViewModel() {

    private val _copyState = MutableStateFlow<AiCopyUiState>(AiCopyUiState.Idle)
    val copyState: StateFlow<AiCopyUiState> = _copyState.asStateFlow()

    private val _conceptState = MutableStateFlow<AiConceptUiState>(AiConceptUiState.Idle)
    val conceptState: StateFlow<AiConceptUiState> = _conceptState.asStateFlow()

    private val _imageState = MutableStateFlow<AiImageUiState>(AiImageUiState.Idle)
    val imageState: StateFlow<AiImageUiState> = _imageState.asStateFlow()

    private val _transcriptState = MutableStateFlow<AiTranscriptUiState>(AiTranscriptUiState.Idle)
    val transcriptState: StateFlow<AiTranscriptUiState> = _transcriptState.asStateFlow()

    private val _chaptersState = MutableStateFlow<AiChaptersUiState>(AiChaptersUiState.Idle)
    val chaptersState: StateFlow<AiChaptersUiState> = _chaptersState.asStateFlow()

    private val _repurposeState = MutableStateFlow<AiRepurposeUiState>(AiRepurposeUiState.Idle)
    val repurposeState: StateFlow<AiRepurposeUiState> = _repurposeState.asStateFlow()

    private val _seoState = MutableStateFlow<AiSeoUiState>(AiSeoUiState.Idle)
    val seoState: StateFlow<AiSeoUiState> = _seoState.asStateFlow()

    private val _musicState = MutableStateFlow<AiMusicUiState>(AiMusicUiState.Idle)
    val musicState: StateFlow<AiMusicUiState> = _musicState.asStateFlow()

    fun generateTitleAndDescription(topic: String, languageCode: String, isLive: Boolean) {
        if (topic.isBlank()) {
            _copyState.value = AiCopyUiState.Error("Please enter a video topic first.")
            return
        }
        viewModelScope.launch {
            _copyState.value = AiCopyUiState.Loading
            try {
                val pack = aiRepository.generateTitleAndDescription(topic.trim(), languageCode, isLive)
                _copyState.value = AiCopyUiState.Success(pack)
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "AI title generation timed out", e)
                _copyState.value = AiCopyUiState.Error("AI generation timed out after 60 seconds. Please retry.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "AI title generation failed", e)
                _copyState.value = AiCopyUiState.Error(e.localizedMessage ?: "Failed to generate AI copy.")
            }
        }
    }

    fun generateThumbnailConcept(topic: String, style: String, overlayText: String) {
        if (topic.isBlank()) {
            _conceptState.value = AiConceptUiState.Error("Enter a video topic to brainstorm thumbnail concepts.")
            return
        }
        viewModelScope.launch {
            _conceptState.value = AiConceptUiState.Loading
            try {
                val text = aiRepository.generateThumbnailConcept(topic.trim(), style, overlayText)
                _conceptState.value = AiConceptUiState.Success(text)
            } catch (e: TimeoutCancellationException) {
                _conceptState.value = AiConceptUiState.Error("Concept generation timed out.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Thumbnail concept failed", e)
                _conceptState.value = AiConceptUiState.Error(e.localizedMessage ?: "Concept generation failed.")
            }
        }
    }

    fun generateThumbnailImage(
        topic: String,
        style: String,
        overlayText: String,
        badgeText: String,
        referencePhoto: Bitmap?
    ) {
        if (topic.isBlank()) {
            _imageState.value = AiImageUiState.Error("Enter a thumbnail topic or subject first.")
            return
        }
        viewModelScope.launch {
            _imageState.value = AiImageUiState.Loading
            try {
                val raw = aiRepository.generateThumbnailArtwork(topic.trim(), style, referencePhoto)
                val preview = ThumbnailRenderer.renderWithOverlay(raw, overlayText, badgeText)
                _imageState.value = AiImageUiState.Success(rawBitmap = raw, previewBitmap = preview)
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Thumbnail image generation timed out", e)
                _imageState.value = AiImageUiState.Error("Image synthesis timed out after 60 seconds. Please try again.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Thumbnail image generation failed", e)
                _imageState.value = AiImageUiState.Error(e.localizedMessage ?: "Thumbnail generation failed.")
            }
        }
    }

    fun updateOverlayPreview(overlayText: String, badgeText: String) {
        val current = _imageState.value as? AiImageUiState.Success ?: return
        val updated = ThumbnailRenderer.renderWithOverlay(current.rawBitmap, overlayText, badgeText)
        _imageState.value = current.copy(previewBitmap = updated)
    }

    fun applyUploadedPhotoDirectly(photo: Bitmap, overlayText: String, badgeText: String) {
        val preview = ThumbnailRenderer.renderWithOverlay(photo, overlayText, badgeText)
        _imageState.value = AiImageUiState.Success(rawBitmap = photo, previewBitmap = preview)
    }

    // --- V3.0 Features #5 & #12: Subtitles (EN/HI) & Speech-to-Text Transcription ---
    fun generateSubtitlesOrTranscript(
        context: Context,
        videoUri: Uri?,
        videoTitle: String,
        videoNotes: String,
        durationSec: Long,
        languageCode: String
    ) {
        if (videoUri == null && videoTitle.isBlank() && videoNotes.isBlank()) {
            _transcriptState.value = AiTranscriptUiState.Error("Select a video or enter notes first.")
            return
        }
        viewModelScope.launch {
            _transcriptState.value = AiTranscriptUiState.Loading
            try {
                val audioBytes = videoUri?.let { MediaEditorEngine.extractAudioTrackForAi(context, it) }
                val res = aiRepository.transcribeMediaWithTimestamps(
                    audioBytes = audioBytes,
                    mimeType = "audio/mp4",
                    videoTitle = videoTitle.ifBlank { "DREAMIIT28 Video" },
                    videoNotes = videoNotes,
                    durationSec = durationSec,
                    targetLanguageCode = languageCode
                )
                val srt = aiRepository.formatSegmentsToSrt(res.segments)
                _transcriptState.value = AiTranscriptUiState.Success(res, srt)
            } catch (e: TimeoutCancellationException) {
                _transcriptState.value = AiTranscriptUiState.Error("Transcription timed out. Try a shorter clip or check connection.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Transcription failed", e)
                _transcriptState.value = AiTranscriptUiState.Error(e.localizedMessage ?: "Transcription failed.")
            }
        }
    }

    // --- V3.0 Feature #13: Automatic Video Chapters ---
    fun generateVideoChapters(
        videoTitle: String,
        transcriptOrNotes: String,
        durationSec: Long
    ) {
        if (videoTitle.isBlank() && transcriptOrNotes.isBlank()) {
            _chaptersState.value = AiChaptersUiState.Error("Select a video or paste a transcript/notes first.")
            return
        }
        viewModelScope.launch {
            _chaptersState.value = AiChaptersUiState.Loading
            try {
                val chapters = aiRepository.suggestVideoChapters(videoTitle, transcriptOrNotes, durationSec)
                _chaptersState.value = AiChaptersUiState.Success(chapters)
            } catch (e: TimeoutCancellationException) {
                _chaptersState.value = AiChaptersUiState.Error("Chapter suggestion timed out.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _chaptersState.value = AiChaptersUiState.Error(e.localizedMessage ?: "Failed to generate chapters.")
            }
        }
    }

    // --- V3.0 Feature #14: Content Repurposing Suggestions ---
    fun suggestRepurposeClips(
        videoTitle: String,
        transcriptOrNotes: String,
        durationSec: Long
    ) {
        if (videoTitle.isBlank() && transcriptOrNotes.isBlank()) {
            _repurposeState.value = AiRepurposeUiState.Error("Select a video or provide transcript/notes first.")
            return
        }
        viewModelScope.launch {
            _repurposeState.value = AiRepurposeUiState.Loading
            try {
                val clips = aiRepository.suggestRepurposeClips(videoTitle, transcriptOrNotes, durationSec)
                _repurposeState.value = AiRepurposeUiState.Success(clips)
            } catch (e: TimeoutCancellationException) {
                _repurposeState.value = AiRepurposeUiState.Error("Clip detection timed out.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _repurposeState.value = AiRepurposeUiState.Error(e.localizedMessage ?: "Failed to suggest Shorts clips.")
            }
        }
    }

    // --- V3.0 Feature #11: YouTube SEO Assistant ---
    fun analyzeSeo(
        currentTitle: String,
        currentDescription: String,
        topicContext: String,
        languageCode: String
    ) {
        if (currentTitle.isBlank() && topicContext.isBlank()) {
            _seoState.value = AiSeoUiState.Error("Enter a title or topic to analyze SEO.")
            return
        }
        viewModelScope.launch {
            _seoState.value = AiSeoUiState.Loading
            try {
                val seo = aiRepository.analyzeAndSuggestSeo(currentTitle, currentDescription, topicContext, languageCode)
                _seoState.value = AiSeoUiState.Success(seo)
            } catch (e: TimeoutCancellationException) {
                _seoState.value = AiSeoUiState.Error("SEO analysis timed out.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _seoState.value = AiSeoUiState.Error(e.localizedMessage ?: "Failed to analyze SEO.")
            }
        }
    }

    fun generateMusicTrack(prompt: String, genre: String, isShortClip: Boolean) {
        viewModelScope.launch {
            _musicState.value = AiMusicUiState.Loading
            try {
                val track = aiRepository.generateMusicTrack(prompt, genre, isShortClip)
                _musicState.value = AiMusicUiState.Success(track)
            } catch (e: TimeoutCancellationException) {
                _musicState.value = AiMusicUiState.Error("Music composition timed out.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _musicState.value = AiMusicUiState.Error(e.localizedMessage ?: "Music generation failed.")
            }
        }
    }

    companion object {
        private const val TAG = "AiStudioVM"

        fun provideFactory(): ViewModelProvider.Factory = viewModelFactory {
            initializer { AiStudioViewModel(DreamiitAiRepository()) }
        }
    }
}
