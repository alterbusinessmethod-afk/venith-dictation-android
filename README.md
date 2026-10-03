# Venith Dictation for Android

A dark Android keyboard with bundled, offline English dictation and optional conservative text cleanup through your own Claude API key. Designed as a separately installed ARM64 app for phones such as the Samsung S22 Ultra.

This is a credited MIT fork of [yutungh/voiceflow-keyboard-android](https://github.com/yutungh/voiceflow-keyboard-android), based on commit `231899b4bf685a1f37df11ac858189c6bcd136c5`. The original README and disabled upstream workflow examples are preserved in [docs/upstream-reference](docs/upstream-reference). Original copyright and third-party licenses remain intact.

## Install

Download the APK or installation ZIP from this repository's Releases page when the verified preview release is available. The ZIP is a container: extract it, then tap the `.apk` on your phone. The English speech model is inside the APK; no separate model download or speech account is required.

Follow [the Samsung setup guide](docs/INSTALL_SAMSUNG.md). The preview does not replace the original VoiceFlow app: its application ID is `com.venith.dictation`.

## What changes

- Dark settings, keyboard, dialogs and prompt editor.
- English Zipformer GigaSpeech int8 bundled locally: **75,208,256 bytes of inference files** with pinned hashes. Two CPU threads, on-demand recognition and resource release after dictation.
- Automatic AI Prompt style in the ChatGPT and Claude apps; Email style in supported mail apps/email fields; manual style selection elsewhere.
- Editable AI Prompt, Email, Casual, Professional and relationship styles. Automatic context can be switched off to keep manual control.
- Claude Sonnet 4.6 as the default cleanup model. Cleanup starts off and requires your own key plus explicit enablement.
- Conservative cleanup instructions and value/negation checks; raw transcript recovery on cleanup errors or suspicious changes.
- Android Keystore encryption for provider keys, disabled backup/cleartext traffic, sensitive-field suppression and history off by default.
- Session guards that prevent pending results from automatically being inserted after an editor changes.

Browser page URLs are not visible to a normal keyboard. Select AI Prompt manually when using ChatGPT or Claude in a browser. No Accessibility service or broad app inventory is used.

## Model and verification limits

Model file size is not peak RAM. No S22 Ultra RAM, microphone, battery, latency or real-key Claude benchmark is claimed without a phone test. English is the bundled language. Optional Vosk/Parakeet choices remain available but require their own downloads and have different resource costs. See [model choices](docs/MODEL_CHOICES.md).

The model runs when you dictate; the recognizer is released afterward. This keyboard does not keep an always-listening background microphone. Recording has a 120-second limit. Provider cleanup sends recognized text to the selected provider; choosing a cloud speech provider separately can send audio too. Keep the bundled local speech model selected for local audio processing.

## Build from source

Use JDK 17, Android SDK platform 35/build-tools 35.0.0, Python 3 and the checked-in Gradle wrapper. Its distribution and wrapper JAR are verified against official Gradle hashes. Run:

```sh
python3 scripts/prepare_model.py
./gradlew --no-daemon :app:testDebugUnitTest :app:assembleRelease
```

The release output is unsigned unless `VENITH_SIGNING_STORE` and `VENITH_SIGNING_PASSWORD` point to your own signing key (alias `venith-dictation`). Keep that private key outside source control. Debug builds also support x86_64 for emulator verification; the release is ARM64.

The pinned GitHub workflow compiles, runs unit tests, and exercises the native bundled model plus dark screens on an Android emulator. The public LibriSpeech fixture is downloaded only for the test APK. No provider key or paid AI call is used by the tests. An emulator receipt is separate from phone performance.

## Credits

VoiceFlow Keyboard: yutungh, MIT. Sherpa-ONNX/Zipformer: k2-fsa, Apache 2.0; model revision `c9e185789e2067cbf79350c7f691d5d2d4c5a28a`. Vosk: Alpha Cephei, Apache 2.0. Other dependency and model notices are in [the bundled license directory](app/src/main/assets/licenses). The test-only LibriSpeech clip is CC BY 4.0, credited to Vassil Panayotov, Guoguo Chen, Daniel Povey and Sanjeev Khudanpur via [OpenSLR 12](https://www.openslr.org/12).

Venith changes are MIT licensed. Review the release verification report and checksum manifest before installing a preview.
