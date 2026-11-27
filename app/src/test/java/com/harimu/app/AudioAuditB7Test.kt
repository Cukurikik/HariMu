package com.harimu.app

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.node.RootForTest
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.test.core.app.ApplicationProvider
import com.harimu.app.core.data.AudioClipSpec
import com.harimu.app.core.data.AudioTrackSpec
import com.harimu.app.core.design.HariMuTheme
import com.harimu.app.core.design.ThemeMode
import com.harimu.app.core.engines.AudioEngine
import com.harimu.app.core.engines.PcmAudio
import com.harimu.app.core.files.FileStorageManager
import com.harimu.app.core.media.AudioStudioEngine
import com.harimu.app.core.media.AudioToolMath
import com.harimu.app.core.media.AudioWaveformExtractor
import com.harimu.app.core.media.FfmpegSessionHandle
import com.harimu.app.core.media.FfmpegSessionRunner
import com.harimu.app.core.media.MediaJob
import com.harimu.app.core.toolapi.ToolContext
import com.harimu.app.core.toolapi.ToolInput
import com.harimu.app.core.toolapi.ToolProgress
import com.harimu.app.core.toolapi.ToolRegistry
import com.harimu.app.feature.studios.AudioStudioScreen
import com.harimu.app.feature.studios.AudioStudioUiState
import com.harimu.app.feature.studios.AudioStudioViewModel
import com.harimu.app.feature.toolscreen.AudioBeforeAfterPreviewCard
import com.harimu.app.feature.tools.audioForgeBatch2CoreTools
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
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import org.robolectric.shadows.ShadowMediaExtractor
import org.robolectric.shadows.ShadowMediaMetadataRetriever
import org.robolectric.shadows.ShadowStatFs
import org.robolectric.shadows.util.DataSource
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.PI
import kotlin.math.sin

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = HariMuApp::class)
class AudioAuditB7Test {

    private lateinit var app: HariMuApp
    private lateinit var storage: FileStorageManager

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        storage = app.container.storage
        ShadowStatFs.registerStats(storage.outputsDir.absolutePath, 2 * 1024 * 1024, 1024 * 1024, 1024 * 1024)
    }

    private fun createSampleStereoWav(name: String, seconds: Int = 1): File {
        val sampleRate = 44_100
        val frames = sampleRate * seconds
        val interleaved = ShortArray(frames * 2)
        for (i in 0 until frames) {
            val monoTone = (sin(2.0 * PI * 440.0 * i / sampleRate) * 14_000.0).toInt()
            val sideTone = (sin(2.0 * PI * 880.0 * i / sampleRate) * 6_000.0).toInt()
            interleaved[i * 2] = (monoTone + sideTone).coerceIn(-32767, 32767).toShort()
            interleaved[i * 2 + 1] = (monoTone - sideTone).coerceIn(-32767, 32767).toShort()
        }
        val out = File(app.cacheDir, name)
        AudioEngine.writeWav(PcmAudio(interleaved, sampleRate, 2), out)
        return out
    }

    private fun registerShadowAudio(
        context: Context,
        uri: Uri,
        durationMs: Long = 10_000L,
        sampleRate: Int = 44_100,
        channels: Int = 2
    ) {
        val ds = DataSource.toDataSource(context, uri)
        ShadowMediaMetadataRetriever.addMetadata(ds, MediaMetadataRetriever.METADATA_KEY_DURATION, durationMs.toString())
        ShadowMediaMetadataRetriever.addMetadata(ds, MediaMetadataRetriever.METADATA_KEY_BITRATE, "192000")
        val fmt = MediaFormat.createAudioFormat("audio/wav", sampleRate, channels).apply {
            setLong(MediaFormat.KEY_DURATION, durationMs * 1000L)
        }
        ShadowMediaExtractor.addTrack(ds, fmt, ByteArray(0))
    }

    @Test
    fun audioStudioEngineBuildsStereoFormatBeforePanAndHonorsCodecArgs() = runBlocking {
        val wav1 = createSampleStereoWav("studio_pan_1.wav")
        val wav2 = createSampleStereoWav("studio_pan_2.wav")
        val uri1 = Uri.fromFile(wav1)
        val uri2 = Uri.fromFile(wav2)

        val clip1 = AudioClipSpec("c1", uri1.toString(), "c1.wav", 4_000L, 0L, 4_000L, 0L, 0f)
        val clip2 = AudioClipSpec("c2", uri2.toString(), "c2.wav", 4_000L, 500L, 3_500L, 1_000L, 3.0f)

        val tracks = listOf(
            AudioTrackSpec("t1", "Vocal", volume = 1.0f, pan = -0.6f, mute = false, solo = false, clips = listOf(clip1)),
            AudioTrackSpec("t2", "Muted", volume = 1.0f, pan = 0.0f, mute = true, solo = false, clips = listOf(clip2)),
            AudioTrackSpec("t3", "Guitar", volume = 0.8f, pan = 0.5f, mute = false, solo = false, clips = listOf(clip2))
        )

        val commands = mutableListOf<List<String>>()
        val fakeJob = MediaJob(runner = object : FfmpegSessionRunner {
            override fun executeAsync(
                arguments: List<String>,
                onLog: (String) -> Unit,
                onTimeMs: (Double) -> Unit,
                onComplete: (Int?, Boolean, Boolean, String) -> Unit
            ): FfmpegSessionHandle {
                commands += arguments
                File(arguments.last()).writeBytes(byteArrayOf(0x52, 0x49, 0x46, 0x46))
                onTimeMs(2000.0)
                onComplete(0, true, false, "ok")
                return object : FfmpegSessionHandle {
                    override val sessionId: Long = 91L
                    override fun cancel() = Unit
                }
            }
        })

        // Export to MP3
        val mp3Events = AudioStudioEngine.exportProject(
            context = app,
            storage = storage,
            tracks = tracks,
            masterVolume = 1.0f,
            outputFormat = "mp3",
            isEnglish = true,
            mediaJob = fakeJob
        ).toList()
        assertTrue(mp3Events.last() is ToolProgress.Done)

        val mp3Cmd = commands.last()
        val fcIdx = mp3Cmd.indexOf("-filter_complex")
        assertTrue(fcIdx >= 0)
        val filterComplex = mp3Cmd[fcIdx + 1]
        val aformatPos = filterComplex.indexOf("aformat=sample_fmts=fltp:sample_rates=44100:channel_layouts=stereo")
        val panPos = filterComplex.indexOf("pan=stereo|")
        assertTrue("Expected aformat stereo normalization", aformatPos >= 0)
        assertTrue("Expected pan filter", panPos > aformatPos)
        assertTrue("Muted track clips must be excluded (only 2 inputs)", mp3Cmd.count { it == "-i" } == 2)
        assertTrue(filterComplex.contains("normalize=0,alimiter=limit=0.95"))
        assertTrue(mp3Cmd.contains("-vn"))
        assertTrue(mp3Cmd.contains("libmp3lame"))

        // Export to FLAC includes compression_level 5
        val flacEvents = AudioStudioEngine.exportProject(
            context = app,
            storage = storage,
            tracks = tracks,
            masterVolume = 1.0f,
            outputFormat = "flac",
            isEnglish = true,
            mediaJob = fakeJob
        ).toList()
        assertTrue(flacEvents.last() is ToolProgress.Done)
        val flacCmd = commands.last()
        assertTrue(flacCmd.contains("flac"))
        assertTrue(flacCmd.contains("-compression_level") && flacCmd.contains("5"))

        // Empty tracks fails cleanly
        val emptyEvents = AudioStudioEngine.exportProject(
            context = app,
            storage = storage,
            tracks = listOf(AudioTrackSpec("t_empty", "Empty")),
            masterVolume = 1.0f,
            outputFormat = "wav",
            isEnglish = true,
            mediaJob = fakeJob
        ).toList()
        assertTrue(emptyEvents.last() is ToolProgress.Failed)

        // Insufficient disk space fails cleanly
        ShadowStatFs.registerStats(storage.outputsDir.absolutePath, 100, 10, 10)
        val noSpaceEvents = AudioStudioEngine.exportProject(
            context = app,
            storage = storage,
            tracks = tracks,
            masterVolume = 1.0f,
            outputFormat = "wav",
            isEnglish = true,
            mediaJob = fakeJob
        ).toList()
        assertTrue(noSpaceEvents.last() is ToolProgress.Failed)
    }

    @Test
    fun audioWaveformExtractorParsesWavWithExtendedRiffChunksAnd8BitPcm() {
        // Construct a RIFF/WAVE file with JUNK chunk before fmt , fact chunk before data, and trailing ID3 chunk
        val sampleRate = 16_000
        val channels = 1
        val pcmSamples = ShortArray(sampleRate) { idx ->
            (sin(2.0 * PI * 300.0 * idx / sampleRate) * 20_000.0).toInt().toShort()
        }
        val pcmBytes = ByteBuffer.allocate(pcmSamples.size * 2).order(ByteOrder.LITTLE_ENDIAN).apply {
            pcmSamples.forEach { putShort(it) }
        }.array()

        val junkPayload = byteArrayOf(1, 2, 3, 4, 5) // odd length -> requires 1 padding byte in RIFF!
        val factPayload = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(pcmSamples.size).array()
        val fmtPayload = ByteBuffer.allocate(18).order(ByteOrder.LITTLE_ENDIAN).apply {
            putShort(1) // PCM
            putShort(channels.toShort())
            putInt(sampleRate)
            putInt(sampleRate * channels * 2)
            putShort((channels * 2).toShort())
            putShort(16)
            putShort(0) // cbSize=0 extended fmt chunk
        }.array()
        val trailingPayload = "TAIL_METADATA".toByteArray()

        fun writeChunk(out: ByteArrayOutputStream, id: String, data: ByteArray) {
            out.write(id.toByteArray(Charsets.US_ASCII))
            out.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(data.size).array())
            out.write(data)
            if (data.size % 2 != 0) out.write(0)
        }

        val body = ByteArrayOutputStream().apply {
            write("WAVE".toByteArray(Charsets.US_ASCII))
            writeChunk(this, "JUNK", junkPayload)
            writeChunk(this, "fmt ", fmtPayload)
            writeChunk(this, "fact", factPayload)
            writeChunk(this, "data", pcmBytes)
            writeChunk(this, "id3 ", trailingPayload)
        }.toByteArray()

        val riffBytes = ByteArrayOutputStream().apply {
            write("RIFF".toByteArray(Charsets.US_ASCII))
            write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(body.size).array())
            write(body)
        }.toByteArray()

        val wave16 = AudioWaveformExtractor.extractPeaksFromWavStream(ByteArrayInputStream(riffBytes), barCount = 48)
        assertNotNull(wave16)
        assertEquals(48, wave16!!.peaks.size)
        assertEquals(16_000, wave16.sampleRateHz)
        assertEquals(1, wave16.channels)
        assertEquals(1_000L, wave16.durationMs)
        assertTrue("Expected dynamic normalized peaks", wave16.peaks.maxOrNull()!! > 0.8f)

        // 8-bit unsigned PCM WAV test
        val pcm8 = ByteArray(8_000) { idx ->
            (128 + (sin(2.0 * PI * 200.0 * idx / 8_000) * 100.0).toInt()).coerceIn(0, 255).toByte()
        }
        val fmt8 = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN).apply {
            putShort(1)
            putShort(1)
            putInt(8_000)
            putInt(8_000)
            putShort(1)
            putShort(8)
        }.array()
        val body8 = ByteArrayOutputStream().apply {
            write("WAVE".toByteArray(Charsets.US_ASCII))
            writeChunk(this, "fmt ", fmt8)
            writeChunk(this, "data", pcm8)
        }.toByteArray()
        val riff8 = ByteArrayOutputStream().apply {
            write("RIFF".toByteArray(Charsets.US_ASCII))
            write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(body8.size).array())
            write(body8)
        }.toByteArray()

        val wave8 = AudioWaveformExtractor.extractPeaksFromWavStream(ByteArrayInputStream(riff8), barCount = 32)
        assertNotNull(wave8)
        assertEquals(32, wave8!!.peaks.size)
        assertEquals(1_000L, wave8.durationMs)
        assertTrue(wave8.peaks.maxOrNull()!! > 0.5f)
    }

    @Test
    fun audioStudioViewModelGuardsEmptyPlaybackTracksActualDurationAndPurgesDeletedWaveforms() {
        val vm = AudioStudioViewModel(app.container)

        assertFalse(vm.state.value.hasAnyClips)
        assertEquals(0L, vm.state.value.actualDurationMs)
        assertEquals(5_000L, vm.state.value.totalDurationMs)

        // Cannot start playing or seek past 0 when empty
        vm.togglePlay()
        assertFalse(vm.state.value.isPlaying)
        vm.setPlaying(true)
        assertFalse(vm.state.value.isPlaying)
        vm.seekPlayhead(2_500L)
        assertEquals(0L, vm.state.value.playheadPositionMs)

        // Verify UiState duration math with real clip populated via loadProject
        val clip = AudioClipSpec("clip_a", "file:///tmp/a.wav", "a.wav", 8_000L, 1_000L, 7_000L, 2_000L, 0f)
        val projectTracks = listOf(
            AudioTrackSpec("tr_1", "Lead", clips = listOf(clip)),
            AudioTrackSpec("tr_2", "Bed", clips = emptyList())
        )
        val json = app.container.workspace.encodeAudioTracks(projectTracks)
        vm.loadProject(
            com.harimu.app.core.data.AudioProjectEntity(
                id = "proj_1",
                title = "Loaded",
                tracksJson = json,
                masterVolume = 1.0f,
                updatedAt = 1234L
            )
        )

        assertTrue(vm.state.value.hasAnyClips)
        assertEquals(8_000L, vm.state.value.actualDurationMs)
        assertEquals(8_000L, vm.state.value.totalDurationMs)

        vm.seekPlayhead(50_000L)
        assertEquals(8_000L, vm.state.value.playheadPositionMs)
        vm.togglePlay()
        assertTrue(vm.state.value.isPlaying)

        // Deleting last clip stops playback and resets playhead & waveform map
        vm.deleteClip("tr_1", "clip_a")
        assertFalse(vm.state.value.hasAnyClips)
        assertFalse(vm.state.value.isPlaying)
        assertEquals(0L, vm.state.value.playheadPositionMs)
        assertFalse("clip_a" in vm.state.value.clipWaveforms)

        val emptyUiState = AudioStudioUiState()
        assertFalse(emptyUiState.hasAnyClips)
    }

    @Test
    fun inMemoryDspSafetyAndVocalRemoverDspModeBehaveSafely() = runBlocking {
        assertTrue(AudioToolMath.assessInMemoryDspSafety(120_000L, isEnglish = true).allowed)
        val tooLong = AudioToolMath.assessInMemoryDspSafety(360_000L, isEnglish = true)
        assertFalse(tooLong.allowed)
        assertNotNull(tooLong.blockingError)

        // Real stereo PcmAudio split produces both vocal_mid.wav and instrumental_side.wav
        val sr = 44_100
        val frames = sr
        val interleaved = ShortArray(frames * 2)
        for (i in 0 until frames) {
            val mid = (sin(2.0 * PI * 440.0 * i / sr) * 14_000.0).toInt()
            val side = (sin(2.0 * PI * 880.0 * i / sr) * 6_000.0).toInt()
            interleaved[i * 2] = (mid + side).coerceIn(-32767, 32767).toShort()
            interleaved[i * 2 + 1] = (mid - side).coerceIn(-32767, 32767).toShort()
        }
        val stemDir = storage.createJobTempDir("b7_stem_test")
        try {
            val stems = com.harimu.app.core.ai.AiLocalEngines.splitStems(PcmAudio(interleaved, sr, 2), stemDir)
            assertEquals("vokal.wav", stems.vocals.name)
            assertEquals("instrumental.wav", stems.instrumental.name)
            assertTrue(stems.vocals.exists() && stems.vocals.length() > 44L)
            assertTrue(stems.instrumental.exists() && stems.instrumental.length() > 44L)
        } finally {
            stemDir.deleteRecursively()
        }

        // Empty decoded PCM under Robolectric ShadowMediaExtractor fails cleanly instead of writing corrupt empty WAV/ZIP
        val wav = createSampleStereoWav("aud04_dsp.wav", seconds = 1)
        val uri = Uri.fromFile(wav)
        registerShadowAudio(app, uri, durationMs = 1_000L, sampleRate = 44_100, channels = 2)
        val coreTools = audioForgeBatch2CoreTools(MediaJob()).associateBy { it.id }
        val aud04 = coreTools.getValue("AUD-04")
        val ctx = ToolContext(app, storage, isEnglish = true)
        val emptyDecodeResult = aud04.run(
            ctx,
            ToolInput(uris = listOf(uri), params = mapOf("mode" to "Mode DSP — Mid/Side Ekstrak Lokal (ZIP 2 Stem)"))
        ).toList().last()
        assertTrue(emptyDecodeResult is ToolProgress.Failed)

        // AUD-04 Mode DSP and AUD-20 reject >5 min audio before decoding to RAM
        val longWav = createSampleStereoWav("aud04_long_dsp.wav", seconds = 1)
        val longUri = Uri.fromFile(longWav)
        registerShadowAudio(app, longUri, durationMs = 420_000L, sampleRate = 44_100, channels = 2)
        val aud04TooLong = aud04.run(
            ctx,
            ToolInput(uris = listOf(longUri), params = mapOf("mode" to "Mode DSP — Mid/Side Ekstrak Lokal (ZIP 2 Stem)"))
        ).toList().last() as ToolProgress.Failed
        assertTrue(aud04TooLong.error.contains("5 minutes"))

        val aud20TooLong = ToolRegistry.findById("AUD-20")!!.run(
            ctx,
            ToolInput(uris = listOf(longUri))
        ).toList().last() as ToolProgress.Failed
        assertTrue(aud20TooLong.error.contains("5 minutes"))

        // AUD-09 rejects endSec <= startSec before invoking FFmpeg
        val aud09InvalidRange = ToolRegistry.findById("AUD-09")!!.run(
            ctx,
            ToolInput(uris = listOf(uri), params = mapOf("startSec" to 10f, "endSec" to 5f))
        ).toList().last()
        assertTrue(aud09InvalidRange is ToolProgress.Failed)
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp")
    fun audioStudioPlayDisabledWhenEmptyAndPreviewCardHasWaveformSemantics() {
        val wav = createSampleStereoWav("preview_card.wav", seconds = 1)
        withMainActivity { activity ->
            val container = (activity.application as HariMuApp).container
            setTestContent(activity, isEnglish = true) {
                AudioStudioScreen(container = container, isEnglish = true, onBack = {})
            }
            val nodes = collectSemanticsNodes(activity.window.decorView, mergingEnabled = true)
            val playBtn = findByTag(nodes, "audio_studio_play_pause_btn")
            assertNotNull(playBtn)
            assertTrue(
                "Play button should be disabled when timeline has no clips",
                playBtn!!.config.getOrNull(SemanticsProperties.Disabled) != null
            )

            setTestContent(activity, isEnglish = true) {
                AudioBeforeAfterPreviewCard(
                    inputUri = Uri.fromFile(wav),
                    outputFile = null,
                    showTrimControls = true,
                    startSec = 0f,
                    endSec = 1f,
                    onRangeChange = { _, _ -> }
                )
            }
            val previewNodes = collectSemanticsNodes(activity.window.decorView, mergingEnabled = true)
            val waveNode = findByTag(previewNodes, "audio_waveform_view")
            assertNotNull(waveNode)
            val cd = waveNode!!.config.getOrNull(SemanticsProperties.ContentDescription)
            assertNotNull("Expected TalkBack description on seekable waveform", cd)
            assertTrue(cd!!.any { it.contains("waveform", ignoreCase = true) })
        }
    }

    private fun withMainActivity(block: (MainActivity) -> Unit) {
        val controller: ActivityController<MainActivity> = Robolectric.buildActivity(MainActivity::class.java).setup()
        try {
            val activity = controller.get()
            flushMainUi(activity)
            block(activity)
        } finally {
            runCatching { controller.pause().stop().destroy() }
            flushMainUi()
        }
    }

    private fun setTestContent(
        activity: MainActivity,
        isEnglish: Boolean,
        content: @Composable () -> Unit
    ) {
        activity.setContent {
            val baseContext = LocalContext.current
            val baseConfig = LocalConfiguration.current
            val targetLocale = if (isEnglish) Locale.ENGLISH else Locale.forLanguageTag("id")
            val localizedConfig = remember(baseConfig, targetLocale) {
                Configuration(baseConfig).apply { setLocale(targetLocale) }
            }
            val localizedResources = remember(baseContext, localizedConfig) {
                baseContext.createConfigurationContext(localizedConfig).resources
            }
            val localizedContext = remember(baseContext, localizedResources) {
                object : ContextWrapper(baseContext) {
                    override fun getResources(): Resources = localizedResources
                }
            }
            CompositionLocalProvider(
                LocalConfiguration provides localizedConfig,
                LocalContext provides localizedContext,
                LocalResources provides localizedResources,
                LocalActivityResultRegistryOwner provides activity,
                LocalOnBackPressedDispatcherOwner provides activity
            ) {
                HariMuTheme(
                    themeMode = ThemeMode.SYSTEM,
                    isSystemDark = false,
                    dynamicColor = false,
                    content = content
                )
            }
        }
        flushMainUi(activity)
    }

    private fun flushMainUi(activity: MainActivity? = null, frames: Int = 4) {
        fun forceLayoutTree(view: View) {
            view.forceLayout()
            if (view is ViewGroup) {
                for (i in 0 until view.childCount) {
                    forceLayoutTree(view.getChildAt(i))
                }
            }
        }
        repeat(frames) {
            ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
            ShadowLooper.idleMainLooper(16, TimeUnit.MILLISECONDS)
            ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
            activity?.let { act ->
                val dm = act.resources.displayMetrics
                act.window?.decorView?.let { decor ->
                    forceLayoutTree(decor)
                    decor.measure(
                        View.MeasureSpec.makeMeasureSpec(dm.widthPixels, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(dm.heightPixels, View.MeasureSpec.EXACTLY)
                    )
                    decor.layout(0, 0, dm.widthPixels, dm.heightPixels)
                    decor.viewTreeObserver.dispatchOnPreDraw()
                }
            }
        }
    }

    private fun collectSemanticsNodes(rootView: View, mergingEnabled: Boolean = false): List<SemanticsNode> {
        val roots = mutableListOf<RootForTest>()
        fun walk(view: View) {
            if (view is RootForTest) roots += view
            if (view is ViewGroup) {
                for (i in 0 until view.childCount) walk(view.getChildAt(i))
            }
        }
        walk(rootView)
        return roots.flatMap { root -> root.semanticsOwner.getAllSemanticsNodes(mergingEnabled = mergingEnabled) }
    }

    private fun findByTag(nodes: List<SemanticsNode>, tag: String): SemanticsNode? =
        nodes.firstOrNull { it.config.getOrNull(SemanticsProperties.TestTag) == tag }
}
