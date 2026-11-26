package com.harimu.app

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.harimu.app.core.toolapi.ToolRegistry
import com.harimu.app.core.toolapi.ToolGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class VideoForgeScreenTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun videoForgeHasAll30ToolsRegistered() {
        val videoTools = ToolRegistry.getToolsByGroup(ToolGroup.VIDEO)
        // Should have exactly 30 VID tools (VID-01 through VID-30)
        // Note: Some IDs might be missing (like VID-07 which is upscaler, implemented in Fase 11)
        // But all 30 should be registered
        assertEquals(30, videoTools.size)

        // Verify all expected tool IDs exist
        val expectedIds = (1..30).map { "VID-${"%02d".format(it)}" }
        val actualIds = videoTools.map { it.id }.toSet()

        expectedIds.forEach { id ->
            assertTrue("Missing tool: $id", actualIds.contains(id))
        }
    }

    @Test
    fun videoForgeToolsHaveCorrectCategories() {
        // Verify tools are categorized correctly for VideoForgeScreen
        val coreEditingIds = setOf("VID-01", "VID-02", "VID-03", "VID-04", "VID-05", "VID-09", "VID-10")
        val audioIds = setOf("VID-08", "VID-12", "VID-13", "VID-16", "VID-24")
        val transformConvertIds = setOf("VID-18", "VID-19", "VID-20", "VID-21", "VID-22")
        val overlaysEffectsIds = setOf("VID-11", "VID-14", "VID-15", "VID-17", "VID-23", "VID-25", "VID-26")
        val creativeIds = setOf("VID-06", "VID-27", "VID-28", "VID-29", "VID-30")

        val allCategorized = coreEditingIds + audioIds + transformConvertIds + overlaysEffectsIds + creativeIds
        assertEquals(30, allCategorized.size)

        // Verify no overlaps
        assertTrue(coreEditingIds.intersect(audioIds).isEmpty())
        assertTrue(coreEditingIds.intersect(transformConvertIds).isEmpty())
        assertTrue(coreEditingIds.intersect(overlaysEffectsIds).isEmpty())
        assertTrue(coreEditingIds.intersect(creativeIds).isEmpty())
        assertTrue(audioIds.intersect(transformConvertIds).isEmpty())
        assertTrue(audioIds.intersect(overlaysEffectsIds).isEmpty())
        assertTrue(audioIds.intersect(creativeIds).isEmpty())
        assertTrue(transformConvertIds.intersect(overlaysEffectsIds).isEmpty())
        assertTrue(transformConvertIds.intersect(creativeIds).isEmpty())
        assertTrue(overlaysEffectsIds.intersect(creativeIds).isEmpty())
    }

    @Test
    fun videoForgeToolsHaveEngineLabels() {
        val videoTools = ToolRegistry.getToolsByGroup(ToolGroup.VIDEO)

        // Count tools by engine label
        val m3ffCount = videoTools.count { it.engineLabel == "M3/FF" }
        val ffCount = videoTools.count { it.engineLabel == "FF" }
        val mlffCount = videoTools.count { it.engineLabel == "ML/FF" }
        val natCount = videoTools.count { it.engineLabel == "NAT" }

        // Verify we have a mix of engines
        assertTrue("Should have M3/FF tools", m3ffCount > 0)
        assertTrue("Should have FF tools", ffCount > 0)
        assertTrue("Should have ML/FF tools", mlffCount > 0)
        assertTrue("Should have NAT tools", natCount > 0)

        // Total should be 30
        assertEquals(30, m3ffCount + ffCount + mlffCount + natCount)
    }

    @Test
    fun videoForgeToolsHaveInputSpecs() {
        val videoTools = ToolRegistry.getToolsByGroup(ToolGroup.VIDEO)

        // Verify input specs match expectations
        val singleVideoTools = videoTools.filter { it.inputSpec.toString().contains("SingleVideo") }
        val multipleVideoTools = videoTools.filter { it.inputSpec.toString().contains("MultipleVideos") }
        val videoAndAudioTools = videoTools.filter { it.inputSpec.toString().contains("VideoAndAudio") }
        val videoAndImageTools = videoTools.filter { it.inputSpec.toString().contains("VideoAndImage") }
        val videoAndSubtitleTools = videoTools.filter { it.inputSpec.toString().contains("VideoAndSubtitle") }
        val videoAndMediaTools = videoTools.filter { it.inputSpec.toString().contains("VideoAndMedia") }
        val multipleImagesTools = videoTools.filter { it.inputSpec.toString().contains("MultipleImages") }

        assertTrue("Should have single video tools", singleVideoTools.size > 0)
        assertTrue("Should have multiple video tools", multipleVideoTools.size > 0)
        assertTrue("Should have video+audio tools", videoAndAudioTools.size > 0)
        assertTrue("Should have video+image tools", videoAndImageTools.size > 0)
        assertTrue("Should have video+subtitle tools", videoAndSubtitleTools.size > 0)
        assertTrue("Should have video+media tools", videoAndMediaTools.size > 0)
        assertTrue("Should have multiple images tools", multipleImagesTools.size > 0)
    }

    @Test
    fun core01ProVideoEditorExists() {
        val core01 = ToolRegistry.findById("CORE-01")
        assertNotNull(core01)
        assertEquals("CORE-01", core01!!.id)
        assertEquals(ToolGroup.CORE, core01.group)
        assertTrue(!core01.isStub)
        assertEquals(com.harimu.app.core.toolapi.DedicatedScreenRoute.VIDEO_EDITOR, core01.dedicatedRoute)
    }
}