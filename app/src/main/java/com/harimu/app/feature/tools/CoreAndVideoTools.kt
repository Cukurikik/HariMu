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
            emit(ToolProgress.Running(40, "Menggabungkan ${input.uris.size} klip..."))
            val dest = ctx.storage.createOutputFile("VID_MERGE", "mp4")
            VideoEngine.mergeVideos(ctx.appContext, input.uris, dest)
            emit(ToolProgress.Done(ToolResult(listOf(ToolOutputFile(dest, "video/mp4", dest.name)), summary = "${input.uris.size} video digabung (${ctx.storage.formatBytes(dest.length())})")))
        }.flowOn(Dispatchers.IO)
    },
    object : Tool {
        override val id = "VID-08"
        override val group = ToolGroup.VIDEO
        override val engineLabel = "NAT"
        override val icon = Icons.Filled.MusicNote
        override val inputSpec = InputSpec.SingleVideo
        override val params = emptyList<ToolParam>()
        override fun title(isEnglish: Boolean) = if (isEnglish) "Video to Audio (M4A)" else "Video ke Audio (M4A)"
        override fun description(isEnglish: Boolean) =
            if (isEnglish) "Extract audio track from a video into M4A." else "Ekstrak track audio asli dari video ke file .m4a."

        override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
            val uri = input.uris.firstOrNull() ?: error("Pilih 1 video")
            emit(ToolProgress.Running(35, "Mengekstrak track audio..."))
            val dest = ctx.storage.createOutputFile("VID_AUDIO", "m4a")
            VideoEngine.trimOrCopyTracks(ctx.appContext, uri, dest, includeVideo = false, includeAudio = true)
            emit(ToolProgress.Done(ToolResult(listOf(ToolOutputFile(dest, "audio/mp4", dest.name)), summary = "Audio diekstrak: ${ctx.storage.formatBytes(dest.length())}")))
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
