package com.harimu.app.core.design.pdf

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.RectF
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.harimu.app.R
import com.harimu.app.core.design.HariMuColors
import com.harimu.app.core.design.HariMuThemeTokens
import com.harimu.app.core.design.MonoTextStyle
import com.harimu.app.core.files.FileStorageManager
import com.harimu.app.core.pdf.PdfVaultEngine
import com.harimu.app.core.pdf.PdfVaultEngine.PdfDocInspection
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

data class PdfWatermarkPreviewSpec(
    val isImage: Boolean = false,
    val text: String = "",
    val imageUri: Uri? = null,
    val opacityPct: Int = 25,
    val angleDeg: Int = -35,
    val sizePct: Int = 10
)

data class PdfRedactionOverlaySpec(
    val pageHits: Map<Int, List<RectF>> = emptyMap(),
    val pageSizes: List<Pair<Float, Float>> = emptyList()
)

private sealed interface PageRenderState {
    data object Loading : PageRenderState
    data object Encrypted : PageRenderState
    data class Ready(
        val bitmap: Bitmap,
        val totalPages: Int,
        val pageWidthPts: Float,
        val pageHeightPts: Float
    ) : PageRenderState

    data class Error(val message: String) : PageRenderState
}

/**
 * Shared real PdfRenderer document preview with multi-page navigation, metadata chips,
 * zoom modal, and optional live watermark / redaction / rotation overlays.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PdfDocumentPreviewCard(
    uri: Uri,
    storage: FileStorageManager,
    isEnglish: Boolean,
    modifier: Modifier = Modifier,
    password: String = "",
    inspection: PdfDocInspection? = null,
    pageIndex: Int? = null,
    onPageChange: ((Int) -> Unit)? = null,
    previewRotationDeg: Int = 0,
    watermarkSpec: PdfWatermarkPreviewSpec? = null,
    redactionOverlay: PdfRedactionOverlaySpec? = null,
    maxHeightDp: Dp = 280.dp,
    title: String? = null,
    testTag: String = "pdf_document_preview_card"
) {
    val context = LocalContext.current
    val spacing = HariMuThemeTokens.spacing
    val accent = HariMuColors.GroupPdf
    val safeAccent = HariMuThemeTokens.semantic.contrastSafeAccent(accent)

    var internalPage by remember(uri) { mutableIntStateOf(0) }
    val currentPage = pageIndex ?: internalPage
    fun setPage(target: Int) {
        val clamped = target.coerceAtLeast(0)
        if (onPageChange != null) onPageChange(clamped) else internalPage = clamped
    }

    var renderState by remember(uri, password) { mutableStateOf<PageRenderState>(PageRenderState.Loading) }
    var showZoomDialog by remember { mutableStateOf(false) }

    val displayName = remember(uri) { storage.getDisplayName(uri) ?: "document.pdf" }

    DisposableEffect(uri, password) {
        onDispose {
            val ready = renderState as? PageRenderState.Ready
            if (ready != null && !ready.bitmap.isRecycled) {
                ready.bitmap.recycle()
            }
        }
    }

    LaunchedEffect(uri, password, currentPage) {
        val oldBitmap = (renderState as? PageRenderState.Ready)?.bitmap
        renderState = PageRenderState.Loading
        val next = withContext(Dispatchers.IO) {
            var temp: File? = null
            try {
                temp = PdfVaultEngine.prepareRenderableTemp(context, storage, uri, password)
                ParcelFileDescriptor.open(temp, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                    PdfRenderer(descriptor).use { renderer ->
                        val total = renderer.pageCount
                        if (total <= 0) {
                            return@withContext PageRenderState.Error(context.getString(R.string.pdf_preview_error))
                        }
                        val safeIndex = currentPage.coerceIn(0, total - 1)
                        val page = renderer.openPage(safeIndex)
                        val targetWidth = 720
                        val targetHeight = (targetWidth * page.height.toFloat() / page.width.coerceAtLeast(1))
                            .roundToInt()
                            .coerceIn(1, 1280)
                        val bmp = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(AndroidColor.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        val wPts = page.width.toFloat()
                        val hPts = page.height.toFloat()
                        page.close()
                        PageRenderState.Ready(bmp, total, wPts, hPts)
                    }
                }
            } catch (_: InvalidPasswordException) {
                PageRenderState.Encrypted
            } catch (_: SecurityException) {
                PageRenderState.Encrypted
            } catch (t: Throwable) {
                PageRenderState.Error(t.localizedMessage ?: context.getString(R.string.pdf_preview_error))
            } finally {
                temp?.delete()
            }
        }
        if (oldBitmap != null && !oldBitmap.isRecycled && (next as? PageRenderState.Ready)?.bitmap !== oldBitmap) {
            oldBitmap.recycle()
        }
        renderState = next
    }

    val readyState = renderState as? PageRenderState.Ready
    val totalPages = inspection?.pageCount?.takeIf { it > 0 } ?: readyState?.totalPages ?: 0
    val safePage = if (totalPages > 0) currentPage.coerceIn(0, totalPages - 1) else 0

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag(testTag),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier.padding(spacing.sm),
            verticalArrangement = Arrangement.spacedBy(spacing.xs)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title ?: stringResource(R.string.pdf_preview_title),
                        style = MaterialTheme.typography.labelLarge,
                        color = safeAccent,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = displayName,
                        style = MonoTextStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (readyState != null) {
                    IconButton(
                        onClick = { showZoomDialog = true },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("${testTag}_zoom_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ZoomIn,
                            contentDescription = stringResource(R.string.cd_pdf_zoom),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            if (inspection != null) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (inspection.pageCount > 0) {
                        MetaBadge(
                            text = stringResource(R.string.pdf_preview_meta_pages, inspection.pageCount),
                            tint = safeAccent
                        )
                    }
                    if (inspection.fileSizeBytes > 0L) {
                        MetaBadge(
                            text = storage.formatBytes(inspection.fileSizeBytes),
                            tint = MaterialTheme.colorScheme.secondary
                        )
                    }
                    if (inspection.isEncrypted) {
                        MetaBadge(
                            text = if (inspection.requiresPassword) {
                                stringResource(R.string.pdf_preview_meta_locked)
                            } else {
                                stringResource(R.string.pdf_preview_meta_unlocked)
                            },
                            tint = if (inspection.requiresPassword) {
                                MaterialTheme.colorScheme.error
                            } else {
                                HariMuThemeTokens.semantic.success
                            },
                            icon = if (inspection.requiresPassword) Icons.Filled.Lock else Icons.Filled.LockOpen
                        )
                    }
                    if (!inspection.requiresPassword) {
                        MetaBadge(
                            text = if (inspection.hasExtractableText) {
                                stringResource(R.string.pdf_preview_meta_text_yes)
                            } else {
                                stringResource(R.string.pdf_preview_meta_text_no)
                            },
                            tint = if (inspection.hasExtractableText) {
                                HariMuThemeTokens.semantic.info
                            } else {
                                HariMuThemeTokens.semantic.warning
                            }
                        )
                    }
                    if (inspection.formFields.isNotEmpty()) {
                        MetaBadge(
                            text = stringResource(R.string.pdf_preview_meta_forms, inspection.formFields.size),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 170.dp, max = maxHeightDp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .then(
                        if (readyState != null) {
                            Modifier.clickable { showZoomDialog = true }
                        } else {
                            Modifier
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                when (val s = renderState) {
                    PageRenderState.Loading -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(16.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp)
                            Text(
                                text = stringResource(R.string.pdf_preview_loading),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    PageRenderState.Encrypted -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Lock,
                                contentDescription = null,
                                tint = safeAccent,
                                modifier = Modifier.size(34.dp)
                            )
                            Text(
                                text = stringResource(R.string.pdf_preview_encrypted),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    is PageRenderState.Error -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PictureAsPdf,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(34.dp)
                            )
                            Text(
                                text = s.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    is PageRenderState.Ready -> {
                        val pageAspect = (s.bitmap.width.toFloat() / s.bitmap.height.toFloat().coerceAtLeast(1f))
                            .coerceIn(0.45f, 2.2f)
                        Box(
                            modifier = Modifier
                                .heightIn(max = maxHeightDp)
                                .aspectRatio(pageAspect)
                                .padding(4.dp)
                                .graphicsLayer { rotationZ = previewRotationDeg.toFloat() }
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                bitmap = s.bitmap.asImageBitmap(),
                                contentDescription = stringResource(R.string.pdf_preview_zoom_title, safePage + 1),
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )

                            // Live Redaction Hit Overlay
                            if (redactionOverlay != null) {
                                val pageHits = redactionOverlay.pageHits[safePage + 1].orEmpty()
                                val pagePts = redactionOverlay.pageSizes.getOrNull(safePage)
                                    ?: (s.pageWidthPts to s.pageHeightPts)
                                if (pageHits.isNotEmpty() && pagePts.first > 0f && pagePts.second > 0f) {
                                    Canvas(modifier = Modifier.fillMaxSize()) {
                                        val sx = size.width / pagePts.first
                                        val sy = size.height / pagePts.second
                                        pageHits.forEach { rect ->
                                            drawRect(
                                                color = Color(0x99DC2626),
                                                topLeft = Offset(rect.left * sx, rect.top * sy),
                                                size = Size(
                                                    (rect.width() * sx).coerceAtLeast(2f),
                                                    (rect.height() * sy).coerceAtLeast(2f)
                                                )
                                            )
                                            drawRect(
                                                color = Color(0xFFDC2626),
                                                topLeft = Offset(rect.left * sx, rect.top * sy),
                                                size = Size(
                                                    (rect.width() * sx).coerceAtLeast(2f),
                                                    (rect.height() * sy).coerceAtLeast(2f)
                                                ),
                                                style = Stroke(width = 2f)
                                            )
                                        }
                                    }
                                }
                            }

                            // Live Watermark Overlay
                            if (watermarkSpec != null) {
                                val alphaVal = (watermarkSpec.opacityPct.coerceIn(5, 100) / 100f)
                                if (watermarkSpec.isImage && watermarkSpec.imageUri != null) {
                                    val fraction = (watermarkSpec.sizePct.coerceIn(10, 90) / 100f)
                                    AsyncImage(
                                        model = watermarkSpec.imageUri,
                                        contentDescription = null,
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier
                                            .fillMaxWidth(fraction)
                                            .alpha(alphaVal)
                                    )
                                } else if (!watermarkSpec.isImage && watermarkSpec.text.isNotBlank()) {
                                    Text(
                                        text = watermarkSpec.text,
                                        color = Color(0xFFBF1A1A).copy(alpha = alphaVal),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = (watermarkSpec.sizePct.coerceIn(4, 30) * 1.4f).sp,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.graphicsLayer {
                                            rotationZ = watermarkSpec.angleDeg.toFloat()
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (totalPages > 1) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { setPage((safePage - 1).coerceAtLeast(0)) },
                        enabled = safePage > 0,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("${testTag}_prev_btn")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = stringResource(R.string.cd_pdf_prev_page)
                        )
                    }
                    Text(
                        text = stringResource(R.string.pdf_preview_page_counter, safePage + 1, totalPages),
                        style = MonoTextStyle,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(
                        onClick = { setPage((safePage + 1).coerceAtMost(totalPages - 1)) },
                        enabled = safePage < totalPages - 1,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("${testTag}_next_btn")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = stringResource(R.string.cd_pdf_next_page)
                        )
                    }
                }

                if (totalPages > 2) {
                    Slider(
                        value = safePage.toFloat(),
                        onValueChange = { setPage(it.roundToInt().coerceIn(0, totalPages - 1)) },
                        valueRange = 0f..(totalPages - 1).toFloat(),
                        steps = (totalPages - 2).coerceAtLeast(0),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else if (totalPages == 1) {
                Text(
                    text = stringResource(R.string.pdf_preview_page_counter, 1, 1),
                    style = MonoTextStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (showZoomDialog && readyState != null) {
        PdfZoomPreviewDialog(
            bitmap = readyState.bitmap,
            pageNumber = safePage + 1,
            onDismiss = { showZoomDialog = false }
        )
    }
}

@Composable
private fun MetaBadge(
    text: String,
    tint: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = tint.copy(alpha = 0.13f),
        border = BorderStroke(1.dp, tint.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(13.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = tint,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun PdfZoomPreviewDialog(
    bitmap: Bitmap,
    pageNumber: Int,
    onDismiss: () -> Unit
) {
    var zoomScale by remember { mutableFloatStateOf(1f) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.pdf_preview_zoom_title, pageNumber),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.cd_image_close)
                        )
                    }
                }
                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .verticalScroll(rememberScrollState()),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = stringResource(R.string.pdf_preview_zoom_title, pageNumber),
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                scaleX = zoomScale
                                scaleY = zoomScale
                            }
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "${(zoomScale * 100).roundToInt()}%",
                        style = MonoTextStyle,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Slider(
                        value = zoomScale,
                        onValueChange = { zoomScale = it },
                        valueRange = 1f..3f,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
