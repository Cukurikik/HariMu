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
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.util.zip.ZipFile

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
        emit(ToolProgress.Running(5, if (ctx.isEnglish) "Preparing file..." else "Menyiapkan berkas..."))
        val result = try {
            block(ctx, input) { percent, message ->
                emit(ToolProgress.Running(percent.coerceIn(1, 99), message))
            }
        } catch (e: InvalidPasswordException) {
            throw IllegalStateException(
                if (ctx.isEnglish) {
                    "This PDF is encrypted or the password is incorrect. Unlock it in PDF Document Studio or decrypt first with PDF-11."
                } else {
                    "PDF ini terenkripsi atau kata sandi salah. Buka di PDF Document Studio atau dekripsi lebih dahulu dengan PDF-11."
                },
                e
            )
        } catch (e: SecurityException) {
            throw IllegalStateException(
                if (ctx.isEnglish) {
                    "This PDF is password-protected. Unlock it in PDF Document Studio or decrypt first with PDF-11."
                } else {
                    "PDF ini dilindungi kata sandi. Buka di PDF Document Studio atau dekripsi lebih dahulu dengan PDF-11."
                },
                e
            )
        }
        emit(ToolProgress.Done(result))
    }.flowOn(Dispatchers.IO)
}

private fun pagesParam(default: String = "") = ToolParam.TextParam(
    "pages", "Halaman (mis. 1-3,5)", "Pages (e.g. 1-3,5)", default
)

private fun slider(key: String, labelId: String, labelEn: String, min: Float, max: Float, default: Float, unit: String = "") =
    ToolParam.SliderParam(key, labelId, labelEn, min, max, default, 1f, unit)

private fun pdfOf(input: ToolInput, isEnglish: Boolean): android.net.Uri =
    input.uris.firstOrNull() ?: error(if (isEnglish) "Select a PDF file first" else "Pilih satu berkas PDF terlebih dahulu")

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
        PdfOcrEngine.ensureTrainedData(ctx.appContext, codes, isEnglish = isEnglish) { pct, msg -> progress(20 + pct / 4, msg) }
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
            require(input.uris.size >= 2) {
                if (ctx.isEnglish) "Select at least 2 PDF files" else "Pilih minimal 2 berkas PDF"
            }
            progress(
                30,
                if (ctx.isEnglish) "Merging ${input.uris.size} documents..." else "Menggabungkan ${input.uris.size} dokumen..."
            )
            val dest = ctx.storage.createOutputFile("PDF_MERGE", "pdf")
            val pages = PdfVaultEngine.merge(ctx.appContext, ctx.storage, input.uris, dest)
            ToolResult(
                outputs = listOf(ToolOutputFile(dest, "application/pdf", dest.name)),
                summary = if (ctx.isEnglish) {
                    "Merged: $pages pages • ${ctx.storage.formatBytes(dest.length())}"
                } else {
                    "Digabung: $pages halaman • ${ctx.storage.formatBytes(dest.length())}"
                }
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
            progress(30, if (ctx.isEnglish) "Splitting pages..." else "Memisahkan halaman...")
            val dest = ctx.storage.createOutputFile("PDF_SPLIT", "zip")
            val (_, parts) = PdfVaultEngine.split(
                ctx.appContext, ctx.storage, pdfOf(input, ctx.isEnglish), dest,
                input.getFloat("perSplit", 1f).toInt()
            )
            ToolResult(
                outputs = listOf(ToolOutputFile(dest, "application/zip", dest.name)),
                summary = if (ctx.isEnglish) {
                    "Split into $parts PDF files inside ZIP"
                } else {
                    "Dipecah menjadi $parts berkas PDF di ZIP"
                }
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
            progress(40, if (ctx.isEnglish) "Rotating pages..." else "Memutar halaman...")
            val angle = input.getString("angle", "90").toIntOrNull() ?: 90
            val uri = pdfOf(input, ctx.isEnglish)
            val dest = ctx.storage.createOutputFile("PDF_ROTATE", "pdf")
            val total = PdfVaultEngine.pageCount(ctx.appContext, ctx.storage, uri)
            val pages = PdfVaultEngine.parsePageRange(input.getString("pages", ""), total)
            val count = PdfVaultEngine.rotate(ctx.appContext, ctx.storage, uri, dest, angle, pages)
            ToolResult(
                outputs = listOf(ToolOutputFile(dest, "application/pdf", dest.name)),
                summary = if (ctx.isEnglish) {
                    "$count pages rotated ${angle}°"
                } else {
                    "$count halaman diputar ${angle}°"
                }
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
        titleId = "Susun Ulang Halaman",
        titleEn = "Reorder Pages",
        descId = "Atur ulang urutan halaman dengan tombol geser kiri/kanan di pratinjau atau tulis urutan manual.",
        descEn = "Reorder pages using the left/right move buttons in the preview strip or type the order manually.",
        block = { ctx, input, progress ->
            val uri = pdfOf(input, ctx.isEnglish)
            val total = PdfVaultEngine.pageCount(ctx.appContext, ctx.storage, uri)
            val order = PdfVaultEngine.parseOrder(input.getString("order", ""), total)
            require(order.isNotEmpty()) {
                if (ctx.isEnglish) "Invalid page order. Example: 2,1,3" else "Urutan halaman tidak valid. Contoh: 2,1,3"
            }
            progress(
                40,
                if (ctx.isEnglish) "Reordering ${order.size} pages..." else "Menyusun ulang ${order.size} halaman..."
            )
            val dest = ctx.storage.createOutputFile("PDF_REORDER", "pdf")
            val pages = PdfVaultEngine.reorder(ctx.appContext, ctx.storage, uri, dest, order)
            ToolResult(
                outputs = listOf(ToolOutputFile(dest, "application/pdf", dest.name)),
                summary = if (ctx.isEnglish) {
                    "New order: ${order.joinToString(",")} ($pages pages)"
                } else {
                    "Urutan baru: ${order.joinToString(",")} ($pages halaman)"
                }
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
            val uri = pdfOf(input, ctx.isEnglish)
            val total = PdfVaultEngine.pageCount(ctx.appContext, ctx.storage, uri)
            val pages = PdfVaultEngine.parsePageRange(input.getString("pages", ""), total)
            require(pages.isNotEmpty()) {
                if (ctx.isEnglish) "No valid pages specified. Example: 1-3,7" else "Tidak ada halaman valid. Contoh: 1-3,7"
            }
            progress(
                40,
                if (ctx.isEnglish) "Extracting ${pages.size} pages..." else "Mengekstrak ${pages.size} halaman..."
            )
            val dest = ctx.storage.createOutputFile("PDF_EXTRACT", "pdf")
            val count = PdfVaultEngine.extract(ctx.appContext, ctx.storage, uri, dest, pages)
            ToolResult(
                outputs = listOf(ToolOutputFile(dest, "application/pdf", dest.name)),
                summary = if (ctx.isEnglish) {
                    "$count pages extracted out of $total pages"
                } else {
                    "$count halaman diekstrak dari $total halaman"
                }
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
            val uri = pdfOf(input, ctx.isEnglish)
            val target = input.getString("target", "Word (.docx)")
            val pages = PdfVaultEngine.pageCount(ctx.appContext, ctx.storage, uri)
            when {
                target.startsWith("PowerPoint") -> {
                    progress(
                        25,
                        if (ctx.isEnglish) "Rendering $pages pages for slides..." else "Merender $pages halaman untuk slide..."
                    )
                    val bitmaps = mutableListOf<android.graphics.Bitmap>()
                    try {
                        PdfRenderUtils.renderPages(ctx.appContext, ctx.storage, uri, dpi = 110, maxPages = 60) { _, rendered, _ ->
                            bitmaps.add(rendered.bitmap)
                            true
                        }
                        val dest = ctx.storage.createOutputFile("PDF_TO_PPTX", "pptx")
                        val count = OfficeConvertEngine.pdfToPptx(bitmaps, dest)
                        ToolResult(
                            outputs = listOf(
                                ToolOutputFile(
                                    dest,
                                    "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                                    dest.name
                                )
                            ),
                            summary = if (ctx.isEnglish) {
                                "Basic: $count image slides (${ctx.storage.formatBytes(dest.length())}). " +
                                    "Text is not editable; minimal OOXML output."
                            } else {
                                "Dasar: $count slide gambar (${ctx.storage.formatBytes(dest.length())}). " +
                                    "Teks tidak bisa diedit; struktur OOXML minimal."
                            }
                        )
                    } finally {
                        bitmaps.forEach { if (!it.isRecycled) it.recycle() }
                    }
                }
                else -> {
                    progress(35, if (ctx.isEnglish) "Extracting text..." else "Mengekstrak teks...")
                    val raw = PdfVaultEngine.extractTextFile(ctx.appContext, ctx.storage, uri)
                    require(raw.isNotBlank()) {
                        if (ctx.isEnglish) {
                            "This PDF has no text layer (likely scanned). Run OCR (PDF-15) first."
                        } else {
                            "PDF ini tidak punya lapisan teks (kemungkinan hasil pindai). Jalankan OCR (PDF-15) lebih dahulu."
                        }
                    }
                    val pageTexts = raw.split('\u000C')
                    val isExcel = target.startsWith("Excel")
                    val dest = ctx.storage.createOutputFile(if (isExcel) "PDF_TO_XLSX" else "PDF_TO_DOCX", if (isExcel) "xlsx" else "docx")
                    val info = if (isExcel) {
                        val rows = OfficeConvertEngine.pdfToXlsx(pageTexts, dest)
                        if (ctx.isEnglish) "$rows text rows" else "$rows baris teks"
                    } else {
                        val count = OfficeConvertEngine.pdfToDocx(pageTexts, dest)
                        if (ctx.isEnglish) "$count text pages" else "$count halaman teks"
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
                        summary = if (ctx.isEnglish) {
                            "Basic: $info • ${fileSummary(ctx, dest)}. Original layout is not preserved."
                        } else {
                            "Dasar: $info • ${fileSummary(ctx, dest)}. Format asli tidak dipertahankan."
                        }
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
            val uri = input.uris.firstOrNull() ?: error(
                if (ctx.isEnglish) "Select a .docx file" else "Pilih berkas .docx"
            )
            progress(20, if (ctx.isEnglish) "Validating Word (.docx) container..." else "Memeriksa wadah Word (.docx)...")
            val temp = ctx.storage.copyUriToTemp(uri, "docx_in", "docx")
            try {
                val isValidDocx = runCatching {
                    ZipFile(temp).use { zip -> zip.getEntry("word/document.xml") != null }
                }.getOrDefault(false)
                require(isValidDocx) {
                    if (ctx.isEnglish) {
                        "Only .docx (Word 2007+) files are supported in this Basic converter"
                    } else {
                        "Hanya .docx (Word 2007+) yang didukung versi Dasar ini"
                    }
                }
                progress(40, if (ctx.isEnglish) "Reading Word paragraphs..." else "Membaca paragraf Word...")
                val dest = ctx.storage.createOutputFile("WORD_TO_PDF", "pdf")
                val pages = OfficeConvertEngine.docxToPdf(temp, dest)
                ToolResult(
                    outputs = listOf(ToolOutputFile(dest, "application/pdf", dest.name)),
                    summary = if (ctx.isEnglish) {
                        "Basic: $pages PDF pages from Word text • ${ctx.storage.formatBytes(dest.length())}"
                    } else {
                        "Dasar: $pages halaman PDF dari teks Word • ${ctx.storage.formatBytes(dest.length())}"
                    }
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
            require(input.uris.isNotEmpty()) {
                if (ctx.isEnglish) "Select at least 1 image" else "Pilih minimal 1 gambar"
            }
            progress(
                35,
                if (ctx.isEnglish) "Building PDF from ${input.uris.size} images..." else "Menyusun ${input.uris.size} gambar..."
            )
            val sizeMode = when (input.getString("size", "Sesuai Gambar")) {
                "A4" -> "A4"
                "Letter" -> "Letter"
                else -> "Fit"
            }
            val dest = ctx.storage.createOutputFile("IMG_TO_PDF", "pdf")
            val pages = PdfVaultEngine.imagesToPdf(ctx.appContext, input.uris, dest, pageSizeMode = sizeMode)
            ToolResult(
                outputs = listOf(ToolOutputFile(dest, "application/pdf", dest.name)),
                summary = if (ctx.isEnglish) {
                    "$pages PDF pages • ${ctx.storage.formatBytes(dest.length())}"
                } else {
                    "$pages halaman PDF • ${ctx.storage.formatBytes(dest.length())}"
                }
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
        descId = "Kecilkan PDF dengan rasterisasi ulang halaman (teks menjadi gambar). Ukuran sebelum/sesudah ditampilkan.",
        descEn = "Shrink a PDF by re-rasterizing pages (text becomes image). Before/after sizes are shown.",
        block = { ctx, input, progress ->
            progress(30, if (ctx.isEnglish) "Rasterizing pages..." else "Merasterisasi halaman...")
            val dest = ctx.storage.createOutputFile("PDF_COMPRESS", "pdf")
            val report = PdfVaultEngine.compress(
                ctx.appContext, ctx.storage, pdfOf(input, ctx.isEnglish), dest,
                input.getFloat("dpi", 150f).toInt(),
                input.getFloat("quality", 70f).toInt()
            )
            val savedPct = if (report.beforeBytes > 0) {
                ((report.beforeBytes - report.afterBytes) * 100.0 / report.beforeBytes).toInt()
            } else 0
            ToolResult(
                outputs = listOf(ToolOutputFile(dest, "application/pdf", dest.name)),
                summary = if (ctx.isEnglish) {
                    "${report.pages} pages • before ${ctx.storage.formatBytes(report.beforeBytes)} → " +
                        "after ${ctx.storage.formatBytes(report.afterBytes)} ($savedPct% smaller). " +
                        "Note: page text is rasterized to images."
                } else {
                    "${report.pages} halaman • sebelum ${ctx.storage.formatBytes(report.beforeBytes)} → " +
                        "sesudah ${ctx.storage.formatBytes(report.afterBytes)} ($savedPct% lebih kecil). " +
                        "Catatan: teks berubah menjadi gambar."
                }
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
            require(userPwd.isNotBlank() || ownerPwd.isNotBlank()) {
                if (ctx.isEnglish) "Enter at least one password" else "Isi minimal satu password"
            }
            val keyAlgo = input.getString("key", "AES-256")
            val keyBits = when (keyAlgo) {
                "AES-128" -> 128
                "RC4-128" -> 128
                else -> 256
            }
            val preferAes = keyAlgo != "RC4-128"
            progress(40, if (ctx.isEnglish) "Applying encryption..." else "Menerapkan enkripsi...")
            val dest = ctx.storage.createOutputFile("PDF_ENCRYPT", "pdf")
            val pages = PdfVaultEngine.encrypt(ctx.appContext, ctx.storage, pdfOf(input, ctx.isEnglish), dest, userPwd, ownerPwd, keyBits, preferAes)
            ToolResult(
                outputs = listOf(ToolOutputFile(dest, "application/pdf", dest.name)),
                summary = if (ctx.isEnglish) {
                    "$pages pages encrypted with $keyAlgo • ${ctx.storage.formatBytes(dest.length())}"
                } else {
                    "$pages halaman terenkripsi $keyAlgo • ${ctx.storage.formatBytes(dest.length())}"
                }
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
            progress(40, if (ctx.isEnglish) "Removing encryption..." else "Membuka enkripsi...")
            val dest = ctx.storage.createOutputFile("PDF_DECRYPT", "pdf")
            val pages = PdfVaultEngine.decrypt(
                ctx.appContext, ctx.storage, pdfOf(input, ctx.isEnglish), dest, input.getString("password", "")
            )
            ToolResult(
                outputs = listOf(ToolOutputFile(dest, "application/pdf", dest.name)),
                summary = if (ctx.isEnglish) {
                    "$pages pages decrypted • ${ctx.storage.formatBytes(dest.length())}"
                } else {
                    "$pages halaman tanpa enkripsi • ${ctx.storage.formatBytes(dest.length())}"
                }
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
                val imageUri = input.secondaryUri ?: error(
                    if (ctx.isEnglish) "Select a watermark image in the second field" else "Pilih gambar watermark pada kolom kedua"
                )
                progress(45, if (ctx.isEnglish) "Applying image watermark..." else "Menempelkan watermark gambar...")
                PdfVaultEngine.addImageWatermark(
                    ctx.appContext, ctx.storage, pdfOf(input, ctx.isEnglish), imageUri, dest,
                    input.getFloat("opacity", 25f).toInt(),
                    input.getFloat("size", 10f).toInt().coerceAtLeast(10)
                )
            } else {
                val text = input.getString("text", "").ifBlank {
                    error(if (ctx.isEnglish) "Watermark text is blank" else "Teks watermark masih kosong")
                }
                progress(45, if (ctx.isEnglish) "Applying text watermark..." else "Menempelkan watermark teks...")
                PdfVaultEngine.addTextWatermark(
                    ctx.appContext, ctx.storage, pdfOf(input, ctx.isEnglish), dest, text,
                    input.getFloat("opacity", 25f).toInt(),
                    input.getFloat("angle", -35f).toInt(),
                    input.getFloat("size", 10f).toInt()
                )
            }
            val modeLabel = if (ctx.isEnglish) {
                if (mode == "Gambar") "image" else "text"
            } else mode
            ToolResult(
                outputs = listOf(ToolOutputFile(dest, "application/pdf", dest.name)),
                summary = if (ctx.isEnglish) {
                    "Applied $modeLabel watermark across $pages pages • ${ctx.storage.formatBytes(dest.length())}"
                } else {
                    "Watermark $modeLabel pada $pages halaman • ${ctx.storage.formatBytes(dest.length())}"
                }
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
            progress(40, if (ctx.isEnglish) "Scanning annotations and layers..." else "Memindai anotasi dan layer...")
            val dest = ctx.storage.createOutputFile("PDF_UNWATERMARK", "pdf")
            val removed = PdfVaultEngine.removeWatermarkObjects(ctx.appContext, ctx.storage, pdfOf(input, ctx.isEnglish), dest)
            ToolResult(
                outputs = listOf(ToolOutputFile(dest, "application/pdf", dest.name)),
                summary = if (removed == 0) {
                    if (ctx.isEnglish) {
                        "No removable annotation/layer objects found. Watermarks burned into page content require Redaction (PDF-14)."
                    } else {
                        "Tidak ada anotasi/layer yang bisa dihapus. Watermark tampaknya menyatu ke aliran konten; " +
                            "gunakan Redaksi (PDF-14) untuk menutup permanen area tertentu."
                    }
                } else {
                    if (ctx.isEnglish) {
                        "$removed watermark objects (annotations/layers) removed • ${ctx.storage.formatBytes(dest.length())}"
                    } else {
                        "$removed objek watermark (anotasi/layer) dihapus • ${ctx.storage.formatBytes(dest.length())}"
                    }
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
            require(terms.isNotEmpty()) {
                if (ctx.isEnglish) "Enter at least one term to redact" else "Isi minimal satu istilah yang ingin dihapus"
            }
            progress(
                35,
                if (ctx.isEnglish) "Locating terms and flattening pages..." else "Mencari istilah dan meratakan halaman..."
            )
            val dest = ctx.storage.createOutputFile("PDF_REDACT", "pdf")
            val report = PdfVaultEngine.redact(
                ctx.appContext, ctx.storage, pdfOf(input, ctx.isEnglish), dest, terms,
                input.getFloat("dpi", 150f).toInt()
            )
            val verifyNote = if (ctx.isEnglish) {
                when {
                    report.boxes == 0 -> "No matching terms found; pages were flattened."
                    report.verified -> "Text verification: PASSED (terms no longer extracted)."
                    else -> "Text verification: FAILED — inspect output before sharing."
                }
            } else {
                when {
                    report.boxes == 0 -> report.note
                    report.verified -> "Verifikasi ekstraksi teks: LULUS (istilah tidak ditemukan lagi)."
                    else -> "Verifikasi ekstraksi teks: GAGAL — periksa hasil sebelum dibagikan."
                }
            }
            ToolResult(
                outputs = listOf(ToolOutputFile(dest, "application/pdf", dest.name)),
                summary = if (ctx.isEnglish) {
                    "${report.boxes} regions redacted across ${report.pages} pages; ${report.remainingTextChars} extracted chars. " +
                        verifyNote + " ${ctx.storage.formatBytes(dest.length())}"
                } else {
                    "${report.boxes} area ditutup pada ${report.pages} halaman; sisa teks ${report.remainingTextChars} karakter. " +
                        verifyNote + " ${ctx.storage.formatBytes(dest.length())}"
                }
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
            val uri = pdfOf(input, ctx.isEnglish)
            val codes = ensureOcrLanguages(ctx, listOf(input.getString("language", "Indonesia")), ctx.isEnglish, progress)
            val total = PdfRenderUtils.pageCount(ctx.appContext, ctx.storage, uri)
            require(total > 0) { if (ctx.isEnglish) "PDF has no pages" else "PDF tidak punya halaman" }
            val builder = StringBuilder()
            progress(50, if (ctx.isEnglish) "Running OCR on $total pages..." else "OCR $total halaman...")
            PdfRenderUtils.renderPages(ctx.appContext, ctx.storage, uri, dpi = 200) { index, rendered, _ ->
                val text = try {
                    PdfOcrEngine.recognize(ctx.appContext, rendered.bitmap, codes, isEnglish = ctx.isEnglish)
                } finally {
                    if (!rendered.bitmap.isRecycled) rendered.bitmap.recycle()
                }
                val pageHeader = if (ctx.isEnglish) "Page ${index + 1}" else "Halaman ${index + 1}"
                builder.append("=== $pageHeader ===\n").append(text).append("\n\n")
                true
            }
            val dest = ctx.storage.createOutputFile("PDF_OCR_TEXT", "txt")
            dest.writeText(builder.toString().trim())
            ToolResult(
                outputs = listOf(ToolOutputFile(dest, "text/plain", dest.name)),
                textOutput = builder.toString().trim().take(4000),
                summary = if (ctx.isEnglish) {
                    "OCR ${codes.joinToString("+")} on $total pages • ${ctx.storage.formatBytes(dest.length())}"
                } else {
                    "OCR ${codes.joinToString("+")} pada $total halaman • ${ctx.storage.formatBytes(dest.length())}"
                }
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
            require(input.uris.isNotEmpty()) {
                if (ctx.isEnglish) "Select at least 1 image" else "Pilih minimal 1 gambar"
            }
            val label = input.getString("language", "Indonesia")
            val labels = if (label.contains("+")) label.split("+").map { it.trim() } else listOf(label)
            val codes = ensureOcrLanguages(ctx, labels, ctx.isEnglish, progress)
            val bitmaps = mutableListOf<android.graphics.Bitmap>()
            val texts = mutableListOf<String>()
            try {
                input.uris.forEachIndexed { index, uri ->
                    progress(
                        50 + index * 40 / input.uris.size,
                        if (ctx.isEnglish) "OCR image ${index + 1}/${input.uris.size}..." else "OCR gambar ${index + 1}/${input.uris.size}..."
                    )
                    val bitmap = com.harimu.app.core.engines.ImageEngine.decodeBitmapSafe(ctx.appContext, uri, 2400)
                    bitmaps.add(bitmap)
                    texts.add(PdfOcrEngine.recognize(ctx.appContext, bitmap, codes, isEnglish = ctx.isEnglish))
                }
                val dest = ctx.storage.createOutputFile("IMG_SEARCHABLE_PDF", "pdf")
                val pages = PdfVaultEngine.bitmapsToPdf(ctx.appContext, bitmaps, texts, dest)
                ToolResult(
                    outputs = listOf(ToolOutputFile(dest, "application/pdf", dest.name)),
                    summary = if (ctx.isEnglish) {
                        "$pages pages with OCR text layer (${codes.joinToString("+")}) • ${ctx.storage.formatBytes(dest.length())}"
                    } else {
                        "$pages halaman dengan lapisan teks OCR (${codes.joinToString("+")}) • ${ctx.storage.formatBytes(dest.length())}"
                    }
                )
            } finally {
                bitmaps.forEach { if (!it.isRecycled) it.recycle() }
            }
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
            val ai = ctx.ai ?: error(
                if (ctx.isEnglish) "AI client is unavailable in this context" else "Klien AI tidak tersedia pada konteks ini"
            )
            val maxPages = input.getFloat("maxPages", 2f).toInt()
            val password = input.getString("password", "")
            progress(20, if (ctx.isEnglish) "Rendering pages for analysis..." else "Merender halaman untuk analisis...")
            val images = mutableListOf<ByteArray>()
            PdfRenderUtils.renderPages(
                ctx.appContext, ctx.storage, pdfOf(input, ctx.isEnglish), dpi = 130, maxPages = maxPages, password = password
            ) { _, rendered, _ ->
                try {
                    val buffer = java.io.ByteArrayOutputStream()
                    rendered.bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, buffer)
                    images.add(buffer.toByteArray())
                } finally {
                    if (!rendered.bitmap.isRecycled) rendered.bitmap.recycle()
                }
                true
            }
            require(images.isNotEmpty()) {
                if (ctx.isEnglish) "No pages available to analyze" else "Tidak ada halaman yang bisa dianalisis"
            }
            val prompt = buildString {
                append("Ekstrak struktur dokumen ini sebagai JSON ringkas: ")
                append("{\"judul\":\"...\",\"ringkasan\":\"...\",\"bagian\":[\"...\"],\"tabel\":[{\"judul\":\"...\",\"kolom\":[\"...\"]}],")
                append("\"daftar\":[\"...\"],\"entitas\":{\"tanggal\":[\"...\"],\"nominal\":[\"...\"]}}. ")
                append("Jangan mengarang isi; tulis null bila tidak ada. Balas JSON saja.")
            }
            val report = StringBuilder()
            var mock = false
            images.forEachIndexed { index, bytes ->
                progress(
                    40 + index * 40 / images.size,
                    if (ctx.isEnglish) "Analyzing page ${index + 1}/${images.size}..." else "Menganalisis halaman ${index + 1}/${images.size}..."
                )
                val result = ai.analyzeImage(bytes, "image/jpeg", prompt)
                mock = mock || result.mock
                val pageHeader = if (ctx.isEnglish) "Page ${index + 1}" else "Halaman ${index + 1}"
                report.append("=== $pageHeader ===\n").append(result.text.trim()).append("\n\n")
            }
            val dest = ctx.storage.createOutputFile("PDF_AI_STRUCTURE", "txt")
            dest.writeText(report.toString().trim())
            ToolResult(
                outputs = listOf(ToolOutputFile(dest, "text/plain", dest.name)),
                textOutput = report.toString().trim().take(4000),
                summary = if (ctx.isEnglish) {
                    (if (mock) "MOCK output (gateway unconfigured) " else "Structure analysis finished ") +
                        "• ${images.size} pages • ${ctx.storage.formatBytes(dest.length())}"
                } else {
                    (if (mock) "Hasil MOCK (gateway tanpa model) " else "Analisis struktur selesai ") +
                        "• ${images.size} halaman • ${ctx.storage.formatBytes(dest.length())}"
                }
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
            progress(40, if (ctx.isEnglish) "Adding page numbers..." else "Menambahkan nomor halaman...")
            val dest = ctx.storage.createOutputFile("PDF_NUMBERS", "pdf")
            val pages = PdfVaultEngine.addPageNumbers(
                ctx.appContext, ctx.storage, pdfOf(input, ctx.isEnglish), dest,
                input.getString("pattern", "{n} / {total}").ifBlank { "{n}" },
                input.getFloat("start", 1f).toInt(),
                input.getString("position", "Tengah Bawah"),
                input.getFloat("size", 4f).toInt()
            )
            ToolResult(
                outputs = listOf(ToolOutputFile(dest, "application/pdf", dest.name)),
                summary = if (ctx.isEnglish) {
                    "Page numbers added to $pages pages"
                } else {
                    "Nomor halaman ditambahkan ke $pages halaman"
                }
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
            val uri = pdfOf(input, ctx.isEnglish)
            val before = PdfVaultEngine.readMetadata(ctx.appContext, ctx.storage, uri)
            val values = mapOf(
                "title" to input.getString("title", ""),
                "author" to input.getString("author", ""),
                "subject" to input.getString("subject", ""),
                "keywords" to input.getString("keywords", "")
            ).filterValues { it.isNotBlank() }
            require(values.isNotEmpty()) {
                if (ctx.isEnglish) "Fill at least one metadata field" else "Isi minimal satu kolom metadata"
            }
            progress(45, if (ctx.isEnglish) "Writing metadata..." else "Menulis metadata...")
            val dest = ctx.storage.createOutputFile("PDF_METADATA", "pdf")
            val pages = PdfVaultEngine.writeMetadata(ctx.appContext, ctx.storage, uri, dest, values)
            val changed = values.keys.joinToString(", ")
            ToolResult(
                outputs = listOf(ToolOutputFile(dest, "application/pdf", dest.name)),
                summary = if (ctx.isEnglish) {
                    "Metadata ($changed) updated across $pages pages. " +
                        "Before: title=\"${before["title"].orEmpty()}\", author=\"${before["author"].orEmpty()}\""
                } else {
                    "Metadata ($changed) diperbarui pada $pages halaman. " +
                        "Sebelum: judul=\"${before["title"].orEmpty()}\", penulis=\"${before["author"].orEmpty()}\""
                }
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
        descId = "Bandingkan teks (baris unik ditambah/dihapus) dan tampilan tiap halaman (gambar diff merah).",
        descEn = "Compare text (added/removed unique lines) and page appearance (red diff images).",
        block = { ctx, input, progress ->
            require(input.uris.size >= 2) {
                if (ctx.isEnglish) "Select 2 PDF files" else "Pilih 2 berkas PDF"
            }
            progress(30, if (ctx.isEnglish) "Comparing text and pages..." else "Membandingkan teks dan halaman...")
            val zip = ctx.storage.createOutputFile("PDF_DIFF_IMG", "zip")
            val report = PdfVaultEngine.compare(
                ctx.appContext, ctx.storage, input.uris[0], input.uris[1], zip
            )
            val textFile = ctx.storage.createOutputFile("PDF_DIFF_REPORT", "txt")
            val body = buildString {
                if (ctx.isEnglish) {
                    appendLine("Document comparison")
                    appendLine("A: ${ctx.storage.getDisplayName(input.uris[0]).orEmpty()} (${report.pagesA} pages)")
                    appendLine("B: ${ctx.storage.getDisplayName(input.uris[1]).orEmpty()} (${report.pagesB} pages)")
                    appendLine("Identical pages: ${report.identicalPages}")
                    appendLine("Different pages: ${report.differentPages}")
                    appendLine("Lines only in A: ${report.removedLines}")
                    appendLine("Lines only in B: ${report.addedLines}")
                    appendLine()
                    appendLine("Note: text diff compares unique-line sets (not LCS); visual diff uses an 8 px grid.")
                } else {
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
            }
            textFile.writeText(body)
            ToolResult(
                outputs = listOf(
                    ToolOutputFile(textFile, "text/plain", textFile.name),
                    ToolOutputFile(zip, "application/zip", zip.name)
                ),
                textOutput = body,
                summary = if (ctx.isEnglish) {
                    "${report.differentPages} pages different, ${report.identicalPages} identical • " +
                        "${report.addedLines}+ / ${report.removedLines}- lines"
                } else {
                    "${report.differentPages} halaman berbeda, ${report.identicalPages} identik • " +
                        "${report.addedLines}+ / ${report.removedLines}- baris"
                }
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
            val uri = pdfOf(input, ctx.isEnglish)
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
                    if (ctx.isEnglish) "This PDF has no AcroForm fields." else "PDF ini tidak memiliki form AcroForm."
                } else {
                    fields.joinToString("\n") { "${it.first} = ${it.second}" }
                }
                ToolResult(
                    textOutput = listing,
                    summary = if (fields.isEmpty()) {
                        if (ctx.isEnglish) "No AcroForm fields found" else "Tidak ada field AcroForm"
                    } else {
                        if (ctx.isEnglish) {
                            "${fields.size} fields: " + fields.take(4).joinToString(", ") { it.first }
                        } else {
                            "${fields.size} field: " + fields.take(4).joinToString(", ") { it.first }
                        }
                    }
                )
            } else {
                progress(
                    45,
                    if (ctx.isEnglish) "Filling ${values.size} fields..." else "Mengisi ${values.size} field..."
                )
                val dest = ctx.storage.createOutputFile("PDF_FORM", "pdf")
                val filled = PdfVaultEngine.fillForm(ctx.appContext, ctx.storage, uri, dest, values)
                val unknown = values.keys - fields.map { it.first }.toSet()
                ToolResult(
                    outputs = listOf(ToolOutputFile(dest, "application/pdf", dest.name)),
                    summary = if (ctx.isEnglish) {
                        "$filled of ${values.size} fields filled" +
                            (if (unknown.isEmpty()) "" else " • not found: ${unknown.joinToString(", ")}") +
                            " • ${ctx.storage.formatBytes(dest.length())}"
                    } else {
                        "$filled dari ${values.size} field terisi" +
                            (if (unknown.isEmpty()) "" else " • tidak ditemukan: ${unknown.joinToString(", ")}") +
                            " • ${ctx.storage.formatBytes(dest.length())}"
                    }
                )
            }
        }
    )
)

/** Ringkasan singkat jumlah tool dan pemeriksaan id unik untuk tes katalog. */
fun pdfVaultToolIds(): List<String> = pdfVaultTools().map { it.id }
