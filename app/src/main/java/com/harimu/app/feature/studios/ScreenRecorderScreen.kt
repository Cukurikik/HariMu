package com.harimu.app.feature.studios

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ScreenShare
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.harimu.app.AppContainer
import com.harimu.app.R
import com.harimu.app.core.design.HariMuThemeTokens
import com.harimu.app.core.design.MonoTextStyle
import com.harimu.app.core.design.ParamSlider
import com.harimu.app.core.recording.ScreenRecorderEngine
import com.harimu.app.core.recording.ScreenRecorderService
import com.harimu.app.core.recording.ScreenRecordingController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Screen Recorder (CORE-05). Persetujuan MediaProjection diminta **setiap sesi** (wajib Android 14+)
 * lalu diteruskan ke [ScreenRecorderService] (foreground `mediaProjection`) yang memegang engine.
 * Kontrol tersedia di notifikasi (jeda/lanjut/stop) dan opsional sebagai overlay.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ScreenRecorderScreen(
    container: AppContainer,
    isEnglish: Boolean,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val state by ScreenRecordingController.state.collectAsStateWithLifecycle()

    var resolution by rememberSaveable { mutableStateOf("720p") }
    var fps by rememberSaveable { mutableIntStateOf(30) }
    var bitrateMbps by rememberSaveable { mutableFloatStateOf(6f) }
    var audioModeName by rememberSaveable { mutableStateOf(ScreenRecorderEngine.AudioMode.MIC.name) }
    val audioMode = remember(audioModeName) {
        ScreenRecorderEngine.AudioMode.entries.firstOrNull { it.name == audioModeName }
            ?: ScreenRecorderEngine.AudioMode.MIC
    }
    var containerFormatName by rememberSaveable { mutableStateOf(ScreenRecorderEngine.Container.MP4.name) }
    val containerFormat = remember(containerFormatName) {
        ScreenRecorderEngine.Container.entries.firstOrNull { it.name == containerFormatName }
            ?: ScreenRecorderEngine.Container.MP4
    }
    var overlayEnabled by rememberSaveable { mutableStateOf(false) }
    var recordedOnce by remember { mutableStateOf(false) }

    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var micDeniedOnce by rememberSaveable { mutableStateOf(false) }
    var pendingStartAfterMic by remember { mutableStateOf(false) }

    val projectionManager = remember {
        context.getSystemService(MediaProjectionManager::class.java)
    }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val overlayLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (!Settings.canDrawOverlays(context)) {
            overlayEnabled = false
            Toast.makeText(
                context,
                resources.getString(R.string.screen_recorder_overlay_needed),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    val captureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            val (width, height) = when (resolution) {
                "1080p" -> 1920 to 1080
                "1440p" -> 2560 to 1440
                "480p" -> 854 to 480
                else -> 1280 to 720
            }
            val config = ScreenRecorderEngine.Config(
                width = width,
                height = height,
                fps = fps,
                bitrateBps = (bitrateMbps * 1_000_000).toInt(),
                audioMode = audioMode,
                container = containerFormat
            )
            ScreenRecordingController.reset()
            ContextCompat.startForegroundService(
                context,
                ScreenRecorderService.intent(context, result.resultCode, data, config, overlayEnabled)
            )
        } else {
            Toast.makeText(
                context,
                resources.getString(R.string.screen_recorder_perm_denied),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasMicPermission = granted
        if (granted) {
            micDeniedOnce = false
            if (pendingStartAfterMic) {
                pendingStartAfterMic = false
                captureLauncher.launch(projectionManager.createScreenCaptureIntent())
            }
        } else {
            pendingStartAfterMic = false
            micDeniedOnce = true
        }
    }

    LaunchedEffect(state.output) {
        val file = state.output ?: return@LaunchedEffect
        if (recordedOnce) return@LaunchedEffect
        recordedOnce = true
        withContext(Dispatchers.IO) {
            container.workspace.recordHistory(
                toolId = "CORE-05",
                toolTitle = "Screen Recorder",
                groupName = "CORE",
                outputFile = file,
                mimeType = if (file.extension == "webm") "video/webm" else "video/mp4",
                summary = "${file.name} (${container.storage.formatBytes(file.length())})"
            )
        }
    }

    LaunchedEffect(state.running) {
        if (state.running) recordedOnce = false
    }

    fun startCapture() {
        val micGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        hasMicPermission = micGranted
        if (!micGranted && audioMode != ScreenRecorderEngine.AudioMode.NONE) {
            pendingStartAfterMic = true
            micLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        captureLauncher.launch(projectionManager.createScreenCaptureIntent())
    }

    val toggleFloatingControls: (Boolean) -> Unit = { checked ->
        if (checked && !Settings.canDrawOverlays(context)) {
            overlayLauncher.launch(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                )
            )
        }
        overlayEnabled = checked
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.screen_recorder_title),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .testTag("screen_recorder_back_button")
                            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
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
                    .widthIn(max = HariMuThemeTokens.layout.formMaxWidth)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = stringResource(R.string.screen_recorder_intro),
                            style = MaterialTheme.typography.bodySmall
                        )

                        Text(stringResource(R.string.screen_recorder_resolution), style = MaterialTheme.typography.labelLarge)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf("480p", "720p", "1080p", "1440p").forEach { option ->
                                FilterChip(
                                    selected = resolution == option,
                                    enabled = !state.running,
                                    onClick = { resolution = option },
                                    modifier = Modifier.heightIn(min = 48.dp),
                                    label = { Text(option) }
                                )
                            }
                        }

                        Text(stringResource(R.string.screen_recorder_fps), style = MaterialTheme.typography.labelLarge)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf(24, 30, 60).forEach { option ->
                                FilterChip(
                                    selected = fps == option,
                                    enabled = !state.running,
                                    onClick = { fps = option },
                                    modifier = Modifier.heightIn(min = 48.dp),
                                    label = { Text("$option fps") }
                                )
                            }
                        }

                        ParamSlider(
                            label = stringResource(R.string.screen_recorder_bitrate),
                            value = bitrateMbps,
                            min = 1f,
                            max = 20f,
                            step = 1f,
                            unit = " Mbps",
                            onValueChange = { bitrateMbps = it }
                        )

                        Text(stringResource(R.string.screen_recorder_audio_source), style = MaterialTheme.typography.labelLarge)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            ScreenRecorderEngine.AudioMode.entries.forEach { mode ->
                                if (mode == ScreenRecorderEngine.AudioMode.MIC_AND_INTERNAL && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                                    return@forEach
                                }
                                FilterChip(
                                    selected = audioMode == mode,
                                    enabled = !state.running,
                                    onClick = { audioModeName = mode.name },
                                    modifier = Modifier.heightIn(min = 48.dp),
                                    label = {
                                        Text(
                                            when (mode) {
                                                ScreenRecorderEngine.AudioMode.NONE -> stringResource(R.string.screen_recorder_audio_none)
                                                ScreenRecorderEngine.AudioMode.MIC -> stringResource(R.string.screen_recorder_audio_mic)
                                                ScreenRecorderEngine.AudioMode.INTERNAL -> stringResource(R.string.screen_recorder_audio_internal)
                                                ScreenRecorderEngine.AudioMode.MIC_AND_INTERNAL -> stringResource(R.string.screen_recorder_audio_mic_internal)
                                            }
                                        )
                                    }
                                )
                            }
                        }

                        Text(stringResource(R.string.screen_recorder_container), style = MaterialTheme.typography.labelLarge)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            ScreenRecorderEngine.Container.entries.forEach { option ->
                                val supported = option == ScreenRecorderEngine.Container.MP4 || Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                                FilterChip(
                                    selected = containerFormat == option,
                                    enabled = !state.running && supported,
                                    onClick = { containerFormatName = option.name },
                                    modifier = Modifier.heightIn(min = 48.dp),
                                    label = {
                                        Text(
                                            when (option) {
                                                ScreenRecorderEngine.Container.MP4 -> "MP4 (H.264/AAC)"
                                                ScreenRecorderEngine.Container.WEBM -> "WebM (VP8/Opus)"
                                            }
                                        )
                                    }
                                )
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .toggleable(
                                    value = overlayEnabled,
                                    enabled = !state.running,
                                    role = Role.Switch,
                                    onValueChange = toggleFloatingControls
                                )
                                .testTag("screen_floating_controls_row"),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text(
                                    stringResource(R.string.screen_recorder_floating_title),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    stringResource(R.string.screen_recorder_floating_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = overlayEnabled,
                                onCheckedChange = null,
                                enabled = !state.running
                            )
                        }

                        if (audioMode != ScreenRecorderEngine.AudioMode.NONE && !hasMicPermission) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("screen_recorder_mic_rationale_card"),
                                colors = CardDefaults.cardColors(
                                    containerColor = HariMuThemeTokens.semantic.warningContainer
                                ),
                                border = BorderStroke(
                                    1.dp,
                                    HariMuThemeTokens.semantic.warning.copy(alpha = 0.5f)
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = stringResource(
                                            if (micDeniedOnce) R.string.recorder_perm_mic_denied_hint
                                            else R.string.screen_recorder_mic_required
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = HariMuThemeTokens.semantic.onWarningContainer
                                    )
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                pendingStartAfterMic = false
                                                micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                            },
                                            modifier = Modifier
                                                .heightIn(min = 48.dp)
                                                .testTag("screen_recorder_grant_mic_button")
                                        ) {
                                            Text(stringResource(R.string.voice_recorder_grant_mic))
                                        }
                                        if (micDeniedOnce) {
                                            OutlinedButton(
                                                onClick = {
                                                    runCatching {
                                                        context.startActivity(
                                                            Intent(
                                                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                                                Uri.parse("package:${context.packageName}")
                                                            )
                                                        )
                                                    }
                                                },
                                                modifier = Modifier
                                                    .heightIn(min = 48.dp)
                                                    .testTag("screen_recorder_open_settings_button")
                                            ) {
                                                Text(stringResource(R.string.recorder_open_app_settings))
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        when {
                            state.error != null -> Text(
                                text = state.error ?: "",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                            state.running -> Text(
                                text = stringResource(
                                    if (state.paused) R.string.screen_recorder_paused_status
                                    else R.string.screen_recorder_active_status
                                ),
                                style = MonoTextStyle,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            state.output != null -> Text(
                                text = "${state.output?.name} (${container.storage.formatBytes(state.output?.length() ?: 0)})",
                                style = MonoTextStyle,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            Button(
                                onClick = { if (state.running) ScreenRecordingController.stop() else startCapture() },
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("screen_record_toggle")
                            ) {
                                Icon(
                                    if (state.running) Icons.Filled.Stop else Icons.AutoMirrored.Filled.ScreenShare,
                                    contentDescription = null
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    stringResource(
                                        if (state.running) R.string.action_stop else R.string.screen_recorder_start
                                    )
                                )
                            }
                            if (state.running) {
                                OutlinedButton(
                                    onClick = {
                                        if (state.paused) ScreenRecordingController.resume() else ScreenRecordingController.pause()
                                    },
                                    modifier = Modifier
                                        .testTag("screen_record_pause_button")
                                        .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                                ) {
                                    Icon(
                                        if (state.paused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                                        contentDescription = stringResource(
                                            if (state.paused) R.string.screen_recorder_cd_resume
                                            else R.string.screen_recorder_cd_pause
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                state.output?.let { file ->
                    val mime = if (file.extension == "webm") "video/webm" else "video/mp4"
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = {
                                runCatching {
                                    context.startActivity(
                                        Intent.createChooser(container.storage.buildOpenIntent(file, mime), file.name)
                                    )
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.action_open))
                        }
                        Button(
                            onClick = {
                                scope.launch {
                                    context.startActivity(
                                        Intent.createChooser(container.storage.buildShareIntent(file, mime), file.name)
                                    )
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                        ) {
                            Icon(Icons.Filled.Share, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.action_share))
                        }
                    }
                }
            }
        }
    }
}
