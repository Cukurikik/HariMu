package com.harimu.app.feature.studios

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.harimu.app.AppContainer
import com.harimu.app.R
import com.harimu.app.core.design.HariMuColors
import com.harimu.app.core.design.MonoTextStyle
import com.harimu.app.core.recording.VoiceRecorderEngine
import com.harimu.app.core.recording.VoiceRecordingController
import com.harimu.app.core.recording.VoiceRecordingService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Voice Recorder (CORE-06): AudioRecord lewat foreground service tipe `microphone`, spektrum FFT
 * 40 pita, jeda/lanjut, WAV/AAC(M4A)/Opus(WebM). Fokus audio dipantau engine sehingga panggilan
 * masuk / aplikasi lain meminta fokus otomatis menjeda rekaman.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceRecorderScreen(
    container: AppContainer,
    isEnglish: Boolean,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by VoiceRecordingController.state.collectAsStateWithLifecycle()
    val spectrum by VoiceRecordingController.spectrum.collectAsStateWithLifecycle()

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var format by remember { mutableStateOf(VoiceRecorderEngine.OutputFormat.WAV) }
    var sampleRate by remember { mutableStateOf(44_100) }
    var isPlaying by remember { mutableStateOf(false) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var recordedOnce by remember { mutableStateOf(false) }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
    }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    LaunchedEffect(state.output) {
        val file = state.output ?: return@LaunchedEffect
        if (recordedOnce) return@LaunchedEffect
        recordedOnce = true
        withContext(Dispatchers.IO) {
            container.workspace.recordHistory(
                toolId = "CORE-06",
                toolTitle = "Voice Recorder",
                groupName = "CORE",
                outputFile = file,
                mimeType = when (file.extension) {
                    "m4a" -> "audio/mp4"
                    "webm" -> "audio/webm"
                    else -> "audio/wav"
                },
                summary = "${file.name} (${container.storage.formatBytes(file.length())})"
            )
        }
    }

    LaunchedEffect(state.running) {
        if (state.running) recordedOnce = false
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { player?.release() }
        }
    }

    fun begin() {
        if (!hasPermission) {
            permLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        notificationLauncher.launch("android.permission.POST_NOTIFICATIONS")
        VoiceRecordingController.reset()
        ContextCompat.startForegroundService(
            context,
            VoiceRecordingService.intent(context, VoiceRecorderEngine.Options(sampleRate, format))
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEnglish) "Voice Recorder Studio" else "Studio Perekam Suara") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(18.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    val secs = (state.elapsedMs / 1000).toInt()
                    Text(
                        text = String.format("%02d:%02d", secs / 60, secs % 60),
                        style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                        color = if (state.running) HariMuColors.ElectricOrange else MaterialTheme.colorScheme.onSurface
                    )

                    Canvas(modifier = Modifier.fillMaxWidth().height(150.dp)) {
                        val gap = 4.dp.toPx()
                        val barW = ((size.width - gap * (spectrum.size - 1)) / spectrum.size).coerceAtLeast(2f)
                        val midY = size.height / 2f
                        val brush = Brush.verticalGradient(
                            listOf(HariMuColors.ElectricOrange, HariMuColors.TealEmerald)
                        )
                        spectrum.forEachIndexed { idx, amp ->
                            val h = (amp * size.height * 0.9f).coerceAtLeast(4f)
                            drawRoundRect(
                                brush = brush,
                                topLeft = Offset(idx * (barW + gap), midY - h / 2f),
                                size = Size(barW, h),
                                cornerRadius = CornerRadius(barW / 2f, barW / 2f)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        VoiceRecorderEngine.OutputFormat.entries.forEach { option ->
                            FilterChip(
                                selected = format == option,
                                enabled = !state.running,
                                onClick = { format = option },
                                label = {
                                    Text(
                                        when (option) {
                                            VoiceRecorderEngine.OutputFormat.WAV -> "WAV/PCM"
                                            VoiceRecorderEngine.OutputFormat.AAC -> "AAC (M4A)"
                                            VoiceRecorderEngine.OutputFormat.OPUS_WEBM -> "Opus (WebM)"
                                        }
                                    )
                                }
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(44_100, 48_000).forEach { rate ->
                            FilterChip(
                                selected = sampleRate == rate,
                                enabled = !state.running,
                                onClick = { sampleRate = rate },
                                label = { Text("${rate / 1000} kHz") }
                            )
                        }
                    }

                    when {
                        state.error != null -> Text(
                            text = state.error ?: "",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                        state.running -> Text(
                            text = if (state.paused) {
                                if (isEnglish) "Paused — audio focus lost or manual pause" else "Dijeda — fokus audio hilang atau jeda manual"
                            } else {
                                if (isEnglish) "Recording… foreground service active" else "Merekam… foreground service aktif"
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
                }
            }

            if (!hasPermission) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(stringResource(R.string.permission_audio_rationale), style = MaterialTheme.typography.bodyMedium)
                        Button(onClick = { permLauncher.launch(Manifest.permission.RECORD_AUDIO) }) {
                            Text(if (isEnglish) "Grant Microphone Permission" else "Izinkan Mikrofon")
                        }
                    }
                }
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            if (state.running) VoiceRecordingController.stop() else begin()
                        },
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (state.running) MaterialTheme.colorScheme.error else HariMuColors.ElectricOrange
                        ),
                        modifier = Modifier.size(84.dp).testTag("record_toggle_button")
                    ) {
                        Icon(
                            imageVector = if (state.running) Icons.Filled.Stop else Icons.Filled.Mic,
                            contentDescription = if (state.running) "Stop" else "Record",
                            modifier = Modifier.size(34.dp)
                        )
                    }
                    if (state.running) {
                        OutlinedButton(
                            onClick = {
                                if (state.paused) VoiceRecordingController.resume() else VoiceRecordingController.pause()
                            }
                        ) {
                            Icon(if (state.paused) Icons.Filled.PlayArrow else Icons.Filled.Pause, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (state.paused) (if (isEnglish) "Resume" else "Lanjut")
                                else (if (isEnglish) "Pause" else "Jeda")
                            )
                        }
                    }
                }
            }

            state.output?.let { file ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = {
                            if (isPlaying) {
                                runCatching { player?.stop(); player?.release() }
                                player = null
                                isPlaying = false
                            } else {
                                runCatching {
                                    player?.release()
                                    player = MediaPlayer().apply {
                                        setDataSource(context, Uri.fromFile(file))
                                        prepare()
                                        setOnCompletionListener { isPlaying = false }
                                        start()
                                    }
                                    isPlaying = true
                                }
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(if (isPlaying) Icons.Filled.Stop else Icons.Filled.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(if (isPlaying) "Stop" else (if (isEnglish) "Play" else "Putar"))
                    }
                    Button(
                        onClick = {
                            val mime = when (file.extension) {
                                "m4a" -> "audio/mp4"
                                "webm" -> "audio/webm"
                                else -> "audio/wav"
                            }
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
            } ?: Spacer(Modifier.height(48.dp))
        }
    }
}
