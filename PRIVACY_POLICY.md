# Privacy Policy — Morse Translator

Last updated: 2026-10-09 (version 2.0)

## Summary

Morse Translator works **fully offline**. We do not collect, transmit, store on servers,
sell, or share any of your personal data — because the app never sends anything anywhere.
There is no `INTERNET` permission in the app at all.

## What the app does with your data

- **Translations and history** are stored only on your device (in the app's private
  storage). They are never uploaded.
- **Settings** (speed, tone, theme, alphabet profile, decoder sensitivity, practice best
  score) are stored only on your device.
- **Practice and review statistics** are computed and stored only on your device. The
  adaptive review never contacts a server.
- **Camera / flashlight** permission is used solely to blink morse signals when you tap
  the flashlight playback button. No images are captured, processed, or stored.
- **Vibration** is used solely for vibration playback of morse signals.
- **Microphone** (new in 2.0) is used solely by the optional audio decoder: when *you*
  press "Start listening", the app analyzes microphone audio **on the device** to detect
  morse tones. Audio is processed in small chunks in memory and is never recorded to a
  file, never uploaded, never logged, and never leaves the device.

## Backups

Android backup is **disabled** for this app (`allowBackup="false"` plus explicit data
extraction rules): your history, settings and progress are excluded from cloud backup
and device-to-device transfer. Uninstalling the app deletes them.

## What we do NOT do

- No accounts, no sign-in, no analytics, no advertising SDKs, no trackers.
- No network requests of any kind are made by the app.
- We never log or store the text you translate or the audio you decode.

## Permissions requested

| Permission | Why |
|---|---|
| `CAMERA` | Blink morse code with the camera flash (only when you press the flash button; runtime permission, you can deny it) |
| `VIBRATE` | Vibrate morse code patterns (normal permission, no prompt) |
| `RECORD_AUDIO` | Decode morse from microphone audio (only when you start the decoder; runtime permission, you can deny it; audio never leaves the device) |

## Contact

If you have privacy questions about this app, open an issue on the GitHub repository.
