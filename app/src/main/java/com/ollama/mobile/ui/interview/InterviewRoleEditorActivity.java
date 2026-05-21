package com.ollama.mobile.ui.interview;

import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.snackbar.Snackbar;
import com.ollama.mobile.R;
import com.ollama.mobile.databinding.ActivityInterviewRoleEditorBinding;
import com.ollama.mobile.repository.InterviewRoleRepository;

public class InterviewRoleEditorActivity extends AppCompatActivity {

    public static final String EXTRA_ROLE_ID = "extra_role_id";
    public static final String EXTRA_ROLE_TITLE = "extra_role_title";
    public static final String EXTRA_CLOUD_MODEL = "extra_cloud_model";

    private ActivityInterviewRoleEditorBinding binding;
    private InterviewRoleEditorViewModel viewModel;
    private PromptImprovementAdapter adapter;

    private String roleId;
    private String roleTitle;
    private String cloudModel;
    private boolean isCreateMode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityInterviewRoleEditorBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        roleId = getIntent().getStringExtra(EXTRA_ROLE_ID);
        roleTitle = getIntent().getStringExtra(EXTRA_ROLE_TITLE);
        cloudModel = getIntent().getStringExtra(EXTRA_CLOUD_MODEL);
        isCreateMode = (roleId == null);

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(isCreateMode ? "New Role" : (roleTitle != null ? roleTitle : "Edit Role"));
        }

        viewModel = new ViewModelProvider(this).get(InterviewRoleEditorViewModel.class);

        setupWindowInsets();
        setupModeUI();
        setupRecyclerView();
        setupClickListeners();
        observeViewModel();
    }

    private void setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.roleEditorRoot, (v, insets) -> {
            int top = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top;
            int bottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom;
            v.setPadding(v.getPaddingLeft(), top, v.getPaddingRight(), bottom);
            return insets;
        });
    }

    private void setupModeUI() {
        if (isCreateMode) {
            binding.llRoleNameFields.setVisibility(View.VISIBLE);
            binding.etSystemPrompt.setHint("Enter system prompt...");
        } else {
            binding.llRoleNameFields.setVisibility(View.GONE);
            // Pre-fill system prompt from repository
            InterviewRoleRepository repo = new InterviewRoleRepository(this);
            com.ollama.mobile.model.InterviewRole tempRole = new com.ollama.mobile.model.InterviewRole(
                    roleId, roleTitle != null ? roleTitle : "", "", null);
            String savedPrompt = repo.getSystemPrompt(tempRole);
            binding.etSystemPrompt.setText(savedPrompt);
        }
    }

    private void setupRecyclerView() {
        adapter = new PromptImprovementAdapter(text -> {
            // Apply AI suggestion to the prompt EditText
            binding.etSystemPrompt.setText(text);
        });
        binding.rvMessages.setLayoutManager(new LinearLayoutManager(this));
        binding.rvMessages.setAdapter(adapter);
    }

    private void setupClickListeners() {
        // Send button
        binding.btnSend.setOnClickListener(v -> sendImproveRequest());

        binding.etImproveInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendImproveRequest();
                return true;
            }
            return false;
        });
    }

    private void sendImproveRequest() {
        String feedbackText = binding.etImproveInput.getText() != null
                ? binding.etImproveInput.getText().toString().trim() : "";
        if (feedbackText.isEmpty()) return;

        String currentPrompt = binding.etSystemPrompt.getText() != null
                ? binding.etSystemPrompt.getText().toString() : "";

        if (cloudModel == null || cloudModel.isEmpty()) {
            Snackbar.make(binding.getRoot(), "No cloud model selected", Snackbar.LENGTH_SHORT).show();
            return;
        }

        binding.etImproveInput.setText("");
        viewModel.improvePrompt(currentPrompt, feedbackText, cloudModel);
    }

    private void saveAndFinish() {
        String prompt = binding.etSystemPrompt.getText() != null
                ? binding.etSystemPrompt.getText().toString().trim() : "";

        if (isCreateMode) {
            String name = binding.etRoleName.getText() != null
                    ? binding.etRoleName.getText().toString().trim() : "";
            if (name.isEmpty()) {
                Snackbar.make(binding.getRoot(), "Enter a role name", Snackbar.LENGTH_SHORT).show();
                return;
            }
            String description = binding.etRoleDescription.getText() != null
                    ? binding.etRoleDescription.getText().toString().trim() : "";
            viewModel.saveUserRole(name, description, prompt, this);
        } else {
            viewModel.saveSystemPrompt(roleId, prompt, this);
        }

        setResult(RESULT_OK);
        finish();
    }

    private void observeViewModel() {
        viewModel.messages.observe(this, messages -> {
            adapter.setMessages(messages);
            if (messages != null && !messages.isEmpty()) {
                binding.rvMessages.scrollToPosition(messages.size() - 1);
            }
        });

        viewModel.improving.observe(this, isImproving -> {
            binding.btnSend.setEnabled(!Boolean.TRUE.equals(isImproving));
            binding.btnSend.setAlpha(Boolean.TRUE.equals(isImproving) ? 0.5f : 1.0f);
        });

        viewModel.error.observe(this, errorMsg -> {
            if (errorMsg != null && !errorMsg.isEmpty()) {
                Snackbar.make(binding.getRoot(), errorMsg, Snackbar.LENGTH_LONG).show();
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_role_editor, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        if (item.getItemId() == R.id.action_save_role) {
            saveAndFinish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
