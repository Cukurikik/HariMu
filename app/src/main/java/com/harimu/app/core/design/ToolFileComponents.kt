package com.harimu.app.core.design

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import com.harimu.app.core.toolapi.InputSpec
import com.harimu.app.core.toolapi.ToolInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * MIME type definitions and utilities for file selection.
 */
object ToolMimeTypes {
    // Images
    const val IMAGE_JPEG = "image/jpeg"
    const val IMAGE_PNG = "image/png"
    const val IMAGE_WEBP = "image/webp"
    const val IMAGE_GIF = "image/gif"
    const val IMAGE_BMP = "image/bmp"
    const val IMAGE_HEIC = "image/heic"
    const val IMAGE_HEIF = "image/heif"
    const val IMAGE_AVIF = "image/avif"
    const val IMAGE_SVG = "image/svg+xml"
    val IMAGE_ALL = arrayOf(IMAGE_JPEG, IMAGE_PNG, IMAGE_WEBP, IMAGE_GIF, IMAGE_BMP, IMAGE_HEIC, IMAGE_HEIF, IMAGE_AVIF, IMAGE_SVG, "image/*")

    // Videos
    const val VIDEO_MP4 = "video/mp4"
    const val VIDEO_MOV = "video/quicktime"
    const val VIDEO_MKV = "video/x-matroska"
    const val VIDEO_AVI = "video/x-msvideo"
    const val VIDEO_WEBM = "video/webm"
    const val VIDEO_3GP = "video/3gpp"
    val VIDEO_ALL = arrayOf(VIDEO_MP4, VIDEO_MOV, VIDEO_MKV, VIDEO_AVI, VIDEO_WEBM, VIDEO_3GP, "video/*")

    // Audio
    const val AUDIO_MP3 = "audio/mpeg"
    const val AUDIO_WAV = "audio/wav"
    const val AUDIO_OGG = "audio/ogg"
    const val AUDIO_AAC = "audio/aac"
    const val AUDIO_FLAC = "audio/flac"
    const val AUDIO_M4A = "audio/mp4"
    const val AUDIO_OPUS = "audio/opus"
    val AUDIO_ALL = arrayOf(AUDIO_MP3, AUDIO_WAV, AUDIO_OGG, AUDIO_AAC, AUDIO_FLAC, AUDIO_M4A, AUDIO_OPUS, "audio/*")

    // Documents
    const val PDF = "application/pdf"
    const val DOC = "application/msword"
    const val DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    const val XLS = "application/vnd.ms-excel"
    const val XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    const val PPT = "application/vnd.ms-powerpoint"
    const val PPTX = "application/vnd.openxmlformats-officedocument.presentationml.presentation"
    const val TXT = "text/plain"
    const val CSV = "text/csv"
    const val JSON = "application/json"
    const val XML = "application/xml"
    const val HTML = "text/html"
    const val MD = "text/markdown"
    val DOCUMENT_ALL = arrayOf(PDF, DOC, DOCX, XLS, XLSX, PPT, PPTX, TXT, CSV, JSON, XML, HTML, MD, "*/*")

    // Archives
    const val ZIP = "application/zip"
    const val ZIP_COMPRESSED = "application/x-zip-compressed"
    const val RAR = "application/x-rar-compressed"
    const val SEVEN_Z = "application/x-7z-compressed"
    const val TAR = "application/x-tar"
    const val GZIP = "application/gzip"
    val ARCHIVE_ALL = arrayOf(ZIP, ZIP_COMPRESSED, RAR, SEVEN_Z, TAR, GZIP, "*/*")

    // Subtitles
    const val SRT = "application/x-subrip"
    const val VTT = "text/vtt"
    const val ASS = "text/x-ass"
    val SUBTITLE_ALL = arrayOf(SRT, VTT, ASS, "text/plain", "*/*")

    // 3D
    const val STL = "model/stl"
    const val OBJ = "model/obj"
    const val GLTF = "model/gltf+json"
    const val GLB = "model/gltf-binary"
    val MODEL_ALL = arrayOf(STL, OBJ, GLTF, GLB, "*/*")

    // All files
    val ALL_FILES = arrayOf("*/*")

    /**
     * Get MIME types for an InputSpec.
     */
    fun forInputSpec(spec: InputSpec): Array<String> = when (spec) {
        InputSpec.SingleImage, InputSpec.MultipleImages -> IMAGE_ALL
        InputSpec.SingleVideo, InputSpec.MultipleVideos,
        InputSpec.VideoAndAudio, InputSpec.VideoAndImage, InputSpec.VideoAndSubtitle, InputSpec.VideoAndMedia -> VIDEO_ALL
        InputSpec.SingleAudio, InputSpec.MultipleAudios,
        InputSpec.AudioAndImage, InputSpec.AudioAndWatermarkAudio -> AUDIO_ALL
        InputSpec.SinglePdf, InputSpec.MultiplePdfs, InputSpec.PdfAndImage -> arrayOf(PDF)
        InputSpec.ZipFile -> ARCHIVE_ALL
        InputSpec.AnyFile -> ALL_FILES
        is InputSpec.TextInput -> ALL_FILES // Text input doesn't use file picker
        else -> ALL_FILES
    }

    /**
     * Get secondary MIME types for compound InputSpecs.
     */
    fun forSecondaryInput(spec: InputSpec): Array<String> = when (spec) {
        InputSpec.VideoAndAudio -> AUDIO_ALL
        InputSpec.VideoAndImage, InputSpec.AudioAndImage, InputSpec.PdfAndImage -> IMAGE_ALL
        InputSpec.VideoAndSubtitle -> SUBTITLE_ALL
        InputSpec.VideoAndMedia -> arrayOf("image/*", "video/*")
        InputSpec.AudioAndWatermarkAudio -> AUDIO_ALL
        else -> ALL_FILES
    }

    /**
     * Check if a URI matches the expected MIME types for an InputSpec.
     */
    fun matchesSpec(uri: Uri, spec: InputSpec, context: Context): Boolean {
        val mimeTypes = forInputSpec(spec)
        if (mimeTypes.contains("*/*")) return true

        val contentResolver = context.contentResolver
        val mimeType = contentResolver.getType(uri)?.lowercase(Locale.ROOT) ?: ""
        return mimeTypes.any { it == "*/*" || it == mimeType || (it.endsWith("/*") && mimeType.startsWith(it.dropLast(1))) }
    }

    /**
     * Get human-readable description of accepted file types.
     */
    fun describeAcceptedTypes(spec: InputSpec): String = when (spec) {
        InputSpec.SingleImage, InputSpec.MultipleImages -> "Images (JPEG, PNG, WebP, HEIC, AVIF, etc.)"
        InputSpec.SingleVideo, InputSpec.MultipleVideos -> "Videos (MP4, MOV, MKV, WebM, etc.)"
        InputSpec.SingleAudio, InputSpec.MultipleAudios -> "Audio (MP3, WAV, OGG, FLAC, AAC, etc.)"
        InputSpec.SinglePdf, InputSpec.MultiplePdfs -> "PDF documents"
        InputSpec.ZipFile -> "ZIP archives"
        InputSpec.VideoAndAudio -> "Video + Audio track"
        InputSpec.VideoAndImage -> "Video + Image overlay"
        InputSpec.VideoAndSubtitle -> "Video + Subtitle (SRT, VTT)"
        InputSpec.VideoAndMedia -> "Video + Image/Video background"
        InputSpec.AudioAndImage -> "Audio + Cover image"
        InputSpec.AudioAndWatermarkAudio -> "Audio + Watermark audio"
        InputSpec.PdfAndImage -> "PDF + Watermark image"
        InputSpec.AnyFile -> "Any file"
        is InputSpec.TextInput -> "Text input"
        else -> "Files"
    }
}

/**
 * PickVisualMedia contract helpers for Compose.
 */
object ToolPickContracts {
    fun singleImage(): ActivityResultContracts.PickVisualMedia = ActivityResultContracts.PickVisualMedia()

    fun multipleImages(maxItems: Int = 20): ActivityResultContracts.PickMultipleVisualMedia =
        ActivityResultContracts.PickMultipleVisualMedia(maxItems)

    fun singleVideo(): ActivityResultContracts.PickVisualMedia = ActivityResultContracts.PickVisualMedia()

    fun multipleVideos(maxItems: Int = 8): ActivityResultContracts.PickMultipleVisualMedia =
        ActivityResultContracts.PickMultipleVisualMedia(maxItems)

    fun singleDocument(mimeTypes: Array<String> = ToolMimeTypes.ALL_FILES): ActivityResultContracts.OpenDocument =
        ActivityResultContracts.OpenDocument()

    fun multipleDocuments(mimeTypes: Array<String> = ToolMimeTypes.ALL_FILES): ActivityResultContracts.OpenMultipleDocuments =
        ActivityResultContracts.OpenMultipleDocuments()

    /**
     * Creates the appropriate PickVisualMedia request for an InputSpec.
     */
    fun createPickRequest(spec: InputSpec): ActivityResultContracts.PickVisualMedia? = when (spec) {
        InputSpec.SingleImage,
        InputSpec.MultipleImages,
        InputSpec.SingleVideo,
        InputSpec.MultipleVideos,
        InputSpec.VideoAndAudio,
        InputSpec.VideoAndImage,
        InputSpec.VideoAndSubtitle,
        InputSpec.VideoAndMedia -> ActivityResultContracts.PickVisualMedia()
        else -> null
    }

    /**
     * Creates the appropriate document picker contract for an InputSpec.
     */
    fun createDocumentContract(spec: InputSpec, allowMultiple: Boolean = false): ActivityResultContract<*, *> = when (spec) {
        InputSpec.SingleAudio,
        InputSpec.AudioAndImage,
        InputSpec.AudioAndWatermarkAudio -> if (allowMultiple) multipleDocuments(ToolMimeTypes.AUDIO_ALL) else singleDocument(ToolMimeTypes.AUDIO_ALL)
        InputSpec.SinglePdf,
        InputSpec.PdfAndImage -> if (allowMultiple) multipleDocuments(arrayOf(ToolMimeTypes.PDF)) else singleDocument(arrayOf(ToolMimeTypes.PDF))
        InputSpec.MultiplePdfs -> multipleDocuments(arrayOf(ToolMimeTypes.PDF))
        InputSpec.ZipFile -> if (allowMultiple) multipleDocuments(ToolMimeTypes.ARCHIVE_ALL) else singleDocument(ToolMimeTypes.ARCHIVE_ALL)
        InputSpec.VideoAndSubtitle -> if (allowMultiple) multipleDocuments(ToolMimeTypes.SUBTITLE_ALL) else singleDocument(ToolMimeTypes.SUBTITLE_ALL)
        InputSpec.VideoAndMedia -> if (allowMultiple) multipleDocuments(arrayOf("image/*", "video/*")) else singleDocument(arrayOf("image/*", "video/*"))
        InputSpec.AnyFile -> if (allowMultiple) multipleDocuments() else singleDocument()
        else -> if (allowMultiple) multipleDocuments() else singleDocument()
    }
}

/**
 * File validation utilities.
 */
object ToolFileValidation {
    /**
     * Validates a URI can be read and matches expected type.
     */
    suspend fun validateUri(
        uri: Uri,
        expectedMimeTypes: Array<String>,
        context: Context,
        maxSizeBytes: Long = Long.MAX_VALUE
    ): ValidationResult {
        return withContext(Dispatchers.IO) {
            val contentResolver = context.contentResolver
            try {
                // Check MIME type
                val mimeType = contentResolver.getType(uri)?.lowercase(Locale.ROOT) ?: ""
                val mimeMatch = expectedMimeTypes.any { it == "*/*" || it == mimeType || (it.endsWith("/*") && mimeType.startsWith(it.dropLast(1))) }
                if (!mimeMatch) {
                    return@withContext ValidationResult.Invalid("File type not supported. Expected: ${expectedMimeTypes.joinToString(", ")}")
                }

                // Check file size
                val cursor = contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.SIZE), null, null, null)
                cursor?.use {
                    if (it.moveToFirst()) {
                        val size = it.getLong(it.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE))
                        if (size > maxSizeBytes) {
                            return@withContext ValidationResult.Invalid("File too large: ${formatBytes(size)} (max: ${formatBytes(maxSizeBytes)})")
                        }
                    }
                }

                // Check readability
                contentResolver.openInputStream(uri)?.use { it.read() }

                ValidationResult.Valid
            } catch (e: Exception) {
                ValidationResult.Invalid("Cannot read file: ${e.localizedMessage}")
            }
        }
    }

    data class ValidationResult(
        val isValid: Boolean,
        val errorMessage: String? = null
    ) {
        companion object {
            val Valid = ValidationResult(true)
            fun Invalid(error: String) = ValidationResult(false, error)
        }
    }

    fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024
        if (kb < 1024) return "$kb KB"
        val mb = kb / 1024
        if (mb < 1024) return String.format(Locale.US, "%.1f MB", mb.toDouble())
        val gb = mb / 1024
        return String.format(Locale.US, "%.1f GB", gb.toDouble())
    }
}

/**
 * Input spec extensions for UI.
 */
fun InputSpec.getPickerLabel(isMulti: Boolean, isEnglish: Boolean): String = when (this) {
    InputSpec.SingleImage -> if (isEnglish) "Select Image" else "Pilih Gambar"
    InputSpec.MultipleImages -> if (isEnglish) "Select Images" else "Pilih Gambar"
    InputSpec.SingleVideo -> if (isEnglish) "Select Video" else "Pilih Video"
    InputSpec.MultipleVideos -> if (isEnglish) "Select Videos" else "Pilih Video"
    InputSpec.SingleAudio -> if (isEnglish) "Select Audio" else "Pilih Audio"
    InputSpec.MultipleAudios -> if (isEnglish) "Select Audio Files" else "Pilih Audio"
    InputSpec.SinglePdf -> if (isEnglish) "Select PDF" else "Pilih PDF"
    InputSpec.MultiplePdfs -> if (isEnglish) "Select PDFs" else "Pilih PDF"
    InputSpec.ZipFile -> if (isEnglish) "Select ZIP Archive" else "Pilih Arsip ZIP"
    InputSpec.AnyFile -> if (isEnglish) "Select Files" else "Pilih Berkas"
    InputSpec.VideoAndAudio -> if (isEnglish) "Select Video" else "Pilih Video"
    InputSpec.VideoAndImage -> if (isEnglish) "Select Video" else "Pilih Video"
    InputSpec.VideoAndSubtitle -> if (isEnglish) "Select Video" else "Pilih Video"
    InputSpec.VideoAndMedia -> if (isEnglish) "Select Video" else "Pilih Video"
    InputSpec.AudioAndImage -> if (isEnglish) "Select Audio" else "Pilih Audio"
    InputSpec.AudioAndWatermarkAudio -> if (isEnglish) "Select Audio" else "Pilih Audio"
    InputSpec.PdfAndImage -> if (isEnglish) "Select PDF" else "Pilih PDF"
    is InputSpec.TextInput -> if (isEnglish) "Enter Text" else "Masukkan Teks"
    InputSpec.None -> ""
}

fun InputSpec.requiresSecondaryInput(): Boolean = when (this) {
    InputSpec.VideoAndAudio,
    InputSpec.VideoAndImage,
    InputSpec.VideoAndSubtitle,
    InputSpec.VideoAndMedia,
    InputSpec.AudioAndImage,
    InputSpec.AudioAndWatermarkAudio,
    InputSpec.PdfAndImage -> true
    else -> false
}

fun InputSpec.getSecondaryLabel(isEnglish: Boolean): String? = when (this) {
    InputSpec.VideoAndAudio -> if (isEnglish) "Background Audio" else "Audio Latar"
    InputSpec.VideoAndImage -> if (isEnglish) "Overlay Image" else "Gambar Overlay"
    InputSpec.VideoAndSubtitle -> if (isEnglish) "Subtitle File" else "File Subtitle"
    InputSpec.VideoAndMedia -> if (isEnglish) "Background Media" else "Media Latar"
    InputSpec.AudioAndImage -> if (isEnglish) "Cover Image" else "Gambar Sampul"
    InputSpec.AudioAndWatermarkAudio -> if (isEnglish) "Watermark Audio" else "Audio Watermark"
    InputSpec.PdfAndImage -> if (isEnglish) "Watermark Image" else "Gambar Watermark"
    else -> null
}