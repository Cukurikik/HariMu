package com.harimu.app.feature.toolscreen

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.harimu.app.AppContainer
import com.harimu.app.R
import com.harimu.app.core.data.JobEntity
import com.harimu.app.core.design.ToolProgressState
import com.harimu.app.core.design.jobs.decodeJobOutputs
import com.harimu.app.core.design.toProgressState
import com.harimu.app.core.jobs.JobInputValidator
import com.harimu.app.core.jobs.ToolJobWorker
import com.harimu.app.core.toolapi.InputSpec
import com.harimu.app.core.toolapi.Tool
import com.harimu.app.core.toolapi.ToolContext
import com.harimu.app.core.toolapi.ToolGroup
import com.harimu.app.core.toolapi.ToolInput
import com.harimu.app.core.toolapi.ToolOutputCodec
import com.harimu.app.core.toolapi.ToolOutputFile
import com.harimu.app.core.toolapi.ToolParam
import com.harimu.app.core.toolapi.ToolProgress
import com.harimu.app.core.toolapi.ToolRegistry
import com.harimu.app.core.toolapi.ToolResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.math.abs

data class ToolUiState(
    val tool: Tool? = null,
    val selectedUris: List<Uri> = emptyList(),
    val secondaryUri: Uri? = null,
    val textInput: String = "",
    val paramValues: Map<String, Any> = emptyMap(),
    val progress: ToolProgress? = null,
    val isRunning: Boolean = false,
    val activeJobId: String? = null,
    val lastJobId: String? = null,
    val jobStatus: String = "",
    val isRetryable: Boolean = false,
    val isInterrupted: Boolean = false,
    val isCancelled: Boolean = false,
    val jobErrorMessage: String = ""
) {
    fun toUiProgressState(): ToolProgressState = when {
        isInterrupted -> ToolProgressState.Interrupted(
            message = jobErrorMessage,
            isRetryable = isRetryable
        )
        isCancelled -> ToolProgressState.Cancelled
        progress is ToolProgress.Failed -> ToolProgressState.Failed(
            error = progress.error,
            isRetryable = isRetryable
        )
        else -> progress?.toProgressState() ?: ToolProgressState.Idle
    }
}

/**
 * Pre-fill data for "Open in Tool" flow from Pro Video Editor.
 * Stores clip URI and parameters to pre-populate the tool screen.
 */
object ToolPrefillData {
    private var _pendingPrefill: Map<String, Any>? = null
    private var _pendingUri: Uri? = null

    fun setPrefill(uri: Uri, params: Map<String, Any>) {
        _pendingUri = uri
        _pendingPrefill = params
    }

    fun consumePrefill(): Pair<Uri?, Map<String, Any>?> {
        val uri = _pendingUri
        val params = _pendingPrefill
        _pendingUri = null
        _pendingPrefill = null
        return uri to params
    }

    fun hasPending(): Boolean = _pendingUri != null || _pendingPrefill != null
}

class ToolViewModel(
    private val toolId: String,
    private val container: AppContainer,
    private val initialJobId: String? = null
) : ViewModel() {

    private val _state = MutableStateFlow(ToolUiState())
    val state: StateFlow<ToolUiState> = _state.asStateFlow()

    private var runJob: Job? = null
    private var appContextRef: Context? = null
    private var sessionJobId: String? = initialJobId

    init {
        val tool = ToolRegistry.findById(toolId)
        val initialParams = mutableMapOf<String, Any>()
        tool?.params?.forEach { p ->
            when (p) {
                is ToolParam.SliderParam -> initialParams[p.key] = p.defaultValue
                is ToolParam.DropdownParam -> initialParams[p.key] = p.defaultOption
                is ToolParam.ToggleParam -> initialParams[p.key] = p.defaultValue
                is ToolParam.TextParam -> initialParams[p.key] = p.defaultValue
            }
        }

        // Check for pre-fill data from "Open in Tool" flow
        val (prefillUri, prefillParams) = ToolPrefillData.consumePrefill()
        if (prefillUri != null) {
            initialParams.putAll(prefillParams ?: emptyMap())
        }
        val initialText = (tool?.inputSpec as? InputSpec.TextInput)?.sample ?: ""
        _state.value = ToolUiState(
            tool = tool,
            selectedUris = prefillUri?.let { listOf(it) } ?: emptyList(),
            paramValues = initialParams,
            textInput = initialText
        )

        viewModelScope.launch {
            val flow = if (!initialJobId.isNullOrBlank()) {
                container.jobManager.observeJob(initialJobId)
            } else {
                container.jobManager.observeLatestForTool(toolId)
            }
            flow.collect { job ->
                if (job == null) return@collect
                if (initialJobId.isNullOrBlank()) {
                    if (job.jobId != sessionJobId && !ToolJobWorker.isActiveStatus(job.status)) {
                        return@collect
                    }
                    if (job.executionMode == ToolJobWorker.MODE_IN_PROCESS) {
                        if (job.jobId == sessionJobId &&
                            job.status == ToolJobWorker.STATUS_CANCELLED &&
                            _state.value.isRunning
                        ) {
                            runJob?.cancel()
                            applyObservedJob(job)
                        }
                        return@collect
                    }
                }
                if (ToolJobWorker.isActiveStatus(job.status)) {
                    sessionJobId = job.jobId
                }
                applyObservedJob(job)
            }
        }
    }

    private suspend fun applyObservedJob(job: JobEntity) {
        when (job.status) {
            ToolJobWorker.STATUS_QUEUED -> _state.update {
                it.copy(
                    isRunning = true,
                    activeJobId = job.jobId,
                    lastJobId = job.jobId,
                    jobStatus = job.status,
                    isInterrupted = false,
                    isCancelled = false,
                    jobErrorMessage = "",
                    progress = ToolProgress.Queued
                )
            }
            ToolJobWorker.STATUS_PREPARING -> _state.update {
                it.copy(
                    isRunning = true,
                    activeJobId = job.jobId,
                    lastJobId = job.jobId,
                    jobStatus = job.status,
                    isInterrupted = false,
                    isCancelled = false,
                    jobErrorMessage = "",
                    progress = ToolProgress.Running(0, job.progressMessage)
                )
            }
            ToolJobWorker.STATUS_RUNNING -> _state.update {
                it.copy(
                    isRunning = true,
                    activeJobId = job.jobId,
                    lastJobId = job.jobId,
                    jobStatus = job.status,
                    isInterrupted = false,
                    isCancelled = false,
                    jobErrorMessage = "",
                    progress = ToolProgress.Running(job.progressPercent, job.progressMessage)
                )
            }
            ToolJobWorker.STATUS_SUCCEEDED -> {
                val workData = withContext(Dispatchers.IO) {
                    val ctx = appContextRef
                    val uuid = job.workRequestId.takeIf { it.isNotBlank() }
                        ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                    if (ctx != null && uuid != null) {
                        runCatching { WorkManager.getInstance(ctx).getWorkInfoById(uuid).get()?.outputData }.getOrNull()
                    } else {
                        null
                    }
                }
                val jobOutputs = decodeJobOutputs(job)
                val decodedWorkOutputs = workData?.getString(ToolJobWorker.KEY_OUTPUTS_JSON)
                    ?.let { raw -> ToolOutputCodec.decode(raw) }
                    .orEmpty()
                    .map { entry ->
                        ToolOutputFile(File(entry.path), entry.mimeType, entry.displayName)
                    }
                val outputs = jobOutputs.ifEmpty { decodedWorkOutputs }
                val workerText = job.textOutput.takeIf { it.isNotBlank() }
                    ?: workData?.getString(ToolJobWorker.KEY_TEXT_OUTPUT)?.takeIf { it.isNotBlank() }
                val fallbackText = outputs.firstOrNull { it.file.exists() && it.mimeType.startsWith("text/") }
                    ?.file?.takeIf { f -> f.length() < 16384L }?.runCatching { readText() }?.getOrNull()
                _state.update {
                    it.copy(
                        isRunning = false,
                        activeJobId = null,
                        lastJobId = job.jobId,
                        jobStatus = job.status,
                        isRetryable = false,
                        isInterrupted = false,
                        isCancelled = false,
                        jobErrorMessage = "",
                        progress = ToolProgress.Done(
                            ToolResult(
                                outputs = outputs,
                                textOutput = workerText ?: fallbackText,
                                summary = job.summary.ifBlank { job.toolTitle }
                            )
                        )
                    )
                }
            }
            ToolJobWorker.STATUS_FAILED -> {
                val retryable = container.jobManager.canRetry(job)
                val msg = job.errorMessage.ifBlank { "Job failed" }
                _state.update {
                    it.copy(
                        isRunning = false,
                        activeJobId = null,
                        lastJobId = job.jobId,
                        jobStatus = job.status,
                        isRetryable = retryable,
                        isInterrupted = false,
                        isCancelled = false,
                        jobErrorMessage = msg,
                        progress = ToolProgress.Failed(msg)
                    )
                }
            }
            ToolJobWorker.STATUS_INTERRUPTED -> {
                val retryable = container.jobManager.canRetry(job)
                val msg = job.errorMessage.ifBlank { job.progressMessage }
                _state.update {
                    it.copy(
                        isRunning = false,
                        activeJobId = null,
                        lastJobId = job.jobId,
                        jobStatus = job.status,
                        isRetryable = retryable,
                        isInterrupted = true,
                        isCancelled = false,
                        jobErrorMessage = msg,
                        progress = null
                    )
                }
            }
            ToolJobWorker.STATUS_CANCELLED -> {
                val retryable = container.jobManager.canRetry(job)
                _state.update {
                    it.copy(
                        isRunning = false,
                        activeJobId = null,
                        lastJobId = job.jobId,
                        jobStatus = job.status,
                        isRetryable = retryable,
                        isInterrupted = false,
                        isCancelled = true,
                        jobErrorMessage = "",
                        progress = null
                    )
                }
            }
        }
    }

    fun setUris(uris: List<Uri>) {
        _state.update {
            it.copy(selectedUris = uris, progress = null, isInterrupted = false, isCancelled = false, jobErrorMessage = "")
        }
    }

    fun setSecondaryUri(uri: Uri?) {
        _state.update {
            it.copy(secondaryUri = uri, progress = null, isInterrupted = false, isCancelled = false, jobErrorMessage = "")
        }
    }

    fun setTextInput(text: String) {
        _state.update {
            it.copy(textInput = text, progress = null, isInterrupted = false, isCancelled = false, jobErrorMessage = "")
        }
    }

    fun setParam(key: String, value: Any) {
        _state.update { it.copy(paramValues = it.paramValues + (key to value)) }
    }

    fun execute(isEnglish: Boolean) {
        val tool = _state.value.tool ?: return
        if (tool.isStub) {
            runJob?.cancel()
            runJob = viewModelScope.launch {
                val job = container.jobManager.enqueueDummyJob(tool, isEnglish)
                sessionJobId = job.jobId
                _state.update {
                    it.copy(
                        isRunning = ToolJobWorker.isActiveStatus(job.status),
                        activeJobId = if (job.status == ToolJobWorker.STATUS_FAILED) null else job.jobId,
                        lastJobId = job.jobId,
                        jobStatus = job.status,
                        isRetryable = job.isRetryable,
                        isInterrupted = false,
                        isCancelled = false,
                        jobErrorMessage = if (job.status == ToolJobWorker.STATUS_FAILED) job.errorMessage else "",
                        progress = if (job.status == ToolJobWorker.STATUS_FAILED) {
                            ToolProgress.Failed(job.errorMessage.ifBlank { "Job gagal" })
                        } else {
                            ToolProgress.Queued
                        }
                    )
                }
            }
            return
        }

        val ctxRef = appContextRef ?: return
        runJob?.cancel()

        // Tool berat (video/audio/PDF/konverter/AI) dijalankan WorkManager + foreground service
        // supaya tidak mati saat aplikasi di latar belakang.
        if (shouldUseWorker(tool)) {
            val input = ToolInput(
                uris = _state.value.selectedUris,
                secondaryUri = _state.value.secondaryUri,
                textInput = _state.value.textInput,
                params = _state.value.paramValues
            )
            runJob = viewModelScope.launch {
                _state.update {
                    it.copy(
                        isRunning = true,
                        isInterrupted = false,
                        isCancelled = false,
                        jobErrorMessage = "",
                        progress = ToolProgress.Queued
                    )
                }
                val job = container.jobManager.enqueueToolJob(tool, input, isEnglish)
                sessionJobId = job.jobId
                _state.update {
                    it.copy(
                        isRunning = ToolJobWorker.isActiveStatus(job.status),
                        activeJobId = if (job.status == ToolJobWorker.STATUS_FAILED) null else job.jobId,
                        lastJobId = job.jobId,
                        jobStatus = job.status,
                        isRetryable = job.isRetryable,
                        jobErrorMessage = if (job.status == ToolJobWorker.STATUS_FAILED) job.errorMessage else "",
                        progress = if (job.status == ToolJobWorker.STATUS_FAILED) {
                            ToolProgress.Failed(job.errorMessage.ifBlank { "Job gagal" })
                        } else {
                            ToolProgress.Queued
                        }
                    )
                }
            }
            return
        }

        runJob = viewModelScope.launch {
            if (!container.storage.hasEnoughDiskSpace()) {
                val msg = ctxRef.getString(R.string.job_error_low_disk)
                _state.update {
                    it.copy(
                        isRunning = false,
                        isInterrupted = false,
                        isCancelled = false,
                        isRetryable = true,
                        jobErrorMessage = msg,
                        progress = ToolProgress.Failed(msg)
                    )
                }
                return@launch
            }
            val input = ToolInput(
                uris = _state.value.selectedUris,
                secondaryUri = _state.value.secondaryUri,
                textInput = _state.value.textInput,
                params = _state.value.paramValues
            )
            val trackedJob = container.jobManager.startInProcessJob(tool, input, isEnglish)
            sessionJobId = trackedJob.jobId
            _state.update {
                it.copy(
                    isRunning = true,
                    activeJobId = trackedJob.jobId,
                    lastJobId = trackedJob.jobId,
                    jobStatus = ToolJobWorker.STATUS_PREPARING,
                    isRetryable = trackedJob.isRetryable,
                    isInterrupted = false,
                    isCancelled = false,
                    jobErrorMessage = "",
                    progress = ToolProgress.Running(0, trackedJob.progressMessage)
                )
            }
            val ctx = ToolContext(
                appContext = ctxRef,
                storage = container.storage,
                isEnglish = isEnglish,
                ai = container.aiClient,
                models = container.modelManager
            )
            var lastDbPct = -1
            var lastDbStatus = ToolJobWorker.STATUS_PREPARING
            tool.run(ctx, input)
                .catch { e ->
                    if (e is CancellationException) throw e
                    val err = e.localizedMessage ?: "Error"
                    container.jobManager.failInProcessJob(trackedJob.jobId, err)
                    val retryable = JobInputValidator.canRetryStoredInput(ctxRef, tool, input)
                    _state.update {
                        it.copy(
                            isRunning = false,
                            activeJobId = null,
                            lastJobId = trackedJob.jobId,
                            jobStatus = ToolJobWorker.STATUS_FAILED,
                            isRetryable = retryable,
                            isInterrupted = false,
                            isCancelled = false,
                            jobErrorMessage = err,
                            progress = ToolProgress.Failed(err)
                        )
                    }
                }
                .collect { p ->
                    when (p) {
                        is ToolProgress.Queued -> _state.update {
                            it.copy(progress = p, jobStatus = ToolJobWorker.STATUS_QUEUED)
                        }
                        is ToolProgress.Running -> {
                            val dbStatus = if (ToolJobWorker.isPreparingSignal(p.percent, p.message)) {
                                ToolJobWorker.STATUS_PREPARING
                            } else {
                                ToolJobWorker.STATUS_RUNNING
                            }
                            val pct = p.percent.coerceIn(0, 100)
                            if (dbStatus != lastDbStatus || abs(pct - lastDbPct) >= 5 || pct == 100) {
                                lastDbStatus = dbStatus
                                lastDbPct = pct
                                container.jobManager.updateInProcessJob(trackedJob.jobId, dbStatus, pct, p.message)
                            }
                            _state.update { it.copy(progress = p, jobStatus = dbStatus) }
                        }
                        is ToolProgress.Done -> {
                            container.jobManager.completeInProcessJob(trackedJob.jobId, p.result)
                            val firstOut = p.result.outputs.firstOrNull()
                            val outputsJson = ToolOutputCodec.encode(
                                p.result.outputs.map { out ->
                                    ToolOutputCodec.Entry(out.file.absolutePath, out.mimeType, out.displayName)
                                }
                            )
                            container.workspace.recordHistory(
                                toolId = tool.id,
                                toolTitle = tool.title(isEnglish),
                                groupName = tool.group.name,
                                outputFile = firstOut?.file,
                                mimeType = firstOut?.mimeType ?: "text/plain",
                                summary = p.result.summary,
                                success = true,
                                jobId = trackedJob.jobId,
                                outputsJson = outputsJson,
                                textOutput = p.result.textOutput.orEmpty()
                            )
                            _state.update {
                                it.copy(
                                    isRunning = false,
                                    activeJobId = null,
                                    lastJobId = trackedJob.jobId,
                                    jobStatus = ToolJobWorker.STATUS_SUCCEEDED,
                                    isRetryable = false,
                                    isInterrupted = false,
                                    isCancelled = false,
                                    jobErrorMessage = "",
                                    progress = p
                                )
                            }
                        }
                        is ToolProgress.Failed -> {
                            container.jobManager.failInProcessJob(trackedJob.jobId, p.error)
                            val retryable = JobInputValidator.canRetryStoredInput(ctxRef, tool, input)
                            _state.update {
                                it.copy(
                                    isRunning = false,
                                    activeJobId = null,
                                    lastJobId = trackedJob.jobId,
                                    jobStatus = ToolJobWorker.STATUS_FAILED,
                                    isRetryable = retryable,
                                    isInterrupted = false,
                                    isCancelled = false,
                                    jobErrorMessage = p.error,
                                    progress = p
                                )
                            }
                        }
                    }
                }
        }
    }

    private fun shouldUseWorker(tool: Tool): Boolean =
        tool.requiresNetwork || tool.requiresModel != null ||
            tool.group == ToolGroup.VIDEO || tool.group == ToolGroup.AUDIO ||
            tool.group == ToolGroup.PDF || tool.group == ToolGroup.CONVERTER

    fun cancel() {
        val currentJobId = _state.value.activeJobId ?: _state.value.lastJobId
        val ctxRef = appContextRef
        val currentTool = _state.value.tool
        val canRetryCurrent = if (ctxRef != null && currentTool != null) {
            val input = ToolInput(
                uris = _state.value.selectedUris,
                secondaryUri = _state.value.secondaryUri,
                textInput = _state.value.textInput,
                params = _state.value.paramValues
            )
            JobInputValidator.canRetryStoredInput(ctxRef, currentTool, input)
        } else {
            false
        }
        if (currentJobId != null) {
            viewModelScope.launch {
                container.jobManager.cancelJob(currentJobId)
            }
        }
        runJob?.cancel()
        _state.update {
            it.copy(
                isRunning = false,
                activeJobId = null,
                lastJobId = currentJobId ?: it.lastJobId,
                jobStatus = ToolJobWorker.STATUS_CANCELLED,
                isCancelled = true,
                isInterrupted = false,
                isRetryable = canRetryCurrent,
                progress = null
            )
        }
    }

    fun retry(isEnglish: Boolean) {
        val ctxRef = appContextRef
        val tool = _state.value.tool ?: return
        val currentInput = ToolInput(
            uris = _state.value.selectedUris,
            secondaryUri = _state.value.secondaryUri,
            textInput = _state.value.textInput,
            params = _state.value.paramValues
        )
        val hasCurrentInputs = ctxRef != null && JobInputValidator.canRetryStoredInput(ctxRef, tool, currentInput)
        if (hasCurrentInputs || tool.isStub) {
            execute(isEnglish)
            return
        }
        val failedJobId = _state.value.lastJobId ?: return
        viewModelScope.launch {
            val retried = container.jobManager.retryJob(failedJobId, isEnglish)
            if (retried != null) {
                sessionJobId = retried.jobId
                _state.update {
                    it.copy(
                        isRunning = ToolJobWorker.isActiveStatus(retried.status),
                        activeJobId = retried.jobId,
                        lastJobId = retried.jobId,
                        jobStatus = retried.status,
                        isInterrupted = false,
                        isCancelled = false,
                        progress = ToolProgress.Queued
                    )
                }
            }
        }
    }

    fun bindContext(context: Context) {
        appContextRef = context.applicationContext
    }

    /**
     * Pre-fill the tool with a clip URI and parameters from the Pro Video Editor.
     * Called when user taps "Open in Tool" from ClipInspector.
     */
    fun preFillFromClip(uri: Uri, params: Map<String, Any>) {
        _state.update { current ->
            val newUris = if (current.selectedUris.contains(uri)) current.selectedUris else listOf(uri)
            val newParams = current.paramValues + params
            current.copy(
                selectedUris = newUris,
                paramValues = newParams,
                isInterrupted = false,
                isCancelled = false,
                jobErrorMessage = "",
                progress = null
            )
        }
    }

    class Factory(
        private val toolId: String,
        private val container: AppContainer,
        private val initialJobId: String? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ToolViewModel(toolId, container, initialJobId) as T
    }
}
