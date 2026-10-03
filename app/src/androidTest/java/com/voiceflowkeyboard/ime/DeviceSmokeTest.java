package com.voiceflowkeyboard.ime;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.SystemClock;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class DeviceSmokeTest {
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private final Context context = instrumentation.getTargetContext();

    private File evidence(String name) {
        File directory = new File(context.getExternalFilesDir(null), "verification");
        assertTrue(directory.isDirectory() || directory.mkdirs());
        return new File(directory, name);
    }

    @Test public void bundledModelDecodesRealSpeechWithAndroidRuntime() throws Exception {
        File pcm = new File(context.getCacheDir(), "smoke-speech.pcm");
        try (InputStream in = instrumentation.getContext().getAssets().open("speech.pcm");
             FileOutputStream out = new FileOutputStream(pcm)) {
            byte[] block = new byte[8192];
            int count;
            while ((count = in.read(block)) != -1) out.write(block, 0, count);
        }
        long start = SystemClock.elapsedRealtime();
        String text = OfflineZipformerClient.transcribePcm(context, pcm);
        assertTrue("A real speech clip must produce a phrase", text.trim().split("\\s+").length >= 6);
        assertTrue("The public reference clip describes stew", text.toLowerCase(java.util.Locale.ROOT).contains("stew"));
        String receipt = "Android API " + android.os.Build.VERSION.SDK_INT + "\nEmulator ABI " + android.os.Build.SUPPORTED_ABIS[0] + "\nSample: LibriSpeech 1089-134686-0001\nText: " + text + "\nDecode and first model staging milliseconds: " + (SystemClock.elapsedRealtime() - start) + "\nThis is an emulator receipt, not an S22 Ultra benchmark.\n";
        try (FileOutputStream out = new FileOutputStream(evidence("model-runtime.txt"))) {
            out.write(receipt.getBytes(StandardCharsets.UTF_8));
        }
    }

    @Test public void apiKeyMigrationEncryptsAndReplacementRecoversCorruption() {
        String fake = "fixture-only-never-sent-to-a-provider";
        assertTrue(Prefs.shared(context).edit().putString("anthropic_api_key",fake).remove("encrypted_anthropic_api_key").commit());
        assertEquals(fake,SecureKeys.read(context,"anthropic_api_key"));
        assertFalse(Prefs.shared(context).contains("anthropic_api_key"));
        String stored = Prefs.shared(context).getString("encrypted_anthropic_api_key","");
        assertFalse(stored.isEmpty());
        assertFalse(stored.contains(fake));
        assertTrue(Prefs.shared(context).edit().putString("encrypted_anthropic_api_key","broken-ciphertext").commit());
        try {
            SecureKeys.read(context,"anthropic_api_key");
            fail("Corrupt ciphertext must produce a visible failure");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("Secure key storage"));
        }
        SecureKeys.save(context,"",fake,"","");
        assertEquals(fake,SecureKeys.read(context,"anthropic_api_key"));
        SecureKeys.save(context,"","","","");
        assertEquals("",SecureKeys.read(context,"anthropic_api_key"));
    }

    @Test public void recordLoadedNativeRuntimeVersion() throws Exception {
        // The stable OrtApiBase C ABI has GetApi then GetVersionString pointers.
        com.sun.jna.NativeLibrary library=com.sun.jna.NativeLibrary.getInstance("onnxruntime");
        com.sun.jna.Pointer base=library.getFunction("OrtGetApiBase").invokePointer(new Object[]{});
        assertNotNull(base);
        com.sun.jna.Pointer versionPointer=base.getPointer(com.sun.jna.Native.POINTER_SIZE);
        assertNotNull(versionPointer);
        String version=(String)com.sun.jna.Function.getFunction(versionPointer).invoke(String.class,new Object[]{});
        assertTrue("ORT must report its own version",version.matches("[0-9]+\\.[0-9]+\\.[0-9]+.*"));
        try (FileOutputStream out=new FileOutputStream(evidence("native-runtime-version.txt"))) {
            out.write(("ONNX Runtime "+version+"\nReported by OrtApiBase.GetVersionString in the Android process.\nA version is not a complete binary-to-source SBOM.\n").getBytes(StandardCharsets.UTF_8));
        }
    }

    private void capture(Class<? extends Activity> screen, String name) throws Exception {
        Activity activity = (Activity) instrumentation.startActivitySync(new Intent(context, screen).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        instrumentation.waitForIdleSync();
        SystemClock.sleep(1800);
        if (screen == KeyboardTestActivity.class) {
            android.accessibilityservice.AccessibilityServiceInfo serviceInfo=instrumentation.getUiAutomation().getServiceInfo();
            serviceInfo.flags |= android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
            instrumentation.getUiAutomation().setServiceInfo(serviceInfo);
            boolean visible=false;
            for (android.view.accessibility.AccessibilityWindowInfo window:instrumentation.getUiAutomation().getWindows()) {
                if (window.getType()==android.view.accessibility.AccessibilityWindowInfo.TYPE_INPUT_METHOD) {
                    android.view.accessibility.AccessibilityNodeInfo node=window.getRoot();
                    visible=node!=null && "com.venith.dictation".contentEquals(node.getPackageName()) && hasText(node,"Start or stop dictation recording");
                }
            }
            assertTrue("The Venith IME must be visible",visible);
        }
        Bitmap screenshot = instrumentation.getUiAutomation().takeScreenshot();
        assertNotNull(screenshot);
        long dark = 0, sampled = 0;
        for (int y = screenshot.getHeight()/8; y < screenshot.getHeight()*7/8; y += 8) {
            for (int x = 8; x < screenshot.getWidth()-8; x += 8) {
                int color = screenshot.getPixel(x,y);
                if (Color.red(color)<70 && Color.green(color)<70 && Color.blue(color)<80) dark++;
                sampled++;
            }
        }
        assertTrue("Screen must predominantly use the dark theme", dark > sampled * 0.6);
        try (FileOutputStream out = new FileOutputStream(evidence(name))) {
            assertTrue(screenshot.compress(Bitmap.CompressFormat.PNG,100,out));
        }
        screenshot.recycle();
        instrumentation.runOnMainSync(activity::finish);
    }

    private boolean hasText(android.view.accessibility.AccessibilityNodeInfo node, String text) {
        if (node.getText() != null && node.getText().toString().contains(text)) return true;
        if (node.getContentDescription() != null && node.getContentDescription().toString().contains(text)) return true;
        for (int i=0;i<node.getChildCount();i++) {
            android.view.accessibility.AccessibilityNodeInfo child=node.getChild(i);
            if (child != null && hasText(child,text)) return true;
        }
        return false;
    }

    @Test public void settingsAndKeyboardRenderDarkAtPhoneWidth() throws Exception {
        capture(SettingsActivity.class,"settings.png");
        capture(KeyboardTestActivity.class,"keyboard.png");
        capture(AboutActivity.class,"about.png");
    }
}
