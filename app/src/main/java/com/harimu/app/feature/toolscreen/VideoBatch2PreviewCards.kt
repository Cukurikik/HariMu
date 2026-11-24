package com.harimu.app.feature.toolscreen

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FaceRetouchingOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.harimu.app.R
import com.harimu.app.core.design.HariMuColors
import com.harimu.app.core.design.MonoTextStyle
import com.harimu.app.core.media.FaceBlurDetector
import com.harimu.app.core.media.FacePreviewSample
import com.harimu.app.core.media.FfmpegProgressParser
import com.harimu.app.core.media.MediaProbe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
fun RoiLogoPreviewCard(
    videoUri: Uri,
    xPct: Float,
    yPct: Float,
    wPct: Float,
    hPct: Float,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var frameBitmap by remember(videoUri) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(videoUri) {
        val loaded = withContext(Dispatchers.IO) {
            val mmr = MediaMetadataRetriever()
            try {
                mmr.setDataSource(context, videoUri)
                val durMs = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 2_000L
                val sampleUs = minOf(1_000_000L, (durMs * 500L))
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    mmr.getScaledFrameAtTime(sampleUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 640, 360)
                } else {
                    mmr.getFrameAtTime(sampleUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                }
            } catch (_: Throwable) {
                null
            } finally {
                runCatching { mmr.release() }
            }
        }
        frameBitmap = loaded
    }

    DisposableEffect(frameBitmap) {
        val current = frameBitmap
        onDispose {
            if (current != null && !current.isRecycled) current.recycle()
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("vid14_roi_preview_card"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.vid14_roi_title), style = MaterialTheme.typography.titleSmall)
            Text(
                text = stringResource(
                    R.string.vid14_roi_coords,
                    xPct.roundToInt(),
                    yPct.roundToInt(),
                    wPct.roundToInt(),
                    hPct.roundToInt()
                ),
                style = MonoTextStyle,
                color = MaterialTheme.colorScheme.primary
            )
            val bmp = frameBitmap
            val aspect = if (bmp != null && bmp.height > 0) {
                (bmp.width.toFloat() / bmp.height.toFloat()).coerceIn(0.45f, 2.4f)
            } else 16f / 9f

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(aspect)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.Black)
            ) {
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val left = (xPct.coerceIn(0f, 95f) / 100f) * size.width
                    val top = (yPct.coerceIn(0f, 95f) / 100f) * size.height
                    val boxW = ((wPct.coerceIn(3f, 100f) / 100f) * size.width).coerceAtMost(size.width - left)
                    val boxH = ((hPct.coerceIn(3f, 100f) / 100f) * size.height).coerceAtMost(size.height - top)
                    drawRect(
                        color = HariMuColors.ElectricOrange.copy(alpha = 0.28f),
                        topLeft = Offset(left, top),
                        size = Size(boxW, boxH)
                    )
                    drawRect(
                        color = HariMuColors.ElectricOrange,
                        topLeft = Offset(left, top),
                        size = Size(boxW, boxH),
                        style = Stroke(width = 3.dp.toPx())
                    )
                }
            }
        }
    }
}

@Composable
fun FaceBlurPreviewCard(
    videoUri: Uri,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var durationMs by remember(videoUri) { mutableStateOf(10_000L) }
    var sampleSec by remember(videoUri) { mutableFloatStateOf(0.5f) }
    var isScanning by remember(videoUri) { mutableStateOf(false) }
    var previewSample by remember(videoUri) { mutableStateOf<FacePreviewSample?>(null) }
    var hasRunPreview by remember(videoUri) { mutableStateOf(false) }

    LaunchedEffect(videoUri) {
        val probe = withContext(Dispatchers.IO) { MediaProbe.probe(context, videoUri) }
        if (probe.durationMs > 0L) {
            durationMs = probe.durationMs
            sampleSec = ((probe.durationMs / 1000f) * 0.25f).coerceIn(0f, probe.durationMs / 1000f)
        }
    }

    DisposableEffect(previewSample) {
        val current = previewSample?.bitmap
        onDispose {
            if (current != null && !current.isRecycled) current.recycle()
        }
    }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("vid25_limitation_card"),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f))
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.padding(top = 2.dp)
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.vid25_limit_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Text(
                        text = stringResource(R.string.vid25_limit_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val maxSec = (durationMs / 1000f).coerceAtLeast(1f)
                val targetMs = (sampleSec * 1000f).toLong().coerceIn(0L, durationMs)
                Text(
                    text = stringResource(R.string.vid25_preview_time, FfmpegProgressParser.formatDurationMs(targetMs)),
                    style = MonoTextStyle
                )
                Slider(
                    value = sampleSec.coerceIn(0f, maxSec),
                    onValueChange = { sampleSec = it },
                    valueRange = 0f..maxSec
                )
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            isScanning = true
                            val result = withContext(Dispatchers.IO) {
                                FaceBlurDetector.detectFacesAtTime(context, videoUri, targetMs)
                            }
                            previewSample = result
                            hasRunPreview = true
                            isScanning = false
                        }
                    },
                    enabled = !isScanning,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("face_blur_preview_button")
                ) {
                    Icon(Icons.Filled.FaceRetouchingOff, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (isScanning) stringResource(R.string.vid25_preview_scanning)
                        else stringResource(R.string.vid25_preview_button)
                    )
                }

                val sample = previewSample
                if (sample != null) {
                    val bmp = sample.bitmap
                    val aspect = if (bmp.height > 0) (bmp.width.toFloat() / bmp.height.toFloat()).coerceIn(0.45f, 2.4f) else 16f / 9f
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(aspect)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.Black)
                    ) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.FillBounds,
                            modifier = Modifier.fillMaxSize()
                        )
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val sx = size.width / bmp.width.coerceAtLeast(1).toFloat()
                            val sy = size.height / bmp.height.coerceAtLeast(1).toFloat()
                            sample.faces.forEach { box ->
                                drawRect(
                                    color = HariMuColors.TealEmerald.copy(alpha = 0.25f),
                                    topLeft = Offset(box.left * sx, box.top * sy),
                                    size = Size(box.width * sx, box.height * sy)
                                )
                                drawRect(
                                    color = HariMuColors.TealEmerald,
                                    topLeft = Offset(box.left * sx, box.top * sy),
                                    size = Size(box.width * sx, box.height * sy),
                                    style = Stroke(width = 3.dp.toPx())
                                )
                            }
                        }
                    }
                    Text(
                        text = if (sample.faces.isNotEmpty()) {
                            stringResource(
                                R.string.vid25_preview_found,
                                sample.faces.size,
                                FfmpegProgressParser.formatDurationMs(sample.timeMs)
                            )
                        } else {
                            stringResource(R.string.vid25_preview_none)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (sample.faces.isNotEmpty()) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error
                    )
                } else if (hasRunPreview) {
                    Text(
                        text = stringResource(R.string.vid25_preview_none),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
