package com.harimu.app.feature.studios

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import com.harimu.app.AppContainer
import com.harimu.app.navigation.ToolRoute
import com.harimu.app.feature.toolscreen.ToolPrefillData
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
import com.harimu.app.core.design.video.ClipInspector
import com.harimu.app.core.design.video.ExportDialog
import com.harimu.app.core.design.video.TimelineTrack
import com.harimu.app.core.design.video.VideoPreview
import com.harimu.app.core.design.video.VideoPreviewPlaceholder
import com.harimu.app.feature.toolscreen.ToolPrefillData
import com.harimu.app.navigation.ToolRoute
import com.harimu.app.core.media.FfmpegProgressParser
import com.harimu.app.core.media.VideoEditorMath
import com.harimu.app.core.toolapi.ToolOutputFile
import com.harimu.app.core.toolapi.ToolProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoEditorScreen(
    container: AppContainer,
    isEnglish: Boolean,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val scope = rememberCoroutineScope()
    val vm: VideoEditorViewModel = viewModel(factory = VideoEditorViewModel.Factory(container))
    val state by vm.state.collectAsStateWithLifecycle()
    val savedProjects by vm.savedProjects.collectAsStateWithLifecycle()
    val settings by container.settings.settingsFlow.collectAsStateWithLifecycle(initialValue = AppSettings())

    val defaultTitle = stringResource(R.string.editor_default_project_title)
    val savedToast = stringResource(R.string.editor_project_saved_toast)
    val fileSavedToast = stringResource(R.string.toast_saved)
    val fileSaveFailedToast = stringResource(R.string.toast_save_failed)
    val navController = androidx.navigation.compose.rememberNavController()

    LaunchedEffect(defaultTitle) {
        vm.initializeDefaultTitle(defaultTitle)
    }

    var showProjectsDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var pendingExportFile by remember { mutableStateOf<ToolOutputFile?>(null) }
    var exportResolution by remember { mutableStateOf(state.outputResolution) }

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

    // Handle "Open in Tool" from ClipInspector
    val openInToolHandler = remember { (toolId: String, params: Map<String, Any>) ->
        val clip = state.selectedClip
        clip?.let {
            ToolPrefillData.setPrefill(it.uriString.toUri(), params)
            navController.navigate(ToolRoute(toolId))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.editor_title),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("editor_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                actions = {
                    // Last saved indicator
                    if (state.lastSavedAt != null) {
                        Text(
                            text = stringResource(
                                R.string.editor_last_saved,
                                FfmpegProgressParser.formatDurationMs(System.currentTimeMillis() - state.lastSavedAt!!)
                            ),
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier.padding(end = 12.dp)
                        )
                    }
                    TextButton(
                        onClick = { showProjectsDialog = true },
                        modifier = Modifier.testTag("editor_projects_button")
                    ) {
                        Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                        androidx.compose.foundation.layout.Spacer(Modifier.width(4.dp))
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
                        androidx.compose.foundation.layout.Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.editor_save_btn))
                    }
                    // Export button opens dialog
                    Button(
                        onClick = {
                            exportResolution = state.outputResolution
                            showExportDialog = true
                        },
                        enabled = state.clips.isNotEmpty() && !state.isExporting,
                        modifier = Modifier
                            .testTag("editor_export_button")
                            .padding(end = 8.dp)
                    ) {
                        Icon(Icons.Filled.MovieCreation, contentDescription = null, modifier = Modifier.size(18.dp))
                        androidx.compose.foundation.layout.Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.editor_export_btn_short))
                    }
                }
            )
        }
    ) { padding ->
        if (isLandscape) {
            // Landscape: Split pane - Preview left, Timeline + Inspector right
            LandscapeLayout(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                state = state,
                vm = vm,
                isEnglish = isEnglish,
                onOpenInTool = openInToolHandler
            )
        } else {
            // Portrait: Stacked layout
            PortraitLayout(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                state = state,
                vm = vm,
                isEnglish = isEnglish,
                onOpenInTool = openInToolHandler
            )
        }
    }

    // Projects Dialog
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

    // Export Dialog
    ExportDialog(
        isOpen = showExportDialog,
        onDismiss = { showExportDialog = false },
        onExport = { resolution ->
            showExportDialog = false
            vm.setResolution(resolution)
            vm.exportTimeline(context, isEnglish)
        },
        clipsCount = state.clips.size,
        totalDurationMs = state.totalDurationMs,
        estimatedFileSizeBytes = estimateFileSize(state.clips, state.outputResolution),
        progress = state.progress,
        result = state.progress?.let { if (it is ToolProgress.Done) it.result else null },
        onOpenResult = state.progress?.let { if (it is ToolProgress.Done) {
            val out = it.result.outputs.firstOrNull()
            out?.let { o ->
                {
                    runCatching {
                        context.startActivity(
                            Intent.createChooser(
                                container.storage.buildOpenIntent(o.file, o.mimeType),
                                o.displayName
                            )
                        )
                    }
                }
            }
        }},
        onShareResult = state.progress?.let { if (it is ToolProgress.Done) {
            val out = it.result.outputs.firstOrNull()
            out?.let { o ->
                {
                    context.startActivity(
                        Intent.createChooser(
                            container.storage.buildShareIntent(o.file, o.mimeType),
                            o.displayName
                        )
                    )
                }
            }
        }},
        onSaveResult = state.progress?.let { if (it is ToolProgress.Done) {
            val out = it.result.outputs.firstOrNull()
            out?.let { o ->
                {
                    val defaultTreeUri = settings.outputFolderUri
                    if (defaultTreeUri.isNotBlank()) {
                        scope.launch(Dispatchers.IO) {
                            val savedUri = container.storage.saveToTreeUri(
                                source = o.file,
                                treeUri = defaultTreeUri.toUri(),
                                mimeType = o.mimeType
                            )
                            withContext(Dispatchers.Main) {
                                if (savedUri != null) {
                                    Toast.makeText(context, fileSavedToast, Toast.LENGTH_SHORT).show()
                                } else {
                                    pendingExportFile = o
                                    safSaveLauncher.launch(o.displayName)
                                }
                            }
                        }
                    } else {
                        pendingExportFile = o
                        safSaveLauncher.launch(o.displayName)
                    }
                }
            }
        }}
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PortraitLayout(
    modifier: Modifier,
    state: com.harimu.app.feature.studios.VideoEditorUiState,
    vm: VideoEditorViewModel,
    isEnglish: Boolean,
    onOpenInTool: (String, Map<String, Any>) -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 760.dp)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Project title
        OutlinedTextField(
            value = state.title,
            onValueChange = vm::setTitle,
            singleLine = true,
            label = { Text(stringResource(R.string.editor_project_title_label)) },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("editor_title_input")
        )

        // Add clips button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = {
                    val launcher = rememberLauncherForActivityResult(
                        ActivityResultContracts.PickMultipleVisualMedia(12)
                    ) { uris ->
                        if (uris.isNotEmpty()) vm.addClips(LocalContext.current, uris)
                    }
                    launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("editor_add_clips_button")
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                androidx.compose.foundation.layout.Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.editor_add_clips))
            }
        }

        // Resolution picker
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

        // Preview area
        if (state.selectedClip != null) {
            VideoPreview(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .testTag("editor_preview"),
                clipUriString = state.selectedClip!!.uriString,
                trimStartMs = state.selectedClip!!.trimStartMs,
                trimEndMs = state.selectedClip!!.trimEndMs,
                speed = state.selectedClip!!.speed,
                rotationDeg = state.selectedClip!!.rotationDeg,
                durationMs = state.selectedClip!!.durationMs,
                showTrimHandles = true
            )
        } else {
            VideoPreviewPlaceholder(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp),
                title = stringResource(R.string.editor_preview_no_clip),
                subtitle = stringResource(R.string.editor_preview_select_clip)
            )
        }

        // Timeline
        TimelineTrack(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("editor_timeline"),
            clips = state.clips,
            selectedClipId = state.selectedClip?.id,
            onClipSelected = vm::selectClip,
            onClipMoved = { from, to -> vm.moveClip(state.clips[from].id, if (to > from) 1 else -1) },
            onClipRemoved = { clipId -> vm.removeClip(clipId) },
            onClipDuplicated = { clipId -> vm.duplicateClip(clipId) },
            showThumbnails = true
        )

        // Clip Inspector
        state.selectedClip?.let { activeClip ->
            val activeIndex = state.clips.indexOfFirst { it.id == activeClip.id }.coerceAtLeast(0)
            ClipInspector(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("editor_inspector"),
                clipIndex = activeIndex,
                clip = activeClip,
                onUpdate = vm::updateSelectedClip,
                onOpenInTool = onOpenInTool,
                showAdvancedActions = true
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LandscapeLayout(
    modifier: Modifier,
    state: com.harimu.app.feature.studios.VideoEditorUiState,
    vm: VideoEditorViewModel,
    isEnglish: Boolean,
    onOpenInTool: (String, Map<String, Any>) -> Unit
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopStart
    ) {
        // Left pane: Preview (60% width)
        Box(
            modifier = Modifier
                .width(0.6f, 0f)
                .fillMaxHeight()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (state.selectedClip != null) {
                    VideoPreview(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(0f, 1f)
                            .testTag("editor_preview_landscape"),
                        clipUriString = state.selectedClip!!.uriString,
                        trimStartMs = state.selectedClip!!.trimStartMs,
                        trimEndMs = state.selectedClip!!.trimEndMs,
                        speed = state.selectedClip!!.speed,
                        rotationDeg = state.selectedClip!!.rotationDeg,
                        durationMs = state.selectedClip!!.durationMs,
                        showTrimHandles = true
                    )
                } else {
                    VideoPreviewPlaceholder(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(0f, 1f),
                        title = stringResource(R.string.editor_preview_no_clip),
                        subtitle = stringResource(R.string.editor_preview_select_clip)
                    )
                }
            }
        }

        // Right pane: Timeline + Inspector (40% width)
        Box(
            modifier = Modifier
                .width(0.4f, 0f)
                .fillMaxHeight()
                .padding(16.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
        ) {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Project title (compact)
                OutlinedTextField(
                    value = state.title,
                    onValueChange = vm::setTitle,
                    singleLine = true,
                    label = { Text(stringResource(R.string.editor_project_title_label)) },
                    modifier = Modifier.fillMaxWidth()
                )

                // Add clips
                Button(
                    onClick = {
                        val launcher = rememberLauncherForActivityResult(
                            ActivityResultContracts.PickMultipleVisualMedia(12)
                        ) { uris ->
                            if (uris.isNotEmpty()) vm.addClips(LocalContext.current, uris)
                        }
                        launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                    },
                    modifier = Modifier.fillMaxWidth().height(40.dp)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    androidx.compose.foundation.layout.Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.editor_add_clips), style = MaterialTheme.typography.labelMedium)
                }

                // Resolution (compact)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf("480p", "720p", "1080p").forEach { res ->
                        FilterChip(
                            selected = state.outputResolution == res,
                            onClick = { vm.setResolution(res) },
                            label = { Text(res, style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Timeline
                TimelineTrack(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("editor_timeline_landscape"),
                    clips = state.clips,
                    selectedClipId = state.selectedClip?.id,
                    onClipSelected = vm::selectClip,
                    onClipMoved = { from, to -> vm.moveClip(state.clips[from].id, if (to > from) 1 else -1) },
                    onClipRemoved = { clipId -> vm.removeClip(clipId) },
                    onClipDuplicated = { clipId -> vm.duplicateClip(clipId) },
                    showThumbnails = true,
                    minClipWidth = 60f,
                    maxClipWidth = 200f
                )

                // Clip Inspector
                state.selectedClip?.let { activeClip ->
                    val activeIndex = state.clips.indexOfFirst { it.id == activeClip.id }.coerceAtLeast(0)
                    ClipInspector(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("editor_inspector_landscape"),
                        clipIndex = activeIndex,
                        clip = activeClip,
                        onUpdate = vm::updateSelectedClip,
                        onOpenInTool = onOpenInTool,
                        showAdvancedActions = true
                    )
                }
            }
        }
    }
}

private fun estimateFileSize(clips: List<EditorClipSpec>, resolution: String): Long {
    // Rough estimation: bitrate * duration
    val totalMs = VideoEditorMath.totalTimelineDurationMs(clips)
    val bitrateBps = when (resolution) {
        "1080p" -> 8_000_000L
        "720p" -> 4_000_000L
        "480p" -> 2_000_000L
        else -> 4_000_000L
    }
    return (bitrateBps * totalMs / 1000L / 8L).coerceAtLeast(100_000L)
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
                    androidx.compose.foundation.layout.Spacer(Modifier.width(6.dp))
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