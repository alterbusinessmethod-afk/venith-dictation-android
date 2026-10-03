package com.voiceflowkeyboard.ime;

import java.io.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class ModelFilesTest {
    @Test public void rejectsTraversalAndExcessiveCopyAndCorruption() throws Exception {
        File root = java.nio.file.Files.createTempDirectory("venith-model-test-").toFile();
        for (String name : new String[]{"../escaped", "/absolute", "..\\escape"}) {
            try { ModelFiles.contained(root, name); fail(name); } catch (IOException expected) {}
        }
        File output = ModelFiles.contained(root, "model.bin");
        try { ModelFiles.copy(new ByteArrayInputStream(new byte[9]), output, 8); fail(); }
        catch (IOException expected) {}
        ModelFiles.copy(new ByteArrayInputStream("abc".getBytes("UTF-8")), output, 3);
        String digest = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";
        assertTrue(ModelFiles.verified(output, 3, digest));
        assertFalse(ModelFiles.verified(output, 4, digest));
        ModelFiles.copy(new ByteArrayInputStream("abd".getBytes("UTF-8")), output, 3);
        assertFalse(ModelFiles.verified(output, 3, digest));
    }
}
