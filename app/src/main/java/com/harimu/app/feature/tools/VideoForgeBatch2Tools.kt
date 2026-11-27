package com.harimu.app.feature.tools

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Title
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.StaticOverlaySettings
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.harimu.app.core.media.FfmpegCapabilities
import com.harimu.app.core.media.Media3Request
import com.harimu.app.core.media.Media3TransformerEngine
import com.harimu.app.core.media.MediaJob
import com.harimu.app.core.media.MediaUriUtils
import com.harimu.app.core.media.SubtitleParser
import com.harimu.app.core.media.VideoBatch2Math
import com.harimu.app.core.media.VideoToolMath
import com.harimu.app.core.toolapi.InputSpec
import com.harimu.app.core.toolapi.Tool
import com.harimu.app.core.toolapi.ToolContext
import com.harimu.app.core.toolapi.ToolGroup
import com.harimu.app.core.toolapi.ToolInput
import com.harimu.app.core.toolapi.ToolParam
import com.harimu.app.core.toolapi.ToolProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt

@androidx.annotation.OptIn(UnstableApi::class)
fun videoForgeBatch2Tools(
    media3: Media3TransformerEngine = Media3TransformerEngine(),
    mediaJob: MediaJob = MediaJob()
): List<Tool> = listOf(
    // VID-11: Stabilize Video (FFmpeg LGPL deshake)
    object : Tool {
        override val id = "VID-11"
        override val group = ToolGroup.VIDEO
        override val engineLabel = "FF"
        override val icon = Icons.Filled.CenterFocusStrong
        override val inputSpec = InputSpec.SingleVideo
        override val params = listOf(
            ToolParam.DropdownParam("strength", "Kekuatan Stabilisasi", "Stabilization Strength", listOf("Ringan", "Sedang", "Kuat"), "Sedang"),
            ToolParam.ToggleParam("cropBorder", "Potong tepi bergetar (8%)", "Crop shaky borders (8%)", true)
        )
        override fun title(isEnglish: Boolean) = if (isEnglish) "Stabilize Video" else "Stabilisasi Video"
        override fun description(isEnglish: Boolean) = if (isEnglish) {
            "Reduce camera shake using FFmpeg LGPL deshake motion compensation."
        } else {
            "Kurangi guncangan kamera memakai filter deshake FFmpeg LGPL."
        }

        override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
            val validation = VideoToolMath.validateVideoInputs(ctx.appContext, ctx.storage, input.uris, isEnglish = ctx.isEnglish)
            if (!validation.valid) {
                emit(ToolProgress.Failed(validation.errorMessage ?: "Invalid video"))
                return@flow
            }
            val probe = validation.probes.first()
            val strength = input.getString("strength", "Sedang")
            val cropBorder = input.getBoolean("cropBorder", true)
            FfmpegCapabilities.requireFilter("deshake", ctx.isEnglish)
            val vf = VideoBatch2Math.buildStabilizeFilter(strength, cropBorder)

            emitAll(
                mediaJob.runCommand(
                    context = ctx.appContext,
                    storage = ctx.storage,
                    inputUris = input.uris,
                    toolTag = "VID_STAB",
                    outputExt = "mp4",
                    outputMime = "video/mp4",
                    expectedDurationMs = probe.durationMs,
                    isEnglish = ctx.isEnglish
                ) { inputs, output ->
                    buildList {
                        add("-y"); add("-i"); add(inputs.first().absolutePath)
                        add("-vf"); add(vf)
                        add("-c:v"); add("mpeg4"); add("-q:v"); add("3")
                        if (probe.hasAudio) {
                            add("-c:a"); add("copy")
                        } else {
                            add("-an")
                        }
                        add("-movflags"); add("+faststart")
                        add(output.absolutePath)
                    }
                }
            )
        }.flowOn(Dispatchers.IO)
    },

    // VID-14: Remove Logo (ROI Blur / Pixelate via LGPL gblur/avgblur/scale+overlay)
    object : Tool {
        override val id = "VID-14"
        override val group = ToolGroup.VIDEO
        override val engineLabel = "FF"
        override val icon = Icons.Filled.BlurOn
        override val inputSpec = InputSpec.SingleVideo
        override val params = listOf(
            ToolParam.DropdownParam("mode", "Mode Samaran", "Mask Mode", listOf("Blur Halus", "Blur Rata", "Pikselasi"), "Blur Halus"),
            ToolParam.SliderParam("xPct", "Posisi X (%)", "X Position (%)", 0f, 90f, 70f, 1f, "%"),
            ToolParam.SliderParam("yPct", "Posisi Y (%)", "Y Position (%)", 0f, 90f, 5f, 1f, "%"),
            ToolParam.SliderParam("wPct", "Lebar Area (%)", "Area Width (%)", 5f, 80f, 22f, 1f, "%"),
            ToolParam.SliderParam("hPct", "Tinggi Area (%)", "Area Height (%)", 5f, 80f, 14f, 1f, "%"),
            ToolParam.SliderParam("strength", "Intensitas Blur", "Blur Intensity", 6f, 36f, 18f, 2f, "")
        )
        override fun title(isEnglish: Boolean) = if (isEnglish) "Remove Logo (Blur)" else "Hapus Logo (Blur)"
        override fun description(isEnglish: Boolean) = if (isEnglish) {
            "Mask or blur watermark/logo inside a selected video rectangle."
        } else {
            "Samarkan watermark atau logo pada area kotak yang dipilih di video."
        }

        override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
            val validation = VideoToolMath.validateVideoInputs(ctx.appContext, ctx.storage, input.uris, isEnglish = ctx.isEnglish)
            if (!validation.valid) {
                emit(ToolProgress.Failed(validation.errorMessage ?: "Invalid video"))
                return@flow
            }
            val probe = validation.probes.first()
            val srcW = probe.orientedWidth.takeIf { it > 0 } ?: 1280
            val srcH = probe.orientedHeight.takeIf { it > 0 } ?: 720
            val roi = VideoBatch2Math.clampRoiRect(
                srcWidth = srcW,
                srcHeight = srcH,
                xPct = input.getFloat("xPct", 70f),
                yPct = input.getFloat("yPct", 5f),
                wPct = input.getFloat("wPct", 22f),
                hPct = input.getFloat("hPct", 14f)
            )
            val mode = input.getString("mode", "Blur Halus")
            when {
                mode.contains("Piksel", ignoreCase = true) -> FfmpegCapabilities.requireFilter("scale", ctx.isEnglish)
                mode.contains("Rata", ignoreCase = true) -> FfmpegCapabilities.requireFilter("avgblur", ctx.isEnglish)
                else -> FfmpegCapabilities.requireFilter("gblur", ctx.isEnglish)
            }
            val strength = input.getFloat("strength", 18f).roundToInt()
            val fc = VideoBatch2Math.buildRemoveLogoFilter(roi, mode, strength)

            emitAll(
                mediaJob.runCommand(
                    context = ctx.appContext,
                    storage = ctx.storage,
                    inputUris = input.uris,
                    toolTag = "VID_DELOGO",
                    outputExt = "mp4",
                    outputMime = "video/mp4",
                    expectedDurationMs = probe.durationMs,
                    isEnglish = ctx.isEnglish
                ) { inputs, output ->
                    buildList {
                        add("-y"); add("-i"); add(inputs.first().absolutePath)
                        add("-filter_complex"); add(fc)
                        add("-c:v"); add("mpeg4"); add("-q:v"); add("3")
                        if (probe.hasAudio) {
                            add("-c:a"); add("copy")
                        } else {
                            add("-an")
                        }
                        add("-movflags"); add("+faststart")
                        add(output.absolutePath)
                    }
                }
            )
        }.flowOn(Dispatchers.IO)
    },

    // VID-15: Add Text to Video (Canvas PNG -> Media3 BitmapOverlay or timed FFmpeg overlay)
    object : Tool {
        override val id = "VID-15"
        override val group = ToolGroup.VIDEO
        override val engineLabel = "M3/FF"
        override val icon = Icons.Filled.Title
        override val inputSpec = InputSpec.SingleVideo
        override val params = listOf(
            ToolParam.TextParam("caption", "Teks Overlay", "Overlay Text", "HariMu", "Tulis teks judul…", "Enter overlay text…"),
            ToolParam.DropdownParam("position", "Posisi", "Position", listOf("Bawah Tengah", "Atas Tengah", "Tengah", "Kiri Bawah", "Kanan Bawah"), "Bawah Tengah"),
            ToolParam.DropdownParam("fontStyle", "Gaya Font", "Font Style", listOf("Sans Bold", "Sans Regular", "Monospace", "Serif"), "Sans Bold"),
            ToolParam.DropdownParam("textColor", "Warna Teks", "Text Color", listOf("Putih", "Kuning", "Oranye", "Merah", "Cyan", "Hitam"), "Putih"),
            ToolParam.SliderParam("fontSize", "Ukuran Teks", "Text Size", 24f, 84f, 44f, 2f, "px"),
            ToolParam.ToggleParam("bgBox", "Kotak latar semi-transparan", "Semi-transparent box", true),
            ToolParam.SliderParam("startSec", "Mulai Tampil (detik)", "Show From (sec)", 0f, 3600f, 0f, 0.5f, "s"),
            ToolParam.SliderParam("endSec", "Selesai Tampil (0=akhir)", "Show Until (0=end)", 0f, 3600f, 0f, 0.5f, "s")
        )
        override fun title(isEnglish: Boolean) = if (isEnglish) "Add Text to Video" else "Tambah Teks ke Video"
        override fun description(isEnglish: Boolean) = if (isEnglish) {
            "Overlay styled text with custom position, font, color, and display window."
        } else {
            "Tempel teks bergaya dengan posisi, font, warna, dan waktu tampil."
        }

        override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
            val validation = VideoToolMath.validateVideoInputs(ctx.appContext, ctx.storage, input.uris, isEnglish = ctx.isEnglish)
            if (!validation.valid) {
                emit(ToolProgress.Failed(validation.errorMessage ?: "Invalid video"))
                return@flow
            }
            val caption = input.getString("caption", "HariMu").trim()
            if (caption.isEmpty()) {
                emit(ToolProgress.Failed(if (ctx.isEnglish) "Overlay text cannot be empty." else "Teks overlay tidak boleh kosong."))
                return@flow
            }
            val probe = validation.probes.first()
            val position = input.getString("position", "Bawah Tengah")
            val fontStyle = input.getString("fontStyle", "Sans Bold")
            val textColor = input.getString("textColor", "Putih")
            val fontSize = input.getFloat("fontSize", 44f)
            val bgBox = input.getBoolean("bgBox", true)
            val startSec = input.getFloat("startSec", 0f).coerceAtLeast(0f)
            val endSec = input.getFloat("endSec", 0f).coerceAtLeast(0f)
            if (probe.durationMs > 0L && (startSec * 1000f).toLong() >= probe.durationMs) {
                emit(ToolProgress.Failed(if (ctx.isEnglish) "Show start time reaches or exceeds video duration." else "Waktu mulai tampil melebihi durasi video."))
                return@flow
            }
            val hasTiming = startSec > 0.05f || (endSec > 0.05f && (probe.durationMs <= 0L || endSec * 1000f < probe.durationMs - 200f))
            if (endSec > 0f && endSec <= startSec) {
                emit(ToolProgress.Failed(if (ctx.isEnglish) "End time must exceed start time." else "Waktu selesai tampil harus lebih besar dari waktu mulai."))
                return@flow
            }

            val bmp = VideoBatch2Math.renderStyledTextBitmap(caption, fontStyle, textColor, fontSize, bgBox)
            val jobDir = ctx.storage.createJobTempDir("text_${UUID.randomUUID().toString().take(6)}")
            val overlayPng = File(jobDir, "text_overlay.png")
            FileOutputStream(overlayPng).use { fos -> bmp.compress(Bitmap.CompressFormat.PNG, 100, fos) }

            val (fx, fy) = VideoBatch2Math.computeFfmpegOverlayXY(position)
            val enableExpr = if (hasTiming) {
                val endVal = if (endSec > 0f) endSec else ((probe.durationMs / 1000f).coerceAtLeast(startSec + 1f))
                String.format(Locale.US, ":enable='between(t,%.2f,%.2f)'", startSec, endVal)
            } else ""

            try {
                if (hasTiming) {
                    emitAll(
                        mediaJob.runCommand(
                            context = ctx.appContext,
                            storage = ctx.storage,
                            inputUris = input.uris,
                            toolTag = "VID_TEXT",
                            outputExt = "mp4",
                            outputMime = "video/mp4",
                            expectedDurationMs = probe.durationMs,
                            isEnglish = ctx.isEnglish
                        ) { inputs, output ->
                            listOf(
                                "-y",
                                "-i", inputs.first().absolutePath,
                                "-i", overlayPng.absolutePath,
                                "-filter_complex", "[0:v][1:v]overlay=$fx:$fy$enableExpr[vout]",
                                "-map", "[vout]",
                                "-map", "0:a?",
                                "-c:v", "mpeg4", "-q:v", "3",
                                "-c:a", "copy",
                                "-movflags", "+faststart",
                                output.absolutePath
                            )
                        }
                    )
                } else {
                    val (ax, ay) = VideoBatch2Math.computeMedia3Anchor(position)
                    val overlaySettings = StaticOverlaySettings.Builder()
                        .setBackgroundFrameAnchor(ax, ay)
                        .setOverlayFrameAnchor(ax, ay)
                        .build()
                    val overlayEffect = OverlayEffect(
                        listOf(BitmapOverlay.createStaticBitmapOverlay(bmp, overlaySettings))
                    )
                    emitAll(
                        media3.runWithFfmpegFallback(
                            context = ctx.appContext,
                            storage = ctx.storage,
                            request = Media3Request(
                                inputUri = input.uris.first(),
                                toolTag = "VID_TEXT",
                                extraVideoEffects = listOf(overlayEffect)
                            ),
                            expectedDurationMs = probe.durationMs,
                            isEnglish = ctx.isEnglish
                        ) { inputs, output ->
                            listOf(
                                "-y",
                                "-i", inputs.first().absolutePath,
                                "-i", overlayPng.absolutePath,
                                "-filter_complex", "[0:v][1:v]overlay=$fx:$fy[vout]",
                                "-map", "[vout]",
                                "-map", "0:a?",
                                "-c:v", "mpeg4", "-q:v", "3",
                                "-c:a", "copy",
                                "-movflags", "+faststart",
                                output.absolutePath
                            )
                        }
                    )
                }
            } finally {
                if (!bmp.isRecycled) bmp.recycle()
                MediaUriUtils.cleanupQuietly(jobDir)
            }
        }.flowOn(Dispatchers.IO)
    },

    // VID-16: Add Audio to Video (Mix or Replace + Volume + Offset)
    object : Tool {
        override val id = "VID-16"
        override val group = ToolGroup.VIDEO
        override val engineLabel = "FF"
        override val icon = Icons.Filled.MusicNote
        override val inputSpec = InputSpec.VideoAndAudio
        override val params = listOf(
            ToolParam.DropdownParam("mode", "Mode Audio", "Audio Mode", listOf("Campur (Mix)", "Ganti Audio Asli"), "Campur (Mix)"),
            ToolParam.SliderParam("addedVol", "Volume Audio Baru (%)", "Added Audio Volume (%)", 10f, 200f, 100f, 5f, "%"),
            ToolParam.SliderParam("origVol", "Volume Audio Asli (%)", "Original Audio Volume (%)", 0f, 150f, 60f, 5f, "%"),
            ToolParam.SliderParam("offsetSec", "Tunda Mulai Audio (detik)", "Audio Start Offset (sec)", 0f, 60f, 0f, 0.5f, "s")
        )
        override fun title(isEnglish: Boolean) = if (isEnglish) "Add Audio to Video" else "Tambah Audio ke Video"
        override fun description(isEnglish: Boolean) = if (isEnglish) {
            "Mix or replace video audio with a background track and custom start offset."
        } else {
            "Campur atau ganti audio video dengan musik latar dan jeda mulai."
        }

        override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
            val validation = VideoToolMath.validateVideoInputs(ctx.appContext, ctx.storage, input.uris, isEnglish = ctx.isEnglish)
            if (!validation.valid) {
                emit(ToolProgress.Failed(validation.errorMessage ?: "Invalid video"))
                return@flow
            }
            val audioUri = input.secondaryUri
            if (audioUri == null || VideoToolMath.resolveInputSizeBytes(ctx.appContext, audioUri) <= 0L) {
                emit(
                    ToolProgress.Failed(
                        if (ctx.isEnglish) "Select a valid background audio file first."
                        else "Pilih file audio latar yang valid terlebih dahulu."
                    )
                )
                return@flow
            }
            val probe = validation.probes.first()
            val mode = input.getString("mode", "Campur (Mix)")
            val mix = mode.contains("Campur", ignoreCase = true) || mode.contains("Mix", ignoreCase = true)
            val addedVol = input.getFloat("addedVol", 100f)
            val origVol = input.getFloat("origVol", 60f)
            val offsetSec = input.getFloat("offsetSec", 0f).coerceAtLeast(0f)
            if (probe.durationMs > 0L && (offsetSec * 1000f).toLong() >= probe.durationMs) {
                emit(ToolProgress.Failed(if (ctx.isEnglish) "Audio offset reaches or exceeds video duration." else "Jeda mulai audio melebihi durasi video."))
                return@flow
            }
            val (fc, outMap) = VideoBatch2Math.buildAddAudioFilter(
                videoHasAudio = probe.hasAudio,
                mixWithOriginal = mix,
                origVolumePct = origVol,
                addedVolumePct = addedVol,
                offsetSec = offsetSec
            )

            emitAll(
                mediaJob.runCommand(
                    context = ctx.appContext,
                    storage = ctx.storage,
                    inputUris = listOf(input.uris.first(), audioUri),
                    toolTag = "VID_ADDAUDIO",
                    outputExt = "mp4",
                    outputMime = "video/mp4",
                    expectedDurationMs = probe.durationMs,
                    isEnglish = ctx.isEnglish
                ) { inputs, output ->
                    listOf(
                        "-y",
                        "-i", inputs[0].absolutePath,
                        "-i", inputs[1].absolutePath,
                        "-filter_complex", fc,
                        "-map", "0:v:0",
                        "-map", outMap,
                        "-c:v", "copy",
                        "-c:a", "aac", "-b:a", "192k",
                        "-shortest",
                        "-movflags", "+faststart",
                        output.absolutePath
                    )
                }
            )
        }.flowOn(Dispatchers.IO)
    },

    // VID-17: Add Image / Watermark Overlay
    object : Tool {
        override val id = "VID-17"
        override val group = ToolGroup.VIDEO
        override val engineLabel = "M3/FF"
        override val icon = Icons.Filled.Image
        override val inputSpec = InputSpec.VideoAndImage
        override val params = listOf(
            ToolParam.DropdownParam("position", "Posisi", "Position", listOf("Kanan Atas", "Kiri Atas", "Kanan Bawah", "Kiri Bawah", "Tengah"), "Kanan Atas"),
            ToolParam.SliderParam("scalePct", "Skala Lebar (%)", "Width Scale (%)", 10f, 70f, 22f, 2f, "%"),
            ToolParam.SliderParam("opacityPct", "Opasitas (%)", "Opacity (%)", 10f, 100f, 85f, 5f, "%"),
            ToolParam.SliderParam("startSec", "Mulai Tampil (detik)", "Show From (sec)", 0f, 3600f, 0f, 0.5f, "s"),
            ToolParam.SliderParam("endSec", "Selesai Tampil (0=akhir)", "Show Until (0=end)", 0f, 3600f, 0f, 0.5f, "s")
        )
        override fun title(isEnglish: Boolean) = if (isEnglish) "Add Image to Video" else "Tambah Gambar ke Video"
        override fun description(isEnglish: Boolean) = if (isEnglish) {
            "Overlay a logo or sticker image onto video with scale, opacity, and timing."
        } else {
            "Tempel logo atau gambar di atas video dengan skala, opasitas, dan waktu tampil."
        }

        override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
            val validation = VideoToolMath.validateVideoInputs(ctx.appContext, ctx.storage, input.uris, isEnglish = ctx.isEnglish)
            if (!validation.valid) {
                emit(ToolProgress.Failed(validation.errorMessage ?: "Invalid video"))
                return@flow
            }
            val imgUri = input.secondaryUri
            if (imgUri == null || VideoToolMath.resolveInputSizeBytes(ctx.appContext, imgUri) <= 0L) {
                emit(
                    ToolProgress.Failed(
                        if (ctx.isEnglish) "Select an overlay image first."
                        else "Pilih gambar overlay terlebih dahulu."
                    )
                )
                return@flow
            }
            val probe = validation.probes.first()
            val position = input.getString("position", "Kanan Atas")
            val scalePct = input.getFloat("scalePct", 22f).coerceIn(10f, 70f)
            val opacityPct = input.getFloat("opacityPct", 85f).coerceIn(10f, 100f)
            val startSec = input.getFloat("startSec", 0f).coerceAtLeast(0f)
            val endSec = input.getFloat("endSec", 0f).coerceAtLeast(0f)
            if (probe.durationMs > 0L && (startSec * 1000f).toLong() >= probe.durationMs) {
                emit(ToolProgress.Failed(if (ctx.isEnglish) "Show start time reaches or exceeds video duration." else "Waktu mulai tampil melebihi durasi video."))
                return@flow
            }
            if (endSec > 0f && endSec <= startSec) {
                emit(ToolProgress.Failed(if (ctx.isEnglish) "End time must exceed start time." else "Waktu selesai tampil harus lebih besar dari waktu mulai."))
                return@flow
            }
            val hasTiming = startSec > 0.05f || (endSec > 0.05f && (probe.durationMs <= 0L || endSec * 1000f < probe.durationMs - 200f))

            val jobDir = ctx.storage.createJobTempDir("imgovr_${UUID.randomUUID().toString().take(6)}")
            val localImg = decodeScaledOverlayBitmap(ctx.appContext, imgUri, probe.orientedWidth.takeIf { it > 0 } ?: 1280, scalePct)
            if (localImg == null) {
                MediaUriUtils.cleanupQuietly(jobDir)
                emit(ToolProgress.Failed(if (ctx.isEnglish) "Could not decode overlay image." else "Gagal membaca gambar overlay."))
                return@flow
            }
            val pngFile = File(jobDir, "overlay.png")
            FileOutputStream(pngFile).use { fos -> localImg.compress(Bitmap.CompressFormat.PNG, 100, fos) }

            val (fx, fy) = VideoBatch2Math.computeFfmpegOverlayXY(position)
            val alpha = String.format(Locale.US, "%.2f", opacityPct / 100f)
            val enableExpr = if (hasTiming) {
                val endVal = if (endSec > 0f) endSec else ((probe.durationMs / 1000f).coerceAtLeast(startSec + 1f))
                String.format(Locale.US, ":enable='between(t,%.2f,%.2f)'", startSec, endVal)
            } else ""
            val fc = "[1:v]format=rgba,colorchannelmixer=aa=$alpha[ovr];[0:v][ovr]overlay=$fx:$fy$enableExpr[vout]"

            try {
                if (hasTiming) {
                    emitAll(
                        mediaJob.runCommand(
                            context = ctx.appContext,
                            storage = ctx.storage,
                            inputUris = input.uris,
                            toolTag = "VID_IMG",
                            outputExt = "mp4",
                            outputMime = "video/mp4",
                            expectedDurationMs = probe.durationMs,
                            isEnglish = ctx.isEnglish
                        ) { inputs, output ->
                            listOf(
                                "-y",
                                "-i", inputs.first().absolutePath,
                                "-i", pngFile.absolutePath,
                                "-filter_complex", fc,
                                "-map", "[vout]",
                                "-map", "0:a?",
                                "-c:v", "mpeg4", "-q:v", "3",
                                "-c:a", "copy",
                                "-movflags", "+faststart",
                                output.absolutePath
                            )
                        }
                    )
                } else {
                    val (ax, ay) = VideoBatch2Math.computeMedia3Anchor(position)
                    val settings = StaticOverlaySettings.Builder()
                        .setBackgroundFrameAnchor(ax, ay)
                        .setOverlayFrameAnchor(ax, ay)
                        .setAlphaScale(opacityPct / 100f)
                        .build()
                    val overlayEffect = OverlayEffect(
                        listOf(BitmapOverlay.createStaticBitmapOverlay(localImg, settings))
                    )
                    emitAll(
                        media3.runWithFfmpegFallback(
                            context = ctx.appContext,
                            storage = ctx.storage,
                            request = Media3Request(
                                inputUri = input.uris.first(),
                                toolTag = "VID_IMG",
                                extraVideoEffects = listOf(overlayEffect)
                            ),
                            expectedDurationMs = probe.durationMs,
                            isEnglish = ctx.isEnglish
                        ) { inputs, output ->
                            listOf(
                                "-y",
                                "-i", inputs.first().absolutePath,
                                "-i", pngFile.absolutePath,
                                "-filter_complex", fc,
                                "-map", "[vout]",
                                "-map", "0:a?",
                                "-c:v", "mpeg4", "-q:v", "3",
                                "-c:a", "copy",
                                "-movflags", "+faststart",
                                output.absolutePath
                            )
                        }
                    )
                }
            } finally {
                if (!localImg.isRecycled) localImg.recycle()
                MediaUriUtils.cleanupQuietly(jobDir)
            }
        }.flowOn(Dispatchers.IO)
    },

    // VID-23: Add Subtitles (SRT / VTT burn-in with styling)
    object : Tool {
        override val id = "VID-23"
        override val group = ToolGroup.VIDEO
        override val engineLabel = "FF"
        override val icon = Icons.Filled.Subtitles
        override val inputSpec = InputSpec.VideoAndSubtitle
        override val params = listOf(
            ToolParam.SliderParam("fontSize", "Ukuran Subtitle", "Subtitle Size", 20f, 64f, 36f, 2f, "pt"),
            ToolParam.DropdownParam("color", "Warna Teks", "Text Color", listOf("Putih", "Kuning", "Oranye", "Cyan", "Hijau"), "Putih"),
            ToolParam.DropdownParam("style", "Gaya Latar", "Box Style", listOf("Outline Hitam", "Kotak Gelap", "Tanpa Latar"), "Outline Hitam"),
            ToolParam.DropdownParam("position", "Posisi", "Position", listOf("Bawah", "Atas", "Tengah"), "Bawah")
        )
        override fun title(isEnglish: Boolean) = if (isEnglish) "Add Subtitles (SRT/VTT)" else "Tambah Subtitle (SRT/VTT)"
        override fun description(isEnglish: Boolean) = if (isEnglish) {
            "Burn .srt or .vtt subtitles directly onto video with custom font size and box style."
        } else {
            "Bakar subtitle .srt atau .vtt permanen ke video dengan pilihan warna dan gaya."
        }

        override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
            val validation = VideoToolMath.validateVideoInputs(ctx.appContext, ctx.storage, input.uris, isEnglish = ctx.isEnglish)
            if (!validation.valid) {
                emit(ToolProgress.Failed(validation.errorMessage ?: "Invalid video"))
                return@flow
            }
            val subUri = input.secondaryUri
            if (subUri == null) {
                emit(
                    ToolProgress.Failed(
                        if (ctx.isEnglish) "Select an .srt or .vtt subtitle file first."
                        else "Pilih file subtitle .srt atau .vtt terlebih dahulu."
                    )
                )
                return@flow
            }
            val rawText = runCatching {
                ctx.appContext.contentResolver.openInputStream(subUri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull().orEmpty()
            val cues = SubtitleParser.parseSrtOrVtt(rawText)
            if (cues.isEmpty()) {
                emit(
                    ToolProgress.Failed(
                        if (ctx.isEnglish) "Subtitle file is empty or contains no valid SRT/VTT cues."
                        else "File subtitle kosong atau tidak memiliki format SRT/VTT yang valid."
                    )
                )
                return@flow
            }

            val probe = validation.probes.first()
            FfmpegCapabilities.requireFilter("ass", ctx.isEnglish)
            val jobDir = ctx.storage.createJobTempDir("sub_${UUID.randomUUID().toString().take(6)}")
            val assFile = File(jobDir, "styled_subs.ass")
            assFile.writeText(
                SubtitleParser.toAssContent(
                    cues = cues,
                    playResW = probe.orientedWidth.takeIf { it > 0 } ?: 1280,
                    playResH = probe.orientedHeight.takeIf { it > 0 } ?: 720,
                    fontSize = input.getFloat("fontSize", 36f).roundToInt(),
                    colorName = input.getString("color", "Putih"),
                    styleMode = input.getString("style", "Outline Hitam"),
                    position = input.getString("position", "Bawah")
                ),
                Charsets.UTF_8
            )
            runCatching { FFmpegKitConfig.setFontDirectory(ctx.appContext, "/system/fonts", null) }
            val escapedAssPath = assFile.absolutePath.replace("\\", "/").replace(":", "\\:").replace("'", "\\'")

            try {
                emitAll(
                    mediaJob.runCommand(
                        context = ctx.appContext,
                        storage = ctx.storage,
                        inputUris = input.uris,
                        toolTag = "VID_SUB",
                        outputExt = "mp4",
                        outputMime = "video/mp4",
                        expectedDurationMs = probe.durationMs,
                        isEnglish = ctx.isEnglish
                    ) { inputs, output ->
                        buildList {
                            add("-y")
                            add("-i"); add(inputs.first().absolutePath)
                            add("-vf"); add("ass='$escapedAssPath'")
                            add("-c:v"); add("mpeg4"); add("-q:v"); add("3")
                            if (probe.hasAudio) {
                                add("-c:a"); add("copy")
                            } else {
                                add("-an")
                            }
                            add("-movflags"); add("+faststart")
                            add(output.absolutePath)
                        }
                    }
                )
            } finally {
                MediaUriUtils.cleanupQuietly(jobDir)
            }
        }.flowOn(Dispatchers.IO)
    }
)

private fun decodeScaledOverlayBitmap(
    context: android.content.Context,
    uri: Uri,
    videoWidth: Int,
    scalePct: Float
): Bitmap? = runCatching {
    val targetW = ((videoWidth.coerceAtLeast(360) * scalePct) / 100f).roundToInt().coerceIn(48, 960)
    val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, boundsOpts) }
    var sample = 1
    while (boundsOpts.outWidth / sample > targetW * 2) sample *= 2

    val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
    val raw = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, decodeOpts) }
        ?: return@runCatching null
    val aspect = raw.height.toFloat() / raw.width.coerceAtLeast(1).toFloat()
    val outW = VideoToolMath.makeEven(targetW, 16)
    val outH = VideoToolMath.makeEven((outW * aspect).roundToInt(), 16)
    if (raw.width == outW && raw.height == outH) return@runCatching raw
    val scaled = Bitmap.createScaledBitmap(raw, outW, outH, true)
    if (scaled != raw) raw.recycle()
    scaled
}.getOrNull()
