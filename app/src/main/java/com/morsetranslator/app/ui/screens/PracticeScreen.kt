package com.morsetranslator.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.morsetranslator.app.R
import com.morsetranslator.app.data.SettingsRepository
import com.morsetranslator.app.morse.MorseCode
import com.morsetranslator.app.morse.MorsePlayer
import com.morsetranslator.app.morse.PlaybackSettings
import com.morsetranslator.app.ui.theme.GlassBottomSpacer
import com.morsetranslator.app.ui.theme.GlassCard
import com.morsetranslator.app.ui.theme.GlassChip
import com.morsetranslator.app.ui.theme.GlassIconButton
import com.morsetranslator.app.ui.theme.GlassPrimaryButton
import com.morsetranslator.app.ui.theme.GlassSegmentedControl
import com.morsetranslator.app.ui.theme.glassBorder
import com.morsetranslator.app.ui.theme.glassContainer
import com.morsetranslator.app.ui.theme.glassGradientBrush
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private fun randomTarget(): Char = ('A'..'Z').random()

private fun shuffledOptions(target: Char): List<Char> =
    (('A'..'Z').filter { it != target }.shuffled().take(3) + target).shuffled()

/** Interactive morse quiz: listen-and-identify or tap-the-code. */
@Composable
fun PracticeScreen(repository: SettingsRepository, player: MorsePlayer) {
    val scope = rememberCoroutineScope()

    var mode by rememberSaveable { mutableStateOf(0) } // 0 = listen, 1 = tap
    var target by remember { mutableStateOf(randomTarget()) }
    var options by remember { mutableStateOf(shuffledOptions(target)) }
    var score by rememberSaveable { mutableStateOf(0) }
    var streak by rememberSaveable { mutableStateOf(0) }
    var chosen by remember { mutableStateOf<Char?>(null) }
    var wasCorrect by remember { mutableStateOf<Boolean?>(null) }
    var tapInput by remember { mutableStateOf("") }

    val best by repository.practiceBest.collectAsState(initial = 0)
    val wpm by repository.wpm.collectAsState(initial = 18)
    val toneHz by repository.toneHz.collectAsState(initial = 700)

    DisposableEffect(Unit) {
        onDispose { player.stop() }
    }

    fun playTarget() {
        val morse = MorseCode.CHAR_TO_MORSE[target] ?: return
        player.playSound(scope, morse, PlaybackSettings(wpm, toneHz)) {}
    }

    fun nextRound() {
        target = randomTarget()
        options = shuffledOptions(target)
        tapInput = ""
        chosen = null
        wasCorrect = null
    }

    fun onResult(correct: Boolean) {
        if (wasCorrect != null) return // already answered this round
        wasCorrect = correct
        if (correct) {
            score++
            streak++
            scope.launch { repository.setPracticeBest(score) }
        } else {
            streak = 0
        }
        scope.launch {
            delay(1000)
            nextRound()
        }
    }

    fun reset() {
        player.stop()
        score = 0
        streak = 0
        nextRound()
    }

    // Auto-play the target morse whenever a new listen round starts.
    LaunchedEffect(target, mode) {
        if (mode == 0) playTarget()
    }

    val expected = MorseCode.CHAR_TO_MORSE[target].orEmpty()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Spacer(Modifier.height(4.dp))

        // Score board — glass pills
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ScoreGlassChip(
                icon = Icons.Filled.Star,
                label = stringResource(R.string.practice_score),
                value = score.toString(),
                modifier = Modifier.weight(1f)
            )
            ScoreGlassChip(
                icon = Icons.Filled.LocalFireDepartment,
                label = stringResource(R.string.practice_streak),
                value = streak.toString(),
                modifier = Modifier.weight(1f)
            )
            ScoreGlassChip(
                icon = Icons.Filled.EmojiEvents,
                label = stringResource(R.string.practice_best),
                value = best.toString(),
                modifier = Modifier.weight(1f)
            )
        }

        // Mode selector
        GlassSegmentedControl(
            options = listOf(
                stringResource(R.string.practice_listen),
                stringResource(R.string.practice_tap)
            ),
            selected = mode,
            onSelect = { mode = it; reset() },
            modifier = Modifier.fillMaxWidth()
        )

        if (mode == 0) {
            // ---- Listen mode ----
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        stringResource(R.string.practice_listen_hint),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "?",
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontWeight = FontWeight.ExtraBold
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )
                    GlassChip(
                        text = stringResource(R.string.practice_replay),
                        onClick = { playTarget() }
                    )
                }
            }

            // Options grid (2x2) — glass answer tiles
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                options.chunked(2).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        row.forEach { char ->
                            val isChosen = chosen == char
                            val tileColor = when {
                                wasCorrect == true && isChosen ->
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                                wasCorrect == false && isChosen ->
                                    MaterialTheme.colorScheme.error.copy(alpha = 0.35f)
                                else -> glassContainer()
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(72.dp)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(tileColor)
                                    .border(1.dp, glassBorder(), RoundedCornerShape(20.dp))
                                    .clickable(enabled = wasCorrect == null) {
                                        chosen = char
                                        onResult(char == target)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    char.toString(),
                                    style = MaterialTheme.typography.headlineMedium.copy(
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // ---- Tap mode ----
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        stringResource(R.string.practice_tap_hint),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        target.toString(),
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontWeight = FontWeight.ExtraBold
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        tapInput.ifBlank { "…" },
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        ),
                        color = when (wasCorrect) {
                            true -> MaterialTheme.colorScheme.primary
                            false -> MaterialTheme.colorScheme.error
                            null -> MaterialTheme.colorScheme.onSurface
                        },
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            if (wasCorrect == null) glassGradientBrush()
                            else glassContainer()
                        )
                        .clickable(enabled = wasCorrect == null) { tapInput += "." }
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "•",
                        style = MaterialTheme.typography.headlineSmall,
                        fontFamily = FontFamily.Monospace,
                        color = if (wasCorrect == null) Color.White
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            if (wasCorrect == null) glassGradientBrush()
                            else glassContainer()
                        )
                        .clickable(enabled = wasCorrect == null) { tapInput += "-" }
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "▬",
                        style = MaterialTheme.typography.headlineSmall,
                        fontFamily = FontFamily.Monospace,
                        color = if (wasCorrect == null) Color.White
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                }
                GlassIconButton(
                    icon = Icons.AutoMirrored.Filled.Backspace,
                    description = "⌫",
                    onClick = { if (tapInput.isNotEmpty()) tapInput = tapInput.dropLast(1) },
                    enabled = wasCorrect == null,
                    size = 52.dp
                )
            }

            GlassPrimaryButton(
                text = stringResource(R.string.practice_check),
                icon = Icons.Filled.Check,
                onClick = { onResult(tapInput == expected && tapInput.isNotEmpty()) },
                enabled = wasCorrect == null && tapInput.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Reset
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            GlassChip(
                text = stringResource(R.string.action_reset),
                onClick = { reset() }
            )
        }

        GlassBottomSpacer()
    }
}

@Composable
private fun ScoreGlassChip(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(glassContainer())
            .border(1.dp, glassBorder(), RoundedCornerShape(20.dp))
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Text(
            value,
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
