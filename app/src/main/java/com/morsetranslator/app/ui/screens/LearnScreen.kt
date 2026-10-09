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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.morsetranslator.app.R
import com.morsetranslator.app.data.SettingsRepository
import com.morsetranslator.app.morse.MorseCode
import com.morsetranslator.app.morse.MorsePlayer
import com.morsetranslator.app.morse.Output
import com.morsetranslator.app.morse.PlaybackSettings
import com.morsetranslator.app.ui.rememberPlaybackController
import com.morsetranslator.app.ui.theme.BottomSpacer
import com.morsetranslator.app.ui.theme.CalmCard
import com.morsetranslator.app.ui.theme.CalmIconButton
import com.morsetranslator.app.ui.theme.CalmSegmentedCard
import com.morsetranslator.app.ui.theme.MorseText
import com.morsetranslator.app.ui.theme.calmTextFieldColors
import kotlinx.coroutines.launch

/**
 * Searchable reference chart. Always shows the *selected alphabet profile's*
 * table — the same table the encoder/decoder uses, never a different one.
 * Tap a row (or the speaker) to hear the character.
 */
@Composable
fun LearnScreen(repository: SettingsRepository, player: MorsePlayer) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    val playback = rememberPlaybackController(player)

    val profileId by repository.profileId.collectAsState(initial = MorseCode.INTERNATIONAL.id)
    val profile = remember(profileId) { MorseCode.profileById(profileId) }
    val wpm by repository.wpm.collectAsState(initial = SettingsRepository.DEFAULT_WPM)
    val toneHz by repository.toneHz.collectAsState(initial = SettingsRepository.DEFAULT_TONE_HZ)

    DisposableEffect(Unit) {
        onDispose { playback.stop() }
    }

    val entries = remember(query, profile) {
        val all = profile.charToMorse.entries.sortedBy { it.key.toString() }
        if (query.isBlank()) all
        else {
            val q = query.trim()
            val qLower = q.lowercase()
            all.filter { (char, morse) ->
                char.toString().lowercase().contains(qLower) ||
                    morse.contains(q) ||
                    MorseCode.describe(char)?.lowercase()?.contains(qLower) == true
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

        Text(
            stringResource(R.string.learn_explanation),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp)
        )

        // Profile switcher — same control as Settings, kept in sync.
        CalmSegmentedCard(
            options = MorseCode.PROFILES.map { stringResource(it.nameRes) },
            selected = MorseCode.PROFILES.indexOfFirst { it.id == profileId }.coerceAtLeast(0),
            onSelect = { index ->
                scope.launch {
                    repository.setProfileId(MorseCode.PROFILES[index].id)
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        if (profileId == com.morsetranslator.app.morse.PersianMorse.PROFILE.id) {
            Text(
                stringResource(R.string.profile_persian_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.learn_search_hint)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = calmTextFieldColors()
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(entries, key = { it.key }) { (char, morse) ->
                CalmCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        playback.play(
                            morse = morse,
                            settings = PlaybackSettings(wpm, toneHz),
                            outputs = setOf(Output.SOUND),
                            profile = profile
                        )
                    }
                ) {
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
                            MorseText(
                                morse,
                                style = MaterialTheme.typography.titleMedium.copy(
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
                        CalmIconButton(
                            icon = Icons.Filled.VolumeUp,
                            description = stringResource(R.string.learn_play, char.toString()),
                            onClick = {
                                playback.play(
                                    morse = morse,
                                    settings = PlaybackSettings(wpm, toneHz),
                                    outputs = setOf(Output.SOUND),
                                    profile = profile
                                )
                            }
                        )
                    }
                }
            }
            item { BottomSpacer() }
        }
    }
}
