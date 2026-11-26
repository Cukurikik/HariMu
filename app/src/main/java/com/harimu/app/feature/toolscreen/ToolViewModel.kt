package com.harimu.app.feature.toolscreen

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.harimu.app.AppContainer
import com.harimu.app.R
import com.harimu.app.core.jobs.ToolJobWorker
import com.harimu.app.core.toolapi.InputSpec
import com.harimu.app.core.toolapi.Tool
import com.harimu.app.core.toolapi.ToolContext
import com.harimu.app.core.toolapi.ToolInput
import com.harimu.app.core.toolapi.ToolOutputFile
import com.harimu.app.core.toolapi.ToolParam
import com.harimu.app.core.toolapi.ToolProgress
import com.harimu.app.core.toolapi.ToolGroup
import com.harimu.app.core.toolapi.ToolRegistry
import com.harimu.app.core.toolapi.ToolResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class ToolUiState(
    val tool: Tool? = null,
    val selectedUris: List<Uri> = emptyList(),
    val secondaryUri: Uri? = null,
    val textInput: String = "",
    val paramValues: Map<String, Any> = emptyMap(),
    val progress: ToolProgress? = null,
    val isRunning: Boolean = false,
    val activeJobId: String? = null
)

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
    private val container: AppContainer
) : ViewModel() {

    private val _state = MutableStateFlow(ToolUiState())
    val state: StateFlow<ToolUiState> = _state.asStateFlow()

    private var runJob: Job? = null
    private var appContextRef: Context? = null

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
            container.jobManager.observeLatestForTool(toolId).collect { job ->
                if (job == null) return@collect
                when (job.status) {
                    ToolJobWorker.STATUS_QUEUED -> _state.update {
                        it.copy(
                            isRunning = true,
                            activeJobId = job.jobId,
                            progress = ToolProgress.Queued
                        )
                    }
                    ToolJobWorker.STATUS_RUNNING -> _state.update {
                        it.copy(
                            isRunning = true,
                            activeJobId = job.jobId,
                            progress = ToolProgress.Running(job.progressPercent, job.progressMessage)
                        )
                    }
                    ToolJobWorker.STATUS_SUCCEEDED -> {
                        val outFile = job.outputPath.takeIf { p -> p.isNotBlank() }?.let { File(it) }
                            ?.takeIf { f -> f.exists() }
                        val outputs = if (outFile != null) {
                            listOf(ToolOutputFile(outFile, job.outputMime.ifBlank { "text/plain" }, outFile.name))
                        } else emptyList()
                        _state.update {
                            it.copy(
                                isRunning = false,
                                activeJobId = null,
                                progress = ToolProgress.Done(
                                    ToolResult(
                                        outputs = outputs,
                                        textOutput = outFile?.takeIf { f -> f.length() < 8192L }?.runCatching { readText() }?.getOrNull(),
                                        summary = job.summary.ifBlank { job.toolTitle }
                                    )
                                )
                            )
                        }
                    }
                    ToolJobWorker.STATUS_FAILED -> _state.update {
                        it.copy(
                            isRunning = false,
                            activeJobId = null,
                            progress = ToolProgress.Failed(job.errorMessage.ifBlank { "Job failed" })
                        )
                    }
                    ToolJobWorker.STATUS_CANCELLED -> _state.update {
                        it.copy(
                            isRunning = false,
                            activeJobId = null,
                            progress = null
                        )
                    }
                }
            }
        }
    }

    fun setUris(uris: List<Uri>) {
        _state.update { it.copy(selectedUris = uris, progress = null) }
    }

    fun setSecondaryUri(uri: Uri?) {
        _state.update { it.copy(secondaryUri = uri, progress = null) }
    }

    fun setTextInput(text: String) {
        _state.update { it.copy(textInput = text, progress = null) }
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
                _state.update {
                    it.copy(
                        isRunning = job.status == ToolJobWorker.STATUS_QUEUED || job.status == ToolJobWorker.STATUS_RUNNING,
                        activeJobId = if (job.status == ToolJobWorker.STATUS_FAILED) null else job.jobId
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
                _state.update { it.copy(isRunning = true, progress = ToolProgress.Queued) }
                val job = container.jobManager.enqueueToolJob(tool, input, isEnglish)
                _state.update {
                    it.copy(
                        isRunning = job.status == ToolJobWorker.STATUS_QUEUED ||
                            job.status == ToolJobWorker.STATUS_RUNNING,
                        activeJobId = if (job.status == ToolJobWorker.STATUS_FAILED) null else job.jobId,
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
                _state.update {
                    it.copy(
                        isRunning = false,
                        progress = ToolProgress.Failed(ctxRef.getString(R.string.job_error_low_disk))
                    )
                }
                return@launch
            }
            _state.update { it.copy(isRunning = true, progress = ToolProgress.Queued) }
            val ctx = ToolContext(
                appContext = ctxRef,
                storage = container.storage,
                isEnglish = isEnglish,
                ai = container.aiClient,
                models = container.modelManager
            )
            val input = ToolInput(
                uris = _state.value.selectedUris,
                secondaryUri = _state.value.secondaryUri,
                textInput = _state.value.textInput,
                params = _state.value.paramValues
            )
            tool.run(ctx, input)
                .catch { e ->
                    _state.update {
                        it.copy(
                            isRunning = false,
                            progress = ToolProgress.Failed(e.localizedMessage ?: "Error")
                        )
                    }
                }
                .collect { p ->
                    when (p) {
                        is ToolProgress.Done -> {
                            val firstOut = p.result.outputs.firstOrNull()
                            container.workspace.recordHistory(
                                toolId = tool.id,
                                toolTitle = tool.title(isEnglish),
                                groupName = tool.group.name,
                                outputFile = firstOut?.file,
                                mimeType = firstOut?.mimeType ?: "text/plain",
                                summary = p.result.summary,
                                success = true
                            )
                            _state.update { it.copy(isRunning = false, progress = p) }
                        }
                        is ToolProgress.Failed -> {
                            _state.update { it.copy(isRunning = false, progress = p) }
                        }
                        else -> _state.update { it.copy(progress = p) }
                    }
                }
        }
    }

    private fun shouldUseWorker(tool: Tool): Boolean =
        tool.requiresNetwork || tool.requiresModel != null ||
            tool.group == ToolGroup.VIDEO || tool.group == ToolGroup.AUDIO ||
            tool.group == ToolGroup.PDF || tool.group == ToolGroup.CONVERTER

    fun cancel() {
        val currentJobId = _state.value.activeJobId
        if (currentJobId != null) {
            viewModelScope.launch {
                container.jobManager.cancelJob(currentJobId)
            }
        }
        runJob?.cancel()
        _state.update { it.copy(isRunning = false, activeJobId = null, progress = null) }
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
                progress = null
            )
        }
    }

    class Factory(
        private val toolId: String,
        private val container: AppContainer
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ToolViewModel(toolId, container) as T
    }
}
