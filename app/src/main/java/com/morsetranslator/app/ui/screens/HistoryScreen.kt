package com.morsetranslator.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.morsetranslator.app.R
import com.morsetranslator.app.data.SettingsRepository
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@Composable
fun HistoryScreen(
    repository: SettingsRepository,
    onSelect: (input: String, textToMorse: Boolean) -> Unit
) {
    val scope = rememberCoroutineScope()
    val history by repository.history.collectAsState(initial = emptyList())
    var showConfirm by remember { mutableStateOf(false) }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text(stringResource(R.string.confirm_clear_title)) },
            text = { Text(stringResource(R.string.confirm_clear_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch { repository.clearHistory() }
                        showConfirm = false
                    }
                ) {
                    Text(stringResource(R.string.action_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        if (history.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = { showConfirm = true }) {
                    Icon(Icons.Filled.DeleteSweep, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.history_clear_all))
                }
            }
        }
        if (history.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Filled.History,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(R.string.history_empty),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    stringResource(R.string.history_empty_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(history, key = { it.id }) { item ->
                    ElevatedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(item.input, item.textToMorse) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AssistChip(
                                        onClick = { },
                                        label = {
                                            Text(
                                                stringResource(
                                                    if (item.textToMorse) R.string.dir_text_to_morse
                                                    else R.string.dir_morse_to_text
                                                )
                                            )
                                        }
                                    )
                                    Text(
                                        DateFormat
                                            .getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                                            .format(Date(item.timestamp)),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    item.input,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontFamily = if (!item.textToMorse) FontFamily.Monospace
                                    else FontFamily.Default
                                )
                                Text(
                                    item.output,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontFamily = if (item.textToMorse) FontFamily.Monospace
                                    else FontFamily.Default
                                )
                            }
                            IconButton(
                                onClick = { scope.launch { repository.removeHistory(item.id) } }
                            ) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = stringResource(R.string.history_delete)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
