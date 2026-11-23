package com.harimu.app.core.media

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import com.harimu.app.core.files.FileStorageManager
import java.io.File
import java.io.FileOutputStream

object MediaUriUtils {

    fun sanitizeName(raw: String, fallback: String = "media"): String {
        val cleaned = raw
            .replace('\\', '_')
            .replace('/', '_')
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .trim('.', '_')
        return cleaned.ifBlank { fallback }.take(64)
    }

    fun resolveExtension(context: Context, uri: Uri, fallbackExt: String = "bin"): String {
        val display = FileStorageManager(context).getDisplayName(uri)
        val fromName = display?.substringAfterLast('.', "")
            ?.lowercase()
            ?.takeIf { it.isNotBlank() && it.length <= 6 && it.all(Char::isLetterOrDigit) }
        if (fromName != null) return fromName

        val mime = runCatching { context.contentResolver.getType(uri) }.getOrNull()
        val fromMime = mime?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
            ?.lowercase()
            ?.takeIf { it.isNotBlank() }
        return fromMime ?: fallbackExt.trimStart('.').ifBlank { "bin" }
    }

    fun copyUriToSafeTemp(
        context: Context,
        storage: FileStorageManager,
        uri: Uri,
        jobDir: File,
        index: Int = 0,
        fallbackExt: String = "mp4"
    ): File {
        if (!jobDir.exists()) jobDir.mkdirs()
        val ext = resolveExtension(context, uri, fallbackExt)
        val rawBase = FileStorageManager(context).getDisplayName(uri)?.substringBeforeLast('.') ?: "input_$index"
        val safeBase = sanitizeName(rawBase, "input_$index")
        val dest = File(jobDir, "${safeBase}_${index}.$ext")

        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(dest).use { output ->
                input.copyTo(output, bufferSize = 32 * 1024)
            }
        } ?: error("Tidak dapat membuka aliran URI: $uri")

        return dest
    }

    fun createOutputFile(storage: FileStorageManager, toolTag: String, extension: String): File =
        storage.createOutputFile(toolTag, extension)

    fun buildOutputFileName(toolTag: String, extension: String, timestampMillis: Long = System.currentTimeMillis()): String =
        FileStorageManager.buildOutputFileName(toolTag, extension, timestampMillis)

    fun cleanupQuietly(vararg files: File?) {
        files.forEach { file ->
            if (file != null && file.exists()) {
                runCatching { file.deleteRecursively() }
            }
        }
    }
}
