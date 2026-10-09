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
}
