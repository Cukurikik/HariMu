package com.harimu.app.core.converter

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.FontDownload
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Meaningful workflow grouping for all 28 Universal Converter tools (CNV-01 through CNV-28).
 * Prevents dumping 28 unrelated conversion entries into a giant flat list.
 */
enum class ConverterWorkflow(
    val titleId: String,
    val titleEn: String,
    val subtitleId: String,
    val subtitleEn: String,
    val icon: ImageVector,
    val toolIds: List<String>
) {
    VIDEO_AUDIO(
        titleId = "Video & Audio",
        titleEn = "Video & Audio",
        subtitleId = "Transkode kontainer, GIF, ekstrak trek, Hi-Res 24-bit, 8D & transkripsi",
        subtitleEn = "Container transcode, GIF, audio extraction, 24-bit Hi-Res, 8D & speech-to-text",
        icon = Icons.Filled.Movie,
        toolIds = listOf("CNV-01", "CNV-02", "CNV-03", "CNV-04", "CNV-05", "CNV-06", "CNV-07", "CNV-08")
    ),
    IMAGES_VECTOR(
        titleId = "Gambar & Vektor",
        titleEn = "Images & Vector",
        subtitleId = "JPG/PNG/WebP/PDF, rasterisasi SVG, paket ikon/favicon & Base64",
        subtitleEn = "JPG/PNG/WebP/PDF, SVG rasterization, icon/favicon packs & Base64",
        icon = Icons.Filled.Image,
        toolIds = listOf("CNV-09", "CNV-10", "CNV-11", "CNV-12")
    ),
    DOCUMENTS_BOOKS(
        titleId = "Dokumen, OCR & E-Book",
        titleEn = "Docs, OCR & E-Books",
        subtitleId = "PDF ↔ Office (Dasar), matriks halaman, OCR 8 bahasa, EPUB & komik CBZ/CBR",
        subtitleEn = "PDF ↔ Office (Basic), page matrix, 8-lang OCR, EPUB & CBZ/CBR comics",
        icon = Icons.Filled.Description,
        toolIds = listOf("CNV-13", "CNV-14", "CNV-15", "CNV-19", "CNV-20")
    ),
    ARCHIVES_SECURITY(
        titleId = "Arsip & Keamanan",
        titleEn = "Archives & Security",
        subtitleId = "Ekstrak aman anti zip-slip, konversi 7Z/RAR/TAR.GZ → ZIP & kunci AES-256",
        subtitleEn = "Zip-slip safe extraction, 7Z/RAR/TAR.GZ → ZIP & AES-256 encryption",
        icon = Icons.Filled.FolderZip,
        toolIds = listOf("CNV-16", "CNV-17", "CNV-18")
    ),
    FONTS_3D(
        titleId = "Font & Mesh 3D",
        titleEn = "Fonts & 3D Mesh",
        subtitleId = "TTF/OTF ⇄ WOFF 1.0, subset TrueType web & konversi STL → OBJ",
        subtitleEn = "TTF/OTF ⇄ WOFF 1.0, TrueType web subsetting & STL → OBJ mesh",
        icon = Icons.Filled.FontDownload,
        toolIds = listOf("CNV-21", "CNV-22", "CNV-28")
    ),
    DATA_CODE(
        titleId = "Data, Kode & Geo",
        titleEn = "Data, Code & Geo",
        subtitleId = "JSON ⇄ XML, CSV ⇄ TSV, formatter JSON/CSS, Markdown → HTML & GeoJSON → KML",
        subtitleEn = "JSON ⇄ XML, CSV ⇄ TSV, JSON/CSS formatting, Markdown → HTML & GeoJSON → KML",
        icon = Icons.Filled.Code,
        toolIds = listOf("CNV-23", "CNV-24", "CNV-25", "CNV-26", "CNV-27")
    );

    fun title(isEnglish: Boolean): String = if (isEnglish) titleEn else titleId
    fun subtitle(isEnglish: Boolean): String = if (isEnglish) subtitleEn else subtitleId

    companion object {
        private val TOOL_FORMAT_KEYWORDS: Map<String, String> = mapOf(
            "CNV-01" to "mp4 mkv webm avi mov video container transcode",
            "CNV-02" to "gif animated video palette",
            "CNV-03" to "mp3 aac m4a wav flac extract audio",
            "CNV-04" to "upscale 2x scale mp4",
            "CNV-05" to "mp3 m4a aac ogg opus wav flac audio transcode",
            "CNV-06" to "hires 24-bit flac wav 96khz",
            "CNV-07" to "stt whisper speech text transcript txt",
            "CNV-08" to "8d spatial audio surround",
            "CNV-09" to "jpg jpeg png webp pdf heic avif dng bmp gif image",
            "CNV-10" to "svg vector raster png jpg",
            "CNV-11" to "icon favicon adaptive zip png",
            "CNV-12" to "base64 datauri png decode encode",
            "CNV-13" to "pdf docx word xlsx excel pptx powerpoint office",
            "CNV-14" to "pdf png jpg zip matrix pages",
            "CNV-15" to "ocr tesseract pdf txt scan",
            "CNV-16" to "zip extract sanitize zip-slip",
            "CNV-17" to "7z rar tar.gz tgz zip archive",
            "CNV-18" to "aes aes-256 encrypt decrypt hzm zip lock password",
            "CNV-19" to "epub ebook txt html pdf",
            "CNV-20" to "cbz cbr comic pdf epub",
            "CNV-21" to "ttf otf woff font webfont",
            "CNV-22" to "subset woff ttf truetype",
            "CNV-23" to "json xml bidirectional",
            "CNV-24" to "csv tsv table comma tab",
            "CNV-25" to "json css pretty minify format",
            "CNV-26" to "markdown md html",
            "CNV-27" to "geojson kml map coordinates",
            "CNV-28" to "stl obj 3d mesh"
        )

        fun searchKeywordsForTool(toolId: String): String = TOOL_FORMAT_KEYWORDS[toolId].orEmpty()

        fun workflowForTool(toolId: String): ConverterWorkflow? =
            entries.firstOrNull { toolId in it.toolIds }

        fun allMappedToolIds(): Set<String> =
            entries.flatMap { it.toolIds }.toSet()
    }
}
