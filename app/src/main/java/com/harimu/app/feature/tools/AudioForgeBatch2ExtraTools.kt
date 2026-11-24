package com.harimu.app.feature.tools

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.SurroundSound
import com.harimu.app.core.media.AudioBatch2Math
import com.harimu.app.core.media.AudioToolMath
import com.harimu.app.core.media.MediaJob
import com.harimu.app.core.media.MediaUriUtils
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
import kotlin.math.roundToInt

fun audioForgeBatch2ExtraTools(
    mediaJob: MediaJob = MediaJob()
): List<Tool> = listOf(
    // AUD-22: Audio Enhancer
    object : Tool {
        override val id = "AUD-22"
        override val group = ToolGroup.AUDIO
        override val engineLabel = "FF"
        override val icon = Icons.Filled.AutoFixHigh
        override val inputSpec = InputSpec.SingleAudio
        override val params = listOf(
            ToolParam.DropdownParam(
                "profile",
                "Profil Peningkatan",
                "Enhancement Profile",
                listOf("Vokal & Podcast", "Musik Seimbang", "Bass & Kejernihan", "Rekaman Jauh"),
                "Vokal & Podcast"
            ),
            ToolParam.SliderParam("clarity", "Kejernihan / Kehadiran (%)", "Clarity / Presence (%)", 0f, 100f, 60f, 5f, "%"),
            ToolParam.SliderParam("warmth", "Kehangatan Bass (%)", "Bass Warmth (%)", 0f, 100f, 40f, 5f, "%"),
            ToolParam.ToggleParam("autoLevel", "Ratakan Dinamika Otomatis", "Auto-Level Dynamics", true)
        )

        override fun title(isEnglish: Boolean) = if (isEnglish) "Audio Enhancer" else "Audio Enhancer"
        override fun description(isEnglish: Boolean) = if (isEnglish) {
            "Boost vocal intelligibility, spectral presence, transient crispness, and dynamic balance."
        } else {
            "Tingkatkan kejernihan vokal, detail frekuensi tinggi, ketajaman, dan keseimbangan dinamika."
        }

        override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
            val validation = AudioToolMath.validateAudioInputs(ctx.appContext, ctx.storage, input.uris, 1, ctx.isEnglish)
            if (!validation.valid) {
                emit(ToolProgress.Failed(validation.errorMessage ?: "Invalid audio"))
                return@flow
            }
            val probe = validation.probes.first()
            val filter = AudioBatch2Math.buildEnhancerFilter(
                profile = input.getString("profile", "Vokal & Podcast"),
                clarityPct = input.getFloat("clarity", 60f),
                warmthPct = input.getFloat("warmth", 40f),
                autoLevel = input.getBoolean("autoLevel", true)
            )
            val srcExt = MediaUriUtils.resolveExtension(ctx.appContext, input.uris.first(), "mp3")
            val outSpec = AudioToolMath.resolveDefaultOutputSpec(srcExt)

            emitAll(
                mediaJob.runCommand(
                    context = ctx.appContext,
                    storage = ctx.storage,
                    inputUris = input.uris,
                    toolTag = "AUD_ENHANCE",
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

    // AUD-24: Add Echo (FFmpeg multi-tap aecho streaming pipeline)
    object : Tool {
        override val id = "AUD-24"
        override val group = ToolGroup.AUDIO
        override val engineLabel = "FF"
        override val icon = Icons.Filled.GraphicEq
        override val inputSpec = InputSpec.SingleAudio
        override val params = listOf(
            ToolParam.SliderParam("delayMs", "Jeda Gema (ms)", "Echo Delay (ms)", 80f, 1000f, 280f, 20f, "ms"),
            ToolParam.SliderParam("decay", "Peluruhan Gema", "Echo Decay", 0.15f, 0.75f, 0.45f, 0.05f, ""),
            ToolParam.DropdownParam(
                "taps",
                "Jumlah Pantulan",
                "Echo Reflections",
                listOf("Tunggal (1 Pantulan)", "Ganda (2 Pantulan)", "Stadion (3 Pantulan)"),
                "Ganda (2 Pantulan)"
            ),
            ToolParam.SliderParam("wetMix", "Level Keluaran Basah", "Wet Output Level", 0.3f, 1.0f, 0.85f, 0.05f, "")
        )

        override fun title(isEnglish: Boolean) = if (isEnglish) "Add Echo Effect" else "Tambah Efek Gema"
        override fun description(isEnglish: Boolean) = if (isEnglish) {
            "Apply single, dual, or stadium multi-tap feedback delay echo via FFmpeg."
        } else {
            "Tambahkan efek gema delay multi-tap (tunggal/ganda/stadion) dengan streaming FFmpeg."
        }

        override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
            val validation = AudioToolMath.validateAudioInputs(ctx.appContext, ctx.storage, input.uris, 1, ctx.isEnglish)
            if (!validation.valid) {
                emit(ToolProgress.Failed(validation.errorMessage ?: "Invalid audio"))
                return@flow
            }
            val probe = validation.probes.first()
            val filter = AudioBatch2Math.buildEchoFilter(
                delayMs = input.getFloat("delayMs", 280f).roundToInt(),
                decay = input.getFloat("decay", 0.45f),
                tapsMode = input.getString("taps", "Ganda (2 Pantulan)"),
                wetMix = input.getFloat("wetMix", 0.85f)
            )
            val srcExt = MediaUriUtils.resolveExtension(ctx.appContext, input.uris.first(), "mp3")
            val outSpec = AudioToolMath.resolveDefaultOutputSpec(srcExt)

            emitAll(
                mediaJob.runCommand(
                    context = ctx.appContext,
                    storage = ctx.storage,
                    inputUris = input.uris,
                    toolTag = "AUD_ECHO",
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

    // AUD-25: Add Reverb (Diffused acoustic room simulation with early/late reflections + HF damping)
    object : Tool {
        override val id = "AUD-25"
        override val group = ToolGroup.AUDIO
        override val engineLabel = "FF"
        override val icon = Icons.Filled.SurroundSound
        override val inputSpec = InputSpec.SingleAudio
        override val params = listOf(
            ToolParam.DropdownParam(
                "roomPreset",
                "Karakter Ruang",
                "Room Character",
                listOf("Ruang Kecil (Studio)", "Aula Konser (Hall)", "Katedral (Luas)", "Plate Klasik"),
                "Aula Konser (Hall)"
            ),
            ToolParam.SliderParam("roomSizePct", "Ukuran Ruang (%)", "Room Size (%)", 20f, 100f, 60f, 5f, "%"),
            ToolParam.SliderParam("dampingPct", "Redaman Frekuensi Tinggi (%)", "High-Freq Damping (%)", 10f, 90f, 40f, 5f, "%"),
            ToolParam.SliderParam("wetPct", "Campuran Gaung (%)", "Reverb Wet Mix (%)", 15f, 85f, 45f, 5f, "%")
        )

        override fun title(isEnglish: Boolean) = if (isEnglish) "Add Reverb" else "Tambah Efek Reverb"
        override fun description(isEnglish: Boolean) = if (isEnglish) {
            "Simulate studio, concert hall, plate, or cathedral acoustic reverb with high-frequency damping."
        } else {
            "Tambahkan gaung akustik studio, aula, plate, atau katedral dengan kontrol redaman tinggi."
        }

        override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
            val validation = AudioToolMath.validateAudioInputs(ctx.appContext, ctx.storage, input.uris, 1, ctx.isEnglish)
            if (!validation.valid) {
                emit(ToolProgress.Failed(validation.errorMessage ?: "Invalid audio"))
                return@flow
            }
            val probe = validation.probes.first()
            val filter = AudioBatch2Math.buildReverbFilter(
                roomPreset = input.getString("roomPreset", "Aula Konser (Hall)"),
                roomSizePct = input.getFloat("roomSizePct", 60f),
                dampingPct = input.getFloat("dampingPct", 40f),
                wetPct = input.getFloat("wetPct", 45f)
            )
            val srcExt = MediaUriUtils.resolveExtension(ctx.appContext, input.uris.first(), "mp3")
            val outSpec = AudioToolMath.resolveDefaultOutputSpec(srcExt)

            emitAll(
                mediaJob.runCommand(
                    context = ctx.appContext,
                    storage = ctx.storage,
                    inputUris = input.uris,
                    toolTag = "AUD_REVERB",
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

    // AUD-28: Audio Visualizer (Render animated waveform/spectrum/CQT/vectorscope to MP4 video)
    object : Tool {
        override val id = "AUD-28"
        override val group = ToolGroup.AUDIO
        override val engineLabel = "FF"
        override val icon = Icons.Filled.Movie
        override val inputSpec = InputSpec.SingleAudio
        override val params = listOf(
            ToolParam.DropdownParam(
                "style",
                "Gaya Visualizer",
                "Visualizer Style",
                listOf(
                    "Gelombang Garis (Line Wave)",
                    "Gelombang Batang (Bar Wave)",
                    "Spektrum Frekuensi (Spectrum)",
                    "CQT Musik (ShowCQT)",
                    "Vektor Stereo (Vectorscope)"
                ),
                "Gelombang Garis (Line Wave)"
            ),
            ToolParam.DropdownParam(
                "colorTheme",
                "Palet Warna",
                "Color Theme",
                listOf(
                    "Neon Sunset (Oranye & Cyan)",
                    "Emerald Pulse (Hijau & Toska)",
                    "Cyber Violet (Ungu & Pink)",
                    "Monokrom Putih"
                ),
                "Neon Sunset (Oranye & Cyan)"
            ),
            ToolParam.DropdownParam(
                "resolution",
                "Resolusi Video MP4",
                "MP4 Video Resolution",
                listOf("1280x720 (HD 16:9)", "1080x1080 (Square 1:1)", "720x1280 (Vertikal 9:16)"),
                "1280x720 (HD 16:9)"
            ),
            ToolParam.DropdownParam("fps", "Frame Rate", "Frame Rate", listOf("24 fps", "30 fps"), "30 fps")
        )

        override fun title(isEnglish: Boolean) = if (isEnglish) "Audio Visualizer (MP4)" else "Audio Visualizer (Video MP4)"
        override fun description(isEnglish: Boolean) = if (isEnglish) {
            "Render animated audio waveforms, spectrums, or CQT visuals directly to an MP4 video."
        } else {
            "Render animasi gelombang, spektrum, atau CQT musik dari audio menjadi video MP4."
        }

        override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
            val validation = AudioToolMath.validateAudioInputs(ctx.appContext, ctx.storage, input.uris, 1, ctx.isEnglish)
            if (!validation.valid) {
                emit(ToolProgress.Failed(validation.errorMessage ?: "Invalid audio"))
                return@flow
            }
            val probe = validation.probes.first()
            val spec = AudioBatch2Math.resolveVisualizerConfig(
                style = input.getString("style", "Gelombang Garis (Line Wave)"),
                colorTheme = input.getString("colorTheme", "Neon Sunset (Oranye & Cyan)"),
                resolution = input.getString("resolution", "1280x720 (HD 16:9)"),
                fpsLabel = input.getString("fps", "30 fps")
            )
            val fc = AudioBatch2Math.buildVisualizerFilterComplex(spec)

            emitAll(
                mediaJob.runCommand(
                    context = ctx.appContext,
                    storage = ctx.storage,
                    inputUris = input.uris,
                    toolTag = "AUD_VIS",
                    outputExt = "mp4",
                    outputMime = "video/mp4",
                    expectedDurationMs = probe.durationMs,
                    isEnglish = ctx.isEnglish
                ) { inputs, output ->
                    buildList {
                        add("-y")
                        add("-i"); add(inputs.first().absolutePath)
                        add("-filter_complex"); add(fc)
                        add("-map"); add("[vout]")
                        add("-map"); add("0:a")
                        add("-c:v"); add("mpeg4")
                        add("-q:v"); add("3")
                        add("-pix_fmt"); add("yuv420p")
                        add("-c:a"); add("aac")
                        add("-b:a"); add("192k")
                        add("-shortest")
                        add("-movflags"); add("+faststart")
                        add(output.absolutePath)
                    }
                }
            )
        }.flowOn(Dispatchers.IO)
    }
)
