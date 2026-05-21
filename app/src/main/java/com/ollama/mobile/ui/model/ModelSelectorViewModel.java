package com.ollama.mobile.ui.model;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;

import com.ollama.mobile.OllamaApp;
import com.ollama.mobile.network.NetworkResult;
import com.ollama.mobile.repository.ModelRepository;
import com.ollama.mobile.repository.SettingsRepository;

import java.util.List;

public class ModelSelectorViewModel extends AndroidViewModel {

    public final MutableLiveData<NetworkResult<List<String>>> modelsResult = new MutableLiveData<>();
    public final MutableLiveData<String> selectedModel = new MutableLiveData<>();

    ModelRepository modelRepository;
    SettingsRepository settingsRepository;
    private boolean skipServiceRebuild = false;

    public ModelSelectorViewModel(@NonNull Application application) {
        super(application);
        OllamaApp app = (OllamaApp) application;
        modelRepository = app.getAppContainer().modelRepository;
        settingsRepository = app.getAppContainer().settingsRepository;
        selectedModel.setValue(settingsRepository.getSelectedModel());
    }

    public ModelSelectorViewModel(Application app, ModelRepository modelRepo, SettingsRepository settingsRepo) {
        super(app);
        this.modelRepository = modelRepo;
        this.settingsRepository = settingsRepo;
        this.skipServiceRebuild = true;
        selectedModel.setValue(settingsRepo.getSelectedModel());
    }

    public void loadModels() {
        modelsResult.postValue(NetworkResult.loading());
        if (!skipServiceRebuild) {
            modelRepository.updateApiService(
                    ((OllamaApp) getApplication()).getAppContainer().ollamaClient.getApiService()
            );
        }
        modelRepository.fetchModels(result -> modelsResult.postValue(result));
    }

    public void selectModel(String model) {
        settingsRepository.setSelectedModel(model);
        selectedModel.postValue(model);
    }
}
