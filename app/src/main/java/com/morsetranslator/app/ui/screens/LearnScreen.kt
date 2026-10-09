package com.morsetranslator.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.morsetranslator.app.R
import com.morsetranslator.app.data.SettingsRepository
import com.morsetranslator.app.morse.MorseCode
import com.morsetranslator.app.morse.MorsePlayer
import com.morsetranslator.app.morse.PlaybackSettings

/** Searchable morse code reference chart with per-character audio preview. */
@Composable
fun LearnScreen(repository: SettingsRepository, player: MorsePlayer) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    var query by remember { mutableStateOf("") }
    var playingChar by remember { mutableStateOf<Char?>(null) }

    val wpm by repository.wpm.collectAsState(initial = 18)
    val toneHz by repository.toneHz.collectAsState(initial = 700)

    DisposableEffect(Unit) {
        onDispose { player.stop() }
    }

    val groups = remember(query) {
        val q = query.trim().lowercase()
        val entries = MorseCode.CHAR_TO_MORSE.entries.filter { (char, morse) ->
            q.isEmpty() ||
                char.toString().lowercase().contains(q) ||
                morse.contains(q)
        }
        listOf(
            R.string.group_letters to entries.filter { it.key.isLetter() },
            R.string.group_digits to entries.filter { it.key.isDigit() },
            R.string.group_punctuation to entries.filter { !it.key.isLetterOrDigit() }
        ).filter { it.second.isNotEmpty() }
    }

    fun playChar(char: Char, morse: String) {
        if (playingChar == char) {
            player.stop()
            playingChar = null
            return
        }
        player.stop()
        playingChar = char
        player.playSound(scope, morse, PlaybackSettings(wpm, toneHz)) {
            playingChar = null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.learn_search_hint)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            groups.forEach { (titleRes, entries) ->
                item(key = "header_$titleRes") {
                    Text(
                        stringResource(titleRes),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                items(entries, key = { it.key }) { (char, morse) ->
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                char.toString(),
                                style = MaterialTheme.typography.headlineSmall,
                                modifier = Modifier.width(48.dp)
                            )
                            Text(
                                morse,
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { playChar(char, morse) }) {
                                Icon(
                                    if (playingChar == char) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                                    contentDescription = stringResource(R.string.play_sound)
                                )
                            }
                            IconButton(
                                onClick = {
                                    clipboard.setText(AnnotatedString(morse))
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.copied),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            ) {
                                Icon(
                                    Icons.Filled.ContentCopy,
                                    contentDescription = stringResource(R.string.action_copy)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
