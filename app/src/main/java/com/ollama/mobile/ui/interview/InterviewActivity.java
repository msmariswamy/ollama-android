package com.ollama.mobile.ui.interview;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.navigation.NavigationView;
import com.google.android.material.snackbar.Snackbar;
import com.ollama.mobile.R;
import com.ollama.mobile.databinding.ActivityInterviewBinding;
import com.ollama.mobile.model.InterviewRole;
import com.ollama.mobile.ui.model.ModelLibraryActivity;
import com.ollama.mobile.ui.settings.SettingsActivity;

import java.io.File;
import java.util.Arrays;
import java.util.List;

public class InterviewActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener {

    public static final String EXTRA_ROLE_ID = "role_id";
    public static final String EXTRA_ROLE_TITLE = "role_title";
    public static final String EXTRA_ROLE_DESCRIPTION = "role_description";
    public static final String EXTRA_ROLE_SKILLS = "role_skills";
    public static final String EXTRA_CUSTOM_NAME = "custom_name";
    public static final String EXTRA_CLOUD_MODEL = "cloud_model";
    public static final String EXTRA_TRANSCRIPTION_MODE = "transcription_mode";

    private ActivityInterviewBinding binding;
    private InterviewViewModel viewModel;
    private InterviewHomepageViewModel homepageViewModel;

    private InterviewRole selectedRole;
    private String customRoleName;
    private String cloudModel;
    private String transcriptionMode;
    private List<String> availableModels = new java.util.ArrayList<>();

    // Tracks whether we already started the session (to avoid re-starting on re-bind)
    private boolean sessionStarted = false;

    private final ActivityResultLauncher<String> requestMicPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    startSession();
                } else {
                    showMicDeniedDialog();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityInterviewBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        ViewCompat.setOnApplyWindowInsetsListener(binding.interviewContentLayout, (v, insets) -> {
            Insets sys = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(sys.left, sys.top, sys.right, sys.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        setSupportActionBar(binding.toolbar);
        setupDrawer();

        viewModel = new ViewModelProvider(this).get(InterviewViewModel.class);
        homepageViewModel = new ViewModelProvider(this).get(InterviewHomepageViewModel.class);

        extractIntentExtras();
        observeViewModel();
        setupModelSelector();

        binding.btnGetSuggestions.setOnClickListener(v -> viewModel.getSuggestions());
        binding.btnStopSession.setOnClickListener(v -> viewModel.stopSession());
        binding.btnSave.setOnClickListener(v -> openSessionFiles());
        binding.btnShare.setOnClickListener(v -> shareTranscript());
        binding.btnShareAudio.setOnClickListener(v -> shareAudio());

        requestMicPermission.launch(android.Manifest.permission.RECORD_AUDIO);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Don't bind to a new service instance if the session already ended —
        // the new service would start with IDLE and overwrite the ENDED state,
        // hiding the Save/Share buttons.
        InterviewSessionService.SessionState current = viewModel.getSessionState().getValue();
        if (current != InterviewSessionService.SessionState.ENDED) {
            viewModel.bindToService(this);
        }
        // LiveData doesn't re-dispatch unchanged state on resume, so re-evaluate manually.
        updateSaveShareVisibility();
    }

    @Override
    protected void onPause() {
        super.onPause();
        viewModel.unbindFromService(this);
    }

    private void extractIntentExtras() {
        Intent intent = getIntent();
        String roleId = intent.getStringExtra(EXTRA_ROLE_ID);
        String roleTitle = intent.getStringExtra(EXTRA_ROLE_TITLE);
        String roleDescription = intent.getStringExtra(EXTRA_ROLE_DESCRIPTION);
        String[] skillsArray = intent.getStringArrayExtra(EXTRA_ROLE_SKILLS);
        customRoleName = intent.getStringExtra(EXTRA_CUSTOM_NAME);
        cloudModel = intent.getStringExtra(EXTRA_CLOUD_MODEL);
        String transMode = intent.getStringExtra(EXTRA_TRANSCRIPTION_MODE);
        transcriptionMode = transMode != null ? transMode : "WHISPER";

        selectedRole = new InterviewRole(
                roleId != null ? roleId : "custom",
                roleTitle != null ? roleTitle : "Custom",
                roleDescription != null ? roleDescription : "",
                skillsArray != null ? Arrays.asList(skillsArray) : null
        );

        String displayName = (customRoleName != null && !customRoleName.isEmpty())
                ? customRoleName : selectedRole.title;
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(displayName);
        }
    }

    private void startSession() {
        // Guard against restarting after the session has already ended (e.g. config change
        // re-triggers the permission callback while the ViewModel still holds ENDED state).
        InterviewSessionService.SessionState current = viewModel.getSessionState().getValue();
        if (current == InterviewSessionService.SessionState.ENDED) return;
        if (!sessionStarted) {
            sessionStarted = true;
            viewModel.startSession(selectedRole, customRoleName,
                    cloudModel != null ? cloudModel : "",
                    transcriptionMode != null ? transcriptionMode : "WHISPER");
        }
    }

    private void setupModelSelector() {
        homepageViewModel.loadCloudModels();

        homepageViewModel.getCloudModels().observe(this, models -> {
            if (models != null) {
                availableModels.clear();
                availableModels.addAll(models);
            }
        });

        viewModel.getActiveModel().observe(this, model -> {
            binding.tvInterviewModel.setText(
                    (model != null && !model.isEmpty()) ? model : "Select model");
        });

        binding.modelSelectorChip.setOnClickListener(v -> showModelPicker());
    }

    private void showModelPicker() {
        if (availableModels.isEmpty()) {
            Snackbar.make(binding.getRoot(), "No models available — check API key in Settings",
                    Snackbar.LENGTH_SHORT).show();
            return;
        }
        String[] modelArray = availableModels.toArray(new String[0]);
        String current = viewModel.getActiveModel().getValue();
        int checked = current != null ? availableModels.indexOf(current) : -1;

        new AlertDialog.Builder(this)
                .setTitle("Switch AI Model")
                .setSingleChoiceItems(modelArray, checked, (dialog, which) -> {
                    String chosen = availableModels.get(which);
                    viewModel.setActiveModel(chosen);
                    homepageViewModel.saveInterviewModel(chosen);
                    dialog.dismiss();
                    Snackbar.make(binding.getRoot(),
                            "Model switched to " + chosen, Snackbar.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void observeViewModel() {
        viewModel.getWhisperLoading().observe(this, loading -> {
            binding.tvWhisperStatus.setVisibility(Boolean.TRUE.equals(loading) ? View.VISIBLE : View.GONE);
        });

        viewModel.getIsListening().observe(this, listening -> {
            binding.tvListeningStatus.setVisibility(Boolean.TRUE.equals(listening) ? View.VISIBLE : View.GONE);
        });

        viewModel.getAudioReady().observe(this, ready -> {
            binding.btnShareAudio.setEnabled(Boolean.TRUE.equals(ready));
            binding.btnShareAudio.setText(Boolean.TRUE.equals(ready) ? "Audio" : "Audio…");
        });

        viewModel.getTranscript().observe(this, text -> {
            if (text != null && !text.isEmpty()) {
                binding.tvTranscript.setText(text);
                binding.scrollTranscript.post(() ->
                        binding.scrollTranscript.fullScroll(View.FOCUS_DOWN));
            }
            updateSaveShareVisibility();
        });

        viewModel.getSuggestionsLiveData().observe(this, text -> {
            if (text != null && !text.isEmpty()) {
                binding.tvSuggestions.setText(text);
            }
        });

        viewModel.getSuggestionsLoading().observe(this, loading -> {
            binding.progressSuggestions.setVisibility(
                    Boolean.TRUE.equals(loading) ? View.VISIBLE : View.GONE);
        });

        viewModel.getSessionState().observe(this, state -> {
            boolean ended = state == InterviewSessionService.SessionState.ENDED;
            binding.btnStopSession.setVisibility(ended ? View.GONE : View.VISIBLE);
            binding.btnGetSuggestions.setVisibility(ended ? View.GONE : View.VISIBLE);
            updateSaveShareVisibility();
        });
    }

    private void updateSaveShareVisibility() {
        InterviewSessionService.SessionState state = viewModel.getSessionState().getValue();
        boolean ended = state == InterviewSessionService.SessionState.ENDED;
        int visibility = ended ? View.VISIBLE : View.GONE;
        binding.btnSave.setVisibility(visibility);
        binding.btnShare.setVisibility(visibility);
        binding.btnShareAudio.setVisibility(visibility);
    }

    private void openSessionFiles() {
        startActivity(new Intent(this, InterviewSessionFilesActivity.class));
    }

    private void shareTranscript() {
        InterviewSessionService.LocalBinder binder = viewModel.getServiceBinder();
        File file = binder != null ? binder.getService().getSessionFile() : null;
        if (file == null || !file.exists()) {
            Snackbar.make(binding.getRoot(), "No transcript to share", Snackbar.LENGTH_SHORT).show();
            return;
        }
        Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(shareIntent, "Share transcript"));
    }

    private void shareAudio() {
        InterviewSessionService.LocalBinder binder = viewModel.getServiceBinder();
        File file = binder != null ? binder.getService().getSessionAudioFile() : null;
        if (file == null || !file.exists()) return;
        Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("audio/wav");
        shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(shareIntent, "Share session audio"));
    }

    private void showMicDeniedDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Microphone required")
                .setMessage("The microphone permission is required to transcribe the interview. Please grant it in Settings.")
                .setPositiveButton("OK", (d, w) -> finish())
                .show();
    }

    private void setupDrawer() {
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, binding.drawerLayout, binding.toolbar,
                R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        binding.drawerLayout.addDrawerListener(toggle);
        toggle.syncState();
        binding.navigationView.setNavigationItemSelectedListener(this);
        binding.navigationView.setCheckedItem(R.id.nav_interview);
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.nav_conversations) {
            finish();
        } else if (id == R.id.nav_session_files) {
            startActivity(new Intent(this, InterviewSessionFilesActivity.class));
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
