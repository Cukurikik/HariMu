package com.harimu.app.feature.studios

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.Cap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.harimu.app.AppContainer
import com.harimu.app.R
import com.harimu.app.core.design.HariMuColors
import com.harimu.app.core.design.MonoTextStyle
import com.harimu.app.core.engines.AudioEngine
import com.harimu.app.core.files.FileStorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioStudioProScreen(
    container: AppContainer,
    isEnglish: Boolean,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val storage = container.storage

    // Track list state
    val tracks = remember { mutableStateListOf<Track>() }
    val selectedTrackIndex = remember { mutableStateOf(-1) }
    val isPlaying = remember { mutableStateOf(false) }
    val playheadMs = remember { mutableStateOf(0L) }
    val totalDurationMs = remember { mutableStateOf(0L) }
    var playbackJob = remember { mutableStateOf<Job?>(null) }
    var mediaPlayer = remember { mutableStateOf<android.media.MediaPlayer?>(null) }

    // File pickers
    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(8)) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                addTracks(uris)
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            stopPlayback()
            mediaPlayer.value?.release()
            mediaPlayer.value = null
        }
    }

    fun addTracks(uris: List<Uri>) {
        uris.forEach { uri ->
            scope.launch {
                try {
                    val pcm = AudioEngine.decodeToPcm(context, uri)
                    val name = storage.getDisplayName(uri) ?: "Track ${tracks.size + 1}"
                    val newTrack = Track(
                        id = System.currentTimeMillis().toString() + tracks.size,
                        name = name,
                        uri = uri,
                        pcm = pcm,
                        gainDb = 0f,
                        pan = 0f,
                        startMs = 0L,
                        enabled = true,
                        color = TRACK_COLORS[tracks.size % TRACK_COLORS.size]
                    )
                    tracks.add(newTrack)
                    updateTotalDuration()
                } catch (e: Exception) {
                    showSnackbar("Gagal memuat: ${e.message}")
                }
            }
        }
    }

    fun updateTotalDuration() {
        var maxEnd = 0L
        tracks.forEach { t ->
            val dur = t.pcm?.samples?.size?.let { (it / t.pcm.channels.toLong() * 1000 / t.pcm.sampleRate) } ?: 0L
            val end = t.startMs + dur
            if (end > maxEnd) maxEnd = end
        }
        totalDurationMs.value = maxEnd
    }

    fun removeTrack(index: Int) {
        if (index in tracks.indices) {
            tracks.removeAt(index)
            updateTotalDuration()
            if (selectedTrackIndex.value >= tracks.size) {
                selectedTrackIndex.value = tracks.size - 1
            }
        }
    }

    fun moveTrack(fromIndex: Int, toIndex: Int) {
        if (fromIndex in tracks.indices && toIndex in tracks.indices && fromIndex != toIndex) {
            val item = tracks.removeAt(fromIndex)
            tracks.add(toIndex, item)
        }
    }

    fun startPlayback() {
        if (tracks.isEmpty()) {
            showSnackbar("Tambahkan track terlebih dahulu")
            return
        }
        stopPlayback()

        scope.launch {
            try {
                val mixedPcm = mixTracks()
                val tempFile = File(context.cacheDir, "studio_mix_${System.currentTimeMillis()}.wav")
                AudioEngine.writeWav(mixedPcm, tempFile)

                val player = android.media.MediaPlayer().apply {
                    setDataSource(tempFile.absolutePath)
                    prepare()
                    setOnCompletionListener {
                        isPlaying.value = false
                        playheadMs.value = 0
                    }
                }
                mediaPlayer.value = player
                player.start()
                isPlaying.value = true

                // Update playhead
                playbackJob.value = scope.launch {
                    while (isPlaying.value) {
                        val pos = mediaPlayer.value?.currentPosition?.toLong() ?: 0L
                        playheadMs.value = pos
                        kotlinx.coroutines.delay(50)
                    }
                }
            } catch (e: Exception) {
                showSnackbar("Gagal memutar: ${e.message}")
            }
        }
    }

    fun stopPlayback() {
        isPlaying.value = false
        playbackJob.value?.cancel()
        playbackJob.value = null
        mediaPlayer.value?.apply {
            stop()
            reset()
        }
        mediaPlayer.value?.release()
        mediaPlayer.value = null
        playheadMs.value = 0
    }

    fun mixTracks(): AudioEngine.PcmAudio {
        if (tracks.isEmpty()) {
            return AudioEngine.PcmAudio(shortArrayOf(), 44100, 2)
        }

        // Find max duration
        var maxSamples = 0
        tracks.forEach { t ->
            t.pcm?.let { pcm ->
                val samples = pcm.samples.size
                val endSample = (t.startMs * pcm.sampleRate / 1000 * pcm.channels).toInt() + samples
                if (endSample > maxSamples) maxSamples = endSample
            }
        }

        val outChannels = 2
        val outSampleRate = tracks.firstOrNull()?.pcm?.sampleRate ?: 44100
        val output = ShortArray(maxSamples * outChannels)

        tracks.forEach { track ->
            track.pcm?.let { pcm ->
                if (!track.enabled) return@forEach
                val gain = 10f.pow(track.gainDb / 20f)
                val pan = track.pan.coerceIn(-1f, 1f)
                val leftGain = gain * (1f - max(0f, pan))
                val rightGain = gain * (1f + min(0f, pan))

                val startSample = (track.startMs * pcm.sampleRate / 1000 * pcm.channels).toInt()
                val frameCount = pcm.samples.size / pcm.channels
                val outStartFrame = (track.startMs * outSampleRate / 1000).toInt()

                for (f in 0 until frameCount) {
                    val outFrame = outStartFrame + f
                    val outIdx = outFrame * outChannels
                    if (outIdx + 1 >= output.size) break

                    var leftVal = 0f
                    var rightVal = 0f

                    if (pcm.channels == 1) {
                        val srcIdx = f
                        if (srcIdx < pcm.samples.size) {
                            val sample = pcm.samples[srcIdx].toFloat()
                            leftVal = sample * leftGain
                            rightVal = sample * rightGain
                        }
                    } else if (pcm.channels == 2) {
                        val srcIdx = f * 2
                        if (srcIdx + 1 < pcm.samples.size) {
                            leftVal = pcm.samples[srcIdx].toFloat() * leftGain
                            rightVal = pcm.samples[srcIdx + 1].toFloat() * rightGain
                        }
                    }

                    output[outIdx] = (output[outIdx].toFloat() + leftVal).coerceIn(Short.MIN_VALUE.toFloat(), Short.MAX_VALUE.toFloat()).toShort()
                    output[outIdx + 1] = (output[outIdx + 1].toFloat() + rightVal).coerceIn(Short.MIN_VALUE.toFloat(), Short.MAX_VALUE.toFloat()).toShort()
                }
            }
        }

        return AudioEngine.PcmAudio(output, outSampleRate, outChannels)
    }

    fun exportMix() {
        scope.launch {
            try {
                val mixedPcm = mixTracks()
                val dest = storage.createOutputFile("AUDIO_STUDIO_PRO", "wav")
                AudioEngine.writeWav(mixedPcm, dest)
                showSnackbar("Diekspor ke ${dest.name}")
            } catch (e: Exception) {
                showSnackbar("Gagal ekspor: ${e.message}")
            }
        }
    }

    fun showSnackbar(msg: String) {
        scope.launch { snackbarHostState.showSnackbar(msg) }
    }

    fun formatTime(ms: Long): String {
        val totalSec = ms / 1000
        val min = totalSec / 60
        val sec = totalSec % 60
        return "%02d:%02d".format(min, sec)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEnglish) "Audio Studio Pro" else "Audio Studio Pro") },
                navigationIcon = {
                    IconButton(onClick = { stopPlayback(); onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = exportMix, enabled = tracks.isNotEmpty()) {
                        Icon(Icons.Filled.Save, contentDescription = "Export")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Transport controls
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isEnglish) "Transport" else "Transport",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${formatTime(playheadMs.value)} / ${formatTime(totalDurationMs.value)}",
                            style = MonoTextStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { if (isPlaying.value) stopPlayback() else startPlayback() }) {
                            Icon(
                                imageVector = if (isPlaying.value) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                                contentDescription = if (isEnglish) "Play/Stop" else "Putar/Berhenti",
                                tint = if (isPlaying.value) HariMuColors.ElectricOrange else MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(text = if (isEnglish) "Space to play/stop" else "Spasi untuk putar/berhenti", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // Track list
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = if (isEnglish) "Tracks (${tracks.size})" else "Track (${tracks.size})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Button(onClick = { audioPicker.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.AudioOnly)) }) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                            Text(text = if (isEnglish) "Add Track" else "Tambah Track")
                        }
                    }

                    if (tracks.isEmpty()) {
                        Text(
                            text = if (isEnglish) "No tracks. Tap 'Add Track' to load audio files." else "Belum ada track. Tekan 'Tambah Track' untuk memuat file audio.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            tracks.forEachIndexed { index, track ->
                                TrackRow(
                                    track = track,
                                    index = index,
                                    isSelected = selectedTrackIndex.value == index,
                                    totalDurationMs = totalDurationMs.value,
                                    playheadMs = playheadMs.value,
                                    onSelect = { selectedTrackIndex.value = index },
                                    onRemove = { removeTrack(index) },
                                    onGainChange = { track.gainDb = it },
                                    onPanChange = { track.pan = it },
                                    onStartChange = { track.startMs = it; updateTotalDuration() },
                                    onMuteToggle = { track.enabled = !track.enabled },
                                    onMoveUp = { if (index > 0) moveTrack(index, index - 1) },
                                    onMoveDown = { if (index < tracks.lastIndex) moveTrack(index, index + 1) },
                                    isEnglish = isEnglish
                                )
                            }
                        }
                    }
                }
            }

            // Selected track detail
            if (selectedTrackIndex.value != -1 && selectedTrackIndex.value < tracks.size) {
                val track = tracks[selectedTrackIndex.value]
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = if (isEnglish) "Track Detail: ${track.name}" else "Detail Track: ${track.name}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Box(
                                modifier = Modifier.size(24.dp).background(track.color).clip(RoundedCornerShape(4.dp))
                            )
                        }

                        // Gain
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Text(text = "Gain", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(80.dp))
                            Slider(
                                value = track.gainDb,
                                onValueChange = { track.gainDb = it },
                                valueRange = -24f..24f,
                                steps = 96,
                                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                                colors = SliderDefaults.colors(thumbColor = track.color, activeTrackColor = track.color)
                            )
                            Text(text = "${track.gainDb.roundToInt()} dB", style = MonoTextStyle, modifier = Modifier.width(60.dp))
                        }

                        // Pan
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Text(text = "Pan", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(80.dp))
                            Slider(
                                value = track.pan,
                                onValueChange = { track.pan = it },
                                valueRange = -1f..1f,
                                steps = 100,
                                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                                colors = SliderDefaults.colors(thumbColor = track.color, activeTrackColor = track.color)
                            )
                            Text(text = when { track.pan < -0.01 -> "L"; track.pan > 0.01 -> "R"; else -> "C" }, style = MonoTextStyle, modifier = Modifier.width(60.dp))
                        }

                        // Start offset
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Text(text = "Start", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(80.dp))
                            Slider(
                                value = (track.startMs / 1000f).coerceIn(0f, 300f),
                                onValueChange = { track.startMs = (it * 1000).toLong(); updateTotalDuration() },
                                valueRange = 0f..300f,
                                steps = 600,
                                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                                colors = SliderDefaults.colors(thumbColor = track.color, activeTrackColor = track.color)
                            )
                            Text(text = formatTime(track.startMs), style = MonoTextStyle, modifier = Modifier.width(60.dp))
                        }

                        // Trim controls (if track has audio)
                        track.pcm?.let { pcm =>
                            val durMs = (pcm.samples.size / pcm.channels.toLong() * 1000 / pcm.sampleRate)
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                Text(text = "Trim", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(80.dp))
                                Slider(
                                    value = (track.trimStartMs / 1000f).coerceIn(0f, (durMs / 1000f).coerceAtLeast(0.1f)),
                                    onValueChange = { track.trimStartMs = (it * 1000).toLong() },
                                    valueRange = 0f..(durMs / 1000f).coerceAtLeast(0.1f),
                                    steps = (durMs / 1000f * 10).coerceAtLeast(10).toInt(),
                                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                                    colors = SliderDefaults.colors(thumbColor = track.color, activeTrackColor = track.color)
                                )
                                Text(text = formatTime(track.trimStartMs), style = MonoTextStyle, modifier = Modifier.width(60.dp))
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                Text(text = "Trim End", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(80.dp))
                                Slider(
                                    value = (track.trimEndMs / 1000f).coerceIn((track.trimStartMs / 1000f).coerceAtLeast(0.1f), durMs / 1000f),
                                    onValueChange = { track.trimEndMs = (it * 1000).toLong() },
                                    valueRange = (track.trimStartMs / 1000f).coerceAtLeast(0.1f)..durMs / 1000f,
                                    steps = (durMs / 1000f * 10).coerceAtLeast(10).toInt(),
                                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                                    colors = SliderDefaults.colors(thumbColor = track.color, activeTrackColor = track.color)
                                )
                                Text(text = formatTime(track.trimEndMs), style = MonoTextStyle, modifier = Modifier.width(60.dp))
                            }
                        }

                        // Waveform preview
                        track.pcm?.let { pcm ->
                            WaveformView(
                                peaks = AudioEngine.extractWaveformPeaks(pcm, 200),
                                playheadPos = (playheadMs.value.toFloat() / totalDurationMs.value.coerceAtLeast(1)).coerceIn(0f, 1f),
                                color = track.color,
                                modifier = Modifier.fillMaxWidth().height(80.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TrackRow(
    track: Track,
    index: Int,
    isSelected: Boolean,
    totalDurationMs: Long,
    playheadMs: Long,
    onSelect: () -> Unit,
    onRemove: () -> Unit,
    onGainChange: (Float) -> Unit,
    onPanChange: (Float) -> Unit,
    onStartChange: (Long) -> Unit,
    onMuteToggle: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    isEnglish: Boolean
) {
    val color = track.color
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isSelected) color.copy(alpha = 0.15f) else Color.Transparent)
            .border(if (isSelected) BorderStroke(2.dp, color) else BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)))
            .clip(RoundedCornerShape(8.dp))
            .padding(12.dp),
        onClick = onSelect
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Drag handle
                Icon(Icons.Filled.DragIndicator, contentDescription = "Drag", tint = MaterialTheme.colorScheme.onSurfaceVariant)

                // Color indicator
                Box(modifier = Modifier.size(12.dp).background(color).clip(RoundedCornerShape(4.dp)))

                // Name
                Text(text = track.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))

                // Mute
                IconButton(onClick = onMuteToggle) {
                    Icon(Icons.Filled.VolumeUp, contentDescription = if (track.enabled) "Mute" else "Unmute", tint = if (track.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                }

                // Move up/down
                Row {
                    IconButton(onClick = onMoveUp, enabled = index > 0) {
                        Icon(Icons.Filled.VolumeDown, contentDescription = "Move up", rotationDegrees = -90f)
                    }
                    IconButton(onClick = onMoveDown, enabled = index < 999) {
                        Icon(Icons.Filled.VolumeDown, contentDescription = "Move down", rotationDegrees = 90f)
                    }
                }

                // Remove
                IconButton(onClick = onRemove) {
                    Icon(Icons.Filled.Remove, contentDescription = if (isEnglish) "Remove" else "Hapus", tint = MaterialTheme.colorScheme.error)
                }
            }

            // Mini controls
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                // Gain
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Gain", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Slider(
                            value = track.gainDb,
                            onValueChange = onGainChange,
                            valueRange = -24f..24f,
                            steps = 48,
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(thumbColor = color, activeTrackColor = color)
                        )
                        Text(text = "${track.gainDb.roundToInt()} dB", style = MonoTextStyle, modifier = Modifier.width(50.dp))
                    }
                }
                // Pan
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Pan", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Slider(
                            value = track.pan,
                            onValueChange = onPanChange,
                            valueRange = -1f..1f,
                            steps = 20,
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(thumbColor = color, activeTrackColor = color)
                        )
                        Text(text = when { track.pan < -0.01 -> "L"; track.pan > 0.01 -> "R"; else -> "C" }, style = MonoTextStyle, modifier = Modifier.width(30.dp))
                    }
                }
                // Start offset
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Start", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Slider(
                            value = (track.startMs / 1000f).coerceIn(0f, 300f),
                            onValueChange = { onStartChange((it * 1000).toLong()) },
                            valueRange = 0f..300f,
                            steps = 60,
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(thumbColor = color, activeTrackColor = color)
                        )
                        Text(text = formatTime(track.startMs), style = MonoTextStyle, modifier = Modifier.width(50.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun WaveformView(peaks: FloatArray, playheadPos: Float, color: Color, modifier: Modifier = Modifier) {
    Box(modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant).clip(RoundedCornerShape(8.dp))) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val centerY = height / 2f
            val barWidth = width / peaks.size
            val path = androidx.compose.ui.graphics.Path()

            peaks.forEachIndexed { i, peak ->
                val x = i * barWidth
                val halfH = peak * centerY * 0.9f
                path.moveTo(x, centerY - halfH)
                path.lineTo(x, centerY + halfH)
            }

            drawPath(
                path,
                color = color.copy(alpha = 0.7f),
                style = androidx.compose.ui.graphics.Stroke(width = max(1f, barWidth * 0.7f), cap = androidx.compose.ui.graphics.Cap.Round)
            )

            // Playhead
            val playheadX = playheadPos * width
            drawLine(
                color = HariMuColors.ElectricOrange,
                start = androidx.compose.ui.geometry.Offset(playheadX, 0f),
                end = androidx.compose.ui.geometry.Offset(playheadX, height),
                strokeWidth = 2f
            )
        }
    }
}

data class Track(
    val id: String,
    val name: String,
    val uri: Uri,
    val pcm: AudioEngine.PcmAudio?,
    var gainDb: Float,
    var pan: Float,
    var startMs: Long,
    var enabled: Boolean,
    val color: Color,
    var trimStartMs: Long = 0,
    var trimEndMs: Long = Long.MAX_VALUE
)

private val TRACK_COLORS = listOf(
    Color(0xFFFF6B2C), // Orange
    Color(0xFF00BFA5), // Teal
    Color(0xFF9C27B0), // Purple
    Color(0xFF2196F3), // Blue
    Color(0xFF4CAF50), // Green
    Color(0xFFE91E63), // Pink
    Color(0xFF00BCD4), // Cyan
    Color(0xFFFF9800), // Amber
)

private fun formatTime(ms: Long): String {
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return "%02d:%02d".format(min, sec)
}