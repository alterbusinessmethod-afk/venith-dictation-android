package com.voiceflowkeyboard.ime;

final class CaptureLimits {
    // Two minutes at mono PCM16 / 16 kHz. Caps audio/tensor accumulation on phones.
    static final int MAX_DURATION_MS = 120000;
    static final long MAX_PCM_BYTES = 16000L * 2 * MAX_DURATION_MS / 1000;
    static final int MAX_TEXT_CHARS = 24000;
    private CaptureLimits() {}
}
