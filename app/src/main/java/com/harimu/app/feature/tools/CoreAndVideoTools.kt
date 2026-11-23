package com.harimu.app.feature.tools

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.BurstMode
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.ui.graphics.vector.ImageVector
import com.harimu.app.core.engines.AudioEngine
import com.harimu.app.core.engines.VideoEngine
import com.harimu.app.core.media.MediaJob
import com.harimu.app.core.media.MediaProbe
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

fun coreTools(): List<Tool> = listOf(
    DedicatedTool(
        id = "CORE-06",
        group = ToolGroup.CORE,
        engineLabel = "NAT",
        icon = Icons.Filled.Mic,
        dedicatedRoute = DedicatedScreenRoute.VOICE_RECORDER,
        titleId = "Voice Recorder",
        titleEn = "Voice Recorder",
        descId = "Rekam suara PCM dengan spektrum audio langsung ke file WAV.",
        descEn = "Record PCM audio with live spectrum visualizer to WAV."
    ),
    object : Tool {
        override val id = "CORE-03"
        override val group = ToolGroup.CORE
        override val engineLabel = "NAT"
        override val icon = Icons.Filled.RecordVoiceOver
        override val inputSpec = InputSpec.TextInput(
            hintId = "Ketik teks yang ingin diubah menjadi suara WAV...",
            hintEn = "Enter text to synthesize into a WAV audio file...",
            sample = "Selamat datang di HariMu, studio utilitas harian Anda."
        )
        override val params = listOf(
            ToolParam.DropdownParam("lang", "Bahasa Suara", "Voice Language", listOf("Indonesia", "English (US)"), "Indonesia"),
            ToolParam.SliderParam("rate", "Kecepatan Bicara", "Speech Rate", 0.5f, 2.0f, 1.0f, 0.1f, "x"),
            ToolParam.SliderParam("pitch", "Nada Suara", "Voice Pitch", 0.6f, 1.8f, 1.0f, 0.1f, "x")
        )
        override fun title(isEnglish: Boolean) = if (isEnglish) "Generate Speech (TTS)" else "Teks ke Suara (TTS)"
        override fun description(isEnglish: Boolean) =
            if (isEnglish) "Synthesize text to a WAV file using Android TTS." else "Ubah teks menjadi file audio WAV dengan engine TTS perangkat."

        override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
            emit(ToolProgress.Running(20, if (ctx.isEnglish) "Preparing TTS engine..." else "Menyiapkan engine TTS..."))
            val text = input.textInput.trim()
            require(text.isNotEmpty()) { if (ctx.isEnglish) "Enter text first" else "Masukkan teks terlebih dahulu" }
            val dest = ctx.storage.createOutputFile("TTS", "wav")
            val isIndo = input.getString("lang", "Indonesia") == "Indonesia"
            AudioEngine.synthesizeTtsToWav(
                ctx.appContext,
                text,
                dest,
                input.getFloat("rate", 1.0f),
                input.getFloat("pitch", 1.0f),
                isIndo
            )
            emit(
                ToolProgress.Done(
                    ToolResult(
                        outputs = listOf(ToolOutputFile(dest, "audio/wav", dest.name)),
                        summary = "${dest.name} (${ctx.storage.formatBytes(dest.length())})"
                    )
                )
            )
        }.flowOn(Dispatchers.IO)
    },
    DedicatedTool(
        id = "CORE-07",
        group = ToolGroup.CORE,
        engineLabel = "JCA",
        icon = Icons.Filled.Key,
        dedicatedRoute = DedicatedScreenRoute.KEY_VAULT,
        titleId = "Key Vault Kripto",
        titleEn = "Crypto Key Vault",
        descId = "Buat dan kelola kunci AES-256 (Keystore), RSA-2048, dan ECDSA P-256.",
        descEn = "Generate and manage AES-256 (Keystore), RSA-2048, and ECDSA P-256 keys."
    ),
    DedicatedTool(
        id = "CORE-08",
        group = ToolGroup.CORE,
        engineLabel = "ZIP",
        icon = Icons.Filled.Backup,
        dedicatedRoute = DedicatedScreenRoute.WORKSPACE_BACKUP,
        titleId = "Backup Workspace (.omni)",
        titleEn = "Workspace Backup (.omni)",
        descId = "Ekspor & pulihkan setelan, favorit, dan riwayat dalam arsip .omni terenkripsi.",
        descEn = "Export & restore settings, favorites, and history in an encrypted .omni archive."
    ),
    DedicatedTool(
        id = "CORE-09",
        group = ToolGroup.CORE,
        engineLabel = "APP",
        icon = Icons.Filled.Settings,
        dedicatedRoute = DedicatedScreenRoute.SETTINGS,
        titleId = "Setelan & Koneksi Cloud",
        titleEn = "Settings & Cloud Connections",
        descId = "Atur tema AMOLED, kualitas ekspor, cache temp, dan endpoint S3/R2.",
        descEn = "Configure AMOLED theme, export quality, temp cache, and S3/R2 endpoint."
    )
)

fun videoTools(): List<Tool> = listOf(
    object : Tool {
        override val id = "VID-01"
        override val group = ToolGroup.VIDEO
        override val engineLabel = "M3/NAT"
        override val icon = Icons.Filled.ContentCut
        override val inputSpec = InputSpec.SingleVideo
        override val params = listOf(
            ToolParam.SliderParam("startSec", "Mulai (detik)", "Start (sec)", 0f, 120f, 0f, 1f, "s"),
            ToolParam.SliderParam("endSec", "Selesai (detik, 0=akhir)", "End (sec, 0=end)", 0f, 180f, 10f, 1f, "s")
        )
        override fun title(isEnglish: Boolean) = if (isEnglish) "Trim Video" else "Potong Video"
        override fun description(isEnglish: Boolean) =
            if (isEnglish) "Cut a video segment without re-encoding loss." else "Potong segmen video MP4 secara cepat lewat MediaMuxer."

        override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
            val uri = input.uris.firstOrNull() ?: error("Pilih 1 video")
            val probe = MediaProbe.probe(ctx.appContext, uri)
            require(probe.hasVideo) { if (ctx.isEnglish) "Unsupported or empty video file" else "File video tidak didukung atau kosong" }
            emit(ToolProgress.Running(30, "Memotong track video & audio..."))
            val startMs = (input.getFloat("startSec", 0f) * 1000f).toLong()
            val endVal = input.getFloat("endSec", 10f)
            val endMs = if (endVal <= 0f) Long.MAX_VALUE else (endVal * 1000f).toLong()
            val dest = ctx.storage.createOutputFile("VID_TRIM", "mp4")
            VideoEngine.trimOrCopyTracks(ctx.appContext, uri, dest, startMs, endMs)
            emit(ToolProgress.Done(ToolResult(listOf(ToolOutputFile(dest, "video/mp4", dest.name)), summary = "Video dipotong: ${ctx.storage.formatBytes(dest.length())}")))
        }.flowOn(Dispatchers.IO)
    },
    object : Tool {
        override val id = "VID-02"
        override val group = ToolGroup.VIDEO
        override val engineLabel = "NAT"
        override val icon = Icons.Filled.MergeType
        override val inputSpec = InputSpec.MultipleVideos
        override val params = emptyList<ToolParam>()
        override fun title(isEnglish: Boolean) = if (isEnglish) "Merge Videos" else "Gabung Video"
        override fun description(isEnglish: Boolean) =
            if (isEnglish) "Concatenate compatible MP4 clips sequentially." else "Gabungkan beberapa klip MP4 berformat kompatibel secara berurutan."

        override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
            require(input.uris.size >= 2) { "Pilih minimal 2 video" }
            input.uris.forEach { u ->
                val p = MediaProbe.probe(ctx.appContext, u)
                require(p.hasVideo) { if (ctx.isEnglish) "Unsupported or empty video file" else "File video tidak didukung atau kosong" }
            }
            emit(ToolProgress.Running(40, "Menggabungkan ${input.uris.size} klip..."))
            val dest = ctx.storage.createOutputFile("VID_MERGE", "mp4")
            VideoEngine.mergeVideos(ctx.appContext, input.uris, dest)
            emit(ToolProgress.Done(ToolResult(listOf(ToolOutputFile(dest, "video/mp4", dest.name)), summary = "${input.uris.size} video digabung (${ctx.storage.formatBytes(dest.length())})")))
        }.flowOn(Dispatchers.IO)
    },
    object : Tool {
        override val id = "VID-03"
        override val group = ToolGroup.VIDEO
        override val engineLabel = "FF"
        override val icon = Icons.Filled.Crop
        override val inputSpec = InputSpec.SingleVideo
        override val params = listOf(
            ToolParam.DropdownParam("ratio", "Rasio", "Aspect Ratio", listOf("1:1", "16:9", "9:16"), "1:1")
        )
        override fun title(isEnglish: Boolean) = if (isEnglish) "Crop Video" else "Potong Bingkai Video"
        override fun description(isEnglish: Boolean) =
            if (isEnglish) "Center-crop video frame to 1:1, 16:9, or 9:16." else "Potong bingkai video ke rasio 1:1, 16:9, atau 9:16."

        override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
            val uri = input.uris.firstOrNull() ?: error("Pilih 1 video")
            val probe = MediaProbe.probe(ctx.appContext, uri)
            require(probe.hasVideo) { if (ctx.isEnglish) "Unsupported or empty video file" else "File video tidak didukung atau kosong" }
            val ratio = input.getString("ratio", "1:1")
            val job = MediaJob()
            val dest = ctx.storage.createOutputFile("VID_CROP", "mp4")
            val (cw, ch) = VideoEngine.cropDimensions(probe.width, probe.height, ratio)
            val x = ((probe.width - cw) / 2).coerceAtLeast(0)
            val y = ((probe.height - ch) / 2).coerceAtLeast(0)
            emit(ToolProgress.Running(10, "Memotong bingkai..."))
            // Use a custom runner through MediaJob by invoking runCommand with builder:
            val flow = job.runCommand(
                context = ctx.appContext,
                storage = ctx.storage,
                inputUris = listOf(uri),
                toolTag = "VID_CROP",
                outputExt = "mp4",
                outputMime = "video/mp4",
                isEnglish = ctx.isEnglish
            ) { inputs, output ->
                listOf(
                    "-y", "-i", inputs.first().absolutePath,
                    "-vf", "crop=${cw}:${ch}:${x}:${y}",
                    "-c:a", "copy", "-movflags", "+faststart",
                    output.absolutePath
                )
            }
            // Collect nested flow and re-emit
            flow.collect { p -> emit(p) }
        }.flowOn(Dispatchers.IO)
    },
    object : Tool {
        override val id = "VID-04"
        override val group = ToolGroup.VIDEO
        override val engineLabel = "FF"
        override val icon = Icons.Filled.Videocam
        override val inputSpec = InputSpec.SingleVideo
        override val params = listOf(
            ToolParam.DropdownParam("res", "Resolusi", "Resolution", listOf("1080p", "720p", "480p", "360p"), "720p")
        )
        override fun title(isEnglish: Boolean) = if (isEnglish) "Resize Video" else "Ubah Ukuran Video"
        override fun description(isEnglish: Boolean) =
            if (isEnglish) "Resize video resolution to 1080p/720p/480p/360p." else "Ubah resolusi video ke 1080p/720p/480p/360p."

        override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
            val uri = input.uris.firstOrNull() ?: error("Pilih 1 video")
            val probe = MediaProbe.probe(ctx.appContext, uri)
            require(probe.hasVideo) { if (ctx.isEnglish) "Unsupported or empty video file" else "File video tidak didukung atau kosong" }
            val height = when (input.getString("res", "720p")) {
                "1080p" -> 1080; "720p" -> 720; "480p" -> 480; "360p" -> 360; else -> 720
            }
            val job = MediaJob()
            val flow = job.runCommand(
                context = ctx.appContext,
                storage = ctx.storage,
                inputUris = listOf(uri),
                toolTag = "VID_RESIZE",
                outputExt = "mp4",
                outputMime = "video/mp4",
                isEnglish = ctx.isEnglish
            ) { inputs, output ->
                listOf(
                    "-y", "-i", inputs.first().absolutePath,
                    "-vf", "scale=-2:${height}",
                    "-c:v", "libx264", "-preset", "medium", "-crf", "23",
                    "-c:a", "copy", "-movflags", "+faststart",
                    output.absolutePath
                )
            }
            flow.collect { p -> emit(p) }
        }.flowOn(Dispatchers.IO)
    },
    object : Tool {
        override val id = "VID-05"
        override val group = ToolGroup.VIDEO
        override val engineLabel = "FF"
        override val icon = Icons.Filled.Speed
        override val inputSpec = InputSpec.SingleVideo
        override val params = listOf(
            ToolParam.SliderParam("speed", "Kecepatan", "Speed", 0.25f, 4.0f, 1.5f, 0.25f, "x")
        )
        override fun title(isEnglish: Boolean) = if (isEnglish) "Change Video Speed" else "Ubah Kecepatan Video"
        override fun description(isEnglish: Boolean) =
            if (isEnglish) "Speed up or slow down video and audio." else "Percepat atau perlambat video dan audio."

        override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
            val uri = input.uris.firstOrNull() ?: error("Pilih 1 video")
            val probe = MediaProbe.probe(ctx.appContext, uri)
            require(probe.hasVideo) { if (ctx.isEnglish) "Unsupported or empty video file" else "File video tidak didukung atau kosong" }
            require(probe.hasAudio) { if (ctx.isEnglish) "Video has no audio track" else "Video tidak memiliki trek audio" }
            val speed = input.getFloat("speed", 1.5f).coerceIn(0.25f, 4f)
            val setpts = VideoEngine.setptsFilter(speed)
            val atempo = VideoEngine.atempoFilter(speed)
            val job = MediaJob()
            val flow = job.runCommand(
                context = ctx.appContext,
                storage = ctx.storage,
                inputUris = listOf(uri),
                toolTag = "VID_SPEED",
                outputExt = "mp4",
                outputMime = "video/mp4",
                isEnglish = ctx.isEnglish
            ) { inputs, output ->
                listOf(
                    "-y", "-i", inputs.first().absolutePath,
                    "-filter_complex", "[0:v]${setpts}[v];[0:a]${atempo}[a]",
                    "-map", "[v]", "-map", "[a]",
                    "-c:v", "libx264", "-preset", "medium", "-crf", "23",
                    "-c:a", "aac", "-b:a", "128k",
                    "-movflags", "+faststart", output.absolutePath
                )
            }
            flow.collect { p -> emit(p) }
        }.flowOn(Dispatchers.IO)
    },
    object : Tool {
        override val id = "VID-06"
        override val group = ToolGroup.VIDEO
        override val engineLabel = "FF"
        override val icon = Icons.Filled.BurstMode
        override val inputSpec = InputSpec.SingleVideo
        override val params = emptyList<ToolParam>()
        override fun title(isEnglish: Boolean) = if (isEnglish) "Video to GIF" else "Video ke GIF"
        override fun description(isEnglish: Boolean) =
            if (isEnglish) "Convert a video clip into an animated GIF." else "Konversi klip video menjadi GIF animasi."

        override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
            val uri = input.uris.firstOrNull() ?: error("Pilih 1 video")
            val probe = MediaProbe.probe(ctx.appContext, uri)
            require(probe.hasVideo) { if (ctx.isEnglish) "Unsupported or empty video file" else "File video tidak didukung atau kosong" }
            val job = MediaJob()
            val flow = job.runCommand(
                context = ctx.appContext,
                storage = ctx.storage,
                inputUris = listOf(uri),
                toolTag = "VID_GIF",
                outputExt = "gif",
                outputMime = "image/gif",
                isEnglish = ctx.isEnglish
            ) { inputs, output ->
                listOf(
                    "-y", "-i", inputs.first().absolutePath,
                    "-vf", "fps=12,scale=480:-1:flags=lanczos,split[s0][s1];[s0]palettegen=max_colors=128[p];[s1][p]paletteuse=dither=bayer",
                    "-loop", "0", output.absolutePath
                )
            }
            flow.collect { p -> emit(p) }
        }.flowOn(Dispatchers.IO)
    },
    object : Tool {
        override val id = "VID-08"
        override val group = ToolGroup.VIDEO
        override val engineLabel = "FF"
        override val icon = Icons.Filled.MusicNote
        override val inputSpec = InputSpec.SingleVideo
        override val params = emptyList<ToolParam>()
        override fun title(isEnglish: Boolean) = if (isEnglish) "Video to Audio (MP3)" else "Video ke Audio (MP3)"
        override fun description(isEnglish: Boolean) =
            if (isEnglish) "Extract audio track from a video into MP3." else "Ekstrak track audio dari video ke file MP3."

        override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
            val uri = input.uris.firstOrNull() ?: error("Pilih 1 video")
            val probe = MediaProbe.probe(ctx.appContext, uri)
            require(probe.hasVideo) { if (ctx.isEnglish) "Unsupported or empty video file" else "File video tidak didukung atau kosong" }
            require(probe.hasAudio) { if (ctx.isEnglish) "Video has no audio track" else "Video tidak memiliki trek audio" }
            val job = MediaJob()
            val flow = job.runCommand(
                context = ctx.appContext,
                storage = ctx.storage,
                inputUris = listOf(uri),
                toolTag = "VID_AUDIO",
                outputExt = "mp3",
                outputMime = "audio/mpeg",
                isEnglish = ctx.isEnglish
            ) { inputs, output ->
                listOf(
                    "-y", "-i", inputs.first().absolutePath,
                    "-vn", "-c:a", "libmp3lame", "-q:a", "2",
                    output.absolutePath
                )
            }
            flow.collect { p -> emit(p) }
        }.flowOn(Dispatchers.IO)
    },
    object : Tool {
        override val id = "VID-09"
        override val group = ToolGroup.VIDEO
        override val engineLabel = "FF"
        override val icon = Icons.Filled.Videocam
        override val inputSpec = InputSpec.SingleVideo
        override val params = listOf(
            ToolParam.DropdownParam("deg", "Sudut", "Angle", listOf("90", "180", "270"), "90")
        )
        override fun title(isEnglish: Boolean) = if (isEnglish) "Rotate Video" else "Putar Video"
        override fun description(isEnglish: Boolean) =
            if (isEnglish) "Rotate video orientation 90, 180, or 270 degrees." else "Putar orientasi video 90, 180, atau 270 derajat."

        override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
            val uri = input.uris.firstOrNull() ?: error("Pilih 1 video")
            val probe = MediaProbe.probe(ctx.appContext, uri)
            require(probe.hasVideo) { if (ctx.isEnglish) "Unsupported or empty video file" else "File video tidak didukung atau kosong" }
            val deg = input.getString("deg", "90")
            val transpose = when (deg) {
                "90" -> "transpose=1"
                "180" -> "transpose=1,transpose=1"
                "270" -> "transpose=2"
                else -> "transpose=1"
            }
            val job = MediaJob()
            val flow = job.runCommand(
                context = ctx.appContext,
                storage = ctx.storage,
                inputUris = listOf(uri),
                toolTag = "VID_ROTATE",
                outputExt = "mp4",
                outputMime = "video/mp4",
                isEnglish = ctx.isEnglish
            ) { inputs, output ->
                listOf(
                    "-y", "-i", inputs.first().absolutePath,
                    "-vf", transpose,
                    "-c:a", "copy", "-movflags", "+faststart",
                    output.absolutePath
                )
            }
            flow.collect { p -> emit(p) }
        }.flowOn(Dispatchers.IO)
    },
    object : Tool {
        override val id = "VID-10"
        override val group = ToolGroup.VIDEO
        override val engineLabel = "FF"
        override val icon = Icons.Filled.Flip
        override val inputSpec = InputSpec.SingleVideo
        override val params = listOf(
            ToolParam.DropdownParam("mode", "Arah", "Direction", listOf("Horizontal", "Vertical", "Both"), "Horizontal")
        )
        override fun title(isEnglish: Boolean) = if (isEnglish) "Flip Video" else "Balik Video"
        override fun description(isEnglish: Boolean) =
            if (isEnglish) "Mirror video horizontally or vertically." else "Cerminkan video horizontal, vertikal, atau keduanya."

        override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
            val uri = input.uris.firstOrNull() ?: error("Pilih 1 video")
            val probe = MediaProbe.probe(ctx.appContext, uri)
            require(probe.hasVideo) { if (ctx.isEnglish) "Unsupported or empty video file" else "File video tidak didukung atau kosong" }
            val vf = when (input.getString("mode", "Horizontal")) {
                "Vertical" -> "vflip"
                "Both" -> "hflip,vflip"
                else -> "hflip"
            }
            val job = MediaJob()
            val flow = job.runCommand(
                context = ctx.appContext,
                storage = ctx.storage,
                inputUris = listOf(uri),
                toolTag = "VID_FLIP",
                outputExt = "mp4",
                outputMime = "video/mp4",
                isEnglish = ctx.isEnglish
            ) { inputs, output ->
                listOf(
                    "-y", "-i", inputs.first().absolutePath,
                    "-vf", vf,
                    "-c:a", "copy", "-movflags", "+faststart",
                    output.absolutePath
                )
            }
            flow.collect { p -> emit(p) }
        }.flowOn(Dispatchers.IO)
    },
    object : Tool {
        override val id = "VID-12"
        override val group = ToolGroup.VIDEO
        override val engineLabel = "FF"
        override val icon = Icons.Filled.Loop
        override val inputSpec = InputSpec.SingleVideo
        override val params = listOf(
            ToolParam.SliderParam("loops", "Jumlah Loop", "Loops", 2f, 10f, 3f, 1f, "x")
        )
        override fun title(isEnglish: Boolean) = if (isEnglish) "Loop Video" else "Loop Video"
        override fun description(isEnglish: Boolean) =
            if (isEnglish) "Repeat a video clip N times." else "Ulangi klip video beberapa kali."

        override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
            val uri = input.uris.firstOrNull() ?: error("Pilih 1 video")
            val probe = MediaProbe.probe(ctx.appContext, uri)
            require(probe.hasVideo) { if (ctx.isEnglish) "Unsupported or empty video file" else "File video tidak didukung atau kosong" }
            val loops = input.getFloat("loops", 3f).toInt().coerceIn(2, 10)
            val totalMs = probe.durationMs * loops
            require(totalMs <= 300_000L) {
                if (ctx.isEnglish) "Video too long to loop safely (max 5 min output)" else "Video terlalu panjang untuk loop aman (maks 5 menit output)"
            }
            val job = MediaJob()
            val flow = job.runCommand(
                context = ctx.appContext,
                storage = ctx.storage,
                inputUris = listOf(uri),
                toolTag = "VID_LOOP",
                outputExt = "mp4",
                outputMime = "video/mp4",
                isEnglish = ctx.isEnglish
            ) { inputs, output ->
                listOf(
                    "-y", "-stream_loop", (loops - 1).toString(),
                    "-i", inputs.first().absolutePath,
                    "-c", "copy", "-movflags", "+faststart",
                    output.absolutePath
                )
            }
            flow.collect { p -> emit(p) }
        }.flowOn(Dispatchers.IO)
    },
    object : Tool {
        override val id = "VID-13"
        override val group = ToolGroup.VIDEO
        override val engineLabel = "FF"
        override val icon = Icons.Filled.VolumeUp
        override val inputSpec = InputSpec.SingleVideo
        override val params = listOf(
            ToolParam.SliderParam("vol", "Volume", "Volume", 0f, 200f, 120f, 10f, "%")
        )
        override fun title(isEnglish: Boolean) = if (isEnglish) "Change Video Volume" else "Ubah Volume Video"
        override fun description(isEnglish: Boolean) =
            if (isEnglish) "Adjust audio track volume in video." else "Atur tingkat volume track audio pada video."

        override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
            val uri = input.uris.firstOrNull() ?: error("Pilih 1 video")
            val probe = MediaProbe.probe(ctx.appContext, uri)
            require(probe.hasVideo) { if (ctx.isEnglish) "Unsupported or empty video file" else "File video tidak didukung atau kosong" }
            require(probe.hasAudio) { if (ctx.isEnglish) "Video has no audio track" else "Video tidak memiliki trek audio" }
            val volPercent = input.getFloat("vol", 120f).coerceIn(0f, 200f)
            val factor = volPercent / 100f
            val job = MediaJob()
            val flow = job.runCommand(
                context = ctx.appContext,
                storage = ctx.storage,
                inputUris = listOf(uri),
                toolTag = "VID_VOL",
                outputExt = "mp4",
                outputMime = "video/mp4",
                isEnglish = ctx.isEnglish
            ) { inputs, output ->
                listOf(
                    "-y", "-i", inputs.first().absolutePath,
                    "-af", "volume=${factor}",
                    "-c:v", "copy", "-c:a", "aac", "-b:a", "128k",
                    "-movflags", "+faststart", output.absolutePath
                )
            }
            flow.collect { p -> emit(p) }
        }.flowOn(Dispatchers.IO)
    },
    object : Tool {
        override val id = "VID-16"
        override val group = ToolGroup.VIDEO
        override val engineLabel = "NAT"
        override val icon = Icons.Filled.MusicNote
        override val inputSpec = InputSpec.VideoAndAudio
        override val params = emptyList<ToolParam>()
        override fun title(isEnglish: Boolean) = if (isEnglish) "Add Audio to Video" else "Tambah Audio ke Video"
        override fun description(isEnglish: Boolean) =
            if (isEnglish) "Mux a chosen audio track onto a video clip." else "Gabungkan track video dengan file audio AAC/M4A pilihan."

        override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
            val vUri = input.uris.firstOrNull() ?: error("Pilih video utama")
            val aUri = input.secondaryUri ?: error("Pilih audio latar")
            emit(ToolProgress.Running(45, "Menggabungkan track video & audio..."))
            val dest = ctx.storage.createOutputFile("VID_MUX", "mp4")
            VideoEngine.muxVideoWithAudio(ctx.appContext, vUri, aUri, dest)
            emit(ToolProgress.Done(ToolResult(listOf(ToolOutputFile(dest, "video/mp4", dest.name)), summary = "Muxing selesai (${ctx.storage.formatBytes(dest.length())})")))
        }.flowOn(Dispatchers.IO)
    },
    object : Tool {
        override val id = "VID-18"
        override val group = ToolGroup.VIDEO
        override val engineLabel = "FF"
        override val icon = Icons.Filled.Videocam
        override val inputSpec = InputSpec.SingleVideo
        override val params = listOf(
            ToolParam.DropdownParam("fmt", "Format", "Format", listOf("MP4", "WebM", "AVI", "MKV"), "MP4")
        )
        override fun title(isEnglish: Boolean) = if (isEnglish) "Video Converter" else "Konverter Video"
        override fun description(isEnglish: Boolean) =
            if (isEnglish) "Convert between MP4, WebM, AVI, and MKV." else "Konversi format MP4, WebM, AVI, dan MKV."

        override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
            val uri = input.uris.firstOrNull() ?: error("Pilih 1 video")
            val probe = MediaProbe.probe(ctx.appContext, uri)
            require(probe.hasVideo) { if (ctx.isEnglish) "Unsupported or empty video file" else "File video tidak didukung atau kosong" }
            val fmt = input.getString("fmt", "MP4").uppercase()
            val (ext, vcodec, acodec, mime) = when (fmt) {
                "WEBM" -> listOf("webm", "libvpx-vp9", "libopus", "video/webm")
                "AVI" -> listOf("avi", "mpeg4", "libmp3lame", "video/x-msvideo")
                "MKV" -> listOf("mkv", "libx264", "aac", "video/x-matroska")
                else -> listOf("mp4", "libx264", "aac", "video/mp4")
            }
            val job = MediaJob()
            val flow = job.runCommand(
                context = ctx.appContext,
                storage = ctx.storage,
                inputUris = listOf(uri),
                toolTag = "VID_CONVERT",
                outputExt = ext,
                outputMime = mime,
                isEnglish = ctx.isEnglish
            ) { inputs, output ->
                mutableListOf<String>().apply {
                    addAll(listOf("-y", "-i", inputs.first().absolutePath))
                    addAll(listOf("-c:v", vcodec))
                    if (vcodec == "libx264") addAll(listOf("-preset", "medium", "-crf", "23"))
                    addAll(listOf("-c:a", acodec))
                    if (ext == "mp4" || ext == "mkv") addAll(listOf("-movflags", "+faststart"))
                    add(output.absolutePath)
                }
            }
            flow.collect { p -> emit(p) }
        }.flowOn(Dispatchers.IO)
    },
    object : Tool {
        override val id = "VID-19"
        override val group = ToolGroup.VIDEO
        override val engineLabel = "FF"
        override val icon = Icons.Filled.Videocam
        override val inputSpec = InputSpec.SingleVideo
        override val params = emptyList<ToolParam>()
        override fun title(isEnglish: Boolean) = if (isEnglish) "Reverse Video" else "Putar Balik Video"
        override fun description(isEnglish: Boolean) =
            if (isEnglish) "Play video and audio backwards." else "Balik arah pemutaran video dan audio."

        override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
            val uri = input.uris.firstOrNull() ?: error("Pilih 1 video")
            val probe = MediaProbe.probe(ctx.appContext, uri)
            require(probe.hasVideo) { if (ctx.isEnglish) "Unsupported or empty video file" else "File video tidak didukung atau kosong" }
            if (probe.durationMs > 0L) {
                require(probe.durationMs <= 90_000L) {
                    if (ctx.isEnglish) "Video too long to reverse safely (max 90s)" else "Video terlalu panjang untuk dibalik aman (maks 90 detik)"
                }
            }
            val hasAudio = probe.hasAudio
            val job = MediaJob()
            val flow = job.runCommand(
                context = ctx.appContext,
                storage = ctx.storage,
                inputUris = listOf(uri),
                toolTag = "VID_REVERSE",
                outputExt = "mp4",
                outputMime = "video/mp4",
                isEnglish = ctx.isEnglish
            ) { inputs, output ->
                if (hasAudio) {
                    listOf(
                        "-y", "-i", inputs.first().absolutePath,
                        "-vf", "reverse", "-af", "areverse",
                        "-c:v", "libx264", "-preset", "medium", "-crf", "23",
                        "-c:a", "aac", "-b:a", "128k",
                        "-movflags", "+faststart", output.absolutePath
                    )
                } else {
                    listOf(
                        "-y", "-i", inputs.first().absolutePath,
                        "-vf", "reverse",
                        "-c:v", "libx264", "-preset", "medium", "-crf", "23",
                        "-an", "-movflags", "+faststart", output.absolutePath
                    )
                }
            }
            flow.collect { p -> emit(p) }
        }.flowOn(Dispatchers.IO)
    },
    object : Tool {
        override val id = "VID-20"
        override val group = ToolGroup.VIDEO
        override val engineLabel = "FF"
        override val icon = Icons.Filled.Videocam
        override val inputSpec = InputSpec.SingleVideo
        override val params = listOf(
            ToolParam.SliderParam("crf", "Kompresi", "Compression", 18f, 35f, 24f, 1f, "")
        )
        override fun title(isEnglish: Boolean) = if (isEnglish) "Video Compressor" else "Kompresor Video"
        override fun description(isEnglish: Boolean) =
            if (isEnglish) "Reduce video size with CRF control." else "Perkecil ukuran file video dengan kontrol CRF."

        override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
            val uri = input.uris.firstOrNull() ?: error("Pilih 1 video")
            val probe = MediaProbe.probe(ctx.appContext, uri)
            require(probe.hasVideo) { if (ctx.isEnglish) "Unsupported or empty video file" else "File video tidak didukung atau kosong" }
            val crf = input.getFloat("crf", 24f).toInt().coerceIn(18, 35)
            val job = MediaJob()
            val flow = job.runCommand(
                context = ctx.appContext,
                storage = ctx.storage,
                inputUris = listOf(uri),
                toolTag = "VID_COMPRESS",
                outputExt = "mp4",
                outputMime = "video/mp4",
                isEnglish = ctx.isEnglish
            ) { inputs, output ->
                listOf(
                    "-y", "-i", inputs.first().absolutePath,
                    "-c:v", "libx264", "-preset", "medium", "-crf", crf.toString(),
                    "-c:a", "aac", "-b:a", "96k",
                    "-movflags", "+faststart", output.absolutePath
                )
            }
            flow.collect { p -> emit(p) }
        }.flowOn(Dispatchers.IO)
    },
    object : Tool {
        override val id = "VID-21"
        override val group = ToolGroup.VIDEO
        override val engineLabel = "NAT"
        override val icon = Icons.Filled.CallSplit
        override val inputSpec = InputSpec.SingleVideo
        override val params = listOf(
            ToolParam.SliderParam("segSec", "Durasi per Bagian (detik)", "Segment Duration (sec)", 3f, 60f, 10f, 1f, "s")
        )
        override fun title(isEnglish: Boolean) = if (isEnglish) "Video Splitter" else "Pecah Video (.zip)"
        override fun description(isEnglish: Boolean) =
            if (isEnglish) "Split video into equal MP4 parts inside a ZIP." else "Pecah video menjadi beberapa bagian MP4 berdurasi sama dalam ZIP."

        override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
            val uri = input.uris.firstOrNull() ?: error("Pilih 1 video")
            val probe = MediaProbe.probe(ctx.appContext, uri)
            require(probe.hasVideo) { if (ctx.isEnglish) "Unsupported or empty video file" else "File video tidak didukung atau kosong" }
            emit(ToolProgress.Running(30, "Memecah segmen video..."))
            val dest = ctx.storage.createOutputFile("VID_SPLIT", "zip")
            val (_, parts) = VideoEngine.splitVideoToZip(ctx.appContext, ctx.storage, uri, dest, input.getFloat("segSec", 10f).toInt())
            emit(ToolProgress.Done(ToolResult(listOf(ToolOutputFile(dest, "application/zip", dest.name)), summary = "$parts segmen dikemas ke ZIP")))
        }.flowOn(Dispatchers.IO)
    },
    object : Tool {
        override val id = "VID-22"
        override val group = ToolGroup.VIDEO
        override val engineLabel = "NAT"
        override val icon = Icons.Filled.BurstMode
        override val inputSpec = InputSpec.SingleVideo
        override val params = listOf(
            ToolParam.SliderParam("intervalSec", "Interval Frame (detik)", "Frame Interval (sec)", 1f, 10f, 2f, 1f, "s"),
            ToolParam.SliderParam("maxFrames", "Maksimal Frame", "Max Frames", 5f, 30f, 12f, 1f, "")
        )
        override fun title(isEnglish: Boolean) = if (isEnglish) "Extract Video Frames" else "Ekstrak Frame Video"
        override fun description(isEnglish: Boolean) =
            if (isEnglish) "Capture PNG frames at fixed intervals into a ZIP." else "Ambil tangkapan frame PNG dari video per interval detik."

        override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
            val uri = input.uris.firstOrNull() ?: error("Pilih 1 video")
            val probe = MediaProbe.probe(ctx.appContext, uri)
            require(probe.hasVideo) { if (ctx.isEnglish) "Unsupported or empty video file" else "File video tidak didukung atau kosong" }
            emit(ToolProgress.Running(35, "Mengambil frame video..."))
            val dest = ctx.storage.createOutputFile("VID_FRAMES", "zip")
            val (_, count) = VideoEngine.extractFramesZip(
                ctx.appContext,
                uri,
                dest,
                input.getFloat("intervalSec", 2f).toInt(),
                input.getFloat("maxFrames", 12f).toInt()
            )
            emit(ToolProgress.Done(ToolResult(listOf(ToolOutputFile(dest, "application/zip", dest.name)), summary = "$count frame PNG diekstrak ke ZIP")))
        }.flowOn(Dispatchers.IO)
    },
    object : Tool {
        override val id = "VID-24"
        override val group = ToolGroup.VIDEO
        override val engineLabel = "NAT"
        override val icon = Icons.Filled.VolumeOff
        override val inputSpec = InputSpec.SingleVideo
        override val params = emptyList<ToolParam>()
        override fun title(isEnglish: Boolean) = if (isEnglish) "Remove Video Audio" else "Bisukan Video"
        override fun description(isEnglish: Boolean) =
            if (isEnglish) "Strip audio track and keep clean silent video." else "Hapus track suara dari video tanpa mengurangi kualitas gambar."

        override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
            val uri = input.uris.firstOrNull() ?: error("Pilih 1 video")
            val probe = MediaProbe.probe(ctx.appContext, uri)
            require(probe.hasVideo) { if (ctx.isEnglish) "Unsupported or empty video file" else "File video tidak didukung atau kosong" }
            require(probe.hasAudio) { if (ctx.isEnglish) "Video has no audio track" else "Video tidak memiliki trek audio" }
            emit(ToolProgress.Running(40, "Menghapus track audio..."))
            val dest = ctx.storage.createOutputFile("VID_MUTE", "mp4")
            VideoEngine.trimOrCopyTracks(ctx.appContext, uri, dest, includeVideo = true, includeAudio = false)
            emit(ToolProgress.Done(ToolResult(listOf(ToolOutputFile(dest, "video/mp4", dest.name)), summary = "Video bisu selesai (${ctx.storage.formatBytes(dest.length())})")))
        }.flowOn(Dispatchers.IO)
    }
)

class DedicatedTool(
    override val id: String,
    override val group: ToolGroup,
    override val engineLabel: String,
    override val icon: ImageVector,
    override val dedicatedRoute: DedicatedScreenRoute,
    private val titleId: String,
    private val titleEn: String,
    private val descId: String,
    private val descEn: String
) : Tool {
    override val inputSpec = InputSpec.None
    override val params = emptyList<ToolParam>()
    override fun title(isEnglish: Boolean) = if (isEnglish) titleEn else titleId
    override fun description(isEnglish: Boolean) = if (isEnglish) descEn else descId
    override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
        emit(ToolProgress.Done(ToolResult(summary = title(ctx.isEnglish))))
    }
}
