package com.harimu.app.feature.studios

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.harimu.app.AppContainer
import com.harimu.app.core.engines.MatrixLayerKind

/**
 * IMG-47 Image Matrix: one canvas, many layers. Every edit re-renders through the compositor,
 * so the preview above the controls is the same picture that gets exported.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageMatrixScreen(
    container: AppContainer,
    isEnglish: Boolean,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val vm: ImageMatrixViewModel = viewModel(
        factory = ImageMatrixViewModel.Factory(container, context.applicationContext)
    )
    val state by vm.state.collectAsState()

    LaunchedEffect(Unit) { vm.initialize() }
    LaunchedEffect(state.lastMessage) {
        val message = state.lastMessage ?: return@LaunchedEffect
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        vm.consumeMessage()
    }

    var textInput by remember { mutableStateOf("") }
    var emojiInput by remember { mutableStateOf("⭐") }
    var backgroundInput by remember { mutableStateOf(state.backgroundHex) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.addImage(uri)
    }

    val canvasW = state.canvasSize.first
    val canvasH = state.canvasSize.second

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEnglish) "Image Matrix Studio" else "Studio Image Matrix") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                },
                actions = {
                    IconButton(onClick = { vm.undo() }, enabled = state.canUndo) {
                        Icon(Icons.Filled.Undo, contentDescription = "Undo")
                    }
                    IconButton(onClick = { vm.redo() }, enabled = state.canRedo) {
                        Icon(Icons.Filled.Redo, contentDescription = "Redo")
                    }
                }
            )
        }
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ------------------------------------------------- preview
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp)
                            .aspectRatio(canvasW.toFloat() / canvasH.toFloat())
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        state.preview?.let { bitmap ->
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        }
                        if (state.isRendering) {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(2.dp)
                                    .align(Alignment.TopCenter)
                            )
                        }
                    }
                    Text(
                        text = "${state.layers.size} layer • ${canvasW}×${canvasH}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            // ------------------------------------------------- add layer
            Button(
                onClick = {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Tambah Layer Gambar")
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = textInput,
                    onValueChange = { textInput = it },
                    label = { Text("Teks baru") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Button(onClick = {
                    vm.addText(textInput)
                    textInput = ""
                }) { Text("+ Teks") }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = emojiInput,
                    onValueChange = { emojiInput = it },
                    label = { Text("Stiker / emoji") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Button(onClick = { vm.addSticker(emojiInput) }) { Text("+ Stiker") }
            }

            // ------------------------------------------------- canvas
            Text("Kanvas", style = MaterialTheme.typography.titleSmall)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                vm.canvasPresets.forEachIndexed { index, preset ->
                    FilterChip(
                        selected = state.canvasPresetIndex == index,
                        onClick = { vm.setCanvasPreset(index) },
                        label = { Text(preset.first) }
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = backgroundInput,
                    onValueChange = { backgroundInput = it },
                    label = { Text("Latar #Hex") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Button(onClick = { vm.setBackground(backgroundInput) }) { Text("Terapkan") }
            }

            // ------------------------------------------------- layers
            Text("Urutan Layer", style = MaterialTheme.typography.titleSmall)
            if (state.layers.isEmpty()) {
                Text(
                    "Belum ada layer. Tambahkan gambar, teks atau stiker.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            state.layers.asReversed().forEachIndexed { reverseIndex, layer ->
                val index = state.layers.lastIndex - reverseIndex
                val selected = layer.id == state.selectedLayer?.id
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { vm.selectLayer(layer.id) },
                    border = if (selected) {
                        BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                    } else {
                        BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                    }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${index + 1}. ${layer.title()}",
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            modifier = Modifier.weight(1f)
                        )
                        if (layer.kind != MatrixLayerKind.IMAGE) {
                            Text(
                                text = vm.layerKindLabel(layer.kind),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        IconButton(onClick = { vm.toggleVisibility(layer.id) }) {
                            Icon(
                                if (layer.visible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                contentDescription = "Visibilitas"
                            )
                        }
                        IconButton(onClick = { vm.moveLayer(layer.id, 1) }) {
                            Icon(Icons.Filled.ArrowUpward, contentDescription = "Naik")
                        }
                        IconButton(onClick = { vm.moveLayer(layer.id, -1) }) {
                            Icon(Icons.Filled.ArrowDownward, contentDescription = "Turun")
                        }
                        IconButton(onClick = { vm.duplicateLayer(layer.id) }) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = "Duplikat")
                        }
                        IconButton(onClick = { vm.deleteLayer(layer.id) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Hapus")
                        }
                    }
                }
            }

            // ------------------------------------------------- editor
            state.selectedLayer?.let { layer ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Layer aktif: ${layer.title()}",
                            style = MaterialTheme.typography.titleSmall
                        )
                        if (layer.kind == MatrixLayerKind.IMAGE) {
                            OutlinedButton(
                                onClick = { vm.removeBackgroundOfLayer(layer.id) },
                                enabled = !state.isRendering,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    if (isEnglish) "Remove background (local heuristic)" else "Hapus latar (heuristik lokal)"
                                )
                            }
                        }
                        if (layer.kind != MatrixLayerKind.IMAGE) {
                            OutlinedTextField(
                                value = layer.text,
                                onValueChange = { vm.updateText(layer.id, it) },
                                label = { Text("Teks") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = layer.colorHex,
                                onValueChange = { vm.setLayerColor(layer.id, it) },
                                label = { Text("Warna #Hex") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            MatrixSlider(
                                label = "Ukuran font (%)",
                                value = layer.fontSizePct,
                                range = 1f..40f,
                                onValue = { value ->
                                    vm.previewLayer(layer.id) { it.copy(fontSizePct = value) }
                                },
                                onCommit = { vm.commitLayers() }
                            )
                        }
                        MatrixSlider(
                            label = "Posisi X (%)",
                            value = layer.xPct,
                            range = 0f..100f,
                            onValue = { value -> vm.previewLayer(layer.id) { it.copy(xPct = value) } },
                            onCommit = { vm.commitLayers() }
                        )
                        MatrixSlider(
                            label = "Posisi Y (%)",
                            value = layer.yPct,
                            range = 0f..100f,
                            onValue = { value -> vm.previewLayer(layer.id) { it.copy(yPct = value) } },
                            onCommit = { vm.commitLayers() }
                        )
                        MatrixSlider(
                            label = "Skala (%)",
                            value = layer.scalePct,
                            range = 10f..300f,
                            onValue = { value -> vm.previewLayer(layer.id) { it.copy(scalePct = value) } },
                            onCommit = { vm.commitLayers() }
                        )
                        MatrixSlider(
                            label = "Rotasi (°)",
                            value = layer.rotationDeg,
                            range = -180f..180f,
                            onValue = { value -> vm.previewLayer(layer.id) { it.copy(rotationDeg = value) } },
                            onCommit = { vm.commitLayers() }
                        )
                        MatrixSlider(
                            label = "Opasitas (%)",
                            value = layer.opacityPct.toFloat(),
                            range = 10f..100f,
                            onValue = { value ->
                                vm.previewLayer(layer.id) { it.copy(opacityPct = value.toInt()) }
                            },
                            onCommit = { vm.commitLayers() }
                        )

                        Text("Blend", style = MaterialTheme.typography.labelMedium)
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            vm.blendModes.forEach { mode ->
                                FilterChip(
                                    selected = layer.blend == mode,
                                    onClick = { vm.updateLayer(layer.id) { it.copy(blend = mode) } },
                                    label = { Text(mode) }
                                )
                            }
                        }
                    }
                }
            }

            // ------------------------------------------------- export
            Button(
                onClick = {
                    vm.export { _, message ->
                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                    }
                },
                enabled = state.layers.isNotEmpty() && !state.isExporting,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Save, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (state.isExporting) "Mengekspor..." else "Ekspor PNG ${canvasW}×${canvasH}")
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun MatrixSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValue: (Float) -> Unit,
    onCommit: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(value.toInt().toString(), style = MaterialTheme.typography.labelMedium)
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onValue,
            valueRange = range,
            onValueChangeFinished = onCommit
        )
    }
}
