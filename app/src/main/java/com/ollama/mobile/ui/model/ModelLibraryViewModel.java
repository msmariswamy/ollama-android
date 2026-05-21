package com.ollama.mobile.ui.model;

import android.app.Application;
import android.os.Environment;
import android.os.StatFs;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;

import com.google.gson.JsonObject;
import com.ollama.mobile.OllamaApp;
import com.ollama.mobile.model.OllamaModel;
import com.ollama.mobile.network.NdjsonStreamReader;
import com.ollama.mobile.network.OllamaClient;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Response;

public class ModelLibraryViewModel extends AndroidViewModel {

    public final MutableLiveData<List<OllamaModel>> installedModels = new MutableLiveData<>(new ArrayList<>());
    public final MutableLiveData<String> errorMessage = new MutableLiveData<>();
    public final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    public final MutableLiveData<String> storageUsed = new MutableLiveData<>("");
    public final MutableLiveData<String> storageAvailable = new MutableLiveData<>("");

    // modelName -> pull progress 0–100, or -1 for indeterminate
    public final MutableLiveData<java.util.Map<String, Integer>> pullProgress =
            new MutableLiveData<>(new java.util.HashMap<>());

    private final OllamaClient ollamaClient;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final java.util.Map<String, Call<ResponseBody>> activePulls = new java.util.HashMap<>();

    public ModelLibraryViewModel(@NonNull Application application) {
        super(application);
        ollamaClient = ((OllamaApp) application).getAppContainer().ollamaClient;
        loadModels();
        updateStorageSummary();
    }

    public void loadModels() {
        isLoading.setValue(true);
        executor.execute(() -> {
            try {
                Response<OllamaModel.TagsResponse> resp = ollamaClient.getApiService().getTags().execute();
                if (resp.isSuccessful() && resp.body() != null && resp.body().models != null) {
                    installedModels.postValue(resp.body().models);
                } else {
                    installedModels.postValue(new ArrayList<>());
                    errorMessage.postValue("Failed to load models");
                }
            } catch (Exception e) {
                errorMessage.postValue(e.getMessage());
            } finally {
                isLoading.postValue(false);
            }
        });
    }

    public void deleteModel(String name) {
        executor.execute(() -> {
            try {
                JsonObject body = new JsonObject();
                body.addProperty("name", name);
                ollamaClient.getApiService().deleteModel(body).execute();
                List<OllamaModel> current = new ArrayList<>(
                        installedModels.getValue() != null ? installedModels.getValue() : new ArrayList<>());
                current.removeIf(m -> name.equals(m.name));
                installedModels.postValue(current);
            } catch (Exception e) {
                errorMessage.postValue("Delete failed: " + e.getMessage());
            }
        });
    }

    public void pullModel(String name) {
        executor.execute(() -> {
            try {
                JsonObject body = new JsonObject();
                body.addProperty("name", name);
                Call<ResponseBody> call = ollamaClient.getApiService().pullModel(body);
                activePulls.put(name, call);
                updateProgress(name, -1);
                Response<ResponseBody> resp = call.execute();
                if (!resp.isSuccessful() || resp.body() == null) {
                    errorMessage.postValue("Pull failed for " + name);
                    clearProgress(name);
                    return;
                }
                com.google.gson.Gson gson = new com.google.gson.Gson();
                java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(resp.body().byteStream()));
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isEmpty()) continue;
                    try {
                        JsonObject obj = gson.fromJson(line, JsonObject.class);
                        if (obj.has("completed") && obj.has("total")) {
                            long completed = obj.get("completed").getAsLong();
                            long total = obj.get("total").getAsLong();
                            if (total > 0) updateProgress(name, (int) (completed * 100 / total));
                        }
                    } catch (Exception ignored) {}
                }
                clearProgress(name);
                activePulls.remove(name);
                loadModels();
            } catch (Exception e) {
                if (!"Canceled".equals(e.getMessage())) {
                    errorMessage.postValue("Pull error: " + e.getMessage());
                }
                clearProgress(name);
                activePulls.remove(name);
            }
        });
    }

    public void cancelPull(String name) {
        Call<ResponseBody> call = activePulls.remove(name);
        if (call != null) call.cancel();
        clearProgress(name);
    }

    private void updateProgress(String name, int progress) {
        java.util.Map<String, Integer> map = new java.util.HashMap<>(
                pullProgress.getValue() != null ? pullProgress.getValue() : new java.util.HashMap<>());
        map.put(name, progress);
        pullProgress.postValue(map);
    }

    private void clearProgress(String name) {
        java.util.Map<String, Integer> map = new java.util.HashMap<>(
                pullProgress.getValue() != null ? pullProgress.getValue() : new java.util.HashMap<>());
        map.remove(name);
        pullProgress.postValue(map);
    }

    private void updateStorageSummary() {
        executor.execute(() -> {
            try {
                StatFs stat = new StatFs(Environment.getDataDirectory().getPath());
                long total = stat.getTotalBytes();
                long free = stat.getFreeBytes();
                long used = total - free;
                storageUsed.postValue(formatBytes(used));
                storageAvailable.postValue(formatBytes(free));
            } catch (Exception ignored) {}
        });
    }

    private static String formatBytes(long bytes) {
        if (bytes >= 1_000_000_000L) return String.format("%.1f GB", bytes / 1e9);
        if (bytes >= 1_000_000L) return String.format("%.0f MB", bytes / 1e6);
        return String.format("%.0f KB", bytes / 1e3);
    }
}
