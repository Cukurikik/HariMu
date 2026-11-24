package com.harimu.app.feature.tools

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.BurstMode
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.ui.graphics.vector.ImageVector
import com.harimu.app.core.ai.AiException
import com.harimu.app.core.engines.AudioEngine
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
        id = "CORE-01",
        group = ToolGroup.CORE,
        engineLabel = "M3+FF",
        icon = Icons.Filled.MovieCreation,
        dedicatedRoute = DedicatedScreenRoute.VIDEO_EDITOR,
        titleId = "Pro Video Editor",
        titleEn = "Pro Video Editor",
        descId = "Editor timeline video: susun klip, trim, kecepatan, rotasi, dan gabung.",
        descEn = "Timeline video editor: arrange clips, trim, speed, rotate, and merge."
    ),
    DedicatedTool(
        id = "CORE-05",
        group = ToolGroup.CORE,
        engineLabel = "MP",
        icon = Icons.Filled.Videocam,
        dedicatedRoute = DedicatedScreenRoute.SCREEN_RECORDER,
        titleId = "Screen Recorder",
        titleEn = "Screen Recorder",
        descId = "Rekam layar via MediaProjection, audio mikrofon/internal, kontrol notifikasi & overlay.",
        descEn = "Record screen via MediaProjection with mic/internal audio, notification controls & overlay."
    ),
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
            ToolParam.DropdownParam(
                "engine",
                "Mesin Suara",
                "Voice Engine",
                listOf("Perangkat (offline)", "Gateway – 5 suara (cloud)"),
                "Perangkat (offline)"
            ),
            ToolParam.DropdownParam(
                "voice",
                "Suara Gateway",
                "Gateway Voice",
                listOf("1 · Alloy", "2 · Echo", "3 · Fable", "4 · Onyx", "5 · Nova"),
                "1 · Alloy"
            ),
            ToolParam.DropdownParam("lang", "Bahasa Suara", "Voice Language", listOf("Indonesia", "English (US)"), "Indonesia"),
            ToolParam.SliderParam("rate", "Kecepatan Bicara", "Speech Rate", 0.5f, 2.0f, 1.0f, 0.1f, "x"),
            ToolParam.SliderParam("pitch", "Nada Suara", "Voice Pitch", 0.6f, 1.8f, 1.0f, 0.1f, "x")
        )
        override fun title(isEnglish: Boolean) = if (isEnglish) "Generate Speech (TTS)" else "Teks ke Suara (TTS)"
        override fun description(isEnglish: Boolean) =
            if (isEnglish) {
                "Synthesize text to WAV with the offline Android engine, or via the AI gateway's 5 cloud voices."
            } else {
                "Ubah teks menjadi WAV dengan engine perangkat (offline) atau 5 suara cloud lewat AI Gateway."
            }

        override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
            val text = input.textInput.trim()
            require(text.isNotEmpty()) { if (ctx.isEnglish) "Enter text first" else "Masukkan teks terlebih dahulu" }
            if (input.getString("engine", "Perangkat (offline)").startsWith("Gateway")) {
                val client = ctx.ai ?: throw AiException(
                    "ai_not_configured",
                    "Klien AI tidak tersedia; atur AI Gateway di Pengaturan."
                )
                if (!client.hasUploadConsent()) {
                    throw AiException(
                        "upload_consent_required",
                        "Unggahan cloud pertama butuh konfirmasi. Jalankan lagi lalu pilih “Izinkan unggah”."
                    )
                }
                val voice = input.getString("voice", "1 · Alloy").substringBefore(' ').toIntOrNull() ?: 1
                val language = if (input.getString("lang", "Indonesia") == "Indonesia") "id" else "en"
                emit(ToolProgress.Running(25, "Meminta suara ke gateway (voice $voice)…"))
                val audio = client.textToSpeech(text, voice, language)
                val cloudDest = ctx.storage.createOutputFile("TTS_CLOUD", "wav")
                cloudDest.writeBytes(audio.bytes)
                emit(
                    ToolProgress.Done(
                        ToolResult(
                            outputs = listOf(ToolOutputFile(cloudDest, "audio/wav", cloudDest.name)),
                            summary = if (audio.mock) {
                                "Suara MOCK (nada 440 Hz), bukan TTS model • ${ctx.storage.formatBytes(cloudDest.length())}"
                            } else {
                                "Voice $voice • ${ctx.storage.formatBytes(cloudDest.length())}"
                            }
                        )
                    )
                )
                return@flow
            }
            emit(ToolProgress.Running(20, if (ctx.isEnglish) "Preparing TTS engine..." else "Menyiapkan engine TTS..."))
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

fun videoTools(): List<Tool> =
    videoForgeBatch1CoreTools() +
        videoForgeBatch1ExtraTools() +
        videoForgeBatch2Tools() +
        videoForgeBatch2ExtraTools()

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
