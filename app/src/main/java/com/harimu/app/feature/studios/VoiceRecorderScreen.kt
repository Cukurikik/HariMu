package com.harimu.app.feature.studios

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaPlayer
import android.media.MediaRecorder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
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
import com.harimu.app.AppContainer
import com.harimu.app.R
import com.harimu.app.core.design.HariMuColors
import com.harimu.app.core.design.MonoTextStyle
import com.harimu.app.core.engines.AudioEngine
import com.harimu.app.core.engines.PcmAudio
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceRecorderScreen(
    container: AppContainer,
    isEnglish: Boolean,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var isRecording by remember { mutableStateOf(false) }
    var elapsedMs by remember { mutableLongStateOf(0L) }
    var lastFile by remember { mutableStateOf<File?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("") }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var recordJob by remember { mutableStateOf<Job?>(null) }
    val spectrumBars = remember { mutableStateListOf<Float>().apply { repeat(40) { add(0.08f) } } }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
    }

    @SuppressLint("MissingPermission")
    fun startRecording() {
        val sampleRate = 44100
        val minBuf = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(4096)

        isRecording = true
        elapsedMs = 0L
        statusText = if (isEnglish) "Recording PCM 44.1kHz..." else "Merekam PCM 44.1kHz..."

        recordJob = scope.launch(Dispatchers.IO) {
            val recorder = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBuf
            )
            val collected = ArrayList<Short>(sampleRate * 30)
            val buf = ShortArray(minBuf / 2)
            val startedAt = System.currentTimeMillis()

            try {
                recorder.startRecording()
                while (isActive && isRecording) {
                    val read = recorder.read(buf, 0, buf.size)
                    if (read > 0) {
                        for (i in 0 until read) collected.add(buf[i])
                        var peak = 0
                        for (i in 0 until read step 4) {
                            val v = abs(buf[i].toInt())
                            if (v > peak) peak = v
                        }
                        val norm = (peak.toFloat() / Short.MAX_VALUE).coerceIn(0.06f, 1f)
                        withContext(Dispatchers.Main) {
                            elapsedMs = System.currentTimeMillis() - startedAt
                            spectrumBars.removeAt(0)
                            spectrumBars.add(norm)
                        }
                    }
                }
            } finally {
                runCatching { recorder.stop() }
                runCatching { recorder.release() }
            }

            if (collected.isNotEmpty()) {
                val outFile = container.storage.createRecordingFile("wav")
                AudioEngine.writeWav(PcmAudio(collected.toShortArray(), sampleRate, 1), outFile)
                container.workspace.recordHistory(
                    toolId = "CORE-06",
                    toolTitle = if (isEnglish) "Voice Recorder" else "Voice Recorder",
                    groupName = "CORE",
                    outputFile = outFile,
                    mimeType = "audio/wav",
                    summary = "${outFile.name} (${container.storage.formatBytes(outFile.length())})"
                )
                withContext(Dispatchers.Main) {
                    lastFile = outFile
                    statusText = "${outFile.name} (${container.storage.formatBytes(outFile.length())})"
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            isRecording = false
            recordJob?.cancel()
            runCatching { player?.release() }
        }
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
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    val secs = (elapsedMs / 1000).toInt()
                    val mm = secs / 60
                    val ss = secs % 60
                    Text(
                        text = String.format("%02d:%02d", mm, ss),
                        style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                        color = if (isRecording) HariMuColors.ElectricOrange else MaterialTheme.colorScheme.onSurface
                    )

                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                    ) {
                        val barCount = spectrumBars.size
                        val gap = 6.dp.toPx()
                        val barW = ((size.width - gap * (barCount - 1)) / barCount).coerceAtLeast(3f)
                        val midY = size.height / 2f
                        val brush = Brush.verticalGradient(
                            listOf(HariMuColors.ElectricOrange, HariMuColors.TealEmerald)
                        )
                        spectrumBars.forEachIndexed { idx, amp ->
                            val h = (amp * size.height * 0.85f).coerceAtLeast(10f)
                            val x = idx * (barW + gap)
                            drawRoundRect(
                                brush = brush,
                                topLeft = Offset(x, midY - h / 2f),
                                size = Size(barW, h),
                                cornerRadius = CornerRadius(barW / 2f, barW / 2f)
                            )
                        }
                    }

                    if (statusText.isNotEmpty()) {
                        Text(statusText, style = MonoTextStyle, color = MaterialTheme.colorScheme.secondary)
                    }
                }
            }

            if (!hasPermission) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
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
                Button(
                    onClick = {
                        if (isRecording) {
                            isRecording = false
                        } else {
                            startRecording()
                        }
                    },
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isRecording) MaterialTheme.colorScheme.error else HariMuColors.ElectricOrange
                    ),
                    modifier = Modifier
                        .size(88.dp)
                        .testTag("record_toggle_button")
                ) {
                    Icon(
                        imageVector = if (isRecording) Icons.Filled.Stop else Icons.Filled.Mic,
                        contentDescription = if (isRecording) "Stop" else "Record",
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            lastFile?.let { file ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
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
                                        setDataSource(file.absolutePath)
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
                        Text(if (isPlaying) (if (isEnglish) "Stop" else "Stop") else (if (isEnglish) "Play WAV" else "Putar WAV"))
                    }
                    Button(
                        onClick = {
                            context.startActivity(
                                Intent.createChooser(container.storage.buildShareIntent(file, "audio/wav"), file.name)
                            )
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
