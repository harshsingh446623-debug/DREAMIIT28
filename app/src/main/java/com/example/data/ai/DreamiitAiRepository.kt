package com.example.data.ai

import android.graphics.Bitmap
import com.example.data.model.AiTitlePack
import com.example.data.model.RepurposeSuggestion
import com.example.data.model.SeoAnalysisPack
import com.example.data.model.TranscriptResult
import com.example.data.model.TranscriptSegment
import com.example.data.model.VideoChapter
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.AspectRatio
import com.google.firebase.ai.type.ImageConfig
import com.google.firebase.ai.type.ImagePart
import com.google.firebase.ai.type.ImageSize
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject

class DreamiitAiRepository {
    private val copySchema = Schema.obj(
        properties = mapOf(
            "titles" to Schema.array(
                items = Schema.string(),
                description = "5 honest, high-CTR YouTube titles"
            ),
            "description" to Schema.string(description = "Structured YouTube video description"),
            "shortPromo" to Schema.string(description = "Short 2-sentence promotional teaser"),
            "tags" to Schema.array(items = Schema.string(), description = "10 relevant YouTube tags"),
            "hashtags" to Schema.array(items = Schema.string(), description = "5 relevant hashtags starting with #"),
            "category" to Schema.string(description = "Best YouTube category name")
        )
    )

    private val transcriptSchema = Schema.obj(
        properties = mapOf(
            "language" to Schema.string(),
            "fullTranscript" to Schema.string(),
            "segments" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "startTimestamp" to Schema.string(description = "SRT timestamp like 00:00:01,000"),
                        "endTimestamp" to Schema.string(description = "SRT timestamp like 00:00:05,500"),
                        "text" to Schema.string()
                    )
                )
            )
        )
    )

    private val chaptersSchema = Schema.obj(
        properties = mapOf(
            "chapters" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "timestamp" to Schema.string(description = "YouTube chapter timestamp starting with 00:00"),
                        "title" to Schema.string(description = "Clear concise chapter title")
                    )
                )
            )
        )
    )

    private val repurposeSchema = Schema.obj(
        properties = mapOf(
            "clips" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "startSec" to Schema.integer(description = "Clip start second"),
                        "endSec" to Schema.integer(description = "Clip end second (15-58s after startSec)"),
                        "reason" to Schema.string(description = "Why this moment works well as a vertical Short"),
                        "suggestedShortsTitle" to Schema.string(description = "Concise Shorts title under 80 chars"),
                        "suggestedShortsDescription" to Schema.string(description = "Shorts description with #Shorts")
                    )
                )
            )
        )
    )

    private val seoSchema = Schema.obj(
        properties = mapOf(
            "scoreOutOf100" to Schema.integer(),
            "titleFeedback" to Schema.string(),
            "descriptionFeedback" to Schema.string(),
            "keywords" to Schema.array(items = Schema.string()),
            "suggestedTitles" to Schema.array(items = Schema.string()),
            "suggestedDescription" to Schema.string(),
            "hashtags" to Schema.array(items = Schema.string()),
            "recommendations" to Schema.array(items = Schema.string()),
            "disclaimer" to Schema.string()
        )
    )

    private val copyrightRiskSchema = Schema.obj(
        properties = mapOf(
            "riskLevel" to Schema.string(description = "One of LOW, MEDIUM, or HIGH"),
            "summary" to Schema.string(description = "Clear explanation of copyright & YouTube Community Guidelines review"),
            "triggers" to Schema.array(items = Schema.string(), description = "Specific copyright or reuse triggers found"),
            "mitigations" to Schema.array(items = Schema.string(), description = "Actionable steps to clear or license material"),
            "policyFlags" to Schema.array(items = Schema.string(), description = "Relevant YouTube policy categories")
        )
    )

    private val copyModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = copySchema
            }
        )
    }

    private val transcriptModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = transcriptSchema
            }
        )
    }

    private val chaptersModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = chaptersSchema
            }
        )
    }

    private val repurposeModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = repurposeSchema
            }
        )
    }

    private val seoModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = seoSchema
            }
        )
    }

    private val copyrightModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = copyrightRiskSchema
            }
        )
    }

    private val conceptModel by lazy {
        Firebase.ai.generativeModel("gemini-3.8-flash")
    }

    private val imageModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-nano-banana-2.1",
            generationConfig = generationConfig {
                responseModalities = listOf(ResponseModality.IMAGE)
                imageConfig = ImageConfig.builder()
                    .setAspectRatio(AspectRatio.LANDSCAPE_16x9)
                    .setImageSize(ImageSize.SIZE_1K)
                    .build()
            }
        )
    }

    suspend fun generateTitleAndDescription(
        topic: String,
        languageCode: String,
        isLiveStream: Boolean
    ): AiTitlePack = withTimeout(60_000L) {
        val langLabel = if (languageCode == "hi") "Hindi (Devanagari script, natural creator tone)" else "English"
        val formatLabel = if (isLiveStream) "YouTube 1080p Live Broadcast" else "YouTube Video Upload"
        val prompt = """
            You are an expert YouTube growth strategist for the channel platform DREAMIIT28.
            Generate honest, accurate, non-clickbait metadata in $langLabel for a $formatLabel about:
            "$topic"
            Rules:
            - Never invent fake claims or misleading clickbait.
            - Provide 5 strong titles, a clear structured description with key takeaways,
              a short promotional blurb, 10 SEO tags, 5 #hashtags, and 1 YouTube category.
        """.trimIndent()

        val response = copyModel.generateContent(prompt)
        val rawJson = response.text ?: throw IllegalStateException("AI model returned empty response.")
        val json = JSONObject(rawJson)

        AiTitlePack(
            titles = json.optStringList("titles"),
            description = json.optString("description"),
            shortPromo = json.optString("shortPromo"),
            tags = json.optStringList("tags"),
            hashtags = json.optStringList("hashtags"),
            category = json.optString("category", "Education")
        )
    }

    suspend fun generateThumbnailConcept(
        topic: String,
        style: String,
        overlayText: String
    ): String = withTimeout(45_000L) {
        val prompt = """
            Suggest 3 concise, honest YouTube thumbnail design concepts for:
            Topic: $topic
            Style: $style
            Text overlay: $overlayText
            Keep every concept faithful to the video content (no misleading clickbait).
        """.trimIndent()
        val response = conceptModel.generateContent(prompt)
        response.text ?: throw IllegalStateException("Concept model returned empty text.")
    }

    suspend fun generateThumbnailArtwork(
        topic: String,
        style: String,
        referencePhoto: Bitmap?
    ): Bitmap = withTimeout(60_000L) {
        val promptText = """
            Create a clean, high-contrast 16:9 YouTube thumbnail background for "$topic".
            Visual style: $style with subtle dark obsidian depth and electric blue rim lighting.
            Leave negative space on the lower-left for bold headline typography.
            Do not embed gibberish text or misleading clickbait elements.
        """.trimIndent()

        val response = if (referencePhoto != null) {
            imageModel.generateContent(
                content {
                    image(referencePhoto)
                    text("Transform and enhance this creator photo into a crisp 16:9 YouTube thumbnail background. $promptText")
                }
            )
        } else {
            imageModel.generateContent(promptText)
        }

        val imagePart = response.candidates.firstOrNull()
            ?.content?.parts?.filterIsInstance<ImagePart>()
            ?.firstOrNull()

        imagePart?.image ?: throw IllegalStateException("No thumbnail image returned by model.")
    }

    // --- V3.0 Features #5 & #12: Audio Speech-to-Text Transcription & Hindi/English SRT Subtitles ---
    suspend fun transcribeMediaWithTimestamps(
        audioBytes: ByteArray?,
        mimeType: String,
        videoTitle: String,
        videoNotes: String,
        durationSec: Long,
        targetLanguageCode: String
    ): TranscriptResult = withTimeout(90_000L) {
        val langLabel = if (targetLanguageCode == "hi") {
            "Hindi (Devanagari script)"
        } else {
            "English"
        }
        val prompt = """
            Transcribe or produce timestamped subtitle segments in $langLabel for this YouTube video.
            Video title: "$videoTitle"
            Creator notes: "${videoNotes.take(800)}"
            Total duration: ${durationSec.coerceAtLeast(10L)} seconds.
            Requirements:
            - Output accurate SRT-style timestamps (HH:MM:SS,mmm) within 00:00:00,000 and the video duration.
            - Never invent fabricated factual claims outside the audio or creator notes.
            - If audio bytes are attached, transcribe the actual spoken speech faithfully in $langLabel.
            - Return both fullTranscript (clean timestamped lines like [00:00] ...) and segments list.
        """.trimIndent()

        val response = if (audioBytes != null && audioBytes.isNotEmpty()) {
            transcriptModel.generateContent(
                content {
                    inlineData(audioBytes, mimeType)
                    text(prompt)
                }
            )
        } else {
            transcriptModel.generateContent(prompt)
        }

        val raw = response.text ?: throw IllegalStateException("Transcription returned empty response.")
        val json = JSONObject(raw)
        val segArr = json.optJSONArray("segments") ?: JSONArray()
        val segments = (0 until segArr.length()).mapNotNull { idx ->
            val obj = segArr.optJSONObject(idx) ?: return@mapNotNull null
            val txt = obj.optString("text").trim()
            if (txt.isBlank()) null
            else TranscriptSegment(
                startTimestamp = obj.optString("startTimestamp", "00:00:00,000"),
                endTimestamp = obj.optString("endTimestamp", "00:00:04,000"),
                text = txt
            )
        }
        TranscriptResult(
            language = json.optString("language", targetLanguageCode),
            fullTranscript = json.optString("fullTranscript"),
            segments = segments
        )
    }

    fun formatSegmentsToSrt(segments: List<TranscriptSegment>): String = buildString {
        segments.forEachIndexed { idx, seg ->
            appendLine(idx + 1)
            appendLine("${seg.startTimestamp} --> ${seg.endTimestamp}")
            appendLine(seg.text)
            appendLine()
        }
    }.trim()

    // --- V3.0 Feature #13: Automatic Video Chapters ---
    suspend fun suggestVideoChapters(
        videoTitle: String,
        transcriptOrNotes: String,
        durationSec: Long
    ): List<VideoChapter> = withTimeout(60_000L) {
        val prompt = """
            Analyze this YouTube video metadata/transcript and propose logical YouTube chapters.
            Title: "$videoTitle"
            Duration: ${durationSec.coerceAtLeast(30L)} seconds
            Content/Transcript:
            "${transcriptOrNotes.take(6000)}"
            Rules:
            - First chapter MUST start at "00:00".
            - Timestamps must be strictly ascending and within duration.
            - Produce 4 to 10 concise, accurate chapter titles matching the video content.
        """.trimIndent()
        val response = chaptersModel.generateContent(prompt)
        val json = JSONObject(response.text ?: "{}")
        val arr = json.optJSONArray("chapters") ?: JSONArray()
        (0 until arr.length()).mapNotNull { idx ->
            val obj = arr.optJSONObject(idx) ?: return@mapNotNull null
            val title = obj.optString("title").trim()
            val ts = obj.optString("timestamp").trim()
            if (title.isBlank() || ts.isBlank()) null else VideoChapter(ts, title)
        }
    }

    // --- V3.0 Feature #14: Content Repurposing (Shorts Clips 9:16) ---
    suspend fun suggestRepurposeClips(
        videoTitle: String,
        transcriptOrNotes: String,
        durationSec: Long
    ): List<RepurposeSuggestion> = withTimeout(60_000L) {
        val maxDur = durationSec.coerceAtLeast(30L)
        val prompt = """
            Identify 3 engaging moments from this video suitable for vertical 9:16 YouTube Shorts (15 to 55 seconds each).
            Video title: "$videoTitle"
            Total duration: $maxDur seconds
            Content/Transcript:
            "${transcriptOrNotes.take(6000)}"
            Rules:
            - startSec >= 0 and endSec <= $maxDur, with (endSec - startSec) between 10 and 58 seconds.
            - Do not make exaggerated guarantees or clickbait promises.
        """.trimIndent()
        val response = repurposeModel.generateContent(prompt)
        val json = JSONObject(response.text ?: "{}")
        val arr = json.optJSONArray("clips") ?: JSONArray()
        (0 until arr.length()).mapNotNull { idx ->
            val obj = arr.optJSONObject(idx) ?: return@mapNotNull null
            val start = obj.optLong("startSec", 0L).coerceIn(0L, (maxDur - 10L).coerceAtLeast(0L))
            val end = obj.optLong("endSec", (start + 30L).coerceAtMost(maxDur)).coerceIn(start + 5L, maxDur)
            RepurposeSuggestion(
                startSec = start,
                endSec = end,
                reason = obj.optString("reason", "Key highlight moment"),
                suggestedShortsTitle = obj.optString("suggestedShortsTitle", "$videoTitle #Shorts"),
                suggestedShortsDescription = obj.optString("suggestedShortsDescription", "$videoTitle\n\n#Shorts")
            )
        }
    }

    // --- V3.0 Feature #11: YouTube SEO Assistant (No Ranking Guarantees) ---
    suspend fun analyzeAndSuggestSeo(
        currentTitle: String,
        currentDescription: String,
        topicContext: String,
        languageCode: String
    ): SeoAnalysisPack = withTimeout(60_000L) {
        val langLabel = if (languageCode == "hi") "Hindi (Devanagari)" else "English"
        val prompt = """
            Act as a responsible YouTube SEO advisor in $langLabel.
            Analyze the following YouTube title and description:
            Title: "$currentTitle"
            Description: "$currentDescription"
            Topic Context: "$topicContext"
            CRITICAL RULES:
            - Support $langLabel keywords, titles, description structure, and #hashtags.
            - Provide constructive clarity, search-intent, and readability recommendations.
            - STRICTLY DO NOT promise guaranteed rankings, guaranteed viral growth, or guaranteed views.
            - Include a clear disclaimer noting that SEO recommendations improve discoverability clarity only and do not guarantee rankings or views.
        """.trimIndent()
        val response = seoModel.generateContent(prompt)
        val json = JSONObject(response.text ?: "{}")
        SeoAnalysisPack(
            scoreOutOf100 = json.optInt("scoreOutOf100", 72).coerceIn(0, 100),
            titleFeedback = json.optString("titleFeedback"),
            descriptionFeedback = json.optString("descriptionFeedback"),
            keywords = json.optStringList("keywords"),
            suggestedTitles = json.optStringList("suggestedTitles"),
            suggestedDescription = json.optString("suggestedDescription"),
            hashtags = json.optStringList("hashtags"),
            recommendations = json.optStringList("recommendations"),
            disclaimer = json.optString(
                "disclaimer",
                "SEO suggestions improve metadata clarity and relevance only; YouTube rankings and views are never guaranteed."
            )
        )
    }

    // --- V4.1 Module 1: AI Copyright Risk & YouTube Policy Pre-Check ---
    suspend fun analyzeCopyrightAndPolicyRisk(
        videoTitle: String,
        description: String,
        tags: List<String>,
        audioNotes: String,
        transcriptOrNotes: String
    ): CopyrightRiskAssessment = withTimeout(60_000L) {
        val prompt = """
            You are a YouTube Copyright & Policy Risk Assistant.
            Review the video metadata below for potential YouTube Content ID, music licensing, third-party clip reuse, trademark, or Community Guidelines risks:
            Title: "$videoTitle"
            Description: "${description.take(2000)}"
            Tags: ${tags.joinToString(", ").ifBlank { "None" }}
            Audio / Sound Notes: "${audioNotes.take(600)}"
            Transcript / Script Notes: "${transcriptOrNotes.take(2500)}"
            Rules:
            - riskLevel MUST be strictly one of: LOW, MEDIUM, HIGH.
            - Explain clearly that this is an automated AI pre-check and does not replace official YouTube Studio Content ID checks or legal counsel.
            - List concrete triggers, actionable mitigations (e.g., YouTube Audio Library swap, license proof, attribution/transformative commentary), and policyFlags.
        """.trimIndent()

        val response = copyrightModel.generateContent(prompt)
        val json = JSONObject(response.text ?: "{}")
        val rawLevel = json.optString("riskLevel", "LOW").uppercase()
        val level = if (rawLevel in listOf("LOW", "MEDIUM", "HIGH")) rawLevel else "LOW"
        CopyrightRiskAssessment(
            riskLevel = level,
            summary = json.optString(
                "summary",
                "AI copyright pre-check complete. Verify third-party music or clips inside YouTube Studio before public release."
            ),
            triggers = json.optStringList("triggers"),
            mitigations = json.optStringList("mitigations"),
            policyFlags = json.optStringList("policyFlags")
        )
    }

    private fun JSONObject.optStringList(key: String): List<String> {
        val arr = optJSONArray(key) ?: return emptyList()
        return (0 until arr.length()).mapNotNull { idx ->
            arr.optString(idx).takeIf { it.isNotBlank() }
        }
    }
}

data class CopyrightRiskAssessment(
    val riskLevel: String,
    val summary: String,
    val triggers: List<String>,
    val mitigations: List<String>,
    val policyFlags: List<String>
)
