package com.harimu.app.feature.studios

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import com.harimu.app.core.design.MonoTextStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    container: AppContainer,
    isEnglish: Boolean,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var password by remember { mutableStateOf("") }
    var statusMsg by remember { mutableStateOf("") }
    var pendingExport by remember { mutableStateOf<File?>(null) }

    val saveSaf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val src = pendingExport
        if (uri != null && src != null) {
            container.storage.copyFileToUri(src, uri)
            Toast.makeText(context, "Backup .omni tersimpan", Toast.LENGTH_SHORT).show()
        }
    }

    val openSaf = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                val res = runCatching {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: error("File tidak terbaca")
                    container.workspace.importOmniBackup(bytes, password.ifBlank { null })
                }
                withContext(Dispatchers.Main) {
                    statusMsg = res.fold(
                        onSuccess = { it },
                        onFailure = { it.localizedMessage ?: "Gagal memulihkan .omni" }
                    )
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEnglish) "Workspace Backup (.omni)" else "Backup Workspace (.omni)") },
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = if (isEnglish) {
                            "Bundles manifest.json, settings, favorites, and history into a portable .omni archive (optional AES-256-GCM encryption)."
                        } else {
                            "Mengemas manifest.json, setelan, favorit, dan riwayat ke arsip .omni portabel (opsional enkripsi AES-256-GCM)."
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text(if (isEnglish) "Encryption Password (Optional)" else "Kata Sandi Enkripsi (Opsional)") }
                    )
                    Button(
                        onClick = {
                            scope.launch(Dispatchers.IO) {
                                val file = container.workspace.exportOmniBackup(password.ifBlank { null })
                                withContext(Dispatchers.Main) {
                                    pendingExport = file
                                    statusMsg = "Dibuat: ${file.name} (${container.storage.formatBytes(file.length())})"
                                    saveSaf.launch(file.name)
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.CloudUpload, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (isEnglish) "Export .omni Backup" else "Ekspor Backup .omni")
                    }

                    OutlinedButton(
                        onClick = { openSaf.launch(arrayOf("*/*")) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.CloudDownload, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (isEnglish) "Restore from .omni File" else "Pulihkan dari File .omni")
                    }

                    if (statusMsg.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text(statusMsg, style = MonoTextStyle, color = MaterialTheme.colorScheme.secondary)
                    }
                }
            }
        }
    }
}
