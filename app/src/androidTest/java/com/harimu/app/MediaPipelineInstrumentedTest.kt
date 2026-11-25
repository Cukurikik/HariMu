package com.harimu.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.harimu.app.core.media.MediaProbe
import com.harimu.app.core.toolapi.ToolInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Media pipeline: probe, trim (VID-01), merge (VID-02), extract audio (VID-08), dan pembatalan job.
 *
 * Catatan: VID-01/VID-02 memakai Media3 lebih dulu dengan fallback FFmpeg; VID-08 selalu FFmpeg
 * (`libmp3lame`). Emulator tanpa dukungan FFmpegKit akan membuat tes VID-08 gagal — bukan bug tool.
 */
@RunWith(AndroidJUnit4::class)
class MediaPipelineInstrumentedTest {

    private val support = InstrumentedTestSupport

    @Before
    fun setUp() {
        support.clearTemp()
    }

    @Test
    fun mediaProbeReadsDurationAndResolution() {
        val probe = MediaProbe.probe(support.context, support.sampleUri("sample_video.mp4"))
        assertTrue("durasi ${probe.durationMs}ms tidak masuk akal", probe.durationMs in 1L..600_000L)
        assertTrue("lebar ${probe.width} harus > 0", probe.width > 0)
        assertTrue("tinggi ${probe.height} harus > 0", probe.height > 0)
        assertTrue("video sampel harus punya video track", probe.hasVideo)
    }

    @Test
    fun trimProducesOutputWithExpectedNameAndShorterDuration() = runBlocking {
        val source = support.sampleUri("sample_video.mp4")
        val before = MediaProbe.probe(support.context, source)

        val result = support.runTool(
            "VID-01",
            ToolInput(
                uris = listOf(source),
                params = mapOf("startSec" to 0f, "endSec" to 0.5f)
            )
        )
        val out = support.singleOutput(result)
        val after = MediaProbe.probeFile(out)

        assertEquals("mime keluaran", "video/mp4", result.outputs.first().mimeType)
        assertTrue("nama keluaran ${out.name}", out.name.startsWith("HariMu_VID_TRIM_"))
        assertTrue(
            "durasi hasil ${after.durationMs}ms",
            after.durationMs >= 1L && after.durationMs <= before.durationMs.coerceAtLeast(1L)
        )
    }

    @Test
    fun mergeConcatenatesTwoClipsIntoOneFile() = runBlocking {
        val source = support.sampleUri("sample_video.mp4")
        val before = MediaProbe.probe(support.context, source)

        val result = support.runTool("VID-02", ToolInput(uris = listOf(source, source)))
        val out = support.singleOutput(result)
        val after = MediaProbe.probeFile(out)

        assertTrue("nama keluaran ${out.name}", out.name.startsWith("HariMu_VID_MERGE_"))
        val expected = before.durationMs * 2
        assertTrue(
            "durasi gabungan ${after.durationMs}ms diharapkan sekitar ${expected}ms",
            after.durationMs >= (expected * 0.6).toLong() && after.durationMs <= (expected * 1.5).toLong()
        )
    }

    @Test
    fun extractAudioProducesMp3WithSimilarDuration() = runBlocking {
        val source = support.sampleUri("sample_video.mp4")
        val before = MediaProbe.probe(support.context, source)

        val result = support.runTool(
            "VID-08",
            ToolInput(uris = listOf(source), params = mapOf("bitrate" to "128k"))
        )
        val out = support.singleOutput(result)
        val after = MediaProbe.probeFile(out)

        assertEquals("audio/mpeg", result.outputs.first().mimeType)
        assertTrue("nama keluaran ${out.name}", out.name.startsWith("HariMu_VID_MP3_") && out.name.endsWith(".mp3"))
        assertTrue("durasi audio ${after.durationMs}ms", after.durationMs in 1L..600_000L)
        assertTrue(
            "durasi audio ${after.durationMs}ms vs video ${before.durationMs}ms",
            after.durationMs >= (before.durationMs * 0.6).toLong()
        )
    }

    @Test
    fun cancellingTrimLeavesNoTemporaryFiles() = runBlocking {
        support.clearTemp()
        val source = support.sampleUri("sample_video.mp4")

        val job = launch(Dispatchers.IO) {
            runCatching {
                support.toolFlow(
                    "VID-01",
                    ToolInput(uris = listOf(source), params = mapOf("startSec" to 0f, "endSec" to 0.5f))
                ).collect { }
            }
        }
        delay(250)
        job.cancelAndJoin()
        delay(500)

        val leftovers = support.storage.tempDir.listFiles().orEmpty().map { it.name }
        assertTrue("file temp tersisa setelah batal: $leftovers", leftovers.isEmpty())
    }
}
