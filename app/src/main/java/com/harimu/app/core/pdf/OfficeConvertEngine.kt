package com.harimu.app.core.pdf

import android.graphics.Bitmap
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.util.Matrix
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Konversi Office **versi dasar on-device** (FASE 12).
 *
 * Batas yang dinyatakan jujur:
 * - PDF → Word/Excel/PowerPoint hanya memindahkan **teks** (dan gambar halaman untuk PowerPoint).
 *   Tata letak asli, tabel kompleks, kolom, dan gaya tidak dipertahankan.
 * - Word → PDF hanya membaca paragraf teks dari `word/document.xml`; gambar, tabel bergaya,
 *   header/footer, dan numbering tidak ikut.
 * Antarmuka kelas ini sengaja dibuat sederhana agar bisa diganti implementasi cloud
 * (LibreOffice/OnlyOffice) tanpa mengubah tool.
 */
object OfficeConvertEngine {

    // --------------------------------------------------------------- ZIP util

    private fun writeZip(dest: File, entries: List<Pair<String, ByteArray>>) {
        ZipOutputStream(dest.outputStream().buffered()).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
    }

    fun escapeXml(value: String): String = buildString(value.length + 16) {
        value.forEach { ch ->
            when (ch) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&apos;")
                '\n', '\r', '\t' -> append(' ')
                else -> if (ch.code < 32) append(' ') else append(ch)
            }
        }
    }

    fun unescapeXml(value: String): String = value
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&amp;", "&")

    // ------------------------------------------------------------ PDF → DOCX

    fun pdfToDocx(pageTexts: List<String>, dest: File): Int {
        val paragraphs = StringBuilder()
        pageTexts.forEachIndexed { index, page ->
            page.lines().forEach { line ->
                paragraphs.append("<w:p><w:r><w:t xml:space=\"preserve\">")
                    .append(escapeXml(line))
                    .append("</w:t></w:r></w:p>")
            }
            if (index < pageTexts.lastIndex) {
                paragraphs.append("<w:p><w:r><w:br w:type=\"page\"/></w:r></w:p>")
            }
        }
        val document = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>$paragraphs
<w:sectPr><w:pgSz w:w="11906" w:h="16838"/><w:pgMar w:top="1134" w:right="1134" w:bottom="1134" w:left="1134"/></w:sectPr></w:body></w:document>"""
        val contentTypes = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/></Types>"""
        val rels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>"""
        writeZip(
            dest,
            listOf(
                "[Content_Types].xml" to contentTypes.toByteArray(),
                "_rels/.rels" to rels.toByteArray(),
                "word/document.xml" to document.toByteArray()
            )
        )
        return pageTexts.size
    }

    // ------------------------------------------------------------ PDF → XLSX

    fun pdfToXlsx(pageTexts: List<String>, dest: File): Int {
        val rows = StringBuilder()
        var rowIndex = 1
        var sheetRows = 0
        pageTexts.forEachIndexed { pageIdx, page ->
            if (pageIdx > 0) {
                rows.append("<row r=\"$rowIndex\"><c r=\"A$rowIndex\" t=\"inlineStr\"><is><t>")
                    .append(escapeXml("--- Halaman ${pageIdx + 1} ---"))
                    .append("</t></is></c></row>")
                rowIndex += 1
                sheetRows += 1
            }
            page.lines().filter { it.isNotBlank() }.forEach { line ->
                val cells = if (line.contains(Regex("\\s{2,}"))) {
                    line.split(Regex("\\s{2,}")).filter { it.isNotBlank() }
                } else {
                    listOf(line.trim())
                }
                rows.append("<row r=\"$rowIndex\">")
                cells.forEachIndexed { cellIndex, cell ->
                    val ref = "${'A' + (cellIndex.coerceAtMost(25))}$rowIndex"
                    rows.append("<c r=\"$ref\" t=\"inlineStr\"><is><t>")
                        .append(escapeXml(cell.trim()))
                        .append("</t></is></c>")
                }
                rows.append("</row>")
                rowIndex += 1
                sheetRows += 1
            }
        }
        val sheet = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>$rows</sheetData></worksheet>"""
        val workbook = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Hasil PDF" sheetId="1" r:id="rId1"/></sheets></workbook>"""
        val workbookRels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/></Relationships>"""
        val contentTypes = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/></Types>"""
        val rels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>"""
        writeZip(
            dest,
            listOf(
                "[Content_Types].xml" to contentTypes.toByteArray(),
                "_rels/.rels" to rels.toByteArray(),
                "xl/workbook.xml" to workbook.toByteArray(),
                "xl/_rels/workbook.xml.rels" to workbookRels.toByteArray(),
                "xl/worksheets/sheet1.xml" to sheet.toByteArray()
            )
        )
        return sheetRows
    }

    // ------------------------------------------------------------ PDF → PPTX

    private const val SLIDE_CX = 9144000L
    private const val SLIDE_CY = 6858000L

    fun pdfToPptx(bitmaps: List<Bitmap>, dest: File): Int {
        val entries = mutableListOf<Pair<String, ByteArray>>()
        val slideOverrides = StringBuilder()
        val presentationSlides = StringBuilder()
        val presentationRels = StringBuilder(
            """<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideMaster" Target="slideMasters/slideMaster1.xml"/>""" +
                """<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/theme" Target="theme/theme1.xml"/>"""
        )
        bitmaps.forEachIndexed { index, bitmap ->
            val slideNumber = index + 1
            val relId = "rId${index + 3}"
            val mediaName = "image${slideNumber}.jpeg"
            val buffer = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, buffer)
            entries.add("ppt/media/$mediaName" to buffer.toByteArray())
            slideOverrides.append(
                """<Override PartName="/ppt/slides/slide$slideNumber.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slide+xml"/>"""
            )
            presentationSlides.append("""<p:sldId id="${255 + slideNumber}" r:id="$relId"/>""")
            presentationRels.append(
                """<Relationship Id="$relId" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide" Target="slides/slide$slideNumber.xml"/>"""
            )
            // Skala gambar agar muat slide, jaga rasio.
            val scale = minOf(SLIDE_CX.toDouble() / bitmap.width, SLIDE_CY.toDouble() / bitmap.height)
            val extCx = (bitmap.width * scale).toLong()
            val extCy = (bitmap.height * scale).toLong()
            val offX = (SLIDE_CX - extCx) / 2
            val offY = (SLIDE_CY - extCy) / 2
            val slide = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<p:sld xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"><p:cSld><p:spTree><p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr><p:grpSpPr/><p:pic><p:nvPicPr><p:cNvPr id="2" name="Halaman $slideNumber"/><p:cNvPicPr/><p:nvPr/></p:nvPicPr><p:blipFill><a:blip r:embed="rId1"/><a:stretch><a:fillRect/></a:stretch></p:blipFill><p:spPr><a:xfrm><a:off x="$offX" y="$offY"/><a:ext cx="$extCx" cy="$extCy"/></a:xfrm><a:prstGeom prst="rect"><a:avLst/></a:prstGeom></p:spPr></p:pic></p:spTree></p:cSld><p:clrMapOvr><a:masterClrMapping/></p:clrMapOvr></p:sld>"""
            entries.add("ppt/slides/slide$slideNumber.xml" to slide.toByteArray())
            val slideRels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="../media/$mediaName"/><Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideLayout" Target="../slideLayouts/slideLayout1.xml"/></Relationships>"""
            entries.add("ppt/slides/_rels/slide$slideNumber.xml.rels" to slideRels.toByteArray())
        }

        val presentation = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<p:presentation xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"><p:sldMasterIdLst><p:sldMasterId id="2147483648" r:id="rId1"/></p:sldMasterIdLst><p:sldIdLst>$presentationSlides</p:sldIdLst><p:sldSz cx="$SLIDE_CX" cy="$SLIDE_CY"/><p:notesSz cx="6858000" cy="9144000"/></p:presentation>"""
        val presentationRelsXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">$presentationRels</Relationships>"""
        val master = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<p:sldMaster xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"><p:cSld><p:spTree><p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr><p:grpSpPr/></p:spTree></p:cSld><p:clrMap bg1="lt1" tx1="dk1" bg2="lt2" tx2="dk2" accent1="accent1" accent2="accent2" accent3="accent3" accent4="accent4" accent5="accent5" accent6="accent6" hlink="hlink" folHlink="folHlink"/><p:sldLayoutIdLst><p:sldLayoutId id="2147483649" r:id="rId1"/></p:sldLayoutIdLst><p:txStyles><p:titleStyle/><p:bodyStyle/><p:otherStyle/></p:txStyles></p:sldMaster>"""
        val masterRels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideLayout" Target="../slideLayouts/slideLayout1.xml"/><Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/theme" Target="../theme/theme1.xml"/></Relationships>"""
        val layout = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<p:sldLayout xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main" type="blank" preserve="1"><p:cSld name="Blank"><p:spTree><p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr><p:grpSpPr/></p:spTree></p:cSld><p:clrMapOvr><a:masterClrMapping/></p:clrMapOvr></p:sldLayout>"""
        val layoutRels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideMaster" Target="../slideMasters/slideMaster1.xml"/></Relationships>"""
        val theme = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<a:theme xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" name="Office"><a:themeElements><a:clrScheme name="Office"><a:dk1><a:sysClr val="windowText" lastClr="000000"/></a:dk1><a:lt1><a:sysClr val="window" lastClr="FFFFFF"/></a:lt1><a:dk2><a:srgbClr val="44546A"/></a:dk2><a:lt2><a:srgbClr val="E7E6E6"/></a:lt2><a:accent1><a:srgbClr val="4472C4"/></a:accent1><a:accent2><a:srgbClr val="ED7D31"/></a:accent2><a:accent3><a:srgbClr val="A5A5A5"/></a:accent3><a:accent4><a:srgbClr val="FFC000"/></a:accent4><a:accent5><a:srgbClr val="5B9BD5"/></a:accent5><a:accent6><a:srgbClr val="70AD47"/></a:accent6><a:hlink><a:srgbClr val="0563C1"/></a:hlink><a:folHlink><a:srgbClr val="954F72"/></a:folHlink></a:clrScheme><a:fontScheme name="Office"><a:majorFont><a:latin typeface="Calibri Light"/><a:ea typeface=""/><a:cs typeface=""/></a:majorFont><a:minorFont><a:latin typeface="Calibri"/><a:ea typeface=""/><a:cs typeface=""/></a:minorFont></a:fontScheme><a:fmtScheme name="Office"><a:fillStyleLst><a:solidFill><a:schemeClr val="phClr"/></a:solidFill><a:solidFill><a:schemeClr val="phClr"/></a:solidFill><a:solidFill><a:schemeClr val="phClr"/></a:solidFill></a:fillStyleLst><a:lnStyleLst><a:ln w="6350"><a:solidFill><a:schemeClr val="phClr"/></a:solidFill></a:ln><a:ln w="12700"><a:solidFill><a:schemeClr val="phClr"/></a:solidFill></a:ln><a:ln w="19050"><a:solidFill><a:schemeClr val="phClr"/></a:solidFill></a:ln></a:lnStyleLst><a:effectStyleLst><a:effectStyle><a:effectLst/></a:effectStyle><a:effectStyle><a:effectLst/></a:effectStyle><a:effectStyle><a:effectLst/></a:effectStyle></a:effectStyleLst><a:bgFillStyleLst><a:solidFill><a:schemeClr val="phClr"/></a:solidFill><a:solidFill><a:schemeClr val="phClr"/></a:solidFill><a:solidFill><a:schemeClr val="phClr"/></a:solidFill></a:bgFillStyleLst></a:fmtScheme></a:themeElements></a:theme>"""
        val contentTypes = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Default Extension="jpeg" ContentType="image/jpeg"/><Override PartName="/ppt/presentation.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml"/><Override PartName="/ppt/slideMasters/slideMaster1.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slideMaster+xml"/><Override PartName="/ppt/slideLayouts/slideLayout1.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slideLayout+xml"/><Override PartName="/ppt/theme/theme1.xml" ContentType="application/vnd.openxmlformats-officedocument.theme+xml"/>$slideOverrides</Types>"""
        val rels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="ppt/presentation.xml"/></Relationships>"""
        entries.add(0, "[Content_Types].xml" to contentTypes.toByteArray())
        entries.add(1, "_rels/.rels" to rels.toByteArray())
        entries.add(2, "ppt/presentation.xml" to presentation.toByteArray())
        entries.add(3, "ppt/_rels/presentation.xml.rels" to presentationRelsXml.toByteArray())
        entries.add(4, "ppt/slideMasters/slideMaster1.xml" to master.toByteArray())
        entries.add(5, "ppt/slideMasters/_rels/slideMaster1.xml.rels" to masterRels.toByteArray())
        entries.add(6, "ppt/slideLayouts/slideLayout1.xml" to layout.toByteArray())
        entries.add(7, "ppt/slideLayouts/_rels/slideLayout1.xml.rels" to layoutRels.toByteArray())
        entries.add(8, "ppt/theme/theme1.xml" to theme.toByteArray())
        writeZip(dest, entries)
        return bitmaps.size
    }

    // ------------------------------------------------------------ DOCX → PDF

    fun readDocxParagraphs(file: File): List<String> {
        val xml = ZipFile(file).use { zip ->
            val entry = zip.getEntry("word/document.xml") ?: error("Bukan .docx yang valid (document.xml tidak ada)")
            zip.getInputStream(entry).bufferedReader().use { it.readText() }
        }
        val paragraphs = mutableListOf<String>()
        Regex("<w:p[ >].*?</w:p>", RegexOption.DOT_MATCHES_ALL).findAll(xml).forEach { match ->
            val text = Regex("<w:t[^>]*>(.*?)</w:t>", RegexOption.DOT_MATCHES_ALL)
                .findAll(match.value)
                .joinToString("") { unescapeXml(it.groupValues[1]) }
            if (text.isNotBlank()) paragraphs.add(text.trim())
        }
        return paragraphs
    }

    fun docxToPdf(file: File, dest: File): Int {
        val paragraphs = readDocxParagraphs(file)
        require(paragraphs.isNotEmpty()) { "Dokumen Word tidak berisi teks yang bisa dikonversi" }
        val font = PDType1Font.HELVETICA
        val fontSize = 11f
        val leading = 15f
        val margin = 56f
        PDDocument().use { document ->
            val pageWidth = PDRectangle.A4.width
            val pageHeight = PDRectangle.A4.height
            val maxWidth = pageWidth - margin * 2
            var page = PDPage(PDRectangle.A4)
            document.addPage(page)
            var content = PDPageContentStream(document, page)
            var y = pageHeight - margin
            try {
                content.beginText()
                content.setFont(font, fontSize)
                content.setNonStrokingColor(0.1f, 0.1f, 0.1f)
                paragraphs.forEach { paragraph ->
                    wrapText(paragraph, font, fontSize, maxWidth).forEach { line ->
                        if (y < margin + leading) {
                            content.endText()
                            content.close()
                            page = PDPage(PDRectangle.A4)
                            document.addPage(page)
                            content = PDPageContentStream(document, page)
                            content.beginText()
                            content.setFont(font, fontSize)
                            content.setNonStrokingColor(0.1f, 0.1f, 0.1f)
                            y = pageHeight - margin
                        }
                        content.setTextMatrix(Matrix.getTranslateInstance(margin, y))
                        content.showText(line)
                        y -= leading
                    }
                    y -= leading * 0.4f
                }
                content.endText()
            } finally {
                content.close()
            }
            document.save(dest)
            return document.numberOfPages
        }
    }

    /** Pembungkus teks sederhana berbasis metrik font. Murni & deterministik (diuji unit). */
    fun wrapText(text: String, font: PDType1Font, fontSize: Float, maxWidth: Float): List<String> {
        val words = text.split(' ').filter { it.isNotEmpty() }
        if (words.isEmpty()) return emptyList()
        val lines = mutableListOf<String>()
        val current = StringBuilder()
        fun widthOf(value: String): Float = runCatching {
            font.getStringWidth(value) / 1000f * fontSize
        }.getOrDefault(value.length * fontSize * 0.5f)
        words.forEach { word ->
            val candidate = if (current.isEmpty()) word else "${current} $word"
            if (widthOf(candidate) <= maxWidth || current.isEmpty()) {
                current.clear()
                current.append(candidate)
            } else {
                lines.add(current.toString())
                current.clear()
                current.append(word)
            }
        }
        if (current.isNotEmpty()) lines.add(current.toString())
        return lines.map { it.take(3000) }
    }
}
