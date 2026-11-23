package com.harimu.app.core.engines

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.harimu.app.core.files.FileStorageManager
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object PdfEngine {

    private fun openPdfRenderer(context: Context, storage: FileStorageManager, uri: Uri): Pair<PdfRenderer, File> {
        val tempPdf = storage.copyUriToTemp(uri, "pdf_in", "pdf")
        val pfd = ParcelFileDescriptor.open(tempPdf, ParcelFileDescriptor.MODE_READ_ONLY)
        return PdfRenderer(pfd) to tempPdf
    }

    fun imagesToPdf(
        context: Context,
        uris: List<Uri>,
        destPdf: File,
        pageSizeMode: String
    ): File {
        val doc = PdfDocument()
        uris.forEachIndexed { idx, uri ->
            val bmp = ImageEngine.decodeBitmapSafe(context, uri, maxDim = 2048)
            val (pageW, pageH) = when (pageSizeMode) {
                "A4" -> 1240 to 1754
                "Letter" -> 1275 to 1650
                else -> bmp.width to bmp.height
            }
            val pageInfo = PdfDocument.PageInfo.Builder(pageW, pageH, idx + 1).create()
            val page = doc.startPage(pageInfo)
            page.canvas.drawColor(Color.WHITE)
            if (pageSizeMode == "Fit") {
                page.canvas.drawBitmap(bmp, 0f, 0f, null)
            } else {
                val margin = 48f
                val availW = pageW - margin * 2
                val availH = pageH - margin * 2
                val scale = min(availW / bmp.width, availH / bmp.height)
                val drawW = bmp.width * scale
                val drawH = bmp.height * scale
                val left = (pageW - drawW) / 2f
                val top = (pageH - drawH) / 2f
                val scaled = Bitmap.createScaledBitmap(bmp, drawW.roundToInt(), drawH.roundToInt(), true)
                page.canvas.drawBitmap(scaled, left, top, null)
                scaled.recycle()
            }
            doc.finishPage(page)
            bmp.recycle()
        }
        FileOutputStream(destPdf).use { doc.writeTo(it) }
        doc.close()
        return destPdf
    }

    fun pdfToImagesZip(
        context: Context,
        storage: FileStorageManager,
        uri: Uri,
        destZip: File,
        scaleFactor: Float,
        format: String
    ): Pair<File, Int> {
        val (renderer, tempFile) = openPdfRenderer(context, storage, uri)
        val count = renderer.pageCount
        try {
            ZipOutputStream(FileOutputStream(destZip)).use { zos ->
                for (i in 0 until count) {
                    renderer.openPage(i).use { page ->
                        val w = (page.width * scaleFactor).roundToInt().coerceAtLeast(100)
                        val h = (page.height * scaleFactor).roundToInt().coerceAtLeast(100)
                        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        val ext = if (format.equals("JPG", true)) "jpg" else "png"
                        val compressFmt = if (ext == "jpg") Bitmap.CompressFormat.JPEG else Bitmap.CompressFormat.PNG
                        zos.putNextEntry(ZipEntry("page_${i + 1}.$ext"))
                        bmp.compress(compressFmt, 92, zos)
                        zos.closeEntry()
                        bmp.recycle()
                    }
                }
            }
        } finally {
            renderer.close()
            tempFile.delete()
        }
        return destZip to count
    }

    fun mergePdfs(
        context: Context,
        storage: FileStorageManager,
        uris: List<Uri>,
        destPdf: File
    ): Pair<File, Int> {
        val doc = PdfDocument()
        var totalPages = 0
        for (uri in uris) {
            val (renderer, temp) = openPdfRenderer(context, storage, uri)
            try {
                for (i in 0 until renderer.pageCount) {
                    renderer.openPage(i).use { page ->
                        val scale = 2f
                        val w = (page.width * scale).roundToInt()
                        val h = (page.height * scale).roundToInt()
                        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                        totalPages++
                        val info = PdfDocument.PageInfo.Builder(w, h, totalPages).create()
                        val outPage = doc.startPage(info)
                        outPage.canvas.drawBitmap(bmp, 0f, 0f, null)
                        doc.finishPage(outPage)
                        bmp.recycle()
                    }
                }
            } finally {
                renderer.close()
                temp.delete()
            }
        }
        FileOutputStream(destPdf).use { doc.writeTo(it) }
        doc.close()
        return destPdf to totalPages
    }

    fun splitPdfToZip(
        context: Context,
        storage: FileStorageManager,
        uri: Uri,
        destZip: File,
        pagesPerSplit: Int
    ): Pair<File, Int> {
        val (renderer, temp) = openPdfRenderer(context, storage, uri)
        val total = renderer.pageCount
        val chunk = pagesPerSplit.coerceAtLeast(1)
        var partCount = 0
        try {
            ZipOutputStream(FileOutputStream(destZip)).use { zos ->
                var start = 0
                while (start < total) {
                    partCount++
                    val end = min(start + chunk, total)
                    val partDoc = PdfDocument()
                    for (i in start until end) {
                        renderer.openPage(i).use { page ->
                            val w = page.width * 2
                            val h = page.height * 2
                            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                            bmp.eraseColor(Color.WHITE)
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                            val info = PdfDocument.PageInfo.Builder(w, h, i - start + 1).create()
                            val outPage = partDoc.startPage(info)
                            outPage.canvas.drawBitmap(bmp, 0f, 0f, null)
                            partDoc.finishPage(outPage)
                            bmp.recycle()
                        }
                    }
                    zos.putNextEntry(ZipEntry("part_${partCount}_p${start + 1}-${end}.pdf"))
                    partDoc.writeTo(zos)
                    zos.closeEntry()
                    partDoc.close()
                    start = end
                }
            }
        } finally {
            renderer.close()
            temp.delete()
        }
        return destZip to partCount
    }

    fun transformPdfPages(
        context: Context,
        storage: FileStorageManager,
        uri: Uri,
        destPdf: File,
        rotateDegrees: Int = 0,
        pageRangeExpr: String = "",
        watermarkText: String? = null,
        addPageNumbers: Boolean = false
    ): Pair<File, Int> {
        val (renderer, temp) = openPdfRenderer(context, storage, uri)
        val total = renderer.pageCount
        val selectedIndices = parsePageIndices(pageRangeExpr, total)
        val outDoc = PdfDocument()
        try {
            selectedIndices.forEachIndexed { idx, pageIndex ->
                renderer.openPage(pageIndex).use { page ->
                    val w = page.width * 2
                    val h = page.height * 2
                    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)

                    val finalBmp = if (rotateDegrees != 0) {
                        val m = Matrix().apply { postRotate(rotateDegrees.toFloat()) }
                        Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true).also {
                            if (it != bmp) bmp.recycle()
                        }
                    } else bmp

                    val info = PdfDocument.PageInfo.Builder(finalBmp.width, finalBmp.height, idx + 1).create()
                    val outPage = outDoc.startPage(info)
                    val canvas = outPage.canvas
                    canvas.drawBitmap(finalBmp, 0f, 0f, null)

                    if (!watermarkText.isNullOrBlank()) {
                        val wmPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = Color.argb(65, 220, 38, 38)
                            textSize = min(finalBmp.width, finalBmp.height) * 0.08f
                            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                            textAlign = Paint.Align.CENTER
                        }
                        canvas.save()
                        canvas.rotate(-32f, finalBmp.width / 2f, finalBmp.height / 2f)
                        canvas.drawText(watermarkText, finalBmp.width / 2f, finalBmp.height / 2f, wmPaint)
                        canvas.restore()
                    }

                    if (addPageNumbers) {
                        val numPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = Color.DKGRAY
                            textSize = max(24f, finalBmp.height * 0.02f)
                            textAlign = Paint.Align.CENTER
                        }
                        canvas.drawText(
                            "${idx + 1} / ${selectedIndices.size}",
                            finalBmp.width / 2f,
                            finalBmp.height - 32f,
                            numPaint
                        )
                    }

                    outDoc.finishPage(outPage)
                    finalBmp.recycle()
                }
            }
            FileOutputStream(destPdf).use { outDoc.writeTo(it) }
        } finally {
            outDoc.close()
            renderer.close()
            temp.delete()
        }
        return destPdf to selectedIndices.size
    }

    fun compareFirstPagesVisual(
        context: Context,
        storage: FileStorageManager,
        uriA: Uri,
        uriB: Uri,
        destPng: File
    ): Pair<File, Double> {
        val (renA, tmpA) = openPdfRenderer(context, storage, uriA)
        val (renB, tmpB) = openPdfRenderer(context, storage, uriB)
        try {
            val pageA = renA.openPage(0)
            val pageB = renB.openPage(0)
            val w = max(pageA.width, pageB.width) * 2
            val h = max(pageA.height, pageB.height) * 2
            val bmpA = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
            val bmpB = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
            pageA.render(bmpA, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            pageB.render(bmpB, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            pageA.close()
            pageB.close()

            val pixA = IntArray(w * h)
            val pixB = IntArray(w * h)
            val diffPix = IntArray(w * h)
            bmpA.getPixels(pixA, 0, w, 0, 0, w, h)
            bmpB.getPixels(pixB, 0, w, 0, 0, w, h)
            bmpA.recycle()
            bmpB.recycle()

            var diffCount = 0
            for (i in pixA.indices) {
                val dr = abs(Color.red(pixA[i]) - Color.red(pixB[i]))
                val dg = abs(Color.green(pixA[i]) - Color.green(pixB[i]))
                val db = abs(Color.blue(pixA[i]) - Color.blue(pixB[i]))
                if (dr + dg + db > 45) {
                    diffCount++
                    diffPix[i] = Color.rgb(255, 70, 50)
                } else {
                    val gray = (Color.red(pixA[i]) * 0.3 + Color.green(pixA[i]) * 0.59 + Color.blue(pixA[i]) * 0.11).toInt()
                    val muted = 200 + (gray / 6)
                    diffPix[i] = Color.rgb(muted, muted, muted)
                }
            }
            val diffBmp = Bitmap.createBitmap(diffPix, w, h, Bitmap.Config.ARGB_8888)
            ImageEngine.saveBitmap(diffBmp, destPng, "PNG", 100)
            diffBmp.recycle()
            val diffPercent = (diffCount.toDouble() / pixA.size) * 100.0
            return destPng to diffPercent
        } finally {
            renA.close()
            renB.close()
            tmpA.delete()
            tmpB.delete()
        }
    }

    private fun parsePageIndices(expr: String, totalPages: Int): List<Int> {
        if (expr.isBlank()) return (0 until totalPages).toList()
        val result = linkedSetOf<Int>()
        expr.split(",").map { it.trim() }.filter { it.isNotEmpty() }.forEach { token ->
            if (token.contains("-")) {
                val parts = token.split("-")
                val start = (parts.getOrNull(0)?.toIntOrNull() ?: 1).coerceIn(1, totalPages)
                val end = (parts.getOrNull(1)?.toIntOrNull() ?: totalPages).coerceIn(1, totalPages)
                val range = if (start <= end) start..end else end..start
                for (p in range) result.add(p - 1)
            } else {
                token.toIntOrNull()?.let { p ->
                    if (p in 1..totalPages) result.add(p - 1)
                }
            }
        }
        return if (result.isEmpty()) (0 until totalPages).toList() else result.toList()
    }
}
