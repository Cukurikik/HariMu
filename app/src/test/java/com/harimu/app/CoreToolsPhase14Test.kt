package com.harimu.app

import com.harimu.app.core.backup.BackupEngine
import com.harimu.app.core.cloud.CloudException
import com.harimu.app.core.cloud.CloudProvider
import com.harimu.app.core.cloud.OAuthPkce
import com.harimu.app.core.cloud.SigV4
import com.harimu.app.core.cloud.UploadRetryPolicy
import com.harimu.app.core.cloud.UploadStatus
import com.harimu.app.core.cloud.UploadTask
import com.harimu.app.core.recording.SpectrumAnalyzer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.PI
import kotlin.math.sin

/** FASE 14 — pengujian murni-Kotlin untuk CORE-05…CORE-09 (tanpa Android framework). */
class CoreToolsPhase14Test {

    // --------------------------------------------------------------- SigV4 (CORE-09)

    @Test
    fun signingKeyMatchesAwsPublishedVector() {
        val key = SigV4.signingKey(
            secretAccessKey = "wJalrXUtnFEMI/K7MDENG+bPxRfiCYEXAMPLEKEY",
            dateStamp = "20150830",
            region = "us-east-1",
            service = "iam"
        )
        assertEquals("c4afb1cc5771d871763a393e44b703571b55cc28424d1a5e86da6ed3c154a4b9", SigV4.hex(key))
    }

    @Test
    fun canonicalUriKeepsSlashesAndEncodesSpaces() {
        assertEquals("/harimu/a%20b/c.png", SigV4.canonicalUri("/harimu/a b/c.png"))
        assertEquals("/", SigV4.canonicalUri(""))
        assertEquals("/bucket/%2B", SigV4.canonicalUri("bucket/+"))
    }

    @Test
    fun canonicalQuerySortsKeysAndValues() {
        assertEquals("a=1&b=2&b=3", SigV4.canonicalQuery("b=2&a=1&b=3"))
        assertEquals("prefix=x%2Fy", SigV4.canonicalQuery("prefix=x/y"))
        assertEquals("", SigV4.canonicalQuery(""))
    }

    @Test
    fun signPutProducesAws4HeaderAndPayloadHash() {
        val signed = SigV4.signPut(
            url = "https://acc.r2.cloudflarestorage.com/bucket/harimu/file.png",
            region = "auto",
            accessKeyId = "AKIDEXAMPLE",
            secretAccessKey = "secret",
            payload = "hello".toByteArray(),
            clock = { 1_700_000_000_000L }
        )
        assertTrue(signed.authorization.startsWith("AWS4-HMAC-SHA256 Credential=AKIDEXAMPLE/"))
        assertTrue(signed.authorization.contains("SignedHeaders=host;x-amz-content-sha256;x-amz-date"))
        assertEquals(SigV4.sha256Hex("hello".toByteArray()), signed.contentSha256)
        assertEquals("20231114T221320Z", signed.amzDate)
    }

    // --------------------------------------------------------------- OAuth PKCE (CORE-09)

    @Test
    fun pkceChallengeMatchesRfc7636Vector() {
        val verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", OAuthPkce.challengeS256(verifier))
    }

    @Test
    fun driveAuthUrlUsesMinimalScopeAndS256() {
        val request = OAuthPkce.driveAuthRequest("client-123", verifier = "verifier", state = "state")
        assertTrue(request.url.startsWith("https://accounts.google.com/o/oauth2/v2/auth?"))
        assertTrue(request.url.contains("scope=https%3A%2F%2Fwww.googleapis.com%2Fauth%2Fdrive.file"))
        assertTrue(request.url.contains("code_challenge_method=S256"))
        assertTrue(request.url.contains("redirect_uri=harimu%3A%2F%2Foauth"))
        assertEquals("verifier", request.codeVerifier)
    }

    @Test
    fun dropboxAuthUrlRequestsOfflineTokens() {
        val request = OAuthPkce.dropboxAuthRequest("app-key")
        assertTrue(request.url.contains("token_access_type=offline"))
        assertTrue(request.url.contains("client_id=app-key"))
        assertTrue(request.url.contains("code_challenge="))
    }

    // --------------------------------------------------------------- Retry queue (CORE-09)

    @Test
    fun retryPolicyBacksOffAndStopsAtThreeAttempts() {
        assertEquals(2_000L, UploadRetryPolicy.delayMillis(1))
        assertEquals(8_000L, UploadRetryPolicy.delayMillis(2))
        assertEquals(30_000L, UploadRetryPolicy.delayMillis(3))
        val task = UploadTask(
            filePath = "/tmp/x",
            displayName = "x",
            mimeType = "image/png",
            provider = CloudProvider.S3_R2,
            attempts = 1
        )
        assertTrue(UploadRetryPolicy.shouldRetry(task, CloudException("500", retryable = true)))
        assertFalse(UploadRetryPolicy.shouldRetry(task, CloudException("400", retryable = false)))
        assertFalse(UploadRetryPolicy.shouldRetry(task.copy(attempts = 3), CloudException("500", retryable = true)))
        assertEquals(UploadStatus.QUEUED, task.status)
    }

    // --------------------------------------------------------------- Spectrum (CORE-06)

    @Test
    fun spectrumPeaksAtGeneratedTone() {
        val sampleRate = 44_100
        val toneHz = 1_000.0
        val samples = ShortArray(SpectrumAnalyzer.FFT_SIZE) {
            (sin(2 * PI * toneHz * it / sampleRate) * 26_000).toInt().toShort()
        }
        val bands = SpectrumAnalyzer.analyze(samples, sampleRate)
        assertEquals(SpectrumAnalyzer.BANDS, bands.size)
        val expectedBand = ((kotlin.math.ln(toneHz / 40.0) / kotlin.math.ln(16_000.0 / 40.0)) *
            SpectrumAnalyzer.BANDS).toInt()
        val peakBand = bands.indices.maxByOrNull { bands[it] } ?: 0
        assertTrue("peak band $peakBand should be near $expectedBand", kotlin.math.abs(peakBand - expectedBand) <= 2)
        assertTrue("peak should be strong", bands[peakBand] > 0.5f)
    }

    @Test
    fun silenceStaysLow() {
        val bands = SpectrumAnalyzer.analyze(ShortArray(SpectrumAnalyzer.FFT_SIZE), 44_100)
        assertTrue(bands.all { it < 0.1f })
        assertEquals(0f, SpectrumAnalyzer.rms(ShortArray(128)), 0.0001f)
    }

    // --------------------------------------------------------------- Backup (CORE-08)

    private fun v1Zip(schemaVersion: Int = 1): ByteArray {
        val json = Json { prettyPrint = true }
        val manifest = """{"schemaVersion":$schemaVersion,"appVersion":"1.0.0","exportedAt":1,"encrypted":false}"""
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            fun entry(name: String, body: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(body.toByteArray())
                zip.closeEntry()
            }
            entry("manifest.json", manifest)
            entry("settings.json", json.encodeToString(com.harimu.app.core.data.AppSettings()))
            entry("favorites.json", "[]")
            entry("history.json", "[]")
        }
        return out.toByteArray()
    }

    @Test
    fun archiveRoundTripWithoutPassword() {
        val archive = BackupEngine.createArchive(
            settingsJson = "{}",
            favoritesJson = """[{"toolId":"CORE-01"}]""",
            historyJson = "[]"
        )
        val payload = BackupEngine.readArchive(archive, null)
        assertTrue(payload.manifest.schemaVersion == BackupEngine.SCHEMA_VERSION)
        assertTrue(payload.manifest.counts.favorites == 1)
        assertEquals(null, payload.migratedFrom)
        assertNotNull(payload.settingsJson)
    }

    @Test
    fun archiveEncryptedRequiresCorrectPassword() {
        val archive = BackupEngine.createArchive("{}", "[]", "[]", password = "rahasia-kuat")
        assertTrue(String(archive.copyOfRange(0, 8), Charsets.US_ASCII) == "OMNIENC2")
        val payload = BackupEngine.readArchive(archive, "rahasia-kuat")
        assertEquals("{}", payload.settingsJson)
        val failure = runCatching { BackupEngine.readArchive(archive, "salah") }
        assertTrue(failure.isFailure)
        assertTrue(runCatching { BackupEngine.readArchive(archive, null) }.isFailure)
    }

    @Test
    fun v1ArchiveIsMigratedToCurrentSchema() {
        val payload = BackupEngine.readArchive(v1Zip(1), null)
        assertTrue(payload.manifest.schemaVersion == BackupEngine.SCHEMA_VERSION)
        assertTrue(payload.migratedFrom == 1)
        assertEquals("PBKDF2-HMAC-SHA256", payload.manifest.kdf)
        assertTrue(payload.manifest.kdfIterations == BackupEngine.KDF_ITERATIONS)
    }

    @Test
    fun unsupportedSchemaIsRejected() {
        val failure = runCatching { BackupEngine.readArchive(v1Zip(99), null) }
        assertTrue(failure.isFailure)
    }

    @Test
    fun legacyOmniEnc1ArchiveStillOpens() {
        val zip = v1Zip(1)
        val salt = ByteArray(16) { it.toByte() }
        val iv = ByteArray(12) { (it * 2).toByte() }
        val key = BackupEngine.deriveKey("lama", salt, 65_536)
        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(
                javax.crypto.Cipher.ENCRYPT_MODE,
                key,
                javax.crypto.spec.GCMParameterSpec(128, iv)
            )
        }
        val encrypted = cipher.doFinal(zip)
        val out = ByteArrayOutputStream().apply {
            write("OMNIENC1".toByteArray(Charsets.US_ASCII))
            write(salt)
            write(iv)
            write(encrypted)
        }
        val payload = BackupEngine.readArchive(out.toByteArray(), "lama")
        assertTrue(payload.manifest.schemaVersion == BackupEngine.SCHEMA_VERSION)
        assertTrue(payload.migratedFrom == 1)
    }

    @Test
    fun historyTrimKeepsMostRecentEntries() {
        val entries = (0 until 12).joinToString(",", "[", "]") { """{"toolId":"T$it"}""" }
        val trimmed = BackupEngine.trimHistoryJson(entries, limit = 5)
        assertEquals(5, Regex("\"toolId\"").findAll(trimmed).count())
    }
}
