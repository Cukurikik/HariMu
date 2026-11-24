package com.harimu.app.feature.tools

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material.icons.filled.PhotoSizeSelectLarge
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.VolumeOff
import com.harimu.app.core.media.Media3Request
import com.harimu.app.core.media.Media3TransformerEngine
import com.harimu.app.core.media.MediaJob
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
import java.util.Locale

fun videoForgeBatch1CoreTools(
    media3: Media3TransformerEngine = Media3TransformerEngine(),
    mediaJob: MediaJob = MediaJob()
): List<Tool> = listOf(
    // VID-01: Trim Video
    object : Tool {
        override val id = "VID-01"
        override val group = ToolGroup.VIDEO
        override val engineLabel = "M3/FF"
        override val icon = Icons.Filled.ContentCut
        override val inputSpec = InputSpec.SingleVideo
        override val params = listOf(
            ToolParam.SliderParam("startSec", "Mulai (detik)", "Start (sec)", 0f, 3600f, 0f, 0.5f, "s"),
            ToolParam.SliderParam("endSec", "Selesai (detik, 0=akhir)", "End (sec, 0=end)", 0f, 3600f, 10f, 0.5f, "s")
        )
        override fun title(isEnglish: Boolean) = if (isEnglish) "Trim Video" else "Potong Video"
        override fun description(isEnglish: Boolean) = if (isEnglish) {
            "Trim a chosen start-to-end range with ExoPlayer preview and Media3/FFmpeg."
        } else {
            "Potong rentang video pilihan dengan preview ExoPlayer dan Media3/FFmpeg."
        }

        override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
            val validation = VideoToolMath.validateVideoInputs(
                context = ctx.appContext,
                storage = ctx.storage,
                uris = input.uris,
                isEnglish = ctx.isEnglish
            )
            if (!validation.valid) {
                emit(ToolProgress.Failed(validation.errorMessage ?: "Invalid video"))
                return@flow
            }
            val probe = validation.probes.first()
            val startMs = (input.getFloat("startSec", 0f) * 1000f).toLong().coerceAtLeast(0L)
            val rawEndSec = input.getFloat("endSec", 10f)
            val endMs = if (rawEndSec <= 0f) {
                if (probe.durationMs > 0L) probe.durationMs else Long.MAX_VALUE
            } else {
                val requestedEnd = (rawEndSec * 1000f).toLong()
                if (probe.durationMs > 0L) requestedEnd.coerceAtMost(probe.durationMs) else requestedEnd
            }
            if (endMs <= startMs) {
                emit(
                    ToolProgress.Failed(
                        if (ctx.isEnglish) "End time must be greater than start time."
                        else "Waktu selesai harus lebih besar dari waktu mulai."
                    )
                )
                return@flow
            }

            val req = Media3Request(
                inputUri = input.uris.first(),
                toolTag = "VID_TRIM",
                clipStartMs = startMs,
                clipEndMs = endMs,
                trimOptimization = true
            )
            val clipDuration = if (endMs < Long.MAX_VALUE) (endMs - startMs) else probe.durationMs
            emitAll(
                media3.runWithFfmpegFallback(
                    context = ctx.appContext,
                    storage = ctx.storage,
                    request = req,
                    expectedDurationMs = clipDuration,
                    isEnglish = ctx.isEnglish
                ) { inputs, output ->
                    val startSecStr = String.format(Locale.US, "%.3f", startMs / 1000.0)
                    buildList {
                        add("-y")
                        add("-ss"); add(startSecStr)
                        add("-i"); add(inputs.first().absolutePath)
                        if (endMs < Long.MAX_VALUE) {
                            add("-t"); add(String.format(Locale.US, "%.3f", (endMs - startMs) / 1000.0))
                        }
                        add("-c"); add("copy")
                        add("-movflags"); add("+faststart")
                        add(output.absolutePath)
                    }
                }
            )
        }.flowOn(Dispatchers.IO)
    },

    // VID-02: Merge Video
    object : Tool {
        override val id = "VID-02"
        override val group = ToolGroup.VIDEO
        override val engineLabel = "M3/FF"
        override val icon = Icons.Filled.MergeType
        override val inputSpec = InputSpec.MultipleVideos
        override val params = emptyList<ToolParam>()
        override fun title(isEnglish: Boolean) = if (isEnglish) "Merge Videos" else "Gabung Video"
        override fun description(isEnglish: Boolean) = if (isEnglish) {
            "Concatenate multiple video clips sequentially via Media3 Composition or FFmpeg."
        } else {
            "Gabungkan beberapa klip video secara berurutan lewat Media3 Composition atau FFmpeg."
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
                emit(ToolProgress.Failed(validation.errorMessage ?: "Invalid videos"))
                return@flow
            }
            val firstProbe = validation.probes.first()
            val targetW = VideoToolMath.makeEven(firstProbe.width.takeIf { it > 0 } ?: 1280)
            val targetH = VideoToolMath.makeEven(firstProbe.height.takeIf { it > 0 } ?: 720)
            val totalDur = validation.probes.sumOf { it.durationMs }
            val allHaveAudio = validation.probes.all { it.hasAudio }

            val req = Media3Request(
                inputUri = input.uris.first(),
                additionalInputUris = input.uris.drop(1),
                toolTag = "VID_MERGE"
            )
            emitAll(
                media3.runWithFfmpegFallback(
                    context = ctx.appContext,
                    storage = ctx.storage,
                    request = req,
                    expectedDurationMs = totalDur,
                    isEnglish = ctx.isEnglish
                ) { inputs, output ->
                    buildList {
                        add("-y")
                        inputs.forEach { file ->
                            add("-i"); add(file.absolutePath)
                        }
                        val vChains = inputs.indices.joinToString("") { idx ->
                            "[$idx:v]scale=$targetW:$targetH:force_original_aspect_ratio=decrease," +
                                "pad=$targetW:$targetH:(ow-iw)/2:(oh-ih)/2,setsar=1,fps=30,format=yuv420p[v$idx];"
                        }
                        if (allHaveAudio) {
                            val pairs = inputs.indices.joinToString("") { idx -> "[v$idx][$idx:a]" }
                            add("-filter_complex")
                            add("${vChains}${pairs}concat=n=${inputs.size}:v=1:a=1[v][a]")
                            add("-map"); add("[v]")
                            add("-map"); add("[a]")
                            add("-c:a"); add("aac")
                        } else {
                            val vOnly = inputs.indices.joinToString("") { idx -> "[v$idx]" }
                            add("-filter_complex")
                            add("${vChains}${vOnly}concat=n=${inputs.size}:v=1:a=0[v]")
                            add("-map"); add("[v]")
                            add("-an")
                        }
                        add("-c:v"); add("mpeg4")
                        add("-q:v"); add("4")
                        add("-movflags"); add("+faststart")
                        add(output.absolutePath)
                    }
                }
            )
        }.flowOn(Dispatchers.IO)
    },

    // VID-03: Crop Video (1:1, 16:9, 9:16)
    object : Tool {
        override val id = "VID-03"
        override val group = ToolGroup.VIDEO
        override val engineLabel = "M3/FF"
        override val icon = Icons.Filled.Crop
        override val inputSpec = InputSpec.SingleVideo
        override val params = listOf(
            ToolParam.DropdownParam("ratio", "Rasio", "Aspect Ratio", listOf("1:1", "16:9", "9:16"), "1:1")
        )
        override fun title(isEnglish: Boolean) = if (isEnglish) "Crop Video" else "Crop Video"
        override fun description(isEnglish: Boolean) = if (isEnglish) {
            "Crop video frame cleanly to 1:1, 16:9, or 9:16 aspect ratio."
        } else {
            "Potong bingkai video ke rasio aspek 1:1, 16:9, atau 9:16."
        }

        override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
            val validation = VideoToolMath.validateVideoInputs(
                context = ctx.appContext,
                storage = ctx.storage,
                uris = input.uris,
                isEnglish = ctx.isEnglish
            )
            if (!validation.valid) {
                emit(ToolProgress.Failed(validation.errorMessage ?: "Invalid video"))
                return@flow
            }
            val probe = validation.probes.first()
            val ratio = input.getString("ratio", "1:1")
            val cropRect = VideoToolMath.calculateCropRect(
                srcWidth = probe.width.takeIf { it > 0 } ?: 1280,
                srcHeight = probe.height.takeIf { it > 0 } ?: 720,
                ratioLabel = ratio
            )

            val req = Media3Request(
                inputUri = input.uris.first(),
                toolTag = "VID_CROP",
                cropAspectRatio = ratio
            )
            emitAll(
                media3.runWithFfmpegFallback(
                    context = ctx.appContext,
                    storage = ctx.storage,
                    request = req,
                    expectedDurationMs = probe.durationMs,
                    isEnglish = ctx.isEnglish
                ) { inputs, output ->
                    buildList {
                        add("-y")
                        add("-i"); add(inputs.first().absolutePath)
                        add("-vf"); add(cropRect.toFfmpegFilter())
                        add("-c:v"); add("mpeg4")
                        add("-q:v"); add("4")
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

    // VID-04: Resize Video (1080/720/480/360)
    object : Tool {
        override val id = "VID-04"
        override val group = ToolGroup.VIDEO
        override val engineLabel = "M3/FF"
        override val icon = Icons.Filled.PhotoSizeSelectLarge
        override val inputSpec = InputSpec.SingleVideo
        override val params = listOf(
            ToolParam.DropdownParam("res", "Resolusi", "Resolution", listOf("1080p", "720p", "480p", "360p"), "720p")
        )
        override fun title(isEnglish: Boolean) = if (isEnglish) "Resize Video" else "Ubah Ukuran Video"
        override fun description(isEnglish: Boolean) = if (isEnglish) {
            "Resize video resolution to 1080p, 720p, 480p, or 360p preserving aspect ratio."
        } else {
            "Ubah resolusi video ke 1080p, 720p, 480p, atau 360p dengan rasio terjaga."
        }

        override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
            val validation = VideoToolMath.validateVideoInputs(
                context = ctx.appContext,
                storage = ctx.storage,
                uris = input.uris,
                isEnglish = ctx.isEnglish
            )
            if (!validation.valid) {
                emit(ToolProgress.Failed(validation.errorMessage ?: "Invalid video"))
                return@flow
            }
            val probe = validation.probes.first()
            val preset = input.getString("res", "720p")
            val dims = VideoToolMath.calculateTargetDimensions(
                srcWidth = probe.width.takeIf { it > 0 } ?: 1280,
                srcHeight = probe.height.takeIf { it > 0 } ?: 720,
                preset = preset
            )

            val req = Media3Request(
                inputUri = input.uris.first(),
                toolTag = "VID_RESIZE",
                targetWidth = dims.width,
                targetHeight = dims.height
            )
            emitAll(
                media3.runWithFfmpegFallback(
                    context = ctx.appContext,
                    storage = ctx.storage,
                    request = req,
                    expectedDurationMs = probe.durationMs,
                    isEnglish = ctx.isEnglish
                ) { inputs, output ->
                    buildList {
                        add("-y")
                        add("-i"); add(inputs.first().absolutePath)
                        add("-vf"); add("scale=${dims.width}:${dims.height}")
                        add("-c:v"); add("mpeg4")
                        add("-q:v"); add("4")
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

    // VID-05: Speed (video+audio)
    object : Tool {
        override val id = "VID-05"
        override val group = ToolGroup.VIDEO
        override val engineLabel = "M3/FF"
        override val icon = Icons.Filled.Speed
        override val inputSpec = InputSpec.SingleVideo
        override val params = listOf(
            ToolParam.SliderParam("speed", "Kecepatan", "Speed", 0.25f, 4.0f, 1.5f, 0.25f, "x"),
            ToolParam.ToggleParam("includeAudio", "Proses Trek Audio", "Process Audio Track", true)
        )
        override fun title(isEnglish: Boolean) = if (isEnglish) "Change Video Speed" else "Ubah Kecepatan Video"
        override fun description(isEnglish: Boolean) = if (isEnglish) {
            "Speed up or slow down video and audio synchronously (0.25x–4.0x)."
        } else {
            "Percepat atau perlambat video dan audio secara sinkron (0.25x–4.0x)."
        }

        override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
            val includeAudio = input.getBoolean("includeAudio", true)
            val validation = VideoToolMath.validateVideoInputs(
                context = ctx.appContext,
                storage = ctx.storage,
                uris = input.uris,
                requireAudio = includeAudio,
                isEnglish = ctx.isEnglish
            )
            if (!validation.valid) {
                emit(ToolProgress.Failed(validation.errorMessage ?: "Invalid video"))
                return@flow
            }
            val probe = validation.probes.first()
            val speed = input.getFloat("speed", 1.5f).coerceIn(0.25f, 4.0f)
            val expectedOutMs = if (probe.durationMs > 0L) (probe.durationMs / speed).toLong() else 0L
            val processAudio = includeAudio && probe.hasAudio

            val req = Media3Request(
                inputUri = input.uris.first(),
                toolTag = "VID_SPEED",
                removeAudio = !processAudio,
                speed = speed
            )
            emitAll(
                media3.runWithFfmpegFallback(
                    context = ctx.appContext,
                    storage = ctx.storage,
                    request = req,
                    expectedDurationMs = expectedOutMs,
                    isEnglish = ctx.isEnglish
                ) { inputs, output ->
                    val setPts = VideoToolMath.buildSetPtsFilter(speed)
                    buildList {
                        add("-y")
                        add("-i"); add(inputs.first().absolutePath)
                        if (processAudio) {
                            val atempo = VideoToolMath.buildAtempoFilterChain(speed)
                            add("-filter_complex")
                            add("[0:v]$setPts[v];[0:a]$atempo[a]")
                            add("-map"); add("[v]")
                            add("-map"); add("[a]")
                            add("-c:a"); add("aac")
                        } else {
                            add("-vf"); add(setPts)
                            add("-an")
                        }
                        add("-c:v"); add("mpeg4")
                        add("-q:v"); add("4")
                        add("-movflags"); add("+faststart")
                        add(output.absolutePath)
                    }
                }
            )
        }.flowOn(Dispatchers.IO)
    },

    // VID-09: Rotate Video
    object : Tool {
        override val id = "VID-09"
        override val group = ToolGroup.VIDEO
        override val engineLabel = "M3/FF"
        override val icon = Icons.Filled.RotateRight
        override val inputSpec = InputSpec.SingleVideo
        override val params = listOf(
            ToolParam.DropdownParam("deg", "Sudut (Searah Jarum Jam)", "Angle (Clockwise)", listOf("90", "180", "270"), "90")
        )
        override fun title(isEnglish: Boolean) = if (isEnglish) "Rotate Video" else "Putar Video"
        override fun description(isEnglish: Boolean) = if (isEnglish) {
            "Rotate video orientation 90°, 180°, or 270° clockwise."
        } else {
            "Putar orientasi video 90°, 180°, atau 270° searah jarum jam."
        }

        override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
            val validation = VideoToolMath.validateVideoInputs(
                context = ctx.appContext,
                storage = ctx.storage,
                uris = input.uris,
                isEnglish = ctx.isEnglish
            )
            if (!validation.valid) {
                emit(ToolProgress.Failed(validation.errorMessage ?: "Invalid video"))
                return@flow
            }
            val probe = validation.probes.first()
            val deg = input.getString("deg", "90").toIntOrNull() ?: 90
            val media3Deg = VideoToolMath.mapRotationToMedia3Degrees(deg)
            val ffmpegFilter = VideoToolMath.buildFfmpegRotateFilter(deg)

            val req = Media3Request(
                inputUri = input.uris.first(),
                toolTag = "VID_ROTATE",
                rotationDegrees = media3Deg
            )
            emitAll(
                media3.runWithFfmpegFallback(
                    context = ctx.appContext,
                    storage = ctx.storage,
                    request = req,
                    expectedDurationMs = probe.durationMs,
                    isEnglish = ctx.isEnglish
                ) { inputs, output ->
                    buildList {
                        add("-y")
                        add("-i"); add(inputs.first().absolutePath)
                        add("-vf"); add(ffmpegFilter)
                        add("-c:v"); add("mpeg4")
                        add("-q:v"); add("4")
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

    // VID-10: Flip Video
    object : Tool {
        override val id = "VID-10"
        override val group = ToolGroup.VIDEO
        override val engineLabel = "M3/FF"
        override val icon = Icons.Filled.Flip
        override val inputSpec = InputSpec.SingleVideo
        override val params = listOf(
            ToolParam.DropdownParam("dir", "Arah Cermin", "Flip Direction", listOf("Horizontal", "Vertical"), "Horizontal")
        )
        override fun title(isEnglish: Boolean) = if (isEnglish) "Flip Video" else "Balik Video (Flip)"
        override fun description(isEnglish: Boolean) = if (isEnglish) {
            "Mirror video horizontally or vertically."
        } else {
            "Cerminkan video secara horizontal atau vertikal."
        }

        override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
            val validation = VideoToolMath.validateVideoInputs(
                context = ctx.appContext,
                storage = ctx.storage,
                uris = input.uris,
                isEnglish = ctx.isEnglish
            )
            if (!validation.valid) {
                emit(ToolProgress.Failed(validation.errorMessage ?: "Invalid video"))
                return@flow
            }
            val probe = validation.probes.first()
            val dir = input.getString("dir", "Horizontal")
            val isHorizontal = dir.equals("Horizontal", ignoreCase = true)

            val req = Media3Request(
                inputUri = input.uris.first(),
                toolTag = "VID_FLIP",
                flipHorizontal = isHorizontal,
                flipVertical = !isHorizontal
            )
            emitAll(
                media3.runWithFfmpegFallback(
                    context = ctx.appContext,
                    storage = ctx.storage,
                    request = req,
                    expectedDurationMs = probe.durationMs,
                    isEnglish = ctx.isEnglish
                ) { inputs, output ->
                    buildList {
                        add("-y")
                        add("-i"); add(inputs.first().absolutePath)
                        add("-vf"); add(if (isHorizontal) "hflip" else "vflip")
                        add("-c:v"); add("mpeg4")
                        add("-q:v"); add("4")
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

    // VID-20: Video Compressor
    object : Tool {
        override val id = "VID-20"
        override val group = ToolGroup.VIDEO
        override val engineLabel = "M3/FF"
        override val icon = Icons.Filled.Compress
        override val inputSpec = InputSpec.SingleVideo
        override val params = listOf(
            ToolParam.SliderParam("crf", "Tingkat Kompresi", "Compression Level", 18f, 35f, 24f, 1f, ""),
            ToolParam.DropdownParam("scale", "Skala Resolusi", "Resolution Scale", listOf("Asli", "720p", "480p", "360p"), "Asli")
        )
        override fun title(isEnglish: Boolean) = if (isEnglish) "Video Compressor" else "Kompresor Video"
        override fun description(isEnglish: Boolean) = if (isEnglish) {
            "Reduce video file size with target bitrate compression and optional downscaling."
        } else {
            "Perkecil ukuran file video dengan kompresi bitrate dan opsi pengecilan resolusi."
        }

        override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
            val validation = VideoToolMath.validateVideoInputs(
                context = ctx.appContext,
                storage = ctx.storage,
                uris = input.uris,
                isEnglish = ctx.isEnglish
            )
            if (!validation.valid) {
                emit(ToolProgress.Failed(validation.errorMessage ?: "Invalid video"))
                return@flow
            }
            val probe = validation.probes.first()
            val crf = input.getFloat("crf", 24f).toInt().coerceIn(18, 35)
            val scaleChoice = input.getString("scale", "Asli")
            val srcW = probe.width.takeIf { it > 0 } ?: 1280
            val srcH = probe.height.takeIf { it > 0 } ?: 720
            val targetDims = if (scaleChoice != "Asli" && scaleChoice != "Original") {
                VideoToolMath.calculateTargetDimensions(srcW, srcH, scaleChoice)
            } else {
                null
            }
            val targetBitrateBps = VideoToolMath.calculateTargetVideoBitrateBps(
                sourceBitrateBps = probe.bitrateBps,
                crf = crf,
                width = targetDims?.width ?: srcW,
                height = targetDims?.height ?: srcH
            ).toInt()

            val req = Media3Request(
                inputUri = input.uris.first(),
                toolTag = "VID_COMPRESS",
                targetWidth = targetDims?.width,
                targetHeight = targetDims?.height,
                targetVideoBitrateBps = targetBitrateBps
            )
            emitAll(
                media3.runWithFfmpegFallback(
                    context = ctx.appContext,
                    storage = ctx.storage,
                    request = req,
                    expectedDurationMs = probe.durationMs,
                    isEnglish = ctx.isEnglish
                ) { inputs, output ->
                    val kbps = (targetBitrateBps / 1000).coerceAtLeast(200)
                    buildList {
                        add("-y")
                        add("-i"); add(inputs.first().absolutePath)
                        if (targetDims != null) {
                            add("-vf"); add("scale=${targetDims.width}:${targetDims.height}")
                        }
                        add("-c:v"); add("mpeg4")
                        add("-b:v"); add("${kbps}k")
                        if (probe.hasAudio) {
                            add("-c:a"); add("aac")
                            add("-b:a"); add("128k")
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

    // VID-24: Remove Audio
    object : Tool {
        override val id = "VID-24"
        override val group = ToolGroup.VIDEO
        override val engineLabel = "M3/FF"
        override val icon = Icons.Filled.VolumeOff
        override val inputSpec = InputSpec.SingleVideo
        override val params = emptyList<ToolParam>()
        override fun title(isEnglish: Boolean) = if (isEnglish) "Remove Video Audio" else "Bisukan Video"
        override fun description(isEnglish: Boolean) = if (isEnglish) {
            "Strip the audio track cleanly while keeping video quality intact."
        } else {
            "Hapus trek suara dari video tanpa mengurangi kualitas gambar."
        }

        override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
            val validation = VideoToolMath.validateVideoInputs(
                context = ctx.appContext,
                storage = ctx.storage,
                uris = input.uris,
                requireAudio = true,
                isEnglish = ctx.isEnglish
            )
            if (!validation.valid) {
                emit(ToolProgress.Failed(validation.errorMessage ?: "Invalid video"))
                return@flow
            }
            val probe = validation.probes.first()

            val req = Media3Request(
                inputUri = input.uris.first(),
                toolTag = "VID_MUTE",
                removeAudio = true
            )
            emitAll(
                media3.runWithFfmpegFallback(
                    context = ctx.appContext,
                    storage = ctx.storage,
                    request = req,
                    expectedDurationMs = probe.durationMs,
                    isEnglish = ctx.isEnglish
                ) { inputs, output ->
                    listOf(
                        "-y",
                        "-i", inputs.first().absolutePath,
                        "-c:v", "copy",
                        "-an",
                        "-movflags", "+faststart",
                        output.absolutePath
                    )
                }
            )
        }.flowOn(Dispatchers.IO)
    }
)
