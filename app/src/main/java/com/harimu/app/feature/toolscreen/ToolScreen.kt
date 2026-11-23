package com.harimu.app.feature.toolscreen

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.harimu.app.AppContainer
import com.harimu.app.R
import com.harimu.app.core.data.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.harimu.app.core.design.EngineBadge
import com.harimu.app.core.design.ErrorState
import com.harimu.app.core.design.FilePickerField
import com.harimu.app.core.design.HariMuColors
import com.harimu.app.core.design.MonoTextStyle
import com.harimu.app.core.design.ParamDropdown
import com.harimu.app.core.design.ParamSlider
import com.harimu.app.core.design.ParamToggle
import com.harimu.app.core.design.ProgressSheet
import com.harimu.app.core.design.ResultCard
import com.harimu.app.core.design.ToolIdBadge
import com.harimu.app.core.design.accentColor
import com.harimu.app.core.toolapi.InputSpec
import com.harimu.app.core.toolapi.ToolOutputFile
import com.harimu.app.core.toolapi.ToolParam
import com.harimu.app.core.toolapi.ToolProgress

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolScreen(
    toolId: String,
    container: AppContainer,
    isEnglish: Boolean,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val vm: ToolViewModel = viewModel(factory = ToolViewModel.Factory(toolId, container))
    LaunchedEffect(context) { vm.bindContext(context) }

    val state by vm.state.collectAsStateWithLifecycle()
    val settings by container.settings.settingsFlow.collectAsStateWithLifecycle(initialValue = AppSettings())
    val tool = state.tool ?: return

    var pendingExportFile by remember { mutableStateOf<ToolOutputFile?>(null) }
    var audioPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var isPlayingAudio by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose { runCatching { audioPlayer?.release() } }
    }

    val singleImagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { vm.setUris(listOf(it)) }
    }
    val multiImagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(20)) { uris ->
        if (uris.isNotEmpty()) vm.setUris(uris)
    }
    val singleVideoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { vm.setUris(listOf(it)) }
    }
    val multiVideoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(8)) { uris ->
        if (uris.isNotEmpty()) vm.setUris(uris)
    }
    val singleDocPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.setUris(listOf(it)) }
    }
    val multiDocPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) vm.setUris(uris)
    }
    val secondaryAudioPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.setSecondaryUri(it) }
    }

    val savedToast = stringResource(R.string.toast_saved)
    val saveFailedToast = stringResource(R.string.toast_save_failed)
    val copiedToast = stringResource(R.string.toast_copied)

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        vm.execute(isEnglish)
    }

    fun triggerExecution() {
        if (tool.isStub && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        vm.execute(isEnglish)
    }

    val safSaveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { destUri ->
        val src = pendingExportFile
        if (destUri != null && src != null) {
            val ok = container.storage.copyFileToUri(src.file, destUri)
            Toast.makeText(context, if (ok) savedToast else saveFailedToast, Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tool.title(isEnglish), maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("tool_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                actions = {
                    IconButton(onClick = onToggleFavorite) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarOutline,
                            contentDescription = stringResource(R.string.cd_toggle_favorite),
                            tint = if (isFavorite) HariMuColors.ElectricOrange else MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
                    .widthIn(max = 640.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            ToolIdBadge(id = tool.id, accent = tool.group.accentColor())
                            EngineBadge(engine = tool.engineLabel)
                            if (tool.isStub) {
                                EngineBadge(engine = stringResource(R.string.badge_coming_soon))
                            }
                        }
                        Text(
                            text = tool.description(isEnglish),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (tool.isStub) {
                    Card(
                        modifier = Modifier.testTag("stub_notice_card"),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(
                                    imageVector = Icons.Filled.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Text(
                                    text = stringResource(R.string.stub_notice_title, tool.plannedPhase.ifBlank { "-" }),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                            Text(
                                text = stringResource(R.string.stub_notice_body, tool.plannedPhase.ifBlank { "-" }),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }

                when (val spec = tool.inputSpec) {
                    InputSpec.None -> Unit
                    is InputSpec.TextInput -> {
                        OutlinedTextField(
                            value = state.textInput,
                            onValueChange = vm::setTextInput,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .testTag("tool_text_input"),
                            label = { Text(if (isEnglish) spec.hintEn else spec.hintId) },
                            textStyle = MonoTextStyle
                        )
                    }
                    else -> {
                        val isMulti = spec == InputSpec.MultipleImages || spec == InputSpec.MultipleVideos ||
                            spec == InputSpec.MultipleAudios || spec == InputSpec.MultiplePdfs || spec == InputSpec.AnyFile
                        val fallbackAudio = stringResource(R.string.fallback_audio_name)
                        val fileNames = state.selectedUris.mapIndexed { idx, uri ->
                            container.storage.getDisplayName(uri) ?: stringResource(R.string.fallback_file_name, idx + 1)
                        }
                        FilePickerField(
                            title = stringResource(R.string.label_input_files),
                            buttonLabel = if (isMulti) stringResource(R.string.action_pick_files) else stringResource(R.string.action_pick_file),
                            selectedFiles = fileNames,
                            onPickClick = {
                                when (spec) {
                                    InputSpec.SingleImage -> singleImagePicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                    InputSpec.MultipleImages -> multiImagePicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                    InputSpec.SingleVideo, InputSpec.VideoAndAudio -> singleVideoPicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                    )
                                    InputSpec.MultipleVideos -> multiVideoPicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                    )
                                    InputSpec.SingleAudio -> singleDocPicker.launch(arrayOf("audio/*"))
                                    InputSpec.MultipleAudios -> multiDocPicker.launch(arrayOf("audio/*"))
                                    InputSpec.SinglePdf -> singleDocPicker.launch(arrayOf("application/pdf"))
                                    InputSpec.MultiplePdfs -> multiDocPicker.launch(arrayOf("application/pdf"))
                                    InputSpec.ZipFile -> singleDocPicker.launch(arrayOf("application/zip", "application/x-zip-compressed", "*/*"))
                                    InputSpec.AnyFile -> multiDocPicker.launch(arrayOf("*/*"))
                                    else -> Unit
                                }
                            },
                            secondaryContent = if (spec == InputSpec.VideoAndAudio) {
                                {
                                    OutlinedButton(
                                        onClick = { secondaryAudioPicker.launch(arrayOf("audio/*")) },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(stringResource(R.string.action_pick_audio_bg))
                                    }
                                    state.secondaryUri?.let { aUri ->
                                        Text(
                                            text = container.storage.getDisplayName(aUri) ?: fallbackAudio,
                                            style = MonoTextStyle,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                    }
                                }
                            } else null
                        )
                    }
                }

                if (tool.params.isNotEmpty()) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(text = stringResource(R.string.label_parameters), style = MaterialTheme.typography.titleSmall)
                            tool.params.forEach { param ->
                                when (param) {
                                    is ToolParam.SliderParam -> {
                                        val v = (state.paramValues[param.key] as? Number)?.toFloat() ?: param.defaultValue
                                        ParamSlider(
                                            label = if (isEnglish) param.labelEn else param.labelId,
                                            value = v,
                                            min = param.min,
                                            max = param.max,
                                            step = param.step,
                                            unit = param.unit,
                                            onValueChange = { vm.setParam(param.key, it) }
                                        )
                                    }
                                    is ToolParam.DropdownParam -> {
                                        val current = state.paramValues[param.key]?.toString() ?: param.defaultOption
                                        ParamDropdown(
                                            label = if (isEnglish) param.labelEn else param.labelId,
                                            options = param.options,
                                            selectedOption = current,
                                            onOptionSelected = { vm.setParam(param.key, it) }
                                        )
                                    }
                                    is ToolParam.ToggleParam -> {
                                        val checked = state.paramValues[param.key] as? Boolean ?: param.defaultValue
                                        ParamToggle(
                                            label = if (isEnglish) param.labelEn else param.labelId,
                                            checked = checked,
                                            onCheckedChange = { vm.setParam(param.key, it) }
                                        )
                                    }
                                    is ToolParam.TextParam -> {
                                        val txt = state.paramValues[param.key]?.toString() ?: param.defaultValue
                                        OutlinedTextField(
                                            value = txt,
                                            onValueChange = { vm.setParam(param.key, it) },
                                            modifier = Modifier.fillMaxWidth(),
                                            singleLine = true,
                                            label = { Text(if (isEnglish) param.labelEn else param.labelId) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Button(
                    onClick = { triggerExecution() },
                    enabled = !state.isRunning,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("run_tool_button")
                ) {
                    Text(
                        text = when {
                            state.isRunning -> stringResource(R.string.action_processing)
                            tool.isStub -> stringResource(R.string.action_run_dummy_job)
                            else -> stringResource(R.string.action_run_tool)
                        },
                        style = MaterialTheme.typography.titleSmall
                    )
                }

                when (val prog = state.progress) {
                    is ToolProgress.Queued -> ProgressSheet(
                        percent = null,
                        message = stringResource(R.string.progress_queued),
                        onCancel = { vm.cancel() }
                    )
                    is ToolProgress.Running -> ProgressSheet(
                        percent = prog.percent,
                        message = prog.message,
                        onCancel = { vm.cancel() }
                    )
                    is ToolProgress.Failed -> ErrorState(
                        message = prog.error,
                        onRetry = { triggerExecution() }
                    )
                    is ToolProgress.Done -> {
                        val firstOut = prog.result.outputs.firstOrNull()
                        ResultCard(
                            summary = prog.result.summary,
                            onOpen = firstOut?.let { out ->
                                {
                                    runCatching {
                                        context.startActivity(
                                            Intent.createChooser(container.storage.buildOpenIntent(out.file, out.mimeType), out.displayName)
                                        )
                                    }
                                }
                            },
                            onShare = firstOut?.let { out ->
                                {
                                    context.startActivity(
                                        Intent.createChooser(container.storage.buildShareIntent(out.file, out.mimeType), out.displayName)
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
                                                    Toast.makeText(context, savedToast, Toast.LENGTH_SHORT).show()
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
                        ) {
                            prog.result.outputs.forEach { out ->
                                if (out.mimeType.startsWith("image/")) {
                                    AsyncImage(
                                        model = out.file,
                                        contentDescription = out.displayName,
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(220.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                    )
                                }
                                if (out.mimeType.startsWith("audio/")) {
                                    OutlinedButton(
                                        onClick = {
                                            if (isPlayingAudio) {
                                                runCatching { audioPlayer?.stop(); audioPlayer?.release() }
                                                audioPlayer = null
                                                isPlayingAudio = false
                                            } else {
                                                runCatching {
                                                    audioPlayer?.release()
                                                    audioPlayer = MediaPlayer().apply {
                                                        setDataSource(out.file.absolutePath)
                                                        prepare()
                                                        setOnCompletionListener { isPlayingAudio = false }
                                                        start()
                                                    }
                                                    isPlayingAudio = true
                                                }
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(if (isPlayingAudio) Icons.Filled.Stop else Icons.Filled.PlayArrow, contentDescription = null)
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            if (isPlayingAudio) stringResource(R.string.action_stop_audio)
                                            else stringResource(R.string.action_play_audio)
                                        )
                                    }
                                }
                            }
                            prog.result.textOutput?.let { txt ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .padding(12.dp)
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(stringResource(R.string.label_output), style = MaterialTheme.typography.labelMedium)
                                            IconButton(onClick = {
                                                clipboard.setText(AnnotatedString(txt))
                                                Toast.makeText(context, copiedToast, Toast.LENGTH_SHORT).show()
                                            }) {
                                                Icon(Icons.Filled.ContentCopy, contentDescription = stringResource(R.string.action_copy), modifier = Modifier.size(18.dp))
                                            }
                                        }
                                        Text(txt, style = MonoTextStyle)
                                    }
                                }
                            }
                        }
                    }
                    null -> Unit
                }
            }
        }
    }
}
