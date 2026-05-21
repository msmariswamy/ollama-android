package com.ollama.mobile.repository;

import android.content.Context;
import android.util.Log;

import androidx.lifecycle.MutableLiveData;

import com.google.gson.Gson;
import com.ollama.mobile.R;
import com.ollama.mobile.audio.AudioCaptureManager;
import com.ollama.mobile.model.ChatMessage;
import com.ollama.mobile.model.ChatRequest;
import com.ollama.mobile.model.InterviewRole;
import com.ollama.mobile.model.RolesConfig;
import com.ollama.mobile.network.OllamaApiService;
import com.ollama.mobile.network.OllamaClient;
import com.ollama.mobile.whisper.WhisperContext;
import com.ollama.mobile.whisper.WhisperModelManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
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

public class InterviewRepository {

    private static final String TAG = "InterviewRepository";
    private static final int ROLLING_WINDOW = 40;
    private static final String CLOUD_BASE_URL = "https://api.ollama.com/";

    private final OllamaClient ollamaClient;
    private final SettingsRepository settingsRepository;
    private final ExecutorService whisperExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService coachingExecutor = Executors.newCachedThreadPool();
    private final ExecutorService audioExecutor    = Executors.newSingleThreadExecutor();

    private WhisperContext whisperContext;
    private AudioCaptureManager captureManager;
    private volatile boolean sessionActive = false;

    private final StringBuilder fullTranscript = new StringBuilder();
    private final StringBuilder fullSuggestions = new StringBuilder();
    private final LinkedList<String> rollingLines = new LinkedList<>();

    public final MutableLiveData<String>  transcript        = new MutableLiveData<>("");
    public final MutableLiveData<String>  suggestions       = new MutableLiveData<>("");
    public final MutableLiveData<Boolean> suggestionsLoading = new MutableLiveData<>(false);
    public final MutableLiveData<Boolean> whisperLoading    = new MutableLiveData<>(false);
    public final MutableLiveData<Boolean> isListening       = new MutableLiveData<>(false);
    public final MutableLiveData<Boolean> audioReady        = new MutableLiveData<>(false);

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

    // ── Audio Capture + Whisper Transcription ───────────────────────────────

    private File rawPcmFile;
    private File sessionAudioFile;

    /**
     * @param audioOutputFile WAV file to write the full session audio to (may be null).
     *                        A temporary PCM file is used during capture; it is converted
     *                        to WAV and deleted on stopListening().
     */
    public void startListening(Context context, File audioOutputFile) {
        sessionActive = true;
        fullTranscript.setLength(0);
        fullSuggestions.setLength(0);
        rollingLines.clear();
        transcript.postValue("");
        suggestions.postValue("");
        audioReady.postValue(false);
        sessionAudioFile = audioOutputFile;
        rawPcmFile = new File(context.getCacheDir(), "session_raw.pcm");

        whisperLoading.postValue(true);
        whisperExecutor.execute(() -> {
            String savedKey = settingsRepository.getWhisperModelKey();
            WhisperModelManager.WhisperModel model;
            try { model = WhisperModelManager.WhisperModel.valueOf(savedKey); }
            catch (Exception e) { model = WhisperModelManager.WhisperModel.BASE_EN; }
            String modelPath = WhisperModelManager.getModelFile(context, model).getAbsolutePath();
            whisperContext = WhisperContext.load(modelPath);
            whisperLoading.postValue(false);

            if (whisperContext == null) {
                Log.e(TAG, "Failed to load Whisper model from " + modelPath);
                return;
            }

            captureManager = new AudioCaptureManager();
            captureManager.start(chunk -> {
                isListening.postValue(true);
                whisperExecutor.execute(() -> {
                    isListening.postValue(false);
                    if (!sessionActive || whisperContext == null) return;
                    String text = whisperContext.transcribe(chunk);
                    Log.d(TAG, "Whisper result: '" + text + "'");
                    if (text != null && !isHallucination(text)) {
                        appendUtterance(text);
                    }
                });
            }, rawPcmFile);
        });
    }

    /**
     * Starts audio recording without Whisper transcription. Used when the Speech Recognizer
     * engine is selected. Audio is captured to a PCM file and converted to WAV on stopListening().
     *
     * @param audioOutputFile WAV file to write the full session audio to (may be null).
     */
    public void initSpeechRecognizerSession() {
        sessionActive = true;
        fullTranscript.setLength(0);
        fullSuggestions.setLength(0);
        rollingLines.clear();
        transcript.postValue("");
        suggestions.postValue("");
        audioReady.postValue(false);
        sessionAudioFile = null;
        rawPcmFile = null;
    }

    public void startWavRecording(Context context, File audioOutputFile) {
        sessionActive = true;
        fullTranscript.setLength(0);
        fullSuggestions.setLength(0);
        rollingLines.clear();
        transcript.postValue("");
        suggestions.postValue("");
        audioReady.postValue(false);
        sessionAudioFile = audioOutputFile;
        rawPcmFile = new File(context.getCacheDir(), "session_raw.pcm");

        captureManager = new AudioCaptureManager();
        captureManager.start(chunk -> { /* chunks discarded — WAV file is written by captureManager */ }, rawPcmFile);
    }

    private static boolean isHallucination(String text) {
        String t = text.trim();
        if (t.isEmpty()) return true;
        return t.equals("[BLANK_AUDIO]") || t.equals("[BLANK]")
                || t.startsWith("(") || t.startsWith("[")
                || t.equalsIgnoreCase("you") || t.equalsIgnoreCase("thank you.");
    }

    public void stopListening() {
        sessionActive = false;
        isListening.postValue(false);
        if (captureManager != null) {
            captureManager.stop();
            captureManager = null;
        }
        // Free Whisper on its own executor (drain queue first)
        whisperExecutor.execute(() -> {
            if (whisperContext != null) {
                whisperContext.free();
                whisperContext = null;
            }
        });

        // Convert PCM → WAV on a dedicated executor — independent of the Whisper backlog
        File pcm = rawPcmFile;
        File wav = sessionAudioFile;
        audioExecutor.execute(() -> {
            if (pcm != null && pcm.exists() && wav != null) {
                try {
                    writePcmToWav(pcm, wav, AudioCaptureManager.SAMPLE_RATE);
                    pcm.delete();
                    Log.d(TAG, "WAV saved: " + wav.getAbsolutePath());
                    audioReady.postValue(true);
                } catch (IOException e) {
                    Log.e(TAG, "Failed to write WAV", e);
                }
            }
        });
    }

    public File getSessionAudioFile() { return sessionAudioFile; }

    // ── WAV utilities ────────────────────────────────────────────────────────

    private static void writePcmToWav(File pcm, File wav, int sampleRate) throws IOException {
        long dataSize = pcm.length();
        try (FileInputStream in  = new FileInputStream(pcm);
             FileOutputStream out = new FileOutputStream(wav)) {
            writeWavHeader(out, dataSize, sampleRate);
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
        }
    }

    private static void writeWavHeader(OutputStream out, long dataSize, int sampleRate)
            throws IOException {
        int channels      = 1;
        int bitsPerSample = 16;
        int byteRate      = sampleRate * channels * bitsPerSample / 8;
        int blockAlign    = channels * bitsPerSample / 8;
        long chunkSize    = 36 + dataSize;

        out.write("RIFF".getBytes());
        writeLe32(out, (int) chunkSize);
        out.write("WAVE".getBytes());
        out.write("fmt ".getBytes());
        writeLe32(out, 16);
        writeLe16(out, (short) 1);
        writeLe16(out, (short) channels);
        writeLe32(out, sampleRate);
        writeLe32(out, byteRate);
        writeLe16(out, (short) blockAlign);
        writeLe16(out, (short) bitsPerSample);
        out.write("data".getBytes());
        writeLe32(out, (int) dataSize);
    }

    private static void writeLe32(OutputStream out, int v) throws IOException {
        out.write(v & 0xFF); out.write((v >> 8) & 0xFF);
        out.write((v >> 16) & 0xFF); out.write((v >> 24) & 0xFF);
    }

    private static void writeLe16(OutputStream out, short v) throws IOException {
        out.write(v & 0xFF); out.write((v >> 8) & 0xFF);
    }

    public void appendFromExternal(String text) {
        appendUtterance(text);
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

        coachingExecutor.execute(() -> {
            try {
                String roleName = (role == InterviewRole.CUSTOM || role.id.equals("custom"))
                        ? customRoleName : role.title;
                String roleDescription = (role == InterviewRole.CUSTOM || role.id.equals("custom"))
                        ? customRoleName : role.description;
                String skillsText = (role.technicalSkills != null && !role.technicalSkills.isEmpty())
                        ? "\nTechnical skills expected: " + String.join(", ", role.technicalSkills)
                        : "";

                String prompt;
                if (role.systemPrompt != null && !role.systemPrompt.isEmpty()) {
                    StringBuilder sb = new StringBuilder(role.systemPrompt);
                    sb.append("\n\nRecent transcript:\n");
                    for (String line : lines) sb.append(line).append("\n");
                    prompt = sb.toString();
                } else {
                    prompt = buildCoachingPrompt(roleName, roleDescription, skillsText, lines);
                }

                List<ChatMessage> messages = new ArrayList<>();
                messages.add(new ChatMessage(ChatMessage.ROLE_USER, prompt));

                ChatRequest request = new ChatRequest(cloudModel, messages, false);
                OllamaApiService cloudApi = buildCloudApiService();
                Response<ResponseBody> response = cloudApi.chat(request).execute();

                if (response.isSuccessful() && response.body() != null) {
                    String body = response.body().string();
                    com.google.gson.JsonObject json = com.google.gson.JsonParser.parseString(body).getAsJsonObject();
                    String content = json.getAsJsonObject("message").get("content").getAsString();
                    String timestamp = new java.text.SimpleDateFormat("HH:mm", java.util.Locale.US)
                            .format(new java.util.Date());
                    if (fullSuggestions.length() > 0) fullSuggestions.append("\n\n");
                    fullSuggestions.append("─── Coaching [").append(timestamp).append("] ───\n");
                    fullSuggestions.append(content);
                    suggestions.postValue(fullSuggestions.toString());
                } else {
                    Log.e(TAG, "Coaching call failed HTTP " + response.code());
                    String errMsg = "Couldn't get suggestions — check cloud connection.";
                    suggestions.postValue(fullSuggestions.length() > 0
                            ? fullSuggestions + "\n\n" + errMsg : errMsg);
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

    private String buildCoachingPrompt(String roleName, String roleDescription,
                                        String skillsText, List<String> lines) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are an expert technical interviewer observing a real-time technical interview.\n");
        sb.append("Role being evaluated: ").append(roleName).append("\n");
        sb.append("Role description: ").append(roleDescription).append(skillsText).append("\n\n");
        sb.append("Recent transcript:\n");
        for (String line : lines) sb.append(line).append("\n");
        sb.append("\nRespond in under 150 words with:\n");
        sb.append("Current Topic: <topic being discussed>\n");
        sb.append("Follow-up Probes: <2-3 follow-up questions to ask>\n");
        sb.append("Gaps Alert: <important areas not yet covered>");
        return sb.toString();
    }

    public String getFullTranscript()   { return fullTranscript.toString(); }
    public String getFullSuggestions()  { return fullSuggestions.toString(); }
}
