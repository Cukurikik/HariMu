package com.harimu.app.core.media

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.audio.SpeedProvider
import androidx.media3.common.util.ExperimentalApi
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import com.harimu.app.core.files.FileStorageManager
import com.harimu.app.core.toolapi.ToolOutputFile
import com.harimu.app.core.toolapi.ToolProgress
import com.harimu.app.core.toolapi.ToolResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

@androidx.annotation.OptIn(UnstableApi::class)
data class Media3Request(
    val inputUri: Uri,
    val toolTag: String,
    val outputExt: String = "mp4",
    val outputMime: String = "video/mp4",
    val removeAudio: Boolean = false,
    val removeVideo: Boolean = false,
    val videoMimeType: String? = null,
    val audioMimeType: String? = null,
    val additionalInputUris: List<Uri> = emptyList(),
    val clipStartMs: Long = 0L,
    val clipEndMs: Long = Long.MAX_VALUE,
    val cropAspectRatio: String? = null,
    val targetWidth: Int? = null,
    val targetHeight: Int? = null,
    val rotationDegrees: Float = 0f,
    val flipHorizontal: Boolean = false,
    val flipVertical: Boolean = false,
    val speed: Float = 1.0f,
    val targetVideoBitrateBps: Int? = null,
    val trimOptimization: Boolean = false,
    val extraVideoEffects: List<Effect> = emptyList(),
    val imageFrameDurationMs: Long = 0L
) {
    val allInputUris: List<Uri>
        get() = listOf(inputUri) + additionalInputUris
}

interface TransformerHandle {
    fun cancel()
}

interface TransformerBackend {
    fun startExport(
        context: Context,
        request: Media3Request,
        outputFile: File,
        onProgress: (percent: Int) -> Unit,
        onCompleted: () -> Unit,
        onError: (Throwable) -> Unit
    ): TransformerHandle
}

@androidx.annotation.OptIn(UnstableApi::class, ExperimentalApi::class)
object RealMedia3TransformerBackend : TransformerBackend {
    override fun startExport(
        context: Context,
        request: Media3Request,
        outputFile: File,
        onProgress: (percent: Int) -> Unit,
        onCompleted: () -> Unit,
        onError: (Throwable) -> Unit
    ): TransformerHandle {
        val mainHandler = Handler(Looper.getMainLooper())
        val stopped = AtomicBoolean(false)
        var transformerRef: Transformer? = null

        mainHandler.post {
            if (stopped.get()) return@post
            try {
                val videoEffects = buildList<Effect> {
                    request.cropAspectRatio?.let { ratio ->
                        add(
                            Presentation.createForAspectRatio(
                                VideoToolMath.parseAspectRatio(ratio),
                                Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP
                            )
                        )
                    }
                    if (request.targetWidth != null && request.targetHeight != null) {
                        add(
                            Presentation.createForWidthAndHeight(
                                request.targetWidth,
                                request.targetHeight,
                                Presentation.LAYOUT_SCALE_TO_FIT
                            )
                        )
                    }
                    if (request.rotationDegrees != 0f || request.flipHorizontal || request.flipVertical) {
                        add(
                            ScaleAndRotateTransformation.Builder()
                                .setRotationDegrees(request.rotationDegrees)
                                .setScale(
                                    if (request.flipHorizontal) -1f else 1f,
                                    if (request.flipVertical) -1f else 1f
                                )
                                .build()
                        )
                    }
                    addAll(request.extraVideoEffects)
                }

                val builder = Transformer.Builder(context)
                request.videoMimeType?.let { builder.setVideoMimeType(it) }
                request.audioMimeType?.let { builder.setAudioMimeType(it) }
                request.targetVideoBitrateBps?.let { bitrate ->
                    val encoderFactory = DefaultEncoderFactory.Builder(context)
                        .setRequestedVideoEncoderSettings(
                            VideoEncoderSettings.Builder()
                                .setBitrate(bitrate)
                                .build()
                        )
                        .build()
                    builder.setEncoderFactory(encoderFactory)
                }
                if (request.trimOptimization && videoEffects.isEmpty() && abs(request.speed - 1.0f) < 0.01f) {
                    builder.experimentalSetTrimOptimizationEnabled(true)
                }

                val transformer = builder.addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        stopped.set(true)
                        onCompleted()
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exportException: ExportException
                    ) {
                        stopped.set(true)
                        onError(exportException)
                    }
                }).build()

                transformerRef = transformer

                val allUris = request.allInputUris
                if (allUris.size > 1) {
                    val items = allUris.map { uri ->
                        val itemBuilder = MediaItem.Builder().setUri(uri)
                        if (request.imageFrameDurationMs > 0L) {
                            itemBuilder.setImageDurationMs(request.imageFrameDurationMs)
                        }
                        val editedBuilder = EditedMediaItem.Builder(itemBuilder.build())
                        if (videoEffects.isNotEmpty()) {
                            editedBuilder.setEffects(Effects(emptyList(), videoEffects))
                        }
                        editedBuilder.build()
                    }
                    val sequence = if (request.imageFrameDurationMs > 0L) {
                        EditedMediaItemSequence.withVideoFrom(items)
                    } else {
                        EditedMediaItemSequence.withAudioAndVideoFrom(items)
                    }
                    val composition = Composition.Builder(sequence).build()
                    transformer.start(composition, outputFile.absolutePath)
                } else {
                    val mediaBuilder = MediaItem.Builder().setUri(request.inputUri)
                    if (request.clipStartMs > 0L || request.clipEndMs < Long.MAX_VALUE) {
                        val clipBuilder = MediaItem.ClippingConfiguration.Builder()
                            .setStartPositionMs(request.clipStartMs.coerceAtLeast(0L))
                        if (request.clipEndMs in 1L until Long.MAX_VALUE) {
                            clipBuilder.setEndPositionMs(request.clipEndMs)
                        }
                        mediaBuilder.setClippingConfiguration(clipBuilder.build())
                    }
                    val editedBuilder = EditedMediaItem.Builder(mediaBuilder.build())
                        .setRemoveAudio(request.removeAudio)
                        .setRemoveVideo(request.removeVideo)
                    if (videoEffects.isNotEmpty()) {
                        editedBuilder.setEffects(Effects(emptyList(), videoEffects))
                    }
                    if (abs(request.speed - 1.0f) > 0.01f) {
                        val targetSpeed = request.speed.coerceIn(0.25f, 4.0f)
                        editedBuilder.setSpeed(object : SpeedProvider {
                            override fun getSpeed(timeUs: Long): Float = targetSpeed
                            override fun getNextSpeedChangeTimeUs(timeUs: Long): Long = C.TIME_UNSET
                        })
                    }
                    transformer.start(editedBuilder.build(), outputFile.absolutePath)
                }

                val holder = ProgressHolder()
                val poller = object : Runnable {
                    override fun run() {
                        if (stopped.get()) return
                        val state = runCatching { transformer.getProgress(holder) }.getOrDefault(Transformer.PROGRESS_STATE_NOT_STARTED)
                        if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                            onProgress(holder.progress.coerceIn(0, 99))
                        }
                        if (!stopped.get() && state != Transformer.PROGRESS_STATE_NOT_STARTED) {
                            mainHandler.postDelayed(this, 250L)
                        }
                    }
                }
                mainHandler.postDelayed(poller, 250L)
            } catch (t: Throwable) {
                stopped.set(true)
                onError(t)
            }
        }

        return object : TransformerHandle {
            override fun cancel() {
                stopped.set(true)
                mainHandler.post {
                    runCatching { transformerRef?.cancel() }
                }
            }
        }
    }
}

class Media3TransformerEngine @JvmOverloads constructor(
    private val transformerBackend: TransformerBackend = RealMedia3TransformerBackend,
    private val ffmpegJob: MediaJob = MediaJob()
) {

    fun runTransformerOnly(
        context: Context,
        storage: FileStorageManager,
        request: Media3Request,
        isEnglish: Boolean = false
    ): Flow<ToolProgress> = callbackFlow {
        val outputFile = MediaUriUtils.createOutputFile(storage, request.toolTag, request.outputExt)
        val finishedSuccess = AtomicBoolean(false)
        var handle: TransformerHandle? = null

        try {
            trySend(ToolProgress.Queued)
            if (!storage.hasEnoughDiskSpace()) {
                val msg = if (isEnglish) "Not enough free disk space." else "Ruang disk tidak cukup."
                MediaUriUtils.cleanupQuietly(outputFile)
                trySend(ToolProgress.Failed(msg))
                close()
                return@callbackFlow
            }

            trySend(
                ToolProgress.Running(
                    percent = 0,
                    message = if (isEnglish) "Running Media3 Transformer…" else "Memproses via Media3 Transformer…"
                )
            )

            handle = transformerBackend.startExport(
                context = context,
                request = request,
                outputFile = outputFile,
                onProgress = { pct ->
                    trySend(
                        ToolProgress.Running(
                            percent = pct,
                            message = if (isEnglish) "Media3 Transformer ($pct%)…" else "Media3 Transformer ($pct%)…"
                        )
                    )
                },
                onCompleted = {
                    if (outputFile.exists() && outputFile.length() > 0L) {
                        finishedSuccess.set(true)
                        val probe = MediaProbe.probeFile(outputFile)
                        val sizeText = storage.formatBytes(outputFile.length())
                        trySend(
                            ToolProgress.Done(
                                ToolResult(
                                    outputs = listOf(ToolOutputFile(outputFile, request.outputMime, outputFile.name)),
                                    textOutput = probe.formatSummary(isEnglish),
                                    summary = if (isEnglish) {
                                        "Media3 completed: ${outputFile.name} ($sizeText)"
                                    } else {
                                        "Media3 selesai: ${outputFile.name} ($sizeText)"
                                    }
                                )
                            )
                        )
                    } else {
                        MediaUriUtils.cleanupQuietly(outputFile)
                        trySend(
                            ToolProgress.Failed(
                                if (isEnglish) "Media3 produced an empty output file." else "Media3 menghasilkan file kosong."
                            )
                        )
                    }
                    close()
                },
                onError = { err ->
                    MediaUriUtils.cleanupQuietly(outputFile)
                    val mapped = MediaErrorMapper.mapMedia3Failure(err, isEnglish)
                    trySend(ToolProgress.Failed("${mapped.userMessage}\n${mapped.technicalDetail}".trim()))
                    close()
                }
            )
        } catch (ce: CancellationException) {
            handle?.cancel()
            MediaUriUtils.cleanupQuietly(outputFile)
            throw ce
        }

        awaitClose {
            if (!finishedSuccess.get()) {
                handle?.cancel()
                MediaUriUtils.cleanupQuietly(outputFile)
            }
        }
    }

    fun runWithFfmpegFallback(
        context: Context,
        storage: FileStorageManager,
        request: Media3Request,
        expectedDurationMs: Long = 0L,
        isEnglish: Boolean = false,
        ffmpegFallbackCommand: (inputs: List<File>, output: File) -> List<String>
    ): Flow<ToolProgress> = flow {
        var media3FailedReason: String? = null
        var completedViaMedia3 = false

        runTransformerOnly(context, storage, request, isEnglish)
            .catch { t ->
                if (t is CancellationException) throw t
                media3FailedReason = t.localizedMessage ?: "Media3 error"
            }
            .collect { progress ->
                when (progress) {
                    is ToolProgress.Done -> {
                        completedViaMedia3 = true
                        emit(progress)
                    }
                    is ToolProgress.Failed -> {
                        media3FailedReason = progress.error
                    }
                    else -> emit(progress)
                }
            }

        if (!completedViaMedia3 && media3FailedReason != null) {
            emit(
                ToolProgress.Running(
                    percent = 1,
                    message = if (isEnglish) {
                        "Media3 unavailable, falling back to FFmpeg…"
                    } else {
                        "Media3 gagal, beralih otomatis ke FFmpeg…"
                    }
                )
            )
            ffmpegJob.runCommand(
                context = context,
                storage = storage,
                inputUris = request.allInputUris,
                toolTag = request.toolTag,
                outputExt = request.outputExt,
                outputMime = request.outputMime,
                expectedDurationMs = expectedDurationMs,
                isEnglish = isEnglish,
                commandBuilder = ffmpegFallbackCommand
            ).collect { ffProgress ->
                if (ffProgress is ToolProgress.Done) {
                    val prevText = ffProgress.result.textOutput.orEmpty()
                    val combined = buildString {
                        appendLine(
                            if (isEnglish) "[Fallback Media3 -> FFmpeg: $media3FailedReason]"
                            else "[Fallback Media3 -> FFmpeg: $media3FailedReason]"
                        )
                        if (prevText.isNotBlank()) append(prevText)
                    }.trim()
                    emit(
                        ToolProgress.Done(
                            ffProgress.result.copy(textOutput = combined)
                        )
                    )
                } else {
                    emit(ffProgress)
                }
            }
        }
    }

    fun remuxMp4WithFallback(
        context: Context,
        storage: FileStorageManager,
        inputUri: Uri,
        isEnglish: Boolean = false
    ): Flow<ToolProgress> {
        val req = Media3Request(
            inputUri = inputUri,
            toolTag = "REMUX_${UUID.randomUUID().toString().take(4)}",
            outputExt = "mp4",
            outputMime = "video/mp4"
        )
        return runWithFfmpegFallback(
            context = context,
            storage = storage,
            request = req,
            isEnglish = isEnglish
        ) { inputs, output ->
            listOf(
                "-y",
                "-i", inputs.first().absolutePath,
                "-c", "copy",
                "-movflags", "+faststart",
                output.absolutePath
            )
        }
    }
}
