package com.morsetranslator.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.morsetranslator.app.R
import com.morsetranslator.app.data.SettingsRepository
import com.morsetranslator.app.morse.MorseCode
import com.morsetranslator.app.morse.MorsePlayer
import com.morsetranslator.app.morse.PlaybackSettings
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
    var rounds by rememberSaveable { mutableStateOf(0) }
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
        rounds++
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
        rounds = 0
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
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Score board
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ScoreChip(
                icon = Icons.Filled.Star,
                label = stringResource(R.string.practice_score),
                value = score.toString(),
                modifier = Modifier.weight(1f)
            )
            ScoreChip(
                icon = Icons.Filled.LocalFireDepartment,
                label = stringResource(R.string.practice_streak),
                value = streak.toString(),
                modifier = Modifier.weight(1f)
            )
            ScoreChip(
                icon = Icons.Filled.EmojiEvents,
                label = stringResource(R.string.practice_best),
                value = best.toString(),
                modifier = Modifier.weight(1f)
            )
        }

        // Mode selector
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = mode == 0,
                onClick = { mode = 0; reset() },
                label = { Text(stringResource(R.string.practice_listen)) },
                modifier = Modifier.weight(1f)
            )
            FilterChip(
                selected = mode == 1,
                onClick = { mode = 1; reset() },
                label = { Text(stringResource(R.string.practice_tap)) },
                modifier = Modifier.weight(1f)
            )
        }

        if (mode == 0) {
            // ---- Listen mode ----
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
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
                        style = MaterialTheme.typography.displayLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    OutlinedButton(onClick = { playTarget() }) {
                        Icon(Icons.Filled.VolumeUp, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.practice_replay))
                    }
                }
            }

            // Options grid (2x2)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                options.chunked(2).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        row.forEach { char ->
                            val isChosen = chosen == char
                            val containerColor = when {
                                wasCorrect == true && isChosen -> MaterialTheme.colorScheme.primary
                                wasCorrect == false && isChosen -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.secondaryContainer
                            }
                            Button(
                                onClick = {
                                    chosen = char
                                    onResult(char == target)
                                },
                                enabled = wasCorrect == null,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(64.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = containerColor
                                )
                            ) {
                                Text(
                                    char.toString(),
                                    style = MaterialTheme.typography.headlineSmall
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // ---- Tap mode ----
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        stringResource(R.string.practice_tap_hint),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        target.toString(),
                        style = MaterialTheme.typography.displayLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        expected,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = FontFamily.Monospace
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        tapInput.ifBlank { "…" },
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontFamily = FontFamily.Monospace
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
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { if (wasCorrect == null) tapInput += "." },
                    modifier = Modifier.weight(1f),
                    enabled = wasCorrect == null
                ) {
                    Text("•", fontFamily = FontFamily.Monospace)
                }
                OutlinedButton(
                    onClick = { if (wasCorrect == null) tapInput += "-" },
                    modifier = Modifier.weight(1f),
                    enabled = wasCorrect == null
                ) {
                    Text("▬", fontFamily = FontFamily.Monospace)
                }
                IconButton(
                    onClick = { if (tapInput.isNotEmpty()) tapInput = tapInput.dropLast(1) },
                    enabled = wasCorrect == null
                ) {
                    Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = "⌫")
                }
            }

            Button(
                onClick = { onResult(tapInput == expected && tapInput.isNotEmpty()) },
                enabled = wasCorrect == null && tapInput.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Check, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.practice_check))
            }
        }

        // Reset
        OutlinedButton(
            onClick = { reset() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Filled.Refresh, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.action_reset))
        }
    }
}

@Composable
private fun ScoreChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = modifier,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
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
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}
