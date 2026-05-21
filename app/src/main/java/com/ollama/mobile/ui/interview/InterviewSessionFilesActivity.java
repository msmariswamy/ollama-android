package com.ollama.mobile.ui.interview;

import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.core.view.GravityCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.navigation.NavigationView;
import com.google.android.material.snackbar.Snackbar;
import com.ollama.mobile.R;
import com.ollama.mobile.databinding.ActivityInterviewSessionFilesBinding;
import com.ollama.mobile.ui.model.ModelLibraryActivity;
import com.ollama.mobile.ui.settings.SettingsActivity;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;

public class InterviewSessionFilesActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener {

    private static final String TAG = "SessionFilesActivity";

    private ActivityInterviewSessionFilesBinding binding;
    private InterviewSessionFilesViewModel viewModel;
    private InterviewSessionFilesAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityInterviewSessionFilesBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Interview Sessions");
        }
        setupDrawer();

        viewModel = new ViewModelProvider(this).get(InterviewSessionFilesViewModel.class);
        adapter = new InterviewSessionFilesAdapter(
                file -> {
                    if (file.getName().endsWith(".wav")) shareFile(file);
                    else openViewer(file);
                },
                this::downloadFile, this::shareFile, this::confirmDelete);

        binding.rvSessionFiles.setLayoutManager(new LinearLayoutManager(this));
        binding.rvSessionFiles.setAdapter(adapter);

        viewModel.getSessionFiles().observe(this, files -> {
            adapter.setFiles(files);
            binding.tvEmptyState.setVisibility(files == null || files.isEmpty() ? View.VISIBLE : View.GONE);
            binding.rvSessionFiles.setVisibility(files != null && !files.isEmpty() ? View.VISIBLE : View.GONE);
        });
    }

    private void openViewer(File file) {
        Intent intent = new Intent(this, InterviewSessionViewerActivity.class);
        intent.putExtra(InterviewSessionViewerActivity.EXTRA_FILE_PATH, file.getAbsolutePath());
        startActivity(intent);
    }

    private void downloadFile(File file) {
        try {
            String mime = file.getName().endsWith(".wav") ? "audio/wav" : "text/plain";
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Downloads.DISPLAY_NAME, file.getName());
                values.put(MediaStore.Downloads.MIME_TYPE, mime);
                values.put(MediaStore.Downloads.IS_PENDING, 1);
                Uri uri = getContentResolver().insert(
                        MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values);
                if (uri == null) throw new IOException("MediaStore insert returned null");
                try (OutputStream out = getContentResolver().openOutputStream(uri);
                     FileInputStream in = new FileInputStream(file)) {
                    byte[] buf = new byte[8192];
                    int len;
                    while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
                }
                values.clear();
                values.put(MediaStore.Downloads.IS_PENDING, 0);
                getContentResolver().update(uri, values, null, null);
            } else {
                File dst = new File(
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                        file.getName());
                try (FileInputStream in = new FileInputStream(file);
                     OutputStream out = new java.io.FileOutputStream(dst)) {
                    byte[] buf = new byte[8192];
                    int len;
                    while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
                }
            }
            Snackbar.make(binding.getRoot(), "Saved to Downloads", Snackbar.LENGTH_SHORT).show();
        } catch (IOException e) {
            Log.e(TAG, "Download failed", e);
            Snackbar.make(binding.getRoot(), "Download failed", Snackbar.LENGTH_SHORT).show();
        }
    }

    private void shareFile(File file) {
        String mime = file.getName().endsWith(".wav") ? "audio/wav" : "text/plain";
        Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType(mime);
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(intent, "Share session"));
    }

    private void confirmDelete(File file) {
        new AlertDialog.Builder(this)
                .setTitle("Delete session")
                .setMessage("Delete \"" + file.getName() + "\"? This cannot be undone.")
                .setPositiveButton("Delete", (d, w) -> viewModel.deleteFile(file))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void setupDrawer() {
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, binding.drawerLayout, binding.toolbar,
                R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        binding.drawerLayout.addDrawerListener(toggle);
        toggle.syncState();
        binding.navigationView.setNavigationItemSelectedListener(this);
        binding.navigationView.setCheckedItem(R.id.nav_session_files);
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.nav_conversations) {
            finish();
        } else if (id == R.id.nav_interview) {
            startActivity(new Intent(this, InterviewSetupActivity.class));
        } else if (id == R.id.nav_session_files) {
            // already here
        } else if (id == R.id.nav_models) {
            startActivity(new Intent(this, ModelLibraryActivity.class));
        } else if (id == R.id.nav_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
        }
        binding.drawerLayout.closeDrawer(GravityCompat.START);
        return true;
    }

    @Override
    public void onBackPressed() {
        if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
            binding.drawerLayout.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }
}
