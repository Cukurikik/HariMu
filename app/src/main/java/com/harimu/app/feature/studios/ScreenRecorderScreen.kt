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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.harimu.app.AppContainer
import com.harimu.app.R
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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenRecorderScreen(
    container: AppContainer,
    isEnglish: Boolean,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by ScreenRecordingController.state.collectAsStateWithLifecycle()

    var resolution by remember { mutableStateOf("720p") }
    var fps by remember { mutableStateOf(30) }
    var bitrateMbps by remember { mutableStateOf(6f) }
    var audioMode by remember { mutableStateOf(ScreenRecorderEngine.AudioMode.MIC) }
    var containerFormat by remember { mutableStateOf(ScreenRecorderEngine.Container.MP4) }
    var overlayEnabled by remember { mutableStateOf(false) }
    var recordedOnce by remember { mutableStateOf(false) }

    val projectionManager = remember {
        context.getSystemService(MediaProjectionManager::class.java)
    }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val overlayLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }

    val captureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            notificationLauncher.launch("android.permission.POST_NOTIFICATIONS")
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
                if (isEnglish) "Screen capture permission denied" else "Izin rekam layar ditolak",
                Toast.LENGTH_SHORT
            ).show()
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
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED && audioMode != ScreenRecorderEngine.AudioMode.NONE
        ) {
            Toast.makeText(
                context,
                if (isEnglish) "Microphone permission is required for audio" else "Izin mikrofon diperlukan untuk audio",
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        captureLauncher.launch(projectionManager.createScreenCaptureIntent())
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEnglish) "Screen Recorder Studio" else "Studio Perekam Layar") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = if (isEnglish) {
                            "MediaProjection records the whole screen. Android 14+ asks for consent every session."
                        } else {
                            "MediaProjection merekam seluruh layar. Android 14+ meminta persetujuan setiap sesi."
                        },
                        style = MaterialTheme.typography.bodySmall
                    )

                    Text(if (isEnglish) "Resolution" else "Resolusi", style = MaterialTheme.typography.labelLarge)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("480p", "720p", "1080p", "1440p").forEach { option ->
                            FilterChip(
                                selected = resolution == option,
                                enabled = !state.running,
                                onClick = { resolution = option },
                                label = { Text(option) }
                            )
                        }
                    }

                    Text(if (isEnglish) "Frame rate" else "Frame rate", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(24, 30, 60).forEach { option ->
                            FilterChip(
                                selected = fps == option,
                                enabled = !state.running,
                                onClick = { fps = option },
                                label = { Text("$option fps") }
                            )
                        }
                    }

                    ParamSlider(
                        label = if (isEnglish) "Bitrate" else "Bitrate",
                        value = bitrateMbps,
                        min = 1f,
                        max = 20f,
                        step = 1f,
                        unit = " Mbps",
                        onValueChange = { bitrateMbps = it }
                    )

                    Text(if (isEnglish) "Audio source" else "Sumber audio", style = MaterialTheme.typography.labelLarge)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ScreenRecorderEngine.AudioMode.entries.forEach { mode ->
                            if (mode == ScreenRecorderEngine.AudioMode.MIC_AND_INTERNAL && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                                return@forEach
                            }
                            FilterChip(
                                selected = audioMode == mode,
                                enabled = !state.running,
                                onClick = { audioMode = mode },
                                label = {
                                    Text(
                                        when (mode) {
                                            ScreenRecorderEngine.AudioMode.NONE -> if (isEnglish) "None" else "Tanpa audio"
                                            ScreenRecorderEngine.AudioMode.MIC -> "Mikrofon"
                                            ScreenRecorderEngine.AudioMode.INTERNAL -> if (isEnglish) "Internal (API 29+)" else "Internal (API 29+)"
                                            ScreenRecorderEngine.AudioMode.MIC_AND_INTERNAL -> if (isEnglish) "Mic + internal" else "Mic + internal"
                                        }
                                    )
                                }
                            )
                        }
                    }

                    Text(if (isEnglish) "Container" else "Kontainer", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ScreenRecorderEngine.Container.entries.forEach { option ->
                            val supported = option == ScreenRecorderEngine.Container.MP4 || Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                            FilterChip(
                                selected = containerFormat == option,
                                enabled = !state.running && supported,
                                onClick = { containerFormat = option },
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
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(if (isEnglish) "Floating controls" else "Tombol mengambang", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                if (isEnglish) "Needs “display over other apps” permission."
                                else "Butuh izin “tampil di atas aplikasi lain”.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = overlayEnabled,
                            onCheckedChange = { checked ->
                                if (checked && !Settings.canDrawOverlays(context)) {
                                    overlayLauncher.launch(
                                        Intent(
                                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                            Uri.parse("package:${context.packageName}")
                                        )
                                    )
                                }
                                overlayEnabled = checked
                            },
                            enabled = !state.running
                        )
                    }

                    when {
                        state.error != null -> Text(
                            text = state.error ?: "",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                        state.running -> Text(
                            text = if (state.paused) {
                                if (isEnglish) "Paused — recording continues after resume" else "Dijeda — rekaman lanjut setelah resume"
                            } else {
                                if (isEnglish) "Recording in background…" else "Merekam di latar belakang…"
                            },
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
                            modifier = Modifier.weight(1f).testTag("screen_record_toggle")
                        ) {
                            Icon(if (state.running) Icons.Filled.Stop else Icons.AutoMirrored.Filled.ScreenShare, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (state.running) "Stop" else if (isEnglish) "Start recording" else "Mulai rekam"
                            )
                        }
                        if (state.running) {
                            OutlinedButton(
                                onClick = {
                                    if (state.paused) ScreenRecordingController.resume() else ScreenRecordingController.pause()
                                }
                            ) {
                                Icon(if (state.paused) Icons.Filled.PlayArrow else Icons.Filled.Pause, contentDescription = null)
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
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(if (isEnglish) "Open" else "Buka")
                    }
                    Button(
                        onClick = {
                            scope.launch {
                                context.startActivity(
                                    Intent.createChooser(container.storage.buildShareIntent(file, mime), file.name)
                                )
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(if (isEnglish) "Share" else "Bagikan")
                    }
                }
            }
        }
    }
}
