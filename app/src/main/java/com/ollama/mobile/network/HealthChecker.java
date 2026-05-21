package com.ollama.mobile.network;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class HealthChecker {

    public interface Callback {
        void onResult(boolean reachable, String message);
    }

    private static final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build();

    public static void check(String baseUrl, String apiKey, Callback callback) {
        new Thread(() -> {
            String url = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
            try {
                Request.Builder reqBuilder = new Request.Builder().url(url).get();
                if (apiKey != null && !apiKey.isEmpty()) {
                    reqBuilder.header("Authorization", "Bearer " + apiKey);
                }
                try (Response response = client.newCall(reqBuilder.build()).execute()) {
                    if (response.isSuccessful() || response.code() == 404) {
                        // 404 is fine — Ollama root returns 404 but server is up
                        callback.onResult(true, "Connected");
                    } else {
                        callback.onResult(false, "Server returned " + response.code());
                    }
                }
            } catch (Exception e) {
                callback.onResult(false, e.getMessage() != null ? e.getMessage() : "Unreachable");
            }
        }).start();
    }
}
