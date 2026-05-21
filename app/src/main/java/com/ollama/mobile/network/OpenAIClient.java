package com.ollama.mobile.network;

import com.ollama.mobile.model.ApiProvider;
import com.ollama.mobile.repository.SettingsRepository;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Retrofit client for OpenAI-compatible providers: OpenRouter, Grok (xAI), and Custom.
 */
public class OpenAIClient {

    private final SettingsRepository settingsRepository;
    private OpenAIApiService apiService;
    private String cachedBaseUrl;

    public OpenAIClient(SettingsRepository settingsRepository) {
        this.settingsRepository = settingsRepository;
        rebuild();
    }

    public void rebuild() {
        ApiProvider provider = settingsRepository.getActiveProvider();
        cachedBaseUrl = settingsRepository.getBaseUrlForProvider(provider);
        String url = cachedBaseUrl.endsWith("/") ? cachedBaseUrl : cachedBaseUrl + "/";

        OkHttpClient httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(300, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .addInterceptor(chain -> {
                    Request.Builder builder = chain.request().newBuilder();
                    String token = settingsRepository.getApiKeyForProvider(provider);
                    if (token != null && !token.isEmpty()) {
                        builder.header("Authorization", "Bearer " + token);
                    }
                    if (provider == ApiProvider.OPENROUTER) {
                        builder.header("HTTP-Referer", "https://github.com/ollama-mobile");
                        builder.header("X-Title", "Ollama Mobile");
                    }
                    return chain.proceed(builder.build());
                })
                .build();

        apiService = new Retrofit.Builder()
                .baseUrl(url)
                .client(httpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(OpenAIApiService.class);
    }

    public OpenAIApiService getApiService() {
        ApiProvider provider = settingsRepository.getActiveProvider();
        String currentUrl = settingsRepository.getBaseUrlForProvider(provider);
        if (!currentUrl.equals(cachedBaseUrl)) {
            rebuild();
        }
        return apiService;
    }
}
