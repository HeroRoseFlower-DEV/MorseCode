# Morse Translator

A calm, fully offline Android Morse code translator built with Kotlin and Jetpack
Compose (Material 3). No accounts, no ads, no analytics, no network — the app does
not even request the `INTERNET` permission.

## Features

- **Bidirectional translation** — Text ⇄ Morse, converted live as you type, with live
  stats (characters, words, estimated transmission time)
- **Validated conversion** — unsupported characters and unknown morse sequences are
  reported with positions instead of being silently dropped
- **Signal-accurate playback** — sound (streaming sine-wave audio), flashlight and
  vibration all follow one shared, standards-based timing model (dot 1 / dash 3 /
  letter gap 3 / word gap 7), individually or combined, with live progress
- **Alphabet profiles** — International (ITU) Morse and Persian Morse (32 letters,
  documented community convention — see [PERSIAN_MORSE.md](PERSIAN_MORSE.md)),
  switchable everywhere: translator, practice, learn and history
- **Audio decoder** — optional microphone listening that detects morse tones and
  decodes them live, 100% on-device (see *Limitations* below)
- **Practice mode** — *Listen* and *Tap it* quizzes with score, streak, best score,
  adaptive spaced-repetition review and a personal progress summary
- **Learn screen** — searchable reference chart for the selected alphabet with
  audio preview
- **Daily message** — a bundled motivational message (Persian/English), playable as
  morse, dismissible per day
- **History** — last 100 translations with search, favorites, tap-to-reload and
  plain-text export
- **Calm light-first design** — warm ivory light theme and a matching dark theme,
  full RTL support, bilingual UI (English / فارسی)

See [CHANGELOG.md](CHANGELOG.md) for version history and
[PRIVACY_POLICY.md](PRIVACY_POLICY.md) for the privacy policy.

## Getting the APK (no Android Studio needed)

1. Push this project to your GitHub repository.
2. Open the **Actions** tab — the `Build APK` workflow runs automatically on every
   push to `main`. It runs **unit tests**, **Android lint**, compiles the
   instrumented UI tests, then builds the APK.
3. Download the `morse-translator-2.0-debug-apk` artifact and install the APK on
   your phone (you may need to allow "Install unknown apps" for your browser).

> This is an **unsigned debug APK** built by CI for testing — it is not a signed
> release and not an AAB. For Play Store distribution, build a signed release /
> AAB (see *Release builds* below).

## Release builds

1. Generate a release keystore (keep it private and back it up):
   ```bash
   keytool -genkeypair -v -keystore release.keystore -alias morse \
     -keyalg RSA -keysize 2048 -validity 10000
   ```
2. Add a `signingConfigs` block to `app/build.gradle.kts` referencing the keystore
   (never commit the keystore or its passwords; use environment variables or CI
   secrets).
3. Build the bundle Google Play wants:
   ```bash
   ./gradlew :app:bundleRelease --no-daemon
   # AAB: app/build/outputs/bundle/release/app-release.aab
   ```

The app targets **API 36 (Android 16)** — required by Google Play for app updates
since 2026-08-31. `applicationId` is `com.morsetranslator.app`, `minSdk` is 26.

## Building locally

Requirements: JDK 17, Android SDK with **API 36** (`platforms;android-36`,
`build-tools;36.0.0`). The project ships a Gradle wrapper (Gradle 8.10.2), so no
separate Gradle install is needed:

```bash
# Point Gradle at your SDK (or set ANDROID_HOME / ANDROID_SDK_ROOT)
echo "sdk.dir=/path/to/Android/Sdk" > local.properties

./gradlew :app:testDebugUnitTest      # unit tests
./gradlew :app:lintDebug              # Android lint
./gradlew :app:assembleAndroidTest    # compile instrumented UI tests (need an emulator to run)
./gradlew :app:assembleDebug          # APK: app/build/outputs/apk/debug/app-debug.apk
```

## Limitations (honest notes)

- **Audio decoder** is energy-based: it detects tone bursts and estimates timing
  from them. It works best with clean, steady tones in a quiet room. Background
  noise, music, echo or unsteady keying degrade accuracy; the UI marks live
  results as provisional. Verify anything important by ear.
- **Persian Morse** is a documented community convention, not an official ITU
  standard (see [PERSIAN_MORSE.md](PERSIAN_MORSE.md)).
- **Backup is off**: history, settings and progress stay on the device and are
  deleted if the app is uninstalled.
- Flashlight, vibration and microphone behavior were not all verified on physical
  hardware in every release — see the release notes in [CHANGELOG.md](CHANGELOG.md).

## Project structure

```
app/src/main/java/com/morsetranslator/app/
├── morse/          # Canonical domain: MorseCode (profiles, validation),
│                   #   MorsePlan (signal timeline), MorsePlayer, PersianMorse
├── audio/          # Offline energy-based decoder (AudioDecoder) + MicRecorder
├── daily/          # Bundled daily messages
├── learn/          # Spaced-repetition review scheduler (pure logic)
├── data/           # SettingsRepository (DataStore): settings, history,
│                   #   review stats, dismissal dates
└── ui/             # Compose UI: calm design system, 5-tab navigation,
                    #   screens (translate, decode, practice, learn, history,
                    #   settings)
app/src/test/       # JVM unit tests (conversion, timing, decoder, review,
                    #   daily, settings/history)
app/src/androidTest/ # Instrumented Compose UI tests (need an emulator to run)
```
