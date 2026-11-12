package com.metrolist.music.utils

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.min

object HighlightShareCard {
    private const val WIDTH = 1080
    private const val HEIGHT = 1920

    /** Renders a 9:16 share card. Artwork is optional; the local, track-seeded gradient is the fallback. */
    fun render(
        title: String,
        artist: String,
        stat: String,
        duration: String,
        eyebrow: String = "YOUR MOST PLAYED",
        artwork: Bitmap? = null,
        seed: String = title + artist,
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val hue = Math.floorMod(seed.hashCode(), 360).toFloat()
        val colors = intArrayOf(
            Color.HSVToColor(floatArrayOf(hue, .72f, .82f)),
            Color.HSVToColor(floatArrayOf((hue + 38f) % 360f, .78f, .56f)),
            Color.HSVToColor(floatArrayOf((hue + 72f) % 360f, .68f, .24f)),
        )
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = LinearGradient(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), colors, floatArrayOf(0f, .52f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), paint)
        paint.shader = null
        paint.color = Color.argb(22, 255, 255, 255)
        canvas.drawCircle(1010f, 250f, 330f, paint)
        canvas.drawCircle(20f, 1500f, 360f, paint)

        val white = Color.WHITE
        paint.color = Color.argb(35, 255, 255, 255)
        canvas.drawRoundRect(RectF(76f, 82f, 1004f, 204f), 61f, 61f, paint)
        paint.color = white
        canvas.drawCircle(145f, 143f, 37f, paint)
        paint.color = colors[0]
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = 42f
        paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        canvas.drawText("S", 145f, 158f, paint)
        paint.color = white
        paint.textAlign = Paint.Align.LEFT
        paint.textSize = 34f
        paint.letterSpacing = .08f
        canvas.drawText("SANZXCY MUSIC", 208f, 156f, paint)
        paint.letterSpacing = 0f

        val artBox = RectF(110f, 270f, 970f, 1130f)
        val cornerRadius = 42f
        val artPath = Path().apply { addRoundRect(artBox, cornerRadius, cornerRadius, Path.Direction.CW) }
        if (artwork != null && artwork.width > 0 && artwork.height > 0) {
            val side = min(artwork.width, artwork.height)
            val left = (artwork.width - side) / 2
            val top = (artwork.height - side) / 2
            canvas.save()
            canvas.clipPath(artPath)
            canvas.drawBitmap(artwork, Rect(left, top, left + side, top + side), artBox, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
            paint.shader = LinearGradient(0f, artBox.top + artBox.height() * .48f, 0f, artBox.bottom, intArrayOf(0x00000000, 0x70000000, 0xA0000000.toInt()), null, Shader.TileMode.CLAMP)
            canvas.drawRect(artBox, paint)
            paint.shader = null
            canvas.restore()
        } else {
            canvas.save()
            canvas.clipPath(artPath)
            paint.shader = LinearGradient(artBox.left, artBox.top, artBox.right, artBox.bottom, colors, null, Shader.TileMode.CLAMP)
            canvas.drawRoundRect(artBox, cornerRadius, cornerRadius, paint)
            paint.shader = null
            paint.color = Color.argb(34, 255, 255, 255)
            canvas.drawCircle(790f, 460f, 260f, paint)
            canvas.drawCircle(300f, 950f, 310f, paint)
            paint.color = Color.WHITE
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = 180f
            paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL)
            canvas.drawText("♪", WIDTH / 2f, 760f, paint)
            paint.textAlign = Paint.Align.LEFT
            canvas.restore()
        }
        paint.color = 0x55ffffff
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        canvas.drawRoundRect(artBox, cornerRadius, cornerRadius, paint)
        paint.style = Paint.Style.FILL

        paint.color = white
        paint.textAlign = Paint.Align.LEFT
        paint.setShadowLayer(8f, 0f, 3f, 0x55000000)
        paint.textSize = 31f
        paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        paint.letterSpacing = .1f
        canvas.drawText(ellipsize(eyebrow, 860f, paint), 110f, 1220f, paint)
        paint.letterSpacing = 0f
        paint.textSize = 76f
        paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        drawWrapped(canvas, title.ifBlank { "Unknown track" }, 110f, 1325f, 860f, paint, 2)
        paint.textSize = 40f
        paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL)
        canvas.drawText(ellipsize(artist.ifBlank { "Unknown artist" }, 860f, paint), 110f, 1510f, paint)
        paint.clearShadowLayer()

        val statBox = RectF(110f, 1580f, 970f, 1780f)
        paint.color = Color.argb(34, 255, 255, 255)
        canvas.drawRoundRect(statBox, 34f, 34f, paint)
        paint.color = Color.argb(65, 255, 255, 255)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        canvas.drawRoundRect(statBox, 34f, 34f, paint)
        paint.style = Paint.Style.FILL
        paint.color = white
        paint.textAlign = Paint.Align.LEFT
        paint.textSize = 37f
        paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        canvas.drawText(ellipsize(stat, 780f, paint), 150f, 1665f, paint)
        paint.alpha = 220
        paint.textSize = 32f
        paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL)
        canvas.drawText(ellipsize(duration, 780f, paint), 150f, 1730f, paint)
        paint.alpha = 255

        paint.color = Color.argb(210, 255, 255, 255)
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = 27f
        paint.letterSpacing = .04f
        canvas.drawText("A YEAR IN MUSIC", WIDTH / 2f, 1860f, paint)
        return bitmap
    }

    private fun ellipsize(value: String, maxWidth: Float, paint: Paint): String =
        TextUtils.ellipsize(value, TextPaint(paint), maxWidth, TextUtils.TruncateAt.END).toString()

    private fun drawWrapped(canvas: Canvas, value: String, x: Float, firstBaseline: Float, maxWidth: Float, paint: Paint, maxLines: Int) {
        val words = value.trim().split(Regex("\\s+")).filter(String::isNotEmpty)
        val lines = mutableListOf<String>()
        var line = ""
        var index = 0
        while (index < words.size && lines.size < maxLines) {
            val candidate = if (line.isEmpty()) words[index] else "$line ${words[index]}"
            if (line.isNotEmpty() && paint.measureText(candidate) > maxWidth) {
                lines += line
                line = ""
            } else {
                line = candidate
                index++
            }
        }
        if (line.isNotEmpty() && lines.size < maxLines) lines += line
        if (lines.isEmpty()) return
        if (index < words.size) lines[lines.lastIndex] = "${lines.last()} ${words.drop(index).joinToString(" ")}"
        lines.forEachIndexed { lineIndex, text ->
            canvas.drawText(ellipsize(text, maxWidth, paint), x, firstBaseline + lineIndex * paint.textSize * 1.12f, paint)
        }
    }

    fun share(context: Context, bitmap: Bitmap, chooserTitle: String) {
        val directory = File(context.cacheDir, "highlight-cards").apply { mkdirs() }
        val file = File(directory, "music-highlight-${java.util.UUID.randomUUID()}.png")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.FileProvider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, chooserTitle))
    }

    /** Loads only HTTPS artwork with tight time and byte limits; returns null for placeholder art. */
    fun loadArtwork(url: String): Bitmap? {
        if (!url.startsWith("https://", ignoreCase = true)) return null
        val connection = (URL(url).openConnection() as? HttpURLConnection) ?: return null
        return try {
            connection.connectTimeout = 4_000
            connection.readTimeout = 5_000
            connection.instanceFollowRedirects = true
            connection.connect()
            if (!connection.url.protocol.equals("https", ignoreCase = true)) return null
            if (connection.responseCode !in 200..299 || connection.contentLengthLong > 5L * 1024 * 1024) return null
            val bytes = ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val buffer = ByteArray(16 * 1024)
                var total = 0
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > 5 * 1024 * 1024) return null
                    bytes.write(buffer, 0, read)
                }
            }
            val raw = bytes.toByteArray()
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(raw, 0, raw.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            val sample = maxOf(1, maxOf(bounds.outWidth, bounds.outHeight) / 1200)
            BitmapFactory.decodeByteArray(raw, 0, raw.size, BitmapFactory.Options().apply { inSampleSize = sample })
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }
}
