package com.harimu.app.feature.studios

import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.harimu.app.AppContainer
import com.harimu.app.core.engines.ImageEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageMatrixScreen(
    container: AppContainer,
    isEnglish: Boolean,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var sourceBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var preset by remember { mutableStateOf("Normal") }
    var contrast by remember { mutableFloatStateOf(1.0f) }
    var brightness by remember { mutableFloatStateOf(0f) }
    var watermark by remember { mutableStateOf("") }
    var isExporting by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                val bmp = ImageEngine.decodeBitmapSafe(context, uri, maxDim = 1600)
                withContext(Dispatchers.Main) {
                    sourceBitmap = bmp
                }
            }
        }
    }

    val previewMatrix = remember(preset, contrast, brightness) {
        val m = ColorMatrix()
        when (preset) {
            "Grayscale" -> m.setToSaturation(0f)
            "Sepia" -> {
                m.setToSaturation(0f)
                m.timesAssign(ColorMatrix().apply { setToScale(1f, 0.92f, 0.78f, 1f) })
            }
            "Invert" -> m.set(
                ColorMatrix(
                    floatArrayOf(
                        -1f, 0f, 0f, 0f, 255f,
                        0f, -1f, 0f, 0f, 255f,
                        0f, 0f, -1f, 0f, 255f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            )
            "Warm" -> m.setToScale(1.1f, 1.02f, 0.88f, 1f)
            "Cool" -> m.setToScale(0.9f, 1.0f, 1.14f, 1f)
        }
        val t = (1f - contrast) * 128f + brightness
        m.timesAssign(
            ColorMatrix(
                floatArrayOf(
                    contrast, 0f, 0f, 0f, t,
                    0f, contrast, 0f, 0f, t,
                    0f, 0f, contrast, 0f, t,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        )
        m
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Image Matrix Studio") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                val bmp = sourceBitmap
                if (bmp == null) {
                    Button(
                        onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                    ) {
                        Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (isEnglish) "Pick Photo to Edit" else "Pilih Foto untuk Diedit")
                    }
                } else {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Preview",
                        contentScale = ContentScale.Fit,
                        colorFilter = ColorFilter.colorMatrix(previewMatrix),
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            if (sourceBitmap != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (isEnglish) "Change" else "Ganti")
                    }
                    OutlinedButton(
                        onClick = { sourceBitmap = sourceBitmap?.let { ImageEngine.rotateBitmap(it, 90f) } },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.RotateRight, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("90°")
                    }
                    OutlinedButton(
                        onClick = { sourceBitmap = sourceBitmap?.let { ImageEngine.flipBitmap(it, true, false) } },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.Flip, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Flip")
                    }
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(if (isEnglish) "Live Presets" else "Preset Filter Langsung", style = MaterialTheme.typography.titleSmall)
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("Normal", "Grayscale", "Sepia", "Warm", "Cool", "Invert").forEach { p ->
                                FilterChip(
                                    selected = preset == p,
                                    onClick = { preset = p },
                                    label = { Text(p) }
                                )
                            }
                        }

                        Text("Contrast (${String.format("%.2f", contrast)}x)", style = MaterialTheme.typography.bodySmall)
                        Slider(value = contrast, onValueChange = { contrast = it }, valueRange = 0.5f..1.8f)

                        Text("Brightness (${brightness.toInt()})", style = MaterialTheme.typography.bodySmall)
                        Slider(value = brightness, onValueChange = { brightness = it }, valueRange = -60f..60f)

                        OutlinedTextField(
                            value = watermark,
                            onValueChange = { watermark = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text(if (isEnglish) "Optional Watermark Text" else "Teks Watermark (Opsional)") }
                        )
                    }
                }

                Button(
                    onClick = {
                        val base = sourceBitmap ?: return@Button
                        isExporting = true
                        scope.launch(Dispatchers.IO) {
                            val filtered = ImageEngine.applyFilter(base, preset, brightness, contrast, 1f)
                            val finalBmp = if (watermark.isNotBlank()) {
                                ImageEngine.addWatermark(filtered, watermark, 50, "Kanan Bawah", false).also {
                                    if (it != filtered) filtered.recycle()
                                }
                            } else filtered
                            val dest = container.storage.createOutputFile("MATRIX", "png")
                            ImageEngine.saveBitmap(finalBmp, dest, "PNG", 100)
                            finalBmp.recycle()
                            container.workspace.recordHistory(
                                toolId = "IMG-47",
                                toolTitle = "Image Matrix Studio",
                                groupName = "IMAGE",
                                outputFile = dest,
                                mimeType = "image/png",
                                summary = "Ekspor ${dest.name} (${container.storage.formatBytes(dest.length())})"
                            )
                            withContext(Dispatchers.Main) {
                                isExporting = false
                                Toast.makeText(
                                    context,
                                    if (isEnglish) "Exported to History: ${dest.name}" else "Diekspor ke Riwayat: ${dest.name}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    },
                    enabled = !isExporting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Icon(Icons.Filled.Save, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (isExporting) "Menyimpan..." else (if (isEnglish) "Export PNG to History" else "Ekspor PNG ke Riwayat"))
                }
            }
        }
    }
}
