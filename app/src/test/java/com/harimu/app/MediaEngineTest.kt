package com.harimu.app

import android.app.Application
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.arthenica.ffmpegkit.MediaInformationJsonParser
import com.harimu.app.core.files.FileStorageManager
import com.harimu.app.core.media.BoundedLogCollector
import com.harimu.app.core.media.FfmpegProgressParser
import com.harimu.app.core.media.FfmpegSessionHandle
import com.harimu.app.core.media.FfmpegSessionRunner
import com.harimu.app.core.media.Media3Request
import com.harimu.app.core.media.Media3TransformerEngine
import com.harimu.app.core.media.MediaErrorMapper
import com.harimu.app.core.media.MediaJob
import com.harimu.app.core.media.MediaProbe
import com.harimu.app.core.media.MediaUriUtils
import com.harimu.app.core.media.TransformerBackend
import com.harimu.app.core.media.TransformerHandle
import com.harimu.app.core.toolapi.ToolProgress
import com.harimu.app.core.toolapi.ToolRegistry
import com.harimu.app.feature.tools.InternalMediaTestTool
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowStatFs
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class MediaEngineTest {

    private lateinit var context: Context
    private lateinit var storage: FileStorageManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        storage = FileStorageManager(context)
        ShadowStatFs.registerStats(storage.outputsDir.absolutePath, 1024 * 1024, 512 * 1024, 512 * 1024)
    }

    private fun copyAssetToCache(assetName: String): File {
        val out = File(context.cacheDir, assetName)
        val assetStream = runCatching { context.assets.open(assetName) }.getOrNull()
        if (assetStream != null) {
            assetStream.use { inp ->
                out.outputStream().use { dest -> inp.copyTo(dest) }
            }
            return out
        }
        val candidates = listOf(
            File("src/androidTest/assets", assetName),
            File("app/src/androidTest/assets", assetName)
        )
        val diskAsset = candidates.firstOrNull { it.exists() }
            ?: throw java.io.FileNotFoundException("Sample asset not found: $assetName")
        diskAsset.copyTo(out, overwrite = true)
        return out
    }

    @Test
    fun progressParserExtractsTimeDurationAndPercent() {
        assertEquals(83_450L, FfmpegProgressParser.parseTimeMsFromLog("frame=  42 fps=24.0 time=00:01:23.45 bitrate=99.7kbits/s"))
        assertEquals(120_500L, FfmpegProgressParser.parseDurationMsFromLog("  Duration: 00:02:00.50, start: 0.000000, bitrate: 256 kb/s"))
        assertEquals(50, FfmpegProgressParser.computePercent(600.0, 1200L))
        assertEquals(99, FfmpegProgressParser.computePercent(5000.0, 1200L))
        assertEquals(0, FfmpegProgressParser.computePercent(600.0, 0L))
        assertEquals("01:23", FfmpegProgressParser.formatDurationMs(83_000L))
    }

    @Test
    fun errorMapperMapsFfmpegAndMedia3ErrorsAndCapsLogTail() {
        val collector = BoundedLogCollector(maxLines = 5)
        (1..8).forEach { collector.append("line-$it") }
        assertEquals(listOf("line-4", "line-5", "line-6", "line-7", "line-8"), collector.allLines())

        val noSpace = MediaErrorMapper.mapFfmpegFailure(1, false, "av_interleaved_write_frame(): No space left on device", isEnglish = true)
        assertTrue(noSpace.userMessage.contains("Storage is full"))

        val invalid = MediaErrorMapper.mapFfmpegFailure(1, false, "moov atom not found\nInvalid data found when processing input", isEnglish = false)
        assertTrue(invalid.userMessage.contains("rusak"))

        val cancelled = MediaErrorMapper.mapFfmpegFailure(255, true, "", isEnglish = false)
        assertTrue(cancelled.userMessage.contains("dibatalkan"))

        val m3 = MediaErrorMapper.mapMedia3Failure(IllegalStateException("Muxer does not support codec"), isEnglish = true)
        assertTrue(m3.userMessage.contains("Hardware muxer"))
    }

    @Test
    fun sampleAssetsExistAreValidAndUriUtilsSafelyCopiesAndNamesOutputs() {
        val videoAsset = copyAssetToCache("sample_video.mp4")
        val audioAsset = copyAssetToCache("sample_audio.wav")
        val imageAsset = copyAssetToCache("sample_image.png")

        assertTrue(videoAsset.exists() && videoAsset.length() > 1000L)
        assertTrue(audioAsset.exists() && audioAsset.length() > 1000L)
        assertTrue(imageAsset.exists() && imageAsset.length() > 50L)

        val bmp = BitmapFactory.decodeFile(imageAsset.absolutePath)
        assertNotNull(bmp)
        assertEquals(64, bmp.width)
        assertEquals(64, bmp.height)
        bmp.recycle()

        val jobDir = storage.createJobTempDir("uri_test")
        val safeCopy = MediaUriUtils.copyUriToSafeTemp(
            context = context,
            storage = storage,
            uri = Uri.fromFile(videoAsset),
            jobDir = jobDir,
            index = 0,
            fallbackExt = "mp4"
        )
        assertTrue(safeCopy.exists())
        assertEquals(videoAsset.length(), safeCopy.length())
        assertEquals("safe_name.mp4", MediaUriUtils.sanitizeName("..\\../safe name.mp4"))

        val outName = MediaUriUtils.buildOutputFileName("REMUX", "mp4", 1_763_900_000_000L)
        assertTrue(outName.startsWith("HariMu_REMUX_") && outName.endsWith(".mp4"))

        MediaUriUtils.cleanupQuietly(jobDir)
        assertFalse(jobDir.exists())
    }

    @Test
    fun mediaProbeParsesFfprobeJsonAndProbesSampleFilesSafely() {
        assertEquals(24f, MediaProbe.parseFpsFraction("24000/1000"), 0.01f)
        assertEquals(29.97f, MediaProbe.parseFpsFraction("30000/1001"), 0.02f)
        assertEquals(2, MediaProbe.parseChannelCount("stereo", null))
        assertEquals(6, MediaProbe.parseChannelCount("5.1", 6L))

        val sampleJson = """
            {
              "format": {
                "filename": "sample_video.mp4",
                "format_name": "mov,mp4,m4a,3gp,3g2,mj2",
                "duration": "1.200000",
                "size": "15000",
                "bit_rate": "99700"
              },
              "streams": [
                {
                  "index": 0,
                  "codec_type": "video",
                  "codec_name": "h264",
                  "width": 128,
                  "height": 72,
                  "r_frame_rate": "24/1",
                  "avg_frame_rate": "24/1"
                },
                {
                  "index": 1,
                  "codec_type": "audio",
                  "codec_name": "aac",
                  "sample_rate": "44100",
                  "channels": 2,
                  "channel_layout": "stereo"
                }
              ]
            }
        """.trimIndent()
        val info = MediaInformationJsonParser.from(sampleJson)
        val parsed = MediaProbe.parseMediaInformation(info)
        assertEquals(1200L, parsed.durationMs)
        assertEquals(128, parsed.width)
        assertEquals(72, parsed.height)
        assertEquals(24f, parsed.fps, 0.01f)
        assertEquals("h264", parsed.videoCodec)
        assertEquals("aac", parsed.audioCodec)
        assertEquals(2, parsed.audioChannels)
        assertEquals(44100, parsed.audioSampleRateHz)

        val videoFile = copyAssetToCache("sample_video.mp4")
        val probedVideo = MediaProbe.probeFile(videoFile)
        assertEquals(videoFile.length(), probedVideo.fileSizeBytes)
        assertTrue(probedVideo.formatSummary(isEnglish = true).contains("Duration:"))

        val audioFile = copyAssetToCache("sample_audio.wav")
        val probedAudio = MediaProbe.probe(context, Uri.fromFile(audioFile))
        assertEquals(audioFile.length(), probedAudio.fileSizeBytes)

        val imageFile = copyAssetToCache("sample_image.png")
        val probedImage = MediaProbe.probe(context, Uri.fromFile(imageFile))
        assertEquals(imageFile.length(), probedImage.fileSizeBytes)
    }

    @Test
    fun mediaJobRemuxesWithProgressAndHandlesCancellationMidJob() = runBlocking {
        val sampleVideo = copyAssetToCache("sample_video.mp4")
        val fakeRunner = object : FfmpegSessionRunner {
            override fun executeAsync(
                arguments: List<String>,
                onLog: (String) -> Unit,
                onTimeMs: (Double) -> Unit,
                onComplete: (Int?, Boolean, Boolean, String) -> Unit
            ): FfmpegSessionHandle {
                val inputPath = arguments[arguments.indexOf("-i") + 1]
                val outputPath = arguments.last()
                onLog("Duration: 00:00:01.20, start: 0.000000, bitrate: 99 kb/s")
                onLog("frame=15 fps=24.0 time=00:00:00.60 bitrate=99.7kbits/s")
                onTimeMs(600.0)
                File(inputPath).copyTo(File(outputPath), overwrite = true)
                onComplete(0, true, false, "muxing overhead: 1.2%")
                return object : FfmpegSessionHandle {
                    override val sessionId: Long = 101L
                    override fun cancel() = Unit
                }
            }
        }

        val mediaJob = MediaJob(runner = fakeRunner)
        val events = mediaJob.remuxMp4(context, storage, Uri.fromFile(sampleVideo), isEnglish = false).toList()

        assertTrue(events.any { it is ToolProgress.Queued })
        assertTrue(events.any { it is ToolProgress.Running && it.percent == 50 })
        val done = events.last() as ToolProgress.Done
        val outFile = done.result.outputs.first().file
        assertTrue(outFile.exists() && outFile.name.startsWith("HariMu_REMUX_"))
        assertEquals(sampleVideo.length(), outFile.length())
        assertTrue(done.result.textOutput?.contains("muxing overhead") == true)

        // Mid-job cancellation test: ensures cancel() is called & incomplete output is removed
        val cancelledCalled = AtomicBoolean(false)
        var createdOut: File? = null
        val slowRunner = object : FfmpegSessionRunner {
            override fun executeAsync(
                arguments: List<String>,
                onLog: (String) -> Unit,
                onTimeMs: (Double) -> Unit,
                onComplete: (Int?, Boolean, Boolean, String) -> Unit
            ): FfmpegSessionHandle {
                val outputPath = arguments.last()
                createdOut = File(outputPath).apply { writeBytes(byteArrayOf(1, 2, 3)) }
                onLog("Duration: 00:00:10.00")
                onTimeMs(2000.0)
                return object : FfmpegSessionHandle {
                    override val sessionId: Long = 202L
                    override fun cancel() {
                        cancelledCalled.set(true)
                        onComplete(255, false, true, "Exiting normally, received signal 15.")
                    }
                }
            }
        }

        val slowJob = MediaJob(runner = slowRunner)
        val received = mutableListOf<ToolProgress>()
        val collectorJob: Job = launch {
            try {
                slowJob.remuxMp4(context, storage, Uri.fromFile(sampleVideo)).collect {
                    received.add(it)
                }
            } catch (_: CancellationException) {
            }
        }
        delay(120L)
        collectorJob.cancel()
        collectorJob.join()

        assertTrue(cancelledCalled.get())
        assertTrue(received.any { it is ToolProgress.Running && it.percent == 20 })
        assertFalse(createdOut?.exists() == true)
    }

    @Test
    fun media3TransformerFallsBackToFfmpegAutomaticallyOnFailure() = runBlocking {
        val sampleVideo = copyAssetToCache("sample_video.mp4")

        val failingMedia3 = object : TransformerBackend {
            override fun startExport(
                context: Context,
                request: Media3Request,
                outputFile: File,
                onProgress: (Int) -> Unit,
                onCompleted: () -> Unit,
                onError: (Throwable) -> Unit
            ): TransformerHandle {
                outputFile.writeText("corrupt-partial")
                onProgress(15)
                onError(IllegalStateException("Mock Media3 codec failure"))
                return object : TransformerHandle {
                    override fun cancel() = Unit
                }
            }
        }
        val succeedingFfmpeg = MediaJob(runner = object : FfmpegSessionRunner {
            override fun executeAsync(
                arguments: List<String>,
                onLog: (String) -> Unit,
                onTimeMs: (Double) -> Unit,
                onComplete: (Int?, Boolean, Boolean, String) -> Unit
            ): FfmpegSessionHandle {
                val inputPath = arguments[arguments.indexOf("-i") + 1]
                val outputPath = arguments.last()
                File(inputPath).copyTo(File(outputPath), overwrite = true)
                onTimeMs(1200.0)
                onComplete(0, true, false, "fallback remux ok")
                return object : FfmpegSessionHandle {
                    override val sessionId: Long = 303L
                    override fun cancel() = Unit
                }
            }
        })

        val engine = Media3TransformerEngine(failingMedia3, succeedingFfmpeg)
        val events = engine.remuxMp4WithFallback(context, storage, Uri.fromFile(sampleVideo), isEnglish = true).toList()

        assertTrue(events.any { it is ToolProgress.Running && it.message.contains("falling back to FFmpeg") })
        val done = events.last() as ToolProgress.Done
        assertTrue(done.result.outputs.first().file.exists())
        assertTrue(done.result.textOutput?.contains("Fallback Media3 -> FFmpeg") == true)
    }

    @Test
    fun media3TransformerCancelDoesNotTriggerFallbackAndCleansPartialOutput() = runBlocking {
        val sampleVideo = copyAssetToCache("sample_video.mp4")
        val media3Cancelled = AtomicBoolean(false)
        val ffmpegInvoked = AtomicBoolean(false)
        var partialOutput: File? = null

        val slowMedia3 = object : TransformerBackend {
            override fun startExport(
                context: Context,
                request: Media3Request,
                outputFile: File,
                onProgress: (Int) -> Unit,
                onCompleted: () -> Unit,
                onError: (Throwable) -> Unit
            ): TransformerHandle {
                partialOutput = outputFile.apply { writeText("partial-m3") }
                onProgress(35)
                return object : TransformerHandle {
                    override fun cancel() {
                        media3Cancelled.set(true)
                    }
                }
            }
        }
        val unusedFfmpeg = MediaJob(runner = object : FfmpegSessionRunner {
            override fun executeAsync(
                arguments: List<String>,
                onLog: (String) -> Unit,
                onTimeMs: (Double) -> Unit,
                onComplete: (Int?, Boolean, Boolean, String) -> Unit
            ): FfmpegSessionHandle {
                ffmpegInvoked.set(true)
                return object : FfmpegSessionHandle {
                    override val sessionId: Long = 404L
                    override fun cancel() = Unit
                }
            }
        })

        val engine = Media3TransformerEngine(slowMedia3, unusedFfmpeg)
        val received = mutableListOf<ToolProgress>()
        val job: Job = launch {
            try {
                engine.remuxMp4WithFallback(context, storage, Uri.fromFile(sampleVideo)).collect {
                    received.add(it)
                }
            } catch (_: CancellationException) {
            }
        }
        delay(120L)
        job.cancel()
        job.join()

        assertTrue(media3Cancelled.get())
        assertFalse(ffmpegInvoked.get())
        assertTrue(received.any { it is ToolProgress.Running && it.percent == 35 })
        assertFalse(partialOutput?.exists() == true)
    }

    @Test
    fun internalRemuxToolIsResolvableWithoutBreaking163CatalogCount() {
        assertEquals(169, ToolRegistry.allTools.size)
        val internal = ToolRegistry.findById(InternalMediaTestTool.ID)
        assertNotNull(internal)
        assertFalse(internal!!.isStub)
    }
}
