package com.morsetranslator.app.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.morsetranslator.app.R
import com.morsetranslator.app.data.SettingsRepository
import com.morsetranslator.app.morse.MorseCode
import com.morsetranslator.app.morse.MorsePlayer
import com.morsetranslator.app.morse.PlaybackSettings
import com.morsetranslator.app.ui.Prefill
import kotlinx.coroutines.launch

@Composable
fun TranslateScreen(
    repository: SettingsRepository,
    player: MorsePlayer,
    prefill: Prefill?,
    onPrefillConsumed: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    var textToMorse by rememberSaveable { mutableStateOf(true) }
    var input by rememberSaveable { mutableStateOf("") }
    var isPlaying by remember { mutableStateOf(false) }

    val wpm by repository.wpm.collectAsState(initial = 18)
    val toneHz by repository.toneHz.collectAsState(initial = 700)

    LaunchedEffect(prefill) {
        if (prefill != null) {
            input = prefill.input
            textToMorse = prefill.textToMorse
            onPrefillConsumed()
        }
    }

    DisposableEffect(Unit) {
        onDispose { player.stop() }
    }

    val output = remember(input, textToMorse) {
        if (textToMorse) MorseCode.encode(input) else MorseCode.decode(input)
    }
    val morseValid = remember(input, textToMorse) {
        textToMorse || input.isBlank() || MorseCode.isValidMorse(MorseCode.normalize(input))
    }
    val morseForStats = remember(input, output, textToMorse) {
        if (textToMorse) output else MorseCode.normalize(input)
    }
    val statsText = remember(input, output, textToMorse, wpm) {
        if (output.isBlank()) null
        else {
            val chars = if (textToMorse) input.length else output.length
            val source = if (textToMorse) input else output
            val words = source.trim().split(Regex("\\s+")).count { it.isNotEmpty() }
            val duration = MorseCode.formatDuration(
                MorseCode.estimatedDurationMs(morseForStats, wpm)
            )
            Triple(chars, words, duration)
        }
    }

    fun toast(msg: String) =
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

    fun saveHistory() {
        scope.launch { repository.addHistory(input, output, textToMorse) }
    }

    /** kind: 0 = sound, 1 = flash, 2 = vibration, 3 = all combined. Toggles stop when playing. */
    fun startPlayback(kind: Int) {
        if (output.isBlank()) return
        if (isPlaying) {
            player.stop()
            isPlaying = false
            return
        }
        val settings = PlaybackSettings(wpm, toneHz)
        isPlaying = true
        saveHistory()
        val done = { isPlaying = false }
        when (kind) {
            0 -> player.playSound(scope, output, settings, done)
            1 -> player.playFlash(scope, output, settings, done)
            2 -> player.playVibration(scope, output, settings, done)
            else -> player.playCombined(scope, output, settings, done)
        }
    }

    val flashPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startPlayback(1)
        else toast(context.getString(R.string.camera_permission_needed))
    }

    fun onFlashPressed() {
        if (!player.hasFlash()) {
            toast(context.getString(R.string.no_flash))
            return
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            startPlayback(1)
        } else {
            flashPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Direction selector
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = textToMorse,
                onClick = { textToMorse = true },
                label = { Text(stringResource(R.string.mode_text_to_morse)) },
                modifier = Modifier.weight(1f)
            )
            FilterChip(
                selected = !textToMorse,
                onClick = { textToMorse = false },
                label = { Text(stringResource(R.string.mode_morse_to_text)) },
                modifier = Modifier.weight(1f)
            )
        }

        // Quick phrases (text mode only)
        if (textToMorse) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.presets_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MorseCode.PRESETS.forEach { (name, text) ->
                        AssistChip(
                            onClick = { input = text },
                            label = { Text(name) }
                        )
                    }
                }
            }
        }

        // Input field
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 120.dp),
            label = {
                Text(
                    stringResource(
                        if (textToMorse) R.string.input_label_text else R.string.input_label_morse
                    )
                )
            },
            placeholder = {
                Text(
                    stringResource(
                        if (textToMorse) R.string.input_hint_text else R.string.input_hint_morse
                    )
                )
            },
            isError = !morseValid,
            supportingText = {
                if (!morseValid) Text(stringResource(R.string.invalid_morse))
            },
            trailingIcon = {
                if (input.isNotEmpty()) {
                    IconButton(onClick = { input = "" }) {
                        Icon(
                            Icons.Filled.Clear,
                            contentDescription = stringResource(R.string.action_clear)
                        )
                    }
                }
            }
        )

        // Morse helper pad (only in morse-input mode)
        if (!textToMorse) {
            MorseInputPad(
                onDot = { input += "." },
                onDash = { input += "-" },
                onLetterSpace = { input += " " },
                onWordSpace = { input += " / " },
                onBackspace = { if (input.isNotEmpty()) input = input.dropLast(1) },
                onTapDuration = { ms -> input += if (ms < 220) "." else "-" }
            )
        }

        // Swap / copy / share
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = {
                    if (output.isNotBlank()) {
                        input = output
                        textToMorse = !textToMorse
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Filled.SwapVert, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.action_swap))
            }
            IconButton(
                onClick = {
                    clipboard.setText(AnnotatedString(output))
                    toast(context.getString(R.string.copied))
                    saveHistory()
                },
                enabled = output.isNotBlank()
            ) {
                Icon(
                    Icons.Filled.ContentCopy,
                    contentDescription = stringResource(R.string.action_copy)
                )
            }
            IconButton(
                onClick = {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, output)
                    }
                    context.startActivity(Intent.createChooser(send, null))
                    saveHistory()
                },
                enabled = output.isNotBlank()
            ) {
                Icon(
                    Icons.Filled.Share,
                    contentDescription = stringResource(R.string.action_share)
                )
            }
        }

        // Output card with live stats
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    stringResource(R.string.output_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                SelectionContainer {
                    Text(
                        text = output.ifBlank { stringResource(R.string.output_hint) },
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontFamily = if (textToMorse) FontFamily.Monospace else FontFamily.Default
                        ),
                        color = if (output.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onSurface
                    )
                }
                statsText?.let { (chars, words, duration) ->
                    Text(
                        stringResource(R.string.stats_format, chars, words, duration),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Playback card
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PlaybackButton(
                        icon = Icons.Filled.VolumeUp,
                        labelRes = R.string.play_sound,
                        enabled = output.isNotBlank() && !isPlaying,
                        onClick = { startPlayback(0) }
                    )
                    PlaybackButton(
                        icon = Icons.Filled.FlashlightOn,
                        labelRes = R.string.play_flash,
                        enabled = output.isNotBlank() && !isPlaying,
                        onClick = { onFlashPressed() }
                    )
                    PlaybackButton(
                        icon = Icons.Filled.Vibration,
                        labelRes = R.string.play_vibrate,
                        enabled = output.isNotBlank() && !isPlaying,
                        onClick = { startPlayback(2) }
                    )
                    PlaybackButton(
                        icon = Icons.Filled.GraphicEq,
                        labelRes = R.string.play_all,
                        enabled = output.isNotBlank() && !isPlaying,
                        onClick = { startPlayback(3) }
                    )
                    if (isPlaying) {
                        FilledIconButton(
                            onClick = {
                                player.stop()
                                isPlaying = false
                            }
                        ) {
                            Icon(
                                Icons.Filled.Stop,
                                contentDescription = stringResource(R.string.action_stop)
                            )
                        }
                    }
                }
                // WPM presets
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf(
                        R.string.wpm_slow to 10,
                        R.string.wpm_normal to 18,
                        R.string.wpm_fast to 30
                    ).forEach { (labelRes, value) ->
                        FilterChip(
                            selected = wpm == value,
                            onClick = { scope.launch { repository.setWpm(value) } },
                            label = { Text(stringResource(labelRes)) }
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        stringResource(R.string.wpm, wpm),
                        modifier = Modifier.width(110.dp),
                        style = MaterialTheme.typography.labelMedium
                    )
                    Slider(
                        value = wpm.toFloat(),
                        onValueChange = { scope.launch { repository.setWpm(it.toInt()) } },
                        valueRange = 5f..40f,
                        steps = 34,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaybackButton(
    icon: ImageVector,
    labelRes: Int,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        FilledTonalIconButton(onClick = onClick, enabled = enabled) {
            Icon(icon, contentDescription = stringResource(labelRes))
        }
        Text(stringResource(labelRes), style = MaterialTheme.typography.labelSmall)
    }
}

/**
 * Helper pad for morse input: dot/dash/space buttons, backspace,
 * and a hold-to-input pad (quick tap = dot, long hold = dash).
 */
@Composable
private fun MorseInputPad(
    onDot: () -> Unit,
    onDash: () -> Unit,
    onLetterSpace: () -> Unit,
    onWordSpace: () -> Unit,
    onBackspace: () -> Unit,
    onTapDuration: (Long) -> Unit
) {
    var pressing by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(onClick = onDot, modifier = Modifier.weight(1f)) {
                Text("•", fontFamily = FontFamily.Monospace)
            }
            OutlinedButton(onClick = onDash, modifier = Modifier.weight(1f)) {
                Text("▬", fontFamily = FontFamily.Monospace)
            }
            OutlinedButton(onClick = onLetterSpace, modifier = Modifier.weight(1f)) {
                Text("␣", fontFamily = FontFamily.Monospace)
            }
            OutlinedButton(onClick = onWordSpace, modifier = Modifier.weight(1f)) {
                Text("/", fontFamily = FontFamily.Monospace)
            }
            IconButton(onClick = onBackspace) {
                Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = "⌫")
            }
        }
        Button(
            onClick = { /* handled by the press detector below */ },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            pressing = true
                            val start = System.currentTimeMillis()
                            val released = try {
                                awaitRelease()
                                true
                            } catch (_: Exception) {
                                false
                            }
                            pressing = false
                            if (released) onTapDuration(System.currentTimeMillis() - start)
                        }
                    )
                },
            colors = ButtonDefaults.buttonColors(
                containerColor = if (pressing) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Icon(Icons.Filled.TouchApp, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.tap_pad_hint))
        }
    }
}
