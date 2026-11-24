package com.harimu.app.feature.toolscreen

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.harimu.app.core.files.FileStorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Viewer PDF ringan (PdfRenderer) untuk pratinjau halaman, pemilihan halaman, dan penyusunan
 * ulang dengan drag. Dipakai tool PDF Vault yang punya parameter `pages` (pilih) atau `order`
 * (susun ulang).
 */
@Composable
fun PdfPagePickerCard(
    pdfUri: Uri,
    storage: FileStorageManager,
    isEnglish: Boolean,
    reorderMode: Boolean,
    pages: List<Int>,
    onPagesChange: (List<Int>) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val thumbnails = remember(pdfUri) { mutableStateListOf<Bitmap>() }
    var pageCount by remember(pdfUri) { mutableStateOf(0) }
    var loading by remember(pdfUri) { mutableStateOf(true) }

    LaunchedEffect(pdfUri) {
        loading = true
        thumbnails.clear()
        withContext(Dispatchers.IO) {
            var temp: File? = null
            try {
                temp = storage.copyUriToTemp(pdfUri, "pdf_preview", "pdf")
                val descriptor = ParcelFileDescriptor.open(temp, ParcelFileDescriptor.MODE_READ_ONLY)
                PdfRenderer(descriptor).use { renderer ->
                    pageCount = renderer.pageCount
                    val limit = minOf(renderer.pageCount, 60)
                    for (index in 0 until limit) {
                        val page = renderer.openPage(index)
                        val width = 120
                        val height = (width * page.height.toFloat() / page.width).roundToInt().coerceAtLeast(1)
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        page.close()
                        withContext(Dispatchers.Main) { thumbnails.add(bitmap) }
                    }
                }
            } finally {
                loading = false
                temp?.delete()
            }
        }
    }

    DisposableEffect(pdfUri) {
        onDispose { thumbnails.forEach { it.recycle() }; thumbnails.clear() }
    }

    val order = remember(pdfUri, pageCount, reorderMode) {
        if (reorderMode) {
            if (pages.isNotEmpty()) pages.filter { it in 1..pageCount } else (1..pageCount).toList()
        } else {
            emptyList()
        }
    }
    var workingOrder by remember(pdfUri, pageCount) { mutableStateOf(order) }
    var dragAccum by remember { mutableStateOf(0f) }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = when {
                    loading -> if (isEnglish) "Rendering preview..." else "Merender pratinjau..."
                    reorderMode -> if (isEnglish) {
                        "Drag a thumbnail to reorder (long-press), or edit the order field."
                    } else {
                        "Seret thumbnail untuk menyusun ulang (tekan lama), atau ubah kolom urutan."
                    }
                    else -> if (isEnglish) {
                        "Tap pages to select. ${pages.size} of $pageCount selected."
                    } else {
                        "Ketuk halaman untuk memilih. ${pages.size} dari $pageCount dipilih."
                    }
                },
                style = MaterialTheme.typography.bodySmall
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (reorderMode) {
                    items(workingOrder, key = { it }) { pageNumber ->
                        val index = workingOrder.indexOf(pageNumber)
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .width(96.dp)
                                .pointerInput(pageNumber) {
                                    detectDragGesturesAfterLongPress(
                                        onDragEnd = { dragAccum = 0f },
                                        onDragCancel = { dragAccum = 0f },
                                        onDrag = { change, amount ->
                                            change.consume()
                                            dragAccum += amount.x
                                            val threshold = 80.dp.toPx()
                                            if (abs(dragAccum) > threshold) {
                                                // Baca posisi terbaru saat gestur berjalan, bukan dari komposisi lama.
                                                val currentIndex = workingOrder.indexOf(pageNumber)
                                                val target = if (dragAccum > 0) currentIndex + 1 else currentIndex - 1
                                                if (currentIndex >= 0 && target in workingOrder.indices) {
                                                    val mutable = workingOrder.toMutableList()
                                                    val moved = mutable.removeAt(currentIndex)
                                                    mutable.add(target, moved)
                                                    workingOrder = mutable
                                                    onPagesChange(mutable)
                                                }
                                                dragAccum = 0f
                                            }
                                        }
                                    )
                                }
                        ) {
                            Thumb(
                                bitmap = thumbnails.getOrNull(pageNumber - 1),
                                label = pageNumber.toString(),
                                highlighted = true,
                                enabled = true
                            )
                            Text(
                                text = "#${index + 1}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    items((1..pageCount).toList(), key = { it }) { pageNumber ->
                        val selected = pageNumber in pages
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .width(96.dp)
                                .pointerInput(selected, pages) {
                                    detectTapGestures {
                                        val updated = if (selected) pages - pageNumber else pages + pageNumber
                                        onPagesChange(updated.sorted())
                                    }
                                }
                        ) {
                            Thumb(
                                bitmap = thumbnails.getOrNull(pageNumber - 1),
                                label = pageNumber.toString(),
                                highlighted = selected,
                                enabled = true
                            )
                        }
                    }
                }
            }
            if (!reorderMode && pageCount > 0) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onPagesChange((1..pageCount).toList()) }) {
                        Text(if (isEnglish) "All pages" else "Semua halaman")
                    }
                    OutlinedButton(onClick = { onPagesChange(emptyList()) }) {
                        Text(if (isEnglish) "Clear" else "Kosongkan")
                    }
                }
            }
        }
    }
}

@Composable
private fun Thumb(bitmap: Bitmap?, label: String, highlighted: Boolean, enabled: Boolean) {
    Box(
        modifier = Modifier
            .size(width = 88.dp, height = 116.dp)
            .border(
                width = if (highlighted) 2.dp else 1.dp,
                color = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(6.dp)
            )
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
            .alpha(if (enabled) 1f else 0.4f),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Halaman $label",
                modifier = Modifier.padding(3.dp).fillMaxWidth()
            )
        } else {
            Text(text = "...", style = MaterialTheme.typography.bodySmall)
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.align(Alignment.BottomCenter).padding(2.dp)
        )
    }
}
