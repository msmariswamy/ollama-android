package com.ollama.mobile.repository;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Patterns;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import java.io.IOException;

public class SettingsRepository {

    private static final String PREFS_FILE = "ollama_secure_prefs";
    private static final String KEY_BASE_URL = "base_url";
    private static final String KEY_API_KEY = "api_key";
    private static final String KEY_MODE = "connection_mode";
    private static final String KEY_SELECTED_MODEL = "selected_model";

    public static final String MODE_LOCAL = "local";
    public static final String MODE_CLOUD = "cloud";
    private static final String CLOUD_BASE_URL = "https://api.ollama.com/";

    private SharedPreferences prefs;

    public SettingsRepository(Context context) {
        SharedPreferences p;
        try {
            MasterKey masterKey = new MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();
            p = EncryptedSharedPreferences.create(
                    context,
                    PREFS_FILE,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (Exception e) {
            // fallback to regular prefs if encryption unavailable
            p = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE);
        }
        prefs = p;
    }

    SettingsRepository(SharedPreferences prefs) {
        this.prefs = prefs;
    }

    public String getMode() {
        return prefs.getString(KEY_MODE, MODE_LOCAL);
    }

    public void setMode(String mode) {
        prefs.edit().putString(KEY_MODE, mode).apply();
    }

    public boolean isCloudMode() {
        return MODE_CLOUD.equals(getMode());
    }

    public String getBaseUrl() {
        if (isCloudMode()) {
            return CLOUD_BASE_URL;
        }
        return prefs.getString(KEY_BASE_URL, "http://192.168.1.1:11434/");
    }

    public void setLocalBaseUrl(String url) {
        if (!url.endsWith("/")) url = url + "/";
        prefs.edit().putString(KEY_BASE_URL, url).apply();
    }

    public String getLocalBaseUrl() {
        return prefs.getString(KEY_BASE_URL, "");
    }

    public String getApiKey() {
        return prefs.getString(KEY_API_KEY, "");
    }

    public void setApiKey(String key) {
        prefs.edit().putString(KEY_API_KEY, key).apply();
    }

    public String getSelectedModel() {
        return prefs.getString(KEY_SELECTED_MODEL, "");
    }

    public void setSelectedModel(String model) {
        prefs.edit().putString(KEY_SELECTED_MODEL, model).apply();
    }

    public static boolean isValidUrl(String url) {
        return url != null && !url.isEmpty() && Patterns.WEB_URL.matcher(url).matches();
    }
}
