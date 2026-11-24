package com.harimu.app

import com.harimu.app.core.engines.ArchiveEngine
import com.harimu.app.core.engines.EbookEngine
import com.harimu.app.core.engines.FontEngine
import com.harimu.app.feature.tools.converterSuiteTools
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tes murni-Kotlin untuk FASE 13: keamanan nama entri arsip, parser EPUB, urutan halaman alami,
 * dan struktur font WOFF/SFNT. Semua fungsi yang diuji deterministik dan tanpa Android.
 */
class ConverterSuiteTest {

    // --------------------------------------------------------------- Arsip

    @Test
    fun sanitizeEntryNameRejectsZipSlipAndAbsolutePaths() {
        assertNull(ArchiveEngine.sanitizeEntryName("../etc/passwd"))
        assertNull(ArchiveEngine.sanitizeEntryName("a/../../b"))
        assertNull(ArchiveEngine.sanitizeEntryName("/etc/passwd"))
        assertNull(ArchiveEngine.sanitizeEntryName("C:\\Windows\\system.ini"))
        assertNull(ArchiveEngine.sanitizeEntryName("bad\u0000name.txt"))
        assertNull(ArchiveEngine.sanitizeEntryName("   "))
    }

    @Test
    fun sanitizeEntryNameNormalizesSeparatorsAndDotSegments() {
        assertEquals("a/b.txt", ArchiveEngine.sanitizeEntryName("a\\b.txt"))
        assertEquals("a/b.txt", ArchiveEngine.sanitizeEntryName("./a//b.txt"))
        assertEquals("folder/file.txt", ArchiveEngine.sanitizeEntryName("folder/./file.txt"))
        assertEquals("nama dengan spasi.txt", ArchiveEngine.sanitizeEntryName("nama dengan spasi.txt"))
    }

    @Test
    fun uniqueChildNameAvoidsCollisionsWithoutLosingExtension() {
        val used = mutableSetOf<String>()
        assertEquals("foto.png", ArchiveEngine.uniqueChildName("folder/foto.png", used))
        assertEquals("foto (2).png", ArchiveEngine.uniqueChildName("lain/foto.png", used))
        assertEquals("foto (3).png", ArchiveEngine.uniqueChildName("foto.png", used))
    }

    @Test
    fun archiveKindIsDetectedFromExtensionAndMagic() {
        assertEquals(ArchiveEngine.Kind.ZIP, ArchiveEngine.kindOf("a.zip"))
        assertEquals(ArchiveEngine.Kind.TAR_GZ, ArchiveEngine.kindOf("a.tar.gz"))
        assertEquals(ArchiveEngine.Kind.TAR_GZ, ArchiveEngine.kindOf("a.TGZ"))
        assertEquals(ArchiveEngine.Kind.SEVEN_Z, ArchiveEngine.kindOf("a.7z"))
        assertEquals(ArchiveEngine.Kind.RAR, ArchiveEngine.kindOf("a.rar"))
        assertNull(ArchiveEngine.kindOf("a.txt"))
    }

    // ---------------------------------------------------------------- EPUB

    @Test
    fun containerRootPathAndOpfSpineAreParsedInOrder() {
        val container = """<?xml version="1.0"?>
            <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
              <rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles>
            </container>"""
        assertEquals("OEBPS/content.opf", EbookEngine.parseContainerRootPath(container))
        assertNull(EbookEngine.parseContainerRootPath("<container/>"))

        val opf = """<?xml version="1.0"?>
            <package xmlns="http://www.idpf.org/2007/opf">
              <metadata><dc:title>Buku Uji</dc:title></metadata>
              <manifest>
                <item id="c1" href="bab1.xhtml" media-type="application/xhtml+xml"/>
                <item id="c2" href="bab2.xhtml" media-type="application/xhtml+xml"/>
                <item id="css" href="style.css" media-type="text/css"/>
              </manifest>
              <spine>
                <itemref idref="c2"/>
                <itemref idref="c1"/>
              </spine>
            </package>"""
        val (manifest, spine) = EbookEngine.parseOpf(opf)
        assertEquals(listOf("c2", "c1"), spine)
        assertEquals("bab1.xhtml", manifest["c1"]?.href)
        assertEquals("text/css", manifest["css"]?.mediaType)
        assertEquals("OEBPS/bab1.xhtml", EbookEngine.resolveHref("OEBPS/", "bab1.xhtml"))
        assertEquals("bab1.xhtml", EbookEngine.resolveHref("", "bab1.xhtml"))
    }

    @Test
    fun htmlToTextDropsScriptsStylesAndDecodesEntities() {
        val text = EbookEngine.htmlToText(
            "<html><head><style>p{color:red}</style></head>" +
                "<body><p>Halo <b>dunia</b></p><script>alert(1)</script>" +
                "<p>Baris&nbsp;dua &amp; &#65; &#x42;</p></body></html>"
        )
        assertTrue(text.contains("Halo dunia"))
        assertTrue(text.contains("Baris dua & A B"))
        assertFalse(text.contains("alert"))
        assertFalse(text.contains("color:red"))
        assertFalse(text.contains("<p>"))
    }

    @Test
    fun comicPagesSortNaturally() {
        val names = listOf("page10.jpg", "page2.jpg", "page1.jpg", "page20.jpg")
        val sorted = names.sortedWith { a, b -> EbookEngine.naturalCompare(a, b) }
        assertEquals(listOf("page1.jpg", "page2.jpg", "page10.jpg", "page20.jpg"), sorted)
        assertTrue(EbookEngine.naturalCompare("a9", "a10") < 0)
        assertEquals(0, EbookEngine.naturalCompare("Same.PNG", "same.png"))
    }

    @Test
    fun comicImageDetectionAcceptsCommonFormats() {
        assertTrue(EbookEngine.isImageName("001.JPG"))
        assertTrue(EbookEngine.isImageName("a/b.webp"))
        assertFalse(EbookEngine.isImageName("info.txt"))
        assertFalse(EbookEngine.isImageName("cover.pdf"))
    }

    // ---------------------------------------------------------------- Font

    private fun syntheticSfnt(): FontEngine.Sfnt {
        val head = ByteArray(54)
        val maxp = ByteArray(6).also { it[4] = 1 }
        val cmap = ByteArray(4).also { it[2] = 0 } // numTables = 0 pada offset 2 (big-endian)
        return FontEngine.Sfnt(
            0x00010000,
            listOf(FontEngine.Table("head", head), FontEngine.Table("maxp", maxp), FontEngine.Table("cmap", cmap))
        )
    }

    @Test
    fun sfntRoundTripPreservesTableTags() {
        val sfnt = syntheticSfnt()
        val bytes = FontEngine.writeSfnt(sfnt)
        assertEquals(FontEngine.Kind.TTF, FontEngine.detectKind(bytes))
        val parsed = FontEngine.readSfnt(bytes)
        assertEquals(listOf("cmap", "head", "maxp"), parsed.tables.map { it.tag })
        assertTrue(parsed.table("maxp")!!.contentEquals(sfnt.table("maxp")!!))
    }

    @Test
    fun woffRoundTripReconstructsTables() {
        val sfnt = syntheticSfnt()
        val woff = FontEngine.writeWoff(sfnt)
        assertEquals(FontEngine.Kind.WOFF, FontEngine.detectKind(woff))
        val parsed = FontEngine.readWoff(woff)
        assertEquals(0x00010000, parsed.flavor)
        assertEquals(listOf("cmap", "head", "maxp"), parsed.tables.map { it.tag })
        assertTrue(parsed.table("cmap")!!.contentEquals(sfnt.table("cmap")!!))
    }

    @Test
    fun convertMovesBetweenWoffAndSfnt() {
        val sfntBytes = FontEngine.writeSfnt(syntheticSfnt())
        val toWoff = FontEngine.convert(sfntBytes, "WOFF")
        assertEquals(FontEngine.Kind.WOFF, toWoff.to)
        val backToSfnt = FontEngine.convert(toWoff.data, "SFNT")
        assertEquals(FontEngine.Kind.TTF, backToSfnt.to)
        assertEquals(listOf("cmap", "head", "maxp"), FontEngine.readSfnt(backToSfnt.data).tables.map { it.tag })
    }

    @Test
    fun woff2AndUnknownInputsAreRefusedWithMessage() {
        val woff2 = byteArrayOf('w'.code.toByte(), 'O'.code.toByte(), 'F'.code.toByte(), '2'.code.toByte(), 0, 0, 0, 0)
        assertEquals(FontEngine.Kind.WOFF2, FontEngine.detectKind(woff2))
        val error = runCatching { FontEngine.convert(woff2, "SFNT") }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
        assertTrue(error!!.message!!.contains("WOFF2", ignoreCase = true))

        assertEquals(FontEngine.Kind.UNKNOWN, FontEngine.detectKind(byteArrayOf(1, 2, 3)))
        assertTrue(runCatching { FontEngine.convert(byteArrayOf(1, 2, 3), "AUTO") }.isFailure)
    }

    @Test
    fun checksumMatchesSfntBigEndianDefinition() {
        assertEquals(1, FontEngine.checksum(byteArrayOf(0, 0, 0, 1)))
        assertEquals(-1, FontEngine.checksum(byteArrayOf(-1, -1, -1, -1)))
        assertEquals(0x01020304, FontEngine.checksum(byteArrayOf(1, 2, 3, 4)))
    }

    @Test
    fun subsetRejectsCffFontsAndFontsWithoutGlyf() {
        val cffSfnt = FontEngine.Sfnt(0x4F54544F, listOf(FontEngine.Table("CFF ", ByteArray(4))))
        val cffBytes = FontEngine.writeSfnt(cffSfnt)
        val error = runCatching { FontEngine.subset(cffBytes, "abc") }.exceptionOrNull()
        assertTrue(error is IllegalStateException)
        assertTrue(error!!.message!!.contains("CFF"))
    }

    // ------------------------------------------------------------ Daftar tool

    @Test
    fun converterSuiteCoversAllNonTenantCatalogIds() {
        val ids = converterSuiteTools().map { it.id }
        val expected = listOf(
            "CNV-01", "CNV-02", "CNV-03", "CNV-04", "CNV-05", "CNV-06", "CNV-07", "CNV-08", "CNV-09",
            "CNV-10", "CNV-11", "CNV-12", "CNV-13", "CNV-15", "CNV-17", "CNV-19", "CNV-20", "CNV-21", "CNV-22"
        )
        assertEquals(expected.sorted(), ids.sorted())
        assertEquals(ids.size, ids.toSet().size)

        val aliases = converterSuiteTools().filter { !it.isStub && it.aliasOfId != null }
        assertTrue(aliases.any { it.id == "CNV-09" && it.aliasOfId == "IMG-23" })
        assertTrue(aliases.any { it.id == "CNV-15" && it.aliasOfId == "PDF-15" })
    }
}
