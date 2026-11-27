package com.harimu.app.feature.studios

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FirstPage
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.harimu.app.AppContainer
import com.harimu.app.R
import com.harimu.app.core.data.AppSettings
import com.harimu.app.core.data.AudioTrackSpec
import com.harimu.app.core.design.HariMuColors
import com.harimu.app.core.design.HariMuThemeTokens
import com.harimu.app.core.design.MonoTextStyle
import com.harimu.app.core.design.audio.AudioClipInspector
import com.harimu.app.core.design.audio.AudioPlayheadOverlay
import com.harimu.app.core.design.audio.AudioStudioExportDialog
import com.harimu.app.core.design.audio.AudioTimeRuler
import com.harimu.app.core.design.audio.AudioTimelineMath
import com.harimu.app.core.design.audio.AudioTimelineTrackRow
import com.harimu.app.core.toolapi.ToolOutputFile
import com.harimu.app.core.toolapi.ToolProgress
import com.harimu.app.feature.toolscreen.ToolPrefillData
import com.harimu.app.feature.toolscreen.uploadToCloud
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AudioStudioScreen(
    container: AppContainer,
    isEnglish: Boolean,
    onOpenTool: (String) -> Unit = {},
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val spacing = HariMuThemeTokens.spacing
    val scope = rememberCoroutineScope()
    val vm: AudioStudioViewModel = viewModel(factory = AudioStudioViewModel.Factory(container))
    val state by vm.state.collectAsStateWithLifecycle()
    val savedProjects by vm.savedProjects.collectAsStateWithLifecycle()
    val settings by container.settings.settingsFlow.collectAsStateWithLifecycle(initialValue = AppSettings())

    val playbackController = remember(context) { AudioStudioPlaybackController(context.applicationContext) }
    DisposableEffect(playbackController) {
        onDispose { playbackController.release() }
    }

    var showProjectsDialog by remember { mutableStateOf(false) }
    var showRenameProjectDialog by remember { mutableStateOf(false) }
    var renameProjectDraft by remember { mutableStateOf("") }
    var showExportDialog by remember { mutableStateOf(false) }
    var showMixerPanel by rememberSaveable { mutableStateOf(false) }
    var showAddTrackDialog by remember { mutableStateOf(false) }
    var newTrackName by remember { mutableStateOf("") }
    var renamingTrack by remember { mutableStateOf<AudioTrackSpec?>(null) }
    var renameTrackDraft by remember { mutableStateOf("") }
    var exportFormat by remember { mutableStateOf("wav") }
    var pendingSaveFile by remember { mutableStateOf<ToolOutputFile?>(null) }

    val savedToast = stringResource(R.string.toast_saved)
    val saveFailedToast = stringResource(R.string.toast_save_failed)

    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            val targetTrackId = state.selectedTrackId ?: state.tracks.firstOrNull()?.trackId
            if (targetTrackId != null) {
                vm.selectTrack(targetTrackId)
                vm.addClipsToTrack(context, targetTrackId, uris)
            }
        }
    }

    val safSaveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("audio/*")
    ) { destUri ->
        val src = pendingSaveFile
        if (destUri != null && src != null) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    container.storage.copyFileToUri(src.file, destUri)
                }
                Toast.makeText(context, if (ok) savedToast else saveFailedToast, Toast.LENGTH_SHORT).show()
            }
        }
    }

    LaunchedEffect(Unit) {
        vm.initializeDefaultTitle(if (isEnglish) "Audio Studio Project" else "Proyek Audio Studio")
    }

    LaunchedEffect(state.tracks) {
        vm.ensureClipWaveforms(context)
    }

    LaunchedEffect(state.isPlaying, state.playheadPositionMs, state.tracks, state.masterVolume) {
        playbackController.syncState(
            tracks = state.tracks,
            masterVolume = state.masterVolume,
            playheadPositionMs = state.playheadPositionMs,
            isPlaying = state.isPlaying
        )
    }

    // Playhead ticker
    LaunchedEffect(state.isPlaying, state.totalDurationMs) {
        if (state.isPlaying) {
            val startTime = System.currentTimeMillis() - state.playheadPositionMs
            while (state.isPlaying) {
                val current = System.currentTimeMillis() - startTime
                if (current >= state.totalDurationMs) {
                    vm.seekPlayhead(0L)
                    vm.setPlaying(false)
                    playbackController.pauseAll()
                    playbackController.seekTo(state.tracks, 0L)
                    break
                }
                vm.seekPlayhead(current)
                delay(50L)
            }
        }
    }

    val selectedTrackAndClip = remember(state.tracks, state.selectedTrackId, state.selectedClipId) {
        val clipTrack = state.selectedClipId?.let { cid ->
            state.tracks.firstOrNull { tr -> tr.clips.any { it.id == cid } }
        }
        val clip = clipTrack?.clips?.firstOrNull { it.id == state.selectedClipId }
        val track = clipTrack ?: state.tracks.firstOrNull { it.trackId == state.selectedTrackId }
        track to clip
    }
    val hasAnySolo = remember(state.tracks) { state.tracks.any { it.solo && !it.mute } }
    val hasAnyClips = remember(state.tracks) { state.tracks.any { it.clips.isNotEmpty() } }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Column(
                        modifier = Modifier
                            .clickable { showProjectsDialog = true }
                            .testTag("audio_studio_project_switcher")
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = state.title,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Icon(
                                imageVector = Icons.Filled.ArrowDropDown,
                                contentDescription = stringResource(R.string.cd_switch_audio_project),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        val totalClipCount = remember(state.tracks) { state.tracks.sumOf { it.clips.size } }
                        Text(
                            text = stringResource(
                                R.string.audio_studio_tracks_meta,
                                state.tracks.size,
                                totalClipCount
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("audio_studio_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            renameProjectDraft = state.title
                            showRenameProjectDialog = true
                        },
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = stringResource(R.string.audio_studio_rename_project)
                        )
                    }
                    IconButton(
                        onClick = { showProjectsDialog = true },
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            imageVector = Icons.Filled.FolderOpen,
                            contentDescription = if (isEnglish) "Open Project" else "Buka Proyek"
                        )
                    }
                    IconButton(
                        onClick = { showExportDialog = true },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("audio_studio_export_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SaveAlt,
                            contentDescription = if (isEnglish) "Export" else "Ekspor",
                            tint = HariMuColors.GroupAudio
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 8.dp,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = spacing.sm, vertical = spacing.xs),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalArrangement = Arrangement.spacedBy(spacing.xxs, Alignment.CenterVertically)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                vm.seekPlayhead(0L)
                                playbackController.seekTo(state.tracks, 0L)
                            },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(
                                imageVector = Icons.Filled.FirstPage,
                                contentDescription = if (isEnglish) "Rewind to start" else "Kembali ke awal"
                            )
                        }
                        FilledIconButton(
                            onClick = { vm.togglePlay() },
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = HariMuColors.GroupAudio,
                                contentColor = Color.White
                            ),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("audio_studio_play_pause_btn")
                        ) {
                            Icon(
                                imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = stringResource(R.string.audio_cd_play_pause)
                            )
                        }
                        IconButton(
                            onClick = { showMixerPanel = !showMixerPanel },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("audio_studio_mixer_toggle")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Tune,
                                contentDescription = stringResource(R.string.cd_mixer),
                                tint = if (showMixerPanel) HariMuColors.GroupAudio else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Text(
                        text = "${AudioTimelineMath.formatDurationMs(state.playheadPositionMs)} / ${AudioTimelineMath.formatDurationMs(state.totalDurationMs)}",
                        style = MonoTextStyle.copy(fontWeight = FontWeight.SemiBold),
                        color = HariMuColors.GroupAudio,
                        modifier = Modifier.align(Alignment.CenterVertically)
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.widthIn(min = 120.dp, max = 168.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = if (isEnglish) "Master volume" else "Volume utama",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Slider(
                            value = state.masterVolume,
                            onValueChange = { vm.setMasterVolume(it) },
                            valueRange = 0f..2f,
                            colors = SliderDefaults.colors(
                                thumbColor = HariMuColors.GroupAudio,
                                activeTrackColor = HariMuColors.GroupAudio
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    ) { padding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            val isWide = maxWidth >= 600.dp
            Row(modifier = Modifier.fillMaxSize()) {
                if (showMixerPanel) {
                    Surface(
                        modifier = Modifier
                            .then(if (isWide) Modifier.width(300.dp) else Modifier.fillMaxWidth())
                            .fillMaxHeight()
                            .testTag("audio_studio_mixer_panel"),
                        tonalElevation = 3.dp,
                        color = MaterialTheme.colorScheme.surfaceContainerLow
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(spacing.sm)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (isEnglish) "Tracks Mixer (${state.tracks.size}/8)" else "Mixer Track (${state.tracks.size}/8)",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                IconButton(
                                    onClick = { showAddTrackDialog = true },
                                    enabled = state.tracks.size < 8,
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("audio_studio_add_track_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Add,
                                        contentDescription = if (isEnglish) "Add Track" else "Tambah Track"
                                    )
                                }
                            }
                            HorizontalDivider(modifier = Modifier.padding(vertical = spacing.xs))

                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(spacing.xs)
                            ) {
                                items(state.tracks.size, key = { idx -> state.tracks[idx].trackId }) { idx ->
                                    val track = state.tracks[idx]
                                    val trackAccent = AudioTimelineMath.accentForTrack(idx)
                                    TrackMixerCard(
                                        track = track,
                                        trackColor = trackAccent,
                                        isSelected = track.trackId == state.selectedTrackId,
                                        canDelete = state.tracks.size > 1,
                                        isEnglish = isEnglish,
                                        onSelect = { vm.selectTrack(track.trackId) },
                                        onRenameTrack = {
                                            renamingTrack = track
                                            renameTrackDraft = track.name
                                        },
                                        onUpdate = { vol, pan, mute, solo ->
                                            vm.updateTrackSettings(track.trackId, vol, pan, mute, solo)
                                        },
                                        onAddClip = {
                                            vm.selectTrack(track.trackId)
                                            audioPickerLauncher.launch(arrayOf("audio/*"))
                                        },
                                        onDeleteTrack = { vm.deleteTrack(track.trackId) }
                                    )
                                }
                            }
                        }
                    }
                }

                if (isWide || !showMixerPanel) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.background)
                            .verticalScroll(rememberScrollState())
                            .padding(spacing.sm)
                            .testTag("audio_studio_timeline_pane"),
                        verticalArrangement = Arrangement.spacedBy(spacing.sm)
                    ) {
                        // Timeline toolbar
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalArrangement = Arrangement.spacedBy(spacing.xxs, Alignment.CenterVertically)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (isEnglish) "Timeline" else "Linimasa",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(spacing.xs))
                                IconButton(
                                    onClick = { vm.setZoomLevel(state.zoomLevel - 0.25f) },
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.ZoomOut,
                                        contentDescription = if (isEnglish) "Zoom Out" else "Perkecil"
                                    )
                                }
                                Text(
                                    text = String.format(Locale.US, "%.2fx", state.zoomLevel),
                                    style = MonoTextStyle
                                )
                                IconButton(
                                    onClick = { vm.setZoomLevel(state.zoomLevel + 0.25f) },
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.ZoomIn,
                                        contentDescription = if (isEnglish) "Zoom In" else "Perbesar"
                                    )
                                }
                            }

                            Button(
                                onClick = {
                                    val targetTrackId = state.selectedTrackId ?: state.tracks.firstOrNull()?.trackId
                                    if (targetTrackId != null) {
                                        vm.selectTrack(targetTrackId)
                                        audioPickerLauncher.launch(arrayOf("audio/*"))
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = HariMuColors.GroupAudio),
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("audio_studio_add_audio_btn")
                            ) {
                                Icon(Icons.Filled.AudioFile, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isEnglish) "Add Audio" else "Tambah Audio",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // Live preview scope note (explicit & honest about pan rendered on export)
                        Text(
                            text = stringResource(R.string.audio_studio_preview_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // Empty state banner when no clips exist yet
                        if (!hasAnyClips) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("audio_studio_empty_card"),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                                border = BorderStroke(1.dp, HariMuColors.GroupAudio.copy(alpha = 0.35f))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.GraphicEq,
                                        contentDescription = null,
                                        tint = HariMuColors.GroupAudio,
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(
                                            text = stringResource(R.string.audio_studio_tracks_empty_title),
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = stringResource(R.string.audio_studio_tracks_empty_subtitle),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        // Synchronized scrollable multi-track timeline + ruler + playhead
                        val horizontalScrollState = rememberScrollState()
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(horizontalScrollState)
                            ) {
                                AudioTimeRuler(
                                    totalDurationMs = state.totalDurationMs,
                                    zoomLevel = state.zoomLevel,
                                    playheadPositionMs = state.playheadPositionMs,
                                    onSeekMs = { targetMs ->
                                        vm.seekPlayhead(targetMs)
                                        playbackController.seekTo(state.tracks, targetMs)
                                    }
                                )
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                state.tracks.forEachIndexed { idx, track ->
                                    AudioTimelineTrackRow(
                                        track = track,
                                        trackColor = AudioTimelineMath.accentForTrack(idx),
                                        isSelected = track.trackId == state.selectedTrackId,
                                        hasAnySolo = hasAnySolo,
                                        selectedClipId = state.selectedClipId,
                                        zoomLevel = state.zoomLevel,
                                        totalDurationMs = state.totalDurationMs,
                                        clipWaveforms = state.clipWaveforms,
                                        onSelectTrack = { vm.selectTrack(track.trackId) },
                                        onSelectClip = { clipId -> vm.selectClip(clipId) }
                                    )
                                }
                            }

                            AudioPlayheadOverlay(
                                playheadPositionMs = state.playheadPositionMs,
                                zoomLevel = state.zoomLevel,
                                horizontalScrollPx = horizontalScrollState.value,
                                modifier = Modifier.matchParentSize()
                            )
                        }

                        // Inline Audio Clip Inspector + Open in Tool handoffs
                        AudioClipInspector(
                            track = selectedTrackAndClip.first,
                            clip = selectedTrackAndClip.second,
                            isEnglish = isEnglish,
                            onUpdateTrim = vm::updateClipTrim,
                            onUpdateOffset = vm::updateClipOffset,
                            onUpdateGain = vm::updateClipGain,
                            onDeleteClip = vm::deleteClip,
                            onOpenInTool = { toolId, clip, params ->
                                ToolPrefillData.setPrefill(clip.uriString.toUri(), params)
                                onOpenTool(toolId)
                            }
                        )
                    }
                }
            }
        }
    }

    if (showProjectsDialog) {
        AlertDialog(
            onDismissRequest = { showProjectsDialog = false },
            title = { Text(if (isEnglish) "Audio Projects" else "Proyek Audio") },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                    item {
                        OutlinedButton(
                            onClick = {
                                vm.newProject(if (isEnglish) "Audio Project" else "Proyek Audio")
                                showProjectsDialog = false
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (isEnglish) "Create New Project" else "Buat Proyek Baru")
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    items(savedProjects, key = { it.id }) { proj ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable {
                                    vm.loadProject(proj)
                                    showProjectsDialog = false
                                },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(proj.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        text = "${proj.id.take(6)} • ${android.text.format.DateFormat.format("yyyy-MM-dd HH:mm", proj.updatedAt)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(
                                    onClick = { vm.deleteSavedProject(proj.id) },
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Delete,
                                        contentDescription = if (isEnglish) "Delete project" else "Hapus proyek",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showProjectsDialog = false }) {
                    Text(if (isEnglish) "Close" else "Tutup")
                }
            }
        )
    }

    if (showRenameProjectDialog) {
        AlertDialog(
            onDismissRequest = { showRenameProjectDialog = false },
            title = { Text(stringResource(R.string.audio_studio_rename_project)) },
            text = {
                OutlinedTextField(
                    value = renameProjectDraft,
                    onValueChange = { renameProjectDraft = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (renameProjectDraft.isNotBlank()) {
                        vm.setTitle(renameProjectDraft.trim())
                    }
                    showRenameProjectDialog = false
                }) {
                    Text(if (isEnglish) "Save" else "Simpan")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameProjectDialog = false }) {
                    Text(if (isEnglish) "Cancel" else "Batal")
                }
            }
        )
    }

    if (showAddTrackDialog) {
        AlertDialog(
            onDismissRequest = { showAddTrackDialog = false },
            title = { Text(if (isEnglish) "Add New Track" else "Tambah Track Baru") },
            text = {
                OutlinedTextField(
                    value = newTrackName,
                    onValueChange = { newTrackName = it },
                    label = { Text(if (isEnglish) "Track Name" else "Nama Track") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    vm.addTrack(newTrackName)
                    newTrackName = ""
                    showAddTrackDialog = false
                }) {
                    Text(if (isEnglish) "Add" else "Tambah")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddTrackDialog = false }) {
                    Text(if (isEnglish) "Cancel" else "Batal")
                }
            }
        )
    }

    renamingTrack?.let { targetTrack ->
        AlertDialog(
            onDismissRequest = { renamingTrack = null },
            title = { Text(stringResource(R.string.audio_studio_rename_track)) },
            text = {
                OutlinedTextField(
                    value = renameTrackDraft,
                    onValueChange = { renameTrackDraft = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    vm.renameTrack(targetTrack.trackId, renameTrackDraft)
                    renamingTrack = null
                }) {
                    Text(if (isEnglish) "Save" else "Simpan")
                }
            },
            dismissButton = {
                TextButton(onClick = { renamingTrack = null }) {
                    Text(if (isEnglish) "Cancel" else "Batal")
                }
            }
        )
    }

    val doneOutput = (state.progress as? ToolProgress.Done)?.result?.outputs?.firstOrNull()
    AudioStudioExportDialog(
        isOpen = showExportDialog,
        tracks = state.tracks,
        initialFormat = exportFormat,
        progress = state.progress,
        onExport = { fmt ->
            exportFormat = fmt
            playbackController.pauseAll()
            vm.setPlaying(false)
            vm.exportProject(context, fmt, isEnglish)
        },
        onCancelExport = {
            vm.cancelExport()
            showExportDialog = false
        },
        onDismiss = {
            showExportDialog = false
            vm.dismissExportProgress()
        },
        onOpenResult = doneOutput?.let { out ->
            {
                runCatching {
                    context.startActivity(
                        Intent.createChooser(
                            container.storage.buildOpenIntent(out.file, out.mimeType),
                            out.displayName
                        )
                    )
                }
            }
        },
        onShareResult = doneOutput?.let { out ->
            {
                context.startActivity(
                    Intent.createChooser(
                        container.storage.buildShareIntent(out.file, out.mimeType),
                        out.displayName
                    )
                )
            }
        },
        onSaveResult = doneOutput?.let { out ->
            {
                val defaultTree = settings.outputFolderUri
                if (defaultTree.isNotBlank()) {
                    scope.launch(Dispatchers.IO) {
                        val saved = container.storage.saveToTreeUri(out.file, defaultTree.toUri(), out.mimeType)
                        withContext(Dispatchers.Main) {
                            if (saved != null) {
                                Toast.makeText(context, savedToast, Toast.LENGTH_SHORT).show()
                            } else {
                                pendingSaveFile = out
                                safSaveLauncher.launch(out.displayName)
                            }
                        }
                    }
                } else {
                    pendingSaveFile = out
                    safSaveLauncher.launch(out.displayName)
                }
            }
        },
        onUploadResult = doneOutput?.let { out ->
            { uploadToCloud(out, container, settings, scope, context, isEnglish) }
        }
    )
}

@Composable
fun TrackMixerCard(
    track: AudioTrackSpec,
    trackColor: Color,
    isSelected: Boolean,
    canDelete: Boolean,
    isEnglish: Boolean,
    onSelect: () -> Unit,
    onRenameTrack: () -> Unit,
    onUpdate: (volume: Float, pan: Float, mute: Boolean, solo: Boolean) -> Unit,
    onAddClip: () -> Unit,
    onDeleteTrack: () -> Unit
) {
    val panCenterLabel = stringResource(R.string.audio_studio_pan_center)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) trackColor.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) trackColor else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .height(22.dp)
                            .background(trackColor, RoundedCornerShape(2.dp))
                    )
                    Text(
                        text = track.name,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    IconButton(
                        onClick = onRenameTrack,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = stringResource(R.string.audio_studio_rename_track),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    IconButton(
                        onClick = onAddClip,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = if (isEnglish) "Add clip" else "Tambah klip",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    if (canDelete) {
                        IconButton(
                            onClick = onDeleteTrack,
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = if (isEnglish) "Delete track" else "Hapus track",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = track.mute,
                        onClick = { onUpdate(track.volume, track.pan, !track.mute, track.solo) },
                        label = { Text("M", style = MaterialTheme.typography.labelMedium) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.2f),
                            selectedLabelColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier.minimumInteractiveComponentSize()
                    )
                    FilterChip(
                        selected = track.solo,
                        onClick = { onUpdate(track.volume, track.pan, track.mute, !track.solo) },
                        label = { Text("S", style = MaterialTheme.typography.labelMedium) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = HariMuColors.TealEmerald.copy(alpha = 0.2f),
                            selectedLabelColor = HariMuColors.TealEmerald
                        ),
                        modifier = Modifier.minimumInteractiveComponentSize()
                    )
                }
                Text(
                    text = stringResource(R.string.audio_studio_clip_count, track.clips.size),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = stringResource(R.string.audio_studio_vol_label, track.volume),
                style = MonoTextStyle,
                color = trackColor
            )
            Slider(
                value = track.volume,
                onValueChange = { onUpdate(it, track.pan, track.mute, track.solo) },
                valueRange = 0f..2f,
                colors = SliderDefaults.colors(thumbColor = trackColor, activeTrackColor = trackColor)
            )

            Text(
                text = stringResource(
                    R.string.audio_studio_pan_label,
                    AudioTimelineMath.formatPan(track.pan, panCenterLabel)
                ),
                style = MonoTextStyle
            )
            Slider(
                value = track.pan,
                onValueChange = { onUpdate(track.volume, it, track.mute, track.solo) },
                valueRange = -1f..1f
            )
        }
    }
}
