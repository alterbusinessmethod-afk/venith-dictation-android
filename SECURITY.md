# Venith Dictation security

Enter optional provider keys directly in the protected phone settings screen. Device-bound Android Keystore encrypts saved values. Never include a real API key, signing key, raw private transcript or credential screenshot in source, a release archive or an issue. If encrypted keys are unreadable, use the explicit confirmed clear action and then enter replacements.

Release builds use the separate com.venith.dictation identity, ARM64 libraries and debuggable=false. The signed preview is separately verified before publication. Backup and cleartext network traffic are disabled; internal settings screens are unexported except the launcher. The IME service is protected by Android's BIND_INPUT_METHOD permission.

Provider HTTP responses are bounded and error messages avoid raw response bodies/transcript leakage. Automatic cross-host redirects are disabled. Bundled model size and SHA256 are pinned, staged and verified before activation. Audio and queued work are bounded; pending insertions revalidate their editor/selection. Cleanup contract checks reduce selected accidental rewrites but are not a universal proof of semantic equivalence.

Emulator, unit and package receipts establish the specific behaviors recorded in a release report. They do not establish physical S22 Ultra performance or guarantee every third-party app/editor behavior. For consequential text, review before sending.

For a vulnerability report, use GitHub's private advisory route if enabled for this repository. Otherwise contact the repository maintainer privately. Share reproduction steps and affected version without keys or personal transcript content. Archived upstream scripts/policies are historical references; the legacy plaintext-key installer is disabled.
