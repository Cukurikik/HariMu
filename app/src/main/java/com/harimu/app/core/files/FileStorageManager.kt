package com.harimu.app.core.files

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FileStorageManager(private val context: Context) {

    val outputsDir: File
        get() = File(context.filesDir, "outputs").apply { mkdirs() }

    val recordingsDir: File
        get() = File(context.filesDir, "recordings").apply { mkdirs() }

    val tempDir: File
        get() = File(context.cacheDir, "temp").apply { mkdirs() }

    fun createOutputFile(toolTag: String, extension: String): File =
        File(outputsDir, buildOutputFileName(toolTag, extension))

    fun createRecordingFile(extension: String = "wav"): File =
        File(recordingsDir, buildOutputFileName("REC", extension))

    fun createTempFile(prefix: String, extension: String): File {
        val cleanExt = extension.trimStart('.').ifBlank { "tmp" }
        return File.createTempFile("${prefix}_", ".$cleanExt", tempDir)
    }

    fun createJobTempDir(jobId: String): File {
        val safeJob = jobId.replace(Regex("[^A-Za-z0-9_-]"), "_").ifBlank { "job" }
        return File(tempDir, "job_$safeJob").apply { mkdirs() }
    }

    fun cleanupJobTemp(jobId: String): Boolean {
        val safeJob = jobId.replace(Regex("[^A-Za-z0-9_-]"), "_").ifBlank { "job" }
        val dir = File(tempDir, "job_$safeJob")
        return if (dir.exists()) dir.deleteRecursively() else true
    }

    fun copyUriToTemp(uri: Uri, prefix: String = "input", fallbackExt: String = "bin"): File {
        val name = getDisplayName(uri)
        val ext = name?.substringAfterLast('.', "")?.takeIf { it.isNotBlank() && it.length <= 6 } ?: fallbackExt
        val dest = createTempFile(prefix, ext)
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(dest).use { output ->
                input.copyTo(output, bufferSize = 16 * 1024)
            }
        } ?: error("Tidak dapat membaca URI: $uri")
        return dest
    }

    fun getDisplayName(uri: Uri): String? {
        if (uri.scheme == "file") return uri.lastPathSegment
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
        }.getOrNull()
    }

    fun contentUriForFile(file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    fun buildShareIntent(file: File, mimeType: String): Intent {
        val uri = contentUriForFile(file)
        return Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun buildOpenIntent(file: File, mimeType: String): Intent {
        val uri = contentUriForFile(file)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun copyFileToUri(source: File, destinationUri: Uri): Boolean = runCatching {
        context.contentResolver.openOutputStream(destinationUri)?.use { out ->
            source.inputStream().use { inp -> inp.copyTo(out, 16 * 1024) }
        } ?: return@runCatching false
        true
    }.getOrDefault(false)

    fun saveToTreeUri(source: File, treeUri: Uri, mimeType: String): Uri? = runCatching {
        val docId = DocumentsContract.getTreeDocumentId(treeUri)
        val parentDocUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
        val createdUri = DocumentsContract.createDocument(
            context.contentResolver,
            parentDocUri,
            mimeType,
            source.name
        ) ?: return@runCatching null
        if (copyFileToUri(source, createdUri)) createdUri else null
    }.getOrNull()

    fun saveToMediaStore(source: File, mimeType: String, subFolder: String = "HariMu"): Uri? = runCatching {
        val resolver = context.contentResolver
        val (collection, relativeDir) = when {
            mimeType.startsWith("image/") ->
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI to "${Environment.DIRECTORY_PICTURES}/$subFolder"
            mimeType.startsWith("video/") ->
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI to "${Environment.DIRECTORY_MOVIES}/$subFolder"
            mimeType.startsWith("audio/") ->
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI to "${Environment.DIRECTORY_MUSIC}/$subFolder"
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                MediaStore.Downloads.EXTERNAL_CONTENT_URI to "${Environment.DIRECTORY_DOWNLOADS}/$subFolder"
            else ->
                MediaStore.Files.getContentUri("external") to "${Environment.DIRECTORY_DOWNLOADS}/$subFolder"
        }

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, source.name)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativeDir)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        val uri = resolver.insert(collection, values) ?: return@runCatching null
        val copied = copyFileToUri(source, uri)
        if (!copied) {
            resolver.delete(uri, null, null)
            return@runCatching null
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val doneValues = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            resolver.update(uri, doneValues, null, null)
        }
        uri
    }.getOrNull()

    fun saveToDefaultDestination(source: File, mimeType: String, configuredTreeUri: String): Uri? {
        if (configuredTreeUri.isNotBlank()) {
            val treeSaved = saveToTreeUri(source, configuredTreeUri.toUri(), mimeType)
            if (treeSaved != null) return treeSaved
        }
        return saveToMediaStore(source, mimeType)
    }

    fun availableDiskSpaceBytes(): Long = runCatching {
        val stat = StatFs(outputsDir.absolutePath)
        stat.availableBytes
    }.getOrDefault(0L)

    fun hasEnoughDiskSpace(requiredBytes: Long = MIN_FREE_DISK_BYTES): Boolean =
        availableDiskSpaceBytes() >= requiredBytes

    fun tempCacheSizeBytes(): Long = dirSizeBytes(tempDir)

    private fun dirSizeBytes(file: File): Long {
        if (!file.exists()) return 0L
        if (file.isFile) return file.length()
        val children = file.listFiles() ?: return 0L
        var total = 0L
        for (child in children) {
            total += dirSizeBytes(child)
        }
        return total
    }

    fun clearTempCache(): Long {
        val freed = tempCacheSizeBytes()
        tempDir.listFiles()?.forEach { it.deleteRecursively() }
        return freed
    }

    fun formatBytes(bytes: Long): String = companionFormatBytes(bytes)

    companion object {
        const val MIN_FREE_DISK_BYTES: Long = 50L * 1024L * 1024L

        fun buildOutputFileName(
            toolTag: String,
            extension: String,
            timestampMillis: Long = System.currentTimeMillis()
        ): String {
            val cleanTag = toolTag
                .trim()
                .replace(Regex("[^A-Za-z0-9_-]"), "_")
                .trim('_')
                .ifBlank { "TOOL" }
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(timestampMillis))
            val cleanExt = extension.trim().trimStart('.').ifBlank { "bin" }
            return "HariMu_${cleanTag}_${stamp}.$cleanExt"
        }

        fun companionFormatBytes(bytes: Long): String {
            if (bytes < 1024L) return "$bytes B"
            val kb = bytes / 1024.0
            if (kb < 1024.0) return String.format(Locale.US, "%.1f KB", kb)
            val mb = kb / 1024.0
            if (mb < 1024.0) return String.format(Locale.US, "%.2f MB", mb)
            val gb = mb / 1024.0
            return String.format(Locale.US, "%.2f GB", gb)
        }
    }
}
