package com.ollama.mobile.ui.interview;

import android.content.Intent;
import android.os.Bundle;
import android.widget.RadioButton;
import android.widget.RadioGroup;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.snackbar.Snackbar;
import com.ollama.mobile.R;
import com.ollama.mobile.databinding.ActivityInterviewSetupBinding;
import com.ollama.mobile.model.InterviewRole;

import java.util.List;

public class InterviewSetupActivity extends AppCompatActivity {

    static final String EXTRA_ROLE_ID = "role_id";
    static final String EXTRA_ROLE_TITLE = "role_title";
    static final String EXTRA_ROLE_DESCRIPTION = "role_description";
    static final String EXTRA_ROLE_SKILLS = "role_skills";
    static final String EXTRA_CUSTOM_NAME = "custom_name";

    private ActivityInterviewSetupBinding binding;
    private InterviewViewModel viewModel;
    private List<InterviewRole> roles;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityInterviewSetupBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets sys = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(sys.left, sys.top, sys.right, sys.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Interview Assistant");
        }

        viewModel = new ViewModelProvider(this).get(InterviewViewModel.class);
        roles = viewModel.loadRoles();
        populateRoles();

        binding.btnStartSession.setOnClickListener(v -> onStartTapped());
    }

    private void populateRoles() {
        RadioGroup group = binding.radioGroupRoles;
        group.removeAllViews();
        for (int i = 0; i < roles.size(); i++) {
            InterviewRole role = roles.get(i);
            RadioButton rb = new RadioButton(this);
            rb.setId(i);
            rb.setText(role.title);
            rb.setTextColor(getColor(R.color.colorTextPrimary));
            rb.setPadding(0, 12, 0, 12);
            group.addView(rb);
        }
        if (!roles.isEmpty()) group.check(0);
    }

    private void onStartTapped() {
        String customName = binding.etCustomRole.getText() != null
                ? binding.etCustomRole.getText().toString().trim()
                : "";

        InterviewRole selectedRole;
        if (!customName.isEmpty()) {
            selectedRole = InterviewRole.CUSTOM;
        } else {
            int checkedId = binding.radioGroupRoles.getCheckedRadioButtonId();
            if (checkedId < 0 || checkedId >= roles.size()) {
                Snackbar.make(binding.getRoot(), "Please select a role or enter a custom one", Snackbar.LENGTH_SHORT).show();
                return;
            }
            selectedRole = roles.get(checkedId);
        }

        Intent intent = new Intent(this, InterviewActivity.class);
        intent.putExtra(EXTRA_ROLE_ID, selectedRole.id);
        intent.putExtra(EXTRA_ROLE_TITLE, selectedRole.title);
        intent.putExtra(EXTRA_ROLE_DESCRIPTION, selectedRole.description);
        if (selectedRole.technicalSkills != null) {
            intent.putExtra(EXTRA_ROLE_SKILLS, selectedRole.technicalSkills.toArray(new String[0]));
        }
        intent.putExtra(EXTRA_CUSTOM_NAME, customName);
        startActivity(intent);
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
