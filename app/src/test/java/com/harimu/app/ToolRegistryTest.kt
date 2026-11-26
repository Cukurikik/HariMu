package com.harimu.app

import com.harimu.app.core.toolapi.ToolGroup
import com.harimu.app.core.toolapi.ToolRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolRegistryTest {

    @Test
    fun registryContainsAll169CatalogEntriesAcrossSixGroups() {
        val all = ToolRegistry.allTools
        assertEquals("Catalog must register all 169 tools", 169, all.size)
        assertEquals("All tool IDs must be unique", 169, all.map { it.id }.toSet().size)

        assertEquals(15, ToolRegistry.countByGroup(ToolGroup.CORE))
        assertEquals(30, ToolRegistry.countByGroup(ToolGroup.VIDEO))
        assertEquals(28, ToolRegistry.countByGroup(ToolGroup.AUDIO))
        assertEquals(47, ToolRegistry.countByGroup(ToolGroup.IMAGE))
        assertEquals(21, ToolRegistry.countByGroup(ToolGroup.PDF))
        assertEquals(28, ToolRegistry.countByGroup(ToolGroup.CONVERTER))
    }

    @Test
    fun noToolIdIsRegisteredTwice() {
        // Regresi Fase 15: CORE-05/06/07 pernah dipakai dua kali sehingga AI Chat, Terjemah, dan
        // Cuaca hilang diam-diam dari registry karena `distinctBy`.
        assertEquals(
            "Duplicate tool IDs: ${ToolRegistry.duplicateIds()}",
            emptyList<String>(),
            ToolRegistry.duplicateIds()
        )
    }

    @Test
    fun everyCatalogIdFrom02KatalogFiturIsResolvable() {
        val expectedIds = buildList {
            (1..15).forEach { add("CORE-%02d".format(it)) }
            (1..30).forEach { add("VID-%02d".format(it)) }
            (1..28).forEach { add("AUD-%02d".format(it)) }
            (1..47).forEach { add("IMG-%02d".format(it)) }
            (1..21).forEach { add("PDF-%02d".format(it)) }
            (1..28).forEach { add("CNV-%02d".format(it)) }
        }
        assertEquals(169, expectedIds.size)
        expectedIds.forEach { id ->
            val tool = ToolRegistry.findById(id)
            assertNotNull("Missing tool $id in ToolRegistry", tool)
            assertTrue("Title ID missing for $id", tool!!.title(isEnglish = false).isNotBlank())
            assertTrue("Title EN missing for $id", tool.title(isEnglish = true).isNotBlank())
            assertTrue("Description ID missing for $id", tool.description(isEnglish = false).isNotBlank())
            assertTrue("Description EN missing for $id", tool.description(isEnglish = true).isNotBlank())
        }
    }

    @Test
    fun searchFindsBothImplementedAndStubToolsInIndonesianAndEnglish() {
        val core02 = ToolRegistry.search("CORE-02", isEnglish = false)
        assertEquals(1, core02.size)
        assertEquals("CORE-02", core02.first().id)
        assertTrue(!core02.first().isStub)  // FASE 11: CORE-02 implemented (cloud)

        val speedTool = ToolRegistry.search("Change Video Speed", isEnglish = true, groupFilter = ToolGroup.VIDEO)
        assertTrue(speedTool.any { it.id == "VID-05" && !it.isStub })

        val upscalerStub = ToolRegistry.search("Video Upscaler", isEnglish = true, groupFilter = ToolGroup.VIDEO)
        assertTrue(upscalerStub.any { it.id == "VID-07" && !it.isStub })  // FASE 11: FFmpeg upscale

        val extractAudio = ToolRegistry.search("Video to Audio", isEnglish = true, groupFilter = ToolGroup.VIDEO)
        assertTrue(extractAudio.any { it.id == "VID-08" && !it.isStub })

        val resize = ToolRegistry.search("Ubah Ukuran", isEnglish = false, groupFilter = ToolGroup.IMAGE)
        assertTrue(resize.any { it.id == "IMG-02" && !it.isStub })

        val crop = ToolRegistry.search("Crop", isEnglish = false, groupFilter = ToolGroup.IMAGE)
        assertTrue(crop.any { it.id == "IMG-01" && !it.isStub })

        val pdfEncrypt = ToolRegistry.search("Enkripsi PDF", isEnglish = false, groupFilter = ToolGroup.PDF)
        assertTrue(pdfEncrypt.any { it.id == "PDF-10" && !it.isStub })  // FASE 12: PDFBox encryption

        val pdfMerge = ToolRegistry.search("Gabung PDF", isEnglish = false, groupFilter = ToolGroup.PDF)
        assertTrue(pdfMerge.any { it.id == "PDF-01" && !it.isStub })

        val pdfRedact = ToolRegistry.search("Redaksi", isEnglish = false, groupFilter = ToolGroup.PDF)
        assertTrue(pdfRedact.any { it.id == "PDF-14" && !it.isStub })

        val base64 = ToolRegistry.search("Base64", isEnglish = true, groupFilter = ToolGroup.CONVERTER)
        assertTrue(base64.any { it.id == "CNV-12" && !it.isStub }) // FASE 13: alias IMG-25

        val archive = ToolRegistry.search("Cross-Archive", isEnglish = true, groupFilter = ToolGroup.CONVERTER)
        assertTrue(archive.any { it.id == "CNV-17" && !it.isStub })

        val font = ToolRegistry.search("Web-Font", isEnglish = true, groupFilter = ToolGroup.CONVERTER)
        assertTrue(font.any { it.id == "CNV-22" && !it.isStub })

        val converterStubs = ToolRegistry.byGroup(ToolGroup.CONVERTER).filter { it.isStub }
        assertTrue("All converter tools must be implemented in Fase 13: $converterStubs", converterStubs.isEmpty())
    }
}
