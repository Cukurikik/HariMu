package com.harimu.app

import com.harimu.app.core.engines.ConverterEngine
import com.harimu.app.core.toolapi.ToolInput
import com.harimu.app.core.toolapi.ToolInputCodec
import com.harimu.app.core.toolapi.ToolOutputCodec
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Regresi audit v3: hardening XXE, sanitasi URL markdown, dan codec input WorkManager. */
@RunWith(RobolectricTestRunner::class)
class AuditV3HardeningTest {

    @Test
    fun xmlToJsonRejectsDoctypeDeclaration() {
        val xxe = """<?xml version="1.0"?>
            <!DOCTYPE foo [ <!ENTITY xxe SYSTEM "file:///etc/passwd"> ]>
            <root><value>&xxe;</value></root>""".trimIndent()
        val failure = runCatching { ConverterEngine.xmlToJson(xxe) }
        assertTrue("DOCTYPE harus ditolak", failure.isFailure)
    }

    @Test
    fun xmlToJsonStillParsesPlainDocuments() {
        val json = ConverterEngine.xmlToJson("<root><a>1</a><a>2</a><b>ok</b></root>")
        assertTrue(json.contains("\"b\""))
        assertTrue(json.contains("ok"))
    }

    @Test
    fun markdownDropsDangerousSchemes() {
        assertNull(ConverterEngine.safeUrl("javascript:alert(1)"))
        assertNull(ConverterEngine.safeUrl("JaVaScRiPt:alert(1)"))
        assertNull(ConverterEngine.safeUrl("data:text/html;base64,AAAA"))
        assertNull(ConverterEngine.safeUrl("file:///etc/passwd"))
        assertNull(ConverterEngine.safeUrl("//evil.example"))
        assertNull(ConverterEngine.safeUrl("https://evil.example/a b"))
        assertEquals("https://harimu.example/a.png", ConverterEngine.safeUrl("https://harimu.example/a.png"))
        assertEquals("mailto:a@b.c", ConverterEngine.safeUrl("mailto:a@b.c"))
    }

    @Test
    fun markdownRendersUnsafeLinkAsPlainText() {
        val html = ConverterEngine.inlineMd("[klik](javascript:alert(1))")
        assertFalse(html.contains("<a href"))
        assertTrue(html.contains("klik"))
    }

    @Test
    fun toolInputRoundTripPreservesParamsAndUris() {
        val input = ToolInput(
            uris = listOf(Uri.parse("content://media/1"), Uri.parse("content://media/2")),
            secondaryUri = Uri.parse("content://media/3"),
            textInput = "halo",
            params = mapOf("quality" to 80f, "loop" to true, "format" to "MP4", "count" to 3)
        )
        val decoded = ToolInputCodec.decode(ToolInputCodec.encode(input))
        assertEquals(2, decoded.uris.size)
        assertEquals("content://media/3", decoded.secondaryUri?.toString())
        assertEquals("halo", decoded.textInput)
        assertEquals(80f, decoded.getFloat("quality"), 0.001f)
        assertTrue(decoded.getBoolean("loop"))
        assertEquals("MP4", decoded.getString("format"))
        assertEquals(3, decoded.getInt("count"))
    }

    @Test
    fun toolInputDecodeToleratesGarbage() {
        val decoded = ToolInputCodec.decode("bukan-json")
        assertEquals(0, decoded.uris.size)
        assertEquals("", decoded.textInput)
    }

    @Test
    fun outputCodecRoundTrip() {
        val encoded = ToolOutputCodec.encode(
            listOf(ToolOutputCodec.Entry("/tmp/a.png", "image/png", "a.png"))
        )
        val decoded = ToolOutputCodec.decode(encoded)
        assertEquals(1, decoded.size)
        assertEquals("/tmp/a.png", decoded.first().path)
        assertEquals("image/png", decoded.first().mimeType)
    }
}
