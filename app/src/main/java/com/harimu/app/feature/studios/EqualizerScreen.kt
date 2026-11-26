package com.harimu.app.feature.studios

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SaveAlt
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.harimu.app.AppContainer
import com.harimu.app.R
import com.harimu.app.core.data.AppSettings
import com.harimu.app.core.design.EngineBadge
import com.harimu.app.core.design.ErrorState
import com.harimu.app.core.design.HariMuColors
import com.harimu.app.core.design.HariMuTheme
import com.harimu.app.core.design.MonoTextStyle
import com.harimu.app.core.design.ParamDropdown
import com.harimu.app.core.design.ProgressSheet
import com.harimu.app.core.design.ResultCard
import com.harimu.app.core.design.ThemeMode
import com.harimu.app.core.design.audio.AudioTimelineMath
import com.harimu.app.core.media.AudioBatch2Math
import com.harimu.app.core.media.AudioToolMath
import com.harimu.app.core.media.AudioWaveformExtractor
import com.harimu.app.core.media.DefaultAudioEncoderCapabilities
import com.harimu.app.core.media.MediaJob
import com.harimu.app.core.toolapi.ToolOutputFile
import com.harimu.app.core.toolapi.ToolProgress
import com.harimu.app.core.toolapi.ToolResult
import com.harimu.app.feature.toolscreen.AudioBeforeAfterPreviewCard
import com.harimu.app.feature.toolscreen.WaveformView
import com.harimu.app.feature.toolscreen.uploadToCloud
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerScreen(
    container: AppContainer,
    isEnglish: Boolean,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by container.settings.settingsFlow.collectAsStateWithLifecycle(initialValue = AppSettings())

    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var selectedName by remember { mutableStateOf("") }
    var isPlaying by remember { mutableStateOf(false) }
    var bypassEq by remember { mutableStateOf(false) }
    var activePresetId by remember { mutableStateOf("flat") }
    var preampDb by remember { mutableFloatStateOf(0f) }
    val bandGains = remember { mutableStateListOf<Float>().apply { repeat(10) { add(0f) } } }

    val fxController = remember { EqualizerFxController() }
    var fxStatus by remember { mutableStateOf(EqLiveEngineStatus()) }
    var waveformPeaks by remember { mutableStateOf<List<Float>>(emptyList()) }
    var waveDurationMs by remember { mutableLongStateOf(0L) }
    var currentPosMs by remember { mutableLongStateOf(0L) }

    var exportFormat by remember { mutableStateOf("MP3") }
    var exportJob by remember { mutableStateOf<Job?>(null) }
    var exportProgress by remember { mutableStateOf<ToolProgress?>(null) }
    var isExporting by remember { mutableStateOf(false) }
    var pendingSaveFile by remember { mutableStateOf<ToolOutputFile?>(null) }

    val savedToast = stringResource(R.string.toast_saved)
    val saveFailedToast = stringResource(R.string.toast_save_failed)

    LaunchedEffect(selectedUri) {
        val uri = selectedUri
        if (uri == null) {
            waveformPeaks = emptyList()
            waveDurationMs = 0L
            currentPosMs = 0L
        } else {
            val wave = AudioWaveformExtractor.extractPeaks(context, uri, barCount = 96)
            waveformPeaks = wave.peaks
            waveDurationMs = wave.durationMs
        }
    }

    LaunchedEffect(isPlaying, selectedUri) {
        while (isPlaying) {
            currentPosMs = fxController.currentPositionMs
            if (fxController.durationMs > 0L) {
                waveDurationMs = fxController.durationMs
            }
            delay(120L)
        }
    }

    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            selectedUri = uri
            selectedName = container.storage.getDisplayName(uri) ?: "audio"
            exportProgress = null
            val res = fxController.prepareAndPlay(
                context = context,
                uri = uri,
                gainsDb = bandGains.toList(),
                preampDb = preampDb,
                bypassed = bypassEq
            )
            res.onSuccess { status ->
                fxStatus = status
                isPlaying = fxController.isPlaying
            }.onFailure { err ->
                fxStatus = EqLiveEngineStatus()
                isPlaying = false
                Toast.makeText(context, err.localizedMessage ?: "Audio FX error", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val safSaveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { destUri ->
        val src = pendingSaveFile
        if (destUri != null && src != null) {
            val ok = container.storage.copyFileToUri(src.file, destUri)
            Toast.makeText(context, if (ok) savedToast else saveFailedToast, Toast.LENGTH_SHORT).show()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            exportJob?.cancel()
            fxController.release()
        }
    }

    fun pushLiveParameters() {
        fxController.updateParameters(bandGains.toList(), preampDb, bypassEq)
    }

    fun startExport() {
        val uri = selectedUri ?: return
        fxController.pause()
        isPlaying = false
        exportJob?.cancel()
        exportJob = scope.launch {
            val validation = withContext(Dispatchers.IO) {
                AudioToolMath.validateAudioInputs(context, container.storage, listOf(uri), 1, isEnglish)
            }
            if (!validation.valid) {
                exportProgress = ToolProgress.Failed(validation.errorMessage ?: "Invalid audio")
                isExporting = false
                return@launch
            }
            val fmtCheck = AudioToolMath.validateOutputFormatAndRate(
                formatLabel = exportFormat,
                sampleRateHz = null,
                capabilities = DefaultAudioEncoderCapabilities,
                isEnglish = isEnglish
            )
            if (!fmtCheck.valid || fmtCheck.spec == null) {
                exportProgress = ToolProgress.Failed(fmtCheck.errorMessage ?: "Unsupported format")
                isExporting = false
                return@launch
            }
            val outSpec = fmtCheck.spec
            val probe = validation.probes.first()
            val eqFilter = AudioBatch2Math.buildEqualizer10BandFfmpegFilter(
                gainsDb = bandGains.toList(),
                preampDb = preampDb,
                enableLimiter = true
            )
            val parity = AudioBatch2Math.computeEqParityReport(
                gainsDb = bandGains.toList(),
                using10BandDynamicsProcessing = fxStatus.usingDynamicsProcessing,
                deviceCenterFreqsHz = fxStatus.deviceCenterFreqsHz,
                minMillibel = fxStatus.minMillibel,
                maxMillibel = fxStatus.maxMillibel,
                isEnglish = isEnglish
            )

            isExporting = true
            exportProgress = ToolProgress.Queued
            val mediaJob = MediaJob()
            mediaJob.runCommand(
                context = context,
                storage = container.storage,
                inputUris = listOf(uri),
                toolTag = "AUD_EQ10",
                outputExt = outSpec.extension,
                outputMime = outSpec.mimeType,
                expectedDurationMs = probe.durationMs,
                isEnglish = isEnglish
            ) { inputs, output ->
                buildList {
                    add("-y")
                    add("-i"); add(inputs.first().absolutePath)
                    if (eqFilter != "anull") {
                        add("-af"); add(eqFilter)
                    }
                    add("-vn")
                    addAll(AudioToolMath.buildCodecOutputArgs(outSpec))
                    add(output.absolutePath)
                }
            }
                .catch { e ->
                    isExporting = false
                    exportProgress = ToolProgress.Failed(e.localizedMessage ?: "Export failed")
                }
                .collect { prog ->
                    when (prog) {
                        is ToolProgress.Done -> {
                            isExporting = false
                            val firstOut = prog.result.outputs.firstOrNull()
                            val parityLine = if (isEnglish) parity.summaryEn else parity.summaryId
                            val decoratedResult = ToolResult(
                                outputs = prog.result.outputs,
                                textOutput = "${prog.result.textOutput.orEmpty()}\n\n$parityLine\nFFmpeg Filter: $eqFilter".trim(),
                                summary = prog.result.summary
                            )
                            val eqTitleStr = if (isEnglish) "Pro 10-Band Equalizer" else "Pro Equalizer 10-Band"
                            if (firstOut != null) {
                                container.workspace.recordHistory(
                                    toolId = "AUD-02",
                                    toolTitle = eqTitleStr,
                                    groupName = "AUDIO",
                                    outputFile = firstOut.file,
                                    mimeType = firstOut.mimeType,
                                    summary = decoratedResult.summary,
                                    success = true
                                )
                            }
                            exportProgress = ToolProgress.Done(decoratedResult)
                        }
                        is ToolProgress.Failed -> {
                            isExporting = false
                            exportProgress = prog
                        }
                        else -> exportProgress = prog
                    }
                }
        }
    }

    val parityReport = remember(bandGains.toList(), fxStatus, isEnglish) {
        AudioBatch2Math.computeEqParityReport(
            gainsDb = bandGains.toList(),
            using10BandDynamicsProcessing = fxStatus.usingDynamicsProcessing,
            deviceCenterFreqsHz = fxStatus.deviceCenterFreqsHz,
            minMillibel = fxStatus.minMillibel,
            maxMillibel = fxStatus.maxMillibel,
            isEnglish = isEnglish
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.eq_title), maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("eq_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            activePresetId = "flat"
                            preampDb = 0f
                            for (i in 0 until 10) bandGains[i] = 0f
                            pushLiveParameters()
                        },
                        modifier = Modifier.testTag("eq_reset_button")
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.eq_reset_flat))
                    }
                }
            )
        }
    ) { padding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.TopCenter
        ) {
            val isWide = maxWidth >= 720.dp
            val totalDurMs = maxOf(fxController.durationMs, waveDurationMs, 1_000L)

            val leftPaneContent: @Composable () -> Unit = {
                Button(
                    onClick = { audioPicker.launch(arrayOf("audio/*")) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("eq_pick_audio_button")
                ) {
                    Icon(Icons.Filled.AudioFile, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (selectedName.isEmpty()) stringResource(R.string.eq_pick_audio) else selectedName, maxLines = 1)
                }

                // Live Playback, Waveform Scrubbing & Bypass Bar
                if (selectedUri != null) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            WaveformView(
                                peaks = waveformPeaks,
                                playheadFraction = (currentPosMs.toFloat() / totalDurMs.toFloat()).coerceIn(0f, 1f),
                                activeColor = HariMuColors.GroupAudio,
                                playedColor = HariMuColors.TealEmerald,
                                onSeekFraction = { frac ->
                                    val targetMs = (frac * totalDurMs).toLong()
                                    fxController.seekTo(targetMs)
                                    currentPosMs = targetMs
                                },
                                modifier = Modifier.testTag("eq_waveform_view")
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${AudioTimelineMath.formatDurationMs(currentPosMs)} / ${AudioTimelineMath.formatDurationMs(totalDurMs)}",
                                    style = MonoTextStyle,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(stringResource(R.string.eq_bypass_label), style = MaterialTheme.typography.bodyMedium)
                                    Switch(
                                        checked = bypassEq,
                                        onCheckedChange = { checked ->
                                            bypassEq = checked
                                            pushLiveParameters()
                                        },
                                        modifier = Modifier.testTag("eq_bypass_switch")
                                    )
                                }
                            }
                            OutlinedButton(
                                onClick = {
                                    if (fxController.isLoaded) {
                                        isPlaying = fxController.togglePlayPause()
                                    } else {
                                        selectedUri?.let { uri ->
                                            fxController.prepareAndPlay(context, uri, bandGains.toList(), preampDb, bypassEq)
                                                .onSuccess {
                                                    fxStatus = it
                                                    isPlaying = fxController.isPlaying
                                                }
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("eq_play_pause_button")
                            ) {
                                Icon(if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    if (isPlaying) stringResource(R.string.eq_pause_live)
                                    else stringResource(R.string.eq_play_live)
                                )
                            }
                        }
                    }
                }

                // EQ Frequency Response Curve Canvas
                EqResponseCurveCard(
                    gainsDb = bandGains.toList(),
                    bypassed = bypassEq
                )

                // Studio Presets
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.eq_presets_title), style = MaterialTheme.typography.titleSmall)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AudioBatch2Math.EQ_10_PRESETS.forEach { preset ->
                            FilterChip(
                                selected = activePresetId == preset.id,
                                onClick = {
                                    activePresetId = preset.id
                                    preset.gainsDb.forEachIndexed { idx, gain ->
                                        bandGains[idx] = gain
                                    }
                                    pushLiveParameters()
                                },
                                label = { Text(if (isEnglish) preset.labelEn else preset.labelId) },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            )
                        }
                    }
                }
            }

            val rightPaneContent: @Composable () -> Unit = {
                // 10-Band EQ Sliders + Preamp
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(stringResource(R.string.eq_preamp_label), style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = String.format(Locale.US, "%+.1f dB", preampDb),
                                style = MonoTextStyle,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        Slider(
                            value = preampDb,
                            onValueChange = { v ->
                                preampDb = v
                                pushLiveParameters()
                            },
                            valueRange = -6f..6f,
                            steps = 23
                        )

                        AudioBatch2Math.EQ_10_BAND_LABELS.forEachIndexed { idx, freqLabel ->
                            val db = bandGains[idx]
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(freqLabel, style = MonoTextStyle)
                                Text(
                                    text = String.format(Locale.US, "%+.1f dB", db),
                                    style = MonoTextStyle,
                                    color = if (bypassEq) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
                                )
                            }
                            Slider(
                                value = db,
                                onValueChange = { v ->
                                    activePresetId = "custom"
                                    bandGains[idx] = (v * 2f).toInt() / 2f
                                    pushLiveParameters()
                                },
                                valueRange = -12f..12f
                            )
                        }
                    }
                }

                // Parity & Active Engine Info Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                    modifier = Modifier.testTag("eq_parity_card")
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(stringResource(R.string.eq_parity_title), style = MaterialTheme.typography.titleSmall)
                            EngineBadge(engine = if (fxStatus.usingDynamicsProcessing) "DP-10" else "FX+FF")
                        }
                        Text(
                            text = when {
                                !fxStatus.active -> stringResource(R.string.eq_engine_idle)
                                fxStatus.usingDynamicsProcessing -> stringResource(R.string.eq_engine_dp)
                                else -> stringResource(R.string.eq_engine_legacy, fxStatus.bandCount)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (fxStatus.active) {
                            Text(
                                text = if (isEnglish) parityReport.summaryEn else parityReport.summaryId,
                                style = MonoTextStyle,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                // FFmpeg Export Section
                if (selectedUri != null) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            ParamDropdown(
                                label = stringResource(R.string.eq_export_format),
                                options = listOf("MP3", "WAV", "FLAC", "M4A (AAC)"),
                                selectedOption = exportFormat,
                                onOptionSelected = { exportFormat = it }
                            )
                            Button(
                                onClick = { startExport() },
                                enabled = !isExporting,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .testTag("eq_export_button")
                            ) {
                                Icon(Icons.Filled.SaveAlt, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.eq_export_button))
                            }
                        }
                    }
                }

                when (val prog = exportProgress) {
                    is ToolProgress.Queued -> ProgressSheet(
                        percent = null,
                        message = stringResource(R.string.progress_queued),
                        onCancel = { exportJob?.cancel(); isExporting = false; exportProgress = null }
                    )
                    is ToolProgress.Running -> ProgressSheet(
                        percent = prog.percent,
                        message = prog.message,
                        onCancel = { exportJob?.cancel(); isExporting = false; exportProgress = null }
                    )
                    is ToolProgress.Failed -> ErrorState(
                        message = prog.error,
                        onRetry = { startExport() }
                    )
                    is ToolProgress.Done -> {
                        val outFile = prog.result.outputs.firstOrNull()
                        if (outFile != null) {
                            AudioBeforeAfterPreviewCard(
                                inputUri = selectedUri,
                                outputFile = outFile.file,
                                showTrimControls = false,
                                startSec = 0f,
                                endSec = 0f,
                                onRangeChange = { _, _ -> }
                            )
                        }
                        ResultCard(
                            summary = prog.result.summary,
                            onOpen = outFile?.let { out ->
                                {
                                    runCatching {
                                        context.startActivity(
                                            Intent.createChooser(container.storage.buildOpenIntent(out.file, out.mimeType), out.displayName)
                                        )
                                    }
                                }
                            },
                            onShare = outFile?.let { out ->
                                {
                                    context.startActivity(
                                        Intent.createChooser(container.storage.buildShareIntent(out.file, out.mimeType), out.displayName)
                                    )
                                }
                            },
                            onSave = outFile?.let { out ->
                                {
                                    val defaultTree = settings.outputFolderUri
                                    if (defaultTree.isNotBlank()) {
                                        scope.launch(Dispatchers.IO) {
                                            val saved = container.storage.saveToTreeUri(out.file, defaultTree.toUri(), out.mimeType)
                                            withContext(Dispatchers.Main) {
                                                if (saved != null) {
                                                    Toast.makeText(context, savedToast, Toast.LENGTH_SHORT).show()
                                                } else {
                                                    pendingSaveFile = out
                                                    safSaveLauncher.launch(out.displayName)
                                                }
                                            }
                                        }
                                    } else {
                                        pendingSaveFile = out
                                        safSaveLauncher.launch(out.displayName)
                                    }
                                }
                            },
                            onUploadToCloud = outFile?.let { out ->
                                { uploadToCloud(out, container, settings, scope, context, isEnglish) }
                            }
                        ) {
                            prog.result.textOutput?.let { txt ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .padding(10.dp)
                                ) {
                                    Text(txt, style = MonoTextStyle)
                                }
                            }
                        }
                    }
                    null -> Unit
                }
            }

            if (isWide) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        leftPaneContent()
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        rightPaneContent()
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 680.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    leftPaneContent()
                    rightPaneContent()
                }
            }
        }
    }
}

@Composable
fun EqResponseCurveCard(
    gainsDb: List<Float>,
    bypassed: Boolean,
    modifier: Modifier = Modifier
) {
    val lineColor = if (bypassed) MaterialTheme.colorScheme.onSurfaceVariant else HariMuColors.ElectricOrange
    val fillTop = lineColor.copy(alpha = 0.25f)
    val fillBottom = lineColor.copy(alpha = 0.02f)
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)
    val zeroColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Filled.Equalizer, contentDescription = null, tint = lineColor)
                    Text(stringResource(R.string.eq_curve_title), style = MaterialTheme.typography.titleSmall)
                }
                Text("±12 dB", style = MonoTextStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                    .padding(horizontal = 10.dp, vertical = 10.dp)
            ) {
                val safe = AudioBatch2Math.normalizeBandGains(gainsDb)
                val midY = size.height / 2f
                val stepX = size.width / (safe.size - 1).coerceAtLeast(1)

                // Horizontal grid lines (+6, 0, -6 dB)
                drawLine(gridColor, Offset(0f, size.height * 0.25f), Offset(size.width, size.height * 0.25f), 1.5f)
                drawLine(zeroColor, Offset(0f, midY), Offset(size.width, midY), 2f)
                drawLine(gridColor, Offset(0f, size.height * 0.75f), Offset(size.width, size.height * 0.75f), 1.5f)

                val pts = safe.mapIndexed { idx, g ->
                    val effective = if (bypassed) 0f else g
                    val x = idx * stepX
                    val y = (midY - (effective / 12f) * (size.height * 0.46f)).coerceIn(2f, size.height - 2f)
                    Offset(x, y)
                }

                val curvePath = Path().apply {
                    moveTo(pts.first().x, pts.first().y)
                    for (i in 0 until pts.size - 1) {
                        val p0 = pts[i]
                        val p1 = pts[i + 1]
                        val cx = (p0.x + p1.x) / 2f
                        cubicTo(cx, p0.y, cx, p1.y, p1.x, p1.y)
                    }
                }

                val fillPath = Path().apply {
                    addPath(curvePath)
                    lineTo(pts.last().x, midY)
                    lineTo(pts.first().x, midY)
                    close()
                }
                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(listOf(fillTop, fillBottom))
                )
                drawPath(
                    path = curvePath,
                    color = lineColor,
                    style = Stroke(width = 4f, cap = StrokeCap.Round)
                )
                pts.forEach { p ->
                    drawCircle(color = lineColor, radius = 6f, center = p)
                }
            }
        }
    }
}

@Preview(showBackground = true, name = "EQ Curve Light")
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, name = "EQ Curve Dark")
@Composable
private fun PreviewEqResponseCurveCard() {
    HariMuTheme(themeMode = ThemeMode.SYSTEM, isSystemDark = false) {
        Box(modifier = Modifier.padding(12.dp)) {
            EqResponseCurveCard(
                gainsDb = listOf(5f, 4f, 2f, 0f, -1f, 0.5f, 2f, 3.5f, 4f, 4.5f),
                bypassed = false
            )
        }
    }
}
