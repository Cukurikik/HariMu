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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.harimu.app.R
import com.harimu.app.core.files.FileStorageManager
import com.harimu.app.core.toolapi.InputSpec
import com.harimu.app.core.toolapi.ToolGroup

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
                            overflow = TextOverflow.Ellipsis
                        )
                        IconButton(onClick = {
                            val newUris = selectedUris.toMutableList()
                            newUris.removeAt(index)
                            onUrisSelected(newUris)
                        }) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_remove_file))
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
                onPickClick = onPickSecondaryAudio ?: { onPickSecondaryDocument?.invoke(arrayOf("audio/*")) },
                onClearClick = { onSecondaryUriSelected(null) },
                icon = Icons.Filled.MusicNote
            )
            InputSpec.VideoAndImage -> SecondaryFilePicker(
                title = stringResource(R.string.action_pick_image_overlay),
                selectedName = secondaryName,
                fallbackName = stringResource(R.string.fallback_image_name),
                onPickClick = onPickSecondaryImage ?: { onPickSingleImage?.invoke() },
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
                onPickClick = onPickSecondaryImage ?: { onPickSingleImage?.invoke() },
                onClearClick = { onSecondaryUriSelected(null) },
                icon = Icons.Filled.Image
            )
            InputSpec.AudioAndWatermarkAudio -> SecondaryFilePicker(
                title = stringResource(R.string.action_pick_watermark_audio),
                selectedName = secondaryName,
                fallbackName = stringResource(R.string.fallback_audio_name),
                onPickClick = onPickSecondaryAudio ?: { onPickSecondaryDocument?.invoke(arrayOf("audio/*")) },
                onClearClick = { onSecondaryUriSelected(null) },
                icon = Icons.Filled.MusicNote
            )
            InputSpec.PdfAndImage -> SecondaryFilePicker(
                title = stringResource(R.string.action_pick_pdf_watermark_image),
                selectedName = secondaryName,
                fallbackName = stringResource(R.string.fallback_image_name),
                onPickClick = onPickSecondaryImage ?: { onPickSingleImage?.invoke() },
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
            modifier = Modifier.fillMaxWidth(),
            icon = { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) }
        ) {
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
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onClearClick) {
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
        InputSpec.PdfAndImage,
        InputSpec.VideoAndImage,
        InputSpec.AudioAndImage -> {
            selectedUris.firstOrNull()?.let { uri ->
                ImagePreviewCard(uri = uri, storage = storage, modifier = modifier)
            }
            secondaryUri?.let { uri ->
                ImagePreviewCard(uri = uri, storage = storage, modifier = modifier, title = "Secondary Image")
            }
        }
        InputSpec.SingleVideo,
        InputSpec.MultipleVideos,
        InputSpec.VideoAndAudio,
        InputSpec.VideoAndImage,
        InputSpec.VideoAndSubtitle,
        InputSpec.VideoAndMedia -> {
            selectedUris.firstOrNull()?.let { uri ->
                VideoPreviewCard(uri = uri, modifier = modifier)
            }
        }
        InputSpec.SingleAudio,
        InputSpec.MultipleAudios,
        InputSpec.AudioAndImage,
        InputSpec.AudioAndWatermarkAudio -> {
            selectedUris.firstOrNull()?.let { uri ->
                AudioPreviewCard(uri = uri, modifier = modifier)
            }
        }
        InputSpec.SinglePdf,
        InputSpec.MultiplePdfs -> {
            selectedUris.firstOrNull()?.let { uri ->
                PdfPreviewCard(uri = uri, storage = storage, modifier = modifier)
            }
        }
        is InputSpec.TextInput -> {
            TextPreviewCard(
                text = params["textInput"]?.toString() ?? "",
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
    val displayName = storage.getDisplayName(uri) ?: "Image"
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
    var probe by remember(uri) { mutableStateOf<com.harimu.app.core.media.MediaProbeResult?>(null) }
    var exoPlayer by remember(uri) {
        mutableStateOf(
            androidx.media3.exoplayer.ExoPlayer.Builder(context).build().apply {
                setMediaItem(androidx.media3.common.MediaItem.fromUri(uri))
                prepare()
            }
        )
    }
    var isPlaying by remember(exoPlayer) { mutableStateOf(false) }

    DisposableEffect(exoPlayer) {
        onDispose { exoPlayer.release() }
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
            Text(text = "Video Preview", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            probe?.let { p ->
                Text(
                    text = "${p.width}×${p.height} • ${com.harimu.app.core.media.FfmpegProgressParser.formatDurationMs(p.durationMs)}",
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
                        contentDescription = if (isPlaying) "Pause" else "Play"
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
    var probe by remember(uri) { mutableStateOf<com.harimu.app.core.media.MediaProbeResult?>(null) }
    var peaks by remember(uri) { mutableStateOf<List<Float>>(emptyList()) }
    var exoPlayer by remember(uri) {
        mutableStateOf(
            androidx.media3.exoplayer.ExoPlayer.Builder(context).build().apply {
                setMediaItem(androidx.media3.common.MediaItem.fromUri(uri))
                prepare()
            }
        )
    }
    var isPlaying by remember(exoPlayer) { mutableStateOf(false) }
    var currentPosMs by remember(exoPlayer) { mutableStateOf(0L) }
    var playerDurationMs by remember(exoPlayer) { mutableStateOf(0L) }

    DisposableEffect(exoPlayer) {
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onPlaybackStateChanged(playbackState: Int) {
                val dur = exoPlayer.duration
                if (dur > 0L) playerDurationMs = dur
            }
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener); exoPlayer.release() }
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
            Text(text = "Audio Preview", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
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
                        contentDescription = if (isPlaying) "Pause" else "Play"
                    )
                }
                Text(
                    text = "${com.harimu.app.core.media.FfmpegProgressParser.formatDurationMs(currentPosMs)} / ${com.harimu.app.core.media.FfmpegProgressParser.formatDurationMs(totalDurationMs)}",
                    style = MonoTextStyle,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            LaunchedEffect(exoPlayer, isPlaying) {
                while (true) {
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
    modifier: Modifier = Modifier
) {
    val displayName = storage.getDisplayName(uri) ?: "PDF"
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = "PDF Preview", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(text = displayName, style = MonoTextStyle, color = MaterialTheme.colorScheme.secondary)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                androidx.compose.material3.Icon(
                    imageVector = Icons.Filled.PictureAsPdf,
                    contentDescription = "PDF",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
            }
        }
    }
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
            Text(text = "Text Input Preview", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            if (text.isBlank()) {
                Text(text = hint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                androidx.compose.foundation.text.BasicTextField(
                    value = text,
                    onValueChange = {},
                    modifier = Modifier
                        .fillMaxWidth()
                        .minLines(6)
                        .maxLines(12)
                        .padding(8.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .clip(RoundedCornerShape(8.dp)),
                    singleLine = false,
                    readOnly = true,
                    textStyle = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}