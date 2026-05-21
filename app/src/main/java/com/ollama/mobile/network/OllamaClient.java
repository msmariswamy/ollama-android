package com.ollama.mobile.network;

import com.ollama.mobile.repository.SettingsRepository;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class OllamaClient {

    private final SettingsRepository settingsRepository;
    private OllamaApiService apiService;
    private String cachedBaseUrl;

    public OllamaClient(SettingsRepository settingsRepository) {
        this.settingsRepository = settingsRepository;
        rebuild();
    }

    public void rebuild() {
        cachedBaseUrl = settingsRepository.getBaseUrl();
        String url = cachedBaseUrl.endsWith("/") ? cachedBaseUrl : cachedBaseUrl + "/";

        OkHttpClient httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(300, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .addInterceptor(chain -> {
                    Request.Builder builder = chain.request().newBuilder();
                    String token = settingsRepository.getApiKey();
                    if (token != null && !token.isEmpty()) {
                        builder.header("Authorization", "Bearer " + token);
                    }
                    return chain.proceed(builder.build());
                })
                .build();

        apiService = new Retrofit.Builder()
                .baseUrl(url)
                .client(httpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(OllamaApiService.class);
    }

    public OllamaApiService getApiService() {
        String currentUrl = settingsRepository.getBaseUrl();
        if (!currentUrl.equals(cachedBaseUrl)) {
            rebuild();
        }
        return apiService;
    }

    public OkHttpClient buildRawClient() {
        return new OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .addInterceptor(chain -> {
                    Request.Builder builder = chain.request().newBuilder();
                    String token = settingsRepository.getApiKey();
                    if (token != null && !token.isEmpty()) {
                        builder.header("Authorization", "Bearer " + token);
                    }
                    return chain.proceed(builder.build());
                })
                .build();
    }
}
