package com.harimu.app.core.data

import com.harimu.app.core.files.FileStorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.security.SecureRandom
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

@Serializable
data class OmniManifest(
    val schemaVersion: Int = 1,
    val appVersion: String = "1.0.0",
    val exportedAt: Long = System.currentTimeMillis(),
    val encrypted: Boolean = false
)

class WorkspaceRepository(
    private val db: AppDatabase,
    private val settings: SettingsRepository,
    private val storage: FileStorageManager
) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    val historyFlow: Flow<List<HistoryEntity>> = db.historyDao().observeAll()
    val favoriteIdsFlow: Flow<Set<String>> = db.favoriteDao().observeFavoriteIds().map { it.toSet() }
    val vaultKeysFlow: Flow<List<VaultKeyEntity>> = db.vaultKeyDao().observeAll()

    suspend fun recordHistory(
        toolId: String,
        toolTitle: String,
        groupName: String,
        outputFile: File?,
        mimeType: String,
        summary: String,
        success: Boolean = true
    ) {
        db.historyDao().insert(
            HistoryEntity(
                toolId = toolId,
                toolTitle = toolTitle,
                groupName = groupName,
                outputPath = outputFile?.absolutePath ?: "",
                outputName = outputFile?.name ?: toolTitle,
                mimeType = mimeType,
                fileSizeBytes = outputFile?.length() ?: 0L,
                summary = summary,
                success = success
            )
        )
    }

    suspend fun deleteHistory(id: Long) = db.historyDao().deleteById(id)
    suspend fun clearHistory() = db.historyDao().clearAll()

    suspend fun toggleFavorite(toolId: String) {
        if (db.favoriteDao().isFavorite(toolId)) {
            db.favoriteDao().delete(toolId)
        } else {
            db.favoriteDao().insert(FavoriteEntity(toolId))
        }
    }

    suspend fun saveVaultKey(entity: VaultKeyEntity): Long = db.vaultKeyDao().insert(entity)
    suspend fun deleteVaultKey(id: Long) = db.vaultKeyDao().deleteById(id)

    suspend fun exportOmniBackup(password: String?): File = withContext(Dispatchers.IO) {
        val currentSettings = settings.current()
        val favorites = db.favoriteDao().getAllOnce()
        val history = db.historyDao().getAllOnce()
        val useEncryption = !password.isNullOrBlank()

        val innerZipBytes = ByteArrayOutputStream().use { bos ->
            ZipOutputStream(bos).use { zos ->
                writeZipEntry(zos, "manifest.json", json.encodeToString(OmniManifest(encrypted = useEncryption)))
                writeZipEntry(zos, "settings.json", json.encodeToString(currentSettings))
                writeZipEntry(zos, "favorites.json", json.encodeToString(favorites))
                writeZipEntry(zos, "history.json", json.encodeToString(history))
            }
            bos.toByteArray()
        }

        val outFile = storage.createOutputFile("BACKUP", if (useEncryption) "omni.enc" else "omni")
        if (!useEncryption) {
            outFile.writeBytes(innerZipBytes)
        } else {
            val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
            val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
            val key = deriveAesKey(password!!, salt)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
            }
            val encrypted = cipher.doFinal(innerZipBytes)
            FileOutputStream(outFile).use { fos ->
                fos.write("OMNIENC1".toByteArray(Charsets.UTF_8))
                fos.write(salt)
                fos.write(iv)
                fos.write(encrypted)
            }
        }
        outFile
    }

    suspend fun importOmniBackup(rawBytes: ByteArray, password: String?): String = withContext(Dispatchers.IO) {
        val header = "OMNIENC1".toByteArray(Charsets.UTF_8)
        val zipBytes = if (rawBytes.size > 36 && rawBytes.sliceArray(0 until 8).contentEquals(header)) {
            require(!password.isNullOrBlank()) { "Backup terenkripsi membutuhkan kata sandi" }
            val salt = rawBytes.sliceArray(8 until 24)
            val iv = rawBytes.sliceArray(24 until 36)
            val cipherText = rawBytes.sliceArray(36 until rawBytes.size)
            val key = deriveAesKey(password, salt)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
            }
            cipher.doFinal(cipherText)
        } else {
            rawBytes
        }

        var manifest: OmniManifest? = null
        var importedSettings: AppSettings? = null
        var importedFavorites: List<FavoriteEntity> = emptyList()
        var importedHistory: List<HistoryEntity> = emptyList()

        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                val content = zis.readBytes().toString(Charsets.UTF_8)
                when (entry.name) {
                    "manifest.json" -> manifest = json.decodeFromString<OmniManifest>(content)
                    "settings.json" -> importedSettings = json.decodeFromString<AppSettings>(content)
                    "favorites.json" -> importedFavorites = json.decodeFromString<List<FavoriteEntity>>(content)
                    "history.json" -> importedHistory = json.decodeFromString<List<HistoryEntity>>(content)
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        require(manifest != null && manifest!!.schemaVersion <= 1) {
            "File .omni tidak valid atau versi skema tidak didukung"
        }

        importedSettings?.let { settings.applyImported(it) }
        importedFavorites.forEach { db.favoriteDao().insert(it) }
        importedHistory.take(100).forEach { db.historyDao().insert(it.copy(id = 0)) }

        "Dipulihkan: ${importedFavorites.size} favorit, ${importedHistory.size} riwayat"
    }

    private fun writeZipEntry(zos: ZipOutputStream, name: String, content: String) {
        zos.putNextEntry(ZipEntry(name))
        zos.write(content.toByteArray(Charsets.UTF_8))
        zos.closeEntry()
    }

    private fun deriveAesKey(password: String, salt: ByteArray): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(password.toCharArray(), salt, 65536, 256)
        val tmp = factory.generateSecret(spec)
        return SecretKeySpec(tmp.encoded, "AES")
    }
}
