package com.harimu.app.core.design

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TextSnippet
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import coil.compose.AsyncImage
import com.harimu.app.R
import com.harimu.app.core.design.pdf.PdfDocumentPreviewCard
import com.harimu.app.core.files.FileStorageManager
import com.harimu.app.core.toolapi.ToolOutputFile
import com.harimu.app.core.toolapi.ToolResult
import com.harimu.app.feature.toolscreen.WaveformView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Result actions for tool outputs.
 */
data class ResultActions(
    val onOpen: ((ToolOutputFile) -> Unit)? = null,
    val onShare: ((ToolOutputFile) -> Unit)? = null,
    val onSave: ((ToolOutputFile) -> Unit)? = null,
    val onDelete: ((ToolOutputFile) -> Unit)? = null,
    val onUploadToCloud: ((ToolOutputFile) -> Unit)? = null,
    val onCopyText: ((String) -> Unit)? = null,
    val onViewDetails: ((ToolOutputFile) -> Unit)? = null
)

/**
 * Preview content generator for different MIME types.
 */
@Composable
fun ToolResultPreview(
    output: ToolOutputFile,
    storage: FileStorageManager,
    modifier: Modifier = Modifier
) {
    val mime = output.mimeType.lowercase()
    when {
        mime.startsWith("image/") -> ImageResultPreview(output = output, modifier = modifier)
        mime.startsWith("video/") -> VideoResultPreview(output = output, modifier = modifier)
        mime.startsWith("audio/") -> AudioResultPreview(output = output, modifier = modifier)
        mime == "application/pdf" -> PdfResultPreview(output = output, storage = storage, modifier = modifier)
        mime.startsWith("text/") || mime == "application/json" || mime == "application/xml" -> TextResultPreview(output = output, modifier = modifier)
        else -> GenericResultPreview(output = output, modifier = modifier)
    }
}

@Composable
private fun ImageResultPreview(output: ToolOutputFile, modifier: Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AsyncImage(
                model = output.file.toUri(),
                contentDescription = output.displayName,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
            Text(text = "${output.file.length()} bytes", style = MonoTextStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun VideoResultPreview(output: ToolOutputFile, modifier: Modifier) {
    val context = LocalContext.current
    val exoPlayer = remember(output.file.toUri()) {
        androidx.media3.exoplayer.ExoPlayer.Builder(context).build().apply {
            setMediaItem(androidx.media3.common.MediaItem.fromUri(output.file.toUri()))
            prepare()
        }
    }
    var isPlaying by remember(exoPlayer) { mutableStateOf(false) }

    DisposableEffect(exoPlayer) {
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener); exoPlayer.release() }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
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
                    modifier = Modifier.align(Alignment.BottomStart).padding(10.dp).size(48.dp)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play"
                    )
                }
            }
            Text(text = output.displayName, style = MonoTextStyle, color = MaterialTheme.colorScheme.secondary)
        }
    }
}

@Composable
private fun AudioResultPreview(output: ToolOutputFile, modifier: Modifier) {
    val context = LocalContext.current
    var peaks by remember(output.file.toUri()) { mutableStateOf<List<Float>>(emptyList()) }
    val exoPlayer = remember(output.file.toUri()) {
        androidx.media3.exoplayer.ExoPlayer.Builder(context).build().apply {
            setMediaItem(androidx.media3.common.MediaItem.fromUri(output.file.toUri()))
            prepare()
        }
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

    LaunchedEffect(output.file.toUri()) {
        val wave = com.harimu.app.core.media.AudioWaveformExtractor.extractPeaks(context, output.file.toUri(), barCount = 96)
        peaks = wave.peaks
    }

    val totalDurationMs = playerDurationMs.takeIf { it > 0L } ?: 10_000L
    val playheadFraction = (currentPosMs.toFloat() / totalDurationMs.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            WaveformView(
                peaks = peaks,
                playheadFraction = playheadFraction,
                modifier = Modifier.fillMaxWidth().height(84.dp),
                playedColor = HariMuColors.TealEmerald,
                activeColor = HariMuColors.ElectricOrange
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
            Text(text = output.displayName, style = MonoTextStyle, color = MaterialTheme.colorScheme.secondary)
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
private fun PdfResultPreview(output: ToolOutputFile, storage: FileStorageManager, modifier: Modifier) {
    PdfDocumentPreviewCard(
        uri = output.file.toUri(),
        storage = storage,
        isEnglish = false,
        maxHeightDp = 240.dp,
        title = output.displayName,
        testTag = "pdf_result_preview_card",
        modifier = modifier
    )
}

@Composable
private fun TextResultPreview(output: ToolOutputFile, modifier: Modifier) {
    val text = remember(output.file.lastModified()) {
        try {
            output.file.readText()
        } catch (e: Exception) {
            "[Cannot read text file]"
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BasicTextField(
                value = text,
                onValueChange = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(8.dp),
                minLines = 8,
                maxLines = 20,
                singleLine = false,
                readOnly = true,
                textStyle = MonoTextStyle
            )
            Text(text = "${text.length} chars", style = MonoTextStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun GenericResultPreview(output: ToolOutputFile, modifier: Modifier) {
    val (icon, label) = when {
        output.mimeType.startsWith("application/zip") -> Icons.Filled.Folder to "Archive"
        output.mimeType.startsWith("application/") -> Icons.Filled.Description to "Document"
        else -> Icons.Filled.Description to "File"
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                androidx.compose.material3.Icon(
                    imageVector = icon,
                    contentDescription = label,
                    modifier = Modifier.fillMaxSize().padding(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
            }
            Text(text = output.displayName, style = MonoTextStyle, color = MaterialTheme.colorScheme.secondary)
            Text(text = output.mimeType, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * Main result card with actions (open, share, save, delete, cloud upload, copy text).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolResultCard(
    result: ToolResult,
    storage: FileStorageManager,
    actions: ResultActions = ResultActions(),
    modifier: Modifier = Modifier,
    showPreview: Boolean = true
) {
    val spacing = HariMuThemeTokens.spacing
    val primaryOutput = result.outputs.firstOrNull()

    HariMuSectionCard(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        borderColor = HariMuThemeTokens.semantic.successBorder.copy(alpha = 0.65f)
    ) {
        // Summary header
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = Icons.Filled.CheckCircle, contentDescription = null, tint = HariMuThemeTokens.semantic.success, modifier = Modifier.size(HariMuThemeTokens.icons.md))
            Spacer(modifier = Modifier.width(spacing.xs))
            Text(text = result.summary, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
        }

        // Preview
        if (showPreview && primaryOutput != null) {
            Spacer(modifier = Modifier.height(spacing.sm))
            ToolResultPreview(output = primaryOutput, storage = storage)
        }

        // Text output
        result.textOutput?.let { textOutput ->
            Spacer(modifier = Modifier.height(spacing.sm))
            OutlinedButton(
                onClick = { actions.onCopyText?.invoke(textOutput) }
            ) {
                Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(spacing.xs))
                Text(stringResource(R.string.action_copy))
            }
        }

        // Actions
        if (primaryOutput != null) {
            Spacer(modifier = Modifier.height(spacing.sm))
            ResultActionBar(
                output = primaryOutput,
                outputCount = result.outputs.size,
                onOpen = actions.onOpen,
                onShare = actions.onShare,
                onSave = actions.onSave,
                onDelete = actions.onDelete,
                onUploadToCloud = actions.onUploadToCloud,
                onViewDetails = actions.onViewDetails
            )
        }

        // Additional outputs indicator
        if (result.outputs.size > 1) {
            Spacer(modifier = Modifier.height(spacing.xs))
            Text(
                text = stringResource(R.string.result_additional_files, result.outputs.size - 1),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ResultActionBar(
    output: ToolOutputFile,
    outputCount: Int,
    onOpen: ((ToolOutputFile) -> Unit)?,
    onShare: ((ToolOutputFile) -> Unit)?,
    onSave: ((ToolOutputFile) -> Unit)?,
    onDelete: ((ToolOutputFile) -> Unit)?,
    onUploadToCloud: ((ToolOutputFile) -> Unit)?,
    onViewDetails: ((ToolOutputFile) -> Unit)?
) {
    val spacing = HariMuThemeTokens.spacing

    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
        verticalArrangement = Arrangement.spacedBy(spacing.xs)
    ) {
        onOpen?.let {
            HariMuSecondaryButton(
                text = stringResource(R.string.action_open),
                onClick = { it(output) },
                icon = Icons.AutoMirrored.Filled.OpenInNew,
                modifier = Modifier.widthIn(min = 104.dp).weight(1f, fill = true)
            )
        }
        onShare?.let {
            HariMuSecondaryButton(
                text = stringResource(R.string.action_share),
                onClick = { it(output) },
                icon = Icons.Filled.Share,
                modifier = Modifier.widthIn(min = 104.dp).weight(1f, fill = true)
            )
        }
        onSave?.let {
            HariMuPrimaryButton(
                text = stringResource(R.string.action_save),
                onClick = { it(output) },
                icon = Icons.Filled.Download,
                containerColor = HariMuThemeTokens.semantic.success,
                contentColor = HariMuThemeTokens.semantic.onSuccess,
                modifier = Modifier.widthIn(min = 104.dp).weight(1f, fill = true)
            )
        }
        onDelete?.let {
            HariMuDestructiveButton(
                text = stringResource(R.string.action_delete),
                onClick = { it(output) },
                icon = Icons.Filled.Delete,
                modifier = Modifier.widthIn(min = 104.dp).weight(1f, fill = true)
            )
        }
        onViewDetails?.let {
            HariMuSecondaryButton(
                text = stringResource(R.string.action_details),
                onClick = { it(output) },
                icon = Icons.Filled.Visibility,
                modifier = Modifier.widthIn(min = 104.dp).weight(1f, fill = true)
            )
        }
    }

    onUploadToCloud?.let {
        HariMuSecondaryButton(
            text = stringResource(R.string.action_upload_cloud),
            onClick = { it(output) },
            icon = Icons.Filled.CloudUpload,
            modifier = Modifier.fillMaxWidth().testTag("upload_to_cloud_button")
        )
    }
}

enum class ResultAction { Open, Share, Save, Delete, Details }

/**
 * Multi-file result card for tools producing multiple outputs.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolMultiResultCard(
    outputs: List<ToolOutputFile>,
    storage: FileStorageManager,
    onItemClick: ((ToolOutputFile) -> Unit)? = null,
    onItemAction: ((ToolOutputFile, ResultAction) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    HariMuSectionCard(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(HariMuThemeTokens.spacing.sm)) {
            Text(text = stringResource(R.string.result_output_files, outputs.size), style = MaterialTheme.typography.titleSmall)

            outputs.forEachIndexed { index, output ->
                ResultItemRow(
                    index = index + 1,
                    output = output,
                    storage = storage,
                    onClick = { onItemClick?.invoke(output) },
                    onAction = { action -> onItemAction?.invoke(output, action) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ResultItemRow(
    index: Int,
    output: ToolOutputFile,
    storage: FileStorageManager,
    onClick: (() -> Unit)?,
    onAction: ((ResultAction) -> Unit)?
) {
    val mime = output.mimeType.lowercase()
    val (icon, color) = when {
        mime.startsWith("image/") -> Icons.Filled.Image to MaterialTheme.colorScheme.primary
        mime.startsWith("video/") -> Icons.Filled.PlayCircle to HariMuColors.GroupVideo
        mime.startsWith("audio/") -> Icons.Filled.MusicNote to HariMuColors.GroupAudio
        mime == "application/pdf" -> Icons.Filled.PictureAsPdf to HariMuColors.GroupPdf
        mime.startsWith("text/") -> Icons.Filled.TextSnippet to HariMuColors.GroupConverter
        else -> Icons.Filled.Description to MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(HariMuThemeTokens.spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(color.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text = "$index. ${output.displayName}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(text = "${storage.formatBytes(output.file.length())} • ${output.mimeType}", style = MonoTextStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            onAction?.let {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = { onAction(ResultAction.Open) }) { Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open") }
                    IconButton(onClick = { onAction(ResultAction.Share) }) { Icon(Icons.Filled.Share, contentDescription = "Share") }
                    IconButton(onClick = { onAction(ResultAction.Save) }) { Icon(Icons.Filled.Download, contentDescription = "Save") }
                    IconButton(onClick = { onAction(ResultAction.Delete) }) { Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }
}

/**
 * Empty result state.
 */
@Composable
fun ToolEmptyResult(
    message: String = "",
    icon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Filled.CheckCircle,
    onAction: (() -> Unit)? = null,
    actionLabel: String? = null,
    modifier: Modifier = Modifier
) {
    val spacing = HariMuThemeTokens.spacing
    HariMuSectionCard(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        borderColor = MaterialTheme.colorScheme.outlineVariant
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacing.md)
        ) {
            Box(
                modifier = Modifier.size(HariMuThemeTokens.icons.emptyContainer).clip(HariMuThemeTokens.shapes.card).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(HariMuThemeTokens.icons.xl))
            }
            if (message.isNotBlank()) {
                Text(text = message, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
            onAction?.let { action ->
                actionLabel?.let { label ->
                    HariMuSecondaryButton(text = label, onClick = action)
                }
            }
        }
    }
}