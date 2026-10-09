package com.morsetranslator.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.morsetranslator.app.BuildConfig
import com.morsetranslator.app.R
import com.morsetranslator.app.data.SettingsRepository
import com.morsetranslator.app.morse.MorseCode
import com.morsetranslator.app.morse.PersianMorse
import com.morsetranslator.app.ui.theme.BottomSpacer
import com.morsetranslator.app.ui.theme.CalmCard
import com.morsetranslator.app.ui.theme.CalmChip
import com.morsetranslator.app.ui.theme.CalmSegmentedCard
import com.morsetranslator.app.ui.theme.SectionTitle
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(repository: SettingsRepository) {
    val scope = rememberCoroutineScope()
    val wpm by repository.wpm.collectAsState(initial = SettingsRepository.DEFAULT_WPM)
    val toneHz by repository.toneHz.collectAsState(initial = SettingsRepository.DEFAULT_TONE_HZ)
    val themeMode by repository.themeMode.collectAsState(initial = 0)
    val profileId by repository.profileId.collectAsState(initial = MorseCode.INTERNATIONAL.id)

    // Responsive local slider values; persisted once on release.
    var wpmSlider by remember(wpm) { mutableStateOf(wpm.toFloat()) }
    var toneSlider by remember(toneHz) { mutableStateOf(toneHz.toFloat()) }

    var showClearHistory by remember { mutableStateOf(false) }
    var showResetReview by remember { mutableStateOf(false) }

    if (showClearHistory) {
        AlertDialog(
            onDismissRequest = { showClearHistory = false },
            title = { Text(stringResource(R.string.confirm_clear_title)) },
            text = { Text(stringResource(R.string.confirm_clear_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch { repository.clearHistory() }
                        showClearHistory = false
                    }
                ) { Text(stringResource(R.string.action_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistory = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
    if (showResetReview) {
        AlertDialog(
            onDismissRequest = { showResetReview = false },
            title = { Text(stringResource(R.string.practice_reset_title)) },
            text = { Text(stringResource(R.string.practice_reset_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch { repository.clearReviewStats() }
                        showResetReview = false
                    }
                ) { Text(stringResource(R.string.action_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showResetReview = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Spacer(Modifier.height(4.dp))

        SectionTitle(stringResource(R.string.settings_playback))
        CalmCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                stringResource(R.string.wpm, wpmSlider.toInt()),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Slider(
                value = wpmSlider,
                onValueChange = { wpmSlider = it },
                onValueChangeFinished = {
                    scope.launch { repository.setWpm(wpmSlider.toInt()) }
                },
                valueRange = 5f..40f,
                steps = 34,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary
                )
            )
            Text(
                stringResource(R.string.playback_speed_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.tone, toneSlider.toInt()),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Slider(
                value = toneSlider,
                onValueChange = { toneSlider = it },
                onValueChangeFinished = {
                    scope.launch { repository.setToneHz(toneSlider.toInt()) }
                },
                valueRange = 300f..1200f,
                steps = 17,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary
                )
            )
            Text(
                stringResource(R.string.tone_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        SectionTitle(stringResource(R.string.settings_alphabet))
        CalmCard(modifier = Modifier.fillMaxWidth()) {
            CalmSegmentedCard(
                options = MorseCode.PROFILES.map { stringResource(it.nameRes) },
                selected = MorseCode.PROFILES.indexOfFirst { it.id == profileId }
                    .coerceAtLeast(0),
                onSelect = { index ->
                    scope.launch { repository.setProfileId(MorseCode.PROFILES[index].id) }
                },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Text(
                if (profileId == PersianMorse.PROFILE.id)
                    stringResource(R.string.profile_persian_note)
                else
                    stringResource(R.string.profile_international_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        SectionTitle(stringResource(R.string.settings_appearance))
        CalmCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                stringResource(R.string.theme),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(Modifier.height(8.dp))
            CalmSegmentedCard(
                options = listOf(
                    stringResource(R.string.theme_system),
                    stringResource(R.string.theme_light),
                    stringResource(R.string.theme_dark)
                ),
                selected = themeMode,
                onSelect = { scope.launch { repository.setThemeMode(it) } },
                modifier = Modifier.fillMaxWidth()
            )
        }

        SectionTitle(stringResource(R.string.settings_data))
        CalmCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                stringResource(R.string.settings_backup_title),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.settings_backup_text),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CalmChip(
                    text = stringResource(R.string.history_clear_all),
                    onClick = { showClearHistory = true }
                )
                CalmChip(
                    text = stringResource(R.string.practice_reset_title),
                    onClick = { showResetReview = true }
                )
            }
        }

        SectionTitle(stringResource(R.string.settings_about))
        CalmCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    // Sourced from the Gradle build configuration, not hard-coded.
                    stringResource(R.string.version, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.about_text),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        BottomSpacer()
    }
}
