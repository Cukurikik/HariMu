package com.harimu.app.feature.studios

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.harimu.app.AppContainer
import com.harimu.app.R
import com.harimu.app.core.converter.ConversionTargetOption
import com.harimu.app.core.converter.ConversionValidationResult
import com.harimu.app.core.converter.ConverterFormatMatrix
import com.harimu.app.core.converter.ConverterWorkflow
import com.harimu.app.core.converter.DetectedSource
import com.harimu.app.core.converter.SourceFormat
import com.harimu.app.core.converter.SourceFormatDetector
import com.harimu.app.core.data.JobEntity
import com.harimu.app.core.toolapi.Tool
import com.harimu.app.core.toolapi.ToolGroup
import com.harimu.app.core.toolapi.ToolInput
import com.harimu.app.core.toolapi.ToolOutputFile
import com.harimu.app.core.toolapi.ToolParam
import com.harimu.app.core.toolapi.ToolRegistry
import com.harimu.app.feature.toolscreen.ExportState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ConverterScreenMode { WORKBENCH, WORKFLOWS }

data class UniversalConverterUiState(
    val mode: ConverterScreenMode = ConverterScreenMode.WORKBENCH,
    val isTextMode: Boolean = false,
    val sources: List<DetectedSource> = emptyList(),
    val sourceUris: List<Uri> = emptyList(),
    val textInput: String = "",
    val detectedTextFormat: SourceFormat = SourceFormat.UNKNOWN,
    val availableTargets: List<ConversionTargetOption> = emptyList(),
    val selectedTargetId: String? = null,
    val paramValues: Map<String, Any> = emptyMap(),
    val batchSeparateJobs: Boolean = true,
    val selectedWorkflow: ConverterWorkflow? = null,
    val focusedToolId: String? = null,
    val searchQuery: String = "",
    val isEnqueueing: Boolean = false,
    val lastEnqueueMessage: String? = null,
    val sourceWarningMessage: String? = null,
    val exportState: ExportState = ExportState.Idle,
    val pendingExportOutput: ToolOutputFile? = null
) {
    val selectedTarget: ConversionTargetOption?
        get() = availableTargets.firstOrNull { it.id == selectedTargetId }

    val selectedTool: Tool?
        get() = selectedTarget?.let { ToolRegistry.findById(it.toolId) }

    val focusedTool: Tool?
        get() = focusedToolId?.let { ToolRegistry.findById(it) }

    val visibleParams: List<ToolParam>
        get() {
            val target = selectedTarget ?: return emptyList()
            val tool = selectedTool ?: return emptyList()
            if (target.visibleParamKeys.isEmpty()) return emptyList()
            return tool.params.filter { it.key in target.visibleParamKeys }
        }

    fun mergedParams(): Map<String, Any> {
        val target = selectedTarget ?: return paramValues
        return paramValues + target.fixedParams
    }

    fun unsupportedReasons(isEnglish: Boolean): List<String> =
        if (isTextMode) {
            emptyList()
        } else {
            sources.mapNotNull { ConverterFormatMatrix.unsupportedReason(it.format, isEnglish) }.distinct()
        }

    fun mixedIncompatibleReason(isEnglish: Boolean): String? =
        if (isTextMode) {
            null
        } else {
            ConverterFormatMatrix.mixedIncompatibleReason(sources, isEnglish)
        }

    fun validate(isEnglish: Boolean): ConversionValidationResult =
        ConverterFormatMatrix.validateInputs(
            target = selectedTarget,
            sources = sources,
            textInput = textInput,
            params = mergedParams(),
            isEnglish = isEnglish,
            isTextMode = isTextMode
        )
}

class UniversalConverterViewModel(
    private val container: AppContainer,
    initialToolId: String? = null
) : ViewModel() {

    private val _state = MutableStateFlow(UniversalConverterUiState())
    val state: StateFlow<UniversalConverterUiState> = _state.asStateFlow()

    val converterJobs: StateFlow<List<JobEntity>> = container.jobManager.observeAllJobs()
        .map { jobs ->
            jobs.filter { it.groupName == ToolGroup.CONVERTER.name || it.toolId.startsWith("CNV-") }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var appContextRef: Context? = null
    private val retryPayloads = mutableMapOf<String, Pair<Tool, ToolInput>>()

    init {
        if (!initialToolId.isNullOrBlank()) {
            focusToolOrWorkflow(initialToolId)
        }
    }

    fun bindContext(context: Context) {
        appContextRef = context.applicationContext
    }

    fun setMode(mode: ConverterScreenMode) {
        _state.update { it.copy(mode = mode) }
    }

    fun setSelectedWorkflow(workflow: ConverterWorkflow?) {
        _state.update { it.copy(selectedWorkflow = workflow) }
    }

    fun setSearchQuery(query: String) {
        _state.update { it.copy(searchQuery = query) }
    }

    fun setTextMode(textMode: Boolean) {
        _state.update { current ->
            val targets = if (textMode) {
                val fmt = SourceFormatDetector.detectTextFormat(current.textInput)
                ConverterFormatMatrix.validFormatTargetsFor(fmt)
            } else {
                ConverterFormatMatrix.validTargetsForSources(current.sources)
            }
            val nextTarget = targets.firstOrNull { it.id == current.selectedTargetId } ?: targets.firstOrNull()
            current.copy(
                isTextMode = textMode,
                detectedTextFormat = if (textMode) SourceFormatDetector.detectTextFormat(current.textInput) else current.detectedTextFormat,
                availableTargets = targets,
                selectedTargetId = nextTarget?.id,
                paramValues = seedParamsForTarget(nextTarget, current.paramValues, current.selectedTarget?.toolId),
                lastEnqueueMessage = null,
                sourceWarningMessage = null
            )
        }
    }

    fun addFiles(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val ctx = appContextRef ?: return
        viewModelScope.launch {
            val detected = withContext(Dispatchers.IO) {
                uris.map { uri -> SourceFormatDetector.detectFromUri(ctx, container.storage, uri) }
            }
            _state.update { current ->
                val mergedPairs = (current.sourceUris.zip(current.sources) + uris.zip(detected))
                    .distinctBy { it.first.toString() }
                val newUris = mergedPairs.map { it.first }
                val newSources = mergedPairs.map { it.second }
                val targets = ConverterFormatMatrix.validTargetsForSources(newSources)
                val nextTarget = current.focusedToolId?.let { focusId ->
                    targets.firstOrNull { it.toolId == focusId }
                } ?: targets.firstOrNull { it.id == current.selectedTargetId }
                    ?: targets.firstOrNull()
                current.copy(
                    isTextMode = false,
                    sourceUris = newUris,
                    sources = newSources,
                    availableTargets = targets,
                    selectedTargetId = nextTarget?.id,
                    paramValues = seedParamsForTarget(nextTarget, current.paramValues, current.selectedTarget?.toolId),
                    lastEnqueueMessage = null,
                    sourceWarningMessage = null
                )
            }
        }
    }

    fun removeFileAt(index: Int) {
        _state.update { current ->
            if (index !in current.sources.indices) return@update current
            val newUris = current.sourceUris.toMutableList().apply { removeAt(index) }
            val newSources = current.sources.toMutableList().apply { removeAt(index) }
            val targets = ConverterFormatMatrix.validTargetsForSources(newSources)
            val nextTarget = targets.firstOrNull { it.id == current.selectedTargetId } ?: targets.firstOrNull()
            current.copy(
                sourceUris = newUris,
                sources = newSources,
                availableTargets = targets,
                selectedTargetId = nextTarget?.id,
                paramValues = seedParamsForTarget(nextTarget, current.paramValues, current.selectedTarget?.toolId),
                sourceWarningMessage = null
            )
        }
    }

    fun clearFiles() {
        _state.update {
            it.copy(
                sourceUris = emptyList(),
                sources = emptyList(),
                availableTargets = emptyList(),
                selectedTargetId = null,
                paramValues = emptyMap(),
                lastEnqueueMessage = null,
                sourceWarningMessage = null
            )
        }
    }

    fun setTextInput(text: String) {
        _state.update { current ->
            val fmt = SourceFormatDetector.detectTextFormat(text)
            val targets = ConverterFormatMatrix.validFormatTargetsFor(fmt)
            val nextTarget = targets.firstOrNull { it.id == current.selectedTargetId } ?: targets.firstOrNull()
            current.copy(
                isTextMode = true,
                textInput = text,
                detectedTextFormat = fmt,
                availableTargets = targets,
                selectedTargetId = nextTarget?.id,
                paramValues = seedParamsForTarget(nextTarget, current.paramValues, current.selectedTarget?.toolId),
                lastEnqueueMessage = null,
                sourceWarningMessage = null
            )
        }
    }

    fun loadSample(format: SourceFormat) {
        val sample = ConverterFormatMatrix.sampleTextForWorkflow(format)
        setTextInput(sample)
    }

    fun loadTextFromUri(uri: Uri, isEnglish: Boolean = false) {
        val ctx = appContextRef ?: return
        viewModelScope.launch {
            val maxEditorBytes = 512 * 1024
            val loadResult = withContext(Dispatchers.IO) {
                runCatching {
                    ctx.contentResolver.openInputStream(uri)?.use { stream ->
                        val out = java.io.ByteArrayOutputStream()
                        val buf = ByteArray(8192)
                        var truncated = false
                        while (out.size() < maxEditorBytes) {
                            val toRead = minOf(buf.size, maxEditorBytes - out.size())
                            val n = stream.read(buf, 0, toRead)
                            if (n <= 0) break
                            out.write(buf, 0, n)
                        }
                        if (out.size() == maxEditorBytes && stream.read() != -1) {
                            truncated = true
                        }
                        out.toByteArray() to truncated
                    }
                }.getOrNull()
            }
            if (loadResult == null) {
                _state.update {
                    it.copy(sourceWarningMessage = ctx.getString(R.string.converter_text_load_empty_err))
                }
                return@launch
            }
            val (bytes, truncated) = loadResult
            if (bytes.isEmpty()) {
                _state.update {
                    it.copy(sourceWarningMessage = ctx.getString(R.string.converter_text_load_empty_err))
                }
                return@launch
            }
            val looksBinary = bytes.take(4096).any { it == 0.toByte() }
            if (looksBinary) {
                _state.update {
                    it.copy(sourceWarningMessage = ctx.getString(R.string.converter_text_load_binary_err))
                }
                return@launch
            }
            val content = bytes.toString(Charsets.UTF_8)
            setTextInput(content)
            if (truncated) {
                _state.update {
                    it.copy(sourceWarningMessage = ctx.getString(R.string.converter_text_load_truncated))
                }
            }
        }
    }

    fun selectTarget(target: ConversionTargetOption) {
        _state.update { current ->
            current.copy(
                selectedTargetId = target.id,
                paramValues = seedParamsForTarget(target, current.paramValues, current.selectedTarget?.toolId),
                lastEnqueueMessage = null
            )
        }
    }

    fun setParam(key: String, value: Any) {
        _state.update { it.copy(paramValues = it.paramValues + (key to value)) }
    }

    fun setBatchSeparate(separate: Boolean) {
        _state.update { it.copy(batchSeparateJobs = separate) }
    }

    fun clearFocusedTool() {
        _state.update { it.copy(focusedToolId = null) }
    }

    fun focusToolOrWorkflow(toolId: String) {
        val workflow = ConverterWorkflow.workflowForTool(toolId)
        val isDataTool = toolId in setOf("CNV-23", "CNV-24", "CNV-25", "CNV-26", "CNV-27")
        if (isDataTool) {
            val sampleFmt = when (toolId) {
                "CNV-23" -> SourceFormat.JSON
                "CNV-24" -> SourceFormat.CSV
                "CNV-25" -> SourceFormat.JSON
                "CNV-26" -> SourceFormat.MARKDOWN
                else -> SourceFormat.GEOJSON
            }
            val sample = ConverterFormatMatrix.sampleTextForWorkflow(sampleFmt)
            val targets = ConverterFormatMatrix.validFormatTargetsFor(sampleFmt)
            val preferred = targets.firstOrNull { it.toolId == toolId } ?: targets.firstOrNull()
            _state.update { current ->
                current.copy(
                    mode = ConverterScreenMode.WORKBENCH,
                    selectedWorkflow = workflow,
                    focusedToolId = toolId,
                    isTextMode = true,
                    textInput = sample,
                    detectedTextFormat = sampleFmt,
                    availableTargets = targets,
                    selectedTargetId = preferred?.id,
                    paramValues = seedParamsForTarget(preferred, current.paramValues, current.selectedTarget?.toolId),
                    sourceWarningMessage = null
                )
            }
        } else {
            _state.update { current ->
                val fileTargets = if (current.sources.isNotEmpty()) {
                    ConverterFormatMatrix.validTargetsForSources(current.sources)
                } else {
                    emptyList()
                }
                val matchingTarget = fileTargets.firstOrNull { it.toolId == toolId }
                val nextTarget = matchingTarget ?: fileTargets.firstOrNull { it.id == current.selectedTargetId } ?: fileTargets.firstOrNull()
                current.copy(
                    mode = ConverterScreenMode.WORKBENCH,
                    selectedWorkflow = workflow,
                    focusedToolId = toolId,
                    isTextMode = false,
                    availableTargets = fileTargets,
                    selectedTargetId = nextTarget?.id,
                    paramValues = seedParamsForTarget(nextTarget, current.paramValues, current.selectedTarget?.toolId),
                    sourceWarningMessage = null
                )
            }
        }
    }

    fun enqueueConversion(isEnglish: Boolean) {
        val current = _state.value
        val target = current.selectedTarget ?: return
        val tool = current.selectedTool ?: return
        val validation = current.validate(isEnglish)
        if (!validation.isValid) {
            _state.update { it.copy(lastEnqueueMessage = validation.blockingError) }
            return
        }

        val params = current.mergedParams()
        viewModelScope.launch {
            _state.update { it.copy(isEnqueueing = true, lastEnqueueMessage = null) }
            val enqueuedJobs = mutableListOf<JobEntity>()

            if (current.isTextMode) {
                val input = ToolInput(textInput = current.textInput, params = params)
                val job = container.jobManager.enqueueToolJob(tool, input, isEnglish)
                retryPayloads[job.jobId] = tool to input
                enqueuedJobs += job
            } else if (target.supportsMultiInputSingleJob || current.sourceUris.size <= 1) {
                val input = ToolInput(uris = current.sourceUris, params = params)
                val job = container.jobManager.enqueueToolJob(tool, input, isEnglish)
                retryPayloads[job.jobId] = tool to input
                enqueuedJobs += job
            } else {
                // Multi-file format conversions always enqueue one job per file so no files are silently dropped.
                for (uri in current.sourceUris) {
                    val input = ToolInput(uris = listOf(uri), params = params)
                    val job = container.jobManager.enqueueToolJob(tool, input, isEnglish)
                    retryPayloads[job.jobId] = tool to input
                    enqueuedJobs += job
                }
            }

            val msg = appContextRef?.let { ctx ->
                if (enqueuedJobs.size > 1) {
                    ctx.getString(R.string.converter_enqueue_batch, enqueuedJobs.size)
                } else {
                    ctx.getString(R.string.converter_enqueue_single)
                }
            } ?: if (enqueuedJobs.size > 1) {
                if (isEnglish) "Queued ${enqueuedJobs.size} conversion jobs" else "${enqueuedJobs.size} job konversi masuk antrean"
            } else {
                if (isEnglish) "Conversion job queued" else "Job konversi dimasukkan ke antrean"
            }
            _state.update { it.copy(isEnqueueing = false, lastEnqueueMessage = msg) }
        }
    }

    fun cancelJob(jobId: String) {
        viewModelScope.launch {
            container.jobManager.cancelJob(jobId)
        }
    }

    fun clearFinishedJobs() {
        viewModelScope.launch(Dispatchers.IO) {
            container.jobManager.clearFinishedJobs()
        }
    }

    fun canRetry(jobId: String): Boolean {
        if (jobId in retryPayloads) return true
        val job = converterJobs.value.firstOrNull { it.jobId == jobId } ?: return false
        return container.jobManager.canRetry(job)
    }

    fun retryJob(jobId: String, isEnglish: Boolean) {
        val payload = retryPayloads[jobId]
        viewModelScope.launch {
            if (payload != null) {
                val newJob = container.jobManager.enqueueToolJob(payload.first, payload.second, isEnglish)
                retryPayloads[newJob.jobId] = payload
            } else {
                container.jobManager.retryJob(jobId, isEnglish)
            }
        }
    }

    fun exportToDefaultFolderOrRequestSaf(
        output: ToolOutputFile,
        defaultTreeUri: String,
        onRequestSaf: (ToolOutputFile) -> Unit
    ) {
        if (!output.file.exists()) {
            _state.update { it.copy(exportState = ExportState.Failed(output.displayName)) }
            return
        }
        if (defaultTreeUri.isNotBlank()) {
            viewModelScope.launch {
                _state.update {
                    it.copy(
                        exportState = ExportState.Exporting(output.displayName),
                        pendingExportOutput = output
                    )
                }
                val savedUri = withContext(Dispatchers.IO) {
                    runCatching {
                        container.storage.saveToTreeUri(
                            source = output.file,
                            treeUri = defaultTreeUri.toUri(),
                            mimeType = output.mimeType
                        )
                    }.getOrNull()
                }
                if (savedUri != null) {
                    _state.update {
                        it.copy(
                            exportState = ExportState.Succeeded(output.displayName),
                            pendingExportOutput = null
                        )
                    }
                } else {
                    _state.update {
                        it.copy(
                            exportState = ExportState.Idle,
                            pendingExportOutput = output
                        )
                    }
                    onRequestSaf(output)
                }
            }
        } else {
            _state.update {
                it.copy(
                    exportState = ExportState.Idle,
                    pendingExportOutput = output
                )
            }
            onRequestSaf(output)
        }
    }

    fun completeSafExport(destUri: Uri?) {
        val output = _state.value.pendingExportOutput
        if (destUri == null || output == null) {
            _state.update { it.copy(pendingExportOutput = null) }
            return
        }
        if (!output.file.exists()) {
            _state.update {
                it.copy(
                    exportState = ExportState.Failed(output.displayName),
                    pendingExportOutput = null
                )
            }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(exportState = ExportState.Exporting(output.displayName)) }
            val ok = withContext(Dispatchers.IO) {
                container.storage.copyFileToUri(output.file, destUri)
            }
            _state.update {
                it.copy(
                    exportState = if (ok) ExportState.Succeeded(output.displayName) else ExportState.Failed(output.displayName),
                    pendingExportOutput = null
                )
            }
        }
    }

    fun clearExportState() {
        _state.update { it.copy(exportState = ExportState.Idle) }
    }

    private fun seedParamsForTarget(
        target: ConversionTargetOption?,
        existing: Map<String, Any>,
        previousToolId: String? = null
    ): Map<String, Any> {
        if (target == null) return emptyMap()
        val tool = ToolRegistry.findById(target.toolId) ?: return target.defaultParams + target.fixedParams
        val sameTool = previousToolId == target.toolId
        val updated = mutableMapOf<String, Any>()
        tool.params.forEach { param ->
            val preserved = if (sameTool) existing[param.key] else null
            updated[param.key] = preserved
                ?: target.defaultParams[param.key]
                ?: when (param) {
                    is ToolParam.SliderParam -> param.defaultValue
                    is ToolParam.DropdownParam -> param.defaultOption
                    is ToolParam.ToggleParam -> param.defaultValue
                    is ToolParam.TextParam -> param.defaultValue
                }
        }
        updated.putAll(target.fixedParams)
        return updated
    }

    class Factory(
        private val container: AppContainer,
        private val initialToolId: String? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            UniversalConverterViewModel(container, initialToolId) as T
    }
}
