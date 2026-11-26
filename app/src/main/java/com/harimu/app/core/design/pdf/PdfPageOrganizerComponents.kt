package com.harimu.app.core.design.pdf

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.harimu.app.R
import com.harimu.app.core.design.HariMuColors
import com.harimu.app.core.design.HariMuThemeTokens
import com.harimu.app.core.design.MonoTextStyle
import com.harimu.app.core.files.FileStorageManager
import com.harimu.app.core.pdf.PdfVaultEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

data class PdfQueueItem(
    val uri: Uri,
    val name: String,
    val pageCount: Int,
    val sizeBytes: Long
)

@Composable
fun PdfMergeQueueCard(
    items: List<PdfQueueItem>,
    storage: FileStorageManager,
    onAddClick: () -> Unit,
    onMoveUp: (Int) -> Unit,
    onMoveDown: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = HariMuColors.GroupPdf
    val safeAccent = HariMuThemeTokens.semantic.contrastSafeAccent(accent)

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.pdf_studio_merge_section),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                OutlinedButton(
                    onClick = onAddClick,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("pdf_merge_add_btn")
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.pdf_studio_merge_add))
                }
            }

            if (items.size < 2) {
                Text(
                    text = stringResource(R.string.pdf_studio_merge_need_two),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            items.forEachIndexed { index, item ->
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(accent.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.PictureAsPdf, contentDescription = null, tint = safeAccent, modifier = Modifier.size(18.dp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${index + 1}. ${item.name}",
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${if (item.pageCount > 0) "${item.pageCount}p • " else ""}${storage.formatBytes(item.sizeBytes)}",
                                style = MonoTextStyle,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = { onMoveUp(index) },
                            enabled = index > 0,
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(Icons.Filled.ArrowUpward, contentDescription = stringResource(R.string.cd_pdf_move_up))
                        }
                        IconButton(
                            onClick = { onMoveDown(index) },
                            enabled = index < items.lastIndex,
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(Icons.Filled.ArrowDownward, contentDescription = stringResource(R.string.cd_pdf_move_down))
                        }
                        IconButton(
                            onClick = { onRemove(index) },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_delete))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PdfPageStripOrganizer(
    pdfUri: Uri,
    storage: FileStorageManager,
    selectedPages: List<Int>,
    pageOrder: List<Int>,
    reorderMode: Boolean,
    onSelectedPagesChange: (List<Int>) -> Unit,
    onPageOrderChange: (List<Int>) -> Unit,
    modifier: Modifier = Modifier,
    password: String = "",
    previewRotationDeg: Int = 0
) {
    val context = LocalContext.current
    val thumbnails = remember(pdfUri, password) { mutableStateListOf<Bitmap>() }
    var totalPages by remember(pdfUri, password) { mutableIntStateOf(0) }
    var loading by remember(pdfUri, password) { mutableStateOf(true) }
    var encryptedError by remember(pdfUri, password) { mutableStateOf(false) }

    DisposableEffect(pdfUri, password) {
        onDispose {
            thumbnails.forEach { if (!it.isRecycled) it.recycle() }
            thumbnails.clear()
        }
    }

    LaunchedEffect(pdfUri, password) {
        loading = true
        encryptedError = false
        thumbnails.forEach { if (!it.isRecycled) it.recycle() }
        thumbnails.clear()
        withContext(Dispatchers.IO) {
            var temp: File? = null
            try {
                temp = PdfVaultEngine.prepareRenderableTemp(context, storage, pdfUri, password)
                ParcelFileDescriptor.open(temp, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                    PdfRenderer(descriptor).use { renderer ->
                        totalPages = renderer.pageCount
                        val limit = minOf(renderer.pageCount, 60)
                        for (index in 0 until limit) {
                            val page = renderer.openPage(index)
                            val w = 132
                            val h = (w * page.height.toFloat() / page.width.coerceAtLeast(1))
                                .roundToInt()
                                .coerceAtLeast(1)
                            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                            bmp.eraseColor(AndroidColor.WHITE)
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            page.close()
                            withContext(Dispatchers.Main) { thumbnails.add(bmp) }
                        }
                    }
                }
            } catch (_: SecurityException) {
                encryptedError = true
            } catch (_: Throwable) {
                encryptedError = false
            } finally {
                loading = false
                temp?.delete()
            }
        }
    }

    val effectiveOrder = remember(pageOrder, totalPages) {
        val valid = pageOrder.filter { it in 1..totalPages }
        if (valid.isNotEmpty()) valid else (1..totalPages).toList()
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (encryptedError) {
            Text(
                text = stringResource(R.string.pdf_preview_encrypted),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
            return@Column
        }
        Text(
            text = when {
                loading -> stringResource(R.string.pdf_preview_loading)
                reorderMode -> stringResource(R.string.pdf_picker_reorder_hint)
                else -> stringResource(R.string.pdf_picker_selected_count, selectedPages.size, totalPages)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (!reorderMode && totalPages > 0) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedButton(
                    onClick = { onSelectedPagesChange((1..totalPages).toList()) },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Text(stringResource(R.string.pdf_picker_all_pages))
                }
                OutlinedButton(
                    onClick = { onSelectedPagesChange((1..totalPages).filter { it % 2 == 1 }) },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Text(stringResource(R.string.pdf_picker_odd_pages))
                }
                OutlinedButton(
                    onClick = { onSelectedPagesChange((1..totalPages).filter { it % 2 == 0 }) },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Text(stringResource(R.string.pdf_picker_even_pages))
                }
                OutlinedButton(
                    onClick = { onSelectedPagesChange(emptyList()) },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Text(stringResource(R.string.pdf_picker_clear_pages))
                }
            }
        }

        if (totalPages > 0) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (reorderMode) {
                    itemsIndexed(effectiveOrder, key = { idx, page -> "${idx}_$page" }) { index, pageNumber ->
                        ReorderPageTile(
                            pageNumber = pageNumber,
                            slotIndex = index,
                            totalSlots = effectiveOrder.size,
                            bitmap = thumbnails.getOrNull(pageNumber - 1),
                            rotationDeg = previewRotationDeg,
                            onMoveLeft = {
                                if (index > 0) {
                                    val mutable = effectiveOrder.toMutableList()
                                    val item = mutable.removeAt(index)
                                    mutable.add(index - 1, item)
                                    onPageOrderChange(mutable)
                                }
                            },
                            onMoveRight = {
                                if (index < effectiveOrder.lastIndex) {
                                    val mutable = effectiveOrder.toMutableList()
                                    val item = mutable.removeAt(index)
                                    mutable.add(index + 1, item)
                                    onPageOrderChange(mutable)
                                }
                            },
                            onRemove = if (effectiveOrder.size > 1) {
                                {
                                    val mutable = effectiveOrder.toMutableList()
                                    mutable.removeAt(index)
                                    onPageOrderChange(mutable)
                                }
                            } else null
                        )
                    }
                } else {
                    itemsIndexed((1..totalPages).toList(), key = { _, page -> page }) { _, pageNumber ->
                        val isSelected = pageNumber in selectedPages
                        SelectablePageTile(
                            pageNumber = pageNumber,
                            bitmap = thumbnails.getOrNull(pageNumber - 1),
                            selected = isSelected,
                            rotationDeg = if (selectedPages.isEmpty() || isSelected) previewRotationDeg else 0,
                            onClick = {
                                val updated = if (isSelected) selectedPages - pageNumber else selectedPages + pageNumber
                                onSelectedPagesChange(updated.sorted())
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectablePageTile(
    pageNumber: Int,
    bitmap: Bitmap?,
    selected: Boolean,
    rotationDeg: Int,
    onClick: () -> Unit
) {
    val accent = HariMuColors.GroupPdf
    val pageDesc = stringResource(R.string.pdf_picker_page_desc, pageNumber)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(96.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(role = Role.Checkbox, onClickLabel = pageDesc, onClick = onClick)
            .padding(2.dp)
            .testTag("pdf_page_tile_$pageNumber")
    ) {
        Box(
            modifier = Modifier
                .size(width = 88.dp, height = 116.dp)
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) accent else MaterialTheme.colorScheme.outline,
                    shape = RoundedCornerShape(8.dp)
                )
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (bitmap != null && !bitmap.isRecycled) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = pageDesc,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .padding(4.dp)
                        .fillMaxWidth()
                        .graphicsLayer { rotationZ = rotationDeg.toFloat() }
                )
            } else {
                Text("...", style = MaterialTheme.typography.bodySmall)
            }
            if (selected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(accent),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
                }
            }
        }
        Text(
            text = "$pageNumber",
            style = MonoTextStyle,
            color = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ReorderPageTile(
    pageNumber: Int,
    slotIndex: Int,
    totalSlots: Int,
    bitmap: Bitmap?,
    rotationDeg: Int,
    onMoveLeft: () -> Unit,
    onMoveRight: () -> Unit,
    onRemove: (() -> Unit)?
) {
    val pageDesc = stringResource(R.string.pdf_picker_page_desc, pageNumber)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.width(104.dp)
    ) {
        Box(
            modifier = Modifier
                .size(width = 92.dp, height = 116.dp)
                .border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (bitmap != null && !bitmap.isRecycled) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = pageDesc,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .padding(4.dp)
                        .fillMaxWidth()
                        .graphicsLayer { rotationZ = rotationDeg.toFloat() }
                )
            } else {
                Text("...", style = MaterialTheme.typography.bodySmall)
            }
            if (onRemove != null) {
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.pdf_picker_remove_page, pageNumber),
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
        Text(
            text = "#${slotIndex + 1} (p.$pageNumber)",
            style = MonoTextStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.Center) {
            IconButton(
                onClick = onMoveLeft,
                enabled = slotIndex > 0,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = stringResource(R.string.pdf_picker_move_left, pageNumber)
                )
            }
            IconButton(
                onClick = onMoveRight,
                enabled = slotIndex < totalSlots - 1,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = stringResource(R.string.pdf_picker_move_right, pageNumber)
                )
            }
        }
    }
}
