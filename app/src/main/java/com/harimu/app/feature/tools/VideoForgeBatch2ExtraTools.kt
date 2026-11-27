package com.harimu.app.feature.tools

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Rect
import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BurstMode
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.FaceRetouchingOff
import androidx.compose.material.icons.filled.FilterBAndW
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Speed
import androidx.exifinterface.media.ExifInterface
import androidx.media3.common.util.UnstableApi
import com.harimu.app.core.media.FaceBlurDetector
import com.harimu.app.core.media.FfmpegCapabilities
import com.harimu.app.core.media.Media3Request
import com.harimu.app.core.media.Media3TransformerEngine
import com.harimu.app.core.media.MediaJob
import com.harimu.app.core.media.MediaProbe
import com.harimu.app.core.media.MediaUriUtils
import com.harimu.app.core.media.TransitionClipInfo
import com.harimu.app.core.media.VideoBatch2Math
import com.harimu.app.core.media.VideoToolMath
import com.harimu.app.core.toolapi.InputSpec
import com.harimu.app.core.toolapi.Tool
import com.harimu.app.core.toolapi.ToolContext
import com.harimu.app.core.toolapi.ToolGroup
import com.harimu.app.core.toolapi.ToolInput
import com.harimu.app.core.toolapi.ToolParam
import com.harimu.app.core.toolapi.ToolProgress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
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
fun videoForgeBatch2ExtraTools(
    media3: Media3TransformerEngine = Media3TransformerEngine(),
    mediaJob: MediaJob = MediaJob()
): List<Tool> = listOf(
    // VID-25: Blur Faces (ML Kit Face Detection + sampling + simple IoU tracking)
    object : Tool {
        override val id = "VID-25"
        override val group = ToolGroup.VIDEO
        override val engineLabel = "ML/FF"
        override val icon = Icons.Filled.FaceRetouchingOff
        override val inputSpec = InputSpec.SingleVideo
        override val params = listOf(
            ToolParam.DropdownParam("blurMode", "Mode Samaran", "Mask Mode", listOf("Blur Halus", "Pikselasi"), "Blur Halus"),
            ToolParam.SliderParam("strength", "Kekuatan Blur", "Blur Strength", 8f, 30f, 18f, 2f, ""),
            ToolParam.SliderParam("samplingFps", "Sampling Deteksi (fps)", "Detection Rate (fps)", 1f, 4f, 2f, 1f, "fps")
        )
        override fun title(isEnglish: Boolean) = if (isEnglish) "Blur Faces" else "Blur Wajah Video"
        override fun description(isEnglish: Boolean) = if (isEnglish) {
            "Detect faces via ML Kit sampling + tracking and blur or pixelate tracked boxes."
        } else {
            "Deteksi wajah dengan sampling ML Kit + pelacakan sederhana lalu samarkan otomatis."
        }

        override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
            val validation = VideoToolMath.validateVideoInputs(ctx.appContext, ctx.storage, input.uris, isEnglish = ctx.isEnglish)
            if (!validation.valid) {
                emit(ToolProgress.Failed(validation.errorMessage ?: "Invalid video"))
                return@flow
            }
            val probe = validation.probes.first()
            val samplingFps = input.getFloat("samplingFps", 2f).roundToInt().coerceIn(1, 4)
            val blurStrength = input.getFloat("strength", 18f).roundToInt().coerceIn(8, 30)
            val pixelate = input.getString("blurMode", "Blur Halus").contains("Piksel", ignoreCase = true)

            emit(ToolProgress.Running(5, if (ctx.isEnglish) "Scanning faces with ML Kit…" else "Memindai wajah dengan ML Kit…"))
            val samplingResult = FaceBlurDetector.sampleVideoFaces(
                context = ctx.appContext,
                uri = input.uris.first(),
                durationMs = probe.durationMs,
                samplingFps = samplingFps
            ) { pct ->
                emit(ToolProgress.Running(pct, if (ctx.isEnglish) "Detecting faces ($pct%)…" else "Mendeteksi wajah ($pct%)…"))
            }

            if (samplingResult.samples.isEmpty()) {
                emit(
                    ToolProgress.Failed(
                        if (ctx.isEnglish) {
                            "No frontal faces detected across sampled frames. Try a higher detection FPS or closer/front-facing video."
                        } else {
                            "Tidak ada wajah terdeteksi pada frame sampel. Coba naikkan fps sampling atau gunakan video menghadap kamera."
                        }
                    )
                )
                return@flow
            }

            val srcW = probe.orientedWidth.takeIf { it > 0 } ?: 1280
            val srcH = probe.orientedHeight.takeIf { it > 0 } ?: 720
            val windows = VideoBatch2Math.smoothAndGroupFaceTracks(
                samples = samplingResult.samples,
                srcWidth = srcW,
                srcHeight = srcH,
                detectWidth = samplingResult.detectWidth,
                detectHeight = samplingResult.detectHeight,
                totalDurationMs = probe.durationMs
            )
            FfmpegCapabilities.requireFilter(if (pixelate) "scale" else "gblur", ctx.isEnglish)
            val filterGraph = VideoBatch2Math.buildFaceBlurFilterGraph(windows, pixelate, blurStrength)
            emit(
                ToolProgress.Running(
                    60,
                    if (ctx.isEnglish) "Rendering ${windows.size} tracked face blur windows…"
                    else "Merender ${windows.size} segmen blur wajah terlacak…"
                )
            )

            emitAll(
                mediaJob.runCommand(
                    context = ctx.appContext,
                    storage = ctx.storage,
                    inputUris = input.uris,
                    toolTag = "VID_FACEBLUR",
                    outputExt = "mp4",
                    outputMime = "video/mp4",
                    expectedDurationMs = probe.durationMs,
                    isEnglish = ctx.isEnglish
                ) { inputs, output ->
                    buildList {
                        add("-y")
                        add("-i"); add(inputs.first().absolutePath)
                        add("-filter_complex"); add(filterGraph)
                        add("-map"); add("[vout]")
                        add("-c:v"); add("mpeg4"); add("-q:v"); add("3")
                        if (probe.hasAudio) {
                            add("-map"); add("0:a?")
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

    // VID-26: Color Grading (Brightness, Contrast, Saturation, Temperature, LUT Presets)
    object : Tool {
        override val id = "VID-26"
        override val group = ToolGroup.VIDEO
        override val engineLabel = "M3/FF"
        override val icon = Icons.Filled.Palette
        override val inputSpec = InputSpec.SingleVideo
        override val params = listOf(
            ToolParam.DropdownParam(
                "preset",
                "Preset LUT / Warna",
                "Color / LUT Preset",
                listOf("Manual", "Sinematik Hangat", "Dingin Nordik", "Vivid Pop", "Film Pudar", "Monokrom"),
                "Manual"
            ),
            ToolParam.SliderParam("brightness", "Kecerahan", "Brightness", -0.4f, 0.4f, 0.0f, 0.05f, ""),
            ToolParam.SliderParam("contrast", "Kontras", "Contrast", 0.6f, 1.8f, 1.05f, 0.05f, "x"),
            ToolParam.SliderParam("saturation", "Saturasi", "Saturation", 0.0f, 2.0f, 1.10f, 0.05f, "x"),
            ToolParam.SliderParam("temperature", "Suhu Warna (Dingin ↔ Hangat)", "Temperature (Cool ↔ Warm)", -1.0f, 1.0f, 0.0f, 0.1f, "")
        )
        override fun title(isEnglish: Boolean) = if (isEnglish) "Color Grading" else "Color Grading Video"
        override fun description(isEnglish: Boolean) = if (isEnglish) {
            "Adjust brightness, contrast, saturation, temperature, or apply built-in LUT presets."
        } else {
            "Atur kecerahan, kontras, saturasi, suhu warna, atau pakai preset LUT siap pakai."
        }

        override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
            val validation = VideoToolMath.validateVideoInputs(ctx.appContext, ctx.storage, input.uris, isEnglish = ctx.isEnglish)
            if (!validation.valid) {
                emit(ToolProgress.Failed(validation.errorMessage ?: "Invalid video"))
                return@flow
            }
            val probe = validation.probes.first()
            val preset = input.getString("preset", "Manual")
            val cfg = VideoBatch2Math.colorPresetConfig(
                preset = preset,
                manualBrightness = input.getFloat("brightness", 0f),
                manualContrast = input.getFloat("contrast", 1.05f),
                manualSaturation = input.getFloat("saturation", 1.1f),
                manualTemp = input.getFloat("temperature", 0f)
            )

            val jobDir = ctx.storage.createJobTempDir("lut_${UUID.randomUUID().toString().take(6)}")
            val lutPath = if (cfg.lutName != "None") {
                File(jobDir, "${cfg.lutName}.cube").apply {
                    writeText(VideoBatch2Math.generatePresetCubeLut(cfg), Charsets.UTF_8)
                }.absolutePath
            } else null

            val m3Effects = VideoBatch2Math.buildMedia3ColorEffects(cfg)
            val vf = VideoBatch2Math.buildColorGradingFfmpegFilter(cfg, lutPath)

            try {
                emitAll(
                    media3.runWithFfmpegFallback(
                        context = ctx.appContext,
                        storage = ctx.storage,
                        request = Media3Request(
                            inputUri = input.uris.first(),
                            toolTag = "VID_COLOR",
                            extraVideoEffects = m3Effects
                        ),
                        expectedDurationMs = probe.durationMs,
                        isEnglish = ctx.isEnglish
                    ) { inputs, output ->
                        listOf(
                            "-y", "-i", inputs.first().absolutePath,
                            "-vf", vf,
                            "-c:v", "mpeg4", "-q:v", "3",
                            if (probe.hasAudio) "-c:a" else "-an",
                            *(if (probe.hasAudio) arrayOf("copy") else emptyArray()),
                            "-movflags", "+faststart",
                            output.absolutePath
                        )
                    }
                )
            } finally {
                MediaUriUtils.cleanupQuietly(jobDir)
            }
        }.flowOn(Dispatchers.IO)
    },

    // VID-27: Video Transitions (xfade + acrossfade)
    object : Tool {
        override val id = "VID-27"
        override val group = ToolGroup.VIDEO
        override val engineLabel = "FF"
        override val icon = Icons.Filled.Compare
        override val inputSpec = InputSpec.MultipleVideos
        override val params = listOf(
            ToolParam.DropdownParam(
                "transition",
                "Efek Transisi",
                "Transition Effect",
                listOf("fade", "dissolve", "wipeleft", "wiperight", "slideup", "slidedown", "circleopen"),
                "fade"
            ),
            ToolParam.SliderParam("durationSec", "Durasi Transisi (detik)", "Transition Duration (sec)", 0.5f, 3.0f, 1.0f, 0.25f, "s"),
            ToolParam.DropdownParam("resolution", "Resolusi Output", "Output Resolution", listOf("720p", "1080p", "480p"), "720p")
        )
        override fun title(isEnglish: Boolean) = if (isEnglish) "Video Transitions" else "Transisi Video"
        override fun description(isEnglish: Boolean) = if (isEnglish) {
            "Join multiple video clips with smooth crossfade, wipe, slide, or circle transitions."
        } else {
            "Gabungkan beberapa klip video dengan transisi fade, dissolve, wipe, atau slide."
        }

        override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
            val validation = VideoToolMath.validateVideoInputs(
                context = ctx.appContext,
                storage = ctx.storage,
                uris = input.uris,
                minCount = 2,
                isEnglish = ctx.isEnglish
            )
            if (!validation.valid) {
                emit(ToolProgress.Failed(validation.errorMessage ?: "Select at least 2 videos"))
                return@flow
            }
            val probes = validation.probes
            val firstProbe = probes.first()
            val resPreset = input.getString("resolution", "720p")
            val dims = VideoToolMath.calculateTargetDimensions(
                srcWidth = firstProbe.orientedWidth.takeIf { it > 0 } ?: 1280,
                srcHeight = firstProbe.orientedHeight.takeIf { it > 0 } ?: 720,
                preset = resPreset
            )
            val transition = input.getString("transition", "fade")
            val transSec = input.getFloat("durationSec", 1.0f)
            val clipInfos = probes.mapIndexed { idx, p ->
                TransitionClipInfo(
                    path = idx.toString(),
                    durationMs = p.durationMs.coerceAtLeast(1_500L),
                    hasAudio = p.hasAudio
                )
            }
            val (fc, mapAudio) = VideoBatch2Math.buildTransitionFilterGraph(
                clips = clipInfos,
                transitionName = transition,
                transitionDurationSec = transSec,
                targetWidth = dims.width,
                targetHeight = dims.height
            )
            val totalExpectedMs = probes.sumOf { it.durationMs.coerceAtLeast(1500L) }

            emitAll(
                mediaJob.runCommand(
                    context = ctx.appContext,
                    storage = ctx.storage,
                    inputUris = input.uris,
                    toolTag = "VID_TRANS",
                    outputExt = "mp4",
                    outputMime = "video/mp4",
                    expectedDurationMs = totalExpectedMs,
                    isEnglish = ctx.isEnglish
                ) { inputs, output ->
                    buildList {
                        add("-y")
                        inputs.forEach { f -> add("-i"); add(f.absolutePath) }
                        add("-filter_complex"); add(fc)
                        add("-map"); add("[vout]")
                        if (mapAudio) {
                            add("-map"); add("[aout]")
                            add("-c:a"); add("aac"); add("-b:a"); add("192k")
                        } else {
                            add("-an")
                        }
                        add("-c:v"); add("mpeg4"); add("-q:v"); add("3")
                        add("-movflags"); add("+faststart")
                        add(output.absolutePath)
                    }
                }
            )
        }.flowOn(Dispatchers.IO)
    },

    // VID-28: Green Screen (Chroma Key)
    object : Tool {
        override val id = "VID-28"
        override val group = ToolGroup.VIDEO
        override val engineLabel = "FF"
        override val icon = Icons.Filled.FilterBAndW
        override val inputSpec = InputSpec.VideoAndMedia
        override val params = listOf(
            ToolParam.DropdownParam("keyColor", "Warna Kunci", "Key Color", listOf("Hijau (#00FF00)", "Biru (#0000FF)", "Putih (#FFFFFF)"), "Hijau (#00FF00)"),
            ToolParam.SliderParam("similarity", "Kemiripan Warna", "Color Similarity", 0.05f, 0.55f, 0.22f, 0.02f, ""),
            ToolParam.SliderParam("blend", "Halus Tepi (Blend)", "Edge Blend", 0.0f, 0.25f, 0.08f, 0.02f, ""),
            ToolParam.DropdownParam("fallbackBg", "Latar Default (jika tanpa media)", "Default BG (if no media)", listOf("Gelap Studio", "Putih Bersih", "Biru Malam"), "Gelap Studio")
        )
        override fun title(isEnglish: Boolean) = if (isEnglish) "Green Screen (Chroma Key)" else "Green Screen (Chroma Key)"
        override fun description(isEnglish: Boolean) = if (isEnglish) {
            "Key out green/blue backgrounds and composite over a chosen image, video, or solid studio backdrop."
        } else {
            "Hapus latar hijau/biru dan gabungkan dengan gambar, video, atau latar solid pilihan."
        }

        override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
            val validation = VideoToolMath.validateVideoInputs(ctx.appContext, ctx.storage, input.uris, isEnglish = ctx.isEnglish)
            if (!validation.valid) {
                emit(ToolProgress.Failed(validation.errorMessage ?: "Invalid video"))
                return@flow
            }
            val probe = validation.probes.first()
            val bgUri = input.secondaryUri
            val hasBg = bgUri != null && VideoToolMath.resolveInputSizeBytes(ctx.appContext, bgUri) > 0L
            val bgIsVideo = if (bgUri != null) {
                val mime = runCatching { ctx.appContext.contentResolver.getType(bgUri) }.getOrNull().orEmpty()
                mime.startsWith("video/") || MediaProbe.probe(ctx.appContext, bgUri).hasVideo
            } else false

            FfmpegCapabilities.requireFilter("chromakey", ctx.isEnglish)
            val keyHex = VideoBatch2Math.mapChromaKeyHex(input.getString("keyColor", "Hijau (#00FF00)"))
            val fallbackHex = VideoBatch2Math.mapFallbackBgColorHex(input.getString("fallbackBg", "Gelap Studio"))
            val sim = input.getFloat("similarity", 0.22f)
            val blend = input.getFloat("blend", 0.08f)
            val targetW = probe.orientedWidth.takeIf { it > 0 } ?: 1280
            val targetH = probe.orientedHeight.takeIf { it > 0 } ?: 720
            val fc = VideoBatch2Math.buildChromaKeyFilter(
                hasBackgroundMedia = hasBg,
                backgroundIsVideo = bgIsVideo,
                keyHex = keyHex,
                similarity = sim,
                blend = blend,
                targetW = targetW,
                targetH = targetH,
                fallbackBgHex = fallbackHex
            )

            val allUris = if (hasBg) listOf(input.uris.first(), bgUri!!) else listOf(input.uris.first())
            emitAll(
                mediaJob.runCommand(
                    context = ctx.appContext,
                    storage = ctx.storage,
                    inputUris = allUris,
                    toolTag = "VID_CHROMA",
                    outputExt = "mp4",
                    outputMime = "video/mp4",
                    expectedDurationMs = probe.durationMs,
                    isEnglish = ctx.isEnglish
                ) { inputs, output ->
                    buildList {
                        add("-y")
                        add("-i"); add(inputs[0].absolutePath)
                        if (hasBg && inputs.size > 1) {
                            if (!bgIsVideo) {
                                add("-loop"); add("1")
                            }
                            add("-i"); add(inputs[1].absolutePath)
                        }
                        add("-filter_complex"); add(fc)
                        add("-map"); add("[vout]")
                        add("-c:v"); add("mpeg4"); add("-q:v"); add("3")
                        if (probe.hasAudio) {
                            add("-map"); add("0:a?")
                            add("-c:a"); add("copy")
                        } else {
                            add("-an")
                        }
                        add("-shortest")
                        add("-movflags"); add("+faststart")
                        add(output.absolutePath)
                    }
                }
            )
        }.flowOn(Dispatchers.IO)
    },

    // VID-29: Stop Motion (Multiple Images -> MP4 via Media3/FFmpeg)
    object : Tool {
        override val id = "VID-29"
        override val group = ToolGroup.VIDEO
        override val engineLabel = "M3/FF"
        override val icon = Icons.Filled.BurstMode
        override val inputSpec = InputSpec.MultipleImages
        override val params = listOf(
            ToolParam.SliderParam("fps", "Kecepatan (FPS)", "Speed (FPS)", 1f, 24f, 6f, 1f, "fps"),
            ToolParam.DropdownParam("resolution", "Resolusi Video", "Video Resolution", listOf("720p", "1080p", "480p"), "720p"),
            ToolParam.DropdownParam("fitMode", "Penyesuaian Rasio", "Aspect Fit", listOf("Contain (Pad)", "Cover (Crop)"), "Contain (Pad)")
        )
        override fun title(isEnglish: Boolean) = if (isEnglish) "Stop Motion" else "Stop Motion"
        override fun description(isEnglish: Boolean) = if (isEnglish) {
            "Assemble a sequence of photos into a stop motion MP4 animation."
        } else {
            "Rangkai kumpulan foto berurutan menjadi video animasi stop motion MP4."
        }

        override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
            if (input.uris.size < 2) {
                emit(ToolProgress.Failed(if (ctx.isEnglish) "Select at least 2 photos." else "Pilih minimal 2 foto untuk Stop Motion."))
                return@flow
            }
            if (!ctx.storage.hasEnoughDiskSpace()) {
                emit(ToolProgress.Failed(if (ctx.isEnglish) "Not enough free disk space." else "Ruang disk tidak cukup."))
                return@flow
            }
            val fps = input.getFloat("fps", 6f).roundToInt().coerceIn(1, 24)
            val shortSide = VideoToolMath.parseResolutionShortSide(input.getString("resolution", "720p"))
            val firstBounds = inspectImageOrientedSize(ctx.appContext, input.uris.first())
            val isLandscape = firstBounds == null || firstBounds.first >= firstBounds.second
            val outW = VideoToolMath.makeEven(if (isLandscape) (shortSide * 16f / 9f).roundToInt() else shortSide)
            val outH = VideoToolMath.makeEven(if (isLandscape) shortSide else (shortSide * 16f / 9f).roundToInt())
            val coverCrop = input.getString("fitMode", "Contain (Pad)").contains("Cover", ignoreCase = true)

            val jobDir = ctx.storage.createJobTempDir("stopmotion_${UUID.randomUUID().toString().take(6)}")
            val normalizedUris = mutableListOf<Uri>()
            try {
                emit(ToolProgress.Running(5, if (ctx.isEnglish) "Preparing ${input.uris.size} frames…" else "Menyiapkan ${input.uris.size} frame foto…"))
                input.uris.forEachIndexed { idx, uri ->
                    currentCoroutineContext().ensureActive()
                    val frameFile = File(jobDir, String.format(Locale.US, "frame_%04d.jpg", idx + 1))
                    val written = normalizeImageToFrame(ctx.appContext, uri, frameFile, outW, outH, coverCrop)
                    if (!written) {
                        MediaUriUtils.cleanupQuietly(jobDir)
                        emit(ToolProgress.Failed(if (ctx.isEnglish) "Unable to decode photo #${idx + 1}." else "Gagal membaca foto ke-${idx + 1}."))
                        return@flow
                    }
                    normalizedUris.add(Uri.fromFile(frameFile))
                    val prepPct = 5 + (((idx + 1) * 35) / input.uris.size)
                    emit(ToolProgress.Running(prepPct, if (ctx.isEnglish) "Normalized frame ${idx + 1}/${input.uris.size}" else "Menyiapkan frame ${idx + 1}/${input.uris.size}"))
                }

                val frameDurMs = (1000L / fps.toLong()).coerceAtLeast(40L)
                val totalMs = frameDurMs * normalizedUris.size.toLong()
                val req = Media3Request(
                    inputUri = normalizedUris.first(),
                    additionalInputUris = normalizedUris.drop(1),
                    toolTag = "VID_STOPMOTION",
                    imageFrameDurationMs = frameDurMs
                )

                emitAll(
                    media3.runWithFfmpegFallback(
                        context = ctx.appContext,
                        storage = ctx.storage,
                        request = req,
                        expectedDurationMs = totalMs,
                        isEnglish = ctx.isEnglish
                    ) { _, output ->
                        val pattern = File(jobDir, "frame_%04d.jpg").absolutePath
                        listOf(
                            "-y",
                            "-framerate", fps.toString(),
                            "-i", pattern,
                            "-c:v", "mpeg4", "-q:v", "3",
                            "-pix_fmt", "yuv420p",
                            "-movflags", "+faststart",
                            output.absolutePath
                        )
                    }
                )
            } catch (ce: CancellationException) {
                throw ce
            } finally {
                MediaUriUtils.cleanupQuietly(jobDir)
            }
        }.flowOn(Dispatchers.IO)
    },

    // VID-30: Time Lapse
    object : Tool {
        override val id = "VID-30"
        override val group = ToolGroup.VIDEO
        override val engineLabel = "FF"
        override val icon = Icons.Filled.Speed
        override val inputSpec = InputSpec.SingleVideo
        override val params = listOf(
            ToolParam.DropdownParam("speedFactor", "Faktor Percepatan", "Speedup Factor", listOf("4x", "8x", "16x", "32x"), "8x"),
            ToolParam.DropdownParam("targetFps", "FPS Output", "Output FPS", listOf("24", "30", "60"), "30"),
            ToolParam.ToggleParam("keepAudio", "Pertahankan audio (hanya 4x)", "Keep audio (4x only)", false)
        )
        override fun title(isEnglish: Boolean) = if (isEnglish) "Time Lapse" else "Time Lapse"
        override fun description(isEnglish: Boolean) = if (isEnglish) {
            "Compress long video recordings into a smooth high-speed 4x–32x time lapse."
        } else {
            "Padatkan rekaman panjang menjadi video time lapse halus 4x–32x."
        }

        override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
            val validation = VideoToolMath.validateVideoInputs(ctx.appContext, ctx.storage, input.uris, isEnglish = ctx.isEnglish)
            if (!validation.valid) {
                emit(ToolProgress.Failed(validation.errorMessage ?: "Invalid video"))
                return@flow
            }
            val probe = validation.probes.first()
            val factor = input.getString("speedFactor", "8x").trimEnd('x', 'X').toIntOrNull()?.coerceIn(2, 64) ?: 8
            val targetFps = input.getString("targetFps", "30").toIntOrNull()?.coerceIn(15, 60) ?: 30
            val keepAudio = input.getBoolean("keepAudio", false) && factor <= 4 && probe.hasAudio

            val vf = VideoBatch2Math.buildTimeLapseVideoFilter(factor, targetFps)
            val expectedMs = (probe.durationMs / factor.toLong()).coerceAtLeast(500L)

            emitAll(
                mediaJob.runCommand(
                    context = ctx.appContext,
                    storage = ctx.storage,
                    inputUris = input.uris,
                    toolTag = "VID_TIMELAPSE",
                    outputExt = "mp4",
                    outputMime = "video/mp4",
                    expectedDurationMs = expectedMs,
                    isEnglish = ctx.isEnglish
                ) { inputs, output ->
                    buildList {
                        add("-y")
                        add("-i"); add(inputs.first().absolutePath)
                        add("-vf"); add(vf)
                        if (keepAudio) {
                            add("-af"); add(VideoToolMath.buildAtempoFilterChain(factor.toFloat()))
                            add("-c:a"); add("aac"); add("-b:a"); add("160k")
                        } else {
                            add("-an")
                        }
                        add("-c:v"); add("mpeg4"); add("-q:v"); add("3")
                        add("-movflags"); add("+faststart")
                        add(output.absolutePath)
                    }
                }
            )
        }.flowOn(Dispatchers.IO)
    }
)

private fun readExifOrientation(context: android.content.Context, uri: Uri): Int = runCatching {
    context.contentResolver.openInputStream(uri)?.use { stream ->
        ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    } ?: ExifInterface.ORIENTATION_NORMAL
}.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

private fun inspectImageOrientedSize(context: android.content.Context, uri: Uri): Pair<Int, Int>? = runCatching {
    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    if (opts.outWidth <= 0 || opts.outHeight <= 0) return@runCatching null
    val orientation = readExifOrientation(context, uri)
    val rotated = orientation == ExifInterface.ORIENTATION_ROTATE_90 ||
        orientation == ExifInterface.ORIENTATION_ROTATE_270 ||
        orientation == ExifInterface.ORIENTATION_TRANSPOSE ||
        orientation == ExifInterface.ORIENTATION_TRANSVERSE
    if (rotated) opts.outHeight to opts.outWidth else opts.outWidth to opts.outHeight
}.getOrNull()

private fun applyExifOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
        ExifInterface.ORIENTATION_TRANSPOSE -> {
            matrix.postRotate(90f)
            matrix.postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_TRANSVERSE -> {
            matrix.postRotate(270f)
            matrix.postScale(-1f, 1f)
        }
        else -> return bitmap
    }
    val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    if (rotated !== bitmap) bitmap.recycle()
    return rotated
}

private fun normalizeImageToFrame(
    context: android.content.Context,
    uri: Uri,
    dest: File,
    targetW: Int,
    targetH: Int,
    coverCrop: Boolean
): Boolean = runCatching {
    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    if (opts.outWidth <= 0 || opts.outHeight <= 0) return@runCatching false

    var sample = 1
    while (opts.outWidth / sample > targetW * 2 && opts.outHeight / sample > targetH * 2) {
        sample *= 2
    }
    val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
    val rawSrc = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, decodeOpts) }
        ?: return@runCatching false
    val src = applyExifOrientation(rawSrc, readExifOrientation(context, uri))

    val canvasBmp = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(canvasBmp)
    canvas.drawColor(Color.BLACK)

    val scaleX = targetW.toFloat() / src.width.toFloat()
    val scaleY = targetH.toFloat() / src.height.toFloat()
    val scale = if (coverCrop) maxOf(scaleX, scaleY) else minOf(scaleX, scaleY)
    val drawW = (src.width * scale).roundToInt()
    val drawH = (src.height * scale).roundToInt()
    val left = (targetW - drawW) / 2
    val top = (targetH - drawH) / 2
    canvas.drawBitmap(src, null, Rect(left, top, left + drawW, top + drawH), null)
    src.recycle()

    FileOutputStream(dest).use { fos ->
        canvasBmp.compress(Bitmap.CompressFormat.JPEG, 92, fos)
    }
    canvasBmp.recycle()
    true
}.getOrDefault(false)
