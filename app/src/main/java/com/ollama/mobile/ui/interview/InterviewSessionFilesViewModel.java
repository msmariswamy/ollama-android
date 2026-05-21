package com.ollama.mobile.ui.interview;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class InterviewSessionFilesViewModel extends AndroidViewModel {

    private final MutableLiveData<List<File>> sessionFiles = new MutableLiveData<>(new ArrayList<>());

    public InterviewSessionFilesViewModel(@NonNull Application application) {
        super(application);
        loadFiles();
    }

    public LiveData<List<File>> getSessionFiles() { return sessionFiles; }

    public void loadFiles() {
        File dir = new File(getApplication().getFilesDir(), "interview_sessions");
        if (!dir.exists() || !dir.isDirectory()) {
            sessionFiles.setValue(new ArrayList<>());
            return;
        }
        File[] files = dir.listFiles(f -> f.isFile()
                && (f.getName().endsWith(".txt") || f.getName().endsWith(".wav")));
        if (files == null || files.length == 0) {
            sessionFiles.setValue(new ArrayList<>());
            return;
        }
        List<File> sorted = new ArrayList<>(Arrays.asList(files));
        // Newest first by last-modified timestamp
        Collections.sort(sorted, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        sessionFiles.setValue(sorted);
    }

    public void deleteFile(File file) {
        if (file.exists()) file.delete();
        // Delete the paired file (txt ↔ wav) if it exists
        String name = file.getName();
        String paired = name.endsWith(".txt")
                ? name.replace(".txt", ".wav")
                : name.replace(".wav", ".txt");
        File pairedFile = new File(file.getParent(), paired);
        if (pairedFile.exists()) pairedFile.delete();
        loadFiles();
    }
}
