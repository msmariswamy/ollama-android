package com.ollama.mobile.repository;

import android.util.Log;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.ollama.mobile.model.AnthropicRequest;
import com.ollama.mobile.model.ApiProvider;
import com.ollama.mobile.model.ChatChunk;
import com.ollama.mobile.model.ChatMessage;
import com.ollama.mobile.model.ChatRequest;
import com.ollama.mobile.model.OpenAIChatRequest;
import com.ollama.mobile.network.AnthropicClient;
import com.ollama.mobile.network.AnthropicStreamReader;
import com.ollama.mobile.network.NdjsonStreamReader;
import com.ollama.mobile.network.OllamaClient;
import com.ollama.mobile.network.OpenAIClient;
import com.ollama.mobile.network.OpenAIStreamReader;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Response;

public class ChatRepository {

    private static final String TAG = "ChatRepository";

    private final OllamaClient ollamaClient;
    private final OpenAIClient openAIClient;
    private final AnthropicClient anthropicClient;
    private final SettingsRepository settingsRepository;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final NdjsonStreamReader ndjsonReader = new NdjsonStreamReader();
    private final OpenAIStreamReader openAIReader = new OpenAIStreamReader();
    private final AnthropicStreamReader anthropicReader = new AnthropicStreamReader();

    private volatile Call<ResponseBody> activeCall;

    public ChatRepository(OllamaClient ollamaClient, OpenAIClient openAIClient,
                          AnthropicClient anthropicClient, SettingsRepository settingsRepository) {
        this.ollamaClient = ollamaClient;
        this.openAIClient = openAIClient;
        this.anthropicClient = anthropicClient;
        this.settingsRepository = settingsRepository;
    }

    public void sendMessage(String model, List<ChatMessage> messages, StreamCallback callback) {
        sendMessage(model, messages, null, callback);
    }

    public void sendMessage(String model, List<ChatMessage> messages, ChatRequest.Options options, StreamCallback callback) {
        ApiProvider provider = settingsRepository.getActiveProvider();
        switch (provider) {
            case OPENROUTER:
            case GROK:
            case CUSTOM_OPENAI:
                sendOpenAIMessage(model, messages, options, callback);
                break;
            case CLAUDE:
                sendAnthropicMessage(model, messages, options, callback);
                break;
            default:
                sendOllamaMessage(model, messages, options, callback);
                break;
        }
    }

    private void sendOllamaMessage(String model, List<ChatMessage> messages,
                                   ChatRequest.Options options, StreamCallback callback) {
        executor.execute(() -> {
            ChatRequest request = new ChatRequest(model, messages, true);
            request.options = options;
            logRequest("/api/chat", model, messages);
            try {
                retrofit2.Call<ResponseBody> retrofitCall = ollamaClient.getApiService().chat(request);
                activeCall = (Call<ResponseBody>) retrofitCall;
                Response<ResponseBody> response = retrofitCall.execute();
                if (!response.isSuccessful() || response.body() == null) {
                    String rawBody = readErrorBody(response);
                    Log.e(TAG, "HTTP " + response.code() + " error: " + rawBody);
                    callback.onError("HTTP " + response.code() + ": " + extractOllamaError(rawBody));
                    return;
                }
                ndjsonReader.read(response.body(), new NdjsonStreamReader.ChunkListener() {
                    @Override public void onChunk(ChatChunk chunk) { callback.onChunk(chunk); }
                    @Override public void onError(String message) { callback.onError(message); }
                    @Override public void onComplete() { activeCall = null; callback.onComplete(); }
                });
            } catch (Exception e) {
                handleException(e, callback);
            }
        });
    }

    private void sendOpenAIMessage(String model, List<ChatMessage> messages,
                                   ChatRequest.Options options, StreamCallback callback) {
        executor.execute(() -> {
            List<OpenAIChatRequest.Message> oaiMessages = new ArrayList<>();
            for (ChatMessage m : messages) {
                oaiMessages.add(new OpenAIChatRequest.Message(m.role, m.content));
            }
            OpenAIChatRequest request = new OpenAIChatRequest(model, oaiMessages, true);
            if (options != null) {
                request.temperature = options.temperature;
                request.topP = options.top_p;
                if (options.num_ctx > 0) request.maxTokens = options.num_ctx;
            }
            logRequest("/v1/chat/completions", model, messages);
            try {
                retrofit2.Call<ResponseBody> retrofitCall = openAIClient.getApiService().chat(request);
                activeCall = (Call<ResponseBody>) retrofitCall;
                Response<ResponseBody> response = retrofitCall.execute();
                if (!response.isSuccessful() || response.body() == null) {
                    String rawBody = readErrorBody(response);
                    Log.e(TAG, "HTTP " + response.code() + " error: " + rawBody);
                    callback.onError("HTTP " + response.code() + ": " + extractOpenAIError(rawBody));
                    return;
                }
                openAIReader.read(response.body(), new OpenAIStreamReader.ChunkListener() {
                    @Override public void onChunk(ChatChunk chunk) { callback.onChunk(chunk); }
                    @Override public void onError(String message) { callback.onError(message); }
                    @Override public void onComplete() { activeCall = null; callback.onComplete(); }
                });
            } catch (Exception e) {
                handleException(e, callback);
            }
        });
    }

    private void sendAnthropicMessage(String model, List<ChatMessage> messages,
                                      ChatRequest.Options options, StreamCallback callback) {
        executor.execute(() -> {
            // Anthropic requires the system message as a separate field
            String systemContent = null;
            List<AnthropicRequest.Message> anthropicMessages = new ArrayList<>();
            for (ChatMessage m : messages) {
                if ("system".equals(m.role)) {
                    systemContent = m.content;
                } else {
                    anthropicMessages.add(new AnthropicRequest.Message(m.role, m.content));
                }
            }
            int maxTokens = (options != null && options.num_ctx > 0) ? options.num_ctx : 4096;
            AnthropicRequest request = new AnthropicRequest(model, anthropicMessages, systemContent, maxTokens, true);
            if (options != null) {
                request.temperature = options.temperature;
                request.topP = options.top_p;
            }
            logRequest("/v1/messages", model, messages);
            try {
                retrofit2.Call<ResponseBody> retrofitCall = anthropicClient.getApiService().messages(request);
                activeCall = (Call<ResponseBody>) retrofitCall;
                Response<ResponseBody> response = retrofitCall.execute();
                if (!response.isSuccessful() || response.body() == null) {
                    String rawBody = readErrorBody(response);
                    Log.e(TAG, "HTTP " + response.code() + " error: " + rawBody);
                    callback.onError("HTTP " + response.code() + ": " + extractAnthropicError(rawBody));
                    return;
                }
                anthropicReader.read(response.body(), new AnthropicStreamReader.ChunkListener() {
                    @Override public void onChunk(ChatChunk chunk) { callback.onChunk(chunk); }
                    @Override public void onError(String message) { callback.onError(message); }
                    @Override public void onComplete() { activeCall = null; callback.onComplete(); }
                });
            } catch (Exception e) {
                handleException(e, callback);
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

    private void logRequest(String endpoint, String model, List<ChatMessage> messages) {
        int approxChars = messages.stream().mapToInt(m -> m.content != null ? m.content.length() : 0).sum();
        Log.d(TAG, "→ POST " + endpoint + " model=" + model
                + " messages=" + messages.size()
                + " ~chars=" + approxChars
                + " ~tokens≈" + (approxChars / 4));
    }

    private void handleException(Exception e, StreamCallback callback) {
        if (e.getMessage() != null && e.getMessage().contains("Canceled")) {
            callback.onCancelled();
        } else {
            Log.e(TAG, "sendMessage exception: " + e.getMessage(), e);
            callback.onError(e.getMessage() != null ? e.getMessage() : "Unknown error");
        }
        activeCall = null;
    }

    private static String readErrorBody(Response<?> response) {
        try {
            if (response.errorBody() != null) {
                String raw = response.errorBody().string();
                if (raw != null && !raw.isEmpty()) return raw;
            }
        } catch (Exception ignored) {}
        return "(no body)";
    }

    private static String extractOllamaError(String raw) {
        if (raw == null || raw.isEmpty()) return "Unknown error";
        try {
            JsonObject obj = JsonParser.parseString(raw).getAsJsonObject();
            if (obj.has("error")) return obj.get("error").getAsString();
        } catch (Exception ignored) {}
        return raw;
    }

    private static String extractOpenAIError(String raw) {
        if (raw == null || raw.isEmpty()) return "Unknown error";
        try {
            JsonObject obj = JsonParser.parseString(raw).getAsJsonObject();
            if (obj.has("error")) {
                JsonObject err = obj.getAsJsonObject("error");
                if (err.has("message")) return err.get("message").getAsString();
            }
        } catch (Exception ignored) {}
        return raw;
    }

    private static String extractAnthropicError(String raw) {
        if (raw == null || raw.isEmpty()) return "Unknown error";
        try {
            JsonObject obj = JsonParser.parseString(raw).getAsJsonObject();
            if (obj.has("error")) {
                JsonObject err = obj.getAsJsonObject("error");
                if (err.has("message")) return err.get("message").getAsString();
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
