package com.alya.assistant;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import android.content.Context;
import android.content.SharedPreferences;

final class SecureKeyStore {
    private static final String PREFERENCES_NAME = "alya_encrypted_settings_v1";
    private static final String LEGACY_PREFERENCES_NAME = "secure_settings";
    private static final String KEY_GEMINI_API = "gemini_key";

    private SecureKeyStore() {}

    static void save(Context context, String apiKey) throws Exception {
        SharedPreferences preferences = getEncryptedPreferences(context);
        if (apiKey.isEmpty()) {
            preferences.edit().remove(KEY_GEMINI_API).apply();
            return;
        }
        preferences.edit().putString(KEY_GEMINI_API, apiKey).apply();
    }

    static String getUserApiKey(Context context) throws Exception {
        return getEncryptedPreferences(context).getString(KEY_GEMINI_API, "");
    }

    private static SharedPreferences getEncryptedPreferences(Context context) throws Exception {
        MasterKey masterKey = new MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build();
        SharedPreferences preferences = EncryptedSharedPreferences.create(
                context,
                PREFERENCES_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
        context.deleteSharedPreferences(LEGACY_PREFERENCES_NAME);
        return preferences;
    }
}