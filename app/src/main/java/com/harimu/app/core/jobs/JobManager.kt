package com.harimu.app.core.jobs

import android.content.Context
import android.os.Build
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.harimu.app.R
import com.harimu.app.core.data.JobDao
import com.harimu.app.core.data.JobEntity
import com.harimu.app.core.files.FileStorageManager
import com.harimu.app.core.toolapi.Tool
import com.harimu.app.core.toolapi.ToolInput
import com.harimu.app.core.toolapi.ToolInputCodec
import com.harimu.app.core.toolapi.ToolOutputCodec
import com.harimu.app.core.toolapi.ToolRegistry
import com.harimu.app.core.toolapi.ToolResult
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

    fun observeJob(jobId: String): Flow<JobEntity?> = jobDao.observeById(jobId)

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
                errorMessage = lowDiskMsg,
                isEnglish = isEnglish,
                executionMode = ToolJobWorker.MODE_WORKER,
                isRetryable = true,
                completedAt = System.currentTimeMillis()
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
            progressMessage = queuedMessage,
            isEnglish = isEnglish,
            executionMode = ToolJobWorker.MODE_WORKER,
            inputSummary = JobInputValidator.buildInputSummary(tool, ToolInput(), isEnglish),
            isRetryable = true
        )
        jobDao.upsert(entity)

        if (queuePolicy.enqueue(jobId) == EnqueueOutcome.START_NOW) {
            workManager.enqueue(request)
        }
        return entity
    }

    /** Menjalankan tool berat di WorkManager (foreground `dataSync`) alih-alih viewModelScope. */
    suspend fun enqueueToolJob(tool: Tool, input: ToolInput, isEnglish: Boolean): JobEntity {
        val jobId = UUID.randomUUID().toString()
        val toolTitle = tool.title(isEnglish)
        val inputJson = ToolInputCodec.encode(input)
        val inputSummary = JobInputValidator.buildInputSummary(tool, input, isEnglish)
        val retryable = JobInputValidator.canRetryStoredInput(context, tool, input)

        if (!storage.hasEnoughDiskSpace()) {
            val lowDiskMsg = context.getString(R.string.job_error_low_disk)
            val failed = JobEntity(
                jobId = jobId,
                workRequestId = "",
                toolId = tool.id,
                toolTitle = toolTitle,
                groupName = tool.group.name,
                isDummy = false,
                status = ToolJobWorker.STATUS_FAILED,
                progressPercent = 0,
                progressMessage = lowDiskMsg,
                errorMessage = lowDiskMsg,
                isEnglish = isEnglish,
                executionMode = ToolJobWorker.MODE_WORKER,
                inputJson = inputJson,
                inputSummary = inputSummary,
                isRetryable = retryable,
                completedAt = System.currentTimeMillis()
            )
            jobDao.upsert(failed)
            return failed
        }
        val queuedMessage = context.getString(R.string.job_queued_message)
        // Disimpan juga di temp job agar job yang di-requeue (queuePolicy/recover) tetap punya input.
        runCatching {
            storage.createJobTempDir(jobId).resolve("input.json").writeText(inputJson, Charsets.UTF_8)
        }
        val request = buildWorkRequest(
            jobId = jobId,
            toolId = tool.id,
            toolTitle = toolTitle,
            groupName = tool.group.name,
            isDummy = false,
            isEnglish = isEnglish,
            inputJson = inputJson
        )
        val entity = JobEntity(
            jobId = jobId,
            workRequestId = request.id.toString(),
            toolId = tool.id,
            toolTitle = toolTitle,
            groupName = tool.group.name,
            isDummy = false,
            status = ToolJobWorker.STATUS_QUEUED,
            progressPercent = 0,
            progressMessage = queuedMessage,
            isEnglish = isEnglish,
            executionMode = ToolJobWorker.MODE_WORKER,
            inputJson = inputJson,
            inputSummary = inputSummary,
            isRetryable = retryable
        )
        jobDao.upsert(entity)
        if (queuePolicy.enqueue(jobId) == EnqueueOutcome.START_NOW) {
            workManager.enqueue(request)
        }
        return entity
    }

    /** Creates a tracked [JobEntity] for fast in-process tools executed in ViewModel scope. */
    suspend fun startInProcessJob(tool: Tool, input: ToolInput, isEnglish: Boolean): JobEntity {
        val jobId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val inputJson = ToolInputCodec.encode(input)
        val entity = JobEntity(
            jobId = jobId,
            workRequestId = "",
            toolId = tool.id,
            toolTitle = tool.title(isEnglish),
            groupName = tool.group.name,
            isDummy = false,
            status = ToolJobWorker.STATUS_PREPARING,
            progressPercent = 0,
            progressMessage = context.getString(R.string.progress_preparing),
            isEnglish = isEnglish,
            executionMode = ToolJobWorker.MODE_IN_PROCESS,
            inputJson = inputJson,
            inputSummary = JobInputValidator.buildInputSummary(tool, input, isEnglish),
            isRetryable = JobInputValidator.canRetryStoredInput(context, tool, input),
            startedAt = now,
            updatedAt = now
        )
        jobDao.upsert(entity)
        return entity
    }

    suspend fun updateInProcessJob(jobId: String, status: String, percent: Int, message: String) {
        jobDao.updateProgress(
            jobId = jobId,
            status = status,
            percent = percent.coerceIn(0, 100),
            message = message
        )
    }

    suspend fun completeInProcessJob(jobId: String, result: ToolResult) {
        val first = result.outputs.firstOrNull()
        val entries = result.outputs.map {
            ToolOutputCodec.Entry(it.file.absolutePath, it.mimeType, it.displayName)
        }
        jobDao.updateCompletion(
            jobId = jobId,
            status = ToolJobWorker.STATUS_SUCCEEDED,
            percent = 100,
            message = result.summary,
            outputPath = first?.file?.absolutePath ?: "",
            outputMime = first?.mimeType ?: "text/plain",
            summary = result.summary,
            errorMessage = "",
            outputsJson = ToolOutputCodec.encode(entries),
            textOutput = result.textOutput.orEmpty(),
            isRetryable = false
        )
    }

    suspend fun failInProcessJob(jobId: String, error: String) {
        val existing = jobDao.getById(jobId)
        val retryable = JobInputValidator.canRetryAfterFailure(context, existing)
        jobDao.updateCompletion(
            jobId = jobId,
            status = ToolJobWorker.STATUS_FAILED,
            percent = 0,
            message = error,
            outputPath = "",
            outputMime = "",
            summary = "",
            errorMessage = error,
            isRetryable = retryable
        )
    }

    suspend fun cancelJob(jobId: String) {
        val existing = jobDao.getById(jobId)
        val retryable = JobInputValidator.canRetryAfterFailure(context, existing)
        val cancelText = context.getString(R.string.action_cancel)

        if (existing?.executionMode == ToolJobWorker.MODE_IN_PROCESS) {
            jobDao.updateCompletion(
                jobId = jobId,
                status = ToolJobWorker.STATUS_CANCELLED,
                percent = existing.progressPercent,
                message = cancelText,
                outputPath = "",
                outputMime = "",
                summary = "",
                errorMessage = "",
                isRetryable = retryable
            )
            return
        }

        val (_, promotedId) = queuePolicy.cancel(jobId)

        if (existing != null && existing.workRequestId.isNotBlank()) {
            runCatching {
                workManager.cancelWorkById(UUID.fromString(existing.workRequestId))
            }
        }
        workManager.cancelAllWorkByTag(jobId)
        storage.cleanupJobTemp(jobId)

        jobDao.updateCompletion(
            jobId = jobId,
            status = ToolJobWorker.STATUS_CANCELLED,
            percent = existing?.progressPercent ?: 0,
            message = cancelText,
            outputPath = "",
            outputMime = "",
            summary = "",
            errorMessage = "",
            isRetryable = retryable
        )

        promotedId?.let { dispatchQueuedJob(it) }
    }

    fun canRetry(job: JobEntity): Boolean = JobInputValidator.canRetry(context, job)

    suspend fun retryJob(jobId: String, isEnglish: Boolean = false): JobEntity? {
        val existing = jobDao.getById(jobId) ?: return null
        if (!JobInputValidator.canRetry(context, existing)) {
            if (existing.isRetryable) {
                jobDao.upsert(existing.copy(isRetryable = false, updatedAt = System.currentTimeMillis()))
            }
            return null
        }
        val tool = ToolRegistry.findById(existing.toolId) ?: return null
        val targetEnglish = isEnglish || existing.isEnglish
        return if (existing.isDummy) {
            enqueueDummyJob(tool, targetEnglish)
        } else {
            val input = ToolInputCodec.decode(existing.inputJson)
            enqueueToolJob(tool, input, targetEnglish)
        }
    }

    suspend fun deleteJob(jobId: String) {
        val existing = jobDao.getById(jobId) ?: return
        if (ToolJobWorker.isActiveStatus(existing.status)) {
            cancelJob(jobId)
        }
        jobDao.deleteById(jobId)
    }

    suspend fun clearFinishedJobs() {
        jobDao.clearFinished()
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
            if (job.executionMode == ToolJobWorker.MODE_IN_PROCESS) {
                val msg = context.getString(R.string.job_interrupted_in_process)
                val retryable = JobInputValidator.canRetryAfterFailure(context, job)
                jobDao.updateCompletion(
                    jobId = job.jobId,
                    status = ToolJobWorker.STATUS_INTERRUPTED,
                    percent = job.progressPercent,
                    message = msg,
                    outputPath = "",
                    outputMime = "",
                    summary = "",
                    errorMessage = msg,
                    isRetryable = retryable
                )
                continue
            }

            val info = job.workRequestId.takeIf { it.isNotBlank() }?.let { rawUuid ->
                runCatching { workManager.getWorkInfoById(UUID.fromString(rawUuid)).get() }.getOrNull()
            }
            when (info?.state) {
                WorkInfo.State.SUCCEEDED -> {
                    val outPath = info.outputData.getString(ToolJobWorker.KEY_OUTPUT_PATH) ?: ""
                    val outMime = info.outputData.getString(ToolJobWorker.KEY_OUTPUT_MIME) ?: "text/plain"
                    val summary = info.outputData.getString(ToolJobWorker.KEY_SUMMARY) ?: job.toolTitle
                    val outputsJson = info.outputData.getString(ToolJobWorker.KEY_OUTPUTS_JSON) ?: ""
                    val textOutput = info.outputData.getString(ToolJobWorker.KEY_TEXT_OUTPUT) ?: ""
                    jobDao.updateCompletion(
                        jobId = job.jobId,
                        status = ToolJobWorker.STATUS_SUCCEEDED,
                        percent = 100,
                        message = summary,
                        outputPath = outPath,
                        outputMime = outMime,
                        summary = summary,
                        errorMessage = "",
                        outputsJson = outputsJson,
                        textOutput = textOutput,
                        isRetryable = false
                    )
                }
                WorkInfo.State.FAILED -> {
                    val err = info.outputData.getString(ToolJobWorker.KEY_ERROR) ?: "Failed"
                    val retryable = JobInputValidator.canRetryAfterFailure(context, job)
                    jobDao.updateCompletion(
                        jobId = job.jobId,
                        status = ToolJobWorker.STATUS_FAILED,
                        percent = 0,
                        message = err,
                        outputPath = "",
                        outputMime = "",
                        summary = "",
                        errorMessage = err,
                        isRetryable = retryable
                    )
                }
                WorkInfo.State.CANCELLED -> {
                    val isSystemInterruption = isWorkInfoSystemInterrupted(info)
                    val retryable = JobInputValidator.canRetryAfterFailure(context, job)
                    if (isSystemInterruption) {
                        val interruptedMsg = context.getString(R.string.job_interrupted_system)
                        jobDao.updateCompletion(
                            jobId = job.jobId,
                            status = ToolJobWorker.STATUS_INTERRUPTED,
                            percent = job.progressPercent,
                            message = interruptedMsg,
                            outputPath = "",
                            outputMime = "",
                            summary = "",
                            errorMessage = interruptedMsg,
                            isRetryable = retryable
                        )
                    } else {
                        jobDao.updateCompletion(
                            jobId = job.jobId,
                            status = ToolJobWorker.STATUS_CANCELLED,
                            percent = job.progressPercent,
                            message = context.getString(R.string.action_cancel),
                            outputPath = "",
                            outputMime = "",
                            summary = "",
                            errorMessage = "",
                            isRetryable = retryable
                        )
                    }
                }
                WorkInfo.State.RUNNING -> runningIds.add(job.jobId)
                WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED, null -> {
                    if (!job.isDummy && !JobInputValidator.areStoredInputsAccessible(context, job)) {
                        val expiredMsg = context.getString(R.string.job_interrupted_input_expired)
                        jobDao.updateCompletion(
                            jobId = job.jobId,
                            status = ToolJobWorker.STATUS_INTERRUPTED,
                            percent = job.progressPercent,
                            message = expiredMsg,
                            outputPath = "",
                            outputMime = "",
                            summary = "",
                            errorMessage = expiredMsg,
                            isRetryable = false
                        )
                    } else if (job.status == ToolJobWorker.STATUS_RUNNING || job.status == ToolJobWorker.STATUS_PREPARING) {
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

    private fun isWorkInfoSystemInterrupted(info: WorkInfo): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
        val reason = info.stopReason
        return reason != WorkInfo.STOP_REASON_NOT_STOPPED &&
            reason != WorkInfo.STOP_REASON_CANCELLED_BY_APP
    }

    fun triggerRecoveryAsync() {
        scope.launch { recoverPendingJobs() }
    }

    private suspend fun dispatchQueuedJob(jobId: String) {
        val job = jobDao.getById(jobId) ?: return
        if (job.status == ToolJobWorker.STATUS_CANCELLED || job.status == ToolJobWorker.STATUS_INTERRUPTED) return
        val restoredInput = job.inputJson.ifBlank {
            runCatching {
                storage.createJobTempDir(jobId).resolve("input.json").takeIf { it.exists() }?.readText(Charsets.UTF_8)
            }.getOrNull().orEmpty()
        }
        if (restoredInput.isNotBlank()) {
            runCatching {
                storage.createJobTempDir(jobId).resolve("input.json").writeText(restoredInput, Charsets.UTF_8)
            }
        }
        val req = buildWorkRequest(
            jobId = job.jobId,
            toolId = job.toolId,
            toolTitle = job.toolTitle,
            groupName = job.groupName,
            isDummy = job.isDummy,
            isEnglish = job.isEnglish,
            inputJson = restoredInput
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
        isEnglish: Boolean,
        inputJson: String = ""
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
                ToolJobWorker.KEY_IS_ENGLISH to isEnglish,
                ToolJobWorker.KEY_INPUT_JSON to inputJson
            )
        )
        .build()
}
