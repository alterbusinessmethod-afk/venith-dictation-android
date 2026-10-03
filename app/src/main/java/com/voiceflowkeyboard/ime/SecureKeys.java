package com.voiceflowkeyboard.ime;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** API keys are encrypted with a device-bound Android Keystore key. */
final class SecureKeys {
    private static final String ALIAS = "venith.dictation.api-keys.v1";
    private static final String[] NAMES = {"openai_api_key", "anthropic_api_key", "xai_api_key", "deepgram_api_key"};
    private SecureKeys() {}

    private static SecretKey key() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        if (store.containsAlias(ALIAS)) return (SecretKey) store.getKey(ALIAS, null);
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
        return generator.generateKey();
    }

    private static String encrypt(String name, String value) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key());
        cipher.updateAAD(name.getBytes(StandardCharsets.UTF_8));
        return Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP) + ":"
                + Base64.encodeToString(cipher.doFinal(value.getBytes(StandardCharsets.UTF_8)), Base64.NO_WRAP);
    }

    static synchronized String read(Context context, String name) {
        SharedPreferences prefs = Prefs.shared(context);
        try {
            migrate(prefs);
            String stored = prefs.getString("encrypted_" + name, "");
            if (stored.isEmpty()) return "";
            String[] parts = stored.split(":", -1);
            if (parts.length != 2) throw new IllegalStateException("Invalid encrypted key");
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)));
            cipher.updateAAD(name.getBytes(StandardCharsets.UTF_8));
            return new String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Secure key storage unavailable. Re-enter or clear provider keys in Settings.", e);
        }
    }

    private static void migrate(SharedPreferences prefs) throws Exception {
        Map<String, String> migrated = new LinkedHashMap<>();
        for (String name : NAMES) {
            String legacy = prefs.getString(name, "");
            if (!legacy.isEmpty() && !prefs.contains("encrypted_" + name)) migrated.put(name, encrypt(name, legacy));
        }
        SharedPreferences.Editor edit = prefs.edit();
        boolean found = false;
        for (String name : NAMES) {
            if (prefs.contains(name)) { edit.remove(name); found = true; }
            if (migrated.containsKey(name)) edit.putString("encrypted_" + name, migrated.get(name));
        }
        if (found && !edit.commit()) throw new IllegalStateException("Could not migrate key storage");
    }

    static synchronized void save(Context context, String... values) {
        try {
            String[] encrypted = new String[NAMES.length];
            for (int i = 0; i < NAMES.length; i++) {
                String value = values[i] == null ? "" : values[i].trim();
                encrypted[i] = value.isEmpty() ? "" : encrypt(NAMES[i], value);
            }
            SharedPreferences.Editor edit = Prefs.shared(context).edit();
            for (int i = 0; i < NAMES.length; i++) {
                edit.remove(NAMES[i]);
                if (encrypted[i].isEmpty()) edit.remove("encrypted_" + NAMES[i]);
                else edit.putString("encrypted_" + NAMES[i], encrypted[i]);
            }
            if (!edit.commit()) throw new IllegalStateException("Could not save keys");
        } catch (Exception e) {
            throw new IllegalStateException("Secure key storage failed; keys were not saved.", e);
        }
    }
}
