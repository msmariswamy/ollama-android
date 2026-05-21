package com.ollama.mobile.repository;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import android.util.Patterns;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

public class SettingsRepository {

    private static final String TAG = "SettingsRepository";

    // Plain SharedPreferences — non-sensitive settings only
    private static final String PREFS_FILE = "ollama_prefs";
    // EncryptedSharedPreferences — API key ONLY
    private static final String SECURE_PREFS_FILE = "ollama_keystore";

    private static final String KEY_BASE_URL = "base_url";
    private static final String KEY_API_KEY = "api_key";
    private static final String KEY_MODE = "connection_mode";
    private static final String KEY_SELECTED_MODEL = "selected_model";
    private static final String KEY_TEMPERATURE = "pref_temperature";
    private static final String KEY_TOP_P = "pref_top_p";
    private static final String KEY_CTX_LENGTH = "pref_ctx_length";
    private static final String KEY_DARK_MODE = "pref_dark_mode";
    private static final String KEY_SYSTEM_PROMPT = "pref_system_prompt";
    private static final String KEY_INTERVIEW_CLOUD_MODEL = "interview_cloud_model";
    private static final String KEY_INTERVIEW_SECTION_EXPANDED = "interview_section_expanded";

    public static final int DARK_MODE_SYSTEM = 0;
    public static final int DARK_MODE_LIGHT = 1;
    public static final int DARK_MODE_DARK = 2;

    public static final String MODE_LOCAL = "local";
    public static final String MODE_CLOUD = "cloud";
    private static final String CLOUD_BASE_URL = "https://api.ollama.com/";

    /** Non-sensitive settings — fast reads, no crypto overhead. */
    private final SharedPreferences prefs;

    /**
     * Encrypted store backed by Android Keystore (AES256-GCM hardware key + AES256-SIV/GCM
     * envelope). Only the API key is stored here. Null if the device Keystore is unavailable
     * (very rare — pre-API-18 or broken hardware). In that case getApiKey() returns "" and
     * setApiKey() logs a warning rather than silently writing plaintext.
     */
    private final SharedPreferences securePrefs;

    public SettingsRepository(Context context) {
        prefs = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE);
        securePrefs = buildSecurePrefs(context);
    }

    /** Test constructor — uses the supplied prefs for both stores. */
    SettingsRepository(SharedPreferences prefs) {
        this.prefs = prefs;
        this.securePrefs = prefs;
    }

    private static SharedPreferences buildSecurePrefs(Context context) {
        try {
            MasterKey masterKey = new MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();
            return EncryptedSharedPreferences.create(
                    context,
                    SECURE_PREFS_FILE,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (Exception e) {
            // Android Keystore unavailable — do NOT fall back to plaintext for the API key.
            Log.e(TAG, "Android Keystore unavailable; API key will not be persisted", e);
            return null;
        }
    }

    // ── Connection mode ───────────────────────────────────────────────────────

    public String getMode() {
        return prefs.getString(KEY_MODE, MODE_LOCAL);
    }

    public void setMode(String mode) {
        prefs.edit().putString(KEY_MODE, mode).apply();
    }

    public boolean isCloudMode() {
        return MODE_CLOUD.equals(getMode());
    }

    // ── URLs ──────────────────────────────────────────────────────────────────

    public String getBaseUrl() {
        if (isCloudMode()) return CLOUD_BASE_URL;
        return prefs.getString(KEY_BASE_URL, "http://192.168.1.1:11434/");
    }

    public void setLocalBaseUrl(String url) {
        if (!url.endsWith("/")) url = url + "/";
        prefs.edit().putString(KEY_BASE_URL, url).apply();
    }

    public String getLocalBaseUrl() {
        return prefs.getString(KEY_BASE_URL, "");
    }

    // ── API key — encrypted ───────────────────────────────────────────────────

    public String getApiKey() {
        if (securePrefs == null) return "";
        return securePrefs.getString(KEY_API_KEY, "");
    }

    public void setApiKey(String key) {
        if (securePrefs == null) {
            Log.w(TAG, "Cannot save API key: Android Keystore unavailable on this device");
            return;
        }
        securePrefs.edit().putString(KEY_API_KEY, key).apply();
    }

    /** Returns true if the Keystore-backed store is available on this device. */
    public boolean isSecureStorageAvailable() {
        return securePrefs != null;
    }

    // ── Model ─────────────────────────────────────────────────────────────────

    public String getSelectedModel() {
        return prefs.getString(KEY_SELECTED_MODEL, "");
    }

    public void setSelectedModel(String model) {
        prefs.edit().putString(KEY_SELECTED_MODEL, model).apply();
    }

    // ── Model parameters ──────────────────────────────────────────────────────

    public float getTemperature() {
        return prefs.getFloat(KEY_TEMPERATURE, 0.7f);
    }

    public void setTemperature(float value) {
        prefs.edit().putFloat(KEY_TEMPERATURE, value).apply();
    }

    public float getTopP() {
        return prefs.getFloat(KEY_TOP_P, 0.9f);
    }

    public void setTopP(float value) {
        prefs.edit().putFloat(KEY_TOP_P, value).apply();
    }

    public int getContextLength() {
        return prefs.getInt(KEY_CTX_LENGTH, 4096);
    }

    public void setContextLength(int value) {
        prefs.edit().putInt(KEY_CTX_LENGTH, value).apply();
    }

    // ── Appearance ────────────────────────────────────────────────────────────

    public int getDarkMode() {
        return prefs.getInt(KEY_DARK_MODE, DARK_MODE_SYSTEM);
    }

    public void setDarkMode(int mode) {
        prefs.edit().putInt(KEY_DARK_MODE, mode).apply();
    }

    // ── System prompt ─────────────────────────────────────────────────────────

    public String getSystemPromptGlobal() {
        return prefs.getString(KEY_SYSTEM_PROMPT, "");
    }

    public void setSystemPromptGlobal(String prompt) {
        prefs.edit().putString(KEY_SYSTEM_PROMPT, prompt).apply();
    }

    // ── Interview assistant ───────────────────────────────────────────────────

    public String getInterviewCloudModel() {
        return prefs.getString(KEY_INTERVIEW_CLOUD_MODEL, "");
    }

    public void setInterviewCloudModel(String model) {
        prefs.edit().putString(KEY_INTERVIEW_CLOUD_MODEL, model).apply();
    }

    public boolean isInterviewSectionExpanded() {
        return prefs.getBoolean(KEY_INTERVIEW_SECTION_EXPANDED, true);
    }

    public void setInterviewSectionExpanded(boolean expanded) {
        prefs.edit().putBoolean(KEY_INTERVIEW_SECTION_EXPANDED, expanded).apply();
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    public static boolean isValidUrl(String url) {
        return url != null && !url.isEmpty() && Patterns.WEB_URL.matcher(url).matches();
    }
}
