package com.harimu.app

import android.app.Application
import android.content.Context
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.harimu.app.core.engines.AudioEngine
import com.harimu.app.core.engines.PcmAudio
import com.harimu.app.core.files.FileStorageManager
import com.harimu.app.core.media.AudioEncoderCapabilities
import com.harimu.app.core.media.AudioToolMath
import com.harimu.app.core.media.AudioWaveformExtractor
import com.harimu.app.core.media.FfmpegSessionHandle
import com.harimu.app.core.media.FfmpegSessionRunner
import com.harimu.app.core.media.Id3Metadata
import com.harimu.app.core.media.Id3TagHelper
import com.harimu.app.core.media.MediaJob
import com.harimu.app.core.toolapi.ToolContext
import com.harimu.app.core.toolapi.ToolGroup
import com.harimu.app.core.toolapi.ToolInput
import com.harimu.app.core.toolapi.ToolProgress
import com.harimu.app.core.toolapi.ToolRegistry
import com.harimu.app.feature.tools.audioForgeBatch1CoreTools
import com.harimu.app.feature.tools.audioForgeBatch1ExtraTools
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowMediaExtractor
import org.robolectric.shadows.ShadowMediaMetadataRetriever
import org.robolectric.shadows.ShadowStatFs
import org.robolectric.shadows.util.DataSource
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipFile
import kotlin.math.PI
import kotlin.math.sin

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class AudioForgeBatch1Test {

    private lateinit var context: Context
    private lateinit var storage: FileStorageManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        storage = FileStorageManager(context)
        ShadowStatFs.registerStats(storage.outputsDir.absolutePath, 2 * 1024 * 1024, 1024 * 1024, 1024 * 1024)
        AudioWaveformExtractor.clearMemoryCache()
    }

    private fun createSampleWavFile(name: String = "sample_audio.wav", durationSec: Int = 2): File {
        val sampleRate = 44_100
        val count = sampleRate * durationSec
        val samples = ShortArray(count) { i ->
            (sin(2.0 * PI * 440.0 * i / sampleRate) * 18_000.0).toInt().toShort()
        }
        val out = File(context.cacheDir, name)
        AudioEngine.writeWav(PcmAudio(samples, sampleRate, 1), out)
        return out
    }

    private fun registerShadowAudio(uri: Uri, durationMs: Long = 12_000L, sampleRate: Int = 44_100, channels: Int = 2) {
        val ds = DataSource.toDataSource(context, uri)
        ShadowMediaMetadataRetriever.addMetadata(ds, MediaMetadataRetriever.METADATA_KEY_DURATION, durationMs.toString())
        ShadowMediaMetadataRetriever.addMetadata(ds, MediaMetadataRetriever.METADATA_KEY_BITRATE, "192000")
        val fmt = MediaFormat.createAudioFormat("audio/mpeg", sampleRate, channels).apply {
            setLong(MediaFormat.KEY_DURATION, durationMs * 1000L)
        }
        ShadowMediaExtractor.addTrack(ds, fmt, ByteArray(0))
    }

    @Test
    fun registryActivatesAll15Batch1AudioToolsAndKeepsP7P8P11Stubs() {
        assertEquals(176, ToolRegistry.allTools.size)
        assertEquals(28, ToolRegistry.byGroup(ToolGroup.AUDIO).size)

        val batch1Ids = listOf(
            "AUD-06", "AUD-07", "AUD-08", "AUD-09", "AUD-10", "AUD-11", "AUD-12", "AUD-13",
            "AUD-14", "AUD-15", "AUD-16", "AUD-17", "AUD-18", "AUD-23", "AUD-26"
        )
        batch1Ids.forEach { id ->
            val tool = ToolRegistry.findById(id)
            assertTrue("Expected $id to be active (not stub)", tool != null && !tool.isStub)
        }

        // FASE 11: AUD-20 dan AUD-27 sudah aktif.
        listOf("AUD-20", "AUD-27").forEach { id ->
            val tool = ToolRegistry.findById(id)
            assertTrue("Expected $id implemented in Fase 11", tool != null && !tool.isStub)
        }

        val aud21 = ToolRegistry.findById("AUD-21")
        assertTrue("Expected AUD-21 to be active in Phase 8", aud21 != null && !aud21.isStub)
    }

    @Test
    fun pitchShiftAndSpeedRatioMathPreserveDurationAndClampAtempo() {
        // +12 semitones -> double rate, half tempo compensation -> net duration invariant
        assertEquals(2.0, AudioToolMath.semitonesToPitchRatio(12f), 0.0001)
        assertEquals(0.5, AudioToolMath.semitonesToPitchRatio(-12f), 0.0001)
        assertEquals(1.0, AudioToolMath.semitonesToPitchRatio(0f), 0.0001)

        val upFilter = AudioToolMath.buildPitchShiftFilter(12f, 44_100)
        assertEquals("asetrate=88200,aresample=44100,atempo=0.5000", upFilter)

        val downFilter = AudioToolMath.buildPitchShiftFilter(-12f, 48_000)
        assertEquals("asetrate=24000,aresample=48000,atempo=2.0000", downFilter)

        assertEquals("anull", AudioToolMath.buildPitchShiftFilter(0f, 44_100))

        // Speed chaining
        assertEquals("atempo=2.0000,atempo=1.5000", AudioToolMath.buildAudioAtempoChain(3.0f))
        assertEquals("atempo=0.5000,atempo=0.5000", AudioToolMath.buildAudioAtempoChain(0.25f))
        assertEquals(6000L, AudioToolMath.computeAdjustedDurationMs(12_000L, 2.0f))
        assertEquals(24_000L, AudioToolMath.computeAdjustedDurationMs(12_000L, 0.5f))
    }

    @Test
    fun encoderValidationProvidesClearMessageAndFormatSuggestions() {
        val limitedCapabilities = object : AudioEncoderCapabilities {
            override fun isEncoderAvailable(codec: String, mimeType: String): Boolean =
                codec != "libvorbis" // simulate OGG encoder unavailable
            override fun availableFormatLabels(): List<String> = listOf("MP3", "WAV", "FLAC")
        }

        val oggMissing = AudioToolMath.validateOutputFormatAndRate(
            formatLabel = "OGG",
            capabilities = limitedCapabilities,
            isEnglish = false
        )
        assertFalse(oggMissing.valid)
        assertTrue(oggMissing.errorMessage!!.contains("libvorbis"))
        assertTrue(oggMissing.errorMessage!!.contains("MP3, WAV, FLAC"))

        val mp3HighRate = AudioToolMath.validateOutputFormatAndRate(
            formatLabel = "MP3",
            sampleRateHz = 96_000,
            isEnglish = true
        )
        assertFalse(mp3HighRate.valid)
        assertTrue(mp3HighRate.errorMessage!!.contains("48000"))
        assertTrue(mp3HighRate.errorMessage!!.contains("WAV or FLAC"))

        val unknownFormat = AudioToolMath.validateOutputFormatAndRate("MIDI", isEnglish = true)
        assertFalse(unknownFormat.valid)
        assertTrue(unknownFormat.errorMessage!!.contains("alternative", ignoreCase = true) || unknownFormat.errorMessage!!.contains("Try one of"))
    }

    @Test
    fun loudnormStatsParserAndPass2BuilderWorkCleanly() {
        val sampleLogs = """
            [Parsed_loudnorm_0 @ 0x7b00]
            {
                "input_i" : "-21.45",
                "input_tp" : "-3.20",
                "input_lra" : "6.80",
                "input_thresh" : "-31.90",
                "output_i" : "-16.02",
                "output_tp" : "-1.50",
                "output_lra" : "6.40",
                "output_thresh" : "-26.30",
                "normalization_type" : "linear",
                "target_offset" : "0.02"
            }
        """.trimIndent()

        val parsed = AudioToolMath.parseLoudnormStats(sampleLogs)
        assertNotNull(parsed)
        assertEquals(-21.45, parsed!!.inputI, 0.01)
        assertEquals(-3.20, parsed.inputTp, 0.01)
        assertEquals(6.80, parsed.inputLra, 0.01)
        assertEquals(-31.90, parsed.inputThresh, 0.01)
        assertEquals(0.02, parsed.targetOffset, 0.01)

        val pass2 = AudioToolMath.buildLoudnormPass2Filter(parsed, targetLufs = -16.0)
        assertTrue(pass2.contains("measured_I=-21.45"))
        assertTrue(pass2.contains("linear=true"))

        val fallback = AudioToolMath.buildLoudnormPass2Filter(null, targetLufs = -16.0)
        assertEquals("loudnorm=I=-16.0:TP=-1.5:LRA=11.0", fallback)
    }

    @Test
    fun id3ParserAndWriterRoundTripMetadataAndCoverArtLosslessly() {
        val srcMp3 = File(context.cacheDir, "fake_audio_frames.mp3")
        val audioPayload = ByteArray(1024) { (it % 251).toByte() }.apply {
            // MP3 frame sync signature simulation at start of audio payload
            this[0] = 0xFF.toByte()
            this[1] = 0xFB.toByte()
        }
        srcMp3.writeBytes(audioPayload)

        val fakePngCover = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
            0x01, 0x02, 0x03, 0x04
        )
        val inputMeta = Id3Metadata(
            title = "Senja di Jakarta",
            artist = "Andika Studio",
            album = "HariMu Sessions",
            year = "2026",
            genre = "Acoustic",
            coverBytes = fakePngCover,
            coverMime = "image/png"
        )

        val taggedMp3 = File(context.cacheDir, "tagged_out.mp3")
        Id3TagHelper.writeId3v23Mp3(srcMp3, taggedMp3, inputMeta)

        val readBack = Id3TagHelper.readTags(taggedMp3)
        assertEquals("Senja di Jakarta", readBack.title)
        assertEquals("Andika Studio", readBack.artist)
        assertEquals("HariMu Sessions", readBack.album)
        assertEquals("2026", readBack.year)
        assertEquals("Acoustic", readBack.genre)
        assertEquals("image/png", readBack.coverMime)
        assertArrayEquals(fakePngCover, readBack.coverBytes)

        // Verify audio frames following ID3 tag remain untouched byte-for-byte
        val tagLen = Id3TagHelper.detectExistingId3v2Length(taggedMp3).toInt()
        assertTrue(tagLen > 10)
        val allBytes = taggedMp3.readBytes()
        val trailingAudio = allBytes.copyOfRange(tagLen, allBytes.size)
        assertArrayEquals(audioPayload, trailingAudio)

        // Overwrite tags with updated title and confirm no duplicate ID3 header grows audio
        val reTaggedMp3 = File(context.cacheDir, "retagged_out.mp3")
        Id3TagHelper.writeId3v23Mp3(taggedMp3, reTaggedMp3, inputMeta.copy(title = "Pagi Cerah", coverBytes = null))
        val readBack2 = Id3TagHelper.readTags(reTaggedMp3)
        assertEquals("Pagi Cerah", readBack2.title)
        val tagLen2 = Id3TagHelper.detectExistingId3v2Length(reTaggedMp3).toInt()
        val reTaggedBytes = reTaggedMp3.readBytes()
        assertArrayEquals(audioPayload, reTaggedBytes.copyOfRange(tagLen2, reTaggedBytes.size))
    }

    @Test
    fun waveformWavExtractorProducesNormalizedBars() {
        val wavFile = createSampleWavFile("wave_test.wav", durationSec = 1)
        val data = wavFile.inputStream().use { AudioWaveformExtractor.extractPeaksFromWavStream(it, barCount = 48) }
        assertNotNull(data)
        assertEquals(48, data!!.peaks.size)
        assertEquals(44_100, data.sampleRateHz)
        assertTrue(data.peaks.all { it in 0.08f..1.0f })
    }

    @Test
    fun batch1AudioPipelinesExecuteLoudnormMergeResampleId3AndSplit() = runBlocking {
        val wav1 = createSampleWavFile("track_1.wav", durationSec = 2)
        val wav2 = createSampleWavFile("track_2.wav", durationSec = 2)
        val uri1 = Uri.fromFile(wav1)
        val uri2 = Uri.fromFile(wav2)
        registerShadowAudio(uri1, durationMs = 60_000L, sampleRate = 44_100, channels = 2)
        registerShadowAudio(uri2, durationMs = 40_000L, sampleRate = 48_000, channels = 1)

        val recordedCommands = mutableListOf<List<String>>()
        val fakeFfmpeg = MediaJob(runner = object : FfmpegSessionRunner {
            override fun executeAsync(
                arguments: List<String>,
                onLog: (String) -> Unit,
                onTimeMs: (Double) -> Unit,
                onComplete: (Int?, Boolean, Boolean, String) -> Unit
            ): FfmpegSessionHandle {
                recordedCommands.add(arguments)
                val isNullSink = arguments.contains("null") && arguments.last() == "-"
                if (isNullSink) {
                    val jsonLog = """
                        {
                            "input_i" : "-19.50",
                            "input_tp" : "-2.10",
                            "input_lra" : "5.20",
                            "input_thresh" : "-29.80",
                            "target_offset" : "0.10"
                        }
                    """.trimIndent()
                    onLog(jsonLog)
                    onComplete(0, true, false, jsonLog)
                } else {
                    val outPath = arguments.last()
                    File(outPath).writeBytes(byteArrayOf(0x49, 0x44, 0x33, 0x03, 0x00, 0x01))
                    onTimeMs(500.0)
                    onComplete(0, true, false, "ok")
                }
                return object : FfmpegSessionHandle {
                    override val sessionId: Long = 10L
                    override fun cancel() = Unit
                }
            }
        })

        val coreTools = audioForgeBatch1CoreTools(fakeFfmpeg).associateBy { it.id }
        val extraTools = audioForgeBatch1ExtraTools(fakeFfmpeg).associateBy { it.id }
        val ctx = ToolContext(context, storage, isEnglish = true)

        // AUD-06 Pitch (+5 semitones)
        val pitchEvents = coreTools.getValue("AUD-06").run(ctx, ToolInput(uris = listOf(uri1), params = mapOf("semitones" to 5f))).toList()
        assertTrue(pitchEvents.last() is ToolProgress.Done)
        assertTrue(recordedCommands.last().any { it.contains("asetrate=") && it.contains("atempo=") })

        // AUD-10 Merge Mix
        recordedCommands.clear()
        val mergeEvents = coreTools.getValue("AUD-10").run(ctx, ToolInput(uris = listOf(uri1, uri2), params = mapOf("mode" to "Campur (Mix)"))).toList()
        assertTrue(mergeEvents.last() is ToolProgress.Done)
        assertTrue(recordedCommands.last().any { it.contains("amix=inputs=2") })

        // AUD-12 Loudnorm 2-pass
        recordedCommands.clear()
        val loudEvents = coreTools.getValue("AUD-12").run(ctx, ToolInput(uris = listOf(uri1))).toList()
        val loudDone = loudEvents.last() as ToolProgress.Done
        assertEquals(2, recordedCommands.size)
        assertTrue(recordedCommands[0].any { it.contains("print_format=json") })
        assertTrue(recordedCommands[1].any { it.contains("measured_I=-19.50") && it.contains("linear=true") })
        assertTrue(loudDone.result.summary.contains("2-pass"))

        // AUD-14 Resampler rejects MP3 @ 96kHz cleanly with clear suggestion
        val badResample = extraTools.getValue("AUD-14").run(
            ctx, ToolInput(uris = listOf(uri1), params = mapOf("rate" to "96000", "format" to "MP3"))
        ).toList().last() as ToolProgress.Failed
        assertTrue(badResample.error.contains("WAV or FLAC"))

        // AUD-16 Silence Remover uses thresholdDb + minDurationSec
        recordedCommands.clear()
        val silenceEvents = extraTools.getValue("AUD-16").run(
            ctx, ToolInput(uris = listOf(uri1), params = mapOf("thresholdDb" to -35f, "minDurationSec" to 0.8f))
        ).toList()
        assertTrue(silenceEvents.last() is ToolProgress.Done)
        assertTrue(recordedCommands.last().any { it.contains("stop_duration=0.80") && it.contains("-35.0dB") })

        // AUD-23 Splitter packs 3 parts into ZIP
        val splitEvents = extraTools.getValue("AUD-23").run(
            ctx, ToolInput(uris = listOf(uri1), params = mapOf("mode" to "Jumlah Bagian", "partsCount" to 3f))
        ).toList()
        val splitDone = splitEvents.last() as ToolProgress.Done
        ZipFile(splitDone.result.outputs.first().file).use { z -> assertEquals(3, z.size()) }
    }
}
