package com.harimu.app.feature.tools

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Splitscreen
import androidx.compose.material.icons.filled.SurroundSound
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Tune
import com.harimu.app.core.media.AudioEncoderCapabilities
import com.harimu.app.core.media.AudioToolMath
import com.harimu.app.core.media.DefaultAudioEncoderCapabilities
import com.harimu.app.core.media.Id3Metadata
import com.harimu.app.core.media.Id3TagHelper
import com.harimu.app.core.media.MediaJob
import com.harimu.app.core.media.MediaProbe
import com.harimu.app.core.media.MediaUriUtils
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
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

fun audioForgeBatch1ExtraTools(
    mediaJob: MediaJob = MediaJob(),
    encoderCapabilities: AudioEncoderCapabilities = DefaultAudioEncoderCapabilities
): List<Tool> = listOf(
    // AUD-14: Audio Resampler (8 - 96 kHz)
    object : Tool {
        override val id = "AUD-14"
        override val group = ToolGroup.AUDIO
        override val engineLabel = "FF"
        override val icon = Icons.Filled.AudioFile
        override val inputSpec = InputSpec.SingleAudio
        override val params = listOf(
            ToolParam.DropdownParam(
                "rate",
                "Sample Rate (Hz)",
                "Sample Rate (Hz)",
                listOf("8000", "11025", "16000", "22050", "44100", "48000", "88200", "96000"),
                "48000"
            ),
            ToolParam.DropdownParam("format", "Format Output", "Output Format", listOf("WAV", "FLAC", "MP3"), "WAV")
        )
        override fun title(isEnglish: Boolean) = if (isEnglish) "Audio Resampler (8–96 kHz)" else "Resampler Audio (8–96 kHz)"
        override fun description(isEnglish: Boolean) = if (isEnglish) {
            "Convert audio sample rate from 8 kHz telephony to 96 kHz studio quality."
        } else {
            "Ubah sample rate audio dari 8 kHz hingga 96 kHz dengan validasi batas codec."
        }

        override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
            val validation = AudioToolMath.validateAudioInputs(ctx.appContext, ctx.storage, input.uris, 1, ctx.isEnglish)
            if (!validation.valid) {
                emit(ToolProgress.Failed(validation.errorMessage ?: "Invalid audio"))
                return@flow
            }
            val targetRate = (input.getString("rate", "48000").toIntOrNull() ?: 48_000).coerceIn(8000, 96_000)
            val formatLabel = input.getString("format", "WAV")
            val formatCheck = AudioToolMath.validateOutputFormatAndRate(
                formatLabel = formatLabel,
                sampleRateHz = targetRate,
                capabilities = encoderCapabilities,
                isEnglish = ctx.isEnglish
            )
            if (!formatCheck.valid || formatCheck.spec == null) {
                emit(ToolProgress.Failed(formatCheck.errorMessage ?: "Format / sample rate unsupported"))
                return@flow
            }
            val outSpec = formatCheck.spec
            val probe = validation.probes.first()

            emitAll(
                mediaJob.runCommand(
                    context = ctx.appContext,
                    storage = ctx.storage,
                    inputUris = input.uris,
                    toolTag = "AUD_RESAMPLE",
                    outputExt = outSpec.extension,
                    outputMime = outSpec.mimeType,
                    expectedDurationMs = probe.durationMs,
                    isEnglish = ctx.isEnglish
                ) { inputs, output ->
                    buildList {
                        add("-y")
                        add("-i"); add(inputs.first().absolutePath)
                        add("-vn")
                        addAll(AudioToolMath.buildCodecOutputArgs(outSpec, sampleRateHz = targetRate))
                        add(output.absolutePath)
                    }
                }
            )
        }.flowOn(Dispatchers.IO)
    },

    // AUD-15: Audio Channel Manipulator
    object : Tool {
        override val id = "AUD-15"
        override val group = ToolGroup.AUDIO
        override val engineLabel = "FF"
        override val icon = Icons.Filled.SurroundSound
        override val inputSpec = InputSpec.SingleAudio
        override val params = listOf(
            ToolParam.DropdownParam(
                "mode",
                "Operasi Kanal",
                "Channel Operation",
                listOf("Mono Mix", "Stereo", "Tukar L/R", "Hanya Kiri", "Hanya Kanan"),
                "Mono Mix"
            )
        )
        override fun title(isEnglish: Boolean) = if (isEnglish) "Audio Channel Manipulator" else "Manipulasi Kanal Audio"
        override fun description(isEnglish: Boolean) = if (isEnglish) {
            "Convert stereo/mono, swap L/R channels, or isolate Left/Right channels."
        } else {
            "Ubah stereo ke mono atau sebaliknya, tukar L/R, atau isolasi satu kanal."
        }

        override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
            val validation = AudioToolMath.validateAudioInputs(ctx.appContext, ctx.storage, input.uris, 1, ctx.isEnglish)
            if (!validation.valid) {
                emit(ToolProgress.Failed(validation.errorMessage ?: "Invalid audio"))
                return@flow
            }
            val probe = validation.probes.first()
            val mode = input.getString("mode", "Mono Mix")
            val srcExt = MediaUriUtils.resolveExtension(ctx.appContext, input.uris.first(), "mp3")
            val outSpec = AudioToolMath.resolveDefaultOutputSpec(srcExt)
            val channelArgs = AudioToolMath.buildChannelArgs(mode, probe.audioChannels.coerceAtLeast(1))

            emitAll(
                mediaJob.runCommand(
                    context = ctx.appContext,
                    storage = ctx.storage,
                    inputUris = input.uris,
                    toolTag = "AUD_CHAN",
                    outputExt = outSpec.extension,
                    outputMime = outSpec.mimeType,
                    expectedDurationMs = probe.durationMs,
                    isEnglish = ctx.isEnglish
                ) { inputs, output ->
                    buildList {
                        add("-y")
                        add("-i"); add(inputs.first().absolutePath)
                        addAll(channelArgs)
                        add("-vn")
                        addAll(AudioToolMath.buildCodecOutputArgs(outSpec))
                        add(output.absolutePath)
                    }
                }
            )
        }.flowOn(Dispatchers.IO)
    },

    // AUD-16: Audio Silence Remover (threshold dB + minimum duration)
    object : Tool {
        override val id = "AUD-16"
        override val group = ToolGroup.AUDIO
        override val engineLabel = "FF"
        override val icon = Icons.AutoMirrored.Filled.VolumeOff
        override val inputSpec = InputSpec.SingleAudio
        override val params = listOf(
            ToolParam.SliderParam("thresholdDb", "Ambang Hening (dB)", "Silence Threshold (dB)", -60f, -15f, -40f, 1f, "dB"),
            ToolParam.SliderParam("minDurationSec", "Durasi Hening Min. (dtk)", "Min Silence Duration (sec)", 0.2f, 3.0f, 0.5f, 0.1f, "s")
        )
        override fun title(isEnglish: Boolean) = if (isEnglish) "Audio Silence Remover" else "Hapus Bagian Hening"
        override fun description(isEnglish: Boolean) = if (isEnglish) {
            "Detect and strip silent gaps using dB threshold and minimum silence duration."
        } else {
            "Deteksi dan pangkas bagian hening berdasarkan ambang dB dan durasi minimum."
        }

        override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
            val validation = AudioToolMath.validateAudioInputs(ctx.appContext, ctx.storage, input.uris, 1, ctx.isEnglish)
            if (!validation.valid) {
                emit(ToolProgress.Failed(validation.errorMessage ?: "Invalid audio"))
                return@flow
            }
            val probe = validation.probes.first()
            val thresholdDb = input.getFloat("thresholdDb", -40f)
            val minDurationSec = input.getFloat("minDurationSec", 0.5f)
            val srcExt = MediaUriUtils.resolveExtension(ctx.appContext, input.uris.first(), "mp3")
            val outSpec = AudioToolMath.resolveDefaultOutputSpec(srcExt)
            val filter = AudioToolMath.buildSilenceRemoveFilter(thresholdDb, minDurationSec)

            emitAll(
                mediaJob.runCommand(
                    context = ctx.appContext,
                    storage = ctx.storage,
                    inputUris = input.uris,
                    toolTag = "AUD_SILENCE",
                    outputExt = outSpec.extension,
                    outputMime = outSpec.mimeType,
                    expectedDurationMs = probe.durationMs,
                    isEnglish = ctx.isEnglish
                ) { inputs, output ->
                    buildList {
                        add("-y")
                        add("-i"); add(inputs.first().absolutePath)
                        add("-af"); add(filter)
                        add("-vn")
                        addAll(AudioToolMath.buildCodecOutputArgs(outSpec))
                        add(output.absolutePath)
                    }
                }
            )
        }.flowOn(Dispatchers.IO)
    },

    // AUD-17: Reverse Audio (with segmented RAM safeguard)
    object : Tool {
        override val id = "AUD-17"
        override val group = ToolGroup.AUDIO
        override val engineLabel = "FF"
        override val icon = Icons.Filled.History
        override val inputSpec = InputSpec.SingleAudio
        override val params = emptyList<ToolParam>()
        override fun title(isEnglish: Boolean) = if (isEnglish) "Reverse Audio" else "Putar Balik Audio"
        override fun description(isEnglish: Boolean) = if (isEnglish) {
            "Reverse audio playback cleanly using FFmpeg with memory-safe chunking."
        } else {
            "Balik arah pemutaran audio dari akhir ke awal dengan pengaman memori."
        }

        override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
            val validation = AudioToolMath.validateAudioInputs(ctx.appContext, ctx.storage, input.uris, 1, ctx.isEnglish)
            if (!validation.valid) {
                emit(ToolProgress.Failed(validation.errorMessage ?: "Invalid audio"))
                return@flow
            }
            val probe = validation.probes.first()
            val safety = AudioToolMath.assessAudioReverseSafety(probe.durationMs, ctx.isEnglish)
            if (!safety.allowed) {
                emit(ToolProgress.Failed(safety.blockingError ?: "Audio too long"))
                return@flow
            }
            val srcExt = MediaUriUtils.resolveExtension(ctx.appContext, input.uris.first(), "mp3")
            val outSpec = AudioToolMath.resolveDefaultOutputSpec(srcExt)

            if (!safety.useSegmentedMode) {
                emitAll(
                    mediaJob.runCommand(
                        context = ctx.appContext,
                        storage = ctx.storage,
                        inputUris = input.uris,
                        toolTag = "AUD_REV",
                        outputExt = outSpec.extension,
                        outputMime = outSpec.mimeType,
                        expectedDurationMs = probe.durationMs,
                        isEnglish = ctx.isEnglish
                    ) { inputs, output ->
                        buildList {
                            add("-y")
                            add("-i"); add(inputs.first().absolutePath)
                            add("-af"); add("areverse")
                            add("-vn")
                            addAll(AudioToolMath.buildCodecOutputArgs(outSpec))
                            add(output.absolutePath)
                        }
                    }
                )
                return@flow
            }

            // Segmented reverse (>45s) for safe RAM footprint
            val jobDir = ctx.storage.createJobTempDir("aud_rev_${UUID.randomUUID().toString().take(8)}")
            val outputFile = MediaUriUtils.createOutputFile(ctx.storage, "AUD_REV", outSpec.extension)
            try {
                emit(ToolProgress.Queued)
                val localIn = MediaUriUtils.copyUriToSafeTemp(
                    context = ctx.appContext,
                    storage = ctx.storage,
                    uri = input.uris.first(),
                    jobDir = jobDir,
                    index = 0,
                    fallbackExt = srcExt
                )
                val chunks = com.harimu.app.core.media.VideoToolMath.computeReverseChunkRanges(probe.durationMs, 20_000L)
                val reversedParts = mutableListOf<File>()
                chunks.reversed().forEachIndexed { idx, (startMs, endMs) ->
                    currentCoroutineContext().ensureActive()
                    val pct = 10 + ((idx * 75) / maxOf(1, chunks.size))
                    emit(
                        ToolProgress.Running(
                            pct,
                            safety.warningMessage ?: if (ctx.isEnglish) {
                                "Reversing segment ${idx + 1}/${chunks.size}…"
                            } else {
                                "Membalik segmen ${idx + 1}/${chunks.size}…"
                            }
                        )
                    )
                    val partFile = File(jobDir, "rev_part_${idx}.${outSpec.extension}")
                    val stepRes = mediaJob.runStep(
                        arguments = buildList {
                            add("-y")
                            add("-ss"); add(String.format(Locale.US, "%.3f", startMs / 1000.0))
                            add("-t"); add(String.format(Locale.US, "%.3f", (endMs - startMs) / 1000.0))
                            add("-i"); add(localIn.absolutePath)
                            add("-af"); add("areverse")
                            add("-vn")
                            addAll(AudioToolMath.buildCodecOutputArgs(outSpec))
                            add(partFile.absolutePath)
                        },
                        expectedDurationMs = endMs - startMs,
                        isEnglish = ctx.isEnglish
                    )
                    if (stepRes.isFailure || !partFile.exists()) {
                        MediaUriUtils.cleanupQuietly(outputFile)
                        emit(ToolProgress.Failed(stepRes.exceptionOrNull()?.localizedMessage ?: "Reverse segment failed"))
                        return@flow
                    }
                    reversedParts.add(partFile)
                }

                val listFile = File(jobDir, "concat_list.txt")
                listFile.writeText(reversedParts.joinToString("\n") { "file '${it.absolutePath.replace("'", "'\\''")}'" })
                emit(ToolProgress.Running(90, if (ctx.isEnglish) "Joining reversed segments…" else "Menggabungkan segmen terbalik…"))
                val concatRes = mediaJob.runStep(
                    arguments = listOf(
                        "-y",
                        "-f", "concat",
                        "-safe", "0",
                        "-i", listFile.absolutePath,
                        "-c", "copy",
                        outputFile.absolutePath
                    ),
                    expectedDurationMs = probe.durationMs,
                    isEnglish = ctx.isEnglish
                )
                if (concatRes.isFailure || !outputFile.exists() || outputFile.length() == 0L) {
                    MediaUriUtils.cleanupQuietly(outputFile)
                    emit(ToolProgress.Failed(concatRes.exceptionOrNull()?.localizedMessage ?: "Reverse concat failed"))
                    return@flow
                }
                val sizeText = ctx.storage.formatBytes(outputFile.length())
                emit(
                    ToolProgress.Done(
                        ToolResult(
                            outputs = listOf(ToolOutputFile(outputFile, outSpec.mimeType, outputFile.name)),
                            summary = if (ctx.isEnglish) {
                                "Reversed (${chunks.size} segments) • ${outputFile.name} ($sizeText)"
                            } else {
                                "Dibalik (${chunks.size} segmen) • ${outputFile.name} ($sizeText)"
                            }
                        )
                    )
                )
            } catch (ce: CancellationException) {
                MediaUriUtils.cleanupQuietly(outputFile)
                throw ce
            } finally {
                MediaUriUtils.cleanupQuietly(jobDir)
            }
        }.flowOn(Dispatchers.IO)
    },

    // AUD-18: Audio Metadata Editor (ID3v2.3 + Album Art)
    object : Tool {
        override val id = "AUD-18"
        override val group = ToolGroup.AUDIO
        override val engineLabel = "LIB/FF"
        override val icon = Icons.Filled.Tag
        override val inputSpec = InputSpec.AudioAndImage
        override val params = listOf(
            ToolParam.TextParam("title", "Judul Lagu", "Track Title", "", "Judul lagu...", "Track title..."),
            ToolParam.TextParam("artist", "Artis", "Artist", "", "Nama artis...", "Artist name..."),
            ToolParam.TextParam("album", "Album", "Album", "", "Nama album...", "Album name..."),
            ToolParam.TextParam("year", "Tahun", "Year", "", "2026", "2026"),
            ToolParam.TextParam("genre", "Genre", "Genre", "", "Pop / Podcast", "Pop / Podcast")
        )
        override fun title(isEnglish: Boolean) = if (isEnglish) "Audio Metadata Editor (ID3)" else "Editor Metadata Audio (ID3)"
        override fun description(isEnglish: Boolean) = if (isEnglish) {
            "Edit ID3 title, artist, album, year, genre, and embedded cover artwork."
        } else {
            "Sunting tag ID3 judul, artis, album, tahun, genre, dan gambar sampul."
        }

        override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
            val validation = AudioToolMath.validateAudioInputs(ctx.appContext, ctx.storage, input.uris, 1, ctx.isEnglish)
            if (!validation.valid) {
                emit(ToolProgress.Failed(validation.errorMessage ?: "Invalid audio"))
                return@flow
            }
            val audioUri = input.uris.first()
            val srcExt = MediaUriUtils.resolveExtension(ctx.appContext, audioUri, "mp3").lowercase(Locale.US)
            val existing = Id3TagHelper.readTags(ctx.appContext, audioUri)

            val coverPayload: Pair<ByteArray, String>? = input.secondaryUri?.let { imgUri ->
                decodeAndResizeCover(ctx, imgUri)
            } ?: existing.coverBytes?.let { it to existing.coverMime }

            val metadata = Id3Metadata(
                title = input.getString("title", "").ifBlank { existing.title },
                artist = input.getString("artist", "").ifBlank { existing.artist },
                album = input.getString("album", "").ifBlank { existing.album },
                year = input.getString("year", "").ifBlank { existing.year },
                genre = input.getString("genre", "").ifBlank { existing.genre },
                coverBytes = coverPayload?.first,
                coverMime = coverPayload?.second ?: "image/jpeg"
            )

            val jobDir = ctx.storage.createJobTempDir("aud_id3_${UUID.randomUUID().toString().take(8)}")
            val outSpec = when (srcExt) {
                "mp3" -> AudioToolMath.FORMAT_SPECS.getValue("MP3")
                "flac" -> AudioToolMath.FORMAT_SPECS.getValue("FLAC")
                "ogg", "oga" -> AudioToolMath.FORMAT_SPECS.getValue("OGG")
                "m4a", "aac" -> AudioToolMath.FORMAT_SPECS.getValue("M4A (AAC)")
                "wav" -> AudioToolMath.FORMAT_SPECS.getValue("WAV")
                else -> AudioToolMath.FORMAT_SPECS.getValue("MP3")
            }
            val outputFile = MediaUriUtils.createOutputFile(ctx.storage, "AUD_ID3", outSpec.extension)

            try {
                emit(ToolProgress.Queued)
                emit(ToolProgress.Running(30, if (ctx.isEnglish) "Writing audio tags…" else "Menulis metadata ID3…"))
                val localIn = MediaUriUtils.copyUriToSafeTemp(
                    context = ctx.appContext,
                    storage = ctx.storage,
                    uri = audioUri,
                    jobDir = jobDir,
                    index = 0,
                    fallbackExt = srcExt
                )

                var coverIncluded = false
                if (srcExt == "mp3") {
                    Id3TagHelper.writeId3v23Mp3(localIn, outputFile, metadata)
                    coverIncluded = metadata.coverBytes != null
                } else {
                    val coverFile = metadata.coverBytes?.let { bytes ->
                        val ext = if (metadata.coverMime.contains("png", ignoreCase = true)) "png" else "jpg"
                        File(jobDir, "cover.$ext").apply { writeBytes(bytes) }
                    }
                    val supportsAttachedPic = coverFile != null && (srcExt == "m4a" || srcExt == "flac")
                    coverIncluded = supportsAttachedPic
                    val args = buildList {
                        add("-y")
                        add("-i"); add(localIn.absolutePath)
                        val coverToAttach = coverFile?.takeIf { supportsAttachedPic }
                        if (coverToAttach != null) {
                            add("-i"); add(coverToAttach.absolutePath)
                            add("-map"); add("0:a")
                            add("-map"); add("1:v")
                            add("-c:a"); add("copy")
                            add("-c:v"); add("mjpeg")
                            add("-disposition:v:0"); add("attached_pic")
                        } else {
                            add("-map"); add("0:a")
                            add("-c:a"); add("copy")
                        }
                        addAll(Id3TagHelper.buildFfmpegMetadataArgs(metadata))
                        add(outputFile.absolutePath)
                    }
                    val res = mediaJob.runStep(args, isEnglish = ctx.isEnglish)
                    if (res.isFailure || !outputFile.exists() || outputFile.length() == 0L) {
                        MediaUriUtils.cleanupQuietly(outputFile)
                        emit(ToolProgress.Failed(res.exceptionOrNull()?.localizedMessage ?: "Metadata write failed"))
                        return@flow
                    }
                }

                val summaryDetails = buildString {
                    append(if (ctx.isEnglish) "Title" else "Judul").append(": ").append(metadata.title.ifBlank { "-" }).append('\n')
                    append(if (ctx.isEnglish) "Artist" else "Artis").append(": ").append(metadata.artist.ifBlank { "-" }).append('\n')
                    append("Album: ").append(metadata.album.ifBlank { "-" }).append('\n')
                    append(if (ctx.isEnglish) "Year" else "Tahun").append(": ").append(metadata.year.ifBlank { "-" }).append('\n')
                    append("Genre: ").append(metadata.genre.ifBlank { "-" }).append('\n')
                    append(if (ctx.isEnglish) "Cover Art: " else "Sampul: ")
                    append(
                        when {
                            coverIncluded -> if (ctx.isEnglish) "Embedded" else "Disematkan"
                            metadata.coverBytes != null -> if (ctx.isEnglish) "Skipped (container .$srcExt does not embed cover; use MP3/M4A/FLAC)" else "Dilewati (kontainer .$srcExt tidak mendukung sampul; pakai MP3/M4A/FLAC)"
                            else -> if (ctx.isEnglish) "None" else "Tidak ada"
                        }
                    )
                }

                val sizeText = ctx.storage.formatBytes(outputFile.length())
                emit(
                    ToolProgress.Done(
                        ToolResult(
                            outputs = listOf(ToolOutputFile(outputFile, outSpec.mimeType, outputFile.name)),
                            textOutput = summaryDetails,
                            summary = if (ctx.isEnglish) {
                                "ID3 tags updated • ${outputFile.name} ($sizeText)"
                            } else {
                                "Metadata ID3 diperbarui • ${outputFile.name} ($sizeText)"
                            }
                        )
                    )
                )
            } catch (ce: CancellationException) {
                MediaUriUtils.cleanupQuietly(outputFile)
                throw ce
            } finally {
                MediaUriUtils.cleanupQuietly(jobDir)
            }
        }.flowOn(Dispatchers.IO)
    },

    // AUD-23: Audio Splitter (.zip)
    object : Tool {
        override val id = "AUD-23"
        override val group = ToolGroup.AUDIO
        override val engineLabel = "FF"
        override val icon = Icons.Filled.Splitscreen
        override val inputSpec = InputSpec.SingleAudio
        override val params = listOf(
            ToolParam.DropdownParam("mode", "Mode Pemecahan", "Split Mode", listOf("Durasi per Bagian", "Jumlah Bagian"), "Durasi per Bagian"),
            ToolParam.SliderParam("segmentSec", "Durasi per Bagian (dtk)", "Duration per Part (sec)", 5f, 300f, 30f, 5f, "s"),
            ToolParam.SliderParam("partsCount", "Jumlah Bagian", "Number of Parts", 2f, 12f, 4f, 1f, "")
        )
        override fun title(isEnglish: Boolean) = if (isEnglish) "Audio Splitter (.zip)" else "Pemecah Audio (.zip)"
        override fun description(isEnglish: Boolean) = if (isEnglish) {
            "Split long audio into equal duration chunks or exact part counts inside a ZIP."
        } else {
            "Pecah file audio panjang berdasarkan durasi atau jumlah bagian ke arsip ZIP."
        }

        override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
            val validation = AudioToolMath.validateAudioInputs(ctx.appContext, ctx.storage, input.uris, 1, ctx.isEnglish)
            if (!validation.valid) {
                emit(ToolProgress.Failed(validation.errorMessage ?: "Invalid audio"))
                return@flow
            }
            val probe = validation.probes.first()
            val byCount = input.getString("mode", "Durasi per Bagian").contains("Jumlah", ignoreCase = true) ||
                input.getString("mode", "").contains("Number", ignoreCase = true)
            val ranges = AudioToolMath.computeSplitRanges(
                totalDurationMs = probe.durationMs,
                byPartCount = byCount,
                segmentSec = input.getFloat("segmentSec", 30f).toInt(),
                partsCount = input.getFloat("partsCount", 4f).toInt()
            )
            val srcExt = MediaUriUtils.resolveExtension(ctx.appContext, input.uris.first(), "mp3")
            val outSpec = AudioToolMath.resolveDefaultOutputSpec(srcExt)

            val jobDir = ctx.storage.createJobTempDir("aud_split_${UUID.randomUUID().toString().take(8)}")
            val zipFile = MediaUriUtils.createOutputFile(ctx.storage, "AUD_SPLIT", "zip")

            try {
                emit(ToolProgress.Queued)
                val localIn = MediaUriUtils.copyUriToSafeTemp(
                    context = ctx.appContext,
                    storage = ctx.storage,
                    uri = input.uris.first(),
                    jobDir = jobDir,
                    index = 0,
                    fallbackExt = srcExt
                )

                val partFiles = mutableListOf<File>()
                ranges.forEachIndexed { idx, (startMs, endMs) ->
                    currentCoroutineContext().ensureActive()
                    val pct = 10 + ((idx * 75) / maxOf(1, ranges.size))
                    emit(
                        ToolProgress.Running(
                            pct,
                            if (ctx.isEnglish) "Splitting part ${idx + 1}/${ranges.size}…"
                            else "Memecah bagian ${idx + 1}/${ranges.size}…"
                        )
                    )
                    val partFile = File(jobDir, "part_${String.format(Locale.US, "%02d", idx + 1)}.${outSpec.extension}")
                    val res = mediaJob.runStep(
                        arguments = buildList {
                            add("-y")
                            add("-ss"); add(String.format(Locale.US, "%.3f", startMs / 1000.0))
                            add("-t"); add(String.format(Locale.US, "%.3f", (endMs - startMs) / 1000.0))
                            add("-i"); add(localIn.absolutePath)
                            add("-vn")
                            addAll(AudioToolMath.buildCodecOutputArgs(outSpec))
                            add(partFile.absolutePath)
                        },
                        expectedDurationMs = endMs - startMs,
                        isEnglish = ctx.isEnglish
                    )
                    if (res.isFailure || !partFile.exists()) {
                        MediaUriUtils.cleanupQuietly(zipFile)
                        emit(ToolProgress.Failed(res.exceptionOrNull()?.localizedMessage ?: "Audio split failed"))
                        return@flow
                    }
                    partFiles.add(partFile)
                }

                emit(ToolProgress.Running(90, if (ctx.isEnglish) "Packing segments to ZIP…" else "Mengemas bagian ke ZIP…"))
                ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
                    val buf = ByteArray(32 * 1024)
                    partFiles.forEach { part ->
                        zos.putNextEntry(ZipEntry(part.name))
                        BufferedInputStream(FileInputStream(part)).use { fis ->
                            while (true) {
                                val n = fis.read(buf)
                                if (n <= 0) break
                                zos.write(buf, 0, n)
                            }
                        }
                        zos.closeEntry()
                    }
                }

                val sizeText = ctx.storage.formatBytes(zipFile.length())
                emit(
                    ToolProgress.Done(
                        ToolResult(
                            outputs = listOf(ToolOutputFile(zipFile, "application/zip", zipFile.name)),
                            summary = if (ctx.isEnglish) {
                                "${partFiles.size} audio parts packed in ${zipFile.name} ($sizeText)"
                            } else {
                                "${partFiles.size} bagian audio dikemas ke ${zipFile.name} ($sizeText)"
                            }
                        )
                    )
                )
            } catch (ce: CancellationException) {
                MediaUriUtils.cleanupQuietly(zipFile)
                throw ce
            } finally {
                MediaUriUtils.cleanupQuietly(jobDir)
            }
        }.flowOn(Dispatchers.IO)
    },

    // AUD-26: Audio Fade In / Out
    object : Tool {
        override val id = "AUD-26"
        override val group = ToolGroup.AUDIO
        override val engineLabel = "FF"
        override val icon = Icons.Filled.Tune
        override val inputSpec = InputSpec.SingleAudio
        override val params = listOf(
            ToolParam.SliderParam("fadeIn", "Fade In (detik)", "Fade In (sec)", 0f, 15f, 2f, 0.5f, "s"),
            ToolParam.SliderParam("fadeOut", "Fade Out (detik)", "Fade Out (sec)", 0f, 15f, 3f, 0.5f, "s")
        )
        override fun title(isEnglish: Boolean) = if (isEnglish) "Audio Fade In / Out" else "Fade In / Out Audio"
        override fun description(isEnglish: Boolean) = if (isEnglish) {
            "Apply smooth intro fade-in and outro fade-out volume curves."
        } else {
            "Terapkan transisi fade-in awal dan fade-out akhir secara halus lewat FFmpeg."
        }

        override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
            val validation = AudioToolMath.validateAudioInputs(ctx.appContext, ctx.storage, input.uris, 1, ctx.isEnglish)
            if (!validation.valid) {
                emit(ToolProgress.Failed(validation.errorMessage ?: "Invalid audio"))
                return@flow
            }
            val probe = validation.probes.first()
            val fadeIn = input.getFloat("fadeIn", 2f)
            val fadeOut = input.getFloat("fadeOut", 3f)
            val srcExt = MediaUriUtils.resolveExtension(ctx.appContext, input.uris.first(), "mp3")
            val outSpec = AudioToolMath.resolveDefaultOutputSpec(srcExt)
            val fadeFilter = AudioToolMath.buildFadeFilter(probe.durationMs, fadeIn, fadeOut)

            emitAll(
                mediaJob.runCommand(
                    context = ctx.appContext,
                    storage = ctx.storage,
                    inputUris = input.uris,
                    toolTag = "AUD_FADE",
                    outputExt = outSpec.extension,
                    outputMime = outSpec.mimeType,
                    expectedDurationMs = probe.durationMs,
                    isEnglish = ctx.isEnglish
                ) { inputs, output ->
                    buildList {
                        add("-y")
                        add("-i"); add(inputs.first().absolutePath)
                        if (fadeFilter != "anull") {
                            add("-af"); add(fadeFilter)
                        }
                        add("-vn")
                        addAll(AudioToolMath.buildCodecOutputArgs(outSpec))
                        add(output.absolutePath)
                    }
                }
            )
        }.flowOn(Dispatchers.IO)
    }
)

private fun decodeAndResizeCover(ctx: ToolContext, imageUri: android.net.Uri): Pair<ByteArray, String>? {
    return runCatching {
        val raw = ctx.appContext.contentResolver.openInputStream(imageUri)?.use { it.readBytes() } ?: return@runCatching null
        if (raw.size <= 350_000) {
            return@runCatching raw to Id3TagHelper.detectImageMimeType(raw)
        }
        val bmp = BitmapFactory.decodeByteArray(raw, 0, raw.size) ?: return@runCatching raw to "image/jpeg"
        val maxSide = maxOf(bmp.width, bmp.height).coerceAtLeast(1)
        val scale = if (maxSide > 800) 800f / maxSide else 1f
        val target = if (scale < 1f) {
            val scaled = Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt().coerceAtLeast(1), (bmp.height * scale).toInt().coerceAtLeast(1), true)
            bmp.recycle()
            scaled
        } else {
            bmp
        }
        val out = ByteArrayOutputStream()
        target.compress(Bitmap.CompressFormat.JPEG, 88, out)
        target.recycle()
        out.toByteArray() to "image/jpeg"
    }.getOrNull()
}
