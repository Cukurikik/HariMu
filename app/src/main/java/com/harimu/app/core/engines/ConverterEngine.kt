package com.harimu.app.core.engines

import android.content.Context
import android.net.Uri
import com.harimu.app.core.files.FileStorageManager
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.xml.parsers.DocumentBuilderFactory

object ConverterEngine {

    private val prettyJson = Json { prettyPrint = true }
    private val compactJson = Json { prettyPrint = false }

    fun decryptArchiveToZip(rawBytes: ByteArray, password: String, destZip: File): File {
        val header = "HARIMUA1".toByteArray(Charsets.UTF_8)
        require(rawBytes.size > 36 && rawBytes.sliceArray(0 until 8).contentEquals(header)) {
            "File bukan arsip terenkripsi HariMu (.hzm)"
        }
        val salt = rawBytes.sliceArray(8 until 24)
        val iv = rawBytes.sliceArray(24 until 36)
        val cipherText = rawBytes.sliceArray(36 until rawBytes.size)
        val key = deriveKey(password, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        }
        destZip.writeBytes(cipher.doFinal(cipherText))
        return destZip
    }

    private fun deriveKey(password: String, salt: ByteArray): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(password.toCharArray(), salt, 65536, 256)
        return SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
    }

    fun jsonToXml(rawJson: String): String {
        val elem = Json.parseToJsonElement(rawJson.trim())
        val sb = StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<root>\n")
        when (elem) {
            is JsonObject -> appendJsonObjectXml(elem, sb, 2)
            is JsonArray -> appendJsonArrayXml("item", elem, sb, 2)
            else -> sb.append("  <value>${escapeXml(elem.jsonPrimitive.content)}</value>\n")
        }
        sb.append("</root>")
        return sb.toString()
    }

    private fun appendJsonObjectXml(obj: JsonObject, sb: StringBuilder, indent: Int) {
        val pad = " ".repeat(indent)
        for ((rawKey, v) in obj) {
            val k = rawKey.replace(Regex("[^A-Za-z0-9_-]"), "_")
            when (v) {
                is JsonObject -> {
                    sb.append("$pad<$k>\n")
                    appendJsonObjectXml(v, sb, indent + 2)
                    sb.append("$pad</$k>\n")
                }
                is JsonArray -> appendJsonArrayXml(k, v, sb, indent)
                is JsonNull -> sb.append("$pad<$k />\n")
                is JsonPrimitive -> sb.append("$pad<$k>${escapeXml(v.content)}</$k>\n")
            }
        }
    }

    private fun appendJsonArrayXml(tag: String, arr: JsonArray, sb: StringBuilder, indent: Int) {
        val pad = " ".repeat(indent)
        for (v in arr) {
            when (v) {
                is JsonObject -> {
                    sb.append("$pad<$tag>\n")
                    appendJsonObjectXml(v, sb, indent + 2)
                    sb.append("$pad</$tag>\n")
                }
                is JsonPrimitive -> sb.append("$pad<$tag>${escapeXml(v.content)}</$tag>\n")
                else -> sb.append("$pad<$tag>${escapeXml(v.toString())}</$tag>\n")
            }
        }
    }

    private const val MAX_XML_CHARS = 4 * 1024 * 1024

    fun xmlToJson(rawXml: String): String {
        // Hardening XXE / billion-laughs. Sebagian fitur Xerces tidak didukung parser Android,
        // jadi setiap setFeature dibuat toleran dan EntityResolver tetap menolak entitas luar.
        require(rawXml.length <= MAX_XML_CHARS) { "XML melebihi batas ${MAX_XML_CHARS / 1024} KB" }
        val factory = DocumentBuilderFactory.newInstance().apply {
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
            runCatching { setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
            runCatching { isXIncludeAware = false }
            isExpandEntityReferences = false
        }
        val builder = factory.newDocumentBuilder()
        builder.setEntityResolver { _, _ ->
            throw org.xml.sax.SAXException("Entitas eksternal dilarang")
        }
        val doc = builder.parse(ByteArrayInputStream(rawXml.toByteArray(Charsets.UTF_8)))
        doc.documentElement.normalize()
        val rootObj = buildJsonObject {
            put(doc.documentElement.tagName, elementToJson(doc.documentElement))
        }
        return prettyJson.encodeToString(JsonObject.serializer(), rootObj)
    }

    private fun elementToJson(el: Element): JsonElement {
        val children = el.childNodes
        val childElements = mutableListOf<Element>()
        for (i in 0 until children.length) {
            val n = children.item(i)
            if (n.nodeType == Node.ELEMENT_NODE) childElements.add(n as Element)
        }
        if (childElements.isEmpty()) return JsonPrimitive(el.textContent?.trim() ?: "")
        val grouped = childElements.groupBy { it.tagName }
        return buildJsonObject {
            for ((tag, list) in grouped) {
                if (list.size == 1) {
                    put(tag, elementToJson(list.first()))
                } else {
                    put(tag, buildJsonArray { list.forEach { add(elementToJson(it)) } })
                }
            }
        }
    }

    fun csvToTsv(csv: String): String = rowsToDelimited(parseDelimitedRows(csv, ','), '\t')

    fun tsvToCsv(tsv: String): String = rowsToDelimited(parseDelimitedRows(tsv, '\t'), ',')

    /**
     * Parser RFC-4180 penuh: kutip ganda, `""` sebagai kutip literal, dan baris baru di dalam
     * kutip. Baris kosong di akhir berkas tidak menghasilkan baris palsu.
     */
    fun parseDelimitedRows(text: String, delimiter: Char): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val row = mutableListOf<String>()
        val cell = StringBuilder()
        var inQuotes = false
        var i = 0
        var sawAny = false
        while (i < text.length) {
            val ch = text[i]
            if (inQuotes) {
                if (ch == '"') {
                    if (i + 1 < text.length && text[i + 1] == '"') {
                        cell.append('"')
                        i += 2
                        continue
                    }
                    inQuotes = false
                    i++
                    continue
                }
                cell.append(ch)
                i++
                continue
            }
            when (ch) {
                '"' -> { inQuotes = true; sawAny = true; i++ }
                delimiter -> { row.add(cell.toString()); cell.clear(); sawAny = true; i++ }
                '\r' -> {
                    if (i + 1 < text.length && text[i + 1] == '\n') i++
                    row.add(cell.toString()); cell.clear(); rows.add(row.toList()); row.clear(); sawAny = false; i++
                }
                '\n' -> {
                    row.add(cell.toString()); cell.clear(); rows.add(row.toList()); row.clear(); sawAny = false; i++
                }
                else -> { cell.append(ch); sawAny = true; i++ }
            }
        }
        if (sawAny || cell.isNotEmpty() || row.isNotEmpty()) {
            row.add(cell.toString())
            rows.add(row.toList())
        }
        return rows
    }

    fun rowsToDelimited(rows: List<List<String>>, delimiter: Char): String =
        rows.joinToString("\n") { row ->
            row.joinToString(delimiter.toString()) { cell -> escapeDelimitedCell(cell, delimiter) }
        }

    fun escapeDelimitedCell(cell: String, delimiter: Char): String {
        val needsQuote = cell.contains(delimiter) || cell.contains('"') ||
            cell.contains('\n') || cell.contains('\r') || cell.trim() != cell
        return if (needsQuote) "\"${cell.replace("\"", "\"\"")}\"" else cell
    }

    fun formatCode(raw: String, mode: String): String {
        val t = raw.trim()
        return when (mode) {
            "Minify JSON" -> {
                val elem = Json.parseToJsonElement(t)
                compactJson.encodeToString(elem)
            }
            "Pretty JSON" -> {
                val elem = Json.parseToJsonElement(t)
                prettyJson.encodeToString(elem)
            }
            "Pretty CSS" -> t
                .replace(Regex("\\s*\\{\\s*"), " {\n  ")
                .replace(Regex(";\\s*"), ";\n  ")
                .replace(Regex("\\s*\\}\\s*"), "\n}\n\n")
                .trim()
            else -> t.lines().joinToString("\n") { it.trimEnd() }
        }
    }

    fun markdownToHtml(md: String): String {
        val body = StringBuilder()
        var inCode = false
        var codeLang = ""
        var listKind = ""

        fun closeList() {
            if (listKind.isNotEmpty()) {
                body.append("</$listKind>\n")
                listKind = ""
            }
        }

        for (line in md.lines()) {
            val t = line.trimEnd()
            if (t.trimStart().startsWith("```")) {
                if (!inCode) {
                    closeList()
                    codeLang = t.trimStart().removePrefix("```").trim()
                    val cls = if (codeLang.isBlank()) "" else " class=\"language-${escapeXml(codeLang)}\""
                    body.append("<pre><code$cls>\n")
                    inCode = true
                } else {
                    body.append("</code></pre>\n")
                    inCode = false
                    codeLang = ""
                }
                continue
            }
            if (inCode) {
                body.append(escapeXml(t)).append("\n")
                continue
            }
            val heading = Regex("^(#{1,6})\\s+(.*)$").find(t)
            val unordered = Regex("^\\s*[-*+]\\s+(.*)$").find(t)
            val ordered = Regex("^\\s*\\d+[.)]\\s+(.*)$").find(t)
            when {
                heading != null -> {
                    closeList()
                    val level = heading.groupValues[1].length
                    body.append("<h$level>${inlineMd(heading.groupValues[2])}</h$level>\n")
                }
                t.trim() == "---" || t.trim() == "***" -> {
                    closeList()
                    body.append("<hr/>\n")
                }
                unordered != null -> {
                    if (listKind != "ul") { closeList(); body.append("<ul>\n"); listKind = "ul" }
                    body.append("<li>${inlineMd(unordered.groupValues[1])}</li>\n")
                }
                ordered != null -> {
                    if (listKind != "ol") { closeList(); body.append("<ol>\n"); listKind = "ol" }
                    body.append("<li>${inlineMd(ordered.groupValues[1])}</li>\n")
                }
                t.startsWith("> ") -> {
                    closeList()
                    body.append("<blockquote>${inlineMd(t.drop(2))}</blockquote>\n")
                }
                t.isBlank() -> closeList()
                else -> {
                    closeList()
                    body.append("<p>${inlineMd(t)}</p>\n")
                }
            }
        }
        closeList()
        if (inCode) body.append("</code></pre>\n")
        return """
            <!DOCTYPE html>
            <html lang="id">
            <head>
              <meta charset="UTF-8"/>
              <meta name="viewport" content="width=device-width, initial-scale=1"/>
              <style>
                body { font-family: sans-serif; max-width: 780px; margin: 2rem auto; padding: 0 1rem; line-height: 1.65; color: #18181b; }
                pre { background: #11141c; color: #f8fafc; padding: 1rem; border-radius: 10px; overflow-x: auto; }
                blockquote { border-left: 4px solid #ff6b2c; margin: 0.75rem 0; padding-left: 1rem; color: #52525b; }
              </style>
            </head>
            <body>
            $body
            </body>
            </html>
        """.trimIndent()
    }

    /**
     * Inline Markdown: kode inline dan tautan dilindungi dulu agar tidak ikut di-escape atau
     * di-format sebagai penekanan (mencegah XSS lewat URL `javascript:`).
     */
    fun inlineMd(text: String): String {
        val tokens = mutableListOf<String>()
        var working = text

        working = Regex("`([^`]+)`").replace(working) { match ->
            tokens.add("<code>${escapeXml(match.groupValues[1])}</code>")
            "\u0000${tokens.lastIndex}\u0000"
        }
        working = Regex("\\[([^\\]]+)]\\(([^)\\s]+)\\)").replace(working) { match ->
            val label = escapeXml(match.groupValues[1])
            val url = match.groupValues[2]
            val safe = safeUrl(url)
            val html = if (safe == null) label else "<a href=\"${escapeXml(safe)}\">$label</a>"
            tokens.add(html)
            "\u0000${tokens.lastIndex}\u0000"
        }

        var escaped = escapeXml(working)
        escaped = escaped
            .replace(Regex("\\*\\*(.+?)\\*\\*"), "<strong>$1</strong>")
            .replace(Regex("__(.+?)__"), "<strong>$1</strong>")
            .replace(Regex("~~(.+?)~~"), "<del>$1</del>")
            .replace(Regex("\\*(.+?)\\*"), "<em>$1</em>")
            .replace(Regex("(?<!\\w)_(.+?)_(?!\\w)"), "<em>$1</em>")
        tokens.forEachIndexed { index, html ->
            escaped = escaped.replace("\u0000$index\u0000", html)
        }
        return escaped
    }

    /** Hanya skema aman yang diizinkan; selain itu tautan diturunkan menjadi teks. */
    fun safeUrl(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        val lower = trimmed.lowercase()
        // Tolak skema berbahaya dan URL protocol-relative (`//evil`) yang lolos dari anchor.
        if (lower.startsWith("//")) return null
        if (lower.startsWith("javascript:") || lower.startsWith("data:") ||
            lower.startsWith("vbscript:") || lower.startsWith("file:")
        ) return null
        val scheme = lower.substringBefore(':', missingDelimiterValue = "")
        val allowed = setOf("http", "https", "mailto", "tel", "")
        if (scheme.isNotEmpty() && scheme !in allowed) return null
        if (trimmed.any { it.isWhitespace() }) return null
        return trimmed
    }

    fun geoJsonToKml(rawGeoJson: String): String {
        val root = Json.parseToJsonElement(rawGeoJson.trim()).jsonObject
        val features = when (root["type"]?.jsonPrimitive?.content) {
            "FeatureCollection" -> root["features"]?.jsonArray ?: JsonArray(emptyList())
            "Feature" -> JsonArray(listOf(root))
            else -> JsonArray(listOf(root))
        }
        val sb = StringBuilder(
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<kml xmlns=\"http://www.opengis.net/kml/2.2\">\n<Document>\n"
        )
        features.forEachIndexed { index, element ->
            val feature = element as? JsonObject ?: return@forEachIndexed
            val props = feature["properties"] as? JsonObject
            fun property(key: String): String? =
                (props?.get(key) as? JsonPrimitive)?.contentOrNull()?.takeIf { it.isNotBlank() }
            val name = property("name") ?: property("title") ?: "Feature ${index + 1}"
            val description = property("description")
            val geometry = (feature["geometry"] as? JsonObject) ?: feature
            sb.append("  <Placemark>\n    <name>${escapeXml(name)}</name>\n")
            if (!description.isNullOrBlank()) {
                sb.append("    <description>${escapeXml(description)}</description>\n")
            }
            appendKmlGeometry(sb, geometry, "    ")
            sb.append("  </Placemark>\n")
        }
        sb.append("</Document>\n</kml>")
        return sb.toString()
    }

    private fun appendKmlGeometry(sb: StringBuilder, geometry: JsonObject, pad: String) {
        when (geometry["type"]?.jsonPrimitive?.content) {
            "Point" -> {
                val point = coordinate(geometry["coordinates"] as? JsonArray)
                if (point != null) sb.append("$pad<Point><coordinates>$point</coordinates></Point>\n")
            }
            "MultiPoint" -> {
                val points = geometry["coordinates"]?.jsonArray ?: return
                sb.append("$pad<MultiGeometry>\n")
                points.forEach { value ->
                    coordinate(value as? JsonArray)?.let {
                        sb.append("$pad  <Point><coordinates>$it</coordinates></Point>\n")
                    }
                }
                sb.append("$pad</MultiGeometry>\n")
            }
            "LineString" -> {
                val line = coordinateLine(geometry["coordinates"] as? JsonArray)
                if (line != null) sb.append("$pad<LineString><coordinates>$line</coordinates></LineString>\n")
            }
            "MultiLineString", "Polygon" -> {
                val rings = geometry["coordinates"]?.jsonArray ?: return
                if (geometry["type"]?.jsonPrimitive?.content == "MultiLineString") {
                    sb.append("$pad<MultiGeometry>\n")
                    rings.forEach { ring ->
                        coordinateLine(ring as? JsonArray)?.let {
                            sb.append("$pad  <LineString><coordinates>$it</coordinates></LineString>\n")
                        }
                    }
                    sb.append("$pad</MultiGeometry>\n")
                } else {
                    appendPolygon(sb, rings, pad)
                }
            }
            "MultiPolygon" -> {
                val polygons = geometry["coordinates"]?.jsonArray ?: return
                sb.append("$pad<MultiGeometry>\n")
                polygons.forEach { polygon ->
                    (polygon as? JsonArray)?.let { appendPolygon(sb, it, "$pad  ") }
                }
                sb.append("$pad</MultiGeometry>\n")
            }
            "GeometryCollection" -> {
                val geometries = geometry["geometries"]?.jsonArray ?: return
                sb.append("$pad<MultiGeometry>\n")
                geometries.forEach { child ->
                    (child as? JsonObject)?.let { appendKmlGeometry(sb, it, "$pad  ") }
                }
                sb.append("$pad</MultiGeometry>\n")
            }
            null -> Unit
        }
    }

    private fun appendPolygon(sb: StringBuilder, rings: JsonArray, pad: String) {
        if (rings.isEmpty()) return
        sb.append("$pad<Polygon>\n")
        val outer = coordinateLine(rings[0] as? JsonArray)
        if (outer != null) {
            sb.append("$pad  <outerBoundaryIs><LinearRing><coordinates>$outer</coordinates></LinearRing></outerBoundaryIs>\n")
        }
        for (i in 1 until rings.size) {
            val inner = coordinateLine(rings[i] as? JsonArray) ?: continue
            sb.append("$pad  <innerBoundaryIs><LinearRing><coordinates>$inner</coordinates></LinearRing></innerBoundaryIs>\n")
        }
        sb.append("$pad</Polygon>\n")
    }

    private fun coordinate(value: JsonArray?): String? {
        if (value == null || value.size < 2) return null
        val lon = value[0].jsonPrimitive.doubleOrNull() ?: return null
        val lat = value[1].jsonPrimitive.doubleOrNull() ?: return null
        val alt = if (value.size > 2) value[2].jsonPrimitive.doubleOrNull() ?: 0.0 else 0.0
        return "$lon,$lat,$alt"
    }

    private fun coordinateLine(value: JsonArray?): String? {
        if (value == null || value.isEmpty()) return null
        val points = value.mapNotNull { coordinate(it as? JsonArray) }
        return if (points.size < 2) null else points.joinToString(" ")
    }

    private fun JsonPrimitive.doubleOrNull(): Double? =
        if (this is JsonNull) null else runCatching { double }.getOrNull()

    private fun JsonPrimitive.contentOrNull(): String? =
        if (this is JsonNull) null else runCatching { content }.getOrNull()

    fun stlToObj(stlBytes: ByteArray): String {
        val vertices = when {
            isBinaryStl(stlBytes) -> parseBinaryStl(stlBytes)
            looksLikeAsciiStl(stlBytes) -> parseAsciiStl(stlBytes.toString(Charsets.UTF_8))
            else -> error("STL tidak dikenali (bukan ASCII ber-'facet normal' maupun binary 84+50n byte)")
        }
        require(vertices.size % 3 == 0) { "Jumlah verteks STL bukan kelipatan 3" }
        require(vertices.isNotEmpty()) { "STL tidak berisi segitiga" }
        val sb = StringBuilder("# Converted by HariMu STL->OBJ\n")
        vertices.forEach { (x, y, z) -> sb.append("v $x $y $z\n") }
        var idx = 1
        while (idx + 2 <= vertices.size) {
            sb.append("f $idx ${idx + 1} ${idx + 2}\n")
            idx += 3
        }
        return sb.toString()
    }

    /**
     * Deteksi binary memakai ukuran pasti `84 + 50 × jumlah segitiga`. Ini lebih andal daripada
     * memeriksa kata "solid", karena banyak STL binary juga diawali "solid".
     */
    fun isBinaryStl(bytes: ByteArray): Boolean {
        if (bytes.size < 84) return false
        val triangles = ByteBuffer.wrap(bytes, 80, 4).order(ByteOrder.LITTLE_ENDIAN).int
        if (triangles <= 0) return false
        return 84L + 50L * triangles == bytes.size.toLong()
    }

    fun looksLikeAsciiStl(bytes: ByteArray): Boolean {
        val head = bytes.toString(Charsets.UTF_8).take(2048)
        return head.trimStart().startsWith("solid") &&
            (head.contains("facet normal") || head.contains("endsolid"))
    }

    private fun parseBinaryStl(bytes: ByteArray): List<Triple<Float, Float, Float>> {
        require(bytes.size >= 84) { "File STL binary tidak valid" }
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        bb.position(80)
        val triangles = bb.int
        val vertices = ArrayList<Triple<Float, Float, Float>>(triangles * 3)
        for (i in 0 until triangles) {
            if (bb.remaining() < 50) break
            bb.float
            bb.float
            bb.float // normal diabaikan
            repeat(3) { vertices.add(Triple(bb.float, bb.float, bb.float)) }
            bb.short
        }
        return vertices
    }

    private fun parseAsciiStl(text: String): List<Triple<Float, Float, Float>> {
        val regex = Regex("(?im)^\\s*vertex\\s+(\\S+)\\s+(\\S+)\\s+(\\S+)")
        return regex.findAll(text).mapNotNull { match ->
            val x = match.groupValues[1].toFloatOrNull()
            val y = match.groupValues[2].toFloatOrNull()
            val z = match.groupValues[3].toFloatOrNull()
            if (x == null || y == null || z == null) null else Triple(x, y, z)
        }.toList()
    }

    private fun escapeXml(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}
