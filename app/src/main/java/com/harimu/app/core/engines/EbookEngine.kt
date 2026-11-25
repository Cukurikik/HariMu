package com.harimu.app.core.engines

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.github.junrar.Archive
import com.harimu.app.core.pdf.OfficeConvertEngine
import com.harimu.app.core.pdf.PdfVaultEngine
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.util.Matrix
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Konversi e-book (FASE 13): EPUB dan arsip komik.
 *
 * Batas jujur:
 * - EPUB dibaca sebagai **teks**: teks dari dokumen XHTML diurutkan sesuai `spine`. Gambar, CSS,
 *   kolom, catatan kaki, dan DRM (Adobe/Apple) tidak didukung; EPUB ber-DRM gagal dengan pesan jelas.
 * - EPUB → PDF merender teks dengan Helvetica (bukan font asli buku).
 * - EPUB keluaran untuk komik dibuat minimal (EPUB 3 tanpa CSS/nav interaktif).
 * - CBR dibaca lewat junrar (hanya baca); CBZ lewat `java.util.zip`.
 */
object EbookEngine {

    data class ManifestItem(val id: String, val href: String, val mediaType: String)

    data class Epub(val title: String, val author: String, val chapters: List<String>, val spine: List<String>)

    // ---------------------------------------------------------------- EPUB baca

    fun readEpub(context: Context, uri: Uri): Epub {
        val temp = File.createTempFile("epub", ".epub", context.cacheDir)
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(temp).use { out -> input.copyTo(out, 32 * 1024) }
            } ?: error("Gagal membaca berkas EPUB")

            ZipFile(temp).use { zip ->
                val container = zip.getEntry("META-INF/container.xml")
                    ?: error("Berkas bukan EPUB valid: META-INF/container.xml tidak ada")
                val containerXml = zip.getInputStream(container).use { it.readBytes().toString(Charsets.UTF_8) }
                val opfPath = parseContainerRootPath(containerXml)
                    ?: error("EPUB tidak menyebutkan paket OPF di container.xml")
                val opfEntry = zip.getEntry(opfPath) ?: zip.getEntry(decodePercent(opfPath))
                    ?: error("Paket OPF tidak ditemukan: $opfPath")
                val opfXml = zip.getInputStream(opfEntry).use { it.readBytes().toString(Charsets.UTF_8) }
                val opfDir = opfPath.substringBeforeLast('/', "")

                val parsed = parseOpf(opfXml)
                val doc = parseXml(opfXml)
                if (zip.getEntry("META-INF/encryption.xml") != null) {
                    error("EPUB ini ber-DRM/terenkripsi (META-INF/encryption.xml); konversi tidak didukung.")
                }

                val mediaBase = if (opfDir.isBlank()) "" else "$opfDir/"
                val chapters = mutableListOf<String>()
                for (idref in parsed.second) {
                    val item = parsed.first[idref] ?: continue
                    if (!item.mediaType.contains("html", true) && !item.mediaType.contains("xml", true)) continue
                    val path = resolveHref(mediaBase, item.href)
                    val entry = zip.getEntry(path) ?: zip.getEntry(decodePercent(path)) ?: continue
                    val xhtml = zip.getInputStream(entry).use { it.readBytes().toString(Charsets.UTF_8) }
                    chapters.add(htmlToText(xhtml))
                }
                require(chapters.any { it.isNotBlank() }) {
                    "EPUB tidak berisi teks yang bisa diekstrak (mungkin hanya gambar atau ber-DRM)."
                }
                val title = (doc?.getElementsByTagName("dc:title")?.item(0)?.textContent
                    ?: doc?.getElementsByTagName("title")?.item(0)?.textContent ?: "")
                    .trim().ifBlank { "EPUB" }
                val author = (doc?.getElementsByTagName("dc:creator")?.item(0)?.textContent ?: "")
                    .trim()
                return Epub(title, author, chapters, parsed.second)
            }
        } finally {
            temp.delete()
        }
    }

    /** Jalur OPF dari `container.xml` (murni, diuji unit). */
    fun parseContainerRootPath(xml: String): String? {
        val doc = parseXml(xml) ?: return null
        val roots = doc.getElementsByTagName("rootfile")
        if (roots.length == 0) return null
        val value = (roots.item(0) as? Element)?.getAttribute("full-path")?.trim()
        return value?.ifBlank { null }
    }

    /** Pasangan (manifest id→item, urutan spine) dari OPF (murni, diuji unit). */
    fun parseOpf(xml: String): Pair<Map<String, ManifestItem>, List<String>> {
        val doc = parseXml(xml) ?: return emptyMap<String, ManifestItem>() to emptyList()
        val manifest = LinkedHashMap<String, ManifestItem>()
        val items = doc.getElementsByTagName("item")
        for (i in 0 until items.length) {
            val el = items.item(i) as? Element ?: continue
            val id = el.getAttribute("id")
            val href = el.getAttribute("href")
            val type = el.getAttribute("media-type")
            if (id.isNotBlank() && href.isNotBlank()) manifest[id] = ManifestItem(id, href, type)
        }
        val spine = mutableListOf<String>()
        val refs = doc.getElementsByTagName("itemref")
        for (i in 0 until refs.length) {
            val el = refs.item(i) as? Element ?: continue
            val idref = el.getAttribute("idref")
            if (idref.isNotBlank()) spine.add(idref)
        }
        return manifest to spine
    }

    fun resolveHref(baseDir: String, href: String): String {
        val cleaned = href.substringBefore('#')
        if (baseDir.isBlank()) return cleaned
        return baseDir + cleaned
    }

    /** Decode `%20` dsb. pada href EPUB tanpa mengubah `+` menjadi spasi. */
    fun decodePercent(value: String): String =
        runCatching { java.net.URLDecoder.decode(value.replace("+", "%2B"), "UTF-8") }.getOrDefault(value)

    /** Ekstrak teks dari XHTML/HTML (murni, diuji unit). */
    fun htmlToText(html: String): String {
        var body = html
            .replace(Regex("(?is)<(script|style)[^>]*>.*?</\\1>"), " ")
            .replace(Regex("(?is)<!--.*?-->"), " ")
            .replace(Regex("(?i)<br\\s*/?>"), "\n")
            .replace(Regex("(?i)</(p|div|li|tr|h[1-6]|blockquote|section|article|pre)>"), "\n")
        body = body.replace(Regex("(?s)<[^>]+>"), "")
        body = decodeEntities(body)
        return body.lineSequence()
            .map { it.replace('\u00A0', ' ').trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")
    }

    fun decodeEntities(text: String): String {
        val named = mapOf(
            "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'",
            "nbsp" to " ", "mdash" to "—", "ndash" to "–", "hellip" to "…",
            "lsquo" to "'", "rsquo" to "'", "ldquo" to "\"", "rdquo" to "\""
        )
        return Regex("&(#x?[0-9a-fA-F]+|[a-zA-Z]+);").replace(text) { match ->
            val token = match.groupValues[1]
            if (token.startsWith("#")) {
                val code = if (token.startsWith("#x") || token.startsWith("#X")) {
                    token.drop(2).toIntOrNull(16)
                } else {
                    token.drop(1).toIntOrNull()
                }
                code?.takeIf { it in 1..0x10FFFF }?.let { String(Character.toChars(it)) } ?: match.value
            } else {
                named[token.lowercase()] ?: match.value
            }
        }
    }

    private fun parseXml(xml: String): org.w3c.dom.Document? = runCatching {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        }
        factory.newDocumentBuilder().parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))
    }.getOrNull()

    // ---------------------------------------------------------------- EPUB tulis

    fun epubToTxt(epub: Epub): String = buildString {
        appendLine(epub.title)
        if (epub.author.isNotBlank()) appendLine(epub.author)
        appendLine()
        epub.chapters.forEachIndexed { index, chapter ->
            if (index > 0) appendLine("\n\n──────────\n")
            appendLine(chapter)
        }
    }

    fun epubToHtml(epub: Epub): String = buildString {
        appendLine("<!DOCTYPE html>")
        appendLine("<html lang=\"id\"><head><meta charset=\"UTF-8\"/>")
        appendLine("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"/>")
        appendLine("<title>${escapeHtml(epub.title)}</title>")
        appendLine("<style>body{font-family:serif;max-width:42rem;margin:2rem auto;padding:0 1rem;line-height:1.7}h1{font-size:1.6rem}</style>")
        appendLine("</head><body>")
        appendLine("<h1>${escapeHtml(epub.title)}</h1>")
        if (epub.author.isNotBlank()) appendLine("<p><em>${escapeHtml(epub.author)}</em></p>")
        epub.chapters.forEach { chapter ->
            chapter.split(Regex("\\n{2,}")).forEach { paragraph ->
                if (paragraph.isNotBlank()) appendLine("<p>${escapeHtml(paragraph).replace("\n", "<br/>")}</p>")
            }
        }
        appendLine("</body></html>")
    }

    /** EPUB → PDF teks (Helvetica). Tata letak asli tidak dipertahankan. */
    fun epubToPdf(context: Context, epub: Epub, dest: File): Int {
        PdfVaultEngine.ensureInit(context)
        val font = PDType1Font.HELVETICA
        val size = 11f
        val leading = 15.5f
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
                content.setFont(font, size)
                content.setNonStrokingColor(0.12f, 0.12f, 0.12f)
                fun ensureSpace() {
                    if (y >= margin + leading) return
                    content.endText()
                    content.close()
                    page = PDPage(PDRectangle.A4)
                    document.addPage(page)
                    content = PDPageContentStream(document, page)
                    content.beginText()
                    content.setFont(font, size)
                    content.setNonStrokingColor(0.12f, 0.12f, 0.12f)
                    y = pageHeight - margin
                }
                epub.chapters.forEachIndexed { index, chapter ->
                    if (index > 0) {
                        ensureSpace()
                        y -= leading
                    }
                    chapter.split('\n').forEach { rawLine ->
                        val line = rawLine.take(2000)
                        OfficeConvertEngine.wrapText(line, font, size, maxWidth).forEach { wrapped ->
                            ensureSpace()
                            content.setTextMatrix(Matrix.getTranslateInstance(margin, y))
                            content.showText(wrapped)
                            y -= leading
                        }
                    }
                }
                content.endText()
            } finally {
                content.close()
            }
            document.save(dest)
            return document.numberOfPages
        }
    }

    // --------------------------------------------------------------- Komik baca

    data class ComicPage(val name: String, val bytes: ByteArray)

    fun readComic(context: Context, uri: Uri, kind: ArchiveEngine.Kind): List<ComicPage> {
        val pages = mutableListOf<ComicPage>()
        when (kind) {
            ArchiveEngine.Kind.ZIP -> {
                val temp = File.createTempFile("comic", ".cbz", context.cacheDir)
                try {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(temp).use { out -> input.copyTo(out, 32 * 1024) }
                    } ?: error("Gagal membaca arsip komik")
                    ZipFile(temp).use { zip ->
                        zip.entries().asSequence()
                            .filter { !it.isDirectory && isImageName(it.name) }
                            .sortedWith { a, b -> naturalCompare(a.name, b.name) }
                            .take(ArchiveEngine.MAX_ENTRIES)
                            .forEach { entry ->
                                zip.getInputStream(entry).use { stream ->
                                    pages.add(ComicPage(entry.name, stream.readBytes()))
                                }
                            }
                    }
                } finally {
                    temp.delete()
                }
            }
            ArchiveEngine.Kind.RAR -> {
                val temp = File.createTempFile("comic", ".cbr", context.cacheDir)
                try {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(temp).use { out -> input.copyTo(out, 32 * 1024) }
                    } ?: error("Gagal membaca arsip komik")
                    Archive(temp).use { archive ->
                        val headers = archive.fileHeaders
                            .filter { !it.isDirectory && !it.isEncrypted && isImageName(it.fileName) }
                            .sortedWith { a, b -> naturalCompare(a.fileName, b.fileName) }
                            .take(ArchiveEngine.MAX_ENTRIES)
                        headers.forEach { header ->
                            archive.getInputStream(header)?.use { stream ->
                                pages.add(ComicPage(header.fileName, stream.readBytes()))
                            }
                        }
                    }
                } finally {
                    temp.delete()
                }
            }
            else -> error("Komik hanya mendukung CBZ (ZIP) atau CBR (RAR)")
        }
        require(pages.isNotEmpty()) { "Arsip komik tidak berisi gambar yang dikenali (JPG/PNG/WebP/GIF)." }
        return pages
    }

    fun isImageName(name: String): Boolean {
        val lower = name.lowercase()
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") ||
            lower.endsWith(".webp") || lower.endsWith(".gif") || lower.endsWith(".bmp")
    }

    /** Urutkan nama halaman secara alami: `page2` sebelum `page10` (murni, diuji unit). */
    fun naturalCompare(a: String, b: String): Int {
        var i = 0
        var j = 0
        while (i < a.length && j < b.length) {
            val ca = a[i]
            val cb = b[j]
            if (ca.isDigit() && cb.isDigit()) {
                var endA = i
                while (endA < a.length && a[endA].isDigit()) endA++
                var endB = j
                while (endB < b.length && b[endB].isDigit()) endB++
                val numA = a.substring(i, endA).trimStart('0')
                val numB = b.substring(j, endB).trimStart('0')
                val cmp = when {
                    numA.length != numB.length -> numA.length - numB.length
                    else -> numA.compareTo(numB)
                }
                if (cmp != 0) return cmp
                i = endA
                j = endB
            } else {
                val cmp = ca.lowercaseChar().compareTo(cb.lowercaseChar())
                if (cmp != 0) return cmp
                i++
                j++
            }
        }
        return (a.length - i) - (b.length - j)
    }

    // --------------------------------------------------------------- Komik tulis

    fun comicToPdf(context: Context, pages: List<ComicPage>, dest: File, maxDimensionPx: Int = 2200): Int {
        PdfVaultEngine.ensureInit(context)
        PDDocument().use { document ->
            pages.forEachIndexed { index, page ->
                val bitmap = decodeSampled(page.bytes, maxDimensionPx)
                    ?: error("Halaman ${page.name} tidak bisa didekode sebagai gambar")
                val buffer = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 88, buffer)
                val widthPt = bitmap.width.toFloat()
                val heightPt = bitmap.height.toFloat()
                bitmap.recycle()
                val image = PDImageXObject.createFromByteArray(document, buffer.toByteArray(), "page$index.jpg")
                val pdfPage = PDPage(PDRectangle(widthPt, heightPt))
                document.addPage(pdfPage)
                PDPageContentStream(document, pdfPage).use { stream ->
                    stream.drawImage(image, 0f, 0f, widthPt, heightPt)
                }
            }
            document.save(dest)
            return document.numberOfPages
        }
    }

    fun comicToEpub(pages: List<ComicPage>, title: String, dest: File): Int {
        val entries = pages.mapIndexed { index, page ->
            val extension = page.name.substringAfterLast('.', "jpg").lowercase()
            val safeExt = if (extension in setOf("jpg", "jpeg", "png", "webp", "gif")) extension else "jpg"
            val name = "images/page%04d.%s".format(index + 1, safeExt)
            val mime = when (safeExt) {
                "png" -> "image/png"
                "webp" -> "image/webp"
                "gif" -> "image/gif"
                else -> "image/jpeg"
            }
            Triple(name, mime, page.bytes)
        }
        val mimetype = "application/epub+zip".toByteArray(Charsets.US_ASCII)
        ZipOutputStream(FileOutputStream(dest).buffered()).use { zip ->
            val crc = CRC32().apply { update(mimetype) }
            zip.putNextEntry(ZipEntry("mimetype").apply {
                method = ZipEntry.STORED
                size = mimetype.size.toLong()
                this.crc = crc.value
            })
            zip.write(mimetype)
            zip.closeEntry()

            val container = """<?xml version="1.0" encoding="UTF-8"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
  <rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles>
</container>""".trimIndent()
            writeEntry(zip, "META-INF/container.xml", container.toByteArray())

            val manifest = StringBuilder()
            val spine = StringBuilder()
            entries.forEachIndexed { index, entry ->
                val (name, mime, bytes) = entry
                val id = "img${index + 1}"
                val pageId = "page${index + 1}"
                manifest.append("""    <item id="$id" href="$name" media-type="$mime"/>""").append('\n')
                manifest.append("""    <item id="$pageId" href="page${index + 1}.xhtml" media-type="application/xhtml+xml"/>""").append('\n')
                spine.append("""    <itemref idref="$pageId"/>""").append('\n')
                writeEntry(zip, "OEBPS/$name", bytes)
                val xhtml = """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
<head><title>Halaman ${index + 1}</title>
<style>html,body{margin:0;padding:0;height:100%}img{display:block;width:100%;height:auto}</style></head>
<body><img src="$name" alt="Halaman ${index + 1}"/></body></html>"""
                writeEntry(zip, "OEBPS/page${index + 1}.xhtml", xhtml.toByteArray())
            }
            val opf = """<?xml version="1.0" encoding="UTF-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="bookid">
  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
    <dc:identifier id="bookid">harimu-comic-${System.currentTimeMillis()}</dc:identifier>
    <dc:title>${escapeHtml(title.ifBlank { "Komik" })}</dc:title>
    <dc:language>id</dc:language>
    <meta property="dcterms:modified">2026-01-01T00:00:00Z</meta>
  </metadata>
  <manifest>
    <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
$manifest  </manifest>
  <spine>
$spine  </spine>
</package>"""
            writeEntry(zip, "OEBPS/content.opf", opf.toByteArray())
            val navItems = entries.indices.joinToString("\n") { index ->
                "<li><a href=\"page${index + 1}.xhtml\">Halaman ${index + 1}</a></li>"
            }
            val nav = """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
<head><title>Daftar Isi</title></head>
<body><nav epub:type="toc"><h1>${escapeHtml(title.ifBlank { "Komik" })}</h1><ol>
$navItems
</ol></nav></body></html>"""
            writeEntry(zip, "OEBPS/nav.xhtml", nav.toByteArray())
        }
        return entries.size
    }

    private fun writeEntry(zip: ZipOutputStream, name: String, bytes: ByteArray) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(bytes)
        zip.closeEntry()
    }

    private fun decodeSampled(bytes: ByteArray, maxDimension: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxDimension || bounds.outHeight / (sample * 2) >= maxDimension) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }

    fun escapeHtml(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    /** Daftar judul bab dari teks (dipakai ringkasan). */
    fun chapterCount(epub: Epub): Int = epub.chapters.count { it.isNotBlank() }
}
