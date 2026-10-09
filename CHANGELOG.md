# Changelog

## 2.0 (2026-10-09)

### Conversion engine
- Canonical Morse domain model with separate **alphabet profiles**: International
  (ITU) and Persian (32 letters; documented community convention, not an ITU
  standard — see PERSIAN_MORSE.md)
- Explicit normalization paths for text vs. morse input (unicode dot/dash variants,
  Persian orthographic folds, combining-mark policy)
- `encodeDetailed`/`decodeDetailed` **report** unsupported characters and unknown
  tokens with positions instead of silently dropping them
- One shared **MorsePlan** signal timeline (dot 1 / dash 3 / letter gap 3 /
  word gap 7); durations are derived from the plan, never approximated twice

### Playback
- Rewritten player: streaming sine-wave audio (no more pre-rendered buffers),
  camera-flash torch and vibration all follow the shared plan, individually or
  combined, with live progress and per-output failure reporting
- Explicit preflight checks (flash hardware/permission, vibrator presence,
  vibration-length limit) with honest, actionable messages

### Audio decoder (new)
- Optional microphone decoding, 100% on-device: energy-based tone detection,
  adaptive unit/WPM estimation, incremental results, sensitivity control
- Runtime microphone permission with rationale; audio is never stored, logged
  or uploaded. Accuracy is best with clean tones in quiet rooms — live results
  are marked provisional

### Learning
- Practice quizzes now drive an **adaptive spaced-repetition review**:
  missed characters return sooner, known ones wait longer; per-character
  progress summary with accuracy, weakest characters and reset
- Learn screen shows the **selected profile's** reference table (searchable,
  tap-to-hear)
- **Daily message**: 30 bundled Persian/English motivational messages, playable
  as morse, dismissible per day

### Design
- Calm light-first redesign replacing the animated Liquid Glass system: warm
  ivory light theme, matching dark theme, static surfaces, reduced motion
- Translation UX rebuilt around a clear hierarchy (explanation → direction →
  input → validated output → playback); RTL/LTR isolation for morse text
- Fifth tab: decoder. Settings show the real build version and backup policy

### Privacy & data
- Still fully offline; **no `INTERNET` permission**
- Android backup **disabled** (`allowBackup="false"` + data extraction rules):
  history, settings and progress stay on-device and are deleted on uninstall
- Settings/history hardened: clamped ranges, profile-aware history (100),
  full-operation deduplication, defensive parsing, plain-text export,
  data-management controls

### Platform
- **Targets API 36 (Android 16)** as required by Google Play for app updates
  (AGP 8.10.1, Kotlin 2.0.20, Gradle 8.10.2 via checked-in wrapper; minSdk 26)
- CI now runs **unit tests + Android lint**, compiles instrumented UI tests,
  then builds a versioned **debug** APK artifact (`morse-translator-2.0-debug-apk`)

### Verification notes
- Unit tests (conversion, timing, decoder, review, daily, settings/history)
  run green in CI. Instrumented Compose UI tests compile in CI (need an
  emulator to execute).
- Flashlight, vibration and microphone behavior were not verified on physical
  hardware for this release.

## 1.2 (2026-10-09)

### Liquid Glass redesign
- Complete UI overhaul in the "liquid glass" style: animated aurora gradient backdrop,
  frosted-glass cards with light refraction, floating glass bottom navigation and
  gradient call-to-action buttons
- Edge-to-edge layout with transparent system bars
- Glass segmented controls, chips and icon buttons across Translate, Practice, Learn,
  History and Settings screens

## 1.1 (2026-10-09)

### New features
- **Practice mode**: interactive morse quiz with two games — *Listen* (hear the code, pick the character)
  and *Tap it* (tap the code for the shown character), with score, streak and persistent best score
- **Quick phrases**: one-tap preset messages (SOS, HELP, YES, NO, OK, I LOVE YOU, GOOD LUCK)
- **Combined playback**: play sound, flashlight and vibration simultaneously
- **Live translation stats**: character/word count and estimated transmission time
- **WPM presets**: Slow (10), Normal (18) and Fast (30) shortcuts next to the speed slider
- **History upgrades**: full-text search and star/unstar favorites

### Improvements
- History capacity increased from 50 to 100 entries
- Settings screen now shows version 1.1

## 1.0 (2026-10-09)

- Initial release: bidirectional Text ⇄ Morse translation with live preview
- Sound, flashlight and vibration playback with adjustable speed (5–40 WPM) and tone (300–1200 Hz)
- Hold-to-input tap pad plus dot/dash/space helper buttons
- Searchable morse reference chart with per-character audio preview
- Translation history (tap to reload, delete, clear-all with confirmation)
- Material 3 dynamic theming, light/dark/system modes, full RTL support
- Bilingual UI: English and Persian (فارسی)
- GitHub Actions workflow building a debug APK on every push
