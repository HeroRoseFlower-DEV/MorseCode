package com.morsetranslator.app.morse

/**
 * International Morse code tables and conversion helpers.
 *
 * Encoding: letters are separated by a single space, words by " / ".
 * Decoding accepts the same format and is tolerant of extra whitespace.
 */
object MorseCode {

    val CHAR_TO_MORSE: Map<Char, String> = linkedMapOf(
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

    val MORSE_TO_CHAR: Map<String, Char> =
        CHAR_TO_MORSE.entries.associate { (char, morse) -> morse to char }

    /** Encode plain text to morse code. Unknown characters are skipped. */
    fun encode(text: String): String {
        val words = text.uppercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        return words.joinToString(" / ") { word ->
            word.mapNotNull { CHAR_TO_MORSE[it] }.joinToString(" ")
        }
    }

    /** Decode morse code to text. Unknown sequences become '�'. */
    fun decode(morse: String): String {
        val normalized = normalize(morse)
        if (normalized.isBlank()) return ""
        return normalized.trim().split(Regex("\\s*/\\s*")).joinToString(" ") { word ->
            word.trim().split(Regex("\\s+"))
                .filter { it.isNotEmpty() }
                .map { MORSE_TO_CHAR[it] ?: '�' }
                .joinToString("")
        }
    }

    /** Replace fancy unicode dots/dashes with their ASCII equivalents. */
    fun normalize(morse: String): String {
        return morse
            .replace('·', '.')
            .replace('•', '.')
            .replace('−', '-')
            .replace('–', '-')
            .replace('—', '-')
    }

    /** True if the string only contains morse symbols, slashes and whitespace. */
    fun isValidMorse(morse: String): Boolean =
        morse.all { it == '.' || it == '-' || it == '/' || it.isWhitespace() }

    /** Duration of one "dit" in milliseconds for the given speed (PARIS standard). */
    fun ditDurationMs(wpm: Int): Int = (1200 / wpm.coerceIn(5, 60)).coerceAtLeast(20)

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
     * Estimated playback duration of a morse string at the given speed,
     * following standard timing (dit=1, dah=3, letter gap=3, word gap=7).
     */
    fun estimatedDurationMs(morse: String, wpm: Int): Long {
        val dit = ditDurationMs(wpm).toLong()
        var total = 0L
        for (c in morse) {
            total += when (c) {
                '.' -> dit * 2      // tone + intra-letter gap
                '-' -> dit * 4      // 3x tone + intra-letter gap
                ' ' -> dit * 2      // +1 dit already counted = letter gap (3)
                '/' -> dit * 6      // +1 dit already counted = word gap (7)
                else -> 0L
            }
        }
        return total
    }

    /** Human-readable duration, e.g. "8s" or "1m 05s". */
    fun formatDuration(ms: Long): String {
        val s = (ms + 500) / 1000
        return if (s < 60) "${s}s" else "${s / 60}m %02d".format(s % 60)
    }
}
