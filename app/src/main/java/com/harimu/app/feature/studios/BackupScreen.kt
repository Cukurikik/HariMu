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
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
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
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val savedToast = stringResource(R.string.backup_saved_toast)
    val spacing = HariMuThemeTokens.spacing
    val controls = HariMuThemeTokens.controls
    val semantic = HariMuThemeTokens.semantic

    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var isBusy by remember { mutableStateOf(false) }
    var statusMsg by remember { mutableStateOf("") }
    var statusIsError by remember { mutableStateOf(false) }
    var pendingExport by remember { mutableStateOf<File?>(null) }

    val saveSaf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val src = pendingExport
        if (uri != null && src != null) {
            scope.launch(Dispatchers.IO) {
                isBusy = true
                val copied = runCatching { container.storage.copyFileToUri(src, uri) }
                withContext(Dispatchers.Main) {
                    if (copied.isSuccess) {
                        statusIsError = false
                        Toast.makeText(context, savedToast, Toast.LENGTH_SHORT).show()
                    } else {
                        statusIsError = true
                        statusMsg = copied.exceptionOrNull()?.localizedMessage ?: "Failed to save .omni"
                    }
                    isBusy = false
                }
            }
        }
    }

    val openSaf = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                withContext(Dispatchers.Main) { isBusy = true }
                val res = runCatching {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: error("File could not be read")
                    container.workspace.importOmniBackup(bytes, password.ifBlank { null })
                }
                withContext(Dispatchers.Main) {
                    res.onSuccess {
                        statusIsError = false
                        statusMsg = it
                    }.onFailure {
                        statusIsError = true
                        statusMsg = it.localizedMessage ?: "Failed to restore .omni"
                    }
                    isBusy = false
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

                    Text(
                        text = stringResource(R.string.backup_scope_exclusion_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("backup_scope_exclusion_note")
                    )

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("backup_password_input"),
                        singleLine = true,
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(
                                onClick = { showPassword = !showPassword },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("backup_password_visibility_toggle")
                            ) {
                                Icon(
                                    imageVector = if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = stringResource(
                                        if (showPassword) R.string.backup_cd_hide_password else R.string.backup_cd_show_password
                                    )
                                )
                            }
                        },
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

                    val (noticeBg, noticeFg, noticeText) = when {
                        password.isBlank() -> Triple(
                            semantic.warningContainer,
                            semantic.onWarningContainer,
                            stringResource(R.string.backup_unencrypted_warning)
                        )
                        password.length < 8 -> Triple(
                            semantic.warningContainer,
                            semantic.onWarningContainer,
                            stringResource(R.string.backup_weak_password_warning)
                        )
                        else -> Triple(
                            semantic.successContainer,
                            semantic.onSuccessContainer,
                            stringResource(R.string.backup_strong_password_info)
                        )
                    }
                    Surface(
                        color = noticeBg,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("backup_encryption_notice")
                    ) {
                        Text(
                            text = noticeText,
                            style = MaterialTheme.typography.bodySmall,
                            color = noticeFg,
                            modifier = Modifier.padding(10.dp)
                        )
                    }

                    if (isBusy) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text(
                            text = stringResource(R.string.backup_processing),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Button(
                        enabled = !isBusy,
                        onClick = {
                            scope.launch(Dispatchers.IO) {
                                withContext(Dispatchers.Main) { isBusy = true }
                                val cleanPass = password.ifBlank { null }
                                val exportResult = runCatching {
                                    container.workspace.exportOmniBackup(cleanPass)
                                }
                                withContext(Dispatchers.Main) {
                                    exportResult.onSuccess { file ->
                                        pendingExport = file
                                        val modeLabel = resources.getString(
                                            if (cleanPass != null) R.string.backup_status_encrypted
                                            else R.string.backup_status_plain
                                        )
                                        statusIsError = false
                                        statusMsg = resources.getString(
                                            R.string.backup_created_fmt,
                                            modeLabel,
                                            file.name,
                                            container.storage.formatBytes(file.length())
                                        )
                                        isBusy = false
                                        saveSaf.launch(file.name)
                                    }.onFailure { err ->
                                        statusIsError = true
                                        statusMsg = err.localizedMessage ?: "Export failed"
                                        isBusy = false
                                    }
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
                        enabled = !isBusy,
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
                                color = if (statusIsError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
            }
        }
    }
}
