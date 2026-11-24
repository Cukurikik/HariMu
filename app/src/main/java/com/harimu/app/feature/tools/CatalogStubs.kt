package com.harimu.app.feature.tools

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FontDownload
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.ImageSearch
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Reorder
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.ui.graphics.vector.ImageVector
import com.harimu.app.core.files.ModelRegistry
import com.harimu.app.core.toolapi.InputSpec
import com.harimu.app.core.toolapi.ModelSpec
import com.harimu.app.core.toolapi.Tool
import com.harimu.app.core.toolapi.ToolContext
import com.harimu.app.core.toolapi.ToolGroup
import com.harimu.app.core.toolapi.ToolInput
import com.harimu.app.core.toolapi.ToolParam
import com.harimu.app.core.toolapi.ToolProgress
import com.harimu.app.core.toolapi.ToolResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class StubTool(
    override val id: String,
    override val group: ToolGroup,
    override val engineLabel: String,
    override val plannedPhase: String,
    override val icon: ImageVector,
    override val inputSpec: InputSpec,
    override val params: List<ToolParam> = emptyList(),
    override val requiresNetwork: Boolean = false,
    override val requiresModel: ModelSpec? = null,
    override val aliasOfId: String? = null,
    private val titleId: String,
    private val titleEn: String,
    private val descId: String,
    private val descEn: String
) : Tool {
    override val isStub: Boolean = true

    override fun title(isEnglish: Boolean): String = if (isEnglish) titleEn else titleId

    override fun description(isEnglish: Boolean): String =
        if (isEnglish) "Coming soon ($plannedPhase) — $descEn"
        else "Segera hadir ($plannedPhase) — $descId"

    override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
        emit(ToolProgress.Running(50, if (ctx.isEnglish) "Preparing stub ($plannedPhase)..." else "Menyiapkan stub ($plannedPhase)…"))
        delay(250L)
        val msg = if (ctx.isEnglish) "Coming soon in $plannedPhase ($id)" else "Segera hadir di $plannedPhase ($id)"
        emit(ToolProgress.Done(ToolResult(summary = msg)))
    }
}

fun stubCoreTools(): List<Tool> = listOf(
    StubTool(
        id = "CORE-02", group = ToolGroup.CORE, engineLabel = "CL", plannedPhase = "P11",
        icon = Icons.Filled.MovieCreation, inputSpec = InputSpec.TextInput("Tulis prompt video...", "Write video prompt..."),
        params = listOf(ToolParam.DropdownParam("ratio", "Rasio", "Aspect Ratio", listOf("16:9", "9:16", "1:1"), "16:9")),
        requiresNetwork = true,
        titleId = "Prompt Video Gen", titleEn = "Prompt Video Gen",
        descId = "Buat video dari prompt teks via AI Gateway.",
        descEn = "Generate video clips from text prompts via AI Gateway."
    ),
    StubTool(
        id = "CORE-04", group = ToolGroup.CORE, engineLabel = "CL", plannedPhase = "P11",
        icon = Icons.Filled.ImageSearch, inputSpec = InputSpec.SingleImage,
        params = listOf(ToolParam.TextParam("question", "Pertanyaan", "Question", "Jelaskan isi gambar ini")),
        requiresNetwork = true,
        titleId = "Analyze Images", titleEn = "Analyze Images",
        descId = "Analisis gambar dan tanya-jawab visual lewat AI Gateway.",
        descEn = "Analyze images and answer visual questions via AI Gateway."
    ),
    StubTool(
        id = "CORE-05", group = ToolGroup.CORE, engineLabel = "NAT", plannedPhase = "P14",
        icon = Icons.Filled.Videocam, inputSpec = InputSpec.None,
        params = listOf(ToolParam.DropdownParam("format", "Format Rekam", "Record Format", listOf("MP4", "WebM"), "MP4")),
        titleId = "Screen Recorder", titleEn = "Screen Recorder",
        descId = "Rekam layar penuh dengan MediaProjection dan audio internal opsional.",
        descEn = "Record full screen via MediaProjection with optional internal audio."
    )
)

fun stubPdfTools(): List<Tool> = listOf(
    StubTool(
        id = "PDF-04", group = ToolGroup.PDF, engineLabel = "LIB", plannedPhase = "P12",
        icon = Icons.Filled.Reorder, inputSpec = InputSpec.SinglePdf,
        params = listOf(ToolParam.TextParam("order", "Urutan Halaman", "Page Order", "1,3,2")),
        titleId = "Urutkan Halaman PDF", titleEn = "Reorder PDF Pages",
        descId = "Susun ulang urutan halaman dokumen PDF.", descEn = "Rearrange PDF page order."
    ),
    StubTool(
        id = "PDF-06", group = ToolGroup.PDF, engineLabel = "CL", plannedPhase = "P12",
        icon = Icons.Filled.Description, inputSpec = InputSpec.SinglePdf,
        params = listOf(ToolParam.DropdownParam("target", "Format", "Target Format", listOf("DOCX", "XLSX", "PPTX"), "DOCX")),
        requiresNetwork = true,
        titleId = "PDF ke Word/Excel/PPT", titleEn = "PDF to Word/Excel/PowerPoint",
        descId = "Konversi PDF menjadi dokumen Office.", descEn = "Convert PDF into Office documents."
    ),
    StubTool(
        id = "PDF-07", group = ToolGroup.PDF, engineLabel = "CL", plannedPhase = "P12",
        icon = Icons.Filled.PictureAsPdf, inputSpec = InputSpec.AnyFile,
        requiresNetwork = true,
        titleId = "Word ke PDF", titleEn = "Word to PDF",
        descId = "Konversi dokumen Word ke PDF berkualitas cetak.", descEn = "Convert Word documents into print-ready PDF."
    ),
    StubTool(
        id = "PDF-09", group = ToolGroup.PDF, engineLabel = "LIB", plannedPhase = "P12",
        icon = Icons.Filled.PictureAsPdf, inputSpec = InputSpec.SinglePdf,
        params = listOf(ToolParam.SliderParam("dpi", "Resolusi Gambar", "Image DPI", 72f, 200f, 120f, 12f, "dpi")),
        titleId = "Kompres PDF", titleEn = "Compress PDF",
        descId = "Kecilkan ukuran file PDF tanpa merusak keterbacaan.", descEn = "Shrink PDF file size while preserving readability."
    ),
    StubTool(
        id = "PDF-10", group = ToolGroup.PDF, engineLabel = "LIB", plannedPhase = "P12",
        icon = Icons.Filled.Lock, inputSpec = InputSpec.SinglePdf,
        params = listOf(ToolParam.DropdownParam("cipher", "Enkripsi", "Cipher", listOf("AES-256", "AES-128"), "AES-256")),
        titleId = "Enkripsi PDF", titleEn = "Encrypt PDF",
        descId = "Lindungi PDF dengan kata sandi AES-256/128.", descEn = "Protect PDF with AES-256/128 password."
    ),
    StubTool(
        id = "PDF-11", group = ToolGroup.PDF, engineLabel = "LIB", plannedPhase = "P12",
        icon = Icons.Filled.LockOpen, inputSpec = InputSpec.SinglePdf,
        titleId = "Dekripsi PDF", titleEn = "Decrypt PDF",
        descId = "Hapus proteksi kata sandi dari PDF.", descEn = "Remove password protection from PDF."
    ),
    StubTool(
        id = "PDF-13", group = ToolGroup.PDF, engineLabel = "LIB", plannedPhase = "P12",
        icon = Icons.Filled.PictureAsPdf, inputSpec = InputSpec.SinglePdf,
        titleId = "Hapus Watermark PDF", titleEn = "Remove PDF Watermark",
        descId = "Bersihkan objek watermark yang terdeteksi di halaman PDF.", descEn = "Clean detected watermark objects from PDF pages."
    ),
    StubTool(
        id = "PDF-14", group = ToolGroup.PDF, engineLabel = "LIB", plannedPhase = "P12",
        icon = Icons.Filled.Security, inputSpec = InputSpec.SinglePdf,
        params = listOf(ToolParam.TextParam("pattern", "Teks/Pola Redaksi", "Redact Pattern", "")),
        titleId = "Redaksi Konten PDF", titleEn = "Redact PDF Content",
        descId = "Hapus teks sensitif secara permanen dari aliran PDF.", descEn = "Permanently remove sensitive text from PDF streams."
    ),
    StubTool(
        id = "PDF-15", group = ToolGroup.PDF, engineLabel = "ML/LIB", plannedPhase = "P12",
        icon = Icons.Filled.DocumentScanner, inputSpec = InputSpec.SinglePdf,
        titleId = "Scan PDF ke Teks (OCR)", titleEn = "Scan PDF to Text (OCR)",
        descId = "Ekstrak teks dari PDF hasil pindaian lewat OCR.", descEn = "Extract text from scanned PDFs via OCR."
    ),
    StubTool(
        id = "PDF-16", group = ToolGroup.PDF, engineLabel = "LIB", plannedPhase = "P12",
        icon = Icons.Filled.DocumentScanner, inputSpec = InputSpec.MultipleImages,
        titleId = "Gambar ke PDF Searchable", titleEn = "Image to Searchable PDF",
        descId = "Buat PDF dengan lapisan teks OCR 8 bahasa.", descEn = "Create searchable PDFs with 8-language OCR text layer."
    ),
    StubTool(
        id = "PDF-17", group = ToolGroup.PDF, engineLabel = "CL/ML", plannedPhase = "P12",
        icon = Icons.Filled.DocumentScanner, inputSpec = InputSpec.SinglePdf,
        requiresNetwork = true,
        titleId = "AI Document Recognition", titleEn = "AI Document Recognition",
        descId = "Ekstrak struktur faktur, KTP, atau tabel secara otomatis.", descEn = "Automatically extract structure from invoices or forms."
    ),
    StubTool(
        id = "PDF-19", group = ToolGroup.PDF, engineLabel = "LIB", plannedPhase = "P12",
        icon = Icons.Filled.EditNote, inputSpec = InputSpec.SinglePdf,
        params = listOf(ToolParam.TextParam("author", "Penulis", "Author", "")),
        titleId = "Editor Metadata PDF", titleEn = "PDF Metadata Editor",
        descId = "Ubah judul, penulis, subjek, dan kata kunci PDF.", descEn = "Edit PDF title, author, subject, and keywords."
    ),
    StubTool(
        id = "PDF-21", group = ToolGroup.PDF, engineLabel = "LIB", plannedPhase = "P12",
        icon = Icons.Filled.EditNote, inputSpec = InputSpec.SinglePdf,
        titleId = "Pengisi Formulir PDF", titleEn = "PDF Form Filler (AcroForm)",
        descId = "Isi dan simpan kolom formulir interaktif AcroForm.", descEn = "Fill and save interactive AcroForm fields."
    )
)

fun stubConverterTools(): List<Tool> = listOf(
    StubTool(
        id = "CNV-01", group = ToolGroup.CONVERTER, engineLabel = "FF", plannedPhase = "P13",
        icon = Icons.Filled.Videocam, inputSpec = InputSpec.SingleVideo, aliasOfId = "VID-18",
        titleId = "Universal Video Transcoder", titleEn = "Universal Video Transcoder",
        descId = "Alias VID-18: transkode video lintas wadah MP4/WebM/MKV.", descEn = "Alias VID-18: transcode video across MP4/WebM/MKV."
    ),
    StubTool(
        id = "CNV-02", group = ToolGroup.CONVERTER, engineLabel = "FF", plannedPhase = "P13",
        icon = Icons.Filled.Videocam, inputSpec = InputSpec.SingleVideo, aliasOfId = "VID-06",
        titleId = "Video ke GIF Animasi", titleEn = "Video to Animated GIF",
        descId = "Alias VID-06: ubah klip video menjadi GIF animasi.", descEn = "Alias VID-06: convert video clips into animated GIF."
    ),
    StubTool(
        id = "CNV-03", group = ToolGroup.CONVERTER, engineLabel = "FF", plannedPhase = "P13",
        icon = Icons.Filled.AudioFile, inputSpec = InputSpec.SingleVideo, aliasOfId = "VID-08",
        titleId = "Ekstraktor Audio Video", titleEn = "Video to Audio Extractor",
        descId = "Perluasan VID-08 ke MP3, WAV, FLAC, dan AAC.", descEn = "Extended VID-08 extractor to MP3, WAV, FLAC, and AAC."
    ),
    StubTool(
        id = "CNV-04", group = ToolGroup.CONVERTER, engineLabel = "ML", plannedPhase = "P13",
        icon = Icons.Filled.Videocam, inputSpec = InputSpec.SingleVideo, aliasOfId = "VID-07",
        requiresModel = ModelRegistry.realEsrganLite,
        titleId = "Video Upscaler (AI) 2x", titleEn = "Video Upscaler (AI) 2x",
        descId = "Alias VID-07: tingkatkan resolusi video 2x.", descEn = "Alias VID-07: upscale video resolution 2x."
    ),
    StubTool(
        id = "CNV-05", group = ToolGroup.CONVERTER, engineLabel = "FF", plannedPhase = "P13",
        icon = Icons.Filled.AudioFile, inputSpec = InputSpec.SingleAudio, aliasOfId = "AUD-13",
        titleId = "Universal Audio Transcoder", titleEn = "Universal Audio Transcoder",
        descId = "Alias AUD-13: konversi MP3/WAV/OGG/FLAC/AAC.", descEn = "Alias AUD-13: convert MP3/WAV/OGG/FLAC/AAC."
    ),
    StubTool(
        id = "CNV-06", group = ToolGroup.CONVERTER, engineLabel = "FF", plannedPhase = "P13",
        icon = Icons.Filled.AudioFile, inputSpec = InputSpec.SingleAudio,
        titleId = "Konverter Hi-Res Lossless", titleEn = "Hi-Res Lossless Converter",
        descId = "Konversi audio lossless FLAC dan WAV 24-bit.", descEn = "Convert lossless FLAC and 24-bit WAV audio."
    ),
    StubTool(
        id = "CNV-07", group = ToolGroup.CONVERTER, engineLabel = "ML/CL", plannedPhase = "P13",
        icon = Icons.Filled.AudioFile, inputSpec = InputSpec.SingleAudio, aliasOfId = "AUD-27",
        requiresModel = ModelRegistry.whisperTiny,
        titleId = "Suara ke Teks", titleEn = "Voice to Text Converter",
        descId = "Alias AUD-27: transkrip ucapan ke teks.", descEn = "Alias AUD-27: transcribe speech to text."
    ),
    StubTool(
        id = "CNV-08", group = ToolGroup.CONVERTER, engineLabel = "FF", plannedPhase = "P13",
        icon = Icons.Filled.AudioFile, inputSpec = InputSpec.SingleAudio, aliasOfId = "AUD-05",
        titleId = "Generator Audio Spasial 8D", titleEn = "8D / Spatial Audio Generator",
        descId = "Alias AUD-05: efek audio spasial 8D.", descEn = "Alias AUD-05: 8D spatial audio effect."
    ),
    StubTool(
        id = "CNV-09", group = ToolGroup.CONVERTER, engineLabel = "NAT", plannedPhase = "P13",
        icon = Icons.Filled.Image, inputSpec = InputSpec.SingleImage, aliasOfId = "IMG-23",
        titleId = "Konverter Gambar Universal", titleEn = "Universal Image Converter",
        descId = "Alias IMG-23: konversi PNG/JPG/WebP/AVIF.", descEn = "Alias IMG-23: convert PNG/JPG/WebP/AVIF."
    ),
    StubTool(
        id = "CNV-10", group = ToolGroup.CONVERTER, engineLabel = "LIB", plannedPhase = "P13",
        icon = Icons.Filled.Image, inputSpec = InputSpec.AnyFile,
        titleId = "SVG ke Raster (PNG/JPG)", titleEn = "SVG to Raster (PNG/JPG)",
        descId = "Render grafik vektor SVG menjadi PNG atau JPG.", descEn = "Render SVG vector graphics to PNG or JPG."
    ),
    StubTool(
        id = "CNV-11", group = ToolGroup.CONVERTER, engineLabel = "NAT", plannedPhase = "P13",
        icon = Icons.Filled.Image, inputSpec = InputSpec.SingleImage, aliasOfId = "IMG-24",
        titleId = "Generator Aset Ikon (+Favicon)", titleEn = "Icon Asset Generator (+Favicon)",
        descId = "Alias IMG-24: paket ikon multi-ukuran dan favicon.", descEn = "Alias IMG-24: multi-size icon and favicon pack."
    ),
    StubTool(
        id = "CNV-12", group = ToolGroup.CONVERTER, engineLabel = "NAT", plannedPhase = "P13",
        icon = Icons.Filled.Code, inputSpec = InputSpec.SingleImage, aliasOfId = "IMG-25",
        titleId = "Encoder Gambar Base64", titleEn = "Base64 Image Encoder",
        descId = "Alias IMG-25: ubah gambar ke string Base64.", descEn = "Alias IMG-25: encode images to Base64 strings."
    ),
    StubTool(
        id = "CNV-13", group = ToolGroup.CONVERTER, engineLabel = "CL", plannedPhase = "P13",
        icon = Icons.Filled.PictureAsPdf, inputSpec = InputSpec.AnyFile, aliasOfId = "PDF-06",
        requiresNetwork = true,
        titleId = "PDF ke Word / Word ke PDF", titleEn = "PDF to Word / Word to PDF",
        descId = "Alias PDF-06/07: konversi dua arah PDF dan Word.", descEn = "Alias PDF-06/07: bidirectional PDF and Word conversion."
    ),
    StubTool(
        id = "CNV-15", group = ToolGroup.CONVERTER, engineLabel = "ML/LIB", plannedPhase = "P13",
        icon = Icons.Filled.DocumentScanner, inputSpec = InputSpec.SinglePdf, aliasOfId = "PDF-15",
        titleId = "Scanner OCR ke .txt", titleEn = "OCR Document Scanner to .txt",
        descId = "Alias PDF-15: pindai dokumen ke teks polos.", descEn = "Alias PDF-15: scan documents to plain text."
    ),
    StubTool(
        id = "CNV-17", group = ToolGroup.CONVERTER, engineLabel = "LIB", plannedPhase = "P13",
        icon = Icons.Filled.Archive, inputSpec = InputSpec.AnyFile,
        titleId = "Konverter Lintas Arsip", titleEn = "Cross-Archive Converter",
        descId = "Konversi ZIP, 7Z, TAR.GZ (RAR baca saja).", descEn = "Convert ZIP, 7Z, TAR.GZ (RAR read-only)."
    ),
    StubTool(
        id = "CNV-19", group = ToolGroup.CONVERTER, engineLabel = "LIB", plannedPhase = "P13",
        icon = Icons.Filled.Book, inputSpec = InputSpec.AnyFile,
        titleId = "Konverter EPUB", titleEn = "EPUB Converter",
        descId = "Konversi e-book EPUB ke TXT, HTML, atau PDF.", descEn = "Convert EPUB e-books to TXT, HTML, or PDF."
    ),
    StubTool(
        id = "CNV-20", group = ToolGroup.CONVERTER, engineLabel = "LIB", plannedPhase = "P13",
        icon = Icons.Filled.Book, inputSpec = InputSpec.AnyFile,
        titleId = "Konverter Komik (CBZ/CBR)", titleEn = "Comic Book Converter (CBZ/CBR)",
        descId = "Ubah arsip komik CBZ/CBR ke PDF atau EPUB.", descEn = "Convert CBZ/CBR comic archives to PDF or EPUB."
    ),
    StubTool(
        id = "CNV-21", group = ToolGroup.CONVERTER, engineLabel = "LIB/CL", plannedPhase = "P13",
        icon = Icons.Filled.FontDownload, inputSpec = InputSpec.AnyFile,
        titleId = "Konverter Font Universal", titleEn = "Universal Font Converter",
        descId = "Konversi TTF, OTF, WOFF, dan WOFF2.", descEn = "Convert TTF, OTF, WOFF, and WOFF2 fonts."
    ),
    StubTool(
        id = "CNV-22", group = ToolGroup.CONVERTER, engineLabel = "LIB/CL", plannedPhase = "P13",
        icon = Icons.Filled.FontDownload, inputSpec = InputSpec.AnyFile,
        titleId = "Optimizer Web-Font (Subsetting)", titleEn = "Web-Font Optimizer (Subsetting)",
        descId = "Pangkas glif font yang tidak dipakai untuk web.", descEn = "Subset unused glyphs from web fonts."
    )
)
