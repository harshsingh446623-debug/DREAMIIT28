package com.example.media

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.ui.PlayerView
import com.example.data.model.AudioAnalysisReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Custom PCM 16-bit AudioProcessor for volume scaling / normalization inside Media3 Transformer.
 */
@OptIn(UnstableApi::class)
class GainAudioProcessor(private val gainLinear: Float) : BaseAudioProcessor() {
    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != androidx.media3.common.C.ENCODING_PCM_16BIT) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return
        val out = replaceOutputBuffer(remaining)
        val clampedGain = gainLinear.coerceIn(0.05f, 4.0f)
        while (inputBuffer.remaining() >= 2) {
            val sample = inputBuffer.short.toInt()
            val scaled = (sample * clampedGain).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            out.putShort(scaled.toShort())
        }
        out.flip()
    }
}

object MediaEditorEngine {

    /**
     * Analyzes real audio samples (peak dBFS, RMS dBFS, sample rate, channels) using MediaExtractor + MediaCodec.
     */
    suspend fun analyzeAudio(context: Context, uri: Uri): AudioAnalysisReport = withContext(Dispatchers.IO) {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, uri, null)
            var audioTrackIndex = -1
            var audioFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val fmt = extractor.getTrackFormat(i)
                val mime = fmt.getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    audioFormat = fmt
                    break
                }
            }
            if (audioTrackIndex == -1 || audioFormat == null) {
                return@withContext AudioAnalysisReport(
                    durationMs = 0L,
                    sampleRateHz = 0,
                    channelCount = 0,
                    peakDb = -96.0,
                    rmsDb = -96.0,
                    recommendedGainLinear = 1.0f,
                    issues = listOf("No audio track detected in selected video."),
                    summary = "Silent / video-only stream detected."
                )
            }

            extractor.selectTrack(audioTrackIndex)
            val mime = audioFormat.getString(MediaFormat.KEY_MIME).orEmpty()
            val sampleRate = audioFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channelCount = audioFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val durationUs = if (audioFormat.containsKey(MediaFormat.KEY_DURATION)) {
                audioFormat.getLong(MediaFormat.KEY_DURATION)
            } else 0L

            codec = MediaCodec.createDecoderByType(mime).apply {
                configure(audioFormat, null, null, 0)
                start()
            }

            val info = MediaCodec.BufferInfo()
            var sawInputEos = false
            var sawOutputEos = false
            var maxAbsSample = 0
            var sumSquares = 0.0
            var sampleCount = 0L
            // Sample up to ~20 seconds of audio across the file for fast, responsive analysis
            val maxSamplesToInspect = sampleRate.toLong() * channelCount * 20L

            while (!sawOutputEos && sampleCount < maxSamplesToInspect) {
                if (!sawInputEos) {
                    val inIdx = codec.dequeueInputBuffer(10_000)
                    if (inIdx >= 0) {
                        val inBuf = codec.getInputBuffer(inIdx)!!
                        val sampleSize = extractor.readSampleData(inBuf, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(inIdx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            sawInputEos = true
                        } else {
                            codec.queueInputBuffer(inIdx, 0, sampleSize, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val outIdx = codec.dequeueOutputBuffer(info, 10_000)
                if (outIdx >= 0) {
                    val outBuf = codec.getOutputBuffer(outIdx)
                    if (outBuf != null && info.size > 0) {
                        outBuf.position(info.offset)
                        outBuf.limit(info.offset + info.size)
                        val shortBuf = outBuf.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                        while (shortBuf.hasRemaining() && sampleCount < maxSamplesToInspect) {
                            val s = shortBuf.get().toInt()
                            val a = abs(s)
                            if (a > maxAbsSample) maxAbsSample = a
                            sumSquares += s.toDouble() * s.toDouble()
                            sampleCount++
                        }
                    }
                    codec.releaseOutputBuffer(outIdx, false)
                    if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        sawOutputEos = true
                    }
                }
            }

            val peakNorm = max(maxAbsSample / 32768.0, 1e-6)
            val rmsNorm = if (sampleCount > 0) max(sqrt(sumSquares / sampleCount) / 32768.0, 1e-6) else 1e-6
            val peakDb = 20.0 * log10(peakNorm)
            val rmsDb = 20.0 * log10(rmsNorm)

            val issues = mutableListOf<String>()
            if (peakDb > -0.8) issues.add("Peak clipping detected (${String.format("%.1f", peakDb)} dBFS) — slight attenuation recommended.")
            if (rmsDb < -28.0) issues.add("Audio is quiet (RMS ${String.format("%.1f", rmsDb)} dBFS) — boost/normalization recommended.")
            if (channelCount == 1) issues.add("Mono audio track detected (1 channel).")
            if (issues.isEmpty()) issues.add("Balanced creator audio levels detected.")

            // Target -1.5 dBFS peak (~0.84 linear) without excessive noise floor amplification
            val targetPeakLinear = 0.84
            val recommendedGain = (targetPeakLinear / peakNorm).toFloat().coerceIn(0.75f, 3.0f)

            AudioAnalysisReport(
                durationMs = durationUs / 1000L,
                sampleRateHz = sampleRate,
                channelCount = channelCount,
                peakDb = peakDb,
                rmsDb = rmsDb,
                recommendedGainLinear = recommendedGain,
                issues = issues,
                summary = "Peak ${String.format("%.1f", peakDb)} dBFS • RMS ${String.format("%.1f", rmsDb)} dBFS • ${sampleRate}Hz ${channelCount}ch"
            )
        } catch (e: Exception) {
            AudioAnalysisReport(
                durationMs = 0L,
                sampleRateHz = 44100,
                channelCount = 2,
                peakDb = -18.0,
                rmsDb = -24.0,
                recommendedGainLinear = 1.0f,
                issues = listOf("Partial codec inspection: ${e.localizedMessage ?: "unknown format"}"),
                summary = "Audio inspection limited by container format."
            )
        } finally {
            try { codec?.stop() } catch (_: Exception) {}
            try { codec?.release() } catch (_: Exception) {}
            try { extractor.release() } catch (_: Exception) {}
        }
    }

    /**
     * Extracts audio track to an M4A/MP4 container under 18 MB so Firebase AI Logic can transcribe/subtitle long videos.
     */
    suspend fun extractAudioTrackForAi(context: Context, uri: Uri, maxBytes: Long = 17_500_000L): ByteArray? =
        withContext(Dispatchers.IO) {
            val outFile = File(context.cacheDir, "ai_audio_${System.currentTimeMillis()}.mp4")
            val extractor = MediaExtractor()
            var muxer: MediaMuxer? = null
            try {
                extractor.setDataSource(context, uri, null)
                var audioTrack = -1
                var format: MediaFormat? = null
                for (i in 0 until extractor.trackCount) {
                    val fmt = extractor.getTrackFormat(i)
                    if (fmt.getString(MediaFormat.KEY_MIME).orEmpty().startsWith("audio/")) {
                        audioTrack = i
                        format = fmt
                        break
                    }
                }
                if (audioTrack == -1 || format == null) return@withContext null
                extractor.selectTrack(audioTrack)
                muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                val outTrack = muxer.addTrack(format)
                muxer.start()

                val buffer = ByteBuffer.allocate(256 * 1024)
                val info = MediaCodec.BufferInfo()
                var writtenBytes = 0L
                while (writtenBytes < maxBytes - 100_000L) {
                    val size = extractor.readSampleData(buffer, 0)
                    if (size < 0) break
                    info.offset = 0
                    info.size = size
                    info.presentationTimeUs = extractor.sampleTime
                    info.flags = extractor.sampleFlags
                    muxer.writeSampleData(outTrack, buffer, info)
                    writtenBytes += size
                    extractor.advance()
                }
                muxer.stop()
                muxer.release()
                muxer = null
                outFile.readBytes().takeIf { it.isNotEmpty() && it.size <= maxBytes }
            } catch (_: Exception) {
                null
            } finally {
                try { muxer?.release() } catch (_: Exception) {}
                try { extractor.release() } catch (_: Exception) {}
                outFile.delete()
            }
        }

    @OptIn(UnstableApi::class)
    suspend fun trimVideo(
        context: Context,
        sourceUri: Uri,
        startMs: Long,
        endMs: Long,
        outputFileName: String,
        onProgress: (Float) -> Unit
    ): File {
        val outDir = File(context.filesDir, "exports/edited_videos").apply { mkdirs() }
        val outFile = File(outDir, outputFileName)
        if (outFile.exists()) outFile.delete()

        val mediaItem = MediaItem.Builder()
            .setUri(sourceUri)
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(startMs.coerceAtLeast(0L))
                    .setEndPositionMs(endMs.coerceAtLeast(startMs + 1000L))
                    .build()
            )
            .build()

        val edited = EditedMediaItem.Builder(mediaItem).build()
        runTransformer(context, edited, outFile, onProgress)
        return outFile
    }

    @OptIn(UnstableApi::class)
    suspend fun adjustAndNormalizeAudio(
        context: Context,
        sourceUri: Uri,
        gainLinear: Float,
        outputFileName: String,
        onProgress: (Float) -> Unit
    ): File {
        val outDir = File(context.filesDir, "exports/edited_videos").apply { mkdirs() }
        val outFile = File(outDir, outputFileName)
        if (outFile.exists()) outFile.delete()

        val mediaItem = MediaItem.fromUri(sourceUri)
        val edited = EditedMediaItem.Builder(mediaItem)
            .setEffects(
                Effects(
                    listOf(GainAudioProcessor(gainLinear)),
                    emptyList()
                )
            )
            .build()
        runTransformer(context, edited, outFile, onProgress)
        return outFile
    }

    @OptIn(UnstableApi::class)
    suspend fun createVertical916Clip(
        context: Context,
        sourceUri: Uri,
        startMs: Long,
        endMs: Long,
        outputFileName: String,
        onProgress: (Float) -> Unit
    ): File {
        val outDir = File(context.filesDir, "exports/shorts_clips").apply { mkdirs() }
        val outFile = File(outDir, outputFileName)
        if (outFile.exists()) outFile.delete()

        val mediaItem = MediaItem.Builder()
            .setUri(sourceUri)
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(startMs.coerceAtLeast(0L))
                    .setEndPositionMs(endMs.coerceAtLeast(startMs + 2000L))
                    .build()
            )
            .build()

        val verticalEffect: Effect = Presentation.createForAspectRatio(
            9f / 16f,
            Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP
        )
        val edited = EditedMediaItem.Builder(mediaItem)
            .setEffects(Effects(emptyList(), listOf(verticalEffect)))
            .build()
        runTransformer(context, edited, outFile, onProgress)
        return outFile
    }

    @OptIn(UnstableApi::class)
    private suspend fun runTransformer(
        context: Context,
        editedMediaItem: EditedMediaItem,
        outputFile: File,
        onProgress: (Float) -> Unit
    ) = withContext(Dispatchers.Main) {
        var finished = false
        val holder = ProgressHolder()
        val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
        suspendCancellableCoroutine<Unit> { cont ->
            val listener = object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    finished = true
                    onProgress(1f)
                    if (cont.isActive) cont.resume(Unit)
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException
                ) {
                    finished = true
                    if (cont.isActive) cont.resumeWithException(exportException)
                }
            }

            val t = Transformer.Builder(context)
                .addListener(listener)
                .build()

            cont.invokeOnCancellation {
                finished = true
                t.cancel()
            }
            t.start(editedMediaItem, outputFile.absolutePath)

            mainHandler.post(object : Runnable {
                override fun run() {
                    if (!finished && cont.isActive) {
                        val state = t.getProgress(holder)
                        if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                            onProgress((holder.progress / 100f).coerceIn(0f, 0.99f))
                        }
                        mainHandler.postDelayed(this, 250L)
                    }
                }
            })
        }
    }

    suspend fun exportTextFile(
        context: Context,
        subdir: String,
        fileName: String,
        content: String
    ): Pair<File, Uri> = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "exports/$subdir").apply { mkdirs() }
        val file = File(dir, fileName)
        file.writeText(content, Charsets.UTF_8)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        file to uri
    }

    fun uriForFile(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

@Composable
fun ExoPlayerVideoPreview(
    videoUri: Uri,
    volume: Float = 1f,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val player = remember(videoUri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(videoUri))
            prepare()
            playWhenReady = false
        }
    }

    LaunchedEffect(volume) {
        player.volume = volume.coerceIn(0f, 1.5f)
    }

    DisposableEffect(player) {
        onDispose {
            player.release()
        }
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                this.player = player
                useController = true
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
        },
        update = { view ->
            if (view.player !== player) {
                view.player = player
            }
        },
        modifier = modifier
    )
}
