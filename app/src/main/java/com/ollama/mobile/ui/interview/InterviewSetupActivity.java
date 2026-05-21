package com.ollama.mobile.ui.interview;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.navigation.NavigationView;
import com.google.android.material.snackbar.Snackbar;
import com.ollama.mobile.R;
import com.ollama.mobile.databinding.ActivityInterviewSetupBinding;
import com.ollama.mobile.model.InterviewRole;
import com.ollama.mobile.ui.model.ModelLibraryActivity;
import com.ollama.mobile.ui.settings.SettingsActivity;
import com.ollama.mobile.whisper.WhisperModelManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class InterviewSetupActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener {

    private ActivityInterviewSetupBinding binding;
    private InterviewHomepageViewModel viewModel;

    private List<InterviewRole> interviewRoles = new ArrayList<>();
    private List<String> cloudModelNames = new ArrayList<>();
    private ArrayAdapter<String> modelSpinnerAdapter;
    private ArrayAdapter<String> roleSpinnerAdapter;
    private InterviewRole currentSelectedRole;
    private WhisperModelManager whisperModelManager;
    private WhisperModelManager.WhisperModel selectedWhisperModel;
    private ArrayAdapter<String> modeAdapter;
    private String selectedMode = "WHISPER";

    private final ActivityResultLauncher<Intent> roleEditorLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK) {
                    refreshRoles();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityInterviewSetupBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setSupportActionBar(binding.toolbar);
        setupDrawer();

        viewModel = new ViewModelProvider(this).get(InterviewHomepageViewModel.class);

        setupSpinners();
        observeViewModel();
        setupWhisperCard();
        setupTranscriptionModeSpinner();

        binding.btnStartInterviewSession.setOnClickListener(v -> startSession());

        binding.btnEditRole.setOnClickListener(v -> {
            if (currentSelectedRole != null && !currentSelectedRole.id.equals("custom")) {
                String model = cloudModelNames.isEmpty() ? "" :
                        cloudModelNames.get(Math.max(0, binding.spinnerInterviewModel.getSelectedItemPosition()));
                Intent intent = new Intent(this, InterviewRoleEditorActivity.class);
                intent.putExtra(InterviewRoleEditorActivity.EXTRA_ROLE_ID, currentSelectedRole.id);
                intent.putExtra(InterviewRoleEditorActivity.EXTRA_ROLE_TITLE, currentSelectedRole.title);
                intent.putExtra(InterviewRoleEditorActivity.EXTRA_CLOUD_MODEL, model);
                roleEditorLauncher.launch(intent);
            }
        });

        binding.btnAddRole.setOnClickListener(v -> {
            String model = cloudModelNames.isEmpty() ? "" :
                    cloudModelNames.get(Math.max(0, binding.spinnerInterviewModel.getSelectedItemPosition()));
            Intent intent = new Intent(this, InterviewRoleEditorActivity.class);
            intent.putExtra(InterviewRoleEditorActivity.EXTRA_CLOUD_MODEL, model);
            roleEditorLauncher.launch(intent);
        });

        viewModel.loadCloudModels();
    }

    private void setupWhisperCard() {
        // Load saved model selection
        String savedKey = viewModel.getSavedWhisperModelKey();
        try {
            selectedWhisperModel = WhisperModelManager.WhisperModel.valueOf(savedKey);
        } catch (Exception e) {
            selectedWhisperModel = WhisperModelManager.WhisperModel.BASE_EN;
        }

        // Set the correct radio button
        if (selectedWhisperModel == WhisperModelManager.WhisperModel.SMALL) {
            binding.rgWhisperModel.check(R.id.rbModelSmall);
        } else {
            binding.rgWhisperModel.check(R.id.rbModelBaseEn);
        }
        binding.tvWhisperModelDesc.setText(selectedWhisperModel.description);

        // Initialize manager for the saved model
        whisperModelManager = new WhisperModelManager(this, selectedWhisperModel);
        observeWhisperManager();

        binding.btnDownloadWhisper.setOnClickListener(v ->
                whisperModelManager.download(this));

        binding.rgWhisperModel.setOnCheckedChangeListener((group, checkedId) -> {
            WhisperModelManager.WhisperModel newModel;
            if (checkedId == R.id.rbModelSmall) {
                newModel = WhisperModelManager.WhisperModel.SMALL;
            } else {
                newModel = WhisperModelManager.WhisperModel.BASE_EN;
            }
            if (newModel == selectedWhisperModel) return;

            selectedWhisperModel = newModel;
            viewModel.saveWhisperModelKey(selectedWhisperModel.name());
            binding.tvWhisperModelDesc.setText(selectedWhisperModel.description);

            // Re-initialise manager for the new model
            whisperModelManager = new WhisperModelManager(InterviewSetupActivity.this, selectedWhisperModel);
            observeWhisperManager();
            updateStartButton();
        });
    }

    private void setupTranscriptionModeSpinner() {
        List<String> labels = Arrays.asList("Whisper (Offline, Multilingual)", "Speech Recognizer (Real-time)");
        modeAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, labels);
        modeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.spinnerTranscriptionMode.setAdapter(modeAdapter);

        selectedMode = viewModel.getTranscriptionMode();
        binding.spinnerTranscriptionMode.setSelection(selectedMode.equals("SPEECH_RECOGNIZER") ? 1 : 0);
        updateWhisperCardForMode(selectedMode);

        binding.spinnerTranscriptionMode.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                selectedMode = (pos == 1) ? "SPEECH_RECOGNIZER" : "WHISPER";
                viewModel.saveTranscriptionMode(selectedMode);
                updateWhisperCardForMode(selectedMode);
                updateStartButton();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void updateWhisperCardForMode(String mode) {
        boolean isSR = "SPEECH_RECOGNIZER".equals(mode);
        binding.tvWhisperModelNote.setVisibility(isSR ? View.VISIBLE : View.GONE);
        if (isSR) {
            binding.tvWhisperModelNote.setText("Optional: if downloaded, Whisper will enhance the saved transcript after the session.");
        }
    }

    private void observeWhisperManager() {
        whisperModelManager.getState().observe(this, state -> {
            switch (state) {
                case NOT_DOWNLOADED:
                    binding.tvWhisperStatus.setText("Not downloaded");
                    binding.progressWhisper.setVisibility(View.GONE);
                    binding.btnDownloadWhisper.setEnabled(true);
                    binding.btnDownloadWhisper.setText("Download Model (" + selectedWhisperModel.sizeStr + ")");
                    binding.btnStartInterviewSession.setEnabled(false);
                    break;
                case DOWNLOADING:
                    binding.tvWhisperStatus.setText("Downloading…");
                    binding.progressWhisper.setVisibility(View.VISIBLE);
                    binding.btnDownloadWhisper.setEnabled(false);
                    binding.btnDownloadWhisper.setText("Downloading…");
                    binding.btnStartInterviewSession.setEnabled(false);
                    break;
                case READY:
                    binding.tvWhisperStatus.setText("Ready");
                    binding.progressWhisper.setVisibility(View.GONE);
                    binding.btnDownloadWhisper.setEnabled(false);
                    binding.btnDownloadWhisper.setText("Downloaded");
                    updateStartButton();
                    break;
                case ERROR:
                    binding.tvWhisperStatus.setText("Download failed");
                    binding.progressWhisper.setVisibility(View.GONE);
                    binding.btnDownloadWhisper.setEnabled(true);
                    binding.btnDownloadWhisper.setText("Retry Download");
                    binding.btnStartInterviewSession.setEnabled(false);
                    Toast.makeText(this, "Model download failed — check your connection", Toast.LENGTH_SHORT).show();
                    break;
            }
        });

        whisperModelManager.getProgress().observe(this, pct ->
                binding.progressWhisper.setProgress(pct));
    }

    private void updateStartButton() {
        Boolean apiKeyMissing = viewModel.getApiKeyMissing().getValue();
        boolean modelReady = "SPEECH_RECOGNIZER".equals(selectedMode)
                || whisperModelManager.getState().getValue() == WhisperModelManager.State.READY;
        binding.btnStartInterviewSession.setEnabled(modelReady && !Boolean.TRUE.equals(apiKeyMissing));
    }

    private void setupSpinners() {
        modelSpinnerAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, cloudModelNames);
        modelSpinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.spinnerInterviewModel.setAdapter(modelSpinnerAdapter);

        interviewRoles = viewModel.loadRoles();
        List<String> roleTitles = new ArrayList<>();
        for (InterviewRole r : interviewRoles) roleTitles.add(r.title);
        roleTitles.add("Custom");
        roleSpinnerAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, roleTitles);
        roleSpinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.spinnerInterviewRole.setAdapter(roleSpinnerAdapter);

        binding.spinnerInterviewRole.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                boolean isCustom = pos == interviewRoles.size();
                binding.tilCustomRole.setVisibility(isCustom ? View.VISIBLE : View.GONE);
                currentSelectedRole = isCustom ? InterviewRole.CUSTOM : interviewRoles.get(pos);
                binding.btnEditRole.setVisibility(!isCustom ? View.VISIBLE : View.GONE);
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void observeViewModel() {
        viewModel.getApiKeyMissing().observe(this, missing -> {
            boolean noKey = Boolean.TRUE.equals(missing);
            binding.cardApiKeyWarning.setVisibility(noKey ? View.VISIBLE : View.GONE);
            binding.spinnerInterviewModel.setEnabled(!noKey);
            updateStartButton();
        });

        viewModel.getCloudModels().observe(this, models -> {
            cloudModelNames.clear();
            if (models != null) cloudModelNames.addAll(models);
            modelSpinnerAdapter.notifyDataSetChanged();

            String saved = viewModel.getSavedInterviewModel();
            if (!saved.isEmpty()) {
                int idx = cloudModelNames.indexOf(saved);
                if (idx >= 0) binding.spinnerInterviewModel.setSelection(idx);
            }
        });
    }

    private void startSession() {
        int modelIdx = binding.spinnerInterviewModel.getSelectedItemPosition();
        if (cloudModelNames.isEmpty() || modelIdx < 0 || modelIdx >= cloudModelNames.size()) {
            Snackbar.make(binding.getRoot(), "Select a cloud model first", Snackbar.LENGTH_SHORT).show();
            return;
        }
        String selectedModel = cloudModelNames.get(modelIdx);
        viewModel.saveInterviewModel(selectedModel);

        int roleIdx = binding.spinnerInterviewRole.getSelectedItemPosition();
        String customName = "";
        InterviewRole role;
        if (roleIdx >= interviewRoles.size()) {
            customName = binding.etCustomRole.getText() != null
                    ? binding.etCustomRole.getText().toString().trim() : "";
            if (customName.isEmpty()) {
                Snackbar.make(binding.getRoot(), "Enter a custom role name", Snackbar.LENGTH_SHORT).show();
                return;
            }
            role = InterviewRole.CUSTOM;
        } else {
            role = interviewRoles.get(roleIdx);
        }

        Intent intent = new Intent(this, InterviewActivity.class);
        intent.putExtra(InterviewActivity.EXTRA_ROLE_ID, role.id);
        intent.putExtra(InterviewActivity.EXTRA_ROLE_TITLE, role.title);
        intent.putExtra(InterviewActivity.EXTRA_ROLE_DESCRIPTION, role.description);
        if (role.technicalSkills != null) {
            intent.putExtra(InterviewActivity.EXTRA_ROLE_SKILLS,
                    role.technicalSkills.toArray(new String[0]));
        }
        intent.putExtra(InterviewActivity.EXTRA_CUSTOM_NAME, customName);
        intent.putExtra(InterviewActivity.EXTRA_CLOUD_MODEL, selectedModel);
        intent.putExtra(InterviewActivity.EXTRA_TRANSCRIPTION_MODE, selectedMode);
        startActivity(intent);
    }

    private void refreshRoles() {
        int prevPos = binding.spinnerInterviewRole.getSelectedItemPosition();
        interviewRoles = viewModel.loadRoles();
        List<String> roleTitles = new ArrayList<>();
        for (InterviewRole r : interviewRoles) roleTitles.add(r.title);
        roleTitles.add("Custom");
        roleSpinnerAdapter.clear();
        roleSpinnerAdapter.addAll(roleTitles);
        roleSpinnerAdapter.notifyDataSetChanged();
        int newPos = Math.min(prevPos, roleSpinnerAdapter.getCount() - 1);
        binding.spinnerInterviewRole.setSelection(newPos);
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
