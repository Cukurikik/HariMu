package com.harimu.app.feature.studios

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.harimu.app.AppContainer
import com.harimu.app.core.engines.VideoEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProVideoEditorScreen(
    container: AppContainer,
    isEnglish: Boolean,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var uris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var status by remember { mutableStateOf("") }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(8)) { list ->
        if (list.isNotEmpty()) uris = list
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text(if (isEnglish) "Pro Video Editor" else "Pro Video Editor") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) }
        })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { picker.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) }, modifier = Modifier.fillMaxWidth()) {
                Text(if (isEnglish) "Pick clips" else "Pilih klip")
            }
            Text(if (isEnglish) "Selected: ${uris.size}" else "Dipilih: ${uris.size}")
            Button(enabled = uris.size >= 2, onClick = {
                scope.launch {
                    status = if (isEnglish) "Rendering..." else "Merender..."
                    withContext(Dispatchers.IO) {
                        val dest = container.storage.createOutputFile("PRO_EDITOR", "mp4")
                        VideoEngine.mergeVideos(context, uris, dest)
                        status = dest.name
                    }
                }
            }, modifier = Modifier.fillMaxWidth()) {
                Text(if (isEnglish) "Render merged video" else "Render gabungan")
            }
            Text(status)
        }
    }
}