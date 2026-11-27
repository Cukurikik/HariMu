package com.harimu.app.feature.studios

import android.app.KeyguardManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.ManagedActivityResultLauncher
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
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.VerifiedUser
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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.harimu.app.AppContainer
import com.harimu.app.R
import com.harimu.app.core.data.VaultKeyEntity
import com.harimu.app.core.design.EmptyStateCard
import com.harimu.app.core.design.EngineBadge
import com.harimu.app.core.design.HariMuColors
import com.harimu.app.core.design.HariMuThemeTokens
import com.harimu.app.core.design.MonoTextStyle
import com.harimu.app.core.security.VaultCryptoEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private enum class PendingVaultAction { EXPORT_PEM, ENCRYPT_FILE, DECRYPT_FILE, SIGN_FILE, VERIFY_FILE }

/**
 * Key Vault (CORE-07).
 *
 * - AES 128/256 tinggal di AndroidKeyStore (non-ekspor), dipakai untuk enkripsi/dekripsi berkas.
 * - RSA 2048/3072/4096 dan ECDSA P-256/384/521 dibuat di perangkat; private key disegel kunci
 *   Keystore sebelum masuk Room. Ekspor PEM **digerbangi konfirmasi kredensial perangkat**
 *   (biometrik/PIN lewat KeyguardManager) + peringatan.
 * - Tanda tangan/verifikasi: `SHA256withRSA` / `SHA256withECDSA`.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun KeyVaultScreen(
    container: AppContainer,
    isEnglish: Boolean,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = remember(context) {
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }
    val scope = rememberCoroutineScope()
    val keys by container.workspace.vaultKeysFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    var alias by remember { mutableStateOf("") }
    var algorithm by remember { mutableStateOf("AES-256") }
    var keepInKeystore by remember { mutableStateOf(true) }
    var status by remember { mutableStateOf("") }
    var pendingKey by remember { mutableStateOf<VaultKeyEntity?>(null) }
    var pendingAction by remember { mutableStateOf(PendingVaultAction.EXPORT_PEM) }
    var pendingInput by remember { mutableStateOf<Pair<VaultKeyEntity, File>?>(null) }
    var pendingOriginal by remember { mutableStateOf<ByteArray?>(null) }
    var pendingVerifyName by remember { mutableStateOf("") }

    val algorithms = listOf(
        "AES-128", "AES-256",
        "RSA-2048", "RSA-3072", "RSA-4096",
        "ECDSA-P-256", "ECDSA-P-384", "ECDSA-P-521"
    )

    fun keySize(label: String): Int = when {
        label.contains("128") -> 128
        label.contains("2048") -> 2048
        label.contains("3072") -> 3072
        label.contains("4096") -> 4096
        label.contains("384") -> 384
        label.contains("521") -> 521
        else -> 256
    }

    /** Baca berkas dari SAF ke cache agar bisa diproses engine. */
    fun copyUriToCache(uri: Uri, name: String): File? = runCatching {
        val dir = File(context.cacheDir, "vault_io").apply { mkdirs() }
        val target = File(dir, name.ifBlank { "vault_${System.currentTimeMillis()}.bin" })
        context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        } ?: error("Tidak bisa membaca berkas")
        target
    }.getOrNull()

    val keyguard = remember { context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager }
    val credentialLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val entity = pendingKey
        if (result.resultCode == android.app.Activity.RESULT_OK && entity != null) {
            runCatching { VaultCryptoEngine.exportPrivatePem(entity) }
                .onSuccess { pem ->
                    clipboard.setPrimaryClip(ClipData.newPlainText("private_pem", pem))
                    status = if (isEnglish) {
                        "Private PEM copied. Store it safely — anyone with it controls this key."
                    } else {
                        "Private PEM disalin. Simpan aman — siapa pun yang memilikinya menguasai kunci ini."
                    }
                }
                .onFailure { status = it.localizedMessage ?: "Ekspor gagal" }
        } else if (result.resultCode != android.app.Activity.RESULT_OK) {
            status = if (isEnglish) "Export cancelled: authentication required" else "Ekspor dibatalkan: butuh autentikasi"
        }
        pendingKey = null
    }

    fun requireAuthentication(action: PendingVaultAction, entity: VaultKeyEntity) {
        pendingKey = entity
        pendingAction = action
        if (action == PendingVaultAction.EXPORT_PEM) {
            if (!keyguard.isDeviceSecure) {
                status = if (isEnglish) {
                    "Set a screen lock (biometric/PIN) before exporting private keys."
                } else {
                    "Atur kunci layar (biometrik/PIN) sebelum mengekspor private key."
                }
                pendingKey = null
                return
            }
            val intent = keyguard.createConfirmDeviceCredentialIntent(
                if (isEnglish) "Authenticate to export private key" else "Autentikasi untuk mengekspor private key",
                if (isEnglish) "Your private key will be copied to the clipboard." else "Private key akan disalin ke papan klip."
            )
            if (intent != null) {
                credentialLauncher.launch(intent)
            } else {
                status = if (isEnglish) "Device credential unavailable" else "Kredensial perangkat tidak tersedia"
                pendingKey = null
            }
        }
    }

    val saveDocument = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        val target = pendingInput
        if (uri == null || target == null) return@rememberLauncherForActivityResult
        val (entity, source) = target
        val action = pendingAction
        scope.launch(Dispatchers.IO) {
            val result = runCatching {
                val dir = File(context.cacheDir, "vault_out").apply { mkdirs() }
                val output = if (action == PendingVaultAction.ENCRYPT_FILE) {
                    File(dir, "${source.name}.hmvlt").also { VaultCryptoEngine.encryptFile(entity, source, it) }
                } else {
                    File(dir, source.name.removeSuffix(".hmvlt")).also { VaultCryptoEngine.decryptFile(entity, source, it) }
                }
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    output.inputStream().use { it.copyTo(stream) }
                } ?: error("Tujuan tidak bisa ditulis")
                output
            }
            withContext(Dispatchers.Main) {
                status = result.fold(
                    onSuccess = {
                        if (isEnglish) "File operation finished: ${it.name}" else "Operasi berkas selesai: ${it.name}"
                    },
                    onFailure = { it.localizedMessage ?: "Operasi berkas gagal" }
                )
            }
            pendingInput = null
        }
    }

    val openDocumentHolder = remember { arrayOf<ManagedActivityResultLauncher<Array<String>, Uri?>?>(null) }
    val openDocument = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val entity = pendingKey ?: return@rememberLauncherForActivityResult
        val action = pendingAction
        val name = uri.lastPathSegment?.substringAfterLast('/') ?: "input.bin"
        scope.launch {
            val local = withContext(Dispatchers.IO) { copyUriToCache(uri, name) }
            if (local == null) {
                status = if (isEnglish) "File could not be read" else "Berkas tidak bisa dibaca"
                return@launch
            }
            when (action) {
                PendingVaultAction.ENCRYPT_FILE -> {
                    pendingInput = entity to local
                    saveDocument.launch("${local.name}.hmvlt")
                }
                PendingVaultAction.DECRYPT_FILE -> {
                    pendingInput = entity to local
                    saveDocument.launch(local.name.removeSuffix(".hmvlt"))
                }
                PendingVaultAction.SIGN_FILE -> {
                    val result = withContext(Dispatchers.IO) {
                        runCatching {
                            val signature = VaultCryptoEngine.signBytes(entity, local.readBytes())
                            val target = container.storage.createOutputFile("SIG", "sig")
                            target.writeText(
                                "HARIMU-SIG v1\nsha256=${entity.fingerprintSha256}\nfile=${local.name}\n" +
                                    Base64.encodeToString(signature, Base64.NO_WRAP),
                                Charsets.UTF_8
                            )
                            target
                        }
                    }
                    result.onSuccess { target ->
                        status = if (isEnglish) {
                            "Signature saved: ${target.name}. Verify by picking the original file, then this .sig"
                        } else {
                            "Tanda tangan disimpan: ${target.name}. Verifikasi dengan memilih berkas asli, lalu .sig ini"
                        }
                    }.onFailure { error ->
                        status = error.localizedMessage ?: "Gagal menandatangani"
                    }
                }
                PendingVaultAction.VERIFY_FILE -> {
                    val original = pendingOriginal
                    if (original == null) {
                        val readOrig = withContext(Dispatchers.IO) {
                            runCatching { local.readBytes() }.getOrNull()
                        }
                        if (readOrig == null) {
                            status = if (isEnglish) "File could not be read" else "Berkas tidak bisa dibaca"
                            return@launch
                        }
                        pendingOriginal = readOrig
                        pendingVerifyName = local.name
                        status = if (isEnglish) {
                            "Now pick the .sig file created when signing ${local.name}"
                        } else {
                            "Sekarang pilih berkas .sig yang dibuat saat menandatangani ${local.name}"
                        }
                        openDocumentHolder[0]?.launch(arrayOf("*/*"))
                    } else {
                        val ok = withContext(Dispatchers.IO) {
                            runCatching {
                                val text = local.readText(Charsets.UTF_8)
                                val encoded = text.lineSequence()
                                    .firstOrNull { it.isNotBlank() && !it.startsWith("HARIMU-SIG") && !it.contains("=") }
                                    ?: text.substringAfterLast('\n').trim()
                                val signature = Base64.decode(encoded, Base64.DEFAULT)
                                VaultCryptoEngine.verifyBytes(entity, original, signature)
                            }.getOrDefault(false)
                        }
                        status = if (ok) {
                            if (isEnglish) "Signature valid for $pendingVerifyName" else "Tanda tangan sah untuk $pendingVerifyName"
                        } else {
                            if (isEnglish) "Signature invalid or file changed" else "Tanda tangan tidak sah atau berkas berubah"
                        }
                        pendingOriginal = null
                        pendingVerifyName = ""
                    }
                }
                else -> Unit
            }
        }
    }
    openDocumentHolder[0] = openDocument

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Key Vault") },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .testTag("key_vault_back_button")
                            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = HariMuThemeTokens.layout.catalogMaxWidth),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            if (isEnglish) "Generate New Key" else "Buat Kunci Kriptografi Baru",
                            style = MaterialTheme.typography.titleMedium
                        )
                        OutlinedTextField(
                            value = alias,
                            onValueChange = { alias = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text(if (isEnglish) "Key Alias" else "Nama Alias Kunci") }
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            algorithms.forEach { alg ->
                                FilterChip(
                                    selected = algorithm == alg,
                                    onClick = {
                                        algorithm = alg
                                        if (!alg.startsWith("AES")) keepInKeystore = false
                                    },
                                    label = { Text(alg) },
                                    modifier = Modifier.heightIn(min = 48.dp)
                                )
                            }
                        }
                        if (algorithm.startsWith("AES")) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = keepInKeystore,
                                    onClick = { keepInKeystore = true },
                                    label = { Text(if (isEnglish) "Keystore (non-exportable)" else "Keystore (non-ekspor)") },
                                    modifier = Modifier.heightIn(min = 48.dp)
                                )
                            }
                        }
                        Text(
                            text = if (algorithm.startsWith("AES")) {
                                if (isEnglish) "AES keys never leave the AndroidKeyStore and are used for file encryption."
                                else "Kunci AES tidak pernah keluar dari AndroidKeyStore dan dipakai untuk enkripsi berkas."
                            } else {
                                if (isEnglish) "RSA/ECDSA private keys are sealed with a Keystore master key; PEM export requires device authentication."
                                else "Private key RSA/ECDSA disegel dengan kunci master Keystore; ekspor PEM butuh autentikasi perangkat."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = {
                                val requestedAlias = alias.ifBlank { "harimu_${System.currentTimeMillis() % 100000}" }
                                scope.launch(Dispatchers.IO) {
                                    val created = runCatching {
                                        VaultCryptoEngine.generateKey(
                                            alias = requestedAlias,
                                            algorithm = algorithm,
                                            keySizeBits = keySize(algorithm),
                                            keepInKeystore = keepInKeystore
                                        )
                                    }
                                    created.onSuccess { container.workspace.saveVaultKey(it) }
                                    withContext(Dispatchers.Main) {
                                        alias = ""
                                        status = created.fold(
                                            onSuccess = {
                                                if (isEnglish) "Key ${it.alias} created (${it.algorithm})"
                                                else "Kunci ${it.alias} dibuat (${it.algorithm})"
                                            },
                                            onFailure = { it.localizedMessage ?: "Gagal membuat kunci" }
                                        )
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("generate_key_button")
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(if (isEnglish) "Generate Key" else "Buat Kunci")
                        }
                    }
                }
            }

            if (status.isNotBlank()) {
                item {
                    Text(status, style = MonoTextStyle, color = MaterialTheme.colorScheme.secondary)
                }
            }

            if (keys.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = Icons.Filled.Key,
                        title = if (isEnglish) "Vault Empty" else "Brankas Masih Kosong",
                        subtitle = if (isEnglish) {
                            "Generate an AES Keystore key or an RSA/ECDSA pair above."
                        } else {
                            "Buat kunci AES AndroidKeyStore atau pasangan RSA/ECDSA di atas."
                        }
                    )
                }
            } else {
                items(keys, key = { it.id }) { item ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Filled.VerifiedUser, contentDescription = null, tint = HariMuColors.TealEmerald)
                                    Text(item.alias, style = MaterialTheme.typography.titleMedium)
                                }
                                EngineBadge(engine = item.algorithm)
                            }
                            Text(
                                text = "SHA-256: ${item.fingerprintSha256.take(35)}…",
                                style = MonoTextStyle,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        clipboard.setPrimaryClip(ClipData.newPlainText("public_pem", item.publicKeyPem))
                                        Toast.makeText(
                                            context,
                                            if (isEnglish) "Public key copied" else "Public key disalin",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 48.dp)
                                ) {
                                    Icon(Icons.Filled.ContentCopy, contentDescription = null)
                                    Spacer(Modifier.width(6.dp))
                                    Text(if (isEnglish) "Public" else "Publik")
                                }
                                OutlinedButton(
                                    onClick = {
                                        if (item.keystoreBacked) {
                                            status = if (isEnglish) {
                                                "AES Keystore keys are non-exportable by design."
                                            } else {
                                                "Kunci AES Keystore memang non-ekspor."
                                            }
                                        } else {
                                            requireAuthentication(PendingVaultAction.EXPORT_PEM, item)
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 48.dp)
                                ) {
                                    Text(if (isEnglish) "Export PEM" else "Ekspor PEM")
                                }
                                IconButton(
                                    onClick = { scope.launch(Dispatchers.IO) { container.workspace.deleteVaultKey(item.id) } },
                                    modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Delete,
                                        contentDescription = stringResource(R.string.key_vault_delete_cd),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                            if (item.keystoreBacked) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = {
                                            pendingKey = item
                                            pendingAction = PendingVaultAction.ENCRYPT_FILE
                                            openDocument.launch(arrayOf("*/*"))
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 48.dp)
                                    ) { Text(if (isEnglish) "Encrypt file" else "Enkripsi berkas") }
                                    OutlinedButton(
                                        onClick = {
                                            pendingKey = item
                                            pendingAction = PendingVaultAction.DECRYPT_FILE
                                            openDocument.launch(arrayOf("*/*"))
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 48.dp)
                                    ) { Text(if (isEnglish) "Decrypt file" else "Dekripsi berkas") }
                                }
                            } else {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = {
                                            pendingKey = item
                                            pendingAction = PendingVaultAction.SIGN_FILE
                                            openDocument.launch(arrayOf("*/*"))
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 48.dp)
                                    ) { Text(if (isEnglish) "Sign file" else "Tanda tangan") }
                                    OutlinedButton(
                                        onClick = {
                                            pendingKey = item
                                            pendingAction = PendingVaultAction.VERIFY_FILE
                                            pendingOriginal = null
                                            status = if (isEnglish) {
                                                "Pick the original file, then the .sig file"
                                            } else {
                                                "Pilih berkas asli, lalu berkas .sig"
                                            }
                                            openDocument.launch(arrayOf("*/*"))
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 48.dp)
                                    ) { Text(if (isEnglish) "Verify" else "Verifikasi") }
                                }
                            }
                        }
                    }
                }
            }
        }
        }
    }
}
