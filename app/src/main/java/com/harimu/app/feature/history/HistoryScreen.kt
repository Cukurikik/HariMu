package com.harimu.app.feature.history

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.harimu.app.AppContainer
import com.harimu.app.R
import com.harimu.app.core.data.HistoryEntity
import com.harimu.app.core.design.EmptyState
import com.harimu.app.core.design.MonoTextStyle
import com.harimu.app.core.design.ToolIdBadge
import com.harimu.app.core.design.accentColor
import com.harimu.app.core.toolapi.ToolGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(
    container: AppContainer
) {
    val context = LocalContext.current
    val currentLocale = LocalLocale.current.platformLocale
    val scope = rememberCoroutineScope()
    val items by container.workspace.historyFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    var pendingSave by remember { mutableStateOf<HistoryEntity?>(null) }

    val savedToast = stringResource(R.string.toast_saved)
    val failedToast = stringResource(R.string.toast_save_failed)

    val safLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { destUri ->
        val item = pendingSave
        if (destUri != null && item != null) {
            val file = File(item.outputPath)
            if (file.exists()) {
                val ok = container.storage.copyFileToUri(file, destUri)
                Toast.makeText(context, if (ok) savedToast else failedToast, Toast.LENGTH_SHORT).show()
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 760.dp),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
        if (items.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.history_count_title, items.size),
                        style = MaterialTheme.typography.titleMedium
                    )
                    TextButton(onClick = { scope.launch(Dispatchers.IO) { container.workspace.clearHistory() } }) {
                        Icon(Icons.Filled.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.action_clear))
                    }
                }
            }
        }

        if (items.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Filled.History,
                    title = stringResource(R.string.history_empty_title),
                    subtitle = stringResource(R.string.history_empty_desc)
                )
            }
        } else {
            items(items, key = { it.id }) { item ->
                val group = ToolGroup.entries.firstOrNull { it.name == item.groupName } ?: ToolGroup.CORE
                val file = File(item.outputPath)
                val fileExists = item.outputPath.isNotEmpty() && file.exists()
                val timeStr = SimpleDateFormat("dd MMM HH:mm", currentLocale).format(Date(item.createdAt))

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
                                ToolIdBadge(id = item.toolId, accent = group.accentColor())
                                Text(item.toolTitle, style = MaterialTheme.typography.titleSmall)
                            }
                            IconButton(onClick = { scope.launch(Dispatchers.IO) { container.workspace.deleteHistory(item.id) } }) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = stringResource(R.string.action_delete),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Text(item.summary, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = "$timeStr • ${container.storage.formatBytes(item.fileSizeBytes)}",
                            style = MonoTextStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (fileExists) {
                            Spacer(Modifier.height(2.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                OutlinedButton(
                                    onClick = {
                                        runCatching {
                                            context.startActivity(
                                                Intent.createChooser(container.storage.buildOpenIntent(file, item.mimeType), item.outputName)
                                            )
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(stringResource(R.string.action_open))
                                }
                                OutlinedButton(
                                    onClick = {
                                        context.startActivity(
                                            Intent.createChooser(container.storage.buildShareIntent(file, item.mimeType), item.outputName)
                                        )
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(stringResource(R.string.action_share))
                                }
                                Button(
                                    onClick = {
                                        pendingSave = item
                                        safLauncher.launch(item.outputName)
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(stringResource(R.string.action_save))
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
