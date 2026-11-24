package com.harimu.app.core.backup

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Backup Workspace `.omni` (FASE 14, CORE-08).
 *
 * Format berkas:
 * - Tanpa kata sandi: ZIP biasa berisi `manifest.json`, `settings.json`, `favorites.json`,
 *   `history.json` (+ `vault_keys.json` metadata tanpa private key).
 * - Terenkripsi: `OMNIENC2` | salt(16) | iv(12) | iterasi(4, big-endian) | AES-GCM(ciphertext ZIP).
 *   KDF = PBKDF2-HMAC-SHA256 dengan 310.000 iterasi dan salt acak 16 byte.
 * - Impor masih menerima format Fase 2 (`OMNIENC1`, 65.536 iterasi) dan ZIP v1 lalu
 *   **memigrasikan** manifes ke skema terbaru.
 *
 * Batas jujur:
 * - Kata sandi tidak disimpan; kehilangan kata sandi berarti arsip tidak bisa dibuka.
 * - Kunci vault yang bersifat non-ekspor tidak ikut dipulihkan (hanya metadatanya); private key
 *   pasangan RSA/EC disegel dengan kunci Keystore perangkat asal sehingga **tidak bisa** dibuka di
 *   perangkat lain dan sengaja tidak diekspor.
 * - Riwayat dipulihkan maksimum 500 entri terakhir.
 */
object BackupEngine {

    const val SCHEMA_VERSION = 2
    const val MIN_SCHEMA_VERSION = 1
    const val KDF_ITERATIONS = 310_000
    private const val LEGACY_ITERATIONS = 65_536
    private const val MAX_ENTRY_BYTES = 32L * 1024 * 1024
    private const val MAX_HISTORY = 500
    private const val MAGIC_V2 = "OMNIENC2"
    private const val MAGIC_V1 = "OMNIENC1"
    private const val GCM_TAG_BITS = 128

    @Serializable
    data class Manifest(
        val schemaVersion: Int = SCHEMA_VERSION,
        val appVersion: String = "1.1.0",
        val exportedAt: Long = System.currentTimeMillis(),
        val encrypted: Boolean = false,
        val kdf: String = "PBKDF2-HMAC-SHA256",
        val kdfIterations: Int = KDF_ITERATIONS,
        val counts: Counts = Counts()
    )

    @Serializable
    data class Counts(
        val favorites: Int = 0,
        val history: Int = 0,
        val vaultKeys: Int = 0
    )

    data class Payload(
        val manifest: Manifest,
        val settingsJson: String?,
        val favoritesJson: String?,
        val historyJson: String?,
        val vaultKeysJson: String?,
        val migratedFrom: Int?
    )

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    /** Bangun arsip `.omni`. [settingsJson] dsb. adalah JSON yang sudah dipotong ke [MAX_HISTORY]. */
    fun createArchive(
        settingsJson: String,
        favoritesJson: String,
        historyJson: String,
        vaultKeysJson: String = "[]",
        password: String? = null
    ): ByteArray {
        val encrypted = !password.isNullOrBlank()
        val manifest = Manifest(
            encrypted = encrypted,
            counts = Counts(
                favorites = countEntries(favoritesJson),
                history = countEntries(historyJson),
                vaultKeys = countEntries(vaultKeysJson)
            )
        )
        val zipBytes = ByteArrayOutputStream().use { buffer ->
            ZipOutputStream(buffer).use { zip ->
                write(zip, "manifest.json", json.encodeToString(manifest))
                write(zip, "settings.json", settingsJson)
                write(zip, "favorites.json", favoritesJson)
                write(zip, "history.json", historyJson)
                write(zip, "vault_keys.json", vaultKeysJson)
            }
            buffer.toByteArray()
        }
        if (!encrypted) return zipBytes

        val salt = randomBytes(16)
        val iv = randomBytes(12)
        val key = deriveKey(password!!, salt, KDF_ITERATIONS)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        }
        val cipherText = cipher.doFinal(zipBytes)
        return ByteArrayOutputStream().use { out ->
            out.write(MAGIC_V2.toByteArray(Charsets.US_ASCII))
            out.write(salt)
            out.write(iv)
            out.write(intToBytes(KDF_ITERATIONS))
            out.write(cipherText)
            out.toByteArray()
        }
    }

    /** Baca arsip, validasi manifes, dan laporkan versi asal bila dimigrasikan. */
    fun readArchive(raw: ByteArray, password: String?): Payload {
        val (zipBytes, legacy) = decode(raw, password)
        val entries = readZip(zipBytes)
        val manifestRaw = entries["manifest.json"] ?: error("Arsip .omni tidak berisi manifest.json")
        val manifest = runCatching { json.decodeFromString<Manifest>(manifestRaw) }
            .getOrElse { error("manifest.json tidak valid: ${it.message}") }
        require(manifest.schemaVersion in MIN_SCHEMA_VERSION..SCHEMA_VERSION) {
            "Versi skema ${manifest.schemaVersion} tidak didukung (didukung ${MIN_SCHEMA_VERSION}..$SCHEMA_VERSION)"
        }
        val migrated = migrateManifest(manifest)
        return Payload(
            manifest = migrated,
            settingsJson = entries["settings.json"],
            favoritesJson = entries["favorites.json"],
            historyJson = entries["history.json"],
            vaultKeysJson = entries["vault_keys.json"],
            migratedFrom = if (legacy || manifest.schemaVersion < SCHEMA_VERSION) manifest.schemaVersion else null
        )
    }

    /** v1 → v2: tidak ada pembelahan data, hanya penegasan versi + parameter KDF di manifes. */
    private fun migrateManifest(manifest: Manifest): Manifest =
        if (manifest.schemaVersion < SCHEMA_VERSION) {
            manifest.copy(
                schemaVersion = SCHEMA_VERSION,
                kdf = manifest.kdf.ifBlank { "PBKDF2-HMAC-SHA256" },
                kdfIterations = manifest.kdfIterations.takeIf { it > 0 } ?: KDF_ITERATIONS
            )
        } else {
            manifest
        }

    private fun countEntries(jsonArray: String): Int {
        val element = runCatching { Json.parseToJsonElement(jsonArray) }.getOrNull()
        return (element as? kotlinx.serialization.json.JsonArray)?.size ?: 0
    }

    private fun decode(raw: ByteArray, password: String?): Pair<ByteArray, Boolean> {
        if (raw.size > 8 && raw.copyOfRange(0, 8).contentEquals(MAGIC_V2.toByteArray(Charsets.US_ASCII))) {
            require(!password.isNullOrBlank()) { "Backup terenkripsi membutuhkan kata sandi" }
            require(raw.size > 40) { "Arsip .omni terenkripsi terpotong" }
            val salt = raw.copyOfRange(8, 24)
            val iv = raw.copyOfRange(24, 36)
            val iterations = bytesToInt(raw.copyOfRange(36, 40))
            require(iterations in 10_000..2_000_000) { "Parameter KDF di luar batas aman" }
            val cipherText = raw.copyOfRange(40, raw.size)
            return decrypt(cipherText, password, salt, iv, iterations) to false
        }
        if (raw.size > 8 && raw.copyOfRange(0, 8).contentEquals(MAGIC_V1.toByteArray(Charsets.US_ASCII))) {
            require(!password.isNullOrBlank()) { "Backup terenkripsi membutuhkan kata sandi" }
            require(raw.size > 36) { "Arsip .omni lama terpotong" }
            val salt = raw.copyOfRange(8, 24)
            val iv = raw.copyOfRange(24, 36)
            val cipherText = raw.copyOfRange(36, raw.size)
            return decrypt(cipherText, password, salt, iv, LEGACY_ITERATIONS) to true
        }
        return raw to false
    }

    private fun decrypt(cipherText: ByteArray, password: String, salt: ByteArray, iv: ByteArray, iterations: Int): ByteArray {
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.DECRYPT_MODE, deriveKey(password, salt, iterations), GCMParameterSpec(GCM_TAG_BITS, iv))
            }
            cipher.doFinal(cipherText)
        } catch (error: Exception) {
            throw IllegalStateException("Kata sandi salah atau arsip .omni rusak", error)
        }
    }

    fun deriveKey(password: String, salt: ByteArray, iterations: Int = KDF_ITERATIONS): SecretKeySpec {
        require(password.isNotEmpty()) { "Kata sandi kosong" }
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, 256)
        return SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
    }

    private fun readZip(bytes: ByteArray): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry: ZipEntry? = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val name = entry.name
                    require(name in ALLOWED_ENTRIES) { "Entri .omni tidak dikenal: $name" }
                    val content = readBounded(zip, name)
                    out[name] = content
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return out
    }

    private fun readBounded(zip: ZipInputStream, name: String): String {
        val buffer = ByteArrayOutputStream()
        val chunk = ByteArray(16 * 1024)
        while (true) {
            val read = zip.read(chunk)
            if (read <= 0) break
            if (buffer.size() + read > MAX_ENTRY_BYTES) error("Entri $name melebihi batas 32 MB")
            buffer.write(chunk, 0, read)
        }
        return buffer.toByteArray().toString(Charsets.UTF_8)
    }

    private fun write(zip: ZipOutputStream, name: String, content: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(content.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun randomBytes(size: Int): ByteArray = ByteArray(size).also { SecureRandom().nextBytes(it) }

    fun intToBytes(value: Int): ByteArray = byteArrayOf(
        ((value shr 24) and 0xFF).toByte(),
        ((value shr 16) and 0xFF).toByte(),
        ((value shr 8) and 0xFF).toByte(),
        (value and 0xFF).toByte()
    )

    fun bytesToInt(bytes: ByteArray): Int {
        require(bytes.size == 4) { "Butuh 4 byte" }
        return ((bytes[0].toInt() and 0xFF) shl 24) or
            ((bytes[1].toInt() and 0xFF) shl 16) or
            ((bytes[2].toInt() and 0xFF) shl 8) or
            (bytes[3].toInt() and 0xFF)
    }

    fun trimHistoryJson(historyJson: String, limit: Int = MAX_HISTORY): String {
        val element = runCatching { Json.parseToJsonElement(historyJson) }.getOrNull()
        val array = element as? kotlinx.serialization.json.JsonArray ?: return historyJson
        if (array.size <= limit) return historyJson
        return kotlinx.serialization.json.JsonArray(array.take(limit)).toString()
    }

    private val ALLOWED_ENTRIES = setOf(
        "manifest.json", "settings.json", "favorites.json", "history.json", "vault_keys.json"
    )
}
