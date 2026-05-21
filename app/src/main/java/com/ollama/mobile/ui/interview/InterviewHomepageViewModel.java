package com.ollama.mobile.ui.interview;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.ollama.mobile.OllamaApp;
import com.ollama.mobile.model.InterviewRole;
import com.ollama.mobile.network.OllamaApiService;
import com.ollama.mobile.repository.InterviewRepository;
import com.ollama.mobile.repository.InterviewRoleRepository;
import com.ollama.mobile.repository.ModelRepository;
import com.ollama.mobile.repository.SettingsRepository;

import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class InterviewHomepageViewModel extends AndroidViewModel {

    private static final String CLOUD_BASE_URL = "https://api.ollama.com/";

    private final SettingsRepository settingsRepository;
    private final InterviewRepository interviewRepository;

    private final MutableLiveData<List<String>> cloudModels = new MutableLiveData<>();
    private final MutableLiveData<Boolean> apiKeyMissing = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> loadingModels = new MutableLiveData<>(false);

    public InterviewHomepageViewModel(@NonNull Application application) {
        super(application);
        settingsRepository = ((OllamaApp) application).getAppContainer().settingsRepository;
        interviewRepository = new InterviewRepository(
                ((OllamaApp) application).getAppContainer().ollamaClient,
                settingsRepository
        );
    }

    public void loadCloudModels() {
        String apiKey = settingsRepository.getApiKey();
        if (apiKey == null || apiKey.isEmpty()) {
            apiKeyMissing.setValue(true);
            return;
        }
        apiKeyMissing.setValue(false);
        loadingModels.setValue(true);

        OllamaApiService cloudApi = buildCloudApiService(apiKey);
        ModelRepository cloudModelRepo = new ModelRepository(cloudApi);
        cloudModelRepo.fetchModels(result -> {
            loadingModels.postValue(false);
            if (result.status == com.ollama.mobile.network.NetworkResult.Status.SUCCESS) {
                cloudModels.postValue(result.data);
            } else {
                apiKeyMissing.postValue(true);
            }
        });
    }

    public List<InterviewRole> loadRoles() {
        return new InterviewRoleRepository(getApplication()).loadAllRoles(getApplication());
    }

    public void deleteUserRole(String roleId, android.content.Context context) {
        new InterviewRoleRepository(context).deleteUserRole(roleId);
    }

    private OllamaApiService buildCloudApiService(String apiKey) {
        OkHttpClient httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .addInterceptor(chain -> {
                    Request.Builder builder = chain.request().newBuilder();
                    if (!apiKey.isEmpty()) {
                        builder.header("Authorization", "Bearer " + apiKey);
                    }
                    return chain.proceed(builder.build());
                })
                .build();
        return new Retrofit.Builder()
                .baseUrl(CLOUD_BASE_URL)
                .client(httpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(OllamaApiService.class);
    }

    public LiveData<List<String>> getCloudModels() { return cloudModels; }
    public LiveData<Boolean> getApiKeyMissing() { return apiKeyMissing; }
    public LiveData<Boolean> getLoadingModels() { return loadingModels; }

    public String getSavedInterviewModel() {
        return settingsRepository.getInterviewCloudModel();
    }

    public void saveInterviewModel(String model) {
        settingsRepository.setInterviewCloudModel(model);
    }

    public String getSavedWhisperModelKey() {
        return settingsRepository.getWhisperModelKey();
    }

    public void saveWhisperModelKey(String key) {
        settingsRepository.setWhisperModelKey(key);
    }

    public boolean isInterviewSectionExpanded() {
        return settingsRepository.isInterviewSectionExpanded();
    }

    public void setInterviewSectionExpanded(boolean expanded) {
        settingsRepository.setInterviewSectionExpanded(expanded);
    }

    public String getTranscriptionMode() {
        return settingsRepository.getTranscriptionMode();
    }

    public void saveTranscriptionMode(String mode) {
        settingsRepository.setTranscriptionMode(mode);
    }
}
