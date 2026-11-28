package com.harimu.app.core.cloud

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

@Serializable
enum class CloudProvider {
    @SerialName("s3_r2") S3_R2,
    @SerialName("google_drive") GOOGLE_DRIVE,
    @SerialName("dropbox") DROPBOX,
    @SerialName("custom_rest") CUSTOM_REST;

    fun label(isEnglish: Boolean): String = when (this) {
        S3_R2 -> "S3 / R2 (SigV4)"
        GOOGLE_DRIVE -> "Google Drive"
        DROPBOX -> "Dropbox"
        CUSTOM_REST -> if (isEnglish) "Custom REST" else "REST Kustom"
    }
}

enum class CloudConnectionState {
    NOT_CONFIGURED,
    SAVED_UNVERIFIED,
    TOKEN_EXPIRED
}

/**
 * Konfigurasi koneksi cloud. **Seluruh objek ini disegel** lewat [com.harimu.app.core.security.SecretBox]
 * sebelum ditulis ke disk; token/secret tidak pernah muncul di DataStore, log, atau nama berkas.
 */
@Serializable
data class CloudConfig(
    val provider: CloudProvider = CloudProvider.S3_R2,
    val enabled: Boolean = false,

    // S3 / R2
    val endpoint: String = "",
    val region: String = "auto",
    val bucket: String = "",
    val accessKeyId: String = "",
    val secretAccessKey: String = "",
    val pathPrefix: String = "harimu/",

    // Google Drive (OAuth, scope drive.file)
    val driveClientId: String = "",
    val driveAccessToken: String = "",
    val driveRefreshToken: String = "",
    val driveTokenExpiresAt: Long = 0L,
    val driveFolderId: String = "",

    // Dropbox (OAuth PKCE)
    val dropboxAppKey: String = "",
    val dropboxAccessToken: String = "",
    val dropboxRefreshToken: String = "",
    val dropboxTokenExpiresAt: Long = 0L,

    // REST kustom
    val customUrl: String = "",
    val customMethod: String = "POST",
    val customFieldName: String = "file",
    val customHeadersJson: String = "{}"
) {
    fun isConfigured(): Boolean = when (provider) {
        CloudProvider.S3_R2 -> endpoint.isNotBlank() && bucket.isNotBlank() &&
            accessKeyId.isNotBlank() && secretAccessKey.isNotBlank()
        CloudProvider.GOOGLE_DRIVE -> driveClientId.isNotBlank() &&
            (driveAccessToken.isNotBlank() || driveRefreshToken.isNotBlank())
        CloudProvider.DROPBOX -> dropboxAppKey.isNotBlank() &&
            (dropboxAccessToken.isNotBlank() || dropboxRefreshToken.isNotBlank())
        CloudProvider.CUSTOM_REST -> customUrl.isNotBlank()
    }

    fun connectionState(nowMs: Long = System.currentTimeMillis()): CloudConnectionState {
        if (!isConfigured()) return CloudConnectionState.NOT_CONFIGURED
        return when (provider) {
            CloudProvider.GOOGLE_DRIVE -> {
                val expired = driveAccessToken.isBlank() ||
                    (driveTokenExpiresAt > 0L && driveTokenExpiresAt <= nowMs + 60_000L)
                if (expired) CloudConnectionState.TOKEN_EXPIRED else CloudConnectionState.SAVED_UNVERIFIED
            }
            CloudProvider.DROPBOX -> {
                val expired = dropboxAccessToken.isBlank() ||
                    (dropboxTokenExpiresAt > 0L && dropboxTokenExpiresAt <= nowMs + 60_000L)
                if (expired) CloudConnectionState.TOKEN_EXPIRED else CloudConnectionState.SAVED_UNVERIFIED
            }
            else -> CloudConnectionState.SAVED_UNVERIFIED
        }
    }

    fun validationError(isEnglish: Boolean): String? = when (provider) {
        CloudProvider.S3_R2 -> {
            val cleanEndpoint = endpoint.trim()
            when {
                !cleanEndpoint.startsWith("https://") && !cleanEndpoint.startsWith("http://") ->
                    if (isEnglish) "S3/R2 endpoint must start with https:// or http://" else "Endpoint S3/R2 harus diawali https:// atau http://"
                bucket.isBlank() ->
                    if (isEnglish) "Bucket name cannot be empty." else "Nama bucket tidak boleh kosong."
                accessKeyId.isBlank() || secretAccessKey.isBlank() ->
                    if (isEnglish) "Access Key ID and Secret Access Key are required." else "Access Key ID dan Secret Access Key wajib diisi."
                else -> null
            }
        }
        CloudProvider.GOOGLE_DRIVE -> when {
            driveClientId.isBlank() ->
                if (isEnglish) "Google Drive OAuth Client ID is required." else "Client ID OAuth Google Drive wajib diisi."
            driveAccessToken.isBlank() && driveRefreshToken.isBlank() ->
                if (isEnglish) "Connect Google Drive OAuth first." else "Hubungkan OAuth Google Drive terlebih dahulu."
            else -> null
        }
        CloudProvider.DROPBOX -> when {
            dropboxAppKey.isBlank() ->
                if (isEnglish) "Dropbox App Key is required." else "App Key Dropbox wajib diisi."
            dropboxAccessToken.isBlank() && dropboxRefreshToken.isBlank() ->
                if (isEnglish) "Connect Dropbox OAuth first." else "Hubungkan OAuth Dropbox terlebih dahulu."
            else -> null
        }
        CloudProvider.CUSTOM_REST -> {
            val cleanUrl = customUrl.trim()
            val method = customMethod.trim().uppercase()
            when {
                !cleanUrl.startsWith("https://") && !cleanUrl.startsWith("http://") ->
                    if (isEnglish) "REST URL must start with https:// or http://" else "URL REST harus diawali https:// atau http://"
                method !in listOf("POST", "PUT", "PATCH") ->
                    if (isEnglish) "REST method must be POST, PUT, or PATCH." else "Metode REST harus POST, PUT, atau PATCH."
                customFieldName.isBlank() ->
                    if (isEnglish) "Multipart file field name cannot be empty." else "Nama field berkas multipart tidak boleh kosong."
                customHeadersJson.isNotBlank() && !isValidHeaderJson(customHeadersJson) ->
                    if (isEnglish) "Headers JSON must be a valid JSON object of strings." else "Headers JSON harus berupa objek JSON dengan nilai string."
                else -> null
            }
        }
    }

    fun isInsecureHttp(): Boolean = when (provider) {
        CloudProvider.CUSTOM_REST -> customUrl.trim().lowercase().startsWith("http://")
        CloudProvider.S3_R2 -> endpoint.trim().lowercase().startsWith("http://")
        else -> false
    }

    fun describe(isEnglish: Boolean): String = when (provider) {
        CloudProvider.S3_R2 -> "${bucket.ifBlank { "?" }} @ ${endpoint.ifBlank { "?" }}"
        CloudProvider.GOOGLE_DRIVE -> if (driveRefreshToken.isNotBlank()) {
            if (isEnglish) "Google Drive (refresh token stored)" else "Google Drive (refresh token tersimpan)"
        } else "Google Drive"
        CloudProvider.DROPBOX -> if (dropboxRefreshToken.isNotBlank()) {
            if (isEnglish) "Dropbox (refresh token stored)" else "Dropbox (refresh token tersimpan)"
        } else "Dropbox"
        CloudProvider.CUSTOM_REST -> "$customMethod ${customUrl.ifBlank { "?" }}"
    }

    /** Buang rahasia saat objek ditampilkan (dipakai UI/log). */
    fun redacted(): String =
        "provider=${provider.name}, enabled=$enabled, state=${connectionState().name}, target=${describe(false)}"

    private fun isValidHeaderJson(raw: String): Boolean {
        val parsed = runCatching { Json.parseToJsonElement(raw) }.getOrNull() as? JsonObject ?: return false
        return parsed.values.all { it is JsonPrimitive && it.isString }
    }
}

@Serializable
data class UploadOutcome(val remoteUrl: String, val bytes: Long, val provider: CloudProvider)

class CloudException(message: String, val retryable: Boolean = false, cause: Throwable? = null) :
    Exception(message, cause)
