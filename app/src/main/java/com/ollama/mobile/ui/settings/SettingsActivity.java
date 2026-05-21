package com.ollama.mobile.ui.settings;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.widget.SeekBar;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.snackbar.Snackbar;
import com.ollama.mobile.R;
import com.ollama.mobile.databinding.ActivitySettingsBinding;
import com.ollama.mobile.repository.SettingsRepository;
import com.ollama.mobile.ui.model.ModelLibraryActivity;

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

        setupModelParameters();
        setupDarkMode();
        binding.cardModels.setOnClickListener(v ->
                startActivity(new Intent(this, ModelLibraryActivity.class)));

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

    private void setupModelParameters() {
        SettingsRepository repo = viewModel.getSettingsRepository();

        // Temperature (0.0–2.0, step 0.1, seekbar 0–20)
        int tempProgress = Math.round(repo.getTemperature() * 10);
        binding.seekTemperature.setProgress(tempProgress);
        binding.tvTemperatureValue.setText(String.format("%.1f", tempProgress / 10f));
        binding.seekTemperature.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                float val = progress / 10f;
                binding.tvTemperatureValue.setText(String.format("%.1f", val));
                if (fromUser) repo.setTemperature(val);
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });

        // Top-p (0.0–1.0, step 0.05, seekbar 0–20)
        int topPProgress = Math.round(repo.getTopP() * 20);
        binding.seekTopP.setProgress(topPProgress);
        binding.tvTopPValue.setText(String.format("%.2f", topPProgress / 20f));
        binding.seekTopP.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                float val = progress / 20f;
                binding.tvTopPValue.setText(String.format("%.2f", val));
                if (fromUser) repo.setTopP(val);
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });

        // Context length chip selection
        int[] ctxValues = {2048, 4096, 8192, 16384, 32768, 65536, 131072, 1048576};
        int[] chipIds = {R.id.chip2k, R.id.chip4k, R.id.chip8k, R.id.chip16k, R.id.chip32k, R.id.chip64k, R.id.chip128k, R.id.chip1m};
        int savedCtx = repo.getContextLength();
        for (int i = 0; i < ctxValues.length; i++) {
            if (ctxValues[i] == savedCtx) {
                binding.chipGroupCtx.check(chipIds[i]);
                break;
            }
        }
        binding.chipGroupCtx.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            int id = checkedIds.get(0);
            for (int i = 0; i < chipIds.length; i++) {
                if (chipIds[i] == id) {
                    repo.setContextLength(ctxValues[i]);
                    break;
                }
            }
        });
    }

    private void setupDarkMode() {
        SettingsRepository repo = viewModel.getSettingsRepository();
        int mode = repo.getDarkMode();
        if (mode == SettingsRepository.DARK_MODE_LIGHT) {
            binding.darkModeToggleGroup.check(R.id.btnDarkModeLight);
        } else if (mode == SettingsRepository.DARK_MODE_DARK) {
            binding.darkModeToggleGroup.check(R.id.btnDarkModeDark);
        } else {
            binding.darkModeToggleGroup.check(R.id.btnDarkModeSystem);
        }
        binding.darkModeToggleGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            int newMode;
            int nightMode;
            if (checkedId == R.id.btnDarkModeLight) {
                newMode = SettingsRepository.DARK_MODE_LIGHT;
                nightMode = AppCompatDelegate.MODE_NIGHT_NO;
            } else if (checkedId == R.id.btnDarkModeDark) {
                newMode = SettingsRepository.DARK_MODE_DARK;
                nightMode = AppCompatDelegate.MODE_NIGHT_YES;
            } else {
                newMode = SettingsRepository.DARK_MODE_SYSTEM;
                nightMode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
            }
            repo.setDarkMode(newMode);
            AppCompatDelegate.setDefaultNightMode(nightMode);
        });
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
            if (!viewModel.getSettingsRepository().isSecureStorageAvailable()) {
                Snackbar.make(binding.getRoot(),
                        "Secure storage unavailable on this device — API key cannot be saved",
                        Snackbar.LENGTH_LONG).show();
                return;
            }
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
