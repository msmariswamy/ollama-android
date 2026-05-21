package com.ollama.mobile.network;

import com.ollama.mobile.model.ApiProvider;
import com.ollama.mobile.repository.SettingsRepository;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Retrofit client for the Anthropic Messages API (Claude models).
 */
public class AnthropicClient {

    private static final String ANTHROPIC_VERSION = "2023-06-01";

    private final SettingsRepository settingsRepository;
    private AnthropicApiService apiService;

    public AnthropicClient(SettingsRepository settingsRepository) {
        this.settingsRepository = settingsRepository;
        rebuild();
    }

    public void rebuild() {
        OkHttpClient httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(300, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .addInterceptor(chain -> {
                    String key = settingsRepository.getApiKeyForProvider(ApiProvider.CLAUDE);
                    Request request = chain.request().newBuilder()
                            .header("x-api-key", key != null ? key : "")
                            .header("anthropic-version", ANTHROPIC_VERSION)
                            .header("content-type", "application/json")
                            .build();
                    return chain.proceed(request);
                })
                .build();

        apiService = new Retrofit.Builder()
                .baseUrl("https://api.anthropic.com/")
                .client(httpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(AnthropicApiService.class);
    }

    public AnthropicApiService getApiService() {
        return apiService;
    }
}
