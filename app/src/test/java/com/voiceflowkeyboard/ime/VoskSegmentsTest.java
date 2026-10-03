package com.voiceflowkeyboard.ime;

import org.junit.Test;
import java.io.IOException;
import static org.junit.Assert.*;

public class VoskSegmentsTest {
    @Test public void pauseEndpointsAndFinalTailRemainInOrder() throws Exception {
        VoskSegments segments = new VoskSegments();
        segments.append("first sentence");
        segments.append("");
        segments.append(" second sentence ");
        segments.append("final unfinished tail");
        assertEquals("first sentence second sentence final unfinished tail", segments.text());
    }
    @Test public void emptyFinalTailDoesNotDropEarlierUtterances() throws Exception {
        VoskSegments segments = new VoskSegments();
        segments.append("complete utterance");
        segments.append("");
        assertEquals("complete utterance", segments.text());
    }
    @Test(expected = IOException.class) public void excessTranscriptIsBounded() throws Exception {
        VoskSegments segments = new VoskSegments();
        segments.append(new String(new char[24001]).replace('\0', 'a'));
    }
}
