package com.harimu.app.feature.tools

import androidx.compose.material.icons.Icons

private fun buildAtempoChain(speed: Float): String {
    val factors = mutableListOf<Float>()
    var remaining = speed
    while (remaining > 2.0f) { factors.add(2.0f); remaining /= 2.0f }
    while (remaining < 0.5f) { factors.add(0.5f); remaining /= 0.5f }
    factors.add(remaining.coerceIn(0.5f, 2.0f))
    return "atempo=" + factors.joinToString(",")
}
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Splitscreen
import androidx.compose.material.icons.filled.SurroundSound
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.WaterDrop
import com.harimu.app.core.engines.AudioEngine
import com.harimu.app.core.engines.PdfEngine
import com.harimu.app.core.media.MediaJob
import com.harimu.app.core.media.MediaProbe
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.runBlocking
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import com.harimu.app.core.toolapi.DedicatedScreenRoute
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

fun audioTools(): List<Tool> = listOf(
    DedicatedTool(
        id = "AUD-02",
        group = ToolGroup.AUDIO,
        engineLabel = "NAT",
        icon = Icons.Filled.Equalizer,
        dedicatedRoute = DedicatedScreenRoute.EQUALIZER,
        titleId = "Pro Equalizer 5-Band Live",
        titleEn = "Live 5-Band Equalizer",
        descId = "Putar audio dengan kontrol Equalizer 5-band Android real-time & preset.",
        descEn = "Play audio with real-time Android 5-band Equalizer & presets."
    ),
    object : Tool {
        override val id = "AUD-08"
        override val group = ToolGroup.AUDIO
        override val engineLabel = "PCM"
        override val icon = Icons.Filled.VolumeUp
        override val inputSpec = InputSpec.SingleAudio
        override val params = listOf(
            ToolParam.SliderParam("gain", "Penguatan Volume (%)", "Volume Gain (%)", 20f, 300f, 150f, 10f, "%")
        )
        override fun title(isEnglish: Boolean) = if (isEnglish) "Change Audio Volume" else "Ubah Volume Audio"
        override fun description(isEnglish: Boolean) = if (isEnglish) "Amplify or attenuate audio level cleanly to WAV." else "Naikkan atau turunkan gain audio dengan proteksi clipping."
        override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
            val uri = input.uris.firstOrNull() ?: error("Pilih file audio")
            emit(ToolProgress.Running(30, "Mendekode audio PCM..."))
            val pcm = AudioEngine.decodeToPcm(ctx.appContext, uri)
            emit(ToolProgress.Running(70, "Menerapkan penguatan volume..."))
            val out = AudioEngine.applyGain(pcm, input.getFloat("gain", 150f))
            val dest = ctx.storage.createOutputFile("AUD_VOL", "wav")
            AudioEngine.writeWav(out, dest)
            emit(ToolProgress.Done(ToolResult(listOf(ToolOutputFile(dest, "audio/wav", dest.name)), summary = "Volume ${input.getFloat("gain", 150f).toInt()}% (${ctx.storage.formatBytes(dest.length())})")))
        }.flowOn(Dispatchers.IO)
    },
    object : Tool {
        override val id = "AUD-09"
        override val group = ToolGroup.AUDIO
        override val engineLabel = "PCM"
        override val icon = Icons.Filled.ContentCut
        override val inputSpec = InputSpec.SingleAudio
        override val params = listOf(
            ToolParam.SliderParam("startSec", "Mulai (detik)", "Start (sec)", 0f, 120f, 0f, 1f, "s"),
            ToolParam.SliderParam("endSec", "Selesai (detik, 0=akhir)", "End (sec, 0=end)", 0f, 180f, 15f, 1f, "s")
        )
        override fun title(isEnglish: Boolean) = if (isEnglish) "Trim Audio" else "Potong Audio"
        override fun description(isEnglish: Boolean) = if (isEnglish) "Cut an audio segment precisely at sample level." else "Potong segmen audio secara presisi ke format WAV."
        override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
            val uri = input.uris.firstOrNull() ?: error("Pilih file audio")
            emit(ToolProgress.Running(35, "Memotong sampel audio..."))
            val pcm = AudioEngine.decodeToPcm(ctx.appContext, uri)
            val trimmed = AudioEngine.trimPcm(pcm, input.getFloat("startSec", 0f), input.getFloat("endSec", 15f))
            val dest = ctx.storage.createOutputFile("AUD_TRIM", "wav")
            AudioEngine.writeWav(trimmed, dest)
            emit(ToolProgress.Done(ToolResult(listOf(ToolOutputFile(dest, "audio/wav", dest.name)), summary = "Audio dipotong (${ctx.storage.formatBytes(dest.length())})")))
        }.flowOn(Dispatchers.IO)
    },
    object : Tool {
        override val id = "AUD-10"
        override val group = ToolGroup.AUDIO
        override val engineLabel = "PCM"
        override val icon = Icons.Filled.MergeType
        override val inputSpec = InputSpec.MultipleAudios
        override val params = listOf(
            ToolParam.DropdownParam("mode", "Mode Gabung", "Merge Mode", listOf("Berurutan (Concat)", "Campur (Mix)"), "Berurutan (Concat)")
        )
        override fun title(isEnglish: Boolean) = if (isEnglish) "Merge / Mix Audio" else "Gabung / Mix Audio"
        override fun description(isEnglish: Boolean) = if (isEnglish) "Join audio clips sequentially or mix them together." else "Sambungkan klip audio berurutan atau campur bersamaan."
        override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
            require(input.uris.size >= 2) { "Pilih minimal 2 file audio" }
            emit(ToolProgress.Running(40, "Memproses ${input.uris.size} track audio..."))
            val pcms = input.uris.map { AudioEngine.decodeToPcm(ctx.appContext, it) }
            val mix = input.getString("mode").contains("Mix")
            val merged = AudioEngine.mergeOrMix(pcms, mix)
            val dest = ctx.storage.createOutputFile("AUD_MERGE", "wav")
            AudioEngine.writeWav(merged, dest)
            emit(ToolProgress.Done(ToolResult(listOf(ToolOutputFile(dest, "audio/wav", dest.name)), summary = "${pcms.size} audio digabung (${ctx.storage.formatBytes(dest.length())})")))
        }.flowOn(Dispatchers.IO)
    },
    object : Tool {
        override val id = "AUD-15"
        override val group = ToolGroup.AUDIO
        override val engineLabel = "PCM"
        override val icon = Icons.Filled.SurroundSound
        override val inputSpec = InputSpec.SingleAudio
        override val params = listOf(
            ToolParam.DropdownParam("mode", "Operasi Kanal", "Channel Operation", listOf("Mono Mix", "Tukar L/R", "Hanya Kiri", "Hanya Kanan"), "Mono Mix")
        )
        override fun title(isEnglish: Boolean) = if (isEnglish) "Audio Channel Manipulator" else "Manipulasi Kanal Audio"
        override fun description(isEnglish: Boolean) = if (isEnglish) "Downmix to mono, swap L/R, or isolate channels." else "Ubah stereo ke mono, tukar L/R, atau isolasi satu kanal."
        override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
            val uri = input.uris.firstOrNull() ?: error("Pilih file audio")
            emit(ToolProgress.Running(45, "Memanipulasi kanal..."))
            val pcm = AudioEngine.decodeToPcm(ctx.appContext, uri)
            val out = AudioEngine.manipulateChannels(pcm, input.getString("mode", "Mono Mix"))
            val dest = ctx.storage.createOutputFile("AUD_CHAN", "wav")
            AudioEngine.writeWav(out, dest)
            emit(ToolProgress.Done(ToolResult(listOf(ToolOutputFile(dest, "audio/wav", dest.name)), summary = "Kanal: ${input.getString("mode", "Mono Mix")}")))
        }.flowOn(Dispatchers.IO)
    },
    object : Tool {
        override val id = "AUD-16"
        override val group = ToolGroup.AUDIO
        override val engineLabel = "DSP"
        override val icon = Icons.Filled.VolumeOff
        override val inputSpec = InputSpec.SingleAudio
        override val params = listOf(
            ToolParam.SliderParam("threshold", "Ambang Hening (%)", "Silence Threshold (%)", 1f, 20f, 3f, 1f, "%")
        )
        override fun title(isEnglish: Boolean) = if (isEnglish) "Audio Silence Remover" else "Hapus Bagian Hening"
        override fun description(isEnglish: Boolean) = if (isEnglish) "Detect and trim quiet RMS segments automatically." else "Pangkas segmen hening berdasarkan ambang energi RMS."
        override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
            val uri = input.uris.firstOrNull() ?: error("Pilih file audio")
            emit(ToolProgress.Running(45, "Menganalisis energi RMS..."))
            val pcm = AudioEngine.decodeToPcm(ctx.appContext, uri)
            val out = AudioEngine.removeSilence(pcm, input.getFloat("threshold", 3f).toInt())
            val dest = ctx.storage.createOutputFile("AUD_NOSILENCE", "wav")
            AudioEngine.writeWav(out, dest)
            emit(ToolProgress.Done(ToolResult(listOf(ToolOutputFile(dest, "audio/wav", dest.name)), summary = "Bagian hening dihapus (${ctx.storage.formatBytes(dest.length())})")))
        }.flowOn(Dispatchers.IO)
    },
    object : Tool {
        override val id = "AUD-17"
        override val group = ToolGroup.AUDIO
        override val engineLabel = "PCM"
        override val icon = Icons.Filled.History
        override val inputSpec = InputSpec.SingleAudio
        override val params = emptyList<ToolParam>()
        override fun title(isEnglish: Boolean) = if (isEnglish) "Reverse Audio" else "Putar Balik Audio"
        override fun description(isEnglish: Boolean) = if (isEnglish) "Reverse audio playback sample-by-sample." else "Balik arah pemutaran audio dari akhir ke awal."
        override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
            val uri = input.uris.firstOrNull() ?: error("Pilih file audio")
            emit(ToolProgress.Running(50, "Membalik sampel PCM..."))
            val pcm = AudioEngine.decodeToPcm(ctx.appContext, uri)
            val out = AudioEngine.reversePcm(pcm)
            val dest = ctx.storage.createOutputFile("AUD_REV", "wav")
            AudioEngine.writeWav(out, dest)
            emit(ToolProgress.Done(ToolResult(listOf(ToolOutputFile(dest, "audio/wav", dest.name)), summary = "Audio dibalik (${ctx.storage.formatBytes(dest.length())})")))
        }.flowOn(Dispatchers.IO)
    },
    object : Tool {
        override val id = "AUD-24"
        override val group = ToolGroup.AUDIO
        override val engineLabel = "DSP"
        override val icon = Icons.Filled.GraphicEq
        override val inputSpec = InputSpec.SingleAudio
        override val params = listOf(
            ToolParam.SliderParam("delayMs", "Jeda Gema (ms)", "Echo Delay (ms)", 80f, 800f, 260f, 20f, "ms"),
            ToolParam.SliderParam("decay", "Peluruhan Gema", "Echo Decay", 0.1f, 0.7f, 0.4f, 0.05f, "")
        )
        override fun title(isEnglish: Boolean) = if (isEnglish) "Add Echo Effect" else "Tambah Efek Gema"
        override fun description(isEnglish: Boolean) = if (isEnglish) "Apply multi-tap feedback delay echo filter." else "Tambahkan efek gema delay multi-tap pada audio."
        override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
            val uri = input.uris.firstOrNull() ?: error("Pilih file audio")
            emit(ToolProgress.Running(50, "Memproses efek gema..."))
            val pcm = AudioEngine.decodeToPcm(ctx.appContext, uri)
            val out = AudioEngine.addEcho(pcm, input.getFloat("delayMs", 260f).toInt(), input.getFloat("decay", 0.4f))
            val dest = ctx.storage.createOutputFile("AUD_ECHO", "wav")
            AudioEngine.writeWav(out, dest)
            emit(ToolProgress.Done(ToolResult(listOf(ToolOutputFile(dest, "audio/wav", dest.name)), summary = "Efek gema diterapkan (${ctx.storage.formatBytes(dest.length())})")))
        }.flowOn(Dispatchers.IO)
    },
    object : Tool {
        override val id = "AUD-26"
        override val group = ToolGroup.AUDIO
        override val engineLabel = "DSP"
        override val icon = Icons.Filled.Tune
        override val inputSpec = InputSpec.SingleAudio
        override val params = listOf(
            ToolParam.SliderParam("fadeIn", "Fade In (detik)", "Fade In (sec)", 0f, 10f, 2f, 0.5f, "s"),
            ToolParam.SliderParam("fadeOut", "Fade Out (detik)", "Fade Out (sec)", 0f, 10f, 3f, 0.5f, "s")
        )
        override fun title(isEnglish: Boolean) = if (isEnglish) "Audio Fade In / Out" else "Fade In / Out Audio"
        override fun description(isEnglish: Boolean) = if (isEnglish) "Apply smooth intro and outro volume envelopes." else "Terapkan transisi suara halus di awal dan akhir audio."
        override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
            val uri = input.uris.firstOrNull() ?: error("Pilih file audio")
            emit(ToolProgress.Running(50, "Menerapkan kurva fade..."))
            val pcm = AudioEngine.decodeToPcm(ctx.appContext, uri)
            val out = AudioEngine.applyFade(pcm, input.getFloat("fadeIn", 2f), input.getFloat("fadeOut", 3f))
            val dest = ctx.storage.createOutputFile("AUD_FADE", "wav")
            AudioEngine.writeWav(out, dest)
            emit(ToolProgress.Done(ToolResult(listOf(ToolOutputFile(dest, "audio/wav", dest.name)), summary = "Fade selesai (${ctx.storage.formatBytes(dest.length())})")))
        }.flowOn(Dispatchers.IO)
    },
    object : Tool {
        override val id = "AUD-28"
        override val group = ToolGroup.AUDIO
        override val engineLabel = "NAT"
        override val icon = Icons.Filled.GraphicEq
        override val inputSpec = InputSpec.SingleAudio
        override val params = listOf(
            ToolParam.TextParam("title", "Judul Poster", "Poster Title", "HariMu Soundwave", "Judul audio...", "Audio title...")
        )
        override fun title(isEnglish: Boolean) = if (isEnglish) "Audio Waveform Poster" else "Visualizer Gelombang Audio"
        override fun description(isEnglish: Boolean) = if (isEnglish) "Render high-res PNG waveform art from any audio." else "Buat gambar poster spektrum gelombang resolusi tinggi dari audio."
        override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
            val uri = input.uris.firstOrNull() ?: error("Pilih file audio")
            emit(ToolProgress.Running(45, "Merender gelombang audio..."))
            val pcm = AudioEngine.decodeToPcm(ctx.appContext, uri)
            val dest = ctx.storage.createOutputFile("AUD_WAVE", "png")
            AudioEngine.renderWaveformPoster(pcm, dest, input.getString("title", "HariMu Soundwave"))
            emit(ToolProgress.Done(ToolResult(listOf(ToolOutputFile(dest, "image/png", dest.name)), summary = "Poster gelombang PNG dibuat")))
        }.flowOn(Dispatchers.IO)
    },
    object : Tool {
        override val id = "AUD-06"
        override val group = ToolGroup.AUDIO
        override val engineLabel = "FF"
        override val icon = Icons.Filled.Tune
        override val inputSpec = InputSpec.SingleAudio
        override val params = listOf(ToolParam.SliderParam("semitones", "Nada (Semitone)", "Pitch (Semitones)", -12f, 12f, 0f, 1f, "st"))
        override fun title(isEnglish: Boolean) = if (isEnglish) "Change Audio Pitch" else "Ubah Pitch Audio"
        override fun description(isEnglish: Boolean) = if (isEnglish) "Shift pitch without changing tempo (rubberband)." else "Naikkan/turunkan nada tanpa mengubah tempo."
        override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
            val uri = input.uris.firstOrNull() ?: error("Pilih file audio")
            val probe = MediaProbe.probe(ctx.appContext, uri)
            require(probe.hasAudio) { if (ctx.isEnglish) "No audio track" else "Tidak ada trek audio" }
            val semi = input.getFloat("semitones", 0f)
            val job = MediaJob()
            job.runCommand(ctx.appContext, ctx.storage, listOf(uri), "AUD_PITCH", "mp3", "audio/mpeg", isEnglish = ctx.isEnglish) { inputs, output ->
                listOf("-y", "-i", inputs.first().absolutePath,
                    "-af", "rubberband=pitch=${semi}",
                    "-c:a", "libmp3lame", "-b:a", "192k", output.absolutePath)
            }.collect { emit(it) }
        }.flowOn(Dispatchers.IO)
    },
    object : Tool {
        override val id = "AUD-07"
        override val group = ToolGroup.AUDIO
        override val engineLabel = "FF"
        override val icon = Icons.Filled.Speed
        override val inputSpec = InputSpec.SingleAudio
        override val params = listOf(ToolParam.SliderParam("speed", "Kecepatan", "Speed", 0.5f, 2.0f, 1.25f, 0.05f, "x"))
        override fun title(isEnglish: Boolean) = if (isEnglish) "Change Audio Speed" else "Ubah Kecepatan Audio"
        override fun description(isEnglish: Boolean) = if (isEnglish) "Adjust tempo without changing pitch." else "Atur tempo tanpa mengubah nada."
        override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
            val uri = input.uris.firstOrNull() ?: error("Pilih file audio")
            val probe = MediaProbe.probe(ctx.appContext, uri)
            require(probe.hasAudio) { if (ctx.isEnglish) "No audio track" else "Tidak ada trek audio" }
            val speed = input.getFloat("speed", 1.25f)
            val job = MediaJob()
            job.runCommand(ctx.appContext, ctx.storage, listOf(uri), "AUD_SPEED", "mp3", "audio/mpeg", isEnglish = ctx.isEnglish) { inputs, output ->
                val atempo = buildAtempoChain(speed)
                listOf("-y", "-i", inputs.first().absolutePath, "-af", atempo, "-c:a", "libmp3lame", "-b:a", "192k", output.absolutePath)
            }.collect { emit(it) }
        }.flowOn(Dispatchers.IO)
    },
    object : Tool {
        override val id = "AUD-11"
        override val group = ToolGroup.AUDIO
        override val engineLabel = "FF"
        override val icon = Icons.Filled.AudioFile
        override val inputSpec = InputSpec.SingleAudio
        override val params = listOf(ToolParam.DropdownParam("bitrate", "Bitrate", "Bitrate", listOf("32k", "64k", "96k", "128k", "192k", "256k", "320k"), "128k"))
        override fun title(isEnglish: Boolean) = if (isEnglish) "Audio Compressor" else "Kompresor Audio"
        override fun description(isEnglish: Boolean) = if (isEnglish) "Reduce audio file size with target bitrate." else "Kecilkan ukuran file audio dengan bitrate target."
        override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
            val uri = input.uris.firstOrNull() ?: error("Pilih file audio")
            val probe = MediaProbe.probe(ctx.appContext, uri)
            require(probe.hasAudio) { if (ctx.isEnglish) "No audio track" else "Tidak ada trek audio" }
            val kbps = input.getString("bitrate", "128k").removeSuffix("k").toInt()
            val job = MediaJob()
            job.runCommand(ctx.appContext, ctx.storage, listOf(uri), "AUD_COMPRESS", "mp3", "audio/mpeg", isEnglish = ctx.isEnglish) { inputs, output ->
                listOf("-y", "-i", inputs.first().absolutePath, "-c:a", "libmp3lame", "-b:a", "${kbps}k", output.absolutePath)
            }.collect { emit(it) }
        }.flowOn(Dispatchers.IO)
    },
    object : Tool {
        override val id = "AUD-12"
        override val group = ToolGroup.AUDIO
        override val engineLabel = "FF"
        override val icon = Icons.Filled.VolumeUp
        override val inputSpec = InputSpec.SingleAudio
        override val params = listOf(ToolParam.SliderParam("lufs", "Target LUFS", "Target LUFS", -24f, -10f, -16f, 1f, " LUFS"))
        override fun title(isEnglish: Boolean) = if (isEnglish) "Loudness Normalizer" else "Normalisasi Loudness"
        override fun description(isEnglish: Boolean) = if (isEnglish) "Normalize loudness to broadcast standard (-16 LUFS) using loudnorm 2-pass." else "Standarkan kenyaringan ke -16 LUFS dengan loudnorm 2-pass."
        override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
            val uri = input.uris.firstOrNull() ?: error("Pilih file audio")
            val probe = MediaProbe.probe(ctx.appContext, uri)
            require(probe.hasAudio) { if (ctx.isEnglish) "No audio track" else "Tidak ada trek audio" }
            val target = input.getFloat("lufs", -16f)
            val job = MediaJob()
            job.runCommand(ctx.appContext, ctx.storage, listOf(uri), "AUD_LOUDNESS", "mp3", "audio/mpeg", isEnglish = ctx.isEnglish) { inputs, output ->
                val pass1 = listOf("-y", "-i", inputs.first().absolutePath, "-af", "loudnorm=I=${target}:TP=-1.5:LRA=11:print_format=json", "-f", "null", "-")
                val res = com.arthenica.ffmpegkit.FFmpegKit.executeWithArguments(*pass1.toTypedArray())
                val logs = res.allLogsAsString ?: ""
                val extract = { k: String -> com.harimu.app.core.engines.AudioEngine.extractJsonValue(logs, k) ?: "" }
                val mi = extract("input_i"); val mt = extract("input_tp"); val ml = extract("input_lra"); val mth = extract("input_thresh"); val off = extract("target_offset")
                listOf("-y", "-i", inputs.first().absolutePath,
                    "-af", "loudnorm=I=${target}:TP=-1.5:LRA=11:measured_I=$mi:measured_TP=$mt:measured_LRA=$ml:measured_thresh=$mth:offset=$off:linear=true:print_format=summary",
                    "-c:a", "libmp3lame", "-b:a", "192k", output.absolutePath)
            }.collect { emit(it) }
        }.flowOn(Dispatchers.IO)
    },
    object : Tool {
        override val id = "AUD-13"
        override val group = ToolGroup.AUDIO
        override val engineLabel = "FF"
        override val icon = Icons.Filled.AudioFile
        override val inputSpec = InputSpec.SingleAudio
        override val params = listOf(ToolParam.DropdownParam("format", "Format", "Format", listOf("MP3", "WAV", "OGG", "FLAC", "M4A"), "MP3"))
        override fun title(isEnglish: Boolean) = if (isEnglish) "Audio Converter" else "Konverter Audio"
        override fun description(isEnglish: Boolean) = if (isEnglish) "Convert between MP3, WAV, OGG, FLAC, M4A." else "Konversi antar MP3, WAV, OGG, FLAC, M4A."
        override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
            val uri = input.uris.firstOrNull() ?: error("Pilih file audio")
            val probe = MediaProbe.probe(ctx.appContext, uri)
            require(probe.hasAudio) { if (ctx.isEnglish) "No audio track" else "Tidak ada trek audio" }
            val fmt = input.getString("format", "MP3").lowercase()
            val ext = when (fmt) { "m4a" -> "m4a"; else -> fmt }
            val mime = when (fmt) {
                "mp3" -> "audio/mpeg"; "wav" -> "audio/wav"; "ogg" -> "audio/ogg";
                "flac" -> "audio/flac"; "m4a" -> "audio/mp4"; else -> "audio/mpeg"
            }
            val job = MediaJob()
            job.runCommand(ctx.appContext, ctx.storage, listOf(uri), "AUD_CONV", ext, mime, isEnglish = ctx.isEnglish) { inputs, output ->
                val codec = when (fmt) {
                    "mp3" -> "libmp3lame"; "ogg" -> "libvorbis"; "flac" -> "flac";
                    "wav" -> "pcm_s16le"; "m4a" -> "aac"; else -> "libmp3lame"
                }
                val bitrate = when (fmt) { "flac" -> "0"; "wav" -> "1411k"; else -> "192k" }
                listOf("-y", "-i", inputs.first().absolutePath, "-c:a", codec, "-b:a", bitrate, output.absolutePath)
            }.collect { emit(it) }
        }.flowOn(Dispatchers.IO)
    },
    object : Tool {
        override val id = "AUD-14"
        override val group = ToolGroup.AUDIO
        override val engineLabel = "FF"
        override val icon = Icons.Filled.AudioFile
        override val inputSpec = InputSpec.SingleAudio
        override val params = listOf(ToolParam.DropdownParam("rate", "Sample Rate", "Sample Rate", listOf("8000", "16000", "22050", "32000", "44100", "48000", "96000"), "48000"))
        override fun title(isEnglish: Boolean) = if (isEnglish) "Audio Resampler" else "Resampler Audio"
        override fun description(isEnglish: Boolean) = if (isEnglish) "Resample audio to 8–96 kHz." else "Ubah sample rate audio 8–96 kHz."
        override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
            val uri = input.uris.firstOrNull() ?: error("Pilih file audio")
            val probe = MediaProbe.probe(ctx.appContext, uri)
            require(probe.hasAudio) { if (ctx.isEnglish) "No audio track" else "Tidak ada trek audio" }
            val rate = input.getString("rate", "48000").toInt()
            val job = MediaJob()
            job.runCommand(ctx.appContext, ctx.storage, listOf(uri), "AUD_RESAMPLE", "mp3", "audio/mpeg", isEnglish = ctx.isEnglish) { inputs, output ->
                listOf("-y", "-i", inputs.first().absolutePath, "-ar", rate.toString(), "-c:a", "libmp3lame", "-b:a", "192k", output.absolutePath)
            }.collect { emit(it) }
        }.flowOn(Dispatchers.IO)
    },
    object : Tool {
        override val id = "AUD-18"
        override val group = ToolGroup.AUDIO
        override val engineLabel = "LIB"
        override val icon = Icons.Filled.Tune
        override val inputSpec = InputSpec.SingleAudio
        override val params = listOf(
            ToolParam.TextParam("title", "Judul", "Title", ""),
            ToolParam.TextParam("artist", "Artis", "Artist", ""),
            ToolParam.TextParam("album", "Album", "Album", ""),
            ToolParam.TextParam("year", "Tahun", "Year", ""),
            ToolParam.TextParam("genre", "Genre", "Genre", "")
        )
        override fun title(isEnglish: Boolean) = if (isEnglish) "Audio Metadata Editor" else "Editor Metadata Audio"
        override fun description(isEnglish: Boolean) = if (isEnglish) "Edit ID3 tags: title, artist, album, year, genre." else "Sunting tag ID3: judul, artis, album, tahun, genre."
        override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
            val uri = input.uris.firstOrNull() ?: error("Pilih file audio")
            val probe = MediaProbe.probe(ctx.appContext, uri)
            require(probe.hasAudio) { if (ctx.isEnglish) "No audio track" else "Tidak ada trek audio" }
            val tags = mapOf(
                "title" to input.getString("title", ""),
                "artist" to input.getString("artist", ""),
                "album" to input.getString("album", ""),
                "date" to input.getString("year", ""),
                "genre" to input.getString("genre", "")
            ).filterValues { it.isNotBlank() }
            val job = MediaJob()
            job.runCommand(ctx.appContext, ctx.storage, listOf(uri), "AUD_META", "mp3", "audio/mpeg", isEnglish = ctx.isEnglish) { inputs, output ->
                val args = mutableListOf("-y", "-i", inputs.first().absolutePath)
                tags.forEach { (k, v) -> args.addAll(listOf("-metadata", "$k=$v")) }
                args.addAll(listOf("-c:a", "copy", output.absolutePath))
                args
            }.collect { emit(it) }
        }.flowOn(Dispatchers.IO)
    },
    object : Tool {
        override val id = "AUD-23"
        override val group = ToolGroup.AUDIO
        override val engineLabel = "FF"
        override val icon = Icons.Filled.Splitscreen
        override val inputSpec = InputSpec.SingleAudio
        override val params = listOf(ToolParam.SliderParam("segSec", "Durasi per Bagian (detik)", "Segment Duration (sec)", 5f, 600f, 30f, 5f, "s"))
        override fun title(isEnglish: Boolean) = if (isEnglish) "Audio Splitter" else "Pemecah Audio"
        override fun description(isEnglish: Boolean) = if (isEnglish) "Split audio into equal-duration parts inside a ZIP." else "Pecah audio menjadi bagian berdurasi sama dalam ZIP."
        override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
            val uri = input.uris.firstOrNull() ?: error("Pilih file audio")
            val probe = MediaProbe.probe(ctx.appContext, uri)
            require(probe.hasAudio) { if (ctx.isEnglish) "No audio track" else "Tidak ada trek audio" }
            val seg = input.getFloat("segSec", 30f).toInt()
            val job = MediaJob()
            val dest = ctx.storage.createOutputFile("AUD_SPLIT", "zip")
            val tempDir = ctx.storage.createJobTempDir("audsplit_${UUID.randomUUID().toString().take(8)}")
            val pattern = "${tempDir.absolutePath}/part_%03d.mp3"
            runBlocking {
                com.arthenica.ffmpegkit.FFmpegKit.executeWithArguments("-y", "-i",
                    com.arthenica.ffmpegkit.FFmpegKitConfig.getSafParameterForRead(ctx.appContext, uri) ?: "",
                    "-f", "segment", "-segment_time", seg.toString(),
                    "-c:a", "libmp3lame", "-b:a", "192k", "-reset_timestamps", "1", pattern)
            }
            val zos = java.util.zip.ZipOutputStream(java.io.FileOutputStream(dest))
            tempDir.listFiles()?.sortedBy { it.name }?.forEach { part ->
                zos.putNextEntry(java.util.zip.ZipEntry(part.name))
                part.inputStream().use { it.copyTo(zos, 16 * 1024) }
                zos.closeEntry()
            }
            zos.close()
            emit(ToolProgress.Done(ToolResult(listOf(ToolOutputFile(dest, "application/zip", dest.name)), summary = "Audio dipecah ke ZIP (${ctx.storage.formatBytes(dest.length())})")))
        }.flowOn(Dispatchers.IO)
    }
)

fun pdfTools(): List<Tool> = listOf(
    object : Tool {
        override val id = "PDF-01"
        override val group = ToolGroup.PDF
        override val engineLabel = "NAT"
        override val icon = Icons.Filled.MergeType
        override val inputSpec = InputSpec.MultiplePdfs
        override val params = emptyList<ToolParam>()
        override fun title(isEnglish: Boolean) = if (isEnglish) "Merge PDF" else "Gabung PDF"
        override fun description(isEnglish: Boolean) = if (isEnglish) "Combine multiple PDF documents into one file." else "Gabungkan beberapa dokumen PDF menjadi satu file."
        override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
            require(input.uris.size >= 2) { "Pilih minimal 2 file PDF" }
            emit(ToolProgress.Running(40, "Menggabungkan ${input.uris.size} dokumen PDF..."))
            val dest = ctx.storage.createOutputFile("PDF_MERGE", "pdf")
            val (_, pages) = PdfEngine.mergePdfs(ctx.appContext, ctx.storage, input.uris, dest)
            emit(ToolProgress.Done(ToolResult(listOf(ToolOutputFile(dest, "application/pdf", dest.name)), summary = "Digabung: $pages halaman (${ctx.storage.formatBytes(dest.length())})")))
        }.flowOn(Dispatchers.IO)
    },
    object : Tool {
        override val id = "PDF-02"
        override val group = ToolGroup.PDF
        override val engineLabel = "NAT"
        override val icon = Icons.Filled.Splitscreen
        override val inputSpec = InputSpec.SinglePdf
        override val params = listOf(
            ToolParam.SliderParam("perSplit", "Halaman per Bagian", "Pages per Split", 1f, 20f, 1f, 1f, "")
        )
        override fun title(isEnglish: Boolean) = if (isEnglish) "Split PDF (.zip)" else "Pisah PDF (.zip)"
        override fun description(isEnglish: Boolean) = if (isEnglish) "Split a PDF into smaller PDF parts inside a ZIP." else "Pecah PDF menjadi beberapa dokumen PDF per N halaman."
        override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
            val uri = input.uris.firstOrNull() ?: error("Pilih 1 file PDF")
            emit(ToolProgress.Running(40, "Memisahkan halaman PDF..."))
            val dest = ctx.storage.createOutputFile("PDF_SPLIT", "zip")
            val (_, parts) = PdfEngine.splitPdfToZip(ctx.appContext, ctx.storage, uri, dest, input.getFloat("perSplit", 1f).toInt())
            emit(ToolProgress.Done(ToolResult(listOf(ToolOutputFile(dest, "application/zip", dest.name)), summary = "Dipecah menjadi $parts file PDF di ZIP")))
        }.flowOn(Dispatchers.IO)
    },
    object : Tool {
        override val id = "PDF-03"
        override val group = ToolGroup.PDF
        override val engineLabel = "NAT"
        override val icon = Icons.Filled.RotateRight
        override val inputSpec = InputSpec.SinglePdf
        override val params = listOf(
            ToolParam.DropdownParam("angle", "Sudut Putar", "Rotation Angle", listOf("90", "180", "270"), "90")
        )
        override fun title(isEnglish: Boolean) = if (isEnglish) "Rotate PDF" else "Putar Halaman PDF"
        override fun description(isEnglish: Boolean) = if (isEnglish) "Rotate all PDF pages by 90, 180, or 270 degrees." else "Putar seluruh halaman PDF 90°, 180°, atau 270°."
        override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
            val uri = input.uris.firstOrNull() ?: error("Pilih 1 file PDF")
            emit(ToolProgress.Running(45, "Memutar halaman PDF..."))
            val deg = input.getString("angle", "90").toIntOrNull() ?: 90
            val dest = ctx.storage.createOutputFile("PDF_ROTATE", "pdf")
            val (_, pages) = PdfEngine.transformPdfPages(ctx.appContext, ctx.storage, uri, dest, rotateDegrees = deg)
            emit(ToolProgress.Done(ToolResult(listOf(ToolOutputFile(dest, "application/pdf", dest.name)), summary = "$pages halaman diputar ${deg}°")))
        }.flowOn(Dispatchers.IO)
    },
    object : Tool {
        override val id = "PDF-05"
        override val group = ToolGroup.PDF
        override val engineLabel = "NAT"
        override val icon = Icons.Filled.ContentCut
        override val inputSpec = InputSpec.SinglePdf
        override val params = listOf(
            ToolParam.TextParam("pages", "Rentang Halaman (mis. 1,3-5)", "Page Range (e.g. 1,3-5)", "1-2", "1,3-5", "1,3-5")
        )
        override fun title(isEnglish: Boolean) = if (isEnglish) "Extract PDF Pages" else "Ekstrak Halaman PDF"
        override fun description(isEnglish: Boolean) = if (isEnglish) "Extract specific page numbers or ranges into a new PDF." else "Ambil halaman tertentu (contoh: 1,3-5) menjadi PDF baru."
        override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
            val uri = input.uris.firstOrNull() ?: error("Pilih 1 file PDF")
            emit(ToolProgress.Running(45, "Mengekstrak halaman terpilih..."))
            val dest = ctx.storage.createOutputFile("PDF_EXTRACT", "pdf")
            val (_, pages) = PdfEngine.transformPdfPages(ctx.appContext, ctx.storage, uri, dest, pageRangeExpr = input.getString("pages", "1-2"))
            emit(ToolProgress.Done(ToolResult(listOf(ToolOutputFile(dest, "application/pdf", dest.name)), summary = "$pages halaman diekstrak")))
        }.flowOn(Dispatchers.IO)
    },
    object : Tool {
        override val id = "PDF-08"
        override val group = ToolGroup.PDF
        override val engineLabel = "NAT"
        override val icon = Icons.Filled.PictureAsPdf
        override val inputSpec = InputSpec.MultipleImages
        override val params = listOf(
            ToolParam.DropdownParam("pageSize", "Ukuran Halaman", "Page Size", listOf("A4", "Letter", "Fit"), "A4")
        )
        override fun title(isEnglish: Boolean) = if (isEnglish) "Images to PDF" else "Gambar ke PDF"
        override fun description(isEnglish: Boolean) = if (isEnglish) "Convert one or more images into a multi-page PDF." else "Susun satu atau beberapa gambar menjadi dokumen PDF."
        override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
            require(input.uris.isNotEmpty()) { "Pilih minimal 1 gambar" }
            emit(ToolProgress.Running(45, "Menyusun ${input.uris.size} gambar ke PDF..."))
            val dest = ctx.storage.createOutputFile("IMG2PDF", "pdf")
            PdfEngine.imagesToPdf(ctx.appContext, input.uris, dest, input.getString("pageSize", "A4"))
            emit(ToolProgress.Done(ToolResult(listOf(ToolOutputFile(dest, "application/pdf", dest.name)), summary = "${input.uris.size} halaman PDF (${ctx.storage.formatBytes(dest.length())})")))
        }.flowOn(Dispatchers.IO)
    },
    object : Tool {
        override val id = "PDF-12"
        override val group = ToolGroup.PDF
        override val engineLabel = "NAT"
        override val icon = Icons.Filled.WaterDrop
        override val inputSpec = InputSpec.SinglePdf
        override val params = listOf(
            ToolParam.TextParam("watermark", "Teks Watermark", "Watermark Text", "RAHASIA • HARIMU", "Teks cap...", "Stamp text...")
        )
        override fun title(isEnglish: Boolean) = if (isEnglish) "Add PDF Watermark" else "Watermark PDF"
        override fun description(isEnglish: Boolean) = if (isEnglish) "Stamp diagonal watermark text on every PDF page." else "Bubuhkan cap watermark diagonal di setiap halaman PDF."
        override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
            val uri = input.uris.firstOrNull() ?: error("Pilih 1 file PDF")
            emit(ToolProgress.Running(50, "Menambahkan watermark PDF..."))
            val dest = ctx.storage.createOutputFile("PDF_WM", "pdf")
            val (_, pages) = PdfEngine.transformPdfPages(
                ctx.appContext, ctx.storage, uri, dest,
                watermarkText = input.getString("watermark", "HARIMU")
            )
            emit(ToolProgress.Done(ToolResult(listOf(ToolOutputFile(dest, "application/pdf", dest.name)), summary = "Watermark ditambahkan ke $pages halaman")))
        }.flowOn(Dispatchers.IO)
    },
    object : Tool {
        override val id = "PDF-18"
        override val group = ToolGroup.PDF
        override val engineLabel = "NAT"
        override val icon = Icons.Filled.FormatListNumbered
        override val inputSpec = InputSpec.SinglePdf
        override val params = emptyList<ToolParam>()
        override fun title(isEnglish: Boolean) = if (isEnglish) "PDF Page Numbering" else "Penomoran Halaman PDF"
        override fun description(isEnglish: Boolean) = if (isEnglish) "Add X / Y page numbers to the footer of each page." else "Tambahkan nomor halaman (X / Y) di bagian bawah dokumen."
        override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
            val uri = input.uris.firstOrNull() ?: error("Pilih 1 file PDF")
            emit(ToolProgress.Running(50, "Memberi nomor halaman..."))
            val dest = ctx.storage.createOutputFile("PDF_NUM", "pdf")
            val (_, pages) = PdfEngine.transformPdfPages(ctx.appContext, ctx.storage, uri, dest, addPageNumbers = true)
            emit(ToolProgress.Done(ToolResult(listOf(ToolOutputFile(dest, "application/pdf", dest.name)), summary = "Nomor halaman ditambahkan pada $pages halaman")))
        }.flowOn(Dispatchers.IO)
    },
    object : Tool {
        override val id = "PDF-20"
        override val group = ToolGroup.PDF
        override val engineLabel = "NAT"
        override val icon = Icons.Filled.Compare
        override val inputSpec = InputSpec.MultiplePdfs
        override val params = emptyList<ToolParam>()
        override fun title(isEnglish: Boolean) = if (isEnglish) "Compare Two PDFs" else "Bandingkan 2 Dokumen PDF"
        override fun description(isEnglish: Boolean) = if (isEnglish) "Highlight pixel differences between page 1 of two PDFs." else "Sorot perbedaan visual halaman pertama antara 2 file PDF."
        override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
            require(input.uris.size >= 2) { "Pilih 2 file PDF untuk dibandingkan" }
            emit(ToolProgress.Running(50, "Membandingkan halaman PDF..."))
            val dest = ctx.storage.createOutputFile("PDF_DIFF", "png")
            val (_, diffPct) = PdfEngine.compareFirstPagesVisual(ctx.appContext, ctx.storage, input.uris[0], input.uris[1], dest)
            emit(ToolProgress.Done(ToolResult(listOf(ToolOutputFile(dest, "image/png", dest.name)), summary = "Perbedaan visual: ${String.format("%.2f", diffPct)}% piksel")))
        }.flowOn(Dispatchers.IO)
    }
)
