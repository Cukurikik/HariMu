package com.harimu.app

import com.harimu.app.core.engines.VideoEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoCalculationsTest {

    @Test
    fun cropRatioAndDimensions() {
        assertEquals(1f, VideoEngine.cropRatioValue("1:1"), 0.001f)
        val (w, h) = VideoEngine.cropDimensions(1920, 1080, "1:1")
        assertEquals(1080, w)
        assertEquals(1080, h)
        assertTrue(w % 2 == 0 && h % 2 == 0)
        assertEquals("crop=1080:1080:420:0", "crop=${w}:${h}:${(1920-w)/2}:${(1080-h)/2}")
    }

    @Test
    fun setptsAndAtempo() {
        assertEquals("setpts=PTS/1.5", VideoEngine.setptsFilter(1.5f))
        assertEquals(0.6666667f, VideoEngine.setptsFactor(1.5f), 0.0001f)
        assertTrue(VideoEngine.atempoFilter(3.0f).contains("atempo=2.0,1.5"))
    }

    @Test
    fun bitrateCalculation() {
        val kbps = VideoEngine.targetBitrateKbps(8_000_000L, 24)
        assertEquals(8000, kbps)
        assertTrue(VideoEngine.bitrateForResolution(1080, 28) < VideoEngine.bitrateForResolution(1080, 20))
    }
}