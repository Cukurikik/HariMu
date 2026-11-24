package com.harimu.app.core.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.harimu.app.core.engines.ImageEngine
import com.harimu.app.core.files.FileStorageManager
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.tom_roush.pdfbox.pdmodel.graphics.state.RenderingMode
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDAcroForm
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotation
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import com.tom_roush.pdfbox.util.Matrix
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Engine PDF Vault (FASE 12) di atas **PDFBox-Android (Apache-2.0)**.
 *
 * Berbeda dari implementasi raster Fase 8 (`PdfEngine`), operasi halaman di sini bekerja pada
 * objek PDF asli, jadi teks, vektor, dan tautan tetap utuh setelah merge/split/rotate/reorder.
 * Hanya operasi yang memang butuh rasterisasi (compress, redact, OCR) yang mengubah halaman
 * menjadi gambar — dan itu dinyatakan jujur di ringkasan hasil.
 */
object PdfVaultEngine {

    private var initialized = false

    fun ensureInit(context: Context) {
        if (initialized) return
        runCatching { PDFBoxResourceLoader.init(context.applicationContext) }
        initialized = true
    }

    // ------------------------------------------------------------ load/save

    data class Loaded(val doc: PDDocument, val tempFile: File) : AutoCloseable {
        override fun close() {
            runCatching { doc.close() }
            runCatching { tempFile.delete() }
        }
    }

    fun load(
        context: Context,
        storage: FileStorageManager,
        uri: Uri,
        password: String = ""
    ): Loaded {
        ensureInit(context)
        val temp = storage.copyUriToTemp(uri, "pdfvault", "pdf")
        val doc = if (password.isNotBlank()) {
            PDDocument.load(temp, password)
        } else {
            PDDocument.load(temp)
        }
        return Loaded(doc, temp)
    }

    fun pageCount(context: Context, storage: FileStorageManager, uri: Uri, password: String = ""): Int =
        load(context, storage, uri, password).use { it.doc.numberOfPages }

    fun isEncrypted(context: Context, storage: FileStorageManager, uri: Uri): Boolean =
        load(context, storage, uri).use { it.doc.isEncrypted }

    // -------------------------------------------------------------- halaman

    fun merge(context: Context, storage: FileStorageManager, uris: List<Uri>, dest: File): Int {
        ensureInit(context)
        PDDocument().use { target ->
            uris.forEach { uri ->
                load(context, storage, uri).use { source ->
                    for (index in 0 until source.doc.numberOfPages) {
                        target.importPage(source.doc.getPage(index))
                    }
                }
            }
            val total = target.numberOfPages
            target.save(dest)
            return total
        }
    }

    fun split(
        context: Context,
        storage: FileStorageManager,
        uri: Uri,
        destZip: File,
        pagesPerFile: Int,
        password: String = ""
    ): Pair<File, Int> {
        load(context, storage, uri, password).use { source ->
            val total = source.doc.numberOfPages
            val chunk = pagesPerFile.coerceAtLeast(1)
            var part = 0
            ZipOutputStream(FileOutputStream(destZip)).use { zip ->
                var start = 0
                while (start < total) {
                    part += 1
                    val end = min(start + chunk, total)
                    PDDocument().use { partDoc ->
                        for (index in start until end) partDoc.importPage(source.doc.getPage(index))
                        zip.putNextEntry(ZipEntry("bagian_${part}_hal_${start + 1}-$end.pdf"))
                        partDoc.save(zip)
                        zip.closeEntry()
                    }
                    start = end
                }
            }
            return destZip to part
        }
    }

    fun rotate(
        context: Context,
        storage: FileStorageManager,
        uri: Uri,
        dest: File,
        angle: Int,
        pages: List<Int> = emptyList()
    ): Int {
        load(context, storage, uri).use { loaded ->
            val targets = if (pages.isEmpty()) (1..loaded.doc.numberOfPages).toList() else pages
            targets.forEach { pageNumber ->
                if (pageNumber in 1..loaded.doc.numberOfPages) {
                    val page = loaded.doc.getPage(pageNumber - 1)
                    page.rotation = ((page.rotation + angle) % 360 + 360) % 360
                }
            }
            loaded.doc.save(dest)
            return targets.size
        }
    }

    fun reorder(
        context: Context,
        storage: FileStorageManager,
        uri: Uri,
        dest: File,
        order: List<Int>
    ): Int {
        load(context, storage, uri).use { loaded ->
            val total = loaded.doc.numberOfPages
            val valid = order.filter { it in 1..total }
            require(valid.isNotEmpty()) { "Urutan halaman kosong setelah divalidasi" }
            PDDocument().use { target ->
                valid.forEach { pageNumber -> target.importPage(loaded.doc.getPage(pageNumber - 1)) }
                target.save(dest)
                return target.numberOfPages
            }
        }
    }

    fun extract(
        context: Context,
        storage: FileStorageManager,
        uri: Uri,
        dest: File,
        pages: List<Int>
    ): Int {
        load(context, storage, uri).use { loaded ->
            val total = loaded.doc.numberOfPages
            val valid = pages.filter { it in 1..total }
            require(valid.isNotEmpty()) { "Tidak ada halaman valid untuk diekstrak" }
            PDDocument().use { target ->
                valid.forEach { pageNumber -> target.importPage(loaded.doc.getPage(pageNumber - 1)) }
                target.save(dest)
                return target.numberOfPages
            }
        }
    }

    // ------------------------------------------------------------ watermark

    fun addTextWatermark(
        context: Context,
        storage: FileStorageManager,
        uri: Uri,
        dest: File,
        text: String,
        opacityPct: Int,
        angleDeg: Int,
        sizePct: Int
    ): Int {
        require(text.isNotBlank()) { "Teks watermark kosong" }
        load(context, storage, uri).use { loaded ->
            val font = PDType1Font(PDType1Font.HELVETICA_BOLD)
            val graphicsState = PDExtendedGraphicsState().apply {
                nonStrokingAlphaConstant = (opacityPct.coerceIn(5, 100) / 100f)
            }
            loaded.doc.pages.forEach { page ->
                val box: PDRectangle = page.mediaBox
                val w = box.width
                val h = box.height
                val fontSize = (min(w, h) * sizePct.coerceIn(2, 30) / 100f).coerceAtLeast(8f)
                val textWidth = font.getStringWidth(text) / 1000f * fontSize
                val angleRad = Math.toRadians(angleDeg.toDouble())
                val startX = (w / 2f - cos(angleRad).toFloat() * textWidth / 2f).coerceAtLeast(2f)
                val startY = (h / 2f - sin(angleRad).toFloat() * textWidth / 2f).coerceAtLeast(2f)
                PDPageContentStream(
                    loaded.doc,
                    page,
                    PDPageContentStream.AppendMode.APPEND,
                    true,
                    true
                ).use { stream ->
                    stream.saveGraphicsState()
                    stream.setGraphicsStateParameters(graphicsState)
                    stream.beginText()
                    stream.setFont(font, fontSize)
                    stream.setNonStrokingColor(0.75f, 0.1f, 0.1f)
                    stream.setTextMatrix(Matrix.getRotateInstance(angleRad, startX, startY))
                    stream.showText(text)
                    stream.endText()
                    stream.restoreGraphicsState()
                }
            }
            loaded.doc.save(dest)
            return loaded.doc.numberOfPages
        }
    }

    fun addImageWatermark(
        context: Context,
        storage: FileStorageManager,
        uri: Uri,
        imageUri: Uri,
        dest: File,
        opacityPct: Int,
        scalePct: Int
    ): Int {
        ensureInit(context)
        val bitmap = ImageEngine.decodeBitmapSafe(context, imageUri, 1200)
        val pngBytes = ByteArrayOutputStream().use { buffer ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, buffer)
            bitmap.recycle()
            buffer.toByteArray()
        }
        load(context, storage, uri).use { loaded ->
            val image = PDImageXObject.createFromByteArray(loaded.doc, pngBytes, "watermark.png")
            val graphicsState = PDExtendedGraphicsState().apply {
                nonStrokingAlphaConstant = (opacityPct.coerceIn(5, 100) / 100f)
            }
            loaded.doc.pages.forEach { page ->
                val w = page.mediaBox.width
                val h = page.mediaBox.height
                val targetWidth = (w * scalePct.coerceIn(5, 100) / 100f).coerceAtLeast(24f)
                val targetHeight = targetWidth * image.height / image.width
                PDPageContentStream(
                    loaded.doc,
                    page,
                    PDPageContentStream.AppendMode.APPEND,
                    true,
                    true
                ).use { stream ->
                    stream.saveGraphicsState()
                    stream.setGraphicsStateParameters(graphicsState)
                    stream.drawImage(
                        image,
                        (w - targetWidth) / 2f,
                        (h - targetHeight) / 2f,
                        targetWidth,
                        targetHeight
                    )
                    stream.restoreGraphicsState()
                }
            }
            loaded.doc.save(dest)
            return loaded.doc.numberOfPages
        }
    }

    // ------------------------------------------------------------ keamanan

    fun encrypt(
        context: Context,
        storage: FileStorageManager,
        uri: Uri,
        dest: File,
        userPassword: String,
        ownerPassword: String,
        keyBits: Int
    ): Int {
        require(userPassword.isNotBlank() || ownerPassword.isNotBlank()) {
            "Isi minimal satu password (pengguna atau pemilik)"
        }
        val resolvedOwner = ownerPassword.ifBlank { userPassword }
        load(context, storage, uri).use { loaded ->
            val permission = AccessPermission().apply {
                setCanPrint(true)
                setCanModify(true)
                setCanExtractContent(true)
                setCanModifyAnnotations(true)
                setCanFillInForm(true)
                setCanAssembleDocument(true)
                setCanExtractForAccessibility(true)
            }
            val policy = StandardProtectionPolicy(resolvedOwner, userPassword, permission)
            policy.encryptionKeyLength = when (keyBits) {
                256 -> 256
                40 -> 40
                else -> 128
            }
            // 40-bit tetap RC4; 128/256 memakai AES (AESV2/AESV3 di StandardSecurityHandler).
            policy.isPreferAES = keyBits != 40
            loaded.doc.protect(policy)
            loaded.doc.save(dest)
            return loaded.doc.numberOfPages
        }
    }

    fun decrypt(
        context: Context,
        storage: FileStorageManager,
        uri: Uri,
        dest: File,
        password: String
    ): Int {
        load(context, storage, uri, password).use { loaded ->
            require(loaded.doc.isEncrypted || password.isNotBlank()) {
                "PDF ini tidak terenkripsi"
            }
            loaded.doc.isAllSecurityToBeRemoved = true
            loaded.doc.save(dest)
            return loaded.doc.numberOfPages
        }
    }

    data class RedactReport(
        val pages: Int,
        val boxes: Int,
        val remainingTextChars: Int,
        val verified: Boolean,
        val note: String
    )

    /**
     * Redaksi permanen: halaman yang mengandung istilah diratakan menjadi gambar lalu kotak hitam
     * digambar di posisi kemunculannya. Karena konten asli diganti gambar, teks di bawah kotak
     * (dan seluruh teks halaman itu) benar-benar hilang dari berkas — bukan sekadar ditutup.
     * Hasil diverifikasi dengan mengekstrak ulang teks PDF keluaran.
     */
    fun redact(
        context: Context,
        storage: FileStorageManager,
        uri: Uri,
        dest: File,
        terms: List<String>,
        dpi: Int = 150,
        password: String = ""
    ): RedactReport {
        require(terms.isNotEmpty()) { "Isi minimal satu istilah yang ingin dihapus" }
        val cleanTerms = terms.map { it.trim() }.filter { it.isNotEmpty() }
        val loaded = load(context, storage, uri, password)
        val hits: Map<Int, List<RectF>>
        val pageSizes: List<Pair<Float, Float>>
        try {
            val locator = TermLocator(cleanTerms)
            locator.startPage = 1
            locator.endPage = loaded.doc.numberOfPages
            locator.getText(loaded.doc)
            hits = locator.hits
            pageSizes = (0 until loaded.doc.numberOfPages).map { index ->
                val box = loaded.doc.getPage(index).mediaBox
                box.width to box.height
            }
        } finally {
            loaded.close()
        }
        if (hits.isEmpty()) {
            return RedactReport(
                pages = pageSizes.size,
                boxes = 0,
                remainingTextChars = extractTextFile(context, storage, uri).length,
                verified = false,
                note = "Istilah tidak ditemukan sebagai teks; tidak ada area yang dihapus."
            )
        }
        // Render tiap halaman (yang punya kotak) ke bitmap, tutup area, lalu susun PDF baru.
        val tempPdf = storage.copyUriToTemp(uri, "pdf_redact_src", "pdf")
        val document = PDDocument()
        try {
            ParcelFileDescriptor.open(tempPdf, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    for (index in 0 until renderer.pageCount) {
                        val page = renderer.openPage(index)
                        val scale = dpi.coerceIn(72, 300) / 72f
                        val width = (page.width * scale).roundToInt().coerceAtLeast(1)
                        val height = (page.height * scale).roundToInt().coerceAtLeast(1)
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                        page.close()
                        val rects = hits[index + 1].orEmpty()
                        if (rects.isNotEmpty()) {
                            val canvas = android.graphics.Canvas(bitmap)
                            val paint = Paint().apply { color = Color.BLACK }
                            val scaleX = width / pageSizes[index].first
                            val scaleY = height / pageSizes[index].second
                            rects.forEach { rect ->
                                canvas.drawRect(
                                    rect.left * scaleX,
                                    rect.top * scaleY,
                                    rect.right * scaleX,
                                    rect.bottom * scaleY,
                                    paint
                                )
                            }
                        }
                        val buffer = ByteArrayOutputStream()
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, buffer)
                        bitmap.recycle()
                        val image = PDImageXObject.createFromByteArray(document, buffer.toByteArray(), "page_$index.jpg")
                        val pdfPage = PDPage(PDRectangle(width.toFloat(), height.toFloat()))
                        document.addPage(pdfPage)
                        PDPageContentStream(document, pdfPage).use { stream ->
                            stream.drawImage(image, 0f, 0f, width.toFloat(), height.toFloat())
                        }
                    }
                }
            }
            document.save(dest)
        } finally {
            document.close()
            tempPdf.delete()
        }
        val remaining = extractText(dest)
        val verified = cleanTerms.none { term -> remaining.contains(term, ignoreCase = true) }
        return RedactReport(
            pages = pageSizes.size,
            boxes = hits.values.sumOf { it.size },
            remainingTextChars = remaining.length,
            verified = verified,
            note = if (verified) {
                "Halaman diratakan; teks di area redaksi hilang dan verifikasi ekstraksi teks lulus."
            } else {
                "Peringatan: istilah masih terbaca pada hasil ekstraksi teks."
            }
        )
    }

    /**
     * Menghapus watermark yang berbentuk objek terpisah: anotasi (stempel/teks annotasi) dan
     * optional content group. Watermark yang sudah menyatu ke aliran konten TIDAK bisa dihapus
     * dengan cara ini — batas ini dilaporkan apa adanya.
     */
    fun removeWatermarkObjects(
        context: Context,
        storage: FileStorageManager,
        uri: Uri,
        dest: File
    ): Int {
        load(context, storage, uri).use { loaded ->
            var removed = 0
            loaded.doc.pages.forEach { page ->
                val annotations: List<PDAnnotation> = page.annotations
                removed += annotations.size
                if (annotations.isNotEmpty()) page.annotations = emptyList()
            }
            val catalog = loaded.doc.documentCatalog
            if (catalog.ocProperties != null) {
                catalog.ocProperties = null
            }
            loaded.doc.save(dest)
            return removed
        }
    }

    // --------------------------------------------------------------- teks

    fun extractText(file: File): String {
        PDDocument.load(file).use { doc ->
            val stripper = PDFTextStripper()
            return stripper.getText(doc)
        }
    }

    fun extractTextFile(
        context: Context,
        storage: FileStorageManager,
        uri: Uri,
        password: String = ""
    ): String = load(context, storage, uri, password).use { extractText(it.tempFile) }

    // ---------------------------------------------------------- metadata

    fun readMetadata(
        context: Context,
        storage: FileStorageManager,
        uri: Uri
    ): Map<String, String> = load(context, storage, uri).use { loaded ->
        val info = loaded.doc.documentInformation
        linkedMapOf(
            "title" to info.title.orEmpty(),
            "author" to info.author.orEmpty(),
            "subject" to info.subject.orEmpty(),
            "keywords" to info.keywords.orEmpty(),
            "creator" to info.creator.orEmpty(),
            "producer" to info.producer.orEmpty()
        )
    }

    fun writeMetadata(
        context: Context,
        storage: FileStorageManager,
        uri: Uri,
        dest: File,
        values: Map<String, String>
    ): Int {
        load(context, storage, uri).use { loaded ->
            val info = loaded.doc.documentInformation
            values["title"]?.let { info.title = it }
            values["author"]?.let { info.author = it }
            values["subject"]?.let { info.subject = it }
            values["keywords"]?.let { info.keywords = it }
            values["creator"]?.let { info.creator = it }
            loaded.doc.save(dest)
            return loaded.doc.numberOfPages
        }
    }

    // ------------------------------------------------------ page numbering

    fun addPageNumbers(
        context: Context,
        storage: FileStorageManager,
        uri: Uri,
        dest: File,
        pattern: String,
        startNumber: Int,
        position: String,
        fontSizePct: Int
    ): Int {
        load(context, storage, uri).use { loaded ->
            val font = PDType1Font(PDType1Font.HELVETICA)
            val total = loaded.doc.numberOfPages
            loaded.doc.pages.forEachIndexed { index, page ->
                val w = page.mediaBox.width
                val h = page.mediaBox.height
                val fontSize = (min(w, h) * fontSizePct.coerceIn(2, 10) / 100f).coerceAtLeast(6f)
                val label = pattern
                    .replace("{n}", (startNumber + index).toString())
                    .replace("{total}", total.toString())
                val textWidth = font.getStringWidth(label) / 1000f * fontSize
                val margin = fontSize * 1.2f
                val x = when (position) {
                    "Kiri Bawah", "Kiri Atas" -> margin
                    "Kanan Bawah", "Kanan Atas" -> w - margin - textWidth
                    else -> (w - textWidth) / 2f
                }
                val y = if (position.contains("Atas")) h - margin - fontSize else margin
                PDPageContentStream(
                    loaded.doc,
                    page,
                    PDPageContentStream.AppendMode.APPEND,
                    true,
                    true
                ).use { stream ->
                    stream.beginText()
                    stream.setFont(font, fontSize)
                    stream.setNonStrokingColor(0.2f, 0.2f, 0.2f)
                    stream.newLineAtOffset(x, y)
                    stream.showText(label)
                    stream.endText()
                }
            }
            loaded.doc.save(dest)
            return total
        }
    }

    // ---------------------------------------------------------- kompresi

    data class CompressReport(val pages: Int, val beforeBytes: Long, val afterBytes: Long)

    /** Rasterisasi ulang halaman (JPEG) untuk mengecilkan ukuran; teks menjadi gambar. */
    fun compress(
        context: Context,
        storage: FileStorageManager,
        uri: Uri,
        dest: File,
        dpi: Int,
        qualityPct: Int
    ): CompressReport {
        val sourceFile = storage.copyUriToTemp(uri, "pdf_compress_src", "pdf")
        val before = sourceFile.length()
        sourceFile.delete()
        val tempPdf = storage.copyUriToTemp(uri, "pdf_compress_render", "pdf")
        val document = PDDocument()
        try {
            ParcelFileDescriptor.open(tempPdf, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    for (index in 0 until renderer.pageCount) {
                        val page = renderer.openPage(index)
                        val scale = dpi.coerceIn(72, 200) / 72f
                        val width = (page.width * scale).roundToInt().coerceAtLeast(1)
                        val height = (page.height * scale).roundToInt().coerceAtLeast(1)
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                        page.close()
                        val buffer = ByteArrayOutputStream()
                        bitmap.compress(Bitmap.CompressFormat.JPEG, qualityPct.coerceIn(30, 95), buffer)
                        bitmap.recycle()
                        val image = PDImageXObject.createFromByteArray(document, buffer.toByteArray(), "p$index.jpg")
                        val pdfPage = PDPage(PDRectangle(width.toFloat(), height.toFloat()))
                        document.addPage(pdfPage)
                        PDPageContentStream(document, pdfPage).use { stream ->
                            stream.drawImage(image, 0f, 0f, width.toFloat(), height.toFloat())
                        }
                    }
                }
            }
            document.save(dest)
            val pages = document.numberOfPages
            return CompressReport(pages, before, dest.length())
        } finally {
            document.close()
            tempPdf.delete()
        }
    }

    // ------------------------------------------------------- PDF + gambar

    /** PDF dari gambar; bila [texts] diisi, tiap halaman mendapat lapisan teks tak terlihat. */
    fun imagesToPdf(
        context: Context,
        imageUris: List<Uri>,
        dest: File,
        texts: List<String> = emptyList(),
        jpegQuality: Int = 90,
        pageSizeMode: String = "Fit"
    ): Int {
        ensureInit(context)
        val document = PDDocument()
        try {
            imageUris.forEachIndexed { index, uri ->
                val bitmap = ImageEngine.decodeBitmapSafe(context, uri, 2400)
                val buffer = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, jpegQuality.coerceIn(40, 100), buffer)
                val pageRect = when (pageSizeMode) {
                    "A4" -> PDRectangle.A4
                    "Letter" -> PDRectangle.LETTER
                    else -> PDRectangle(bitmap.width.toFloat(), bitmap.height.toFloat())
                }
                val widthPt = pageRect.width
                val heightPt = pageRect.height
                val margin = if (pageSizeMode == "Fit") 0f else 28f
                val availableW = widthPt - margin * 2
                val availableH = heightPt - margin * 2
                val scale = min(availableW / bitmap.width, availableH / bitmap.height)
                val drawW = bitmap.width * scale
                val drawH = bitmap.height * scale
                val drawX = (widthPt - drawW) / 2f
                val drawY = (heightPt - drawH) / 2f
                bitmap.recycle()
                val image = PDImageXObject.createFromByteArray(document, buffer.toByteArray(), "img$index.jpg")
                val page = PDPage(pageRect)
                document.addPage(page)
                PDPageContentStream(document, page).use { stream ->
                    stream.drawImage(image, drawX, drawY, drawW, drawH)
                    val text = texts.getOrNull(index).orEmpty()
                    if (text.isNotBlank()) {
                        val font = PDType1Font(PDType1Font.HELVETICA)
                        val fontSize = max(6f, min(12f, heightPt / 70f))
                        stream.setRenderingMode(RenderingMode.NEITHER)
                        stream.beginText()
                        stream.setFont(font, fontSize)
                        val margin = fontSize * 1.4f
                        var y = heightPt - margin
                        text.lineSequence().take(200).forEach { line ->
                            if (line.isNotBlank()) {
                                // Lebar baris dibatasi agar tidak melewati halaman.
                                val trimmed = line.take(180)
                                stream.setTextMatrix(Matrix.getTranslateInstance(margin, y))
                                stream.showText(trimmed.replace(Regex("[\\u0000-\\u001F]"), " "))
                                y -= margin
                            }
                        }
                        stream.endText()
                        stream.setRenderingMode(RenderingMode.FILL)
                    }
                }
            }
            document.save(dest)
            return imageUris.size
        } finally {
            document.close()
        }
    }

    // ---------------------------------------------------------- AcroForm

    fun listFormFields(
        context: Context,
        storage: FileStorageManager,
        uri: Uri
    ): List<Pair<String, String>> = load(context, storage, uri).use { loaded ->
        val form: PDAcroForm = loaded.doc.documentCatalog.acroForm ?: return emptyList()
        form.fieldTree.mapNotNull { field ->
            val value = runCatching { field.valueAsString }.getOrNull().orEmpty()
            field.fullyQualifiedName?.let { it to value }
        }
    }

    fun fillForm(
        context: Context,
        storage: FileStorageManager,
        uri: Uri,
        dest: File,
        values: Map<String, String>
    ): Int {
        load(context, storage, uri).use { loaded ->
            val form = loaded.doc.documentCatalog.acroForm
                ?: throw IllegalStateException("PDF ini tidak punya form AcroForm")
            var filled = 0
            values.forEach { (name, value) ->
                val field = form.fieldTree.firstOrNull { it.fullyQualifiedName == name }
                if (field != null) {
                    runCatching { field.setValue(value) }
                        .onSuccess { filled += 1 }
                }
            }
            loaded.doc.save(dest)
            return filled
        }
    }

    // --------------------------------------------------------- pembanding

    data class CompareReport(
        val pagesA: Int,
        val pagesB: Int,
        val identicalPages: Int,
        val differentPages: Int,
        val addedLines: Int,
        val removedLines: Int,
        val diffImagesZip: File?
    )

    fun compare(
        context: Context,
        storage: FileStorageManager,
        uriA: Uri,
        uriB: Uri,
        diffZip: File?,
        dpi: Int = 96
    ): CompareReport {
        val textA = extractTextFile(context, storage, uriA)
        val textB = extractTextFile(context, storage, uriB)
        val linesA = textA.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val linesB = textB.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val setA = linesA.toSet()
        val setB = linesB.toSet()
        val removed = setA.count { it !in setB }
        val added = setB.count { it !in setA }

        val tempA = storage.copyUriToTemp(uriA, "cmp_a", "pdf")
        val tempB = storage.copyUriToTemp(uriB, "cmp_b", "pdf")
        var identical = 0
        var different = 0
        val zipStream = diffZip?.let { ZipOutputStream(FileOutputStream(it)) }
        try {
            ParcelFileDescriptor.open(tempA, ParcelFileDescriptor.MODE_READ_ONLY).use { descA ->
                ParcelFileDescriptor.open(tempB, ParcelFileDescriptor.MODE_READ_ONLY).use { descB ->
                    PdfRenderer(descA).use { rendererA ->
                        PdfRenderer(descB).use { rendererB ->
                            val pages = max(rendererA.pageCount, rendererB.pageCount)
                            for (index in 0 until pages) {
                                val bitmapA = renderBitmap(rendererA, index, dpi)
                                val bitmapB = renderBitmap(rendererB, index, dpi)
                                if (bitmapA == null || bitmapB == null) {
                                    different += 1
                                    bitmapA?.recycle()
                                    bitmapB?.recycle()
                                    continue
                                }
                                val diff = buildDiffBitmap(bitmapA, bitmapB)
                                val changed = diff.changed
                                if (changed) different += 1 else identical += 1
                                if (changed && zipStream != null) {
                                    zipStream.putNextEntry(ZipEntry("diff_hal_${index + 1}.png"))
                                    diff.bitmap.compress(Bitmap.CompressFormat.PNG, 100, zipStream)
                                    zipStream.closeEntry()
                                }
                                diff.bitmap.recycle()
                                bitmapA.recycle()
                                bitmapB.recycle()
                            }
                        }
                    }
                }
            }
        } finally {
            tempA.delete()
            tempB.delete()
            zipStream?.let { runCatching { it.close() } }
        }
        return CompareReport(
            pagesA = pageCount(context, storage, uriA),
            pagesB = pageCount(context, storage, uriB),
            identicalPages = identical,
            differentPages = different,
            addedLines = added,
            removedLines = removed,
            diffImagesZip = diffZip
        )
    }

    private data class DiffResult(val bitmap: Bitmap, val changed: Boolean)

    private fun renderBitmap(renderer: PdfRenderer, index: Int, dpi: Int): Bitmap? {
        if (index >= renderer.pageCount) return null
        val page = renderer.openPage(index)
        val scale = dpi.coerceIn(60, 150) / 72f
        val width = (page.width * scale).roundToInt().coerceAtLeast(1)
        val height = (page.height * scale).roundToInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
        page.close()
        return bitmap
    }

    private fun buildDiffBitmap(a: Bitmap, b: Bitmap, tolerance: Int = 24): DiffResult {
        val width = max(a.width, b.width)
        val height = max(a.height, b.height)
        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        out.eraseColor(Color.WHITE)
        val canvas = android.graphics.Canvas(out)
        canvas.drawBitmap(a, 0f, 0f, null)
        val changedPixels = IntArray(width * height) { 0 }
        var changed = false
        // Bandingkan pada grid 8 px supaya cepat untuk halaman besar.
        val step = 8
        val paint = Paint().apply { color = Color.argb(190, 220, 38, 38) }
        var y = 0
        while (y < height) {
            var x = 0
            while (x < width) {
                val pa = a.getPixel(min(x, a.width - 1), min(y, a.height - 1))
                val pb = b.getPixel(min(x, b.width - 1), min(y, b.height - 1))
                val diff = kotlin.math.abs(Color.red(pa) - Color.red(pb)) +
                    kotlin.math.abs(Color.green(pa) - Color.green(pb)) +
                    kotlin.math.abs(Color.blue(pa) - Color.blue(pb))
                if (diff > tolerance) {
                    changed = true
                    canvas.drawRect(
                        x.toFloat(),
                        y.toFloat(),
                        (x + step).toFloat(),
                        (y + step).toFloat(),
                        paint
                    )
                }
                x += step
            }
            y += step
        }
        return DiffResult(out, changed)
    }

    // ------------------------------------------------- helper pencarian teks

    private class TermLocator(private val terms: List<String>) : PDFTextStripper() {
        val hits = mutableMapOf<Int, MutableList<RectF>>()

        override fun writeString(text: String, textPositions: MutableList<TextPosition>) {
            super.writeString(text, textPositions)
            if (textPositions.isEmpty()) return
            val lower = text.lowercase()
            terms.forEach { term ->
                val needle = term.lowercase()
                var from = lower.indexOf(needle)
                while (from >= 0) {
                    val to = from + needle.length
                    // Petakan indeks karakter ke TextPosition yang beririsan.
                    val start = textPositions.getOrNull(from)
                    val end = textPositions.getOrNull((to - 1).coerceAtMost(textPositions.size - 1))
                    if (start != null && end != null) {
                        val left = min(start.xDirAdj, end.xDirAdj)
                        val right = max(start.xDirAdj + start.widthDirAdj, end.xDirAdj + end.widthDirAdj)
                        val top = min(start.yDirAdj - start.heightDir, end.yDirAdj - end.heightDir)
                        val bottom = max(start.yDirAdj, end.yDirAdj)
                        val pageIndex = currentPageNo
                        hits.getOrPut(pageIndex) { mutableListOf() }
                            .add(RectF(left - 2f, top - 2f, right + 2f, bottom + 2f))
                    }
                    from = lower.indexOf(needle, to)
                }
            }
        }
    }

    // ----------------------------------------------------------- halaman

    /** Parser rentang halaman: "1-3,5,8-" → [1,2,3,5,8,9,...]. Murni, diuji unit. */
    fun parsePageRange(expression: String, totalPages: Int): List<Int> {
        val clean = expression.trim()
        if (clean.isEmpty() || totalPages <= 0) return emptyList()
        val result = linkedSetOf<Int>()
        clean.split(',').forEach { chunk ->
            val part = chunk.trim()
            if (part.isEmpty()) return@forEach
            if (part.contains('-')) {
                val bounds = part.split('-')
                val start = bounds.getOrNull(0)?.trim()?.toIntOrNull() ?: 1
                val end = bounds.getOrNull(1)?.trim()?.toIntOrNull() ?: totalPages
                val from = start.coerceIn(1, totalPages)
                val to = end.coerceIn(1, totalPages)
                if (from <= to) for (page in from..to) result.add(page)
            } else {
                part.toIntOrNull()?.let { page ->
                    if (page in 1..totalPages) result.add(page)
                }
            }
        }
        return result.toList()
    }

    /** PDF dari bitmap di memori (dipakai OCR agar tidak perlu file antara). */
    fun bitmapsToPdf(
        context: Context,
        bitmaps: List<Bitmap>,
        texts: List<String>,
        dest: File,
        jpegQuality: Int = 88
    ): Int {
        ensureInit(context)
        val document = PDDocument()
        try {
            bitmaps.forEachIndexed { index, bitmap ->
                val buffer = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, jpegQuality.coerceIn(40, 100), buffer)
                val widthPt = bitmap.width.toFloat()
                val heightPt = bitmap.height.toFloat()
                val image = PDImageXObject.createFromByteArray(document, buffer.toByteArray(), "ocr$index.jpg")
                val page = PDPage(PDRectangle(widthPt, heightPt))
                document.addPage(page)
                PDPageContentStream(document, page).use { stream ->
                    stream.drawImage(image, 0f, 0f, widthPt, heightPt)
                    val text = texts.getOrNull(index).orEmpty()
                    if (text.isNotBlank()) {
                        val font = PDType1Font(PDType1Font.HELVETICA)
                        val fontSize = max(6f, min(11f, heightPt / 80f))
                        stream.setRenderingMode(RenderingMode.NEITHER)
                        stream.beginText()
                        stream.setFont(font, fontSize)
                        var y = heightPt - fontSize * 2f
                        text.lineSequence().take(300).forEach { line ->
                            if (line.isNotBlank()) {
                                stream.setTextMatrix(Matrix.getTranslateInstance(fontSize * 1.5f, y))
                                stream.showText(line.take(160).replace(Regex("[\\u0000-\\u001F]"), " "))
                                y -= fontSize * 1.4f
                            }
                        }
                        stream.endText()
                        stream.setRenderingMode(RenderingMode.FILL)
                    }
                }
            }
            document.save(dest)
            return bitmaps.size
        } finally {
            document.close()
        }
    }

    /** Parse rentang halaman tanpa mengetahui jumlah halaman (dipakai UI pemilih halaman). */
    fun parsePagesLoose(expression: String): List<Int> = parsePageRange(expression, 20_000)

    /** Format balik daftar halaman menjadi "1-3,5" agar ringkas di kolom teks. */
    fun formatPageRange(pages: List<Int>): String {
        if (pages.isEmpty()) return ""
        val sorted = pages.distinct().sorted()
        val parts = mutableListOf<String>()
        var start = sorted.first()
        var previous = start
        sorted.drop(1).forEach { page ->
            if (page == previous + 1) {
                previous = page
            } else {
                parts.add(if (start == previous) "$start" else "$start-$previous")
                start = page
                previous = page
            }
        }
        parts.add(if (start == previous) "$start" else "$start-$previous")
        return parts.joinToString(",")
    }

    fun parseOrder(expression: String, totalPages: Int): List<Int> =
        expression.split(',').mapNotNull { it.trim().toIntOrNull() }
            .filter { it in 1..totalPages }
}
