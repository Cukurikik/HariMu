package com.harimu.app.core.cloud

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class CloudProvider {
    @SerialName("s3_r2") S3_R2,
    @SerialName("google_drive") GOOGLE_DRIVE,
    @SerialName("dropbox") DROPBOX,
    @SerialName("custom_rest") CUSTOM_REST;

    fun label(isEnglish: Boolean): String = when (this) {
        S3_R2 -> if (isEnglish) "S3 / R2 (SigV4)" else "S3 / R2 (SigV4)"
        GOOGLE_DRIVE -> "Google Drive"
        DROPBOX -> "Dropbox"
        CUSTOM_REST -> if (isEnglish) "Custom REST" else "REST Kustom"
    }
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
        "provider=${provider.name}, enabled=$enabled, target=${describe(false)}"
}

@Serializable
data class UploadOutcome(val remoteUrl: String, val bytes: Long, val provider: CloudProvider)

class CloudException(message: String, val retryable: Boolean = false, cause: Throwable? = null) :
    Exception(message, cause)
