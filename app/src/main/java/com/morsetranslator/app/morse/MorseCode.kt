package com.morsetranslator.app.morse

import java.text.Normalizer
import java.util.Locale

/**
 * Canonical Morse domain model.
 *
 * Responsibilities are separated so every layer (translation UI, validation,
 * duration estimate, playback, tests) shares one correct representation:
 *
 * - [AlphabetProfile] — a named, self-contained letter→morse table. The
 *   international profile is always available; other profiles (e.g. Persian)
 *   are added only with a verified, documented mapping.
 * - [normalizeText] / [normalizeMorse] — the single documented normalization
 *   path for each input kind. Plain text and Morse input follow *separate*
 *   explicit rules so a hyphen in text is never confused with a Morse dash.
 * - [encodeDetailed] / [decodeDetailed] — conversion that *reports*
 *   unsupported characters/tokens instead of silently dropping them.
 * - [playbackMorse] — the single correct Morse sequence to play for a given
 *   UI state. In text→Morse mode this is the encoded output; in Morse→text
 *   mode it is the normalized Morse sequence from the user's input. A decoded
 *   text string (e.g. "SOS") is never sent to the signal player.
 *
 * Encoding format: letters separated by one space, words by " / ".
 */
object MorseCode {

    // ------------------------------------------------------- alphabet model

    /**
     * A named Morse alphabet. Tables are kept separate per profile so the
     * international A–Z table can never become ambiguous with another script.
     */
    data class AlphabetProfile(
        /** Stable id, persisted in settings (e.g. "international"). */
        val id: String,
        /** Short display name, resolved to a localized string by the UI. */
        val nameRes: Int,
        val charToMorse: Map<Char, String>,
        /**
         * Characters with no Morse representation that the profile
         * explicitly ignores during normalization (documented per profile).
         * They are never reported as unsupported.
         */
        val ignoredChars: Set<Char> = emptySet()
    ) {
        val morseToChar: Map<String, Char> by lazy {
            charToMorse.entries.associate { (char, morse) -> morse to char }
        }

        /** All characters this profile can encode. */
        val supportedChars: Set<Char> get() = charToMorse.keys
    }

    /** International Morse (ITU): A–Z, 0–9 and common punctuation. */
    val INTERNATIONAL = AlphabetProfile(
        id = "international",
        nameRes = com.morsetranslator.app.R.string.profile_international,
        charToMorse = linkedMapOf(
            'A' to ".-", 'B' to "-...", 'C' to "-.-.", 'D' to "-..", 'E' to ".",
            'F' to "..-.", 'G' to "--.", 'H' to "....", 'I' to "..", 'J' to ".---",
            'K' to "-.-", 'L' to ".-..", 'M' to "--", 'N' to "-.", 'O' to "---",
            'P' to ".--.", 'Q' to "--.-", 'R' to ".-.", 'S' to "...", 'T' to "-",
            'U' to "..-", 'V' to "...-", 'W' to ".--", 'X' to "-..-", 'Y' to "-.--",
            'Z' to "--..",
            '0' to "-----", '1' to ".----", '2' to "..---", '3' to "...--",
            '4' to "....-", '5' to ".....", '6' to "-....", '7' to "--...",
            '8' to "---..", '9' to "----.",
            '.' to ".-.-.-", ',' to "--..--", '?' to "..--..", '\'' to ".----.",
            '!' to "-.-.--", '/' to "-..-.", '(' to "-.--.", ')' to "-.--.-",
            '&' to ".-...", ':' to "---...", ';' to "-.-.-.", '=' to "-...-",
            '+' to ".-.-.", '-' to "-....-", '_' to "..--.-", '"' to ".-..-.",
            '$' to "...-..-", '@' to ".--.-."
        )
    )

    /**
     * Profiles selectable in Settings, in display order. Additional profiles
     * are appended here only with a verified, documented mapping —
     * see PERSIAN_MORSE.md for the Persian profile's source and status.
     */
    val PROFILES: List<AlphabetProfile> by lazy {
        listOf(INTERNATIONAL, PersianMorse.PROFILE)
    }

    fun profileById(id: String): AlphabetProfile =
        PROFILES.firstOrNull { it.id == id } ?: INTERNATIONAL

    // ------------------------------------------------------- compatibility

    /** International table, kept for compact call sites. */
    val CHAR_TO_MORSE: Map<Char, String> get() = INTERNATIONAL.charToMorse

    /** Reverse international table, kept for compact call sites. */
    val MORSE_TO_CHAR: Map<String, Char> get() = INTERNATIONAL.morseToChar

    /** Encode with the international profile; unknown characters are skipped. */
    fun encode(text: String): String = encodeDetailed(text, INTERNATIONAL).morse

    /** Decode with the international profile; unknown sequences become '�'. */
    fun decode(morse: String): String = decodeDetailed(morse, INTERNATIONAL).text

    // ------------------------------------------------------- normalization

    /**
     * Normalizes *Morse* input. Rules (applied in order):
     * 1. Compatible dot glyphs (· • ‧) become '.'; compatible dash glyphs
     *    (− – — ― ‐) become '-'. This only affects Morse input, never text.
     * 2. Leading/trailing whitespace is trimmed.
     * 3. Any run of whitespace (spaces, tabs, newlines) collapses to one space.
     * 4. Word separators are canonicalized: any slash with surrounding
     *    whitespace becomes exactly " / ".
     * 5. Whitespace is collapsed again so separators are never doubled.
     */
    fun normalizeMorse(morse: String): String {
        return morse
            .replace('·', '.')
            .replace('•', '.')
            .replace('‧', '.')
            .replace('−', '-')
            .replace('–', '-')
            .replace('—', '-')
            .replace('―', '-')
            .replace('‐', '-')
            .trim()
            .replace(Regex("\\s+"), " ")
            .replace(Regex("\\s*/\\s*"), " / ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /**
     * Normalizes *plain text* for the international profile. Rules:
     * 1. Unicode NFC normalization.
     * 2. Line breaks become spaces (multi-line input stays one message).
     * 3. Outer whitespace is trimmed.
     * 4. Latin letters are uppercased with [Locale.ROOT] (never the device
     *    locale, so e.g. Turkish dotted-I cannot change the mapping).
     *
     * Nothing is deleted: characters without a mapping are reported by
     * [validateText]/[encodeDetailed], never silently dropped as "handled".
     */
    fun normalizeText(text: String, profile: AlphabetProfile = INTERNATIONAL): String {
        if (profile.id == PersianMorse.PROFILE.id) {
            return PersianMorse.normalizePersianText(text)
        }
        var out = Normalizer.normalize(text, Normalizer.Form.NFC)
        out = out.replace(Regex("[\\r\\n]+"), " ")
        out = out.trim()
        // Locale.ROOT: e.g. Turkish dotted-I must not change the mapping.
        return out.uppercase(Locale.ROOT)
    }

    /** True if the string only contains Morse symbols, slashes and whitespace. */
    fun isValidMorse(morse: String): Boolean =
        morse.all { it == '.' || it == '-' || it == '/' || it.isWhitespace() }

    // ------------------------------------------------------- validation

    /** A character that cannot be encoded, with its offset in normalized text. */
    data class UnsupportedChar(val char: Char, val position: Int)

    /** A Morse token with no mapping, with its 0-based token index. */
    data class UnknownToken(val token: String, val index: Int)

    data class TextValidation(val unsupported: List<UnsupportedChar>) {
        val isOk: Boolean get() = unsupported.isEmpty()
    }

    data class MorseValidation(
        val unknownTokens: List<UnknownToken>,
        /** True when separators are malformed (e.g. "//", leading/trailing "/"). */
        val malformedSeparators: Boolean
    ) {
        val isOk: Boolean get() = unknownTokens.isEmpty() && !malformedSeparators
    }

    /** Reports every character [encodeDetailed] would have to skip. */
    fun validateText(text: String, profile: AlphabetProfile = INTERNATIONAL): TextValidation {
        val normalized = normalizeText(text, profile)
        if (normalized.isBlank()) return TextValidation(emptyList())
        val bad = mutableListOf<UnsupportedChar>()
        normalized.forEachIndexed { i, c ->
            if (!c.isWhitespace() && c !in profile.charToMorse && c !in profile.ignoredChars) {
                bad.add(UnsupportedChar(c, i))
            }
        }
        return TextValidation(bad)
    }

    /** Reports unknown tokens and malformed separator structures. */
    fun validateMorse(morse: String, profile: AlphabetProfile = INTERNATIONAL): MorseValidation {
        val normalized = normalizeMorse(morse)
        if (normalized.isBlank()) return MorseValidation(emptyList(), false)
        val unknown = mutableListOf<UnknownToken>()
        var malformed = false
        var tokenIndex = 0
        for (word in normalized.split(Regex("\\s*/\\s*"))) {
            val tokens = word.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            if (tokens.isEmpty()) {
                malformed = true // e.g. "//" or a "/" with no valid token
                continue
            }
            for (token in tokens) {
                if (token !in profile.morseToChar) {
                    unknown.add(UnknownToken(token, tokenIndex))
                }
                tokenIndex++
            }
        }
        return MorseValidation(unknown, malformed)
    }

    // ------------------------------------------------------- conversion

    /**
     * Explicit conversion policy: letters joined with one space, words with
     * " / ". Words that become empty (all characters unsupported) are
     * skipped so no phantom separators appear. Blank input yields an empty
     * result — never stray separators. Unsupported characters are listed in
     * [EncodeResult.unsupported] with positions; the UI must surface them
     * instead of presenting a partial conversion as complete success.
     */
    data class EncodeResult(val morse: String, val unsupported: List<UnsupportedChar>) {
        val isComplete: Boolean get() = unsupported.isEmpty()
    }

    fun encodeDetailed(
        text: String,
        profile: AlphabetProfile = INTERNATIONAL
    ): EncodeResult {
        val normalized = normalizeText(text, profile)
        if (normalized.isBlank()) return EncodeResult("", emptyList())
        val unsupported = mutableListOf<UnsupportedChar>()
        val words = normalized.split(Regex("\\s+")).filter { it.isNotEmpty() }
        val encodedWords = mutableListOf<String>()
        var offset = 0
        for (word in words) {
            val codes = mutableListOf<String>()
            word.forEachIndexed { i, c ->
                val code = profile.charToMorse[c]
                if (code != null) codes.add(code)
                else unsupported.add(UnsupportedChar(c, offset + i))
            }
            if (codes.isNotEmpty()) encodedWords.add(codes.joinToString(" "))
            offset += word.length + 1
        }
        return EncodeResult(encodedWords.joinToString(" / "), unsupported)
    }

    /**
     * Best-effort decoding: unknown tokens become '�' and are reported;
     * empty words from malformed separators are skipped. Blank input yields
     * an empty result.
     */
    data class DecodeResult(
        val text: String,
        val unknownTokens: List<UnknownToken>,
        val malformedSeparators: Boolean
    ) {
        val isComplete: Boolean get() = unknownTokens.isEmpty() && !malformedSeparators
    }

    fun decodeDetailed(
        morse: String,
        profile: AlphabetProfile = INTERNATIONAL
    ): DecodeResult {
        val normalized = normalizeMorse(morse)
        if (normalized.isBlank()) return DecodeResult("", emptyList(), false)
        val unknown = mutableListOf<UnknownToken>()
        var malformed = false
        var tokenIndex = 0
        val words = mutableListOf<String>()
        for (word in normalized.split(Regex("\\s*/\\s*"))) {
            val tokens = word.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            if (tokens.isEmpty()) {
                malformed = true
                continue
            }
            val sb = StringBuilder()
            for (token in tokens) {
                val char = profile.morseToChar[token]
                if (char != null) sb.append(char)
                else {
                    sb.append('�')
                    unknown.add(UnknownToken(token, tokenIndex))
                }
                tokenIndex++
            }
            words.add(sb.toString())
        }
        return DecodeResult(words.joinToString(" "), unknown, malformed)
    }

    // ------------------------------------------------------- playback input

    /**
     * The single correct Morse sequence to play for a UI state.
     * - text→Morse mode: the encoded output (already normalized by encoding).
     * - Morse→text mode: the valid, normalized Morse sequence from the input.
     *
     * A decoded text string is never returned here — sending text such as
     * "SOS" to the signal player would produce silence, not signals.
     */
    fun playbackMorse(
        textToMorse: Boolean,
        input: String,
        encodedOutput: String,
        profile: AlphabetProfile = INTERNATIONAL
    ): String {
        return if (textToMorse) {
            encodedOutput.trim()
        } else {
            normalizeMorse(input)
        }
    }

    // ------------------------------------------------------- timing helpers

    /** Duration of one time unit in ms at the given WPM (PARIS standard). */
    fun unitDurationMs(wpm: Int): Int = (1200 / wpm.coerceIn(5, 60)).coerceAtLeast(20)

    /** Kept for compatibility; prefer [unitDurationMs]. */
    fun ditDurationMs(wpm: Int): Int = unitDurationMs(wpm)

    /** Built-in quick phrases for one-tap sending. */
    val PRESETS: Map<String, String> = linkedMapOf(
        "SOS" to "SOS",
        "HELP" to "HELP",
        "YES" to "YES",
        "NO" to "NO",
        "OK" to "OK",
        "I LOVE YOU" to "I LOVE YOU",
        "GOOD LUCK" to "GOOD LUCK"
    )

    /**
     * Estimated playback duration, derived from the same [MorsePlan] the
     * player consumes — never a separate approximate parser.
     */
    fun estimatedDurationMs(
        morse: String,
        wpm: Int,
        profile: AlphabetProfile = INTERNATIONAL
    ): Long = MorsePlan.durationMs(MorsePlan.parse(morse, wpm, profile))

    /**
     * Unambiguous human-readable duration, e.g. "8s", "1m 05s", "2h 03m 07s".
     * Seconds are never omitted.
     */
    fun formatDuration(ms: Long): String {
        val totalSeconds = (ms + 500) / 1000
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        return when {
            h > 0 -> "%dh %02dm %02ds".format(h, m, s)
            m > 0 -> "%dm %02ds".format(m, s)
            else -> "%ds".format(s)
        }
    }

    /** NATO phonetic alphabet name for a letter, or null for digits/punctuation. */
    fun describe(char: Char): String? = NATO[char.uppercaseChar()]

    private val NATO = mapOf(
        'A' to "Alpha", 'B' to "Bravo", 'C' to "Charlie", 'D' to "Delta",
        'E' to "Echo", 'F' to "Foxtrot", 'G' to "Golf", 'H' to "Hotel",
        'I' to "India", 'J' to "Juliett", 'K' to "Kilo", 'L' to "Lima",
        'M' to "Mike", 'N' to "November", 'O' to "Oscar", 'P' to "Papa",
        'Q' to "Quebec", 'R' to "Romeo", 'S' to "Sierra", 'T' to "Tango",
        'U' to "Uniform", 'V' to "Victor", 'W' to "Whiskey", 'X' to "X-ray",
        'Y' to "Yankee", 'Z' to "Zulu"
    )
}
