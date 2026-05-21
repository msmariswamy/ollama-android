package com.ollama.mobile.ui.settings;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;

import com.ollama.mobile.OllamaApp;
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
        settingsRepository.setMode(SettingsRepository.MODE_LOCAL);
        ((OllamaApp) getApplication()).getAppContainer().ollamaClient.rebuild();
        checkConnection();
    }

    public void saveApiKey(String key) {
        settingsRepository.setApiKey(key);
        settingsRepository.setMode(SettingsRepository.MODE_CLOUD);
        ((OllamaApp) getApplication()).getAppContainer().ollamaClient.rebuild();
        checkConnection();
    }
}
