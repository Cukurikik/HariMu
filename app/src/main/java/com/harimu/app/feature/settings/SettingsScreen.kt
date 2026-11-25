package com.harimu.app.feature.settings

import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.harimu.app.AppContainer
import com.harimu.app.R
import com.harimu.app.core.data.AppSettings
import com.harimu.app.core.design.MonoTextStyle
import com.harimu.app.core.design.ParamSlider
import com.harimu.app.core.design.ParamToggle
import com.harimu.app.core.design.ThemeMode
import com.harimu.app.core.toolapi.DedicatedScreenRoute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    container: AppContainer,
    settings: AppSettings,
    onOpenStudio: (DedicatedScreenRoute) -> Unit,
    onOpenInternalTool: (String) -> Unit = {},
    onOpenLicenses: () -> Unit = {}
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()

    var tempBytes by remember { mutableLongStateOf(container.storage.tempCacheSizeBytes()) }
    var freeBytes by remember { mutableLongStateOf(container.storage.availableDiskSpaceBytes()) }
    var modelStatuses by remember { mutableStateOf(container.modelManager.listStatuses()) }
    var modelTotalBytes by remember { mutableLongStateOf(container.modelManager.totalDownloadedBytes()) }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            val label = uri.lastPathSegment?.substringAfterLast(':') ?: uri.toString()
            scope.launch {
                container.settings.setOutputFolder(uri.toString(), label)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 640.dp),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.settings_appearance), style = MaterialTheme.typography.titleMedium)

                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ThemeMode.entries.forEach { mode ->
                                FilterChip(
                                    selected = settings.resolvedThemeMode() == mode,
                                    onClick = { scope.launch { container.settings.setThemeMode(mode) } },
                                    label = { Text(stringResource(mode.labelRes)) },
                                    modifier = Modifier.testTag("theme_chip_${mode.name}")
                                )
                            }
                        }

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            ParamToggle(
                                label = stringResource(R.string.dynamic_color_label),
                                subtitle = stringResource(R.string.dynamic_color_desc),
                                checked = settings.dynamicColor,
                                onCheckedChange = { scope.launch { container.settings.setDynamicColor(it) } },
                                modifier = Modifier.testTag("dynamic_color_switch")
                            )
                        }

                        ParamToggle(
                            label = stringResource(R.string.language_english_label),
                            checked = settings.isEnglish,
                            onCheckedChange = { scope.launch { container.settings.setEnglish(it) } },
                            modifier = Modifier.testTag("language_switch")
                        )
                    }
                }
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.settings_engine_storage), style = MaterialTheme.typography.titleMedium)

                        ParamSlider(
                            label = stringResource(R.string.settings_export_quality),
                            value = settings.outputQuality.toFloat(),
                            min = 40f,
                            max = 100f,
                            step = 5f,
                            unit = "%",
                            onValueChange = { scope.launch { container.settings.setOutputQuality(it.roundToInt()) } }
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = stringResource(R.string.settings_output_folder_title),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = settings.outputFolderLabel.ifBlank {
                                    stringResource(R.string.settings_output_folder_default)
                                },
                                style = MonoTextStyle,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = { folderPicker.launch(null) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("pick_output_folder_button")
                                ) {
                                    Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(stringResource(R.string.settings_output_folder_pick))
                                }
                                if (settings.outputFolderUri.isNotBlank()) {
                                    TextButton(onClick = { scope.launch { container.settings.setOutputFolder("", "") } }) {
                                        Text(stringResource(R.string.settings_output_folder_reset))
                                    }
                                }
                            }
                        }

                        Text(
                            text = stringResource(R.string.settings_free_space, container.storage.formatBytes(freeBytes)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        ParamToggle(
                            label = stringResource(R.string.settings_auto_clean),
                            checked = settings.autoCleanTemp,
                            onCheckedChange = { scope.launch { container.settings.setAutoCleanTemp(it) } }
                        )

                        OutlinedButton(
                            onClick = {
                                scope.launch(Dispatchers.IO) {
                                    val freed = container.storage.clearTempCache()
                                    val msg = resources.getString(R.string.toast_temp_cleared, container.storage.formatBytes(freed))
                                    withContext(Dispatchers.Main) {
                                        tempBytes = container.storage.tempCacheSizeBytes()
                                        freeBytes = container.storage.availableDiskSpaceBytes()
                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("clear_temp_button")
                        ) {
                            Icon(Icons.Filled.CleaningServices, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.settings_clear_temp, container.storage.formatBytes(tempBytes)))
                        }

                        OutlinedButton(
                            onClick = { onOpenInternalTool(com.harimu.app.feature.tools.InternalMediaTestTool.ID) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("test_media_engine_button")
                        ) {
                            Text(stringResource(R.string.settings_test_media_engine))
                        }
                    }
                }
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Filled.Memory, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(stringResource(R.string.settings_models_title), style = MaterialTheme.typography.titleMedium)
                        }
                        Text(
                            text = stringResource(R.string.settings_models_empty, container.storage.formatBytes(modelTotalBytes)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        modelStatuses.forEachIndexed { idx, status ->
                            if (idx > 0) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(status.spec.name, style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        text = if (status.isDownloaded) {
                                            stringResource(R.string.model_status_ready, container.storage.formatBytes(status.localSizeBytes))
                                        } else {
                                            "${stringResource(R.string.model_status_missing)} • ${status.spec.sizeLabel}"
                                        },
                                        style = MonoTextStyle,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (status.isDownloaded) {
                                    IconButton(onClick = {
                                        container.modelManager.deleteModel(status.spec.id)
                                        modelStatuses = container.modelManager.listStatuses()
                                        modelTotalBytes = container.modelManager.totalDownloadedBytes()
                                    }) {
                                        Icon(
                                            Icons.Filled.Delete,
                                            contentDescription = stringResource(R.string.action_delete)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "AI Gateway",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = if (settings.isEnglish) {
                                "Endpoint for cloud AI tools. Provider keys live on the server, never on this phone."
                            } else {
                                "Endpoint untuk tool AI cloud. Kunci provider disimpan di server, bukan di HP ini."
                            },
                            style = MaterialTheme.typography.bodySmall
                        )
                        var gatewayUrl by remember(settings.aiGatewayUrl) { mutableStateOf(settings.aiGatewayUrl) }
                        var aiStatus by remember { mutableStateOf("") }
                        OutlinedTextField(
                            value = gatewayUrl,
                            onValueChange = { gatewayUrl = it },
                            label = { Text("URL Gateway (https://…)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = {
                                scope.launch {
                                    container.settings.setAiGatewayUrl(gatewayUrl)
                                    aiStatus = if (settings.isEnglish) "Saved" else "Tersimpan"
                                }
                            }) { Text(if (settings.isEnglish) "Save" else "Simpan") }
                            TextButton(onClick = {
                                scope.launch {
                                    container.settings.setAiGatewayUrl(gatewayUrl)
                                    aiStatus = if (settings.isEnglish) "Connecting…" else "Menghubungkan…"
                                    runCatching {
                                        container.aiClient.ensureToken(forceRefresh = true)
                                        val quota = container.aiClient.quota()
                                        aiStatus = if (settings.isEnglish) {
                                            "Connected • quota ${quota.used}/${quota.limit}"
                                        } else {
                                            "Terhubung • kuota ${quota.used}/${quota.limit}"
                                        }
                                    }.onFailure { error ->
                                        aiStatus = error.localizedMessage ?: "Gagal terhubung"
                                    }
                                }
                            }) { Text(if (settings.isEnglish) "Test & connect" else "Uji & hubungkan") }
                            TextButton(onClick = {
                                scope.launch {
                                    container.aiClient.forgetToken()
                                    aiStatus = if (settings.isEnglish) "Token forgotten" else "Token dilupakan"
                                }
                            }) { Text(if (settings.isEnglish) "Forget token" else "Lupakan token") }
                        }
                        TextButton(onClick = {
                            scope.launch {
                                val granted = container.settings.aiUploadConsent()
                                container.settings.setAiUploadConsent(!granted)
                                aiStatus = if (!granted) {
                                    if (settings.isEnglish) "Cloud upload allowed" else "Unggahan cloud diizinkan"
                                } else {
                                    if (settings.isEnglish) "Cloud upload revoked" else "Izin unggah dicabut"
                                }
                            }
                        }) {
                            Text(
                                if (settings.aiUploadConsent) {
                                    if (settings.isEnglish) "Revoke upload consent" else "Cabut izin unggah"
                                } else {
                                    if (settings.isEnglish) "Allow upload consent" else "Izinkan unggah"
                                }
                            )
                        }
                        if (aiStatus.isNotBlank()) {
                            Text(aiStatus, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(stringResource(R.string.settings_security_workspace), style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = { onOpenStudio(DedicatedScreenRoute.KEY_VAULT) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Filled.Key, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.settings_key_vault_btn))
                            }
                            Button(
                                onClick = { onOpenStudio(DedicatedScreenRoute.WORKSPACE_BACKUP) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Filled.Backup, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.settings_backup_btn))
                            }
                        }
                    }
                }
            }

            item {
                CloudConnectionsCard(container = container, isEnglish = settings.isEnglish)
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(stringResource(R.string.settings_licenses_title), style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = stringResource(R.string.settings_licenses_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedButton(
                            onClick = onOpenLicenses,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("open_source_licenses_button")
                        ) {
                            Icon(Icons.Filled.Gavel, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.settings_licenses_btn))
                        }
                    }
                }
            }
        }
    }
}
