package com.ollama.mobile.repository;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import android.util.Patterns;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.ollama.mobile.model.ApiProvider;
import com.ollama.mobile.model.CustomProviderConfig;
import com.ollama.mobile.whisper.WhisperModelManager;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class SettingsRepository {

    private static final String TAG = "SettingsRepository";

    // Plain SharedPreferences — non-sensitive settings only
    private static final String PREFS_FILE = "ollama_prefs";
    // EncryptedSharedPreferences — API keys ONLY
    private static final String SECURE_PREFS_FILE = "ollama_keystore";

    private static final String KEY_BASE_URL = "base_url";
    private static final String KEY_API_KEY = "api_key";
    private static final String KEY_MODE = "connection_mode";
    private static final String KEY_ACTIVE_PROVIDER = "active_provider";
    private static final String KEY_API_KEY_PREFIX = "api_key_provider_";
    private static final String KEY_CUSTOM_OPENAI_BASE_URL = "custom_openai_base_url";
    private static final String KEY_CUSTOM_OPENAI_NAME = "custom_openai_name";
    private static final String KEY_CUSTOM_PROVIDERS_JSON = "custom_providers_json";
    private static final String KEY_ACTIVE_CUSTOM_ID = "active_custom_provider_id";
    private static final String KEY_CUSTOM_KEY_PREFIX = "api_key_custom_";
    private static final String KEY_SELECTED_MODEL = "selected_model";
    private static final String KEY_TEMPERATURE = "pref_temperature";
    private static final String KEY_TOP_P = "pref_top_p";
    private static final String KEY_CTX_LENGTH = "pref_ctx_length";
    private static final String KEY_DARK_MODE = "pref_dark_mode";
    private static final String KEY_SYSTEM_PROMPT = "pref_system_prompt";
    private static final String KEY_INTERVIEW_CLOUD_MODEL = "interview_cloud_model";
    private static final String KEY_INTERVIEW_SECTION_EXPANDED = "interview_section_expanded";
    private static final String KEY_WHISPER_MODEL = "whisper_model";
    private static final String KEY_TRANSCRIPTION_MODE = "transcription_mode";

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

    // ── Active provider ───────────────────────────────────────────────────────

    public ApiProvider getActiveProvider() {
        String name = prefs.getString(KEY_ACTIVE_PROVIDER, null);
        if (name == null) {
            // Migrate from legacy mode key
            return MODE_CLOUD.equals(prefs.getString(KEY_MODE, MODE_LOCAL))
                    ? ApiProvider.OLLAMA_CLOUD : ApiProvider.OLLAMA_LOCAL;
        }
        try {
            return ApiProvider.valueOf(name);
        } catch (IllegalArgumentException e) {
            return ApiProvider.OLLAMA_LOCAL;
        }
    }

    public void setActiveProvider(ApiProvider provider) {
        prefs.edit()
                .putString(KEY_ACTIVE_PROVIDER, provider.name())
                .putString(KEY_MODE, provider == ApiProvider.OLLAMA_LOCAL ? MODE_LOCAL : MODE_CLOUD)
                .apply();
    }

    // ── Per-provider API keys — encrypted ────────────────────────────────────

    public String getApiKeyForProvider(ApiProvider provider) {
        if (securePrefs == null) return "";
        if (provider == ApiProvider.CUSTOM_OPENAI) {
            return getCustomProviderApiKey(getActiveCustomId());
        }
        // Legacy fallback: if provider is OLLAMA_CLOUD, also check the old KEY_API_KEY
        String key = securePrefs.getString(KEY_API_KEY_PREFIX + provider.name(), "");
        if ((key == null || key.isEmpty()) && provider == ApiProvider.OLLAMA_CLOUD) {
            key = securePrefs.getString(KEY_API_KEY, "");
        }
        return key != null ? key : "";
    }

    public void setApiKeyForProvider(ApiProvider provider, String key) {
        if (securePrefs == null) {
            Log.w(TAG, "Cannot save API key: Android Keystore unavailable");
            return;
        }
        SharedPreferences.Editor editor = securePrefs.edit()
                .putString(KEY_API_KEY_PREFIX + provider.name(), key);
        // Keep legacy key in sync for OLLAMA_CLOUD
        if (provider == ApiProvider.OLLAMA_CLOUD) {
            editor.putString(KEY_API_KEY, key);
        }
        editor.apply();
    }

    // ── Custom OpenAI provider list ───────────────────────────────────────────

    private static final Gson gson = new Gson();

    public List<CustomProviderConfig> getCustomProviders() {
        String json = prefs.getString(KEY_CUSTOM_PROVIDERS_JSON, null);
        if (json == null || json.isEmpty()) {
            // Migrate legacy single-custom entry if present
            String legacyUrl = prefs.getString(KEY_CUSTOM_OPENAI_BASE_URL, "");
            String legacyName = prefs.getString(KEY_CUSTOM_OPENAI_NAME, "");
            if (!legacyUrl.isEmpty()) {
                CustomProviderConfig legacy = new CustomProviderConfig(
                        UUID.randomUUID().toString(), legacyName.isEmpty() ? "Custom" : legacyName, legacyUrl);
                List<CustomProviderConfig> list = new ArrayList<>();
                list.add(legacy);
                saveCustomProviderList(list);
                // Migrate key
                if (securePrefs != null) {
                    String legacyKey = securePrefs.getString(KEY_API_KEY_PREFIX + "CUSTOM_OPENAI", "");
                    if (!legacyKey.isEmpty()) {
                        securePrefs.edit().putString(KEY_CUSTOM_KEY_PREFIX + legacy.id, legacyKey).apply();
                        setActiveCustomId(legacy.id);
                    }
                }
                return list;
            }
            return new ArrayList<>();
        }
        Type listType = new TypeToken<List<CustomProviderConfig>>() {}.getType();
        List<CustomProviderConfig> result = gson.fromJson(json, listType);
        return result != null ? result : new ArrayList<>();
    }

    public CustomProviderConfig addCustomProvider(String name, String baseUrl, String apiKey) {
        String id = UUID.randomUUID().toString();
        if (!baseUrl.endsWith("/")) baseUrl = baseUrl + "/";
        CustomProviderConfig config = new CustomProviderConfig(id, name, baseUrl);
        List<CustomProviderConfig> list = getCustomProviders();
        list.add(config);
        saveCustomProviderList(list);
        if (apiKey != null && !apiKey.isEmpty() && securePrefs != null) {
            securePrefs.edit().putString(KEY_CUSTOM_KEY_PREFIX + id, apiKey).apply();
        }
        return config;
    }

    public void updateCustomProvider(String id, String name, String baseUrl, String apiKey) {
        List<CustomProviderConfig> list = getCustomProviders();
        for (CustomProviderConfig c : list) {
            if (c.id.equals(id)) {
                c.name = name;
                c.baseUrl = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
                break;
            }
        }
        saveCustomProviderList(list);
        if (apiKey != null && !apiKey.isEmpty() && securePrefs != null) {
            securePrefs.edit().putString(KEY_CUSTOM_KEY_PREFIX + id, apiKey).apply();
        }
    }

    public void removeCustomProvider(String id) {
        List<CustomProviderConfig> list = getCustomProviders();
        list.removeIf(c -> c.id.equals(id));
        saveCustomProviderList(list);
        if (securePrefs != null) {
            securePrefs.edit().remove(KEY_CUSTOM_KEY_PREFIX + id).apply();
        }
        if (id.equals(getActiveCustomId())) {
            prefs.edit().remove(KEY_ACTIVE_CUSTOM_ID).apply();
        }
    }

    public String getCustomProviderApiKey(String id) {
        if (securePrefs == null) return "";
        String key = securePrefs.getString(KEY_CUSTOM_KEY_PREFIX + id, "");
        return key != null ? key : "";
    }

    public String getActiveCustomId() {
        return prefs.getString(KEY_ACTIVE_CUSTOM_ID, "");
    }

    public void setActiveCustomId(String id) {
        prefs.edit().putString(KEY_ACTIVE_CUSTOM_ID, id).apply();
    }

    private void saveCustomProviderList(List<CustomProviderConfig> list) {
        prefs.edit().putString(KEY_CUSTOM_PROVIDERS_JSON, gson.toJson(list)).apply();
    }

    /** Returns the base URL for the given provider. */
    public String getBaseUrlForProvider(ApiProvider provider) {
        switch (provider) {
            case OLLAMA_LOCAL:  return prefs.getString(KEY_BASE_URL, "http://192.168.1.1:11434/");
            case OLLAMA_CLOUD:  return CLOUD_BASE_URL;
            case OPENROUTER:    return "https://openrouter.ai/api/v1/";
            case GROK:          return "https://api.x.ai/v1/";
            case CLAUDE:        return "https://api.anthropic.com/";
            case CUSTOM_OPENAI: {
                String activeId = getActiveCustomId();
                if (!activeId.isEmpty()) {
                    for (CustomProviderConfig c : getCustomProviders()) {
                        if (c.id.equals(activeId)) return c.baseUrl;
                    }
                }
                return "";
            }
            default: return prefs.getString(KEY_BASE_URL, "http://192.168.1.1:11434/");
        }
    }

    // ── Connection mode (legacy + derived) ───────────────────────────────────

    public String getMode() {
        return prefs.getString(KEY_MODE, MODE_LOCAL);
    }

    public void setMode(String mode) {
        prefs.edit().putString(KEY_MODE, mode).apply();
    }

    public boolean isCloudMode() {
        return getActiveProvider() != ApiProvider.OLLAMA_LOCAL;
    }

    // ── URLs ──────────────────────────────────────────────────────────────────

    public String getBaseUrl() {
        return getBaseUrlForProvider(getActiveProvider());
    }

    public void setLocalBaseUrl(String url) {
        if (!url.endsWith("/")) url = url + "/";
        prefs.edit().putString(KEY_BASE_URL, url).apply();
    }

    public String getLocalBaseUrl() {
        return prefs.getString(KEY_BASE_URL, "");
    }

    // ── API key — encrypted (returns key for active provider) ─────────────────

    public String getApiKey() {
        return getApiKeyForProvider(getActiveProvider());
    }

    public void setApiKey(String key) {
        setApiKeyForProvider(getActiveProvider(), key);
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

    // ── Whisper model selection ───────────────────────────────────────────────

    public String getWhisperModelKey() {
        return prefs.getString(KEY_WHISPER_MODEL, WhisperModelManager.WhisperModel.BASE_EN.name());
    }

    public void setWhisperModelKey(String key) {
        prefs.edit().putString(KEY_WHISPER_MODEL, key).apply();
    }

    // ── Transcription engine mode ─────────────────────────────────────────────

    public String getTranscriptionMode() {
        return prefs.getString(KEY_TRANSCRIPTION_MODE, "WHISPER");
    }

    public void setTranscriptionMode(String mode) {
        prefs.edit().putString(KEY_TRANSCRIPTION_MODE, mode).apply();
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    public static boolean isValidUrl(String url) {
        return url != null && !url.isEmpty() && Patterns.WEB_URL.matcher(url).matches();
    }
}
