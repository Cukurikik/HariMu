package com.harimu.app

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.harimu.app.core.data.AppDatabase
import com.harimu.app.core.data.EditorClipSpec
import com.harimu.app.core.data.SettingsRepository
import com.harimu.app.core.data.WorkspaceRepository
import com.harimu.app.core.files.FileStorageManager
import com.harimu.app.core.media.BlurredTrackWindow
import com.harimu.app.core.media.FaceBox
import com.harimu.app.core.media.FfmpegSessionHandle
import com.harimu.app.core.media.FfmpegSessionRunner
import com.harimu.app.core.media.Media3Request
import com.harimu.app.core.media.Media3TransformerEngine
import com.harimu.app.core.media.MediaJob
import com.harimu.app.core.media.SubtitleParser
import com.harimu.app.core.media.TimedFaces
import com.harimu.app.core.media.TransformerBackend
import com.harimu.app.core.media.TransformerHandle
import com.harimu.app.core.media.TransitionClipInfo
import com.harimu.app.core.media.VideoBatch2Math
import com.harimu.app.core.media.VideoEditorEngine
import com.harimu.app.core.media.VideoEditorMath
import com.harimu.app.core.toolapi.DedicatedScreenRoute
import com.harimu.app.core.toolapi.ToolContext
import com.harimu.app.core.toolapi.ToolGroup
import com.harimu.app.core.toolapi.ToolInput
import com.harimu.app.core.toolapi.ToolProgress
import com.harimu.app.core.toolapi.ToolRegistry
import com.harimu.app.feature.tools.videoForgeBatch2ExtraTools
import com.harimu.app.feature.tools.videoForgeBatch2Tools
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.After
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
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class VideoForgeBatch2AndEditorTest {

    private lateinit var context: Context
    private lateinit var storage: FileStorageManager
    private lateinit var db: AppDatabase
    private lateinit var workspace: WorkspaceRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        storage = FileStorageManager(context)
        ShadowStatFs.registerStats(storage.outputsDir.absolutePath, 2 * 1024 * 1024, 1024 * 1024, 1024 * 1024)
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        workspace = WorkspaceRepository(db, SettingsRepository(context), storage)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun sampleVideoFile(name: String = "sample_b2.mp4"): File {
        val out = File(context.cacheDir, name)
        if (!out.exists()) {
            val disk = listOf(
                File("src/androidTest/assets/sample_video.mp4"),
                File("app/src/androidTest/assets/sample_video.mp4")
            ).first { it.exists() }
            disk.copyTo(out, overwrite = true)
        }
        return out
    }

    private fun samplePngFile(name: String): File {
        val f = File(context.cacheDir, name)
        val bmp = Bitmap.createBitmap(160, 90, Bitmap.Config.ARGB_8888)
        FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
        return f
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
        ShadowMediaMetadataRetriever.addMetadata(ds, MediaMetadataRetriever.METADATA_KEY_BITRATE, "5000000")

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
    fun registryHasCore01AndAllBatch2VideoToolsAndLeavesVid07Stub() {
        assertEquals(170, ToolRegistry.allTools.size)
        assertEquals(16, ToolRegistry.countByGroup(ToolGroup.CORE))
        assertEquals(30, ToolRegistry.countByGroup(ToolGroup.VIDEO))

        val core01 = ToolRegistry.findById("CORE-01")
        assertNotNull(core01)
        assertFalse(core01!!.isStub)
        assertEquals(DedicatedScreenRoute.VIDEO_EDITOR, core01.dedicatedRoute)

        val batch2Ids = listOf(
            "VID-11", "VID-14", "VID-15", "VID-16", "VID-17", "VID-23",
            "VID-25", "VID-26", "VID-27", "VID-28", "VID-29", "VID-30"
        )
        batch2Ids.forEach { id ->
            val t = ToolRegistry.findById(id)
            assertTrue("Expected $id to be implemented in Fase 5", t != null && !t.isStub)
        }

        val upscaler = ToolRegistry.findById("VID-07")
        assertTrue("VID-07 implemented in Fase 11", upscaler != null && !upscaler.isStub)
    }

    @Test
    fun subtitleParserParsesSrtAndVttAndBuildsStyledAss() {
        val srt = """
            1
            00:00:01,000 --> 00:00:03,500
            <i>Halo</i> HariMu

            2
            00:00:04,000 --> 00:00:06,250
            Baris kedua
        """.trimIndent()
        val srtCues = SubtitleParser.parseSrtOrVtt(srt)
        assertEquals(2, srtCues.size)
        assertEquals(1000L, srtCues[0].startMs)
        assertEquals(3500L, srtCues[0].endMs)
        assertEquals("Halo HariMu", srtCues[0].text)

        val vtt = """
            WEBVTT

            00:02.500 --> 00:05.000
            Subtitle VTT
        """.trimIndent()
        val vttCues = SubtitleParser.parseSrtOrVtt(vtt)
        assertEquals(1, vttCues.size)
        assertEquals(2500L, vttCues[0].startMs)
        assertEquals(5000L, vttCues[0].endMs)

        val ass = SubtitleParser.toAssContent(
            cues = srtCues,
            playResW = 1920,
            playResH = 1080,
            fontSize = 42,
            colorName = "Kuning",
            styleMode = "Kotak Gelap",
            position = "Atas"
        )
        assertTrue(ass.contains("[V4+ Styles]"))
        assertTrue(ass.contains("BorderStyle,Outline") || ass.contains("Style: Default"))
        assertTrue(ass.contains("Dialogue: 0,0:00:01.00,0:00:03.50"))
    }

    @Test
    fun videoBatch2MathBuildsValidFiltersAndFaceTrackWindows() {
        // VID-11 deshake
        val stab = VideoBatch2Math.buildStabilizeFilter("Kuat", cropBorder = true)
        assertTrue(stab.contains("deshake="))
        assertTrue(stab.contains("rx=48"))
        assertTrue(stab.contains("crop="))

        // VID-14 ROI clamp + filter
        val roi = VideoBatch2Math.clampRoiRect(1920, 1080, xPct = 80f, yPct = 85f, wPct = 30f, hPct = 25f)
        assertTrue(roi.x + roi.width <= 1920)
        assertTrue(roi.y + roi.height <= 1080)
        assertEquals(0, roi.width % 2)
        assertEquals(0, roi.height % 2)
        val delogoFilter = VideoBatch2Math.buildRemoveLogoFilter(roi, "Blur Halus", 18)
        assertTrue(delogoFilter.contains("gblur="))
        assertTrue(delogoFilter.contains("overlay=${roi.x}:${roi.y}"))

        // VID-16 Add Audio
        val (mixFc, mixMap) = VideoBatch2Math.buildAddAudioFilter(true, true, 50f, 120f, 2.5f)
        assertEquals("[aout]", mixMap)
        assertTrue(mixFc.contains("adelay=2500|2500"))
        assertTrue(mixFc.contains("amix=inputs=2"))

        // VID-26 LUT + Color grading
        val cfg = VideoBatch2Math.colorPresetConfig("Sinematik Hangat", 0f, 1f, 1f, 0f)
        val cube = VideoBatch2Math.generatePresetCubeLut(cfg, size = 5)
        assertTrue(cube.contains("LUT_3D_SIZE 5"))
        val gradeVf = VideoBatch2Math.buildColorGradingFfmpegFilter(cfg, "/tmp/warm.cube")
        assertTrue(gradeVf.contains("lut3d=file='/tmp/warm.cube'"))
        assertTrue(gradeVf.contains("colorbalance="))

        // VID-27 Transitions
        val (transFc, mapAudio) = VideoBatch2Math.buildTransitionFilterGraph(
            clips = listOf(
                TransitionClipInfo("a.mp4", 4_000L, true),
                TransitionClipInfo("b.mp4", 5_000L, true),
                TransitionClipInfo("c.mp4", 3_000L, true)
            ),
            transitionName = "dissolve",
            transitionDurationSec = 1.0f,
            targetWidth = 1280,
            targetHeight = 720
        )
        assertTrue(mapAudio)
        assertTrue(transFc.contains("xfade=transition=dissolve:duration=1.000:offset=3.000"))
        assertTrue(transFc.contains("acrossfade=d=1.000"))

        // VID-28 Chroma key
        val chroma = VideoBatch2Math.buildChromaKeyFilter(
            hasBackgroundMedia = false,
            backgroundIsVideo = false,
            keyHex = "0x00FF00",
            similarity = 0.25f,
            blend = 0.1f,
            targetW = 1280,
            targetH = 720,
            fallbackBgHex = "0x111827"
        )
        assertTrue(chroma.contains("chromakey=0x00FF00:0.25:0.10"))
        assertTrue(chroma.contains("color=c=0x111827"))

        // VID-25 Face smoothing & filter graph
        val samples = listOf(
            TimedFaces(0L, listOf(FaceBox(100, 80, 120, 120, trackingId = 7))),
            TimedFaces(500L, listOf(FaceBox(104, 82, 122, 122, trackingId = 7))),
            TimedFaces(1000L, listOf(FaceBox(106, 84, 120, 120, trackingId = 7)))
        )
        val windows = VideoBatch2Math.smoothAndGroupFaceTracks(
            samples = samples,
            srcWidth = 1280,
            srcHeight = 720,
            detectWidth = 640,
            detectHeight = 360,
            totalDurationMs = 3_000L
        )
        assertEquals(1, windows.size)
        val blurGraph = VideoBatch2Math.buildFaceBlurFilterGraph(
            windows = listOf(BlurredTrackWindow(0L, 3_000L, windows.first().box)),
            pixelate = false,
            blurStrength = 16
        )
        assertTrue(blurGraph.contains("gblur=sigma=16"))
        assertTrue(blurGraph.contains("enable='between(t,0.00,3.00)'"))
    }

    @Test
    fun roomSavesAndResumesVideoProjectAndEditorExportsThreeClipsWithTrimAndSpeed() = runBlocking {
        val sampleA = sampleVideoFile("clip_a.mp4")
        val sampleB = sampleVideoFile("clip_b.mp4")
        val sampleC = sampleVideoFile("clip_c.mp4")

        val uriA = Uri.fromFile(sampleA)
        val uriB = Uri.fromFile(sampleB)
        val uriC = Uri.fromFile(sampleC)
        registerShadowVideoMetadata(uriA, durationMs = 6_000L, width = 1280, height = 720, includeAudio = true)
        registerShadowVideoMetadata(uriB, durationMs = 8_000L, width = 1920, height = 1080, includeAudio = false)
        registerShadowVideoMetadata(uriC, durationMs = 4_000L, width = 1280, height = 720, includeAudio = true)

        val clips = listOf(
            EditorClipSpec("c1", uriA.toString(), "clip_a.mp4", 6_000L, trimStartMs = 1_000L, trimEndMs = 5_000L, speed = 2.0f, rotationDeg = 0),
            EditorClipSpec("c2", uriB.toString(), "clip_b.mp4", 8_000L, trimStartMs = 0L, trimEndMs = 4_000L, speed = 0.5f, rotationDeg = 90),
            EditorClipSpec("c3", uriC.toString(), "clip_c.mp4", 4_000L, trimStartMs = 500L, trimEndMs = 3_500L, speed = 1.0f, rotationDeg = 180, muteAudio = true)
        )
        assertEquals(13_000L, VideoEditorMath.totalTimelineDurationMs(clips))

        workspace.saveVideoProject(
            id = "proj-1",
            title = "Vlog Liburan",
            clips = clips,
            outputResolution = "720p"
        )
        val loaded = workspace.getVideoProject("proj-1")
        assertNotNull(loaded)
        assertEquals("Vlog Liburan", loaded!!.title)
        val decoded = workspace.decodeEditorClips(loaded.clipsJson)
        assertEquals(3, decoded.size)
        assertEquals(2.0f, decoded[0].speed, 0.01f)
        assertEquals(90, decoded[1].rotationDeg)
        assertTrue(decoded[2].muteAudio)
        assertEquals(1, workspace.videoProjectsFlow.first().size)

        val executedCommands = mutableListOf<List<String>>()
        val fakeFfmpeg = MediaJob(runner = object : FfmpegSessionRunner {
            override fun executeAsync(
                arguments: List<String>,
                onLog: (String) -> Unit,
                onTimeMs: (Double) -> Unit,
                onComplete: (Int?, Boolean, Boolean, String) -> Unit
            ): FfmpegSessionHandle {
                executedCommands.add(arguments)
                sampleA.copyTo(File(arguments.last()), overwrite = true)
                onTimeMs(500.0)
                onComplete(0, true, false, "ok")
                return object : FfmpegSessionHandle {
                    override val sessionId: Long = 9L
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
                    sampleA.copyTo(outputFile, overwrite = true)
                    onProgress(90)
                    onCompleted()
                    return object : TransformerHandle { override fun cancel() = Unit }
                }
            },
            ffmpegJob = fakeFfmpeg
        )

        val editorEngine = VideoEditorEngine(media3 = fakeMedia3, mediaJob = fakeFfmpeg)
        val events = editorEngine.exportTimeline(context, storage, decoded, "720p", isEnglish = true).toList()
        val done = events.last() as ToolProgress.Done
        assertTrue(done.result.outputs.first().file.exists())
        assertTrue(done.result.summary.contains("3 clips"))
        // Clip 2 & 3 had silent/muted audio while clip 1 had audio -> normalized via FFmpeg with anullsrc
        assertTrue(executedCommands.any { cmd -> cmd.any { it.contains("anullsrc") } })
        assertTrue(executedCommands.any { cmd -> cmd.any { it.contains("transpose=1") } })
    }

    @Test
    fun batch2VideoToolsExecuteWithExpectedCommands() = runBlocking {
        val sample = sampleVideoFile("batch2_vid.mp4")
        val videoUri = Uri.fromFile(sample)
        registerShadowVideoMetadata(videoUri, durationMs = 5_000L, width = 1280, height = 720, includeAudio = true)

        val imgFile = samplePngFile("overlay_b2.png")
        val imgUri = Uri.fromFile(imgFile)

        val srtFile = File(context.cacheDir, "subs_b2.srt").apply {
            writeText("1\n00:00:00,500 --> 00:00:02,000\nTes Subtitle\n")
        }
        val srtUri = Uri.fromFile(srtFile)

        val commands = mutableListOf<List<String>>()
        val fakeFfmpeg = MediaJob(runner = object : FfmpegSessionRunner {
            override fun executeAsync(
                arguments: List<String>,
                onLog: (String) -> Unit,
                onTimeMs: (Double) -> Unit,
                onComplete: (Int?, Boolean, Boolean, String) -> Unit
            ): FfmpegSessionHandle {
                commands.add(arguments)
                sample.copyTo(File(arguments.last()), overwrite = true)
                onTimeMs(500.0)
                onComplete(0, true, false, "ok")
                return object : FfmpegSessionHandle {
                    override val sessionId: Long = 10L
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
                    onProgress(85)
                    onCompleted()
                    return object : TransformerHandle { override fun cancel() = Unit }
                }
            },
            ffmpegJob = fakeFfmpeg
        )

        val tools = (videoForgeBatch2Tools(fakeMedia3, fakeFfmpeg) +
            videoForgeBatch2ExtraTools(fakeMedia3, fakeFfmpeg)).associateBy { it.id }
        val ctx = ToolContext(context, storage, isEnglish = true)

        // VID-11 Stabilize -> deshake
        assertTrue(tools.getValue("VID-11").run(ctx, ToolInput(uris = listOf(videoUri))).toList().last() is ToolProgress.Done)
        assertTrue(commands.last().any { it.contains("deshake=") })

        // VID-14 Remove Logo -> crop + gblur + overlay
        assertTrue(tools.getValue("VID-14").run(ctx, ToolInput(uris = listOf(videoUri))).toList().last() is ToolProgress.Done)
        assertTrue(commands.last().any { it.contains("gblur=") && it.contains("overlay=") })

        // VID-15 Add Text (timed) -> FFmpeg overlay=enable
        val vid15Done = tools.getValue("VID-15").run(
            ctx,
            ToolInput(uris = listOf(videoUri), params = mapOf("caption" to "HariMu", "startSec" to 1.0f, "endSec" to 3.0f))
        ).toList().last()
        assertTrue(vid15Done is ToolProgress.Done)
        assertTrue(commands.last().any { it.contains("between(t,1.00,3.00)") })

        // VID-16 Add Audio -> mix + offset
        val audioFile = File(context.cacheDir, "bgm.m4a").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
        val vid16Done = tools.getValue("VID-16").run(
            ctx,
            ToolInput(uris = listOf(videoUri), secondaryUri = Uri.fromFile(audioFile), params = mapOf("offsetSec" to 1.5f))
        ).toList().last()
        assertTrue(vid16Done is ToolProgress.Done)
        assertTrue(commands.last().any { it.contains("adelay=1500|1500") })

        // VID-17 Add Image
        assertTrue(
            tools.getValue("VID-17").run(ctx, ToolInput(uris = listOf(videoUri), secondaryUri = imgUri)).toList().last() is ToolProgress.Done
        )

        // VID-23 Subtitles -> ass filter
        assertTrue(
            tools.getValue("VID-23").run(ctx, ToolInput(uris = listOf(videoUri), secondaryUri = srtUri)).toList().last() is ToolProgress.Done
        )
        assertTrue(commands.last().any { it.contains("ass=") })

        // VID-26 Color Grading
        assertTrue(
            tools.getValue("VID-26").run(ctx, ToolInput(uris = listOf(videoUri), params = mapOf("preset" to "Vivid Pop"))).toList().last() is ToolProgress.Done
        )

        // VID-27 Transitions
        assertTrue(
            tools.getValue("VID-27").run(ctx, ToolInput(uris = listOf(videoUri, videoUri), params = mapOf("transition" to "wipeleft"))).toList().last() is ToolProgress.Done
        )
        assertTrue(commands.last().any { it.contains("xfade=transition=wipeleft") })

        // VID-28 Green Screen
        assertTrue(
            tools.getValue("VID-28").run(ctx, ToolInput(uris = listOf(videoUri))).toList().last() is ToolProgress.Done
        )
        assertTrue(commands.last().any { it.contains("chromakey=") })

        // VID-29 Stop Motion
        assertTrue(
            tools.getValue("VID-29").run(ctx, ToolInput(uris = listOf(imgUri, imgUri), params = mapOf("fps" to 8f))).toList().last() is ToolProgress.Done
        )

        // VID-30 Time Lapse
        assertTrue(
            tools.getValue("VID-30").run(ctx, ToolInput(uris = listOf(videoUri), params = mapOf("speedFactor" to "16x"))).toList().last() is ToolProgress.Done
        )
        assertTrue(commands.last().any { it.contains("setpts=0.06250*PTS") })
    }
}
