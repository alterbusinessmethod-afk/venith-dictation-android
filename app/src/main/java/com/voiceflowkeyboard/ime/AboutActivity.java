package com.voiceflowkeyboard.ime;

import android.app.Activity;
import android.os.Bundle;
import android.text.util.Linkify;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Human-readable provenance and notices, packaged in the APK for offline access. */
public final class AboutActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.applyWindow(this);
        setTitle("About and licences");

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Ui.BACKGROUND);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        Ui.applySystemBarPadding(root, dp(20), dp(18), dp(20), dp(28));
        scroll.addView(root);

        TextView title = Ui.text(this, "Venith Dictation", 26, true, Ui.TEXT);
        root.addView(title);
        addParagraph(root, "A personal Android dictation keyboard built from VoiceFlow Keyboard by Yutun (2026). Original source: https://github.com/yutungh/voiceflow-keyboard-android");
        addHeading(root, "Privacy");
        addParagraph(root, "Bundled Zipformer speech runs on this phone. Cloud cleanup is optional: when enabled, transcript text is sent to the selected provider. The local audio stays on this device for local transcription. Provider keys are entered on this phone.");
        addHeading(root, "Open-source and model notices");
        addParagraph(root, readNotices());

        setContentView(scroll);
    }

    private void addHeading(LinearLayout parent, String heading) {
        TextView view = Ui.text(this, heading, 18, true, Ui.ACCENT);
        view.setPadding(0, dp(24), 0, dp(8));
        parent.addView(view);
    }

    private void addParagraph(LinearLayout parent, String body) {
        TextView view = Ui.text(this, body, 14, false, Ui.TEXT);
        view.setLineSpacing(dp(3), 1f);
        view.setGravity(Gravity.START);
        view.setAutoLinkMask(Linkify.WEB_URLS);
        view.setLinkTextColor(Ui.ACCENT);
        view.setTextIsSelectable(true);
        view.setPadding(0, dp(10), 0, 0);
        parent.addView(view);
    }

    private String readNotices() {
        try (InputStream in = getAssets().open("licenses/NOTICES.txt");
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int total = 0;
            for (int n; (n = in.read(buffer)) != -1; ) {
                total += n;
                if (total > 1024 * 1024) {
                    return "Packaged notices exceed the display limit. See the source repository.";
                }
                out.write(buffer, 0, n);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "Packaged notices are unavailable. See the source repository's LICENSE and third_party directory.";
        }
    }

    private int dp(int value) {
        return Ui.dp(this, value);
    }
}
