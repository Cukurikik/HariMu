package com.harimu.app.feature.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.harimu.app.AppContainer
import com.harimu.app.core.cloud.CloudConfig
import com.harimu.app.core.cloud.CloudProvider
import com.harimu.app.core.cloud.OAuthCallbackBus
import com.harimu.app.core.cloud.OAuthPkce
import com.harimu.app.core.cloud.UploadStatus
import com.harimu.app.core.cloud.UploadTask
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

@Composable
fun CloudConnectionsCard(container: AppContainer, isEnglish: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var config by remember { mutableStateOf(CloudConfig()) }
    var loaded by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var tasks by remember { mutableStateOf<List<UploadTask>>(emptyList()) }
    var codeInput by remember { mutableStateOf("") }
    var showCodeInput by remember { mutableStateOf(false) }

    fun refreshTasks() {
        scope.launch(Dispatchers.IO) { tasks = container.uploadQueue.snapshot() }
    }

    LaunchedEffect(Unit) {
        config = container.cloudStore.load()
        tasks = container.uploadQueue.snapshot()
        loaded = true
    }

    fun save(updated: CloudConfig = config) {
        scope.launch(Dispatchers.IO) {
            container.cloudStore.save(updated)
            withContext(Dispatchers.Main) {
                config = updated
                status = if (isEnglish) "Saved (encrypted at rest)" else "Tersimpan (terenkripsi)"
            }
        }
    }

    fun exchangeCode(code: String, expectedState: String) {
        val provider = PendingOAuth.provider ?: return
        if (expectedState.isNotBlank() && PendingOAuth.state.isNotBlank() && expectedState != PendingOAuth.state) {
            status = if (isEnglish) "OAuth state mismatch — discarded" else "State OAuth tidak cocok — diabaikan"
            return
        }
        val clientId = if (provider == CloudProvider.GOOGLE_DRIVE) config.driveClientId else config.dropboxAppKey
        scope.launch {
            status = if (isEnglish) "Exchanging code…" else "Menukar kode…"
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
                save(updated)
                status = if (isEnglish) "Connected and encrypted" else "Terhubung dan terenkripsi"
            }.onFailure { error ->
                status = error.localizedMessage ?: "OAuth gagal"
            }
            PendingOAuth.provider = null
            PendingOAuth.verifier = ""
            PendingOAuth.state = ""
            showCodeInput = false
            codeInput = ""
        }
    }

    LaunchedEffect(Unit) {
        OAuthCallbackBus.events.collect { uri ->
            val parsed = Uri.parse(uri)
            val code = parsed.getQueryParameter("code")
            val state = parsed.getQueryParameter("state").orEmpty()
            if (!code.isNullOrBlank()) exchangeCode(code, state)
        }
    }

    Card(
        modifier = Modifier.testTag("cloud_connections_card"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = if (isEnglish) "Cloud Connections" else "Koneksi Cloud",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = if (isEnglish) {
                    "Credentials are sealed with an AndroidKeyStore key. Uploads run through a retry queue."
                } else {
                    "Kredensial disegel dengan kunci AndroidKeyStore. Unggahan lewat antrean dengan retry."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CloudProvider.entries.forEach { provider ->
                    FilterChip(
                        selected = config.provider == provider,
                        onClick = { config = config.copy(provider = provider) },
                        label = { Text(provider.label(isEnglish)) }
                    )
                }
            }

            when (config.provider) {
                CloudProvider.S3_R2 -> {
                    Field("Endpoint (https://…)", config.endpoint) { config = config.copy(endpoint = it) }
                    Field(if (isEnglish) "Region (auto for R2)" else "Region (auto untuk R2)", config.region) {
                        config = config.copy(region = it)
                    }
                    Field("Bucket", config.bucket) { config = config.copy(bucket = it) }
                    Field("Access Key ID", config.accessKeyId) { config = config.copy(accessKeyId = it) }
                    Field("Secret Access Key", config.secretAccessKey) { config = config.copy(secretAccessKey = it) }
                    Field("Path prefix", config.pathPrefix) { config = config.copy(pathPrefix = it) }
                }
                CloudProvider.GOOGLE_DRIVE -> {
                    Field("OAuth Client ID (PKCE, tanpa secret)", config.driveClientId) {
                        config = config.copy(driveClientId = it)
                    }
                    Field(if (isEnglish) "Folder ID (optional)" else "Folder ID (opsional)", config.driveFolderId) {
                        config = config.copy(driveFolderId = it)
                    }
                    Text(
                        if (isEnglish) "Scope: drive.file (only files created by HariMu)."
                        else "Scope: drive.file (hanya berkas yang dibuat HariMu).",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                if (config.driveClientId.isBlank()) {
                                    status = if (isEnglish) "Fill client ID first" else "Isi Client ID dulu"
                                    return@Button
                                }
                                val request = OAuthPkce.driveAuthRequest(config.driveClientId)
                                PendingOAuth.provider = CloudProvider.GOOGLE_DRIVE
                                PendingOAuth.verifier = request.codeVerifier
                                PendingOAuth.state = request.state
                                showCodeInput = true
                                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(request.url))) }
                                    .onFailure { status = it.localizedMessage ?: "Tidak ada browser" }
                            }
                        ) { Text(if (isEnglish) "Connect Drive" else "Hubungkan Drive") }
                        TextButton(onClick = { config = config.copy(driveAccessToken = "", driveRefreshToken = "") }) {
                            Text(if (isEnglish) "Disconnect" else "Putuskan")
                        }
                    }
                    OAuthCodeRow(
                        visible = showCodeInput,
                        value = codeInput,
                        isEnglish = isEnglish,
                        onValueChange = { codeInput = it },
                        onSubmit = {
                            val parsed = runCatching { Uri.parse(codeInput) }.getOrNull()
                            val code = parsed?.getQueryParameter("code") ?: codeInput.trim()
                            exchangeCode(code, parsed?.getQueryParameter("state").orEmpty())
                        }
                    )
                }
                CloudProvider.DROPBOX -> {
                    Field("Dropbox App Key (PKCE)", config.dropboxAppKey) { config = config.copy(dropboxAppKey = it) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                if (config.dropboxAppKey.isBlank()) {
                                    status = if (isEnglish) "Fill app key first" else "Isi app key dulu"
                                    return@Button
                                }
                                val request = OAuthPkce.dropboxAuthRequest(config.dropboxAppKey)
                                PendingOAuth.provider = CloudProvider.DROPBOX
                                PendingOAuth.verifier = request.codeVerifier
                                PendingOAuth.state = request.state
                                showCodeInput = true
                                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(request.url))) }
                                    .onFailure { status = it.localizedMessage ?: "Tidak ada browser" }
                            }
                        ) { Text(if (isEnglish) "Connect Dropbox" else "Hubungkan Dropbox") }
                        TextButton(onClick = { config = config.copy(dropboxAccessToken = "", dropboxRefreshToken = "") }) {
                            Text(if (isEnglish) "Disconnect" else "Putuskan")
                        }
                    }
                    OAuthCodeRow(
                        visible = showCodeInput,
                        value = codeInput,
                        isEnglish = isEnglish,
                        onValueChange = { codeInput = it },
                        onSubmit = {
                            val parsed = runCatching { Uri.parse(codeInput) }.getOrNull()
                            val code = parsed?.getQueryParameter("code") ?: codeInput.trim()
                            exchangeCode(code, parsed?.getQueryParameter("state").orEmpty())
                        }
                    )
                }
                CloudProvider.CUSTOM_REST -> {
                    Field("URL", config.customUrl) { config = config.copy(customUrl = it) }
                    Field(if (isEnglish) "Method (POST/PUT/PATCH)" else "Metode (POST/PUT/PATCH)", config.customMethod) {
                        config = config.copy(customMethod = it)
                    }
                    Field(if (isEnglish) "File field name" else "Nama field berkas", config.customFieldName) {
                        config = config.copy(customFieldName = it)
                    }
                    Field("Headers JSON ({\"Authorization\":\"…\"})", config.customHeadersJson) {
                        config = config.copy(customHeadersJson = it)
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { save(config.copy(enabled = true)) }) {
                    Text(if (isEnglish) "Save" else "Simpan")
                }
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            status = if (isEnglish) "Testing…" else "Menguji…"
                            runCatching { container.cloudUploader.testConnection(config) }
                                .onSuccess { status = it }
                                .onFailure { status = it.localizedMessage ?: "Tes gagal" }
                        }
                    }
                ) { Text(if (isEnglish) "Test connection" else "Tes koneksi") }
                OutlinedButton(onClick = { refreshTasks() }) {
                    Text(if (isEnglish) "Refresh queue" else "Segarkan antrean")
                }
            }

            if (status.isNotBlank()) {
                Text(status, style = MonoTextStyle, color = MaterialTheme.colorScheme.secondary)
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
            Text(if (isEnglish) "Upload queue" else "Antrean unggahan", style = MaterialTheme.typography.labelLarge)
            if (tasks.isEmpty()) {
                Text(
                    if (isEnglish) "No uploads yet. Use “Upload to cloud” on any tool result."
                    else "Belum ada unggahan. Pakai “Unggah ke cloud” pada hasil tool.",
                    style = MaterialTheme.typography.bodySmall
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
                            text = "${task.status} • ${task.attempts}x" + if (task.lastError.isBlank()) "" else " • ${task.lastError.take(60)}",
                            style = MonoTextStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(task.provider.name, style = MaterialTheme.typography.labelSmall)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        scope.launch(Dispatchers.IO) {
                            val uploaded = container.uploadQueue.processPending(
                                configProvider = { container.cloudStore.load() },
                                persistConfig = { container.cloudStore.save(it) }
                            )
                            tasks = container.uploadQueue.snapshot()
                            withContext(Dispatchers.Main) {
                                status = if (isEnglish) "$uploaded uploaded" else "$uploaded terunggah"
                            }
                        }
                    }
                ) { Text(if (isEnglish) "Process queue" else "Proses antrean") }
                OutlinedButton(
                    onClick = {
                        scope.launch(Dispatchers.IO) {
                            container.uploadQueue.retryFailed(
                                configProvider = { container.cloudStore.load() },
                                persistConfig = { container.cloudStore.save(it) }
                            )
                            tasks = container.uploadQueue.snapshot()
                        }
                    }
                ) { Text(if (isEnglish) "Retry failed" else "Ulangi gagal") }
                TextButton(
                    onClick = {
                        scope.launch(Dispatchers.IO) {
                            container.uploadQueue.clearFinished()
                            tasks = container.uploadQueue.snapshot()
                        }
                    }
                ) { Text(if (isEnglish) "Clear finished" else "Bersihkan selesai") }
            }
            Text(
                text = if (loaded && config.isConfigured()) config.describe(isEnglish)
                else if (isEnglish) "Not configured yet" else "Belum dikonfigurasi",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

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
private fun OAuthCodeRow(
    visible: Boolean,
    value: String,
    isEnglish: Boolean,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit
) {
    if (!visible) return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = {
                Text(if (isEnglish) "Paste redirect URL or code" else "Tempel URL redirect atau kode")
            },
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onSubmit) {
                Text(if (isEnglish) "Exchange code" else "Tukar kode")
            }
            Text(
                if (isEnglish) "Redirect: ${OAuthPkce.REDIRECT_URI}"
                else "Redirect: ${OAuthPkce.REDIRECT_URI}",
                style = MaterialTheme.typography.bodySmall
            )
        }
        Spacer(Modifier.width(0.dp))
    }
}
