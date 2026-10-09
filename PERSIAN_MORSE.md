# Persian Morse Alphabet — Mapping, Sources and Status

## Status: documented community convention (NOT an official standard)

The Persian Morse table implemented in `PersianMorse.kt` is a **widely
documented de-facto convention**, not a formal standard:

- **ITU-R M.1677-1** (the international Morse recommendation) defines only the
  Latin A–Z / 0–9 / punctuation code. It defines **no** Persian or Arabic letters.
- No Iranian national standard or ITU document for Persian Morse was found
  (researched 2026-10-09).
- Two independent Wikipedia articles document the **same** table and agree on
  **all 32 letters**, the digits and the punctuation used here:
  - https://en.wikipedia.org/wiki/Morse_code_for_non-Latin_alphabets (section "Persian";
    codes read from the unambiguous `{{morse|dot|dash|…}}` templates)
  - https://fa.wikipedia.org/wiki/کد_مورس (section «کد مورس برای الفبای فارسی»)

Because the mapping is conventional rather than standardized, the app:
- keeps it in a **separate, clearly labeled profile** ("Persian (فارسی) — community convention"),
  never merged into the international table;
- states the provenance in the app where the profile is selected;
- does **not** claim ITU/official status anywhere in the UI or docs.

## The mapping

Most letters reuse the ITU code of a Latin letter (ب = B = `-...`). Five
letters have Persian-specific codes with no ITU equivalent: **چ** `---.`،
**ش** `----`، **ق** `...---`، **ص** `.-.-`، **غ** `..--`.

Decoding is unambiguous *inside* the Persian profile (all 32 codes are
unique). Across profiles it is inherently ambiguous (e.g. `.-` is `A`
internationally and `ا` in Persian) — which is why profiles are separate
encode/decode modes.

### Letters (both sources agree)

| حرف | کد | حرف | کد | حرف | کد | حرف | کد |
|---|---|---|---|---|---|---|---|
| ا | `.-` | د | `-..` | ض | `..-..` | ک | `-.-` |
| ب | `-...` | ذ | `...-` | ط | `..-` | گ | `--.-` |
| پ | `.--.` | ر | `.-.` | ظ | `-.--` | ل | `.-..` |
| ت | `-` | ز | `--..` | ع | `---` | م | `--` |
| ث | `-.-.` | ژ | `--.` | غ | `..--` | ن | `-.` |
| ج | `.---` | س | `...` | ف | `..-.` | و | `.--` |
| چ | `---.` | ش | `----` | ق | `...---` | ه | `.` |
| ح | `....` | ص | `.-.-` | ی | `..` | خ | `-..-` |

### Digits

Persian digits ۰–۹ use the **same codes as Latin digits**
(fa.wikipedia digits table; the table notes codes are read right-to-left,
i.e. the first transmitted element is the rightmost symbol shown):

`۰` → `-----`, `۱` → `.----`, `۲` → `..---`, `۳` → `...--`, `۴` → `....-`,
`۵` → `.....`, `۶` → `-....`, `۷` → `--...`, `۸` → `---..`, `۹` → `----.`

Arabic-Indic digits ٠–٩ are folded to the same codes.

### Punctuation (fa.wikipedia «نقطه‌گذاری» table)

| علامت | کد | علامت | کد |
|---|---|---|---|
| `.` (نقطه) | `......` | `!` | `--..--` |
| `،` | `.-.-.-` | `-` | `-....-` |
| `؛` | `-.-.-.` | `/` | `------` |
| `:` | `---...` | `ـ` (تطویل U+0640) | `..--.-` |
| `؟` | `..--..` | `(` | `-.--.` |
| | | `)` | `-.--.-` |

Notes:
- The fa.wikipedia row for `!` labels the name column «علامت سؤال»
  (question mark) while the sign column clearly shows `!`; the sign column
  was followed.
- One table row («سر سطر», newline) has an empty sign cell and was **not**
  included — the character could not be identified reliably.

## Normalization rules (Persian profile)

Applied in `PersianMorse.normalizePersianText`, in order:

1. Unicode NFC normalization.
2. Orthographic folds (each documented in `foldChar`):
   - Arabic Yeh `ي` (U+064A) → Persian Yeh `ی` (U+06CC)
   - Arabic Kaf `ك` (U+0643) → Persian Kaf `ک` (U+06A9)
   - Teh Marbuta `ة` → Heh `ه`; Alef Maksura `ى` → `ی`
   - `آ` `أ` `إ` → Alef `ا`; `ؤ` → `و`; `ئ` → `ی`
   - Persian digits ۰–۹ kept; Arabic-Indic digits ٠–٩ folded to ۰–۹
3. Line breaks become spaces; outer whitespace trimmed.
4. **Explicitly ignored** (no Morse representation; removed by documented
   policy, never reported as unsupported): ZWNJ (U+200C, نیم‌فاصله),
   Arabic combining marks U+064B–U+0655 (covers e.g. `نسخهٔ` with U+0654
   HAMZA ABOVE) and superscript alef U+0670.
   These do not change the readable message.

Anything else without a mapping is reported as an unsupported character
with its position — never silently dropped as if conversion succeeded.

## Known limitations

- Mixed Persian/Latin text: Latin letters are unsupported in the Persian
  profile (and vice versa). The UI reports their positions; switching
  profiles is the documented remedy. There is no auto-detection.
- No claim is made about transmission compatibility with any specific
  operator or organization using a different local convention.
