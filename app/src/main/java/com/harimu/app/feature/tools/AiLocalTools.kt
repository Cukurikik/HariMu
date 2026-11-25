package com.harimu.app.feature.tools

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BlurCircular
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Portrait
import com.harimu.app.core.ai.AiException
import com.harimu.app.core.ai.AiLocalEngines
import com.harimu.app.core.engines.AudioEngine
import com.harimu.app.core.engines.ImageIO
import com.harimu.app.core.engines.VideoEngine
import com.harimu.app.core.media.FfmpegCapabilities
import com.harimu.app.core.media.Media3Request
import com.harimu.app.core.media.Media3TransformerEngine
import com.harimu.app.core.media.MediaJob
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
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Tool "AI on-device" FASE 11.
 *
 * Model neural (U²-Net, Real-ESRGAN, Demucs, Whisper) belum dibundel karena berkas model tidak
 * tersedia di repo. Tool di sini memakai algoritma klasik yang benar-benar berjalan dan langsung
 * menghasilkan berkas — statusnya ditulis jujur "heuristik, bukan model" di katalog, DECISIONS,
 * dan ringkasan hasil. ModelManager menyediakan jalur unduh + checksum untuk saat model tersedia.
 */
fun aiLocalTools(): List<Tool> = listOf(
    localUpscaleTool(),
    localBackgroundRemoverTool(),
    localBokehTool(),
    localDenoiseTool(),
    videoUpscaleTool(),
    stemSplitterTool(),
    speechToTextTool()
)

// -------------------------------------------------------------- IMG-11

private fun localUpscaleTool(): Tool = object : Tool {
    override val id = "IMG-11"
    override val group = ToolGroup.IMAGE
    override val engineLabel = "NAT/DSP"
    override val icon = Icons.Filled.HighQuality
    override val inputSpec = InputSpec.SingleImage
    override val params = listOf(
        ToolParam.DropdownParam("factor", "Perbesaran", "Scale Factor", listOf("2x", "3x", "4x"), "2x"),
        ToolParam.SliderParam("sharpen", "Ketajaman (%)", "Sharpening (%)", 0f, 100f, 55f, 5f, "%"),
        ToolParam.DropdownParam("format", "Format Simpan", "Save Format", listOf("PNG", "JPG"), "PNG")
    )

    override fun title(isEnglish: Boolean) = if (isEnglish) "AI Image Upscaler" else "Pembesar Gambar AI"
    override fun description(isEnglish: Boolean) = if (isEnglish) {
        "2–4× enlargement with edge-aware sharpening. Classic algorithm, not a neural model."
    } else {
        "Perbesar 2–4× dengan penajaman tepi. Algoritma klasik, bukan model neural."
    }

    override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
        val uri = input.uris.firstOrNull() ?: error("Pilih 1 gambar")
        val factor = input.getString("factor", "2x").removeSuffix("x").toIntOrNull() ?: 2
        emit(ToolProgress.Running(20, "Mendekode gambar…"))
        val source = ImageIO.decodePreview(ctx.appContext, uri, 3000)
        emit(ToolProgress.Running(45, "Memperbesar ${source.width}×${source.height} → ${factor}× …"))
        val out = withContext(Dispatchers.Default) {
            AiLocalEngines.upscale(source, factor, input.getFloat("sharpen", 55f).toInt())
        }
        if (out !== source) source.recycle()
        val format = input.getString("format", "PNG")
        val ext = if (format == "JPG") "jpg" else "png"
        val dest = ctx.storage.createOutputFile("IMG_UPSCALE", ext)
        ImageIO.encode(out, dest, format, 94)
        val size = "${out.width}×${out.height}"
        out.recycle()
        emit(
            ToolProgress.Done(
                ToolResult(
                    outputs = listOf(ToolOutputFile(dest, if (ext == "png") "image/png" else "image/jpeg", dest.name)),
                    summary = "$size (${factor}×) • unsharp mask • ${ctx.storage.formatBytes(dest.length())}\n" +
                        "Catatan: heuristik klasik, bukan super-resolution neural."
                )
            )
        )
    }.flowOn(Dispatchers.IO)
}

// -------------------------------------------------------------- IMG-12

private fun localBackgroundRemoverTool(): Tool = object : Tool {
    override val id = "IMG-12"
    override val group = ToolGroup.IMAGE
    override val engineLabel = "NAT/DSP"
    override val icon = Icons.Filled.CleaningServices
    override val inputSpec = InputSpec.SingleImage
    override val params = listOf(
        ToolParam.SliderParam("tolerance", "Toleransi Warna (%)", "Colour Tolerance (%)", 5f, 80f, 28f, 1f, "%"),
        ToolParam.SliderParam("feather", "Pelembutan Tepi (px)", "Edge Feather (px)", 0f, 8f, 2f, 1f, "px"),
        ToolParam.DropdownParam(
            "background",
            "Latar Hasil",
            "Result Background",
            listOf("Transparan", "Putih", "Hitam", "#FFE082"),
            "Transparan"
        )
    )

    override fun title(isEnglish: Boolean) = if (isEnglish) "AI Background Remover" else "Penghapus Latar AI"
    override fun description(isEnglish: Boolean) = if (isEnglish) {
        "Remove a connected background from the image edges. Heuristic segmentation, not U²-Net."
    } else {
        "Hapus latar yang tersambung ke tepi gambar. Segmentasi heuristik, bukan U²-Net."
    }

    override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
        val uri = input.uris.firstOrNull() ?: error("Pilih 1 gambar")
        emit(ToolProgress.Running(25, "Mendekode gambar…"))
        val source = ImageIO.decodePreview(ctx.appContext, uri, 2400)
        emit(ToolProgress.Running(55, "Menghapus latar (flood fill dari tepi)…"))
        val bg = input.getString("background", "Transparan").let {
            if (it == "Transparan") "" else it
        }
        val out = withContext(Dispatchers.Default) {
            AiLocalEngines.removeBackground(
                source = source,
                tolerancePercent = input.getFloat("tolerance", 28f).toInt(),
                featherPx = input.getFloat("feather", 2f).toInt(),
                backgroundColorHex = bg
            )
        }
        source.recycle()
        val dest = ctx.storage.createOutputFile("IMG_BGREMOVE", "png")
        ImageIO.encode(out, dest, "PNG", 100)
        val size = "${out.width}×${out.height}"
        out.recycle()
        emit(
            ToolProgress.Done(
                ToolResult(
                    outputs = listOf(ToolOutputFile(dest, "image/png", dest.name)),
                    summary = "$size • latar ${if (bg.isBlank()) "transparan" else bg} • " +
                        "${ctx.storage.formatBytes(dest.length())}\n" +
                        "Catatan: subjek yang menyentuh tepi bisa ikut terhapus (heuristik)."
                )
            )
        )
    }.flowOn(Dispatchers.IO)
}

// -------------------------------------------------------------- IMG-19

private fun localBokehTool(): Tool = object : Tool {
    override val id = "IMG-19"
    override val group = ToolGroup.IMAGE
    override val engineLabel = "NAT/DSP"
    override val icon = Icons.Filled.Portrait
    override val inputSpec = InputSpec.SingleImage
    override val params = listOf(
        ToolParam.SliderParam("focusX", "Pusat Fokus X (%)", "Focus X (%)", 0f, 100f, 50f, 1f, "%"),
        ToolParam.SliderParam("focusY", "Pusat Fokus Y (%)", "Focus Y (%)", 0f, 100f, 45f, 1f, "%"),
        ToolParam.SliderParam("radius", "Radius Fokus (%)", "Focus Radius (%)", 10f, 90f, 42f, 2f, "%"),
        ToolParam.SliderParam("strength", "Kekuatan Blur (%)", "Blur Strength (%)", 10f, 100f, 75f, 5f, "%")
    )

    override fun title(isEnglish: Boolean) = if (isEnglish) "AI Portrait Bokeh" else "Bokeh Potret AI"
    override fun description(isEnglish: Boolean) = if (isEnglish) {
        "Radial background blur around a focus point. Depth-map bokeh is deferred to a model."
    } else {
        "Blur radial di sekitar titik fokus. Bokeh berbasis depth map menunggu model."
    }

    override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
        val uri = input.uris.firstOrNull() ?: error("Pilih 1 gambar")
        emit(ToolProgress.Running(25, "Mendekode gambar…"))
        val source = ImageIO.decodePreview(ctx.appContext, uri, 2400)
        emit(ToolProgress.Running(55, "Merender bokeh radial…"))
        val out = withContext(Dispatchers.Default) {
            AiLocalEngines.portraitBokeh(
                source = source,
                focusXPct = input.getFloat("focusX", 50f),
                focusYPct = input.getFloat("focusY", 45f),
                radiusPct = input.getFloat("radius", 42f).toInt(),
                strengthPct = input.getFloat("strength", 75f).toInt()
            )
        }
        source.recycle()
        val dest = ctx.storage.createOutputFile("IMG_BOKEH", "jpg")
        ImageIO.encode(out, dest, "JPG", 93)
        out.recycle()
        emit(
            ToolProgress.Done(
                ToolResult(
                    outputs = listOf(ToolOutputFile(dest, "image/jpeg", dest.name)),
                    summary = "Bokeh radial ${ctx.storage.formatBytes(dest.length())} • " +
                        "bukan depth map, subjek di luar lingkaran fokus ikut blur."
                )
            )
        )
    }.flowOn(Dispatchers.IO)
}

// -------------------------------------------------------------- IMG-22

private fun localDenoiseTool(): Tool = object : Tool {
    override val id = "IMG-22"
    override val group = ToolGroup.IMAGE
    override val engineLabel = "NAT/DSP"
    override val icon = Icons.Filled.BlurCircular
    override val inputSpec = InputSpec.SingleImage
    override val params = listOf(
        ToolParam.SliderParam("strength", "Kekuatan Denoise (%)", "Denoise Strength (%)", 10f, 100f, 55f, 5f, "%"),
        ToolParam.SliderParam("detail", "Pertahankan Detail (%)", "Keep Detail (%)", 0f, 100f, 60f, 5f, "%")
    )

    override fun title(isEnglish: Boolean) = if (isEnglish) "AI Image Denoiser" else "Penghilang Noise AI"
    override fun description(isEnglish: Boolean) = if (isEnglish) {
        "Edge-preserving smart blur for high-ISO photos. Classic filter, not a neural denoiser."
    } else {
        "Smart blur tepi-terjaga untuk foto ISO tinggi. Filter klasik, bukan denoiser neural."
    }

    override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
        val uri = input.uris.firstOrNull() ?: error("Pilih 1 gambar")
        emit(ToolProgress.Running(25, "Mendekode gambar…"))
        val source = ImageIO.decodePreview(ctx.appContext, uri, 3000)
        emit(ToolProgress.Running(55, "Menghaluskan noise…"))
        val out = withContext(Dispatchers.Default) {
            AiLocalEngines.denoise(
                source = source,
                strengthPercent = input.getFloat("strength", 55f).toInt(),
                detailPercent = input.getFloat("detail", 60f).toInt()
            )
        }
        source.recycle()
        val dest = ctx.storage.createOutputFile("IMG_DENOISE", "jpg")
        ImageIO.encode(out, dest, "JPG", 93)
        out.recycle()
        emit(
            ToolProgress.Done(
                ToolResult(
                    outputs = listOf(ToolOutputFile(dest, "image/jpeg", dest.name)),
                    summary = "Smart blur tepi-terjaga • ${ctx.storage.formatBytes(dest.length())}\n" +
                        "Catatan: bukan model denoiser; tekstur halus bisa ikut lembut."
                )
            )
        )
    }.flowOn(Dispatchers.IO)
}

// --------------------------------------------------------------- VID-07

private fun videoUpscaleTool(
    media3: Media3TransformerEngine = Media3TransformerEngine(),
    ffmpegJob: MediaJob = MediaJob()
): Tool = object : Tool {
    override val id = "VID-07"
    override val group = ToolGroup.VIDEO
    override val engineLabel = "M3/FF"
    override val icon = Icons.Filled.HighQuality
    override val inputSpec = InputSpec.SingleVideo
    override val params = listOf(
        ToolParam.DropdownParam("factor", "Perbesaran", "Scale", listOf("1.5x", "2x"), "1.5x"),
        ToolParam.SliderParam("sharpness", "Ketajaman (jalur FFmpeg)", "Sharpening (FFmpeg path)", 0f, 100f, 0f, 5f, "%"),
        ToolParam.DropdownParam("quality", "Kualitas", "Quality", listOf("Tinggi", "Sedang"), "Sedang")
    )

    override fun title(isEnglish: Boolean) = if (isEnglish) "Video Upscaler 1.5–2×" else "Pembesar Video 1.5–2×"
    override fun description(isEnglish: Boolean) = if (isEnglish) {
        "Upscale a short clip with the device encoder (Media3 + H.264). Max 60 s and 1080p input, output ≤ 2160p. " +
            "Sharpening above 0 switches to the FFmpeg path (LGPL mpeg4 + unsharp, slower)."
    } else {
        "Perbesar klip pendek memakai encoder perangkat (Media3 + H.264). Maks 60 detik & input 1080p, hasil ≤ 2160p. " +
            "Ketajaman di atas 0 beralih ke jalur FFmpeg (LGPL mpeg4 + unsharp, lebih lambat)."
    }

    override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
        val uri = input.uris.firstOrNull() ?: error("Pilih 1 video")
        val durationMs = VideoEngine.getVideoDurationMs(ctx.appContext, uri)
        require(durationMs in 1..60_000L) {
            "Durasi ${durationMs / 1000}s di luar batas 1–60 detik. Potong dulu dengan tool trim video."
        }
        val meta = withContext(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(ctx.appContext, uri)
                val w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                val h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                w to h
            } finally {
                runCatching { retriever.release() }
            }
        }
        require(meta.second in 1..1080) {
            "Tinggi video ${meta.second}px melebihi 1080p. Turunkan resolusinya dulu."
        }
        val factor = input.getString("factor", "1.5x").removeSuffix("x").toFloatOrNull() ?: 1.5f
        val targetHeight = (meta.second * factor).roundToInt()
        require(targetHeight <= 2160) { "Hasil ${targetHeight}px melebihi 2160p." }

        // Encoder H.264 membutuhkan dimensi genap.
        fun even(value: Int): Int = ((value.coerceAtLeast(1) + 1) / 2) * 2
        val targetWidth = even((meta.first * factor).roundToInt())
        val targetH = even(targetHeight)
        val sharp = input.getFloat("sharpness", 0f).toInt().coerceIn(0, 100)
        val highQuality = input.getString("quality", "Sedang").startsWith("Tinggi")

        if (sharp <= 0) {
            emit(ToolProgress.Running(10, "Menyiapkan berkas sumber…"))
            emit(
                ToolProgress.Running(
                    25,
                    "Media3 H.264: ${meta.first}×${meta.second} → $targetWidth×$targetH (encoder perangkat)…"
                )
            )
            val request = Media3Request(
                inputUri = uri,
                toolTag = "VID_UPSCALE",
                outputExt = "mp4",
                outputMime = "video/mp4",
                videoMimeType = "video/avc",
                targetWidth = targetWidth,
                targetHeight = targetH,
                // Kira-kira 0,1 bit/piksel/frame @30fps; "Tinggi" memakai 1,5× nilai dasar.
                targetVideoBitrateBps = (
                    targetWidth.toLong() * targetH * (if (highQuality) 3L else 2L)
                    ).toInt()
            )
            media3.runTransformerOnly(
                context = ctx.appContext,
                storage = ctx.storage,
                request = request,
                isEnglish = ctx.isEnglish
            ).collect { progress ->
                if (progress is ToolProgress.Done) {
                    val size = progress.result.outputs.firstOrNull()?.file?.length() ?: 0L
                    emit(
                        ToolProgress.Done(
                            progress.result.copy(
                                summary = "$factor× → $targetWidth×$targetH • ${ctx.storage.formatBytes(size)} • " +
                                    "H.264 Media3 (encoder perangkat, bukan model AI)"
                            )
                        )
                    )
                } else {
                    emit(progress)
                }
            }
        } else {
            // unsharp tidak ada di Media3; jalur FFmpeg dipakai hanya saat pengguna meminta penajaman.
            FfmpegCapabilities.requireFilter("unsharp", ctx.isEnglish)
            FfmpegCapabilities.requireEncoder("mpeg4", ctx.isEnglish)
            val amount = sharp / 100f * 1.2f
            val filter = "scale=trunc(iw*$factor/2)*2:trunc(ih*$factor/2)*2:flags=lanczos," +
                "unsharp=5:5:$amount:5:5:0"
            emit(
                ToolProgress.Running(
                    25,
                    "FFmpeg: ${meta.first}×${meta.second} → $targetWidth×$targetH (mpeg4 + unsharp, CPU)…"
                )
            )
            ffmpegJob.runCommand(
                context = ctx.appContext,
                storage = ctx.storage,
                inputUris = listOf(uri),
                toolTag = "VID_UPSCALE",
                outputExt = "mp4",
                outputMime = "video/mp4",
                expectedDurationMs = durationMs,
                isEnglish = ctx.isEnglish
            ) { inputs, output ->
                listOf(
                    "-y",
                    "-i", inputs.first().absolutePath,
                    "-vf", filter,
                    "-c:v", "mpeg4", "-q:v", if (highQuality) "2" else "4",
                    "-c:a", "copy",
                    "-movflags", "+faststart",
                    output.absolutePath
                )
            }.collect { progress ->
                if (progress is ToolProgress.Done) {
                    val size = progress.result.outputs.firstOrNull()?.file?.length() ?: 0L
                    emit(
                        ToolProgress.Done(
                            progress.result.copy(
                                summary = "$factor× → $targetWidth×$targetH • ${ctx.storage.formatBytes(size)} • " +
                                    "FFmpeg mpeg4 + unsharp (LGPL, bukan model AI)"
                            )
                        )
                    )
                } else {
                    emit(progress)
                }
            }
        }
    }.flowOn(Dispatchers.IO)
}

// -------------------------------------------------------------- AUD-20

private fun stemSplitterTool(): Tool = object : Tool {
    override val id = "AUD-20"
    override val group = ToolGroup.AUDIO
    override val engineLabel = "DSP"
    override val icon = Icons.Filled.CallSplit
    override val inputSpec = InputSpec.SingleAudio
    override val params = listOf(
        ToolParam.DropdownParam(
            "output",
            "Keluaran",
            "Output",
            listOf("Vokal + Instrumen", "Instrumen saja", "Vokal saja"),
            "Vokal + Instrumen"
        ),
        ToolParam.ToggleParam("zip", "Bungkus ZIP", "Bundle as ZIP", true)
    )

    override fun title(isEnglish: Boolean) = if (isEnglish) "Stem Splitter (Vokal/Instrumen)" else "Pemisah Stem (Vokal/Instrumen)"
    override fun description(isEnglish: Boolean) = if (isEnglish) {
        "Mid/side split into vocals and instrumental WAVs. DSP heuristic, not Demucs 4-stem."
    } else {
        "Pisahkan mid/side menjadi WAV vokal dan instrumen. Heuristik DSP, bukan Demucs 4-stem."
    }

    override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
        val uri = input.uris.firstOrNull() ?: error("Pilih 1 file audio")
        emit(ToolProgress.Running(15, "Mendekode audio ke PCM…"))
        val pcm = withContext(Dispatchers.IO) { AudioEngine.decodeToPcm(ctx.appContext, uri) }
        require(pcm.samples.isNotEmpty()) { "Audio kosong atau tidak dapat didekode" }
        emit(ToolProgress.Running(45, "Memisahkan kanal tengah/sisi…"))
        val mode = input.getString("output", "Vokal + Instrumen")
        val tempDir = ctx.storage.createTempFile("stems", "dir").let { probe ->
            val dir = File(probe.parentFile, "stems_${System.currentTimeMillis()}")
            probe.delete()
            dir.mkdirs()
            dir
        }
        val stems = withContext(Dispatchers.Default) {
            AiLocalEngines.splitStems(pcm, tempDir, instrumentalOnly = mode == "Instrumen saja")
        }
        val files = buildList {
            if (mode != "Instrumen saja") add(stems.vocals)
            if (mode != "Vokal saja") add(stems.instrumental)
        }
        emit(ToolProgress.Running(80, "Menulis hasil…"))
        val outputs = mutableListOf<ToolOutputFile>()
        if (input.getBoolean("zip", true) && files.size > 1) {
            val zipFile = ctx.storage.createOutputFile("AUD_STEMS", "zip")
            ZipOutputStream(FileOutputStream(zipFile)).use { zip ->
                files.forEach { file ->
                    zip.putNextEntry(ZipEntry(file.name))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
                zip.putNextEntry(ZipEntry("catatan.txt"))
                zip.write(
                    ("Dihasilkan HariMu AUD-20 dengan mid/side DSP.\n" +
                        "Vokal = (L+R)/2, Instrumen = (L-R)/2. Ini bukan Demucs; hasil terbaik untuk\n" +
                        "rekaman dengan vokal center-panned.\n").toByteArray()
                )
                zip.closeEntry()
            }
            outputs += ToolOutputFile(zipFile, "application/zip", zipFile.name)
        } else {
            files.forEach { file ->
                val dest = ctx.storage.createOutputFile("AUD_STEM_${file.nameWithoutExtension.uppercase()}", "wav")
                file.copyTo(dest, overwrite = true)
                outputs += ToolOutputFile(dest, "audio/wav", dest.name)
            }
        }
        tempDir.deleteRecursively()
        emit(
            ToolProgress.Done(
                ToolResult(
                    outputs = outputs,
                    summary = "${files.size} jalur • ${ctx.storage.formatBytes(outputs.sumOf { it.file.length() })} • " +
                        "mid/side DSP (bukan model). Kualitas bergantung pada posisi vokal di mix."
                )
            )
        )
    }.flowOn(Dispatchers.IO)
}

// -------------------------------------------------------------- AUD-27

private fun speechToTextTool(): Tool = object : Tool {
    override val id = "AUD-27"
    override val group = ToolGroup.AUDIO
    override val engineLabel = "CL"
    override val icon = Icons.Filled.Hearing
    override val inputSpec = InputSpec.SingleAudio
    override val requiresNetwork = true
    override val params = listOf(
        ToolParam.DropdownParam("language", "Bahasa", "Language", listOf("Otomatis", "id", "en"), "Otomatis"),
        ToolParam.ToggleParam("saveReport", "Simpan Transkrip .txt", "Save Transcript .txt", true)
    )

    override fun title(isEnglish: Boolean) = if (isEnglish) "Speech to Text" else "Ubah Suara ke Teks"
    override fun description(isEnglish: Boolean) = if (isEnglish) {
        "Transcribe audio through the AI gateway (Whisper-compatible). On-device Whisper model is not bundled."
    } else {
        "Transkripsi audio lewat AI Gateway (kompatibel Whisper). Model Whisper on-device belum dibundel."
    }

    override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
        val client = ctx.ai ?: throw AiException("ai_not_configured", "Klien AI tidak tersedia")
        if (!client.hasUploadConsent()) {
            throw AiException(
                "upload_consent_required",
                "Unggahan cloud pertama butuh konfirmasi. Jalankan lagi lalu pilih “Izinkan unggah”."
            )
        }
        val uri = input.uris.firstOrNull() ?: error("Pilih 1 file audio")
        emit(ToolProgress.Running(10, "Membaca audio…"))
        val bytes = withContext(Dispatchers.IO) {
            ctx.appContext.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw IllegalStateException("Audio tidak dapat dibaca")
        }
        require(bytes.size <= 15 * 1024 * 1024) {
            "Ukuran audio ${ctx.storage.formatBytes(bytes.size.toLong())} melebihi 15 MB. Potong dulu."
        }
        val mime = when (val ext = ctx.storage.getDisplayName(uri)?.substringAfterLast('.', "").lowercase()) {
            "wav" -> "audio/wav"
            "mp3" -> "audio/mpeg"
            "m4a", "mp4" -> "audio/mp4"
            "ogg", "opus" -> "audio/ogg"
            "flac" -> "audio/flac"
            else -> if (ext.isBlank()) "audio/wav" else "audio/$ext"
        }
        val language = input.getString("language", "Otomatis").let { if (it == "Otomatis") null else it }
        emit(ToolProgress.Running(25, "Mengunggah ${ctx.storage.formatBytes(bytes.size.toLong())}…"))
        val result = client.speechToText(bytes, mime, language) { written, total ->
            runCatching { max(0.0, written.toDouble() / max(1L, total).toDouble()) }
        }
        emit(ToolProgress.Running(90, "Menyimpan transkrip…"))
        val outputs = mutableListOf<ToolOutputFile>()
        if (input.getBoolean("saveReport", true)) {
            val dest = ctx.storage.createOutputFile("TRANSCRIPT", "txt")
            dest.writeText(result.text.trim())
            outputs += ToolOutputFile(dest, "text/plain", dest.name)
        }
        emit(
            ToolProgress.Done(
                ToolResult(
                    outputs = outputs,
                    textOutput = result.text.trim(),
                    summary = if (result.mock) {
                        "Transkrip MOCK — provider mock tanpa kunci model, bukan hasil Whisper"
                    } else {
                        "Transkrip selesai • ${result.text.length} karakter" +
                            (result.quotaRemaining?.let { " • sisa kuota $it" } ?: "")
                    }
                )
            )
        )
    }.flowOn(Dispatchers.IO)
}
