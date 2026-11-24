package com.harimu.app.core.cloud

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Klien unggah cloud (CORE-09): S3/R2 (SigV4 PUT), Google Drive (multipart + token OAuth),
 * Dropbox (`/2/files/upload`), dan REST kustom (multipart).
 *
 * Batas jujur:
 * - S3 memakai **path-style** `endpoint/bucket/key` (didukung R2 dan S3); virtual-host style,
 *   multipart upload >5 GB, dan presigned URL tidak diimplementasikan.
 * - Google Drive memakai `uploadType=multipart` (satu permintaan) — berkas sangat besar sebaiknya
 *   memakai resumable yang belum ada.
 * - REST kustom mengirim **satu** berkas per permintaan; header diambil dari JSON `customHeadersJson`.
 */
class CloudUploader(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(180, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()
) {

    private val json = Json { ignoreUnknownKeys = true }

    /** Uji koneksi tanpa mengunggah data pengguna (HEAD/GET ringan sesuai provider). */
    suspend fun testConnection(config: CloudConfig): String = withContext(Dispatchers.IO) {
        require(config.isConfigured()) { "Koneksi belum lengkap" }
        when (config.provider) {
            CloudProvider.S3_R2 -> {
                // Tanda tangani PUT kosong ke objek uji; server menjawab 200/403 tanpa menyimpan isi.
                val key = "${config.pathPrefix.trimEnd('/')}/_harimu_test_${System.currentTimeMillis()}.txt"
                val url = buildS3Url(config, key)
                val headers = SigV4.signPut(
                    url = url,
                    region = config.region.ifBlank { "auto" },
                    accessKeyId = config.accessKeyId,
                    secretAccessKey = config.secretAccessKey,
                    payload = ByteArray(0)
                )
                val request = Request.Builder()
                    .url(url)
                    .put(ByteArray(0).toRequestBody("text/plain".toMediaType()))
                    .header("Authorization", headers.authorization)
                    .header("x-amz-content-sha256", headers.contentSha256)
                    .header("x-amz-date", headers.amzDate)
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw CloudException(
                            "S3/R2 menolak kredensial (HTTP ${response.code}). Periksa bucket, region, dan kunci.",
                            retryable = response.code >= 500
                        )
                    }
                }
                "S3/R2 OK (HTTP 200, uji tulis ${if (config.bucket.isNotBlank()) config.bucket else "bucket"})"
            }
            CloudProvider.GOOGLE_DRIVE -> {
                val request = Request.Builder()
                    .url("https://www.googleapis.com/drive/v3/about?fields=user")
                    .header("Authorization", "Bearer ${config.driveAccessToken}")
                    .get()
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw CloudException("Google Drive menolak token (HTTP ${response.code})", retryable = response.code >= 500)
                    }
                }
                "Google Drive OK"
            }
            CloudProvider.DROPBOX -> {
                val request = Request.Builder()
                    .url("https://api.dropboxapi.com/2/users/get_current_account")
                    .header("Authorization", "Bearer ${config.dropboxAccessToken}")
                    .post(ByteArray(0).toRequestBody("application/json".toMediaType()))
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw CloudException("Dropbox menolak token (HTTP ${response.code})", retryable = response.code >= 500)
                    }
                }
                "Dropbox OK"
            }
            CloudProvider.CUSTOM_REST -> {
                val request = Request.Builder()
                    .url(config.customUrl)
                    .header("Accept", "application/json")
                    .get()
                    .build()
                client.newCall(request).execute().use { response ->
                    if (response.code >= 500) throw CloudException("Server REST galat ${response.code}", retryable = true)
                }
                "REST kustom menjawab (kode ${response.code})"
            }
        }
    }

    suspend fun upload(file: File, displayName: String, mimeType: String, config: CloudConfig): UploadOutcome =
        withContext(Dispatchers.IO) {
            require(file.exists()) { "Berkas tidak ditemukan: ${file.name}" }
            require(config.isConfigured()) { "Koneksi cloud belum dikonfigurasi" }
            when (config.provider) {
                CloudProvider.S3_R2 -> uploadS3(file, displayName, mimeType, config)
                CloudProvider.GOOGLE_DRIVE -> uploadDrive(file, displayName, mimeType, config)
                CloudProvider.DROPBOX -> uploadDropbox(file, displayName, mimeType, config)
                CloudProvider.CUSTOM_REST -> uploadCustomRest(file, displayName, mimeType, config)
            }
        }

    private fun uploadS3(file: File, displayName: String, mimeType: String, config: CloudConfig): UploadOutcome {
        val key = objectKey(config.pathPrefix, displayName)
        val url = buildS3Url(config, key)
        val payload = file.readBytes()
        val signed = SigV4.signPut(
            url = url,
            region = config.region.ifBlank { "auto" },
            accessKeyId = config.accessKeyId,
            secretAccessKey = config.secretAccessKey,
            payload = payload,
            extraHeaders = mapOf("content-type" to mimeType)
        )
        val request = Request.Builder()
            .url(url)
            .put(payload.toRequestBody(mimeType.toMediaType()))
            .header("Authorization", signed.authorization)
            .header("x-amz-content-sha256", signed.contentSha256)
            .header("x-amz-date", signed.amzDate)
            .header("Content-Type", mimeType)
            .build()
        execute(request) { response ->
            if (!response.isSuccessful) {
                val retryable = response.code >= 500 || response.code == 429
                throw CloudException("Unggah S3 gagal (HTTP ${response.code})", retryable)
            }
        }
        return UploadOutcome(url, file.length(), CloudProvider.S3_R2)
    }

    private fun uploadDrive(file: File, displayName: String, mimeType: String, config: CloudConfig): UploadOutcome {
        require(config.driveAccessToken.isNotBlank()) {
            "Access token Google Drive kosong; sambungkan ulang di Pengaturan"
        }
        val metadata = buildString {
            append("{\"name\":\"").append(escapeJson(displayName)).append('"')
            if (config.driveFolderId.isNotBlank()) {
                append(",\"parents\":[\"").append(escapeJson(config.driveFolderId)).append("\"]")
            }
            append('}')
        }
        val boundary = "harimu-${System.currentTimeMillis()}"
        val bodyBuilder = MultipartBody.Builder(boundary).setType("multipart/related".toMediaType())
        bodyBuilder.addPart(metadata.toRequestBody("application/json; charset=UTF-8".toMediaType()))
        bodyBuilder.addPart(file.asRequestBody(mimeType.toMediaType()))
        val request = Request.Builder()
            .url("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id,name,webViewLink")
            .header("Authorization", "Bearer ${config.driveAccessToken}")
            .post(bodyBuilder.build())
            .build()
        var remoteId = ""
        var webLink = ""
        execute(request) { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw CloudException("Unggah Drive gagal (HTTP ${response.code}): ${text.take(200)}", response.code >= 500)
            }
            remoteId = Regex("\"id\"\\s*:\\s*\"([^\"]+)\"").find(text)?.groupValues?.get(1).orEmpty()
            webLink = Regex("\"webViewLink\"\\s*:\\s*\"([^\"]+)\"").find(text)?.groupValues?.get(1).orEmpty()
        }
        return UploadOutcome(
            remoteUrl = webLink.ifBlank { "https://drive.google.com/file/d/$remoteId/view" },
            bytes = file.length(),
            provider = CloudProvider.GOOGLE_DRIVE
        )
    }

    private fun uploadDropbox(file: File, displayName: String, mimeType: String, config: CloudConfig): UploadOutcome {
        require(config.dropboxAccessToken.isNotBlank()) {
            "Access token Dropbox kosong; sambungkan ulang di Pengaturan"
        }
        val path = "/${config.pathPrefix.trim('/').ifBlank { "Harimu" }}/${displayName}"
        val arg = """{"path":"${escapeJson(path)}","mode":"add","autorename":true,"mute":false}"""
        val request = Request.Builder()
            .url("https://content.dropboxapi.com/2/files/upload")
            .header("Authorization", "Bearer ${config.dropboxAccessToken}")
            .header("Dropbox-API-Arg", arg)
            .header("Content-Type", "application/octet-stream")
            .post(file.asRequestBody(mimeType.toMediaType()))
            .build()
        var link = ""
        execute(request) { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw CloudException("Unggah Dropbox gagal (HTTP ${response.code}): ${text.take(200)}", response.code >= 500)
            }
            link = Regex("\"path_display\"\\s*:\\s*\"([^\"]+)\"").find(text)?.groupValues?.get(1).orEmpty()
        }
        return UploadOutcome("dropbox:$link", file.length(), CloudProvider.DROPBOX)
    }

    private fun uploadCustomRest(file: File, displayName: String, mimeType: String, config: CloudConfig): UploadOutcome {
        val builder = MultipartBody.Builder().setType(MultipartBody.FORM)
        builder.addFormDataPart(config.customFieldName.ifBlank { "file" }, displayName, file.asRequestBody(mimeType.toMediaType()))
        val requestBuilder = Request.Builder().url(config.customUrl)
        runCatching { json.parseToJsonElement(config.customHeadersJson) as? JsonObject }
            .getOrNull()
            ?.forEach { (key, value) ->
                if (key.equals("content-type", true) || key.equals("host", true)) return@forEach
                requestBuilder.header(key, value.jsonPrimitive.content)
            }
        val method = config.customMethod.uppercase()
        val body = builder.build()
        when (method) {
            "PUT" -> requestBuilder.put(body)
            "PATCH" -> requestBuilder.patch(body)
            else -> requestBuilder.post(body)
        }
        var bodyText = ""
        execute(requestBuilder.build()) { response ->
            bodyText = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw CloudException("REST kustom gagal (HTTP ${response.code}): ${bodyText.take(200)}", response.code >= 500)
            }
        }
        val link = Regex("\"(?:url|link|location|path)\"\\s*:\\s*\"([^\"]+)\"").find(bodyText)?.groupValues?.get(1)
        return UploadOutcome(link ?: config.customUrl, file.length(), CloudProvider.CUSTOM_REST)
    }

    // --------------------------------------------------------------- OAuth token

    /** Perbarui access token memakai refresh token. Mengembalikan config baru (tanpa menyimpan). */
    suspend fun refreshTokenIfNeeded(config: CloudConfig, force: Boolean = false): CloudConfig =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            when (config.provider) {
                CloudProvider.GOOGLE_DRIVE -> {
                    val expired = force || config.driveAccessToken.isBlank() || config.driveTokenExpiresAt <= now + 60_000
                    if (!expired || config.driveRefreshToken.isBlank() || config.driveClientId.isBlank()) return@withContext config
                    val form = formBody(
                        mapOf(
                            "client_id" to config.driveClientId,
                            "refresh_token" to config.driveRefreshToken,
                            "grant_type" to "refresh_token"
                        )
                    )
                    val request = Request.Builder()
                        .url(OAuthPkce.tokenEndpoint(CloudProvider.GOOGLE_DRIVE))
                        .post(form)
                        .build()
                    val tokens = exchange(request, "Google Drive")
                    config.copy(
                        driveAccessToken = tokens.accessToken,
                        driveTokenExpiresAt = now + tokens.expiresInSec * 1000
                    )
                }
                CloudProvider.DROPBOX -> {
                    val expired = force || config.dropboxAccessToken.isBlank() || config.dropboxTokenExpiresAt <= now + 60_000
                    if (!expired || config.dropboxRefreshToken.isBlank() || config.dropboxAppKey.isBlank()) return@withContext config
                    val form = formBody(
                        mapOf(
                            "grant_type" to "refresh_token",
                            "refresh_token" to config.dropboxRefreshToken,
                            "client_id" to config.dropboxAppKey
                        )
                    )
                    val request = Request.Builder()
                        .url(OAuthPkce.tokenEndpoint(CloudProvider.DROPBOX))
                        .post(form)
                        .build()
                    val tokens = exchange(request, "Dropbox")
                    config.copy(
                        dropboxAccessToken = tokens.accessToken,
                        dropboxTokenExpiresAt = now + tokens.expiresInSec * 1000
                    )
                }
                else -> config
            }
        }

    /** Tukar authorization code dengan token (dipakai UI setelah deep link OAuth). */
    suspend fun exchangeCode(
        provider: CloudProvider,
        code: String,
        codeVerifier: String,
        clientId: String,
        redirectUri: String = OAuthPkce.REDIRECT_URI
    ): OAuthTokens = withContext(Dispatchers.IO) {
        val form = formBody(
            mapOf(
                "code" to code,
                "grant_type" to "authorization_code",
                "client_id" to clientId,
                "redirect_uri" to redirectUri,
                "code_verifier" to codeVerifier
            )
        )
        val request = Request.Builder().url(OAuthPkce.tokenEndpoint(provider)).post(form).build()
        exchange(request, provider.label(false))
    }

    data class OAuthTokens(val accessToken: String, val refreshToken: String, val expiresInSec: Long)

    private fun exchange(request: Request, label: String): OAuthTokens {
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw CloudException("Otorisasi $label gagal (HTTP ${response.code}): ${text.take(200)}")
            }
            fun field(name: String): String =
                Regex("\"$name\"\\s*:\\s*\"([^\"]*)\"").find(text)?.groupValues?.get(1).orEmpty()
            val access = field("access_token")
            if (access.isBlank()) throw CloudException("Provider tidak mengembalikan access_token")
            val expires = Regex("\"expires_in\"\\s*:\\s*(\\d+)").find(text)?.groupValues?.get(1)?.toLongOrNull() ?: 3600L
            return OAuthTokens(access, field("refresh_token"), expires)
        }
    }

    private inline fun execute(request: Request, block: (okhttp3.Response) -> Unit) {
        client.newCall(request).execute().use(block)
    }

    private fun buildS3Url(config: CloudConfig, key: String): String {
        val base = config.endpoint.trimEnd('/')
        require(base.startsWith("http")) { "Endpoint S3/R2 harus diawali https://" }
        val bucket = config.bucket.trim('/')
        val encodedKey = key.split('/').joinToString("/") { URLEncoder.encode(it, "UTF-8").replace("+", "%20") }
        return "$base/$bucket/$encodedKey"
    }

    private fun objectKey(prefix: String, displayName: String): String {
        val cleanPrefix = prefix.trim('/')
        return if (cleanPrefix.isBlank()) displayName else "$cleanPrefix/$displayName"
    }

    private fun formBody(values: Map<String, String>): okhttp3.RequestBody {
        val encoded = values.entries.joinToString("&") { (key, value) ->
            "${URLEncoder.encode(key, "UTF-8")}=${URLEncoder.encode(value, "UTF-8")}"
        }
        return encoded.toRequestBody("application/x-www-form-urlencoded".toMediaType())
    }

    private fun escapeJson(value: String): String = value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", " ")
}
