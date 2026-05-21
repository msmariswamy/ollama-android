package com.ollama.mobile.repository;

import com.ollama.mobile.model.OllamaModel;
import com.ollama.mobile.network.NetworkResult;
import com.ollama.mobile.network.OllamaApiService;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import retrofit2.Response;

public class ModelRepository {

    private OllamaApiService apiService;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public ModelRepository(OllamaApiService apiService) {
        this.apiService = apiService;
    }

    public void updateApiService(OllamaApiService apiService) {
        this.apiService = apiService;
    }

    public void fetchModels(Callback callback) {
        executor.execute(() -> {
            try {
                Response<OllamaModel.TagsResponse> response = apiService.getTags().execute();
                if (response.isSuccessful() && response.body() != null) {
                    List<String> names = new ArrayList<>();
                    for (OllamaModel m : response.body().models) {
                        names.add(m.name);
                    }
                    callback.onResult(NetworkResult.success(names));
                } else {
                    callback.onResult(NetworkResult.error("Server error: " + response.code()));
                }
            } catch (Exception e) {
                callback.onResult(NetworkResult.error(e.getMessage() != null ? e.getMessage() : "Network error"));
            }
        });
    }

    public void fetchModelsWithMeta(MetaCallback callback) {
        executor.execute(() -> {
            try {
                Response<OllamaModel.TagsResponse> response = apiService.getTags().execute();
                if (response.isSuccessful() && response.body() != null) {
                    callback.onResult(NetworkResult.success(response.body().models));
                } else {
                    callback.onResult(NetworkResult.error("Server error: " + response.code()));
                }
            } catch (Exception e) {
                callback.onResult(NetworkResult.error(e.getMessage() != null ? e.getMessage() : "Network error"));
            }
        });
    }

    public interface Callback {
        void onResult(NetworkResult<List<String>> result);
    }

    public interface MetaCallback {
        void onResult(NetworkResult<List<OllamaModel>> result);
    }
}
