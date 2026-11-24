package com.harimu.app.feature.studios

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.harimu.app.AppContainer
import com.harimu.app.core.data.EditorClipSpec
import com.harimu.app.core.data.VideoProjectEntity
import com.harimu.app.core.media.MediaProbe
import com.harimu.app.core.media.VideoEditorEngine
import com.harimu.app.core.media.VideoEditorMath
import com.harimu.app.core.toolapi.ToolProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

data class VideoEditorUiState(
    val projectId: String = UUID.randomUUID().toString(),
    val title: String = "Proyek Baru",
    val clips: List<EditorClipSpec> = emptyList(),
    val selectedClipId: String? = null,
    val outputResolution: String = "720p",
    val createdAt: Long = System.currentTimeMillis(),
    val isExporting: Boolean = false,
    val progress: ToolProgress? = null,
    val lastSavedAt: Long? = null
) {
    val selectedClip: EditorClipSpec?
        get() = clips.firstOrNull { it.id == selectedClipId } ?: clips.firstOrNull()

    val totalDurationMs: Long
        get() = VideoEditorMath.totalTimelineDurationMs(clips)
}

class VideoEditorViewModel(
    private val container: AppContainer,
    private val engine: VideoEditorEngine = VideoEditorEngine()
) : ViewModel() {

    private val _state = MutableStateFlow(VideoEditorUiState())
    val state: StateFlow<VideoEditorUiState> = _state.asStateFlow()

    val savedProjects: StateFlow<List<VideoProjectEntity>> = container.workspace.videoProjectsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var exportJob: Job? = null

    fun initializeDefaultTitle(defaultTitle: String) {
        _state.update { current ->
            if (current.clips.isEmpty() && current.title == "Proyek Baru") {
                current.copy(title = defaultTitle)
            } else {
                current
            }
        }
    }

    fun newProject(defaultTitle: String) {
        exportJob?.cancel()
        _state.value = VideoEditorUiState(
            projectId = UUID.randomUUID().toString(),
            title = defaultTitle,
            clips = emptyList(),
            selectedClipId = null,
            outputResolution = "720p"
        )
    }

    fun loadProject(entity: VideoProjectEntity) {
        exportJob?.cancel()
        val loadedClips = container.workspace.decodeEditorClips(entity.clipsJson)
        _state.value = VideoEditorUiState(
            projectId = entity.id,
            title = entity.title,
            clips = loadedClips,
            selectedClipId = loadedClips.firstOrNull()?.id,
            outputResolution = entity.outputResolution.ifBlank { "720p" },
            createdAt = entity.createdAt,
            lastSavedAt = entity.updatedAt
        )
    }

    fun deleteSavedProject(projectId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            container.workspace.deleteVideoProject(projectId)
        }
    }

    fun setTitle(title: String) {
        _state.update { it.copy(title = title) }
    }

    fun setResolution(resolution: String) {
        _state.update { it.copy(outputResolution = resolution) }
        persistCurrentProjectQuietly()
    }

    fun addClips(context: Context, uris: List<Uri>) {
        if (uris.isEmpty()) return
        val appCtx = context.applicationContext
        viewModelScope.launch {
            val newSpecs = withContext(Dispatchers.IO) {
                uris.mapIndexed { idx, uri ->
                    val probe = MediaProbe.probe(appCtx, uri)
                    val dur = probe.durationMs.coerceAtLeast(1_000L)
                    val name = container.storage.getDisplayName(uri) ?: "Clip_${idx + 1}.mp4"
                    EditorClipSpec(
                        id = UUID.randomUUID().toString(),
                        uriString = uri.toString(),
                        displayName = name,
                        durationMs = dur,
                        trimStartMs = 0L,
                        trimEndMs = dur,
                        speed = 1.0f,
                        rotationDeg = 0,
                        muteAudio = false
                    )
                }
            }
            _state.update { st ->
                val updated = st.clips + newSpecs
                st.copy(
                    clips = updated,
                    selectedClipId = st.selectedClipId ?: newSpecs.firstOrNull()?.id,
                    progress = null
                )
            }
            persistCurrentProjectQuietly()
        }
    }

    fun selectClip(clipId: String) {
        _state.update { it.copy(selectedClipId = clipId) }
    }

    fun moveClip(clipId: String, delta: Int) {
        _state.update { st ->
            val idx = st.clips.indexOfFirst { it.id == clipId }
            if (idx < 0) return@update st
            val targetIdx = (idx + delta).coerceIn(0, st.clips.lastIndex)
            if (targetIdx == idx) return@update st
            val mutable = st.clips.toMutableList()
            val item = mutable.removeAt(idx)
            mutable.add(targetIdx, item)
            st.copy(clips = mutable)
        }
        persistCurrentProjectQuietly()
    }

    fun duplicateClip(clipId: String) {
        _state.update { st ->
            val idx = st.clips.indexOfFirst { it.id == clipId }
            if (idx < 0) return@update st
            val copy = st.clips[idx].copy(id = UUID.randomUUID().toString())
            val mutable = st.clips.toMutableList()
            mutable.add(idx + 1, copy)
            st.copy(clips = mutable, selectedClipId = copy.id)
        }
        persistCurrentProjectQuietly()
    }

    fun removeClip(clipId: String) {
        _state.update { st ->
            val updated = st.clips.filterNot { it.id == clipId }
            val nextSelected = if (st.selectedClipId == clipId) updated.firstOrNull()?.id else st.selectedClipId
            st.copy(clips = updated, selectedClipId = nextSelected)
        }
        persistCurrentProjectQuietly()
    }

    fun updateSelectedClip(transform: (EditorClipSpec) -> EditorClipSpec) {
        _state.update { st ->
            val targetId = st.selectedClip?.id ?: return@update st
            val updated = st.clips.map { if (it.id == targetId) transform(it) else it }
            st.copy(clips = updated)
        }
    }

    fun saveProjectNow(onSaved: () -> Unit = {}) {
        val st = _state.value
        viewModelScope.launch(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            container.workspace.saveVideoProject(
                id = st.projectId,
                title = st.title.trim().ifBlank { "Untitled" },
                clips = st.clips,
                outputResolution = st.outputResolution,
                createdAt = st.createdAt
            )
            withContext(Dispatchers.Main) {
                _state.update { it.copy(lastSavedAt = now) }
                onSaved()
            }
        }
    }

    fun exportTimeline(context: Context, isEnglish: Boolean) {
        val st = _state.value
        if (st.clips.isEmpty()) return
        val appCtx = context.applicationContext
        exportJob?.cancel()
        exportJob = viewModelScope.launch {
            withContext(Dispatchers.IO) {
                container.workspace.saveVideoProject(
                    id = st.projectId,
                    title = st.title.trim().ifBlank { "Untitled" },
                    clips = st.clips,
                    outputResolution = st.outputResolution,
                    createdAt = st.createdAt
                )
            }
            _state.update { it.copy(isExporting = true, progress = ToolProgress.Queued) }

            engine.exportTimeline(
                context = appCtx,
                storage = container.storage,
                clips = st.clips,
                resolutionPreset = st.outputResolution,
                isEnglish = isEnglish
            ).catch { t ->
                _state.update {
                    it.copy(
                        isExporting = false,
                        progress = ToolProgress.Failed(t.localizedMessage ?: "Export error")
                    )
                }
            }.collect { prog ->
                when (prog) {
                    is ToolProgress.Done -> {
                        val outFile = prog.result.outputs.firstOrNull()?.file
                        withContext(Dispatchers.IO) {
                            container.workspace.saveVideoProject(
                                id = st.projectId,
                                title = st.title.trim().ifBlank { "Untitled" },
                                clips = st.clips,
                                outputResolution = st.outputResolution,
                                lastExportPath = outFile?.absolutePath.orEmpty(),
                                createdAt = st.createdAt
                            )
                            container.workspace.recordHistory(
                                toolId = "CORE-01",
                                toolTitle = "Pro Video Editor",
                                groupName = "CORE",
                                outputFile = outFile,
                                mimeType = "video/mp4",
                                summary = prog.result.summary,
                                success = true
                            )
                        }
                        _state.update { it.copy(isExporting = false, progress = prog, lastSavedAt = System.currentTimeMillis()) }
                    }
                    is ToolProgress.Failed -> _state.update { it.copy(isExporting = false, progress = prog) }
                    else -> _state.update { it.copy(progress = prog) }
                }
            }
        }
    }

    fun cancelExport() {
        exportJob?.cancel()
        _state.update { it.copy(isExporting = false, progress = null) }
    }

    private fun persistCurrentProjectQuietly() {
        val st = _state.value
        if (st.clips.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            container.workspace.saveVideoProject(
                id = st.projectId,
                title = st.title.trim().ifBlank { "Untitled" },
                clips = st.clips,
                outputResolution = st.outputResolution,
                createdAt = st.createdAt
            )
            withContext(Dispatchers.Main) {
                _state.update { it.copy(lastSavedAt = System.currentTimeMillis()) }
            }
        }
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            VideoEditorViewModel(container) as T
    }
}
