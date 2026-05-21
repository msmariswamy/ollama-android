package com.ollama.mobile.repository;

import android.util.Log;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.ollama.mobile.model.ChatChunk;
import com.ollama.mobile.model.ChatMessage;
import com.ollama.mobile.model.ChatRequest;
import com.ollama.mobile.network.NdjsonStreamReader;
import com.ollama.mobile.network.OllamaClient;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Response;

public class ChatRepository {

    private static final String TAG = "ChatRepository";

    private final OllamaClient ollamaClient;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final NdjsonStreamReader streamReader = new NdjsonStreamReader();

    private volatile Call<ResponseBody> activeCall;

    public ChatRepository(OllamaClient ollamaClient) {
        this.ollamaClient = ollamaClient;
    }

    public void sendMessage(String model, List<ChatMessage> messages, StreamCallback callback) {
        executor.execute(() -> {
            ChatRequest request = new ChatRequest(model, messages, true);
            int approxChars = messages.stream().mapToInt(m -> m.content != null ? m.content.length() : 0).sum();
            Log.d(TAG, "→ POST /api/chat model=" + model
                    + " messages=" + messages.size()
                    + " ~chars=" + approxChars
                    + " ~tokens≈" + (approxChars / 4));
            try {
                retrofit2.Call<ResponseBody> retrofitCall = ollamaClient.getApiService().chat(request);
                activeCall = (Call<ResponseBody>) retrofitCall;
                Response<ResponseBody> response = retrofitCall.execute();
                if (!response.isSuccessful() || response.body() == null) {
                    String rawBody = "(no body)";
                    try {
                        if (response.errorBody() != null) {
                            String raw = response.errorBody().string();
                            if (raw != null && !raw.isEmpty()) rawBody = raw;
                        }
                    } catch (Exception ignored) {}
                    Log.e(TAG, "HTTP " + response.code() + " [model=" + model
                            + " messages=" + messages.size() + "] error body: " + rawBody);
                    callback.onError("HTTP " + response.code() + ": " + extractErrorMessage(rawBody));
                    return;
                }
                streamReader.read(response.body(), new NdjsonStreamReader.ChunkListener() {
                    @Override
                    public void onChunk(ChatChunk chunk) {
                        callback.onChunk(chunk);
                    }

                    @Override
                    public void onError(String message) {
                        callback.onError(message);
                    }

                    @Override
                    public void onComplete() {
                        activeCall = null;
                        callback.onComplete();
                    }
                });
            } catch (Exception e) {
                if (e.getMessage() != null && e.getMessage().contains("Canceled")) {
                    callback.onCancelled();
                } else {
                    Log.e(TAG, "sendMessage exception: " + e.getMessage(), e);
                    callback.onError(e.getMessage() != null ? e.getMessage() : "Unknown error");
                }
                activeCall = null;
            }
        });
    }

    public void cancelStream() {
        Call<ResponseBody> call = activeCall;
        if (call != null) {
            call.cancel();
            activeCall = null;
        }
    }

    /** Extracts the human-readable "error" field from an Ollama JSON error body, falling back to the raw string. */
    private static String extractErrorMessage(String raw) {
        if (raw == null || raw.isEmpty()) return "Unknown error";
        try {
            JsonObject obj = JsonParser.parseString(raw).getAsJsonObject();
            if (obj.has("error")) {
                return obj.get("error").getAsString();
            }
        } catch (Exception ignored) {}
        return raw;
    }

    public interface StreamCallback {
        void onChunk(ChatChunk chunk);
        void onError(String message);
        void onComplete();
        void onCancelled();
    }
}
