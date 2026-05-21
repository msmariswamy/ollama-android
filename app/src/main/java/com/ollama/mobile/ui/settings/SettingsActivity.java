package com.ollama.mobile.ui.settings;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.snackbar.Snackbar;
import com.ollama.mobile.R;
import com.ollama.mobile.databinding.ActivitySettingsBinding;
import com.ollama.mobile.repository.SettingsRepository;

public class SettingsActivity extends AppCompatActivity {

    private ActivitySettingsBinding binding;
    private SettingsViewModel viewModel;
    private FoundHostsAdapter hostsAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets sys = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(sys.left, sys.top, sys.right, sys.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.settings);
        }

        viewModel = new ViewModelProvider(this).get(SettingsViewModel.class);

        setupModeToggle();
        setupFoundHostsList();
        observeViewModel();
        loadCurrentSettings();

        binding.btnSave.setOnClickListener(v -> onSave());
        binding.btnScanNetwork.setOnClickListener(v -> viewModel.scanNetwork());

        viewModel.checkConnection();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    private void setupModeToggle() {
        binding.modeToggleGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            if (checkedId == R.id.btnModeLocal) {
                binding.localFields.setVisibility(View.VISIBLE);
                binding.cloudFields.setVisibility(View.GONE);
            } else {
                binding.localFields.setVisibility(View.GONE);
                binding.cloudFields.setVisibility(View.VISIBLE);
            }
        });
    }

    private void setupFoundHostsList() {
        hostsAdapter = new FoundHostsAdapter(ip -> {
            binding.etServerIp.setText(ip);
            viewModel.saveLocalUrl(ip);
        });
        binding.rvFoundHosts.setLayoutManager(new LinearLayoutManager(this));
        binding.rvFoundHosts.setAdapter(hostsAdapter);
    }

    private void observeViewModel() {
        viewModel.isConnected.observe(this, connected -> {
            if (connected == null) {
                setStatusDot("#AAAAAA");
            } else if (connected) {
                setStatusDot("#4CAF50");
            } else {
                setStatusDot("#F44336");
            }
        });

        viewModel.statusMessage.observe(this, msg -> binding.tvStatus.setText(msg));

        viewModel.isScanning.observe(this, scanning -> {
            binding.scanProgressLayout.setVisibility(scanning ? View.VISIBLE : View.GONE);
            binding.rvFoundHosts.setVisibility(View.VISIBLE);
            binding.btnScanNetwork.setEnabled(!scanning);
        });

        viewModel.scanProgress.observe(this, progress -> binding.scanProgress.setProgress(progress));

        viewModel.foundHosts.observe(this, hosts -> {
            hostsAdapter.setHosts(hosts);
        });
    }

    private void setStatusDot(String colorHex) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(Color.parseColor(colorHex));
        binding.statusDot.setBackground(drawable);
    }

    private void loadCurrentSettings() {
        SettingsRepository repo = viewModel.getSettingsRepository();
        boolean isCloud = repo.isCloudMode();

        if (isCloud) {
            binding.modeToggleGroup.check(R.id.btnModeCloud);
            binding.cloudFields.setVisibility(View.VISIBLE);
            binding.localFields.setVisibility(View.GONE);
            String key = repo.getApiKey();
            if (key != null && !key.isEmpty()) binding.etApiKey.setText(key);
        } else {
            binding.modeToggleGroup.check(R.id.btnModeLocal);
            String url = repo.getLocalBaseUrl();
            if (url != null && !url.isEmpty()) {
                // Strip protocol and port for display
                String display = url.replace("http://", "").replace(":11434", "").replace("/", "");
                binding.etServerIp.setText(display);
            }
        }
    }

    private void onSave() {
        int checkedId = binding.modeToggleGroup.getCheckedButtonId();
        if (checkedId == R.id.btnModeLocal) {
            String ip = binding.etServerIp.getText() != null ? binding.etServerIp.getText().toString().trim() : "";
            if (ip.isEmpty()) {
                binding.tilServerIp.setError(getString(R.string.error_invalid_url));
                return;
            }
            binding.tilServerIp.setError(null);
            viewModel.saveLocalUrl(ip);
        } else {
            String key = binding.etApiKey.getText() != null ? binding.etApiKey.getText().toString().trim() : "";
            if (key.isEmpty()) {
                binding.tilApiKey.setError("API key is required");
                return;
            }
            binding.tilApiKey.setError(null);
            viewModel.saveApiKey(key);
        }
        Snackbar.make(binding.getRoot(), "Settings saved", Snackbar.LENGTH_SHORT).show();
    }
}
