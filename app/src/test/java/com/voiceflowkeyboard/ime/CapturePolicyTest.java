package com.voiceflowkeyboard.ime;

import org.junit.Test;
import static org.junit.Assert.*;

public class CapturePolicyTest {
    @Test public void stoppedSessionRetainsDecoderAndRejectsStaleEditor() {
        CaptureSession capture = new CaptureSession(7, "com.openai.chatgpt", 11, 1, "offline_parakeet", false, 5, 5);
        assertEquals("offline_parakeet", capture.provider);
        assertTrue(capture.matches(7, "com.openai.chatgpt", 11, 1, 5, 5));
        assertFalse(capture.matches(8, "com.openai.chatgpt", 11, 1, 5, 5));
        assertFalse(capture.matches(7, "com.openai.chatgpt", 12, 1, 5, 5));
        assertFalse(capture.matches(7, "com.google.android.gm", 11, 1, 5, 5));
        assertFalse(capture.matches(7, "com.openai.chatgpt", 11, 129, 5, 5));
    }
    @Test public void delayedDecodeCannotCommitAfterCursorOrSelectionMovement() {
        CaptureSession pending = new CaptureSession(4, "app", 7, 1, "offline_zipformer", false, 9, 9);
        String decodedRaw = "keep this dictated text";
        StringBuilder committed = new StringBuilder();
        if (pending.matches(4, "app", 7, 1, 2, 2)) committed.append(decodedRaw);
        assertEquals("", committed.toString());
        assertEquals("keep this dictated text", decodedRaw);
        assertFalse(pending.matches(4, "app", 7, 1, 9, 14));
        assertFalse(pending.matches(5, "app", 7, 1, 9, 9));
        assertTrue(pending.matches(4, "app", 7, 1, 9, 9));
        CaptureSession unknown = new CaptureSession(4, "app", 7, 1, "offline_zipformer", false, -1, -1);
        assertFalse(unknown.matches(4, "app", 7, 1, -1, -1));
    }
    @Test public void contextNeverGuessesBrowserHostAndSupportsManualOverride() {
        assertEquals("ai_prompt", EditorPolicy.mode("com.anthropic.claude", 1, "casual", true));
        assertEquals("email", EditorPolicy.mode("com.google.android.gm", 1, "casual", true));
        assertEquals("casual", EditorPolicy.mode("com.android.chrome", 1, "casual", true));
        assertEquals("business", EditorPolicy.mode("com.openai.chatgpt", 1, "business", false));
    }
    @Test public void unknownAndPasswordFieldsFailClosed() {
        assertFalse(EditorPolicy.allowsVoice(null, 1));
        assertFalse(EditorPolicy.allowsVoice("app", 0));
        assertFalse(EditorPolicy.allowsVoice("app", 2));
        for (int type : new int[]{129,145,225,18}) assertFalse(EditorPolicy.allowsVoice("app", type));
        assertTrue(EditorPolicy.incognito(0x01000000));
        assertFalse(EditorPolicy.incognito(0));
    }
}
