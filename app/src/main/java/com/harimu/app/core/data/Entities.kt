package com.harimu.app.core.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val toolId: String,
    val toolTitle: String,
    val groupName: String,
    val outputPath: String,
    val outputName: String,
    val mimeType: String,
    val fileSizeBytes: Long,
    val summary: String,
    val success: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val toolId: String,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "vault_keys")
data class VaultKeyEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val alias: String,
    val algorithm: String,
    val keySizeBits: Int,
    val publicKeyPem: String,
    val privateKeyPem: String,
    val fingerprintSha256: String,
    val keystoreBacked: Boolean,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "jobs")
data class JobEntity(
    @PrimaryKey val jobId: String,
    val workRequestId: String,
    val toolId: String,
    val toolTitle: String,
    val groupName: String,
    val isDummy: Boolean,
    val status: String,
    val progressPercent: Int = 0,
    val progressMessage: String = "",
    val outputPath: String = "",
    val outputMime: String = "",
    val summary: String = "",
    val errorMessage: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
