package com.morsetranslator.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.morsetranslator.app.R
import com.morsetranslator.app.data.SettingsRepository
import com.morsetranslator.app.ui.theme.GlassBottomSpacer
import com.morsetranslator.app.ui.theme.GlassCard
import com.morsetranslator.app.ui.theme.GlassChip
import com.morsetranslator.app.ui.theme.GlassIconButton
import com.morsetranslator.app.ui.theme.glassBorder
import com.morsetranslator.app.ui.theme.glassContainer
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
    var query by remember { mutableStateOf("") }

    val filtered = remember(history, query) {
        if (query.isBlank()) history
        else history.filter {
            it.input.contains(query, ignoreCase = true) ||
                it.output.contains(query, ignoreCase = true)
        }
    }

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
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Spacer(Modifier.height(4.dp))

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.history_search_hint)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(20.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = glassContainer(),
                unfocusedContainerColor = glassContainer(),
                disabledContainerColor = glassContainer(),
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = glassBorder()
            )
        )

        if (history.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                GlassChip(
                    text = stringResource(R.string.history_clear_all),
                    onClick = { showConfirm = true }
                )
            }
        }

        if (filtered.isEmpty()) {
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
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
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
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filtered, key = { it.id }) { item ->
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onSelect(item.input, item.textToMorse) }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
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
                                    GlassChip(
                                        text = stringResource(
                                            if (item.textToMorse) R.string.dir_text_to_morse
                                            else R.string.dir_morse_to_text
                                        ),
                                        onClick = { },
                                        selected = true
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
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Medium
                                    ),
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
                            GlassIconButton(
                                icon = if (item.isFavorite) Icons.Filled.Star else Icons.Outlined.Star,
                                description = stringResource(R.string.action_favorite),
                                onClick = { scope.launch { repository.toggleFavorite(item.id) } },
                                selected = item.isFavorite,
                                size = 44.dp
                            )
                            GlassIconButton(
                                icon = Icons.Filled.Delete,
                                description = stringResource(R.string.history_delete),
                                onClick = { scope.launch { repository.removeHistory(item.id) } },
                                size = 44.dp
                            )
                        }
                    }
                }
                item { GlassBottomSpacer() }
            }
        }
    }
}
