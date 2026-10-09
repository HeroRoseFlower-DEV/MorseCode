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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.morsetranslator.app.R
import com.morsetranslator.app.data.SettingsRepository
import com.morsetranslator.app.morse.MorseCode
import com.morsetranslator.app.morse.MorsePlayer
import com.morsetranslator.app.morse.PlaybackSettings
import com.morsetranslator.app.ui.Prefill
import com.morsetranslator.app.ui.theme.GlassBottomSpacer
import com.morsetranslator.app.ui.theme.GlassCard
import com.morsetranslator.app.ui.theme.GlassChip
import com.morsetranslator.app.ui.theme.GlassIconButton
import com.morsetranslator.app.ui.theme.GlassPrimaryButton
import com.morsetranslator.app.ui.theme.GlassSegmentedControl
import com.morsetranslator.app.ui.theme.SectionTitle
import com.morsetranslator.app.ui.theme.glassBorder
import com.morsetranslator.app.ui.theme.glassContainer
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
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Spacer(Modifier.height(4.dp))

        // Direction selector — glass segmented control
        GlassSegmentedControl(
            options = listOf(
                stringResource(R.string.mode_text_to_morse),
                stringResource(R.string.mode_morse_to_text)
            ),
            selected = if (textToMorse) 0 else 1,
            onSelect = { textToMorse = it == 0 },
            modifier = Modifier.fillMaxWidth()
        )

        // Quick phrases (text mode only)
        if (textToMorse) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionTitle(stringResource(R.string.presets_label))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MorseCode.PRESETS.forEach { (name, text) ->
                        GlassChip(text = name, onClick = { input = text })
                    }
                }
            }
        }

        // Input field — frosted glass
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
            },
            shape = RoundedCornerShape(20.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = glassContainer(),
                unfocusedContainerColor = glassContainer(),
                disabledContainerColor = glassContainer(),
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = glassBorder()
            )
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
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GlassPrimaryButton(
                text = stringResource(R.string.action_swap),
                icon = Icons.Filled.SwapVert,
                onClick = {
                    if (output.isNotBlank()) {
                        input = output
                        textToMorse = !textToMorse
                    }
                },
                modifier = Modifier.weight(1f)
            )
            GlassIconButton(
                icon = Icons.Filled.ContentCopy,
                description = stringResource(R.string.action_copy),
                enabled = output.isNotBlank(),
                onClick = {
                    clipboard.setText(AnnotatedString(output))
                    toast(context.getString(R.string.copied))
                    saveHistory()
                },
                size = 52.dp
            )
            GlassIconButton(
                icon = Icons.Filled.Share,
                description = stringResource(R.string.action_share),
                enabled = output.isNotBlank(),
                onClick = {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, output)
                    }
                    context.startActivity(Intent.createChooser(send, null))
                    saveHistory()
                },
                size = 52.dp
            )
        }

        // Output card with live stats
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                stringResource(R.string.output_label),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(8.dp))
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
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.stats_format, chars, words, duration),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Playback card
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Top
            ) {
                PlaybackGlassButton(
                    icon = Icons.Filled.VolumeUp,
                    label = stringResource(R.string.play_sound),
                    enabled = output.isNotBlank() && !isPlaying,
                    onClick = { startPlayback(0) }
                )
                PlaybackGlassButton(
                    icon = Icons.Filled.FlashlightOn,
                    label = stringResource(R.string.play_flash),
                    enabled = output.isNotBlank() && !isPlaying,
                    onClick = { onFlashPressed() }
                )
                PlaybackGlassButton(
                    icon = Icons.Filled.Vibration,
                    label = stringResource(R.string.play_vibrate),
                    enabled = output.isNotBlank() && !isPlaying,
                    onClick = { startPlayback(2) }
                )
                PlaybackGlassButton(
                    icon = Icons.Filled.GraphicEq,
                    label = stringResource(R.string.play_all),
                    enabled = output.isNotBlank() && !isPlaying,
                    onClick = { startPlayback(3) }
                )
            }
            if (isPlaying) {
                Spacer(Modifier.height(4.dp))
                GlassPrimaryButton(
                    text = stringResource(R.string.action_stop),
                    onClick = {
                        player.stop()
                        isPlaying = false
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(Modifier.height(8.dp))
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
                    GlassChip(
                        text = stringResource(labelRes),
                        selected = wpm == value,
                        onClick = { scope.launch { repository.setWpm(value) } }
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
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                )
                Slider(
                    value = wpm.toFloat(),
                    onValueChange = { scope.launch { repository.setWpm(it.toInt()) } },
                    valueRange = 5f..40f,
                    steps = 34,
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary
                    )
                )
            }
        }

        GlassBottomSpacer()
    }
}

@Composable
private fun PlaybackGlassButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        GlassIconButton(
            icon = icon,
            description = label,
            onClick = onClick,
            enabled = enabled,
            size = 60.dp
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium)
        )
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
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GlassChip(text = "•", onClick = onDot, modifier = Modifier.weight(1f))
            GlassChip(text = "▬", onClick = onDash, modifier = Modifier.weight(1f))
            GlassChip(text = "␣", onClick = onLetterSpace, modifier = Modifier.weight(1f))
            GlassChip(text = "/", onClick = onWordSpace, modifier = Modifier.weight(1f))
            GlassIconButton(
                icon = Icons.AutoMirrored.Filled.Backspace,
                description = "⌫",
                onClick = onBackspace,
                size = 48.dp
            )
        }
        GlassPrimaryButton(
            text = stringResource(R.string.tap_pad_hint),
            icon = Icons.Filled.TouchApp,
            onClick = { /* handled by the press detector below */ },
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            val start = System.currentTimeMillis()
                            val released = try {
                                awaitRelease()
                                true
                            } catch (_: Exception) {
                                false
                            }
                            if (released) onTapDuration(System.currentTimeMillis() - start)
                        }
                    )
                }
        )
    }
}
