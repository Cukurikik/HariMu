package com.harimu.app.feature.studios

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.harimu.app.AppContainer
import com.harimu.app.core.design.EmptyStateCard
import com.harimu.app.core.design.EngineBadge
import com.harimu.app.core.design.HariMuColors
import com.harimu.app.core.design.MonoTextStyle
import com.harimu.app.core.engines.KeyVaultEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeyVaultScreen(
    container: AppContainer,
    isEnglish: Boolean,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val keys by container.workspace.vaultKeysFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    var alias by remember { mutableStateOf("") }
    var algorithm by remember { mutableStateOf("AES-256 (AndroidKeyStore)") }

    val algorithms = listOf("AES-256 (AndroidKeyStore)", "ECDSA P-256 (PEM)", "RSA-2048 (PEM)")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Key Vault") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(if (isEnglish) "Generate New Key" else "Buat Kunci Kriptografi Baru", style = MaterialTheme.typography.titleMedium)
                        OutlinedTextField(
                            value = alias,
                            onValueChange = { alias = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text(if (isEnglish) "Key Alias" else "Nama Alias Kunci") }
                        )
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            algorithms.forEach { alg ->
                                FilterChip(
                                    selected = algorithm == alg,
                                    onClick = { algorithm = alg },
                                    label = { Text(alg) }
                                )
                            }
                        }
                        Button(
                            onClick = {
                                scope.launch(Dispatchers.IO) {
                                    val entity = KeyVaultEngine.generateKey(alias, algorithm)
                                    container.workspace.saveVaultKey(entity)
                                    withContext(Dispatchers.Main) {
                                        alias = ""
                                        Toast.makeText(context, "Kunci ${entity.alias} dibuat", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("generate_key_button")
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(if (isEnglish) "Generate Key" else "Buat Kunci")
                        }
                    }
                }
            }

            if (keys.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = Icons.Filled.Key,
                        title = if (isEnglish) "Vault Empty" else "Brankas Masih Kosong",
                        subtitle = if (isEnglish) "Generate an AES-256 hardware-backed or RSA/ECDSA PEM key above." else "Buat kunci AES-256 AndroidKeyStore atau pasangan kunci RSA/ECDSA PEM."
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
                                text = "SHA-256: ${item.fingerprintSha256.take(35)}...",
                                style = MonoTextStyle,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        clipboard.setText(AnnotatedString(item.publicKeyPem))
                                        Toast.makeText(context, "Public Key disalin", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Filled.ContentCopy, contentDescription = null)
                                    Spacer(Modifier.width(6.dp))
                                    Text(if (isEnglish) "Copy Public" else "Salin Publik")
                                }
                                IconButton(
                                    onClick = {
                                        scope.launch(Dispatchers.IO) { container.workspace.deleteVaultKey(item.id) }
                                    }
                                ) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
