# Install Venith Dictation on your Samsung S22 Ultra

## First installation

1. Send the installation ZIP to your phone through Drive or email, or download the APK from this repository's verified preview release.
2. In Samsung My Files, extract the ZIP. Tap `Venith-Dictation-1.0.0-preview.1-arm64.apk`. Android installs the APK, not the ZIP. If asked, temporarily allow installation from the particular app you used to open it.
3. Open **Venith Dictation**. Its **Start here** steps open Android keyboard settings, choose the keyboard, request microphone access and provide a private test field.
4. Enable Venith Dictation under **Settings > General management > Keyboard list and default** if the shortcut does not open the expected One UI screen. Android displays its standard warning for every enabled keyboard.
5. Choose Venith Dictation using the keyboard selector when typing. Keep your Samsung keyboard available for normal typing and easy switching.
6. Leave **Speech model** set to **Bundled English Zipformer** and **cleanup off** for the first test. Allow at least 500 MB of free phone storage for installation and the model's private working copy. First dictation verifies/stages the bundled model and may take longer.
7. In the test field, tap the microphone, speak a short English sentence and tap again to finish. Confirm the words appear. Repeat in airplane mode to establish that your chosen speech path works offline.

The app has a separate identity from VoiceFlow, so both can remain installed. Its settings and provider keys do not automatically transfer. This release targets ARM64 Android 8 or newer.

## Optional Claude cleanup

1. Open **Provider API keys** and enter your Anthropic API key directly on the phone. Your key is not included in the APK or this ZIP. The key screen blocks screenshots and uses device-bound Android Keystore encryption.
2. Select Anthropic and `claude-sonnet-4-6` for cleanup. Enable **cloud cleanup** only when you want recognized text sent to Anthropic. API charges apply to your account. Audio stays local while the bundled speech model remains selected.
3. Choose Casual, Professional, Email or AI Prompt. With **automatic context** enabled, the ChatGPT/Claude apps select AI Prompt, and supported mail contexts select Email. Turn automatic context off to make manual selection persist.
4. In a browser, select AI Prompt or Email manually. A normal keyboard cannot reliably see the browser's page/domain.
5. The **Prompt editor** lets you paste your own existing laptop cleanup prompt. That exact prompt was not supplied during this build; editable conservative defaults are included. Keep the instruction to clean the transcript rather than answer its questions.

The default prompt removes clear disfluencies and accidental repetitions, fixes punctuation and preserves intent. Warmth and restrained emojis belong to the selected style and the emotion already expressed. Names, quantities, negation, uncertainty, code and URLs require particular care; review the result before sending important text. Raw mode bypasses cleanup. A provider error or suspicious output should leave the local transcript available for recovery.

## Privacy and everyday use

History starts off. Enable it only if you want transcripts retained on the phone. Voice is suppressed in password and non-text fields, and the keyboard respects apps' no-personalized-learning flag for history/learning. Android still controls the keyboard lifecycle; this is on-demand dictation, with no always-listening service.

Moving to another field/app or moving the cursor while a result is pending cancels automatic insertion. Recover and review any available transcript in the keyboard instead. A recording is limited to 120 seconds to bound memory use. For longer material, dictate in shorter sections.

## Your phone acceptance check

Before relying on the preview, try: a short offline sentence; names and numbers; two pauses in one recording; negation such as “do not send”; ChatGPT/Claude and email modes; moving the cursor before a delayed result; returning to Samsung keyboard; and optional cleanup with your own key. Confirm acceptable speed, heat and memory on your S22 Ultra. Those hardware/provider checks could not be performed from the build computer.

For a failed install, check free storage, Android version and ARM64 compatibility. For a provider failure, check the key/model/account quota and recover the raw transcript. If saved encrypted keys become unreadable, use the key screen's explicit replacement/recovery control. Do not post your key in an issue or screenshot.

Keep the release SHA256 manifest with the APK. Future updates must use the same signing identity; an unrelated signature requires uninstalling and re-entering settings/keys. Export only prompts you deliberately want to preserve.
