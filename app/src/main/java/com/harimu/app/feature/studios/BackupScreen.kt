package com.harimu.app.feature.studios

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.harimu.app.AppContainer
import com.harimu.app.R
import com.harimu.app.core.design.HariMuInsetPanel
import com.harimu.app.core.design.HariMuResponsiveContainer
import com.harimu.app.core.design.HariMuSectionCard
import com.harimu.app.core.design.HariMuThemeTokens
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
    val savedToast = stringResource(R.string.backup_saved_toast)
    val spacing = HariMuThemeTokens.spacing
    val controls = HariMuThemeTokens.controls

    var password by remember { mutableStateOf("") }
    var statusMsg by remember { mutableStateOf("") }
    var pendingExport by remember { mutableStateOf<File?>(null) }

    val saveSaf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val src = pendingExport
        if (uri != null && src != null) {
            scope.launch(Dispatchers.IO) {
                val copied = runCatching { container.storage.copyFileToUri(src, uri) }
                withContext(Dispatchers.Main) {
                    if (copied.isSuccess) {
                        Toast.makeText(context, savedToast, Toast.LENGTH_SHORT).show()
                    } else {
                        statusMsg = copied.exceptionOrNull()?.localizedMessage ?: "Gagal menyimpan .omni"
                    }
                }
            }
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
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(if (isEnglish) "Workspace Backup (.omni)" else "Backup Workspace (.omni)") },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("backup_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        HariMuResponsiveContainer(
            modifier = Modifier.padding(padding),
            maxWidth = HariMuThemeTokens.layout.formMaxWidth,
            scrollable = true
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
                HariMuSectionCard {
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("backup_password_input"),
                        singleLine = true,
                        label = {
                            Text(
                                if (isEnglish) {
                                    "Encryption Password (Optional)"
                                } else {
                                    "Kata Sandi Enkripsi (Opsional)"
                                }
                            )
                        }
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = controls.buttonMinHeight)
                            .testTag("backup_export_button")
                    ) {
                        Icon(Icons.Filled.CloudUpload, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (isEnglish) "Export .omni Backup" else "Ekspor Backup .omni")
                    }

                    OutlinedButton(
                        onClick = { openSaf.launch(arrayOf("*/*")) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = controls.buttonMinHeight)
                            .testTag("backup_restore_button")
                    ) {
                        Icon(Icons.Filled.CloudDownload, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (isEnglish) "Restore from .omni File" else "Pulihkan dari File .omni")
                    }

                    if (statusMsg.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        HariMuInsetPanel {
                            Text(
                                text = statusMsg,
                                style = MonoTextStyle,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
            }
        }
    }
}
