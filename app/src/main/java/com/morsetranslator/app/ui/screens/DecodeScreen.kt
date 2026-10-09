package com.morsetranslator.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.morsetranslator.app.R
import com.morsetranslator.app.audio.AudioDecoder
import com.morsetranslator.app.audio.MicRecorder
import com.morsetranslator.app.data.SettingsRepository
import com.morsetranslator.app.morse.MorseCode
import com.morsetranslator.app.ui.theme.BottomSpacer
import com.morsetranslator.app.ui.theme.CalmCard
import com.morsetranslator.app.ui.theme.CalmChip
import com.morsetranslator.app.ui.theme.MorseText
import com.morsetranslator.app.ui.theme.PrimaryButton
import com.morsetranslator.app.ui.theme.SectionTitle
import kotlinx.coroutines.launch

/**
 * Offline live audio-to-Morse decoding.
 *
 * The microphone is opened only after the user presses Start; audio is
 * processed on-device in small chunks and never uploaded, stored or logged.
 * Recognition is energy-based and honest about uncertainty: the UI marks
 * live results as provisional and never claims guaranteed accuracy.
 */
@Composable
fun DecodeScreen(repository: SettingsRepository) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val recorder = remember { MicRecorder(context) }

    var recording by remember { mutableStateOf(false) }
    var segments by remember { mutableStateOf<List<AudioDecoder.ToneSegment>>(emptyList()) }
    var classification by remember { mutableStateOf(AudioDecoder.Classification("", 0L, 0)) }
    var showRationale by remember { mutableStateOf(false) }
    var micUnavailable by remember { mutableStateOf(false) }

    val threshold by repository.decoderThreshold.collectAsState(
        initial = SettingsRepository.DEFAULT_DECODER_THRESHOLD
    )
    var thresholdSlider by remember(threshold) { mutableStateOf((threshold * 100).toFloat()) }
    val profileId by repository.profileId.collectAsState(initial = MorseCode.INTERNATIONAL.id)
    val profile = remember(profileId) { MorseCode.profileById(profileId) }

    // Microphone is released on screen exit, backgrounding, rotation and
    // permission failure: an in-progress session stops rather than leaking
    // the microphone or recording without a visible UI.
    DisposableEffect(Unit) {
        onDispose {
            recorder.release()
            recording = false
        }
    }

    val decoded = remember(classification.morse, profile) {
        if (classification.morse.isBlank()) "" else
            MorseCode.decodeDetailed(classification.morse, profile).text
    }

    fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Toast.makeText(
                context,
                context.getString(R.string.mic_permission_needed),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    fun startRecording() {
        micUnavailable = false
        segments = emptyList()
        classification = AudioDecoder.Classification("", 0L, 0)
        val config = AudioDecoder.DecodeConfig(
            onThreshold = threshold.toFloat(),
            offThreshold = (threshold * 0.5).toFloat().coerceAtLeast(0.02f)
        )
        val ok = recorder.start(scope, config) { segs, cls ->
            segments = segs
            classification = cls
        }
        if (!ok) {
            micUnavailable = true
            recording = false
        } else {
            recording = true
        }
    }

    fun stopRecording() {
        recorder.stop()
        recording = false
    }

    fun onMicButton() {
        if (recording) {
            stopRecording()
            return
        }
        when {
            hasMicPermission() -> startRecording()
            else -> showRationale = true
        }
    }

    if (showRationale) {
        AlertDialog(
            onDismissRequest = { showRationale = false },
            title = { Text(stringResource(R.string.mic_rationale_title)) },
            text = { Text(stringResource(R.string.mic_rationale_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRationale = false
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                ) { Text(stringResource(R.string.action_continue)) }
            },
            dismissButton = {
                TextButton(onClick = { showRationale = false }) {
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

        Text(
            stringResource(R.string.decode_explanation),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp)
        )

        // Status + start/stop
        CalmCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    if (recording) Icons.Filled.FiberManualRecord else Icons.Filled.MicOff,
                    contentDescription = null,
                    tint = if (recording) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(
                            if (recording) R.string.decode_listening
                            else R.string.decode_idle
                        ),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text(
                        stringResource(R.string.decode_privacy_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            PrimaryButton(
                text = stringResource(
                    if (recording) R.string.decode_stop else R.string.decode_start
                ),
                icon = if (recording) Icons.Filled.MicOff else Icons.Filled.Mic,
                onClick = { onMicButton() },
                modifier = Modifier.fillMaxWidth()
            )
            if (micUnavailable) {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.decode_mic_unavailable),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        // Live results
        SectionTitle(stringResource(R.string.decode_results))
        CalmCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                stringResource(R.string.decode_morse_label),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(Modifier.height(4.dp))
            SelectionContainer {
                MorseText(
                    classification.morse.ifBlank { "—" },
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.decode_text_label),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(Modifier.height(4.dp))
            SelectionContainer {
                Text(
                    decoded.ifBlank { "—" },
                    style = MaterialTheme.typography.headlineSmall
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (classification.estimatedWpm > 0)
                        stringResource(R.string.decode_wpm, classification.estimatedWpm)
                    else stringResource(R.string.decode_wpm_unknown),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    stringResource(R.string.decode_segments, segments.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (recording) {
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.decode_uncertain_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                CalmChip(
                    text = stringResource(R.string.decode_clear),
                    onClick = {
                        segments = emptyList()
                        classification = AudioDecoder.Classification("", 0L, 0)
                    }
                )
            }
        }

        // Sensitivity
        SectionTitle(stringResource(R.string.decode_sensitivity))
        CalmCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                stringResource(R.string.decode_sensitivity_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Slider(
                value = thresholdSlider,
                onValueChange = { thresholdSlider = it },
                onValueChangeFinished = {
                    scope.launch {
                        repository.setDecoderThreshold(thresholdSlider / 100.0)
                    }
                },
                valueRange = 3f..30f,
                steps = 26,
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary
                )
            )
            Text(
                stringResource(R.string.decode_threshold_value, thresholdSlider.toInt()),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        BottomSpacer()
    }
}
