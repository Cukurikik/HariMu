package com.harimu.app.core.files

import android.content.Context
import com.harimu.app.core.toolapi.ModelSpec
import java.io.File

data class LocalModelStatus(
    val spec: ModelSpec,
    val isDownloaded: Boolean,
    val localSizeBytes: Long
)

object ModelRegistry {
    val u2NetSeg = ModelSpec(
        id = "u2net_seg",
        name = "U²-Net Subject Segmentation",
        sizeBytes = 42L * 1024L * 1024L,
        sizeLabel = "42 MB"
    )
    val whisperTiny = ModelSpec(
        id = "whisper_tiny",
        name = "Whisper Tiny Speech-to-Text",
        sizeBytes = 75L * 1024L * 1024L,
        sizeLabel = "75 MB"
    )
    val realEsrganLite = ModelSpec(
        id = "realesrgan_lite",
        name = "Real-ESRGAN Lite Upscaler",
        sizeBytes = 18L * 1024L * 1024L,
        sizeLabel = "18 MB"
    )
    val demucsLite = ModelSpec(
        id = "demucs_lite",
        name = "Demucs 4-Stem Separator",
        sizeBytes = 84L * 1024L * 1024L,
        sizeLabel = "84 MB"
    )

    val allModels: List<ModelSpec> = listOf(u2NetSeg, whisperTiny, realEsrganLite, demucsLite)
}

class ModelManager(private val context: Context) {

    val modelsDir: File
        get() = File(context.noBackupFilesDir, "models").apply { mkdirs() }

    fun modelFile(modelId: String): File {
        val safeId = modelId.replace(Regex("[^A-Za-z0-9_-]"), "_")
        return File(modelsDir, "$safeId.tflite")
    }

    fun isDownloaded(modelId: String): Boolean {
        val file = modelFile(modelId)
        return file.exists() && file.length() > 0L
    }

    fun listStatuses(): List<LocalModelStatus> = ModelRegistry.allModels.map { spec ->
        val file = modelFile(spec.id)
        val exists = file.exists() && file.length() > 0L
        LocalModelStatus(
            spec = spec,
            isDownloaded = exists,
            localSizeBytes = if (exists) file.length() else 0L
        )
    }

    fun totalDownloadedBytes(): Long =
        modelsDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    fun deleteModel(modelId: String): Boolean {
        val file = modelFile(modelId)
        return if (file.exists()) file.delete() else false
    }

    fun clearAllModels(): Long {
        val total = totalDownloadedBytes()
        modelsDir.listFiles()?.forEach { it.deleteRecursively() }
        return total
    }
}
