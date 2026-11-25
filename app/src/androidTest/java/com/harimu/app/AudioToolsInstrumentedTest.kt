package com.harimu.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.harimu.app.core.media.MediaProbe
import com.harimu.app.core.toolapi.ToolInput
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Audio tools: AUD-13 (konverter), AUD-12 (loudnorm), AUD-16 (hapus hening), AUD-09 (potong).
 * Sumber: `sample_audio.wav`, sehingga keluaran default AUD-09/AUD-12/AUD-16 adalah WAV.
 */
@RunWith(AndroidJUnit4::class)
class AudioToolsInstrumentedTest {

    private val support = InstrumentedTestSupport
    private val sourceAudio get() = support.sampleUri("sample_audio.wav")

    @Before
    fun setUp() {
        support.clearTemp()
    }

    private fun sourceDurationMs(): Long =
        MediaProbe.probe(support.context, sourceAudio).durationMs

    @Test
    fun audioConverterProducesMp3() = runBlocking {
        val before = sourceDurationMs()
        val result = support.runTool(
            "AUD-13",
            ToolInput(
                uris = listOf(sourceAudio),
                params = mapOf("format" to "MP3", "bitrate" to "128k")
            )
        )
        val out = support.singleOutput(result)
        val probe = MediaProbe.probeFile(out)

        assertEquals("audio/mpeg", result.outputs.first().mimeType)
        assertTrue("nama keluaran ${out.name}", out.name.startsWith("HariMu_AUD_CONV_") && out.name.endsWith(".mp3"))
        assertTrue("durasi hasil ${probe.durationMs}ms vs sumber ${before}ms", probe.durationMs >= (before * 0.6).toLong())
    }

    @Test
    fun loudnessNormalizerKeepsDurationAndWritesWav() = runBlocking {
        val before = sourceDurationMs()
        val result = support.runTool(
            "AUD-12",
            ToolInput(
                uris = listOf(sourceAudio),
                params = mapOf("targetLufs" to "-16 LUFS (Podcast/Musik)")
            )
        )
        val out = support.singleOutput(result)
        val probe = MediaProbe.probeFile(out)

        assertTrue("nama keluaran ${out.name}", out.name.startsWith("HariMu_AUD_NORM_"))
        assertEquals("audio/wav", result.outputs.first().mimeType)
        assertTrue("durasi ${probe.durationMs}ms vs ${before}ms", probe.durationMs >= (before * 0.6).toLong())
        assertTrue("durasi ${probe.durationMs}ms vs ${before}ms", probe.durationMs <= (before * 1.5).toLong())
    }

    @Test
    fun silenceRemoverNeverGrowsTheFile() = runBlocking {
        val before = sourceDurationMs()
        val result = support.runTool(
            "AUD-16",
            ToolInput(
                uris = listOf(sourceAudio),
                params = mapOf("thresholdDb" to -40f, "minDurationSec" to 0.5f)
            )
        )
        val out = support.singleOutput(result)
        val probe = MediaProbe.probeFile(out)

        assertTrue("nama keluaran ${out.name}", out.name.startsWith("HariMu_AUD_SILENCE_"))
        assertTrue("hasil ${probe.durationMs}ms harus > 0", probe.durationMs > 0L)
        assertTrue(
            "hasil ${probe.durationMs}ms tidak boleh lebih panjang dari sumber ${before}ms (+300ms toleransi)",
            probe.durationMs <= before + 300L
        )
    }

    @Test
    fun trimAudioCutsRequestedRange() = runBlocking {
        val before = sourceDurationMs()
        val result = support.runTool(
            "AUD-09",
            ToolInput(
                uris = listOf(sourceAudio),
                params = mapOf("startSec" to 0f, "endSec" to 0.5f)
            )
        )
        val out = support.singleOutput(result)
        val probe = MediaProbe.probeFile(out)

        assertTrue("nama keluaran ${out.name}", out.name.startsWith("HariMu_AUD_TRIM_"))
        assertTrue("hasil ${probe.durationMs}ms harus > 0", probe.durationMs > 0L)
        // endSec 0.5 dapat terpotong ke durasi sumber bila sampel lebih pendek dari 1 detik.
        val upper = if (before >= 1_000L) 1_200L else before
        assertTrue("hasil ${probe.durationMs}ms melebihi batas ${upper}ms", probe.durationMs <= upper)
    }
}
