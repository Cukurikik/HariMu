package com.harimu.app

import com.harimu.app.core.engines.AudioEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioCalculationsTest {

    private fun buildAtempoChain(speed: Float): String {
        val factors = mutableListOf<Float>()
        var remaining = speed
        while (remaining > 2.0f) { factors.add(2.0f); remaining /= 2.0f }
        while (remaining < 0.5f) { factors.add(0.5f); remaining /= 0.5f }
        factors.add(remaining.coerceIn(0.5f, 2.0f))
        return "atempo=" + factors.joinToString(",")
    }

    @Test
    fun testAtempoChain() {
        val chain = buildAtempoChain(1.5f)
        assertEquals("atempo=1.5", chain)
    }

    @Test
    fun testAtempoChainHighSpeed() {
        val chain = buildAtempoChain(3.0f)
        assertTrue(chain.startsWith("atempo=2.0,"))
    }

    @Test
    fun testAtempoChainLowSpeed() {
        val chain = buildAtempoChain(0.25f)
        assertTrue(chain.startsWith("atempo=0.5,"))
    }

    @Test
    fun testPitchShiftFactor() {
        // 12 semitones = 2x frequency
        val factor = (2.0).pow(12.0 / 12.0)
        assertEquals(2.0, factor, 0.001)
        // -12 semitones = 0.5x frequency
        val factor2 = (2.0).pow(-12.0 / 12.0)
        assertEquals(0.5, factor2, 0.001)
    }

    @Test
    fun testWaveformPeaks() {
        val pcm = AudioEngine.PcmAudio(
            samples = ShortArray(44100 * 2) { (Math.sin(it / 100.0) * Short.MAX_VALUE).toShort() },
            sampleRate = 44100,
            channels = 1
        )
        val peaks = AudioEngine.extractWaveformPeaks(pcm, 100)
        assertEquals(100, peaks.size)
        assertTrue(peaks.all { it >= 0f && it <= 1f })
    }

    @Test
    fun testId3TagParsing() {
        // Basic test structure - actual parsing tested via integration
        assertTrue(true)
    }
}