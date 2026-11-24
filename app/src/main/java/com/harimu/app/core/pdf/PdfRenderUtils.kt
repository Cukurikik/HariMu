package com.harimu.app.core.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.harimu.app.core.files.FileStorageManager
import kotlin.math.roundToInt

/** Rasterisasi halaman PDF dengan PdfRenderer bawaan Android (tanpa dependensi tambahan). */
object PdfRenderUtils {

    data class Rendered(val bitmap: Bitmap, val pageWidthPts: Float, val pageHeightPts: Float)

    fun renderPages(
        context: Context,
        storage: FileStorageManager,
        uri: Uri,
        dpi: Int,
        maxPages: Int = Int.MAX_VALUE,
        onPage: (index: Int, page: Rendered, totalPages: Int) -> Boolean
    ): Int {
        val temp = storage.copyUriToTemp(uri, "pdfrender", "pdf")
        try {
            ParcelFileDescriptor.open(temp, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    val total = minOf(renderer.pageCount, maxPages)
                    for (index in 0 until total) {
                        val page = renderer.openPage(index)
                        val scale = dpi.coerceIn(72, 300) / 72f
                        val width = (page.width * scale).roundToInt().coerceAtLeast(1)
                        val height = (page.height * scale).roundToInt().coerceAtLeast(1)
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                        val rendered = Rendered(bitmap, page.width.toFloat(), page.height.toFloat())
                        page.close()
                        val keepGoing = onPage(index, rendered, total)
                        if (!keepGoing) {
                            bitmap.recycle()
                            return index + 1
                        }
                    }
                    return total
                }
            }
        } finally {
            temp.delete()
        }
    }

    fun bitmap(context: Context, storage: FileStorageManager, uri: Uri, dpi: Int, pageIndex: Int = 0): Bitmap? {
        val temp = storage.copyUriToTemp(uri, "pdfpage", "pdf")
        try {
            ParcelFileDescriptor.open(temp, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    if (pageIndex !in 0 until renderer.pageCount) return null
                    val page = renderer.openPage(pageIndex)
                    val scale = dpi.coerceIn(72, 300) / 72f
                    val width = (page.width * scale).roundToInt().coerceAtLeast(1)
                    val height = (page.height * scale).roundToInt().coerceAtLeast(1)
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                    page.close()
                    return bitmap
                }
            }
        } finally {
            temp.delete()
        }
    }

    fun pageCount(context: Context, storage: FileStorageManager, uri: Uri): Int {
        val temp = storage.copyUriToTemp(uri, "pdfcount", "pdf")
        try {
            ParcelFileDescriptor.open(temp, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { renderer -> return renderer.pageCount }
            }
        } finally {
            temp.delete()
        }
    }
}
