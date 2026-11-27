package com.harimu.app.feature.tools

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
import com.harimu.app.core.engines.ImageEngine
import com.harimu.app.core.engines.ImageFormatMatrix
import com.harimu.app.core.engines.ImageGeometryMath
import com.harimu.app.core.engines.ImageIO
import com.harimu.app.core.engines.PixelRect
import com.harimu.app.core.engines.Quad
import com.harimu.app.core.toolapi.InputSpec
import com.harimu.app.core.toolapi.Tool
import com.harimu.app.core.toolapi.ToolContext
import com.harimu.app.core.toolapi.ToolGroup
import com.harimu.app.core.toolapi.ToolInput
import com.harimu.app.core.toolapi.ToolOutputFile
import com.harimu.app.core.toolapi.ToolParam
import com.harimu.app.core.toolapi.ToolProgress
import com.harimu.app.core.toolapi.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Image Forge batch 1 (FASE 9): IMG-01, 03, 05, 06, 07, 08, 09, 10, 23, 24, 25, 26, 27, 28, 29, 30.
 * Geometry lives in [ImageGeometryMath] (pure Kotlin, golden-tested); decoding/encoding and
 * metadata handling live in [ImageIO]. Honest platform limits are listed in
 * `docs/DECISIONS.md` §12 and [ImageFormatMatrix].
 */
fun imageForgeBatch1Tools(): List<Tool> = listOf(
    cropProTool(),
    rotateTool(),
    canvasExpanderTool(),
    perspectiveTool(),
    straightenTool(),
    autoCropTool(),
    alignmentTool(),
    multiCropTool(),
    formatConverterTool(),
    iconPackTool(),
    base64Tool(),
    compressorTool(),
    webOptimizerTool(),
    paletteTool(),
    exifEditorTool(),
    sizeAnalyzerTool()
)

private const val PERSPECTIVE_DEFAULT_TL_X = 8f
private const val PERSPECTIVE_DEFAULT_TL_Y = 8f
private const val PERSPECTIVE_DEFAULT_TR_X = 92f
private const val PERSPECTIVE_DEFAULT_TR_Y = 8f
private const val PERSPECTIVE_DEFAULT_BR_X = 92f
private const val PERSPECTIVE_DEFAULT_BR_Y = 92f
private const val PERSPECTIVE_DEFAULT_BL_X = 8f
private const val PERSPECTIVE_DEFAULT_BL_Y = 92f

private object Batch1Support {

    fun zipEntry(zip: ZipOutputStream, name: String, bytes: ByteArray) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(bytes)
        zip.closeEntry()
    }

    fun safeName(raw: String): String = raw.trim()
        .replace(Regex("[^A-Za-z0-9_-]+"), "_")
        .trim('_')
        .ifBlank { "file" }

    fun kb(bytes: Long): String = String.format(Locale.US, "%.1f KB", bytes / 1024.0)

    fun savings(part: Long, whole: Long): String =
        if (whole <= 0L) "-" else String.format(Locale.US, "%.0f%%", part * 100.0 / whole)

    fun scaleToWidth(source: Bitmap, width: Int): Bitmap {
        val safeWidth = width.coerceIn(1, source.width)
        val height = (source.height.toFloat() * safeWidth / source.width).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, safeWidth, height, true)
    }

    fun squareCenter(source: Bitmap, size: Int): Bitmap {
        val side = min(source.width, source.height)
        val left = (source.width - side) / 2
        val top = (source.height - side) / 2
        val square = Bitmap.createBitmap(source, left, top, side, side)
        return try {
            val scaled = Bitmap.createScaledBitmap(square, size, size, true)
            if (scaled === square) {
                square.copy(Bitmap.Config.ARGB_8888, false)
            } else {
                scaled
            }
        } finally {
            if (!square.isRecycled) square.recycle()
        }
    }

    fun circular(source: Bitmap): Bitmap {
        val size = min(source.width, source.height)
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(source, (size - source.width) / 2f, (size - source.height) / 2f, paint)
        return out
    }

    fun adaptiveForeground(source: Bitmap): Bitmap {
        val size = source.width
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val inner = (size * 0.72f).roundToInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(source, inner, inner, true)
        try {
            canvas.drawBitmap(
                scaled,
                ((size - inner) / 2f),
                ((size - inner) / 2f),
                Paint(Paint.FILTER_BITMAP_FLAG)
            )
        } finally {
            if (scaled !== source && !scaled.isRecycled) scaled.recycle()
        }
        return out
    }

    fun encodeFile(bitmap: Bitmap, dest: File, label: String, quality: Int = 95): File =
        ImageIO.encode(bitmap, dest, label, quality)
}

// --------------------------------------------------------------------- IMG-01

private fun cropProTool(): Tool = object : Tool {
    override val id = "IMG-01"
    override val group = ToolGroup.IMAGE
    override val engineLabel = "NAT/TILE"
    override val icon = Icons.Filled.Crop
    override val inputSpec = InputSpec.SingleImage
    override val params = listOf<ToolParam>(
        ToolParam.DropdownParam("ratio", "Rasio Aspek", "Aspect Ratio", listOf("Bebas", "1:1", "16:9", "9:16", "4:3", "3:4", "3:2"), "1:1"),
        ToolParam.SliderParam("zoom", "Zoom (%)", "Zoom (%)", 100f, 400f, 100f, 5f, "%"),
        ToolParam.SliderParam("offsetX", "Geser Horizontal (%)", "Horizontal Offset (%)", -100f, 100f, 0f, 5f, "%"),
        ToolParam.SliderParam("offsetY", "Geser Vertikal (%)", "Vertical Offset (%)", -100f, 100f, 0f, 5f, "%"),
        ToolParam.DropdownParam("format", "Format Simpan", "Save Format", listOf("JPG", "PNG", "WEBP"), "JPG"),
        ToolParam.ToggleParam("keepExif", "Pertahankan EXIF & Profil Warna", "Keep EXIF & Color Profile", true)
    )

    override fun title(isEnglish: Boolean) = if (isEnglish) "Image Cropper Pro" else "Crop Gambar Pro"
    override fun description(isEnglish: Boolean) = if (isEnglish) "Crop full-resolution with aspect ratio, zoom and offset, decoding only the needed region." else "Potong resolusi penuh dengan rasio, zoom, dan geser; hanya area hasil yang didekode."

    override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
        val uri = input.uris.firstOrNull() ?: error("Pilih 1 gambar")
        val format = input.getString("format", "JPG")
        val entry = ImageFormatMatrix.requireEncodable(format)
        emit(ToolProgress.Running(15, "Membaca dimensi gambar..."))
        val info = ImageIO.readSourceInfo(ctx.appContext, uri, ctx.storage.getDisplayName(uri))
        val ratioLabel = input.getString("ratio", "1:1")
        val ratioW: Float
        val ratioH: Float
        if (ratioLabel == "Bebas") {
            ratioW = info.displayWidth.toFloat()
            ratioH = info.displayHeight.toFloat()
        } else {
            val parts = ratioLabel.split(':')
            ratioW = parts.getOrNull(0)?.toFloatOrNull() ?: 1f
            ratioH = parts.getOrNull(1)?.toFloatOrNull() ?: 1f
        }
        val rect = ImageGeometryMath.cropRectForRatio(
            info.displayWidth,
            info.displayHeight,
            ratioW,
            ratioH,
            input.getFloat("zoom", 100f),
            input.getFloat("offsetX", 0f),
            input.getFloat("offsetY", 0f)
        )
        emit(ToolProgress.Running(45, "Mendekode area ${rect.width}×${rect.height} (resolusi penuh)..."))
        val cropped = ImageIO.decodeRegionFullRes(ctx.appContext, uri, rect, info.orientation)
        val dest = try {
            emit(ToolProgress.Running(80, "Menyimpan hasil..."))
            val outFile = ctx.storage.createOutputFile("IMG_CROP", ImageFormatMatrix.extensionFor(entry.id))
            Batch1Support.encodeFile(cropped, outFile, entry.id, 95)
            outFile
        } finally {
            if (!cropped.isRecycled) cropped.recycle()
        }
        var extra = ""
        if (input.getBoolean("keepExif", true)) {
            val copied = ImageIO.copyExif(ctx.appContext, uri, dest)
            if (copied > 0) extra = " • $copied tag EXIF disalin"
            if (entry.id == "JPG") {
                val icc = ImageIO.readJpegIccProfile(ctx.appContext, uri)
                if (icc != null && ImageIO.injectJpegIccProfile(dest, icc)) {
                    extra += " • profil ICC disalin"
                }
            }
        }
        emit(
            ToolProgress.Done(
                ToolResult(
                    outputs = listOf(ToolOutputFile(dest, ImageFormatMatrix.mimeTypeFor(entry.id), dest.name)),
                    summary = "Crop ${rect.width}×${rect.height} dari ${info.displayWidth}×${info.displayHeight} " +
                        "(${ctx.storage.formatBytes(dest.length())})$extra"
                )
            )
        )
    }.flowOn(Dispatchers.IO)
}

// --------------------------------------------------------------------- IMG-03

private fun rotateTool(): Tool = object : Tool {
    override val id = "IMG-03"
    override val group = ToolGroup.IMAGE
    override val engineLabel = "NAT"
    override val icon = Icons.AutoMirrored.Filled.RotateRight
    override val inputSpec = InputSpec.SingleImage
    override val params = listOf<ToolParam>(
        ToolParam.DropdownParam("deg", "Sudut Putar", "Rotation Angle", listOf("90", "180", "270"), "90"),
        ToolParam.DropdownParam("format", "Format Simpan", "Save Format", listOf("JPG", "PNG", "WEBP"), "JPG"),
        ToolParam.ToggleParam("keepExif", "Pertahankan EXIF & Profil Warna", "Keep EXIF & Color Profile", true)
    )

    override fun title(isEnglish: Boolean) = if (isEnglish) "Image Rotator" else "Putar Gambar"
    override fun description(isEnglish: Boolean) = if (isEnglish) "Rotate 90/180/270 degrees at full resolution with EXIF orientation corrected first." else "Putar 90°/180°/270° pada resolusi penuh setelah orientasi EXIF dikoreksi."

    override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
        val uri = input.uris.firstOrNull() ?: error("Pilih 1 gambar")
        val degrees = input.getString("deg", "90").toFloatOrNull() ?: 90f
        val entry = ImageFormatMatrix.requireEncodable(input.getString("format", "JPG"))
        emit(ToolProgress.Running(20, "Mendekode gambar resolusi penuh..."))
        val source = ImageIO.decodeFullGuarded(ctx.appContext, uri)
        var rotated: Bitmap? = null
        val (dest, size) = try {
            emit(ToolProgress.Running(60, "Memutar ${degrees.toInt()}°..."))
            val matrix = Matrix().apply { postRotate(degrees) }
            rotated = Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
            val outFile = ctx.storage.createOutputFile("IMG_ROT", ImageFormatMatrix.extensionFor(entry.id))
            Batch1Support.encodeFile(rotated, outFile, entry.id, 95)
            outFile to "${rotated.width}×${rotated.height}"
        } finally {
            if (rotated != null && rotated !== source && !rotated.isRecycled) rotated.recycle()
            if (!source.isRecycled) source.recycle()
        }
        if (input.getBoolean("keepExif", true)) ImageIO.copyExif(ctx.appContext, uri, dest)
        if (entry.id == "JPG") {
            val icc = ImageIO.readJpegIccProfile(ctx.appContext, uri)
            if (icc != null) ImageIO.injectJpegIccProfile(dest, icc)
        }
        emit(
            ToolProgress.Done(
                ToolResult(
                    outputs = listOf(ToolOutputFile(dest, ImageFormatMatrix.mimeTypeFor(entry.id), dest.name)),
                    summary = "Diputar ${degrees.toInt()}° → $size (${ctx.storage.formatBytes(dest.length())})"
                )
            )
        )
    }.flowOn(Dispatchers.IO)
}

// --------------------------------------------------------------------- IMG-05

private fun canvasExpanderTool(): Tool = object : Tool {
    override val id = "IMG-05"
    override val group = ToolGroup.IMAGE
    override val engineLabel = "NAT"
    override val icon = Icons.Filled.AspectRatio
    override val inputSpec = InputSpec.SingleImage
    override val params = listOf<ToolParam>(
        ToolParam.DropdownParam("aspect", "Rasio Kanvas", "Canvas Aspect", listOf("1:1", "4:5", "16:9", "9:16", "3:4"), "1:1"),
        ToolParam.DropdownParam("mode", "Isi Latar", "Background Fill", listOf("Warna solid", "Blur latar"), "Blur latar"),
        ToolParam.TextParam("bgHex", "Warna Latar (#Hex)", "Background (#Hex)", "#13161F")
    )

    override fun title(isEnglish: Boolean) = if (isEnglish) "Canvas Expander" else "Perluas Kanvas"
    override fun description(isEnglish: Boolean) = if (isEnglish) "Pad the photo to a target aspect ratio with solid colour or a blurred copy of itself." else "Tambahkan latar solid atau salinan blur foto agar pas rasio target tanpa memotong isi."

    override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
        val uri = input.uris.firstOrNull() ?: error("Pilih 1 gambar")
        val ratio = when (input.getString("aspect", "1:1")) {
            "4:5" -> 4f / 5f
            "16:9" -> 16f / 9f
            "9:16" -> 9f / 16f
            "3:4" -> 3f / 4f
            else -> 1f
        }
        emit(ToolProgress.Running(25, "Mendekode gambar..."))
        val source = ImageIO.decodeFullGuarded(ctx.appContext, uri)
        var out: Bitmap? = null
        val (outW, outH, dest) = try {
            val (targetW, targetH) = ImageGeometryMath.expandCanvasSize(source.width, source.height, ratio)
            emit(ToolProgress.Running(60, "Menyusun kanvas ${targetW}×${targetH}..."))
            val background = ImageEngine.parseColorSafe(
                input.getString("bgHex", "#13161F"),
                Color.parseColor("#13161F")
            )
            out = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(out)
            canvas.drawColor(background)
            if (input.getString("mode", "Blur latar") == "Blur latar") {
                val coverScale = max(targetW.toFloat() / source.width, targetH.toFloat() / source.height)
                val coverW = (source.width * coverScale).roundToInt().coerceAtLeast(1)
                val coverH = (source.height * coverScale).roundToInt().coerceAtLeast(1)
                val cover = Bitmap.createScaledBitmap(source, coverW, coverH, true)
                var tiny: Bitmap? = null
                try {
                    val tinyW = max(1, targetW / 32)
                    val tinyH = max(1, targetH / 32)
                    tiny = Bitmap.createScaledBitmap(cover, tinyW, tinyH, true)
                    val paint = Paint(Paint.FILTER_BITMAP_FLAG)
                    val target = Rect(
                        (targetW - coverW) / 2,
                        (targetH - coverH) / 2,
                        (targetW - coverW) / 2 + coverW,
                        (targetH - coverH) / 2 + coverH
                    )
                    canvas.drawBitmap(tiny, null, RectF(target), paint)
                } finally {
                    if (tiny != null && tiny !== cover && !tiny.isRecycled) tiny.recycle()
                    if (cover !== source && !cover.isRecycled) cover.recycle()
                }
            }
            canvas.drawBitmap(source, (targetW - source.width) / 2f, (targetH - source.height) / 2f, null)
            val outFile = ctx.storage.createOutputFile("IMG_CANVAS", "png")
            Batch1Support.encodeFile(out, outFile, "PNG")
            Triple(targetW, targetH, outFile)
        } finally {
            if (out != null && !out.isRecycled) out.recycle()
            if (!source.isRecycled) source.recycle()
        }
        emit(
            ToolProgress.Done(
                ToolResult(
                    outputs = listOf(ToolOutputFile(dest, "image/png", dest.name)),
                    summary = "Kanvas ${outW}×${outH} (${ctx.storage.formatBytes(dest.length())})"
                )
            )
        )
    }.flowOn(Dispatchers.IO)
}

// --------------------------------------------------------------------- IMG-06

private fun perspectiveTool(): Tool = object : Tool {
    override val id = "IMG-06"
    override val group = ToolGroup.IMAGE
    override val engineLabel = "NAT (homografi)"
    override val icon = Icons.Filled.Crop
    override val inputSpec = InputSpec.SingleImage
    override val params = listOf<ToolParam>(
        ToolParam.SliderParam("tlX", "Kiri Atas X (%)", "Top-Left X (%)", 0f, 100f, PERSPECTIVE_DEFAULT_TL_X, 0.5f, "%"),
        ToolParam.SliderParam("tlY", "Kiri Atas Y (%)", "Top-Left Y (%)", 0f, 100f, PERSPECTIVE_DEFAULT_TL_Y, 0.5f, "%"),
        ToolParam.SliderParam("trX", "Kanan Atas X (%)", "Top-Right X (%)", 0f, 100f, PERSPECTIVE_DEFAULT_TR_X, 0.5f, "%"),
        ToolParam.SliderParam("trY", "Kanan Atas Y (%)", "Top-Right Y (%)", 0f, 100f, PERSPECTIVE_DEFAULT_TR_Y, 0.5f, "%"),
        ToolParam.SliderParam("brX", "Kanan Bawah X (%)", "Bottom-Right X (%)", 0f, 100f, PERSPECTIVE_DEFAULT_BR_X, 0.5f, "%"),
        ToolParam.SliderParam("brY", "Kanan Bawah Y (%)", "Bottom-Right Y (%)", 0f, 100f, PERSPECTIVE_DEFAULT_BR_Y, 0.5f, "%"),
        ToolParam.SliderParam("blX", "Kiri Bawah X (%)", "Bottom-Left X (%)", 0f, 100f, PERSPECTIVE_DEFAULT_BL_X, 0.5f, "%"),
        ToolParam.SliderParam("blY", "Kiri Bawah Y (%)", "Bottom-Left Y (%)", 0f, 100f, PERSPECTIVE_DEFAULT_BL_Y, 0.5f, "%"),
        ToolParam.DropdownParam("format", "Format Simpan", "Save Format", listOf("PNG", "JPG"), "PNG")
    )

    override fun title(isEnglish: Boolean) = if (isEnglish) "Perspective Corrector" else "Koreksi Perspektif"
    override fun description(isEnglish: Boolean) = if (isEnglish) "Drag four corners of the document and warp it back to a flat rectangle." else "Geser empat titik sudut dokumen lalu luruskan menjadi persegi lewat homografi."

    override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
        val uri = input.uris.firstOrNull() ?: error("Pilih 1 gambar")
        val entry = ImageFormatMatrix.requireEncodable(input.getString("format", "PNG"))
        emit(ToolProgress.Running(20, "Mendekode gambar..."))
        val source = ImageIO.decodeFullGuarded(ctx.appContext, uri)
        var warped: Bitmap? = null
        val (outW, outH, dest) = try {
            val quad = Quad.fromPercents(
                source.width,
                source.height,
                input.getFloat("tlX", PERSPECTIVE_DEFAULT_TL_X),
                input.getFloat("tlY", PERSPECTIVE_DEFAULT_TL_Y),
                input.getFloat("trX", PERSPECTIVE_DEFAULT_TR_X),
                input.getFloat("trY", PERSPECTIVE_DEFAULT_TR_Y),
                input.getFloat("brX", PERSPECTIVE_DEFAULT_BR_X),
                input.getFloat("brY", PERSPECTIVE_DEFAULT_BR_Y),
                input.getFloat("blX", PERSPECTIVE_DEFAULT_BL_X),
                input.getFloat("blY", PERSPECTIVE_DEFAULT_BL_Y)
            )
            ImageGeometryMath.quadValidationError(quad, source.width, source.height)?.let { reason ->
                throw IllegalStateException("$reason. Atur ulang 4 titik lewat kartu pratinjau.")
            }
            val (targetW, targetH) = ImageGeometryMath.perspectiveOutputSize(quad)
            val values = ImageGeometryMath.perspectiveMatrixValues(quad, targetW, targetH)
                ?: throw IllegalStateException("Homografi tidak dapat dihitung dari titik tersebut. Ubah posisi titik.")
            emit(ToolProgress.Running(65, "Memetakan perspektif ke ${targetW}×${targetH}..."))
            val matrix = Matrix().apply { setValues(values) }
            warped = Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
            val outFile = ctx.storage.createOutputFile("IMG_PERSP", ImageFormatMatrix.extensionFor(entry.id))
            Batch1Support.encodeFile(warped, outFile, entry.id, 95)
            Triple(targetW, targetH, outFile)
        } finally {
            if (warped != null && warped !== source && !warped.isRecycled) warped.recycle()
            if (!source.isRecycled) source.recycle()
        }
        emit(
            ToolProgress.Done(
                ToolResult(
                    outputs = listOf(ToolOutputFile(dest, ImageFormatMatrix.mimeTypeFor(entry.id), dest.name)),
                    summary = "Perspektif dikoreksi → ${outW}×${outH} (${ctx.storage.formatBytes(dest.length())})"
                )
            )
        )
    }.flowOn(Dispatchers.IO)
}

// --------------------------------------------------------------------- IMG-07

private fun straightenTool(): Tool = object : Tool {
    override val id = "IMG-07"
    override val group = ToolGroup.IMAGE
    override val engineLabel = "NAT"
    override val icon = Icons.Filled.Straighten
    override val inputSpec = InputSpec.SingleImage
    override val params = listOf<ToolParam>(
        ToolParam.SliderParam("angle", "Koreksi Sudut (°)", "Angle Correction (°)", -25f, 25f, 3f, 0.5f, "°"),
        ToolParam.DropdownParam("fill", "Tepi Kosong", "Empty Corners", listOf("Potong otomatis", "Warna latar"), "Potong otomatis"),
        ToolParam.TextParam("bgHex", "Warna Latar (#Hex)", "Background (#Hex)", "#13161F")
    )

    override fun title(isEnglish: Boolean) = if (isEnglish) "Image Straightener" else "Pelurus Horizon"
    override fun description(isEnglish: Boolean) = if (isEnglish) "Rotate by fine degrees and keep the largest rectangle without empty corners." else "Putar dengan sudut halus lalu ambil persegi terbesar tanpa sudut kosong."

    override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
        val uri = input.uris.firstOrNull() ?: error("Pilih 1 gambar")
        val angle = input.getFloat("angle", 3f)
        emit(ToolProgress.Running(20, "Mendekode gambar..."))
        val source = ImageIO.decodeFullGuarded(ctx.appContext, uri)
        var rotated: Bitmap? = null
        var out: Bitmap? = null
        val (dest, size) = try {
            val originalWidth = source.width
            val originalHeight = source.height
            val (canvasW, canvasH) = ImageGeometryMath.rotatedBounds(originalWidth, originalHeight, angle)
            rotated = Bitmap.createBitmap(canvasW, canvasH, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(rotated)
            val background = if (input.getString("fill", "Potong otomatis") == "Warna latar") {
                ImageEngine.parseColorSafe(input.getString("bgHex", "#13161F"), Color.parseColor("#13161F"))
            } else {
                Color.TRANSPARENT
            }
            canvas.drawColor(background)
            val matrix = Matrix()
            matrix.postTranslate((canvasW - source.width) / 2f, (canvasH - source.height) / 2f)
            matrix.postRotate(angle, canvasW / 2f, canvasH / 2f)
            canvas.drawBitmap(source, matrix, Paint(Paint.FILTER_BITMAP_FLAG))
            emit(ToolProgress.Running(65, "Memotong tepi kosong..."))
            out = if (input.getString("fill", "Potong otomatis") == "Potong otomatis") {
                val region = ImageGeometryMath
                    .straightenSafeRect(originalWidth, originalHeight, angle)
                    .clampedTo(canvasW, canvasH)
                Bitmap.createBitmap(rotated, region.left, region.top, region.width, region.height)
            } else {
                rotated
            }
            val outFile = ctx.storage.createOutputFile("IMG_STRAIGHT", "png")
            Batch1Support.encodeFile(out, outFile, "PNG")
            outFile to "${out.width}×${out.height}"
        } finally {
            if (out != null && out !== rotated && !out.isRecycled) out.recycle()
            if (rotated != null && !rotated.isRecycled) rotated.recycle()
            if (!source.isRecycled) source.recycle()
        }
        emit(
            ToolProgress.Done(
                ToolResult(
                    outputs = listOf(ToolOutputFile(dest, "image/png", dest.name)),
                    summary = "Diluruskan ${String.format(Locale.US, "%.1f", angle)}° → $size (${ctx.storage.formatBytes(dest.length())})"
                )
            )
        )
    }.flowOn(Dispatchers.IO)
}

// --------------------------------------------------------------------- IMG-08

private fun autoCropTool(): Tool = object : Tool {
    override val id = "IMG-08"
    override val group = ToolGroup.IMAGE
    override val engineLabel = "NAT (saliency)"
    override val icon = Icons.Filled.AutoFixHigh
    override val inputSpec = InputSpec.SingleImage
    override val params = listOf<ToolParam>(
        ToolParam.SliderParam("threshold", "Ambang Subjek (%)", "Subject Threshold (%)", 5f, 60f, 22f, 1f, "%"),
        ToolParam.SliderParam("padding", "Padding (%)", "Padding (%)", 0f, 20f, 4f, 1f, "%")
    )

    override fun title(isEnglish: Boolean) = if (isEnglish) "Smart Auto-Crop" else "Crop Otomatis Cerdas"
    override fun description(isEnglish: Boolean) = if (isEnglish) "Local saliency heuristic finds the subject and crops around it, full resolution." else "Heuristik saliency lokal menemukan subjek lalu memotongnya pada resolusi penuh."

    override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
        val uri = input.uris.firstOrNull() ?: error("Pilih 1 gambar")
        emit(ToolProgress.Running(15, "Menganalisis konten gambar..."))
        val info = ImageIO.readSourceInfo(ctx.appContext, uri, ctx.storage.getDisplayName(uri))
        val preview = ImageIO.decodePreview(ctx.appContext, uri, 96)
        val (gridW, gridH, pixels) = try {
            val w = preview.width
            val h = preview.height
            val px = IntArray(w * h)
            preview.getPixels(px, 0, w, 0, 0, w, h)
            Triple(w, h, px)
        } finally {
            if (!preview.isRecycled) preview.recycle()
        }
        val box = ImageGeometryMath.saliencyBoundingBox(
            pixels,
            gridW,
            gridH,
            input.getFloat("threshold", 22f).toInt()
        ) ?: throw IllegalStateException(
            "Tidak ada subjek menonjol yang terdeteksi. Turunkan ambang subjek atau pakai IMG-01 untuk crop manual."
        )
        val scaleX = info.displayWidth.toFloat() / gridW
        val scaleY = info.displayHeight.toFloat() / gridH
        val inDisplay = PixelRect(
            (box.left * scaleX).roundToInt(),
            (box.top * scaleY).roundToInt(),
            (box.width * scaleX).roundToInt().coerceAtLeast(1),
            (box.height * scaleY).roundToInt().coerceAtLeast(1)
        )
        val padded = ImageGeometryMath.padBoundingBox(
            inDisplay,
            info.displayWidth,
            info.displayHeight,
            input.getFloat("padding", 4f)
        )
        emit(ToolProgress.Running(55, "Mendekode area ${padded.width}×${padded.height}..."))
        val cropped = ImageIO.decodeRegionFullRes(ctx.appContext, uri, padded, info.orientation)
        val dest = try {
            val outFile = ctx.storage.createOutputFile("IMG_AUTOCROP", "png")
            Batch1Support.encodeFile(cropped, outFile, "PNG")
            outFile
        } finally {
            if (!cropped.isRecycled) cropped.recycle()
        }
        emit(
            ToolProgress.Done(
                ToolResult(
                    outputs = listOf(ToolOutputFile(dest, "image/png", dest.name)),
                    summary = "Auto-crop ${padded.width}×${padded.height} dari ${info.displayWidth}×${info.displayHeight} " +
                        "(${ctx.storage.formatBytes(dest.length())}) • heuristik kontras lokal, bukan ML Kit"
                )
            )
        )
    }.flowOn(Dispatchers.IO)
}

// --------------------------------------------------------------------- IMG-09

private fun alignmentTool(): Tool = object : Tool {
    override val id = "IMG-09"
    override val group = ToolGroup.IMAGE
    override val engineLabel = "NAT"
    override val icon = Icons.Filled.Image
    override val inputSpec = InputSpec.MultipleImages
    override val params = listOf<ToolParam>(
        ToolParam.SliderParam("maxShift", "Geser Maks (%)", "Max Shift (%)", 1f, 20f, 6f, 1f, "%"),
        ToolParam.ToggleParam("average", "Rata-rata progresif (kurangi noise)", "Progressive average (noise reduction)", true)
    )

    override fun title(isEnglish: Boolean) = if (isEnglish) "Image Alignment" else "Penyelaras Gambar"
    override fun description(isEnglish: Boolean) = if (isEnglish) "Align stacked shots by translation and merge them into one averaged frame." else "Selaraskan tumpukan foto lewat translasi lalu gabungkan menjadi satu frame rata-rata."

    override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
        require(input.uris.size >= 2) { "Pilih minimal 2 gambar" }
        val maxShiftPct = input.getFloat("maxShift", 6f)
        val average = input.getBoolean("average", true)
        val alignSize = 128
        emit(ToolProgress.Running(10, "Menyiapkan pratinjau penyelarasan..."))
        val reference = ImageIO.decodePreview(ctx.appContext, input.uris.first(), alignSize)
        var refGrid: Bitmap? = null
        val refPixels = IntArray(alignSize * alignSize)
        try {
            refGrid = Bitmap.createScaledBitmap(reference, alignSize, alignSize, true)
            refGrid.getPixels(refPixels, 0, alignSize, 0, 0, alignSize, alignSize)
        } finally {
            if (refGrid != null && refGrid !== reference && !refGrid.isRecycled) refGrid.recycle()
            if (!reference.isRecycled) reference.recycle()
        }
        val maxShiftPx = (alignSize * maxShiftPct / 100f).roundToInt().coerceAtLeast(1)
        val offsetList = mutableListOf<Pair<Int, Int>>()
        for (index in 1 until input.uris.size) {
            emit(ToolProgress.Running(10 + index * 10, "Menghitung offset gambar ${index + 1}..."))
            val preview = ImageIO.decodePreview(ctx.appContext, input.uris[index], alignSize)
            var grid: Bitmap? = null
            val pixels = IntArray(alignSize * alignSize)
            try {
                grid = Bitmap.createScaledBitmap(preview, alignSize, alignSize, true)
                grid.getPixels(pixels, 0, alignSize, 0, 0, alignSize, alignSize)
            } finally {
                if (grid != null && grid !== preview && !grid.isRecycled) grid.recycle()
                if (!preview.isRecycled) preview.recycle()
            }
            offsetList += ImageGeometryMath.alignmentOffset(refPixels, pixels, alignSize, alignSize, maxShiftPx)
        }

        emit(ToolProgress.Running(35, "Mendekode gambar resolusi penuh..."))
        val first = ImageIO.decodeFullGuarded(ctx.appContext, input.uris.first(), 24_000_000L)
        val fullW = first.width
        val fullH = first.height
        val scaleX = fullW.toFloat() / alignSize
        val scaleY = fullH.toFloat() / alignSize
        val canvasBitmap = Bitmap.createBitmap(fullW, fullH, Bitmap.Config.ARGB_8888)
        val dest = try {
            val canvas = Canvas(canvasBitmap)
            try {
                canvas.drawBitmap(first, 0f, 0f, null)
            } finally {
                if (!first.isRecycled) first.recycle()
            }
            val paint = Paint(Paint.FILTER_BITMAP_FLAG)
            for (index in 1 until input.uris.size) {
                emit(ToolProgress.Running(35 + (index * 50 / input.uris.size), "Menggabungkan gambar ${index + 1}..."))
                val (dx, dy) = offsetList[index - 1]
                val current = ImageIO.decodeFullGuarded(ctx.appContext, input.uris[index], 24_000_000L)
                try {
                    val alpha = if (average) (255 / (index + 1)).coerceAtLeast(1) else 140
                    paint.alpha = alpha
                    val matrix = Matrix().apply { postTranslate(-dx * scaleX, -dy * scaleY) }
                    canvas.drawBitmap(current, matrix, paint)
                } finally {
                    if (!current.isRecycled) current.recycle()
                }
            }
            val outFile = ctx.storage.createOutputFile("IMG_ALIGN", "png")
            Batch1Support.encodeFile(canvasBitmap, outFile, "PNG")
            outFile
        } finally {
            if (!canvasBitmap.isRecycled) canvasBitmap.recycle()
        }
        val report = buildString {
            appendLine("Penyelarasan ${input.uris.size} gambar (grid ${alignSize}×${alignSize}, maks ${maxShiftPx}px)")
            appendLine("Mode: ${if (average) "rata-rata progresif (alpha 1/k)" else "tumpuk 55% opacity"}")
            offsetList.forEachIndexed { index, offset ->
                appendLine("Gambar ${index + 2}: dx=${(offset.first * scaleX).roundToInt()}px " +
                    "dy=${(offset.second * scaleY).roundToInt()}px (grid ${offset.first}, ${offset.second})")
            }
            appendLine("Translasi saja; rotasi/skala antar frame tidak dikoreksi.")
        }
        emit(
            ToolProgress.Done(
                ToolResult(
                    outputs = listOf(ToolOutputFile(dest, "image/png", dest.name)),
                    textOutput = report,
                    summary = "Diselaraskan ${input.uris.size} gambar → ${fullW}×${fullH} (${ctx.storage.formatBytes(dest.length())})"
                )
            )
        )
    }.flowOn(Dispatchers.IO)
}

// --------------------------------------------------------------------- IMG-10

private fun multiCropTool(): Tool = object : Tool {
    override val id = "IMG-10"
    override val group = ToolGroup.IMAGE
    override val engineLabel = "NAT"
    override val icon = Icons.Filled.GridOn
    override val inputSpec = InputSpec.SingleImage
    override val params = listOf<ToolParam>(
        ToolParam.TextParam(
            "regions",
            "Region (nama:x,y,w,h;... persen)",
            "Regions (name:x,y,w,h;... percent)",
            "tengah:25,25,50,50; atas:0,0,100,50; bawah:0,50,100,50"
        ),
        ToolParam.DropdownParam("format", "Format Simpan", "Save Format", listOf("PNG", "JPG"), "PNG")
    )

    override fun title(isEnglish: Boolean) = if (isEnglish) "Multi-layer Crop" else "Crop Multi-Layer"
    override fun description(isEnglish: Boolean) = if (isEnglish) "Export several crop regions from one photo into a single ZIP." else "Ekspor beberapa area crop dari satu foto sekaligus ke dalam satu ZIP."

    override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
        val uri = input.uris.firstOrNull() ?: error("Pilih 1 gambar")
        val regions = ImageGeometryMath.parseCropRegions(
            input.getString("regions", "tengah:25,25,50,50")
        )
        require(regions.isNotEmpty()) { "Isi minimal satu region, contoh: tengah:25,25,50,50" }
        val entry = ImageFormatMatrix.requireEncodable(input.getString("format", "PNG"))
        val info = ImageIO.readSourceInfo(ctx.appContext, uri, ctx.storage.getDisplayName(uri))
        val dest = ctx.storage.createOutputFile("IMG_MULTICROP", "zip")
        var totalBytes = 0L
        ZipOutputStream(FileOutputStream(dest)).use { zip ->
            regions.forEachIndexed { index, region ->
                emit(ToolProgress.Running(15 + index * 70 / regions.size, "Crop '${region.name}'..."))
                val rect = ImageGeometryMath.cropRect(
                    info.displayWidth,
                    info.displayHeight,
                    region.leftPct,
                    region.topPct,
                    region.widthPct,
                    region.heightPct
                )
                val bitmap = ImageIO.decodeRegionFullRes(ctx.appContext, uri, rect, info.orientation)
                val bytes = try {
                    ImageIO.encodeToBytes(bitmap, entry.id, 95)
                } finally {
                    if (!bitmap.isRecycled) bitmap.recycle()
                }
                val name = "${index + 1}_${Batch1Support.safeName(region.name)}." +
                    ImageFormatMatrix.extensionFor(entry.id)
                Batch1Support.zipEntry(zip, name, bytes)
                totalBytes += bytes.size
            }
            Batch1Support.zipEntry(
                zip,
                "regions.txt",
                regions.joinToString("\n") { "${it.name}:x=${it.leftPct}% y=${it.topPct}% w=${it.widthPct}% h=${it.heightPct}%" }
                    .toByteArray()
            )
        }
        emit(
            ToolProgress.Done(
                ToolResult(
                    outputs = listOf(ToolOutputFile(dest, "application/zip", dest.name)),
                    summary = "${regions.size} region diekspor (${ctx.storage.formatBytes(dest.length())}, total gambar ${Batch1Support.kb(totalBytes)})"
                )
            )
        )
    }.flowOn(Dispatchers.IO)
}

// --------------------------------------------------------------------- IMG-23

private fun formatConverterTool(): Tool = object : Tool {
    override val id = "IMG-23"
    override val group = ToolGroup.IMAGE
    override val engineLabel = "NAT+LIB"
    override val icon = Icons.Filled.SwapHoriz
    override val inputSpec = InputSpec.SingleImage
    override val params = listOf<ToolParam>(
        ToolParam.DropdownParam("format", "Format Tujuan", "Target Format", ImageFormatMatrix.allLabels, "WEBP"),
        ToolParam.SliderParam("quality", "Kualitas (%)", "Quality (%)", 30f, 100f, 90f, 5f, "%"),
        ToolParam.ToggleParam("keepMeta", "Pertahankan EXIF & Profil Warna", "Keep EXIF & Color Profile", true)
    )

    override fun title(isEnglish: Boolean) = if (isEnglish) "Image Format Converter" else "Konverter Format Gambar"
    override fun description(isEnglish: Boolean) = if (isEnglish) "Convert to any format the platform truly supports; unsupported targets are refused with the reason." else "Konversi ke format yang benar-benar didukung platform; target tak didukung ditolak dengan alasannya."

    override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
        val uri = input.uris.firstOrNull() ?: error("Pilih 1 gambar")
        val target = ImageFormatMatrix.requireEncodable(input.getString("format", "WEBP"))
        val info = ImageIO.readSourceInfo(ctx.appContext, uri, ctx.storage.getDisplayName(uri))
        val sourceEntry = ImageFormatMatrix.byLabel(info.formatLabel)
        if (sourceEntry != null && !sourceEntry.canDecode) {
            throw IllegalStateException(
                "Sumber ${sourceEntry.id} tidak dapat dibaca di perangkat Android (${sourceEntry.library}). " +
                    sourceEntry.noteId
            )
        }
        emit(ToolProgress.Running(20, "Mendekode ${info.formatLabel} ${info.width}×${info.height}..."))
        val source = ImageIO.decodeFullGuarded(ctx.appContext, uri)
        val dest = try {
            val outFile = ctx.storage.createOutputFile("IMG_CONV", ImageFormatMatrix.extensionFor(target.id))
            emit(ToolProgress.Running(65, "Menyandikan ${target.id}..."))
            Batch1Support.encodeFile(source, outFile, target.id, input.getFloat("quality", 90f).toInt())
            outFile
        } finally {
            if (!source.isRecycled) source.recycle()
        }
        if (input.getBoolean("keepMeta", true)) {
            if (target.id == "JPG") {
                val icc = ImageIO.readJpegIccProfile(ctx.appContext, uri)
                if (icc != null) ImageIO.injectJpegIccProfile(dest, icc)
            }
            ImageIO.copyExif(ctx.appContext, uri, dest)
        }
        val sizeNote = if (info.byteSize > 0) {
            " • asal ${ctx.storage.formatBytes(info.byteSize)} → ${ctx.storage.formatBytes(dest.length())}"
        } else {
            " • ${ctx.storage.formatBytes(dest.length())}"
        }
        emit(
            ToolProgress.Done(
                ToolResult(
                    outputs = listOf(ToolOutputFile(dest, ImageFormatMatrix.mimeTypeFor(target.id), dest.name)),
                    summary = "${info.formatLabel} → ${target.id}$sizeNote"
                )
            )
        )
    }.flowOn(Dispatchers.IO)
}

// --------------------------------------------------------------------- IMG-24

private fun iconPackTool(): Tool = object : Tool {
    override val id = "IMG-24"
    override val group = ToolGroup.IMAGE
    override val engineLabel = "NAT"
    override val icon = Icons.Filled.Apps
    override val inputSpec = InputSpec.SingleImage
    override val params = listOf<ToolParam>(
        ToolParam.DropdownParam("shape", "Bentuk Dasar", "Base Shape", listOf("Kotak", "Bulat", "Adaptif"), "Kotak"),
        ToolParam.TextParam("bgHex", "Warna Latar (#Hex)", "Background (#Hex)", "#13161F")
    )

    override fun title(isEnglish: Boolean) = if (isEnglish) "App Icon Pack Generator" else "Generator Paket Ikon"
    override fun description(isEnglish: Boolean) = if (isEnglish) "Android legacy/round/adaptive, Play 512, iOS AppIcon set and favicons in one ZIP." else "Ikon Android legacy/round/adaptive, Play 512, set iOS AppIcon, dan favicon dalam satu ZIP."

    override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
        val uri = input.uris.firstOrNull() ?: error("Pilih 1 gambar ikon")
        val shape = input.getString("shape", "Kotak")
        val backgroundHex = input.getString("bgHex", "#13161F")
        emit(ToolProgress.Running(15, "Menyiapkan kanvas ikon..."))
        val source = ImageIO.decodePreview(ctx.appContext, uri, 1024)
        val entries = ImageGeometryMath.iconPackEntries()
        val dest = ctx.storage.createOutputFile("ICON_PACK", "zip")
        try {
            ZipOutputStream(FileOutputStream(dest)).use { zip ->
                entries.forEachIndexed { index, spec ->
                    if (index % 4 == 0) {
                        emit(ToolProgress.Running(20 + index * 60 / entries.size, "Merender ${spec.label}..."))
                    }
                    val square = Batch1Support.squareCenter(source, spec.sizePx)
                    var bitmap: Bitmap? = null
                    try {
                        val isRound = spec.path.contains("_round")
                        val isForeground = spec.path.contains("foreground")
                        bitmap = when {
                            isForeground -> Batch1Support.adaptiveForeground(square)
                            isRound && shape != "Adaptif" -> Batch1Support.circular(square)
                            else -> square
                        }
                        Batch1Support.zipEntry(zip, spec.path, ImageIO.encodeToBytes(bitmap, "PNG", 100))
                    } finally {
                        if (bitmap != null && bitmap !== square && !bitmap.isRecycled) bitmap.recycle()
                        if (!square.isRecycled) square.recycle()
                    }
                }
                Batch1Support.zipEntry(
                    zip,
                    "mipmap-anydpi-v26/ic_launcher.xml",
                    ImageGeometryMath.adaptiveIconXml().toByteArray()
                )
                Batch1Support.zipEntry(
                    zip,
                    "mipmap-anydpi-v26/ic_launcher_round.xml",
                    ImageGeometryMath.adaptiveIconXml().toByteArray()
                )
                Batch1Support.zipEntry(
                    zip,
                    "values/ic_launcher_background.xml",
                    ("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<resources>\n" +
                        "    <color name=\"ic_launcher_background\">$backgroundHex</color>\n</resources>\n").toByteArray()
                )
                Batch1Support.zipEntry(
                    zip,
                    "ios/AppIcon.appiconset/Contents.json",
                    iosContentsJson(entries).toByteArray()
                )
                Batch1Support.zipEntry(zip, "README.txt", readmeForIconPack(entries.size).toByteArray())
            }
        } finally {
            if (!source.isRecycled) source.recycle()
        }
        emit(
            ToolProgress.Done(
                ToolResult(
                    outputs = listOf(ToolOutputFile(dest, "application/zip", dest.name)),
                    summary = "${entries.size} PNG ikon + adaptive XML + iOS Contents.json + favicon " +
                        "(${ctx.storage.formatBytes(dest.length())})"
                )
            )
        )
    }.flowOn(Dispatchers.IO)

    private fun iosContentsJson(entries: List<com.harimu.app.core.engines.IconSpec>): String {
        val iosEntries = entries.filter { it.path.startsWith("ios/AppIcon.appiconset/") }
        val items = iosEntries.joinToString(",\n") { spec ->
            val file = spec.path.substringAfterLast('/')
            val parts = file.removePrefix("Icon-App-").removeSuffix(".png").split('@')
            val size = parts.getOrNull(0).orEmpty()
            val scale = parts.getOrNull(1).orEmpty()
            """    { "filename": "$file", "idiom": "universal", "scale": "$scale", "size": "$size" }"""
        }
        return """{
  "images": [
$items
  ],
  "info": { "author": "xcode", "version": 1 }
}
"""
    }

    private fun readmeForIconPack(count: Int): String = """
        |Paket ikon HariMu ($count PNG + adaptif)
        |
        |Android
        |  - Salin folder mipmap-* ke app/src/main/res/.
        |  - File mipmap-anydpi-v26/ic_launcher.xml + ic_launcher_round.xml sudah disertakan;
        |    letakkan di app/src/main/res/mipmap-anydpi-v26/.
        |  - Salin values/ic_launcher_background.xml ke app/src/main/res/values/.
        |  - Di AndroidManifest.xml:
        |      <application android:icon="@mipmap/ic_launcher"
        |                   android:roundIcon="@mipmap/ic_launcher_round" ... >
        |
        |Google Play
        |  - playstore-icon-512.png (512×512, tanpa transparansi).
        |
        |iOS
        |  - Salin ios/AppIcon.appiconset/ ke Assets.xcassets/AppIcon.appiconset/.
        |  - Contents.json sudah disertakan untuk Xcode 14+.
        |
        |Web
        |  - favicon-16/32/48.png dan apple-touch-icon-180.png ke root situs.
        |
        |Catatan: ikon sumber dipotong tengah menjadi persegi lalu diskalakan.
    """.trimMargin()
}

// --------------------------------------------------------------------- IMG-25

private fun base64Tool(): Tool = object : Tool {
    override val id = "IMG-25"
    override val group = ToolGroup.IMAGE
    override val engineLabel = "NAT"
    override val icon = Icons.Filled.Code
    override val inputSpec = InputSpec.SingleImage
    override val params = listOf<ToolParam>(
        ToolParam.DropdownParam(
            "mode",
            "Mode",
            "Mode",
            listOf("Encode (gambar → Base64)", "Decode (Base64 → gambar)"),
            "Encode (gambar → Base64)"
        ),
        ToolParam.ToggleParam("dataUri", "Sertakan Data URI (data:image/...)", "Include Data URI prefix", true),
        ToolParam.TextParam("data", "Tempel Base64 (mode decode)", "Paste Base64 (decode mode)", "")
    )

    override fun title(isEnglish: Boolean) = if (isEnglish) "Base64 Encoder / Decoder" else "Encoder / Decoder Base64"
    override fun description(isEnglish: Boolean) = if (isEnglish) "Turn an image into Base64 text, or rebuild a PNG from pasted Base64." else "Ubah gambar menjadi teks Base64, atau bangun PNG dari Base64 yang ditempel."

    override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
        val decodeMode = input.getString("mode", "Encode").startsWith("Decode")
        if (!decodeMode) {
            val uri = input.uris.firstOrNull() ?: error(
                if (ctx.isEnglish) "Select 1 image" else "Pilih 1 gambar"
            )
            emit(ToolProgress.Running(40, if (ctx.isEnglish) "Encoding Base64..." else "Menyandikan Base64..."))
            val base64 = ImageEngine.encodeToBase64(
                ctx.appContext,
                uri,
                input.getBoolean("dataUri", true)
            )
            val dest = ctx.storage.createOutputFile("IMG_B64", "txt")
            dest.writeText(base64)
            emit(
                ToolProgress.Done(
                    ToolResult(
                        outputs = listOf(ToolOutputFile(dest, "text/plain", dest.name)),
                        textOutput = base64.take(2500),
                        summary = if (ctx.isEnglish) {
                            "Base64 length: ${base64.length} chars (${ctx.storage.formatBytes(dest.length())})"
                        } else {
                            "Panjang Base64: ${base64.length} karakter (${ctx.storage.formatBytes(dest.length())})"
                        }
                    )
                )
            )
        } else {
            val raw = input.getString("data", "").ifBlank { input.textInput }.ifBlank {
                readTextInputOrUri(ctx, input)
            }
            require(raw.isNotBlank()) {
                if (ctx.isEnglish) "Paste Base64 data or select a Base64 text file first" else "Tempel data Base64 atau pilih berkas teks Base64 terlebih dahulu"
            }
            emit(ToolProgress.Running(40, if (ctx.isEnglish) "Decoding Base64..." else "Mendekode Base64..."))
            val dest = ctx.storage.createOutputFile("IMG_FROM_B64", "png")
            ImageEngine.decodeFromBase64(raw, dest)
            emit(
                ToolProgress.Done(
                    ToolResult(
                        outputs = listOf(ToolOutputFile(dest, "image/png", dest.name)),
                        summary = if (ctx.isEnglish) {
                            "Base64 decoded to PNG (${ctx.storage.formatBytes(dest.length())})"
                        } else {
                            "Base64 didekode menjadi PNG (${ctx.storage.formatBytes(dest.length())})"
                        }
                    )
                )
            )
        }
    }.flowOn(Dispatchers.IO)
}

// --------------------------------------------------------------------- IMG-26

private fun compressorTool(): Tool = object : Tool {
    override val id = "IMG-26"
    override val group = ToolGroup.IMAGE
    override val engineLabel = "NAT"
    override val icon = Icons.Filled.Compress
    override val inputSpec = InputSpec.SingleImage
    override val params = listOf<ToolParam>(
        ToolParam.DropdownParam("format", "Format", "Format", listOf("WebP", "JPG"), "WebP"),
        ToolParam.SliderParam("quality", "Kualitas (%)", "Quality (%)", 20f, 95f, 70f, 5f, "%"),
        ToolParam.SliderParam("maxDim", "Sisi Maks (px)", "Max Dimension (px)", 640f, 2560f, 1600f, 160f, "px"),
        ToolParam.TextParam("targetKB", "Target Ukuran (KB, kosong = abaikan)", "Target Size (KB, blank = ignore)", "")
    )

    override fun title(isEnglish: Boolean) = if (isEnglish) "Smart Image Compressor" else "Kompresor Gambar Cerdas"
    override fun description(isEnglish: Boolean) = if (isEnglish) "Compare quality vs size, then hit an optional KB target automatically." else "Bandingkan kualitas vs ukuran, lalu capai target KB otomatis bila diisi."

    override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
        val uri = input.uris.firstOrNull() ?: error("Pilih 1 gambar")
        val formatLabel = input.getString("format", "WebP")
        val entry = ImageFormatMatrix.requireEncodable(formatLabel)
        val maxDim = input.getFloat("maxDim", 1600f).toInt()
        val info = ImageIO.readSourceInfo(ctx.appContext, uri, ctx.storage.getDisplayName(uri))
        emit(ToolProgress.Running(15, "Mendekode gambar (maks ${maxDim}px)..."))
        val source = ImageIO.decodePreview(ctx.appContext, uri, maxDim)
        val outW: Int
        val outH: Int
        val rows: List<Triple<Int, Int, ByteArray>>
        val chosenQuality: Int
        val chosenBytes: ByteArray
        var hitTarget = false
        try {
            emit(ToolProgress.Running(30, "Menguji 4 tingkat kualitas..."))
            val probes = listOf(40, 60, 80, 92)
            rows = probes.map { quality ->
                val bytes = ImageIO.encodeToBytes(source, entry.id, quality)
                Triple(quality, bytes.size, bytes)
            }
            val targetKb = input.getString("targetKB", "").trim().toLongOrNull()
            if (targetKb != null && targetKb > 0) {
                emit(ToolProgress.Running(55, "Mencari kualitas untuk ≤ ${targetKb} KB..."))
                val limit = targetKb * 1024
                var best: ByteArray? = null
                var bestQuality = 20
                var low = 10
                var high = 95
                repeat(7) {
                    if (low > high) return@repeat
                    val mid = (low + high) / 2
                    val bytes = ImageIO.encodeToBytes(source, entry.id, mid)
                    if (bytes.size <= limit) {
                        best = bytes
                        bestQuality = mid
                        low = mid + 1
                    } else {
                        high = mid - 1
                    }
                }
                if (best != null) {
                    chosenBytes = best!!
                    chosenQuality = bestQuality
                    hitTarget = true
                } else {
                    val smallest = rows.minByOrNull { it.second }!!
                    chosenBytes = smallest.third
                    chosenQuality = smallest.first
                }
            } else {
                val quality = input.getFloat("quality", 70f).toInt()
                chosenQuality = quality
                chosenBytes = ImageIO.encodeToBytes(source, entry.id, quality)
            }
            outW = source.width
            outH = source.height
        } finally {
            if (!source.isRecycled) source.recycle()
        }
        val targetKb = input.getString("targetKB", "").trim().toLongOrNull()
        val dest = ctx.storage.createOutputFile("IMG_COMP", ImageFormatMatrix.extensionFor(entry.id))
        dest.writeBytes(chosenBytes)
        val table = buildString {
            appendLine("Pratinjau kualitas vs ukuran (${entry.id}, ${outW}×${outH})")
            appendLine("Asli: ${if (info.byteSize > 0) ctx.storage.formatBytes(info.byteSize) else "tidak diketahui"}")
            appendLine("kualitas | ukuran | vs asli")
            rows.forEach { (quality, size, _) ->
                appendLine("${quality.toString().padStart(3)}%     | ${Batch1Support.kb(size.toLong()).padStart(9)} | " +
                    Batch1Support.savings(size.toLong(), info.byteSize))
            }
            appendLine("Dipilih: ${chosenQuality}% → ${Batch1Support.kb(chosenBytes.size.toLong())}" +
                if (hitTarget) " (target ${targetKb} KB tercapai)" else "")
        }
        emit(
            ToolProgress.Done(
                ToolResult(
                    outputs = listOf(ToolOutputFile(dest, ImageFormatMatrix.mimeTypeFor(entry.id), dest.name)),
                    textOutput = table,
                    summary = "${entry.id} ${chosenQuality}% → ${ctx.storage.formatBytes(dest.length())}" +
                        if (hitTarget) " (target ${targetKb} KB tercapai)" else ""
                )
            )
        )
    }.flowOn(Dispatchers.IO)
}

// --------------------------------------------------------------------- IMG-27

private fun webOptimizerTool(): Tool = object : Tool {
    override val id = "IMG-27"
    override val group = ToolGroup.IMAGE
    override val engineLabel = "NAT"
    override val icon = Icons.Filled.Tune
    override val inputSpec = InputSpec.SingleImage
    override val params = listOf<ToolParam>(
        ToolParam.TextParam("widths", "Lebar (px, pisah koma)", "Widths (px, comma separated)", "320,640,960,1280,1920"),
        ToolParam.DropdownParam("format", "Format Keluaran", "Output Format", listOf("Keduanya", "WebP", "JPG"), "Keduanya"),
        ToolParam.SliderParam("quality", "Kualitas (%)", "Quality (%)", 40f, 95f, 78f, 5f, "%")
    )

    override fun title(isEnglish: Boolean) = if (isEnglish) "Web Image Optimizer" else "Optimizer Gambar Web"
    override fun description(isEnglish: Boolean) = if (isEnglish) "Export a responsive WebP/JPG bundle with ready-to-paste srcset markup." else "Ekspor bundel WebP/JPG responsif lengkap dengan markup srcset siap tempel."

    override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
        val uri = input.uris.firstOrNull() ?: error("Pilih 1 gambar")
        val widths = input.getString("widths", "320,640,960,1280,1920")
            .split(',', ' ')
            .mapNotNull { it.trim().toIntOrNull() }
            .filter { it >= 64 }
            .distinct()
            .sorted()
        require(widths.isNotEmpty()) { "Isi minimal satu lebar valid, contoh: 320,640,960" }
        val mode = input.getString("format", "Keduanya")
        val quality = input.getFloat("quality", 78f).toInt()
        val info = ImageIO.readSourceInfo(ctx.appContext, uri, ctx.storage.getDisplayName(uri))
        val usable = widths.filter { it <= info.displayWidth }.ifEmpty { listOf(info.displayWidth) }
        emit(ToolProgress.Running(15, "Mendekode gambar (maks ${usable.max()}px)..."))
        val source = ImageIO.decodePreview(ctx.appContext, uri, usable.max())
        val formats = when (mode) {
            "WebP" -> listOf("WEBP")
            "JPG" -> listOf("JPG")
            else -> listOf("WEBP", "JPG")
        }
        val baseName = Batch1Support.safeName(
            ctx.storage.getDisplayName(uri)?.substringBeforeLast('.') ?: "image"
        )
        val dest = ctx.storage.createOutputFile("IMG_WEB", "zip")
        val manifest = StringBuilder()
        var count = 0
        try {
            ZipOutputStream(FileOutputStream(dest)).use { zip ->
                usable.forEachIndexed { index, width ->
                    emit(ToolProgress.Running(25 + index * 55 / usable.size, "Merender ${width}px..."))
                    val scaled = Batch1Support.scaleToWidth(source, width)
                    try {
                        formats.forEach { format ->
                            val ext = if (format == "WEBP") "webp" else "jpg"
                            val bytes = ImageIO.encodeToBytes(scaled, format, quality)
                            val fileName = "${baseName}-${width}.${ext}"
                            Batch1Support.zipEntry(zip, fileName, bytes)
                            manifest.appendLine("$fileName ${Batch1Support.kb(bytes.size.toLong())}")
                            count++
                        }
                    } finally {
                        if (scaled !== source && !scaled.isRecycled) scaled.recycle()
                    }
                }
                val srcsetWebp = usable.joinToString(", ") { "$baseName-${it}.webp ${it}w" }
                val srcsetJpg = usable.joinToString(", ") { "$baseName-${it}.jpg ${it}w" }
                val snippet = buildString {
                    appendLine("<!-- Responsive image (hasil IMG-27) -->")
                    appendLine("<picture>")
                    if ("WEBP" in formats) {
                        appendLine("""  <source type="image/webp" srcset="$srcsetWebp" sizes="(max-width: 960px) 100vw, ${usable.max()}px">""")
                    }
                    appendLine(
                        """  <img src="$baseName-${usable.first()}.jpg" """ +
                            """srcset="$srcsetJpg" """ +
                            """sizes="(max-width: 960px) 100vw, ${usable.max()}px" """ +
                            """width="${usable.max()}" height="${(info.displayHeight.toFloat() * usable.max() / info.displayWidth).roundToInt()}" """ +
                            """loading="lazy" decoding="async" alt="">"""
                    )
                    appendLine("</picture>")
                }
                Batch1Support.zipEntry(zip, "responsive-snippet.html", snippet.toByteArray())
                Batch1Support.zipEntry(
                    zip,
                    "manifest.txt",
                    ("Sumber: ${info.displayWidth}×${info.displayHeight} ${info.formatLabel}\n" +
                        "Lebar: ${usable.joinToString(", ")} px\n" +
                        "Format: ${formats.joinToString(", ")}\nKualitas: $quality%\n\n" + manifest).toByteArray()
                )
            }
        } finally {
            if (!source.isRecycled) source.recycle()
        }
        emit(
            ToolProgress.Done(
                ToolResult(
                    outputs = listOf(ToolOutputFile(dest, "application/zip", dest.name)),
                    textOutput = manifest.toString(),
                    summary = "$count berkas responsif (${formats.joinToString("/")}) dalam ZIP " +
                        "(${ctx.storage.formatBytes(dest.length())})"
                )
            )
        )
    }.flowOn(Dispatchers.IO)
}

// --------------------------------------------------------------------- IMG-28

private fun paletteTool(): Tool = object : Tool {
    override val id = "IMG-28"
    override val group = ToolGroup.IMAGE
    override val engineLabel = "K-MEANS"
    override val icon = Icons.Filled.Palette
    override val inputSpec = InputSpec.SingleImage
    override val params = listOf<ToolParam>(
        ToolParam.SliderParam("count", "Jumlah Warna", "Color Count", 3f, 8f, 5f, 1f, "")
    )

    override fun title(isEnglish: Boolean) = if (isEnglish) "Color Palette Extractor" else "Ekstraktor Palet Warna"
    override fun description(isEnglish: Boolean) = if (isEnglish) "Extract dominant hex colors with deterministic K-Means clustering." else "Ekstrak warna dominan (Hex) dengan klaster K-Means deterministik."

    override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
        val uri = input.uris.firstOrNull() ?: error("Pilih 1 gambar")
        emit(ToolProgress.Running(35, "Menjalankan klaster K-Means..."))
        val source = ImageIO.decodePreview(ctx.appContext, uri, 320)
        var card: Bitmap? = null
        val (colors, dest) = try {
            val (paletteColors, paletteCard) = ImageEngine.extractKMeansPalette(source, input.getFloat("count", 5f).toInt())
            card = paletteCard
            val outFile = ctx.storage.createOutputFile("PALETTE", "png")
            Batch1Support.encodeFile(paletteCard, outFile, "PNG")
            paletteColors to outFile
        } finally {
            if (card != null && !card.isRecycled) card.recycle()
            if (!source.isRecycled) source.recycle()
        }
        val hexList = colors.joinToString("\n") { String.format(Locale.US, "#%06X", 0xFFFFFF and it) }
        emit(
            ToolProgress.Done(
                ToolResult(
                    outputs = listOf(ToolOutputFile(dest, "image/png", dest.name)),
                    textOutput = hexList,
                    summary = "${colors.size} warna dominan (K-Means, urut jumlah piksel)"
                )
            )
        )
    }.flowOn(Dispatchers.IO)
}

// --------------------------------------------------------------------- IMG-29

private fun exifEditorTool(): Tool = object : Tool {
    override val id = "IMG-29"
    override val group = ToolGroup.IMAGE
    override val engineLabel = "EXIF"
    override val icon = Icons.Filled.Info
    override val inputSpec = InputSpec.SingleImage
    override val params = listOf<ToolParam>(
        ToolParam.ToggleParam("strip", "Hapus Semua EXIF/GPS", "Strip all EXIF/GPS", false),
        ToolParam.ToggleParam("removeGps", "Hapus GPS Saja", "Remove GPS only", true),
        ToolParam.TextParam("artist", "Artist (kosong = biarkan)", "Artist (blank = keep)", ""),
        ToolParam.TextParam("copyright", "Copyright (kosong = biarkan)", "Copyright (blank = keep)", ""),
        ToolParam.TextParam("description", "Deskripsi (kosong = biarkan)", "Description (blank = keep)", "")
    )

    override fun title(isEnglish: Boolean) = if (isEnglish) "EXIF Editor & Stripper" else "Editor & Penghapus EXIF"
    override fun description(isEnglish: Boolean) = if (isEnglish) "Inspect every tag, edit artist/copyright, and export a clean copy without re-encoding." else "Periksa seluruh tag, ubah artist/copyright, dan ekspor salinan bersih tanpa encode ulang."

    override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
        val uri = input.uris.firstOrNull() ?: error("Pilih 1 gambar")
        emit(ToolProgress.Running(25, "Membaca metadata..."))
        val report = ImageIO.exifReport(ctx.appContext, uri)
        val info = ImageIO.readSourceInfo(ctx.appContext, uri, ctx.storage.getDisplayName(uri))
        val sourceEntry = ImageFormatMatrix.byLabel(info.formatLabel)
        val extension = sourceEntry
            ?.takeIf { it.canDecode }
            ?.let { ImageFormatMatrix.extensionFor(it.id) }
            ?: "jpg"
        val dest = ctx.storage.createOutputFile("IMG_META", extension)
        ctx.appContext.contentResolver.openInputStream(uri)?.use { stream ->
            dest.outputStream().use { out -> stream.copyTo(out, 64 * 1024) }
        } ?: throw IllegalStateException("Gambar tidak dapat dibaca")
        emit(ToolProgress.Running(70, "Menulis metadata..."))
        var stripped = false
        if (input.getBoolean("strip", false)) {
            stripped = ImageIO.stripMetadata(dest)
        }
        val artist = input.getString("artist", "").trim().ifBlank { null }
        val copyright = input.getString("copyright", "").trim().ifBlank { null }
        val description = input.getString("description", "").trim().ifBlank { null }
        val edited = ImageIO.writeExifAttributes(
            dest,
            artist,
            copyright,
            description,
            input.getBoolean("removeGps", true)
        )
        val text = buildString {
            appendLine("File: ${ctx.storage.getDisplayName(uri) ?: "gambar"}")
            appendLine("Dimensi: ${info.displayWidth}×${info.displayHeight} (orientasi EXIF ${info.orientation})")
            appendLine("Ukuran: ${if (info.byteSize > 0) ctx.storage.formatBytes(info.byteSize) else "tidak diketahui"}")
            appendLine("Format: ${info.formatLabel}")
            appendLine("--- Tag EXIF ---")
            if (report.isEmpty()) appendLine("(tidak ada tag EXIF terbaca)") else report.forEach { (key, value) ->
                appendLine("$key: $value")
            }
            appendLine("--- Aksi ---")
            appendLine("Hapus semua metadata: ${if (stripped) "berhasil" else "tidak dilakukan/gagal"}")
            appendLine("Hapus GPS: ${if (input.getBoolean("removeGps", true)) "ya" else "tidak"}")
            appendLine("Artist/Copyright/Deskripsi: ${if (edited) "tersimpan" else "tidak berubah"}")
            appendLine("Catatan: piksel tidak di-encode ulang; metadata ditulis ulang pada salinan byte-per-byte.")
        }
        emit(
            ToolProgress.Done(
                ToolResult(
                    outputs = listOf(ToolOutputFile(dest, ImageFormatMatrix.mimeTypeFor(info.formatLabel), dest.name)),
                    textOutput = text,
                    summary = "Metadata ${if (stripped) "dibersihkan" else "diperbarui"} • ${report.size} tag terbaca"
                )
            )
        )
    }.flowOn(Dispatchers.IO)
}

// --------------------------------------------------------------------- IMG-30

private fun sizeAnalyzerTool(): Tool = object : Tool {
    override val id = "IMG-30"
    override val group = ToolGroup.IMAGE
    override val engineLabel = "NAT"
    override val icon = Icons.Filled.Info
    override val inputSpec = InputSpec.SingleImage
    override val params = emptyList<ToolParam>()

    override fun title(isEnglish: Boolean) = if (isEnglish) "Image File Size Analyzer" else "Analisis Ukuran File Gambar"
    override fun description(isEnglish: Boolean) = if (isEnglish) "Encode one photo into every supported format and report the real byte sizes." else "Encode satu foto ke setiap format yang didukung lalu laporkan ukuran byte sebenarnya."

    override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
        val uri = input.uris.firstOrNull() ?: error("Pilih 1 gambar")
        emit(ToolProgress.Running(15, "Mendekode gambar..."))
        val info = ImageIO.readSourceInfo(ctx.appContext, uri, ctx.storage.getDisplayName(uri))
        val source = ImageIO.decodePreview(ctx.appContext, uri, 1600)
        val candidates = listOf(
            "PNG" to 100,
            "JPG" to 90,
            "JPG" to 75,
            "JPG" to 60,
            "WEBP" to 90,
            "WEBP" to 75,
            "WEBP" to 60,
            "PDF" to 100
        )
        val rows = mutableListOf<Triple<String, Int, Long>>()
        val dimensions = try {
            candidates.forEachIndexed { index, (format, quality) ->
                emit(ToolProgress.Running(30 + index * 60 / candidates.size, "Encode $format ${quality}%..."))
                val size = ImageIO.encodeToBytes(source, format, quality).size.toLong()
                rows += Triple(format, quality, size)
            }
            "${source.width}×${source.height}"
        } finally {
            if (!source.isRecycled) source.recycle()
        }
        val bmpEstimate = info.displayWidth.toLong() * info.displayHeight * 3 + 54
        val best = rows.minByOrNull { it.third }
        val text = buildString {
            appendLine("Analisis ukuran untuk ${ctx.storage.getDisplayName(uri) ?: "gambar"}")
            appendLine("Sumber: ${info.displayWidth}×${info.displayHeight} ${info.formatLabel}, " +
                (if (info.byteSize > 0) ctx.storage.formatBytes(info.byteSize) else "ukuran tidak diketahui"))
            appendLine("Uji encode pada ${dimensions}px")
            appendLine()
            appendLine("Format | Kualitas | Ukuran | vs sumber")
            rows.sortedBy { it.third }.forEach { (format, quality, size) ->
                appendLine(
                    "${format.padEnd(5)} | ${quality.toString().padStart(3)}%     | " +
                        "${Batch1Support.kb(size).padStart(9)} | ${Batch1Support.savings(size, info.byteSize)}"
                )
            }
            appendLine()
            appendLine("Estimasi BMP (tidak di-encode): ${Batch1Support.kb(bmpEstimate)} (${info.displayWidth}×${info.displayHeight}×3+54)")
            appendLine("Tidak diuji: HEIC/AVIF (tanpa encoder di Android), SVG/RAW/TIFF/PSD (tidak didukung).")
            appendLine("Terkecil: ${best?.first} ${best?.second}% → ${best?.let { Batch1Support.kb(it.third) }}")
        }
        val dest = ctx.storage.createOutputFile("IMG_SIZE_REPORT", "txt")
        dest.writeText(text)
        emit(
            ToolProgress.Done(
                ToolResult(
                    outputs = listOf(ToolOutputFile(dest, "text/plain", dest.name)),
                    textOutput = text,
                    summary = "${rows.size} format diuji • terkecil ${best?.first} ${best?.second}% " +
                        "${best?.let { Batch1Support.kb(it.third) }}"
                )
            )
        )
    }.flowOn(Dispatchers.IO)
}
