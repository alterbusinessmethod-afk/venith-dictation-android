package com.voiceflowkeyboard.ime;

import java.util.Locale;

/** Pure policy shared by capture, delivery, and context routing. */
final class EditorPolicy {
    private EditorPolicy() {}

    static boolean allowsVoice(String packageName, int inputType) {
        if (packageName == null || packageName.isEmpty() || (inputType & 15) != 1) return false;
        int variation = inputType & 4080;
        return variation != 128 && variation != 144 && variation != 224;
    }

    static boolean incognito(int imeOptions) {
        return (imeOptions & 0x01000000) != 0;
    }

    static String mode(String packageName, int inputType, String manual, boolean automatic) {
        if (!automatic || !allowsVoice(packageName, inputType)) return manual;
        if ("com.openai.chatgpt".equals(packageName) || "com.anthropic.claude".equals(packageName)) {
            return "ai_prompt";
        }
        String app = packageName.toLowerCase(Locale.ROOT);
        int variation = inputType & 4080;
        if ("com.google.android.gm".equals(app) || "com.microsoft.office.outlook".equals(app)
                || "com.samsung.android.email.provider".equals(app)
                || variation == 32 || variation == 208) return "email";
        return manual;
    }
}
