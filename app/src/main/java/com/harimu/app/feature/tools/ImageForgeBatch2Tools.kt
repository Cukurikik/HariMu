package com.harimu.app.feature.tools

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.BurstMode
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.FilterVintage
import androidx.compose.material.icons.filled.Flare
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Tune
import com.harimu.app.core.engines.GifEncoder
import com.harimu.app.core.engines.ImageBlendModes
import com.harimu.app.core.engines.ImageEngine
import com.harimu.app.core.engines.ImageFilterPipeline
import com.harimu.app.core.engines.ImageFilterSpec
import com.harimu.app.core.engines.ImageFormatMatrix
import com.harimu.app.core.engines.ImageLutEngine
import com.harimu.app.core.engines.ImageOverlayEngine
import com.harimu.app.core.engines.ImageIO
import com.harimu.app.core.engines.VideoEngine
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
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Image Forge batch 2 (FASE 10): IMG-32, 33, 37-41, 43-46.
 * IMG-47 lives in its own studio screen (`feature/studios/ImageMatrixViewModel`).
 */
fun imageForgeBatch2Tools(): List<Tool> = listOf(
    overlayTool(),
    filtersTool(),
    shadowGlowTool(),
    stickerTool(),
    lutTool(),
    vintageTool(),
    flareTool(),
    batchTool(),
    spriteSheetTool(),
    gifTool(),
    videoFrameTool()
)

private object B2 {

    fun csv(value: String): String =
        value.replace('\n', ' ').replace('\r', ' ').replace(',', ';').replace('"', '\'')

    fun nameWithoutExtension(raw: String?): String {
        val name = raw?.substringAfterLast('/')?.substringAfterLast(':') ?: "gambar"
        return name.substringBeforeLast('.', name).ifBlank { "gambar" }
    }

    fun extensionOf(raw: String?): String {
        val name = raw ?: return "jpg"
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext.ifBlank { "jpg" }
    }

    fun safeFileName(raw: String): String =
        raw.trim().replace(Regex("[^A-Za-z0-9._-]+"), "_").trim('_').ifBlank { "file" }

    fun addZipEntry(zip: ZipOutputStream, name: String, bytes: ByteArray) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(bytes)
        zip.closeEntry()
    }
}

// --------------------------------------------------------------------- IMG-32

private fun overlayTool(): Tool = object : Tool {
    override val id = "IMG-32"
    override val group = ToolGroup.IMAGE
    override val engineLabel = "NAT/PorterDuff"
    override val icon = Icons.Filled.Layers
    override val inputSpec = InputSpec.MultipleImages
    override val params = listOf<ToolParam>(
        ToolParam.DropdownParam("blend", "Mode Blend", "Blend Mode", ImageBlendModes.labels, "Overlay"),
        ToolParam.SliderParam("opacity", "Opasitas Lapisan Atas (%)", "Top Layer Opacity (%)", 5f, 100f, 80f, 5f, "%"),
        ToolParam.SliderParam("scale", "Skala Lapisan Atas (%)", "Top Layer Scale (%)", 10f, 200f, 100f, 5f, "%"),
        ToolParam.SliderParam("offsetX", "Geser X (%)", "Offset X (%)", -100f, 100f, 0f, 5f, "%"),
        ToolParam.SliderParam("offsetY", "Geser Y (%)", "Offset Y (%)", -100f, 100f, 0f, 5f, "%"),
        ToolParam.DropdownParam("format", "Format Simpan", "Save Format", listOf("PNG", "JPG", "WEBP"), "PNG")
    )

    override fun title(isEnglish: Boolean) = if (isEnglish) "Image Overlay Blender" else "Overlay & Blender Gambar"
    override fun description(isEnglish: Boolean) = if (isEnglish) "Composite a second photo over the first with 9 blend modes." else "Gabungkan foto kedua di atas foto pertama dengan 9 mode blend."

    override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
        require(input.uris.size >= 2) { "Pilih 2 gambar: dasar lalu lapisan atas" }
        val entry = ImageFormatMatrix.requireEncodable(input.getString("format", "PNG"))
        emit(ToolProgress.Running(20, "Mendekode gambar dasar..."))
        val base = ImageIO.decodeFullGuarded(ctx.appContext, input.uris[0], 24_000_000L)
        emit(ToolProgress.Running(45, "Mendekode lapisan atas..."))
        val top = ImageIO.decodeFullGuarded(ctx.appContext, input.uris[1], 24_000_000L)
        emit(ToolProgress.Running(70, "Menggabungkan layer..."))
        val out = ImageOverlayEngine.blend(
            base = base,
            top = top,
            blendMode = input.getString("blend", "Overlay"),
            opacityPct = input.getFloat("opacity", 80f).toInt(),
            scalePct = input.getFloat("scale", 100f).toInt(),
            offsetXPct = input.getFloat("offsetX", 0f),
            offsetYPct = input.getFloat("offsetY", 0f)
        )
        base.recycle()
        top.recycle()
        val dest = ctx.storage.createOutputFile("IMG_OVERLAY", ImageFormatMatrix.extensionFor(entry.id))
        ImageIO.encode(out, dest, entry.id, 95)
        val size = "${out.width}×${out.height}"
        out.recycle()
        emit(
            ToolProgress.Done(
                ToolResult(
                    outputs = listOf(ToolOutputFile(dest, ImageFormatMatrix.mimeTypeFor(entry.id), dest.name)),
                    summary = "Blend ${input.getString("blend", "Overlay")} pada $size " +
                        "(${ctx.storage.formatBytes(dest.length())})"
                )
            )
        )
    }.flowOn(Dispatchers.IO)
}

// --------------------------------------------------------------------- IMG-33

private fun filtersTool(): Tool = object : Tool {
    override val id = "IMG-33"
    override val group = ToolGroup.IMAGE
    override val engineLabel = "GPU+MAT"
    override val icon = Icons.Filled.AutoFixHigh
    override val inputSpec = InputSpec.SingleImage
    override val params = listOf<ToolParam>(
        ToolParam.DropdownParam("preset", "Preset", "Preset", ImageFilterPipeline.presets, "Vintage"),
        ToolParam.SliderParam("brightness", "Kecerahan", "Brightness", -80f, 80f, 0f, 2f, ""),
        ToolParam.SliderParam("contrast", "Kontras", "Contrast", 0.5f, 1.8f, 1.05f, 0.05f, "x"),
        ToolParam.SliderParam("saturation", "Saturasi", "Saturation", 0f, 1.6f, 1f, 0.05f, "x"),
        ToolParam.SliderParam("temperature", "Suhu Warna", "Temperature", -50f, 50f, 0f, 5f, ""),
        ToolParam.SliderParam("blur", "Blur (%)", "Blur (%)", 0f, 12f, 0f, 1f, "%"),
        ToolParam.SliderParam("vignette", "Vignette (%)", "Vignette (%)", 0f, 70f, 0f, 5f, "%"),
        ToolParam.DropdownParam("format", "Format Simpan", "Save Format", listOf("JPG", "PNG", "WEBP"), "JPG")
    )

    override fun title(isEnglish: Boolean) = if (isEnglish) "Image Filters (GPU Preview)" else "Filter Gambar (Pratinjau GPU)"
    override fun description(isEnglish: Boolean) = if (isEnglish) "Adjust presets, contrast, saturation, blur and vignette with a live GPU preview of the same pipeline used for export." else "Atur preset, kontras, saturasi, blur dan vignette dengan pratinjau GPU dari pipeline yang sama dengan ekspor."

    override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
        val uri = input.uris.firstOrNull() ?: error("Pilih 1 gambar")
        val entry = ImageFormatMatrix.requireEncodable(input.getString("format", "JPG"))
        val spec = ImageFilterSpec(
            preset = input.getString("preset", "Vintage"),
            brightness = input.getFloat("brightness", 0f),
            contrast = input.getFloat("contrast", 1.05f),
            saturation = input.getFloat("saturation", 1f),
            temperature = input.getFloat("temperature", 0f),
            blurRadius = input.getFloat("blur", 0f).toInt(),
            vignette = input.getFloat("vignette", 0f).toInt()
        )
        emit(ToolProgress.Running(25, "Mendekode gambar resolusi penuh..."))
        val source = ImageIO.decodeFullGuarded(ctx.appContext, uri)
        emit(ToolProgress.Running(60, "Menerapkan pipeline filter..."))
        val out = ImageFilterPipeline.apply(source, spec)
        source.recycle()
        val dest = ctx.storage.createOutputFile("IMG_FLT", ImageFormatMatrix.extensionFor(entry.id))
        ImageIO.encode(out, dest, entry.id, 94)
        val size = "${out.width}×${out.height}"
        out.recycle()
        emit(
            ToolProgress.Done(
                ToolResult(
                    outputs = listOf(ToolOutputFile(dest, ImageFormatMatrix.mimeTypeFor(entry.id), dest.name)),
                    summary = "Preset ${spec.preset} + pipeline penuh pada $size " +
                        "(${ctx.storage.formatBytes(dest.length())})"
                )
            )
        )
    }.flowOn(Dispatchers.IO)
}

// --------------------------------------------------------------------- IMG-37

private fun shadowGlowTool(): Tool = object : Tool {
    override val id = "IMG-37"
    override val group = ToolGroup.IMAGE
    override val engineLabel = "NAT/Blur"
    override val icon = Icons.Filled.BlurOn
    override val inputSpec = InputSpec.SingleImage
    override val params = listOf<ToolParam>(
        ToolParam.DropdownParam("shape", "Bentuk Siluet", "Silhouette Shape", listOf("Alpha subjek", "Kotak"), "Alpha subjek"),
        ToolParam.TextParam("shadowColor", "Warna Bayangan (#Hex)", "Shadow Color (#Hex)", "#000000"),
        ToolParam.SliderParam("shadowBlur", "Radius Bayangan (% sisi)", "Shadow Radius (% edge)", 0f, 20f, 4f, 1f, "%"),
        ToolParam.SliderParam("shadowX", "Geser X Bayangan (%)", "Shadow X Offset (%)", -20f, 20f, 2f, 1f, "%"),
        ToolParam.SliderParam("shadowY", "Geser Y Bayangan (%)", "Shadow Y Offset (%)", -20f, 20f, 3f, 1f, "%"),
        ToolParam.TextParam("glowColor", "Warna Glow (#Hex)", "Glow Color (#Hex)", "#FFE082"),
        ToolParam.SliderParam("glow", "Kekuatan Glow (%)", "Glow Strength (%)", 0f, 80f, 35f, 5f, "%"),
        ToolParam.SliderParam("padding", "Padding Kanvas (%)", "Canvas Padding (%)", 0f, 20f, 8f, 1f, "%")
    )

    override fun title(isEnglish: Boolean) = if (isEnglish) "Shadow & Glow" else "Efek Bayangan & Glow"
    override fun description(isEnglish: Boolean) = if (isEnglish) "Add a soft drop shadow or outer glow around the subject or frame." else "Tambahkan drop shadow lembut atau outer glow di sekitar subjek atau bingkai."

    override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
        val uri = input.uris.firstOrNull() ?: error("Pilih 1 gambar")
        emit(ToolProgress.Running(25, "Mendekode gambar..."))
        val source = ImageIO.decodeFullGuarded(ctx.appContext, uri)
        emit(ToolProgress.Running(60, "Menyusun bayangan & glow..."))
        val out = ImageOverlayEngine.shadowGlow(
            source = source,
            shape = input.getString("shape", "Alpha subjek"),
            shadowHex = input.getString("shadowColor", "#000000"),
            shadowBlurPct = input.getFloat("shadowBlur", 4f),
            shadowOffsetXPct = input.getFloat("shadowX", 2f),
            shadowOffsetYPct = input.getFloat("shadowY", 3f),
            glowHex = input.getString("glowColor", "#FFE082"),
            glowStrengthPct = input.getFloat("glow", 35f).toInt(),
            paddingPct = input.getFloat("padding", 8f).toInt()
        )
        source.recycle()
        val dest = ctx.storage.createOutputFile("IMG_SHADOW", "png")
        ImageIO.encode(out, dest, "PNG", 100)
        val size = "${out.width}×${out.height}"
        out.recycle()
        emit(
            ToolProgress.Done(
                ToolResult(
                    outputs = listOf(ToolOutputFile(dest, "image/png", dest.name)),
                    summary = "Bayangan + glow pada kanvas $size (${ctx.storage.formatBytes(dest.length())})"
                )
            )
        )
    }.flowOn(Dispatchers.IO)
}

// --------------------------------------------------------------------- IMG-38

private fun stickerTool(): Tool = object : Tool {
    override val id = "IMG-38"
    override val group = ToolGroup.IMAGE
    override val engineLabel = "NAT"
    override val icon = Icons.Filled.EmojiEmotions
    override val inputSpec = InputSpec.SingleImage
    override val params = listOf<ToolParam>(
        ToolParam.TextParam("emoji", "Stiker / Emoji", "Sticker / Emoji", "⭐"),
        ToolParam.SliderParam("xPct", "Posisi X (%)", "Position X (%)", 0f, 100f, 75f, 1f, "%"),
        ToolParam.SliderParam("yPct", "Posisi Y (%)", "Position Y (%)", 0f, 100f, 25f, 1f, "%"),
        ToolParam.SliderParam("sizePct", "Ukuran (% lebar)", "Size (% width)", 2f, 60f, 18f, 1f, "%"),
        ToolParam.SliderParam("rotation", "Rotasi (°)", "Rotation (°)", -180f, 180f, 0f, 5f, "°"),
        ToolParam.SliderParam("opacity", "Opasitas (%)", "Opacity (%)", 10f, 100f, 100f, 5f, "%")
    )

    override fun title(isEnglish: Boolean) = if (isEnglish) "Sticker / Emoji Overlay" else "Overlay Stiker & Emoji"
    override fun description(isEnglish: Boolean) = if (isEnglish) "Stamp any emoji or text sticker with position, size, rotation and opacity." else "Tempel emoji atau stiker teks dengan posisi, ukuran, rotasi dan opasitas."

    override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
        val uri = input.uris.firstOrNull() ?: error("Pilih 1 gambar")
        val emoji = input.getString("emoji", "⭐").ifBlank { "⭐" }
        emit(ToolProgress.Running(30, "Mendekode gambar..."))
        val source = ImageIO.decodeFullGuarded(ctx.appContext, uri)
        val out = ImageOverlayEngine.addSticker(
            source = source,
            emoji = emoji,
            xPct = input.getFloat("xPct", 75f),
            yPct = input.getFloat("yPct", 25f),
            sizePct = input.getFloat("sizePct", 18f),
            rotationDeg = input.getFloat("rotation", 0f),
            opacityPct = input.getFloat("opacity", 100f).toInt()
        )
        source.recycle()
        val dest = ctx.storage.createOutputFile("IMG_STICKER", "png")
        ImageIO.encode(out, dest, "PNG", 100)
        out.recycle()
        emit(
            ToolProgress.Done(
                ToolResult(
                    outputs = listOf(ToolOutputFile(dest, "image/png", dest.name)),
                    summary = "Stiker \"$emoji\" ditempel (${ctx.storage.formatBytes(dest.length())})"
                )
            )
        )
    }.flowOn(Dispatchers.IO)
}

// --------------------------------------------------------------------- IMG-39

private fun lutTool(): Tool = object : Tool {
    override val id = "IMG-39"
    override val group = ToolGroup.IMAGE
    override val engineLabel = "NAT/LUT"
    override val icon = Icons.Filled.Tune
    override val inputSpec = InputSpec.MultipleImages
    override val params = listOf<ToolParam>(
        ToolParam.DropdownParam(
            "mode",
            "Sumber LUT",
            "LUT Source",
            listOf("Preset bawaan", "File .cube (tempel teks)", "Strip PNG (gambar ke-2)"),
            "Preset bawaan"
        ),
        ToolParam.DropdownParam("preset", "Preset LUT", "LUT Preset", ImageLutEngine.presetNames, "Teal & Orange"),
        ToolParam.TextParam("cubeText", "Isi file .cube", "Contents of .cube file", ""),
        ToolParam.SliderParam("intensity", "Intensitas LUT (%)", "LUT Intensity (%)", 0f, 100f, 85f, 5f, "%")
    )

    override fun title(isEnglish: Boolean) = if (isEnglish) "LUT Color Filter" else "Filter Warna LUT"
    override fun description(isEnglish: Boolean) = if (isEnglish) "Apply a .cube LUT, a PNG lookup strip, or a built-in film look." else "Terapkan LUT .cube, strip PNG, atau preset film bawaan."

    override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
        val uri = input.uris.firstOrNull() ?: error("Pilih foto yang akan diberi LUT")
        val mode = input.getString("mode", "Preset bawaan")
        emit(ToolProgress.Running(20, "Menyiapkan LUT..."))
        val lut = when {
            mode.startsWith("File") -> {
                val text = input.getString("cubeText", "").trim()
                require(text.isNotBlank()) {
                    "Tempel isi file .cube pada kolom teks, atau pilih mode preset/strip PNG."
                }
                ImageLutEngine.parseCube(text, "Cube tempel")
            }
            mode.startsWith("Strip") -> {
                require(input.uris.size >= 2) {
                    "Pilih gambar ke-2 sebagai strip LUT (lebar = size×size, tinggi = size)."
                }
                emit(ToolProgress.Running(35, "Membaca strip PNG..."))
                val strip = ImageIO.decodePreview(ctx.appContext, input.uris[1], 4096)
                val size = sqrt(strip.width.toFloat()).roundToInt()
                require(size * size == strip.width) {
                    "Lebar strip ${strip.width}px bukan kuadrat sempurna (butuh size×size)."
                }
                val cube = ImageLutEngine.fromPngStrip(strip, size)
                strip.recycle()
                cube
            }
            else -> ImageLutEngine.preset(input.getString("preset", "Teal & Orange"))
        }
        emit(ToolProgress.Running(55, "Mendekode foto..."))
        val source = ImageIO.decodeFullGuarded(ctx.appContext, uri, 36_000_000L)
        emit(ToolProgress.Running(75, "Menerapkan LUT ${lut.title} (${lut.size}³)..."))
        val out = ImageLutEngine.apply(source, lut, input.getFloat("intensity", 85f).toInt())
        source.recycle()
        val dest = ctx.storage.createOutputFile("IMG_LUT", "png")
        ImageIO.encode(out, dest, "PNG", 100)
        out.recycle()
        emit(
            ToolProgress.Done(
                ToolResult(
                    outputs = listOf(ToolOutputFile(dest, "image/png", dest.name)),
                    summary = "LUT \"${lut.title}\" (${lut.size}³) intensitas ${input.getFloat("intensity", 85f).toInt()}% " +
                        "(${ctx.storage.formatBytes(dest.length())})"
                )
            )
        )
    }.flowOn(Dispatchers.IO)
}

// --------------------------------------------------------------------- IMG-40

private fun vintageTool(): Tool = object : Tool {
    override val id = "IMG-40"
    override val group = ToolGroup.IMAGE
    override val engineLabel = "NAT/GL"
    override val icon = Icons.Filled.FilterVintage
    override val inputSpec = InputSpec.SingleImage
    override val params = listOf<ToolParam>(
        ToolParam.DropdownParam(
            "preset",
            "Simulasi Film",
            "Film Simulation",
            listOf("Kodak Portra", "Fuji Classic", "Cinema Fade", "Mono Grain"),
            "Kodak Portra"
        ),
        ToolParam.SliderParam("warmth", "Kehangatan", "Warmth", -40f, 40f, 12f, 2f, ""),
        ToolParam.SliderParam("fade", "Fade (angkat hitam, %)", "Fade (lift blacks, %)", 0f, 60f, 12f, 2f, "%"),
        ToolParam.SliderParam("grain", "Grain (%)", "Grain (%)", 0f, 60f, 18f, 2f, "%"),
        ToolParam.SliderParam("vignette", "Vignette (%)", "Vignette (%)", 0f, 70f, 22f, 2f, "%")
    )

    override fun title(isEnglish: Boolean) = if (isEnglish) "Vintage / Film Simulation" else "Simulasi Film Vintage"
    override fun description(isEnglish: Boolean) = if (isEnglish) "Film looks with warmth, lifted blacks, grain and vignette." else "Tampilan film dengan kehangatan, hitam terangkat, grain dan vignette."

    override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
        val uri = input.uris.firstOrNull() ?: error("Pilih 1 gambar")
        emit(ToolProgress.Running(25, "Mendekode gambar resolusi penuh..."))
        val source = ImageIO.decodeFullGuarded(ctx.appContext, uri)
        emit(ToolProgress.Running(60, "Merender grain & vignette..."))
        val out = ImageOverlayEngine.vintageFilm(
            source = source,
            preset = input.getString("preset", "Kodak Portra"),
            warmth = input.getFloat("warmth", 12f),
            fadePct = input.getFloat("fade", 12f).toInt(),
            grainPct = input.getFloat("grain", 18f).toInt(),
            vignettePct = input.getFloat("vignette", 22f).toInt()
        )
        source.recycle()
        val dest = ctx.storage.createOutputFile("IMG_FILM", "jpg")
        ImageIO.encode(out, dest, "JPG", 92)
        out.recycle()
        emit(
            ToolProgress.Done(
                ToolResult(
                    outputs = listOf(ToolOutputFile(dest, "image/jpeg", dest.name)),
                    summary = "Film ${input.getString("preset", "Kodak Portra")} diterapkan " +
                        "(${ctx.storage.formatBytes(dest.length())})"
                )
            )
        )
    }.flowOn(Dispatchers.IO)
}

// --------------------------------------------------------------------- IMG-41

private fun flareTool(): Tool = object : Tool {
    override val id = "IMG-41"
    override val group = ToolGroup.IMAGE
    override val engineLabel = "NAT"
    override val icon = Icons.Filled.Flare
    override val inputSpec = InputSpec.SingleImage
    override val params = listOf<ToolParam>(
        ToolParam.SliderParam("xPct", "Posisi Sumber Cahaya X (%)", "Light X (%)", 0f, 100f, 78f, 1f, "%"),
        ToolParam.SliderParam("yPct", "Posisi Sumber Cahaya Y (%)", "Light Y (%)", 0f, 100f, 22f, 1f, "%"),
        ToolParam.SliderParam("intensity", "Intensitas (%)", "Intensity (%)", 10f, 100f, 60f, 5f, "%"),
        ToolParam.SliderParam("streak", "Panjang Streak (% layar)", "Streak Length (% frame)", 0f, 120f, 60f, 5f, "%"),
        ToolParam.SliderParam("ghosts", "Jumlah Ghost", "Ghost Count", 0f, 4f, 2f, 1f, ""),
        ToolParam.TextParam("warm", "Warna Cahaya (#Hex)", "Light Color (#Hex)", "#FFD9A0")
    )

    override fun title(isEnglish: Boolean) = if (isEnglish) "Lens Flare Generator" else "Generator Lens Flare"
    override fun description(isEnglish: Boolean) = if (isEnglish) "Add an anamorphic streak, halo and ghosting with screen blending." else "Tambahkan streak anamorfik, halo dan ghost dengan blending screen."

    override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
        val uri = input.uris.firstOrNull() ?: error("Pilih 1 gambar")
        emit(ToolProgress.Running(30, "Mendekode gambar..."))
        val source = ImageIO.decodeFullGuarded(ctx.appContext, uri)
        val out = ImageOverlayEngine.lensFlare(
            source = source,
            xPct = input.getFloat("xPct", 78f),
            yPct = input.getFloat("yPct", 22f),
            intensityPct = input.getFloat("intensity", 60f).toInt(),
            streakPct = input.getFloat("streak", 60f).toInt(),
            ghostCount = input.getFloat("ghosts", 2f).toInt(),
            warmHex = input.getString("warm", "#FFD9A0")
        )
        source.recycle()
        val dest = ctx.storage.createOutputFile("IMG_FLARE", "jpg")
        ImageIO.encode(out, dest, "JPG", 93)
        out.recycle()
        emit(
            ToolProgress.Done(
                ToolResult(
                    outputs = listOf(ToolOutputFile(dest, "image/jpeg", dest.name)),
                    summary = "Lens flare intensitas ${input.getFloat("intensity", 60f).toInt()}% " +
                        "(${ctx.storage.formatBytes(dest.length())})"
                )
            )
        )
    }.flowOn(Dispatchers.IO)
}

// --------------------------------------------------------------------- IMG-43

private val batchOperations = listOf(
    "Konversi Format",
    "Resize",
    "Watermark",
    "Kompres",
    "Metadata (hapus EXIF)",
    "Thumbnail",
    "Rename",
    "Hapus Latar (heuristik)"
)

private fun batchTool(): Tool = object : Tool {
    override val id = "IMG-43"
    override val group = ToolGroup.IMAGE
    override val engineLabel = "BATCH"
    override val icon = Icons.Filled.BurstMode
    override val inputSpec = InputSpec.MultipleImages
    override val params = listOf<ToolParam>(
        ToolParam.DropdownParam("operation", "Operasi Batch", "Batch Operation", batchOperations, "Konversi Format"),
        ToolParam.DropdownParam("format", "Format Keluaran", "Output Format", listOf("JPG", "PNG", "WEBP"), "JPG"),
        ToolParam.SliderParam("maxEdge", "Sisi Maks (px)", "Max Edge (px)", 320f, 2400f, 1600f, 160f, "px"),
        ToolParam.SliderParam("quality", "Kualitas (%)", "Quality (%)", 40f, 95f, 82f, 5f, "%"),
        ToolParam.TextParam("watermark", "Teks Watermark (mode Watermark)", "Watermark Text (Watermark mode)", "© HariMu"),
        ToolParam.TextParam("renamePattern", "Pola Nama ({n} nomor, {name} nama asli)", "Name Pattern ({n} index, {name} original)", "HariMu_{n}_{name}")
    )

    override fun title(isEnglish: Boolean) = if (isEnglish) "Batch Image Processing" else "Pemrosesan Gambar Batch"
    override fun description(isEnglish: Boolean) = if (isEnglish) "Run one operation over many photos and get a per-file success/failure report in a ZIP." else "Jalankan satu operasi ke banyak foto sekaligus dengan laporan sukses/gagal per file dalam ZIP."

    override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
        require(input.uris.isNotEmpty()) { "Pilih minimal 1 gambar" }
        val operation = input.getString("operation", "Konversi Format")
        val formatLabel = input.getString("format", "JPG")
        val entry = ImageFormatMatrix.requireEncodable(formatLabel)
        val maxEdge = input.getFloat("maxEdge", 1600f).toInt().coerceIn(64, 4000)
        val quality = input.getFloat("quality", 82f).toInt().coerceIn(30, 100)
        val watermark = input.getString("watermark", "© HariMu")
        val pattern = input.getString("renamePattern", "HariMu_{n}_{name}")
        val dest = ctx.storage.createOutputFile("IMG_BATCH", "zip")
        val report = StringBuilder("file,operasi,status,ukuran,pesan\n")
        var success = 0
        var failed = 0
        var skipped = 0

        ZipOutputStream(FileOutputStream(dest)).use { zip ->
            input.uris.forEachIndexed { index, uri ->
                currentCoroutineContext().ensureActive()
                val displayName = ctx.storage.getDisplayName(uri) ?: "gambar_${index + 1}"
                emit(
                    ToolProgress.Running(
                        5 + index * 85 / input.uris.size,
                        "(${index + 1}/${input.uris.size}) $operation — $displayName"
                    )
                )
                val baseName = B2.nameWithoutExtension(displayName)
                val renderedName = pattern
                    .replace("{n}", (index + 1).toString())
                    .replace("{name}", baseName)
                    .let { B2.safeFileName(it) }
                try {
                    val originalExt = B2.extensionOf(displayName)
                    when (operation) {
                        "Metadata (hapus EXIF)" -> {
                            val temp = ctx.storage.createTempFile("batch_meta", originalExt)
                            ctx.appContext.contentResolver.openInputStream(uri)?.use { stream ->
                                temp.outputStream().use { out -> stream.copyTo(out, 64 * 1024) }
                            } ?: error("Tidak dapat membaca file")
                            val cleared = ImageIO.stripMetadata(temp)
                            B2.addZipEntry(zip, "$renderedName.$originalExt", temp.readBytes())
                            report.append(
                                "${B2.csv(displayName)},${B2.csv(operation)},SUKSES,${temp.length()}," +
                                    "Metadata ${if (cleared) "dihapus" else "tidak berubah"}\n"
                            )
                            temp.delete()
                            success++
                        }
                        "Rename" -> {
                            ctx.appContext.contentResolver.openInputStream(uri)?.use { stream ->
                                val bytes = stream.readBytes()
                                B2.addZipEntry(zip, "$renderedName.$originalExt", bytes)
                                report.append(
                                    "${B2.csv(displayName)},${B2.csv(operation)},SUKSES,${bytes.size}," +
                                        "Diubah nama menjadi $renderedName.$originalExt\n"
                                )
                            } ?: error("Tidak dapat membaca file")
                            success++
                        }
                        else -> {
                            val bitmap = when (operation) {
                                "Thumbnail" -> ImageIO.decodePreview(ctx.appContext, uri, min(maxEdge, 512))
                                "Resize", "Kompres", "Konversi Format" -> ImageIO.decodePreview(ctx.appContext, uri, maxEdge)
                                "Hapus Latar (heuristik)" -> ImageIO.decodePreview(ctx.appContext, uri, min(maxEdge, 2000))
                                else -> ImageIO.decodeFullGuarded(ctx.appContext, uri, 24_000_000L)
                            }
                            val processed = when (operation) {
                                "Watermark" -> ImageEngine.addWatermark(bitmap, watermark, 45, "Kanan Bawah", false)
                                "Hapus Latar (heuristik)" ->
                                    com.harimu.app.core.ai.AiLocalEngines.removeBackground(bitmap, 28, 2, "")
                                else -> bitmap
                            }
                            // Hapus latar butuh alpha ⇒ selalu PNG, apa pun format pilihan pengguna.
                            val outputLabel = if (operation.startsWith("Hapus Latar")) "PNG" else entry.id
                            val bytes = ImageIO.encodeToBytes(processed, outputLabel, quality)
                            if (processed !== bitmap) processed.recycle()
                            bitmap.recycle()
                            val outputExt = ImageFormatMatrix.extensionFor(outputLabel)
                            B2.addZipEntry(zip, "$renderedName.$outputExt", bytes)
                            report.append(
                                "${B2.csv(displayName)},${B2.csv(operation)},SUKSES,${bytes.size}," +
                                    "${outputExt.uppercase()} ${bytes.size} byte\n"
                            )
                            success++
                        }
                    }
                } catch (t: Throwable) {
                    if (t is kotlinx.coroutines.CancellationException) throw t
                    failed++
                    report.append(
                        "${B2.csv(displayName)},${B2.csv(operation)},GAGAL,0,${B2.csv(t.localizedMessage ?: "error")}\n"
                    )
                }
            }
            B2.addZipEntry(zip, "laporan_batch.csv", report.toString().toByteArray())
        }

        val text = report.toString()
        emit(
            ToolProgress.Done(
                ToolResult(
                    outputs = listOf(ToolOutputFile(dest, "application/zip", dest.name)),
                    textOutput = text,
                    summary = "Batch $operation: $success sukses, $failed gagal, $skipped ditunda " +
                        "dari ${input.uris.size} file (${ctx.storage.formatBytes(dest.length())})"
                )
            )
        )
    }.flowOn(Dispatchers.IO)
}

// --------------------------------------------------------------------- IMG-44

private fun spriteSheetTool(): Tool = object : Tool {
    override val id = "IMG-44"
    override val group = ToolGroup.IMAGE
    override val engineLabel = "NAT"
    override val icon = Icons.Filled.GridOn
    override val inputSpec = InputSpec.MultipleImages
    override val params = listOf<ToolParam>(
        ToolParam.SliderParam("cols", "Jumlah Kolom", "Columns", 1f, 8f, 3f, 1f, ""),
        ToolParam.ToggleParam("atlas", "Tulis atlas JSON", "Write JSON atlas", true)
    )

    override fun title(isEnglish: Boolean) = if (isEnglish) "Sprite Sheet Generator" else "Generator Sprite Sheet"
    override fun description(isEnglish: Boolean) = if (isEnglish) "Pack frames into a grid PNG with an optional JSON atlas." else "Gabungkan frame menjadi grid PNG dengan atlas JSON opsional."

    override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
        require(input.uris.size >= 2) { "Pilih minimal 2 gambar" }
        emit(ToolProgress.Running(20, "Mendekode ${input.uris.size} frame..."))
        val bitmaps = input.uris.map { ImageIO.decodePreview(ctx.appContext, it, 512) }
        emit(ToolProgress.Running(60, "Menyusun sprite sheet..."))
        val cols = input.getFloat("cols", 3f).toInt()
        val (sheet, atlasJson) = ImageEngine.createSpriteSheet(bitmaps, cols)
        bitmaps.forEach { it.recycle() }
        val dest = ctx.storage.createOutputFile("SPRITE", "png")
        ImageIO.encode(sheet, dest, "PNG", 100)
        sheet.recycle()
        val outputs = mutableListOf(ToolOutputFile(dest, "image/png", dest.name))
        if (input.getBoolean("atlas", true)) {
            val atlasFile = ctx.storage.createOutputFile("SPRITE_ATLAS", "json")
            atlasFile.writeText(atlasJson)
            outputs += ToolOutputFile(atlasFile, "application/json", atlasFile.name)
        }
        emit(
            ToolProgress.Done(
                ToolResult(
                    outputs = outputs,
                    textOutput = if (input.getBoolean("atlas", true)) atlasJson else null,
                    summary = "Sprite sheet ${input.uris.size} frame, ${cols.coerceIn(1, input.uris.size)} kolom " +
                        "(${ctx.storage.formatBytes(dest.length())})"
                )
            )
        )
    }.flowOn(Dispatchers.IO)
}

// --------------------------------------------------------------------- IMG-45

private fun gifTool(): Tool = object : Tool {
    override val id = "IMG-45"
    override val group = ToolGroup.IMAGE
    override val engineLabel = "GIF89a"
    override val icon = Icons.Filled.Movie
    override val inputSpec = InputSpec.MultipleImages
    override val params = listOf<ToolParam>(
        ToolParam.SliderParam("fps", "Frame per Detik", "Frames per Second", 1f, 15f, 8f, 1f, "fps"),
        ToolParam.SliderParam("maxWidth", "Lebar Maks (px)", "Max Width (px)", 240f, 960f, 640f, 80f, "px"),
        ToolParam.ToggleParam("loop", "Ulangi Tanpa Hentian", "Loop Forever", true)
    )

    override fun title(isEnglish: Boolean) = if (isEnglish) "Animated GIF Maker" else "Pembuat GIF Animasi"
    override fun description(isEnglish: Boolean) = if (isEnglish) "Combine photos into a looping GIF with a shared 256-colour palette." else "Gabungkan foto menjadi GIF berulang dengan palet 256 warna bersama."

    override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
        require(input.uris.size >= 2) { "Pilih minimal 2 gambar" }
        require(input.uris.size <= 40) { "Maksimal 40 frame per GIF agar memori aman" }
        val fps = input.getFloat("fps", 8f).toInt().coerceIn(1, 15)
        val maxWidth = input.getFloat("maxWidth", 640f).toInt().coerceIn(120, 960)
        val loop = input.getBoolean("loop", true)

        emit(ToolProgress.Running(10, "Mendekode frame..."))
        val decoded = mutableListOf<Bitmap>()
        input.uris.forEachIndexed { index, uri ->
            currentCoroutineContext().ensureActive()
            emit(ToolProgress.Running(10 + index * 30 / input.uris.size, "Frame ${index + 1}..."))
            decoded += ImageIO.decodePreview(ctx.appContext, uri, maxWidth)
        }
        val longest = decoded.maxOfOrNull { max(it.width, it.height) } ?: maxWidth
        val scale = (maxWidth.toFloat() / longest).coerceAtMost(1f)
        val canvasW = (decoded.first().width * scale).roundToInt().coerceIn(2, maxWidth)
        val canvasH = (decoded.first().height * scale).roundToInt().coerceIn(2, maxWidth)
        val frames = decoded.map { frame ->
            if (frame.width == canvasW && frame.height == canvasH) {
                frame
            } else {
                ImageOverlayEngine.fitCenter(frame, canvasW, canvasH, Color.BLACK)
            }
        }
        decoded.forEach { if (frames.none { f -> f === it }) it.recycle() }

        emit(ToolProgress.Running(50, "Mengkuantisasi ${frames.size} frame (median-cut)..."))
        val dest = ctx.storage.createOutputFile("ANIMASI", "gif")
        val delay = (100f / fps).roundToInt().coerceAtLeast(2)
        val written = FileOutputStream(dest).use { out ->
            GifEncoder(canvasW, canvasH).encode(frames, delay, loop, out)
        }
        frames.forEach { it.recycle() }
        emit(
            ToolProgress.Done(
                ToolResult(
                    outputs = listOf(ToolOutputFile(dest, "image/gif", dest.name)),
                    summary = "$written frame ${canvasW}×${canvasH} pada $fps fps " +
                        "(${ctx.storage.formatBytes(dest.length())})"
                )
            )
        )
    }.flowOn(Dispatchers.IO)
}

// --------------------------------------------------------------------- IMG-46

private fun videoFrameTool(): Tool = object : Tool {
    override val id = "IMG-46"
    override val group = ToolGroup.IMAGE
    override val engineLabel = "NAT"
    override val icon = Icons.Filled.BurstMode
    override val inputSpec = InputSpec.SingleVideo
    override val aliasOfId = "VID-22"
    override val params = listOf<ToolParam>(
        ToolParam.SliderParam("intervalSec", "Interval Frame (detik)", "Frame Interval (sec)", 1f, 10f, 2f, 1f, "s"),
        ToolParam.SliderParam("maxFrames", "Maksimal Frame", "Max Frames", 5f, 60f, 12f, 1f, "")
    )

    override fun title(isEnglish: Boolean) = if (isEnglish) "Video Frame Extractor" else "Ekstraktor Frame Video"
    override fun description(isEnglish: Boolean) = if (isEnglish) "Alias of VID-22: grab PNG frames at a fixed interval into a ZIP." else "Alias VID-22: ambil frame PNG per interval detik ke dalam ZIP."

    override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
        val uri = input.uris.firstOrNull() ?: error("Pilih 1 video")
        val interval = input.getFloat("intervalSec", 2f).toInt().coerceIn(1, 10)
        val maxFrames = input.getFloat("maxFrames", 12f).toInt().coerceIn(5, 60)
        emit(ToolProgress.Running(20, "Membaca frame video..."))
        val dest = ctx.storage.createOutputFile("IMG_FRAMES", "zip")
        val (zipFile, count) = VideoEngine.extractFramesZip(
            context = ctx.appContext,
            uri = uri,
            destZip = dest,
            intervalSec = interval,
            maxFrames = maxFrames
        )
        require(count > 0) { "Tidak ada frame yang dapat diambil dari video ini" }
        emit(
            ToolProgress.Done(
                ToolResult(
                    outputs = listOf(ToolOutputFile(zipFile, "application/zip", zipFile.name)),
                    summary = "$count frame PNG (interval ${interval}s) diekstrak ke ZIP " +
                        "(${ctx.storage.formatBytes(zipFile.length())})"
                )
            )
        )
    }.flowOn(Dispatchers.IO)
}
