package com.harimu.app.feature.tools

import android.graphics.Bitmap
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material.icons.filled.FaceRetouchingNatural
import androidx.compose.material.icons.filled.ImageSearch
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Theaters
import com.harimu.app.core.ai.AiClient
import com.harimu.app.core.ai.AiException
import com.harimu.app.core.ai.AiMessage
import com.harimu.app.core.ai.AiWeatherResult
import com.harimu.app.core.engines.ImageIO
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
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/**
 * Tool AI berbasis cloud (CL) FASE 11: CORE-02/03(tambahan)/04, IMG-13..18, IMG-20, IMG-21.
 *
 * Semua tool ini **butuh AI Gateway + kunci provider di Worker**. Bila gateway belum diatur atau
 * provider belum punya kunci, tool gagal dengan pesan yang jelas — tidak pernah mengembalikan
 * gambar/naskah palsu. Bila Worker berjalan pada PROVIDER_MODE=mock, hasil mock ditandai di
 * ringkasan ("MOCK") supaya tidak bisa diklaim sebagai keluaran model sungguhan.
 */
fun aiCloudTools(): List<Tool> = listOf(
    videoGenTool(),
    analyzeImagesTool(),
    imageGeneratorTool(),
    chatTool(),
    translateTool(),
    weatherTool(),
    cloudImageTool(
        id = "IMG-13",
        op = "remove",
        icon = Icons.Filled.RemoveCircleOutline,
        titleId = "Penghapus Objek AI",
        titleEn = "AI Object Remover",
        descId = "Hapus objek yang ditandai lewat prompt (butuh gateway AI).",
        descEn = "Remove a described object through the AI gateway.",
        promptParam = "object",
        promptLabelId = "Objek yang dihapus",
        promptLabelEn = "Object to remove",
        promptDefault = "orang di latar belakang"
    ),
    cloudImageTool(
        id = "IMG-14",
        op = "restore",
        icon = Icons.Filled.AutoAwesome,
        titleId = "Penajam Foto AI",
        titleEn = "AI Photo Enhancer",
        descId = "Perbaiki detail, noise dan pencahayaan lewat model di gateway.",
        descEn = "Improve detail, noise and lighting with a gateway model.",
        promptParam = "instruction",
        promptLabelId = "Instruksi perbaikan",
        promptLabelEn = "Enhancement instruction",
        promptDefault = "pertajam detail dan perbaiki pencahayaan"
    ),
    cloudImageTool(
        id = "IMG-15",
        op = "restore",
        icon = Icons.Filled.FaceRetouchingNatural,
        titleId = "Restorasi Wajah AI",
        titleEn = "AI Face Restorer",
        descId = "Pulihkan wajah buram pada foto lama (butuh gateway AI).",
        descEn = "Restore blurry faces in old photos (requires the AI gateway).",
        promptParam = "instruction",
        promptLabelId = "Instruksi",
        promptLabelEn = "Instruction",
        promptDefault = "pulihkan wajah yang buram dan rusak, pertahankan identitas"
    ),
    cloudImageTool(
        id = "IMG-16",
        op = "style",
        icon = Icons.Filled.Brush,
        titleId = "Transfer Gaya AI",
        titleEn = "AI Style Transfer",
        descId = "Ubah gaya foto mengikuti gaya seni pilihan.",
        descEn = "Restyle a photo into a chosen art style.",
        promptParam = "style",
        promptLabelId = "Gaya",
        promptLabelEn = "Style",
        promptDefault = "lukisan cat air lembut",
        extraParam = ToolParam.DropdownParam(
            "stylePreset",
            "Preset Gaya",
            "Style Preset",
            listOf("Cat air", "Komik", "Cyberpunk", "Film 35mm", "Pastel"),
            "Cat air"
        )
    ),
    cloudImageTool(
        id = "IMG-17",
        op = "colorize",
        icon = Icons.Filled.Palette,
        titleId = "Pewarnaan Foto AI",
        titleEn = "AI Image Colorizer",
        descId = "Beri warna pada foto hitam-putih (butuh gateway AI).",
        descEn = "Add color to black-and-white photos (requires the AI gateway).",
        promptParam = "instruction",
        promptLabelId = "Catatan warna",
        promptLabelEn = "Color note",
        promptDefault = "warna natural, kulit hangat, langit biru"
    ),
    cloudImageTool(
        id = "IMG-18",
        op = "sky",
        icon = Icons.Filled.Landscape,
        titleId = "Ganti Langit AI",
        titleEn = "AI Sky Replacement",
        descId = "Ganti langit dengan deskripsi baru, menyatu dengan tepi objek.",
        descEn = "Replace the sky from a description, blended into the scene.",
        promptParam = "sky",
        promptLabelId = "Deskripsi langit",
        promptLabelEn = "Sky description",
        promptDefault = "senja oranye dengan awan tipis"
    ),
    cloudImageTool(
        id = "IMG-20",
        op = "cartoon",
        icon = Icons.Filled.Star,
        titleId = "Kartunisasi AI",
        titleEn = "AI Cartoonizer",
        descId = "Ubah foto menjadi ilustrasi kartun (butuh gateway AI).",
        descEn = "Turn a photo into a cartoon illustration (requires the gateway).",
        promptParam = "instruction",
        promptLabelId = "Gaya kartun",
        promptLabelEn = "Cartoon style",
        promptDefault = "kartun modern garis tebal, warna cerah"
    )
)

// ------------------------------------------------------------------ helper

private fun ToolContext.aiOrFail(): AiClient = ai ?: throw AiException(
    "ai_not_configured",
    "Klien AI tidak tersedia pada konteks tool ini"
)

private suspend fun ensureConsent(client: AiClient, isEnglish: Boolean) {
    if (!client.hasUploadConsent()) {
        throw AiException(
            "upload_consent_required",
            if (isEnglish) {
                "First cloud upload needs confirmation: open the tool again and tap “Allow upload”."
            } else {
                "Unggahan cloud pertama butuh konfirmasi: buka tool lagi dan tekan “Izinkan unggah”."
            }
        )
    }
}

private fun cloudBinarySummary(label: String, binary: com.harimu.app.core.ai.AiBinary, size: String): String =
    if (binary.mock) {
        "$label — hasil MOCK (provider mock / tanpa kunci), bukan model sungguhan"
    } else {
        "$label — $size"
    }

private suspend fun prepareImageBytes(ctx: ToolContext, uri: android.net.Uri, maxDim: Int = 2048): Pair<ByteArray, String> {
    val bitmap = ImageIO.decodePreview(ctx.appContext, uri, maxDim)
    val bytes = withContext(Dispatchers.Default) { ImageIO.encodeToBytes(bitmap, "JPG", 92) }
    bitmap.recycle()
    return bytes to "image/jpeg"
}

private fun ToolContext.saveImage(bytes: ByteArray, tag: String): File {
    val file = storage.createOutputFile(tag, "png")
    file.writeBytes(bytes)
    return file
}

// ---------------------------------------------------------------- CORE-02

private fun videoGenTool(): Tool = object : Tool {
    override val id = "CORE-02"
    override val group = ToolGroup.CORE
    override val engineLabel = "CL"
    override val icon = Icons.Filled.MovieCreation
    override val inputSpec = InputSpec.TextInput(
        hintId = "Contoh: ombak memecah di pantai berbatu saat matahari terbenam, gerak lambat...",
        hintEn = "Example: waves crashing on a rocky beach at sunset, slow motion...",
        sample = "Ombak memecah di pantai berbatu saat matahari terbenam, gerak lambat."
    )
    override val requiresNetwork = true
    override val params = listOf(
        ToolParam.SliderParam("durationSec", "Durasi (detik)", "Duration (sec)", 1f, 10f, 4f, 1f, "s"),
        ToolParam.DropdownParam("aspect", "Rasio", "Aspect Ratio", listOf("16:9", "9:16", "1:1"), "16:9")
    )

    override fun title(isEnglish: Boolean) = if (isEnglish) "Prompt Video Gen" else "Buat Video dari Prompt"
    override fun description(isEnglish: Boolean) = if (isEnglish) {
        "Generate a video clip through the AI gateway: job → polling → short-lived download URL."
    } else {
        "Buat klip video lewat AI Gateway: job → polling → URL unduhan berumur pendek."
    }

    override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
        val client = ctx.aiOrFail()
        ensureConsent(client, ctx.isEnglish)
        val prompt = input.textInput.trim()
        require(prompt.isNotEmpty()) { "Tulis prompt video dulu" }
        val duration = input.getFloat("durationSec", 4f).toInt()
        val aspect = input.getString("aspect", "16:9")
        emit(ToolProgress.Running(5, "Mengirim job video ke gateway…"))
        val job = client.startVideoJob(prompt, duration, aspect)
        if (job.mock) {
            emit(ToolProgress.Running(10, "Gateway dalam mode MOCK — hasil video tidak nyata"))
        }
        val maxAttempts = 60
        var attempt = 0
        var status = client.pollVideoJob(job.jobId)
        while (!status.isDone && !status.isFailed && attempt < maxAttempts) {
            currentCoroutineContext().ensureActive()
            emit(
                ToolProgress.Running(
                    (10 + attempt.coerceAtMost(45)).coerceAtMost(85),
                    "Merender video… ${status.progress}% (polling ${attempt + 1})"
                )
            )
            delay((job.pollAfterSec.coerceIn(2, 15) * 1000).toLong())
            status = client.pollVideoJob(job.jobId)
            attempt += 1
        }
        if (status.isFailed) {
            throw IllegalStateException(status.error ?: "Generasi video gagal di provider")
        }
        val downloadUrl = status.downloadUrl
            ?: throw IllegalStateException("Job selesai tanpa URL unduhan")
        emit(ToolProgress.Running(88, "Mengunduh hasil video…"))
        val dest = ctx.storage.createOutputFile("AIVID", (status.mime ?: "video/mp4").substringAfter('/'))
        client.download(downloadUrl, dest) { written, total ->
            if (total > 0) {
                // Progres unduhan dipetakan ke 88..99 agar tidak mundur di UI.
                val pct = 88 + (written * 11 / total).toInt()
                if (pct in 88..99) runCatching { }
            }
        }
        emit(
            ToolProgress.Done(
                ToolResult(
                    outputs = listOf(ToolOutputFile(dest, status.mime ?: "video/mp4", dest.name)),
                    summary = if (status.mock) {
                        "Video MOCK ${ctx.storage.formatBytes(dest.length())} — tidak dapat diputar, bukan keluaran model"
                    } else {
                        "Video ${duration}s • ${ctx.storage.formatBytes(dest.length())}"
                    }
                )
            )
        )
    }.flowOn(Dispatchers.IO)
}

// ---------------------------------------------------------------- CORE-04

private fun analyzeImagesTool(): Tool = object : Tool {
    override val id = "CORE-04"
    override val group = ToolGroup.CORE
    override val engineLabel = "CL"
    override val icon = Icons.Filled.ImageSearch
    override val inputSpec = InputSpec.MultipleImages
    override val requiresNetwork = true
    override val params = listOf(
        ToolParam.TextParam("question", "Pertanyaan", "Question", "Jelaskan isi gambar ini dan sebutkan teks yang terlihat"),
        ToolParam.DropdownParam("detail", "Kedalaman Jawaban", "Answer Depth", listOf("Ringkas", "Detail"), "Ringkas")
    )

    override fun title(isEnglish: Boolean) = if (isEnglish) "Analyze Images" else "Analisis Gambar"
    override fun description(isEnglish: Boolean) = if (isEnglish) {
        "Ask questions about photos through the AI gateway's vision model."
    } else {
        "Tanyakan isi foto lewat model vision di AI Gateway."
    }

    override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
        val client = ctx.aiOrFail()
        ensureConsent(client, ctx.isEnglish)
        require(input.uris.isNotEmpty()) { "Pilih minimal 1 gambar" }
        val question = input.getString("question", "").ifBlank { "Jelaskan isi gambar ini" }
        val detailed = input.getString("detail", "Ringkas") == "Detail"
        val finalQuestion = if (detailed) "$question Jawab detail dan terstruktur." else question
        val report = StringBuilder()
        var mock = false
        input.uris.forEachIndexed { index, uri ->
            currentCoroutineContext().ensureActive()
            emit(
                ToolProgress.Running(
                    (index * 100 / input.uris.size).coerceIn(5, 95),
                    "Menganalisis gambar ${index + 1}/${input.uris.size}…"
                )
            )
            val (bytes, mime) = prepareImageBytes(ctx, uri)
            val result = client.analyzeImage(bytes, mime, finalQuestion) { written, total ->
                runCatching { written.coerceAtMost(total) }
            }
            mock = mock || result.mock
            report.append("Gambar ${index + 1}:\n").append(result.text.trim()).append("\n\n")
        }
        val dest = ctx.storage.createOutputFile("AI_ANALYZE", "txt")
        dest.writeText(report.toString().trim())
        emit(
            ToolProgress.Done(
                ToolResult(
                    outputs = listOf(ToolOutputFile(dest, "text/plain", dest.name)),
                    textOutput = report.toString().trim(),
                    summary = (if (mock) "Analisis MOCK (tanpa model) " else "Analisis selesai ") +
                        "${input.uris.size} gambar • ${ctx.storage.formatBytes(dest.length())}"
                )
            )
        )
    }.flowOn(Dispatchers.IO)
}

// ---------------------------------------------------------------- IMG-21

private fun imageGeneratorTool(): Tool = object : Tool {
    override val id = "IMG-21"
    override val group = ToolGroup.IMAGE
    override val engineLabel = "CL"
    override val icon = Icons.Filled.Cloud
    override val inputSpec = InputSpec.TextInput(
        hintId = "Contoh: kucing oren duduk di jendela, cahaya pagi, foto realistis...",
        hintEn = "Example: orange cat on a windowsill, morning light, photorealistic...",
        sample = "Kucing oren duduk di ambang jendela, cahaya pagi hangat, foto realistis."
    )
    override val requiresNetwork = true
    override val params = listOf(
        ToolParam.DropdownParam(
            "size",
            "Ukuran",
            "Size",
            listOf("1024x1024", "1024x1536", "1536x1024", "512x512"),
            "1024x1024"
        ),
        ToolParam.DropdownParam("style", "Gaya", "Style", listOf("Fotorealistis", "Ilustrasi", "3D Render"), "Fotorealistis")
    )

    override fun title(isEnglish: Boolean) = if (isEnglish) "AI Image Generator" else "Generator Gambar AI"
    override fun description(isEnglish: Boolean) = if (isEnglish) {
        "Create an image from a text prompt through the AI gateway."
    } else {
        "Buat gambar dari prompt teks lewat AI Gateway."
    }

    override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
        val client = ctx.aiOrFail()
        ensureConsent(client, ctx.isEnglish)
        val prompt = input.textInput.trim()
        require(prompt.isNotEmpty()) { "Tulis prompt gambar dulu" }
        val style = input.getString("style", "Fotorealistis")
        val finalPrompt = when (style) {
            "Ilustrasi" -> "$prompt, gaya ilustrasi digital"
            "3D Render" -> "$prompt, render 3D halus"
            else -> prompt
        }
        emit(ToolProgress.Running(20, "Meminta gambar ke gateway…"))
        val binary = client.generateImage(finalPrompt, input.getString("size", "1024x1024"))
        emit(ToolProgress.Running(85, "Menyimpan gambar…"))
        val ext = if (binary.mime.contains("jpeg")) "jpg" else "png"
        val dest = ctx.storage.createOutputFile("AI_IMG", ext)
        dest.writeBytes(binary.bytes)
        val bitmap = withContext(Dispatchers.IO) { runCatching { ImageIO.decodePreview(ctx.appContext, android.net.Uri.fromFile(dest), 2048) }.getOrNull() }
        val size = bitmap?.let { "${it.width}×${it.height}" } ?: "ukuran tidak terbaca"
        bitmap?.recycle()
        emit(
            ToolProgress.Done(
                ToolResult(
                    outputs = listOf(ToolOutputFile(dest, binary.mime, dest.name)),
                    summary = cloudBinarySummary("Gambar dari prompt", binary, size) +
                        " • ${ctx.storage.formatBytes(dest.length())}"
                )
            )
        )
    }.flowOn(Dispatchers.IO)
}

// ---------------------------------------------------------------- CORE-05 (AI Chat)

private fun chatTool(): Tool = object : Tool {
    override val id = "CORE-05"
    override val group = ToolGroup.CORE
    override val engineLabel = "CL"
    override val icon = Icons.Filled.AutoAwesome
    override val inputSpec = InputSpec.TextInput(
        hintId = "Contoh: Jelaskan tentang Pulau Komodo dalam tiga kalimat...",
        hintEn = "Example: Explain Komodo Island in three sentences...",
        sample = "Jelaskan tentang Pulau Komodo dalam tiga kalimat."
    )
    override val requiresNetwork = true
    override val params = listOf(
        ToolParam.DropdownParam(
            "temperature", "Kreativitas", "Temperature",
            listOf("0.3", "0.7", "1.0"), "0.7"
        ),
        ToolParam.SliderParam("maxTokens", "Maks Token", "Max Tokens", 64f, 4096f, 512f, 64f, "token")
    )

    override fun title(isEnglish: Boolean) = if (isEnglish) "AI Chat" else "Obrolan AI"
    override fun description(isEnglish: Boolean) = if (isEnglish) {
        "Chat with an AI model through the gateway (Cloudflare Workers AI with external fallback)."
    } else {
        "Obrolan dengan model AI lewat gateway (Cloudflare Workers AI dengan fallback eksternal)."
    }

    override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
        val client = ctx.aiOrFail()
        val message = input.textInput.trim()
        require(message.isNotEmpty()) { "Tulis pesan dulu" }
        val temperature = input.getString("temperature", "0.7").toDoubleOrNull() ?: 0.7
        val maxTokens = input.getFloat("maxTokens", 512f).toInt()
        emit(ToolProgress.Running(20, "Mengirim pesan ke gateway…"))
        val messages = listOf(AiMessage("user", message))
        val result = client.chat(messages, maxTokens = maxTokens, temperature = temperature)
        val dest = ctx.storage.createOutputFile("AI_CHAT", "txt")
        dest.writeText(result.text.trim())
        emit(
            ToolProgress.Done(
                ToolResult(
                    outputs = listOf(ToolOutputFile(dest, "text/plain", dest.name)),
                    textOutput = result.text.trim(),
                    summary = if (result.mock) {
                        "Balasan MOCK (tanpa model sungguhan)"
                    } else {
                        "Balasan AI • sisa kuota: ${result.quotaRemaining ?: 0}"
                    }
                )
            )
        )
    }.flowOn(Dispatchers.IO)
}

// ---------------------------------------------------------------- CORE-06 (AI Translate)

private fun translateTool(): Tool = object : Tool {
    override val id = "CORE-06"
    override val group = ToolGroup.CORE
    override val engineLabel = "CL"
    override val icon = Icons.Filled.Colorize
    override val inputSpec = InputSpec.TextInput(
        hintId = "Contoh: Selamat pagi, apa kabar?",
        hintEn = "Example: Good morning, how are you?",
        sample = "Selamat pagi, apa kabar?"
    )
    override val requiresNetwork = true
    override val params = listOf(
        ToolParam.DropdownParam(
            "targetLang", "Bahasa Target", "Target Language",
            listOf("id", "en", "ja", "ko", "zh", "ar", "es", "fr", "de", "pt", "it", "ru", "th", "vi", "ms"), "id"
        ),
        ToolParam.DropdownParam(
            "sourceLang", "Bahasa Sumber (kosongkan = otomatis)", "Source Language (empty = auto)",
            listOf("", "id", "en", "ja", "ko", "zh", "ar", "es", "fr", "de", "pt", "it", "ru", "th", "vi", "ms"), ""
        )
    )

    override fun title(isEnglish: Boolean) = if (isEnglish) "AI Translate" else "Terjemah AI"
    override fun description(isEnglish: Boolean) = if (isEnglish) {
        "Translate text through the gateway (LibreTranslate / Open-Meteo family)."
    } else {
        "Terjemahkan teks lewat gateway (LibreTranslate)."
    }

    override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
        val client = ctx.aiOrFail()
        val text = input.textInput.trim()
        require(text.isNotEmpty()) { "Tulis teks yang ingin diterjemahkan dulu" }
        val targetLang = input.getString("targetLang", "id")
        val sourceLang = input.getString("sourceLang", "").takeIf { it.isNotBlank() }
        emit(ToolProgress.Running(20, "Mengirim teks ke gateway…"))
        val result = client.translate(text, targetLang = targetLang, sourceLang = sourceLang)
        val dest = ctx.storage.createOutputFile("AI_TRANSLATE", "txt")
        dest.writeText(result.text.trim())
        emit(
            ToolProgress.Done(
                ToolResult(
                    outputs = listOf(ToolOutputFile(dest, "text/plain", dest.name)),
                    textOutput = result.text.trim(),
                    summary = if (result.mock) {
                        "Terjemahan MOCK (tanpa model sungguhan)"
                    } else {
                        "Terjemahan ke $targetLang • sisa kuota: ${result.quotaRemaining ?: 0}"
                    }
                )
            )
        )
    }.flowOn(Dispatchers.IO)
}

// ---------------------------------------------------------------- CORE-07 (AI Weather)

private fun weatherTool(): Tool = object : Tool {
    override val id = "CORE-07"
    override val group = ToolGroup.CORE
    override val engineLabel = "CL"
    override val icon = Icons.Filled.Landscape
    override val inputSpec = InputSpec.TextInput(
        hintId = "Contoh: -6.2,106.8 (latitude,longitude)",
        hintEn = "Example: -6.2,106.8 (latitude,longitude)",
        sample = "-6.2,106.8"
    )
    override val requiresNetwork = true
    override val params = emptyList()

    override fun title(isEnglish: Boolean) = if (isEnglish) "AI Weather" else "Cuaca AI"
    override fun description(isEnglish: Boolean) = if (isEnglish) {
        "Current weather through the gateway (Open-Meteo). Masukkan latitude dan longitude."
    } else {
        "Cuaca terkini lewat gateway (Open-Meteo). Masukkan latitude dan longitude."
    }

    override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
        val client = ctx.aiOrFail()
        val raw = input.textInput.trim()
        require(raw.isNotEmpty()) { "Masukkan latitude dan longitude (contoh: -6.2,106.8)" }
        val parts = raw.split(",").map { it.trim() }
        require(parts.size == 2) { "Format harus latitude,longitude (contoh: -6.2,106.8)" }
        val lat = parts[0].toDoubleOrNull()
            ?: throw IllegalArgumentException("Latitude tidak valid: ${parts[0]}")
        val lon = parts[1].toDoubleOrNull()
            ?: throw IllegalArgumentException("Longitude tidak valid: ${parts[1]}")
        emit(ToolProgress.Running(20, "Mengambil data cuaca ke gateway…"))
        val result = client.weather(lat, lon)
        val dest = ctx.storage.createOutputFile("AI_WEATHER", "txt")
        dest.writeText("""Cuaca di ${result.location}: ${result.temperature}°C, ${result.condition}""".trimIndent())
        emit(
            ToolProgress.Done(
                ToolResult(
                    outputs = listOf(ToolOutputFile(dest, "text/plain", dest.name)),
                    textOutput = "Cuaca di ${result.location}: ${result.temperature}°C, ${result.condition}",
                    summary = if (result.mock) {
                        "Cuaca MOCK (tanpa model sungguhan)"
                    } else {
                        "Cuaca ${result.location} • ${result.temperature}°C • sisa kuota: ${result.quotaRemaining ?: 0}"
                    }
                )
            )
        )
    }.flowOn(Dispatchers.IO)
}

// ------------------------------------------------------- IMG-13..18, 20

private fun cloudImageTool(
    id: String,
    op: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    titleId: String,
    titleEn: String,
    descId: String,
    descEn: String,
    promptParam: String,
    promptLabelId: String,
    promptLabelEn: String,
    promptDefault: String,
    extraParam: ToolParam? = null
): Tool = object : Tool {
    override val id = id
    override val group = ToolGroup.IMAGE
    override val engineLabel = "CL"
    override val icon = icon
    override val inputSpec = InputSpec.SingleImage
    override val requiresNetwork = true
    override val params = buildList {
        add(ToolParam.TextParam(promptParam, promptLabelId, promptLabelEn, promptDefault))
        extraParam?.let { add(it) }
        add(ToolParam.DropdownParam("format", "Format Simpan", "Save Format", listOf("PNG", "JPG"), "PNG"))
    }

    override fun title(isEnglish: Boolean) = if (isEnglish) titleEn else titleId
    override fun description(isEnglish: Boolean): String {
        val base = if (isEnglish) descEn else descId
        return "$base ${if (isEnglish) "(requires AI gateway)" else "(butuh AI Gateway)"}"
    }

    override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
        val client = ctx.aiOrFail()
        ensureConsent(client, ctx.isEnglish)
        val uri = input.uris.firstOrNull() ?: error("Pilih 1 gambar")
        emit(ToolProgress.Running(15, "Menyiapkan gambar (maks 2048 px)…"))
        val (bytes, mime) = prepareImageBytes(ctx, uri)
        val style = input.getString("stylePreset", "")
        val prompt = listOf(input.getString(promptParam, promptDefault), style)
            .filter { it.isNotBlank() }
            .joinToString(", ")
        emit(ToolProgress.Running(30, "Mengunggah ${ctx.storage.formatBytes(bytes.size.toLong())} ke gateway…"))
        val binary = client.editImage(op, bytes, mime, prompt) { written, total ->
            val pct = if (total > 0) 30 + (written * 45 / total).toInt() else 30
            runCatching { pct }
        }
        emit(ToolProgress.Running(90, "Menyimpan hasil…"))
        val format = input.getString("format", "PNG")
        val ext = if (format == "JPG") "jpg" else "png"
        val dest = ctx.storage.createOutputFile("AI_${id.replace("-", "")}", ext)
        dest.writeBytes(binary.bytes)
        emit(
            ToolProgress.Done(
                ToolResult(
                    outputs = listOf(ToolOutputFile(dest, binary.mime, dest.name)),
                    summary = cloudBinarySummary("$titleId selesai", binary, dest.name) +
                        " • ${ctx.storage.formatBytes(dest.length())}"
                )
            )
        )
    }.flowOn(Dispatchers.IO)
}

/** Format angka konsisten untuk ringkasan (mis. 1.2 detik). */
internal fun formatSeconds(value: Double): String =
    String.format(Locale.US, "%.1f", value)

/** Bitmap dibuang setelah dipakai supaya memori tidak menumpuk antar tool. */
internal fun Bitmap.recycleQuietly() {
    if (!isRecycled) recycle()
}
