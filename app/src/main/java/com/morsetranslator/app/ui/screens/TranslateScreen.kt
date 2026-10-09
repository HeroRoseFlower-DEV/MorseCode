package com.morsetranslator.app.ui.screens

import android.Manifest
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.morsetranslator.app.R
import com.morsetranslator.app.data.SettingsRepository
import com.morsetranslator.app.morse.MorseCode
import com.morsetranslator.app.morse.MorsePlayer
import com.morsetranslator.app.morse.Output
import com.morsetranslator.app.morse.OutputIssue
import com.morsetranslator.app.morse.PlaybackSettings
import com.morsetranslator.app.ui.Prefill
import com.morsetranslator.app.ui.issueText
import com.morsetranslator.app.ui.rememberPlaybackController
import com.morsetranslator.app.ui.theme.BottomSpacer
import com.morsetranslator.app.ui.theme.CalmCard
import com.morsetranslator.app.ui.theme.CalmChip
import com.morsetranslator.app.ui.theme.CalmIconButton
import com.morsetranslator.app.ui.theme.CalmOutlineChip
import com.morsetranslator.app.ui.theme.CalmSegmentedCard
import com.morsetranslator.app.ui.theme.MorseText
import com.morsetranslator.app.ui.theme.PrimaryButton
import com.morsetranslator.app.ui.theme.SectionTitle
import com.morsetranslator.app.ui.theme.calmTextFieldColors
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
    val playback = rememberPlaybackController(player)

    var textToMorse by rememberSaveable { mutableStateOf(true) }
    var input by rememberSaveable { mutableStateOf("") }
    // Output is an enum and not Bundle-saveable; persist the selected names
    // (Set<String> is saveable) and derive the enum set from them.
    var outputNames by rememberSaveable { mutableStateOf(setOf(Output.SOUND.name)) }
    val outputs: Set<Output> = remember(outputNames) {
        outputNames.mapNotNull { runCatching { Output.valueOf(it) }.getOrNull() }.toSet()
    }
    fun toggleOutput(output: Output) {
        outputNames =
            if (output.name in outputNames) outputNames - output.name
            else outputNames + output.name
    }
    var settingsExpanded by rememberSaveable { mutableStateOf(false) }
    var showFlashRationale by remember { mutableStateOf(false) }

    val wpm by repository.wpm.collectAsState(initial = SettingsRepository.DEFAULT_WPM)
    val toneHz by repository.toneHz.collectAsState(initial = SettingsRepository.DEFAULT_TONE_HZ)
    val profileId by repository.profileId.collectAsState(initial = MorseCode.INTERNATIONAL.id)
    val profile = remember(profileId) { MorseCode.profileById(profileId) }

    // Local slider state: responsive while dragging, persisted on release.
    var wpmSlider by remember(wpm) { mutableStateOf(wpm.toFloat()) }
    var toneSlider by remember(toneHz) { mutableStateOf(toneHz.toFloat()) }

    LaunchedEffect(prefill) {
        if (prefill != null) {
            input = prefill.input
            textToMorse = prefill.textToMorse
            onPrefillConsumed()
        }
    }

    DisposableEffect(Unit) {
        onDispose { playback.stop() }
    }

    // ---- canonical conversion (single source of truth)
    val encodeResult = remember(input, profile) {
        if (textToMorse) MorseCode.encodeDetailed(input, profile) else null
    }
    val decodeResult = remember(input, profile) {
        if (!textToMorse) MorseCode.decodeDetailed(input, profile) else null
    }
    val output = when {
        textToMorse -> encodeResult?.morse.orEmpty()
        else -> decodeResult?.text.orEmpty()
    }
    // The exact sequence the player must use (never decoded text).
    val playbackMorse = remember(textToMorse, input, encodeResult, profile) {
        MorseCode.playbackMorse(textToMorse, input, encodeResult?.morse.orEmpty(), profile)
    }
    val planDurationMs = remember(playbackMorse, wpm, profile) {
        MorseCode.estimatedDurationMs(playbackMorse, wpm, profile)
    }
    val statsText = remember(input, output, playbackMorse, textToMorse, wpm, profile) {
        if (output.isBlank()) null
        else {
            val chars = if (textToMorse) input.length else output.length
            val source = if (textToMorse) input else output
            val words = source.trim().split(Regex("\\s+")).count { it.isNotEmpty() }
            Triple(chars, words, MorseCode.formatDuration(planDurationMs))
        }
    }

    fun toast(msg: String) =
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

    fun saveHistory() {
        scope.launch { repository.addHistory(input, output, textToMorse, profileId) }
    }

    val flashPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        // Preflight inside the player reports denial explicitly; just start.
        playback.play(
            playbackMorse,
            PlaybackSettings(wpm, toneHz),
            outputs,
            profile,
            onStarted = { saveHistory() }
        )
    }

    fun onPlayPressed() {
        if (playbackMorse.isBlank()) return
        playback.clearMessages()
        if (Output.FLASH in outputs && !player.hasFlashPermission()) {
            showFlashRationale = true
            return
        }
        playback.play(
            playbackMorse,
            PlaybackSettings(wpm, toneHz),
            outputs,
            profile,
            onStarted = { saveHistory() }
        )
    }

    if (showFlashRationale) {
        AlertDialog(
            onDismissRequest = { showFlashRationale = false },
            title = { Text(stringResource(R.string.flash_rationale_title)) },
            text = { Text(stringResource(R.string.flash_rationale_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showFlashRationale = false
                        flashPermissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                ) { Text(stringResource(R.string.action_continue)) }
            },
            dismissButton = {
                TextButton(onClick = { showFlashRationale = false }) {
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

        // 1. Title + explanation
        Text(
            stringResource(R.string.translate_explanation),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp)
        )

        // 2. Mode control
        CalmSegmentedCard(
            options = listOf(
                stringResource(R.string.mode_text_to_morse),
                stringResource(R.string.mode_morse_to_text)
            ),
            selected = if (textToMorse) 0 else 1,
            onSelect = { textToMorse = it == 0 },
            modifier = Modifier.fillMaxWidth(),
            testTagPrefix = "mode_option_"
        )

        // Quick phrases (text mode, international profile)
        if (textToMorse && profile.id == MorseCode.INTERNATIONAL.id) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SectionTitle(stringResource(R.string.presets_label))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MorseCode.PRESETS.forEach { (name, text) ->
                        CalmOutlineChip(text = name, onClick = { input = text })
                    }
                }
            }
        }

        // 3. Input card
        CalmCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                stringResource(
                    if (textToMorse) R.string.input_label_text else R.string.input_label_morse
                ),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 110.dp),
                placeholder = {
                    Text(
                        stringResource(
                            if (textToMorse) R.string.input_hint_text else R.string.input_hint_morse
                        )
                    )
                },
                shape = RoundedCornerShape(12.dp),
                colors = calmTextFieldColors(),
                trailingIcon = {
                    Row {
                        IconButton(
                            onClick = {
                                clipboard.getText()?.let { input = it.text }
                            }
                        ) {
                            Icon(
                                Icons.Filled.ContentPaste,
                                contentDescription = stringResource(R.string.action_paste)
                            )
                        }
                        if (input.isNotEmpty()) {
                            IconButton(onClick = { input = "" }) {
                                Icon(
                                    Icons.Filled.Clear,
                                    contentDescription = stringResource(R.string.action_clear)
                                )
                            }
                        }
                    }
                }
            )

            // 4. Validation feedback, right under the input
            val validationContent: @Composable () -> Unit = {
                if (textToMorse) {
                    val unsupported = encodeResult?.unsupported.orEmpty()
                    if (unsupported.isNotEmpty()) {
                        ValidationWarning(
                            title = stringResource(
                                R.string.validation_unsupported_title, unsupported.size
                            ),
                            detail = stringResource(
                                R.string.validation_unsupported_detail,
                                unsupported.take(8).joinToString(", ") {
                                    "'${it.char}' (#${it.position + 1})"
                                } + if (unsupported.size > 8) "…" else ""
                            )
                        )
                    }
                } else {
                    val unknown = decodeResult?.unknownTokens.orEmpty()
                    val malformed = decodeResult?.malformedSeparators == true
                    if (unknown.isNotEmpty()) {
                        ValidationWarning(
                            title = stringResource(
                                R.string.validation_unknown_title, unknown.size
                            ),
                            detail = stringResource(
                                R.string.validation_unknown_detail,
                                unknown.take(6).joinToString(", ") {
                                    "'${it.token}' (#${it.index + 1})"
                                } + if (unknown.size > 6) "…" else ""
                            )
                        )
                    }
                    if (malformed) {
                        ValidationWarning(
                            title = stringResource(R.string.validation_malformed_title),
                            detail = stringResource(R.string.validation_malformed_detail)
                        )
                    }
                }
            }
            validationContent()
        }

        // Morse helper pad (morse-input mode)
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

        // 5. Output card
        CalmCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.output_label),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                )
                Row {
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
            }
            Spacer(Modifier.height(4.dp))
            SelectionContainer {
                if (textToMorse) {
                    MorseText(
                        output.ifBlank { stringResource(R.string.output_hint) },
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (output.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onSurface
                    )
                } else {
                    Text(
                        text = output.ifBlank { stringResource(R.string.output_hint) },
                        color = if (output.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onSurface
                    )
                }
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

        // 6-8. Playback card: outputs, primary play, progress, settings
        CalmCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                stringResource(R.string.playback_outputs_label),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                OutputChip(
                    label = stringResource(R.string.play_sound),
                    icon = Icons.Filled.VolumeUp,
                    selected = Output.SOUND in outputs,
                    onToggle = {
                        toggleOutput(Output.SOUND)
                    },
                    modifier = Modifier.weight(1f)
                )
                OutputChip(
                    label = stringResource(R.string.play_flash),
                    icon = Icons.Filled.FlashlightOn,
                    selected = Output.FLASH in outputs,
                    onToggle = { toggleOutput(Output.FLASH) },
                    modifier = Modifier.weight(1f)
                )
                OutputChip(
                    label = stringResource(R.string.play_vibrate),
                    icon = Icons.Filled.Vibration,
                    selected = Output.VIBRATION in outputs,
                    onToggle = { toggleOutput(Output.VIBRATION) },
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(12.dp))

            // 7. Primary play action
            if (playback.isPlaying) {
                PrimaryButton(
                    text = stringResource(R.string.action_stop),
                    icon = Icons.Filled.Stop,
                    onClick = { playback.stop() },
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                PrimaryButton(
                    text = stringResource(R.string.play_morse),
                    icon = Icons.Filled.GraphicEq,
                    onClick = { onPlayPressed() },
                    enabled = playbackMorse.isNotBlank() && outputs.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // 10. Progress + status
            AnimatedVisibility(visible = playback.isPlaying || playback.progress > 0f) {
                Column {
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { playback.progress },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (playback.isPlaying && playback.totalMs > 0) {
                            stringResource(
                                R.string.progress_format,
                                MorseCode.formatDuration(playback.elapsedMs),
                                MorseCode.formatDuration(playback.totalMs)
                            )
                        } else if (!playback.isPlaying && playback.progress >= 1f) {
                            stringResource(
                                R.string.progress_format,
                                MorseCode.formatDuration(planDurationMs),
                                MorseCode.formatDuration(planDurationMs)
                            )
                        } else {
                            stringResource(
                                R.string.progress_estimate,
                                MorseCode.formatDuration(planDurationMs)
                            )
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Recoverable issues / fatal errors (never color-only: icon + text)
            val issues = playback.issues
            val fatal = playback.fatalIssue
            if (issues.isNotEmpty() || fatal != null) {
                Spacer(Modifier.height(8.dp))
                (fatal?.let { listOf(it) } ?: issues).forEach { issue ->
                    PlaybackIssueRow(issue = issue)
                }
                TextButton(onClick = { playback.clearMessages() }) {
                    Text(stringResource(R.string.action_dismiss))
                }
            }

            // 9. Collapsible playback settings
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = { settingsExpanded = !settingsExpanded }
                ) {
                    Text(stringResource(R.string.playback_settings))
                    Icon(
                        if (settingsExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = null
                    )
                }
            }
            AnimatedVisibility(visible = settingsExpanded) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                                onClick = {
                                    wpmSlider = value.toFloat()
                                    scope.launch { repository.setWpm(value) }
                                },
                                label = { Text(stringResource(labelRes)) }
                            )
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            stringResource(R.string.wpm, wpmSlider.toInt()),
                            modifier = Modifier.width(110.dp),
                            style = MaterialTheme.typography.labelLarge
                        )
                        Slider(
                            value = wpmSlider,
                            onValueChange = { wpmSlider = it },
                            onValueChangeFinished = {
                                scope.launch { repository.setWpm(wpmSlider.toInt()) }
                            },
                            valueRange = 5f..40f,
                            steps = 34,
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            stringResource(R.string.tone, toneSlider.toInt()),
                            modifier = Modifier.width(110.dp),
                            style = MaterialTheme.typography.labelLarge
                        )
                        Slider(
                            value = toneSlider,
                            onValueChange = { toneSlider = it },
                            onValueChangeFinished = {
                                scope.launch { repository.setToneHz(toneSlider.toInt()) }
                            },
                            valueRange = 300f..1200f,
                            steps = 17,
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                }
            }
        }

        // Swap direction (secondary)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            CalmChip(
                text = stringResource(R.string.action_swap),
                onClick = {
                    if (output.isNotBlank()) {
                        input = output
                        textToMorse = !textToMorse
                    }
                }
            )
        }

        // Daily message
        DailyMessageCard(repository = repository, player = player)

        BottomSpacer()
    }
}


@Composable
private fun OutputChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilterChip(
        selected = selected,
        onClick = onToggle,
        label = { Text(label, maxLines = 1) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        modifier = modifier
    )
}

/** Warning block with icon + text (never color-only). */
@Composable
private fun ValidationWarning(title: String, detail: String) {
    Spacer(Modifier.height(8.dp))
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            Icons.Filled.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary
        )
        Column {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.secondary
            )
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PlaybackIssueRow(issue: OutputIssue) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            Icons.Filled.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary
        )
        Text(
            issueText(issue),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
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
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CalmOutlineChip(text = "•", onClick = onDot, modifier = Modifier.weight(1f))
            CalmOutlineChip(text = "▬", onClick = onDash, modifier = Modifier.weight(1f))
            CalmOutlineChip(text = "␣", onClick = onLetterSpace, modifier = Modifier.weight(1f))
            CalmOutlineChip(text = "/", onClick = onWordSpace, modifier = Modifier.weight(1f))
            CalmIconButton(
                icon = Icons.AutoMirrored.Filled.Backspace,
                description = "⌫",
                onClick = onBackspace
            )
        }
        PrimaryButton(
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
