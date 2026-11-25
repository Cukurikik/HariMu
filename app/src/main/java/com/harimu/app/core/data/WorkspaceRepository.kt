package com.harimu.app.core.data

import androidx.room.withTransaction
import com.harimu.app.core.backup.BackupEngine
import com.harimu.app.core.files.FileStorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

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
    val videoProjectsFlow: Flow<List<VideoProjectEntity>> = db.videoProjectDao().observeAll()
    val audioProjectsFlow: Flow<List<AudioProjectEntity>> = db.audioProjectDao().observeAll()


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

    suspend fun saveVideoProject(
        id: String,
        title: String,
        clips: List<EditorClipSpec>,
        outputResolution: String,
        lastExportPath: String = "",
        createdAt: Long = System.currentTimeMillis()
    ) {
        db.videoProjectDao().upsert(
            VideoProjectEntity(
                id = id,
                title = title,
                clipsJson = encodeEditorClips(clips),
                outputResolution = outputResolution,
                lastExportPath = lastExportPath,
                createdAt = createdAt,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun getVideoProject(id: String): VideoProjectEntity? = db.videoProjectDao().getById(id)

    suspend fun deleteVideoProject(id: String) = db.videoProjectDao().deleteById(id)

    fun encodeEditorClips(clips: List<EditorClipSpec>): String = json.encodeToString(clips)

    fun decodeEditorClips(clipsJson: String): List<EditorClipSpec> =
        runCatching { json.decodeFromString<List<EditorClipSpec>>(clipsJson) }.getOrDefault(emptyList())

    suspend fun saveAudioProject(
        id: String,
        title: String,
        tracks: List<AudioTrackSpec>,
        masterVolume: Float,
        lastExportPath: String = "",
        createdAt: Long = System.currentTimeMillis()
    ) {
        db.audioProjectDao().upsert(
            AudioProjectEntity(
                id = id,
                title = title,
                tracksJson = encodeAudioTracks(tracks),
                masterVolume = masterVolume,
                lastExportPath = lastExportPath,
                createdAt = createdAt,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun getAudioProject(id: String): AudioProjectEntity? = db.audioProjectDao().getById(id)

    suspend fun deleteAudioProject(id: String) = db.audioProjectDao().deleteById(id)

    fun encodeAudioTracks(tracks: List<AudioTrackSpec>): String = json.encodeToString(tracks)

    fun decodeAudioTracks(tracksJson: String): List<AudioTrackSpec> =
        runCatching { json.decodeFromString<List<AudioTrackSpec>>(tracksJson) }.getOrDefault(emptyList())


    /**
     * Ekspor backup .omni lewat [BackupEngine] (FASE 14, CORE-08): ZIP + manifest skema 2, enkripsi
     * AES-GCM/PBKDF2 310k bila kata sandi diisi, dan hanya **metadata** kunci vault (private key
     * tidak pernah ikut karena disegel Keystore perangkat).
     */
    suspend fun exportOmniBackup(password: String?): File = withContext(Dispatchers.IO) {
        val currentSettings = settings.current()
        val favorites = db.favoriteDao().getAllOnce()
        val history = db.historyDao().getAllOnce()
        val vaultKeys = db.vaultKeyDao().getAllOnce().map {
            it.copy(privateKeyPem = "", publicKeyPem = if (it.keystoreBacked) it.publicKeyPem else "(tidak diekspor)")
        }
        val bytes = BackupEngine.createArchive(
            settingsJson = json.encodeToString(currentSettings),
            favoritesJson = json.encodeToString(favorites),
            historyJson = BackupEngine.trimHistoryJson(json.encodeToString(history)),
            vaultKeysJson = json.encodeToString(vaultKeys),
            password = password
        )
        val outFile = storage.createOutputFile("BACKUP", if (password.isNullOrBlank()) "omni" else "omni.enc")
        outFile.writeBytes(bytes)
        outFile
    }

    /** Impor .omni: validasi manifes, migrasi skema v1→v2, terapkan pengaturan/favorit/riwayat. */
    suspend fun importOmniBackup(rawBytes: ByteArray, password: String?): String = withContext(Dispatchers.IO) {
        val payload = BackupEngine.readArchive(rawBytes, password)
        var importedFavorites = 0
        var importedHistory = 0

        payload.settingsJson?.let { raw ->
            runCatching { json.decodeFromString<AppSettings>(raw) }.getOrNull()?.let { settings.applyImported(it) }
        }
        // Satu transaksi: bila ada entri rusak, tidak ada favorit/riwayat yang tersimpan separuh.
        db.withTransaction {
            payload.favoritesJson?.let { raw ->
                runCatching { json.decodeFromString<List<FavoriteEntity>>(raw) }.getOrDefault(emptyList()).forEach {
                    db.favoriteDao().insert(it)
                    importedFavorites++
                }
            }
            payload.historyJson?.let { raw ->
                runCatching { json.decodeFromString<List<HistoryEntity>>(raw) }.getOrDefault(emptyList())
                    .take(MAX_RESTORED_HISTORY)
                    .forEach {
                        db.historyDao().insert(it.copy(id = 0))
                        importedHistory++
                    }
            }
        }
        val vaultMetadata = payload.vaultKeysJson?.let { raw ->
            runCatching { json.decodeFromString<List<VaultKeyEntity>>(raw) }.getOrDefault(emptyList()).size
        } ?: 0

        val isEnglish = settings.current().isEnglish
        val migrationNote = payload.migratedFrom?.let {
            if (isEnglish) " (migrated from schema v$it)" else " (dimigrasikan dari skema v$it)"
        } ?: ""
        if (isEnglish) {
            "Restored: $importedFavorites favorites, $importedHistory history entries, $vaultMetadata key metadata (private keys stay device-bound)$migrationNote"
        } else {
            "Dipulihkan: $importedFavorites favorit, $importedHistory riwayat, $vaultMetadata metadata kunci (private key tetap terikat perangkat)$migrationNote"
        }
    }

    private companion object {
        const val MAX_RESTORED_HISTORY = 500
    }
}
