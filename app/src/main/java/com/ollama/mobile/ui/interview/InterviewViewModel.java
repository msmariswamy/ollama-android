package com.ollama.mobile.ui.interview;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.ollama.mobile.OllamaApp;
import com.ollama.mobile.model.InterviewRole;
import com.ollama.mobile.repository.InterviewRepository;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class InterviewViewModel extends AndroidViewModel {

    private static final String TAG = "InterviewViewModel";
    private static final long AUTO_SUGGEST_INTERVAL_MS = 60_000L;

    public enum SessionState { IDLE, ACTIVE, ENDED }

    private final InterviewRepository repository;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final MutableLiveData<SessionState> sessionState = new MutableLiveData<>(SessionState.IDLE);
    private final MutableLiveData<InterviewRole> selectedRole = new MutableLiveData<>();
    private final MutableLiveData<String> savedTranscriptPath = new MutableLiveData<>();

    private InterviewRole activeRole;
    private String customRoleName = "";
    private String activeCloudModel = "";

    private final Runnable autoSuggestRunnable = new Runnable() {
        @Override
        public void run() {
            if (sessionState.getValue() == SessionState.ACTIVE) {
                getSuggestions();
                handler.postDelayed(this, AUTO_SUGGEST_INTERVAL_MS);
            }
        }
    };

    public InterviewViewModel(@NonNull Application application) {
        super(application);
        repository = new InterviewRepository(
                ((OllamaApp) application).getAppContainer().ollamaClient,
                ((OllamaApp) application).getAppContainer().settingsRepository
        );
    }

    public List<InterviewRole> loadRoles() {
        return repository.loadRoles(getApplication());
    }

    public void startSession(InterviewRole role, String customName, String cloudModel) {
        activeRole = role;
        customRoleName = customName != null ? customName : "";
        activeCloudModel = cloudModel != null ? cloudModel : "";
        selectedRole.setValue(role);
        sessionState.setValue(SessionState.ACTIVE);

        Context ctx = getApplication();
        ctx.startService(new Intent(ctx, InterviewSessionService.class));
        repository.startListening(ctx);

        handler.postDelayed(autoSuggestRunnable, AUTO_SUGGEST_INTERVAL_MS);
    }

    public void stopSession() {
        sessionState.setValue(SessionState.ENDED);
        handler.removeCallbacks(autoSuggestRunnable);
        repository.stopListening();

        Context ctx = getApplication();
        ctx.stopService(new Intent(ctx, InterviewSessionService.class));
    }

    public void getSuggestions() {
        if (activeRole == null) return;
        handler.removeCallbacks(autoSuggestRunnable);
        repository.getCoachingSuggestions(activeRole, customRoleName, activeCloudModel);
        if (sessionState.getValue() == SessionState.ACTIVE) {
            handler.postDelayed(autoSuggestRunnable, AUTO_SUGGEST_INTERVAL_MS);
        }
    }

    public File saveTranscript() {
        String text = repository.getFullTranscript();
        if (text == null || text.isEmpty()) return null;
        try {
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            File dir = getApplication().getExternalFilesDir(null);
            if (dir == null) return null;
            File file = new File(dir, "interview_" + timestamp + ".txt");
            try (FileWriter writer = new FileWriter(file)) {
                writer.write(text);
            }
            savedTranscriptPath.postValue(file.getAbsolutePath());
            return file;
        } catch (IOException e) {
            Log.e(TAG, "Failed to save transcript", e);
            return null;
        }
    }

    // ── LiveData accessors ───────────────────────────────────────────────────

    public LiveData<SessionState> getSessionState() { return sessionState; }
    public LiveData<InterviewRole> getSelectedRole() { return selectedRole; }
    public LiveData<String> getTranscript() { return repository.transcript; }
    public LiveData<String> getSuggestionsLiveData() { return repository.suggestions; }
    public LiveData<Boolean> getSuggestionsLoading() { return repository.suggestionsLoading; }
    public LiveData<String> getSavedTranscriptPath() { return savedTranscriptPath; }

    @Override
    protected void onCleared() {
        handler.removeCallbacksAndMessages(null);
        if (sessionState.getValue() == SessionState.ACTIVE) {
            repository.stopListening();
        }
        super.onCleared();
    }
}
