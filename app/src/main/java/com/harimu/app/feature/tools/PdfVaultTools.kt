package com.harimu.app.feature.tools

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MergeType
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Reorder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Splitscreen
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.ui.graphics.vector.ImageVector
import com.harimu.app.core.pdf.OfficeConvertEngine
import com.harimu.app.core.pdf.PdfOcrEngine
import com.harimu.app.core.pdf.PdfRenderUtils
import com.harimu.app.core.pdf.PdfVaultEngine
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
import java.io.File

/**
 * PDF Vault (FASE 12) — 21 tool katalog dengan engine PDFBox-Android + Tesseract4Android.
 *
 * Setiap ringkasan hasil menyebut batas nyata operasi (rasterisasi, watermark objek, OCR
 * on-device, konversi Office "Dasar") supaya status di katalog bisa diaudit.
 */
private typealias ProgressSink = suspend (Int, String) -> Unit

private class PdfVaultTool(
    override val id: String,
    override val icon: ImageVector,
    override val inputSpec: InputSpec,
    override val params: List<ToolParam>,
    private val titleId: String,
    private val titleEn: String,
    private val descId: String,
    private val descEn: String,
    override val engineLabel: String = "LIB",
    override val requiresNetwork: Boolean = false,
    private val block: suspend (ToolContext, ToolInput, ProgressSink) -> ToolResult
) : Tool {

    override val group = ToolGroup.PDF

    override fun title(isEnglish: Boolean): String = if (isEnglish) titleEn else titleId

    override fun description(isEnglish: Boolean): String = if (isEnglish) descEn else descId

    override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
        emit(ToolProgress.Running(5, "Menyiapkan berkas..."))
        val result = block(ctx, input) { percent, message ->
            emit(ToolProgress.Running(percent.coerceIn(1, 99), message))
        }
        emit(ToolProgress.Done(result))
    }.flowOn(Dispatchers.IO)
}

private fun pagesParam(default: String = "") = ToolParam.TextParam(
    "pages", "Halaman (mis. 1-3,5)", "Pages (e.g. 1-3,5)", default
)

private fun slider(key: String, labelId: String, labelEn: String, min: Float, max: Float, default: Float, unit: String = "") =
    ToolParam.SliderParam(key, labelId, labelEn, min, max, default, 1f, unit)

private fun pdfOf(input: ToolInput): android.net.Uri =
    input.uris.firstOrNull() ?: error("Pilih satu berkas PDF terlebih dahulu")

private fun fileSummary(ctx: ToolContext, file: File): String =
    "${file.name} • ${ctx.storage.formatBytes(file.length())}"

private val LANGUAGE_LABELS_ID = PdfOcrEngine.LANGUAGES.map { it.labelId }

private suspend fun ensureOcrLanguages(
    ctx: ToolContext,
    labels: List<String>,
    isEnglish: Boolean,
    progress: ProgressSink
): List<String> {
    val codes = labels.map { PdfOcrEngine.codeFromLabel(it) }.distinct()
    val missing = PdfOcrEngine.missing(ctx.appContext, codes)
    if (missing.isNotEmpty()) {
        progress(20, if (isEnglish) "Downloading OCR data: ${missing.joinToString(", ")}" else "Mengunduh data OCR: ${missing.joinToString(", ")}")
        PdfOcrEngine.ensureTrainedData(ctx.appContext, codes) { pct, msg -> progress(20 + pct / 4, msg) }
        if (isEnglish) progress(45, "OCR data ready")
    }
    return codes
}

fun pdfVaultTools(): List<Tool> = listOf(
    // ---------------------------------------------------------------- dasar
    PdfVaultTool(
        id = "PDF-01",
        icon = Icons.AutoMirrored.Filled.MergeType,
        inputSpec = InputSpec.MultiplePdfs,
        params = emptyList(),
        titleId = "Gabung PDF",
        titleEn = "Merge PDF",
        descId = "Gabungkan beberapa PDF menjadi satu berkas utuh (objek asli, bukan raster).",
        descEn = "Combine multiple PDFs into one file, keeping original page objects.",
        block = { ctx, input, progress ->
            require(input.uris.size >= 2) { "Pilih minimal 2 berkas PDF" }
            progress(30, "Menggabungkan ${input.uris.size} dokumen...")
            val dest = ctx.storage.createOutputFile("PDF_MERGE", "pdf")
            val pages = PdfVaultEngine.merge(ctx.appContext, ctx.storage, input.uris, dest)
            ToolResult(
                outputs = listOf(ToolOutputFile(dest, "application/pdf", dest.name)),
                summary = "Digabung: $pages halaman • ${ctx.storage.formatBytes(dest.length())}"
            )
        }
    ),
    PdfVaultTool(
        id = "PDF-02",
        icon = Icons.Filled.Splitscreen,
        inputSpec = InputSpec.SinglePdf,
        params = listOf(slider("perSplit", "Halaman per Bagian", "Pages per File", 1f, 50f, 1f)),
        titleId = "Pisah PDF (.zip)",
        titleEn = "Split PDF (.zip)",
        descId = "Pecah PDF menjadi beberapa berkas per N halaman di dalam ZIP.",
        descEn = "Split a PDF into per-N-page files inside a ZIP.",
        block = { ctx, input, progress ->
            progress(30, "Memisahkan halaman...")
            val dest = ctx.storage.createOutputFile("PDF_SPLIT", "zip")
            val (_, parts) = PdfVaultEngine.split(
                ctx.appContext, ctx.storage, pdfOf(input), dest,
                input.getFloat("perSplit", 1f).toInt()
            )
            ToolResult(
                outputs = listOf(ToolOutputFile(dest, "application/zip", dest.name)),
                summary = "Dipecah menjadi $parts berkas PDF di ZIP"
            )
        }
    ),
    PdfVaultTool(
        id = "PDF-03",
        icon = Icons.AutoMirrored.Filled.RotateRight,
        inputSpec = InputSpec.SinglePdf,
        params = listOf(
            ToolParam.DropdownParam("angle", "Sudut", "Angle", listOf("90", "180", "270"), "90"),
            pagesParam()
        ),
        titleId = "Putar Halaman PDF",
        titleEn = "Rotate PDF",
        descId = "Putar halaman PDF 90/180/270 derajat. Kosongkan halaman untuk semua.",
        descEn = "Rotate PDF pages by 90/180/270 degrees. Leave pages empty for all.",
        block = { ctx, input, progress ->
            progress(40, "Memutar halaman...")
            val angle = input.getString("angle", "90").toIntOrNull() ?: 90
            val dest = ctx.storage.createOutputFile("PDF_ROTATE", "pdf")
            val total = PdfVaultEngine.pageCount(ctx.appContext, ctx.storage, pdfOf(input))
            val pages = PdfVaultEngine.parsePageRange(input.getString("pages", ""), total)
            val count = PdfVaultEngine.rotate(ctx.appContext, ctx.storage, pdfOf(input), dest, angle, pages)
            ToolResult(
                outputs = listOf(ToolOutputFile(dest, "application/pdf", dest.name)),
                summary = "$count halaman diputar ${angle}°"
            )
        }
    ),
    PdfVaultTool(
        id = "PDF-04",
        icon = Icons.Filled.Reorder,
        inputSpec = InputSpec.SinglePdf,
        params = listOf(
            ToolParam.TextParam(
                "order", "Urutan Halaman", "Page Order",
                "", "1,3,2", "1,3,2"
            )
        ),
        titleId = "Susun Ulang Halaman (drag)",
        titleEn = "Reorder Pages (drag)",
        descId = "Seret thumbnail di panel pratinjau untuk mengubah urutan, atau tulis urutan manual.",
        descEn = "Drag thumbnails in the preview panel to reorder, or type the order manually.",
        block = { ctx, input, progress ->
            val uri = pdfOf(input)
            val total = PdfVaultEngine.pageCount(ctx.appContext, ctx.storage, uri)
            val order = PdfVaultEngine.parseOrder(input.getString("order", ""), total)
            require(order.isNotEmpty()) { "Urutan halaman tidak valid. Contoh: 2,1,3" }
            progress(40, "Menyusun ulang ${order.size} halaman...")
            val dest = ctx.storage.createOutputFile("PDF_REORDER", "pdf")
            val pages = PdfVaultEngine.reorder(ctx.appContext, ctx.storage, uri, dest, order)
            ToolResult(
                outputs = listOf(ToolOutputFile(dest, "application/pdf", dest.name)),
                summary = "Urutan baru: ${order.joinToString(",")} ($pages halaman)"
            )
        }
    ),
    PdfVaultTool(
        id = "PDF-05",
        icon = Icons.Filled.ContentCut,
        inputSpec = InputSpec.SinglePdf,
        params = listOf(pagesParam("1")),
        titleId = "Ekstrak Halaman",
        titleEn = "Extract Pages",
        descId = "Ambil halaman tertentu menjadi PDF baru (mis. 1-3,7).",
        descEn = "Extract specific pages into a new PDF (e.g. 1-3,7).",
        block = { ctx, input, progress ->
            val uri = pdfOf(input)
            val total = PdfVaultEngine.pageCount(ctx.appContext, ctx.storage, uri)
            val pages = PdfVaultEngine.parsePageRange(input.getString("pages", ""), total)
            require(pages.isNotEmpty()) { "Tidak ada halaman valid. Contoh: 1-3,7" }
            progress(40, "Mengekstrak ${pages.size} halaman...")
            val dest = ctx.storage.createOutputFile("PDF_EXTRACT", "pdf")
            val count = PdfVaultEngine.extract(ctx.appContext, ctx.storage, uri, dest, pages)
            ToolResult(
                outputs = listOf(ToolOutputFile(dest, "application/pdf", dest.name)),
                summary = "$count halaman diekstrak dari $total halaman"
            )
        }
    ),
    PdfVaultTool(
        id = "PDF-06",
        icon = Icons.Filled.Description,
        inputSpec = InputSpec.SinglePdf,
        params = listOf(
            ToolParam.DropdownParam(
                "target", "Format Tujuan", "Target Format",
                listOf("Word (.docx)", "Excel (.xlsx)", "PowerPoint (.pptx)"), "Word (.docx)"
            )
        ),
        engineLabel = "LIB",
        titleId = "PDF ke Word/Excel/PowerPoint (Dasar)",
        titleEn = "PDF to Word/Excel/PowerPoint (Basic)",
        descId = "Versi Dasar on-device: teks per halaman (Word/Excel) atau gambar halaman (PowerPoint). " +
            "Tata letak, tabel bergaya, dan kolom tidak dipertahankan.",
        descEn = "Basic on-device: per-page text (Word/Excel) or page images (PowerPoint). " +
            "Layout, styled tables, and columns are not preserved.",
        block = { ctx, input, progress ->
            val uri = pdfOf(input)
            val target = input.getString("target", "Word (.docx)")
            val pages = PdfVaultEngine.pageCount(ctx.appContext, ctx.storage, uri)
            when {
                target.startsWith("PowerPoint") -> {
                    progress(25, "Merender $pages halaman untuk slide...")
                    val bitmaps = mutableListOf<android.graphics.Bitmap>()
                    PdfRenderUtils.renderPages(ctx.appContext, ctx.storage, uri, dpi = 110, maxPages = 60) { _, rendered, _ ->
                        bitmaps.add(rendered.bitmap)
                        true
                    }
                    val dest = ctx.storage.createOutputFile("PDF_TO_PPTX", "pptx")
                    val count = OfficeConvertEngine.pdfToPptx(bitmaps, dest)
                    bitmaps.forEach { it.recycle() }
                    ToolResult(
                        outputs = listOf(
                            ToolOutputFile(
                                dest,
                                "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                                dest.name
                            )
                        ),
                        summary = "Dasar: $count slide gambar (${ctx.storage.formatBytes(dest.length())}). " +
                            "Teks tidak bisa diedit; struktur OOXML minimal, belum diuji di PowerPoint asli."
                    )
                }
                else -> {
                    progress(35, "Mengekstrak teks...")
                    val raw = PdfVaultEngine.extractTextFile(ctx.appContext, ctx.storage, uri)
                    require(raw.isNotBlank()) {
                        "PDF ini tidak punya lapisan teks (kemungkinan hasil pindai). Jalankan OCR (PDF-15) lebih dahulu."
                    }
                    val pageTexts = raw.split('\u000C')
                    val isExcel = target.startsWith("Excel")
                    val dest = ctx.storage.createOutputFile(if (isExcel) "PDF_TO_XLSX" else "PDF_TO_DOCX", if (isExcel) "xlsx" else "docx")
                    val info = if (isExcel) {
                        val rows = OfficeConvertEngine.pdfToXlsx(pageTexts, dest)
                        "$rows baris teks"
                    } else {
                        val count = OfficeConvertEngine.pdfToDocx(pageTexts, dest)
                        "$count halaman teks"
                    }
                    ToolResult(
                        outputs = listOf(
                            ToolOutputFile(
                                dest,
                                if (isExcel) "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                                else "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                dest.name
                            )
                        ),
                        summary = "Dasar: $info • ${fileSummary(ctx, dest)}. Format asli tidak dipertahankan."
                    )
                }
            }
        }
    ),
    PdfVaultTool(
        id = "PDF-07",
        icon = Icons.Filled.PictureAsPdf,
        inputSpec = InputSpec.AnyFile,
        params = emptyList(),
        titleId = "Word ke PDF (Dasar)",
        titleEn = "Word to PDF (Basic)",
        descId = "Versi Dasar on-device: membaca paragraf teks dari .docx. Gambar, tabel bergaya, dan header/footer tidak ikut.",
        descEn = "Basic on-device: reads text paragraphs from .docx. Images, styled tables, and headers/footers are skipped.",
        block = { ctx, input, progress ->
            val uri = input.uris.firstOrNull() ?: error("Pilih berkas .docx")
            val name = ctx.storage.getDisplayName(uri).orEmpty()
            require(name.endsWith(".docx", true)) { "Hanya .docx (Word 2007+) yang didukung versi Dasar ini" }
            progress(35, "Membaca paragraf Word...")
            val temp = ctx.storage.copyUriToTemp(uri, "docx_in", "docx")
            try {
                val dest = ctx.storage.createOutputFile("WORD_TO_PDF", "pdf")
                val pages = OfficeConvertEngine.docxToPdf(temp, dest)
                ToolResult(
                    outputs = listOf(ToolOutputFile(dest, "application/pdf", dest.name)),
                    summary = "Dasar: $pages halaman PDF dari teks Word • ${ctx.storage.formatBytes(dest.length())}"
                )
            } finally {
                temp.delete()
            }
        }
    ),
    PdfVaultTool(
        id = "PDF-08",
        icon = Icons.Filled.Image,
        inputSpec = InputSpec.MultipleImages,
        params = listOf(
            ToolParam.DropdownParam(
                "size", "Ukuran Halaman", "Page Size",
                listOf("Sesuai Gambar", "A4", "Letter"), "Sesuai Gambar"
            )
        ),
        titleId = "Gambar ke PDF",
        titleEn = "Image to PDF",
        descId = "Susun beberapa gambar menjadi PDF. Bisa mengikuti ukuran gambar atau halaman A4/Letter.",
        descEn = "Combine images into a PDF, sized to each image or to A4/Letter pages.",
        block = { ctx, input, progress ->
            require(input.uris.isNotEmpty()) { "Pilih minimal 1 gambar" }
            progress(35, "Menyusun ${input.uris.size} gambar...")
            val sizeMode = when (input.getString("size", "Sesuai Gambar")) {
                "A4" -> "A4"
                "Letter" -> "Letter"
                else -> "Fit"
            }
            val dest = ctx.storage.createOutputFile("IMG_TO_PDF", "pdf")
            val pages = PdfVaultEngine.imagesToPdf(ctx.appContext, input.uris, dest, pageSizeMode = sizeMode)
            ToolResult(
                outputs = listOf(ToolOutputFile(dest, "application/pdf", dest.name)),
                summary = "$pages halaman PDF • ${ctx.storage.formatBytes(dest.length())}"
            )
        }
    ),
    PdfVaultTool(
        id = "PDF-09",
        icon = Icons.Filled.Compress,
        inputSpec = InputSpec.SinglePdf,
        params = listOf(
            slider("dpi", "Resolusi (DPI)", "Resolution (DPI)", 100f, 200f, 150f),
            slider("quality", "Kualitas JPEG", "JPEG Quality", 40f, 90f, 70f, "%")
        ),
        titleId = "Kompres PDF",
        titleEn = "Compress PDF",
        descId = "Kecilkan PDF dengan rasterisasi ulang halaman (text menjadi gambar). Ukuran sebelum/sesudah ditampilkan.",
        descEn = "Shrink a PDF by re-rasterizing pages (text becomes image). Before/after sizes are shown.",
        block = { ctx, input, progress ->
            progress(30, "Merasterisasi halaman...")
            val dest = ctx.storage.createOutputFile("PDF_COMPRESS", "pdf")
            val report = PdfVaultEngine.compress(
                ctx.appContext, ctx.storage, pdfOf(input), dest,
                input.getFloat("dpi", 150f).toInt(),
                input.getFloat("quality", 70f).toInt()
            )
            val savedPct = if (report.beforeBytes > 0) {
                ((report.beforeBytes - report.afterBytes) * 100.0 / report.beforeBytes).toInt()
            } else 0
            ToolResult(
                outputs = listOf(ToolOutputFile(dest, "application/pdf", dest.name)),
                summary = "${report.pages} halaman • sebelum ${ctx.storage.formatBytes(report.beforeBytes)} → " +
                    "sesudah ${ctx.storage.formatBytes(report.afterBytes)} ($savedPct% lebih kecil). " +
                    "Catatan: teks berubah menjadi gambar."
            )
        }
    ),
    // -------------------------------------------------------------- keamanan
    PdfVaultTool(
        id = "PDF-10",
        icon = Icons.Filled.Lock,
        inputSpec = InputSpec.SinglePdf,
        params = listOf(
            ToolParam.TextParam("userPwd", "Password Pengguna", "User Password", ""),
            ToolParam.TextParam("ownerPwd", "Password Pemilik", "Owner Password", ""),
            ToolParam.DropdownParam("key", "Enkripsi", "Encryption", listOf("AES-256", "AES-128", "RC4-128"), "AES-256")
        ),
        titleId = "Enkripsi PDF",
        titleEn = "Encrypt PDF",
        descId = "Lindungi PDF dengan AES-256, AES-128, atau RC4-128. Password pengguna membuka dokumen, pemilik mengatur izin.",
        descEn = "Protect a PDF with AES-256, AES-128, or RC4-128. User password opens the document; owner controls permissions.",
        block = { ctx, input, progress ->
            val userPwd = input.getString("userPwd", "")
            val ownerPwd = input.getString("ownerPwd", "")
            require(userPwd.isNotBlank() || ownerPwd.isNotBlank()) { "Isi minimal satu password" }
            val keyAlgo = input.getString("key", "AES-256")
            val keyBits = when (keyAlgo) {
                "AES-128" -> 128
                "RC4-128" -> 128
                else -> 256
            }
            val preferAes = keyAlgo != "RC4-128"
            progress(40, "Menerapkan enkripsi...")
            val dest = ctx.storage.createOutputFile("PDF_ENCRYPT", "pdf")
            val pages = PdfVaultEngine.encrypt(ctx.appContext, ctx.storage, pdfOf(input), dest, userPwd, ownerPwd, keyBits, preferAes)
            ToolResult(
                outputs = listOf(ToolOutputFile(dest, "application/pdf", dest.name)),
                summary = "$pages halaman terenkripsi $keyAlgo • ${ctx.storage.formatBytes(dest.length())}"
            )
        }
    ),
    PdfVaultTool(
        id = "PDF-11",
        icon = Icons.Filled.LockOpen,
        inputSpec = InputSpec.SinglePdf,
        params = listOf(ToolParam.TextParam("password", "Password", "Password", "")),
        titleId = "Dekripsi PDF",
        titleEn = "Decrypt PDF",
        descId = "Hapus enkripsi dari PDF yang Anda miliki password-nya.",
        descEn = "Remove encryption from a PDF you have the password for.",
        block = { ctx, input, progress ->
            progress(40, "Membuka enkripsi...")
            val dest = ctx.storage.createOutputFile("PDF_DECRYPT", "pdf")
            val pages = PdfVaultEngine.decrypt(
                ctx.appContext, ctx.storage, pdfOf(input), dest, input.getString("password", "")
            )
            ToolResult(
                outputs = listOf(ToolOutputFile(dest, "application/pdf", dest.name)),
                summary = "$pages halaman tanpa enkripsi • ${ctx.storage.formatBytes(dest.length())}"
            )
        }
    ),
    PdfVaultTool(
        id = "PDF-12",
        icon = Icons.Filled.WaterDrop,
        inputSpec = InputSpec.PdfAndImage,
        params = listOf(
            ToolParam.DropdownParam("mode", "Jenis Watermark", "Watermark Type", listOf("Teks", "Gambar"), "Teks"),
            ToolParam.TextParam("text", "Teks Watermark", "Watermark Text", "RAHASIA"),
            slider("opacity", "Opasitas", "Opacity", 5f, 100f, 25f, "%"),
            slider("angle", "Sudut", "Angle", -90f, 90f, -35f, "°"),
            slider("size", "Ukuran", "Size", 2f, 30f, 10f, "%")
        ),
        titleId = "Tambah Watermark",
        titleEn = "Add Watermark",
        descId = "Tambahkan watermark teks atau gambar (pilih gambar di kolom kedua) ke semua halaman.",
        descEn = "Add a text or image watermark (pick an image in the second field) to every page.",
        block = { ctx, input, progress ->
            val mode = input.getString("mode", "Teks")
            val dest = ctx.storage.createOutputFile("PDF_WATERMARK", "pdf")
            val pages = if (mode == "Gambar") {
                val imageUri = input.secondaryUri ?: error("Pilih gambar watermark pada kolom kedua")
                progress(45, "Menempelkan watermark gambar...")
                PdfVaultEngine.addImageWatermark(
                    ctx.appContext, ctx.storage, pdfOf(input), imageUri, dest,
                    input.getFloat("opacity", 25f).toInt(),
                    input.getFloat("size", 10f).toInt().coerceAtLeast(10)
                )
            } else {
                val text = input.getString("text", "").ifBlank { error("Teks watermark masih kosong") }
                progress(45, "Menempelkan watermark teks...")
                PdfVaultEngine.addTextWatermark(
                    ctx.appContext, ctx.storage, pdfOf(input), dest, text,
                    input.getFloat("opacity", 25f).toInt(),
                    input.getFloat("angle", -35f).toInt(),
                    input.getFloat("size", 10f).toInt()
                )
            }
            ToolResult(
                outputs = listOf(ToolOutputFile(dest, "application/pdf", dest.name)),
                summary = "Watermark $mode pada $pages halaman • ${ctx.storage.formatBytes(dest.length())}"
            )
        }
    ),
    PdfVaultTool(
        id = "PDF-13",
        icon = Icons.Filled.CleaningServices,
        inputSpec = InputSpec.SinglePdf,
        params = emptyList(),
        titleId = "Hapus Watermark (objek)",
        titleEn = "Remove Watermark (objects)",
        descId = "Menghapus watermark berbentuk anotasi/layer terpisah. Watermark yang menyatu ke konten tidak bisa dihapus — batas ini nyata.",
        descEn = "Removes watermark annotations/optional-content layers. Watermarks burned into page content cannot be removed — a real limit.",
        block = { ctx, input, progress ->
            progress(40, "Memindai anotasi dan layer...")
            val dest = ctx.storage.createOutputFile("PDF_UNWATERMARK", "pdf")
            val removed = PdfVaultEngine.removeWatermarkObjects(ctx.appContext, ctx.storage, pdfOf(input), dest)
            ToolResult(
                outputs = listOf(ToolOutputFile(dest, "application/pdf", dest.name)),
                summary = if (removed == 0) {
                    "Tidak ada anotasi/layer yang bisa dihapus. Watermark tampaknya menyatu ke aliran konten; " +
                        "gunakan Redaksi (PDF-14) untuk menutup permanen area tertentu."
                } else {
                    "$removed objek watermark (anotasi/layer) dihapus • ${ctx.storage.formatBytes(dest.length())}"
                }
            )
        }
    ),
    PdfVaultTool(
        id = "PDF-14",
        icon = Icons.Filled.VisibilityOff,
        inputSpec = InputSpec.SinglePdf,
        params = listOf(
            ToolParam.TextParam("terms", "Istilah yang Dihapus", "Terms to Redact", "", "rahasia, NIK", "secret, ID"),
            slider("dpi", "Resolusi Hasil", "Output DPI", 100f, 250f, 150f)
        ),
        titleId = "Redaksi Permanen",
        titleEn = "Permanent Redaction",
        descId = "Menghapus konten di area istilah secara permanen (halaman diratakan, teks hilang) lalu memverifikasi hasil dengan ekstraksi teks.",
        descEn = "Permanently removes content under term areas (page flattened, text gone), then verifies by extracting text from the result.",
        block = { ctx, input, progress ->
            val terms = input.getString("terms", "")
                .split(',', ';', '\n')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            require(terms.isNotEmpty()) { "Isi minimal satu istilah yang ingin dihapus" }
            progress(35, "Mencari istilah dan meratakan halaman...")
            val dest = ctx.storage.createOutputFile("PDF_REDACT", "pdf")
            val report = PdfVaultEngine.redact(
                ctx.appContext, ctx.storage, pdfOf(input), dest, terms,
                input.getFloat("dpi", 150f).toInt()
            )
            val verifyNote = when {
                report.boxes == 0 -> report.note
                report.verified -> "Verifikasi ekstraksi teks: LULUS (istilah tidak ditemukan lagi)."
                else -> "Verifikasi ekstraksi teks: GAGAL — periksa hasil sebelum dibagikan."
            }
            ToolResult(
                outputs = listOf(ToolOutputFile(dest, "application/pdf", dest.name)),
                summary = "${report.boxes} area ditutup pada ${report.pages} halaman; sisa teks ${report.remainingTextChars} karakter. " +
                    verifyNote + " ${ctx.storage.formatBytes(dest.length())}"
            )
        }
    ),
    // ------------------------------------------------------------------- OCR
    PdfVaultTool(
        id = "PDF-15",
        icon = Icons.Filled.DocumentScanner,
        inputSpec = InputSpec.SinglePdf,
        params = listOf(
            ToolParam.DropdownParam("language", "Bahasa OCR", "OCR Language", LANGUAGE_LABELS_ID, "Indonesia")
        ),
        engineLabel = "OCR",
        titleId = "Scan PDF ke Teks (OCR)",
        titleEn = "Scan PDF to Text (OCR)",
        descId = "OCR on-device untuk PDF hasil pindai (8 bahasa; data bahasa diunduh saat pertama dipakai).",
        descEn = "On-device OCR for scanned PDFs (8 languages; language data is downloaded on first use).",
        block = { ctx, input, progress ->
            val uri = pdfOf(input)
            val codes = ensureOcrLanguages(ctx, listOf(input.getString("language", "Indonesia")), ctx.isEnglish, progress)
            val total = PdfRenderUtils.pageCount(ctx.appContext, ctx.storage, uri)
            require(total > 0) { "PDF tidak punya halaman" }
            val builder = StringBuilder()
            progress(50, "OCR $total halaman...")
            PdfRenderUtils.renderPages(ctx.appContext, ctx.storage, uri, dpi = 200) { index, rendered, _ ->
                val text = PdfOcrEngine.recognize(ctx.appContext, rendered.bitmap, codes)
                rendered.bitmap.recycle()
                builder.append("=== Halaman ${index + 1} ===\n").append(text).append("\n\n")
                true
            }
            val dest = ctx.storage.createOutputFile("PDF_OCR_TEXT", "txt")
            dest.writeText(builder.toString().trim())
            ToolResult(
                outputs = listOf(ToolOutputFile(dest, "text/plain", dest.name)),
                textOutput = builder.toString().trim().take(4000),
                summary = "OCR ${codes.joinToString("+")} pada $total halaman • ${ctx.storage.formatBytes(dest.length())}"
            )
        }
    ),
    PdfVaultTool(
        id = "PDF-16",
        icon = Icons.Filled.Search,
        inputSpec = InputSpec.MultipleImages,
        params = listOf(
            ToolParam.DropdownParam(
                "language", "Bahasa OCR", "OCR Language",
                LANGUAGE_LABELS_ID + "Indonesia + Inggris", "Indonesia"
            )
        ),
        engineLabel = "OCR",
        titleId = "Gambar ke PDF Pencarian (OCR)",
        titleEn = "Image to Searchable PDF (OCR)",
        descId = "Buat PDF dari gambar dengan lapisan teks tak terlihat hasil OCR, sehingga bisa dicari dan disalin.",
        descEn = "Build a PDF from images with an invisible OCR text layer, so it is searchable and copyable.",
        block = { ctx, input, progress ->
            require(input.uris.isNotEmpty()) { "Pilih minimal 1 gambar" }
            val label = input.getString("language", "Indonesia")
            val labels = if (label.contains("+")) label.split("+").map { it.trim() } else listOf(label)
            val codes = ensureOcrLanguages(ctx, labels, ctx.isEnglish, progress)
            val bitmaps = mutableListOf<android.graphics.Bitmap>()
            val texts = mutableListOf<String>()
            input.uris.forEachIndexed { index, uri ->
                progress(50 + index * 40 / input.uris.size, "OCR gambar ${index + 1}/${input.uris.size}...")
                val bitmap = com.harimu.app.core.engines.ImageEngine.decodeBitmapSafe(ctx.appContext, uri, 2400)
                texts.add(PdfOcrEngine.recognize(ctx.appContext, bitmap, codes))
                bitmaps.add(bitmap)
            }
            val dest = ctx.storage.createOutputFile("IMG_SEARCHABLE_PDF", "pdf")
            val pages = PdfVaultEngine.bitmapsToPdf(ctx.appContext, bitmaps, texts, dest)
            bitmaps.forEach { it.recycle() }
            ToolResult(
                outputs = listOf(ToolOutputFile(dest, "application/pdf", dest.name)),
                summary = "$pages halaman dengan lapisan teks OCR (${codes.joinToString("+")}) • ${ctx.storage.formatBytes(dest.length())}"
            )
        }
    ),
    PdfVaultTool(
        id = "PDF-17",
        icon = Icons.Filled.AutoAwesome,
        inputSpec = InputSpec.SinglePdf,
        params = listOf(slider("maxPages", "Halaman Dianalisis", "Pages Analyzed", 1f, 5f, 2f)),
        engineLabel = "CL",
        requiresNetwork = true,
        titleId = "AI Document Recognition",
        titleEn = "AI Document Recognition",
        descId = "Kenali struktur dokumen (judul, bagian, tabel, daftar) lewat AI Gateway. Butuh URL gateway + kredensial di Pengaturan.",
        descEn = "Recognize document structure (title, sections, tables, lists) via the AI Gateway. Requires a gateway URL and credentials in Settings.",
        block = { ctx, input, progress ->
            val ai = ctx.ai ?: error("Klien AI tidak tersedia pada konteks ini")
            val maxPages = input.getFloat("maxPages", 2f).toInt()
            progress(20, "Merender halaman untuk analisis...")
            val images = mutableListOf<ByteArray>()
            PdfRenderUtils.renderPages(ctx.appContext, ctx.storage, pdfOf(input), dpi = 130, maxPages = maxPages) { _, rendered, _ ->
                val buffer = java.io.ByteArrayOutputStream()
                rendered.bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, buffer)
                rendered.bitmap.recycle()
                images.add(buffer.toByteArray())
                true
            }
            require(images.isNotEmpty()) { "Tidak ada halaman yang bisa dianalisis" }
            val prompt = buildString {
                append("Ekstrak struktur dokumen ini sebagai JSON ringkas: ")
                append("{\"judul\":\"...\",\"ringkasan\":\"...\",\"bagian\":[\"...\"],\"tabel\":[{\"judul\":\"...\",\"kolom\":[\"...\"]}],")
                append("\"daftar\":[\"...\"],\"entitas\":{\"tanggal\":[\"...\"],\"nominal\":[\"...\"]}}. ")
                append("Jangan mengarang isi; tulis null bila tidak ada. Balas JSON saja.")
            }
            val report = StringBuilder()
            var mock = false
            images.forEachIndexed { index, bytes ->
                progress(40 + index * 40 / images.size, "Menganalisis halaman ${index + 1}/${images.size}...")
                val result = ai.analyzeImage(bytes, "image/jpeg", prompt)
                mock = mock || result.mock
                report.append("=== Halaman ${index + 1} ===\n").append(result.text.trim()).append("\n\n")
            }
            val dest = ctx.storage.createOutputFile("PDF_AI_STRUCTURE", "txt")
            dest.writeText(report.toString().trim())
            ToolResult(
                outputs = listOf(ToolOutputFile(dest, "text/plain", dest.name)),
                textOutput = report.toString().trim().take(4000),
                summary = (if (mock) "Hasil MOCK (gateway tanpa model) " else "Analisis struktur selesai ") +
                    "• ${images.size} halaman • ${ctx.storage.formatBytes(dest.length())}"
            )
        }
    ),
    // ---------------------------------------------------------------- lainnya
    PdfVaultTool(
        id = "PDF-18",
        icon = Icons.Filled.FormatListNumbered,
        inputSpec = InputSpec.SinglePdf,
        params = listOf(
            ToolParam.TextParam("pattern", "Pola Nomor", "Number Pattern", "{n} / {total}"),
            ToolParam.DropdownParam(
                "position", "Posisi", "Position",
                listOf("Tengah Bawah", "Kanan Bawah", "Kiri Bawah", "Kanan Atas"), "Tengah Bawah"
            ),
            slider("start", "Mulai Dari", "Start At", 1f, 100f, 1f),
            slider("size", "Ukuran Font", "Font Size", 2f, 8f, 4f, "%")
        ),
        titleId = "Nomor Halaman",
        titleEn = "Page Numbering",
        descId = "Tambahkan nomor halaman dengan pola {n} dan {total}.",
        descEn = "Add page numbers using {n} and {total} placeholders.",
        block = { ctx, input, progress ->
            progress(40, "Menambahkan nomor halaman...")
            val dest = ctx.storage.createOutputFile("PDF_NUMBERS", "pdf")
            val pages = PdfVaultEngine.addPageNumbers(
                ctx.appContext, ctx.storage, pdfOf(input), dest,
                input.getString("pattern", "{n} / {total}").ifBlank { "{n}" },
                input.getFloat("start", 1f).toInt(),
                input.getString("position", "Tengah Bawah"),
                input.getFloat("size", 4f).toInt()
            )
            ToolResult(
                outputs = listOf(ToolOutputFile(dest, "application/pdf", dest.name)),
                summary = "Nomor halaman ditambahkan ke $pages halaman"
            )
        }
    ),
    PdfVaultTool(
        id = "PDF-19",
        icon = Icons.Filled.EditNote,
        inputSpec = InputSpec.SinglePdf,
        params = listOf(
            ToolParam.TextParam("title", "Judul", "Title", ""),
            ToolParam.TextParam("author", "Penulis", "Author", ""),
            ToolParam.TextParam("subject", "Subjek", "Subject", ""),
            ToolParam.TextParam("keywords", "Kata Kunci", "Keywords", "")
        ),
        titleId = "Editor Metadata",
        titleEn = "Metadata Editor",
        descId = "Lihat dan ubah Judul/Penulis/Subjek/Kata Kunci. Kolom kosong tidak diubah.",
        descEn = "View and edit Title/Author/Subject/Keywords. Blank fields are left unchanged.",
        block = { ctx, input, progress ->
            val before = PdfVaultEngine.readMetadata(ctx.appContext, ctx.storage, pdfOf(input))
            val values = mapOf(
                "title" to input.getString("title", ""),
                "author" to input.getString("author", ""),
                "subject" to input.getString("subject", ""),
                "keywords" to input.getString("keywords", "")
            ).filterValues { it.isNotBlank() }
            require(values.isNotEmpty()) { "Isi minimal satu kolom metadata" }
            progress(45, "Menulis metadata...")
            val dest = ctx.storage.createOutputFile("PDF_METADATA", "pdf")
            val pages = PdfVaultEngine.writeMetadata(ctx.appContext, ctx.storage, pdfOf(input), dest, values)
            val changed = values.keys.joinToString(", ")
            ToolResult(
                outputs = listOf(ToolOutputFile(dest, "application/pdf", dest.name)),
                summary = "Metadata ($changed) diperbarui pada $pages halaman. " +
                    "Sebelum: judul=\"${before["title"].orEmpty()}\", penulis=\"${before["author"].orEmpty()}\""
            )
        }
    ),
    PdfVaultTool(
        id = "PDF-20",
        icon = Icons.Filled.Compare,
        inputSpec = InputSpec.MultiplePdfs,
        params = emptyList(),
        engineLabel = "LIB+NAT",
        titleId = "Bandingkan Dokumen",
        titleEn = "Compare Documents",
        descId = "Bandingkan teks (baris ditambah/dihapus) dan tampilan tiap halaman (gambar diff merah).",
        descEn = "Compare text (added/removed lines) and page appearance (red diff images).",
        block = { ctx, input, progress ->
            require(input.uris.size >= 2) { "Pilih 2 berkas PDF" }
            progress(30, "Membandingkan teks dan halaman...")
            val zip = ctx.storage.createOutputFile("PDF_DIFF_IMG", "zip")
            val report = PdfVaultEngine.compare(
                ctx.appContext, ctx.storage, input.uris[0], input.uris[1], zip
            )
            val textFile = ctx.storage.createOutputFile("PDF_DIFF_REPORT", "txt")
            val body = buildString {
                appendLine("Perbandingan dokumen")
                appendLine("A: ${ctx.storage.getDisplayName(input.uris[0]).orEmpty()} (${report.pagesA} halaman)")
                appendLine("B: ${ctx.storage.getDisplayName(input.uris[1]).orEmpty()} (${report.pagesB} halaman)")
                appendLine("Halaman identik: ${report.identicalPages}")
                appendLine("Halaman berbeda: ${report.differentPages}")
                appendLine("Baris hanya di A: ${report.removedLines}")
                appendLine("Baris hanya di B: ${report.addedLines}")
                appendLine()
                appendLine("Catatan: diff teks berbasis baris unik (bukan LCS), diff visual memakai grid 8 px.")
            }
            textFile.writeText(body)
            ToolResult(
                outputs = listOf(
                    ToolOutputFile(textFile, "text/plain", textFile.name),
                    ToolOutputFile(zip, "application/zip", zip.name)
                ),
                textOutput = body,
                summary = "${report.differentPages} halaman berbeda, ${report.identicalPages} identik • " +
                    "${report.addedLines}+ / ${report.removedLines}- baris"
            )
        }
    ),
    PdfVaultTool(
        id = "PDF-21",
        icon = Icons.Filled.TextFields,
        inputSpec = InputSpec.SinglePdf,
        params = listOf(
            ToolParam.TextParam(
                "values",
                "Nilai Form (nama=nilai, pisah |)",
                "Form Values (name=value, separated by |)",
                "", "nama=Andi|tanggal=24/11/2026", "name=Alex|date=2026-11-24"
            )
        ),
        titleId = "Isi Form PDF (AcroForm)",
        titleEn = "PDF Form Filler (AcroForm)",
        descId = "Isi field AcroForm. Biarkan kosong untuk melihat daftar field yang tersedia.",
        descEn = "Fill AcroForm fields. Leave empty to list available fields.",
        block = { ctx, input, progress ->
            val uri = pdfOf(input)
            val fields = PdfVaultEngine.listFormFields(ctx.appContext, ctx.storage, uri)
            val raw = input.getString("values", "")
            val values = raw.split('|', '\n')
                .mapNotNull { line ->
                    val idx = line.indexOf('=')
                    if (idx <= 0) null else line.substring(0, idx).trim() to line.substring(idx + 1).trim()
                }
                .toMap()
            if (values.isEmpty()) {
                val listing = if (fields.isEmpty()) {
                    "PDF ini tidak memiliki form AcroForm."
                } else {
                    fields.joinToString("\n") { "${it.first} = ${it.second}" }
                }
                ToolResult(
                    textOutput = listing,
                    summary = if (fields.isEmpty()) "Tidak ada field AcroForm" else "${fields.size} field: " +
                        fields.take(4).joinToString(", ") { it.first }
                )
            } else {
                progress(45, "Mengisi ${values.size} field...")
                val dest = ctx.storage.createOutputFile("PDF_FORM", "pdf")
                val filled = PdfVaultEngine.fillForm(ctx.appContext, ctx.storage, uri, dest, values)
                val unknown = values.keys - fields.map { it.first }.toSet()
                ToolResult(
                    outputs = listOf(ToolOutputFile(dest, "application/pdf", dest.name)),
                    summary = "$filled dari ${values.size} field terisi" +
                        (if (unknown.isEmpty()) "" else " • tidak ditemukan: ${unknown.joinToString(", ")}") +
                        " • ${ctx.storage.formatBytes(dest.length())}"
                )
            }
        }
    )
)

/** Ringkasan singkat jumlah tool dan pemeriksaan id unik untuk tes katalog. */
fun pdfVaultToolIds(): List<String> = pdfVaultTools().map { it.id }
