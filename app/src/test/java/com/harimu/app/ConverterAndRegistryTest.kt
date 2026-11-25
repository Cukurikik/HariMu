package com.harimu.app

import com.harimu.app.core.engines.ConverterEngine
import com.harimu.app.core.toolapi.ToolGroup
import com.harimu.app.core.toolapi.ToolRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ConverterAndRegistryTest {

    @Test
    fun registryContainsAllSixGroups() {
        val tools = ToolRegistry.allTools
        assertTrue(tools.size >= 50)
        ToolGroup.entries.forEach { group ->
            assertTrue("Group $group should have tools", ToolRegistry.countByGroup(group) > 0)
        }
        assertEquals(tools.size, tools.map { it.id }.toSet().size)
    }

    @Test
    fun jsonXmlRoundTripWorks() {
        val json = """{"app":"HariMu","version":"1"}"""
        val xml = ConverterEngine.jsonToXml(json)
        assertTrue(xml.contains("<app>HariMu</app>"))
        val back = ConverterEngine.xmlToJson(xml)
        assertTrue(back.contains("HariMu"))
    }

    @Test
    fun csvTsvConversionPreservesQuotedCommas() {
        val csv = "id,name\n1,\"Hello, World\""
        val tsv = ConverterEngine.csvToTsv(csv)
        assertEquals("id\tname\n1\tHello, World", tsv)
        val back = ConverterEngine.tsvToCsv(tsv)
        assertEquals(csv, back)
    }

    @Test
    fun markdownAndGeoJsonConvertCleanly() {
        val html = ConverterEngine.markdownToHtml("# Judul\n\nTeks **tebal**")
        assertTrue(html.contains("<h1>Judul</h1>"))
        assertTrue(html.contains("<strong>tebal</strong>"))

        val kml = ConverterEngine.geoJsonToKml(
            """{"type":"Point","coordinates":[106.82,-6.17]}"""
        )
        assertTrue(kml.contains("<coordinates>106.82,-6.17,0.0</coordinates>"))
    }

    @Test
    fun binaryStlConvertsToObj() {
        val buf = ByteBuffer.allocate(84 + 50).order(ByteOrder.LITTLE_ENDIAN)
        buf.position(80)
        buf.putInt(1)
        repeat(3) { buf.putFloat(0f) } // normal
        buf.putFloat(1f); buf.putFloat(2f); buf.putFloat(3f)
        buf.putFloat(4f); buf.putFloat(5f); buf.putFloat(6f)
        buf.putFloat(7f); buf.putFloat(8f); buf.putFloat(9f)
        buf.putShort(0)
        val obj = ConverterEngine.stlToObj(buf.array())
        assertTrue(obj.contains("v 1.0 2.0 3.0"))
        assertTrue(obj.contains("f 1 2 3"))
    }
}
