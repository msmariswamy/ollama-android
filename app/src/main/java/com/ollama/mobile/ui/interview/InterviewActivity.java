package com.ollama.mobile.ui.interview;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.snackbar.Snackbar;
import com.ollama.mobile.databinding.ActivityInterviewBinding;
import com.ollama.mobile.model.InterviewRole;

import java.io.File;
import java.util.Arrays;

public class InterviewActivity extends AppCompatActivity {

    private ActivityInterviewBinding binding;
    private InterviewViewModel viewModel;

    private InterviewRole selectedRole;
    private String customRoleName;

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

        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets sys = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(sys.left, sys.top, sys.right, sys.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        viewModel = new ViewModelProvider(this).get(InterviewViewModel.class);

        extractIntentExtras();
        observeViewModel();

        binding.btnGetSuggestions.setOnClickListener(v -> viewModel.getSuggestions());
        binding.btnStopSession.setOnClickListener(v -> viewModel.stopSession());
        binding.btnSaveShare.setOnClickListener(v -> saveAndShare());

        requestMicPermission.launch(android.Manifest.permission.RECORD_AUDIO);
    }

    private void extractIntentExtras() {
        Intent intent = getIntent();
        String roleId = intent.getStringExtra(InterviewSetupActivity.EXTRA_ROLE_ID);
        String roleTitle = intent.getStringExtra(InterviewSetupActivity.EXTRA_ROLE_TITLE);
        String roleDescription = intent.getStringExtra(InterviewSetupActivity.EXTRA_ROLE_DESCRIPTION);
        String[] skillsArray = intent.getStringArrayExtra(InterviewSetupActivity.EXTRA_ROLE_SKILLS);
        customRoleName = intent.getStringExtra(InterviewSetupActivity.EXTRA_CUSTOM_NAME);

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
        viewModel.startSession(selectedRole, customRoleName);
    }

    private void observeViewModel() {
        viewModel.getTranscript().observe(this, text -> {
            if (text != null && !text.isEmpty()) {
                binding.tvTranscript.setText(text);
                binding.scrollTranscript.post(() ->
                        binding.scrollTranscript.fullScroll(View.FOCUS_DOWN));
            }
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
            boolean ended = state == InterviewViewModel.SessionState.ENDED;
            binding.btnStopSession.setVisibility(ended ? View.GONE : View.VISIBLE);
            binding.btnGetSuggestions.setVisibility(ended ? View.GONE : View.VISIBLE);
            boolean hasTranscript = viewModel.getTranscript().getValue() != null
                    && !viewModel.getTranscript().getValue().isEmpty();
            binding.btnSaveShare.setVisibility(ended && hasTranscript ? View.VISIBLE : View.GONE);
        });
    }

    private void saveAndShare() {
        File file = viewModel.saveTranscript();
        if (file == null) {
            Snackbar.make(binding.getRoot(), "No transcript to save", Snackbar.LENGTH_SHORT).show();
            return;
        }
        Uri uri = FileProvider.getUriForFile(this,
                getPackageName() + ".fileprovider", file);
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(shareIntent, "Share transcript"));
    }

    private void showMicDeniedDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Microphone required")
                .setMessage("The microphone permission is required to transcribe the interview. Please grant it in Settings.")
                .setPositiveButton("OK", (d, w) -> finish())
                .show();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
