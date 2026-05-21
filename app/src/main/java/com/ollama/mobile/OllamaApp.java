package com.ollama.mobile;

import android.app.Application;

import androidx.appcompat.app.AppCompatDelegate;

import com.ollama.mobile.repository.SettingsRepository;

public class OllamaApp extends Application {

    private AppContainer appContainer;

    @Override
    public void onCreate() {
        super.onCreate();
        appContainer = new AppContainer(this);
        applyDarkMode();
    }

    private void applyDarkMode() {
        int mode = appContainer.settingsRepository.getDarkMode();
        int nightMode;
        if (mode == SettingsRepository.DARK_MODE_LIGHT) {
            nightMode = AppCompatDelegate.MODE_NIGHT_NO;
        } else if (mode == SettingsRepository.DARK_MODE_DARK) {
            nightMode = AppCompatDelegate.MODE_NIGHT_YES;
        } else {
            nightMode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        }
        AppCompatDelegate.setDefaultNightMode(nightMode);
    }

    public AppContainer getAppContainer() {
        return appContainer;
    }
}
