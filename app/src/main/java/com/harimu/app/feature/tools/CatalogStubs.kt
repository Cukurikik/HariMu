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
        id = "CORE-05", group = ToolGroup.CORE, engineLabel = "NAT", plannedPhase = "P14",
        icon = Icons.Filled.Videocam, inputSpec = InputSpec.None,
        params = listOf(ToolParam.DropdownParam("format", "Format Rekam", "Record Format", listOf("MP4", "WebM"), "MP4")),
        titleId = "Screen Recorder", titleEn = "Screen Recorder",
        descId = "Rekam layar penuh dengan MediaProjection dan audio internal opsional.",
        descEn = "Record full screen via MediaProjection with optional internal audio."
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
