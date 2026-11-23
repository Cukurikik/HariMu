package com.harimu.app.core.design

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Preview(showBackground = true, name = "ToolCard Light")
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, fontScale = 1.4f, name = "ToolCard Dark LargeFont")
@Composable
private fun PreviewToolCard() {
    HariMuTheme(themeMode = ThemeMode.SYSTEM, isSystemDark = false) {
        Column(modifier = Modifier.padding(12.dp)) {
            ToolCard(
                id = "VID-01",
                title = "Potong Video",
                description = "Potong segmen video MP4 secara cepat lewat MediaMuxer.",
                engineLabel = "M3/NAT",
                icon = Icons.Filled.ContentCut,
                accent = HariMuColors.GroupVideo,
                isFavorite = true,
                onClick = {},
                onToggleFavorite = {}
            )
        }
    }
}

@Preview(showBackground = true, name = "GroupHeader")
@Composable
private fun PreviewGroupHeader() {
    HariMuTheme(themeMode = ThemeMode.DARK, isSystemDark = true) {
        GroupHeader(
            title = "Video Forge",
            subtitle = "Potong, gabung, ekstrak audio & frame",
            toolCount = 7,
            icon = Icons.Filled.Videocam,
            accent = HariMuColors.GroupVideo,
            modifier = Modifier.padding(12.dp)
        )
    }
}

@Preview(showBackground = true, name = "Param Controls")
@Composable
private fun PreviewParamControls() {
    HariMuTheme(themeMode = ThemeMode.LIGHT, isSystemDark = false) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ParamSlider(
                label = "Kualitas Kompresi",
                value = 85f,
                min = 20f,
                max = 100f,
                unit = "%",
                onValueChange = {}
            )
            ParamDropdown(
                label = "Format Tujuan",
                options = listOf("PNG", "JPG", "WEBP", "PDF"),
                selectedOption = "WEBP",
                onOptionSelected = {}
            )
            ParamToggle(
                label = "Hapus metadata EXIF otomatis",
                checked = true,
                onCheckedChange = {}
            )
        }
    }
}

@Preview(showBackground = true, name = "FilePickerField")
@Composable
private fun PreviewFilePickerField() {
    HariMuTheme(themeMode = ThemeMode.AMOLED, isSystemDark = true) {
        FilePickerField(
            title = "File Masukan",
            buttonLabel = "Pilih File",
            selectedFiles = listOf("sample_video.mp4", "overlay.png"),
            onPickClick = {},
            modifier = Modifier.padding(12.dp)
        )
    }
}

@Preview(showBackground = true, name = "ProgressSheet")
@Composable
private fun PreviewProgressSheet() {
    HariMuTheme(themeMode = ThemeMode.DARK, isSystemDark = true) {
        ProgressSheet(
            percent = 64,
            message = "Memproses frame video...",
            onCancel = {},
            modifier = Modifier.padding(12.dp)
        )
    }
}

@Preview(showBackground = true, name = "ResultCard")
@Composable
private fun PreviewResultCard() {
    HariMuTheme(themeMode = ThemeMode.LIGHT, isSystemDark = false) {
        ResultCard(
            summary = "HariMu_IMG_20261123.png (1.2 MB)",
            onOpen = {},
            onShare = {},
            onSave = {},
            modifier = Modifier.padding(12.dp)
        )
    }
}

@Preview(showBackground = true, name = "Empty & Error States")
@Composable
private fun PreviewEmptyAndErrorStates() {
    HariMuTheme(themeMode = ThemeMode.DARK, isSystemDark = true) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            EmptyState(
                icon = Icons.Filled.History,
                title = "Belum Ada Riwayat",
                subtitle = "Hasil ekspor dari tool akan muncul di sini."
            )
            ErrorState(
                message = "Format berkas tidak didukung atau rusak.",
                onRetry = {}
            )
        }
    }
}

@Preview(showBackground = true, name = "ModelDownloadDialog")
@Composable
private fun PreviewModelDownloadDialog() {
    HariMuTheme(themeMode = ThemeMode.LIGHT, isSystemDark = false) {
        ModelDownloadDialog(
            modelName = "U2-Net Segmentasi",
            modelSizeLabel = "42 MB",
            onConfirmDownload = {},
            onDismiss = {}
        )
    }
}
