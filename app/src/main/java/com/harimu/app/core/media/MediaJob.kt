package com.harimu.app.core.media

import android.content.Context
import android.net.Uri
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.harimu.app.core.files.FileStorageManager
import com.harimu.app.core.toolapi.ToolOutputFile
import com.harimu.app.core.toolapi.ToolProgress
import com.harimu.app.core.toolapi.ToolResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

interface FfmpegSessionHandle {
    val sessionId: Long
    fun cancel()
}

interface FfmpegSessionRunner {
    fun executeAsync(
        arguments: List<String>,
        onLog: (String) -> Unit,
        onTimeMs: (Double) -> Unit,
        onComplete: (returnCode: Int?, isSuccess: Boolean, isCancelled: Boolean, allLogs: String) -> Unit
    ): FfmpegSessionHandle
}

object RealFfmpegSessionRunner : FfmpegSessionRunner {
    override fun executeAsync(
        arguments: List<String>,
        onLog: (String) -> Unit,
        onTimeMs: (Double) -> Unit,
        onComplete: (returnCode: Int?, isSuccess: Boolean, isCancelled: Boolean, allLogs: String) -> Unit
    ): FfmpegSessionHandle {
        val session = FFmpegKit.executeWithArgumentsAsync(
            arguments.toTypedArray(),
            { completed ->
                val rc = completed.returnCode
                onComplete(
                    rc?.value,
                    ReturnCode.isSuccess(rc),
                    ReturnCode.isCancel(rc),
                    completed.allLogsAsString ?: ""
                )
            },
            { log ->
                log?.message?.let(onLog)
            },
            { stats ->
                if (stats != null && stats.time > 0.0) {
                    onTimeMs(stats.time)
                }
            }
        )
        return object : FfmpegSessionHandle {
            override val sessionId: Long = session.sessionId
            override fun cancel() {
                runCatching { FFmpegKit.cancel(session.sessionId) }
            }
        }
    }
}

class MediaJob(
    private val runner: FfmpegSessionRunner = RealFfmpegSessionRunner
) {

    fun runCommand(
        context: Context,
        storage: FileStorageManager,
        inputUris: List<Uri>,
        toolTag: String,
        outputExt: String,
        outputMime: String,
        expectedDurationMs: Long = 0L,
        isEnglish: Boolean = false,
        commandBuilder: (inputFiles: List<File>, outputFile: File) -> List<String>
    ): Flow<ToolProgress> = callbackFlow {
        val jobId = "ffmpeg_${UUID.randomUUID().toString().take(8)}"
        val jobTempDir = storage.createJobTempDir(jobId)
        val outputFile = MediaUriUtils.createOutputFile(storage, toolTag, outputExt)
        val logs = BoundedLogCollector()
        val cancelledFlag = AtomicBoolean(false)
        val succeededFlag = AtomicBoolean(false)
        val discoveredDurationMs = AtomicLong(expectedDurationMs.coerceAtLeast(0L))
        var handle: FfmpegSessionHandle? = null

        try {
            trySend(ToolProgress.Queued)

            if (!storage.hasEnoughDiskSpace()) {
                val msg = if (isEnglish) "Not enough free disk space." else "Ruang disk tidak cukup untuk memproses media."
                MediaUriUtils.cleanupQuietly(outputFile, jobTempDir)
                trySend(ToolProgress.Failed(msg))
                close()
                return@callbackFlow
            }

            trySend(
                ToolProgress.Running(
                    percent = 0,
                    message = if (isEnglish) "Preparing media inputs…" else "Menyiapkan file input…"
                )
            )

            val localInputs = inputUris.mapIndexed { index, uri ->
                MediaUriUtils.copyUriToSafeTemp(
                    context = context,
                    storage = storage,
                    uri = uri,
                    jobDir = jobTempDir,
                    index = index,
                    fallbackExt = outputExt
                )
            }

            if (discoveredDurationMs.get() <= 0L && localInputs.isNotEmpty()) {
                val probe = MediaProbe.probeFile(localInputs.first())
                if (probe.durationMs > 0L) {
                    discoveredDurationMs.set(probe.durationMs)
                }
            }

            val args = commandBuilder(localInputs, outputFile)
            logs.append("ffmpeg ${args.joinToString(" ")}")

            trySend(
                ToolProgress.Running(
                    percent = 1,
                    message = if (isEnglish) "Running FFmpeg…" else "Memproses dengan FFmpeg…"
                )
            )

            handle = runner.executeAsync(
                arguments = args,
                onLog = { line ->
                    logs.append(line)
                    if (discoveredDurationMs.get() <= 0L) {
                        FfmpegProgressParser.parseDurationMsFromLog(line)?.let { d ->
                            discoveredDurationMs.compareAndSet(0L, d)
                        }
                    }
                    FfmpegProgressParser.parseTimeMsFromLog(line)?.let { tMs ->
                        val pct = FfmpegProgressParser.computePercent(tMs.toDouble(), discoveredDurationMs.get())
                        trySend(
                            ToolProgress.Running(
                                percent = pct,
                                message = if (isEnglish) "Processing FFmpeg ($pct%)…" else "Memproses FFmpeg ($pct%)…"
                            )
                        )
                    }
                },
                onTimeMs = { processedMs ->
                    val pct = FfmpegProgressParser.computePercent(processedMs, discoveredDurationMs.get())
                    trySend(
                        ToolProgress.Running(
                            percent = pct,
                            message = if (isEnglish) "Processing FFmpeg ($pct%)…" else "Memproses FFmpeg ($pct%)…"
                        )
                    )
                },
                onComplete = { returnCode, isSuccess, isCancelled, allLogs ->
                    if (allLogs.isNotBlank()) logs.append(allLogs)
                    if (isCancelled || cancelledFlag.get()) {
                        MediaUriUtils.cleanupQuietly(outputFile, jobTempDir)
                        close(CancellationException("FFmpeg job cancelled"))
                    } else if (isSuccess && outputFile.exists() && outputFile.length() > 0L) {
                        succeededFlag.set(true)
                        val outProbe = MediaProbe.probeFile(outputFile)
                        val sizeText = storage.formatBytes(outputFile.length())
                        val summary = if (isEnglish) {
                            "Completed: ${outputFile.name} ($sizeText)"
                        } else {
                            "Selesai: ${outputFile.name} ($sizeText)"
                        }
                        val detail = buildString {
                            appendLine(outProbe.formatSummary(isEnglish))
                            val tail = logs.tail(8)
                            if (tail.isNotBlank()) {
                                appendLine()
                                append(tail)
                            }
                        }.trim()
                        MediaUriUtils.cleanupQuietly(jobTempDir)
                        trySend(
                            ToolProgress.Done(
                                ToolResult(
                                    outputs = listOf(ToolOutputFile(outputFile, outputMime, outputFile.name)),
                                    textOutput = detail,
                                    summary = summary
                                )
                            )
                        )
                        close()
                    } else {
                        val mapped = MediaErrorMapper.mapFfmpegFailure(
                            returnCodeValue = returnCode,
                            isCancelled = false,
                            logs = logs.allLines().joinToString("\n"),
                            isEnglish = isEnglish
                        )
                        MediaUriUtils.cleanupQuietly(outputFile, jobTempDir)
                        trySend(ToolProgress.Failed("${mapped.userMessage}\n${mapped.technicalDetail}".trim()))
                        close()
                    }
                }
            )
        } catch (ce: CancellationException) {
            cancelledFlag.set(true)
            handle?.cancel()
            MediaUriUtils.cleanupQuietly(outputFile, jobTempDir)
            throw ce
        } catch (t: Throwable) {
            MediaUriUtils.cleanupQuietly(outputFile, jobTempDir)
            trySend(ToolProgress.Failed(t.localizedMessage ?: "MediaJob error"))
            close()
        }

        awaitClose {
            if (!succeededFlag.get()) {
                cancelledFlag.set(true)
                handle?.cancel()
                MediaUriUtils.cleanupQuietly(outputFile)
            }
            MediaUriUtils.cleanupQuietly(jobTempDir)
        }
    }.flowOn(Dispatchers.IO)

    suspend fun runStep(
        arguments: List<String>,
        expectedDurationMs: Long = 0L,
        isEnglish: Boolean = false,
        onProgress: (percent: Int) -> Unit = {}
    ): Result<String> = kotlinx.coroutines.suspendCancellableCoroutine { cont ->
        val logs = BoundedLogCollector()
        val discoveredDuration = AtomicLong(expectedDurationMs.coerceAtLeast(0L))
        logs.append("ffmpeg ${arguments.joinToString(" ")}")

        val handle = runner.executeAsync(
            arguments = arguments,
            onLog = { line ->
                logs.append(line)
                if (discoveredDuration.get() <= 0L) {
                    FfmpegProgressParser.parseDurationMsFromLog(line)?.let { d ->
                        discoveredDuration.compareAndSet(0L, d)
                    }
                }
                FfmpegProgressParser.parseTimeMsFromLog(line)?.let { tMs ->
                    onProgress(FfmpegProgressParser.computePercent(tMs.toDouble(), discoveredDuration.get()))
                }
            },
            onTimeMs = { processedMs ->
                onProgress(FfmpegProgressParser.computePercent(processedMs, discoveredDuration.get()))
            },
            onComplete = { returnCode, isSuccess, isCancelled, allLogs ->
                if (allLogs.isNotBlank()) logs.append(allLogs)
                if (!cont.isActive) return@executeAsync
                if (isCancelled) {
                    cont.cancel(CancellationException("FFmpeg step cancelled"))
                } else if (isSuccess) {
                    cont.resumeWith(Result.success(Result.success(logs.tail(40))))
                } else {
                    val mapped = MediaErrorMapper.mapFfmpegFailure(
                        returnCodeValue = returnCode,
                        isCancelled = false,
                        logs = logs.allLines().joinToString("\n"),
                        isEnglish = isEnglish
                    )
                    cont.resumeWith(
                        Result.success(
                            Result.failure(IllegalStateException("${mapped.userMessage}\n${mapped.technicalDetail}".trim()))
                        )
                    )
                }
            }
        )

        cont.invokeOnCancellation {
            handle.cancel()
        }
    }

    fun remuxMp4(
        context: Context,
        storage: FileStorageManager,
        inputUri: Uri,
        isEnglish: Boolean = false
    ): Flow<ToolProgress> = runCommand(
        context = context,
        storage = storage,
        inputUris = listOf(inputUri),
        toolTag = "REMUX",
        outputExt = "mp4",
        outputMime = "video/mp4",
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
