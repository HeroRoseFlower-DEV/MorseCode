package com.morsetranslator.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.morsetranslator.app.R
import com.morsetranslator.app.data.SettingsRepository
import com.morsetranslator.app.learn.ReviewScheduler
import com.morsetranslator.app.morse.MorseCode
import com.morsetranslator.app.morse.MorsePlayer
import com.morsetranslator.app.morse.Output
import com.morsetranslator.app.morse.PersianMorse
import com.morsetranslator.app.morse.PlaybackSettings
import com.morsetranslator.app.ui.rememberPlaybackController
import com.morsetranslator.app.ui.theme.BottomSpacer
import com.morsetranslator.app.ui.theme.CalmCard
import com.morsetranslator.app.ui.theme.CalmChip
import com.morsetranslator.app.ui.theme.CalmIconButton
import com.morsetranslator.app.ui.theme.CalmSegmentedCard
import com.morsetranslator.app.ui.theme.PrimaryButton
import com.morsetranslator.app.ui.theme.SectionTitle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Interactive morse quiz: listen-and-identify or tap-the-code. */
@Composable
fun PracticeScreen(repository: SettingsRepository, player: MorsePlayer) {
    val scope = rememberCoroutineScope()
    val playback = rememberPlaybackController(player)

    val profileId by repository.profileId.collectAsState(initial = MorseCode.INTERNATIONAL.id)
    val profile = remember(profileId) { MorseCode.profileById(profileId) }
    val candidates = remember(profile) {
        if (profile.id == PersianMorse.PROFILE.id) {
            // Practice the 32 Persian letters (not digits/punctuation).
            PersianMorse.LETTERS
        } else {
            ('A'..'Z').toList()
        }
    }
    val reviewStats by repository.reviewStats.collectAsState(initial = emptyMap())
    val todayEpochDay = remember { LocalDate.now().toEpochDay() }

    var mode by rememberSaveable { mutableStateOf(0) } // 0 = listen, 1 = tap
    // Round state is keyed on a counter (not on the target char itself):
    // the same character can be picked twice in a row, and the profile
    // (hence the candidate set) can change while this screen is alive.
    var round by remember(candidates) { mutableStateOf(0) }
    var target by remember(candidates) { mutableStateOf(candidates.first()) }
    val options = remember(round) { shuffledOptions(target, candidates) }
    var score by rememberSaveable { mutableStateOf(0) }
    var streak by rememberSaveable { mutableStateOf(0) }
    var chosen by remember(round) { mutableStateOf<Char?>(null) }
    var wasCorrect by remember(round) { mutableStateOf<Boolean?>(null) }
    var tapInput by remember(round) { mutableStateOf("") }
    var showResetConfirm by remember { mutableStateOf(false) }

    val best by repository.practiceBest.collectAsState(initial = 0)
    val wpm by repository.wpm.collectAsState(initial = SettingsRepository.DEFAULT_WPM)
    val toneHz by repository.toneHz.collectAsState(initial = SettingsRepository.DEFAULT_TONE_HZ)

    DisposableEffect(Unit) {
        onDispose { playback.stop() }
    }

    fun playTarget() {
        val morse = profile.charToMorse[target] ?: return
        playback.play(
            morse = morse,
            settings = PlaybackSettings(wpm, toneHz),
            outputs = setOf(Output.SOUND),
            profile = profile
        )
    }

    fun nextRound() {
        // Adaptive review: prioritize due/weak characters (local, transparent).
        // Per-round state (options/chosen/answer/input) resets via the round key.
        target = ReviewScheduler.pickNext(reviewStats, candidates, todayEpochDay)
        round++
    }

    fun onResult(correct: Boolean) {
        if (wasCorrect != null) return // already answered this round
        wasCorrect = correct
        scope.launch {
            repository.recordReviewAnswer(target, correct, todayEpochDay)
        }
        if (correct) {
            score++
            streak++
            scope.launch { repository.setPracticeBest(score) }
        } else {
            streak = 0
        }
        scope.launch {
            delay(1100)
            nextRound()
        }
    }

    fun reset() {
        playback.stop()
        score = 0
        streak = 0
        nextRound()
    }

    // Auto-play the target morse whenever a new listen round starts.
    LaunchedEffect(target, mode) {
        if (mode == 0) playTarget()
    }

    val expected = profile.charToMorse[target].orEmpty()
    val totalAttempts = reviewStats.values.sumOf { it.attempts }
    val totalCorrect = reviewStats.values.sumOf { it.correct }
    val accuracy = if (totalAttempts == 0) null
    else (totalCorrect * 100 / totalAttempts)
    val weakest = remember(reviewStats) {
        ReviewScheduler.weakest(reviewStats, candidates, 3)
    }
    val dueCount = remember(reviewStats) {
        ReviewScheduler.dueCount(reviewStats, candidates, todayEpochDay)
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text(stringResource(R.string.practice_reset_title)) },
            text = { Text(stringResource(R.string.practice_reset_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch { repository.clearReviewStats() }
                        showResetConfirm = false
                    }
                ) { Text(stringResource(R.string.action_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) {
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
            stringResource(R.string.practice_explanation),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp)
        )

        // Score board
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ScoreCard(
                icon = Icons.Filled.Star,
                label = stringResource(R.string.practice_score),
                value = score.toString(),
                modifier = Modifier.weight(1f)
            )
            ScoreCard(
                icon = Icons.Filled.LocalFireDepartment,
                label = stringResource(R.string.practice_streak),
                value = streak.toString(),
                modifier = Modifier.weight(1f)
            )
            ScoreCard(
                icon = Icons.Filled.EmojiEvents,
                label = stringResource(R.string.practice_best),
                value = best.toString(),
                modifier = Modifier.weight(1f)
            )
        }

        // Mode selector
        CalmSegmentedCard(
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
            CalmCard(modifier = Modifier.fillMaxWidth()) {
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
                    CalmChip(
                        text = stringResource(R.string.practice_replay),
                        onClick = { playTarget() }
                    )
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
                            AnswerTile(
                                char = char,
                                state = when {
                                    wasCorrect == true && isChosen -> AnswerState.CORRECT
                                    wasCorrect == false && isChosen -> AnswerState.WRONG
                                    else -> AnswerState.IDLE
                                },
                                enabled = wasCorrect == null,
                                onClick = {
                                    chosen = char
                                    onResult(char == target)
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // Retry hint after a wrong answer: show the correct code.
            AnimatedVisibility(visible = wasCorrect == false) {
                CalmCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        stringResource(R.string.practice_correct_was, target.toString(), expected),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        } else {
            // ---- Tap mode ----
            CalmCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
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
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PrimaryButton(
                    text = "•",
                    onClick = { if (wasCorrect == null) tapInput += "." },
                    enabled = wasCorrect == null,
                    modifier = Modifier.weight(1f)
                )
                PrimaryButton(
                    text = "▬",
                    onClick = { if (wasCorrect == null) tapInput += "-" },
                    enabled = wasCorrect == null,
                    modifier = Modifier.weight(1f)
                )
                CalmIconButton(
                    icon = Icons.AutoMirrored.Filled.Backspace,
                    description = "⌫",
                    onClick = { if (tapInput.isNotEmpty()) tapInput = tapInput.dropLast(1) },
                    enabled = wasCorrect == null
                )
            }

            PrimaryButton(
                text = stringResource(R.string.practice_check),
                icon = Icons.Filled.Check,
                onClick = { onResult(tapInput == expected && tapInput.isNotEmpty()) },
                enabled = wasCorrect == null && tapInput.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            )

            AnimatedVisibility(visible = wasCorrect == false) {
                CalmCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        stringResource(R.string.practice_correct_was, target.toString(), expected),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        // ---- Progress (adaptive review summary)
        SectionTitle(stringResource(R.string.practice_progress_title))
        CalmCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        stringResource(R.string.practice_attempts, totalAttempts),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        if (accuracy == null) stringResource(R.string.practice_no_data)
                        else stringResource(R.string.practice_accuracy, accuracy, dueCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                CalmChip(
                    text = stringResource(R.string.action_reset),
                    onClick = { showResetConfirm = true }
                )
            }
            if (weakest.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.practice_weakest),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(Modifier.height(4.dp))
                weakest.forEach { (char, stats) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            char.toString(),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Text(
                            stringResource(
                                R.string.practice_weakest_detail,
                                (stats.accuracy * 100).toInt(),
                                stats.attempts
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.practice_review_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            CalmChip(
                text = stringResource(R.string.practice_replay),
                onClick = { reset() }
            )
        }

        BottomSpacer()
    }
}

private enum class AnswerState { IDLE, CORRECT, WRONG }

@Composable
private fun AnswerTile(
    char: Char,
    state: AnswerState,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val container = when (state) {
        AnswerState.CORRECT -> MaterialTheme.colorScheme.primaryContainer
        AnswerState.WRONG -> MaterialTheme.colorScheme.errorContainer
        AnswerState.IDLE -> MaterialTheme.colorScheme.surface
    }
    val content = when (state) {
        AnswerState.CORRECT -> MaterialTheme.colorScheme.onPrimaryContainer
        AnswerState.WRONG -> MaterialTheme.colorScheme.onErrorContainer
        AnswerState.IDLE -> MaterialTheme.colorScheme.onSurface
    }
    Card(
        modifier = modifier
            .height(72.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier.fillMaxSize(),
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

@Composable
private fun ScoreCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    CalmCard(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxWidth(),
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
}

private fun shuffledOptions(target: Char, candidates: List<Char>): List<Char> =
    (candidates.filter { it != target }.shuffled().take(3) + target).shuffled()
