package com.voiceflowkeyboard.ime;

import org.junit.Test;
import java.io.IOException;
import static org.junit.Assert.*;

public class CleanupSafetyTest {
    @Test public void allowsNoiseRemovalButRejectsChangedValuesAndSentiment() throws Exception {
        assertEquals("Maybe pay 42 tomorrow.",CleanupSafety.requirePreserved("um maybe pay 42 tomorrow","Maybe pay 42 tomorrow."));
        rejects("I think we should not pay 42", "We should pay 43.");
        rejects("Send the report", "Send the report, love you.");
        rejects("Maybe meet tomorrow", "Meet tomorrow.");
        rejects("I don't agree", "I agree.");
        rejects("Use https://example.com/a", "Use https://example.net/a");
    }
    @Test public void retainsLiteralCodeAndDoesNotAcceptEmptyOutput() throws Exception {
        rejects("Run `rm -rf /`", "Run the command.");
        rejects("Please help", "");
        assertEquals("Should we do this?",CleanupSafety.requirePreserved("Should we do this?","Should we do this?"));
    }
    private static void rejects(String raw,String output) throws Exception {
        try { CleanupSafety.requirePreserved(raw,output);fail(output); } catch(IOException expected) {}
    }
}
