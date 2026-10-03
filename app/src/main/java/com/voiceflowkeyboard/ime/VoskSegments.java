package com.voiceflowkeyboard.ime;

import java.io.IOException;

/** Retains each endpointed utterance and the final unfinished tail in recording order. */
final class VoskSegments {
    private static final int MAX_TEXT_CHARS = 24000;
    private final StringBuilder text = new StringBuilder();

    void append(String segment) throws IOException {
        if (segment == null || segment.trim().isEmpty()) return;
        String clean = segment.trim();
        int separator = text.length() == 0 ? 0 : 1;
        if (clean.length() > MAX_TEXT_CHARS - text.length() - separator) {
            throw new IOException("Offline transcript exceeded text limit");
        }
        if (separator != 0) text.append(' ');
        text.append(clean);
    }

    String text() { return text.toString(); }
}
