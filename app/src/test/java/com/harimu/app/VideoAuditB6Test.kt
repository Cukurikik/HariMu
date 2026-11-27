package com.harimu.app

import android.app.Application
import android.content.Context
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.harimu.app.core.data.EditorClipSpec
import com.harimu.app.core.files.FileStorageManager
import com.harimu.app.core.media.FfmpegSessionHandle
import com.harimu.app.core.media.FfmpegSessionRunner
import com.harimu.app.core.media.Media3Request
import com.harimu.app.core.media.Media3TransformerEngine
import com.harimu.app.core.media.MediaJob
import com.harimu.app.core.media.MediaProbeResult
import com.harimu.app.core.media.TransformerBackend
import com.harimu.app.core.media.TransformerHandle
import com.harimu.app.core.media.VideoEditorEngine
import com.harimu.app.core.media.VideoToolMath
import com.harimu.app.core.toolapi.ToolContext
import com.harimu.app.core.toolapi.ToolInput
import com.harimu.app.core.toolapi.ToolProgress
import com.harimu.app.core.toolapi.ToolRegistry
import com.harimu.app.feature.studios.VideoEditorViewModel
import com.harimu.app.feature.tools.videoForgeBatch1CoreTools
import com.harimu.app.feature.tools.videoForgeBatch1ExtraTools
import com.harimu.app.feature.tools.videoForgeBatch2Tools
import com.harimu.app.feature.toolscreen.ToolWorkspaceValidator
import com.harimu.app.feature.toolscreen.WorkspaceStage
import kotlinx.coroutines.Dispatchers
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class VideoAuditB6Test {

    private lateinit var app: Application
    private lateinit var context: Context
    private lateinit var storage: FileStorageManager

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        context = app
        storage = FileStorageManager(context)
        ShadowStatFs.registerStats(storage.outputsDir.absolutePath, 4 * 1024 * 1024, 2 * 1024 * 1024, 2 * 1024 * 1024)
    }

    private fun sampleVideoFile(name: String = "b6_sample.mp4"): File {
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

    private fun registerShadowVideo(
        uri: Uri,
        durationMs: Long,
        width: Int,
        height: Int,
        rotationDeg: Int = 0,
        includeAudio: Boolean = true
    ) {
        val ds = DataSource.toDataSource(context, uri)
        ShadowMediaMetadataRetriever.addMetadata(ds, MediaMetadataRetriever.METADATA_KEY_DURATION, durationMs.toString())
        ShadowMediaMetadataRetriever.addMetadata(ds, MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH, width.toString())
        ShadowMediaMetadataRetriever.addMetadata(ds, MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT, height.toString())
        ShadowMediaMetadataRetriever.addMetadata(ds, MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION, rotationDeg.toString())
        ShadowMediaMetadataRetriever.addMetadata(ds, MediaMetadataRetriever.METADATA_KEY_BITRATE, "4000000")

        val vFmt = MediaFormat.createVideoFormat("video/avc", width, height).apply {
            setInteger(MediaFormat.KEY_FRAME_RATE, 30)
            setLong(MediaFormat.KEY_DURATION, durationMs * 1000L)
        }
        ShadowMediaExtractor.addTrack(ds, vFmt, ByteArray(0))
        if (includeAudio) {
            val aFmt = MediaFormat.createAudioFormat("audio/mp4a-latm", 44100, 2).apply {
                setLong(MediaFormat.KEY_DURATION, durationMs * 1000L)
            }
            ShadowMediaExtractor.addTrack(ds, aFmt, ByteArray(0))
        }
    }

    private fun fakeMediaEngines(
        recordedCommands: MutableList<List<String>>,
        media3FailsOnTag: Set<String> = emptySet()
    ): Pair<Media3TransformerEngine, MediaJob> {
        val sample = sampleVideoFile()
        val fakeFfmpeg = MediaJob(runner = object : FfmpegSessionRunner {
            override fun executeAsync(
                arguments: List<String>,
                onLog: (String) -> Unit,
                onTimeMs: (Double) -> Unit,
                onComplete: (Int?, Boolean, Boolean, String) -> Unit
            ): FfmpegSessionHandle {
                recordedCommands.add(arguments)
                val outPath = arguments.last()
                File(outPath).parentFile?.mkdirs()
                sample.copyTo(File(outPath), overwrite = true)
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
                    if (request.toolTag in media3FailsOnTag) {
                        onError(IllegalStateException("Simulated Media3 error for ${request.toolTag}"))
                    } else {
                        outputFile.parentFile?.mkdirs()
                        sample.copyTo(outputFile, overwrite = true)
                        onProgress(100)
                        onCompleted()
                    }
                    return object : TransformerHandle { override fun cancel() = Unit }
                }
            },
            ffmpegJob = fakeFfmpeg
        )
        return fakeMedia3 to fakeFfmpeg
    }

    @Test
    fun mediaProbeOrientedDimensionsHandleQuarterTurns() {
        val r0 = MediaProbeResult(width = 1920, height = 1080, rotationDegrees = 0)
        assertFalse(r0.isQuarterTurnRotated)
        assertEquals(1920, r0.orientedWidth)
        assertEquals(1080, r0.orientedHeight)

        val r90 = MediaProbeResult(width = 1920, height = 1080, rotationDegrees = 90)
        assertTrue(r90.isQuarterTurnRotated)
        assertEquals(1080, r90.orientedWidth)
        assertEquals(1920, r90.orientedHeight)

        val r180 = MediaProbeResult(width = 1920, height = 1080, rotationDegrees = 180)
        assertFalse(r180.isQuarterTurnRotated)
        assertEquals(1920, r180.orientedWidth)
        assertEquals(1080, r180.orientedHeight)

        val r270 = MediaProbeResult(width = 1920, height = 1080, rotationDegrees = 270)
        assertTrue(r270.isQuarterTurnRotated)
        assertEquals(1080, r270.orientedWidth)
        assertEquals(1920, r270.orientedHeight)
    }

    @Test
    fun trimRangeValidationAndWorkspaceValidatorEnforceBounds() {
        assertNull(VideoToolMath.validateTrimRange(1_000L, 5_000L, 10_000L, isEnglish = true))
        assertNotNull(VideoToolMath.validateTrimRange(-500L, 5_000L, 10_000L, isEnglish = true))
        assertNotNull(VideoToolMath.validateTrimRange(10_000L, 12_000L, 10_000L, isEnglish = true))
        assertNotNull(VideoToolMath.validateTrimRange(6_000L, 4_000L, 10_000L, isEnglish = false))

        val dummyUri = Uri.fromFile(sampleVideoFile())
        val vid15 = ToolRegistry.findById("VID-15")!!
        val invalidTextRange = ToolWorkspaceValidator.validateWorkspaceInput(
            context = context,
            tool = vid15,
            input = ToolInput(
                uris = listOf(dummyUri),
                params = mapOf("caption" to "Test", "startSec" to 8f, "endSec" to 4f)
            ),
            checkUriReadability = false
        )
        assertFalse(invalidTextRange.isValid)
        assertEquals(WorkspaceStage.INVALID_INPUT, invalidTextRange.stage)

        val vid17 = ToolRegistry.findById("VID-17")!!
        val validOverlayRange = ToolWorkspaceValidator.validateWorkspaceInput(
            context = context,
            tool = vid17,
            input = ToolInput(
                uris = listOf(dummyUri),
                secondaryUri = Uri.fromFile(sampleVideoFile("overlay_placeholder.mp4")),
                params = mapOf("startSec" to 1f, "endSec" to 5f)
            ),
            checkUriReadability = false
        )
        assertTrue(validOverlayRange.isValid)
    }

    @Test
    fun rotatedPortraitVideoUsesOrientedDimensionsAcrossToolsAndEditor() = runBlocking {
        val rotatedFile = File(context.cacheDir, "rotated_portrait_1080x1920.mp4").also {
            sampleVideoFile().copyTo(it, overwrite = true)
        }
        val rotatedUri = Uri.fromFile(rotatedFile)
        // Coded 1920x1080 with 90-degree rotation => display size is portrait 1080x1920
        registerShadowVideo(rotatedUri, durationMs = 15_000L, width = 1920, height = 1080, rotationDeg = 90, includeAudio = true)

        val cmds = mutableListOf<List<String>>()
        val (fakeMedia3, fakeFfmpeg) = fakeMediaEngines(cmds, media3FailsOnTag = setOf("VID_RESIZE", "VID_CROP"))
        val coreTools = videoForgeBatch1CoreTools(fakeMedia3, fakeFfmpeg).associateBy { it.id }
        val batch2Tools = videoForgeBatch2Tools(fakeMedia3, fakeFfmpeg).associateBy { it.id }
        val ctx = ToolContext(context, storage, isEnglish = true)

        // VID-04 Resize to 720p portrait -> 720x1280
        val resizeRes = coreTools.getValue("VID-04").run(
            ctx, ToolInput(uris = listOf(rotatedUri), params = mapOf("res" to "720p"))
        ).toList().last()
        assertTrue(resizeRes is ToolProgress.Done)
        assertTrue(cmds.last().any { it.contains("scale=720:1280") })

        // VID-14 Logo blur on portrait 1080x1920 -> ROI must stay in portrait height 1920
        cmds.clear()
        val delogoRes = batch2Tools.getValue("VID-14").run(
            ctx,
            ToolInput(
                uris = listOf(rotatedUri),
                params = mapOf("xPct" to 10f, "yPct" to 80f, "wPct" to 20f, "hPct" to 15f)
            )
        ).toList().last()
        assertTrue(delogoRes is ToolProgress.Done)
        // 80% of orientedHeight 1920 is 1536 (> 1080 coded height)
        val filterArg = cmds.last().joinToString(" ")
        assertTrue("Expected portrait ROI y > 1080 in $filterArg", filterArg.contains("crop=") && filterArg.contains(":1536"))

        // VideoEditorEngine portrait canvas export + mixed-codec concat fallback
        cmds.clear()
        val clipMuted = EditorClipSpec(
            id = "c1",
            uriString = rotatedUri.toString(),
            displayName = "Clip 1",
            durationMs = 15_000L,
            trimStartMs = 0L,
            trimEndMs = 4_000L,
            muteAudio = true // forces FFmpeg segment (since overall includeAudio = true)
        )
        val clipAudio = EditorClipSpec(
            id = "c2",
            uriString = rotatedUri.toString(),
            displayName = "Clip 2",
            durationMs = 15_000L,
            trimStartMs = 1_000L,
            trimEndMs = 6_000L,
            muteAudio = false // runs through Media3 segment => mixed codecs across clips
        )
        val (edMedia3, edFfmpeg) = fakeMediaEngines(cmds, media3FailsOnTag = setOf("ED_MERGE"))
        val editorEngine = VideoEditorEngine(edMedia3, edFfmpeg)
        val exportEvents = editorEngine.exportTimeline(
            context = context,
            storage = storage,
            clips = listOf(clipMuted, clipAudio),
            resolutionPreset = "720p",
            isEnglish = true
        ).toList()
        assertTrue("Expected editor export Done, got ${exportEvents.last()}", exportEvents.last() is ToolProgress.Done)
        val mergeCmd = cmds.last().joinToString(" ")
        assertTrue("Expected portrait 720:1280 concat fallback", mergeCmd.contains("720:1280") && mergeCmd.contains("concat=n=2:v=1:a=1"))
    }

    @Test
    fun videoToolsRejectOutOfRangeTrimAndSplitOver100Parts() = runBlocking {
        val sampleFile = File(context.cacheDir, "bounds_check_10s.mp4").also {
            sampleVideoFile().copyTo(it, overwrite = true)
        }
        val uri = Uri.fromFile(sampleFile)
        registerShadowVideo(uri, durationMs = 10_000L, width = 1280, height = 720, includeAudio = true)

        val cmds = mutableListOf<List<String>>()
        val (fakeMedia3, fakeFfmpeg) = fakeMediaEngines(cmds)
        val coreTools = videoForgeBatch1CoreTools(fakeMedia3, fakeFfmpeg).associateBy { it.id }
        val extraTools = videoForgeBatch1ExtraTools(fakeFfmpeg).associateBy { it.id }
        val batch2Tools = videoForgeBatch2Tools(fakeMedia3, fakeFfmpeg).associateBy { it.id }
        val ctx = ToolContext(context, storage, isEnglish = true)

        val trimFail = coreTools.getValue("VID-01").run(
            ctx, ToolInput(uris = listOf(uri), params = mapOf("startSec" to 12f, "endSec" to 15f))
        ).toList().last()
        assertTrue(trimFail is ToolProgress.Failed)

        val gifFail = extraTools.getValue("VID-06").run(
            ctx, ToolInput(uris = listOf(uri), params = mapOf("startSec" to 11f, "maxSec" to 5f))
        ).toList().last()
        assertTrue(gifFail is ToolProgress.Failed)

        val audioOffsetFail = batch2Tools.getValue("VID-16").run(
            ctx,
            ToolInput(
                uris = listOf(uri),
                secondaryUri = Uri.fromFile(sampleFile),
                params = mapOf("offsetSec" to 12f)
            )
        ).toList().last()
        assertTrue(audioOffsetFail is ToolProgress.Failed)

        // Long 10-minute video with 3s segments = 200 parts > 100 max limit
        val longFile = File(context.cacheDir, "long_split_10m.mp4").also {
            sampleVideoFile().copyTo(it, overwrite = true)
        }
        val longUri = Uri.fromFile(longFile)
        registerShadowVideo(longUri, durationMs = 600_000L, width = 1280, height = 720, includeAudio = false)
        val splitTooMany = extraTools.getValue("VID-21").run(
            ctx, ToolInput(uris = listOf(longUri), params = mapOf("segSec" to 3f))
        ).toList().last()
        assertTrue(splitTooMany is ToolProgress.Failed)
    }

    @Test
    fun videoEditorViewModelClampsZoomAndPersistsAndCancelsExport() = runBlocking {
        val container = AppContainer(app)
        val vm = VideoEditorViewModel(container)

        vm.setTimelineZoom(10.0f)
        assertEquals(4.0f, vm.state.value.timelineZoom, 0.001f)
        vm.setTimelineZoom(0.05f)
        assertEquals(0.25f, vm.state.value.timelineZoom, 0.001f)
        vm.setTimelineZoom(1.5f)
        assertEquals(1.5f, vm.state.value.timelineZoom, 0.001f)

        val testClip = EditorClipSpec(
            id = "clip-vm-1",
            uriString = Uri.fromFile(sampleVideoFile()).toString(),
            displayName = "Sample.mp4",
            durationMs = 8_000L,
            trimStartMs = 500L,
            trimEndMs = 6_500L
        )
        vm.loadProject(
            com.harimu.app.core.data.VideoProjectEntity(
                id = "proj-b6-1",
                title = "B6 Test Project",
                clipsJson = container.workspace.encodeEditorClips(listOf(testClip)),
                outputResolution = "720p",
                createdAt = 1000L,
                updatedAt = 2000L
            )
        )
        assertEquals(1, vm.state.value.clips.size)

        vm.updateSelectedClip { it.copy(speed = 2.0f, trimStartMs = 1_000L) }
        assertEquals(2.0f, vm.state.value.selectedClip!!.speed, 0.001f)
        assertEquals(1_000L, vm.state.value.selectedClip!!.trimStartMs)

        vm.cancelExport()
        assertFalse(vm.state.value.isExporting)
        assertNull(vm.state.value.progress)
    }
}
