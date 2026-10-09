# Morse Translator

A minimal, modern Android Morse code translator built with Kotlin and Jetpack Compose (Material 3).

## Features

- **Bidirectional translation** — Text ⇄ Morse code, translated live as you type
- **Sound playback** — precise sine-wave audio with adjustable speed (5–40 WPM) and tone frequency (300–1200 Hz)
- **Flashlight signals** — blink morse via the camera flash (runtime permission handled)
- **Vibration playback** — feel the code with accurate timing
- **Tap pad** — hold-to-input: quick tap = dot, long hold = dash, plus dot/dash/space buttons
- **Learn screen** — searchable reference chart of every character with audio preview
- **History** — last 50 translations, tap to reload, swipe-free delete, clear-all with confirmation
- **Theming** — Material 3 dynamic colors, light / dark / system modes, full RTL support
- **Bilingual UI** — English and Persian (فارسی)

## Getting the APK (no Android Studio needed)

1. Create a new GitHub repository and push this project to it.
2. Open the **Actions** tab — the `Build APK` workflow runs automatically on every push to `main`.
3. When it finishes, download the `morse-translator-apk` artifact and install `app-debug.apk` on your phone
   (you may need to allow "Install unknown apps" for your browser).

> The APK is signed with the debug key, which is fine for personal use. For Play Store
> distribution, generate a release key and configure `signingConfigs` in `app/build.gradle.kts`.

## Building locally

Requirements: JDK 17, Android SDK (API 34), Gradle 8.10+.

```bash
# Point Gradle at your SDK (or set ANDROID_HOME / ANDROID_SDK_ROOT)
echo "sdk.dir=/path/to/Android/Sdk" > local.properties

gradle :app:assembleDebug --no-daemon
# APK: app/build/outputs/apk/debug/app-debug.apk
```

## Project structure

```
app/src/main/java/com/morsetranslator/app/
├── MainActivity.kt            # entry point, theme wiring
├── morse/
│   ├── MorseCode.kt           # ITU tables, encode/decode, timing math
│   └── MorsePlayer.kt         # sound (AudioTrack) / flashlight / vibration playback
├── data/
│   └── SettingsRepository.kt  # DataStore: WPM, tone, theme, history
└── ui/
    ├── AppNav.kt              # bottom navigation + top bar
    ├── theme/Theme.kt         # Material 3 dynamic theme
    └── screens/
        ├── TranslateScreen.kt # translator + playback + tap pad
        ├── LearnScreen.kt      # searchable reference chart
        ├── HistoryScreen.kt    # translation history
        └── SettingsScreen.kt   # appearance + playback settings
```

## Tech stack

- Kotlin 2.0, Jetpack Compose (BOM 2024.10.00), Material 3
- Navigation Compose, DataStore Preferences, Lifecycle Runtime Compose
- Min SDK 26 · Target/Compile SDK 34 · AGP 8.5.2

## License

MIT — see [LICENSE](LICENSE).
