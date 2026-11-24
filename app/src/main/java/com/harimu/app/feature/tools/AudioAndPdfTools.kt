package com.harimu.app.feature.tools

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Splitscreen
import androidx.compose.material.icons.filled.SurroundSound
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.WaterDrop
import com.harimu.app.core.engines.AudioEngine
import com.harimu.app.core.engines.PdfEngine
import com.harimu.app.core.toolapi.DedicatedScreenRoute
import com.harimu.app.core.toolapi.InputSpec
import com.harimu.app.core.toolapi.Tool
import com.harimu.app.core.toolapi.ToolContext
import com.harimu.app.core.toolapi.ToolGroup
import com.harimu.app.core.toolapi.ToolInput
import com.harimu.app.core.toolapi.ToolOutputFile
import com.harimu.app.core.toolapi.ToolParam
import com.harimu.app.core.toolapi.ToolProgress
import com.harimu.app.core.toolapi.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

fun audioTools(): List<Tool> = listOf(
    DedicatedTool(
        id = "AUD-02",
        group = ToolGroup.AUDIO,
        engineLabel = "NAT+FF",
        icon = Icons.Filled.Equalizer,
        dedicatedRoute = DedicatedScreenRoute.EQUALIZER,
        titleId = "Pro Equalizer 10-Band (Live + Ekspor)",
        titleEn = "Pro 10-Band Equalizer (Live + Export)",
        descId = "Pratinjau live 10-band (DynamicsProcessing/Equalizer) & ekspor FFmpeg setara.",
        descEn = "Live 10-band AudioFX preview with matching FFmpeg 10-band EQ export."
    ),
    DedicatedTool(
        id = "AUD-21",
        group = ToolGroup.AUDIO,
        engineLabel = "NAT+FF",
        icon = Icons.Filled.GraphicEq,
        dedicatedRoute = DedicatedScreenRoute.AUDIO_STUDIO,
        titleId = "Audio Studio Pro (DAW)",
        titleEn = "Audio Studio Pro (Multitrack DAW)",
        descId = "Mixer multitrack dengan kontrol gain, pan, dan trim.",
        descEn = "Multitrack mixer with per-track gain, pan, and trim."
    )
) + audioForgeBatch1CoreTools() +
    audioForgeBatch1ExtraTools() +
    audioForgeBatch2CoreTools() +
    audioForgeBatch2ExtraTools()

fun pdfTools(): List<Tool> = listOf(
    object : Tool {
        override val id = "PDF-01"
        override val group = ToolGroup.PDF
        override val engineLabel = "NAT"
        override val icon = Icons.Filled.MergeType
        override val inputSpec = InputSpec.MultiplePdfs
        override val params = emptyList<ToolParam>()
        override fun title(isEnglish: Boolean) = if (isEnglish) "Merge PDF" else "Gabung PDF"
        override fun description(isEnglish: Boolean) = if (isEnglish) "Combine multiple PDF documents into one file." else "Gabungkan beberapa dokumen PDF menjadi satu file."
        override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
            require(input.uris.size >= 2) { "Pilih minimal 2 file PDF" }
            emit(ToolProgress.Running(40, "Menggabungkan ${input.uris.size} dokumen PDF..."))
            val dest = ctx.storage.createOutputFile("PDF_MERGE", "pdf")
            val (_, pages) = PdfEngine.mergePdfs(ctx.appContext, ctx.storage, input.uris, dest)
            emit(ToolProgress.Done(ToolResult(listOf(ToolOutputFile(dest, "application/pdf", dest.name)), summary = "Digabung: $pages halaman (${ctx.storage.formatBytes(dest.length())})")))
        }.flowOn(Dispatchers.IO)
    },
    object : Tool {
        override val id = "PDF-02"
        override val group = ToolGroup.PDF
        override val engineLabel = "NAT"
        override val icon = Icons.Filled.Splitscreen
        override val inputSpec = InputSpec.SinglePdf
        override val params = listOf(
            ToolParam.SliderParam("perSplit", "Halaman per Bagian", "Pages per Split", 1f, 20f, 1f, 1f, "")
        )
        override fun title(isEnglish: Boolean) = if (isEnglish) "Split PDF (.zip)" else "Pisah PDF (.zip)"
        override fun description(isEnglish: Boolean) = if (isEnglish) "Split a PDF into smaller PDF parts inside a ZIP." else "Pecah PDF menjadi beberapa dokumen PDF per N halaman."
        override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
            val uri = input.uris.firstOrNull() ?: error("Pilih 1 file PDF")
            emit(ToolProgress.Running(40, "Memisahkan halaman PDF..."))
            val dest = ctx.storage.createOutputFile("PDF_SPLIT", "zip")
            val (_, parts) = PdfEngine.splitPdfToZip(ctx.appContext, ctx.storage, uri, dest, input.getFloat("perSplit", 1f).toInt())
            emit(ToolProgress.Done(ToolResult(listOf(ToolOutputFile(dest, "application/zip", dest.name)), summary = "Dipecah menjadi $parts file PDF di ZIP")))
        }.flowOn(Dispatchers.IO)
    },
    object : Tool {
        override val id = "PDF-03"
        override val group = ToolGroup.PDF
        override val engineLabel = "NAT"
        override val icon = Icons.Filled.RotateRight
        override val inputSpec = InputSpec.SinglePdf
        override val params = listOf(
            ToolParam.DropdownParam("angle", "Sudut Putar", "Rotation Angle", listOf("90", "180", "270"), "90")
        )
        override fun title(isEnglish: Boolean) = if (isEnglish) "Rotate PDF" else "Putar Halaman PDF"
        override fun description(isEnglish: Boolean) = if (isEnglish) "Rotate all PDF pages by 90, 180, or 270 degrees." else "Putar seluruh halaman PDF 90°, 180°, atau 270°."
        override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
            val uri = input.uris.firstOrNull() ?: error("Pilih 1 file PDF")
            emit(ToolProgress.Running(45, "Memutar halaman PDF..."))
            val deg = input.getString("angle", "90").toIntOrNull() ?: 90
            val dest = ctx.storage.createOutputFile("PDF_ROTATE", "pdf")
            val (_, pages) = PdfEngine.transformPdfPages(ctx.appContext, ctx.storage, uri, dest, rotateDegrees = deg)
            emit(ToolProgress.Done(ToolResult(listOf(ToolOutputFile(dest, "application/pdf", dest.name)), summary = "$pages halaman diputar ${deg}°")))
        }.flowOn(Dispatchers.IO)
    },
    object : Tool {
        override val id = "PDF-05"
        override val group = ToolGroup.PDF
        override val engineLabel = "NAT"
        override val icon = Icons.Filled.ContentCut
        override val inputSpec = InputSpec.SinglePdf
        override val params = listOf(
            ToolParam.TextParam("pages", "Rentang Halaman (mis. 1,3-5)", "Page Range (e.g. 1,3-5)", "1-2", "1,3-5", "1,3-5")
        )
        override fun title(isEnglish: Boolean) = if (isEnglish) "Extract PDF Pages" else "Ekstrak Halaman PDF"
        override fun description(isEnglish: Boolean) = if (isEnglish) "Extract specific page numbers or ranges into a new PDF." else "Ambil halaman tertentu (contoh: 1,3-5) menjadi PDF baru."
        override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
            val uri = input.uris.firstOrNull() ?: error("Pilih 1 file PDF")
            emit(ToolProgress.Running(45, "Mengekstrak halaman terpilih..."))
            val dest = ctx.storage.createOutputFile("PDF_EXTRACT", "pdf")
            val (_, pages) = PdfEngine.transformPdfPages(ctx.appContext, ctx.storage, uri, dest, pageRangeExpr = input.getString("pages", "1-2"))
            emit(ToolProgress.Done(ToolResult(listOf(ToolOutputFile(dest, "application/pdf", dest.name)), summary = "$pages halaman diekstrak")))
        }.flowOn(Dispatchers.IO)
    },
    object : Tool {
        override val id = "PDF-08"
        override val group = ToolGroup.PDF
        override val engineLabel = "NAT"
        override val icon = Icons.Filled.PictureAsPdf
        override val inputSpec = InputSpec.MultipleImages
        override val params = listOf(
            ToolParam.DropdownParam("pageSize", "Ukuran Halaman", "Page Size", listOf("A4", "Letter", "Fit"), "A4")
        )
        override fun title(isEnglish: Boolean) = if (isEnglish) "Images to PDF" else "Gambar ke PDF"
        override fun description(isEnglish: Boolean) = if (isEnglish) "Convert one or more images into a multi-page PDF." else "Susun satu atau beberapa gambar menjadi dokumen PDF."
        override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
            require(input.uris.isNotEmpty()) { "Pilih minimal 1 gambar" }
            emit(ToolProgress.Running(45, "Menyusun ${input.uris.size} gambar ke PDF..."))
            val dest = ctx.storage.createOutputFile("IMG2PDF", "pdf")
            PdfEngine.imagesToPdf(ctx.appContext, input.uris, dest, input.getString("pageSize", "A4"))
            emit(ToolProgress.Done(ToolResult(listOf(ToolOutputFile(dest, "application/pdf", dest.name)), summary = "${input.uris.size} halaman PDF (${ctx.storage.formatBytes(dest.length())})")))
        }.flowOn(Dispatchers.IO)
    },
    object : Tool {
        override val id = "PDF-12"
        override val group = ToolGroup.PDF
        override val engineLabel = "NAT"
        override val icon = Icons.Filled.WaterDrop
        override val inputSpec = InputSpec.SinglePdf
        override val params = listOf(
            ToolParam.TextParam("watermark", "Teks Watermark", "Watermark Text", "RAHASIA • HARIMU", "Teks cap...", "Stamp text...")
        )
        override fun title(isEnglish: Boolean) = if (isEnglish) "Add PDF Watermark" else "Watermark PDF"
        override fun description(isEnglish: Boolean) = if (isEnglish) "Stamp diagonal watermark text on every PDF page." else "Bubuhkan cap watermark diagonal di setiap halaman PDF."
        override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
            val uri = input.uris.firstOrNull() ?: error("Pilih 1 file PDF")
            emit(ToolProgress.Running(50, "Menambahkan watermark PDF..."))
            val dest = ctx.storage.createOutputFile("PDF_WM", "pdf")
            val (_, pages) = PdfEngine.transformPdfPages(
                ctx.appContext, ctx.storage, uri, dest,
                watermarkText = input.getString("watermark", "HARIMU")
            )
            emit(ToolProgress.Done(ToolResult(listOf(ToolOutputFile(dest, "application/pdf", dest.name)), summary = "Watermark ditambahkan ke $pages halaman")))
        }.flowOn(Dispatchers.IO)
    },
    object : Tool {
        override val id = "PDF-18"
        override val group = ToolGroup.PDF
        override val engineLabel = "NAT"
        override val icon = Icons.Filled.FormatListNumbered
        override val inputSpec = InputSpec.SinglePdf
        override val params = emptyList<ToolParam>()
        override fun title(isEnglish: Boolean) = if (isEnglish) "PDF Page Numbering" else "Penomoran Halaman PDF"
        override fun description(isEnglish: Boolean) = if (isEnglish) "Add X / Y page numbers to the footer of each page." else "Tambahkan nomor halaman (X / Y) di bagian bawah dokumen."
        override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
            val uri = input.uris.firstOrNull() ?: error("Pilih 1 file PDF")
            emit(ToolProgress.Running(50, "Memberi nomor halaman..."))
            val dest = ctx.storage.createOutputFile("PDF_NUM", "pdf")
            val (_, pages) = PdfEngine.transformPdfPages(ctx.appContext, ctx.storage, uri, dest, addPageNumbers = true)
            emit(ToolProgress.Done(ToolResult(listOf(ToolOutputFile(dest, "application/pdf", dest.name)), summary = "Nomor halaman ditambahkan pada $pages halaman")))
        }.flowOn(Dispatchers.IO)
    },
    object : Tool {
        override val id = "PDF-20"
        override val group = ToolGroup.PDF
        override val engineLabel = "NAT"
        override val icon = Icons.Filled.Compare
        override val inputSpec = InputSpec.MultiplePdfs
        override val params = emptyList<ToolParam>()
        override fun title(isEnglish: Boolean) = if (isEnglish) "Compare Two PDFs" else "Bandingkan 2 Dokumen PDF"
        override fun description(isEnglish: Boolean) = if (isEnglish) "Highlight pixel differences between page 1 of two PDFs." else "Sorot perbedaan visual halaman pertama antara 2 file PDF."
        override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
            require(input.uris.size >= 2) { "Pilih 2 file PDF untuk dibandingkan" }
            emit(ToolProgress.Running(50, "Membandingkan halaman PDF..."))
            val dest = ctx.storage.createOutputFile("PDF_DIFF", "png")
            val (_, diffPct) = PdfEngine.compareFirstPagesVisual(ctx.appContext, ctx.storage, input.uris[0], input.uris[1], dest)
            emit(ToolProgress.Done(ToolResult(listOf(ToolOutputFile(dest, "image/png", dest.name)), summary = "Perbedaan visual: ${String.format("%.2f", diffPct)}% piksel")))
        }.flowOn(Dispatchers.IO)
    }
)
