package com.harimu.app

import com.harimu.app.core.engines.ConverterEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Tes murni-Kotlin untuk parser/konverter teks FASE 13 (CNV-23…28).
 * Tidak membuat berkas Android; tidak butuh toolchain Android untuk logikanya.
 */
class ConverterCoreTest {

    // ------------------------------------------------------------- JSON ⇄ XML

    @Test
    fun jsonToXmlHandlesNestedArraysNullAndInvalidNames() {
        val xml = ConverterEngine.jsonToXml(
            """{"a":1,"b c":"<x>","arr":[1,2],"nul":null,"obj":{"k":"v"}}"""
        )
        assertTrue(xml.startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"))
        assertTrue(xml.contains("<a>1</a>"))
        assertTrue(xml.contains("<b_c>&lt;x&gt;</b_c>"))
        assertTrue(xml.contains("<arr>1</arr>"))
        assertTrue(xml.contains("<arr>2</arr>"))
        assertTrue(xml.contains("<nul />"))
        assertTrue(xml.contains("<obj>"))
        assertTrue(xml.trimEnd().endsWith("</root>"))
    }

    @Test
    fun xmlToJsonGroupsRepeatedChildrenIntoArray() {
        val json = ConverterEngine.xmlToJson(
            "<root><item>1</item><item>2</item><single>x</single></root>"
        )
        assertTrue(json.contains("\"item\": ["))
        assertTrue(json.contains("\"single\": \"x\""))
        assertTrue(json.contains("\"root\""))
    }

    @Test
    fun xmlToJsonEmptyElementBecomesEmptyString() {
        val json = ConverterEngine.xmlToJson("<root><empty/></root>")
        assertTrue(json.contains("\"empty\": \"\""))
    }

    // ------------------------------------------------------------- CSV ⇄ TSV

    @Test
    fun csvToTsvSupportsQuotesEscapedQuotesAndMultilineCells() {
        val rows = ConverterEngine.parseDelimitedRows("a,\"b,c\"\n\"line1\nline2\",\"say \"\"hi\"\"\"", ',')
        assertEquals(2, rows.size)
        assertEquals(listOf("a", "b,c"), rows[0])
        assertEquals(listOf("line1\nline2", "say \"hi\""), rows[1])

        val tsv = ConverterEngine.csvToTsv("a,\"b,c\"\n\"line1\nline2\",\"say \"\"hi\"\"\"")
        assertTrue(tsv.startsWith("a\tb,c"))
        assertTrue(tsv.contains("\"line1\nline2\""))
    }

    @Test
    fun crlfAndTrailingNewlineDoNotCreatePhantomRows() {
        val rows = ConverterEngine.parseDelimitedRows("a,b\r\nc,d\r\n", ',')
        assertEquals(2, rows.size)
        assertEquals(listOf("c", "d"), rows[1])
        assertEquals(emptyList<List<String>>(), ConverterEngine.parseDelimitedRows("", ','))
    }

    @Test
    fun tsvToCsvQuotesTabsQuotesAndWhitespace() {
        val csv = ConverterEngine.tsvToCsv("x\t\"a,b\"\n\"tab\there\"\t \"pad\" ")
        assertTrue(csv.contains("\"a,b\""))
        assertTrue(csv.contains("\"tab\there\""))
        assertTrue(csv.contains("\" pad \""))
    }

    // ------------------------------------------------------------- GeoJSON → KML

    @Test
    fun geoJsonToKmlSupportsPolygonHolesAndMultiGeometries() {
        val geojson = """
            {"type":"FeatureCollection","features":[
              {"type":"Feature","properties":{"name":"Kota","description":"Ibu kota"},
               "geometry":{"type":"Polygon","coordinates":[
                 [[106.7,-6.1],[106.9,-6.1],[106.9,-6.3],[106.7,-6.3],[106.7,-6.1]],
                 [[106.8,-6.2],[106.85,-6.2],[106.85,-6.25],[106.8,-6.2]]
               ]}},
              {"type":"Feature","properties":{"name":"Pulau"},
               "geometry":{"type":"MultiPolygon","coordinates":[
                 [[[100.0,0.0],[101.0,0.0],[101.0,1.0],[100.0,0.0]]],
                 [[[110.0,0.0],[111.0,0.0],[111.0,1.0],[110.0,0.0]]]
               ]}}
            ]}
        """.trimIndent()
        val kml = ConverterEngine.geoJsonToKml(geojson)
        assertTrue(kml.contains("<name>Kota</name>"))
        assertTrue(kml.contains("<description>Ibu kota</description>"))
        assertEquals(1, Regex("<innerBoundaryIs>").findAll(kml).count())
        assertEquals(1, Regex("<MultiGeometry>").findAll(kml).count())
        assertTrue(kml.contains("106.7,-6.1,0.0"))
    }

    @Test
    fun geoJsonToKmlHandlesPointAndGeometryCollection() {
        val point = ConverterEngine.geoJsonToKml(
            """{"type":"Point","coordinates":[106.8271,-6.1754]}"""
        )
        assertTrue(point.contains("<Point><coordinates>106.8271,-6.1754,0.0</coordinates></Point>"))

        val collection = ConverterEngine.geoJsonToKml(
            """{"type":"GeometryCollection","geometries":[
                 {"type":"Point","coordinates":[1,2]},
                 {"type":"LineString","coordinates":[[1,2],[3,4]]}
               ]}"""
        )
        assertTrue(collection.contains("<Point>"))
        assertTrue(collection.contains("<LineString>"))
    }

    // ------------------------------------------------------------- Markdown

    @Test
    fun markdownGroupsListsAndHandlesInlineFormatting() {
        val html = ConverterEngine.markdownToHtml(
            "# Judul\n\n- satu\n- dua\n\n**tebal** dan *miring* serta `a*b`"
        )
        assertTrue(html.contains("<h1>Judul</h1>"))
        assertTrue(html.contains("<ul>"))
        assertTrue(html.contains("<li>satu</li>"))
        assertTrue(html.contains("<strong>tebal</strong>"))
        assertTrue(html.contains("<em>miring</em>"))
        assertTrue(html.contains("<code>a*b</code>"))
        assertTrue(html.contains("</ul>"))
    }

    @Test
    fun markdownRejectsJavascriptUrlsAndEscapesHtml() {
        val html = ConverterEngine.markdownToHtml(
            "[klik](javascript:alert(1)) [aman](https://contoh.id/?a=1&b=2) <script>"
        )
        assertFalse(html.contains("javascript:"))
        assertTrue(html.contains("klik"))
        assertTrue(html.contains("<a href=\"https://contoh.id/?a=1&amp;b=2\">aman</a>"))
        assertTrue(html.contains("&lt;script&gt;"))
    }

    @Test
    fun markdownFencedCodeIsEscapedAndLanguageKept() {
        val html = ConverterEngine.markdownToHtml("```kotlin\nval x = 1 < 2\n```")
        assertTrue(html.contains("<pre><code class=\"language-kotlin\">"))
        assertTrue(html.contains("val x = 1 &lt; 2"))
        assertTrue(html.contains("</code></pre>"))
    }

    // ------------------------------------------------------------- STL → OBJ

    @Test
    fun asciiStlIsDetectedAndConverted() {
        val ascii = """
            solid cube
              facet normal 0 0 1
                outer loop
                  vertex 0 0 0
                  vertex 1 0 0
                  vertex 0 1 0
                endloop
              endfacet
            endsolid cube
        """.trimIndent().toByteArray()
        assertFalse(ConverterEngine.isBinaryStl(ascii))
        assertTrue(ConverterEngine.looksLikeAsciiStl(ascii))
        val obj = ConverterEngine.stlToObj(ascii)
        assertTrue(obj.contains("v 0.0 0.0 0.0"))
        assertTrue(obj.contains("v 1.0 0.0 0.0"))
        assertTrue(obj.contains("f 1 2 3"))
    }

    @Test
    fun binaryStlIsDetectedByExactSizeAndConverted() {
        val triangles = 2
        val buffer = ByteBuffer.allocate(84 + triangles * 50).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put(ByteArray(80))
        buffer.putInt(triangles)
        repeat(triangles) { t ->
            buffer.putFloat(0f); buffer.putFloat(0f); buffer.putFloat(1f)
            repeat(3) { v ->
                buffer.putFloat(t.toFloat()); buffer.putFloat(v.toFloat()); buffer.putFloat(0f)
            }
            buffer.putShort(0)
        }
        val bytes = buffer.array()
        assertTrue(ConverterEngine.isBinaryStl(bytes))
        assertFalse(ConverterEngine.looksLikeAsciiStl(bytes))
        val obj = ConverterEngine.stlToObj(bytes)
        assertEquals(6, Regex("^v ", RegexOption.MULTILINE).findAll(obj).count())
        assertEquals(2, Regex("^f ", RegexOption.MULTILINE).findAll(obj).count())
    }

    @Test
    fun truncatedBinaryStlIsRejectedInsteadOfSilentlyEmpty() {
        val bytes = ByteArray(84)
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).putInt(80, 1)
        assertFalse(ConverterEngine.isBinaryStl(bytes))
        val error = runCatching { ConverterEngine.stlToObj(bytes) }.exceptionOrNull()
        assertTrue(error is IllegalStateException)
    }

    // ------------------------------------------------------- Formatter JSON

    @Test
    fun formatCodeMinifiesAndPrettyPrintsJson() {
        val pretty = ConverterEngine.formatCode("{\"a\":1,\"b\":[2,3]}", "Pretty JSON")
        assertTrue(pretty.contains("\n"))
        val minified = ConverterEngine.formatCode(pretty, "Minify JSON")
        assertEquals("{\"a\":1,\"b\":[2,3]}", minified)
    }

    @Test
    fun formatCodePrettyCssAddsBreaks() {
        val css = ConverterEngine.formatCode("body{color:red;margin:0}", "Pretty CSS")
        assertTrue(css.contains("{\n  "))
        assertTrue(css.contains(";\n  "))
    }
}
