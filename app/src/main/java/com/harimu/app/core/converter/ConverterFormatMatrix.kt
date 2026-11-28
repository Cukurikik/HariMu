package com.harimu.app.core.converter

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import com.harimu.app.core.engines.ConverterEngine
import com.harimu.app.core.engines.EbookEngine
import com.harimu.app.core.engines.FontEngine
import com.harimu.app.core.files.FileStorageManager
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64
import java.util.Locale
import java.util.zip.ZipInputStream

/**
 * Source format identified from magic bytes, container structure, extension, or text inspection.
 */
enum class SourceFormat(
    val badge: String,
    val labelId: String,
    val labelEn: String,
    val category: ConverterWorkflow?
) {
    // Video
    MP4("MP4", "Video MP4", "MP4 Video", ConverterWorkflow.VIDEO_AUDIO),
    WEBM("WEBM", "Video WebM", "WebM Video", ConverterWorkflow.VIDEO_AUDIO),
    AVI("AVI", "Video AVI", "AVI Video", ConverterWorkflow.VIDEO_AUDIO),
    MKV("MKV", "Video MKV", "MKV Video", ConverterWorkflow.VIDEO_AUDIO),
    OTHER_VIDEO("VIDEO", "Video Lain", "Other Video", ConverterWorkflow.VIDEO_AUDIO),

    // Audio
    MP3("MP3", "Audio MP3", "MP3 Audio", ConverterWorkflow.VIDEO_AUDIO),
    WAV("WAV", "Audio WAV", "WAV Audio", ConverterWorkflow.VIDEO_AUDIO),
    FLAC("FLAC", "Audio FLAC", "FLAC Audio", ConverterWorkflow.VIDEO_AUDIO),
    OGG("OGG", "Audio OGG", "OGG Audio", ConverterWorkflow.VIDEO_AUDIO),
    M4A_AAC("M4A", "Audio AAC/M4A", "AAC/M4A Audio", ConverterWorkflow.VIDEO_AUDIO),
    OPUS("OPUS", "Audio OPUS", "OPUS Audio", ConverterWorkflow.VIDEO_AUDIO),
    OTHER_AUDIO("AUDIO", "Audio Lain", "Other Audio", ConverterWorkflow.VIDEO_AUDIO),

    // Images & Vector
    JPG("JPG", "Gambar JPEG", "JPEG Image", ConverterWorkflow.IMAGES_VECTOR),
    PNG("PNG", "Gambar PNG", "PNG Image", ConverterWorkflow.IMAGES_VECTOR),
    WEBP("WEBP", "Gambar WebP", "WebP Image", ConverterWorkflow.IMAGES_VECTOR),
    BMP("BMP", "Gambar BMP", "BMP Image", ConverterWorkflow.IMAGES_VECTOR),
    HEIC("HEIC", "Gambar HEIC", "HEIC Image", ConverterWorkflow.IMAGES_VECTOR),
    AVIF("AVIF", "Gambar AVIF", "AVIF Image", ConverterWorkflow.IMAGES_VECTOR),
    DNG("DNG", "RAW DNG", "DNG RAW", ConverterWorkflow.IMAGES_VECTOR),
    GIF("GIF", "Gambar GIF", "GIF Image", ConverterWorkflow.IMAGES_VECTOR),
    SVG("SVG", "Vektor SVG", "SVG Vector", ConverterWorkflow.IMAGES_VECTOR),
    TIFF("TIFF", "TIFF (Tidak Didukung)", "TIFF (Unsupported)", ConverterWorkflow.IMAGES_VECTOR),
    PSD("PSD", "Photoshop PSD (Tidak Didukung)", "PSD (Unsupported)", ConverterWorkflow.IMAGES_VECTOR),
    RAW_OTHER("RAW", "RAW Kamera (Tidak Didukung)", "Camera RAW (Unsupported)", ConverterWorkflow.IMAGES_VECTOR),

    // Documents & Books
    PDF("PDF", "Dokumen PDF", "PDF Document", ConverterWorkflow.DOCUMENTS_BOOKS),
    DOCX("DOCX", "Word (.docx)", "Word (.docx)", ConverterWorkflow.DOCUMENTS_BOOKS),
    EPUB("EPUB", "E-Book EPUB", "EPUB E-Book", ConverterWorkflow.DOCUMENTS_BOOKS),
    CBZ("CBZ", "Komik CBZ", "CBZ Comic", ConverterWorkflow.DOCUMENTS_BOOKS),
    CBR("CBR", "Komik CBR", "CBR Comic", ConverterWorkflow.DOCUMENTS_BOOKS),

    // Archives
    ZIP("ZIP", "Arsip ZIP", "ZIP Archive", ConverterWorkflow.ARCHIVES_SECURITY),
    TAR_GZ("TAR.GZ", "Arsip TAR.GZ", "TAR.GZ Archive", ConverterWorkflow.ARCHIVES_SECURITY),
    SEVEN_Z("7Z", "Arsip 7-Zip", "7-Zip Archive", ConverterWorkflow.ARCHIVES_SECURITY),
    RAR("RAR", "Arsip RAR (Baca Saja)", "RAR Archive (Read-Only)", ConverterWorkflow.ARCHIVES_SECURITY),
    HZM("HZM", "Arsip Lama HariMu (.hzm)", "Legacy HariMu (.hzm)", ConverterWorkflow.ARCHIVES_SECURITY),

    // Fonts & 3D
    TTF("TTF", "Font TrueType (.ttf)", "TrueType Font (.ttf)", ConverterWorkflow.FONTS_3D),
    OTF("OTF", "Font OpenType CFF (.otf)", "OpenType CFF (.otf)", ConverterWorkflow.FONTS_3D),
    WOFF("WOFF", "Web Font WOFF 1.0", "WOFF 1.0 Font", ConverterWorkflow.FONTS_3D),
    WOFF2("WOFF2", "WOFF 2.0 (Tidak Didukung)", "WOFF 2.0 (Unsupported)", ConverterWorkflow.FONTS_3D),
    STL("STL", "Model 3D STL", "STL 3D Mesh", ConverterWorkflow.FONTS_3D),

    // Structured Text & Data
    JSON("JSON", "Data JSON", "JSON Data", ConverterWorkflow.DATA_CODE),
    XML("XML", "Data XML", "XML Data", ConverterWorkflow.DATA_CODE),
    CSV("CSV", "Tabel CSV", "CSV Table", ConverterWorkflow.DATA_CODE),
    TSV("TSV", "Tabel TSV", "TSV Table", ConverterWorkflow.DATA_CODE),
    CSS("CSS", "Stylesheet CSS", "CSS Stylesheet", ConverterWorkflow.DATA_CODE),
    MARKDOWN("MD", "Teks Markdown", "Markdown Text", ConverterWorkflow.DATA_CODE),
    GEOJSON("GEOJSON", "GeoJSON Spasial", "GeoJSON Spatial", ConverterWorkflow.DATA_CODE),
    BASE64_IMAGE("BASE64", "Teks Gambar Base64", "Base64 Image Text", ConverterWorkflow.IMAGES_VECTOR),

    UNKNOWN("FILE", "Berkas Umum / Tidak Dikenal", "General / Unknown File", null);

    fun label(isEnglish: Boolean): String = if (isEnglish) labelEn else labelId
}

data class DetectedSource(
    val uriString: String,
    val displayName: String,
    val extension: String,
    val byteSize: Long,
    val format: SourceFormat,
    val detectionMethod: String,
    val noteId: String? = null,
    val noteEn: String? = null,
    val extensionMismatch: Boolean = false
) {
    fun note(isEnglish: Boolean): String? = if (isEnglish) noteEn else noteId
}

/**
 * Valid target conversion option derived directly from real HariMu engine capabilities.
 * Never exposes unsupported combinations as selectable targets.
 */
data class ConversionTargetOption(
    val id: String,
    val toolId: String,
    val labelId: String,
    val labelEn: String,
    val outputExt: String,
    val outputBadge: String,
    val isLimitedOrBasic: Boolean = false,
    val requiresNetwork: Boolean = false,
    val isArchiveSecurityAction: Boolean = false,
    val limitNoteId: String? = null,
    val limitNoteEn: String? = null,
    val supportsBatchQueue: Boolean = true,
    val supportsMultiInputSingleJob: Boolean = false,
    val fixedParams: Map<String, Any> = emptyMap(),
    val defaultParams: Map<String, Any> = emptyMap(),
    val visibleParamKeys: List<String> = emptyList()
) {
    fun label(isEnglish: Boolean): String = if (isEnglish) labelEn else labelId
    fun limitNote(isEnglish: Boolean): String? = if (isEnglish) limitNoteEn else limitNoteId
}

data class ConversionValidationResult(
    val isValid: Boolean,
    val blockingError: String? = null,
    val warnings: List<String> = emptyList()
)

object SourceFormatDetector {

    private val AUDIO_EXTS = setOf("mp3", "wav", "flac", "ogg", "m4a", "aac", "opus", "amr", "3ga", "wma")
    private val VIDEO_EXTS = setOf("mp4", "m4v", "mov", "webm", "avi", "mkv", "3gp", "ts")

    private val STRICT_MAGIC_BINARY_EXTS = setOf(
        "png", "jpg", "jpeg", "webp", "bmp", "dib", "gif", "psd", "psb", "tif", "tiff",
        "heic", "heif", "avif", "avifs", "pdf", "zip", "docx", "epub", "cbz", "cbr",
        "7z", "rar", "tar.gz", "tgz", "hzm", "woff", "woff2", "otf", "ttf", "flac", "ogg", "opus", "wav"
    )
    private val TEXT_EXTS = setOf("json", "geojson", "xml", "kml", "csv", "tsv", "css", "md", "markdown", "txt", "svg")
    private val BINARY_NON_TEXT_EXTS = AUDIO_EXTS + VIDEO_EXTS + STRICT_MAGIC_BINARY_EXTS + setOf("dng", "cr2", "nef", "arw", "orf", "rw2")

    fun detectFromBytesAndName(
        uriString: String,
        displayName: String,
        headerBytes: ByteArray,
        byteSize: Long
    ): DetectedSource {
        val cleanName = displayName.trim().ifBlank { "file" }
        val ext = extractCompositeExtension(cleanName)
        val (format, method, mismatch) = sniff(ext, headerBytes, byteSize)
        val (baseNoteId, baseNoteEn) = limitationHint(format)
        val (noteId, noteEn) = if (mismatch && ext.isNotBlank()) {
            val mismatchId = "Ekstensi .$ext tidak cocok dengan isi berkas sebenarnya ($method). Target ditentukan dari isi asli."
            val mismatchEn = "Extension .$ext does not match the actual file content ($method). Targets follow the real format."
            listOfNotNull(mismatchId, baseNoteId).joinToString(" ") to
                listOfNotNull(mismatchEn, baseNoteEn).joinToString(" ")
        } else {
            baseNoteId to baseNoteEn
        }
        return DetectedSource(
            uriString = uriString,
            displayName = cleanName,
            extension = ext,
            byteSize = byteSize,
            format = format,
            detectionMethod = method,
            noteId = noteId,
            noteEn = noteEn,
            extensionMismatch = mismatch
        )
    }

    fun detectFromUri(context: Context, storage: FileStorageManager, uri: Uri): DetectedSource {
        val name = storage.getDisplayName(uri) ?: uri.lastPathSegment ?: "file"
        val size = querySizeBytes(context, uri)
        val header = runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val buf = ByteArray(4096)
                val read = stream.read(buf)
                if (read > 0) buf.copyOf(read) else ByteArray(0)
            }
        }.getOrNull() ?: ByteArray(0)

        var detected = detectFromBytesAndName(uri.toString(), name, header, size)
        // Inspect actual ZIP manifest for PK archives instead of trusting .docx/.epub/.cbz extensions alone.
        if (detected.format in setOf(SourceFormat.ZIP, SourceFormat.DOCX, SourceFormat.EPUB, SourceFormat.CBZ)) {
            val zipSubType = sniffZipSubtype(context, uri, detected.extension)
            if (zipSubType != null) {
                val expectedExt = when (zipSubType) {
                    SourceFormat.DOCX -> "docx"
                    SourceFormat.EPUB -> "epub"
                    SourceFormat.CBZ -> "cbz"
                    else -> "zip"
                }
                val mismatch = detected.extension.isNotBlank() &&
                    detected.extension != expectedExt &&
                    detected.extension in setOf("docx", "epub", "cbz")
                val (baseId, baseEn) = limitationHint(zipSubType)
                val (nId, nEn) = if (mismatch) {
                    listOfNotNull(
                        "Ekstensi .${detected.extension} tidak cocok dengan isi arsip ZIP ($expectedExt).",
                        baseId
                    ).joinToString(" ") to listOfNotNull(
                        "Extension .${detected.extension} does not match the ZIP package contents ($expectedExt).",
                        baseEn
                    ).joinToString(" ")
                } else {
                    baseId to baseEn
                }
                detected = detected.copy(
                    format = zipSubType,
                    detectionMethod = "ZIP/Manifest",
                    noteId = nId,
                    noteEn = nEn,
                    extensionMismatch = mismatch
                )
            }
        }
        return detected
    }

    private fun sniffZipSubtype(context: Context, uri: Uri, ext: String): SourceFormat? = runCatching {
        context.contentResolver.openInputStream(uri)?.use { raw ->
            ZipInputStream(raw.buffered()).use { zis ->
                var inspected = 0
                var hasImages = false
                try {
                    while (inspected < 512) {
                        val entry = zis.nextEntry ?: break
                        inspected++
                        val name = entry.name
                        if (name == "word/document.xml") return@runCatching SourceFormat.DOCX
                        if (name == "META-INF/container.xml") return@runCatching SourceFormat.EPUB
                        if (!entry.isDirectory && EbookEngine.isImageName(name)) {
                            hasImages = true
                        }
                        zis.closeEntry()
                    }
                } catch (_: Exception) {
                    // Encrypted or truncated entry payload after headers; preserve inspected classification if entries exist.
                }
                when {
                    inspected == 0 -> null
                    ext == "cbz" -> if (hasImages) SourceFormat.CBZ else SourceFormat.ZIP
                    else -> SourceFormat.ZIP
                }
            }
        }
    }.getOrNull()

    private fun querySizeBytes(context: Context, uri: Uri): Long {
        if (uri.scheme == "file") {
            return uri.path?.let { java.io.File(it).length() } ?: -1L
        }
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getLong(0) else -1L
            }
        }.getOrNull() ?: -1L
    }

    fun extractCompositeExtension(name: String): String {
        val lower = name.lowercase(Locale.US)
        if (lower.endsWith(".tar.gz")) return "tar.gz"
        return lower.substringAfterLast('.', "")
    }

    private data class SniffResult(
        val format: SourceFormat,
        val method: String,
        val mismatch: Boolean = false
    )

    private fun sniff(ext: String, bytes: ByteArray, byteSize: Long = bytes.size.toLong()): SniffResult {
        // 1. Magic-byte / header detection first for deterministic binary & structured types
        if (bytes.size >= 8 && bytes.copyOfRange(0, 8).contentEquals("HARIMUA1".toByteArray(Charsets.UTF_8))) {
            return SniffResult(SourceFormat.HZM, "Header", ext.isNotBlank() && ext != "hzm")
        }
        if (bytes.size >= 5 && bytes.copyOfRange(0, 5).contentEquals("%PDF-".toByteArray(Charsets.US_ASCII))) {
            return SniffResult(SourceFormat.PDF, "Header", ext.isNotBlank() && ext != "pdf")
        }
        if (bytes.size >= 8 &&
            bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte()
        ) {
            return SniffResult(SourceFormat.PNG, "Header", ext.isNotBlank() && ext != "png")
        }
        if (bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte()) {
            return SniffResult(SourceFormat.JPG, "Header", ext.isNotBlank() && ext !in setOf("jpg", "jpeg"))
        }
        if (bytes.size >= 12 &&
            bytes.copyOfRange(0, 4).contentEquals("RIFF".toByteArray(Charsets.US_ASCII))
        ) {
            val tag = String(bytes, 8, 4, Charsets.US_ASCII)
            when (tag) {
                "WEBP" -> return SniffResult(SourceFormat.WEBP, "Header", ext.isNotBlank() && ext != "webp")
                "WAVE" -> return SniffResult(SourceFormat.WAV, "Header", ext.isNotBlank() && ext != "wav")
                "AVI " -> return SniffResult(SourceFormat.AVI, "Header", ext.isNotBlank() && ext != "avi")
            }
        }
        if (bytes.size >= 18 && bytes[0] == 'B'.code.toByte() && bytes[1] == 'M'.code.toByte()) {
            val dibSize = ByteBuffer.wrap(bytes, 14, 4).order(ByteOrder.LITTLE_ENDIAN).int
            if (dibSize in setOf(12, 40, 52, 56, 64, 108, 124)) {
                return SniffResult(SourceFormat.BMP, "Header", ext.isNotBlank() && ext !in setOf("bmp", "dib"))
            }
        }
        if (bytes.size >= 4 && bytes.copyOfRange(0, 4).contentEquals("GIF8".toByteArray(Charsets.US_ASCII))) {
            return SniffResult(SourceFormat.GIF, "Header", ext.isNotBlank() && ext != "gif")
        }
        if (bytes.size >= 4 && bytes.copyOfRange(0, 4).contentEquals("8BPS".toByteArray(Charsets.US_ASCII))) {
            return SniffResult(SourceFormat.PSD, "Header", ext.isNotBlank() && ext !in setOf("psd", "psb"))
        }
        if (bytes.size >= 4 && (
                (bytes[0] == 'I'.code.toByte() && bytes[1] == 'I'.code.toByte() && bytes[2] == 42.toByte() && bytes[3] == 0.toByte()) ||
                    (bytes[0] == 'M'.code.toByte() && bytes[1] == 'M'.code.toByte() && bytes[2] == 0.toByte() && bytes[3] == 42.toByte())
                )
        ) {
            return if (ext == "dng") {
                SniffResult(SourceFormat.DNG, "Header")
            } else {
                SniffResult(SourceFormat.TIFF, "Header", ext.isNotBlank() && ext !in setOf("tif", "tiff"))
            }
        }
        if (bytes.size >= 12 && bytes.copyOfRange(4, 8).contentEquals("ftyp".toByteArray(Charsets.US_ASCII))) {
            val brand = String(bytes, 8, 4, Charsets.US_ASCII).lowercase(Locale.US)
            return when {
                brand.startsWith("heic") || brand.startsWith("heix") || brand.startsWith("mif1") ->
                    SniffResult(SourceFormat.HEIC, "Header", ext.isNotBlank() && ext !in setOf("heic", "heif"))
                brand.startsWith("avif") || brand.startsWith("avis") ->
                    SniffResult(SourceFormat.AVIF, "Header", ext.isNotBlank() && ext !in setOf("avif", "avifs"))
                brand.startsWith("m4a") ->
                    SniffResult(SourceFormat.M4A_AAC, "Header", ext.isNotBlank() && ext !in setOf("m4a", "aac"))
                else ->
                    SniffResult(SourceFormat.MP4, "Header", ext.isNotBlank() && ext !in setOf("mp4", "m4v", "mov", "3gp"))
            }
        }
        if (bytes.size >= 4 &&
            bytes[0] == 0x1A.toByte() && bytes[1] == 0x45.toByte() &&
            bytes[2] == 0xDF.toByte() && bytes[3] == 0xA3.toByte()
        ) {
            val fmt = if (ext == "webm") SourceFormat.WEBM else SourceFormat.MKV
            return SniffResult(fmt, "Header")
        }
        if (bytes.size >= 4 && bytes.copyOfRange(0, 4).contentEquals("fLaC".toByteArray(Charsets.US_ASCII))) {
            return SniffResult(SourceFormat.FLAC, "Header", ext.isNotBlank() && ext != "flac")
        }
        if (bytes.size >= 4 && bytes.copyOfRange(0, 4).contentEquals("OggS".toByteArray(Charsets.US_ASCII))) {
            val fmt = if (ext == "opus") SourceFormat.OPUS else SourceFormat.OGG
            return SniffResult(fmt, "Header")
        }
        if (bytes.size >= 3 && (
                bytes.copyOfRange(0, 3).contentEquals("ID3".toByteArray(Charsets.US_ASCII)) ||
                    (bytes[0] == 0xFF.toByte() && (bytes[1].toInt() and 0xE0) == 0xE0)
                )
        ) {
            return SniffResult(SourceFormat.MP3, "Header", ext.isNotBlank() && ext != "mp3")
        }
        if (bytes.size >= 6 &&
            bytes.copyOfRange(0, 6).contentEquals(
                byteArrayOf(0x37, 0x7A, 0xBC.toByte(), 0xAF.toByte(), 0x27, 0x1C)
            )
        ) {
            return SniffResult(SourceFormat.SEVEN_Z, "Header", ext.isNotBlank() && ext != "7z")
        }
        if (bytes.size >= 4 && bytes.copyOfRange(0, 4).contentEquals("Rar!".toByteArray(Charsets.US_ASCII))) {
            val fmt = if (ext == "cbr") SourceFormat.CBR else SourceFormat.RAR
            return SniffResult(fmt, "Header", ext.isNotBlank() && ext !in setOf("rar", "cbr"))
        }
        if (bytes.size >= 2 && bytes[0] == 0x1F.toByte() && bytes[1] == 0x8B.toByte()) {
            return SniffResult(SourceFormat.TAR_GZ, "Header", ext.isNotBlank() && ext !in setOf("tar.gz", "tgz", "gz"))
        }
        when (FontEngine.detectKind(bytes)) {
            FontEngine.Kind.WOFF2 -> return SniffResult(SourceFormat.WOFF2, "Header", ext.isNotBlank() && ext != "woff2")
            FontEngine.Kind.WOFF -> return SniffResult(SourceFormat.WOFF, "Header", ext.isNotBlank() && ext != "woff")
            FontEngine.Kind.OTF -> return SniffResult(SourceFormat.OTF, "Header", ext.isNotBlank() && ext != "otf")
            FontEngine.Kind.TTF -> return SniffResult(SourceFormat.TTF, "Header", ext.isNotBlank() && ext != "ttf")
            FontEngine.Kind.UNKNOWN -> Unit
        }
        if (bytes.size >= 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()) {
            return when (ext) {
                "docx" -> SniffResult(SourceFormat.DOCX, "Header")
                "epub" -> SniffResult(SourceFormat.EPUB, "Header")
                "cbz" -> SniffResult(SourceFormat.CBZ, "Header")
                else -> SniffResult(SourceFormat.ZIP, "Header", ext.isNotBlank() && ext != "zip")
            }
        }
        if (isBinaryStlHeader(bytes, byteSize)) {
            return SniffResult(SourceFormat.STL, "Header", ext.isNotBlank() && ext != "stl")
        }
        if (bytes.isNotEmpty() && looksLikeText(bytes)) {
            val headText = bytes.toString(Charsets.UTF_8).trimStart()
            if (headText.startsWith("<svg", ignoreCase = true) ||
                (headText.startsWith("<?xml", ignoreCase = true) && headText.contains("<svg", ignoreCase = true))
            ) {
                return SniffResult(SourceFormat.SVG, "Content", ext.isNotBlank() && ext !in setOf("svg", "xml"))
            }
            if (ConverterEngine.looksLikeAsciiStl(bytes)) {
                return SniffResult(SourceFormat.STL, "Header", ext.isNotBlank() && ext != "stl")
            }
            if (looksLikeBase64Image(headText)) {
                val expected = ext.isBlank() || ext in setOf("txt", "b64", "base64")
                return SniffResult(SourceFormat.BASE64_IMAGE, "Content", !expected)
            }
            // Do not allow plaintext content with a spoofed binary extension (e.g. text renamed .png or .mp3)
            if (ext in BINARY_NON_TEXT_EXTS) {
                val textDetected = detectStructuredTextStrict(headText)
                return if (textDetected != SourceFormat.UNKNOWN) {
                    SniffResult(textDetected, "Content", mismatch = true)
                } else {
                    SniffResult(SourceFormat.UNKNOWN, "Content", mismatch = true)
                }
            }
            val strictText = detectStructuredTextStrict(headText)
            // Validate structured text extensions against actual text structure
            return when (ext) {
                "json", "geojson" -> {
                    when (strictText) {
                        SourceFormat.JSON, SourceFormat.GEOJSON -> SniffResult(strictText, "Content")
                        SourceFormat.XML -> SniffResult(SourceFormat.XML, "Content", mismatch = true)
                        else -> SniffResult(SourceFormat.UNKNOWN, "Content", mismatch = true)
                    }
                }
                "xml", "kml" -> {
                    if (headText.startsWith("<")) SniffResult(SourceFormat.XML, "Content")
                    else if (strictText != SourceFormat.UNKNOWN) SniffResult(strictText, "Content", mismatch = true)
                    else SniffResult(SourceFormat.UNKNOWN, "Content", mismatch = true)
                }
                "csv" -> {
                    if (strictText != SourceFormat.UNKNOWN) {
                        SniffResult(strictText, "Content", mismatch = true)
                    } else if (!headText.contains(',') && headText.contains('\t')) {
                        SniffResult(SourceFormat.TSV, "Content", mismatch = true)
                    } else {
                        SniffResult(SourceFormat.CSV, "Content")
                    }
                }
                "tsv" -> {
                    if (strictText != SourceFormat.UNKNOWN) {
                        SniffResult(strictText, "Content", mismatch = true)
                    } else if (!headText.contains('\t') && headText.contains(',')) {
                        SniffResult(SourceFormat.CSV, "Content", mismatch = true)
                    } else {
                        SniffResult(SourceFormat.TSV, "Content")
                    }
                }
                "css" -> {
                    if (strictText != SourceFormat.UNKNOWN) {
                        SniffResult(strictText, "Content", mismatch = true)
                    } else if (byteSize in 1L..bytes.size.toLong() && (!headText.contains('{') || !headText.contains(':'))) {
                        SniffResult(SourceFormat.UNKNOWN, "Content", mismatch = true)
                    } else {
                        SniffResult(SourceFormat.CSS, "Content")
                    }
                }
                "md", "markdown" -> {
                    if (strictText != SourceFormat.UNKNOWN && strictText != SourceFormat.BASE64_IMAGE) {
                        SniffResult(strictText, "Content", mismatch = true)
                    } else {
                        SniffResult(SourceFormat.MARKDOWN, "Content")
                    }
                }
                "svg" -> SniffResult(SourceFormat.UNKNOWN, "Content", mismatch = true)
                else -> {
                    SniffResult(strictText, if (strictText != SourceFormat.UNKNOWN) "Content" else "Ext")
                }
            }
        }

        // 2. Non-empty binary data that failed strict magic signatures must never be trusted by extension alone.
        if (bytes.isNotEmpty()) {
            if (ext in TEXT_EXTS || ext in STRICT_MAGIC_BINARY_EXTS) {
                return SniffResult(SourceFormat.UNKNOWN, "Header", mismatch = true)
            }
            return when (ext) {
                in VIDEO_EXTS -> SniffResult(SourceFormat.OTHER_VIDEO, "Ext")
                in AUDIO_EXTS -> SniffResult(SourceFormat.OTHER_AUDIO, "Ext")
                "dng" -> SniffResult(SourceFormat.DNG, "Ext")
                "cr2", "nef", "arw", "orf", "rw2" -> SniffResult(SourceFormat.RAW_OTHER, "Ext")
                "stl" -> SniffResult(SourceFormat.STL, "Ext")
                else -> SniffResult(SourceFormat.UNKNOWN, "Header")
            }
        }

        // 3. Extension fallback only when header bytes are unavailable (empty)
        val byExt = when (ext) {
            "mp4", "m4v" -> SourceFormat.MP4
            "webm" -> SourceFormat.WEBM
            "avi" -> SourceFormat.AVI
            "mkv" -> SourceFormat.MKV
            in VIDEO_EXTS -> SourceFormat.OTHER_VIDEO
            "mp3" -> SourceFormat.MP3
            "wav" -> SourceFormat.WAV
            "flac" -> SourceFormat.FLAC
            "ogg" -> SourceFormat.OGG
            "m4a", "aac" -> SourceFormat.M4A_AAC
            "opus" -> SourceFormat.OPUS
            in AUDIO_EXTS -> SourceFormat.OTHER_AUDIO
            "jpg", "jpeg" -> SourceFormat.JPG
            "png" -> SourceFormat.PNG
            "webp" -> SourceFormat.WEBP
            "bmp", "dib" -> SourceFormat.BMP
            "heic", "heif" -> SourceFormat.HEIC
            "avif", "avifs" -> SourceFormat.AVIF
            "dng" -> SourceFormat.DNG
            "tif", "tiff" -> SourceFormat.TIFF
            "psd", "psb" -> SourceFormat.PSD
            "cr2", "nef", "arw", "orf", "rw2" -> SourceFormat.RAW_OTHER
            "svg" -> SourceFormat.SVG
            "gif" -> SourceFormat.GIF
            "pdf" -> SourceFormat.PDF
            "docx" -> SourceFormat.DOCX
            "epub" -> SourceFormat.EPUB
            "cbz" -> SourceFormat.CBZ
            "cbr" -> SourceFormat.CBR
            "zip" -> SourceFormat.ZIP
            "tar.gz", "tgz" -> SourceFormat.TAR_GZ
            "7z" -> SourceFormat.SEVEN_Z
            "rar" -> SourceFormat.RAR
            "hzm" -> SourceFormat.HZM
            "ttf" -> SourceFormat.TTF
            "otf" -> SourceFormat.OTF
            "woff" -> SourceFormat.WOFF
            "woff2" -> SourceFormat.WOFF2
            "stl" -> SourceFormat.STL
            "geojson" -> SourceFormat.GEOJSON
            "json" -> SourceFormat.JSON
            "xml", "kml" -> SourceFormat.XML
            "csv" -> SourceFormat.CSV
            "tsv" -> SourceFormat.TSV
            "css" -> SourceFormat.CSS
            "md", "markdown" -> SourceFormat.MARKDOWN
            else -> SourceFormat.UNKNOWN
        }
        return SniffResult(byExt, "Ext")
    }

    private fun looksLikeText(bytes: ByteArray): Boolean {
        if (bytes.isEmpty()) return false
        var controlCount = 0
        for (b in bytes) {
            val u = b.toInt() and 0xFF
            if (u == 0) return false
            if (u < 0x20 && u != 0x09 && u != 0x0A && u != 0x0D && u != 0x0C) {
                controlCount++
            }
        }
        return controlCount * 50 <= bytes.size
    }

    private fun isBinaryStlHeader(bytes: ByteArray, byteSize: Long): Boolean {
        if (bytes.size < 84) return false
        val triangles = ByteBuffer.wrap(bytes, 80, 4).order(ByteOrder.LITTLE_ENDIAN).int
        if (triangles <= 0) return false
        val expectedBytes = 84L + 50L * triangles
        return if (byteSize > 0L) expectedBytes == byteSize else expectedBytes == bytes.size.toLong()
    }

    private fun looksLikeBase64Image(trimmed: String): Boolean {
        if (trimmed.startsWith("data:image/", ignoreCase = true)) return true
        val compact = trimmed.filterNot { it.isWhitespace() }
        if (compact.length < 16) return false
        val hasImageMagicPrefix = compact.startsWith("iVBORw0KGgo") ||
            compact.startsWith("/9j/") ||
            compact.startsWith("R0lGOD") ||
            compact.startsWith("UklGR")
        if (!hasImageMagicPrefix) return false
        return compact.all { ch ->
            ch in 'A'..'Z' || ch in 'a'..'z' || ch in '0'..'9' || ch == '+' || ch == '/' || ch == '='
        }
    }

    private fun isBareGeoJsonText(lower: String): Boolean {
        if (lower.contains("\"featurecollection\"") || lower.contains("\"geometrycollection\"")) return true
        if (lower.contains("\"geometry\"") && lower.contains("\"coordinates\"")) return true
        return lower.contains("\"coordinates\"") && (
            lower.contains("\"point\"") ||
                lower.contains("\"multipoint\"") ||
                lower.contains("\"linestring\"") ||
                lower.contains("\"multilinestring\"") ||
                lower.contains("\"polygon\"") ||
                lower.contains("\"multipolygon\"")
            )
    }

    private fun detectStructuredTextStrict(raw: String): SourceFormat {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return SourceFormat.UNKNOWN
        if (looksLikeBase64Image(trimmed)) return SourceFormat.BASE64_IMAGE
        if (trimmed.startsWith("<")) {
            return if (trimmed.contains("<svg", ignoreCase = true)) SourceFormat.SVG else SourceFormat.XML
        }
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            val lower = trimmed.lowercase(Locale.US)
            return if (isBareGeoJsonText(lower)) SourceFormat.GEOJSON else SourceFormat.JSON
        }
        return SourceFormat.UNKNOWN
    }

    fun detectTextFormat(raw: String): SourceFormat {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return SourceFormat.UNKNOWN
        if (looksLikeBase64Image(trimmed)) return SourceFormat.BASE64_IMAGE
        if (trimmed.startsWith("<")) {
            return if (trimmed.contains("<svg", ignoreCase = true)) SourceFormat.SVG else SourceFormat.XML
        }
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            val lower = trimmed.lowercase(Locale.US)
            return if (isBareGeoJsonText(lower)) SourceFormat.GEOJSON else SourceFormat.JSON
        }
        val lines = trimmed.lines().filter { it.isNotBlank() }
        if (lines.any { it.startsWith("#") || it.startsWith("- ") || it.startsWith("```") || it.contains("**") }) {
            return SourceFormat.MARKDOWN
        }
        if (trimmed.contains("{") && trimmed.contains("}") && trimmed.contains(":") && trimmed.contains(";")) {
            return SourceFormat.CSS
        }
        if (lines.any { it.contains('\t') }) return SourceFormat.TSV
        if (lines.any { it.contains(',') }) return SourceFormat.CSV
        return SourceFormat.MARKDOWN
    }

    private fun limitationHint(format: SourceFormat): Pair<String?, String?> = when (format) {
        SourceFormat.WOFF2 ->
            "WOFF2 ditolak oleh FontEngine karena memerlukan dekompresi Brotli dan transformasi glyf/hmtx yang belum dibundel." to
                "WOFF2 is refused by FontEngine because Brotli decompression and glyf/hmtx transforms are not bundled."
        SourceFormat.OTF ->
            "OTF (CFF) hanya mendukung konversi ke WOFF 1.0. Konversi OTF → TTF dan subsetting CFF tidak didukung." to
                "OTF (CFF) only converts to WOFF 1.0. OTF → TTF outline conversion and CFF subsetting are unsupported."
        SourceFormat.TIFF ->
            "Format TIFF tidak didukung (tidak ada decoder/encoder TIFF di perangkat)." to
                "TIFF is not supported (no TIFF codec bundled)."
        SourceFormat.PSD ->
            "File PSD berlapis tidak dapat diparse on-device." to
                "Layered PSD files cannot be parsed on-device."
        SourceFormat.DNG ->
            "Dekode DNG bergantung pada dukungan decoder perangkat Android dan belum selalu konsisten di semua pabrikan." to
                "DNG decoding depends on device OEM platform decoders and may fail on unsupported devices."
        SourceFormat.RAW_OTHER ->
            "RAW kamera (CR2/NEF/ARW/ORF/RW2) tidak didukung tanpa libraw; hanya DNG pada perangkat yang mendukung." to
                "Camera RAW (CR2/NEF/ARW/ORF/RW2) is unsupported without libraw; only DNG works on supported devices."
        SourceFormat.HEIC ->
            "HEIC hanya bisa didekode di Android 9+ (API 28+); encode ke HEIC tidak tersedia." to
                "HEIC decoding requires Android 9+ (API 28+); HEIC encoding is unavailable."
        SourceFormat.AVIF ->
            "AVIF hanya bisa didekode di Android 12+ (API 31+); encode ke AVIF tidak tersedia." to
                "AVIF decoding requires Android 12+ (API 31+); AVIF encoding is unavailable."
        SourceFormat.RAR, SourceFormat.CBR ->
            "RAR/CBR bersifat baca-saja via junrar; keluaran selalu ditulis sebagai ZIP, PDF, atau EPUB." to
                "RAR/CBR is read-only via junrar; outputs are always written as ZIP, PDF, or EPUB."
        SourceFormat.EPUB ->
            "EPUB dikonversi berdasarkan teks spine (CSS, gambar, dan EPUB ber-DRM ditolak)." to
                "EPUB conversion reads spine text only (CSS, images, and DRM-protected EPUBs are rejected)."
        else -> null to null
    }
}

object ConverterFormatMatrix {

    fun validFormatTargetsFor(
        format: SourceFormat,
        apiLevel: Int = Build.VERSION.SDK_INT
    ): List<ConversionTargetOption> = when (format) {
        SourceFormat.MP4, SourceFormat.WEBM, SourceFormat.AVI, SourceFormat.MKV, SourceFormat.OTHER_VIDEO -> listOf(
            ConversionTargetOption(
                id = "vid_mp4", toolId = "CNV-01", labelId = "Video MP4", labelEn = "MP4 Video",
                outputExt = "mp4", outputBadge = "MP4",
                fixedParams = mapOf("fmt" to "MP4")
            ),
            ConversionTargetOption(
                id = "vid_mkv", toolId = "CNV-01", labelId = "Video MKV", labelEn = "MKV Video",
                outputExt = "mkv", outputBadge = "MKV",
                fixedParams = mapOf("fmt" to "MKV")
            ),
            ConversionTargetOption(
                id = "vid_webm", toolId = "CNV-01", labelId = "Video WebM", labelEn = "WebM Video",
                outputExt = "webm", outputBadge = "WEBM",
                fixedParams = mapOf("fmt" to "WebM")
            ),
            ConversionTargetOption(
                id = "vid_avi", toolId = "CNV-01", labelId = "Video AVI", labelEn = "AVI Video",
                outputExt = "avi", outputBadge = "AVI",
                fixedParams = mapOf("fmt" to "AVI")
            ),
            ConversionTargetOption(
                id = "vid_gif", toolId = "CNV-02", labelId = "GIF Animasi", labelEn = "Animated GIF",
                outputExt = "gif", outputBadge = "GIF",
                visibleParamKeys = listOf("startSec", "maxSec", "fps", "width")
            ),
            ConversionTargetOption(
                id = "vid_audio_mp3", toolId = "CNV-03", labelId = "Ekstrak Audio MP3", labelEn = "Extract MP3 Audio",
                outputExt = "mp3", outputBadge = "MP3",
                fixedParams = mapOf("format" to "MP3"),
                visibleParamKeys = listOf("bitrate")
            ),
            ConversionTargetOption(
                id = "vid_audio_aac", toolId = "CNV-03", labelId = "Ekstrak Audio AAC", labelEn = "Extract AAC Audio",
                outputExt = "m4a", outputBadge = "M4A",
                fixedParams = mapOf("format" to "AAC"),
                visibleParamKeys = listOf("bitrate")
            ),
            ConversionTargetOption(
                id = "vid_audio_wav", toolId = "CNV-03", labelId = "Ekstrak Audio WAV", labelEn = "Extract WAV Audio",
                outputExt = "wav", outputBadge = "WAV",
                isLimitedOrBasic = true,
                limitNoteId = "WAV/FLAC hanya wadah lossless dari trek audio terdekode, tidak menambah detail.",
                limitNoteEn = "WAV/FLAC wraps decoded audio in a lossless container; it does not improve source detail.",
                fixedParams = mapOf("format" to "WAV")
            ),
            ConversionTargetOption(
                id = "vid_audio_flac", toolId = "CNV-03", labelId = "Ekstrak Audio FLAC", labelEn = "Extract FLAC Audio",
                outputExt = "flac", outputBadge = "FLAC",
                isLimitedOrBasic = true,
                limitNoteId = "WAV/FLAC hanya wadah lossless dari trek audio terdekode, tidak menambah detail.",
                limitNoteEn = "WAV/FLAC wraps decoded audio in a lossless container; it does not improve source detail.",
                fixedParams = mapOf("format" to "FLAC")
            ),
            ConversionTargetOption(
                id = "vid_upscale", toolId = "CNV-04", labelId = "Perbesar 1.5–2×", labelEn = "Upscale 1.5–2×",
                outputExt = "mp4", outputBadge = "2x MP4",
                isLimitedOrBasic = true,
                limitNoteId = "Skaler perangkat Media3/FFmpeg (maks 60 detik & input ≤1080p), bukan model super-resolusi AI.",
                limitNoteEn = "Device Media3/FFmpeg scaler (max 60s & ≤1080p input), not a neural super-resolution model.",
                visibleParamKeys = listOf("factor", "sharpness", "quality")
            )
        )

        SourceFormat.MP3, SourceFormat.WAV, SourceFormat.FLAC, SourceFormat.OGG,
        SourceFormat.M4A_AAC, SourceFormat.OPUS, SourceFormat.OTHER_AUDIO -> listOf(
            ConversionTargetOption(
                id = "aud_mp3", toolId = "CNV-05", labelId = "MP3", labelEn = "MP3",
                outputExt = "mp3", outputBadge = "MP3",
                fixedParams = mapOf("format" to "MP3"),
                visibleParamKeys = listOf("bitrate")
            ),
            ConversionTargetOption(
                id = "aud_m4a", toolId = "CNV-05", labelId = "M4A (AAC)", labelEn = "M4A (AAC)",
                outputExt = "m4a", outputBadge = "M4A",
                fixedParams = mapOf("format" to "M4A (AAC)"),
                visibleParamKeys = listOf("bitrate")
            ),
            ConversionTargetOption(
                id = "aud_ogg", toolId = "CNV-05", labelId = "OGG", labelEn = "OGG",
                outputExt = "ogg", outputBadge = "OGG",
                fixedParams = mapOf("format" to "OGG"),
                visibleParamKeys = listOf("bitrate")
            ),
            ConversionTargetOption(
                id = "aud_opus", toolId = "CNV-05", labelId = "OPUS", labelEn = "OPUS",
                outputExt = "opus", outputBadge = "OPUS",
                fixedParams = mapOf("format" to "OPUS"),
                visibleParamKeys = listOf("bitrate")
            ),
            ConversionTargetOption(
                id = "aud_wav", toolId = "CNV-05", labelId = "WAV 16-bit", labelEn = "WAV 16-bit",
                outputExt = "wav", outputBadge = "WAV",
                fixedParams = mapOf("format" to "WAV")
            ),
            ConversionTargetOption(
                id = "aud_flac", toolId = "CNV-05", labelId = "FLAC", labelEn = "FLAC",
                outputExt = "flac", outputBadge = "FLAC",
                fixedParams = mapOf("format" to "FLAC")
            ),
            ConversionTargetOption(
                id = "aud_hires_flac", toolId = "CNV-06", labelId = "Hi-Res FLAC 24-bit", labelEn = "Hi-Res 24-bit FLAC",
                outputExt = "flac", outputBadge = "24-bit FLAC",
                isLimitedOrBasic = true,
                limitNoteId = "Upsampling sumber lossy ke 24-bit/96 kHz tidak memulihkan detail yang sudah hilang.",
                limitNoteEn = "Upsampling a lossy source to 24-bit/96 kHz cannot restore lost detail.",
                fixedParams = mapOf("format" to "FLAC 24-bit"),
                visibleParamKeys = listOf("rate")
            ),
            ConversionTargetOption(
                id = "aud_hires_wav", toolId = "CNV-06", labelId = "Hi-Res WAV 24-bit", labelEn = "Hi-Res 24-bit WAV",
                outputExt = "wav", outputBadge = "24-bit WAV",
                isLimitedOrBasic = true,
                limitNoteId = "Upsampling sumber lossy ke 24-bit/96 kHz tidak memulihkan detail yang sudah hilang.",
                limitNoteEn = "Upsampling a lossy source to 24-bit/96 kHz cannot restore lost detail.",
                fixedParams = mapOf("format" to "WAV 24-bit"),
                visibleParamKeys = listOf("rate")
            ),
            ConversionTargetOption(
                id = "aud_8d", toolId = "CNV-08", labelId = "Audio Spasial 8D", labelEn = "8D Spatial Audio",
                outputExt = when (format) {
                    SourceFormat.WAV -> "wav"
                    SourceFormat.FLAC -> "flac"
                    SourceFormat.OGG -> "ogg"
                    SourceFormat.M4A_AAC -> "m4a"
                    else -> "mp3"
                },
                outputBadge = when (format) {
                    SourceFormat.WAV -> "8D WAV"
                    SourceFormat.FLAC -> "8D FLAC"
                    SourceFormat.OGG -> "8D OGG"
                    SourceFormat.M4A_AAC -> "8D M4A"
                    else -> "8D MP3"
                },
                visibleParamKeys = listOf("rotationHz", "depthPct", "roomReverb")
            ),
            ConversionTargetOption(
                id = "aud_stt", toolId = "CNV-07", labelId = "Transkrip Teks (.txt)", labelEn = "Speech Transcript (.txt)",
                outputExt = "txt", outputBadge = "TXT • AI",
                requiresNetwork = true,
                isLimitedOrBasic = true,
                limitNoteId = "Memerlukan AI Gateway cloud & izin unggah (maks 15 MB).",
                limitNoteEn = "Requires Cloud AI Gateway & upload consent (max 15 MB).",
                visibleParamKeys = listOf("language", "saveReport")
            )
        )

        SourceFormat.JPG, SourceFormat.PNG, SourceFormat.WEBP, SourceFormat.BMP, SourceFormat.GIF -> rasterImageTargets()
        SourceFormat.HEIC -> if (apiLevel >= 28) rasterImageTargets() else emptyList()
        SourceFormat.AVIF -> if (apiLevel >= 31) rasterImageTargets() else emptyList()
        SourceFormat.DNG -> if (apiLevel >= 26) rasterImageTargets() else emptyList()

        SourceFormat.SVG -> listOf(
            ConversionTargetOption(
                id = "svg_png", toolId = "CNV-10", labelId = "Render PNG", labelEn = "Render PNG",
                outputExt = "png", outputBadge = "PNG",
                isLimitedOrBasic = true,
                limitNoteId = "AndroidSVG 1.4: filter efek, font sistem, dan animasi tidak dirender (maks 4096 px).",
                limitNoteEn = "AndroidSVG 1.4: SVG filters, system fonts, and animations are skipped (max 4096 px).",
                fixedParams = mapOf("fmt" to "PNG"),
                visibleParamKeys = listOf("width", "bg")
            ),
            ConversionTargetOption(
                id = "svg_jpg", toolId = "CNV-10", labelId = "Render JPG", labelEn = "Render JPG",
                outputExt = "jpg", outputBadge = "JPG",
                isLimitedOrBasic = true,
                limitNoteId = "AndroidSVG 1.4: filter efek & animasi tidak dirender; JPG memakai latar solid (bawaan putih).",
                limitNoteEn = "AndroidSVG 1.4: filters & animations are skipped; JPG uses a solid background (white by default).",
                fixedParams = mapOf("fmt" to "JPG"),
                defaultParams = mapOf("bg" to "Putih"),
                visibleParamKeys = listOf("width", "bg")
            )
        )

        SourceFormat.PDF -> listOf(
            ConversionTargetOption(
                id = "pdf_png_zip", toolId = "CNV-14", labelId = "Halaman → PNG (.zip)", labelEn = "Pages → PNG (.zip)",
                outputExt = "zip", outputBadge = "PNG ZIP",
                fixedParams = mapOf("fmt" to "PNG"),
                visibleParamKeys = listOf("scale")
            ),
            ConversionTargetOption(
                id = "pdf_jpg_zip", toolId = "CNV-14", labelId = "Halaman → JPG (.zip)", labelEn = "Pages → JPG (.zip)",
                outputExt = "zip", outputBadge = "JPG ZIP",
                fixedParams = mapOf("fmt" to "JPG"),
                visibleParamKeys = listOf("scale")
            ),
            ConversionTargetOption(
                id = "pdf_ocr_txt", toolId = "CNV-15", labelId = "OCR → Teks (.txt)", labelEn = "OCR → Text (.txt)",
                outputExt = "txt", outputBadge = "OCR TXT",
                isLimitedOrBasic = true,
                limitNoteId = "Tesseract 8 bahasa; mengunduh data bahasa (~4–15 MB) otomatis pada pemakaian pertama.",
                limitNoteEn = "Tesseract 8 languages; auto-downloads traineddata (~4–15 MB) on first use.",
                visibleParamKeys = listOf("language")
            ),
            ConversionTargetOption(
                id = "pdf_docx_basic", toolId = "CNV-13", labelId = "Word .docx (Dasar)", labelEn = "Word .docx (Basic)",
                outputExt = "docx", outputBadge = "DOCX • BASIC",
                isLimitedOrBasic = true,
                limitNoteId = "Konversi Dasar on-device: mengekstrak teks per halaman saja; tata letak/tabel kompleks tidak dipertahankan.",
                limitNoteEn = "Basic on-device conversion: extracts per-page text only; complex layouts/tables are not preserved.",
                fixedParams = mapOf("target" to "Word (.docx)")
            ),
            ConversionTargetOption(
                id = "pdf_xlsx_basic", toolId = "CNV-13", labelId = "Excel .xlsx (Dasar)", labelEn = "Excel .xlsx (Basic)",
                outputExt = "xlsx", outputBadge = "XLSX • BASIC",
                isLimitedOrBasic = true,
                limitNoteId = "Konversi Dasar on-device: menulis baris teks per halaman ke lembar kerja tanpa deteksi kolom tabel.",
                limitNoteEn = "Basic on-device conversion: writes page lines to rows without column table reconstruction.",
                fixedParams = mapOf("target" to "Excel (.xlsx)")
            ),
            ConversionTargetOption(
                id = "pdf_pptx_basic", toolId = "CNV-13", labelId = "PowerPoint .pptx (Dasar)", labelEn = "PowerPoint .pptx (Basic)",
                outputExt = "pptx", outputBadge = "PPTX • BASIC",
                isLimitedOrBasic = true,
                limitNoteId = "Konversi Dasar on-device: merender halaman PDF sebagai slide gambar (teks tidak dapat diedit).",
                limitNoteEn = "Basic on-device conversion: renders PDF pages as slide images (text is not editable).",
                fixedParams = mapOf("target" to "PowerPoint (.pptx)")
            )
        )

        SourceFormat.DOCX -> listOf(
            ConversionTargetOption(
                id = "docx_pdf_basic", toolId = "CNV-13", labelId = "Dokumen PDF (Dasar)", labelEn = "PDF Document (Basic)",
                outputExt = "pdf", outputBadge = "PDF • BASIC",
                isLimitedOrBasic = true,
                limitNoteId = "Konversi Dasar on-device: merender paragraf teks .docx; gambar & header/footer dilewati.",
                limitNoteEn = "Basic on-device conversion: renders .docx text paragraphs; images & headers/footers are skipped."
            )
        )

        SourceFormat.EPUB -> listOf(
            ConversionTargetOption(
                id = "epub_txt", toolId = "CNV-19", labelId = "Teks Polos (.txt)", labelEn = "Plain Text (.txt)",
                outputExt = "txt", outputBadge = "TXT",
                isLimitedOrBasic = true,
                limitNoteId = "Mengikuti urutan spine; gambar/CSS diabaikan dan EPUB ber-DRM ditolak.",
                limitNoteEn = "Follows spine order; images/CSS are omitted and DRM-protected EPUBs are refused.",
                fixedParams = mapOf("target" to "TXT")
            ),
            ConversionTargetOption(
                id = "epub_html", toolId = "CNV-19", labelId = "Dokumen HTML (.html)", labelEn = "HTML Document (.html)",
                outputExt = "html", outputBadge = "HTML",
                isLimitedOrBasic = true,
                limitNoteId = "Mengikuti urutan spine; gambar/CSS asli tidak disertakan.",
                limitNoteEn = "Follows spine order; original images/CSS are omitted.",
                fixedParams = mapOf("target" to "HTML")
            ),
            ConversionTargetOption(
                id = "epub_pdf", toolId = "CNV-19", labelId = "PDF Teks (.pdf)", labelEn = "Text PDF (.pdf)",
                outputExt = "pdf", outputBadge = "PDF",
                isLimitedOrBasic = true,
                limitNoteId = "Dirender ulang memakai Helvetica polos tanpa tata letak atau gambar asli buku.",
                limitNoteEn = "Reflowed with plain Helvetica without original book layout or images.",
                fixedParams = mapOf("target" to "PDF")
            )
        )

        SourceFormat.CBZ, SourceFormat.CBR -> listOf(
            ConversionTargetOption(
                id = "comic_pdf", toolId = "CNV-20", labelId = "Komik → PDF", labelEn = "Comic → PDF",
                outputExt = "pdf", outputBadge = "PDF",
                isLimitedOrBasic = format == SourceFormat.CBR,
                limitNoteId = if (format == SourceFormat.CBR) "CBR (RAR) bersifat baca-saja; halaman disusun alami." else null,
                limitNoteEn = if (format == SourceFormat.CBR) "CBR (RAR) is read-only; pages are sorted naturally." else null,
                fixedParams = mapOf("target" to "PDF"),
                visibleParamKeys = listOf("width")
            ),
            ConversionTargetOption(
                id = "comic_epub", toolId = "CNV-20", labelId = "Komik → EPUB", labelEn = "Comic → EPUB",
                outputExt = "epub", outputBadge = "EPUB",
                isLimitedOrBasic = true,
                limitNoteId = "EPUB 3 minimal per gambar halaman (CBR bersifat baca-saja).",
                limitNoteEn = "Minimal EPUB 3 wrapper per page image (CBR is read-only).",
                fixedParams = mapOf("target" to "EPUB")
            )
        )

        SourceFormat.ZIP -> listOf(
            ConversionTargetOption(
                id = "zip_clean", toolId = "CNV-16", labelId = "Ekstrak & Bersihkan ZIP", labelEn = "Extract & Sanitize ZIP",
                outputExt = "zip", outputBadge = "CLEAN ZIP",
                visibleParamKeys = listOf("password")
            ),
            ConversionTargetOption(
                id = "zip_decrypt", toolId = "CNV-18", labelId = "Dekripsi ZIP AES-256 → ZIP", labelEn = "Decrypt AES-256 ZIP → ZIP",
                outputExt = "zip", outputBadge = "PLAIN ZIP",
                isArchiveSecurityAction = true,
                fixedParams = mapOf("mode" to "Dekripsi ZIP AES-256"),
                visibleParamKeys = listOf("password")
            )
        )

        SourceFormat.TAR_GZ, SourceFormat.SEVEN_Z, SourceFormat.RAR -> listOf(
            ConversionTargetOption(
                id = "arch_to_zip", toolId = "CNV-17", labelId = "Konversi ke ZIP Bersih", labelEn = "Convert to Clean ZIP",
                outputExt = "zip", outputBadge = "ZIP",
                isLimitedOrBasic = format == SourceFormat.RAR || format == SourceFormat.TAR_GZ,
                limitNoteId = when (format) {
                    SourceFormat.RAR -> "RAR hanya dibaca (read-only); proteksi zip-slip & batas rasio 200x aktif."
                    SourceFormat.TAR_GZ -> "TAR.GZ tidak mendukung kata sandi; seluruh isi direpack aman ke ZIP."
                    else -> null
                },
                limitNoteEn = when (format) {
                    SourceFormat.RAR -> "RAR is read-only; zip-slip sanitization and 200x zip-bomb caps enforced."
                    SourceFormat.TAR_GZ -> "TAR.GZ does not support passwords; contents are safely repacked into ZIP."
                    else -> null
                },
                visibleParamKeys = if (format == SourceFormat.TAR_GZ) emptyList() else listOf("password")
            )
        )

        SourceFormat.HZM -> listOf(
            ConversionTargetOption(
                id = "hzm_decrypt", toolId = "CNV-18", labelId = "Dekripsi .hzm Lama → ZIP", labelEn = "Decrypt Legacy .hzm → ZIP",
                outputExt = "zip", outputBadge = "PLAIN ZIP",
                isArchiveSecurityAction = true,
                fixedParams = mapOf("mode" to "Dekripsi ZIP AES-256"),
                visibleParamKeys = listOf("password")
            )
        )

        SourceFormat.TTF -> listOf(
            ConversionTargetOption(
                id = "ttf_to_woff", toolId = "CNV-21", labelId = "WOFF 1.0 (.woff)", labelEn = "WOFF 1.0 (.woff)",
                outputExt = "woff", outputBadge = "WOFF",
                fixedParams = mapOf("target" to "TTF/OTF → WOFF")
            ),
            ConversionTargetOption(
                id = "ttf_subset", toolId = "CNV-22", labelId = "Subset Web-Font (.woff)", labelEn = "Subset Web-Font (.woff)",
                outputExt = "woff", outputBadge = "SUBSET WOFF",
                isLimitedOrBasic = true,
                limitNoteId = "Membuang tabel hinting/GSUB/GPOS/kern agar ukuran kecil; hanya glif terpilih dipertahankan.",
                limitNoteEn = "Drops hinting/GSUB/GPOS/kern tables for compactness; keeps selected glyphs.",
                visibleParamKeys = listOf("mode", "chars")
            )
        )

        SourceFormat.OTF -> listOf(
            ConversionTargetOption(
                id = "otf_to_woff", toolId = "CNV-21", labelId = "WOFF 1.0 (.woff)", labelEn = "WOFF 1.0 (.woff)",
                outputExt = "woff", outputBadge = "WOFF",
                isLimitedOrBasic = true,
                limitNoteId = "OTF (CFF) dikonversi ke wadah WOFF 1.0; konversi outline OTF → TTF dan subsetting CFF tidak didukung.",
                limitNoteEn = "OTF (CFF) converts into WOFF 1.0 container; OTF → TTF and CFF subsetting are unsupported.",
                fixedParams = mapOf("target" to "TTF/OTF → WOFF")
            )
        )

        SourceFormat.WOFF -> listOf(
            ConversionTargetOption(
                id = "woff_to_sfnt", toolId = "CNV-21", labelId = "TTF / OTF Asli", labelEn = "Original TTF / OTF",
                outputExt = "ttf", outputBadge = "TTF/OTF",
                fixedParams = mapOf("target" to "WOFF → TTF/OTF")
            ),
            ConversionTargetOption(
                id = "woff_subset", toolId = "CNV-22", labelId = "Subset TrueType → WOFF", labelEn = "Subset TrueType → WOFF",
                outputExt = "woff", outputBadge = "SUBSET WOFF",
                isLimitedOrBasic = true,
                limitNoteId = "Hanya berhasil untuk WOFF berisi glyph TrueType (glyf); font WOFF berbasis CFF ditolak.",
                limitNoteEn = "Works only for TrueType-backed WOFF (glyf); CFF-backed WOFF is refused.",
                visibleParamKeys = listOf("mode", "chars")
            )
        )

        SourceFormat.STL -> listOf(
            ConversionTargetOption(
                id = "stl_to_obj", toolId = "CNV-28", labelId = "Wavefront OBJ (.obj)", labelEn = "Wavefront OBJ (.obj)",
                outputExt = "obj", outputBadge = "OBJ",
                isLimitedOrBasic = true,
                limitNoteId = "Membaca STL ASCII/binary (84+50n byte); normal permukaan tidak disalin ke OBJ.",
                limitNoteEn = "Parses ASCII/binary STL (84+50n bytes); face normals are omitted from OBJ output."
            )
        )

        SourceFormat.JSON -> listOf(
            ConversionTargetOption(
                id = "json_xml", toolId = "CNV-23", labelId = "Konversi → XML", labelEn = "Convert → XML",
                outputExt = "xml", outputBadge = "XML",
                fixedParams = mapOf("dir" to "JSON -> XML")
            ),
            ConversionTargetOption(
                id = "json_pretty", toolId = "CNV-25", labelId = "Pretty JSON", labelEn = "Pretty JSON",
                outputExt = "json", outputBadge = "PRETTY",
                fixedParams = mapOf("mode" to "Pretty JSON")
            ),
            ConversionTargetOption(
                id = "json_minify", toolId = "CNV-25", labelId = "Minify JSON", labelEn = "Minify JSON",
                outputExt = "json", outputBadge = "MINIFY",
                fixedParams = mapOf("mode" to "Minify JSON")
            ),
            ConversionTargetOption(
                id = "json_kml", toolId = "CNV-27", labelId = "GeoJSON → KML", labelEn = "GeoJSON → KML",
                outputExt = "kml", outputBadge = "KML"
            )
        )

        SourceFormat.GEOJSON -> listOf(
            ConversionTargetOption(
                id = "geojson_kml", toolId = "CNV-27", labelId = "Google Earth KML", labelEn = "Google Earth KML",
                outputExt = "kml", outputBadge = "KML"
            ),
            ConversionTargetOption(
                id = "geojson_pretty", toolId = "CNV-25", labelId = "Pretty JSON", labelEn = "Pretty JSON",
                outputExt = "json", outputBadge = "PRETTY",
                fixedParams = mapOf("mode" to "Pretty JSON")
            ),
            ConversionTargetOption(
                id = "geojson_minify", toolId = "CNV-25", labelId = "Minify JSON", labelEn = "Minify JSON",
                outputExt = "json", outputBadge = "MINIFY",
                fixedParams = mapOf("mode" to "Minify JSON")
            )
        )

        SourceFormat.XML -> listOf(
            ConversionTargetOption(
                id = "xml_json", toolId = "CNV-23", labelId = "Konversi → JSON", labelEn = "Convert → JSON",
                outputExt = "json", outputBadge = "JSON",
                fixedParams = mapOf("dir" to "XML -> JSON")
            )
        )

        SourceFormat.CSV -> listOf(
            ConversionTargetOption(
                id = "csv_tsv", toolId = "CNV-24", labelId = "Tabel TSV (Tab)", labelEn = "TSV Table (Tab)",
                outputExt = "tsv", outputBadge = "TSV",
                fixedParams = mapOf("dir" to "CSV -> TSV")
            )
        )

        SourceFormat.TSV -> listOf(
            ConversionTargetOption(
                id = "tsv_csv", toolId = "CNV-24", labelId = "Tabel CSV (Koma)", labelEn = "CSV Table (Comma)",
                outputExt = "csv", outputBadge = "CSV",
                fixedParams = mapOf("dir" to "TSV -> CSV")
            )
        )

        SourceFormat.CSS -> listOf(
            ConversionTargetOption(
                id = "css_pretty", toolId = "CNV-25", labelId = "Pretty CSS", labelEn = "Pretty CSS",
                outputExt = "css", outputBadge = "CSS",
                fixedParams = mapOf("mode" to "Pretty CSS")
            )
        )

        SourceFormat.MARKDOWN -> listOf(
            ConversionTargetOption(
                id = "md_html", toolId = "CNV-26", labelId = "HTML Mandiri", labelEn = "Standalone HTML",
                outputExt = "html", outputBadge = "HTML"
            )
        )

        SourceFormat.BASE64_IMAGE -> listOf(
            ConversionTargetOption(
                id = "b64_png", toolId = "CNV-12", labelId = "Dekode → PNG", labelEn = "Decode → PNG",
                outputExt = "png", outputBadge = "PNG",
                fixedParams = mapOf("mode" to "Decode (Base64 → gambar)")
            )
        )

        SourceFormat.WOFF2, SourceFormat.TIFF, SourceFormat.PSD, SourceFormat.RAW_OTHER, SourceFormat.UNKNOWN -> emptyList()
    }

    private fun rasterImageTargets(): List<ConversionTargetOption> = listOf(
        ConversionTargetOption(
            id = "img_webp", toolId = "CNV-09", labelId = "WebP", labelEn = "WebP",
            outputExt = "webp", outputBadge = "WEBP",
            fixedParams = mapOf("format" to "WebP"),
            visibleParamKeys = listOf("quality", "keepMeta")
        ),
        ConversionTargetOption(
            id = "img_jpg", toolId = "CNV-09", labelId = "JPEG (.jpg)", labelEn = "JPEG (.jpg)",
            outputExt = "jpg", outputBadge = "JPG",
            fixedParams = mapOf("format" to "JPG"),
            visibleParamKeys = listOf("quality", "keepMeta")
        ),
        ConversionTargetOption(
            id = "img_png", toolId = "CNV-09", labelId = "PNG (Transparan)", labelEn = "PNG (Alpha)",
            outputExt = "png", outputBadge = "PNG",
            fixedParams = mapOf("format" to "PNG"),
            visibleParamKeys = listOf("keepMeta")
        ),
        ConversionTargetOption(
            id = "img_pdf", toolId = "CNV-09", labelId = "PDF Halaman Tunggal", labelEn = "Single-Page PDF",
            outputExt = "pdf", outputBadge = "PDF",
            fixedParams = mapOf("format" to "PDF")
        ),
        ConversionTargetOption(
            id = "img_icons", toolId = "CNV-11", labelId = "Paket Ikon + Favicon (.zip)", labelEn = "Icon + Favicon Pack (.zip)",
            outputExt = "zip", outputBadge = "ICON ZIP",
            visibleParamKeys = listOf("shape", "bgHex")
        ),
        ConversionTargetOption(
            id = "img_b64", toolId = "CNV-12", labelId = "Teks Base64 (.txt)", labelEn = "Base64 Text (.txt)",
            outputExt = "txt", outputBadge = "BASE64",
            fixedParams = mapOf("mode" to "Encode (gambar → Base64)"),
            visibleParamKeys = listOf("dataUri")
        )
    )

    /**
     * Encryption into an AES-256 ZIP archive works cleanly for any readable file(s).
     * Kept clearly labeled as an Archive Security action rather than a format conversion.
     */
    fun securityTargetsFor(sources: List<DetectedSource>): List<ConversionTargetOption> {
        if (sources.isEmpty()) return emptyList()
        return listOf(
            ConversionTargetOption(
                id = "any_encrypt_zip",
                toolId = "CNV-18",
                labelId = if (sources.size > 1) "Kunci ${sources.size} File ke ZIP AES-256" else "Kunci ke ZIP AES-256",
                labelEn = if (sources.size > 1) "Lock ${sources.size} Files to AES-256 ZIP" else "Lock to AES-256 ZIP",
                outputExt = "zip",
                outputBadge = "AES-256",
                isArchiveSecurityAction = true,
                supportsBatchQueue = false,
                supportsMultiInputSingleJob = true,
                fixedParams = mapOf("mode" to "Enkripsi ZIP AES-256"),
                visibleParamKeys = listOf("password")
            )
        )
    }

    fun validTargetsForSources(
        sources: List<DetectedSource>,
        apiLevel: Int = Build.VERSION.SDK_INT
    ): List<ConversionTargetOption> {
        if (sources.isEmpty()) return emptyList()
        val perFileTargets = sources.map { src -> validFormatTargetsFor(src.format, apiLevel) }
        val intersected = if (perFileTargets.size == 1) {
            perFileTargets.first()
        } else {
            val commonIds = perFileTargets.drop(1).fold(perFileTargets.first().map { it.id }.toSet()) { acc, list ->
                acc intersect list.map { it.id }.toSet()
            }
            perFileTargets.first().filter { it.id in commonIds }
        }
        return (intersected + securityTargetsFor(sources)).distinctBy { it.id }
    }

    fun unsupportedReason(format: SourceFormat, isEnglish: Boolean, apiLevel: Int = Build.VERSION.SDK_INT): String? = when (format) {
        SourceFormat.WOFF2 -> if (isEnglish) {
            "WOFF2 conversion is not supported on-device (requires Brotli & glyf/hmtx transforms). You can still lock the file into an AES-256 ZIP."
        } else {
            "Konversi WOFF2 tidak didukung di perangkat (butuh Brotli & transformasi glyf/hmtx). Berkas tetap bisa dikunci ke ZIP AES-256."
        }
        SourceFormat.TIFF -> if (isEnglish) {
            "TIFF images cannot be decoded or encoded on-device (no bundled TIFF codec)."
        } else {
            "Gambar TIFF belum bisa didekode/dienkode di perangkat (codec TIFF belum tersedia)."
        }
        SourceFormat.PSD -> if (isEnglish) {
            "Layered PSD/PSB files are not supported on-device."
        } else {
            "Berkas Photoshop PSD/PSB berlapis tidak didukung di perangkat."
        }
        SourceFormat.RAW_OTHER -> if (isEnglish) {
            "Proprietary camera RAW (CR2/NEF/ARW/ORF/RW2) is unsupported without libraw; only DNG is supported on compatible devices."
        } else {
            "RAW kamera (CR2/NEF/ARW/ORF/RW2) belum didukung tanpa libraw; hanya DNG yang didukung pada perangkat tertentu."
        }
        SourceFormat.HEIC -> if (apiLevel < 28) {
            if (isEnglish) "HEIC decoding requires Android 9 (API 28) or higher."
            else "Dekode HEIC membutuhkan Android 9 (API 28) atau lebih baru."
        } else null
        SourceFormat.AVIF -> if (apiLevel < 31) {
            if (isEnglish) "AVIF decoding requires Android 12 (API 31) or higher."
            else "Dekode AVIF membutuhkan Android 12 (API 31) atau lebih baru."
        } else null
        SourceFormat.UNKNOWN -> if (isEnglish) {
            "Unrecognized file format — format-specific conversions are hidden. Only AES-256 ZIP archiving is available."
        } else {
            "Format berkas tidak dikenali — konversi format spesifik disembunyikan. Hanya penguncian ZIP AES-256 yang tersedia."
        }
        else -> null
    }

    fun mixedIncompatibleReason(
        sources: List<DetectedSource>,
        isEnglish: Boolean,
        apiLevel: Int = Build.VERSION.SDK_INT
    ): String? {
        if (sources.size < 2) return null
        val formatTargets = validTargetsForSources(sources, apiLevel).filter { !it.isArchiveSecurityAction }
        if (formatTargets.isNotEmpty()) return null
        if (sources.any { unsupportedReason(it.format, isEnglish, apiLevel) != null }) return null
        val distinctBadges = sources.map { it.format.badge }.distinct().joinToString(", ")
        return if (isEnglish) {
            "Selected files mix incompatible formats ($distinctBadges) with no shared format converter. Remove mismatched files or pack them all into an AES-256 ZIP."
        } else {
            "Berkas yang dipilih mencampur format berbeda ($distinctBadges) tanpa konverter bersama. Hapus berkas yang berbeda atau kunci semuanya ke ZIP AES-256."
        }
    }

    fun validateInputs(
        target: ConversionTargetOption?,
        sources: List<DetectedSource>,
        textInput: String,
        params: Map<String, Any>,
        isEnglish: Boolean,
        isTextMode: Boolean? = null
    ): ConversionValidationResult {
        if (target == null) {
            return ConversionValidationResult(
                isValid = false,
                blockingError = if (isEnglish) "Select a supported target format first." else "Pilih format tujuan yang didukung dulu."
            )
        }
        val warnings = mutableListOf<String>()
        val isDataTextTool = target.toolId in setOf("CNV-23", "CNV-24", "CNV-25", "CNV-26", "CNV-27")
        val isDecodeBase64 = target.toolId == "CNV-12" && target.fixedParams["mode"]?.toString()?.startsWith("Decode") == true

        if (isDataTextTool || isDecodeBase64) {
            val useTextInput = when (isTextMode) {
                true -> true
                false -> false
                null -> textInput.isNotBlank() || sources.isEmpty()
            }
            if (useTextInput) {
                if (textInput.isBlank()) {
                    return ConversionValidationResult(
                        isValid = false,
                        blockingError = if (isEnglish) "Enter source text or switch to Files mode." else "Masukkan teks sumber atau pindah ke mode Berkas."
                    )
                }
                if (target.toolId == "CNV-23" && textInput.length > 4 * 1024 * 1024) {
                    return ConversionValidationResult(
                        isValid = false,
                        blockingError = if (isEnglish) "Input exceeds 4 MB XML/JSON limit." else "Masukan melebihi batas 4 MB XML/JSON."
                    )
                }
                if ((target.id == "json_xml" || target.id == "json_pretty" || target.id == "json_minify" || target.id == "geojson_pretty" || target.id == "geojson_minify") &&
                    runCatching { Json.parseToJsonElement(textInput.trim()) }.isFailure
                ) {
                    return ConversionValidationResult(
                        isValid = false,
                        blockingError = if (isEnglish) "Invalid JSON syntax." else "Sintaks JSON tidak valid."
                    )
                }
                if (target.id == "xml_json" && !textInput.trimStart().startsWith("<")) {
                    return ConversionValidationResult(
                        isValid = false,
                        blockingError = if (isEnglish) "XML input must start with '<'." else "Masukan XML harus diawali tanda '<'."
                    )
                }
                if (target.id == "css_pretty" && (!textInput.contains('{') || !textInput.contains('}') || !textInput.contains(':'))) {
                    return ConversionValidationResult(
                        isValid = false,
                        blockingError = if (isEnglish) "Invalid CSS syntax (expected selector { property: value; })." else "Sintaks CSS tidak valid (diperlukan selector { property: value; })."
                    )
                }
                if (target.toolId == "CNV-27" && !looksLikeValidGeoJson(textInput)) {
                    return ConversionValidationResult(
                        isValid = false,
                        blockingError = if (isEnglish) {
                            "JSON input is not valid GeoJSON (requires FeatureCollection/Feature/Geometry with coordinates)."
                        } else {
                            "Masukan JSON belum berstruktur GeoJSON (memerlukan type FeatureCollection/Feature/Geometry dengan coordinates)."
                        }
                    )
                }
                if (isDecodeBase64 && !isDecodableBase64Payload(textInput)) {
                    return ConversionValidationResult(
                        isValid = false,
                        blockingError = if (isEnglish) {
                            "Base64 payload is invalid or truncated."
                        } else {
                            "Data Base64 tidak valid atau terpotong."
                        }
                    )
                }
            } else {
                if (sources.isEmpty()) {
                    return ConversionValidationResult(
                        isValid = false,
                        blockingError = if (isEnglish) "Pick at least one input file." else "Pilih minimal satu berkas masukan."
                    )
                }
                val maxTextFileBytes = 4L * 1024L * 1024L
                val oversized = sources.firstOrNull { it.byteSize > maxTextFileBytes }
                if (oversized != null) {
                    return ConversionValidationResult(
                        isValid = false,
                        blockingError = if (isEnglish) {
                            "${oversized.displayName} exceeds the 4 MB text conversion limit."
                        } else {
                            "${oversized.displayName} melebihi batas konversi teks 4 MB."
                        }
                    )
                }
            }
            if (target.isLimitedOrBasic) {
                target.limitNote(isEnglish)?.let { warnings += it }
            }
            return ConversionValidationResult(isValid = true, warnings = warnings)
        }

        if (sources.isEmpty()) {
            return ConversionValidationResult(
                isValid = false,
                blockingError = if (isEnglish) "Pick at least one input file." else "Pilih minimal satu berkas masukan."
            )
        }

        if (target.toolId == "CNV-07") {
            val oversized = sources.firstOrNull { it.byteSize > 15L * 1024L * 1024L }
            if (oversized != null) {
                return ConversionValidationResult(
                    isValid = false,
                    blockingError = if (isEnglish) {
                        "${oversized.displayName} exceeds the 15 MB cloud transcription limit."
                    } else {
                        "${oversized.displayName} melebihi batas transkripsi cloud 15 MB."
                    }
                )
            }
        }

        if (target.toolId == "CNV-18") {
            val mode = (target.fixedParams["mode"] ?: params["mode"])?.toString().orEmpty()
            val pwd = params["password"]?.toString().orEmpty()
            val isDecrypt = mode.startsWith("Dekripsi")
            if (isDecrypt && pwd.isBlank()) {
                return ConversionValidationResult(
                    isValid = false,
                    blockingError = if (isEnglish) "Enter the archive password to decrypt." else "Masukkan kata sandi arsip untuk mendekripsi."
                )
            }
            if (!isDecrypt && pwd.length < 8) {
                return ConversionValidationResult(
                    isValid = false,
                    blockingError = if (isEnglish) {
                        "AES-256 ZIP encryption requires a password of at least 8 characters."
                    } else {
                        "Enkripsi ZIP AES-256 membutuhkan kata sandi minimal 8 karakter."
                    }
                )
            }
        }

        if (target.toolId == "CNV-22") {
            val hasOtf = sources.any { it.format == SourceFormat.OTF }
            val hasWoff2 = sources.any { it.format == SourceFormat.WOFF2 }
            if (hasOtf || hasWoff2) {
                return ConversionValidationResult(
                    isValid = false,
                    blockingError = if (isEnglish) {
                        "Web-font subsetting supports only TrueType (.ttf or TrueType-backed .woff); CFF/OTF and WOFF2 are refused."
                    } else {
                        "Subsetting font web hanya mendukung TrueType (.ttf atau .woff TrueType); CFF/OTF dan WOFF2 ditolak."
                    }
                )
            }
        }

        if (target.isLimitedOrBasic) {
            target.limitNote(isEnglish)?.let { warnings += it }
        }
        return ConversionValidationResult(isValid = true, warnings = warnings)
    }

    private fun looksLikeValidGeoJson(raw: String): Boolean {
        val obj = runCatching { Json.parseToJsonElement(raw.trim()) as? JsonObject }.getOrNull() ?: return false
        val type = runCatching { obj["type"]?.jsonPrimitive?.content }.getOrNull() ?: return false
        return type in setOf(
            "FeatureCollection", "Feature", "Point", "MultiPoint",
            "LineString", "MultiLineString", "Polygon", "MultiPolygon", "GeometryCollection"
        )
    }

    private fun isDecodableBase64Payload(raw: String): Boolean {
        val clean = raw.trim().substringAfter("base64,", raw.trim()).filterNot { it.isWhitespace() }
        if (clean.length < 8) return false
        val decoded = runCatching { Base64.getMimeDecoder().decode(clean) }.getOrNull() ?: return false
        return decoded.size >= 8
    }

    /** Sample snippets for Data/Code & Base64 text conversions. */
    fun sampleTextForWorkflow(format: SourceFormat): String = when (format) {
        SourceFormat.JSON -> "{\n  \"app\": \"HariMu\",\n  \"module\": \"Converter\",\n  \"offline\": true\n}"
        SourceFormat.XML -> "<root>\n  <app>HariMu</app>\n  <version>1</version>\n</root>"
        SourceFormat.CSV -> "id,tool,workflow\nCNV-01,Video Transcoder,Video & Audio\nCNV-23,JSON XML,Data & Code"
        SourceFormat.TSV -> "id\ttool\tworkflow\nCNV-24\tCSV TSV\tData & Code"
        SourceFormat.CSS -> ".card{background:#11141c;color:#ffffff;padding:16px;border-radius:12px;}"
        SourceFormat.MARKDOWN -> "# HariMu Converter\n\nKonversi **lokal** dan aman.\n\n- Cepat\n- Tervalidasi"
        SourceFormat.GEOJSON -> "{\"type\":\"FeatureCollection\",\"features\":[{\"type\":\"Feature\",\"properties\":{\"name\":\"Monas Jakarta\"},\"geometry\":{\"type\":\"Point\",\"coordinates\":[106.8271,-6.1754]}}]}"
        else -> "{\n  \"app\": \"HariMu\"\n}"
    }
}
