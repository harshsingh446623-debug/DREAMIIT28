package com.example.media

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import com.example.data.model.VideoQualityReport
import com.example.ui.viewmodel.DreamiitStudioHelpers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object MediaInspector {
    suspend fun inspectVideo(context: Context, uri: Uri): VideoQualityReport =
        withContext(Dispatchers.IO) {
            var width = 0
            var height = 0
            var durationSec = 0L
            var fps = 30
            var fileSize = 0L
            val fileName = getFileName(context, uri)
            val mimeType = context.contentResolver.getType(uri)

            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst() && sizeIndex != -1) {
                    fileSize = cursor.getLong(sizeIndex)
                }
            }

            val retriever = MediaMetadataRetriever()
            var metadataReadable = false
            try {
                retriever.setDataSource(context, uri)
                width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                    ?.toIntOrNull() ?: 0
                height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                    ?.toIntOrNull() ?: 0
                val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull() ?: 0L
                durationSec = (durationMs / 1000L).coerceAtLeast(0L)
                val captureRate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)
                    ?.toFloatOrNull()?.toInt()
                if (captureRate != null && captureRate > 0) {
                    fps = captureRate
                }
                metadataReadable = true
            } catch (_: Exception) {
                metadataReadable = false
            } finally {
                try {
                    retriever.release()
                } catch (_: Exception) {
                }
            }

            val (validMp4, mp4Error) = if (!metadataReadable && fileSize <= 0L) {
                false to "Selected file is unreadable or corrupted."
            } else {
                DreamiitStudioHelpers.validateMp4Metadata(
                    fileName = fileName,
                    mimeType = mimeType,
                    fileSizeBytes = fileSize,
                    durationSec = durationSec
                )
            }

            val is1080p = width >= 1920 && height >= 1080
            val warning = when {
                !validMp4 -> mp4Error
                width == 0 || height == 0 ->
                    "Source resolution metadata unavailable; server transcoding will inspect stream before output."
                height < 720 ->
                    "Source is ${width}×${height}. Recommended minimum is 720p HD (1280×720) for sharp live streaming."
                !is1080p ->
                    "Source is ${width}×${height} HD. Matches V20.0 720p default CBR live profile cleanly."
                else -> null
            }

            VideoQualityReport(
                width = width,
                height = height,
                fps = fps,
                durationSec = durationSec,
                fileSizeBytes = fileSize,
                isGenuine1080p = is1080p,
                warningMessage = warning,
                isValidMp4 = validMp4,
                validationError = mp4Error
            )
        }

    fun getFileName(context: Context, uri: Uri): String {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && idx != -1) {
                return cursor.getString(idx).orEmpty()
            }
        }
        return "DREAMIIT28_Video.mp4"
    }
}
