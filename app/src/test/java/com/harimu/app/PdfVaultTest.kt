package com.harimu.app

import com.harimu.app.core.pdf.PdfVaultEngine
import com.harimu.app.feature.tools.pdfVaultToolIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tes murni-Kotlin untuk bagian PDF Vault yang tidak butuh Android/PDFBox:
 * parser rentang halaman, formatter, dan kelengkapan 21 tool katalog.
 *
 * Operasi I/O PDF (enkripsi, redaksi, OCR, konversi Office) tidak bisa diuji di sini —
 * toolchain Android dihapus pada lingkungan ini, jadi tidak ada klaim build/test lulus.
 */
class PdfVaultTest {

    @Test
    fun parsePageRangeHandlesRangesAndSingles() {
        assertEquals(listOf(1, 2, 3, 5), PdfVaultEngine.parsePageRange("1-3,5", 10))
        assertEquals(listOf(1, 2, 3, 4), PdfVaultEngine.parsePageRange("1-", 4))
        assertEquals(listOf(8, 9, 10), PdfVaultEngine.parsePageRange("8-", 10))
        assertEquals((1..10).toList(), PdfVaultEngine.parsePageRange("-9,10", 10))
    }

    @Test
    fun parsePageRangeClampsAndIgnoresJunk() {
        assertEquals(listOf(10), PdfVaultEngine.parsePageRange("10-50", 10))
        assertEquals(listOf(1, 2), PdfVaultEngine.parsePageRange("abc,1,,2", 10))
        assertTrue(PdfVaultEngine.parsePageRange("", 10).isEmpty())
        assertEquals(listOf(1, 2, 3), PdfVaultEngine.parsePageRange("0,-3", 10))
        assertTrue(PdfVaultEngine.parsePageRange("1-3", 0).isEmpty())
    }

    @Test
    fun parsePageRangeDeduplicatesInOrder() {
        assertEquals(listOf(2, 3, 4), PdfVaultEngine.parsePageRange("2-3,3,4,2", 9))
    }

    @Test
    fun formatPageRangeCompressesConsecutivePages() {
        assertEquals("1-3,5", PdfVaultEngine.formatPageRange(listOf(3, 1, 2, 5)))
        assertEquals("2", PdfVaultEngine.formatPageRange(listOf(2)))
        assertEquals("", PdfVaultEngine.formatPageRange(emptyList()))
        assertEquals("1-5", PdfVaultEngine.formatPageRange(listOf(5, 4, 3, 2, 1)))
    }

    @Test
    fun formatAndParseRoundTrip() {
        val pages = listOf(1, 2, 3, 7, 9, 10, 11)
        val expression = PdfVaultEngine.formatPageRange(pages)
        assertEquals("1-3,7,9-11", expression)
        assertEquals(pages, PdfVaultEngine.parsePageRange(expression, 11))
    }

    @Test
    fun parseLooseAcceptsExpressionsWithoutKnownPageCount() {
        assertEquals(listOf(2, 3, 4), PdfVaultEngine.parsePagesLoose("2-4"))
    }

    @Test
    fun parseOrderDropsInvalidPages() {
        assertEquals(listOf(3, 1, 2), PdfVaultEngine.parseOrder("3,1,2", 3))
        assertEquals(listOf(1, 2), PdfVaultEngine.parseOrder("1,9,2", 3))
        assertTrue(PdfVaultEngine.parseOrder("x,0,-1", 3).isEmpty())
    }

    @Test
    fun pdfVaultExposesExactlyCatalogIds() {
        val ids = pdfVaultToolIds()
        assertEquals(21, ids.size)
        assertEquals(21, ids.toSet().size)
        (1..21).forEach { number ->
            val id = "PDF-%02d".format(number)
            assertTrue("Missing $id", ids.contains(id))
        }
    }
}
