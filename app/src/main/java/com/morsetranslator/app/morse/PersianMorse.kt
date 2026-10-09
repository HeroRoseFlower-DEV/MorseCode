package com.morsetranslator.app.morse

import com.morsetranslator.app.R
import java.text.Normalizer

/**
 * Persian (Farsi) Morse alphabet profile.
 *
 * PROVENANCE — read before treating this as a standard:
 * This is a *documented community convention*, NOT an official ITU standard.
 * ITU-R M.1677-1 defines only the international Latin Morse code. Two
 * independent Wikipedia articles document the same Persian table and agree on
 * all 32 letters, the digits and the punctuation below:
 * - https://en.wikipedia.org/wiki/Morse_code_for_non-Latin_alphabets (Persian section)
 * - https://fa.wikipedia.org/wiki/کد_مورس (section «کد مورس برای الفبای فارسی»)
 * Neither article cites a primary source for the Persian table itself, and no
 * Iranian national standard or ITU document for Persian Morse was found
 * (researched 2026-10-09). Full provenance discussion: PERSIAN_MORSE.md.
 *
 * Most letters reuse ITU Latin codes (e.g. ب = B = -...); five letters have
 * Persian-specific codes with no ITU equivalent: چ (---.), ش (----),
 * ق (...---), ص (.-.-), غ (..--).
 *
 * The profile is kept strictly separate from [MorseCode.INTERNATIONAL]:
 * decoding is unambiguous inside each profile, but a code like ".-" means
 * 'A' in the international profile and 'ا' in the Persian profile.
 */
object PersianMorse {

    val PROFILE = MorseCode.AlphabetProfile(
        id = "persian",
        nameRes = R.string.profile_persian,
        charToMorse = linkedMapOf(
            // 32 Persian letters (both Wikipedia sources agree on all of them)
            'ا' to ".-", 'ب' to "-...", 'پ' to ".--.", 'ت' to "-",
            'ث' to "-.-.", 'ج' to ".---", 'چ' to "---.", 'ح' to "....",
            'خ' to "-..-", 'د' to "-..", 'ذ' to "...-", 'ر' to ".-.",
            'ز' to "--..", 'ژ' to "--.", 'س' to "...", 'ش' to "----",
            'ص' to ".-.-", 'ض' to "..-..", 'ط' to "..-", 'ظ' to "-.--",
            'ع' to "---", 'غ' to "..--", 'ف' to "..-.", 'ق' to "...---",
            'ک' to "-.-", 'گ' to "--.-", 'ل' to ".-..", 'م' to "--",
            'ن' to "-.", 'و' to ".--", 'ه' to ".", 'ی' to "..",
            // Digits: Persian ۰-۹ use the same codes as Latin digits
            // (fa.wikipedia digits table).
            '۰' to "-----", '۱' to ".----", '۲' to "..---", '۳' to "...--",
            '۴' to "....-", '۵' to ".....", '۶' to "-....", '۷' to "--...",
            '۸' to "---..", '۹' to "----.",
            // Persian punctuation (fa.wikipedia «نقطه‌گذاری» table).
            // Note: several codes intentionally mirror international ones
            // (؟ = ..--.. like '?'); all are unique inside this profile.
            '.' to "......",
            '،' to ".-.-.-",
            '؛' to "-.-.-.",
            ':' to "---...",
            '؟' to "..--..",
            '!' to "--..--",
            '-' to "-....-",
            '/' to "------",
            'ـ' to "..--.-", // U+0640 ARABIC TATWEEL (kashida)
            '(' to "-.--.",
            ')' to "-.--.-"
        ),
        // Characters with no Morse representation. Skipped by explicit,
        // documented policy (they do not change the readable message);
        // they are NOT reported as unsupported.
        ignoredChars = buildSet {
            add('‌') // U+200C ZERO WIDTH NON-JOINER (نیم‌فاصله)
            // Arabic combining marks U+064B–U+0655 (fathatan … hamza below;
            // covers e.g. نسخهٔ with U+0654 HAMZA ABOVE) and U+0670 superscript alef
            for (c in 'ً'..'ٕ') add(c)
            add('ٰ')
        }
    )

    /** The 32 Persian letters, in alphabet order (for practice/learn). */
    val LETTERS: List<Char> = listOf(
        'ا', 'ب', 'پ', 'ت', 'ث', 'ج', 'چ', 'ح',
        'خ', 'د', 'ذ', 'ر', 'ز', 'ژ', 'س', 'ش',
        'ص', 'ض', 'ط', 'ظ', 'ع', 'غ', 'ف', 'ق',
        'ک', 'گ', 'ل', 'م', 'ن', 'و', 'ه', 'ی'
    )

    /**
     * Documented orthographic folds applied before lookup. Each maps a
     * compatibility/code-point variant to the canonical Persian form used as
     * a table key. Applied symmetrically wherever Persian text is normalized.
     */
    fun foldChar(c: Char): Char = when (c) {
        'ي' -> 'ی' // U+064A ARABIC YEH → U+06CC FARSI YEH
        'ك' -> 'ک' // U+0643 ARABIC KAF → U+06A9 KEHEH
        'ة' -> 'ه' // TEH MARBUTA → HEH
        'ى' -> 'ی' // ALEF MAKSURA → FARSI YEH
        'آ' -> 'ا' // ALEF WITH MADDA → ALEF
        'أ' -> 'ا' // ALEF WITH HAMZA ABOVE → ALEF
        'إ' -> 'ا' // ALEF WITH HAMZA BELOW → ALEF
        'ؤ' -> 'و' // WAW WITH HAMZA → WAW
        'ئ' -> 'ی' // YEH WITH HAMZA → FARSI YEH
        // Persian (U+06F0–06F9) and Arabic-Indic (U+0660–0669) digits → Persian digits
        in '۰'..'۹' -> c
        in '٠'..'٩' -> ('۰' + (c - '٠'))
        else -> c
    }

    /**
     * Normalizes Persian plain text. Rules:
     * 1. Unicode NFC normalization.
     * 2. [foldChar] applied to every character.
     * 3. Line breaks become spaces; outer whitespace trimmed.
     * 4. No case conversion (Persian has no case).
     *
     * Characters in [MorseCode.AlphabetProfile.ignoredChars] are removed
     * here by explicit policy (see [PROFILE]); anything else without a
     * mapping is reported by validation, never silently dropped as "handled".
     */
    fun normalizePersianText(text: String): String {
        var out = Normalizer.normalize(text, Normalizer.Form.NFC)
        out = buildString(out.length) {
            for (c in out) {
                val folded = foldChar(c)
                if (folded !in PROFILE.ignoredChars) append(folded)
            }
        }
        out = out.replace(Regex("[\\r\\n]+"), " ")
        return out.trim()
    }
}
