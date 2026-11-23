package com.harimu.app.core.jobs

import android.content.Context
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.harimu.app.R
import com.harimu.app.core.data.JobDao
import com.harimu.app.core.data.JobEntity
import com.harimu.app.core.files.FileStorageManager
import com.harimu.app.core.toolapi.Tool
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.util.UUID

class JobManager(
    private val context: Context,
    private val workManager: WorkManager,
    private val jobDao: JobDao,
    private val storage: FileStorageManager,
    val queuePolicy: JobQueuePolicy = JobQueuePolicy(maxParallel = 2)
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun observeAllJobs(): Flow<List<JobEntity>> = jobDao.observeAll()

    fun observeLatestForTool(toolId: String): Flow<JobEntity?> = jobDao.observeLatestForTool(toolId)

    suspend fun enqueueDummyJob(tool: Tool, isEnglish: Boolean): JobEntity {
        val jobId = UUID.randomUUID().toString()
        val toolTitle = tool.title(isEnglish)

        if (!storage.hasEnoughDiskSpace()) {
            val lowDiskMsg = context.getString(R.string.job_error_low_disk)
            val failed = JobEntity(
                jobId = jobId,
                workRequestId = "",
                toolId = tool.id,
                toolTitle = toolTitle,
                groupName = tool.group.name,
                isDummy = true,
                status = ToolJobWorker.STATUS_FAILED,
                progressPercent = 0,
                progressMessage = lowDiskMsg,
                errorMessage = lowDiskMsg
            )
            jobDao.upsert(failed)
            return failed
        }

        val queuedMessage = context.getString(R.string.job_queued_message)
        val request = buildWorkRequest(
            jobId = jobId,
            toolId = tool.id,
            toolTitle = toolTitle,
            groupName = tool.group.name,
            isDummy = true,
            isEnglish = isEnglish
        )

        val entity = JobEntity(
            jobId = jobId,
            workRequestId = request.id.toString(),
            toolId = tool.id,
            toolTitle = toolTitle,
            groupName = tool.group.name,
            isDummy = true,
            status = ToolJobWorker.STATUS_QUEUED,
            progressPercent = 0,
            progressMessage = queuedMessage
        )
        jobDao.upsert(entity)

        if (queuePolicy.enqueue(jobId) == EnqueueOutcome.START_NOW) {
            workManager.enqueue(request)
        }
        return entity
    }

    suspend fun cancelJob(jobId: String) {
        val existing = jobDao.getById(jobId)
        val (_, promotedId) = queuePolicy.cancel(jobId)

        if (existing != null && existing.workRequestId.isNotBlank()) {
            runCatching {
                workManager.cancelWorkById(UUID.fromString(existing.workRequestId))
            }
        }
        workManager.cancelAllWorkByTag(jobId)
        storage.cleanupJobTemp(jobId)

        val cancelText = context.getString(R.string.action_cancel)
        jobDao.updateCompletion(
            jobId = jobId,
            status = ToolJobWorker.STATUS_CANCELLED,
            percent = 0,
            message = cancelText,
            outputPath = "",
            outputMime = "",
            summary = "",
            errorMessage = ""
        )

        promotedId?.let { dispatchQueuedJob(it) }
    }

    suspend fun onWorkerFinished(jobId: String) {
        val promotedId = queuePolicy.completeAndDequeueNext(jobId)
        if (promotedId != null) {
            dispatchQueuedJob(promotedId)
        }
    }

    suspend fun recoverPendingJobs() {
        val unresolved = jobDao.getPendingOrRunning()
        if (unresolved.isEmpty()) return

        val runningIds = mutableListOf<String>()
        val queuedIds = mutableListOf<String>()

        for (job in unresolved) {
            val info = job.workRequestId.takeIf { it.isNotBlank() }?.let { rawUuid ->
                runCatching { workManager.getWorkInfoById(UUID.fromString(rawUuid)).get() }.getOrNull()
            }
            when (info?.state) {
                WorkInfo.State.SUCCEEDED -> {
                    val outPath = info.outputData.getString(ToolJobWorker.KEY_OUTPUT_PATH) ?: ""
                    val outMime = info.outputData.getString(ToolJobWorker.KEY_OUTPUT_MIME) ?: "text/plain"
                    val summary = info.outputData.getString(ToolJobWorker.KEY_SUMMARY) ?: job.toolTitle
                    jobDao.updateCompletion(
                        jobId = job.jobId,
                        status = ToolJobWorker.STATUS_SUCCEEDED,
                        percent = 100,
                        message = summary,
                        outputPath = outPath,
                        outputMime = outMime,
                        summary = summary,
                        errorMessage = ""
                    )
                }
                WorkInfo.State.FAILED -> {
                    val err = info.outputData.getString(ToolJobWorker.KEY_ERROR) ?: "Failed"
                    jobDao.updateCompletion(
                        jobId = job.jobId,
                        status = ToolJobWorker.STATUS_FAILED,
                        percent = 0,
                        message = err,
                        outputPath = "",
                        outputMime = "",
                        summary = "",
                        errorMessage = err
                    )
                }
                WorkInfo.State.CANCELLED -> {
                    jobDao.updateCompletion(
                        jobId = job.jobId,
                        status = ToolJobWorker.STATUS_CANCELLED,
                        percent = 0,
                        message = context.getString(R.string.action_cancel),
                        outputPath = "",
                        outputMime = "",
                        summary = "",
                        errorMessage = ""
                    )
                }
                WorkInfo.State.RUNNING -> runningIds.add(job.jobId)
                WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED, null -> {
                    if (job.status == ToolJobWorker.STATUS_RUNNING) {
                        runningIds.add(job.jobId)
                    } else {
                        queuedIds.add(job.jobId)
                    }
                }
            }
        }

        val toStart = queuePolicy.restore(runningIds, queuedIds)
        for (jobId in toStart) {
            val job = jobDao.getById(jobId) ?: continue
            val existingState = job.workRequestId.takeIf { it.isNotBlank() }?.let { rawUuid ->
                runCatching { workManager.getWorkInfoById(UUID.fromString(rawUuid)).get()?.state }.getOrNull()
            }
            if (existingState == null || existingState.isFinished) {
                dispatchQueuedJob(jobId)
            }
        }
    }

    fun triggerRecoveryAsync() {
        scope.launch { recoverPendingJobs() }
    }

    private suspend fun dispatchQueuedJob(jobId: String) {
        val job = jobDao.getById(jobId) ?: return
        if (job.status == ToolJobWorker.STATUS_CANCELLED) return
        val req = buildWorkRequest(
            jobId = job.jobId,
            toolId = job.toolId,
            toolTitle = job.toolTitle,
            groupName = job.groupName,
            isDummy = job.isDummy,
            isEnglish = false
        )
        jobDao.upsert(job.copy(workRequestId = req.id.toString(), updatedAt = System.currentTimeMillis()))
        workManager.enqueue(req)
    }

    private fun buildWorkRequest(
        jobId: String,
        toolId: String,
        toolTitle: String,
        groupName: String,
        isDummy: Boolean,
        isEnglish: Boolean
    ) = OneTimeWorkRequestBuilder<ToolJobWorker>()
        .addTag(ToolJobWorker.TAG_HARIMU_JOB)
        .addTag(jobId)
        .setInputData(
            workDataOf(
                ToolJobWorker.KEY_JOB_ID to jobId,
                ToolJobWorker.KEY_TOOL_ID to toolId,
                ToolJobWorker.KEY_TOOL_TITLE to toolTitle,
                ToolJobWorker.KEY_GROUP_NAME to groupName,
                ToolJobWorker.KEY_IS_DUMMY to isDummy,
                ToolJobWorker.KEY_IS_ENGLISH to isEnglish
            )
        )
        .build()
}
