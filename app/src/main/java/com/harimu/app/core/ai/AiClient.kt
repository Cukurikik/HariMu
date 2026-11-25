package com.harimu.app.core.ai

import com.harimu.app.core.data.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.BufferedSink
import java.io.File
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.TimeUnit
import kotlin.math.min
import kotlin.math.pow

/** Error terstruktur dari AI Gateway: {code, message, retryable}. */
class AiException(
    val code: String,
    override val message: String,
    val retryable: Boolean = false,
    val retryAfterSec: Long? = null,
    val httpStatus: Int = 0
) : Exception(message) {

    val isQuota: Boolean get() = code == "quota_exceeded"
    val isNotConfigured: Boolean get() = code == "ai_not_configured"

    fun userMessage(isEnglish: Boolean): String = when (code) {
        "ai_not_configured" -> if (isEnglish) {
            "AI gateway URL is not set (Settings → AI Gateway)."
        } else {
            "URL AI Gateway belum diatur (Pengaturan → AI Gateway)."
        }
        "quota_exceeded" -> if (isEnglish) {
            "Daily AI quota is used up. Try again tomorrow or reduce usage."
        } else {
            "Kuota AI harian habis. Coba lagi besok atau kurangi pemakaian."
        }
        "rate_limited" -> if (isEnglish) {
            "Too many requests. Wait ${retryAfterSec ?: 30}s and retry."
        } else {
            "Terlalu banyak permintaan. Tunggu ${retryAfterSec ?: 30} detik lalu coba lagi."
        }
        "provider_not_configured" -> if (isEnglish) {
            "The gateway has no provider key for this feature (server configuration)."
        } else {
            "Gateway belum punya kunci provider untuk fitur ini (konfigurasi server)."
        }
        "unauthorized" -> if (isEnglish) "Install token rejected. Reconnect the gateway." else
            "Token instal ditolak. Hubungkan ulang gateway."
        else -> message
    }
}

data class AiTextResult(
    val text: String,
    val mock: Boolean,
    val quotaRemaining: Int?
)

/** Pesan chat dengan peran (system/user/assistant) dan isi konten. */
data class AiMessage(val role: String, val content: String)

data class AiQuota(val used: Int, val limit: Int, val resetsInSec: Long)

data class AiVideoJob(val jobId: String, val mock: Boolean, val pollAfterSec: Int)

data class AiVideoStatus(
    val status: String,
    val progress: Int,
    val downloadUrl: String?,
    val mime: String?,
    val error: String?,
    val mock: Boolean
) {
    val isDone: Boolean get() = status == "done"
    val isFailed: Boolean get() = status == "failed"
}

data class AiBinary(
    val bytes: ByteArray,
    val mime: String,
    val mock: Boolean,
    val quotaRemaining: Int?
)

/** Hasil cuaca dari gateway. */
data class AiWeatherResult(
    val temperature: Double,
    val condition: String,
    val location: String,
    val mock: Boolean,
    val quotaRemaining: Int?
)

/**
 * Klien AI Gateway (FASE 11).
 *
 * - Kunci provider tidak pernah ada di sini; klien hanya memegang token instal per perangkat.
 * - Retry eksponensial hanya untuk error `retryable`, menghormati `retry-after` (maks 30 detik).
 * - Unggahan melaporkan progres per 64 KB, dan pembatalan coroutine membatalkan panggilan HTTP.
 * - Semua hasil membawa flag `mock` supaya UI/katalog bisa jujur soal asal output.
 */
class AiClient(
    private val settings: SettingsRepository
) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false }

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(180, TimeUnit.SECONDS)
        .callTimeout(300, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build()

    private val cachedTokenLock = Any()
    private var cachedToken: String? = null
    private var cachedTokenExpiresAt: Long = 0L

    // ------------------------------------------------------------ konfigurasi

    suspend fun configuredUrl(): String = settings.current().aiGatewayUrl.trim().trimEnd('/')

    suspend fun isConfigured(): Boolean = configuredUrl().isNotBlank()

    suspend fun hasUploadConsent(): Boolean = settings.aiUploadConsent()

    suspend fun grantUploadConsent() = settings.setAiUploadConsent(true)

    suspend fun setGatewayUrl(url: String) = settings.setAiGatewayUrl(url)

    private suspend fun requireBaseUrl(): String {
        val url = configuredUrl()
        if (url.isBlank()) {
            throw AiException(
                "ai_not_configured",
                "URL AI Gateway belum diatur. Buka Pengaturan → AI Gateway dan tempel URL Worker."
            )
        }
        if (!url.startsWith("https://") && !url.startsWith("http://10.0.2.2")) {
            throw AiException(
                "ai_not_configured",
                "URL AI Gateway harus https:// (atau http://10.0.2.2 untuk emulator)."
            )
        }
        return url
    }

    // ----------------------------------------------------------------- token

    private suspend fun installId(): String = settings.aiInstallId()

    suspend fun ensureToken(forceRefresh: Boolean = false): String {
        val base = requireBaseUrl()
        val nowSec = System.currentTimeMillis() / 1000
        synchronized(cachedTokenLock) {
            val token = cachedToken
            if (!forceRefresh && token != null && cachedTokenExpiresAt - 60 > nowSec) return token
        }
        if (!forceRefresh) {
            val (storedToken, storedExpires) = settings.aiToken()
            if (storedToken.isNotBlank() && storedExpires - 60 > nowSec) {
                synchronized(cachedTokenLock) {
                    cachedToken = storedToken
                    cachedTokenExpiresAt = storedExpires
                }
                return storedToken
            }
        }
        val body = """{"installId":"${installId()}"}"""
            .toRequestBody(JSON_MEDIA)
        val request = Request.Builder()
            .url("$base/v1/install")
            .post(body)
            .build()
        val response = execute(request)
        val payload = response.use { readJsonObject(it, "install") }
        val token = payload.string("token")
            ?: throw AiException("bad_response", "Gateway tidak mengembalikan token instal")
        val expiresAt = payload.long("expiresAt") ?: (nowSec + 3600)
        settings.saveAiToken(token, expiresAt)
        synchronized(cachedTokenLock) {
            cachedToken = token
            cachedTokenExpiresAt = expiresAt
        }
        return token
    }

    suspend fun forgetToken() {
        settings.clearAiToken()
        synchronized(cachedTokenLock) {
            cachedToken = null
            cachedTokenExpiresAt = 0L
        }
    }

    // --------------------------------------------------------------- request

    private suspend fun execute(request: Request): Response {
        var attempt = 0
        while (true) {
            currentCoroutineContext().ensureActive()
            val response = try {
                blockingCall(request)
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (io: IOException) {
                if (attempt < MAX_RETRIES) {
                    delay(backoffMs(attempt))
                    attempt += 1
                    continue
                }
                throw AiException(
                    "network_error",
                    "Tidak dapat menghubungi AI Gateway: ${io.message ?: "koneksi gagal"}",
                    retryable = true
                )
            }
            if (response.isSuccessful) return response
            val error = parseError(response)
            response.close()
            if (error.retryable && attempt < MAX_RETRIES) {
                val waitMs = (error.retryAfterSec ?: backoffSec(attempt)).coerceIn(1, 30) * 1000
                delay(waitMs)
                attempt += 1
                continue
            }
            throw error
        }
    }

    /** Panggilan di IO dengan pembatalan: coroutine cancel → call.cancel(). */
    private suspend fun blockingCall(request: Request): Response = withContext(Dispatchers.IO) {
        val call = http.newCall(request)
        val job = currentCoroutineContext()[Job]
        val handle = job?.invokeOnCompletion { cause ->
            if (cause is CancellationException) runCatching { call.cancel() }
        }
        try {
            call.execute()
        } catch (interrupted: InterruptedIOException) {
            currentCoroutineContext().ensureActive()
            throw interrupted
        } finally {
            handle?.dispose()
        }
    }

    private suspend fun authed(
        path: String,
        method: String = "GET",
        bodyFactory: (() -> RequestBody)? = null
    ): Response {
        val base = requireBaseUrl()
        var token = ensureToken()
        fun build(currentToken: String): Request {
            val builder = Request.Builder()
                .url("$base$path")
                .header("authorization", "Bearer $currentToken")
                .header("accept", "application/json, audio/*, image/*")
            val body = bodyFactory?.invoke()
            when (method) {
                "GET" -> builder.get()
                "POST" -> builder.post(body ?: EMPTY_BODY)
                else -> builder.method(method, body ?: EMPTY_BODY)
            }
            return builder.build()
        }
        var response = execute(build(token))
        if (response.code == 401) {
            response.close()
            token = ensureToken(forceRefresh = true)
            response = execute(build(token))
        }
        return response
    }

    // ---------------------------------------------------------------- health

    suspend fun health(): JsonObject {
        val base = requireBaseUrl()
        val response = execute(Request.Builder().url("$base/v1/health").get().build())
        return response.use { readJsonObject(it, "health") }
    }

    suspend fun quota(): AiQuota {
        val payload = authed("/v1/quota").use { readJsonObject(it, "quota") }
        return AiQuota(
            used = payload.int("used") ?: 0,
            limit = payload.int("limit") ?: 0,
            resetsInSec = payload.long("resetsInSec") ?: 0L
        )
    }

    // --------------------------------------------------------------- fitur

    suspend fun textToSpeech(
        text: String,
        voice: Int,
        language: String? = null,
        onUploadProgress: (Long, Long) -> Unit = { _, _ -> }
    ): AiBinary {
        val body = buildString {
            append("{\"text\":").append(jsonString(text))
            append(",\"voice\":").append(voice.coerceIn(1, 5))
            if (!language.isNullOrBlank()) append(",\"language\":").append(jsonString(language))
            append("}")
        }
        val requestBody = body.toRequestBody(JSON_MEDIA)
        onUploadProgress(0, body.toByteArray().size.toLong())
        val response = authed("/v1/text-to-speech", "POST") { requestBody }
        return response.use { toBinary(it, onUploadProgress) }
    }

    suspend fun generateImage(
        prompt: String,
        size: String = "1024x1024"
    ): AiBinary {
        val body = """{"prompt":${jsonString(prompt)},"size":${jsonString(size)}}"""
            .toRequestBody(JSON_MEDIA)
        val response = authed("/v1/image-generate", "POST") { body }
        return response.use { toBinary(it) }
    }

    suspend fun editImage(
        operation: String,
        bytes: ByteArray,
        mime: String,
        prompt: String = "",
        onUploadProgress: (Long, Long) -> Unit = { _, _ -> }
    ): AiBinary {
        val response = authed("/v1/image-edit/$operation", "POST") {
            MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("image", "input.png", ProgressRequestBody(bytes, mime, onUploadProgress))
                .addFormDataPart("prompt", prompt)
                .build()
        }
        return response.use { toBinary(it) }
    }

    suspend fun analyzeImage(
        bytes: ByteArray,
        mime: String,
        question: String,
        onUploadProgress: (Long, Long) -> Unit = { _, _ -> }
    ): AiTextResult {
        val response = authed("/v1/image-analyze", "POST") {
            MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("image", "input.png", ProgressRequestBody(bytes, mime, onUploadProgress))
                .addFormDataPart("question", question)
                .build()
        }
        val payload = response.use { readJsonObject(it, "image-analyze") }
        return AiTextResult(
            text = payload.string("text").orEmpty(),
            mock = payload.boolean("mock") ?: false,
            quotaRemaining = payload.int("quotaRemaining")
        )
    }

    suspend fun speechToText(
        bytes: ByteArray,
        mime: String,
        language: String? = null,
        onUploadProgress: (Long, Long) -> Unit = { _, _ -> }
    ): AiTextResult {
        val response = authed("/v1/speech-to-text", "POST") {
            val builder = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("audio", "audio.wav", ProgressRequestBody(bytes, mime, onUploadProgress))
            if (!language.isNullOrBlank()) builder.addFormDataPart("language", language)
            builder.build()
        }
        val payload = response.use { readJsonObject(it, "speech-to-text") }
        return AiTextResult(
            text = payload.string("text").orEmpty(),
            mock = payload.boolean("mock") ?: false,
            quotaRemaining = payload.int("quotaRemaining")
        )
    }

    suspend fun chat(
        messages: List<AiMessage>,
        maxTokens: Int? = null,
        temperature: Double? = null
    ): AiTextResult {
        val body = buildString {
            append("{\"messages\":[")
            messages.joinToString(separator = ",") { m ->
                "{\"role\":${jsonString(m.role)},\"content\":${jsonString(m.content)}}"
            }
            append("]")
            maxTokens?.let { append(",\"maxTokens\":$it") }
            temperature?.let { append(",\"temperature\":$it") }
            append("}")
        }
        val requestBody = body.toRequestBody(JSON_MEDIA)
        val response = authed("/v1/chat", "POST") { requestBody }
        return response.use {
            val payload = readJsonObject(it, "chat")
            AiTextResult(
                text = payload.string("text").orEmpty(),
                mock = payload.boolean("mock") ?: false,
                quotaRemaining = payload.int("quotaRemaining")
            )
        }
    }

    suspend fun translate(
        text: String,
        targetLang: String = "id",
        sourceLang: String? = null
    ): AiTextResult {
        val body = buildString {
            append("{\"text\":").append(jsonString(text))
            append(",\"targetLang\":").append(jsonString(targetLang))
            if (!sourceLang.isNullOrBlank()) append(",\"sourceLang\":").append(jsonString(sourceLang))
            append("}")
        }
        val requestBody = body.toRequestBody(JSON_MEDIA)
        val response = authed("/v1/translate", "POST") { requestBody }
        return response.use {
            val payload = readJsonObject(it, "translate")
            AiTextResult(
                text = payload.string("text").orEmpty(),
                mock = payload.boolean("mock") ?: false,
                quotaRemaining = payload.int("quotaRemaining")
            )
        }
    }

    suspend fun weather(latitude: Double, longitude: Double): AiWeatherResult {
        val response = authed("/v1/weather?lat=$latitude&lon=$longitude", "GET")
        return response.use {
            val payload = readJsonObject(it, "weather")
            AiWeatherResult(
                temperature = payload.long("temperature")?.toDouble() ?: 0.0,
                condition = payload.string("condition").orEmpty(),
                location = payload.string("location").orEmpty(),
                mock = payload.boolean("mock") ?: false,
                quotaRemaining = payload.int("quotaRemaining")
            )
        }
    }

    suspend fun startVideoJob(
        prompt: String,
        durationSec: Int,
        aspect: String
    ): AiVideoJob {
        val body = """{"prompt":${jsonString(prompt)},"durationSec":$durationSec,"aspect":${jsonString(aspect)}}"""
            .toRequestBody(JSON_MEDIA)
        val payload = authed("/v1/video-generate", "POST") { body }.use { readJsonObject(it, "video-generate") }
        val jobId = payload.string("jobId")
            ?: throw AiException("bad_response", "Gateway tidak mengembalikan jobId")
        return AiVideoJob(
            jobId = jobId,
            mock = payload.boolean("mock") ?: false,
            pollAfterSec = payload.int("pollAfterSec") ?: 5
        )
    }

    suspend fun pollVideoJob(jobId: String): AiVideoStatus {
        val payload = authed("/v1/jobs/$jobId").use { readJsonObject(it, "jobs") }
        return AiVideoStatus(
            status = payload.string("status") ?: "unknown",
            progress = payload.int("progress") ?: 0,
            downloadUrl = payload.string("downloadUrl"),
            mime = payload.string("mime"),
            error = payload.string("error"),
            mock = payload.boolean("mock") ?: false
        )
    }

    /** Mengunduh artefak lewat URL bertanda tangan (tanpa header auth). */
    suspend fun download(
        relativeOrAbsoluteUrl: String,
        dest: File,
        onProgress: (Long, Long) -> Unit = { _, _ -> }
    ): File {
        val base = requireBaseUrl()
        val url = if (relativeOrAbsoluteUrl.startsWith("http")) relativeOrAbsoluteUrl else base + relativeOrAbsoluteUrl
        val response = execute(Request.Builder().url(url).get().build())
        response.use { res ->
            val body = res.body ?: throw AiException("bad_response", "Unduhan kosong")
            val total = body.contentLength()
            if (total > MAX_DOWNLOAD_BYTES) {
                throw AiException("payload_too_large", "Unduhan melebihi batas ${MAX_DOWNLOAD_BYTES / (1024 * 1024)} MB")
            }
            dest.parentFile?.mkdirs()
            body.byteStream().use { input ->
                dest.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var written = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        written += read
                        if (written > MAX_DOWNLOAD_BYTES) {
                            dest.delete()
                            throw AiException("payload_too_large", "Unduhan melebihi batas ${MAX_DOWNLOAD_BYTES / (1024 * 1024)} MB")
                        }
                        onProgress(written, total)
                    }
                }
            }
            if (total > 0 && dest.length() != total) {
                throw AiException("incomplete_download", "Unduhan terpotong (${dest.length()}/$total byte)", retryable = true)
            }
        }
        return dest
    }

    // --------------------------------------------------------------- helper

    private suspend fun toBinary(
        response: Response,
        onProgress: (Long, Long) -> Unit = { _, _ -> }
    ): AiBinary {
        val mime = response.header("content-type")?.substringBefore(';') ?: "application/octet-stream"
        val declared = response.body?.contentLength() ?: -1L
        if (declared > MAX_BINARY_BYTES) {
            throw AiException("payload_too_large", "Respons biner melebihi batas ${MAX_BINARY_BYTES / (1024 * 1024)} MB")
        }
        val stream = response.body?.byteStream() ?: throw AiException("bad_response", "Gateway mengembalikan berkas kosong")
        val buffer = java.io.ByteArrayOutputStream()
        val chunk = ByteArray(64 * 1024)
        stream.use { input ->
            while (true) {
                currentCoroutineContext().ensureActive()
                val read = input.read(chunk)
                if (read <= 0) break
                if (buffer.size().toLong() + read > MAX_BINARY_BYTES) {
                    throw AiException("payload_too_large", "Respons biner melebihi batas ${MAX_BINARY_BYTES / (1024 * 1024)} MB")
                }
                buffer.write(chunk, 0, read)
            }
        }
        val bytes = buffer.toByteArray()
        if (bytes.isEmpty()) {
            throw AiException("bad_response", "Gateway mengembalikan berkas kosong", retryable = true)
        }
        onProgress(bytes.size.toLong(), bytes.size.toLong())
        return AiBinary(
            bytes = bytes,
            mime = mime,
            mock = response.header("x-mock") == "1",
            quotaRemaining = response.header("x-quota-remaining")?.toIntOrNull()
        )
    }

    private suspend fun readJsonObject(response: Response, label: String): JsonObject {
        val text = response.body?.string().orEmpty()
        return runCatching { json.parseToJsonElement(text).jsonObject }.getOrElse {
            throw AiException("bad_response", "Respons $label bukan JSON yang valid")
        }
    }

    private fun parseError(response: Response): AiException {
        val raw = runCatching { response.body?.string() }.getOrNull().orEmpty()
        val payload = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull()
        val code = payload?.string("code") ?: when (response.code) {
            401 -> "unauthorized"
            403 -> "forbidden"
            404 -> "not_found"
            413 -> "payload_too_large"
            429 -> "rate_limited"
            else -> "http_${response.code}"
        }
        val message = payload?.string("message") ?: "AI Gateway menolak permintaan (${response.code})"
        val retryable = payload?.boolean("retryable") ?: (response.code >= 500)
        val retryAfter = payload?.long("retryAfterSec")
            ?: response.header("retry-after")?.toLongOrNull()
        return AiException(code, message, retryable, retryAfter, response.code)
    }

    private fun backoffSec(attempt: Int): Long =
        (2.0.pow(attempt.toDouble())).toLong().coerceIn(1, 15)

    private fun backoffMs(attempt: Int): Long = backoffSec(attempt) * 1000

    private fun jsonString(value: String): String =
        buildString {
            append('"')
            value.forEach { ch ->
                when (ch) {
                    '"' -> append("\\\"")
                    '\\' -> append("\\\\")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> if (ch < ' ') append("\\u%04x".format(ch.code)) else append(ch)
                }
            }
            append('"')
        }

    private fun JsonObject.string(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull

    private fun JsonObject.int(key: String): Int? =
        this[key]?.jsonPrimitive?.intOrNull

    private fun JsonObject.long(key: String): Long? =
        this[key]?.jsonPrimitive?.longOrNull

    private fun JsonObject.boolean(key: String): Boolean? =
        this[key]?.jsonPrimitive?.booleanOrNull

    /** RequestBody multipart yang melaporkan progres tulis (0..total byte). */
    private class ProgressRequestBody(
        private val bytes: ByteArray,
        mime: String,
        private val onProgress: (Long, Long) -> Unit
    ) : RequestBody() {

        private val mediaType = mime.toMediaTypeOrNull()

        override fun contentType() = mediaType

        override fun contentLength(): Long = bytes.size.toLong()

        override fun writeTo(sink: BufferedSink) {
            val chunk = 64 * 1024
            var offset = 0
            onProgress(0L, bytes.size.toLong())
            while (offset < bytes.size) {
                val length = min(chunk, bytes.size - offset)
                sink.write(bytes, offset, length)
                offset += length
                onProgress(offset.toLong(), bytes.size.toLong())
            }
        }
    }

    companion object {
        /** Batas keras agar respons gateway tidak menghabiskan memori proses. */
        private const val MAX_BINARY_BYTES = 96L * 1024 * 1024
        private const val MAX_DOWNLOAD_BYTES = 512L * 1024 * 1024

        private const val MAX_RETRIES = 2
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaTypeOrNull()
        private val EMPTY_BODY = ByteArray(0).toRequestBody(null)
    }
}
