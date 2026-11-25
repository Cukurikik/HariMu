package com.harimu.app.feature.studios

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.harimu.app.AppContainer
import com.harimu.app.R
import com.harimu.app.core.data.AudioTrackSpec
import com.harimu.app.core.design.ErrorState
import com.harimu.app.core.design.ProgressSheet
import com.harimu.app.core.design.ResultCard
import com.harimu.app.core.toolapi.ToolProgress
import kotlinx.coroutines.delay
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioStudioScreen(
    container: AppContainer,
    isEnglish: Boolean,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val vm: AudioStudioViewModel = viewModel(factory = AudioStudioViewModel.Factory(container))
    val state by vm.state.collectAsState()
    val savedProjects by vm.savedProjects.collectAsState()

    var showProjectsDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showMixerPanel by remember { mutableStateOf(true) }
    var showAddTrackDialog by remember { mutableStateOf(false) }
    var newTrackName by remember { mutableStateOf("") }
    var exportFormat by remember { mutableStateOf("wav") }

    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty() && state.selectedTrackId != null) {
            vm.addClipsToTrack(context, state.selectedTrackId!!, uris)
        }
    }

    LaunchedEffect(Unit) {
        vm.initializeDefaultTitle(if (isEnglish) "Audio Studio Project" else "Proyek Audio Studio")
    }

    // Playhead ticker
    LaunchedEffect(state.isPlaying) {
        if (state.isPlaying) {
            val startTime = System.currentTimeMillis() - state.playheadPositionMs
            while (state.isPlaying) {
                val current = System.currentTimeMillis() - startTime
                if (current >= state.totalDurationMs) {
                    vm.seekPlayhead(0L)
                    vm.togglePlay()
                    break
                }
                vm.seekPlayhead(current)
                delay(50L)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column(modifier = Modifier.clickable { showProjectsDialog = true }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(state.title, fontWeight = FontWeight.Bold, maxLines = 1)
                            Icon(
                                Icons.Filled.ArrowDropDown,
                                contentDescription = stringResource(R.string.cd_switch_audio_project),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Text(
                            text = if (isEnglish) "${state.tracks.size} Tracks • Tap to switch" else "${state.tracks.size} Track • Ketuk untuk beralih",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = if (isEnglish) "Back" else "Kembali")
                    }
                },
                actions = {
                    IconButton(onClick = { showProjectsDialog = true }) {
                        Icon(Icons.Filled.FolderOpen, contentDescription = if (isEnglish) "Open Project" else "Buka Proyek")
                    }
                    IconButton(onClick = { showExportDialog = true }) {
                        Icon(Icons.Filled.SaveAlt, contentDescription = if (isEnglish) "Export" else "Ekspor")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 8.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { vm.seekPlayhead(0L) }) {
                                Icon(Icons.Filled.FirstPage, contentDescription = "Rewind")
                            }
                            FilledIconButton(
                                onClick = { vm.togglePlay() },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    contentDescription = if (state.isPlaying) "Pause" else "Play"
                                )
                            }
                            IconButton(
                                onClick = { showMixerPanel = !showMixerPanel }
                            ) {
                                Icon(Icons.Filled.Tune, contentDescription = "Mixer")
                            }
                        }

                        Text(
                            text = formatTimeMs(state.playheadPositionMs) + " / " + formatTimeMs(state.totalDurationMs),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.width(140.dp)) {
                            Icon(Icons.Filled.VolumeUp, contentDescription = null, modifier = Modifier.size(18.dp))
                            Slider(
                                value = state.masterVolume,
                                onValueChange = { vm.setMasterVolume(it) },
                                valueRange = 0f..2f,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (showMixerPanel) {
                Surface(
                    modifier = Modifier.width(280.dp).fillMaxHeight(),
                    tonalElevation = 4.dp,
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(if (isEnglish) "Tracks Mixer" else "Mixer Track", fontWeight = FontWeight.Bold)
                            IconButton(onClick = { showAddTrackDialog = true }) {
                                Icon(Icons.Filled.Add, contentDescription = "Add Track")
                            }
                        }
                        Divider(modifier = Modifier.padding(vertical = 8.dp))

                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(state.tracks) { track ->
                                TrackMixerCard(
                                    track = track,
                                    isSelected = track.trackId == state.selectedTrackId,
                                    isEnglish = isEnglish,
                                    onSelect = { vm.selectTrack(track.trackId) },
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

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (isEnglish) "Timeline" else "Linimasa", fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.width(12.dp))
                        IconButton(onClick = { vm.setZoomLevel(state.zoomLevel - 0.25f) }) {
                            Icon(Icons.Filled.ZoomOut, contentDescription = "Zoom Out")
                        }
                        Text(String.format(Locale.US, "%.1fx", state.zoomLevel), style = MaterialTheme.typography.bodySmall)
                        IconButton(onClick = { vm.setZoomLevel(state.zoomLevel + 0.25f) }) {
                            Icon(Icons.Filled.ZoomIn, contentDescription = "Zoom In")
                        }
                    }

                    Button(
                        onClick = {
                            state.selectedTrackId?.let { trackId ->
                                audioPickerLauncher.launch(arrayOf("audio/*"))
                            } ?: run {
                                state.tracks.firstOrNull()?.trackId?.let {
                                    vm.selectTrack(it)
                                    audioPickerLauncher.launch(arrayOf("audio/*"))
                                }
                            }
                        },
                        modifier = Modifier.height(36.dp)
                    ) {
                        Icon(Icons.Filled.AudioFile, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isEnglish) "+ Add Audio" else "+ Tambah Audio")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                val horizontalScrollState = rememberScrollState()
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .horizontalScroll(horizontalScrollState)
                            .padding(vertical = 12.dp)
                    ) {
                        state.tracks.forEach { track ->
                            TrackTimelineRow(
                                track = track,
                                isSelected = track.trackId == state.selectedTrackId,
                                selectedClipId = state.selectedClipId,
                                zoomLevel = state.zoomLevel,
                                totalDurationMs = state.totalDurationMs,
                                onSelectTrack = { vm.selectTrack(track.trackId) },
                                onSelectClip = { clipId -> vm.selectClip(clipId) },
                                onDeleteClip = { clipId -> vm.deleteClip(track.trackId, clipId) },
                                onUpdateOffset = { clipId, newOffset -> vm.updateClipOffset(track.trackId, clipId, newOffset) }
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }

                    val timelineWidthPx = (state.totalDurationMs * 0.15f * state.zoomLevel).coerceAtLeast(600f)
                    val playheadRatio = if (state.totalDurationMs > 0L) (state.playheadPositionMs.toFloat() / state.totalDurationMs.toFloat()) else 0f
                    val playheadX = (timelineWidthPx * playheadRatio) - horizontalScrollState.value

                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(2.dp)
                            .offset(x = playheadX.dp)
                            .background(MaterialTheme.colorScheme.error)
                    )
                }
            }
        }
    }

    if (showProjectsDialog) {
        AlertDialog(
            onDismissRequest = { showProjectsDialog = false },
            title = { Text(if (isEnglish) "Audio Projects" else "Proyek Audio") },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                    item {
                        OutlinedButton(
                            onClick = {
                                vm.newProject(if (isEnglish) "Audio Project ${System.currentTimeMillis() % 1000}" else "Proyek Baru")
                                showProjectsDialog = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (isEnglish) "Create New Project" else "Buat Proyek Baru")
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    items(savedProjects) { proj ->
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
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(proj.title, fontWeight = FontWeight.Bold)
                                    Text(
                                        text = "ID: ${proj.id.take(6)} • Updated: ${android.text.format.DateFormat.format("yyyy-MM-dd HH:mm", proj.updatedAt)}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                IconButton(onClick = { vm.deleteSavedProject(proj.id) }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
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

    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text(if (isEnglish) "Export Audio Project" else "Ekspor Proyek Audio") },
            text = {
                Column {
                    Text(if (isEnglish) "Select output audio format:" else "Pilih format audio keluaran:")
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("wav", "mp3", "flac").forEach { fmt ->
                            FilterChip(
                                selected = exportFormat == fmt,
                                onClick = { exportFormat = fmt },
                                label = { Text(fmt.uppercase(Locale.US)) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    showExportDialog = false
                    vm.exportProject(context, exportFormat, isEnglish)
                }) {
                    Text(if (isEnglish) "Start Export" else "Mulai Ekspor")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExportDialog = false }) {
                    Text(if (isEnglish) "Cancel" else "Batal")
                }
            }
        )
    }

    // Progress Sheet during export
    if (state.isExporting || state.progress != null) {
        val prog = state.progress ?: ToolProgress.Queued
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f))
                .wrapContentSize(Alignment.Center)
                .padding(24.dp)
        ) {
            when (prog) {
                is ToolProgress.Queued -> ProgressSheet(
                    percent = null,
                    message = if (isEnglish) "Queued..." else "Dalam antrean...",
                    onCancel = {}
                )
                is ToolProgress.Running -> ProgressSheet(
                    percent = prog.percent,
                    message = prog.message,
                    onCancel = {}
                )
                is ToolProgress.Failed -> ErrorState(
                    message = prog.error,
                    onRetry = { vm.exportProject(context, exportFormat, isEnglish) }
                )
                is ToolProgress.Done -> {
                    val firstOut = prog.result.outputs.firstOrNull()
                    ResultCard(
                        summary = prog.result.summary,
                        onOpen = firstOut?.let { out ->
                            {
                                runCatching {
                                    context.startActivity(
                                        android.content.Intent.createChooser(
                                            container.storage.buildOpenIntent(out.file, out.mimeType),
                                            out.displayName
                                        )
                                    )
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun TrackMixerCard(
    track: AudioTrackSpec,
    isSelected: Boolean,
    isEnglish: Boolean,
    onSelect: () -> Unit,
    onUpdate: (volume: Float, pan: Float, mute: Boolean, solo: Boolean) -> Unit,
    onAddClip: () -> Unit,
    onDeleteTrack: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        ),
        border = if (isSelected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(track.name, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = onAddClip, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Filled.Add, contentDescription = "Add", modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = onDeleteTrack, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = track.mute,
                    onClick = { onUpdate(track.volume, track.pan, !track.mute, track.solo) },
                    label = { Text("M", fontSize = 11.sp) },
                    modifier = Modifier.height(28.dp)
                )
                FilterChip(
                    selected = track.solo,
                    onClick = { onUpdate(track.volume, track.pan, track.mute, !track.solo) },
                    label = { Text("S", fontSize = 11.sp) },
                    modifier = Modifier.height(28.dp)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text("Vol: ${String.format(Locale.US, "%.1f", track.volume)}x", style = MaterialTheme.typography.bodySmall)
            Slider(
                value = track.volume,
                onValueChange = { onUpdate(it, track.pan, track.mute, track.solo) },
                valueRange = 0f..2f,
                modifier = Modifier.height(24.dp)
            )

            Text("Pan: ${String.format(Locale.US, "%.1f", track.pan)}", style = MaterialTheme.typography.bodySmall)
            Slider(
                value = track.pan,
                onValueChange = { onUpdate(track.volume, it, track.mute, track.solo) },
                valueRange = -1f..1f,
                modifier = Modifier.height(24.dp)
            )
        }
    }
}

@Composable
fun TrackTimelineRow(
    track: AudioTrackSpec,
    isSelected: Boolean,
    selectedClipId: String?,
    zoomLevel: Float,
    totalDurationMs: Long,
    onSelectTrack: () -> Unit,
    onSelectClip: (String) -> Unit,
    onDeleteClip: (String) -> Unit,
    onUpdateOffset: (String, Long) -> Unit
) {
    val trackWidth = (totalDurationMs.toFloat() * 0.15f * zoomLevel).coerceAtLeast(800.dp.value).dp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .clickable(onClick = onSelectTrack)
            .background(if (isSelected) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) else Color.Transparent)
            .border(width = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(100.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(8.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(track.name, fontWeight = FontWeight.Bold, maxLines = 1, style = MaterialTheme.typography.bodyMedium)
        }

        Box(
            modifier = Modifier
                .width(trackWidth)
                .fillMaxHeight()
                .padding(horizontal = 4.dp)
        ) {
            track.clips.forEach { clip ->
                val clipStartPx = (clip.offsetMs.toFloat() * 0.15f * zoomLevel)
                val clipWidthPx = ((clip.trimEndMs - clip.trimStartMs).toFloat() * 0.15f * zoomLevel).coerceAtLeast(40f)

                Box(
                    modifier = Modifier
                        .offset(x = clipStartPx.dp)
                        .width(clipWidthPx.dp)
                        .fillMaxHeight(0.85f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (clip.id == selectedClipId) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiaryContainer)
                        .clickable { onSelectClip(clip.id) }
                        .padding(6.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Column {
                        Text(
                            text = clip.displayName,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = if (clip.id == selectedClipId) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onTertiaryContainer,
                            maxLines = 1
                        )
                        Text(
                            text = "${formatTimeMs(clip.trimEndMs - clip.trimStartMs)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (clip.id == selectedClipId) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }
    }
}

private fun formatTimeMs(ms: Long): String {
    val sec = ms / 1000L
    val m = sec / 60L
    val s = sec % 60L
    val millis = (ms % 1000L) / 100L
    return String.format(Locale.US, "%02d:%02d.%d", m, s, millis)
}
