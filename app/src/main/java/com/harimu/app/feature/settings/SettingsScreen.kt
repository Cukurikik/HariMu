package com.harimu.app.feature.settings

import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.harimu.app.AppContainer
import com.harimu.app.R
import com.harimu.app.core.ai.AiException
import com.harimu.app.core.ai.AiQuota
import com.harimu.app.core.data.AppSettings
import com.harimu.app.core.design.HariMuThemeTokens
import com.harimu.app.core.design.MonoTextStyle
import com.harimu.app.core.design.ParamSlider
import com.harimu.app.core.design.ParamToggle
import com.harimu.app.core.design.ThemeMode
import com.harimu.app.core.files.LocalModelStatus
import com.harimu.app.core.toolapi.DedicatedScreenRoute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URI
import kotlin.math.roundToInt

private data class SettingsStorageStats(
    val tempBytes: Long,
    val freeBytes: Long,
    val modelStatuses: List<LocalModelStatus>,
    val modelTotalBytes: Long
)

private fun isValidAiGatewayInput(raw: String): Boolean {
    val clean = raw.trim()
    return clean.isEmpty() || clean.startsWith("https://") || clean.startsWith("http://10.0.2.2")
}

private fun gatewayHostLabel(raw: String): String {
    val clean = raw.trim()
    if (clean.isBlank()) return "Cloudflare AI Gateway"
    return runCatching { URI(clean).host?.takeIf { it.isNotBlank() } }.getOrNull() ?: clean
}

private fun formatResetDuration(resetsInSec: Long, unknownLabel: String): String {
    if (resetsInSec <= 0L) return unknownLabel
    val hours = resetsInSec / 3600L
    val mins = (resetsInSec % 3600L) / 60L
    return when {
        hours > 0 -> "${hours}h ${mins}m"
        mins > 0 -> "${mins}m"
        else -> "${resetsInSec}s"
    }
}

@OptIn(ExperimentalLayoutApi::class)
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

    var tempBytes by remember { mutableLongStateOf(0L) }
    var freeBytes by remember { mutableLongStateOf(0L) }
    var modelStatuses by remember { mutableStateOf<List<LocalModelStatus>>(emptyList()) }
    var modelTotalBytes by remember { mutableLongStateOf(0L) }

    suspend fun refreshStorageAndModels() {
        val stats = withContext(Dispatchers.IO) {
            SettingsStorageStats(
                tempBytes = container.storage.tempCacheSizeBytes(),
                freeBytes = container.storage.availableDiskSpaceBytes(),
                modelStatuses = container.modelManager.listStatuses(),
                modelTotalBytes = container.modelManager.totalDownloadedBytes()
            )
        }
        tempBytes = stats.tempBytes
        freeBytes = stats.freeBytes
        modelStatuses = stats.modelStatuses
        modelTotalBytes = stats.modelTotalBytes
    }

    LaunchedEffect(Unit) {
        refreshStorageAndModels()
    }

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

    val spacing = HariMuThemeTokens.spacing
    val layout = HariMuThemeTokens.layout
    val semantic = HariMuThemeTokens.semantic
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = layout.formMaxWidth),
            contentPadding = PaddingValues(spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.sm)
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.settings_appearance), style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = stringResource(R.string.settings_appearance_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            ThemeMode.entries.forEach { mode ->
                                FilterChip(
                                    selected = settings.resolvedThemeMode() == mode,
                                    onClick = { scope.launch { container.settings.setThemeMode(mode) } },
                                    label = { Text(stringResource(mode.labelRes)) },
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag("theme_chip_${mode.name}")
                                )
                            }
                        }

                        if (settings.resolvedThemeMode() == ThemeMode.AMOLED) {
                            Text(
                                text = stringResource(R.string.settings_amoled_note),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
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
                        Text(
                            text = stringResource(R.string.settings_engine_storage_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        ParamSlider(
                            label = stringResource(R.string.settings_export_quality),
                            value = settings.outputQuality.toFloat(),
                            min = 40f,
                            max = 100f,
                            step = 5f,
                            unit = "%",
                            onValueChange = { scope.launch { container.settings.setOutputQuality(it.roundToInt()) } }
                        )
                        Text(
                            text = stringResource(R.string.settings_export_quality_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
                                        .heightIn(min = 48.dp)
                                        .testTag("pick_output_folder_button")
                                ) {
                                    Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(stringResource(R.string.settings_output_folder_pick))
                                }
                                if (settings.outputFolderUri.isNotBlank()) {
                                    TextButton(
                                        onClick = { scope.launch { container.settings.setOutputFolder("", "") } },
                                        modifier = Modifier.heightIn(min = 48.dp)
                                    ) {
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
                            onCheckedChange = { scope.launch { container.settings.setAutoCleanTemp(it) } },
                            modifier = Modifier.testTag("auto_clean_temp_switch")
                        )

                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    val msg = withContext(Dispatchers.IO) {
                                        val freed = container.storage.clearTempCache()
                                        resources.getString(R.string.toast_temp_cleared, container.storage.formatBytes(freed))
                                    }
                                    refreshStorageAndModels()
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
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
                                .heightIn(min = 48.dp)
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
                                    IconButton(
                                        onClick = {
                                            scope.launch {
                                                withContext(Dispatchers.IO) {
                                                    container.modelManager.deleteModel(status.spec.id)
                                                }
                                                refreshStorageAndModels()
                                            }
                                        },
                                        modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                                    ) {
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
                var gatewayUrl by rememberSaveable(settings.aiGatewayUrl) { mutableStateOf(settings.aiGatewayUrl) }
                var aiStatus by remember { mutableStateOf("") }
                var isTestingAi by remember { mutableStateOf(false) }
                var aiTestError by remember { mutableStateOf(false) }
                var verifiedGatewayUrl by remember { mutableStateOf<String?>(null) }
                var lastQuota by remember { mutableStateOf<AiQuota?>(null) }
                var hasCachedToken by remember { mutableStateOf(false) }

                LaunchedEffect(settings.aiGatewayUrl, aiStatus) {
                    hasCachedToken = container.settings.aiToken().first.isNotBlank()
                }

                val urlValid = isValidAiGatewayInput(gatewayUrl)
                val normalizedUrl = gatewayUrl.trim().trimEnd('/')
                val isVerifiedCurrent = normalizedUrl.isNotBlank() && verifiedGatewayUrl == normalizedUrl

                fun runGatewayConnectionTest() {
                    if (!isValidAiGatewayInput(gatewayUrl) || gatewayUrl.isBlank()) {
                        aiTestError = true
                        aiStatus = resources.getString(R.string.settings_ai_gateway_invalid_url)
                        return
                    }
                    scope.launch {
                        isTestingAi = true
                        aiTestError = false
                        container.settings.setAiGatewayUrl(gatewayUrl)
                        aiStatus = resources.getString(R.string.settings_ai_gateway_connecting)
                        runCatching {
                            container.aiClient.ensureToken(forceRefresh = true)
                            container.aiClient.quota()
                        }.onSuccess { quota ->
                            lastQuota = quota
                            verifiedGatewayUrl = normalizedUrl
                            hasCachedToken = true
                            aiTestError = false
                            aiStatus = resources.getString(
                                R.string.settings_ai_gateway_connected,
                                quota.used,
                                quota.limit
                            )
                        }.onFailure { error ->
                            aiTestError = true
                            verifiedGatewayUrl = null
                            aiStatus = (error as? AiException)?.userMessage(settings.isEnglish)
                                ?: error.localizedMessage
                                ?: resources.getString(R.string.settings_ai_gateway_connect_failed)
                        }
                        isTestingAi = false
                    }
                }

                Card(
                    modifier = Modifier.testTag("ai_gateway_card"),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = layout.cardBorderAlpha))
                ) {
                    Column(modifier = Modifier.padding(spacing.md), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        Text(
                            text = stringResource(R.string.settings_ai_gateway_title),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = stringResource(R.string.settings_ai_gateway_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        val (badgeBg, badgeFg, badgeLabel, badgeIcon) = when {
                            aiTestError -> Quadruple(
                                MaterialTheme.colorScheme.errorContainer,
                                MaterialTheme.colorScheme.onErrorContainer,
                                stringResource(R.string.settings_ai_gateway_status_error),
                                Icons.Filled.ErrorOutline
                            )
                            isVerifiedCurrent -> Quadruple(
                                semantic.successContainer,
                                semantic.onSuccessContainer,
                                stringResource(R.string.settings_ai_gateway_status_verified),
                                Icons.Filled.CheckCircle
                            )
                            settings.aiGatewayUrl.isBlank() -> Quadruple(
                                MaterialTheme.colorScheme.surfaceVariant,
                                MaterialTheme.colorScheme.onSurfaceVariant,
                                stringResource(R.string.settings_ai_gateway_status_not_set),
                                Icons.Filled.CloudOff
                            )
                            hasCachedToken -> Quadruple(
                                semantic.infoContainer,
                                semantic.onInfoContainer,
                                stringResource(R.string.settings_ai_gateway_status_token_ready),
                                Icons.Filled.CheckCircle
                            )
                            else -> Quadruple(
                                semantic.warningContainer,
                                semantic.onWarningContainer,
                                stringResource(R.string.settings_ai_gateway_status_saved),
                                Icons.Filled.ErrorOutline
                            )
                        }
                        Surface(
                            modifier = Modifier.testTag("ai_gateway_status_badge"),
                            shape = RoundedCornerShape(999.dp),
                            color = badgeBg
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(badgeIcon, contentDescription = null, tint = badgeFg, modifier = Modifier.size(16.dp))
                                Text(badgeLabel, style = MaterialTheme.typography.labelMedium, color = badgeFg)
                            }
                        }

                        if (isTestingAi) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }

                        OutlinedTextField(
                            value = gatewayUrl,
                            onValueChange = {
                                gatewayUrl = it
                                aiTestError = false
                            },
                            label = { Text(stringResource(R.string.settings_ai_gateway_url_label)) },
                            isError = !urlValid,
                            supportingText = if (!urlValid) {
                                { Text(stringResource(R.string.settings_ai_gateway_invalid_url)) }
                            } else {
                                null
                            },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("ai_gateway_url_input")
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                            verticalArrangement = Arrangement.spacedBy(spacing.xxs)
                        ) {
                            Button(
                                enabled = !isTestingAi && urlValid,
                                onClick = {
                                    scope.launch {
                                        container.settings.setAiGatewayUrl(gatewayUrl)
                                        verifiedGatewayUrl = null
                                        aiTestError = false
                                        aiStatus = resources.getString(R.string.settings_ai_gateway_saved)
                                    }
                                },
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("ai_gateway_save_button")
                            ) { Text(stringResource(R.string.settings_ai_gateway_save)) }

                            OutlinedButton(
                                enabled = !isTestingAi && urlValid && gatewayUrl.isNotBlank(),
                                onClick = ::runGatewayConnectionTest,
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("ai_gateway_test_button")
                            ) { Text(stringResource(R.string.settings_ai_gateway_test)) }

                            if (aiTestError) {
                                OutlinedButton(
                                    enabled = !isTestingAi && urlValid && gatewayUrl.isNotBlank(),
                                    onClick = ::runGatewayConnectionTest,
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag("ai_gateway_retry_button")
                                ) {
                                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(stringResource(R.string.settings_ai_gateway_retry))
                                }
                            }

                            if (hasCachedToken) {
                                TextButton(
                                    enabled = !isTestingAi,
                                    onClick = {
                                        scope.launch {
                                            container.aiClient.forgetToken()
                                            hasCachedToken = false
                                            verifiedGatewayUrl = null
                                            lastQuota = null
                                            aiTestError = false
                                            aiStatus = resources.getString(R.string.settings_ai_gateway_forgotten)
                                        }
                                    },
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag("ai_gateway_forget_button")
                                ) { Text(stringResource(R.string.settings_ai_gateway_forget)) }
                            }
                        }

                        lastQuota?.let { quota ->
                            Text(
                                text = stringResource(
                                    R.string.settings_ai_gateway_quota_fmt,
                                    quota.used,
                                    quota.limit,
                                    formatResetDuration(
                                        quota.resetsInSec,
                                        stringResource(R.string.settings_ai_gateway_reset_unknown)
                                    )
                                ),
                                style = MonoTextStyle,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }

                        ParamToggle(
                            label = stringResource(R.string.settings_ai_gateway_consent_title),
                            subtitle = if (settings.aiUploadConsent) {
                                stringResource(
                                    R.string.settings_ai_gateway_consent_desc_on,
                                    gatewayHostLabel(settings.aiGatewayUrl)
                                )
                            } else {
                                stringResource(R.string.settings_ai_gateway_consent_desc_off)
                            },
                            checked = settings.aiUploadConsent,
                            onCheckedChange = { allowed ->
                                scope.launch {
                                    container.settings.setAiUploadConsent(allowed)
                                    aiStatus = resources.getString(
                                        if (allowed) {
                                            R.string.settings_ai_gateway_upload_allowed
                                        } else {
                                            R.string.settings_ai_gateway_upload_revoked
                                        }
                                    )
                                }
                            },
                            modifier = Modifier.testTag("ai_upload_consent_switch")
                        )

                        Text(
                            text = stringResource(R.string.settings_ai_gateway_quota_info),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (aiStatus.isNotBlank()) {
                            Text(
                                text = aiStatus,
                                style = MonoTextStyle,
                                color = if (aiTestError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.testTag("ai_gateway_status_text")
                            )
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
                        Text(
                            text = stringResource(R.string.settings_security_workspace_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedButton(
                                onClick = { onOpenStudio(DedicatedScreenRoute.KEY_VAULT) },
                                modifier = Modifier
                                    .weight(1f)
                                    .widthIn(min = 140.dp)
                                    .heightIn(min = 48.dp)
                                    .testTag("settings_key_vault_button")
                            ) {
                                Icon(Icons.Filled.Key, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    stringResource(R.string.settings_key_vault_btn),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Button(
                                onClick = { onOpenStudio(DedicatedScreenRoute.WORKSPACE_BACKUP) },
                                modifier = Modifier
                                    .weight(1f)
                                    .widthIn(min = 140.dp)
                                    .heightIn(min = 48.dp)
                                    .testTag("settings_backup_button")
                            ) {
                                Icon(Icons.Filled.Backup, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    stringResource(R.string.settings_backup_btn),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
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
                                .heightIn(min = 48.dp)
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

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

