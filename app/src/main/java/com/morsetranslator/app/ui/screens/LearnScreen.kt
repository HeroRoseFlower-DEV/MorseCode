package com.morsetranslator.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.unit.dp
import com.morsetranslator.app.R
import com.morsetranslator.app.data.SettingsRepository
import com.morsetranslator.app.morse.MorseCode
import com.morsetranslator.app.morse.MorsePlayer
import com.morsetranslator.app.morse.PlaybackSettings
import com.morsetranslator.app.ui.theme.GlassBottomSpacer
import com.morsetranslator.app.ui.theme.GlassCard
import com.morsetranslator.app.ui.theme.GlassIconButton
import com.morsetranslator.app.ui.theme.glassBorder
import com.morsetranslator.app.ui.theme.glassContainer

/** Searchable morse reference chart with per-character audio preview. */
@Composable
fun LearnScreen(repository: SettingsRepository, player: MorsePlayer) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }

    val wpm by repository.wpm.collectAsState(initial = 18)
    val toneHz by repository.toneHz.collectAsState(initial = 700)

    DisposableEffect(Unit) {
        onDispose { player.stop() }
    }

    val entries = remember(query) {
        val all = MorseCode.CHAR_TO_MORSE.entries.sortedBy { it.key.toString() }
        if (query.isBlank()) all
        else {
            val q = query.trim().lowercase()
            all.filter { (char, morse) ->
                char.toString().lowercase().contains(q) ||
                    morse.contains(query.trim()) ||
                    MorseCode.describe(char)?.lowercase()?.contains(q) == true
            }
        }
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
            placeholder = { Text(stringResource(R.string.learn_search_hint)) },
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

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(entries, key = { it.key }) { (char, morse) ->
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            char.toString(),
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.ExtraBold
                            ),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                morse,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            MorseCode.describe(char)?.let { desc ->
                                Text(
                                    desc,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        GlassIconButton(
                            icon = Icons.Filled.VolumeUp,
                            description = stringResource(R.string.learn_play, char.toString()),
                            onClick = {
                                player.playSound(
                                    scope,
                                    morse,
                                    PlaybackSettings(wpm, toneHz)
                                ) {}
                            },
                            size = 48.dp
                        )
                    }
                }
            }
            item { GlassBottomSpacer() }
        }
    }
}
