package com.ollama.mobile.repository;

import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;

import androidx.lifecycle.MutableLiveData;

import com.google.gson.Gson;
import com.ollama.mobile.R;
import com.ollama.mobile.model.ChatMessage;
import com.ollama.mobile.model.ChatRequest;
import com.ollama.mobile.model.InterviewRole;
import com.ollama.mobile.model.RolesConfig;
import com.ollama.mobile.network.OllamaApiService;
import com.ollama.mobile.network.OllamaClient;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.ResponseBody;
import retrofit2.Response;

public class InterviewRepository {

    private static final String TAG = "InterviewRepository";
    private static final int ROLLING_WINDOW = 40;
    private static final String CLOUD_BASE_URL = "https://api.ollama.com/";

    private final OllamaClient ollamaClient;
    private final SettingsRepository settingsRepository;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private AudioManager audioManager;
    private SpeechRecognizer speechRecognizer;
    private volatile boolean sessionActive = false;

    private final StringBuilder fullTranscript = new StringBuilder();
    private final LinkedList<String> rollingLines = new LinkedList<>();

    public final MutableLiveData<String> transcript = new MutableLiveData<>("");
    public final MutableLiveData<String> suggestions = new MutableLiveData<>("");
    public final MutableLiveData<Boolean> suggestionsLoading = new MutableLiveData<>(false);

    private List<InterviewRole> builtInRoles = new ArrayList<>();

    public InterviewRepository(OllamaClient ollamaClient, SettingsRepository settingsRepository) {
        this.ollamaClient = ollamaClient;
        this.settingsRepository = settingsRepository;
    }

    public List<InterviewRole> loadRoles(Context context) {
        try (InputStream is = context.getResources().openRawResource(R.raw.roles_config);
             InputStreamReader reader = new InputStreamReader(is)) {
            RolesConfig config = new Gson().fromJson(reader, RolesConfig.class);
            List<InterviewRole> roles = new ArrayList<>();
            if (config.java != null)
                roles.add(new InterviewRole("java", config.java.title, config.java.description, config.java.technicalSkills));
            if (config.android != null)
                roles.add(new InterviewRole("android", config.android.title, config.android.description, config.android.technicalSkills));
            if (config.ios != null)
                roles.add(new InterviewRole("ios", config.ios.title, config.ios.description, config.ios.technicalSkills));
            if (config.reactnative != null)
                roles.add(new InterviewRole("reactnative", config.reactnative.title, config.reactnative.description, config.reactnative.technicalSkills));
            builtInRoles = roles;
            return roles;
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse roles_config.json", e);
            return Collections.emptyList();
        }
    }

    public List<InterviewRole> getBuiltInRoles() {
        return builtInRoles;
    }

    // ── Speech Recognition ──────────────────────────────────────────────────

    public void startListening(Context context) {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.w(TAG, "SpeechRecognizer not available on this device");
            return;
        }
        sessionActive = true;
        fullTranscript.setLength(0);
        rollingLines.clear();
        transcript.postValue("");

        audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context);
        speechRecognizer.setRecognitionListener(new InterviewRecognitionListener(context));
        beginListening();
    }

    private void beginListening() {
        if (!sessionActive) return;
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L);
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false);
        mainHandler.post(() -> {
            if (sessionActive && speechRecognizer != null) {
                if (audioManager != null) {
                    audioManager.adjustStreamVolume(AudioManager.STREAM_NOTIFICATION, AudioManager.ADJUST_MUTE, 0);
                }
                speechRecognizer.startListening(intent);
                if (audioManager != null) {
                    mainHandler.postDelayed(() -> audioManager.adjustStreamVolume(
                            AudioManager.STREAM_NOTIFICATION, AudioManager.ADJUST_UNMUTE, 0), 100);
                }
            }
        });
    }

    public void stopListening() {
        sessionActive = false;
        if (speechRecognizer != null) {
            mainHandler.post(() -> {
                speechRecognizer.destroy();
                speechRecognizer = null;
            });
        }
    }

    private class InterviewRecognitionListener implements RecognitionListener {
        private final Context context;

        InterviewRecognitionListener(Context ctx) {
            this.context = ctx;
        }

        @Override
        public void onResults(Bundle results) {
            ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            if (matches != null && !matches.isEmpty()) {
                String utterance = matches.get(0).trim();
                if (!utterance.isEmpty()) {
                    appendUtterance(utterance);
                }
            }
            // restart for continuous recognition
            beginListening();
        }

        @Override public void onEndOfSpeech() { /* restart happens in onResults */ }
        @Override public void onError(int error) {
            Log.d(TAG, "SpeechRecognizer error " + error + "; restarting");
            mainHandler.postDelayed(() -> beginListening(), 300);
        }
        @Override public void onReadyForSpeech(Bundle params) {}
        @Override public void onBeginningOfSpeech() {}
        @Override public void onRmsChanged(float rmsdB) {}
        @Override public void onBufferReceived(byte[] buffer) {}
        @Override public void onPartialResults(Bundle partialResults) {}
        @Override public void onEvent(int eventType, Bundle params) {}
    }

    private void appendUtterance(String utterance) {
        if (fullTranscript.length() > 0) fullTranscript.append("\n");
        fullTranscript.append(utterance);

        rollingLines.add(utterance);
        if (rollingLines.size() > ROLLING_WINDOW) rollingLines.removeFirst();

        transcript.postValue(fullTranscript.toString());
    }

    // ── Coaching Suggestions ────────────────────────────────────────────────

    public void getCoachingSuggestions(InterviewRole role, String customRoleName, String cloudModel) {
        if (rollingLines.isEmpty()) return;

        suggestionsLoading.postValue(true);
        List<String> lines = new ArrayList<>(rollingLines);

        executor.execute(() -> {
            try {
                String roleName = (role == InterviewRole.CUSTOM || role.id.equals("custom"))
                        ? customRoleName : role.title;
                String roleDescription = (role == InterviewRole.CUSTOM || role.id.equals("custom"))
                        ? customRoleName : role.description;
                String skillsText = (role.technicalSkills != null && !role.technicalSkills.isEmpty())
                        ? "\nTechnical skills expected: " + String.join(", ", role.technicalSkills)
                        : "";

                String prompt = buildCoachingPrompt(roleName, roleDescription, skillsText, lines);

                List<ChatMessage> messages = new ArrayList<>();
                messages.add(new ChatMessage(ChatMessage.ROLE_USER, prompt));

                ChatRequest request = new ChatRequest(cloudModel, messages, false);
                OllamaApiService cloudApi = buildCloudApiService();
                Response<ResponseBody> response = cloudApi.chat(request).execute();

                if (response.isSuccessful() && response.body() != null) {
                    String body = response.body().string();
                    com.google.gson.JsonObject json = com.google.gson.JsonParser.parseString(body).getAsJsonObject();
                    String content = json.getAsJsonObject("message").get("content").getAsString();
                    suggestions.postValue(content);
                } else {
                    Log.e(TAG, "Coaching call failed HTTP " + response.code());
                    suggestions.postValue("Couldn't get suggestions — check cloud connection.");
                }
            } catch (Exception e) {
                Log.e(TAG, "Coaching call error", e);
                suggestions.postValue("Couldn't get suggestions — check cloud connection.");
            } finally {
                suggestionsLoading.postValue(false);
            }
        });
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

    private String buildCoachingPrompt(String roleName, String roleDescription, String skillsText, List<String> lines) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are an expert technical interviewer observing a real-time technical interview.\n");
        sb.append("Role being evaluated: ").append(roleName).append("\n");
        sb.append("Role description: ").append(roleDescription).append(skillsText).append("\n\n");
        sb.append("Recent transcript:\n");
        for (String line : lines) {
            sb.append(line).append("\n");
        }
        sb.append("\nRespond in under 150 words with:\n");
        sb.append("Current Topic: <topic being discussed>\n");
        sb.append("Follow-up Probes: <2-3 follow-up questions to ask>\n");
        sb.append("Gaps Alert: <important areas not yet covered>");
        return sb.toString();
    }

    public String getFullTranscript() {
        return fullTranscript.toString();
    }
}
