package com.harimu.app

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.harimu.app.core.pdf.PdfVaultEngine
import com.harimu.app.core.toolapi.ToolInput
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.zip.ZipFile

/**
 * PDF Vault: merge (PDF-01), split (PDF-02), encrypt (PDF-10) → decrypt (PDF-11), redact (PDF-14).
 *
 * PDF dasar dibuat dari `sample_image.png` lewat `PdfVaultEngine.imagesToPdf`, sehingga tes tidak
 * membutuhkan aset PDF tambahan.
 */
@RunWith(AndroidJUnit4::class)
class PdfVaultInstrumentedTest {

    private val support = InstrumentedTestSupport

    @Before
    fun setUp() {
        support.clearTemp()
    }

    private fun basePdf(pageCount: Int): Uri {
        val dest = File(support.context.cacheDir, "samples/base_${pageCount}hal_${System.currentTimeMillis()}.pdf")
        dest.parentFile?.mkdirs()
        val image = support.sampleUri("sample_image.png")
        val pages = PdfVaultEngine.imagesToPdf(
            context = support.context,
            imageUris = List(pageCount) { image },
            dest = dest,
            pageSizeMode = "Fit"
        )
        assertEquals("PDF dasar harus punya $pageCount halaman", pageCount, pages)
        return Uri.fromFile(dest)
    }

    @Test
    fun mergeCombinesPageCounts() = runBlocking {
        val onePage = basePdf(1)
        val result = support.runTool("PDF-01", ToolInput(uris = listOf(onePage, onePage)))
        val out = support.singleOutput(result)

        assertEquals("application/pdf", result.outputs.first().mimeType)
        assertEquals(2, PdfVaultEngine.pageCount(support.context, support.storage, Uri.fromFile(out)))
    }

    @Test
    fun splitWritesOnePdfPerPageIntoZip() = runBlocking {
        val twoPages = basePdf(2)
        val result = support.runTool(
            "PDF-02",
            ToolInput(uris = listOf(twoPages), params = mapOf("perSplit" to 1f))
        )
        val zip = support.singleOutput(result)

        ZipFile(zip).use { archive ->
            val pdfEntries = archive.entries().toList().filter { !it.isDirectory }
            assertEquals("harus ada 2 PDF di ZIP", 2, pdfEntries.size)
            assertTrue("semua entri harus .pdf", pdfEntries.all { it.name.endsWith(".pdf") })
        }
    }

    @Test
    fun encryptMarksPdfEncryptedAndDecryptRestoresIt() = runBlocking {
        val twoPages = basePdf(2)
        val password = "ujiRahasia123"

        val encrypted = support.runTool(
            "PDF-10",
            ToolInput(
                uris = listOf(twoPages),
                params = mapOf("userPwd" to password, "ownerPwd" to password, "key" to "AES-256")
            )
        )
        val encryptedFile = support.singleOutput(encrypted)
        assertTrue(
            "PDF hasil PDF-10 harus terenkripsi",
            PdfVaultEngine.isEncrypted(support.context, support.storage, Uri.fromFile(encryptedFile))
        )

        val decrypted = support.runTool(
            "PDF-11",
            ToolInput(uris = listOf(Uri.fromFile(encryptedFile)), params = mapOf("password" to password))
        )
        val decryptedFile = support.singleOutput(decrypted)
        assertFalse(
            "PDF hasil PDF-11 harus tanpa enkripsi",
            PdfVaultEngine.isEncrypted(support.context, support.storage, Uri.fromFile(decryptedFile))
        )
        assertEquals(2, PdfVaultEngine.pageCount(support.context, support.storage, Uri.fromFile(decryptedFile)))
    }

    @Test
    fun redactRemovesSearchableTextPermanently() = runBlocking {
        val onePage = basePdf(1)
        val watermarked = File(support.context.cacheDir, "samples/watermark_${System.currentTimeMillis()}.pdf")
        val pages = PdfVaultEngine.addTextWatermark(
            context = support.context,
            storage = support.storage,
            uri = onePage,
            dest = watermarked,
            text = "RAHASIA",
            opacityPct = 30,
            angleDeg = 0,
            sizePct = 12
        )
        assertEquals(1, pages)
        assertTrue(
            "prasyarat: teks RAHASIA harus bisa diekstrak sebelum redaksi",
            PdfVaultEngine.extractText(watermarked).contains("RAHASIA")
        )

        val result = support.runTool(
            "PDF-14",
            ToolInput(
                uris = listOf(Uri.fromFile(watermarked)),
                params = mapOf("terms" to "RAHASIA", "dpi" to 150f)
            )
        )
        val redacted = support.singleOutput(result)
        val remaining = PdfVaultEngine.extractText(redacted)

        assertFalse("teks RAHASIA masih bisa diekstrak: $remaining", remaining.contains("RAHASIA", ignoreCase = true))
        assertTrue(
            "ringkasan harus menyatakan verifikasi ekstraksi LULUS, dapat: ${result.summary}",
            result.summary.contains("LULUS")
        )
    }
}
