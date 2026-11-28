package com.harimu.app.feature.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
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
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.harimu.app.AppContainer
import com.harimu.app.R
import com.harimu.app.core.cloud.CloudConfig
import com.harimu.app.core.cloud.CloudConnectionState
import com.harimu.app.core.cloud.CloudProvider
import com.harimu.app.core.cloud.OAuthCallbackBus
import com.harimu.app.core.cloud.OAuthPkce
import com.harimu.app.core.cloud.UploadStatus
import com.harimu.app.core.design.HariMuThemeTokens
import com.harimu.app.core.design.MonoTextStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Menyimpan verifier PKCE selama browser terbuka (satu proses). */
private object PendingOAuth {
    var provider: CloudProvider? = null
    var verifier: String = ""
    var state: String = ""
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CloudConnectionsCard(container: AppContainer, isEnglish: Boolean) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val semantic = HariMuThemeTokens.semantic

    var config by remember { mutableStateOf(CloudConfig()) }
    var loaded by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var statusIsError by remember { mutableStateOf(false) }
    var verifiedFingerprint by remember { mutableStateOf<String?>(null) }
    var isTesting by remember { mutableStateOf(false) }
    var isExchangingOAuth by remember { mutableStateOf(false) }
    var isQueueBusy by remember { mutableStateOf(false) }
    var showS3Secret by remember { mutableStateOf(false) }
    var showCustomHeaders by remember { mutableStateOf(false) }

    val tasks by container.uploadQueue.tasksFlow.collectAsStateWithLifecycle()
    var codeInput by rememberSaveable { mutableStateOf("") }
    var showCodeInput by rememberSaveable { mutableStateOf(false) }

    fun refreshTasks() {
        scope.launch(Dispatchers.IO) { container.uploadQueue.snapshot() }
    }

    LaunchedEffect(Unit) {
        config = container.cloudStore.load()
        container.uploadQueue.snapshot()
        loaded = true
    }

    fun persistConfig(updated: CloudConfig, statusText: String = resources.getString(R.string.cloud_saved_status)) {
        scope.launch(Dispatchers.IO) {
            container.cloudStore.save(updated)
            withContext(Dispatchers.Main) {
                config = updated
                status = statusText
                statusIsError = false
            }
        }
    }

    fun exchangeCode(code: String, expectedState: String) {
        val provider = PendingOAuth.provider ?: return
        if (expectedState.isNotBlank() && PendingOAuth.state.isNotBlank() && expectedState != PendingOAuth.state) {
            status = resources.getString(R.string.cloud_oauth_state_mismatch)
            statusIsError = true
            return
        }
        val clientId = if (provider == CloudProvider.GOOGLE_DRIVE) config.driveClientId else config.dropboxAppKey
        scope.launch {
            isExchangingOAuth = true
            statusIsError = false
            status = resources.getString(R.string.cloud_oauth_exchanging)
            runCatching {
                container.cloudUploader.exchangeCode(provider, code, PendingOAuth.verifier, clientId)
            }.onSuccess { tokens ->
                val updated = if (provider == CloudProvider.GOOGLE_DRIVE) {
                    config.copy(
                        provider = CloudProvider.GOOGLE_DRIVE,
                        enabled = true,
                        driveAccessToken = tokens.accessToken,
                        driveRefreshToken = tokens.refreshToken.ifBlank { config.driveRefreshToken },
                        driveTokenExpiresAt = System.currentTimeMillis() + tokens.expiresInSec * 1000
                    )
                } else {
                    config.copy(
                        provider = CloudProvider.DROPBOX,
                        enabled = true,
                        dropboxAccessToken = tokens.accessToken,
                        dropboxRefreshToken = tokens.refreshToken.ifBlank { config.dropboxRefreshToken },
                        dropboxTokenExpiresAt = System.currentTimeMillis() + tokens.expiresInSec * 1000
                    )
                }
                verifiedFingerprint = null
                persistConfig(updated, resources.getString(R.string.cloud_oauth_connected))
            }.onFailure { error ->
                statusIsError = true
                status = error.localizedMessage ?: "OAuth error"
            }
            PendingOAuth.provider = null
            PendingOAuth.verifier = ""
            PendingOAuth.state = ""
            showCodeInput = false
            codeInput = ""
            isExchangingOAuth = false
        }
    }

    LaunchedEffect(Unit) {
        OAuthCallbackBus.events.collect { uri ->
            val parsed = Uri.parse(uri)
            val oauthError = parsed.getQueryParameter("error")
            if (!oauthError.isNullOrBlank()) {
                val desc = parsed.getQueryParameter("error_description").orEmpty()
                val detail = listOf(oauthError, desc).filter { it.isNotBlank() }.joinToString(": ")
                status = resources.getString(R.string.cloud_oauth_error_fmt, detail)
                statusIsError = true
                PendingOAuth.provider = null
                PendingOAuth.verifier = ""
                PendingOAuth.state = ""
                showCodeInput = false
                codeInput = ""
                return@collect
            }
            val code = parsed.getQueryParameter("code")
            val state = parsed.getQueryParameter("state").orEmpty()
            if (!code.isNullOrBlank()) exchangeCode(code, state)
        }
    }

    val validationError = remember(config, isEnglish) {
        if (config.provider == CloudProvider.GOOGLE_DRIVE || config.provider == CloudProvider.DROPBOX) {
            null
        } else {
            config.validationError(isEnglish)
        }
    }
    val connectionState = remember(config) { config.connectionState() }
    val isVerifiedCurrent = loaded &&
        config.isConfigured() &&
        verifiedFingerprint == config.redacted() &&
        !statusIsError

    Card(
        modifier = Modifier.testTag("cloud_connections_card"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = stringResource(R.string.cloud_title),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = stringResource(R.string.cloud_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            val (badgeBg, badgeFg, badgeLabel, badgeIcon) = when {
                statusIsError -> CloudBadgeStyle(
                    MaterialTheme.colorScheme.errorContainer,
                    MaterialTheme.colorScheme.onErrorContainer,
                    stringResource(R.string.cloud_state_error),
                    Icons.Filled.ErrorOutline
                )
                !loaded || connectionState == CloudConnectionState.NOT_CONFIGURED -> CloudBadgeStyle(
                    MaterialTheme.colorScheme.surfaceVariant,
                    MaterialTheme.colorScheme.onSurfaceVariant,
                    stringResource(R.string.cloud_state_not_configured),
                    Icons.Filled.CloudOff
                )
                isVerifiedCurrent -> CloudBadgeStyle(
                    semantic.successContainer,
                    semantic.onSuccessContainer,
                    stringResource(R.string.cloud_state_verified),
                    Icons.Filled.CheckCircle
                )
                connectionState == CloudConnectionState.TOKEN_EXPIRED -> CloudBadgeStyle(
                    semantic.warningContainer,
                    semantic.onWarningContainer,
                    stringResource(R.string.cloud_state_token_expired),
                    Icons.Filled.ErrorOutline
                )
                else -> CloudBadgeStyle(
                    semantic.infoContainer,
                    semantic.onInfoContainer,
                    stringResource(R.string.cloud_state_saved_unverified),
                    Icons.Filled.ErrorOutline
                )
            }
            Surface(
                modifier = Modifier.testTag("cloud_connection_status_badge"),
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

            if (isTesting || isExchangingOAuth || isQueueBusy) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CloudProvider.entries.forEach { provider ->
                    FilterChip(
                        selected = config.provider == provider,
                        onClick = {
                            config = config.copy(provider = provider)
                            statusIsError = false
                            verifiedFingerprint = null
                        },
                        label = { Text(provider.label(isEnglish)) },
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("cloud_provider_chip_${provider.name}")
                    )
                }
            }

            when (config.provider) {
                CloudProvider.S3_R2 -> {
                    Field(stringResource(R.string.cloud_field_s3_endpoint), config.endpoint) {
                        config = config.copy(endpoint = it)
                        verifiedFingerprint = null
                    }
                    Field(stringResource(R.string.cloud_field_s3_region), config.region) {
                        config = config.copy(region = it)
                        verifiedFingerprint = null
                    }
                    Field(stringResource(R.string.cloud_field_s3_bucket), config.bucket) {
                        config = config.copy(bucket = it)
                        verifiedFingerprint = null
                    }
                    Field(stringResource(R.string.cloud_field_s3_access_key), config.accessKeyId) {
                        config = config.copy(accessKeyId = it)
                        verifiedFingerprint = null
                    }
                    SecretField(
                        label = stringResource(R.string.cloud_field_s3_secret_key),
                        value = config.secretAccessKey,
                        visible = showS3Secret,
                        onToggleVisibility = { showS3Secret = !showS3Secret },
                        fieldTag = "cloud_s3_secret_input",
                        toggleTag = "cloud_secret_visibility_toggle"
                    ) {
                        config = config.copy(secretAccessKey = it)
                        verifiedFingerprint = null
                    }
                    Field(stringResource(R.string.cloud_field_path_prefix), config.pathPrefix) {
                        config = config.copy(pathPrefix = it)
                    }
                    Text(
                        text = stringResource(R.string.cloud_s3_probe_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                CloudProvider.GOOGLE_DRIVE -> {
                    Field(stringResource(R.string.cloud_field_drive_client_id), config.driveClientId) {
                        config = config.copy(driveClientId = it)
                        verifiedFingerprint = null
                    }
                    Field(stringResource(R.string.cloud_field_drive_folder_id), config.driveFolderId) {
                        config = config.copy(driveFolderId = it)
                    }
                    Text(
                        text = stringResource(R.string.cloud_drive_scope_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            enabled = !isTesting && !isExchangingOAuth,
                            onClick = {
                                if (config.driveClientId.isBlank()) {
                                    status = resources.getString(R.string.cloud_oauth_fill_drive_id)
                                    statusIsError = true
                                    return@Button
                                }
                                val request = OAuthPkce.driveAuthRequest(config.driveClientId.trim())
                                PendingOAuth.provider = CloudProvider.GOOGLE_DRIVE
                                PendingOAuth.verifier = request.codeVerifier
                                PendingOAuth.state = request.state
                                showCodeInput = true
                                statusIsError = false
                                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(request.url))) }
                                    .onFailure {
                                        status = it.localizedMessage ?: resources.getString(R.string.cloud_oauth_no_browser)
                                        statusIsError = true
                                    }
                            },
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) { Text(stringResource(R.string.cloud_drive_connect)) }
                        TextButton(
                            enabled = (config.driveAccessToken.isNotBlank() || config.driveRefreshToken.isNotBlank()) &&
                                !isTesting && !isExchangingOAuth,
                            onClick = {
                                verifiedFingerprint = null
                                showCodeInput = false
                                codeInput = ""
                                persistConfig(
                                    updated = config.copy(
                                        driveAccessToken = "",
                                        driveRefreshToken = "",
                                        driveTokenExpiresAt = 0L,
                                        enabled = false
                                    ),
                                    statusText = resources.getString(R.string.cloud_oauth_disconnected)
                                )
                            },
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("cloud_drive_disconnect_button")
                        ) { Text(stringResource(R.string.cloud_oauth_disconnect)) }
                    }
                    OAuthCodeRow(
                        visible = showCodeInput,
                        value = codeInput,
                        enabled = !isExchangingOAuth,
                        onValueChange = { codeInput = it },
                        onSubmit = {
                            val parsed = runCatching { Uri.parse(codeInput) }.getOrNull()
                            val oauthErr = parsed?.getQueryParameter("error")
                            if (!oauthErr.isNullOrBlank()) {
                                status = resources.getString(R.string.cloud_oauth_error_fmt, oauthErr)
                                statusIsError = true
                                return@OAuthCodeRow
                            }
                            val code = parsed?.getQueryParameter("code") ?: codeInput.trim()
                            exchangeCode(code, parsed?.getQueryParameter("state").orEmpty())
                        }
                    )
                }
                CloudProvider.DROPBOX -> {
                    Field(stringResource(R.string.cloud_dropbox_app_key), config.dropboxAppKey) {
                        config = config.copy(dropboxAppKey = it)
                        verifiedFingerprint = null
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            enabled = !isTesting && !isExchangingOAuth,
                            onClick = {
                                if (config.dropboxAppKey.isBlank()) {
                                    status = resources.getString(R.string.cloud_oauth_fill_dropbox_key)
                                    statusIsError = true
                                    return@Button
                                }
                                val request = OAuthPkce.dropboxAuthRequest(config.dropboxAppKey.trim())
                                PendingOAuth.provider = CloudProvider.DROPBOX
                                PendingOAuth.verifier = request.codeVerifier
                                PendingOAuth.state = request.state
                                showCodeInput = true
                                statusIsError = false
                                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(request.url))) }
                                    .onFailure {
                                        status = it.localizedMessage ?: resources.getString(R.string.cloud_oauth_no_browser)
                                        statusIsError = true
                                    }
                            },
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) { Text(stringResource(R.string.cloud_dropbox_connect)) }
                        TextButton(
                            enabled = (config.dropboxAccessToken.isNotBlank() || config.dropboxRefreshToken.isNotBlank()) &&
                                !isTesting && !isExchangingOAuth,
                            onClick = {
                                verifiedFingerprint = null
                                showCodeInput = false
                                codeInput = ""
                                persistConfig(
                                    updated = config.copy(
                                        dropboxAccessToken = "",
                                        dropboxRefreshToken = "",
                                        dropboxTokenExpiresAt = 0L,
                                        enabled = false
                                    ),
                                    statusText = resources.getString(R.string.cloud_oauth_disconnected)
                                )
                            },
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("cloud_dropbox_disconnect_button")
                        ) { Text(stringResource(R.string.cloud_oauth_disconnect)) }
                    }
                    OAuthCodeRow(
                        visible = showCodeInput,
                        value = codeInput,
                        enabled = !isExchangingOAuth,
                        onValueChange = { codeInput = it },
                        onSubmit = {
                            val parsed = runCatching { Uri.parse(codeInput) }.getOrNull()
                            val oauthErr = parsed?.getQueryParameter("error")
                            if (!oauthErr.isNullOrBlank()) {
                                status = resources.getString(R.string.cloud_oauth_error_fmt, oauthErr)
                                statusIsError = true
                                return@OAuthCodeRow
                            }
                            val code = parsed?.getQueryParameter("code") ?: codeInput.trim()
                            exchangeCode(code, parsed?.getQueryParameter("state").orEmpty())
                        }
                    )
                }
                CloudProvider.CUSTOM_REST -> {
                    Field(stringResource(R.string.cloud_field_rest_url), config.customUrl) {
                        config = config.copy(customUrl = it)
                        verifiedFingerprint = null
                    }
                    Field(stringResource(R.string.cloud_field_rest_method), config.customMethod) {
                        config = config.copy(customMethod = it)
                        verifiedFingerprint = null
                    }
                    Field(stringResource(R.string.cloud_field_rest_field), config.customFieldName) {
                        config = config.copy(customFieldName = it)
                        verifiedFingerprint = null
                    }
                    SecretField(
                        label = stringResource(R.string.cloud_field_rest_headers),
                        value = config.customHeadersJson,
                        visible = showCustomHeaders,
                        onToggleVisibility = { showCustomHeaders = !showCustomHeaders },
                        fieldTag = "cloud_rest_headers_input",
                        toggleTag = "cloud_rest_headers_visibility_toggle"
                    ) {
                        config = config.copy(customHeadersJson = it)
                        verifiedFingerprint = null
                    }
                }
            }

            if (config.isInsecureHttp()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = semantic.warningContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.cloud_rest_http_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = semantic.onWarningContainer,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            if (validationError != null && config.redacted() != CloudConfig(provider = config.provider).redacted()) {
                Text(
                    text = validationError,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("cloud_validation_error")
                )
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Button(
                    enabled = !isTesting && !isExchangingOAuth,
                    onClick = {
                        val err = config.validationError(isEnglish)
                        if (err != null) {
                            status = err
                            statusIsError = true
                            return@Button
                        }
                        val validEnabled = config.isConfigured()
                        persistConfig(config.copy(enabled = validEnabled))
                    },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("cloud_save_button")
                ) {
                    Text(stringResource(R.string.cloud_action_save))
                }
                OutlinedButton(
                    enabled = !isTesting && !isExchangingOAuth && config.isConfigured(),
                    onClick = {
                        val err = config.validationError(isEnglish)
                        if (err != null) {
                            status = err
                            statusIsError = true
                            return@OutlinedButton
                        }
                        scope.launch {
                            isTesting = true
                            statusIsError = false
                            status = resources.getString(R.string.cloud_action_testing)
                            runCatching {
                                container.cloudUploader.testConnectionDetailed(
                                    config = config,
                                    persistConfig = { updated -> container.cloudStore.save(updated) }
                                )
                            }.onSuccess { outcome ->
                                config = outcome.updatedConfig
                                verifiedFingerprint = outcome.updatedConfig.redacted()
                                statusIsError = false
                                status = outcome.summary
                            }.onFailure { err ->
                                verifiedFingerprint = null
                                statusIsError = true
                                status = err.localizedMessage ?: "Test failed"
                            }
                            isTesting = false
                        }
                    },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("cloud_test_connection_button")
                ) {
                    Text(
                        stringResource(
                            if (isTesting) R.string.cloud_action_testing else R.string.cloud_action_test
                        )
                    )
                }
                OutlinedButton(
                    enabled = !isQueueBusy,
                    onClick = { refreshTasks() },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text(stringResource(R.string.cloud_action_refresh_queue))
                }
            }

            if (status.isNotBlank()) {
                Text(
                    text = status,
                    style = MonoTextStyle,
                    color = if (statusIsError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.testTag("cloud_status_text")
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
            Text(stringResource(R.string.cloud_queue_title), style = MaterialTheme.typography.labelLarge)
            val queuedCount = tasks.count { it.status == UploadStatus.QUEUED }
            val runningCount = tasks.count { it.status == UploadStatus.RUNNING }
            val failedCount = tasks.count { it.status == UploadStatus.FAILED }
            val doneCount = tasks.count { it.status == UploadStatus.DONE }
            if (tasks.isEmpty()) {
                Text(
                    text = stringResource(R.string.cloud_queue_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = stringResource(
                        R.string.cloud_queue_summary_fmt,
                        queuedCount,
                        runningCount,
                        failedCount,
                        doneCount
                    ),
                    style = MonoTextStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            tasks.take(5).forEach { task ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(task.displayName, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = "${task.status} • ${task.attempts}x" +
                                if (task.lastError.isBlank()) "" else " • ${task.lastError.take(60)}",
                            style = MonoTextStyle,
                            color = if (task.status == UploadStatus.FAILED) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(task.provider.name, style = MaterialTheme.typography.labelSmall)
                        if (task.status == UploadStatus.FAILED) {
                            IconButton(
                                enabled = !isQueueBusy,
                                onClick = {
                                    scope.launch(Dispatchers.IO) {
                                        isQueueBusy = true
                                        runCatching {
                                            container.uploadQueue.retryTask(
                                                taskId = task.id,
                                                configProvider = { container.cloudStore.load() },
                                                persistConfig = { container.cloudStore.save(it) }
                                            )
                                        }
                                        isQueueBusy = false
                                    }
                                },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(
                                    Icons.Filled.Refresh,
                                    contentDescription = stringResource(R.string.job_upload_retry)
                                )
                            }
                        }
                        if (task.status != UploadStatus.RUNNING) {
                            IconButton(
                                enabled = !isQueueBusy,
                                onClick = {
                                    scope.launch(Dispatchers.IO) {
                                        container.uploadQueue.removeTask(task.id)
                                    }
                                },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = stringResource(R.string.action_delete)
                                )
                            }
                        }
                    }
                }
            }
            if (tasks.size > 5) {
                Text(
                    text = stringResource(R.string.cloud_queue_overflow_fmt, tasks.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Button(
                    enabled = !isQueueBusy && (queuedCount > 0 || failedCount > 0),
                    onClick = {
                        scope.launch(Dispatchers.IO) {
                            isQueueBusy = true
                            val result = runCatching {
                                container.uploadQueue.processPending(
                                    configProvider = { container.cloudStore.load() },
                                    persistConfig = { container.cloudStore.save(it) }
                                )
                            }
                            withContext(Dispatchers.Main) {
                                result.onSuccess { uploaded ->
                                    if (uploaded > 0) {
                                        statusIsError = false
                                        status = resources.getString(R.string.cloud_queue_processed_fmt, uploaded)
                                    } else {
                                        statusIsError = true
                                        status = resources.getString(R.string.cloud_queue_none_uploaded)
                                    }
                                }.onFailure { err ->
                                    statusIsError = true
                                    status = err.localizedMessage ?: resources.getString(R.string.cloud_queue_none_uploaded)
                                }
                                isQueueBusy = false
                            }
                        }
                    },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) { Text(stringResource(R.string.cloud_action_process_queue)) }
                OutlinedButton(
                    enabled = !isQueueBusy && failedCount > 0,
                    onClick = {
                        scope.launch(Dispatchers.IO) {
                            isQueueBusy = true
                            runCatching {
                                container.uploadQueue.retryFailed(
                                    configProvider = { container.cloudStore.load() },
                                    persistConfig = { container.cloudStore.save(it) }
                                )
                            }
                            isQueueBusy = false
                        }
                    },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) { Text(stringResource(R.string.cloud_action_retry_failed)) }
                TextButton(
                    enabled = !isQueueBusy && doneCount > 0,
                    onClick = {
                        scope.launch(Dispatchers.IO) {
                            container.uploadQueue.clearFinished()
                        }
                    },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) { Text(stringResource(R.string.cloud_action_clear_finished)) }
            }
            Text(
                text = if (loaded && config.isConfigured()) {
                    config.describe(isEnglish)
                } else {
                    stringResource(R.string.cloud_state_not_configured)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private data class CloudBadgeStyle(
    val containerColor: androidx.compose.ui.graphics.Color,
    val contentColor: androidx.compose.ui.graphics.Color,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun SecretField(
    label: String,
    value: String,
    visible: Boolean,
    onToggleVisibility: () -> Unit,
    fieldTag: String,
    toggleTag: String,
    onChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(
                onClick = onToggleVisibility,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag(toggleTag)
            ) {
                Icon(
                    imageVector = if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = stringResource(
                        if (visible) R.string.cloud_cd_hide_secret else R.string.cloud_cd_show_secret
                    )
                )
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .testTag(fieldTag)
    )
}

@Composable
private fun OAuthCodeRow(
    visible: Boolean,
    value: String,
    enabled: Boolean,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit
) {
    if (!visible) return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(stringResource(R.string.cloud_oauth_redirect_label)) },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth()
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Button(
                onClick = onSubmit,
                enabled = enabled && value.isNotBlank(),
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(stringResource(R.string.cloud_oauth_exchange_btn))
            }
            Text(
                text = stringResource(R.string.cloud_oauth_redirect_uri_fmt, OAuthPkce.REDIRECT_URI),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
    }
}
