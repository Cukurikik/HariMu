package com.harimu.app.feature.tools

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.NoiseAware
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.SurroundSound
import androidx.compose.material.icons.filled.WaterDrop
import com.harimu.app.core.media.AudioBatch2Math
import com.harimu.app.core.media.AudioToolMath
import com.harimu.app.core.media.MediaJob
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

fun audioForgeBatch2CoreTools(
    mediaJob: MediaJob = MediaJob()
): List<Tool> = listOf(
    // AUD-01: Mastering Hub (Compressor + Limiter + Studio Presets)
    object : Tool {
        override val id = "AUD-01"
        override val group = ToolGroup.AUDIO
        override val engineLabel = "FF"
        override val icon = Icons.Filled.GraphicEq
        override val inputSpec = InputSpec.SingleAudio
        override val params = listOf(
            ToolParam.DropdownParam(
                "preset",
                "Preset Studio",
                "Studio Preset",
                listOf("Studio Warm", "Vocal Broadcast", "Club Punch", "Acoustic Clear", "Custom"),
                "Studio Warm"
            ),
            ToolParam.SliderParam("thresholdDb", "Threshold Kompresor (dB)", "Compressor Threshold (dB)", -36f, -6f, -18f, 1f, "dB"),
            ToolParam.SliderParam("ratio", "Rasio Kompresor", "Compressor Ratio", 1.5f, 8.0f, 3.0f, 0.5f, ":1"),
            ToolParam.SliderParam("ceilingDb", "Batas Limiter (dBTP)", "Limiter Ceiling (dBTP)", -3.0f, -0.1f, -1.0f, 0.1f, "dB"),
            ToolParam.SliderParam("makeupDb", "Drive / Makeup Gain (dB)", "Drive / Makeup Gain (dB)", 0f, 10f, 3.0f, 0.5f, "dB"),
            ToolParam.ToggleParam("warmthEnhance", "Aktifkan EQ Mastering & Lebar Stereo", "Apply Mastering EQ & Stereo Width", true)
        )

        override fun title(isEnglish: Boolean) = if (isEnglish) "Mastering Hub" else "Mastering Hub"
        override fun description(isEnglish: Boolean) = if (isEnglish) {
            "Broadcast-ready mastering chain with studio EQ, compressor, stereo width, and peak limiter."
        } else {
            "Rantai mastering audio lengkap: EQ studio, kompresor, pelebar stereo, dan limiter puncak."
        }

        override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
            val validation = AudioToolMath.validateAudioInputs(ctx.appContext, ctx.storage, input.uris, 1, ctx.isEnglish)
            if (!validation.valid) {
                emit(ToolProgress.Failed(validation.errorMessage ?: "Invalid audio"))
                return@flow
            }
            val probe = validation.probes.first()
            val preset = input.getString("preset", "Studio Warm")
            val presetSpec = AudioBatch2Math.resolveMasteringPreset(preset)
            val usePresetDefaults = preset != "Custom" && !input.params.containsKey("thresholdDb")
            val thresholdDb = if (usePresetDefaults) presetSpec.thresholdDb else input.getFloat("thresholdDb", presetSpec.thresholdDb)
            val ratio = if (usePresetDefaults) presetSpec.ratio else input.getFloat("ratio", presetSpec.ratio)
            val ceilingDb = if (usePresetDefaults) presetSpec.ceilingDb else input.getFloat("ceilingDb", presetSpec.ceilingDb)
            val makeupDb = if (usePresetDefaults) presetSpec.makeupDb else input.getFloat("makeupDb", presetSpec.makeupDb)
            val warmth = input.getBoolean("warmthEnhance", true)

            val filter = AudioBatch2Math.buildMasteringFilter(
                preset = preset,
                ratio = ratio,
                thresholdDb = thresholdDb,
                ceilingDb = ceilingDb,
                makeupDb = makeupDb,
                warmthEnhance = warmth
            )
            val srcExt = MediaUriUtils.resolveExtension(ctx.appContext, input.uris.first(), "mp3")
            val outSpec = AudioToolMath.resolveDefaultOutputSpec(srcExt)

            emitAll(
                mediaJob.runCommand(
                    context = ctx.appContext,
                    storage = ctx.storage,
                    inputUris = input.uris,
                    toolTag = "AUD_MASTER",
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

    // AUD-03: Noise Reduction (FFmpeg afftdn FFT denoiser + strength slider; arnndn noted when .rnnn weights provided)
    object : Tool {
        override val id = "AUD-03"
        override val group = ToolGroup.AUDIO
        override val engineLabel = "FF"
        override val icon = Icons.Filled.NoiseAware
        override val inputSpec = InputSpec.SingleAudio
        override val params = listOf(
            ToolParam.DropdownParam(
                "mode",
                "Mode Reduksi",
                "Reduction Mode",
                listOf("Seimbang (afftdn)", "Suara Jernih (High-Pass + afftdn)", "Agresif (Noise Berat)"),
                "Seimbang (afftdn)"
            ),
            ToolParam.SliderParam("strength", "Kekuatan Reduksi (%)", "Reduction Strength (%)", 10f, 100f, 55f, 5f, "%"),
            ToolParam.SliderParam("noiseFloorDb", "Lantai Derau (dB)", "Noise Floor (dB)", -80f, -20f, -45f, 1f, "dB")
        )

        override fun title(isEnglish: Boolean) = if (isEnglish) "Noise Reduction" else "Reduksi Derau Audio"
        override fun description(isEnglish: Boolean) = if (isEnglish) {
            "Attenuate background hiss, hum, and room noise using FFmpeg FFT denoising (afftdn)."
        } else {
            "Kurangi hiss, dengung, dan derau latar pada rekaman dengan filter FFT afftdn."
        }

        override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
            val validation = AudioToolMath.validateAudioInputs(ctx.appContext, ctx.storage, input.uris, 1, ctx.isEnglish)
            if (!validation.valid) {
                emit(ToolProgress.Failed(validation.errorMessage ?: "Invalid audio"))
                return@flow
            }
            val probe = validation.probes.first()
            val mode = input.getString("mode", "Seimbang (afftdn)")
            val strength = input.getFloat("strength", 55f)
            val floorDb = input.getFloat("noiseFloorDb", -45f)
            val filter = AudioBatch2Math.buildNoiseReductionFilter(strength, floorDb, mode)

            val srcExt = MediaUriUtils.resolveExtension(ctx.appContext, input.uris.first(), "mp3")
            val outSpec = AudioToolMath.resolveDefaultOutputSpec(srcExt)

            emitAll(
                mediaJob.runCommand(
                    context = ctx.appContext,
                    storage = ctx.storage,
                    inputUris = input.uris,
                    toolTag = "AUD_DENOISE",
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

    // AUD-04: Vocal Remover (Fast Center-Channel Cancellation; AI Demucs mode deferred to P11)
    object : Tool {
        override val id = "AUD-04"
        override val group = ToolGroup.AUDIO
        override val engineLabel = "FF/ML"
        override val icon = Icons.Filled.RecordVoiceOver
        override val inputSpec = InputSpec.SingleAudio
        override val params = listOf(
            ToolParam.DropdownParam(
                "mode",
                "Mode Pemisahan",
                "Separation Mode",
                listOf(
                    "Mode Cepat — Kanselasi Tengah (Instrumental)",
                    "Mode Cepat — Isolasi Vokal (Acapella)",
                    "Mode DSP — Mid/Side Lokal (vokal + instrumen)"
                ),
                "Mode Cepat — Kanselasi Tengah (Instrumental)"
            ),
            ToolParam.ToggleParam("preserveBass", "Pertahankan Bass Tengah (< 120 Hz)", "Preserve Center Bass (< 120 Hz)", true),
            ToolParam.SliderParam("strength", "Kedalaman Kanselasi (%)", "Cancellation Depth (%)", 50f, 100f, 92f, 2f, "%")
        )

        override fun title(isEnglish: Boolean) = if (isEnglish) "Vocal Remover" else "Penghapus Vokal"
        override fun description(isEnglish: Boolean) = if (isEnglish) {
            "Remove or isolate center-panned vocals with bass preservation, or export mid/side WAV stems."
        } else {
            "Hapus atau isolasi vokal kanal tengah dengan pelindung bass, atau ekspor stem mid/side WAV."
        }

        override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
            val validation = AudioToolMath.validateAudioInputs(ctx.appContext, ctx.storage, input.uris, 1, ctx.isEnglish)
            if (!validation.valid) {
                emit(ToolProgress.Failed(validation.errorMessage ?: "Invalid audio"))
                return@flow
            }
            val probe = validation.probes.first()
            val mode = input.getString("mode", "Mode Cepat — Kanselasi Tengah (Instrumental)")
            if (mode.startsWith("Mode DSP")) {
                val safety = AudioToolMath.assessInMemoryDspSafety(probe.durationMs, ctx.isEnglish)
                if (!safety.allowed) {
                    emit(ToolProgress.Failed(safety.blockingError ?: "Audio too long for RAM DSP"))
                    return@flow
                }
                emit(ToolProgress.Running(20, if (ctx.isEnglish) "Decoding PCM for mid/side split…" else "Mendekode PCM untuk pemisahan mid/side…"))
                val dir = ctx.storage.createJobTempDir("aud04_${System.currentTimeMillis()}")
                var zipFile: java.io.File? = null
                try {
                    val pcm = kotlinx.coroutines.withContext(Dispatchers.IO) {
                        com.harimu.app.core.engines.AudioEngine.decodeToPcm(ctx.appContext, input.uris.first())
                    }
                    if (pcm.samples.isEmpty()) {
                        emit(ToolProgress.Failed(if (ctx.isEnglish) "Audio is empty or could not be decoded." else "Audio kosong atau tidak dapat didekode."))
                        return@flow
                    }
                    emit(ToolProgress.Running(60, if (ctx.isEnglish) "Splitting mid and side channels…" else "Memisahkan kanal tengah dan sisi…"))
                    val stems = kotlinx.coroutines.withContext(Dispatchers.Default) {
                        com.harimu.app.core.ai.AiLocalEngines.splitStems(pcm, dir)
                    }
                    val createdZip = ctx.storage.createOutputFile("AUD_DSP_STEMS", "zip")
                    zipFile = createdZip
                    java.util.zip.ZipOutputStream(java.io.FileOutputStream(createdZip)).use { zip ->
                        listOf(stems.vocals, stems.instrumental).forEach { file ->
                            zip.putNextEntry(java.util.zip.ZipEntry(file.name))
                            file.inputStream().use { it.copyTo(zip) }
                            zip.closeEntry()
                        }
                    }
                    emit(
                        ToolProgress.Done(
                            ToolResult(
                                outputs = listOf(ToolOutputFile(createdZip, "application/zip", createdZip.name)),
                                summary = if (ctx.isEnglish) {
                                    "2 WAV tracks (vocals + instrumental) • ${ctx.storage.formatBytes(createdZip.length())}\n" +
                                        "Note: local mid/side DSP, not a Demucs model; best for center-panned vocals."
                                } else {
                                    "2 jalur WAV (vokal + instrumen) • ${ctx.storage.formatBytes(createdZip.length())}\n" +
                                        "Catatan: mid/side DSP lokal, bukan model Demucs; hasil terbaik untuk vokal center-panned."
                                }
                            )
                        )
                    )
                } catch (ce: kotlinx.coroutines.CancellationException) {
                    zipFile?.delete()
                    throw ce
                } finally {
                    dir.deleteRecursively()
                }
                return@flow
            }
            val err = AudioBatch2Math.validateVocalRemoverInput(probe, mode, ctx.isEnglish)
            if (err != null) {
                emit(ToolProgress.Failed(err))
                return@flow
            }
            val preserveBass = input.getBoolean("preserveBass", true)
            val strength = input.getFloat("strength", 92f)
            val fc = AudioBatch2Math.buildVocalRemoverFilterComplex(mode, preserveBass, strength)
            val srcExt = MediaUriUtils.resolveExtension(ctx.appContext, input.uris.first(), "mp3")
            val outSpec = AudioToolMath.resolveDefaultOutputSpec(srcExt)

            emitAll(
                mediaJob.runCommand(
                    context = ctx.appContext,
                    storage = ctx.storage,
                    inputUris = input.uris,
                    toolTag = "AUD_VOCAL",
                    outputExt = outSpec.extension,
                    outputMime = outSpec.mimeType,
                    expectedDurationMs = probe.durationMs,
                    isEnglish = ctx.isEnglish
                ) { inputs, output ->
                    buildList {
                        add("-y")
                        add("-i"); add(inputs.first().absolutePath)
                        add("-filter_complex"); add(fc)
                        add("-map"); add("[aout]")
                        addAll(AudioToolMath.buildCodecOutputArgs(outSpec))
                        add(output.absolutePath)
                    }
                }
            )
        }.flowOn(Dispatchers.IO)
    },

    // AUD-05: 8D Audio Engine (360-degree spatial azimuth rotation + speed control)
    object : Tool {
        override val id = "AUD-05"
        override val group = ToolGroup.AUDIO
        override val engineLabel = "FF"
        override val icon = Icons.Filled.SurroundSound
        override val inputSpec = InputSpec.SingleAudio
        override val params = listOf(
            ToolParam.SliderParam("rotationHz", "Kecepatan Putar (Hz)", "Rotation Speed (Hz)", 0.05f, 1.0f, 0.15f, 0.05f, "Hz"),
            ToolParam.SliderParam("depthPct", "Kedalaman Rotasi 360° (%)", "360° Spatial Depth (%)", 25f, 100f, 85f, 5f, "%"),
            ToolParam.ToggleParam("roomReverb", "Tambahkan Ruang Ambience 3D", "Add 3D Room Ambience", true)
        )

        override fun title(isEnglish: Boolean) = if (isEnglish) "8D Audio Engine" else "8D Audio Engine"
        override fun description(isEnglish: Boolean) = if (isEnglish) {
            "Create immersive 360° rotating spatial audio tuned for headphones."
        } else {
            "Buat efek audio spasial berputar 360° yang imersif untuk headphone."
        }

        override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
            val validation = AudioToolMath.validateAudioInputs(ctx.appContext, ctx.storage, input.uris, 1, ctx.isEnglish)
            if (!validation.valid) {
                emit(ToolProgress.Failed(validation.errorMessage ?: "Invalid audio"))
                return@flow
            }
            val probe = validation.probes.first()
            val filter = AudioBatch2Math.build8dAudioFilter(
                rotationHz = input.getFloat("rotationHz", 0.15f),
                depthPct = input.getFloat("depthPct", 85f),
                roomReverb = input.getBoolean("roomReverb", true)
            )
            val srcExt = MediaUriUtils.resolveExtension(ctx.appContext, input.uris.first(), "mp3")
            val outSpec = AudioToolMath.resolveDefaultOutputSpec(srcExt)

            emitAll(
                mediaJob.runCommand(
                    context = ctx.appContext,
                    storage = ctx.storage,
                    inputUris = input.uris,
                    toolTag = "AUD_8D",
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

    // AUD-19: Audio Watermark (periodic chime or custom audio marker)
    object : Tool {
        override val id = "AUD-19"
        override val group = ToolGroup.AUDIO
        override val engineLabel = "FF"
        override val icon = Icons.Filled.WaterDrop
        override val inputSpec = InputSpec.AudioAndWatermarkAudio
        override val params = listOf(
            ToolParam.SliderParam("intervalSec", "Interval Penanda (detik)", "Marker Interval (sec)", 3f, 60f, 10f, 1f, "s"),
            ToolParam.SliderParam("volumePct", "Volume Penanda (%)", "Marker Volume (%)", 10f, 100f, 35f, 5f, "%"),
            ToolParam.DropdownParam(
                "toneHz",
                "Nada Penanda Bawaan (jika tanpa file)",
                "Built-in Marker Tone (if no file)",
                listOf("880 Hz (Chime)", "1000 Hz (Beep)", "523 Hz (Soft C5)"),
                "880 Hz (Chime)"
            )
        )

        override fun title(isEnglish: Boolean) = if (isEnglish) "Audio Watermark" else "Watermark Audio"
        override fun description(isEnglish: Boolean) = if (isEnglish) {
            "Embed a periodic tone or custom watermark clip at fixed intervals to protect previews."
        } else {
            "Sematkan suara penanda berkala atau klip watermark kustom pada interval tertentu."
        }

        override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
            val urisToValidate = listOfNotNull(input.uris.firstOrNull(), input.secondaryUri)
            val validation = AudioToolMath.validateAudioInputs(ctx.appContext, ctx.storage, urisToValidate, 1, ctx.isEnglish)
            if (!validation.valid) {
                emit(ToolProgress.Failed(validation.errorMessage ?: "Invalid audio"))
                return@flow
            }
            val mainProbe = validation.probes.first()
            val intervalSec = input.getFloat("intervalSec", 10f)
            val volumePct = input.getFloat("volumePct", 35f)
            val hasCustomMarker = input.secondaryUri != null && urisToValidate.size >= 2
            val fc = if (hasCustomMarker) {
                AudioBatch2Math.buildCustomWatermarkFilterComplex(intervalSec, volumePct, mainProbe.durationMs)
            } else {
                val toneHz = AudioBatch2Math.parseToneFrequencyHz(input.getString("toneHz", "880 Hz (Chime)"))
                AudioBatch2Math.buildBuiltInWatermarkFilterComplex(intervalSec, volumePct, toneHz, mainProbe.durationMs)
            }

            val srcExt = MediaUriUtils.resolveExtension(ctx.appContext, input.uris.first(), "mp3")
            val outSpec = AudioToolMath.resolveDefaultOutputSpec(srcExt)

            emitAll(
                mediaJob.runCommand(
                    context = ctx.appContext,
                    storage = ctx.storage,
                    inputUris = urisToValidate,
                    toolTag = "AUD_WM",
                    outputExt = outSpec.extension,
                    outputMime = outSpec.mimeType,
                    expectedDurationMs = mainProbe.durationMs,
                    isEnglish = ctx.isEnglish
                ) { inputs, output ->
                    buildList {
                        add("-y")
                        inputs.forEach { file ->
                            add("-i"); add(file.absolutePath)
                        }
                        add("-filter_complex"); add(fc)
                        add("-map"); add("[aout]")
                        addAll(AudioToolMath.buildCodecOutputArgs(outSpec))
                        add(output.absolutePath)
                    }
                }
            )
        }.flowOn(Dispatchers.IO)
    }
)
