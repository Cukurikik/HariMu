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
        fun workflowForTool(toolId: String): ConverterWorkflow? =
            entries.firstOrNull { toolId in it.toolIds }

        fun allMappedToolIds(): Set<String> =
            entries.flatMap { it.toolIds }.toSet()
    }
}
