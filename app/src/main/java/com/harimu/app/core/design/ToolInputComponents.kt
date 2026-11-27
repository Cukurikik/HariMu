package com.harimu.app.core.design

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.harimu.app.R
import com.harimu.app.core.design.pdf.PdfDocumentPreviewCard
import com.harimu.app.core.design.pdf.PdfWatermarkPreviewSpec
import com.harimu.app.core.files.FileStorageManager
import com.harimu.app.core.toolapi.InputSpec
import com.harimu.app.feature.toolscreen.WaveformView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Unified file selection component supporting all InputSpec types.
 * Handles MIME compatibility, multi-file selection, and secondary inputs.
 * Pickers must be created in the parent composable using rememberLauncherForActivityResult
 * and passed as callbacks to this component.
 */
@Composable
fun ToolFileSelector(
    inputSpec: InputSpec,
    selectedUris: List<Uri>,
    secondaryUri: Uri?,
    onUrisSelected: (List<Uri>) -> Unit,
    onSecondaryUriSelected: (Uri?) -> Unit,
    storage: FileStorageManager,
    modifier: Modifier = Modifier,
    isEnglish: Boolean = false,
    // Primary pickers
    onPickSingleImage: (() -> Unit)? = null,
    onPickMultipleImages: (() -> Unit)? = null,
    onPickSingleVideo: (() -> Unit)? = null,
    onPickMultipleVideos: (() -> Unit)? = null,
    onPickSingleDocument: ((Array<String>) -> Unit)? = null,
    onPickMultipleDocuments: ((Array<String>) -> Unit)? = null,
    // Secondary pickers
    onPickSecondaryAudio: (() -> Unit)? = null,
    onPickSecondaryImage: (() -> Unit)? = null,
    onPickSecondaryDocument: ((Array<String>) -> Unit)? = null,
    onPickSecondaryMedia: (() -> Unit)? = null,
) {
    val spacing = HariMuThemeTokens.spacing
    val isMulti = when (inputSpec) {
        is InputSpec.MultipleImages,
        is InputSpec.MultipleVideos,
        is InputSpec.MultipleAudios,
        is InputSpec.MultiplePdfs,
        InputSpec.AnyFile -> true
        else -> false
    }

    // Build file names for display
    val fileNames = selectedUris.mapIndexed { idx, uri ->
        storage.getDisplayName(uri) ?: stringResource(R.string.fallback_file_name, idx + 1)
    }

    val secondaryName = secondaryUri?.let { storage.getDisplayName(it) }

    HariMuSectionCard(modifier = modifier) {
        // Title
        Text(
            text = stringResource(R.string.label_input_files),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )

        // Main file picker button
        HariMuPrimaryButton(
            text = if (isMulti) stringResource(R.string.action_pick_files) else stringResource(R.string.action_pick_file),
            onClick = {
                when (inputSpec) {
                    InputSpec.SingleImage -> onPickSingleImage?.invoke()
                    InputSpec.MultipleImages -> onPickMultipleImages?.invoke()
                    InputSpec.SingleVideo,
                    InputSpec.VideoAndAudio,
                    InputSpec.VideoAndImage,
                    InputSpec.VideoAndSubtitle,
                    InputSpec.VideoAndMedia -> onPickSingleVideo?.invoke()
                    InputSpec.MultipleVideos -> onPickMultipleVideos?.invoke()
                    InputSpec.SingleAudio,
                    InputSpec.AudioAndImage,
                    InputSpec.AudioAndWatermarkAudio -> onPickSingleDocument?.invoke(arrayOf("audio/*"))
                    InputSpec.MultipleAudios -> onPickMultipleDocuments?.invoke(arrayOf("audio/*"))
                    InputSpec.SinglePdf,
                    InputSpec.PdfAndImage -> onPickSingleDocument?.invoke(arrayOf("application/pdf"))
                    InputSpec.MultiplePdfs -> onPickMultipleDocuments?.invoke(arrayOf("application/pdf"))
                    InputSpec.ZipFile -> onPickSingleDocument?.invoke(arrayOf("application/zip", "application/x-zip-compressed", "*/*"))
                    InputSpec.AnyFile -> onPickMultipleDocuments?.invoke(arrayOf("*/*"))
                    else -> Unit
                }
            },
            icon = Icons.Filled.AttachFile,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("pick_input_button")
        )

        // Selected files list
        if (fileNames.isNotEmpty()) {
            HariMuInsetPanel {
                fileNames.forEachIndexed { index, name ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${index + 1}. $name",
                            style = MonoTextStyle,
                            color = MaterialTheme.colorScheme.secondary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = {
                                val newUris = selectedUris.toMutableList()
                                newUris.removeAt(index)
                                onUrisSelected(newUris)
                            },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_delete))
                        }
                    }
                }
            }
        }

        // Secondary input for compound specs
        when (inputSpec) {
            InputSpec.VideoAndAudio -> SecondaryFilePicker(
                title = stringResource(R.string.action_pick_audio_bg),
                selectedName = secondaryName,
                fallbackName = stringResource(R.string.fallback_audio_name),
                onPickClick = { onPickSecondaryAudio?.invoke() ?: onPickSecondaryDocument?.invoke(arrayOf("audio/*")) },
                onClearClick = { onSecondaryUriSelected(null) },
                icon = Icons.Filled.MusicNote
            )
            InputSpec.VideoAndImage -> SecondaryFilePicker(
                title = stringResource(R.string.action_pick_image_overlay),
                selectedName = secondaryName,
                fallbackName = stringResource(R.string.fallback_image_name),
                onPickClick = { onPickSecondaryImage?.invoke() ?: onPickSingleImage?.invoke() },
                onClearClick = { onSecondaryUriSelected(null) },
                icon = Icons.Filled.Image
            )
            InputSpec.VideoAndSubtitle -> SecondaryFilePicker(
                title = stringResource(R.string.action_pick_subtitle_file),
                selectedName = secondaryName,
                fallbackName = stringResource(R.string.fallback_subtitle_name),
                onPickClick = { onPickSecondaryDocument?.invoke(arrayOf("application/x-subrip", "text/vtt", "text/plain", "*/*")) },
                onClearClick = { onSecondaryUriSelected(null) },
                icon = Icons.Filled.Description
            )
            InputSpec.VideoAndMedia -> SecondaryFilePicker(
                title = stringResource(R.string.action_pick_bg_media),
                selectedName = secondaryName,
                fallbackName = stringResource(R.string.fallback_media_name),
                onPickClick = { onPickSecondaryMedia?.invoke() },
                onClearClick = { onSecondaryUriSelected(null) },
                icon = Icons.Filled.Folder
            )
            InputSpec.AudioAndImage -> SecondaryFilePicker(
                title = stringResource(R.string.action_pick_cover_image),
                selectedName = secondaryName,
                fallbackName = stringResource(R.string.fallback_cover_name),
                onPickClick = { onPickSecondaryImage?.invoke() ?: onPickSingleImage?.invoke() },
                onClearClick = { onSecondaryUriSelected(null) },
                icon = Icons.Filled.Image
            )
            InputSpec.AudioAndWatermarkAudio -> SecondaryFilePicker(
                title = stringResource(R.string.action_pick_watermark_audio),
                selectedName = secondaryName,
                fallbackName = stringResource(R.string.fallback_audio_name),
                onPickClick = { onPickSecondaryAudio?.invoke() ?: onPickSecondaryDocument?.invoke(arrayOf("audio/*")) },
                onClearClick = { onSecondaryUriSelected(null) },
                icon = Icons.Filled.MusicNote
            )
            InputSpec.PdfAndImage -> SecondaryFilePicker(
                title = stringResource(R.string.action_pick_pdf_watermark_image),
                selectedName = secondaryName,
                fallbackName = stringResource(R.string.fallback_image_name),
                onPickClick = { onPickSecondaryImage?.invoke() ?: onPickSingleImage?.invoke() },
                onClearClick = { onSecondaryUriSelected(null) },
                icon = Icons.Filled.Image
            )
            else -> Unit
        }
    }
}

@Composable
private fun SecondaryFilePicker(
    title: String,
    selectedName: String?,
    fallbackName: String,
    onPickClick: () -> Unit,
    onClearClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    val spacing = HariMuThemeTokens.spacing
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        OutlinedButton(
            onClick = onPickClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(spacing.xs))
            Text(title)
        }
        selectedName?.let { name ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = name,
                    style = MonoTextStyle,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onClearClick,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_clear_secondary))
                }
            }
        }
    }
}

/**
 * Input preview component for images, videos, audio, PDFs, and text.
 * Shows appropriate preview based on input type and selected URIs.
 */
@Composable
fun ToolInputPreview(
    inputSpec: InputSpec,
    selectedUris: List<Uri>,
    secondaryUri: Uri?,
    params: Map<String, Any>,
    storage: FileStorageManager,
    modifier: Modifier = Modifier,
    isEnglish: Boolean = false
) {
    when (inputSpec) {
        InputSpec.SingleImage,
        InputSpec.MultipleImages,
        InputSpec.VideoAndImage,
        InputSpec.AudioAndImage -> {
            selectedUris.firstOrNull()?.let { uri ->
                ImagePreviewCard(uri = uri, storage = storage, modifier = modifier)
            }
            secondaryUri?.let { uri ->
                ImagePreviewCard(
                    uri = uri,
                    storage = storage,
                    modifier = modifier,
                    title = stringResource(R.string.preview_secondary_image)
                )
            }
        }
        InputSpec.PdfAndImage -> {
            val wmSpec = PdfWatermarkPreviewSpec(
                isImage = params["mode"]?.toString() == "Gambar",
                text = params["text"]?.toString().orEmpty(),
                imageUri = secondaryUri,
                opacityPct = (params["opacity"] as? Number)?.toInt() ?: 25,
                angleDeg = (params["angle"] as? Number)?.toInt() ?: -35,
                sizePct = (params["size"] as? Number)?.toInt() ?: 10
            )
            selectedUris.firstOrNull()?.let { uri ->
                PdfPreviewCard(
                    uri = uri,
                    storage = storage,
                    isEnglish = isEnglish,
                    params = params,
                    watermarkSpec = wmSpec,
                    modifier = modifier
                )
            }
            secondaryUri?.let { uri ->
                ImagePreviewCard(
                    uri = uri,
                    storage = storage,
                    modifier = modifier,
                    title = stringResource(R.string.action_pick_pdf_watermark_image)
                )
            }
        }
        InputSpec.SingleVideo,
        InputSpec.MultipleVideos,
        InputSpec.VideoAndAudio,
        InputSpec.VideoAndSubtitle,
        InputSpec.VideoAndMedia -> {
            selectedUris.firstOrNull()?.let { uri ->
                VideoPreviewCard(uri = uri, modifier = modifier)
            }
        }
        InputSpec.SingleAudio,
        InputSpec.MultipleAudios,
        InputSpec.AudioAndWatermarkAudio -> {
            selectedUris.firstOrNull()?.let { uri ->
                AudioPreviewCard(uri = uri, modifier = modifier)
            }
        }
        InputSpec.SinglePdf,
        InputSpec.MultiplePdfs -> {
            selectedUris.firstOrNull()?.let { uri ->
                PdfPreviewCard(
                    uri = uri,
                    storage = storage,
                    isEnglish = isEnglish,
                    params = params,
                    modifier = modifier
                )
            }
        }
        is InputSpec.TextInput -> {
            TextPreviewCard(
                text = params["textInput"]?.toString() ?: "",
                hint = if (isEnglish) inputSpec.hintEn else inputSpec.hintId,
                modifier = modifier
            )
        }
        else -> Unit
    }
}

@Composable
private fun ImagePreviewCard(
    uri: Uri,
    storage: FileStorageManager,
    modifier: Modifier = Modifier,
    title: String? = null
) {
    val displayName = storage.getDisplayName(uri) ?: stringResource(R.string.fallback_image_name)
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            title?.let {
                Text(text = it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            Text(text = displayName, style = MonoTextStyle, color = MaterialTheme.colorScheme.secondary)
            AsyncImage(
                model = uri,
                contentDescription = displayName,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
        }
    }
}

@Composable
private fun VideoPreviewCard(
    uri: Uri,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var probe by remember(uri) { mutableStateOf<com.harimu.app.core.media.MediaProbeResult?>(null) }
    val exoPlayer = remember(uri) {
        androidx.media3.exoplayer.ExoPlayer.Builder(context.applicationContext).build().apply {
            setMediaItem(androidx.media3.common.MediaItem.fromUri(uri))
            prepare()
        }
    }
    var isPlaying by remember(exoPlayer) { mutableStateOf(false) }
    var playerError by remember(exoPlayer) { mutableStateOf<String?>(null) }

    DisposableEffect(exoPlayer, lifecycleOwner) {
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == androidx.media3.common.Player.STATE_READY) {
                    playerError = null
                }
            }
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                isPlaying = false
                playerError = error.localizedMessage ?: "Playback error"
            }
        }
        val lifecycleObserver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                exoPlayer.pause()
            }
        }
        exoPlayer.addListener(listener)
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    LaunchedEffect(uri) {
        val probed = withContext(Dispatchers.IO) { com.harimu.app.core.media.MediaProbe.probe(context, uri) }
        probe = probed
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = stringResource(R.string.preview_video_title), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            probe?.let { p ->
                Text(
                    text = "${p.orientedWidth}×${p.orientedHeight} • ${com.harimu.app.core.media.FfmpegProgressParser.formatDurationMs(p.durationMs)}",
                    style = MonoTextStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black)
            ) {
                androidx.media3.ui.compose.ContentFrame(
                    player = exoPlayer,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
                if (playerError != null) {
                    Text(
                        text = stringResource(R.string.video_preview_error),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.72f))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
                androidx.compose.material3.FilledIconButton(
                    onClick = { if (isPlaying) exoPlayer.pause() else exoPlayer.play() },
                    colors = androidx.compose.material3.IconButtonDefaults.filledIconButtonColors(
                        containerColor = Color.Black.copy(alpha = 0.65f),
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(10.dp)
                        .size(48.dp)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = stringResource(if (isPlaying) R.string.cd_pause else R.string.cd_play)
                    )
                }
            }
        }
    }
}

@Composable
private fun AudioPreviewCard(
    uri: Uri,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var probe by remember(uri) { mutableStateOf<com.harimu.app.core.media.MediaProbeResult?>(null) }
    var peaks by remember(uri) { mutableStateOf<List<Float>>(emptyList()) }
    val exoPlayer = remember(uri) {
        androidx.media3.exoplayer.ExoPlayer.Builder(context.applicationContext).build().apply {
            setMediaItem(androidx.media3.common.MediaItem.fromUri(uri))
            prepare()
        }
    }
    var isPlaying by remember(exoPlayer) { mutableStateOf(false) }
    var currentPosMs by remember(exoPlayer) { mutableStateOf(0L) }
    var playerDurationMs by remember(exoPlayer) { mutableStateOf(0L) }

    DisposableEffect(exoPlayer, lifecycleOwner) {
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onPlaybackStateChanged(playbackState: Int) {
                val dur = exoPlayer.duration
                if (dur > 0L) playerDurationMs = dur
            }
        }
        val lifecycleObserver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                exoPlayer.pause()
            }
        }
        exoPlayer.addListener(listener)
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    LaunchedEffect(uri) {
        val probed = withContext(Dispatchers.IO) { com.harimu.app.core.media.MediaProbe.probe(context, uri) }
        probe = probed
        val wave = com.harimu.app.core.media.AudioWaveformExtractor.extractPeaks(context, uri, barCount = 96)
        peaks = wave.peaks
    }

    val totalDurationMs = (probe?.durationMs?.takeIf { it > 0L } ?: playerDurationMs.takeIf { it > 0L } ?: 10_000L)
    val playheadFraction = (currentPosMs.toFloat() / totalDurationMs.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = stringResource(R.string.preview_audio_title), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            probe?.let { p ->
                Text(
                    text = "${com.harimu.app.core.media.FfmpegProgressParser.formatDurationMs(p.durationMs)} • ${p.audioSampleRateHz} Hz • ${p.audioCodec.uppercase()}",
                    style = MonoTextStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            WaveformView(
                peaks = peaks,
                playheadFraction = playheadFraction,
                modifier = Modifier.fillMaxWidth().height(84.dp).testTag("audio_preview_waveform")
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                androidx.compose.material3.FilledIconButton(
                    onClick = { if (isPlaying) exoPlayer.pause() else exoPlayer.play() },
                    colors = androidx.compose.material3.IconButtonDefaults.filledIconButtonColors(
                        containerColor = HariMuColors.GroupAudio,
                        contentColor = Color.White
                    ),
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = stringResource(if (isPlaying) R.string.cd_pause else R.string.cd_play)
                    )
                }
                Text(
                    text = "${com.harimu.app.core.media.FfmpegProgressParser.formatDurationMs(currentPosMs)} / ${com.harimu.app.core.media.FfmpegProgressParser.formatDurationMs(totalDurationMs)}",
                    style = MonoTextStyle,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            LaunchedEffect(exoPlayer, isPlaying) {
                while (isPlaying) {
                    currentPosMs = exoPlayer.currentPosition.coerceAtLeast(0L)
                    kotlinx.coroutines.delay(120L)
                }
            }
        }
    }
}

@Composable
private fun PdfPreviewCard(
    uri: Uri,
    storage: FileStorageManager,
    isEnglish: Boolean,
    params: Map<String, Any>,
    modifier: Modifier = Modifier,
    watermarkSpec: PdfWatermarkPreviewSpec? = null
) {
    val password = params["password"]?.toString()
        ?: params["userPwd"]?.toString()
        ?: ""
    val previewRotationDeg = params["angle"]?.toString()?.toIntOrNull() ?: 0
    PdfDocumentPreviewCard(
        uri = uri,
        storage = storage,
        isEnglish = isEnglish,
        password = password,
        previewRotationDeg = previewRotationDeg,
        watermarkSpec = watermarkSpec,
        maxHeightDp = 240.dp,
        modifier = modifier
    )
}

@Composable
private fun TextPreviewCard(
    text: String,
    hint: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = stringResource(R.string.preview_text_title), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            if (text.isBlank()) {
                Text(text = hint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                BasicTextField(
                    value = text,
                    onValueChange = {},
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .padding(8.dp),
                    minLines = 6,
                    maxLines = 12,
                    singleLine = false,
                    readOnly = true,
                    textStyle = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}