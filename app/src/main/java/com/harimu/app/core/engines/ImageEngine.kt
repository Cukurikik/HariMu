package com.harimu.app.core.engines

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.util.Base64
import androidx.exifinterface.media.ExifInterface
import com.harimu.app.core.files.FileStorageManager
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

object ImageEngine {

    fun decodeBitmapSafe(context: Context, uri: Uri, maxDim: Int = 2560): Bitmap {
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, boundsOpts)
        }
        val origW = boundsOpts.outWidth.coerceAtLeast(1)
        val origH = boundsOpts.outHeight.coerceAtLeast(1)
        var sample = 1
        while (origW / sample > maxDim || origH / sample > maxDim) {
            sample *= 2
        }
        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val raw = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, decodeOpts)
        } ?: error("Gagal membaca gambar dari URI")

        val orientation = context.contentResolver.openInputStream(uri)?.use {
            runCatching {
                ExifInterface(it).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL

        return applyExifOrientation(raw, orientation)
    }

    private fun applyExifOrientation(bmp: Bitmap, orientation: Int): Bitmap {
        val m = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
            else -> return bmp
        }
        val rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
        if (rotated != bmp) bmp.recycle()
        return rotated
    }

    fun saveBitmap(
        bitmap: Bitmap,
        dest: File,
        format: String = "PNG",
        quality: Int = 92
    ): File {
        val upper = format.uppercase()
        if (upper == "PDF") {
            val doc = PdfDocument()
            val info = PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, 1).create()
            val page = doc.startPage(info)
            page.canvas.drawBitmap(bitmap, 0f, 0f, null)
            doc.finishPage(page)
            FileOutputStream(dest).use { doc.writeTo(it) }
            doc.close()
            return dest
        }
        val compressFmt = when (upper) {
            "JPG", "JPEG" -> Bitmap.CompressFormat.JPEG
            "WEBP" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Bitmap.CompressFormat.WEBP_LOSSY
            } else {
                @Suppress("DEPRECATION")
                Bitmap.CompressFormat.WEBP
            }
            else -> Bitmap.CompressFormat.PNG
        }
        FileOutputStream(dest).use { out ->
            bitmap.compress(compressFmt, quality.coerceIn(10, 100), out)
        }
        return dest
    }

    fun cropAspect(src: Bitmap, ratioLabel: String): Bitmap {
        val targetRatio = when (ratioLabel) {
            "1:1" -> 1f
            "16:9" -> 16f / 9f
            "9:16" -> 9f / 16f
            "4:3" -> 4f / 3f
            "3:4" -> 3f / 4f
            else -> src.width.toFloat() / src.height.toFloat()
        }
        val srcRatio = src.width.toFloat() / src.height.toFloat()
        val cropW: Int
        val cropH: Int
        if (srcRatio > targetRatio) {
            cropH = src.height
            cropW = (cropH * targetRatio).roundToInt().coerceIn(1, src.width)
        } else {
            cropW = src.width
            cropH = (cropW / targetRatio).roundToInt().coerceIn(1, src.height)
        }
        val x = ((src.width - cropW) / 2).coerceAtLeast(0)
        val y = ((src.height - cropH) / 2).coerceAtLeast(0)
        return Bitmap.createBitmap(src, x, y, cropW, cropH)
    }

    fun resizeBitmap(src: Bitmap, scalePercent: Int, maxEdge: Int): Bitmap {
        val targetW: Int
        val targetH: Int
        if (maxEdge > 0) {
            val scale = min(maxEdge.toFloat() / src.width, maxEdge.toFloat() / src.height).coerceAtMost(2f)
            targetW = (src.width * scale).roundToInt().coerceAtLeast(1)
            targetH = (src.height * scale).roundToInt().coerceAtLeast(1)
        } else {
            val factor = (scalePercent / 100f).coerceIn(0.1f, 2.0f)
            targetW = (src.width * factor).roundToInt().coerceAtLeast(1)
            targetH = (src.height * factor).roundToInt().coerceAtLeast(1)
        }
        return Bitmap.createScaledBitmap(src, targetW, targetH, true)
    }

    fun rotateBitmap(src: Bitmap, degrees: Float): Bitmap {
        val m = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
    }

    fun flipBitmap(src: Bitmap, horizontal: Boolean, vertical: Boolean): Bitmap {
        val sx = if (horizontal) -1f else 1f
        val sy = if (vertical) -1f else 1f
        val m = Matrix().apply { postScale(sx, sy, src.width / 2f, src.height / 2f) }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
    }

    fun straightenBitmap(src: Bitmap, angleDeg: Float): Bitmap {
        val rotated = rotateBitmap(src, angleDeg)
        val rad = Math.toRadians(abs(angleDeg).toDouble())
        val scale = (cos(rad) + sin(rad)).toFloat().coerceAtLeast(1f)
        val w = (src.width / scale).roundToInt().coerceIn(1, rotated.width)
        val h = (src.height / scale).roundToInt().coerceIn(1, rotated.height)
        val x = ((rotated.width - w) / 2).coerceAtLeast(0)
        val y = ((rotated.height - h) / 2).coerceAtLeast(0)
        val cropped = Bitmap.createBitmap(rotated, x, y, w, h)
        if (cropped != rotated) rotated.recycle()
        return cropped
    }

    fun expandCanvas(src: Bitmap, targetAspect: String, bgHex: String): Bitmap {
        val ratio = when (targetAspect) {
            "1:1" -> 1f
            "16:9" -> 16f / 9f
            "9:16" -> 9f / 16f
            "4:5" -> 4f / 5f
            else -> 1f
        }
        val outW: Int
        val outH: Int
        if (src.width.toFloat() / src.height.toFloat() > ratio) {
            outW = src.width
            outH = (outW / ratio).roundToInt().coerceAtLeast(src.height)
        } else {
            outH = src.height
            outW = (outH * ratio).roundToInt().coerceAtLeast(src.width)
        }
        val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(parseColorSafe(bgHex, Color.parseColor("#13161F")))
        canvas.drawBitmap(src, (outW - src.width) / 2f, (outH - src.height) / 2f, null)
        return out
    }

    fun applyFilter(
        src: Bitmap,
        preset: String,
        brightness: Float = 0f,
        contrast: Float = 1f,
        saturation: Float = 1f
    ): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val cm = ColorMatrix()

        when (preset.lowercase()) {
            "grayscale" -> cm.setSaturation(0f)
            "sepia" -> {
                cm.setSaturation(0f)
                val sepia = ColorMatrix().apply { setScale(1f, 0.92f, 0.78f, 1f) }
                cm.postConcat(sepia)
            }
            "invert" -> cm.set(
                floatArrayOf(
                    -1f, 0f, 0f, 0f, 255f,
                    0f, -1f, 0f, 0f, 255f,
                    0f, 0f, -1f, 0f, 255f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            "warm" -> cm.postConcat(ColorMatrix().apply { setScale(1.1f, 1.02f, 0.88f, 1f) })
            "cool" -> cm.postConcat(ColorMatrix().apply { setScale(0.9f, 1.0f, 1.14f, 1f) })
            "vintage" -> {
                cm.setSaturation(0.7f)
                cm.postConcat(
                    ColorMatrix(
                        floatArrayOf(
                            1.08f, 0f, 0f, 0f, 10f,
                            0f, 1.02f, 0f, 0f, 6f,
                            0f, 0f, 0.88f, 0f, 14f,
                            0f, 0f, 0f, 1f, 0f
                        )
                    )
                )
            }
            else -> cm.setSaturation(saturation)
        }

        if (contrast != 1f || brightness != 0f) {
            val t = (1f - contrast) * 128f + brightness
            cm.postConcat(
                ColorMatrix(
                    floatArrayOf(
                        contrast, 0f, 0f, 0f, t,
                        0f, contrast, 0f, 0f, t,
                        0f, 0f, contrast, 0f, t,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            )
        }

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(cm)
        }
        canvas.drawBitmap(src, 0f, 0f, paint)
        return out
    }

    fun addWatermark(
        src: Bitmap,
        text: String,
        opacityPercent: Int,
        position: String,
        tiled: Boolean
    ): Bitmap {
        val out = src.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)
        val alpha = ((opacityPercent.coerceIn(5, 100) / 100f) * 255).roundToInt()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            this.alpha = alpha
            textSize = (min(src.width, src.height) * 0.055f).coerceAtLeast(24f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setShadowLayer(4f, 2f, 2f, Color.argb(alpha, 0, 0, 0))
        }
        if (tiled) {
            canvas.save()
            canvas.rotate(-25f, src.width / 2f, src.height / 2f)
            val stepX = max(paint.measureText(text) + 120f, 220f)
            val stepY = paint.textSize * 3.5f
            var y = -src.height.toFloat()
            while (y < src.height * 2f) {
                var x = -src.width.toFloat()
                while (x < src.width * 2f) {
                    canvas.drawText(text, x, y, paint)
                    x += stepX
                }
                y += stepY
            }
            canvas.restore()
        } else {
            val bounds = Rect()
            paint.getTextBounds(text, 0, text.length, bounds)
            val pad = max(24f, min(src.width, src.height) * 0.04f)
            val x = when (position) {
                "Kiri Atas", "Kiri Bawah" -> pad
                "Tengah" -> (src.width - bounds.width()) / 2f
                else -> (src.width - bounds.width() - pad)
            }
            val y = when (position) {
                "Kiri Atas", "Kanan Atas" -> pad + bounds.height()
                "Tengah" -> (src.height + bounds.height()) / 2f
                else -> (src.height - pad)
            }
            canvas.drawText(text, x, y, paint)
        }
        return out
    }

    fun addBorderFrame(src: Bitmap, paddingPercent: Int, frameHex: String, polaroidBottom: Boolean): Bitmap {
        val pad = ((min(src.width, src.height) * paddingPercent) / 100f).roundToInt().coerceAtLeast(16)
        val extraBottom = if (polaroidBottom) pad * 3 else pad
        val out = Bitmap.createBitmap(src.width + pad * 2, src.height + pad + extraBottom, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(parseColorSafe(frameHex, Color.WHITE))
        canvas.drawBitmap(src, pad.toFloat(), pad.toFloat(), null)
        return out
    }

    fun applyGradientOverlay(src: Bitmap, startHex: String, endHex: String, opacityPercent: Int): Bitmap {
        val out = src.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)
        val c1 = parseColorSafe(startHex, Color.parseColor("#FF6B2C"))
        val c2 = parseColorSafe(endHex, Color.parseColor("#00BFA5"))
        val alpha = ((opacityPercent.coerceIn(5, 95) / 100f) * 255).roundToInt()
        val paint = Paint().apply {
            shader = LinearGradient(0f, 0f, out.width.toFloat(), out.height.toFloat(), c1, c2, Shader.TileMode.CLAMP)
            this.alpha = alpha
        }
        canvas.drawRect(0f, 0f, out.width.toFloat(), out.height.toFloat(), paint)
        return out
    }

    fun createReflectionEffect(src: Bitmap, mode: String): Bitmap {
        return if (mode == "Cermin Horizontal") {
            val halfW = src.width / 2
            val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(out)
            val leftPart = Bitmap.createBitmap(src, 0, 0, halfW, src.height)
            val m = Matrix().apply { postScale(-1f, 1f) }
            val mirrored = Bitmap.createBitmap(leftPart, 0, 0, leftPart.width, leftPart.height, m, true)
            canvas.drawBitmap(leftPart, 0f, 0f, null)
            canvas.drawBitmap(mirrored, halfW.toFloat(), 0f, null)
            leftPart.recycle()
            mirrored.recycle()
            out
        } else {
            val reflH = (src.height * 0.45f).roundToInt().coerceAtLeast(1)
            val out = Bitmap.createBitmap(src.width, src.height + reflH, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(out)
            canvas.drawColor(Color.parseColor("#0F1219"))
            canvas.drawBitmap(src, 0f, 0f, null)
            val m = Matrix().apply { postScale(1f, -1f) }
            val flipped = Bitmap.createBitmap(src, 0, src.height - reflH, src.width, reflH, m, true)
            val reflPaint = Paint().apply { alpha = 120 }
            canvas.drawBitmap(flipped, 0f, src.height.toFloat(), reflPaint)
            flipped.recycle()
            out
        }
    }

    fun createTypographyPoster(text: String, themeStyle: String, width: Int = 1080, height: Int = 1350): Bitmap {
        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val (c1, c2, txtColor) = when (themeStyle) {
            "Sunset Amber" -> Triple(Color.parseColor("#1D1128"), Color.parseColor("#FF6B2C"), Color.WHITE)
            "Teal Obsidian" -> Triple(Color.parseColor("#0C121D"), Color.parseColor("#007D6E"), Color.WHITE)
            "Editorial Paper" -> Triple(Color.parseColor("#FAF6EE"), Color.parseColor("#EAE1D0"), Color.parseColor("#18181B"))
            else -> Triple(Color.parseColor("#111827"), Color.parseColor("#1E293B"), Color.WHITE)
        }
        val bgPaint = Paint().apply {
            shader = LinearGradient(0f, 0f, width.toFloat(), height.toFloat(), c1, c2, Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        val borderPaint = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 6f
            color = txtColor
            alpha = 60
        }
        canvas.drawRect(64f, 64f, width - 64f, height - 64f, borderPaint)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = txtColor
            textSize = 74f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val words = text.ifBlank { "HariMu Studio" }.split(Regex("\\s+"))
        val lines = mutableListOf<String>()
        var current = StringBuilder()
        val maxLineW = width - 220f
        for (w in words) {
            val candidate = if (current.isEmpty()) w else "$current $w"
            if (textPaint.measureText(candidate) <= maxLineW) {
                current = StringBuilder(candidate)
            } else {
                if (current.isNotEmpty()) lines.add(current.toString())
                current = StringBuilder(w)
            }
        }
        if (current.isNotEmpty()) lines.add(current.toString())

        val lineH = textPaint.textSize * 1.35f
        var y = (height - lines.size * lineH) / 2f + textPaint.textSize * 0.8f
        for (line in lines) {
            canvas.drawText(line, width / 2f, y, textPaint)
            y += lineH
        }
        return out
    }

    fun createSpriteSheet(bitmaps: List<Bitmap>, columns: Int): Pair<Bitmap, String> {
        val cols = columns.coerceIn(1, bitmaps.size.coerceAtLeast(1))
        val cellW = bitmaps.maxOf { it.width }.coerceAtMost(512)
        val cellH = bitmaps.maxOf { it.height }.coerceAtMost(512)
        val rows = ((bitmaps.size + cols - 1) / cols).coerceAtLeast(1)
        val sheet = Bitmap.createBitmap(cols * cellW, rows * cellH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(sheet)
        val atlas = StringBuilder("{\n  \"cellWidth\": $cellW,\n  \"cellHeight\": $cellH,\n  \"frames\": [\n")
        bitmaps.forEachIndexed { idx, bmp ->
            val col = idx % cols
            val row = idx / cols
            val x = col * cellW
            val y = row * cellH
            val scaled = Bitmap.createScaledBitmap(bmp, cellW, cellH, true)
            canvas.drawBitmap(scaled, x.toFloat(), y.toFloat(), null)
            scaled.recycle()
            atlas.append("    {\"index\": $idx, \"x\": $x, \"y\": $y, \"w\": $cellW, \"h\": $cellH}")
            if (idx < bitmaps.lastIndex) atlas.append(",")
            atlas.append("\n")
        }
        atlas.append("  ]\n}")
        return sheet to atlas.toString()
    }

    fun generateIconPackZip(src: Bitmap, destZip: File): File {
        val sizes = listOf(
            "mipmap-mdpi/ic_launcher.png" to 48,
            "mipmap-hdpi/ic_launcher.png" to 72,
            "mipmap-xhdpi/ic_launcher.png" to 96,
            "mipmap-xxhdpi/ic_launcher.png" to 144,
            "mipmap-xxxhdpi/ic_launcher.png" to 192,
            "playstore-icon-512.png" to 512,
            "favicon-32.png" to 32
        )
        ZipOutputStream(FileOutputStream(destZip)).use { zos ->
            for ((path, size) in sizes) {
                val scaled = Bitmap.createScaledBitmap(src, size, size, true)
                zos.putNextEntry(ZipEntry(path))
                scaled.compress(Bitmap.CompressFormat.PNG, 100, zos)
                zos.closeEntry()
                scaled.recycle()
            }
        }
        return destZip
    }

    fun extractKMeansPalette(src: Bitmap, colorCount: Int): Pair<List<Int>, Bitmap> {
        val k = colorCount.coerceIn(3, 8)
        val thumb = Bitmap.createScaledBitmap(src, 96, 96, true)
        val pixels = IntArray(thumb.width * thumb.height)
        thumb.getPixels(pixels, 0, thumb.width, 0, 0, thumb.width, thumb.height)
        thumb.recycle()

        val centroids = ImageGeometryMath.kMeansPalette(pixels, k)

        val cardW = 1080
        val cardH = 420
        val card = Bitmap.createBitmap(cardW, cardH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(card)
        canvas.drawColor(Color.parseColor("#11141C"))
        val swatchW = cardW.toFloat() / centroids.size.coerceAtLeast(1)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 30f
            typeface = Typeface.MONOSPACE
            textAlign = Paint.Align.CENTER
        }
        centroids.forEachIndexed { idx, color ->
            paint.color = color
            val left = idx * swatchW
            canvas.drawRect(left, 0f, left + swatchW, cardH - 90f, paint)
            val hex = String.format("#%06X", 0xFFFFFF and color)
            canvas.drawText(hex, left + swatchW / 2f, cardH - 35f, labelPaint)
        }
        return centroids to card
    }

    fun inspectExif(context: Context, uri: Uri, storage: FileStorageManager): String {
        val name = storage.getDisplayName(uri) ?: "image"
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        val exifText = context.contentResolver.openInputStream(uri)?.use { stream ->
            val exif = ExifInterface(stream)
            val tags = listOf(
                "Make" to ExifInterface.TAG_MAKE,
                "Model" to ExifInterface.TAG_MODEL,
                "DateTime" to ExifInterface.TAG_DATETIME,
                "Exposure" to ExifInterface.TAG_EXPOSURE_TIME,
                "F-Number" to ExifInterface.TAG_F_NUMBER,
                "ISO" to ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY,
                "FocalLength" to ExifInterface.TAG_FOCAL_LENGTH,
                "GPS Lat" to ExifInterface.TAG_GPS_LATITUDE,
                "GPS Lon" to ExifInterface.TAG_GPS_LONGITUDE,
                "Software" to ExifInterface.TAG_SOFTWARE
            )
            tags.mapNotNull { (label, tag) ->
                exif.getAttribute(tag)?.let { "$label: $it" }
            }.joinToString("\n").ifBlank { "(Tidak ada tag EXIF sensitif terdeteksi)" }
        } ?: "(EXIF tidak terbaca)"

        val mp = (opts.outWidth.toLong() * opts.outHeight.toLong()) / 1_000_000.0
        return buildString {
            appendLine("File: $name")
            appendLine("Resolusi: ${opts.outWidth} × ${opts.outHeight} (${String.format("%.2f", mp)} MP)")
            appendLine("Ukuran Asli: ${storage.formatBytes(bytes.size.toLong())}")
            appendLine("MIME: ${opts.outMimeType ?: "unknown"}")
            appendLine("--- Metadata EXIF ---")
            appendLine(exifText)
        }
    }

    fun encodeToBase64(context: Context, uri: Uri, includeDataUri: Boolean): String {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("Gagal membaca gambar")
        val mime = context.contentResolver.getType(uri) ?: "image/png"
        val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
        return if (includeDataUri) "data:$mime;base64,$b64" else b64
    }

    fun decodeFromBase64(rawText: String, dest: File): File {
        val clean = rawText.trim().substringAfter("base64,", rawText.trim())
        val bytes = Base64.decode(clean, Base64.DEFAULT)
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: error("Data Base64 bukan gambar valid")
        saveBitmap(bmp, dest, "PNG", 100)
        bmp.recycle()
        return dest
    }

    fun parseColorSafe(hex: String, fallback: Int): Int = runCatching {
        val clean = hex.trim().let { if (it.startsWith("#")) it else "#$it" }
        Color.parseColor(clean)
    }.getOrDefault(fallback)
}
