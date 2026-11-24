package com.harimu.app.core.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM history ORDER BY createdAt DESC")
    suspend fun getAllOnce(): List<HistoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: HistoryEntity): Long

    @Query("DELETE FROM history WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM history")
    suspend fun clearAll()
}

@Dao
interface FavoriteDao {
    @Query("SELECT toolId FROM favorites ORDER BY addedAt DESC")
    fun observeFavoriteIds(): Flow<List<String>>

    @Query("SELECT * FROM favorites ORDER BY addedAt DESC")
    suspend fun getAllOnce(): List<FavoriteEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE toolId = :toolId)")
    suspend fun isFavorite(toolId: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE toolId = :toolId")
    suspend fun delete(toolId: String)
}

@Dao
interface VaultKeyDao {
    @Query("SELECT * FROM vault_keys ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<VaultKeyEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: VaultKeyEntity): Long

    @Query("DELETE FROM vault_keys WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface JobDao {
    @Query("SELECT * FROM jobs ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<JobEntity>>

    @Query("SELECT * FROM jobs WHERE toolId = :toolId ORDER BY createdAt DESC LIMIT 1")
    fun observeLatestForTool(toolId: String): Flow<JobEntity?>

    @Query("SELECT * FROM jobs WHERE status IN ('QUEUED', 'RUNNING') ORDER BY createdAt ASC")
    suspend fun getPendingOrRunning(): List<JobEntity>

    @Query("SELECT * FROM jobs WHERE jobId = :jobId LIMIT 1")
    suspend fun getById(jobId: String): JobEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: JobEntity)

    @Query("UPDATE jobs SET status = :status, progressPercent = :percent, progressMessage = :message, updatedAt = :updatedAt WHERE jobId = :jobId")
    suspend fun updateProgress(jobId: String, status: String, percent: Int, message: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE jobs SET status = :status, progressPercent = :percent, progressMessage = :message, outputPath = :outputPath, outputMime = :outputMime, summary = :summary, errorMessage = :errorMessage, updatedAt = :updatedAt WHERE jobId = :jobId")
    suspend fun updateCompletion(
        jobId: String,
        status: String,
        percent: Int,
        message: String,
        outputPath: String,
        outputMime: String,
        summary: String,
        errorMessage: String,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query("DELETE FROM jobs WHERE status NOT IN ('QUEUED', 'RUNNING')")
    suspend fun clearFinished()
}

@Dao
interface VideoProjectDao {
    @Query("SELECT * FROM video_projects ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<VideoProjectEntity>>

    @Query("SELECT * FROM video_projects WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): VideoProjectEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: VideoProjectEntity)

    @Query("DELETE FROM video_projects WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface AudioProjectDao {
    @Query("SELECT * FROM audio_projects ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<AudioProjectEntity>>

    @Query("SELECT * FROM audio_projects WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): AudioProjectEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: AudioProjectEntity)

    @Query("DELETE FROM audio_projects WHERE id = :id")
    suspend fun deleteById(id: String)
}


