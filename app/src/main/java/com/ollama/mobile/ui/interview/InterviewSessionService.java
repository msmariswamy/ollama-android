package com.ollama.mobile.ui.interview;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;

import com.ollama.mobile.OllamaApp;
import com.ollama.mobile.R;
import com.ollama.mobile.model.InterviewRole;
import com.ollama.mobile.repository.InterviewRepository;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class InterviewSessionService extends Service {

    private static final String TAG = "InterviewSessionService";
    private static final String CHANNEL_ID = "interview_session";
    private static final int NOTIFICATION_ID = 1001;
    private static final long AUTO_SUGGEST_INTERVAL_MS = 60_000L;

    public enum SessionState { IDLE, ACTIVE, ENDED }

    public class LocalBinder extends Binder {
        public InterviewSessionService getService() { return InterviewSessionService.this; }
    }

    private final IBinder binder = new LocalBinder();
    private InterviewRepository repository;

    private InterviewRole activeRole;
    private String customRoleName = "";
    private String activeCloudModel = "";
    private String activeTranscriptionMode = "WHISPER";
    private SpeechRecognizerManager srManager;

    private final MutableLiveData<SessionState> sessionState = new MutableLiveData<>(SessionState.IDLE);
    private final MutableLiveData<String> activeModelLiveData = new MutableLiveData<>("");

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable autoSuggestRunnable = new Runnable() {
        @Override
        public void run() {
            if (sessionState.getValue() == SessionState.ACTIVE) {
                getSuggestions();
                handler.postDelayed(this, AUTO_SUGGEST_INTERVAL_MS);
            }
        }
    };

    private File sessionFile;

    // Observe repository transcript to write snapshot on each update
    private final Observer<String> transcriptObserver = text -> {
        if (text != null && !text.isEmpty() && sessionFile != null) {
            writeSnapshot();
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        OllamaApp app = (OllamaApp) getApplication();
        repository = new InterviewRepository(
                app.getAppContainer().ollamaClient,
                app.getAppContainer().settingsRepository
        );
        repository.transcript.observeForever(transcriptObserver);
    }

    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        startForeground(NOTIFICATION_ID, buildNotification());
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        repository.transcript.removeObserver(transcriptObserver);
        if (sessionState.getValue() == SessionState.ACTIVE) {
            if (srManager != null) {
                srManager.stop();
                srManager = null;
            }
            repository.stopListening();
        }
        stopForeground(true);
        super.onDestroy();
    }

    // ── Session Control ──────────────────────────────────────────────────────

    public void startSession(InterviewRole role, String customName, String cloudModel, String transcriptionMode) {
        activeRole = role;
        customRoleName = customName != null ? customName : "";
        activeCloudModel = cloudModel != null ? cloudModel : "";
        activeTranscriptionMode = transcriptionMode != null ? transcriptionMode : "WHISPER";
        activeModelLiveData.setValue(activeCloudModel);
        sessionState.setValue(SessionState.ACTIVE);

        initSessionFile();
        File audioFile = sessionFile != null
                ? new File(sessionFile.getParent(),
                           sessionFile.getName().replace(".txt", ".wav"))
                : null;

        if ("SPEECH_RECOGNIZER".equals(activeTranscriptionMode)) {
            // SpeechRecognizer holds exclusive mic access — cannot run AudioRecord in parallel.
            // Skip WAV recording; transcript is provided by SR restart loop.
            repository.initSpeechRecognizerSession();
            srManager = new SpeechRecognizerManager();
            srManager.start(this, new SpeechRecognizerManager.Listener() {
                @Override public void onPartial(String text) { /* optional: could show partial */ }
                @Override public void onResult(String text) { repository.appendFromExternal(text); }
                @Override public void onUnavailable() { /* fallback or log */ }
            });
        } else {
            repository.startListening(this, audioFile);
            srManager = null;
        }

        handler.postDelayed(autoSuggestRunnable, AUTO_SUGGEST_INTERVAL_MS);
    }

    public void stopSession() {
        sessionState.setValue(SessionState.ENDED);
        handler.removeCallbacks(autoSuggestRunnable);
        if (srManager != null) {
            srManager.stop();
            srManager = null;
        }
        repository.stopListening();
        writeSnapshot();
        stopForeground(true);
        stopSelf();
    }

    public void getSuggestions() {
        if (activeRole == null) return;
        handler.removeCallbacks(autoSuggestRunnable);
        repository.getCoachingSuggestions(activeRole, customRoleName, activeCloudModel);
        if (sessionState.getValue() == SessionState.ACTIVE) {
            handler.postDelayed(autoSuggestRunnable, AUTO_SUGGEST_INTERVAL_MS);
        }
    }

    public void setActiveModel(String model) {
        activeCloudModel = model != null ? model : "";
        activeModelLiveData.setValue(activeCloudModel);
    }

    // ── File Persistence ─────────────────────────────────────────────────────

    private void initSessionFile() {
        File dir = new File(getFilesDir(), "interview_sessions");
        if (!dir.exists()) dir.mkdirs();

        String roleId = (activeRole != null && !activeRole.id.equals("custom"))
                ? activeRole.id : "custom";
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        sessionFile = new File(dir, "interview_" + timestamp + "_" + roleId + ".txt");

        String displayRole = resolveDisplayRole();
        try (FileWriter writer = new FileWriter(sessionFile, false)) {
            writer.write(buildHeader(displayRole));
            writer.write("(Session in progress…)\n");
        } catch (IOException e) {
            Log.e(TAG, "Failed to create session file", e);
        }
    }

    private void writeSnapshot() {
        if (sessionFile == null) return;
        String transcript = repository.getFullTranscript();
        String suggestions = repository.getFullSuggestions();
        if (transcript.isEmpty() && suggestions.isEmpty()) return;

        StringBuilder content = new StringBuilder();
        content.append(buildHeader(resolveDisplayRole()));
        if (!transcript.isEmpty()) {
            content.append("TRANSCRIPT\n──────────\n").append(transcript).append("\n\n");
        }
        if (!suggestions.isEmpty()) {
            content.append("AI COACHING NOTES\n─────────────────\n").append(suggestions);
        }

        try (FileWriter writer = new FileWriter(sessionFile, false)) {
            writer.write(content.toString());
        } catch (IOException e) {
            Log.e(TAG, "Failed to write session snapshot", e);
        }
    }

    private String buildHeader(String displayRole) {
        String date = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(new Date());
        return "Ollama Mobile — Interview Session\n"
                + "Role: " + displayRole + "\n"
                + "Model: " + activeCloudModel + "\n"
                + "Date: " + date + "\n"
                + "═══════════════════════════════\n\n";
    }

    private String resolveDisplayRole() {
        if (customRoleName != null && !customRoleName.isEmpty()) return customRoleName;
        if (activeRole != null) return activeRole.title;
        return "Interview";
    }

    // ── LiveData Accessors ───────────────────────────────────────────────────

    public LiveData<SessionState> getSessionState() { return sessionState; }
    public LiveData<String>  getTranscript()        { return repository.transcript; }
    public LiveData<String>  getSuggestionsLiveData(){ return repository.suggestions; }
    public LiveData<Boolean> getSuggestionsLoading() { return repository.suggestionsLoading; }
    public LiveData<Boolean> getWhisperLoading()    { return repository.whisperLoading; }
    public LiveData<Boolean> getIsListening()       { return repository.isListening; }
    public LiveData<Boolean> getAudioReady()        { return repository.audioReady; }
    public LiveData<String>  getActiveModel()       { return activeModelLiveData; }

    @Nullable
    public File getSessionFile() { return sessionFile; }

    @Nullable
    public File getSessionAudioFile() { return repository.getSessionAudioFile(); }

    // ── Notification ─────────────────────────────────────────────────────────

    private Notification buildNotification() {
        Intent tapIntent = new Intent(this, InterviewActivity.class);
        tapIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, tapIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Interview session active")
                .setContentText("Tap to return to the session")
                .setSmallIcon(R.drawable.ic_interview)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Interview Session",
                NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Keeps the microphone active during a live interview session");
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.createNotificationChannel(channel);
    }
}
