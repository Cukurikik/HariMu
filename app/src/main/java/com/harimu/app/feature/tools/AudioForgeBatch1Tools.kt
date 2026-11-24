package com.harimu.app.feature.tools

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MergeType
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import com.harimu.app.core.media.AudioEncoderCapabilities
import com.harimu.app.core.media.AudioToolMath
import com.harimu.app.core.media.DefaultAudioEncoderCapabilities
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.util.Locale
import java.util.UUID

fun audioForgeBatch1CoreTools(
    mediaJob: MediaJob = MediaJob(),
    encoderCapabilities: AudioEncoderCapabilities = DefaultAudioEncoderCapabilities
): List<Tool> = listOf(
    // AUD-06: Change Audio Pitch (preserving duration)
    object : Tool {
        override val id = "AUD-06"
        override val group = ToolGroup.AUDIO
        override val engineLabel = "FF"
        override val icon = Icons.Filled.Tune
        override val inputSpec = InputSpec.SingleAudio
        override val params = listOf(
            ToolParam.SliderParam("semitones", "Nada (Semitone)", "Pitch (Semitones)", -12f, 12f, 2f, 1f, "st")
        )
        override fun title(isEnglish: Boolean) = if (isEnglish) "Change Audio Pitch" else "Ubah Pitch Audio"
        override fun description(isEnglish: Boolean) = if (isEnglish) {
            "Shift audio pitch up or down (-12 to +12 semitones) without changing duration."
        } else {
            "Naikkan atau turunkan nada (-12 s.d. +12 semitone) tanpa mengubah durasi."
        }

        override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
            val validation = AudioToolMath.validateAudioInputs(ctx.appContext, ctx.storage, input.uris, 1, ctx.isEnglish)
            if (!validation.valid) {
                emit(ToolProgress.Failed(validation.errorMessage ?: "Invalid audio"))
                return@flow
            }
            val probe = validation.probes.first()
            val semitones = input.getFloat("semitones", 2f).coerceIn(-12f, 12f)
            val srcExt = MediaUriUtils.resolveExtension(ctx.appContext, input.uris.first(), "mp3")
            val outSpec = AudioToolMath.resolveDefaultOutputSpec(srcExt)
            val sampleRate = probe.audioSampleRateHz.takeIf { it in 8000..192000 } ?: 44_100
            val filter = AudioToolMath.buildPitchShiftFilter(semitones, sampleRate)

            emitAll(
                mediaJob.runCommand(
                    context = ctx.appContext,
                    storage = ctx.storage,
                    inputUris = input.uris,
                    toolTag = "AUD_PITCH",
                    outputExt = outSpec.extension,
                    outputMime = outSpec.mimeType,
                    expectedDurationMs = probe.durationMs,
                    isEnglish = ctx.isEnglish
                ) { inputs, output ->
                    buildList {
                        add("-y")
                        add("-i"); add(inputs.first().absolutePath)
                        if (filter != "anull") {
                            add("-af"); add(filter)
                        }
                        add("-vn")
                        addAll(AudioToolMath.buildCodecOutputArgs(outSpec))
                        add(output.absolutePath)
                    }
                }
            )
        }.flowOn(Dispatchers.IO)
    },

    // AUD-07: Change Audio Speed
    object : Tool {
        override val id = "AUD-07"
        override val group = ToolGroup.AUDIO
        override val engineLabel = "FF"
        override val icon = Icons.Filled.Speed
        override val inputSpec = InputSpec.SingleAudio
        override val params = listOf(
            ToolParam.SliderParam("speed", "Kecepatan", "Speed", 0.5f, 2.0f, 1.25f, 0.05f, "x")
        )
        override fun title(isEnglish: Boolean) = if (isEnglish) "Change Audio Speed" else "Ubah Kecepatan Audio"
        override fun description(isEnglish: Boolean) = if (isEnglish) {
            "Adjust audio tempo cleanly (0.5x–2.0x) while keeping natural pitch."
        } else {
            "Atur tempo audio (0.5x–2.0x) sambil menjaga nada tetap natural."
        }

        override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
            val validation = AudioToolMath.validateAudioInputs(ctx.appContext, ctx.storage, input.uris, 1, ctx.isEnglish)
            if (!validation.valid) {
                emit(ToolProgress.Failed(validation.errorMessage ?: "Invalid audio"))
                return@flow
            }
            val probe = validation.probes.first()
            val speed = input.getFloat("speed", 1.25f).coerceIn(0.25f, 4.0f)
            val srcExt = MediaUriUtils.resolveExtension(ctx.appContext, input.uris.first(), "mp3")
            val outSpec = AudioToolMath.resolveDefaultOutputSpec(srcExt)
            val filter = AudioToolMath.buildAudioAtempoChain(speed)
            val expectedOutMs = AudioToolMath.computeAdjustedDurationMs(probe.durationMs, speed)

            emitAll(
                mediaJob.runCommand(
                    context = ctx.appContext,
                    storage = ctx.storage,
                    inputUris = input.uris,
                    toolTag = "AUD_SPEED",
                    outputExt = outSpec.extension,
                    outputMime = outSpec.mimeType,
                    expectedDurationMs = expectedOutMs,
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

    // AUD-08: Change Audio Volume
    object : Tool {
        override val id = "AUD-08"
        override val group = ToolGroup.AUDIO
        override val engineLabel = "FF"
        override val icon = Icons.AutoMirrored.Filled.VolumeUp
        override val inputSpec = InputSpec.SingleAudio
        override val params = listOf(
            ToolParam.SliderParam("gain", "Penguatan Volume (%)", "Volume Gain (%)", 20f, 300f, 150f, 10f, "%")
        )
        override fun title(isEnglish: Boolean) = if (isEnglish) "Change Audio Volume" else "Ubah Volume Audio"
        override fun description(isEnglish: Boolean) = if (isEnglish) {
            "Boost or attenuate audio volume cleanly with FFmpeg."
        } else {
            "Naikkan atau turunkan level volume audio secara bersih lewat FFmpeg."
        }

        override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
            val validation = AudioToolMath.validateAudioInputs(ctx.appContext, ctx.storage, input.uris, 1, ctx.isEnglish)
            if (!validation.valid) {
                emit(ToolProgress.Failed(validation.errorMessage ?: "Invalid audio"))
                return@flow
            }
            val probe = validation.probes.first()
            val gain = input.getFloat("gain", 150f).coerceIn(0f, 300f)
            val srcExt = MediaUriUtils.resolveExtension(ctx.appContext, input.uris.first(), "mp3")
            val outSpec = AudioToolMath.resolveDefaultOutputSpec(srcExt)
            val volFilter = AudioToolMath.buildVolumeFilter(gain)

            emitAll(
                mediaJob.runCommand(
                    context = ctx.appContext,
                    storage = ctx.storage,
                    inputUris = input.uris,
                    toolTag = "AUD_VOL",
                    outputExt = outSpec.extension,
                    outputMime = outSpec.mimeType,
                    expectedDurationMs = probe.durationMs,
                    isEnglish = ctx.isEnglish
                ) { inputs, output ->
                    buildList {
                        add("-y")
                        add("-i"); add(inputs.first().absolutePath)
                        add("-af"); add(volFilter)
                        add("-vn")
                        addAll(AudioToolMath.buildCodecOutputArgs(outSpec))
                        add(output.absolutePath)
                    }
                }
            )
        }.flowOn(Dispatchers.IO)
    },

    // AUD-09: Trim Audio (with WaveformView & Before/After Preview)
    object : Tool {
        override val id = "AUD-09"
        override val group = ToolGroup.AUDIO
        override val engineLabel = "FF"
        override val icon = Icons.Filled.ContentCut
        override val inputSpec = InputSpec.SingleAudio
        override val params = listOf(
            ToolParam.SliderParam("startSec", "Mulai (detik)", "Start (sec)", 0f, 3600f, 0f, 0.5f, "s"),
            ToolParam.SliderParam("endSec", "Selesai (detik, 0=akhir)", "End (sec, 0=end)", 0f, 3600f, 15f, 0.5f, "s")
        )
        override fun title(isEnglish: Boolean) = if (isEnglish) "Trim Audio" else "Potong Audio"
        override fun description(isEnglish: Boolean) = if (isEnglish) {
            "Cut a precise audio range with interactive waveform preview."
        } else {
            "Potong segmen audio secara presisi dengan pratinjau gelombang suara."
        }

        override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
            val validation = AudioToolMath.validateAudioInputs(ctx.appContext, ctx.storage, input.uris, 1, ctx.isEnglish)
            if (!validation.valid) {
                emit(ToolProgress.Failed(validation.errorMessage ?: "Invalid audio"))
                return@flow
            }
            val probe = validation.probes.first()
            val startSec = input.getFloat("startSec", 0f).coerceAtLeast(0f)
            val rawEndSec = input.getFloat("endSec", 15f)
            val totalSec = if (probe.durationMs > 0L) probe.durationMs / 1000f else Float.MAX_VALUE
            val endSec = if (rawEndSec <= 0f) {
                if (probe.durationMs > 0L) totalSec else 0f
            } else {
                if (probe.durationMs > 0L) minOf(rawEndSec, totalSec) else rawEndSec
            }
            if (endSec > 0f && endSec <= startSec) {
                emit(
                    ToolProgress.Failed(
                        if (ctx.isEnglish) "End time must be greater than start time."
                        else "Waktu selesai harus lebih besar dari waktu mulai."
                    )
                )
                return@flow
            }
            val srcExt = MediaUriUtils.resolveExtension(ctx.appContext, input.uris.first(), "mp3")
            val outSpec = AudioToolMath.resolveDefaultOutputSpec(srcExt)
            val expectedDurMs = if (endSec > startSec) ((endSec - startSec) * 1000f).toLong() else probe.durationMs

            emitAll(
                mediaJob.runCommand(
                    context = ctx.appContext,
                    storage = ctx.storage,
                    inputUris = input.uris,
                    toolTag = "AUD_TRIM",
                    outputExt = outSpec.extension,
                    outputMime = outSpec.mimeType,
                    expectedDurationMs = expectedDurMs,
                    isEnglish = ctx.isEnglish
                ) { inputs, output ->
                    buildList {
                        add("-y")
                        add("-ss"); add(String.format(Locale.US, "%.3f", startSec))
                        add("-i"); add(inputs.first().absolutePath)
                        if (endSec > startSec) {
                            add("-t"); add(String.format(Locale.US, "%.3f", endSec - startSec))
                        }
                        add("-vn")
                        addAll(AudioToolMath.buildCodecOutputArgs(outSpec))
                        add(output.absolutePath)
                    }
                }
            )
        }.flowOn(Dispatchers.IO)
    },

    // AUD-10: Merge Audio (Sequential Concat & Parallel Mix)
    object : Tool {
        override val id = "AUD-10"
        override val group = ToolGroup.AUDIO
        override val engineLabel = "FF"
        override val icon = Icons.AutoMirrored.Filled.MergeType
        override val inputSpec = InputSpec.MultipleAudios
        override val params = listOf(
            ToolParam.DropdownParam("mode", "Mode Gabung", "Merge Mode", listOf("Berurutan (Concat)", "Campur (Mix)"), "Berurutan (Concat)")
        )
        override fun title(isEnglish: Boolean) = if (isEnglish) "Merge / Mix Audio" else "Gabung / Mix Audio"
        override fun description(isEnglish: Boolean) = if (isEnglish) {
            "Join multiple audio clips sequentially or mix them together with auto resampling."
        } else {
            "Sambungkan klip audio berurutan atau campur bersamaan dengan penyelarasan sample rate."
        }

        override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
            val validation = AudioToolMath.validateAudioInputs(ctx.appContext, ctx.storage, input.uris, 2, ctx.isEnglish)
            if (!validation.valid) {
                emit(ToolProgress.Failed(validation.errorMessage ?: "Select at least 2 audio files"))
                return@flow
            }
            val mixMode = input.getString("mode", "Berurutan (Concat)").contains("Mix", ignoreCase = true)
            val filterGraph = AudioToolMath.buildMergeFilterComplex(input.uris.size, mixMode)
            val expectedDuration = if (mixMode) {
                validation.probes.maxOfOrNull { it.durationMs } ?: 0L
            } else {
                validation.probes.sumOf { it.durationMs }
            }
            val outSpec = AudioToolMath.FORMAT_SPECS.getValue("MP3")

            emitAll(
                mediaJob.runCommand(
                    context = ctx.appContext,
                    storage = ctx.storage,
                    inputUris = input.uris,
                    toolTag = "AUD_MERGE",
                    outputExt = outSpec.extension,
                    outputMime = outSpec.mimeType,
                    expectedDurationMs = expectedDuration,
                    isEnglish = ctx.isEnglish
                ) { inputs, output ->
                    buildList {
                        add("-y")
                        inputs.forEach { file ->
                            add("-i"); add(file.absolutePath)
                        }
                        add("-filter_complex"); add(filterGraph)
                        add("-map"); add("[aout]")
                        addAll(AudioToolMath.buildCodecOutputArgs(outSpec))
                        add(output.absolutePath)
                    }
                }
            )
        }.flowOn(Dispatchers.IO)
    },

    // AUD-11: Audio Compressor (32k - 320k bitrate)
    object : Tool {
        override val id = "AUD-11"
        override val group = ToolGroup.AUDIO
        override val engineLabel = "FF"
        override val icon = Icons.Filled.Compress
        override val inputSpec = InputSpec.SingleAudio
        override val params = listOf(
            ToolParam.DropdownParam(
                "bitrate",
                "Bitrate Target",
                "Target Bitrate",
                listOf("32k", "64k", "96k", "128k", "192k", "256k", "320k"),
                "128k"
            ),
            ToolParam.DropdownParam("format", "Format Output", "Output Format", listOf("MP3", "OGG", "M4A (AAC)"), "MP3")
        )
        override fun title(isEnglish: Boolean) = if (isEnglish) "Audio Compressor (32k–320k)" else "Kompresor Audio (32k–320k)"
        override fun description(isEnglish: Boolean) = if (isEnglish) {
            "Shrink audio file size with selectable bitrate from 32k to 320k."
        } else {
            "Perkecil ukuran file audio dengan pemilihan bitrate 32k hingga 320k."
        }

        override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
            val validation = AudioToolMath.validateAudioInputs(ctx.appContext, ctx.storage, input.uris, 1, ctx.isEnglish)
            if (!validation.valid) {
                emit(ToolProgress.Failed(validation.errorMessage ?: "Invalid audio"))
                return@flow
            }
            val fmtLabel = input.getString("format", "MP3")
            val fmtCheck = AudioToolMath.validateOutputFormatAndRate(fmtLabel, null, encoderCapabilities, ctx.isEnglish)
            if (!fmtCheck.valid || fmtCheck.spec == null) {
                emit(ToolProgress.Failed(fmtCheck.errorMessage ?: "Unsupported format"))
                return@flow
            }
            val outSpec = fmtCheck.spec
            val bitrate = input.getString("bitrate", "128k")
            val probe = validation.probes.first()

            emitAll(
                mediaJob.runCommand(
                    context = ctx.appContext,
                    storage = ctx.storage,
                    inputUris = input.uris,
                    toolTag = "AUD_COMP",
                    outputExt = outSpec.extension,
                    outputMime = outSpec.mimeType,
                    expectedDurationMs = probe.durationMs,
                    isEnglish = ctx.isEnglish
                ) { inputs, output ->
                    buildList {
                        add("-y")
                        add("-i"); add(inputs.first().absolutePath)
                        add("-vn")
                        addAll(AudioToolMath.buildCodecOutputArgs(outSpec, bitrateOverride = bitrate))
                        add(output.absolutePath)
                    }
                }
            )
        }.flowOn(Dispatchers.IO)
    },

    // AUD-12: Loudness Normalizer (-16 LUFS, 2-pass loudnorm)
    object : Tool {
        override val id = "AUD-12"
        override val group = ToolGroup.AUDIO
        override val engineLabel = "FF"
        override val icon = Icons.Filled.GraphicEq
        override val inputSpec = InputSpec.SingleAudio
        override val params = listOf(
            ToolParam.DropdownParam(
                "targetLufs",
                "Target Loudness",
                "Target Loudness",
                listOf("-16 LUFS (Podcast/Musik)", "-14 LUFS (Streaming)", "-23 LUFS (Broadcast)"),
                "-16 LUFS (Podcast/Musik)"
            )
        )
        override fun title(isEnglish: Boolean) = if (isEnglish) "Loudness Normalizer (-16 LUFS)" else "Normalisasi Loudness (-16 LUFS)"
        override fun description(isEnglish: Boolean) = if (isEnglish) {
            "Two-pass EBU R128 loudness normalization to -16 LUFS standard."
        } else {
            "Standarkan kenyaringan audio ke -16 LUFS memakai loudnorm 2-pass."
        }

        override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
            val validation = AudioToolMath.validateAudioInputs(ctx.appContext, ctx.storage, input.uris, 1, ctx.isEnglish)
            if (!validation.valid) {
                emit(ToolProgress.Failed(validation.errorMessage ?: "Invalid audio"))
                return@flow
            }
            val probe = validation.probes.first()
            val targetStr = input.getString("targetLufs", "-16 LUFS (Podcast/Musik)")
            val targetLufs = when {
                targetStr.contains("-14") -> -14.0
                targetStr.contains("-23") -> -23.0
                else -> -16.0
            }
            val srcExt = MediaUriUtils.resolveExtension(ctx.appContext, input.uris.first(), "mp3")
            val outSpec = AudioToolMath.resolveDefaultOutputSpec(srcExt)

            val jobDir = ctx.storage.createJobTempDir("aud_loud_${UUID.randomUUID().toString().take(8)}")
            val outputFile = MediaUriUtils.createOutputFile(ctx.storage, "AUD_NORM", outSpec.extension)

            try {
                emit(ToolProgress.Queued)
                emit(
                    ToolProgress.Running(
                        5,
                        if (ctx.isEnglish) "Pass 1: measuring integrated LUFS…" else "Tahap 1: mengukur kenyaringan (LUFS)…"
                    )
                )
                val localIn = MediaUriUtils.copyUriToSafeTemp(
                    context = ctx.appContext,
                    storage = ctx.storage,
                    uri = input.uris.first(),
                    jobDir = jobDir,
                    index = 0,
                    fallbackExt = srcExt
                )

                val pass1Logs = StringBuilder()
                val pass1Result = mediaJob.runStep(
                    arguments = listOf(
                        "-y",
                        "-i", localIn.absolutePath,
                        "-af", AudioToolMath.buildLoudnormPass1Filter(targetLufs),
                        "-f", "null",
                        "-"
                    ),
                    expectedDurationMs = probe.durationMs,
                    isEnglish = ctx.isEnglish
                ) { pct ->
                    // Progress notification best-effort in logs
                }
                pass1Result.onSuccess { pass1Logs.append(it) }
                // Also run full stats capture if pass1 logs didn't include JSON tail
                val stats = AudioToolMath.parseLoudnormStats(pass1Logs.toString())

                emit(
                    ToolProgress.Running(
                        50,
                        if (ctx.isEnglish) {
                            if (stats != null) "Pass 2: normalizing (${stats.inputI} → $targetLufs LUFS)…"
                            else "Pass 2: normalizing to $targetLufs LUFS…"
                        } else {
                            if (stats != null) "Tahap 2: normalisasi (${stats.inputI} → $targetLufs LUFS)…"
                            else "Tahap 2: normalisasi ke $targetLufs LUFS…"
                        }
                    )
                )

                val pass2Filter = AudioToolMath.buildLoudnormPass2Filter(stats, targetLufs)
                val pass2Args = buildList {
                    add("-y")
                    add("-i"); add(localIn.absolutePath)
                    add("-af"); add(pass2Filter)
                    add("-vn")
                    addAll(AudioToolMath.buildCodecOutputArgs(outSpec))
                    add(outputFile.absolutePath)
                }

                val pass2Result = mediaJob.runStep(
                    arguments = pass2Args,
                    expectedDurationMs = probe.durationMs,
                    isEnglish = ctx.isEnglish
                )
                if (pass2Result.isFailure || !outputFile.exists() || outputFile.length() == 0L) {
                    MediaUriUtils.cleanupQuietly(outputFile)
                    val msg = pass2Result.exceptionOrNull()?.localizedMessage
                        ?: if (ctx.isEnglish) "Loudness normalization failed." else "Normalisasi loudness gagal."
                    emit(ToolProgress.Failed(msg))
                    return@flow
                }

                val outProbe = MediaProbe.probeFile(outputFile)
                val modeLabel = if (stats != null) "2-pass (${stats.inputI} → $targetLufs LUFS)" else "1-pass ($targetLufs LUFS)"
                val sizeText = ctx.storage.formatBytes(outputFile.length())
                emit(
                    ToolProgress.Done(
                        ToolResult(
                            outputs = listOf(ToolOutputFile(outputFile, outSpec.mimeType, outputFile.name)),
                            textOutput = "${outProbe.formatSummary(ctx.isEnglish)}\nMode: $modeLabel",
                            summary = if (ctx.isEnglish) {
                                "Normalized $modeLabel • ${outputFile.name} ($sizeText)"
                            } else {
                                "Normalisasi $modeLabel • ${outputFile.name} ($sizeText)"
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

    // AUD-13: Audio Converter (MP3 / WAV / OGG / FLAC / M4A / OPUS)
    object : Tool {
        override val id = "AUD-13"
        override val group = ToolGroup.AUDIO
        override val engineLabel = "FF"
        override val icon = Icons.Filled.AudioFile
        override val inputSpec = InputSpec.SingleAudio
        override val params = listOf(
            ToolParam.DropdownParam("format", "Format Tujuan", "Target Format", listOf("MP3", "WAV", "OGG", "FLAC", "M4A (AAC)", "OPUS"), "MP3"),
            ToolParam.DropdownParam("bitrate", "Kualitas / Bitrate", "Quality / Bitrate", listOf("128k", "192k", "256k", "320k"), "192k")
        )
        override fun title(isEnglish: Boolean) = if (isEnglish) "Audio Converter" else "Konverter Audio"
        override fun description(isEnglish: Boolean) = if (isEnglish) {
            "Convert audio across MP3, WAV, OGG, FLAC, M4A, and OPUS with encoder validation."
        } else {
            "Konversi audio antar MP3, WAV, OGG, FLAC, M4A, dan OPUS dengan validasi encoder."
        }

        override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
            val validation = AudioToolMath.validateAudioInputs(ctx.appContext, ctx.storage, input.uris, 1, ctx.isEnglish)
            if (!validation.valid) {
                emit(ToolProgress.Failed(validation.errorMessage ?: "Invalid audio"))
                return@flow
            }
            val targetFormat = input.getString("format", "MP3")
            val formatCheck = AudioToolMath.validateOutputFormatAndRate(
                formatLabel = targetFormat,
                sampleRateHz = null,
                capabilities = encoderCapabilities,
                isEnglish = ctx.isEnglish
            )
            if (!formatCheck.valid || formatCheck.spec == null) {
                emit(ToolProgress.Failed(formatCheck.errorMessage ?: "Unsupported format"))
                return@flow
            }
            val outSpec = formatCheck.spec
            val bitrate = input.getString("bitrate", "192k")
            val probe = validation.probes.first()

            emitAll(
                mediaJob.runCommand(
                    context = ctx.appContext,
                    storage = ctx.storage,
                    inputUris = input.uris,
                    toolTag = "AUD_CONV",
                    outputExt = outSpec.extension,
                    outputMime = outSpec.mimeType,
                    expectedDurationMs = probe.durationMs,
                    isEnglish = ctx.isEnglish
                ) { inputs, output ->
                    buildList {
                        add("-y")
                        add("-i"); add(inputs.first().absolutePath)
                        add("-vn")
                        addAll(AudioToolMath.buildCodecOutputArgs(outSpec, bitrateOverride = bitrate))
                        add(output.absolutePath)
                    }
                }
            )
        }.flowOn(Dispatchers.IO)
    }
)
