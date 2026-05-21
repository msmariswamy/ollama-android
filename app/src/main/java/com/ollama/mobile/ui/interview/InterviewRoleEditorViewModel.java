package com.ollama.mobile.ui.interview;

import android.app.Application;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;

import com.google.gson.JsonParser;
import com.ollama.mobile.OllamaApp;
import com.ollama.mobile.model.ChatMessage;
import com.ollama.mobile.model.ChatRequest;
import com.ollama.mobile.model.InterviewRole;
import com.ollama.mobile.network.OllamaApiService;
import com.ollama.mobile.repository.InterviewRoleRepository;
import com.ollama.mobile.repository.SettingsRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.ResponseBody;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class InterviewRoleEditorViewModel extends AndroidViewModel {

    private static final String TAG = "InterviewRoleEditorVM";
    private static final String CLOUD_BASE_URL = "https://api.ollama.com/";

    public final MutableLiveData<List<PromptMessage>> messages = new MutableLiveData<>(new ArrayList<>());
    public final MutableLiveData<Boolean> improving = new MutableLiveData<>(false);
    public final MutableLiveData<String> error = new MutableLiveData<>();

    private InterviewRoleRepository roleRepository;
    private final SettingsRepository settingsRepository;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public InterviewRoleEditorViewModel(@NonNull Application application) {
        super(application);
        settingsRepository = ((OllamaApp) application).getAppContainer().settingsRepository;
    }

    private InterviewRoleRepository getRoleRepository() {
        if (roleRepository == null) {
            roleRepository = new InterviewRoleRepository(getApplication());
        }
        return roleRepository;
    }

    /**
     * Calls cloud API to improve the current prompt based on user feedback.
     * Adds USER message then AI message to the messages list.
     */
    public void improvePrompt(String currentPrompt, String userFeedback, String cloudModel) {
        if (userFeedback == null || userFeedback.trim().isEmpty()) return;

        // Add user message
        addMessage(new PromptMessage(PromptMessage.Type.USER, userFeedback.trim()));
        improving.setValue(true);

        final String feedbackText = userFeedback.trim();
        executor.execute(() -> {
            try {
                String promptText = "You are a prompt engineer. Improve this interview coaching system prompt based on the following feedback.\n\n"
                        + "Current prompt:\n" + currentPrompt
                        + "\n\nFeedback: " + feedbackText
                        + "\n\nReturn ONLY the improved prompt text.";

                List<ChatMessage> msgs = new ArrayList<>();
                msgs.add(new ChatMessage(ChatMessage.ROLE_USER, promptText));
                ChatRequest request = new ChatRequest(cloudModel, msgs, false);

                OllamaApiService cloudApi = buildCloudApiService();
                Response<ResponseBody> response = cloudApi.chat(request).execute();

                if (response.isSuccessful() && response.body() != null) {
                    String body = response.body().string();
                    String content = JsonParser.parseString(body)
                            .getAsJsonObject()
                            .getAsJsonObject("message")
                            .get("content")
                            .getAsString();
                    mainHandler.post(() -> {
                        addMessage(new PromptMessage(PromptMessage.Type.AI, content.trim()));
                        improving.setValue(false);
                    });
                } else {
                    Log.e(TAG, "Improve prompt call failed HTTP " + response.code());
                    mainHandler.post(() -> {
                        error.setValue("Failed to improve prompt. Check your connection.");
                        improving.setValue(false);
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "Improve prompt error", e);
                mainHandler.post(() -> {
                    error.setValue("Error: " + e.getMessage());
                    improving.setValue(false);
                });
            }
        });
    }

    private void addMessage(PromptMessage msg) {
        List<PromptMessage> current = messages.getValue();
        if (current == null) current = new ArrayList<>();
        List<PromptMessage> updated = new ArrayList<>(current);
        updated.add(msg);
        messages.setValue(updated);
    }

    /**
     * Saves the system prompt for an existing role.
     */
    public void saveSystemPrompt(String roleId, String prompt, Context context) {
        getRoleRepository().saveSystemPrompt(roleId, prompt);
    }

    /**
     * Creates and saves a new user-defined role.
     */
    public void saveUserRole(String name, String description, String systemPrompt, Context context) {
        String id = "user_" + System.currentTimeMillis();
        InterviewRole role = new InterviewRole(id, name, description, null);
        role.systemPrompt = systemPrompt;
        getRoleRepository().saveUserRole(role);
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            getRoleRepository().saveSystemPrompt(id, systemPrompt);
        }
    }

    private OllamaApiService buildCloudApiService() {
        String apiKey = settingsRepository.getApiKey();
        OkHttpClient httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(300, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .addInterceptor(chain -> {
                    Request.Builder builder = chain.request().newBuilder();
                    if (apiKey != null && !apiKey.isEmpty()) {
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

    // ── Inner class ──────────────────────────────────────────────────────────

    public static class PromptMessage {
        public enum Type { USER, AI }
        public final Type type;
        public final String text;

        public PromptMessage(Type type, String text) {
            this.type = type;
            this.text = text;
        }
    }
}
