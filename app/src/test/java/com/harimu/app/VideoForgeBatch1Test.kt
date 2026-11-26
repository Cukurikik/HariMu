package com.harimu.app

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.harimu.app.core.files.FileStorageManager
import com.harimu.app.core.media.FfmpegSessionHandle
import com.harimu.app.core.media.FfmpegSessionRunner
import com.harimu.app.core.media.Media3Request
import com.harimu.app.core.media.Media3TransformerEngine
import com.harimu.app.core.media.MediaJob
import com.harimu.app.core.media.TransformerBackend
import com.harimu.app.core.media.TransformerHandle
import com.harimu.app.core.media.VideoToolMath
import com.harimu.app.core.toolapi.ToolContext
import com.harimu.app.core.toolapi.ToolGroup
import com.harimu.app.core.toolapi.ToolInput
import com.harimu.app.core.toolapi.ToolProgress
import com.harimu.app.core.toolapi.ToolRegistry
import com.harimu.app.feature.tools.videoForgeBatch1CoreTools
import com.harimu.app.feature.tools.videoForgeBatch1ExtraTools
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
import java.util.zip.ZipFile

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class VideoForgeBatch1Test {

    private lateinit var context: Context
    private lateinit var storage: FileStorageManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        storage = FileStorageManager(context)
        ShadowStatFs.registerStats(storage.outputsDir.absolutePath, 2 * 1024 * 1024, 1024 * 1024, 1024 * 1024)
    }

    private fun sampleVideoFile(): File {
        val out = File(context.cacheDir, "sample_video.mp4")
        if (!out.exists()) {
            val disk = listOf(
                File("src/androidTest/assets/sample_video.mp4"),
                File("app/src/androidTest/assets/sample_video.mp4")
            ).first { it.exists() }
            disk.copyTo(out, overwrite = true)
        }
        return out
    }

    private fun registerShadowVideoMetadata(
        uri: Uri,
        durationMs: Long,
        width: Int,
        height: Int,
        includeAudio: Boolean
    ) {
        val ds = DataSource.toDataSource(context, uri)
        ShadowMediaMetadataRetriever.addMetadata(ds, MediaMetadataRetriever.METADATA_KEY_DURATION, durationMs.toString())
        ShadowMediaMetadataRetriever.addMetadata(ds, MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH, width.toString())
        ShadowMediaMetadataRetriever.addMetadata(ds, MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT, height.toString())
        ShadowMediaMetadataRetriever.addMetadata(ds, MediaMetadataRetriever.METADATA_KEY_BITRATE, "6000000")

        val videoFormat = MediaFormat.createVideoFormat("video/avc", width, height).apply {
            setInteger(MediaFormat.KEY_FRAME_RATE, 30)
            setLong(MediaFormat.KEY_DURATION, durationMs * 1000L)
        }
        ShadowMediaExtractor.addTrack(ds, videoFormat, ByteArray(0))
        if (includeAudio) {
            val audioFormat = MediaFormat.createAudioFormat("audio/mp4a-latm", 44100, 2).apply {
                setLong(MediaFormat.KEY_DURATION, durationMs * 1000L)
            }
            ShadowMediaExtractor.addTrack(ds, audioFormat, ByteArray(0))
        }
    }

    @Test
    fun cropAndResizeGeometryStayEvenAndRespectOrientation() {
        // Landscape 1080p -> 1:1, 16:9, 9:16
        val cropSquare = VideoToolMath.calculateCropRect(1920, 1080, "1:1")
        assertEquals(1080, cropSquare.width)
        assertEquals(1080, cropSquare.height)
        assertEquals(420, cropSquare.x)
        assertEquals(0, cropSquare.y)

        val cropVertical = VideoToolMath.calculateCropRect(1920, 1080, "9:16")
        assertEquals(0, cropVertical.width % 2)
        assertEquals(0, cropVertical.height % 2)
        assertEquals(1080, cropVertical.height)
        assertEquals(608, cropVertical.width)

        val cropWide = VideoToolMath.calculateCropRect(1080, 1920, "16:9")
        assertEquals(1080, cropWide.width)
        assertEquals(608, cropWide.height)

        // Resize presets
        val r720 = VideoToolMath.calculateTargetDimensions(1920, 1080, "720p")
        assertEquals(1280, r720.width)
        assertEquals(720, r720.height)

        val r480Portrait = VideoToolMath.calculateTargetDimensions(1080, 1920, "480p")
        assertEquals(480, r480Portrait.width)
        assertEquals(852, r480Portrait.height)

        val r360 = VideoToolMath.calculateTargetDimensions(1920, 1080, "360p")
        assertEquals(640, r360.width)
        assertEquals(360, r360.height)
    }

    @Test
    fun speedSetPtsAndAtempoChainingHandleExtremeRates() {
        assertEquals("setpts=0.5000*PTS", VideoToolMath.buildSetPtsFilter(2.0f))
        assertEquals("setpts=4.0000*PTS", VideoToolMath.buildSetPtsFilter(0.25f))
        assertEquals("setpts=0.2500*PTS", VideoToolMath.buildSetPtsFilter(4.0f))

        assertEquals("atempo=0.5000,atempo=0.5000", VideoToolMath.buildAtempoFilterChain(0.25f))
        assertEquals("atempo=2.0000,atempo=2.0000", VideoToolMath.buildAtempoFilterChain(4.0f))
        assertEquals("atempo=2.0000,atempo=1.5000", VideoToolMath.buildAtempoFilterChain(3.0f))
        assertEquals("atempo=1.5000", VideoToolMath.buildAtempoFilterChain(1.5f))
    }

    @Test
    fun bitrateCompressionAndVolumeAndRotationCalculationsAreMonotonic() {
        val highQuality = VideoToolMath.calculateTargetVideoBitrateBps(10_000_000L, 18, 1920, 1080)
        val midQuality = VideoToolMath.calculateTargetVideoBitrateBps(10_000_000L, 24, 1920, 1080)
        val lowQuality = VideoToolMath.calculateTargetVideoBitrateBps(10_000_000L, 35, 1920, 1080)
        assertTrue(highQuality > midQuality)
        assertTrue(midQuality > lowQuality)
        assertTrue(highQuality <= 9_000_000L)

        assertEquals("volume=0.00", VideoToolMath.buildVolumeFilter(0f))
        assertEquals("volume=1.50", VideoToolMath.buildVolumeFilter(150f))
        assertEquals(270f, VideoToolMath.mapRotationToMedia3Degrees(90), 0.01f)
        assertEquals("transpose=1", VideoToolMath.buildFfmpegRotateFilter(90))
        assertEquals("hflip,vflip", VideoToolMath.buildFfmpegRotateFilter(180))
        assertEquals("transpose=2", VideoToolMath.buildFfmpegRotateFilter(270))
    }

    @Test
    fun splitterAndReverseChunkingAndSafetyGuardsWork() {
        val splitRanges = VideoToolMath.computeSplitterRanges(totalDurationMs = 125_000L, segmentDurationMs = 30_000L)
        assertEquals(5, splitRanges.size)
        assertEquals(0L to 30_000L, splitRanges.first())
        assertEquals(120_000L to 125_000L, splitRanges.last())

        val reverseChunks = VideoToolMath.computeReverseChunkRanges(totalDurationMs = 125_000L, chunkDurationMs = 12_000L)
        assertEquals(11, reverseChunks.size)

        val shortRev = VideoToolMath.assessReverseSafety(15_000L, isEnglish = true)
        assertTrue(shortRev.allowed && !shortRev.useSegmentedMode)

        val longRev = VideoToolMath.assessReverseSafety(120_000L, isEnglish = true)
        assertTrue(longRev.allowed && longRev.useSegmentedMode)
        assertTrue(longRev.warningMessage != null)

        val overLimitRev = VideoToolMath.assessReverseSafety(240_000L, isEnglish = true)
        assertFalse(overLimitRev.allowed)

        val overLimitLoop = VideoToolMath.assessLoopSafety(120_000L, loops = 6, isEnglish = false)
        assertFalse(overLimitLoop.allowed)
    }

    @Test
    fun registryContainsAll17ActiveBatch1ToolsAndKeepsOtherVideoStubsIntact() {
        assertEquals(170, ToolRegistry.allTools.size)
        val videoTools = ToolRegistry.byGroup(ToolGroup.VIDEO)
        assertEquals(30, videoTools.size)

        val batch1Ids = listOf(
            "VID-01", "VID-02", "VID-03", "VID-04", "VID-05", "VID-06", "VID-08",
            "VID-09", "VID-10", "VID-12", "VID-13", "VID-18", "VID-19", "VID-20",
            "VID-21", "VID-22", "VID-24"
        )
        batch1Ids.forEach { id ->
            val tool = ToolRegistry.findById(id)
            assertTrue("Expected $id to be implemented (not stub)", tool != null && !tool.isStub)
        }

        // FASE 11: VID-07 sudah diimplementasikan (FFmpeg lanczos + unsharp).
        val upscaler = ToolRegistry.findById("VID-07")
        assertTrue("Expected VID-07 implemented", upscaler != null && !upscaler.isStub)
    }

    @Test
    fun validationRejectsEmptyFileAndUnsupportedExtension() {
        val emptyFile = File(context.cacheDir, "empty_video.mp4").apply { writeBytes(ByteArray(0)) }
        val emptyRes = VideoToolMath.validateVideoInputs(context, storage, listOf(Uri.fromFile(emptyFile)), isEnglish = true)
        assertFalse(emptyRes.valid)
        assertTrue(emptyRes.errorMessage?.contains("empty", ignoreCase = true) == true)

        val textFile = File(context.cacheDir, "not_a_video.txt").apply { writeText("hello") }
        val extRes = VideoToolMath.validateVideoInputs(context, storage, listOf(Uri.fromFile(textFile)), isEnglish = false)
        assertFalse(extRes.valid)
        assertTrue(extRes.errorMessage?.contains("tidak didukung", ignoreCase = true) == true)
    }

    @Test
    fun toolsValidateAudioPresenceAndExecuteBatch1Pipelines() = runBlocking {
        val sample = sampleVideoFile()
        val withAudioFile = File(context.cacheDir, "sample_with_audio.mp4").also { sample.copyTo(it, overwrite = true) }
        val withAudioUri = Uri.fromFile(withAudioFile)
        registerShadowVideoMetadata(withAudioUri, durationMs = 2_400L, width = 1280, height = 720, includeAudio = true)

        val silentFile = File(context.cacheDir, "silent_1080p_long.mp4").also { sample.copyTo(it, overwrite = true) }
        val silentUri = Uri.fromFile(silentFile)
        registerShadowVideoMetadata(silentUri, durationMs = 150_000L, width = 1920, height = 1080, includeAudio = false)

        val recordedCommands = mutableListOf<List<String>>()
        val fakeFfmpeg = MediaJob(runner = object : FfmpegSessionRunner {
            override fun executeAsync(
                arguments: List<String>,
                onLog: (String) -> Unit,
                onTimeMs: (Double) -> Unit,
                onComplete: (Int?, Boolean, Boolean, String) -> Unit
            ): FfmpegSessionHandle {
                recordedCommands.add(arguments)
                val outPath = arguments.last()
                File(outPath).writeBytes(byteArrayOf(0x47, 0x49, 0x46, 0x38, 0x39, 0x61))
                onTimeMs(500.0)
                onComplete(0, true, false, "ok")
                return object : FfmpegSessionHandle {
                    override val sessionId: Long = 1L
                    override fun cancel() = Unit
                }
            }
        })
        val fakeMedia3 = Media3TransformerEngine(
            transformerBackend = object : TransformerBackend {
                override fun startExport(
                    context: Context,
                    request: Media3Request,
                    outputFile: File,
                    onProgress: (Int) -> Unit,
                    onCompleted: () -> Unit,
                    onError: (Throwable) -> Unit
                ): TransformerHandle {
                    sample.copyTo(outputFile, overwrite = true)
                    onProgress(80)
                    onCompleted()
                    return object : TransformerHandle { override fun cancel() = Unit }
                }
            },
            ffmpegJob = fakeFfmpeg
        )

        val coreTools = videoForgeBatch1CoreTools(fakeMedia3, fakeFfmpeg).associateBy { it.id }
        val extraTools = videoForgeBatch1ExtraTools(fakeFfmpeg).associateBy { it.id }
        val ctx = ToolContext(context, storage, isEnglish = true)

        // Missing audio rejections on Speed (when includeAudio=true), MP3, Volume, Remove Audio
        val speedSilentFail = coreTools.getValue("VID-05").run(
            ctx, ToolInput(uris = listOf(silentUri), params = mapOf("speed" to 2f, "includeAudio" to true))
        ).toList().last() as ToolProgress.Failed
        assertTrue(speedSilentFail.error.contains("audio", ignoreCase = true))

        val mp3SilentFail = extraTools.getValue("VID-08").run(
            ctx, ToolInput(uris = listOf(silentUri))
        ).toList().last() as ToolProgress.Failed
        assertTrue(mp3SilentFail.error.contains("audio", ignoreCase = true))

        val volSilentFail = extraTools.getValue("VID-13").run(
            ctx, ToolInput(uris = listOf(silentUri), params = mapOf("volumePct" to 120f))
        ).toList().last() as ToolProgress.Failed
        assertTrue(volSilentFail.error.contains("audio", ignoreCase = true))

        val removeAudioSilentFail = coreTools.getValue("VID-24").run(
            ctx, ToolInput(uris = listOf(silentUri))
        ).toList().last() as ToolProgress.Failed
        assertTrue(removeAudioSilentFail.error.contains("audio", ignoreCase = true))

        // VID-06: 2-pass GIF uses palettegen then paletteuse
        val gifEvents = extraTools.getValue("VID-06").run(ctx, ToolInput(uris = listOf(withAudioUri))).toList()
        assertTrue(gifEvents.last() is ToolProgress.Done)
        assertTrue(recordedCommands.any { it.any { arg -> arg.contains("palettegen") } })
        assertTrue(recordedCommands.any { it.any { arg -> arg.contains("paletteuse") } })

        // VID-08: Video to MP3 succeeds with audio track using libmp3lame
        val mp3Events = extraTools.getValue("VID-08").run(ctx, ToolInput(uris = listOf(withAudioUri), params = mapOf("bitrate" to "256k"))).toList()
        assertTrue(mp3Events.last() is ToolProgress.Done)
        assertTrue(recordedCommands.last().contains("libmp3lame"))

        // VID-18: WebM Converter invokes libvpx + libvorbis
        recordedCommands.clear()
        val convEvents = extraTools.getValue("VID-18").run(ctx, ToolInput(uris = listOf(withAudioUri), params = mapOf("fmt" to "WebM"))).toList()
        assertTrue(convEvents.last() is ToolProgress.Done)
        assertTrue(recordedCommands.any { it.contains("libvpx") && it.contains("libvorbis") })

        // VID-19: 1080p >= 2 min triggers segmented chunked Reverse + concat rather than full-clip RAM reverse
        recordedCommands.clear()
        val revEvents = extraTools.getValue("VID-19").run(ctx, ToolInput(uris = listOf(silentUri))).toList()
        assertTrue(revEvents.last() is ToolProgress.Done)
        assertTrue(recordedCommands.any { it.contains("concat") })
        assertTrue(recordedCommands.count { it.contains("reverse") } >= 10)

        // VID-21: Splitter writes valid multi-part ZIP
        val splitEvents = extraTools.getValue("VID-21").run(ctx, ToolInput(uris = listOf(silentUri), params = mapOf("segSec" to 30f))).toList()
        val splitDone = splitEvents.last() as ToolProgress.Done
        val zipFile = splitDone.result.outputs.first().file
        assertTrue(zipFile.exists())
        ZipFile(zipFile).use { z -> assertEquals(5, z.size()) }

        // VID-03: Crop & VID-24: Remove Audio succeed when valid
        val cropEvents = coreTools.getValue("VID-03").run(ctx, ToolInput(uris = listOf(withAudioUri), params = mapOf("ratio" to "9:16"))).toList()
        assertTrue(cropEvents.last() is ToolProgress.Done)

        val muteEvents = coreTools.getValue("VID-24").run(ctx, ToolInput(uris = listOf(withAudioUri))).toList()
        assertTrue(muteEvents.last() is ToolProgress.Done)
    }
}
