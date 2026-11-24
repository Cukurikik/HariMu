package com.harimu.app.feature.studios

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.ContentFrame
import com.harimu.app.AppContainer
import com.harimu.app.R
import com.harimu.app.core.data.AppSettings
import com.harimu.app.core.data.EditorClipSpec
import com.harimu.app.core.data.VideoProjectEntity
import com.harimu.app.core.design.EmptyState
import com.harimu.app.core.design.EngineBadge
import com.harimu.app.core.design.ErrorState
import com.harimu.app.core.design.HariMuColors
import com.harimu.app.core.design.MonoTextStyle
import com.harimu.app.core.design.ParamToggle
import com.harimu.app.core.design.ProgressSheet
import com.harimu.app.core.design.ResultCard
import com.harimu.app.core.media.FfmpegProgressParser
import com.harimu.app.core.media.VideoEditorMath
import com.harimu.app.core.toolapi.ToolOutputFile
import com.harimu.app.core.toolapi.ToolProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.roundToLong

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoEditorScreen(
    container: AppContainer,
    isEnglish: Boolean,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val vm: VideoEditorViewModel = viewModel(factory = VideoEditorViewModel.Factory(container))
    val state by vm.state.collectAsStateWithLifecycle()
    val savedProjects by vm.savedProjects.collectAsStateWithLifecycle()
    val settings by container.settings.settingsFlow.collectAsStateWithLifecycle(initialValue = AppSettings())

    val defaultTitle = stringResource(R.string.editor_default_project_title)
    val savedToast = stringResource(R.string.editor_project_saved_toast)
    val fileSavedToast = stringResource(R.string.toast_saved)
    val fileSaveFailedToast = stringResource(R.string.toast_save_failed)

    LaunchedEffect(defaultTitle) {
        vm.initializeDefaultTitle(defaultTitle)
    }

    var showProjectsDialog by remember { mutableStateOf(false) }
    var pendingExportFile by remember { mutableStateOf<ToolOutputFile?>(null) }

    val addClipsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(12)
    ) { uris ->
        if (uris.isNotEmpty()) vm.addClips(context, uris)
    }

    val safSaveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("video/mp4")) { destUri ->
        val src = pendingExportFile
        if (destUri != null && src != null) {
            val ok = container.storage.copyFileToUri(src.file, destUri)
            Toast.makeText(context, if (ok) fileSavedToast else fileSaveFailedToast, Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.editor_title), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("editor_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                actions = {
                    TextButton(
                        onClick = { showProjectsDialog = true },
                        modifier = Modifier.testTag("editor_projects_button")
                    ) {
                        Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.editor_projects_btn))
                    }
                    TextButton(
                        onClick = {
                            vm.saveProjectNow {
                                Toast.makeText(context, savedToast, Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.testTag("editor_save_button")
                    ) {
                        Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.editor_save_btn))
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 760.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                OutlinedTextField(
                    value = state.title,
                    onValueChange = vm::setTitle,
                    singleLine = true,
                    label = { Text(stringResource(R.string.editor_project_title_label)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("editor_title_input")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            addClipsLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("editor_add_clips_button")
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.editor_add_clips))
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.editor_resolution_label), style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("480p", "720p", "1080p").forEach { res ->
                            FilterChip(
                                selected = state.outputResolution == res,
                                onClick = { vm.setResolution(res) },
                                label = { Text(res) },
                                modifier = Modifier.testTag("editor_res_$res")
                            )
                        }
                    }
                }

                Text(
                    text = stringResource(
                        R.string.editor_timeline_header,
                        state.clips.size,
                        FfmpegProgressParser.formatDurationMs(state.totalDurationMs)
                    ),
                    style = MaterialTheme.typography.titleSmall
                )

                if (state.clips.isEmpty()) {
                    EmptyState(
                        icon = Icons.Filled.MovieCreation,
                        title = stringResource(R.string.editor_timeline_empty_title),
                        subtitle = stringResource(R.string.editor_timeline_empty_desc)
                    )
                } else {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(vertical = 2.dp)
                    ) {
                        itemsIndexed(state.clips, key = { _, item -> item.id }) { idx, clip ->
                            val isSelected = state.selectedClip?.id == clip.id
                            TimelineClipCard(
                                index = idx,
                                totalCount = state.clips.size,
                                clip = clip,
                                selected = isSelected,
                                onSelect = { vm.selectClip(clip.id) },
                                onMoveLeft = { vm.moveClip(clip.id, -1) },
                                onMoveRight = { vm.moveClip(clip.id, +1) },
                                onDuplicate = { vm.duplicateClip(clip.id) },
                                onRemove = { vm.removeClip(clip.id) }
                            )
                        }
                    }

                    state.selectedClip?.let { activeClip ->
                        val activeIndex = state.clips.indexOfFirst { it.id == activeClip.id }.coerceAtLeast(0)
                        SelectedClipInspector(
                            clipIndex = activeIndex,
                            clip = activeClip,
                            onUpdate = vm::updateSelectedClip
                        )
                    }

                    Button(
                        onClick = { vm.exportTimeline(context, isEnglish) },
                        enabled = !state.isExporting,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("editor_export_button")
                    ) {
                        Text(
                            text = stringResource(R.string.editor_export_button, state.clips.size),
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                }

                when (val prog = state.progress) {
                    is ToolProgress.Queued -> ProgressSheet(
                        percent = null,
                        message = stringResource(R.string.progress_queued),
                        onCancel = vm::cancelExport
                    )
                    is ToolProgress.Running -> ProgressSheet(
                        percent = prog.percent,
                        message = prog.message,
                        onCancel = vm::cancelExport
                    )
                    is ToolProgress.Failed -> ErrorState(
                        message = prog.error,
                        onRetry = { vm.exportTimeline(context, isEnglish) }
                    )
                    is ToolProgress.Done -> {
                        val firstOut = prog.result.outputs.firstOrNull()
                        ResultCard(
                            summary = prog.result.summary,
                            onOpen = firstOut?.let { out ->
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
                            onShare = firstOut?.let { out ->
                                {
                                    context.startActivity(
                                        Intent.createChooser(
                                            container.storage.buildShareIntent(out.file, out.mimeType),
                                            out.displayName
                                        )
                                    )
                                }
                            },
                            onSave = firstOut?.let { out ->
                                {
                                    val defaultTreeUri = settings.outputFolderUri
                                    if (defaultTreeUri.isNotBlank()) {
                                        scope.launch(Dispatchers.IO) {
                                            val savedUri = container.storage.saveToTreeUri(
                                                source = out.file,
                                                treeUri = defaultTreeUri.toUri(),
                                                mimeType = out.mimeType
                                            )
                                            withContext(Dispatchers.Main) {
                                                if (savedUri != null) {
                                                    Toast.makeText(context, fileSavedToast, Toast.LENGTH_SHORT).show()
                                                } else {
                                                    pendingExportFile = out
                                                    safSaveLauncher.launch(out.displayName)
                                                }
                                            }
                                        }
                                    } else {
                                        pendingExportFile = out
                                        safSaveLauncher.launch(out.displayName)
                                    }
                                }
                            }
                        )
                    }
                    null -> Unit
                }
            }
        }
    }

    if (showProjectsDialog) {
        SavedProjectsDialog(
            projects = savedProjects,
            decodeCount = { container.workspace.decodeEditorClips(it.clipsJson).size },
            onNewProject = {
                vm.newProject(defaultTitle)
                showProjectsDialog = false
            },
            onLoadProject = { project ->
                vm.loadProject(project)
                showProjectsDialog = false
            },
            onDeleteProject = { project ->
                vm.deleteSavedProject(project.id)
            },
            onDismiss = { showProjectsDialog = false }
        )
    }
}

@Composable
private fun TimelineClipCard(
    index: Int,
    totalCount: Int,
    clip: EditorClipSpec,
    selected: Boolean,
    onSelect: () -> Unit,
    onMoveLeft: () -> Unit,
    onMoveRight: () -> Unit,
    onDuplicate: () -> Unit,
    onRemove: () -> Unit
) {
    val effectiveMs = VideoEditorMath.effectiveClipDurationMs(clip)
    val borderColor = if (selected) HariMuColors.ElectricOrange else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
    Card(
        modifier = Modifier
            .width(224.dp)
            .clickable(onClick = onSelect)
            .testTag("editor_clip_$index"),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(if (selected) 2.dp else 1.dp, borderColor)
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.editor_clip_index, index + 1),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = HariMuColors.ElectricOrange
                )
                Text(
                    text = FfmpegProgressParser.formatDurationMs(effectiveMs),
                    style = MonoTextStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = clip.displayName,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                EngineBadge(engine = String.format(Locale.US, "%.2gx", clip.speed))
                if (clip.rotationDeg != 0) {
                    EngineBadge(engine = "${clip.rotationDeg}°")
                }
                if (clip.muteAudio) {
                    EngineBadge(engine = stringResource(R.string.editor_clip_muted))
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = onMoveLeft,
                    enabled = index > 0,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.editor_cd_move_left))
                }
                IconButton(
                    onClick = onMoveRight,
                    enabled = index < totalCount - 1,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.editor_cd_move_right))
                }
                IconButton(
                    onClick = onDuplicate,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = stringResource(R.string.editor_cd_duplicate), modifier = Modifier.size(18.dp))
                }
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.editor_cd_remove), tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun SelectedClipInspector(
    clipIndex: Int,
    clip: EditorClipSpec,
    onUpdate: ((EditorClipSpec) -> EditorClipSpec) -> Unit
) {
    val context = LocalContext.current
    val clipUri = remember(clip.uriString) { clip.uriString.toUri() }
    val exoPlayer = remember(clip.id, clip.uriString) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(clipUri))
            repeatMode = Player.REPEAT_MODE_ONE
            prepare()
        }
    }
    var isPlaying by remember(exoPlayer) { mutableStateOf(false) }
    var currentPosMs by remember(exoPlayer) { mutableLongStateOf(clip.trimStartMs) }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    LaunchedEffect(exoPlayer, clip.speed) {
        exoPlayer.playbackParameters = PlaybackParameters(clip.speed.coerceIn(0.25f, 4.0f))
    }

    LaunchedEffect(exoPlayer, isPlaying, clip.trimStartMs, clip.trimEndMs) {
        while (true) {
            currentPosMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            if (isPlaying && currentPosMs >= clip.trimEndMs && clip.trimEndMs > clip.trimStartMs) {
                exoPlayer.seekTo(clip.trimStartMs)
            }
            delay(150L)
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("editor_clip_inspector"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = stringResource(R.string.editor_inspector_title, clipIndex + 1, clip.displayName),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(185.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black)
            ) {
                ContentFrame(
                    player = exoPlayer,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
                FilledIconButton(
                    onClick = {
                        if (exoPlayer.isPlaying) {
                            exoPlayer.pause()
                        } else {
                            if (exoPlayer.currentPosition < clip.trimStartMs || exoPlayer.currentPosition >= clip.trimEndMs) {
                                exoPlayer.seekTo(clip.trimStartMs)
                            }
                            exoPlayer.play()
                        }
                    },
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = Color.Black.copy(alpha = 0.65f),
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                        .size(48.dp)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = stringResource(R.string.video_cd_play_pause)
                    )
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.65f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "${FfmpegProgressParser.formatDurationMs(currentPosMs)} / ${FfmpegProgressParser.formatDurationMs(clip.durationMs)}",
                        style = MonoTextStyle,
                        color = Color.White
                    )
                }
            }

            val maxSec = (clip.durationMs / 1000f).coerceAtLeast(1f)
            val startSec = (clip.trimStartMs / 1000f).coerceIn(0f, (maxSec - 0.3f).coerceAtLeast(0f))
            val endSec = (clip.trimEndMs / 1000f).coerceIn(startSec + 0.3f, maxSec)
            Text(
                text = stringResource(
                    R.string.editor_clip_trim_label,
                    FfmpegProgressParser.formatDurationMs(clip.trimStartMs),
                    FfmpegProgressParser.formatDurationMs(clip.trimEndMs),
                    FfmpegProgressParser.formatDurationMs(VideoEditorMath.effectiveClipDurationMs(clip))
                ),
                style = MonoTextStyle,
                color = MaterialTheme.colorScheme.primary
            )
            RangeSlider(
                value = startSec..endSec,
                onValueChange = { range ->
                    val sMs = (range.start * 1000f).roundToLong().coerceIn(0L, (clip.durationMs - 300L).coerceAtLeast(0L))
                    val eMs = (range.endInclusive * 1000f).roundToLong().coerceIn(sMs + 300L, clip.durationMs)
                    onUpdate { it.copy(trimStartMs = sMs, trimEndMs = eMs) }
                },
                onValueChangeFinished = {
                    exoPlayer.seekTo(clip.trimStartMs)
                },
                valueRange = 0f..maxSec,
                colors = SliderDefaults.colors(
                    thumbColor = HariMuColors.ElectricOrange,
                    activeTrackColor = HariMuColors.ElectricOrange
                ),
                modifier = Modifier.testTag("editor_clip_trim_slider")
            )

            Text(
                text = stringResource(R.string.editor_clip_speed_label, String.format(Locale.US, "%.2f", clip.speed)),
                style = MaterialTheme.typography.labelLarge
            )
            Slider(
                value = clip.speed.coerceIn(0.25f, 4.0f),
                onValueChange = { spd ->
                    val snapped = ((spd * 4f).roundToLong() / 4f).coerceIn(0.25f, 4.0f)
                    onUpdate { it.copy(speed = snapped) }
                },
                valueRange = 0.25f..4.0f,
                modifier = Modifier.testTag("editor_clip_speed_slider")
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0.5f, 1.0f, 1.5f, 2.0f).forEach { spd ->
                    FilterChip(
                        selected = kotlin.math.abs(clip.speed - spd) < 0.05f,
                        onClick = { onUpdate { it.copy(speed = spd) } },
                        label = { Text("${spd}x") }
                    )
                }
            }

            Text(
                text = stringResource(R.string.editor_clip_rotation_label),
                style = MaterialTheme.typography.labelLarge
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0, 90, 180, 270).forEach { deg ->
                    FilterChip(
                        selected = VideoEditorMath.normalizeRotation(clip.rotationDeg) == deg,
                        onClick = { onUpdate { it.copy(rotationDeg = deg) } },
                        label = { Text("${deg}°") }
                    )
                }
            }

            ParamToggle(
                label = stringResource(R.string.editor_clip_mute_label),
                checked = clip.muteAudio,
                onCheckedChange = { muted -> onUpdate { it.copy(muteAudio = muted) } }
            )
        }
    }
}

@Composable
private fun SavedProjectsDialog(
    projects: List<VideoProjectEntity>,
    decodeCount: (VideoProjectEntity) -> Int,
    onNewProject: () -> Unit,
    onLoadProject: (VideoProjectEntity) -> Unit,
    onDeleteProject: (VideoProjectEntity) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.editor_saved_projects_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onNewProject,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.editor_new_project))
                }
                if (projects.isEmpty()) {
                    Text(
                        text = stringResource(R.string.editor_no_saved_projects),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    projects.take(6).forEach { proj ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(proj.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        text = stringResource(R.string.editor_project_meta, decodeCount(proj), proj.outputResolution),
                                        style = MonoTextStyle,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                TextButton(onClick = { onLoadProject(proj) }) {
                                    Text(stringResource(R.string.editor_load_project))
                                }
                                IconButton(onClick = { onDeleteProject(proj) }, modifier = Modifier.size(48.dp)) {
                                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete), tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
