package com.harimu.app.core.engines

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

/**
 * Memory-conscious image IO for Image Forge: bounds-only inspection, region decode for
 * crops, full decode with a pixel budget, and re-encoding that keeps EXIF/ICC where the
 * platform allows it. Limits are documented in `docs/DECISIONS.md` §12.
 */
object ImageIO {

    /** Full bitmaps are only decoded below this many pixels (~48 MP, 192 MB ARGB). */
    const val MAX_FULL_DECODE_PIXELS: Long = 48_000_000L

    private val exifCopyTags = listOf(
        ExifInterface.TAG_MAKE,
        ExifInterface.TAG_MODEL,
        ExifInterface.TAG_DATETIME,
        ExifInterface.TAG_DATETIME_ORIGINAL,
        ExifInterface.TAG_EXPOSURE_TIME,
        ExifInterface.TAG_F_NUMBER,
        ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY,
        ExifInterface.TAG_FOCAL_LENGTH,
        ExifInterface.TAG_LENS_MODEL,
        ExifInterface.TAG_FLASH,
        ExifInterface.TAG_WHITE_BALANCE,
        ExifInterface.TAG_SCENE_CAPTURE_TYPE,
        ExifInterface.TAG_SOFTWARE,
        ExifInterface.TAG_ORIENTATION,
        ExifInterface.TAG_COLOR_SPACE,
        ExifInterface.TAG_IMAGE_DESCRIPTION,
        ExifInterface.TAG_ARTIST,
        ExifInterface.TAG_COPYRIGHT
    )

    private val exifReportTags = listOf(
        "Make" to ExifInterface.TAG_MAKE,
        "Model" to ExifInterface.TAG_MODEL,
        "DateTime" to ExifInterface.TAG_DATETIME,
        "DateTimeOriginal" to ExifInterface.TAG_DATETIME_ORIGINAL,
        "ExposureTime" to ExifInterface.TAG_EXPOSURE_TIME,
        "FNumber" to ExifInterface.TAG_F_NUMBER,
        "ISO" to ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY,
        "FocalLength" to ExifInterface.TAG_FOCAL_LENGTH,
        "LensModel" to ExifInterface.TAG_LENS_MODEL,
        "Flash" to ExifInterface.TAG_FLASH,
        "WhiteBalance" to ExifInterface.TAG_WHITE_BALANCE,
        "Orientation" to ExifInterface.TAG_ORIENTATION,
        "ColorSpace" to ExifInterface.TAG_COLOR_SPACE,
        "Software" to ExifInterface.TAG_SOFTWARE,
        "Artist" to ExifInterface.TAG_ARTIST,
        "Copyright" to ExifInterface.TAG_COPYRIGHT,
        "ImageDescription" to ExifInterface.TAG_IMAGE_DESCRIPTION,
        "GPSLatitude" to ExifInterface.TAG_GPS_LATITUDE,
        "GPSLongitude" to ExifInterface.TAG_GPS_LONGITUDE,
        "GPSAltitude" to ExifInterface.TAG_GPS_ALTITUDE,
        "GPSTimeStamp" to ExifInterface.TAG_GPS_TIMESTAMP
    )

    data class SourceInfo(
        val width: Int,
        val height: Int,
        val displayWidth: Int,
        val displayHeight: Int,
        val orientation: Int,
        val mimeType: String?,
        val formatLabel: String,
        val byteSize: Long
    ) {
        val megapixels: Double get() = width.toLong() * height / 1_000_000.0
        val wasRotated: Boolean get() = orientation == 5 || orientation == 6 || orientation == 7 || orientation == 8
    }

    fun readSourceInfo(context: Context, uri: Uri, displayName: String? = null): SourceInfo {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        val w = bounds.outWidth.coerceAtLeast(1)
        val h = bounds.outHeight.coerceAtLeast(1)
        val orientation = runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val swapped = orientation == 5 || orientation == 6 || orientation == 7 || orientation == 8
        val name = displayName ?: uri.lastPathSegment.orEmpty()
        val ext = name.substringAfterLast('.', "")
        val entry = ImageFormatMatrix.forExtension(ext)
        val byteSize = runCatching {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length }
        }.getOrNull()?.takeIf { it >= 0 } ?: -1L
        return SourceInfo(
            width = w,
            height = h,
            displayWidth = if (swapped) h else w,
            displayHeight = if (swapped) w else h,
            orientation = orientation,
            mimeType = bounds.outMimeType ?: context.contentResolver.getType(uri),
            formatLabel = entry?.id ?: ext.uppercase().ifBlank { "UNKNOWN" },
            byteSize = byteSize
        )
    }

    fun decodePreview(context: Context, uri: Uri, maxDim: Int = 2048): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        val w = bounds.outWidth.coerceAtLeast(1)
        val h = bounds.outHeight.coerceAtLeast(1)
        var sample = 1
        while (w / sample > maxDim || h / sample > maxDim) sample *= 2
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val raw = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: error("Gambar tidak dapat dibaca. Format mungkin tidak didukung perangkat ini.")
        val orientation = readOrientation(context, uri)
        return applyOrientation(raw, orientation)
    }

    /** Full-resolution decode with an explicit pixel budget instead of silent downscaling. */
    fun decodeFullGuarded(
        context: Context,
        uri: Uri,
        maxPixels: Long = MAX_FULL_DECODE_PIXELS
    ): Bitmap {
        val info = readSourceInfo(context, uri)
        val pixels = info.width.toLong() * info.height
        if (pixels > maxPixels) {
            throw IllegalStateException(
                "Gambar ${info.width}×${info.height} (${formatMp(info.megapixels)}) melebihi batas " +
                    "${formatMp(maxPixels / 1_000_000.0)} untuk operasi resolusi penuh. " +
                    "Kecilkan dulu lewat IMG-02 atau potong area yang dibutuhkan."
            )
        }
        val options = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
        val raw = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: error("Gambar tidak dapat didekode pada resolusi penuh (${info.formatLabel}).")
        return applyOrientation(raw, info.orientation)
    }

    /**
     * Decodes only [displayRect] (coordinates after EXIF rotation) at full source resolution,
     * so a 1:1 crop of a 12 MP photo never materialises the whole bitmap.
     */
    fun decodeRegionFullRes(
        context: Context,
        uri: Uri,
        displayRect: PixelRect,
        orientation: Int
    ): Bitmap {
        val info = readSourceInfo(context, uri)
        val sourceRegion = ImageGeometryMath.cropSourceRegion(
            info.width,
            info.height,
            displayRect,
            orientation
        )
        if (sourceRegion.area > MAX_FULL_DECODE_PIXELS) {
            throw IllegalStateException(
                "Area crop ${sourceRegion.width}×${sourceRegion.height} terlalu besar untuk region decode."
            )
        }
        val options = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
        val region = context.contentResolver.openInputStream(uri)?.use { stream ->
            @Suppress("DEPRECATION")
            val decoder = BitmapRegionDecoder.newInstance(stream, false)
            try {
                decoder?.decodeRegion(
                    Rect(
                        sourceRegion.left,
                        sourceRegion.top,
                        sourceRegion.right,
                        sourceRegion.bottom
                    ),
                    options
                )
            } finally {
                decoder?.recycle()
            }
        }
        // Region decoding is not available for every container; fall back to a guarded
        // full decode + crop instead of failing the tool.
        val crop = region ?: run {
            val full = decodeFullGuarded(context, uri)
            val r = displayRect.clampedTo(full.width, full.height)
            val piece = Bitmap.createBitmap(full, r.left, r.top, r.width, r.height)
            if (piece != full) full.recycle()
            piece
        }
        return applyOrientation(crop, orientation)
    }

    fun readOrientation(context: Context, uri: Uri): Int = runCatching {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            ExifInterface(stream).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        } ?: ExifInterface.ORIENTATION_NORMAL
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

    fun applyOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> matrix.setValues(
                floatArrayOf(0f, 1f, 0f, 1f, 0f, 0f, 0f, 0f, 1f)
            )
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> matrix.setValues(
                floatArrayOf(0f, -1f, 0f, -1f, 0f, 0f, 0f, 0f, 1f)
            )
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(270f)
            else -> return bitmap
        }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated != bitmap) bitmap.recycle()
        return rotated
    }

    // ------------------------------------------------------------- encoding

    fun encode(bitmap: Bitmap, dest: File, formatLabel: String, quality: Int): File {
        val entry = ImageFormatMatrix.requireEncodable(formatLabel)
        val q = quality.coerceIn(1, 100)
        if (entry.id == "PDF") {
            writePdf(bitmap, dest)
            return dest
        }
        val target = if (entry.id == "JPG" && bitmap.hasAlpha()) flattenOnWhite(bitmap) else bitmap
        val compressFormat = when (entry.id) {
            "JPG" -> Bitmap.CompressFormat.JPEG
            "WEBP" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Bitmap.CompressFormat.WEBP_LOSSY
            } else {
                @Suppress("DEPRECATION")
                Bitmap.CompressFormat.WEBP
            }
            else -> Bitmap.CompressFormat.PNG
        }
        FileOutputStream(dest).use { out ->
            target.compress(compressFormat, if (entry.id == "PNG") 100 else q, out)
        }
        if (target != bitmap) target.recycle()
        return dest
    }

    fun encodeToBytes(bitmap: Bitmap, formatLabel: String, quality: Int): ByteArray {
        val entry = ImageFormatMatrix.requireEncodable(formatLabel)
        if (entry.id == "PDF") {
            val temp = File.createTempFile("harimu_pdf_", ".pdf")
            try {
                writePdf(bitmap, temp)
                return temp.readBytes()
            } finally {
                temp.delete()
            }
        }
        val target = if (entry.id == "JPG" && bitmap.hasAlpha()) flattenOnWhite(bitmap) else bitmap
        val compressFormat = when (entry.id) {
            "JPG" -> Bitmap.CompressFormat.JPEG
            "WEBP" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Bitmap.CompressFormat.WEBP_LOSSY
            } else {
                @Suppress("DEPRECATION")
                Bitmap.CompressFormat.WEBP
            }
            else -> Bitmap.CompressFormat.PNG
        }
        val stream = ByteArrayOutputStream()
        target.compress(compressFormat, if (entry.id == "PNG") 100 else quality.coerceIn(1, 100), stream)
        if (target != bitmap) target.recycle()
        return stream.toByteArray()
    }

    private fun writePdf(bitmap: Bitmap, dest: File) {
        val document = PdfDocument()
        val info = PdfDocument.PageInfo.Builder(
            bitmap.width.coerceAtLeast(1),
            bitmap.height.coerceAtLeast(1),
            1
        ).create()
        val page = document.startPage(info)
        page.canvas.drawBitmap(bitmap, 0f, 0f, null)
        document.finishPage(page)
        FileOutputStream(dest).use { document.writeTo(it) }
        document.close()
    }

    private fun flattenOnWhite(source: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(source, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
        return out
    }

    // ------------------------------------------------------------ EXIF / ICC

    fun exifReport(context: Context, uri: Uri): List<Pair<String, String>> = runCatching {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val exif = ExifInterface(stream)
            exifReportTags.mapNotNull { (label, tag) ->
                exif.getAttribute(tag)?.let { label to it }
            }
        } ?: emptyList()
    }.getOrDefault(emptyList())

    /** Copies camera metadata from [sourceUri] into an already-written [dest] file. */
    fun copyExif(context: Context, sourceUri: Uri, dest: File, keepGps: Boolean = false): Int {
        return runCatching {
            val destination = ExifInterface(dest.absolutePath)
            var copied = 0
            context.contentResolver.openInputStream(sourceUri)?.use { stream ->
                val source = ExifInterface(stream)
                exifCopyTags.forEach { tag ->
                    if (tag == ExifInterface.TAG_GPS_LATITUDE || tag == ExifInterface.TAG_GPS_LONGITUDE) return@forEach
                    source.getAttribute(tag)?.let { value ->
                        destination.setAttribute(tag, value)
                        copied++
                    }
                }
                if (keepGps) {
                    listOf(
                        ExifInterface.TAG_GPS_LATITUDE,
                        ExifInterface.TAG_GPS_LATITUDE_REF,
                        ExifInterface.TAG_GPS_LONGITUDE,
                        ExifInterface.TAG_GPS_LONGITUDE_REF,
                        ExifInterface.TAG_GPS_ALTITUDE,
                        ExifInterface.TAG_GPS_ALTITUDE_REF,
                        ExifInterface.TAG_GPS_TIMESTAMP,
                        ExifInterface.TAG_GPS_DATESTAMP
                    ).forEach { tag ->
                        source.getAttribute(tag)?.let { value ->
                            destination.setAttribute(tag, value)
                            copied++
                        }
                    }
                } else {
                    destination.setAttribute(ExifInterface.TAG_GPS_LATITUDE, null)
                    destination.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, null)
                    destination.setAttribute(ExifInterface.TAG_GPS_TIMESTAMP, null)
                }
            }
            // Orientation was baked into the pixels, so the copy must declare normal.
            destination.setAttribute(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL.toString()
            )
            destination.saveAttributes()
            copied
        }.getOrDefault(0)
    }

    /** Removes EXIF/GPS (and for JPEG the APP1 segment) while leaving pixel data untouched. */
    fun stripMetadata(file: File): Boolean {
        var cleared = false
        val isJpeg = runCatching {
            file.inputStream().use { stream ->
                val head = ByteArray(2)
                stream.read(head) == 2 && (head[0].toInt() and 0xFF) == 0xFF && (head[1].toInt() and 0xFF) == 0xD8
            }
        }.getOrDefault(false)
        if (isJpeg) {
            cleared = runCatching { stripJpegApp1(file) }.getOrDefault(false)
        }
        val attributes = runCatching { ExifInterface(file.absolutePath) }.getOrNull()
        if (attributes != null) {
            runCatching {
                listOf(
                    ExifInterface.TAG_MAKE,
                    ExifInterface.TAG_MODEL,
                    ExifInterface.TAG_DATETIME,
                    ExifInterface.TAG_DATETIME_ORIGINAL,
                    ExifInterface.TAG_EXPOSURE_TIME,
                    ExifInterface.TAG_F_NUMBER,
                    ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY,
                    ExifInterface.TAG_FOCAL_LENGTH,
                    ExifInterface.TAG_LENS_MODEL,
                    ExifInterface.TAG_GPS_LATITUDE,
                    ExifInterface.TAG_GPS_LATITUDE_REF,
                    ExifInterface.TAG_GPS_LONGITUDE,
                    ExifInterface.TAG_GPS_LONGITUDE_REF,
                    ExifInterface.TAG_GPS_ALTITUDE,
                    ExifInterface.TAG_GPS_TIMESTAMP,
                    ExifInterface.TAG_SOFTWARE,
                    ExifInterface.TAG_ARTIST,
                    ExifInterface.TAG_COPYRIGHT,
                    ExifInterface.TAG_IMAGE_DESCRIPTION
                ).forEach { tag -> attributes.setAttribute(tag, null) }
                attributes.saveAttributes()
                cleared = true
            }
        }
        return cleared
    }

    fun writeExifAttributes(
        file: File,
        artist: String?,
        copyright: String?,
        description: String?,
        removeGps: Boolean
    ): Boolean = runCatching {
        val exif = ExifInterface(file.absolutePath)
        artist?.let { exif.setAttribute(ExifInterface.TAG_ARTIST, it) }
        copyright?.let { exif.setAttribute(ExifInterface.TAG_COPYRIGHT, it) }
        description?.let { exif.setAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION, it) }
        if (removeGps) {
            listOf(
                ExifInterface.TAG_GPS_LATITUDE,
                ExifInterface.TAG_GPS_LATITUDE_REF,
                ExifInterface.TAG_GPS_LONGITUDE,
                ExifInterface.TAG_GPS_LONGITUDE_REF,
                ExifInterface.TAG_GPS_ALTITUDE,
                ExifInterface.TAG_GPS_ALTITUDE_REF,
                ExifInterface.TAG_GPS_TIMESTAMP,
                ExifInterface.TAG_GPS_DATESTAMP
            ).forEach { exif.setAttribute(it, null) }
        }
        exif.saveAttributes()
        true
    }.getOrDefault(false)

    /** Reads the ICC profile embedded in a JPEG (`ICC_PROFILE` APP2 segments). */
    fun readJpegIccProfile(context: Context, uri: Uri): ByteArray? = runCatching {
        val bytes = context.contentResolver.openInputStream(uri)?.use { stream ->
            val buffer = ByteArrayOutputStream()
            val chunk = ByteArray(64 * 1024)
            var total = 0
            while (total < 4 * 1024 * 1024) {
                val read = stream.read(chunk)
                if (read <= 0) break
                buffer.write(chunk, 0, read)
                total += read
            }
            buffer.toByteArray()
        } ?: return null
        extractIccFromJpeg(bytes)
    }.getOrNull()

    fun extractIccFromJpeg(bytes: ByteArray): ByteArray? {
        if (bytes.size < 4) return null
        if ((bytes[0].toInt() and 0xFF) != 0xFF || (bytes[1].toInt() and 0xFF) != 0xD8) return null
        val chunks = sortedMapOf<Int, ByteArray>()
        var index = 2
        while (index + 4 <= bytes.size) {
            if ((bytes[index].toInt() and 0xFF) != 0xFF) {
                index++
                continue
            }
            val marker = bytes[index + 1].toInt() and 0xFF
            if (marker == 0xDA || marker == 0xD9) break
            val length = ((bytes[index + 2].toInt() and 0xFF) shl 8) or (bytes[index + 3].toInt() and 0xFF)
            if (length < 2 || index + 2 + length > bytes.size) break
            if (marker == 0xE2 && length > 16) {
                val header = String(bytes, index + 4, 12, Charsets.US_ASCII)
                if (header.startsWith("ICC_PROFILE")) {
                    val sequence = bytes[index + 16].toInt() and 0xFF
                    chunks[sequence] = bytes.copyOfRange(index + 18, index + 2 + length)
                }
            }
            index += 2 + length
        }
        if (chunks.isEmpty()) return null
        val out = ByteArrayOutputStream()
        chunks.values.forEach { out.write(it) }
        return out.toByteArray()
    }

    /** Injects a JPEG ICC profile as APP2 segment(s) right after SOI. */
    fun injectJpegIccProfile(jpeg: File, profile: ByteArray): Boolean = runCatching {
        if (profile.isEmpty()) return false
        val original = jpeg.readBytes()
        if (original.size < 2) return false
        val maxChunk = 65519
        val total = (profile.size + maxChunk - 1) / maxChunk
        if (total > 255) return false
        val out = ByteArrayOutputStream(original.size + profile.size + 64)
        out.write(0xFF)
        out.write(0xD8)
        for (part in 0 until total) {
            val from = part * maxChunk
            val to = minOf(profile.size, from + maxChunk)
            val chunk = profile.copyOfRange(from, to)
            val length = 2 + 12 + 2 + chunk.size
            out.write(0xFF)
            out.write(0xE2)
            out.write((length shr 8) and 0xFF)
            out.write(length and 0xFF)
            out.write("ICC_PROFILE".toByteArray(Charsets.US_ASCII))
            out.write(0)
            out.write(part + 1)
            out.write(total)
            out.write(chunk)
        }
        out.write(original, 2, original.size - 2)
        jpeg.writeBytes(out.toByteArray())
        true
    }.getOrDefault(false)

    private fun stripJpegApp1(file: File): Boolean {
        val bytes = file.readBytes()
        if (bytes.size < 4) return false
        val out = ByteArrayOutputStream(bytes.size)
        out.write(0xFF)
        out.write(0xD8)
        var index = 2
        while (index + 4 <= bytes.size) {
            if ((bytes[index].toInt() and 0xFF) != 0xFF) {
                out.write(bytes, index, bytes.size - index)
                break
            }
            val marker = bytes[index + 1].toInt() and 0xFF
            if (marker == 0xDA) {
                out.write(bytes, index, bytes.size - index)
                break
            }
            val length = ((bytes[index + 2].toInt() and 0xFF) shl 8) or (bytes[index + 3].toInt() and 0xFF)
            if (length < 2 || index + 2 + length > bytes.size) {
                out.write(bytes, index, bytes.size - index)
                break
            }
            // 0xE1 = APP1 (EXIF/XMP). Everything else (ICC APP2, JFIF APP0, ...) is kept.
            if (marker != 0xE1) out.write(bytes, index, 2 + length)
            index += 2 + length
        }
        file.writeBytes(out.toByteArray())
        return true
    }

    fun formatMp(megapixels: Double): String = when {
        megapixels >= 10 -> "${megapixels.toInt()} MP"
        else -> String.format(java.util.Locale.US, "%.1f MP", megapixels)
    }
}
