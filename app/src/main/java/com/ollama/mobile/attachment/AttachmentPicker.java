package com.ollama.mobile.attachment;

import android.Manifest;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.google.android.material.snackbar.Snackbar;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class AttachmentPicker {

    private static final int MAX_FILES = 5;
    private static final long MAX_FILE_SIZE_BYTES = 20L * 1024 * 1024; // 20 MB

    private static final String[] MIME_TYPES = {
        "image/*",
        "application/pdf",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "text/markdown",
        "text/plain",
        "audio/mpeg",
        "audio/wav",
        "audio/mp4",
        "audio/ogg"
    };

    public interface Callback {
        void onFilesSelected(List<Uri> uris);
    }

    private final AppCompatActivity activity;
    private final Callback callback;
    private ActivityResultLauncher<String[]> fileLauncher;
    private ActivityResultLauncher<Uri> cameraLauncher;
    private ActivityResultLauncher<String[]> permissionLauncher;
    private Uri cameraOutputUri;

    public AttachmentPicker(AppCompatActivity activity, Callback callback) {
        this.activity = activity;
        this.callback = callback;
        registerLaunchers();
    }

    private void registerLaunchers() {
        fileLauncher = activity.registerForActivityResult(
            new ActivityResultContracts.OpenMultipleDocuments(),
            uris -> {
                if (uris != null && !uris.isEmpty()) onFilesSelected(uris);
            }
        );

        cameraLauncher = activity.registerForActivityResult(
            new ActivityResultContracts.TakePicture(),
            success -> {
                if (Boolean.TRUE.equals(success) && cameraOutputUri != null) {
                    List<Uri> list = new ArrayList<>();
                    list.add(cameraOutputUri);
                    onFilesSelected(list);
                }
            }
        );

        permissionLauncher = activity.registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(),
            result -> {
                boolean allGranted = !result.containsValue(false);
                if (allGranted) {
                    showChooserDialog();
                } else {
                    showPermissionRationale();
                }
            }
        );
    }

    public void showChooserDialog() {
        if (!hasStoragePermission()) {
            requestPermissions();
            return;
        }
        String[] options = {"Gallery / Files", "Camera"};
        new AlertDialog.Builder(activity)
            .setTitle("Attach File")
            .setItems(options, (dialog, which) -> {
                if (which == 0) fileLauncher.launch(MIME_TYPES);
                else launchCamera();
            })
            .show();
    }

    private void launchCamera() {
        try {
            File cacheDir = new File(activity.getCacheDir(), "camera");
            //noinspection ResultOfMethodCallIgnored
            cacheDir.mkdirs();
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            File imageFile = new File(cacheDir, "IMG_" + timestamp + ".jpg");
            cameraOutputUri = FileProvider.getUriForFile(
                activity,
                activity.getPackageName() + ".fileprovider",
                imageFile
            );
            cameraLauncher.launch(cameraOutputUri);
        } catch (Exception e) {
            Toast.makeText(activity, "Could not open camera", Toast.LENGTH_SHORT).show();
        }
    }

    private void onFilesSelected(List<Uri> rawUris) {
        List<Uri> validUris = new ArrayList<>();
        for (Uri uri : rawUris) {
            long size = getFileSize(uri);
            if (size > MAX_FILE_SIZE_BYTES) {
                Toast.makeText(activity, "File too large (max 20 MB)", Toast.LENGTH_SHORT).show();
                continue;
            }
            validUris.add(uri);
        }
        if (validUris.size() > MAX_FILES) {
            Snackbar.make(activity.findViewById(android.R.id.content),
                "Only the first " + MAX_FILES + " files are accepted",
                Snackbar.LENGTH_SHORT).show();
            validUris = validUris.subList(0, MAX_FILES);
        }
        if (!validUris.isEmpty()) callback.onFilesSelected(new ArrayList<>(validUris));
    }

    private long getFileSize(Uri uri) {
        try {
            android.database.Cursor cursor = activity.getContentResolver()
                .query(uri, null, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE);
                if (idx >= 0) {
                    long size = cursor.getLong(idx);
                    cursor.close();
                    return size;
                }
                cursor.close();
            }
        } catch (Exception ignored) {}
        return 0;
    }

    private boolean hasStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(activity, Manifest.permission.READ_MEDIA_IMAGES)
                    == PackageManager.PERMISSION_GRANTED;
        } else {
            return ContextCompat.checkSelfPermission(activity, Manifest.permission.READ_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void requestPermissions() {
        String[] perms;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms = new String[]{
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO
            };
        } else {
            perms = new String[]{Manifest.permission.READ_EXTERNAL_STORAGE};
        }
        permissionLauncher.launch(perms);
    }

    private void showPermissionRationale() {
        new AlertDialog.Builder(activity)
            .setTitle("Storage Permission Required")
            .setMessage("Storage access is required to attach files to your messages.")
            .setPositiveButton("OK", null)
            .show();
    }
}
