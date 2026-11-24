package com.harimu.app.core.cloud

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * OAuth 2.0 PKCE (RFC 7636) untuk Google Drive & Dropbox (CORE-09).
 *
 * Fungsi pembentuk verifier/challenge/URL bersifat murni. Penukaran kode (`code`→token) dilakukan
 * [OAuthClient] dengan OkHttp. Redirect memakai skema aplikasi `harimu://oauth` yang didaftarkan di
 * AndroidManifest dan ditangani MainActivity.
 *
 * Batas jujur:
 * - Google Drive memakai scope minimal `drive.file` (hanya berkas yang dibuat aplikasi ini).
 * - Dropbox memakai scope `files.content.write` + `files.content.read`; PKCE tanpa client secret.
 * - Refresh token disimpan **terenkripsi** lewat [CloudCredentialStore]; access token diperbarui
 *   otomatis bila kedaluwarsa.
 */
object OAuthPkce {

    const val REDIRECT_URI = "harimu://oauth"
    const val DRIVE_SCOPE = "https://www.googleapis.com/auth/drive.file"
    const val DROPBOX_SCOPE = "files.content.write files.content.read"

    private const val DRIVE_AUTH = "https://accounts.google.com/o/oauth2/v2/auth"
    private const val DRIVE_TOKEN = "https://oauth2.googleapis.com/token"
    private const val DROPBOX_AUTH = "https://www.dropbox.com/oauth2/authorize"
    private const val DROPBOX_TOKEN = "https://api.dropboxapi.com/oauth2/token"

    data class AuthRequest(val url: String, val codeVerifier: String, val state: String)

    fun verifier(random: ByteArray = ByteArray(48).also { SecureRandom().nextBytes(it) }): String =
        base64Url(random)

    fun challengeS256(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        return base64Url(digest)
    }

    fun base64Url(bytes: ByteArray): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    fun driveAuthRequest(clientId: String, verifier: String = verifier(), state: String = newState()): AuthRequest {
        require(clientId.isNotBlank()) { "Client ID Google Drive kosong" }
        val url = "$DRIVE_AUTH?" + listOf(
            "client_id=${encode(clientId)}",
            "redirect_uri=${encode(REDIRECT_URI)}",
            "response_type=code",
            "scope=${encode(DRIVE_SCOPE)}",
            "code_challenge=${encode(challengeS256(verifier))}",
            "code_challenge_method=S256",
            "access_type=offline",
            "prompt=consent",
            "state=${encode(state)}"
        ).joinToString("&")
        return AuthRequest(url, verifier, state)
    }

    fun dropboxAuthRequest(appKey: String, verifier: String = verifier(), state: String = newState()): AuthRequest {
        require(appKey.isNotBlank()) { "App key Dropbox kosong" }
        val url = "$DROPBOX_AUTH?" + listOf(
            "client_id=${encode(appKey)}",
            "redirect_uri=${encode(REDIRECT_URI)}",
            "response_type=code",
            "token_access_type=offline",
            "scope=${encode(DROPBOX_SCOPE)}",
            "code_challenge=${encode(challengeS256(verifier))}",
            "code_challenge_method=S256",
            "state=${encode(state)}"
        ).joinToString("&")
        return AuthRequest(url, verifier, state)
    }

    fun tokenEndpoint(provider: CloudProvider): String = when (provider) {
        CloudProvider.GOOGLE_DRIVE -> DRIVE_TOKEN
        CloudProvider.DROPBOX -> DROPBOX_TOKEN
        else -> error("Provider tidak memakai OAuth")
    }

    fun newState(): String = base64Url(ByteArray(16).also { SecureRandom().nextBytes(it) })

    private fun encode(value: String): String = SigV4.encode(value)
}
