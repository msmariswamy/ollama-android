package com.ollama.mobile.ui.settings;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;

import com.ollama.mobile.OllamaApp;
import com.ollama.mobile.model.ApiProvider;
import com.ollama.mobile.model.CustomProviderConfig;
import com.ollama.mobile.network.HealthChecker;
import com.ollama.mobile.network.LocalNetworkScanner;
import com.ollama.mobile.repository.SettingsRepository;

import java.util.ArrayList;
import java.util.List;

public class SettingsViewModel extends AndroidViewModel {

    public final MutableLiveData<String> statusMessage = new MutableLiveData<>("Checking…");
    public final MutableLiveData<Boolean> isConnected = new MutableLiveData<>(null);
    public final MutableLiveData<List<String>> foundHosts = new MutableLiveData<>(new ArrayList<>());
    public final MutableLiveData<Integer> scanProgress = new MutableLiveData<>(0);
    public final MutableLiveData<Boolean> isScanning = new MutableLiveData<>(false);

    SettingsRepository settingsRepository;
    private final LocalNetworkScanner scanner = new LocalNetworkScanner();

    public SettingsViewModel(@NonNull Application application) {
        super(application);
        settingsRepository = ((OllamaApp) application).getAppContainer().settingsRepository;
    }

    public SettingsViewModel(Application app, SettingsRepository settingsRepo) {
        super(app);
        this.settingsRepository = settingsRepo;
    }

    public SettingsRepository getSettingsRepository() {
        return settingsRepository;
    }

    public void checkConnection() {
        statusMessage.postValue("Checking…");
        isConnected.postValue(null);
        HealthChecker.check(settingsRepository.getBaseUrl(), settingsRepository.getApiKey(), (reachable, message) -> {
            isConnected.postValue(reachable);
            statusMessage.postValue(reachable ? "Connected" : message);
        });
    }

    public void scanNetwork() {
        isScanning.postValue(true);
        scanProgress.postValue(0);
        foundHosts.postValue(new ArrayList<>());

        scanner.scan(getApplication(), new LocalNetworkScanner.ScanCallback() {
            @Override
            public void onProgress(int scanned, int total) {
                scanProgress.postValue(scanned);
            }

            @Override
            public void onHostFound(String ip) {
                List<String> current = foundHosts.getValue();
                if (current == null) current = new ArrayList<>();
                List<String> updated = new ArrayList<>(current);
                updated.add(ip);
                foundHosts.postValue(updated);
            }

            @Override
            public void onComplete(List<String> found) {
                isScanning.postValue(false);
            }
        });
    }

    public void saveLocalUrl(String ip) {
        String url = "http://" + ip + ":11434";
        settingsRepository.setLocalBaseUrl(url);
        settingsRepository.setActiveProvider(ApiProvider.OLLAMA_LOCAL);
        rebuildClients();
        checkConnection();
    }

    public void saveApiKey(String key) {
        settingsRepository.setApiKey(key);
        settingsRepository.setMode(SettingsRepository.MODE_CLOUD);
        rebuildClients();
        checkConnection();
    }

    /** Save config for a fixed provider and set it as active. */
    public void saveProviderConfig(ApiProvider provider, String apiKey, boolean setActive) {
        if (apiKey != null && !apiKey.isEmpty()) {
            settingsRepository.setApiKeyForProvider(provider, apiKey);
        }
        if (setActive) {
            settingsRepository.setActiveProvider(provider);
            rebuildClients();
            checkConnection();
        }
    }

    // ── Custom provider list ──────────────────────────────────────────────────

    public final MutableLiveData<List<CustomProviderConfig>> customProviders =
            new MutableLiveData<>(new ArrayList<>());

    public void loadCustomProviders() {
        customProviders.postValue(settingsRepository.getCustomProviders());
    }

    /** Adds a new custom provider, selects it as active, returns the created config. */
    public CustomProviderConfig addCustomProvider(String name, String baseUrl, String apiKey) {
        CustomProviderConfig config = settingsRepository.addCustomProvider(name, baseUrl, apiKey);
        settingsRepository.setActiveCustomId(config.id);
        settingsRepository.setActiveProvider(ApiProvider.CUSTOM_OPENAI);
        rebuildClients();
        checkConnection();
        loadCustomProviders();
        return config;
    }

    /** Updates fields of an existing custom provider; if it is the active one, rebuilds clients. */
    public void updateCustomProvider(String id, String name, String baseUrl, String apiKey) {
        settingsRepository.updateCustomProvider(id, name, baseUrl, apiKey);
        if (id.equals(settingsRepository.getActiveCustomId())) {
            rebuildClients();
            checkConnection();
        }
        loadCustomProviders();
    }

    public void removeCustomProvider(String id) {
        boolean wasActive = id.equals(settingsRepository.getActiveCustomId())
                && settingsRepository.getActiveProvider() == ApiProvider.CUSTOM_OPENAI;
        settingsRepository.removeCustomProvider(id);
        if (wasActive) {
            // Fall back to first remaining custom, or Ollama Local
            List<CustomProviderConfig> remaining = settingsRepository.getCustomProviders();
            if (!remaining.isEmpty()) {
                settingsRepository.setActiveCustomId(remaining.get(0).id);
            } else {
                settingsRepository.setActiveProvider(ApiProvider.OLLAMA_LOCAL);
            }
            rebuildClients();
            checkConnection();
        }
        loadCustomProviders();
    }

    public void selectCustomProvider(String id) {
        settingsRepository.setActiveCustomId(id);
        settingsRepository.setActiveProvider(ApiProvider.CUSTOM_OPENAI);
        rebuildClients();
        checkConnection();
    }

    private void rebuildClients() {
        OllamaApp app = (OllamaApp) getApplication();
        app.getAppContainer().ollamaClient.rebuild();
        app.getAppContainer().openAIClient.rebuild();
        app.getAppContainer().anthropicClient.rebuild();
    }
}
