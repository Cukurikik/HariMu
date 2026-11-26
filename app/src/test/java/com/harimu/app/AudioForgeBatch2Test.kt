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
import com.harimu.app.core.files.ModelManager
import com.harimu.app.core.files.ModelRegistry
import com.harimu.app.core.media.AudioBatch2Math
import com.harimu.app.core.media.DeferredStemSplitterEngine
import com.harimu.app.core.media.FfmpegSessionHandle
import com.harimu.app.core.media.FfmpegSessionRunner
import com.harimu.app.core.media.MediaJob
import com.harimu.app.core.media.MediaProbeResult
import com.harimu.app.core.media.StemExecutionMode
import com.harimu.app.core.media.StemReadiness
import com.harimu.app.core.media.StemSplitRequest
import com.harimu.app.core.toolapi.ToolContext
import com.harimu.app.core.toolapi.ToolGroup
import com.harimu.app.core.toolapi.ToolInput
import com.harimu.app.core.toolapi.ToolProgress
import com.harimu.app.core.toolapi.ToolRegistry
import com.harimu.app.feature.tools.audioForgeBatch2CoreTools
import com.harimu.app.feature.tools.audioForgeBatch2ExtraTools
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
import java.io.File
import kotlin.math.PI
import kotlin.math.sin

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class AudioForgeBatch2Test {

    private lateinit var context: Context
    private lateinit var storage: FileStorageManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        storage = FileStorageManager(context)
        ShadowStatFs.registerStats(storage.outputsDir.absolutePath, 2 * 1024 * 1024, 1024 * 1024, 1024 * 1024)
    }

    private fun createSampleWav(name: String): File {
        val sampleRate = 44_100
        val count = sampleRate * 2
        val samples = ShortArray(count) { i ->
            (sin(2.0 * PI * 440.0 * i / sampleRate) * 16_000.0).toInt().toShort()
        }
        val out = File(context.cacheDir, name)
        AudioEngine.writeWav(PcmAudio(samples, sampleRate, 1), out)
        return out
    }

    private fun registerShadowAudio(uri: Uri, durationMs: Long = 10_000L, sampleRate: Int = 44_100, channels: Int = 2) {
        val ds = DataSource.toDataSource(context, uri)
        ShadowMediaMetadataRetriever.addMetadata(ds, MediaMetadataRetriever.METADATA_KEY_DURATION, durationMs.toString())
        ShadowMediaMetadataRetriever.addMetadata(ds, MediaMetadataRetriever.METADATA_KEY_BITRATE, "192000")
        val fmt = MediaFormat.createAudioFormat("audio/mpeg", sampleRate, channels).apply {
            setLong(MediaFormat.KEY_DURATION, durationMs * 1000L)
        }
        ShadowMediaExtractor.addTrack(ds, fmt, ByteArray(0))
    }

    @Test
    fun registryActivatesAllFase7AudioToolsAndDefersOnlyP8AndP11() {
        assertEquals(182, ToolRegistry.allTools.size)
        assertEquals(28, ToolRegistry.byGroup(ToolGroup.AUDIO).size)

        val fase7And8ActiveIds = listOf(
            "AUD-01", "AUD-02", "AUD-03", "AUD-04", "AUD-05",
            "AUD-19", "AUD-21", "AUD-22", "AUD-24", "AUD-25", "AUD-28"
        )
        fase7And8ActiveIds.forEach { id ->
            val tool = ToolRegistry.findById(id)
            assertTrue("Expected $id to be active", tool != null && !tool.isStub)
        }

        // FASE 11: AUD-20 (mid/side DSP) dan AUD-27 (cloud STT) sudah terimplementasi.
        listOf("AUD-20", "AUD-27").forEach { id ->
            val tool = ToolRegistry.findById(id)
            assertTrue("Expected $id to be implemented in Fase 11", tool != null && !tool.isStub)
        }
    }

    @Test
    fun equalizer10BandFfmpegFilterAndLiveParityMathMatchClosely() {
        val bassPreset = AudioBatch2Math.EQ_10_PRESETS.first { it.id == "bass" }.gainsDb
        val filter = AudioBatch2Math.buildEqualizer10BandFfmpegFilter(bassPreset, preampDb = -1.5f, enableLimiter = true)

        assertTrue(filter.contains("equalizer=f=31:t=o:w=1.0:g=6.00"))
        assertTrue(filter.contains("equalizer=f=62:t=o:w=1.0:g=5.50"))
        assertTrue(filter.contains("equalizer=f=125:t=o:w=1.0:g=4.00"))
        assertTrue(filter.contains("volume="))
        assertTrue(filter.contains("alimiter=limit=0.9441"))

        val dpParity = AudioBatch2Math.computeEqParityReport(
            gainsDb = bassPreset,
            using10BandDynamicsProcessing = true,
            isEnglish = true
        )
        assertEquals(0f, dpParity.maxDeltaDb, 0.001f)
        assertEquals(10, dpParity.liveBandCount)

        // Fallback 5-band Equalizer interpolation checks
        val fallbackFreqs = listOf(60, 230, 910, 3600, 14000)
        val mapped = AudioBatch2Math.map10BandsToDeviceEqLevels(
            gainsDb = bassPreset,
            deviceCenterFreqsHz = fallbackFreqs,
            minMillibel = -1500,
            maxMillibel = 1500
        )
        assertEquals(5, mapped.size)
        assertTrue("60 Hz band should boost ~550 mB", mapped[0] in 500..600)
        assertTrue("910 Hz band should stay near 0 mB", mapped[2] in -50..80)

        val fallbackParity = AudioBatch2Math.computeEqParityReport(
            gainsDb = bassPreset,
            using10BandDynamicsProcessing = false,
            deviceCenterFreqsHz = fallbackFreqs,
            isEnglish = true
        )
        assertEquals(5, fallbackParity.liveBandCount)
        assertTrue(fallbackParity.meanDeltaDb < 1.0f)
    }

    @Test
    fun masteringNoiseVocal8dWatermarkReverbAndVisualizerFiltersAreValid() {
        // AUD-01 Mastering
        val master = AudioBatch2Math.buildMasteringFilter(
            preset = "Studio Warm",
            ratio = 3.5f,
            thresholdDb = -18f,
            ceilingDb = -1.0f,
            makeupDb = 3.0f,
            warmthEnhance = true
        )
        assertTrue(master.contains("acompressor=threshold=0.1259:ratio=3.50"))
        assertTrue(master.contains("alimiter=limit=0.8913"))
        assertTrue(master.contains("stereotools="))

        // AUD-03 Noise Reduction
        val nr = AudioBatch2Math.buildNoiseReductionFilter(strengthPct = 70f, noiseFloorDb = -42f, mode = "Suara Jernih")
        assertTrue(nr.contains("highpass=f=85"))
        assertTrue(nr.contains("afftdn=nr=32.0:nf=-42.0"))

        // AUD-04 Vocal Remover validation + filter
        val monoErr = AudioBatch2Math.validateVocalRemoverInput(
            MediaProbeResult(audioChannels = 1, durationMs = 5000L),
            mode = "Mode Cepat — Kanselasi Tengah (Instrumental)",
            isEnglish = true
        )
        assertNotNull(monoErr)
        assertNull(
            AudioBatch2Math.validateVocalRemoverInput(
                MediaProbeResult(audioChannels = 2, durationMs = 5000L),
                mode = "Mode Cepat — Kanselasi Tengah (Instrumental)",
                isEnglish = true
            )
        )
        val aiErr = AudioBatch2Math.validateVocalRemoverInput(
            MediaProbeResult(audioChannels = 2, durationMs = 5000L),
            mode = "Mode AI — Demucs On-Device (P11)",
            isEnglish = true
        )
        assertTrue(aiErr!!.contains("Phase 11"))

        val vocalFc = AudioBatch2Math.buildVocalRemoverFilterComplex("Mode Cepat — Kanselasi Tengah", preserveBass = true, strengthPct = 90f)
        assertTrue(vocalFc.contains("lowpass=f=120") && vocalFc.contains("pan=stereo|c0=c0-0.90*c1"))

        // AUD-05 8D Audio
        val spatial = AudioBatch2Math.build8dAudioFilter(0.20f, 80f, roomReverb = true)
        assertTrue(spatial.contains("apulsator=mode=sine:hz=0.20:amount=0.80"))
        assertTrue(spatial.contains("aecho="))

        // AUD-19 Watermark (built-in and custom)
        val builtInWm = AudioBatch2Math.buildBuiltInWatermarkFilterComplex(10f, 40f, 880, 25_000L)
        assertTrue(builtInWm.contains("sine=f=880") && builtInWm.contains("mod(t,10.00)"))
        val customWm = AudioBatch2Math.buildCustomWatermarkFilterComplex(8f, 50f, 20_000L)
        assertTrue(customWm.contains("apad=whole_dur=8.00") && customWm.contains("aloop=loop=-1"))

        // AUD-25 Reverb
        val rev = AudioBatch2Math.buildReverbFilter("Katedral (Luas)", roomSizePct = 80f, dampingPct = 50f, wetPct = 55f)
        assertTrue(rev.contains("lowpass=f=") && rev.contains("amix=inputs=2"))

        // AUD-28 Visualizer
        val visSpec = AudioBatch2Math.resolveVisualizerConfig(
            style = "Spektrum Frekuensi (Spectrum)",
            colorTheme = "Cyber Violet",
            resolution = "1080x1080 (Square 1:1)",
            fpsLabel = "24 fps"
        )
        val visFc = AudioBatch2Math.buildVisualizerFilterComplex(visSpec)
        assertTrue(visFc.contains("showspectrum=s=1080x1080") && visFc.contains("fps=24") && visFc.contains("format=yuv420p[vout]"))
    }

    @Test
    fun stemSplitterContractChecksModelReadinessAndDefersInferenceCleanly() {
        runBlocking {
            ModelManager(context).deleteModel(ModelRegistry.demucsLite.id)
            val missing = DeferredStemSplitterEngine.checkReadiness(context, StemExecutionMode.ON_DEVICE_MODEL)
            assertTrue(missing is StemReadiness.MissingModel)

            val cloud = DeferredStemSplitterEngine.checkReadiness(context, StemExecutionMode.CLOUD_GATEWAY)
            assertTrue(cloud is StemReadiness.RequiresCloudConfig)

            // Even if model file exists, inference remains honestly deferred to P11
            val modelFile = ModelManager(context).modelFile(ModelRegistry.demucsLite.id)
            java.io.RandomAccessFile(modelFile, "rw").use { it.setLength(ModelRegistry.demucsLite.sizeBytes) }
            val deferred = DeferredStemSplitterEngine.checkReadiness(context, StemExecutionMode.ON_DEVICE_MODEL)
            assertTrue(deferred is StemReadiness.InferenceDeferred)
            assertEquals("P11", (deferred as StemReadiness.InferenceDeferred).plannedPhase)

            val ctx = ToolContext(context, storage, isEnglish = true)
            val events = DeferredStemSplitterEngine.splitStems(ctx, StemSplitRequest(Uri.EMPTY)).toList()
            assertTrue(events.last() is ToolProgress.Failed)
            modelFile.delete()
        }
    }

    @Test
    fun batch2ToolsExecuteFfmpegPipelinesAndProduceOutputs() = runBlocking {
        val wavMain = createSampleWav("b2_main.wav")
        val wavWm = createSampleWav("b2_wm.wav")
        val uriMain = Uri.fromFile(wavMain)
        val uriWm = Uri.fromFile(wavWm)
        registerShadowAudio(uriMain, durationMs = 15_000L, sampleRate = 44_100, channels = 2)
        registerShadowAudio(uriWm, durationMs = 1_000L, sampleRate = 44_100, channels = 2)

        val commands = mutableListOf<List<String>>()
        val fakeFfmpeg = MediaJob(runner = object : FfmpegSessionRunner {
            override fun executeAsync(
                arguments: List<String>,
                onLog: (String) -> Unit,
                onTimeMs: (Double) -> Unit,
                onComplete: (Int?, Boolean, Boolean, String) -> Unit
            ): FfmpegSessionHandle {
                commands.add(arguments)
                val outPath = arguments.last()
                File(outPath).writeBytes(byteArrayOf(0x00, 0x00, 0x00, 0x20, 0x66, 0x74, 0x79, 0x70))
                onTimeMs(7500.0)
                onComplete(0, true, false, "ok")
                return object : FfmpegSessionHandle {
                    override val sessionId: Long = 22L
                    override fun cancel() = Unit
                }
            }
        })

        val core = audioForgeBatch2CoreTools(fakeFfmpeg).associateBy { it.id }
        val extra = audioForgeBatch2ExtraTools(fakeFfmpeg).associateBy { it.id }
        val ctx = ToolContext(context, storage, isEnglish = true)

        assertTrue(core.getValue("AUD-01").run(ctx, ToolInput(uris = listOf(uriMain))).toList().last() is ToolProgress.Done)
        assertTrue(commands.last().any { it.contains("acompressor=") && it.contains("alimiter=") })

        assertTrue(core.getValue("AUD-03").run(ctx, ToolInput(uris = listOf(uriMain))).toList().last() is ToolProgress.Done)
        assertTrue(commands.last().any { it.contains("afftdn=") })

        assertTrue(core.getValue("AUD-04").run(ctx, ToolInput(uris = listOf(uriMain))).toList().last() is ToolProgress.Done)
        assertTrue(commands.last().any { it.contains("pan=stereo") })

        assertTrue(core.getValue("AUD-05").run(ctx, ToolInput(uris = listOf(uriMain))).toList().last() is ToolProgress.Done)
        assertTrue(commands.last().any { it.contains("apulsator=") })

        // AUD-19 custom watermark audio
        assertTrue(
            core.getValue("AUD-19").run(ctx, ToolInput(uris = listOf(uriMain), secondaryUri = uriWm)).toList().last() is ToolProgress.Done
        )
        assertTrue(commands.last().count { it == "-i" } == 2)

        assertTrue(extra.getValue("AUD-22").run(ctx, ToolInput(uris = listOf(uriMain))).toList().last() is ToolProgress.Done)
        assertTrue(extra.getValue("AUD-24").run(ctx, ToolInput(uris = listOf(uriMain))).toList().last() is ToolProgress.Done)
        assertTrue(extra.getValue("AUD-25").run(ctx, ToolInput(uris = listOf(uriMain))).toList().last() is ToolProgress.Done)

        val visDone = extra.getValue("AUD-28").run(ctx, ToolInput(uris = listOf(uriMain))).toList().last() as ToolProgress.Done
        assertEquals("video/mp4", visDone.result.outputs.first().mimeType)
        assertTrue(commands.last().any { it.contains("showwaves=") })
    }
}
