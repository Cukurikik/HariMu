package com.harimu.app.core.jobs

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.harimu.app.HariMuApp
import com.harimu.app.R
import com.harimu.app.core.data.AppDatabase
import com.harimu.app.core.data.HistoryEntity
import com.harimu.app.core.files.FileStorageManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
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

        runCatching {
            setForeground(createForegroundInfo(notificationId, toolTitle, 0, ""))
        }

        val tempDir = storage.createJobTempDir(jobId)
        try {
            if (!storage.hasEnoughDiskSpace()) {
                val msg = applicationContext.getString(R.string.job_error_low_disk)
                jobDao.updateCompletion(
                    jobId = jobId,
                    status = STATUS_FAILED,
                    percent = 0,
                    message = msg,
                    outputPath = "",
                    outputMime = "",
                    summary = "",
                    errorMessage = msg
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

                jobDao.updateCompletion(
                    jobId = jobId,
                    status = STATUS_SUCCEEDED,
                    percent = 100,
                    message = summary,
                    outputPath = outFile.absolutePath,
                    outputMime = "text/plain",
                    summary = summary,
                    errorMessage = ""
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
                        success = true
                    )
                )
                return Result.success(
                    workDataOf(
                        KEY_OUTPUT_PATH to outFile.absolutePath,
                        KEY_OUTPUT_MIME to "text/plain",
                        KEY_SUMMARY to summary
                    )
                )
            } else {
                val msg = applicationContext.getString(R.string.badge_coming_soon)
                jobDao.updateCompletion(
                    jobId = jobId,
                    status = STATUS_FAILED,
                    percent = 0,
                    message = msg,
                    outputPath = "",
                    outputMime = "",
                    summary = "",
                    errorMessage = msg
                )
                return Result.failure(workDataOf(KEY_ERROR to msg))
            }
        } catch (ce: CancellationException) {
            withContext(NonCancellable) {
                val cancelText = applicationContext.getString(R.string.action_cancel)
                jobDao.updateCompletion(
                    jobId = jobId,
                    status = STATUS_CANCELLED,
                    percent = 0,
                    message = cancelText,
                    outputPath = "",
                    outputMime = "",
                    summary = "",
                    errorMessage = ""
                )
            }
            throw ce
        } catch (t: Throwable) {
            val err = t.localizedMessage ?: "Unknown error"
            withContext(NonCancellable) {
                jobDao.updateCompletion(
                    jobId = jobId,
                    status = STATUS_FAILED,
                    percent = 0,
                    message = err,
                    outputPath = "",
                    outputMime = "",
                    summary = "",
                    errorMessage = err
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
        const val KEY_PROGRESS_PERCENT = "progress_percent"
        const val KEY_PROGRESS_MESSAGE = "progress_message"
        const val KEY_OUTPUT_PATH = "output_path"
        const val KEY_OUTPUT_MIME = "output_mime"
        const val KEY_SUMMARY = "summary"
        const val KEY_ERROR = "error"

        const val STATUS_QUEUED = "QUEUED"
        const val STATUS_RUNNING = "RUNNING"
        const val STATUS_SUCCEEDED = "SUCCEEDED"
        const val STATUS_FAILED = "FAILED"
        const val STATUS_CANCELLED = "CANCELLED"
    }
}
