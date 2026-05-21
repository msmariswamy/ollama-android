package com.ollama.mobile.ui.interview;

import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;

import com.ollama.mobile.model.InterviewRole;

public class InterviewViewModel extends AndroidViewModel {

    // MediatorLiveData proxies — populated once service is bound
    private final MediatorLiveData<InterviewSessionService.SessionState> sessionState =
            new MediatorLiveData<>(InterviewSessionService.SessionState.IDLE);
    private final MediatorLiveData<String> transcript = new MediatorLiveData<>();
    private final MediatorLiveData<String> suggestions = new MediatorLiveData<>();
    private final MediatorLiveData<Boolean> suggestionsLoading = new MediatorLiveData<>(false);
    private final MediatorLiveData<String>  activeModel    = new MediatorLiveData<>("");
    private final MediatorLiveData<Boolean> whisperLoading = new MediatorLiveData<>(false);
    private final MediatorLiveData<Boolean> isListening    = new MediatorLiveData<>(false);
    private final MediatorLiveData<Boolean> audioReady     = new MediatorLiveData<>(false);

    @Nullable
    private InterviewSessionService.LocalBinder serviceBinder;
    private boolean bound = false;

    // Pending session — stored when startSession() is called before the service is bound
    private InterviewRole pendingRole;
    private String pendingCustomName;
    private String pendingCloudModel;
    private String pendingTranscriptionMode;
    private boolean hasPendingSession = false;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            serviceBinder = (InterviewSessionService.LocalBinder) service;
            InterviewSessionService svc = serviceBinder.getService();
            sessionState.addSource(svc.getSessionState(), sessionState::setValue);
            transcript.addSource(svc.getTranscript(), transcript::setValue);
            suggestions.addSource(svc.getSuggestionsLiveData(), suggestions::setValue);
            suggestionsLoading.addSource(svc.getSuggestionsLoading(), suggestionsLoading::setValue);
            activeModel.addSource(svc.getActiveModel(), activeModel::setValue);
            whisperLoading.addSource(svc.getWhisperLoading(), whisperLoading::setValue);
            isListening.addSource(svc.getIsListening(), isListening::setValue);
            audioReady.addSource(svc.getAudioReady(), audioReady::setValue);

            // Start any session that was requested before the service was ready
            if (hasPendingSession) {
                hasPendingSession = false;
                svc.startSession(pendingRole, pendingCustomName, pendingCloudModel, pendingTranscriptionMode);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            // Called only on unexpected disconnects (service crash). Normal unbind
            // goes through unbindFromService() which calls removeSources() directly.
            if (serviceBinder != null) {
                removeSources(serviceBinder.getService());
                serviceBinder = null;
            }
            bound = false;
        }
    };

    public InterviewViewModel(@NonNull Application application) {
        super(application);
    }

    // ── Service Binding ──────────────────────────────────────────────────────

    public void bindToService(Context context) {
        if (bound) return;
        Intent intent = new Intent(context, InterviewSessionService.class);
        context.startService(intent);
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
        bound = true;
    }

    public void unbindFromService(Context context) {
        if (!bound) return;
        // Remove MediatorLiveData sources before unbinding — onServiceDisconnected
        // is NOT called for normal unbinds, so we must clean up here.
        if (serviceBinder != null) {
            removeSources(serviceBinder.getService());
            serviceBinder = null;
        }
        context.unbindService(serviceConnection);
        bound = false;
    }

    private void removeSources(InterviewSessionService svc) {
        sessionState.removeSource(svc.getSessionState());
        transcript.removeSource(svc.getTranscript());
        suggestions.removeSource(svc.getSuggestionsLiveData());
        suggestionsLoading.removeSource(svc.getSuggestionsLoading());
        activeModel.removeSource(svc.getActiveModel());
        whisperLoading.removeSource(svc.getWhisperLoading());
        isListening.removeSource(svc.getIsListening());
        audioReady.removeSource(svc.getAudioReady());
    }

    @Nullable
    public InterviewSessionService.LocalBinder getServiceBinder() {
        return serviceBinder;
    }

    // ── Session Control (delegated to service) ───────────────────────────────

    public void startSession(InterviewRole role, String customName, String cloudModel, String transcriptionMode) {
        // Show the model in the UI immediately — don't wait for service round-trip
        activeModel.setValue(cloudModel != null ? cloudModel : "");

        if (serviceBinder != null) {
            serviceBinder.getService().startSession(role, customName, cloudModel, transcriptionMode);
        } else {
            // Service not yet bound (permission callback fired before onResume).
            // Store and replay in onServiceConnected.
            pendingRole = role;
            pendingCustomName = customName;
            pendingCloudModel = cloudModel;
            pendingTranscriptionMode = transcriptionMode;
            hasPendingSession = true;
        }
    }

    public void stopSession() {
        if (serviceBinder != null) {
            serviceBinder.getService().stopSession();
        }
    }

    public void getSuggestions() {
        if (serviceBinder != null) {
            serviceBinder.getService().getSuggestions();
        }
    }

    public void setActiveModel(String model) {
        activeModel.setValue(model != null ? model : "");
        if (serviceBinder != null) {
            serviceBinder.getService().setActiveModel(model);
        }
    }

    // ── LiveData Accessors ───────────────────────────────────────────────────

    public LiveData<InterviewSessionService.SessionState> getSessionState() { return sessionState; }
    public LiveData<String> getTranscript() { return transcript; }
    public LiveData<String> getSuggestionsLiveData() { return suggestions; }
    public LiveData<Boolean> getSuggestionsLoading() { return suggestionsLoading; }
    public LiveData<Boolean> getWhisperLoading()     { return whisperLoading; }
    public LiveData<Boolean> getIsListening()        { return isListening; }
    public LiveData<Boolean> getAudioReady()         { return audioReady; }
    public LiveData<String> getActiveModel() { return activeModel; }
}
