package com.ollama.mobile.ui.interview;

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

import java.util.ArrayList;

public class SpeechRecognizerManager {

    private static final String TAG = "SpeechRecMgr";
    private static final long RESTART_DELAY_MS = 100;

    public interface Listener {
        void onPartial(String text);
        void onResult(String text);
        void onUnavailable();
    }

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private SpeechRecognizer recognizer;
    private volatile boolean active = false;
    private Listener listener;
    private Context appContext;

    private int savedMusicVol = -1;

    public void start(Context context, Listener listener) {
        this.appContext = context.getApplicationContext();
        this.listener  = listener;
        this.active    = true;
        mainHandler.post(this::createAndStart);
    }

    public void stop() {
        active = false;
        // Remove pending restarts immediately, then destroy on main thread.
        // Must be synchronous when already on main thread — stopSelf() in the
        // service will destroy the process before a posted runnable can run.
        mainHandler.removeCallbacksAndMessages(null);
        if (Looper.myLooper() == Looper.getMainLooper()) {
            destroyRecognizer();
        } else {
            mainHandler.post(this::destroyRecognizer);
        }
    }

    private void createAndStart() {
        if (!active) return;
        destroyRecognizer();

        if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
            if (listener != null) listener.onUnavailable();
            return;
        }

        recognizer = SpeechRecognizer.createSpeechRecognizer(appContext);
        recognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle p) {
                // Beep has already fired by now — safe to restore volume
                unmuteBeep();
            }
            @Override public void onBeginningOfSpeech() {}
            @Override public void onRmsChanged(float rms) {}
            @Override public void onBufferReceived(byte[] b) {}
            @Override public void onEvent(int t, Bundle p) {}
            @Override public void onEndOfSpeech() {}

            @Override
            public void onPartialResults(Bundle partial) {
                ArrayList<String> list = partial.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (list != null && !list.isEmpty() && listener != null)
                    listener.onPartial(list.get(0));
            }

            @Override
            public void onResults(Bundle results) {
                ArrayList<String> list = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (list != null && !list.isEmpty() && listener != null)
                    listener.onResult(list.get(0));
                scheduleRestart(RESTART_DELAY_MS);
            }

            @Override
            public void onError(int error) {
                Log.d(TAG, "error=" + error);
                long delay = (error == SpeechRecognizer.ERROR_NO_MATCH
                        || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) ? 100 : 300;
                scheduleRestart(delay);
            }
        });

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L);
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L);
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 0L);
        muteBeep();
        recognizer.startListening(intent);
    }

    private void scheduleRestart(long delayMs) {
        if (!active) return;
        mainHandler.postDelayed(this::createAndStart, delayMs);
    }

    private void destroyRecognizer() {
        unmuteBeep(); // safety: restore if stop() called before onReadyForSpeech
        if (recognizer != null) {
            try { recognizer.cancel(); } catch (Exception ignored) {}
            try { recognizer.destroy(); } catch (Exception ignored) {}
            recognizer = null;
        }
    }

    private void muteBeep() {
        try {
            AudioManager am = (AudioManager) appContext.getSystemService(Context.AUDIO_SERVICE);
            if (am == null) return;
            savedMusicVol = am.getStreamVolume(AudioManager.STREAM_MUSIC);
            am.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0);
        } catch (Exception e) {
            Log.w(TAG, "muteBeep failed: " + e.getMessage());
            savedMusicVol = -1;
        }
    }

    private void unmuteBeep() {
        if (savedMusicVol < 0) return;
        try {
            AudioManager am = (AudioManager) appContext.getSystemService(Context.AUDIO_SERVICE);
            if (am != null) am.setStreamVolume(AudioManager.STREAM_MUSIC, savedMusicVol, 0);
        } catch (Exception e) {
            Log.w(TAG, "unmuteBeep failed: " + e.getMessage());
        }
        savedMusicVol = -1;
    }
}
