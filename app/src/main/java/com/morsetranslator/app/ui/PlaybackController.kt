package com.morsetranslator.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.morsetranslator.app.morse.MorseCode
import com.morsetranslator.app.morse.MorsePlayer
import com.morsetranslator.app.morse.Output
import com.morsetranslator.app.morse.OutputIssue
import com.morsetranslator.app.morse.PlaybackEvent
import com.morsetranslator.app.morse.PlaybackSettings
import kotlinx.coroutines.CoroutineScope

/**
 * UI-side state holder for one playback session.
 *
 * Translates [MorsePlayer] events into observable Compose state: playing
 * flag, progress, and per-output issues / fatal errors. The player delivers
 * all callbacks on the main thread, so state updates are safe.
 */
class PlaybackController(
    private val player: MorsePlayer,
    private val scope: CoroutineScope
) {
    var isPlaying by mutableStateOf(false)
        private set
    var progress by mutableStateOf(0f)
        private set
    var elapsedMs by mutableStateOf(0L)
        private set
    var totalMs by mutableStateOf(0L)
        private set

    /** Outputs that could not run (shown as a recoverable warning). */
    var issues by mutableStateOf<List<OutputIssue>>(emptyList())
        private set

    /** Set when nothing could be played at all. */
    var fatalIssue by mutableStateOf<OutputIssue?>(null)
        private set

    /**
     * Starts playback, or stops when already playing (toggle behavior).
     * [onStarted] runs when the session actually starts (e.g. to save history).
     */
    fun play(
        morse: String,
        settings: PlaybackSettings,
        outputs: Set<Output>,
        profile: MorseCode.AlphabetProfile = MorseCode.INTERNATIONAL,
        onStarted: () -> Unit = {}
    ) {
        if (isPlaying) {
            stop()
            return
        }
        if (morse.isBlank() || outputs.isEmpty()) return
        issues = emptyList()
        fatalIssue = null
        progress = 0f
        elapsedMs = 0L
        totalMs = 0L
        player.play(scope, morse, settings, outputs, profile) { event ->
            when (event) {
                is PlaybackEvent.Started -> {
                    isPlaying = true
                    onStarted()
                }
                is PlaybackEvent.Progress -> {
                    progress = event.fraction
                    elapsedMs = event.elapsedMs
                    totalMs = event.totalMs
                }
                is PlaybackEvent.Finished -> {
                    isPlaying = false
                    progress = 1f
                    issues = event.issues
                }
                is PlaybackEvent.Failed -> {
                    isPlaying = false
                    fatalIssue = event.issue
                }
            }
        }
    }

    fun stop() {
        player.stop()
        isPlaying = false
    }

    fun clearMessages() {
        issues = emptyList()
        fatalIssue = null
    }
}

@Composable
fun rememberPlaybackController(player: MorsePlayer): PlaybackController {
    val scope = rememberCoroutineScope()
    return remember(player) { PlaybackController(player, scope) }
}
