package com.harimu.app.core.design

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview

@Preview(showBackground = true, name = "ToolCard Light")
@Preview(
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    fontScale = 1.4f,
    name = "ToolCard Dark LargeFont"
)
@Composable
private fun PreviewToolCard() {
    HariMuTheme(themeMode = ThemeMode.SYSTEM, isSystemDark = false) {
        Column(
            modifier = Modifier.padding(HariMuThemeTokens.spacing.sm),
            verticalArrangement = Arrangement.spacedBy(HariMuThemeTokens.spacing.xs)
        ) {
            ToolCard(
                id = "VID-01",
                title = "Potong Video",
                description = "Potong segmen video MP4 secara cepat lewat MediaMuxer tanpa render ulang.",
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

@Preview(showBackground = true, name = "Chrome & Surfaces Light")
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, name = "Chrome & Surfaces AMOLED")
@Composable
private fun PreviewChromeAndSurfaces() {
    HariMuTheme(themeMode = ThemeMode.AMOLED, isSystemDark = true) {
        Column(
            modifier = Modifier.padding(HariMuThemeTokens.spacing.sm),
            verticalArrangement = Arrangement.spacedBy(HariMuThemeTokens.spacing.sm)
        ) {
            HariMuTopBar(
                title = "Pro Video Editor",
                subtitle = "182 tools • On-device",
                onBack = {}
            )
            HariMuSectionCard {
                Text("Section Card", style = MaterialTheme.typography.titleMedium)
                HariMuInsetPanel {
                    Text(
                        "Inset Panel (surfaceContainerLow) menghindari kartu bersarang.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            HariMuElevatedCard {
                Text("Elevated Card", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Preview(showBackground = true, name = "Buttons, Inputs & Search Light")
@Composable
private fun PreviewButtonsAndInputs() {
    HariMuTheme(themeMode = ThemeMode.LIGHT, isSystemDark = false) {
        Column(
            modifier = Modifier.padding(HariMuThemeTokens.spacing.sm),
            verticalArrangement = Arrangement.spacedBy(HariMuThemeTokens.spacing.sm)
        ) {
            HariMuSearchBar(
                query = "compress pdf",
                onQueryChange = {}
            )
            HariMuTextField(
                value = "HariMu_Project_01",
                onValueChange = {},
                label = "Nama Proyek"
            )
            HariMuPrimaryButton(
                text = "Jalankan Tool",
                onClick = {},
                icon = Icons.Filled.PlayArrow,
                modifier = Modifier.fillMaxWidth()
            )
            HariMuSecondaryButton(
                text = "Simpan Pengaturan",
                onClick = {},
                icon = Icons.Filled.Check,
                modifier = Modifier.fillMaxWidth()
            )
            HariMuDestructiveButton(
                text = "Hapus Riwayat",
                onClick = {},
                icon = Icons.Filled.Delete,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Preview(showBackground = true, name = "Status Banners")
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, name = "Status Banners Dark")
@Composable
private fun PreviewStatusBanners() {
    HariMuTheme(themeMode = ThemeMode.SYSTEM, isSystemDark = false) {
        Column(
            modifier = Modifier.padding(HariMuThemeTokens.spacing.sm),
            verticalArrangement = Arrangement.spacedBy(HariMuThemeTokens.spacing.xs)
        ) {
            HariMuStatusBanner(
                message = "Semua operasi berjalan lokal di perangkat.",
                tone = StatusTone.INFO
            )
            HariMuStatusBanner(
                message = "File berhasil diekspor ke folder pilihan.",
                tone = StatusTone.SUCCESS
            )
            HariMuStatusBanner(
                message = "Video berdurasi > 20 detik akan diproses per segmen.",
                tone = StatusTone.WARNING
            )
            HariMuStatusBanner(
                message = "Berkas tidak dapat dibuka atau rusak.",
                tone = StatusTone.ERROR,
                actionLabel = "Coba Lagi",
                onAction = {}
            )
        }
    }
}

@Preview(showBackground = true, name = "GroupHeader Dark")
@Composable
private fun PreviewGroupHeader() {
    HariMuTheme(themeMode = ThemeMode.DARK, isSystemDark = true) {
        GroupHeader(
            title = "Video Forge",
            subtitle = "Potong, gabung, ekstrak audio & frame",
            toolCount = 30,
            icon = Icons.Filled.Videocam,
            accent = HariMuColors.GroupVideo,
            modifier = Modifier.padding(HariMuThemeTokens.spacing.sm)
        )
    }
}

@Preview(showBackground = true, name = "Param Controls")
@Composable
private fun PreviewParamControls() {
    HariMuTheme(themeMode = ThemeMode.LIGHT, isSystemDark = false) {
        Column(
            modifier = Modifier.padding(HariMuThemeTokens.spacing.sm),
            verticalArrangement = Arrangement.spacedBy(HariMuThemeTokens.spacing.sm)
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
                subtitle = "Lindungi lokasi dan informasi perangkat sebelum dibagikan",
                checked = true,
                onCheckedChange = {}
            )
        }
    }
}

@Preview(showBackground = true, name = "FilePickerField AMOLED")
@Composable
private fun PreviewFilePickerField() {
    HariMuTheme(themeMode = ThemeMode.AMOLED, isSystemDark = true) {
        FilePickerField(
            title = "File Masukan",
            buttonLabel = "Pilih File",
            selectedFiles = listOf("sample_video.mp4", "overlay.png"),
            onPickClick = {},
            modifier = Modifier.padding(HariMuThemeTokens.spacing.sm)
        )
    }
}

@Preview(showBackground = true, name = "ProgressSheet Dark")
@Composable
private fun PreviewProgressSheet() {
    HariMuTheme(themeMode = ThemeMode.DARK, isSystemDark = true) {
        ProgressSheet(
            percent = 64,
            message = "Memproses frame video...",
            onCancel = {},
            modifier = Modifier.padding(HariMuThemeTokens.spacing.sm)
        )
    }
}

@Preview(showBackground = true, name = "ResultCard Light")
@Preview(showBackground = true, fontScale = 1.4f, name = "ResultCard LargeFont")
@Composable
private fun PreviewResultCard() {
    HariMuTheme(themeMode = ThemeMode.LIGHT, isSystemDark = false) {
        ResultCard(
            summary = "HariMu_IMG_20261123.png (1.2 MB)",
            onOpen = {},
            onShare = {},
            onSave = {},
            onUploadToCloud = {},
            modifier = Modifier.padding(HariMuThemeTokens.spacing.sm)
        )
    }
}

@Preview(showBackground = true, name = "Empty & Error States Dark")
@Composable
private fun PreviewEmptyAndErrorStates() {
    HariMuTheme(themeMode = ThemeMode.DARK, isSystemDark = true) {
        Column(
            modifier = Modifier.padding(HariMuThemeTokens.spacing.sm),
            verticalArrangement = Arrangement.spacedBy(HariMuThemeTokens.spacing.sm)
        ) {
            EmptyState(
                icon = Icons.Filled.History,
                title = "Belum Ada Riwayat",
                subtitle = "Hasil ekspor dari tool akan muncul di sini.",
                actionLabel = "Jelajahi Katalog",
                onAction = {}
            )
            ErrorState(
                message = "Format berkas tidak didukung atau rusak.",
                onRetry = {}
            )
        }
    }
}

@Preview(showBackground = true, name = "ModelDownloadDialog Light")
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

@Preview(showBackground = true, widthDp = 840, heightDp = 360, name = "Responsive Container Expanded")
@Composable
private fun PreviewResponsiveContainer() {
    HariMuTheme(themeMode = ThemeMode.LIGHT, isSystemDark = false) {
        HariMuResponsiveContainer(maxWidth = HariMuThemeTokens.layout.formMaxWidth) {
            HariMuSectionCard {
                Text("Tablet Centered Form Surface", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Max width 640.dp prevents awkward edge-to-edge stretching on tablets.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
