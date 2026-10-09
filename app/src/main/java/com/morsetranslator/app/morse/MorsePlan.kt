package com.morsetranslator.app.morse

/**
 * The single canonical timing model for the app.
 *
 * [parse] turns a Morse string into an immutable timeline of [Event]s using
 * International Morse timing:
 * - dot signal: 1 time unit, dash signal: 3 time units
 * - gap between elements inside one character: 1 unit
 * - gap between characters in one word: 3 units
 * - gap between words: 7 units — this *replaces* the character gap,
 *   it is never added on top of it
 * - one unit = 1200 / WPM milliseconds (PARIS standard)
 *
 * The sound player, torch player, vibration player, combined playback, UI
 * progress and the duration estimate all consume this plan. Nothing parses
 * the Morse string a second time with its own ad-hoc rules.
 *
 * Parsing policy (documented):
 * - Input is normalized with [MorseCode.normalizeMorse] first, so equivalent
 *   normalized representations always produce equivalent plans, and
 *   leading/trailing whitespace never changes the plan.
 * - Tokens that are not playable (unknown sequences or containing characters
 *   other than '.'/'-') are skipped: they cannot produce signals. Validation
 *   ([MorseCode.validateMorse]) reports them to the user separately.
 * - Malformed separators (e.g. "//") yield no events; they are a validation
 *   concern, not silent signals.
 * - The plan ends with the final signal; there is no trailing gap, and
 *   [durationMs] is exactly the sum of the planned events.
 */
object MorsePlan {

    sealed interface Event {
        /** Active signal: tone on, torch on, vibration on. */
        data class Signal(val durationMs: Long) : Event

        /** Silence between signals. */
        data class Gap(val durationMs: Long) : Event
    }

    fun parse(
        morse: String,
        wpm: Int,
        profile: MorseCode.AlphabetProfile = MorseCode.INTERNATIONAL
    ): List<Event> {
        val normalized = MorseCode.normalizeMorse(morse)
        if (normalized.isBlank()) return emptyList()
        val unit = MorseCode.unitDurationMs(wpm).toLong()
        val events = mutableListOf<Event>()

        fun isPlayable(token: String): Boolean =
            token.isNotEmpty() &&
                token.all { it == '.' || it == '-' } &&
                token in profile.morseToChar

        val words = normalized.split(Regex("\\s*/\\s*"))
        // For each word: does any later word contain playable content?
        // (Word gaps replace letter gaps and are never stacked.)
        val gapAfterWord = BooleanArray(words.size)
        var playableSeenAfter = false
        for (i in words.indices.reversed()) {
            val hasPlayable = words[i].trim().split(Regex("\\s+")).any(::isPlayable)
            gapAfterWord[i] = hasPlayable && playableSeenAfter
            if (hasPlayable) playableSeenAfter = true
        }

        words.forEachIndexed { wordIndex, word ->
            // Only playable tokens contribute; unknown ones are skipped
            // (reported separately by validation).
            val tokens = word.trim().split(Regex("\\s+")).filter(::isPlayable)
            tokens.forEachIndexed { tokenIndex, token ->
                token.forEachIndexed { symbolIndex, symbol ->
                    events.add(
                        Event.Signal(if (symbol == '.') unit else unit * 3)
                    )
                    if (symbolIndex < token.length - 1) {
                        events.add(Event.Gap(unit)) // intra-character gap
                    }
                }
                if (tokenIndex < tokens.size - 1) {
                    events.add(Event.Gap(unit * 3)) // letter gap
                }
            }
            if (gapAfterWord[wordIndex]) {
                events.add(Event.Gap(unit * 7)) // word gap (replaces letter gap)
            }
        }
        return events
    }

    /** Exact planned duration: the sum of all event durations. */
    fun durationMs(events: List<Event>): Long =
        events.sumOf {
            when (it) {
                is Event.Signal -> it.durationMs
                is Event.Gap -> it.durationMs
            }
        }

    /** Number of audible/visible signals in the plan. */
    fun signalCount(events: List<Event>): Int =
        events.count { it is Event.Signal }
}
