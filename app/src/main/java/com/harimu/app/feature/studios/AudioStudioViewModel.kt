package com.harimu.app.feature.studios

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.harimu.app.AppContainer
import com.harimu.app.core.data.AudioClipSpec
import com.harimu.app.core.data.AudioProjectEntity
import com.harimu.app.core.data.AudioTrackSpec
import com.harimu.app.core.media.MediaProbe
import com.harimu.app.core.media.AudioStudioEngine
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

data class AudioStudioUiState(
    val projectId: String = UUID.randomUUID().toString(),
    val title: String = "Proyek Audio Baru",
    val tracks: List<AudioTrackSpec> = listOf(
        AudioTrackSpec("tr_1", "Vokal", 1.0f, 0.0f, false, false, emptyList()),
        AudioTrackSpec("tr_2", "Instrument", 1.0f, 0.0f, false, false, emptyList()),
        AudioTrackSpec("tr_3", "Beat / Drum", 1.0f, 0.0f, false, false, emptyList()),
        AudioTrackSpec("tr_4", "FX / Lainnya", 1.0f, 0.0f, false, false, emptyList())
    ),
    val masterVolume: Float = 1.0f,
    val selectedTrackId: String? = "tr_1",
    val selectedClipId: String? = null,
    val zoomLevel: Float = 1.0f,
    val scrollPositionMs: Long = 0L,
    val playheadPositionMs: Long = 0L,
    val isPlaying: Boolean = false,
    val isExporting: Boolean = false,
    val progress: ToolProgress? = null,
    val lastSavedAt: Long? = null
) {
    val totalDurationMs: Long
        get() = tracks.maxOfOrNull { tr ->
            tr.clips.maxOfOrNull { clip -> clip.offsetMs + (clip.trimEndMs - clip.trimStartMs) } ?: 0L
        } ?: 5_000L
}

class AudioStudioViewModel(
    private val container: AppContainer
) : ViewModel() {

    private val _state = MutableStateFlow(AudioStudioUiState())
    val state: StateFlow<AudioStudioUiState> = _state.asStateFlow()

    val savedProjects: StateFlow<List<AudioProjectEntity>> = container.workspace.audioProjectsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var exportJob: Job? = null

    fun initializeDefaultTitle(defaultTitle: String) {
        _state.update { current ->
            if (current.tracks.all { it.clips.isEmpty() } && current.title == "Proyek Audio Baru") {
                current.copy(title = defaultTitle)
            } else {
                current
            }
        }
    }

    fun newProject(defaultTitle: String) {
        exportJob?.cancel()
        _state.value = AudioStudioUiState(
            projectId = UUID.randomUUID().toString(),
            title = defaultTitle
        )
    }

    fun loadProject(entity: AudioProjectEntity) {
        exportJob?.cancel()
        val loadedTracks = container.workspace.decodeAudioTracks(entity.tracksJson)
        _state.value = AudioStudioUiState(
            projectId = entity.id,
            title = entity.title,
            tracks = loadedTracks.ifEmpty { _state.value.tracks },
            masterVolume = entity.masterVolume,
            selectedTrackId = loadedTracks.firstOrNull()?.trackId ?: "tr_1",
            lastSavedAt = entity.updatedAt
        )
    }

    fun deleteSavedProject(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            container.workspace.deleteAudioProject(id)
        }
    }

    fun setTitle(title: String) {
        _state.update { it.copy(title = title) }
        persistQuietly()
    }

    fun setMasterVolume(vol: Float) {
        _state.update { it.copy(masterVolume = vol.coerceIn(0f, 2f)) }
        persistQuietly()
    }

    fun selectTrack(trackId: String) {
        _state.update { it.copy(selectedTrackId = trackId) }
    }

    fun selectClip(clipId: String?) {
        _state.update { it.copy(selectedClipId = clipId) }
    }

    fun setZoomLevel(zoom: Float) {
        _state.update { it.copy(zoomLevel = zoom.coerceIn(0.5f, 5.0f)) }
    }

    fun seekPlayhead(posMs: Long) {
        _state.update { it.copy(playheadPositionMs = posMs.coerceAtLeast(0L)) }
    }

    fun togglePlay() {
        _state.update { it.copy(isPlaying = !it.isPlaying) }
    }

    fun addTrack(name: String) {
        _state.update { st ->
            if (st.tracks.size >= 8) return@update st
            val newId = "tr_${UUID.randomUUID().toString().take(6)}"
            val newTrack = AudioTrackSpec(trackId = newId, name = name.ifBlank { "Track ${st.tracks.size + 1}" })
            st.copy(tracks = st.tracks + newTrack, selectedTrackId = newId)
        }
        persistQuietly()
    }

    fun deleteTrack(trackId: String) {
        _state.update { st ->
            if (st.tracks.size <= 1) return@update st
            val updated = st.tracks.filter { it.trackId != trackId }
            st.copy(
                tracks = updated,
                selectedTrackId = if (st.selectedTrackId == trackId) updated.firstOrNull()?.trackId else st.selectedTrackId
            )
        }
        persistQuietly()
    }

    fun updateTrackSettings(trackId: String, volume: Float, pan: Float, mute: Boolean, solo: Boolean) {
        _state.update { st ->
            val updated = st.tracks.map { tr ->
                if (tr.trackId == trackId) {
                    tr.copy(
                        volume = volume.coerceIn(0f, 2f),
                        pan = pan.coerceIn(-1f, 1f),
                        mute = mute,
                        solo = solo
                    )
                } else tr
            }
            st.copy(tracks = updated)
        }
        persistQuietly()
    }

    fun addClipsToTrack(context: Context, trackId: String, uris: List<Uri>) {
        if (uris.isEmpty()) return
        val appCtx = context.applicationContext
        viewModelScope.launch {
            val newClips = withContext(Dispatchers.IO) {
                uris.mapIndexed { idx, uri ->
                    val probe = MediaProbe.probe(appCtx, uri)
                    val dur = probe.durationMs.coerceAtLeast(1_000L)
                    val name = container.storage.getDisplayName(uri) ?: "Audio_${idx + 1}"
                    AudioClipSpec(
                        id = UUID.randomUUID().toString(),
                        uriString = uri.toString(),
                        displayName = name,
                        durationMs = dur,
                        trimStartMs = 0L,
                        trimEndMs = dur,
                        offsetMs = 0L,
                        gainDb = 0f
                    )
                }
            }

            _state.update { st ->
                val updatedTracks = st.tracks.map { tr ->
                    if (tr.trackId == trackId) {
                        val lastOffset = tr.clips.maxOfOrNull { it.offsetMs + (it.trimEndMs - it.trimStartMs) } ?: 0L
                        val adjusted = newClips.mapIndexed { index, clip ->
                            clip.copy(offsetMs = lastOffset + (if (index == 0 && lastOffset > 0L) 500L else 0L))
                        }
                        tr.copy(clips = tr.clips + adjusted)
                    } else tr
                }
                st.copy(tracks = updatedTracks, selectedClipId = newClips.firstOrNull()?.id)
            }
            persistQuietly()
        }
    }

    fun deleteClip(trackId: String, clipId: String) {
        _state.update { st ->
            val updatedTracks = st.tracks.map { tr ->
                if (tr.trackId == trackId) {
                    tr.copy(clips = tr.clips.filter { it.id != clipId })
                } else tr
            }
            st.copy(tracks = updatedTracks, selectedClipId = if (st.selectedClipId == clipId) null else st.selectedClipId)
        }
        persistQuietly()
    }

    fun updateClipTrim(trackId: String, clipId: String, startMs: Long, endMs: Long) {
        _state.update { st ->
            val updatedTracks = st.tracks.map { tr ->
                if (tr.trackId == trackId) {
                    val clips = tr.clips.map { clip ->
                        if (clip.id == clipId) {
                            val s = startMs.coerceAtLeast(0L)
                            val e = endMs.coerceAtLeast(s + 250L).coerceAtMost(clip.durationMs)
                            clip.copy(trimStartMs = s, trimEndMs = e)
                        } else clip
                    }
                    tr.copy(clips = clips)
                } else tr
            }
            st.copy(tracks = updatedTracks)
        }
        persistQuietly()
    }

    fun updateClipOffset(trackId: String, clipId: String, offsetMs: Long) {
        _state.update { st ->
            val updatedTracks = st.tracks.map { tr ->
                if (tr.trackId == trackId) {
                    val clips = tr.clips.map { clip ->
                        if (clip.id == clipId) clip.copy(offsetMs = offsetMs.coerceAtLeast(0L)) else clip
                    }
                    tr.copy(clips = clips)
                } else tr
            }
            st.copy(tracks = updatedTracks)
        }
        persistQuietly()
    }

    fun updateClipGain(trackId: String, clipId: String, gainDb: Float) {
        _state.update { st ->
            val updatedTracks = st.tracks.map { tr ->
                if (tr.trackId == trackId) {
                    val clips = tr.clips.map { clip ->
                        if (clip.id == clipId) clip.copy(gainDb = gainDb.coerceIn(-24f, 24f)) else clip
                    }
                    tr.copy(clips = clips)
                } else tr
            }
            st.copy(tracks = updatedTracks)
        }
        persistQuietly()
    }

    private fun persistQuietly() {
        val st = _state.value
        viewModelScope.launch(Dispatchers.IO) {
            container.workspace.saveAudioProject(
                id = st.projectId,
                title = st.title,
                tracks = st.tracks,
                masterVolume = st.masterVolume
            )
        }
    }

    fun exportProject(context: Context, format: String = "wav", isEnglish: Boolean = false) {
        exportJob?.cancel()
        val st = _state.value
        persistQuietly()

        exportJob = viewModelScope.launch {
            _state.update { it.copy(isExporting = true, progress = ToolProgress.Queued) }
            AudioStudioEngine.exportProject(
                context = context,
                storage = container.storage,
                tracks = st.tracks,
                masterVolume = st.masterVolume,
                outputFormat = format,
                isEnglish = isEnglish
            ).catch { e ->
                _state.update { it.copy(isExporting = false, progress = ToolProgress.Failed(e.localizedMessage ?: "Export failed")) }
            }.collect { progress ->
                _state.update { current ->
                    val finished = progress is ToolProgress.Done || progress is ToolProgress.Failed
                    if (progress is ToolProgress.Done) {
                        viewModelScope.launch(Dispatchers.IO) {
                            val res = progress.result
                            container.workspace.recordHistory(
                                toolId = "AUD-21",
                                toolTitle = "Audio Studio Pro ($format)",
                                groupName = "AUDIO",
                                outputFile = res.outputs.firstOrNull()?.file,
                                mimeType = res.outputs.firstOrNull()?.mimeType ?: "audio/wav",
                                summary = res.summary
                            )
                        }
                    }
                    current.copy(
                        isExporting = !finished,
                        progress = progress
                    )
                }
            }
        }
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AudioStudioViewModel(container) as T
        }
    }
}
