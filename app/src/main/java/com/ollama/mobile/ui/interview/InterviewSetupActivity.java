package com.ollama.mobile.ui.interview;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.snackbar.Snackbar;
import com.ollama.mobile.databinding.ActivityInterviewSetupBinding;
import com.ollama.mobile.model.InterviewRole;

import java.util.ArrayList;
import java.util.List;

public class InterviewSetupActivity extends AppCompatActivity {

    private ActivityInterviewSetupBinding binding;
    private InterviewHomepageViewModel viewModel;

    private List<InterviewRole> interviewRoles = new ArrayList<>();
    private List<String> cloudModelNames = new ArrayList<>();
    private ArrayAdapter<String> modelSpinnerAdapter;
    private ArrayAdapter<String> roleSpinnerAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityInterviewSetupBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setSupportActionBar(binding.toolbar);

        binding.toolbar.setNavigationOnClickListener(v -> finish());

        viewModel = new ViewModelProvider(this).get(InterviewHomepageViewModel.class);

        setupSpinners();
        observeViewModel();

        binding.btnStartInterviewSession.setOnClickListener(v -> startSession());

        viewModel.loadCloudModels();
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
                binding.tilCustomRole.setVisibility(pos == interviewRoles.size() ? View.VISIBLE : View.GONE);
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
            binding.btnStartInterviewSession.setEnabled(!noKey);
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
        startActivity(intent);
    }
}
