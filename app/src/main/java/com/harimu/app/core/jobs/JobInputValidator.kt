package com.harimu.app.core.jobs

import android.content.Context
import android.net.Uri
import com.harimu.app.core.data.JobEntity
import com.harimu.app.core.toolapi.InputSpec
import com.harimu.app.core.toolapi.Tool
import com.harimu.app.core.toolapi.ToolInput
import com.harimu.app.core.toolapi.ToolInputCodec
import com.harimu.app.core.toolapi.ToolRegistry
import java.io.File

/**
 * Shared validation & summary helpers for [JobEntity] inputs.
 *
 * Ensures retryability and recovery decisions never lie when non-persistable URIs expire
 * or referenced local files have been deleted.
 */
object JobInputValidator {

    fun buildInputSummary(tool: Tool, input: ToolInput, isEnglish: Boolean): String {
        val parts = mutableListOf<String>()
        if (input.uris.isNotEmpty()) {
            parts += if (isEnglish) {
                if (input.uris.size == 1) "1 input file" else "${input.uris.size} input files"
            } else {
                if (input.uris.size == 1) "1 berkas masukan" else "${input.uris.size} berkas masukan"
            }
        }
        if (input.secondaryUri != null) {
            parts += if (isEnglish) "+ secondary file" else "+ berkas sekunder"
        }
        if (input.textInput.isNotBlank()) {
            parts += if (isEnglish) "text (${input.textInput.length} chars)" else "teks (${input.textInput.length} karakter)"
        }
        if (tool.isStub) {
            parts += if (isEnglish) "queued stub" else "simulasi antrean"
        }
        if (parts.isEmpty() && tool.inputSpec == InputSpec.None) {
            parts += if (isEnglish) "no file input" else "tanpa berkas masukan"
        }
        return parts.joinToString(" • ")
    }

    fun canRetryAfterFailure(context: Context, job: JobEntity?): Boolean {
        if (job == null) return false
        if (job.isDummy) return ToolRegistry.findById(job.toolId) != null
        val tool = ToolRegistry.findById(job.toolId) ?: return false
        if (tool.isStub) return false
        val input = ToolInputCodec.decode(job.inputJson)
        return canRetryStoredInput(context, tool, input)
    }

    fun canRetry(context: Context, job: JobEntity?): Boolean {
        if (job == null || !ToolJobWorker.isAttentionStatus(job.status)) return false
        if (job.isDummy) return ToolRegistry.findById(job.toolId) != null
        val tool = ToolRegistry.findById(job.toolId) ?: return false
        if (tool.isStub) return false
        if (job.inputJson.isBlank() && tool.inputSpec != InputSpec.None) return false
        val input = ToolInputCodec.decode(job.inputJson)
        return canRetryStoredInput(context, tool, input)
    }

    fun canRetryStoredInput(context: Context, tool: Tool, input: ToolInput): Boolean {
        if (tool.id == "IMG-25" || tool.id == "CNV-12") {
            val decodeMode = input.getString("mode", "Encode").startsWith("Decode")
            if (decodeMode) {
                val hasText = input.getString("data", "").isNotBlank() || input.textInput.isNotBlank()
                return hasText || (input.uris.isNotEmpty() && input.uris.all { isUriReadable(context, it) })
            }
        }
        return when (tool.inputSpec) {
            InputSpec.None -> true
            is InputSpec.TextInput ->
                input.textInput.isNotBlank() || (input.uris.isNotEmpty() && input.uris.all { isUriReadable(context, it) })
            InputSpec.VideoAndAudio,
            InputSpec.VideoAndImage,
            InputSpec.VideoAndSubtitle,
            InputSpec.VideoAndMedia,
            InputSpec.AudioAndImage,
            InputSpec.AudioAndWatermarkAudio,
            InputSpec.PdfAndImage -> {
                input.uris.isNotEmpty() &&
                    input.uris.all { isUriReadable(context, it) } &&
                    (input.secondaryUri == null || isUriReadable(context, input.secondaryUri))
            }
            else -> {
                input.uris.isNotEmpty() && input.uris.all { isUriReadable(context, it) }
            }
        }
    }

    fun areStoredInputsAccessible(context: Context, job: JobEntity): Boolean {
        if (job.isDummy) return true
        val tool = ToolRegistry.findById(job.toolId) ?: return false
        val input = ToolInputCodec.decode(job.inputJson)
        return canRetryStoredInput(context, tool, input)
    }

    fun isUriReadable(context: Context, uri: Uri): Boolean {
        val scheme = uri.scheme?.lowercase()
        if (scheme == "file" || scheme.isNullOrBlank()) {
            val path = uri.path ?: return false
            val file = File(path)
            return file.exists() && file.canRead()
        }
        return runCatching {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { true }
                ?: context.contentResolver.openInputStream(uri)?.use { true }
                ?: false
        }.getOrDefault(false)
    }
}
