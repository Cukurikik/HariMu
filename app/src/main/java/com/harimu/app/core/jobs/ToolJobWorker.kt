package com.harimu.app.core.jobs

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.harimu.app.HariMuApp
import com.harimu.app.R
import com.harimu.app.core.data.AppDatabase
import com.harimu.app.core.data.HistoryEntity
import com.harimu.app.core.files.FileStorageManager
import com.harimu.app.core.toolapi.ToolContext
import com.harimu.app.core.toolapi.ToolInputCodec
import com.harimu.app.core.toolapi.ToolOutputCodec
import com.harimu.app.core.toolapi.ToolProgress
import com.harimu.app.core.toolapi.ToolRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext

class ToolJobWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val jobId = inputData.getString(KEY_JOB_ID) ?: id.toString()
        val toolId = inputData.getString(KEY_TOOL_ID) ?: "STUB"
        val toolTitle = inputData.getString(KEY_TOOL_TITLE) ?: toolId
        val groupName = inputData.getString(KEY_GROUP_NAME) ?: "CORE"
        val isDummy = inputData.getBoolean(KEY_IS_DUMMY, true)
        val isEnglish = inputData.getBoolean(KEY_IS_ENGLISH, false)

        val app = applicationContext as? HariMuApp
        val db = app?.container?.database ?: AppDatabase.create(applicationContext)
        val storage = app?.container?.storage ?: FileStorageManager(applicationContext)
        val jobDao = db.jobDao()
        val historyDao = db.historyDao()
        val notificationId = (jobId.hashCode() and 0x7FFFFFFF).coerceAtLeast(1001)
        val preparingMsg = applicationContext.getString(R.string.progress_preparing)

        runCatching {
            setForeground(createForegroundInfo(notificationId, toolTitle, 0, preparingMsg))
        }
        jobDao.updateProgress(jobId, STATUS_PREPARING, 0, preparingMsg)

        val tempDir = storage.createJobTempDir(jobId)
        try {
            if (!storage.hasEnoughDiskSpace()) {
                val msg = applicationContext.getString(R.string.job_error_low_disk)
                val existing = jobDao.getById(jobId)
                val retryable = JobInputValidator.canRetryAfterFailure(applicationContext, existing)
                jobDao.updateCompletion(
                    jobId = jobId,
                    status = STATUS_FAILED,
                    percent = 0,
                    message = msg,
                    outputPath = "",
                    outputMime = "",
                    summary = "",
                    errorMessage = msg,
                    isRetryable = retryable
                )
                return Result.failure(workDataOf(KEY_ERROR to msg))
            }

            if (isDummy) {
                val marker = java.io.File(tempDir, "dummy_scratch.tmp")
                marker.writeText("job=$jobId\ntool=$toolId\n")

                val steps = listOf(20, 50, 80, 100)
                for (pct in steps) {
                    currentCoroutineContext().ensureActive()
                    if (isStopped) throw CancellationException("Worker stopped")
                    val stepMsg = applicationContext.getString(R.string.job_dummy_step, pct)
                    jobDao.updateProgress(jobId, STATUS_RUNNING, pct, stepMsg)
                    setProgress(workDataOf(KEY_PROGRESS_PERCENT to pct, KEY_PROGRESS_MESSAGE to stepMsg))
                    runCatching {
                        setForeground(createForegroundInfo(notificationId, toolTitle, pct, stepMsg))
                    }
                    delay(400L)
                }

                val outFile = storage.createOutputFile(toolId, "txt")
                val reportText = buildString {
                    appendLine("HariMu Dummy Job Report")
                    appendLine("Tool: $toolId — $toolTitle")
                    appendLine("Group: $groupName")
                    appendLine("Status: COMPLETED")
                    appendLine("Language: ${if (isEnglish) "EN" else "ID"}")
                }
                outFile.writeText(reportText)
                val summary = applicationContext.getString(R.string.job_dummy_done, outFile.name)
                val outputsJson = ToolOutputCodec.encode(
                    listOf(ToolOutputCodec.Entry(outFile.absolutePath, "text/plain", outFile.name))
                )

                jobDao.updateCompletion(
                    jobId = jobId,
                    status = STATUS_SUCCEEDED,
                    percent = 100,
                    message = summary,
                    outputPath = outFile.absolutePath,
                    outputMime = "text/plain",
                    summary = summary,
                    errorMessage = "",
                    outputsJson = outputsJson,
                    textOutput = reportText,
                    isRetryable = false
                )
                historyDao.insert(
                    HistoryEntity(
                        toolId = toolId,
                        toolTitle = toolTitle,
                        groupName = groupName,
                        outputPath = outFile.absolutePath,
                        outputName = outFile.name,
                        mimeType = "text/plain",
                        fileSizeBytes = outFile.length(),
                        summary = summary,
                        success = true,
                        jobId = jobId,
                        outputsJson = outputsJson,
                        textOutput = reportText
                    )
                )
                return Result.success(
                    workDataOf(
                        KEY_OUTPUT_PATH to outFile.absolutePath,
                        KEY_OUTPUT_MIME to "text/plain",
                        KEY_SUMMARY to summary.take(512),
                        KEY_OUTPUTS_JSON to if (outputsJson.length <= 2048) outputsJson else "",
                        KEY_TEXT_OUTPUT to reportText.take(1500)
                    )
                )
            } else {
                return runRealTool(
                    jobId = jobId,
                    toolId = toolId,
                    toolTitle = toolTitle,
                    groupName = groupName,
                    isEnglish = isEnglish,
                    notificationId = notificationId,
                    db = db,
                    storage = storage
                )
            }
        } catch (ce: CancellationException) {
            withContext(NonCancellable) {
                val existing = jobDao.getById(jobId)
                if (existing?.status != STATUS_CANCELLED) {
                    val isSystemInterruption = isSystemStopReason()
                    val retryable = JobInputValidator.canRetryAfterFailure(applicationContext, existing)
                    if (isSystemInterruption) {
                        val interruptedMsg = applicationContext.getString(R.string.job_interrupted_system)
                        jobDao.updateCompletion(
                            jobId = jobId,
                            status = STATUS_INTERRUPTED,
                            percent = existing?.progressPercent ?: 0,
                            message = interruptedMsg,
                            outputPath = "",
                            outputMime = "",
                            summary = "",
                            errorMessage = interruptedMsg,
                            isRetryable = retryable
                        )
                    } else {
                        val cancelText = applicationContext.getString(R.string.action_cancel)
                        jobDao.updateCompletion(
                            jobId = jobId,
                            status = STATUS_CANCELLED,
                            percent = existing?.progressPercent ?: 0,
                            message = cancelText,
                            outputPath = "",
                            outputMime = "",
                            summary = "",
                            errorMessage = "",
                            isRetryable = retryable
                        )
                    }
                }
            }
            throw ce
        } catch (t: Throwable) {
            val err = t.localizedMessage ?: "Unknown error"
            withContext(NonCancellable) {
                val existing = jobDao.getById(jobId)
                val retryable = JobInputValidator.canRetryAfterFailure(applicationContext, existing)
                jobDao.updateCompletion(
                    jobId = jobId,
                    status = STATUS_FAILED,
                    percent = 0,
                    message = err,
                    outputPath = "",
                    outputMime = "",
                    summary = "",
                    errorMessage = err,
                    isRetryable = retryable
                )
            }
            return Result.failure(workDataOf(KEY_ERROR to err))
        } finally {
            withContext(NonCancellable) {
                storage.cleanupJobTemp(jobId)
                app?.container?.jobManager?.onWorkerFinished(jobId)
            }
        }
    }

    private fun isSystemStopReason(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
        val reason = stopReason
        return reason != WorkInfo.STOP_REASON_NOT_STOPPED &&
            reason != WorkInfo.STOP_REASON_CANCELLED_BY_APP
    }

    /**
     * Menjalankan tool nyata di dalam WorkManager (FASE 15/H5): proses bertahan saat layar mati
     * berkat foreground service `dataSync`, progres ditulis ke Room, dan hasil dikembalikan lewat
     * `outputsJson` agar UI bisa menampilkan semua berkas.
     */
    private suspend fun runRealTool(
        jobId: String,
        toolId: String,
        toolTitle: String,
        groupName: String,
        isEnglish: Boolean,
        notificationId: Int,
        db: AppDatabase,
        storage: FileStorageManager
    ): Result {
        val jobDao = db.jobDao()
        val tool = ToolRegistry.findById(toolId)
        val inputJson = inputData.getString(KEY_INPUT_JSON).orEmpty().ifBlank {
            runCatching {
                java.io.File(storage.createJobTempDir(jobId), "input.json")
                    .takeIf { it.exists() }?.readText(Charsets.UTF_8)
            }.getOrNull().orEmpty()
        }.ifBlank {
            jobDao.getById(jobId)?.inputJson.orEmpty()
        }
        if (tool == null) {
            val msg = "Tool $toolId tidak terdaftar"
            jobDao.updateCompletion(jobId, STATUS_FAILED, 0, msg, "", "", "", msg, isRetryable = false)
            return Result.failure(workDataOf(KEY_ERROR to msg))
        }
        val container = (applicationContext as? HariMuApp)?.container
        val ctx = ToolContext(
            appContext = applicationContext,
            storage = storage,
            isEnglish = isEnglish,
            ai = container?.aiClient,
            models = container?.modelManager
        )
        val input = ToolInputCodec.decode(inputJson)
        val retryableOnFail = JobInputValidator.canRetryStoredInput(applicationContext, tool, input)
        var lastMessage = applicationContext.getString(R.string.progress_preparing)

        return try {
            val terminal = tool.run(ctx, input)
                .onEach { progress ->
                    currentCoroutineContext().ensureActive()
                    if (isStopped) throw CancellationException("Worker stopped")
                    when (progress) {
                        is ToolProgress.Queued -> {
                            lastMessage = applicationContext.getString(R.string.progress_queued)
                        }
                        is ToolProgress.Running -> {
                            lastMessage = progress.message
                            val dbStatus = if (isPreparingSignal(progress.percent, progress.message)) {
                                STATUS_PREPARING
                            } else {
                                STATUS_RUNNING
                            }
                            val pct = progress.percent.coerceIn(0, 100)
                            jobDao.updateProgress(jobId, dbStatus, pct, progress.message)
                            setProgress(
                                workDataOf(
                                    KEY_PROGRESS_PERCENT to progress.percent,
                                    KEY_PROGRESS_MESSAGE to progress.message
                                )
                            )
                            runCatching {
                                setForeground(
                                    createForegroundInfo(notificationId, toolTitle, progress.percent, progress.message)
                                )
                            }
                        }
                        else -> Unit
                    }
                }
                .first { it is ToolProgress.Done || it is ToolProgress.Failed }

            when (terminal) {
                is ToolProgress.Done -> {
                    val outputs = terminal.result.outputs
                    val first = outputs.firstOrNull()
                    val summary = terminal.result.summary
                    val entries = outputs.map {
                        ToolOutputCodec.Entry(it.file.absolutePath, it.mimeType, it.displayName)
                    }
                    val outputsJson = ToolOutputCodec.encode(entries)
                    val textOutput = terminal.result.textOutput.orEmpty()

                    jobDao.updateCompletion(
                        jobId = jobId,
                        status = STATUS_SUCCEEDED,
                        percent = 100,
                        message = summary,
                        outputPath = first?.file?.absolutePath ?: "",
                        outputMime = first?.mimeType ?: "text/plain",
                        summary = summary,
                        errorMessage = "",
                        outputsJson = outputsJson,
                        textOutput = textOutput,
                        isRetryable = false
                    )
                    if (outputs.isNotEmpty() || textOutput.isNotBlank()) {
                        db.historyDao().insert(
                            HistoryEntity(
                                toolId = toolId,
                                toolTitle = toolTitle,
                                groupName = groupName,
                                outputPath = first?.file?.absolutePath ?: "",
                                outputName = first?.displayName ?: toolTitle,
                                mimeType = first?.mimeType ?: "text/plain",
                                fileSizeBytes = first?.file?.length() ?: 0L,
                                summary = summary,
                                success = true,
                                jobId = jobId,
                                outputsJson = outputsJson,
                                textOutput = textOutput
                            )
                        )
                    }
                    runCatching { setForeground(createForegroundInfo(notificationId, toolTitle, 100, summary)) }
                    Result.success(
                        workDataOf(
                            KEY_OUTPUT_PATH to (first?.file?.absolutePath ?: ""),
                            KEY_OUTPUT_MIME to (first?.mimeType ?: "text/plain"),
                            KEY_SUMMARY to summary.take(512),
                            KEY_OUTPUTS_JSON to if (outputsJson.length <= 2048) outputsJson else "",
                            KEY_TEXT_OUTPUT to textOutput.take(1500)
                        )
                    )
                }
                is ToolProgress.Failed -> {
                    jobDao.updateCompletion(
                        jobId = jobId,
                        status = STATUS_FAILED,
                        percent = 0,
                        message = terminal.error,
                        outputPath = "",
                        outputMime = "",
                        summary = "",
                        errorMessage = terminal.error,
                        isRetryable = retryableOnFail
                    )
                    Result.failure(workDataOf(KEY_ERROR to terminal.error))
                }
                else -> {
                    jobDao.updateCompletion(
                        jobId = jobId,
                        status = STATUS_FAILED,
                        percent = 0,
                        message = lastMessage,
                        outputPath = "",
                        outputMime = "",
                        summary = "",
                        errorMessage = lastMessage,
                        isRetryable = retryableOnFail
                    )
                    Result.failure(workDataOf(KEY_ERROR to lastMessage))
                }
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            val err = t.localizedMessage ?: "Tool gagal dijalankan"
            withContext(NonCancellable) {
                jobDao.updateCompletion(
                    jobId = jobId,
                    status = STATUS_FAILED,
                    percent = 0,
                    message = err,
                    outputPath = "",
                    outputMime = "",
                    summary = "",
                    errorMessage = err,
                    isRetryable = retryableOnFail
                )
            }
            Result.failure(workDataOf(KEY_ERROR to err))
        }
    }

    private fun createForegroundInfo(
        notificationId: Int,
        toolTitle: String,
        percent: Int,
        message: String
    ): ForegroundInfo {
        ensureNotificationChannel()
        val cancelIntent = WorkManager.getInstance(applicationContext).createCancelPendingIntent(id)
        val title = applicationContext.getString(R.string.job_notif_title, toolTitle)
        val cancelLabel = applicationContext.getString(R.string.action_cancel)

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(message.ifBlank { applicationContext.getString(R.string.progress_queued) })
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, percent.coerceIn(0, 100), percent == 0)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, cancelLabel, cancelIntent)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(notificationId, notification)
        }
    }

    private fun ensureNotificationChannel() {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                applicationContext.getString(R.string.job_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = applicationContext.getString(R.string.job_channel_desc)
            }
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID = "harimu_jobs"
        const val TAG_HARIMU_JOB = "harimu_tool_job"

        const val KEY_JOB_ID = "job_id"
        const val KEY_TOOL_ID = "tool_id"
        const val KEY_TOOL_TITLE = "tool_title"
        const val KEY_GROUP_NAME = "group_name"
        const val KEY_IS_DUMMY = "is_dummy"
        const val KEY_IS_ENGLISH = "is_english"
        const val KEY_INPUT_JSON = "input_json"
        const val KEY_PROGRESS_PERCENT = "progress_percent"
        const val KEY_PROGRESS_MESSAGE = "progress_message"
        const val KEY_OUTPUT_PATH = "output_path"
        const val KEY_OUTPUT_MIME = "output_mime"
        const val KEY_SUMMARY = "summary"
        const val KEY_OUTPUTS_JSON = "outputs_json"
        const val KEY_TEXT_OUTPUT = "text_output"
        const val KEY_ERROR = "error"

        const val STATUS_QUEUED = "QUEUED"
        const val STATUS_PREPARING = "PREPARING"
        const val STATUS_RUNNING = "RUNNING"
        const val STATUS_SUCCEEDED = "SUCCEEDED"
        const val STATUS_FAILED = "FAILED"
        const val STATUS_CANCELLED = "CANCELLED"
        const val STATUS_INTERRUPTED = "INTERRUPTED"

        const val MODE_WORKER = "WORKER"
        const val MODE_IN_PROCESS = "IN_PROCESS"

        fun isActiveStatus(status: String): Boolean =
            status == STATUS_QUEUED || status == STATUS_PREPARING || status == STATUS_RUNNING

        fun isAttentionStatus(status: String): Boolean =
            status == STATUS_FAILED || status == STATUS_CANCELLED || status == STATUS_INTERRUPTED

        fun isPreparingSignal(percent: Int, message: String): Boolean =
            percent == 0 && (
                message.contains("Menyiapkan", ignoreCase = true) ||
                    message.contains("Preparing", ignoreCase = true)
                )
    }
}
