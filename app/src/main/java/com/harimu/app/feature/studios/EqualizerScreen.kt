package com.harimu.app.feature.studios

import android.media.MediaPlayer
import android.media.audiofx.Equalizer
import android.net.Uri
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.harimu.app.AppContainer
import com.harimu.app.core.design.MonoTextStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerScreen(
    container: AppContainer,
    isEnglish: Boolean,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var selectedName by remember { mutableStateOf("") }
    var isPlaying by remember { mutableStateOf(false) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var eq by remember { mutableStateOf<Equalizer?>(null) }
    var minLevel by remember { mutableStateOf((-1500).toShort()) }
    var maxLevel by remember { mutableStateOf((1500).toShort()) }
    val bandFreqs = remember { mutableStateListOf<String>() }
    val bandLevels = remember { mutableStateListOf<Float>() }
    val presets = remember { mutableStateListOf<String>() }
    var activePreset by remember { mutableStateOf(-1) }

    fun releaseAudio() {
        runCatching { eq?.release() }
        runCatching { player?.stop(); player?.release() }
        eq = null
        player = null
        isPlaying = false
    }

    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            releaseAudio()
            selectedUri = uri
            selectedName = container.storage.getDisplayName(uri) ?: "audio"
            runCatching {
                val mp = MediaPlayer().apply {
                    setDataSource(context, uri)
                    prepare()
                    isLooping = true
                    start()
                }
                val equalizer = Equalizer(0, mp.audioSessionId).apply { enabled = true }
                val range = equalizer.bandLevelRange
                minLevel = range[0]
                maxLevel = range[1]
                bandFreqs.clear()
                bandLevels.clear()
                for (b in 0 until equalizer.numberOfBands) {
                    val hz = equalizer.getCenterFreq(b.toShort()) / 1000
                    bandFreqs.add(if (hz >= 1000) "${hz / 1000} kHz" else "$hz Hz")
                    bandLevels.add(equalizer.getBandLevel(b.toShort()).toFloat())
                }
                presets.clear()
                for (p in 0 until equalizer.numberOfPresets) {
                    presets.add(equalizer.getPresetName(p.toShort()))
                }
                player = mp
                eq = equalizer
                isPlaying = true
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { releaseAudio() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEnglish) "Live 5-Band Equalizer" else "Equalizer 5-Band Live") },
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Button(
                onClick = { audioPicker.launch(arrayOf("audio/*")) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.AudioFile, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (selectedName.isEmpty()) (if (isEnglish) "Pick Audio Track" else "Pilih File Audio") else selectedName)
            }

            if (player != null) {
                OutlinedButton(
                    onClick = {
                        val mp = player ?: return@OutlinedButton
                        if (isPlaying) {
                            mp.pause()
                            isPlaying = false
                        } else {
                            mp.start()
                            isPlaying = true
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(if (isPlaying) Icons.Filled.Stop else Icons.Filled.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (isPlaying) (if (isEnglish) "Pause Playback" else "Jeda Pemutaran") else (if (isEnglish) "Resume Playback" else "Putar Audio"))
                }

                if (presets.isNotEmpty()) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        presets.forEachIndexed { idx, name ->
                            FilterChip(
                                selected = activePreset == idx,
                                onClick = {
                                    activePreset = idx
                                    eq?.let { e ->
                                        e.usePreset(idx.toShort())
                                        for (b in 0 until e.numberOfBands) {
                                            bandLevels[b] = e.getBandLevel(b.toShort()).toFloat()
                                        }
                                    }
                                },
                                label = { Text(name) }
                            )
                        }
                    }
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        bandFreqs.forEachIndexed { idx, freqLabel ->
                            val db = bandLevels[idx] / 100f
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(freqLabel, style = MonoTextStyle)
                                Text("${String.format("%+.1f", db)} dB", style = MonoTextStyle, color = MaterialTheme.colorScheme.primary)
                            }
                            Slider(
                                value = bandLevels[idx],
                                onValueChange = { v ->
                                    activePreset = -1
                                    bandLevels[idx] = v
                                    eq?.setBandLevel(idx.toShort(), v.toInt().toShort())
                                },
                                valueRange = minLevel.toFloat()..maxLevel.toFloat()
                            )
                        }
                    }
                }
            }
        }
    }
}
