package com.harimu.app.feature.tools

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Build
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.BurstMode
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Gif
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Transform
import com.harimu.app.core.media.MediaJob
import com.harimu.app.core.media.MediaProbe
import com.harimu.app.core.media.MediaUriUtils
import com.harimu.app.core.media.VideoToolMath
import com.harimu.app.core.toolapi.InputSpec
import com.harimu.app.core.toolapi.Tool
import com.harimu.app.core.toolapi.ToolContext
import com.harimu.app.core.toolapi.ToolGroup
import com.harimu.app.core.toolapi.ToolInput
import com.harimu.app.core.toolapi.ToolOutputFile
import com.harimu.app.core.toolapi.ToolParam
import com.harimu.app.core.toolapi.ToolProgress
import com.harimu.app.core.toolapi.ToolResult
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
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

fun videoForgeBatch1ExtraTools(
    mediaJob: MediaJob = MediaJob()
): List<Tool> = listOf(
    // VID-06: Video to GIF (2-pass palettegen + paletteuse)
    object : Tool {
        override val id = "VID-06"
        override val group = ToolGroup.VIDEO
        override val engineLabel = "FF"
        override val icon = Icons.Filled.Gif
        override val inputSpec = InputSpec.SingleVideo
        override val params = listOf(
            ToolParam.SliderParam("startSec", "Mulai (detik)", "Start (sec)", 0f, 3600f, 0f, 0.5f, "s"),
            ToolParam.SliderParam("maxSec", "Durasi GIF (detik)", "GIF Duration (sec)", 2f, 30f, 8f, 1f, "s"),
            ToolParam.SliderParam("fps", "FPS GIF", "GIF FPS", 5f, 24f, 12f, 1f, "fps"),
            ToolParam.SliderParam("width", "Lebar (px)", "Width (px)", 240f, 720f, 480f, 40f, "px")
        )
        override fun title(isEnglish: Boolean) = if (isEnglish) "Video to GIF" else "Video ke GIF"
        override fun description(isEnglish: Boolean) = if (isEnglish) {
            "Convert a video clip into a palette-optimized animated GIF (2-pass FFmpeg)."
        } else {
            "Konversi klip video menjadi GIF animasi dengan palet optimal (FFmpeg 2-pass)."
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
            val startSec = input.getFloat("startSec", 0f).coerceAtLeast(0f)
            val maxSec = input.getFloat("maxSec", 8f).coerceIn(2f, 30f)
            val fps = input.getFloat("fps", 12f).toInt().coerceIn(5, 24)
            val width = VideoToolMath.makeEven(input.getFloat("width", 480f).toInt().coerceIn(240, 720))

            val jobDir = ctx.storage.createJobTempDir("gif_${UUID.randomUUID().toString().take(6)}")
            val outGif = ctx.storage.createOutputFile("VID_GIF", "gif")
            val paletteFile = File(jobDir, "palette.png")

            try {
                emit(ToolProgress.Running(5, if (ctx.isEnglish) "Preparing video…" else "Menyiapkan video…"))
                val localIn = MediaUriUtils.copyUriToSafeTemp(ctx.appContext, ctx.storage, input.uris.first(), jobDir, 0, "mp4")
                val startArg = String.format(Locale.US, "%.2f", startSec)
                val durArg = String.format(Locale.US, "%.2f", maxSec)
                val vfBase = "fps=$fps,scale=$width:-1:flags=lanczos"

                emit(ToolProgress.Running(20, if (ctx.isEnglish) "Generating GIF palette (pass 1/2)…" else "Membuat palet warna GIF (tahap 1/2)…"))
                val pass1 = mediaJob.runStep(
                    arguments = listOf(
                        "-y", "-ss", startArg, "-t", durArg,
                        "-i", localIn.absolutePath,
                        "-vf", "$vfBase,palettegen=stats_mode=diff",
                        paletteFile.absolutePath
                    ),
                    expectedDurationMs = (maxSec * 1000f).toLong(),
                    isEnglish = ctx.isEnglish
                )
                pass1.getOrElse { err ->
                    MediaUriUtils.cleanupQuietly(outGif)
                    emit(ToolProgress.Failed(err.message ?: "Palette generation failed"))
                    return@flow
                }

                emit(ToolProgress.Running(55, if (ctx.isEnglish) "Encoding animated GIF (pass 2/2)…" else "Menyusun animasi GIF (tahap 2/2)…"))
                val pass2 = mediaJob.runStep(
                    arguments = listOf(
                        "-y", "-ss", startArg, "-t", durArg,
                        "-i", localIn.absolutePath,
                        "-i", paletteFile.absolutePath,
                        "-lavfi", "$vfBase [x]; [x][1:v] paletteuse=dither=bayer",
                        outGif.absolutePath
                    ),
                    expectedDurationMs = (maxSec * 1000f).toLong(),
                    isEnglish = ctx.isEnglish
                )
                pass2.getOrElse { err ->
                    MediaUriUtils.cleanupQuietly(outGif)
                    emit(ToolProgress.Failed(err.message ?: "GIF encoding failed"))
                    return@flow
                }

                if (!outGif.exists() || outGif.length() <= 0L) {
                    MediaUriUtils.cleanupQuietly(outGif)
                    emit(ToolProgress.Failed(if (ctx.isEnglish) "GIF output is empty." else "Output GIF kosong."))
                    return@flow
                }

                val sizeText = ctx.storage.formatBytes(outGif.length())
                emit(
                    ToolProgress.Done(
                        ToolResult(
                            outputs = listOf(ToolOutputFile(outGif, "image/gif", outGif.name)),
                            summary = if (ctx.isEnglish) "GIF created: ${outGif.name} ($sizeText)" else "GIF selesai: ${outGif.name} ($sizeText)"
                        )
                    )
                )
            } catch (ce: CancellationException) {
                MediaUriUtils.cleanupQuietly(outGif)
                throw ce
            } finally {
                MediaUriUtils.cleanupQuietly(jobDir)
            }
        }.flowOn(Dispatchers.IO)
    },

    // VID-08: Video to Audio (MP3)
    object : Tool {
        override val id = "VID-08"
        override val group = ToolGroup.VIDEO
        override val engineLabel = "FF"
        override val icon = Icons.Filled.MusicNote
        override val inputSpec = InputSpec.SingleVideo
        override val params = listOf(
            ToolParam.DropdownParam("bitrate", "Bitrate MP3", "MP3 Bitrate", listOf("128k", "192k", "256k", "320k"), "192k")
        )
        override fun title(isEnglish: Boolean) = if (isEnglish) "Video to Audio (MP3)" else "Video ke Audio (MP3)"
        override fun description(isEnglish: Boolean) = if (isEnglish) {
            "Extract the audio track from a video file and encode it to MP3."
        } else {
            "Ekstrak trek audio dari video dan simpan sebagai file MP3."
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
            val bitrate = input.getString("bitrate", "192k").ifBlank { "192k" }

            emitAll(
                mediaJob.runCommand(
                    context = ctx.appContext,
                    storage = ctx.storage,
                    inputUris = input.uris,
                    toolTag = "VID_MP3",
                    outputExt = "mp3",
                    outputMime = "audio/mpeg",
                    expectedDurationMs = probe.durationMs,
                    isEnglish = ctx.isEnglish
                ) { inputs, output ->
                    listOf(
                        "-y",
                        "-i", inputs.first().absolutePath,
                        "-vn",
                        "-c:a", "libmp3lame",
                        "-b:a", bitrate,
                        "-ar", "44100",
                        output.absolutePath
                    )
                }
            )
        }.flowOn(Dispatchers.IO)
    },

    // VID-12: Loop Video
    object : Tool {
        override val id = "VID-12"
        override val group = ToolGroup.VIDEO
        override val engineLabel = "FF"
        override val icon = Icons.Filled.Loop
        override val inputSpec = InputSpec.SingleVideo
        override val params = listOf(
            ToolParam.SliderParam("loops", "Jumlah Pengulangan", "Loop Count", 2f, 10f, 3f, 1f, "x")
        )
        override fun title(isEnglish: Boolean) = if (isEnglish) "Loop Video" else "Loop Video"
        override fun description(isEnglish: Boolean) = if (isEnglish) {
            "Repeat a video clip cleanly N times (with long-video duration safeguards)."
        } else {
            "Ulangi klip video beberapa kali (dilengkapi pengaman batas durasi)."
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
            val loops = input.getFloat("loops", 3f).toInt().coerceIn(2, 10)
            val safety = VideoToolMath.assessLoopSafety(probe.durationMs, loops, ctx.isEnglish)
            if (!safety.allowed) {
                emit(ToolProgress.Failed(safety.blockingError ?: "Loop limit exceeded"))
                return@flow
            }
            safety.warningMessage?.let { warn ->
                emit(ToolProgress.Running(2, warn))
            }

            val expectedDur = probe.durationMs * loops.toLong()
            emitAll(
                mediaJob.runCommand(
                    context = ctx.appContext,
                    storage = ctx.storage,
                    inputUris = input.uris,
                    toolTag = "VID_LOOP",
                    outputExt = "mp4",
                    outputMime = "video/mp4",
                    expectedDurationMs = expectedDur,
                    isEnglish = ctx.isEnglish
                ) { inputs, output ->
                    listOf(
                        "-y",
                        "-stream_loop", (loops - 1).toString(),
                        "-i", inputs.first().absolutePath,
                        "-c", "copy",
                        "-movflags", "+faststart",
                        output.absolutePath
                    )
                }
            )
        }.flowOn(Dispatchers.IO)
    },

    // VID-13: Change Video Volume
    object : Tool {
        override val id = "VID-13"
        override val group = ToolGroup.VIDEO
        override val engineLabel = "FF"
        override val icon = Icons.AutoMirrored.Filled.VolumeUp
        override val inputSpec = InputSpec.SingleVideo
        override val params = listOf(
            ToolParam.SliderParam("vol", "Volume", "Volume", 0f, 200f, 120f, 10f, "%")
        )
        override fun title(isEnglish: Boolean) = if (isEnglish) "Change Video Volume" else "Ubah Volume Video"
        override fun description(isEnglish: Boolean) = if (isEnglish) {
            "Adjust audio track gain (0%–200%) while copying the original video stream."
        } else {
            "Atur tingkat volume audio video (0%–200%) tanpa re-encode gambar."
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
            val volPct = input.getFloat("vol", 120f).coerceIn(0f, 200f)
            val volFilter = VideoToolMath.buildVolumeFilter(volPct)

            emitAll(
                mediaJob.runCommand(
                    context = ctx.appContext,
                    storage = ctx.storage,
                    inputUris = input.uris,
                    toolTag = "VID_VOL",
                    outputExt = "mp4",
                    outputMime = "video/mp4",
                    expectedDurationMs = probe.durationMs,
                    isEnglish = ctx.isEnglish
                ) { inputs, output ->
                    listOf(
                        "-y",
                        "-i", inputs.first().absolutePath,
                        "-c:v", "copy",
                        "-af", volFilter,
                        "-c:a", "aac",
                        "-b:a", "192k",
                        "-movflags", "+faststart",
                        output.absolutePath
                    )
                }
            )
        }.flowOn(Dispatchers.IO)
    },

    // VID-18: Video Converter (MP4/WebM/AVI/MKV)
    object : Tool {
        override val id = "VID-18"
        override val group = ToolGroup.VIDEO
        override val engineLabel = "FF"
        override val icon = Icons.Filled.Transform
        override val inputSpec = InputSpec.SingleVideo
        override val params = listOf(
            ToolParam.DropdownParam("fmt", "Format Tujuan", "Target Format", listOf("MP4", "WebM", "AVI", "MKV"), "MP4")
        )
        override fun title(isEnglish: Boolean) = if (isEnglish) "Video Converter" else "Konverter Video"
        override fun description(isEnglish: Boolean) = if (isEnglish) {
            "Convert videos cleanly across MP4, WebM, AVI, and MKV containers."
        } else {
            "Konversi video antar kontainer MP4, WebM, AVI, dan MKV."
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
            val fmt = input.getString("fmt", "MP4").uppercase(Locale.US)
            val (ext, mime) = when (fmt) {
                "WEBM" -> "webm" to "video/webm"
                "AVI" -> "avi" to "video/x-msvideo"
                "MKV" -> "mkv" to "video/x-matroska"
                else -> "mp4" to "video/mp4"
            }

            emitAll(
                mediaJob.runCommand(
                    context = ctx.appContext,
                    storage = ctx.storage,
                    inputUris = input.uris,
                    toolTag = "VID_CONV_$fmt",
                    outputExt = ext,
                    outputMime = mime,
                    expectedDurationMs = probe.durationMs,
                    isEnglish = ctx.isEnglish
                ) { inputs, output ->
                    buildList {
                        add("-y")
                        add("-i"); add(inputs.first().absolutePath)
                        add("-vf"); add("scale=trunc(iw/2)*2:trunc(ih/2)*2")
                        when (fmt) {
                            "WEBM" -> {
                                add("-c:v"); add("libvpx")
                                add("-b:v"); add("1500k")
                                add("-deadline"); add("realtime")
                                add("-cpu-used"); add("4")
                                if (probe.hasAudio) {
                                    add("-c:a"); add("libvorbis")
                                    add("-b:a"); add("128k")
                                } else add("-an")
                            }
                            "AVI" -> {
                                add("-c:v"); add("mpeg4")
                                add("-q:v"); add("4")
                                if (probe.hasAudio) {
                                    add("-c:a"); add("libmp3lame")
                                    add("-b:a"); add("192k")
                                } else add("-an")
                            }
                            else -> {
                                add("-c:v"); add("mpeg4")
                                add("-q:v"); add("4")
                                if (probe.hasAudio) {
                                    add("-c:a"); add("aac")
                                    add("-b:a"); add("160k")
                                } else add("-an")
                                if (ext == "mp4") {
                                    add("-movflags"); add("+faststart")
                                }
                            }
                        }
                        add(output.absolutePath)
                    }
                }
            )
        }.flowOn(Dispatchers.IO)
    },

    // VID-19: Reverse Video (with memory safety check & 12s segmented reverse for >20s)
    object : Tool {
        override val id = "VID-19"
        override val group = ToolGroup.VIDEO
        override val engineLabel = "FF"
        override val icon = Icons.Filled.FastRewind
        override val inputSpec = InputSpec.SingleVideo
        override val params = listOf(
            ToolParam.ToggleParam("includeAudio", "Balikkan Audio Juga", "Reverse Audio Too", true)
        )
        override fun title(isEnglish: Boolean) = if (isEnglish) "Reverse Video" else "Putar Balik Video"
        override fun description(isEnglish: Boolean) = if (isEnglish) {
            "Play video and audio backwards using memory-safe segmented chunks for long clips."
        } else {
            "Balik arah video dan audio dengan mode segmentasi aman memori untuk klip panjang."
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
            val safety = VideoToolMath.assessReverseSafety(probe.durationMs, ctx.isEnglish)
            if (!safety.allowed) {
                emit(ToolProgress.Failed(safety.blockingError ?: "Video too long for reverse"))
                return@flow
            }

            val reverseAudio = input.getBoolean("includeAudio", true) && probe.hasAudio
            if (!safety.useSegmentedMode) {
                emitAll(
                    mediaJob.runCommand(
                        context = ctx.appContext,
                        storage = ctx.storage,
                        inputUris = input.uris,
                        toolTag = "VID_REV",
                        outputExt = "mp4",
                        outputMime = "video/mp4",
                        expectedDurationMs = probe.durationMs,
                        isEnglish = ctx.isEnglish
                    ) { inputs, output ->
                        buildList {
                            add("-y")
                            add("-i"); add(inputs.first().absolutePath)
                            add("-vf"); add("reverse")
                            if (reverseAudio) {
                                add("-af"); add("areverse")
                                add("-c:a"); add("aac")
                            } else {
                                add("-an")
                            }
                            add("-c:v"); add("mpeg4")
                            add("-q:v"); add("4")
                            add("-movflags"); add("+faststart")
                            add(output.absolutePath)
                        }
                    }
                )
                return@flow
            }

            // Segmented reverse (> 20s and <= 180s) to bound RAM cleanly per 12s chunk
            val jobDir = ctx.storage.createJobTempDir("rev_${UUID.randomUUID().toString().take(6)}")
            val outMp4 = ctx.storage.createOutputFile("VID_REV", "mp4")
            try {
                emit(ToolProgress.Running(4, safety.warningMessage ?: "Segmented reverse…"))
                val localIn = MediaUriUtils.copyUriToSafeTemp(ctx.appContext, ctx.storage, input.uris.first(), jobDir, 0, "mp4")
                val chunks = VideoToolMath.computeReverseChunkRanges(probe.durationMs, chunkDurationMs = 12_000L)
                val reversedChunkFiles = mutableListOf<File>()

                for ((index, range) in chunks.withIndex()) {
                    currentCoroutineContext().ensureActive()
                    val (startMs, endMs) = range
                    val chunkDurSec = ((endMs - startMs).coerceAtLeast(200L)) / 1000.0
                    val pct = 10 + ((index * 75) / chunks.size.coerceAtLeast(1))
                    emit(
                        ToolProgress.Running(
                            pct,
                            if (ctx.isEnglish) "Reversing segment ${index + 1}/${chunks.size}…"
                            else "Membalik segmen ${index + 1}/${chunks.size}…"
                        )
                    )
                    val revChunk = File(jobDir, "rev_$index.mp4")
                    val args = buildList {
                        add("-y")
                        add("-ss"); add(String.format(Locale.US, "%.3f", startMs / 1000.0))
                        add("-t"); add(String.format(Locale.US, "%.3f", chunkDurSec))
                        add("-i"); add(localIn.absolutePath)
                        add("-vf"); add("reverse")
                        if (reverseAudio) {
                            add("-af"); add("areverse")
                            add("-c:a"); add("aac")
                        } else {
                            add("-an")
                        }
                        add("-c:v"); add("mpeg4")
                        add("-q:v"); add("4")
                        add(revChunk.absolutePath)
                    }
                    mediaJob.runStep(args, endMs - startMs, ctx.isEnglish).getOrElse { err ->
                        MediaUriUtils.cleanupQuietly(outMp4)
                        emit(ToolProgress.Failed(err.message ?: "Reverse segment failed"))
                        return@flow
                    }
                    reversedChunkFiles.add(revChunk)
                }

                emit(ToolProgress.Running(90, if (ctx.isEnglish) "Merging reversed segments…" else "Menggabungkan segmen terbalik…"))
                val concatList = File(jobDir, "concat.txt")
                concatList.writeText(
                    reversedChunkFiles.reversed().joinToString("\n") { chunk ->
                        "file '${chunk.absolutePath.replace("'", "'\\''")}'"
                    }
                )
                mediaJob.runStep(
                    arguments = listOf(
                        "-y",
                        "-f", "concat",
                        "-safe", "0",
                        "-i", concatList.absolutePath,
                        "-c", "copy",
                        "-movflags", "+faststart",
                        outMp4.absolutePath
                    ),
                    expectedDurationMs = probe.durationMs,
                    isEnglish = ctx.isEnglish
                ).getOrElse { err ->
                    MediaUriUtils.cleanupQuietly(outMp4)
                    emit(ToolProgress.Failed(err.message ?: "Reverse concat failed"))
                    return@flow
                }

                val outProbe = MediaProbe.probeFile(outMp4)
                val sizeText = ctx.storage.formatBytes(outMp4.length())
                emit(
                    ToolProgress.Done(
                        ToolResult(
                            outputs = listOf(ToolOutputFile(outMp4, "video/mp4", outMp4.name)),
                            textOutput = outProbe.formatSummary(ctx.isEnglish),
                            summary = if (ctx.isEnglish) {
                                "Segmented reverse done (${chunks.size} parts): ${outMp4.name} ($sizeText)"
                            } else {
                                "Reverse tersegmentasi selesai (${chunks.size} bagian): ${outMp4.name} ($sizeText)"
                            }
                        )
                    )
                )
            } catch (ce: CancellationException) {
                MediaUriUtils.cleanupQuietly(outMp4)
                throw ce
            } finally {
                MediaUriUtils.cleanupQuietly(jobDir)
            }
        }.flowOn(Dispatchers.IO)
    },

    // VID-21: Video Splitter (.zip)
    object : Tool {
        override val id = "VID-21"
        override val group = ToolGroup.VIDEO
        override val engineLabel = "M3/FF"
        override val icon = Icons.AutoMirrored.Filled.CallSplit
        override val inputSpec = InputSpec.SingleVideo
        override val params = listOf(
            ToolParam.DropdownParam("mode", "Mode Pemecahan", "Split Mode", listOf("Bagian Sama", "Rentang Pilihan"), "Bagian Sama"),
            ToolParam.SliderParam("segSec", "Durasi per Bagian (detik)", "Segment Duration (sec)", 3f, 60f, 10f, 1f, "s"),
            ToolParam.SliderParam("startSec", "Mulai Rentang (detik)", "Range Start (sec)", 0f, 3600f, 0f, 0.5f, "s"),
            ToolParam.SliderParam("endSec", "Selesai Rentang (0=akhir)", "Range End (0=end)", 0f, 3600f, 0f, 0.5f, "s")
        )
        override fun title(isEnglish: Boolean) = if (isEnglish) "Video Splitter" else "Pecah Video (.zip)"
        override fun description(isEnglish: Boolean) = if (isEnglish) {
            "Split video into equal MP4 parts or a chosen timeline range packed into ZIP."
        } else {
            "Pecah video menjadi bagian MP4 merata atau rentang pilihan ke dalam ZIP."
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
            val totalMs = probe.durationMs.coerceAtLeast(1000L)
            val mode = input.getString("mode", "Bagian Sama")
            val isRangeMode = mode == "Rentang Pilihan" || mode == "Selected Range"
            val segMs = (input.getFloat("segSec", 10f).coerceAtLeast(3f) * 1000f).toLong()
            val startMs = (input.getFloat("startSec", 0f) * 1000f).toLong().coerceAtLeast(0L)
            val rawEnd = input.getFloat("endSec", 0f)
            val endMs = if (rawEnd <= 0f) Long.MAX_VALUE else (rawEnd * 1000f).toLong()

            val ranges = if (isRangeMode) {
                val safeEnd = if (endMs == Long.MAX_VALUE) totalMs else endMs.coerceAtMost(totalMs)
                if (safeEnd <= startMs) {
                    emit(
                        ToolProgress.Failed(
                            if (ctx.isEnglish) "Range end must exceed start."
                            else "Akhir rentang harus lebih besar dari awal."
                        )
                    )
                    return@flow
                }
                VideoToolMath.computeSplitterRanges(totalMs, segMs, startMs, safeEnd)
            } else {
                VideoToolMath.computeSplitterRanges(totalMs, segMs)
            }

            val jobDir = ctx.storage.createJobTempDir("split_${UUID.randomUUID().toString().take(6)}")
            val outZip = ctx.storage.createOutputFile("VID_SPLIT", "zip")

            try {
                emit(ToolProgress.Running(5, if (ctx.isEnglish) "Preparing video segments…" else "Menyiapkan pemecahan segmen…"))
                val localIn = MediaUriUtils.copyUriToSafeTemp(ctx.appContext, ctx.storage, input.uris.first(), jobDir, 0, "mp4")

                ZipOutputStream(FileOutputStream(outZip)).use { zos ->
                    for ((idx, range) in ranges.withIndex()) {
                        currentCoroutineContext().ensureActive()
                        val (sMs, eMs) = range
                        val partFile = File(jobDir, "part_${idx + 1}.mp4")
                        val pct = 10 + ((idx * 85) / ranges.size.coerceAtLeast(1))
                        emit(
                            ToolProgress.Running(
                                pct,
                                if (ctx.isEnglish) "Splitting part ${idx + 1}/${ranges.size}…"
                                else "Memecah bagian ${idx + 1}/${ranges.size}…"
                            )
                        )
                        val stepRes = mediaJob.runStep(
                            arguments = listOf(
                                "-y",
                                "-ss", String.format(Locale.US, "%.3f", sMs / 1000.0),
                                "-t", String.format(Locale.US, "%.3f", ((eMs - sMs).coerceAtLeast(200L)) / 1000.0),
                                "-i", localIn.absolutePath,
                                "-c", "copy",
                                "-movflags", "+faststart",
                                partFile.absolutePath
                            ),
                            expectedDurationMs = eMs - sMs,
                            isEnglish = ctx.isEnglish
                        )
                        stepRes.getOrElse { err ->
                            MediaUriUtils.cleanupQuietly(outZip)
                            emit(ToolProgress.Failed(err.message ?: "Split part failed"))
                            return@flow
                        }
                        zos.putNextEntry(ZipEntry("part_${idx + 1}.mp4"))
                        partFile.inputStream().use { inp -> inp.copyTo(zos, 32 * 1024) }
                        zos.closeEntry()
                        MediaUriUtils.cleanupQuietly(partFile)
                    }
                }

                val sizeText = ctx.storage.formatBytes(outZip.length())
                emit(
                    ToolProgress.Done(
                        ToolResult(
                            outputs = listOf(ToolOutputFile(outZip, "application/zip", outZip.name)),
                            summary = if (ctx.isEnglish) {
                                "${ranges.size} MP4 parts packed into ZIP ($sizeText)"
                            } else {
                                "${ranges.size} bagian MP4 dikemas ke ZIP ($sizeText)"
                            }
                        )
                    )
                )
            } catch (ce: CancellationException) {
                MediaUriUtils.cleanupQuietly(outZip)
                throw ce
            } finally {
                MediaUriUtils.cleanupQuietly(jobDir)
            }
        }.flowOn(Dispatchers.IO)
    },

    // VID-22: Extract Frames (Memory-safe downscaled streaming to ZIP)
    object : Tool {
        override val id = "VID-22"
        override val group = ToolGroup.VIDEO
        override val engineLabel = "NAT"
        override val icon = Icons.Filled.BurstMode
        override val inputSpec = InputSpec.SingleVideo
        override val params = listOf(
            ToolParam.SliderParam("intervalSec", "Interval Frame (detik)", "Frame Interval (sec)", 1f, 10f, 2f, 1f, "s"),
            ToolParam.SliderParam("maxFrames", "Maksimal Frame", "Max Frames", 5f, 60f, 12f, 1f, "")
        )
        override fun title(isEnglish: Boolean) = if (isEnglish) "Extract Video Frames" else "Ekstrak Frame Video"
        override fun description(isEnglish: Boolean) = if (isEnglish) {
            "Capture PNG frames at fixed intervals safely without OOM into a ZIP."
        } else {
            "Ekstrak frame PNG dari video per interval detik secara aman memori ke ZIP."
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
            val intervalSec = input.getFloat("intervalSec", 2f).toInt().coerceIn(1, 10)
            val maxFrames = input.getFloat("maxFrames", 12f).toInt().coerceIn(5, 60)
            val destZip = ctx.storage.createOutputFile("VID_FRAMES", "zip")
            val mmr = MediaMetadataRetriever()

            try {
                mmr.setDataSource(ctx.appContext, input.uris.first())
                val durationMs = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                    ?: validation.probes.first().durationMs.coerceAtLeast(1000L)
                val stepMs = intervalSec * 1000L
                val totalTarget = minOf(maxFrames, ((durationMs / stepMs) + 1L).toInt().coerceAtLeast(1))
                var count = 0
                var tMs = 0L

                ZipOutputStream(FileOutputStream(destZip)).use { zos ->
                    while (tMs <= durationMs && count < maxFrames) {
                        currentCoroutineContext().ensureActive()
                        val frame: Bitmap? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                            mmr.getScaledFrameAtTime(
                                tMs * 1000L,
                                MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                                1280,
                                1280
                            )
                        } else {
                            mmr.getFrameAtTime(tMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                        }
                        if (frame != null) {
                            count++
                            val pct = (count * 95) / totalTarget.coerceAtLeast(1)
                            emit(
                                ToolProgress.Running(
                                    pct.coerceIn(5, 95),
                                    if (ctx.isEnglish) "Extracting frame $count/$totalTarget…" else "Mengambil frame $count/$totalTarget…"
                                )
                            )
                            zos.putNextEntry(ZipEntry(String.format(Locale.US, "frame_%03d.png", count)))
                            frame.compress(Bitmap.CompressFormat.PNG, 92, zos)
                            zos.closeEntry()
                            frame.recycle()
                        }
                        tMs += stepMs
                    }
                }

                if (count == 0) {
                    MediaUriUtils.cleanupQuietly(destZip)
                    emit(ToolProgress.Failed(if (ctx.isEnglish) "No video frames could be decoded." else "Tidak ada frame video yang dapat didekode."))
                    return@flow
                }

                val sizeText = ctx.storage.formatBytes(destZip.length())
                emit(
                    ToolProgress.Done(
                        ToolResult(
                            outputs = listOf(ToolOutputFile(destZip, "application/zip", destZip.name)),
                            summary = if (ctx.isEnglish) {
                                "$count PNG frames extracted to ZIP ($sizeText)"
                            } else {
                                "$count frame PNG diekstrak ke ZIP ($sizeText)"
                            }
                        )
                    )
                )
            } catch (ce: CancellationException) {
                MediaUriUtils.cleanupQuietly(destZip)
                throw ce
            } finally {
                runCatching { mmr.release() }
            }
        }.flowOn(Dispatchers.IO)
    }
)
