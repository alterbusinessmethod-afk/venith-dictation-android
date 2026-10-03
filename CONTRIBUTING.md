# Contributing

Thanks for helping improve Venith Dictation and its credited VoiceFlow base.

This project is intended to be a practical open-source base for Android voice dictation keyboards. Keep changes understandable, privacy-conscious, and useful for people who want to build or test the app themselves.

## Good First Contributions

- Device compatibility reports.
- Setup documentation fixes.
- Tests for key-storage recovery and Android input-method lifecycle behavior.
- Better custom dictionary and replacement UI.
- Realtime transcription experiments.
- Keyboard layout fixes for different screen sizes.
- Accessibility improvements.

## Development Setup

Requirements:

- JDK 17
- Android SDK platform 35 / build-tools 35.0.0, or Android Studio
- Python 3 for the pinned model download helper

Build:

```bash
python3 scripts/prepare_model.py
./gradlew --no-daemon :app:testDebugUnitTest :app:assembleDebug
```

On Windows:

```powershell
python scripts/prepare_model.py
.\gradlew.bat --no-daemon :app:testDebugUnitTest :app:assembleDebug
```

Install on a connected Android device:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Pull Requests

Before opening a pull request:

- Run the verified model helper, regression tests and debug build above.
- Do not commit API keys, keystores, APKs, local SDK folders, or generated build output.
- Keep privacy implications explicit in the PR description.
- Include screenshots for keyboard layout changes when possible.

## Security And Privacy

Do not add telemetry, analytics, or server calls without clear documentation and an opt-in path.

Do not hardcode API keys. Users should provide their own keys.
