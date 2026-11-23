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
import java.security.SecureRandom
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.xml.parsers.DocumentBuilderFactory

object ConverterEngine {

    private val prettyJson = Json { prettyPrint = true }
    private val compactJson = Json { prettyPrint = false }

    fun inspectAndRepackZip(context: Context, storage: FileStorageManager, uri: Uri, destZip: File): Pair<File, String> {
        val entries = mutableListOf<String>()
        var totalUncompressed = 0L
        context.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input).use { zis ->
                ZipOutputStream(FileOutputStream(destZip)).use { zos ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        val bytes = zis.readBytes()
                        totalUncompressed += bytes.size
                        entries.add("${entry.name} (${storage.formatBytes(bytes.size.toLong())})")
                        zos.putNextEntry(ZipEntry(entry.name))
                        zos.write(bytes)
                        zos.closeEntry()
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            }
        } ?: error("Gagal membuka arsip ZIP")
        val report = "Total entri: ${entries.size} (${storage.formatBytes(totalUncompressed)})\n" +
            entries.take(30).joinToString("\n")
        return destZip to report
    }

    fun createEncryptedArchive(
        context: Context,
        storage: FileStorageManager,
        uris: List<Uri>,
        password: String,
        destFile: File
    ): File {
        require(password.length >= 4) { "Kata sandi minimal 4 karakter" }
        val zipBytes = ByteArrayOutputStream().use { bos ->
            ZipOutputStream(bos).use { zos ->
                uris.forEachIndexed { idx, uri ->
                    val name = storage.getDisplayName(uri) ?: "file_${idx + 1}.bin"
                    zos.putNextEntry(ZipEntry(name))
                    context.contentResolver.openInputStream(uri)?.use { it.copyTo(zos, 16 * 1024) }
                    zos.closeEntry()
                }
            }
            bos.toByteArray()
        }
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val key = deriveKey(password, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        }
        val encrypted = cipher.doFinal(zipBytes)
        FileOutputStream(destFile).use { fos ->
            fos.write("HARIMUA1".toByteArray(Charsets.UTF_8))
            fos.write(salt)
            fos.write(iv)
            fos.write(encrypted)
        }
        return destFile
    }

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

    fun xmlToJson(rawXml: String): String {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(ByteArrayInputStream(rawXml.toByteArray(Charsets.UTF_8)))
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

    fun csvToTsv(csv: String): String =
        csv.lines().joinToString("\n") { line -> parseDelimitedLine(line, ',').joinToString("\t") }

    fun tsvToCsv(tsv: String): String =
        tsv.lines().joinToString("\n") { line ->
            line.split("\t").joinToString(",") { cell ->
                if (cell.contains(",") || cell.contains("\"")) "\"${cell.replace("\"", "\"\"")}\"" else cell
            }
        }

    private fun parseDelimitedLine(line: String, delim: Char): List<String> {
        val out = mutableListOf<String>()
        val cur = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            if (ch == '"') {
                if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                    cur.append('"'); i++
                } else {
                    inQuotes = !inQuotes
                }
            } else if (ch == delim && !inQuotes) {
                out.add(cur.toString()); cur.clear()
            } else {
                cur.append(ch)
            }
            i++
        }
        out.add(cur.toString())
        return out
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
        for (line in md.lines()) {
            val t = line.trimEnd()
            if (t.startsWith("```")) {
                if (!inCode) { body.append("<pre><code>\n"); inCode = true }
                else { body.append("</code></pre>\n"); inCode = false }
                continue
            }
            if (inCode) {
                body.append(escapeXml(t)).append("\n")
                continue
            }
            when {
                t.startsWith("### ") -> body.append("<h3>${inlineMd(t.drop(4))}</h3>\n")
                t.startsWith("## ") -> body.append("<h2>${inlineMd(t.drop(3))}</h2>\n")
                t.startsWith("# ") -> body.append("<h1>${inlineMd(t.drop(2))}</h1>\n")
                t.startsWith("> ") -> body.append("<blockquote>${inlineMd(t.drop(2))}</blockquote>\n")
                t.startsWith("- ") || t.startsWith("* ") -> body.append("<li>${inlineMd(t.drop(2))}</li>\n")
                t.isBlank() -> body.append("<br/>\n")
                else -> body.append("<p>${inlineMd(t)}</p>\n")
            }
        }
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

    private fun inlineMd(text: String): String = escapeXml(text)
        .replace(Regex("\\*\\*(.+?)\\*\\*"), "<strong>$1</strong>")
        .replace(Regex("\\*(.+?)\\*"), "<em>$1</em>")
        .replace(Regex("`(.+?)`"), "<code>$1</code>")
        .replace(Regex("\\[(.+?)\\]\\((.+?)\\)"), "<a href=\"$2\">$1</a>")

    fun geoJsonToKml(rawGeoJson: String): String {
        val root = Json.parseToJsonElement(rawGeoJson.trim()).jsonObject
        val features = if (root["type"]?.jsonPrimitive?.content == "FeatureCollection") {
            root["features"]!!.jsonArray
        } else {
            JsonArray(listOf(root))
        }
        val sb = StringBuilder(
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<kml xmlns=\"http://www.opengis.net/kml/2.2\">\n<Document>\n"
        )
        for (i in features.indices) {
            val feat = features[i].jsonObject
            val props = feat["properties"] as? JsonObject
            val name = props?.get("name")?.jsonPrimitive?.content?.ifBlank { null } ?: "Feature ${i + 1}"
            val geom = (feat["geometry"] as? JsonObject) ?: feat
            val type = geom["type"]?.jsonPrimitive?.content ?: ""
            val coords = geom["coordinates"]?.jsonArray ?: continue
            sb.append("  <Placemark>\n    <name>${escapeXml(name)}</name>\n")
            when (type) {
                "Point" -> {
                    val lon = coords[0].jsonPrimitive.double
                    val lat = coords[1].jsonPrimitive.double
                    sb.append("    <Point><coordinates>$lon,$lat,0</coordinates></Point>\n")
                }
                "LineString" -> {
                    val pts = coords.joinToString(" ") { el ->
                        val c = el.jsonArray
                        "${c[0].jsonPrimitive.double},${c[1].jsonPrimitive.double},0"
                    }
                    sb.append("    <LineString><coordinates>$pts</coordinates></LineString>\n")
                }
                "Polygon" -> {
                    val ring = coords[0].jsonArray
                    val pts = ring.joinToString(" ") { el ->
                        val c = el.jsonArray
                        "${c[0].jsonPrimitive.double},${c[1].jsonPrimitive.double},0"
                    }
                    sb.append("    <Polygon><outerBoundaryIs><LinearRing><coordinates>$pts</coordinates></LinearRing></outerBoundaryIs></Polygon>\n")
                }
            }
            sb.append("  </Placemark>\n")
        }
        sb.append("</Document>\n</kml>")
        return sb.toString()
    }

    fun stlToObj(stlBytes: ByteArray): String {
        val asText = stlBytes.toString(Charsets.UTF_8)
        val vertices = mutableListOf<Triple<Float, Float, Float>>()
        if (asText.trimStart().startsWith("solid") && asText.contains("facet normal")) {
            val regex = Regex("vertex\\s+([-+0-9.eE]+)\\s+([-+0-9.eE]+)\\s+([-+0-9.eE]+)")
            regex.findAll(asText).forEach { m ->
                vertices.add(
                    Triple(
                        m.groupValues[1].toFloat(),
                        m.groupValues[2].toFloat(),
                        m.groupValues[3].toFloat()
                    )
                )
            }
        } else {
            require(stlBytes.size >= 84) { "File STL binary tidak valid" }
            val bb = ByteBuffer.wrap(stlBytes).order(ByteOrder.LITTLE_ENDIAN)
            bb.position(80)
            val triCount = bb.int
            for (i in 0 until triCount) {
                if (bb.remaining() < 50) break
                bb.float; bb.float; bb.float // skip normal
                repeat(3) {
                    vertices.add(Triple(bb.float, bb.float, bb.float))
                }
                bb.short // attribute byte count
            }
        }
        val sb = StringBuilder("# Converted by HariMu STL->OBJ\n")
        vertices.forEach { (x, y, z) -> sb.append("v $x $y $z\n") }
        var idx = 1
        while (idx + 2 <= vertices.size) {
            sb.append("f $idx ${idx + 1} ${idx + 2}\n")
            idx += 3
        }
        return sb.toString()
    }

    private fun escapeXml(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}
