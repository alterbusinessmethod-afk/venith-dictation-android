# Venith Dictation privacy

This policy describes this fork's default build. As an Android input method, the keyboard receives editor metadata and text necessary for typing and can insert text into the active field. Microphone access is used when you start dictation. Voice is suppressed for password and non-text fields; this does not make a keyboard unable to see all other text presented by Android.

## Default local path

Bundled English Zipformer is the default speech provider. Audio is recorded on the phone and decoded locally. Its model weights are included in the APK and verified before the private working copy is activated. Cleanup and transcript history start off. The recognizer is released after a dictation task, and the microphone is not an always-listening background service. Dictation is limited to 120 seconds.

Optional Vosk and Parakeet models require downloads from their configured public sources. These downloads reveal normal network metadata to those hosts. Their local integrity receipt detects later corruption; it is separate from the bundled Zipformer's pinned upstream hash verification.

## Optional cloud paths

Enabling cloud cleanup sends recognized transcript text and your selected cleanup instructions to the selected API provider, authenticated with your own key. Anthropic Sonnet 4.6 is the default cleanup choice. The local speech path keeps audio on the phone. If you explicitly select OpenAI, xAI or Deepgram cloud speech instead, the recorded audio is sent to that provider. Network-unavailable cloud speech falls back to the bundled local provider.

Normal typed keystrokes are not sent to an app-owned server. This app has no app-owned backend or analytics collector. Provider handling of data and API billing is governed by your provider account and its current policy.

## Keys and local files

API keys are encrypted in private preferences using AES-GCM with a device-bound Android Keystore key. The key screen blocks screenshots, autofill and saved view state. Legacy plaintext preference entries are migrated and removed only after encrypted values can be committed. A storage failure is shown; an explicit confirmed clear control allows recovery from unreadable ciphertext without clearing other settings. The app disables Android backup and cleartext HTTP traffic. No developer key is bundled.

Temporary recording files are held in private app cache during recognition and cleanup and removed by the app when the task finishes/cancels. Models and settings are in private app storage. User prompts, provider/model IDs and style choices remain local. Transcript history is optional; apps' no-personalized-learning flag suppresses history/learning. Raw transcript recovery is available in the current session when appropriate. Android or a process termination may leave a cache file until normal app/cache cleanup.

## Context

Automatic context reads the active editor's app package/input type to select AI Prompt or Email. It does not scrape the app screen, read browser URLs, use an Accessibility service or inventory every installed app. Browser use needs manual mode selection. Turn automatic context off for persistent manual control. Pending delivery is guarded by editor and cursor/selection snapshots.

The original upstream policy is retained as historical reference under docs/upstream-reference; this document describes the Venith fork.
