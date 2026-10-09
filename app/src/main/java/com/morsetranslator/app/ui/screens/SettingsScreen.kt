package com.morsetranslator.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.morsetranslator.app.R
import com.morsetranslator.app.data.SettingsRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(repository: SettingsRepository) {
    val scope = rememberCoroutineScope()
    val themeMode by repository.themeMode.collectAsState(initial = 0)
    val wpm by repository.wpm.collectAsState(initial = 18)
    val toneHz by repository.toneHz.collectAsState(initial = 700)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Appearance
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    stringResource(R.string.settings_appearance),
                    style = MaterialTheme.typography.titleMedium
                )
                val options: List<Triple<Int, Int, ImageVector>> = listOf(
                    Triple(0, R.string.theme_system, Icons.Filled.SettingsBrightness),
                    Triple(1, R.string.theme_light, Icons.Filled.LightMode),
                    Triple(2, R.string.theme_dark, Icons.Filled.DarkMode)
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    options.forEachIndexed { index, (value, labelRes, icon) ->
                        SegmentedButton(
                            selected = themeMode == value,
                            onClick = { scope.launch { repository.setThemeMode(value) } },
                            shape = SegmentedButtonDefaults.itemShape(index, options.size),
                            icon = { Icon(icon, contentDescription = null) },
                            label = { Text(stringResource(labelRes)) }
                        )
                    }
                }
            }
        }

        // Playback
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    stringResource(R.string.settings_playback),
                    style = MaterialTheme.typography.titleMedium
                )
                SettingSlider(
                    label = stringResource(R.string.wpm, wpm),
                    value = wpm.toFloat(),
                    range = 5f..40f,
                    steps = 34,
                    onChange = { scope.launch { repository.setWpm(it.toInt()) } }
                )
                SettingSlider(
                    label = stringResource(R.string.tone, toneHz),
                    value = toneHz.toFloat(),
                    range = 300f..1200f,
                    steps = 17,
                    onChange = { scope.launch { repository.setToneHz(it.toInt()) } }
                )
            }
        }

        // About
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    Icons.Filled.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp)
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        stringResource(R.string.version, "1.1"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        stringResource(R.string.about_text),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onChange: (Float) -> Unit
) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            steps = steps,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
