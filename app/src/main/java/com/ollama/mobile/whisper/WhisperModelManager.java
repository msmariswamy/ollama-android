package com.ollama.mobile.whisper;

import android.content.Context;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class WhisperModelManager {

    private static final String TAG = "WhisperModelManager";

    public enum WhisperModel {
        BASE_EN(
                "ggml-base.en.bin",
                "base.en",
                "142 MB",
                "English only",
                "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.en.bin",
                100_000_000L
        ),
        SMALL(
                "ggml-small.bin",
                "small",
                "466 MB",
                "Multilingual (99 languages)",
                "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small.bin",
                400_000_000L
        );

        public final String filename;
        public final String displayName;
        public final String sizeStr;
        public final String description;
        public final String url;
        public final long minValidSize;

        WhisperModel(String filename, String displayName, String sizeStr,
                     String description, String url, long minValidSize) {
            this.filename = filename;
            this.displayName = displayName;
            this.sizeStr = sizeStr;
            this.description = description;
            this.url = url;
            this.minValidSize = minValidSize;
        }
    }

    public enum State { NOT_DOWNLOADED, DOWNLOADING, READY, ERROR }

    private final WhisperModel model;
    private final MutableLiveData<State>   stateLive    = new MutableLiveData<>();
    private final MutableLiveData<Integer> progressLive = new MutableLiveData<>(0);
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public WhisperModelManager(Context context, WhisperModel model) {
        this.model = model;
        stateLive.setValue(isModelPresent(context, model) ? State.READY : State.NOT_DOWNLOADED);
    }

    public static File getModelFile(Context context, WhisperModel model) {
        return new File(context.getFilesDir(), model.filename);
    }

    public static boolean isModelPresent(Context context, WhisperModel model) {
        File f = getModelFile(context, model);
        return f.exists() && f.length() >= model.minValidSize;
    }

    public LiveData<State>   getState()    { return stateLive; }
    public LiveData<Integer> getProgress() { return progressLive; }

    public void download(Context context) {
        if (stateLive.getValue() == State.DOWNLOADING) return;
        stateLive.postValue(State.DOWNLOADING);
        progressLive.postValue(0);

        executor.execute(() -> {
            File dest = getModelFile(context, model);
            File tmp  = new File(dest.getParentFile(), dest.getName() + ".tmp");
            try {
                OkHttpClient client = new OkHttpClient.Builder()
                        .connectTimeout(30, TimeUnit.SECONDS)
                        .readTimeout(300, TimeUnit.SECONDS)
                        .build();
                Request req = new Request.Builder().url(model.url).build();
                try (Response resp = client.newCall(req).execute()) {
                    if (!resp.isSuccessful() || resp.body() == null) {
                        Log.e(TAG, "HTTP " + resp.code());
                        stateLive.postValue(State.ERROR);
                        return;
                    }
                    long total = resp.body().contentLength();
                    try (InputStream in = resp.body().byteStream();
                         FileOutputStream out = new FileOutputStream(tmp)) {
                        byte[] buf = new byte[32_768];
                        long downloaded = 0;
                        int n;
                        while ((n = in.read(buf)) != -1) {
                            out.write(buf, 0, n);
                            downloaded += n;
                            if (total > 0) {
                                progressLive.postValue((int)(downloaded * 100 / total));
                            }
                        }
                    }
                    if (!tmp.renameTo(dest)) {
                        Log.e(TAG, "Rename failed");
                        tmp.delete();
                        stateLive.postValue(State.ERROR);
                        return;
                    }
                    progressLive.postValue(100);
                    stateLive.postValue(State.READY);
                }
            } catch (IOException e) {
                Log.e(TAG, "Download failed", e);
                tmp.delete();
                stateLive.postValue(State.ERROR);
            }
        });
    }

    public void deleteModel(Context context) {
        getModelFile(context, model).delete();
        stateLive.postValue(State.NOT_DOWNLOADED);
        progressLive.postValue(0);
    }
}
