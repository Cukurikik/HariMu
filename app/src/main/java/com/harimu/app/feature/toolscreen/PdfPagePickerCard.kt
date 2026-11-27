package com.harimu.app.feature.toolscreen

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.harimu.app.core.design.pdf.PdfPageStripOrganizer
import com.harimu.app.core.files.FileStorageManager

/**
 * Accessible PDF page selector & reorder strip backed by PdfRenderer thumbnails.
 * Used by PDF Vault tools with `pages` or `order` parameters.
 */
@Composable
fun PdfPagePickerCard(
    pdfUri: Uri,
    storage: FileStorageManager,
    isEnglish: Boolean,
    reorderMode: Boolean,
    pages: List<Int>,
    onPagesChange: (List<Int>) -> Unit,
    modifier: Modifier = Modifier,
    password: String = "",
    previewRotationDeg: Int = 0
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
    ) {
        PdfPageStripOrganizer(
            pdfUri = pdfUri,
            storage = storage,
            selectedPages = if (reorderMode) emptyList() else pages,
            pageOrder = if (reorderMode) pages else emptyList(),
            reorderMode = reorderMode,
            onSelectedPagesChange = { if (!reorderMode) onPagesChange(it) },
            onPageOrderChange = { if (reorderMode) onPagesChange(it) },
            password = password,
            previewRotationDeg = previewRotationDeg,
            modifier = Modifier.padding(12.dp)
        )
    }
}
