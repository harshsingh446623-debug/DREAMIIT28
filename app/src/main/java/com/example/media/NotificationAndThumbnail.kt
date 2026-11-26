package com.example.media

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.R
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

object NotificationHelper {
    private const val CHANNEL_ID = "dreamiit28_studio_alerts"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                "DREAMIIT28 Broadcast & Upload Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Live start/stop, upload status, and streaming health alerts"
            }
            manager.createNotificationChannel(channel)
        }
    }

    fun showSystemNotification(context: Context, id: Int, title: String, body: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }
        ensureChannel(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(id, notification)
    }
}

object ThumbnailRenderer {
    fun renderWithOverlay(
        source: Bitmap,
        headline: String,
        badgeText: String = "DREAMIIT28 • 1080p LIVE"
    ): Bitmap {
        val width = 1280
        val height = 720
        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)

        val scaled = Bitmap.createScaledBitmap(source, width, height, true)
        canvas.drawBitmap(scaled, 0f, 0f, null)

        // Bottom cinematic dark gradient scrim for crisp readable text
        val scrimPaint = Paint().apply {
            shader = LinearGradient(
                0f, height * 0.42f, 0f, height.toFloat(),
                Color.TRANSPARENT, Color.argb(235, 5, 7, 13),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, height * 0.42f, width.toFloat(), height.toFloat(), scrimPaint)

        // Electric blue accent frame
        val borderPaint = Paint().apply {
            color = Color.parseColor("#1E88FF")
            style = Paint.Style.STROKE
            strokeWidth = 10f
            isAntiAlias = true
        }
        canvas.drawRect(5f, 5f, width - 5f, height - 5f, borderPaint)

        // Top-left electric badge
        if (badgeText.isNotBlank()) {
            val badgePaint = Paint().apply {
                color = Color.parseColor("#1E88FF")
                isAntiAlias = true
            }
            val badgeRect = RectF(44f, 40f, 44f + (badgeText.length * 18f) + 48f, 96f)
            canvas.drawRoundRect(badgeRect, 14f, 14f, badgePaint)

            val badgeTextPaint = Paint().apply {
                color = Color.WHITE
                textSize = 26f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                isAntiAlias = true
            }
            canvas.drawText(badgeText.take(36), 68f, 77f, badgeTextPaint)
        }

        // Bold bottom headline (up to 2 lines)
        if (headline.isNotBlank()) {
            val textPaint = Paint().apply {
                color = Color.WHITE
                textSize = 64f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                isAntiAlias = true
                setShadowLayer(12f, 0f, 4f, Color.BLACK)
            }
            val words = headline.trim().split(Regex("\\s+"))
            val line1 = StringBuilder()
            val line2 = StringBuilder()
            for (w in words) {
                if (line1.length + w.length < 24 && line2.isEmpty()) {
                    if (line1.isNotEmpty()) line1.append(" ")
                    line1.append(w)
                } else if (line2.length + w.length < 26) {
                    if (line2.isNotEmpty()) line2.append(" ")
                    line2.append(w)
                }
            }
            val baseY = if (line2.isEmpty()) height - 72f else height - 138f
            canvas.drawText(line1.toString(), 48f, baseY, textPaint)
            if (line2.isNotEmpty()) {
                val accentPaint = Paint(textPaint).apply {
                    color = Color.parseColor("#00D4FF")
                }
                canvas.drawText(line2.toString(), 48f, baseY + 74f, accentPaint)
            }
        }
        return out
    }

    fun toJpegBytes(bitmap: Bitmap, quality: Int = 92): ByteArray {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
        return stream.toByteArray()
    }

    fun saveToCacheFile(context: Context, bitmap: Bitmap, fileName: String): File {
        val dir = File(context.cacheDir, "thumbnails").apply { mkdirs() }
        val file = File(dir, fileName)
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 94, out)
        }
        return file
    }
}
