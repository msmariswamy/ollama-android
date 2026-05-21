package com.ollama.mobile.ui.settings;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.graphics.Insets;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.navigation.NavigationView;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputLayout;
import com.ollama.mobile.R;
import com.ollama.mobile.databinding.ActivitySettingsBinding;
import com.ollama.mobile.model.ApiProvider;
import com.ollama.mobile.model.CustomProviderConfig;
import com.ollama.mobile.repository.SettingsRepository;
import com.ollama.mobile.ui.interview.InterviewSessionFilesActivity;
import com.ollama.mobile.ui.interview.InterviewSetupActivity;
import com.ollama.mobile.ui.model.ModelLibraryActivity;

import java.util.List;

public class SettingsActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener {

    private ActivitySettingsBinding binding;
    private SettingsViewModel viewModel;
    private FoundHostsAdapter hostsAdapter;

    private ApiProvider selectedProvider = ApiProvider.OLLAMA_LOCAL;
    /** ID of the selected custom provider, when selectedProvider == CUSTOM_OPENAI. */
    private String selectedCustomId = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        ViewCompat.setOnApplyWindowInsetsListener(binding.settingsContentLayout, (v, insets) -> {
            Insets sys = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(sys.left, sys.top, sys.right, sys.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setTitle(R.string.settings);
        setupDrawer();

        viewModel = new ViewModelProvider(this).get(SettingsViewModel.class);

        setupProviderCards();
        setupCustomProviderSection();
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
        viewModel.loadCustomProviders();
    }

    // ── Drawer ────────────────────────────────────────────────────────────────

    private void setupDrawer() {
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, binding.drawerLayout, binding.toolbar,
                R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        binding.drawerLayout.addDrawerListener(toggle);
        toggle.syncState();
        binding.navigationView.setNavigationItemSelectedListener(this);
        binding.navigationView.setCheckedItem(R.id.nav_settings);
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.nav_conversations) finish();
        else if (id == R.id.nav_interview) startActivity(new Intent(this, InterviewSetupActivity.class));
        else if (id == R.id.nav_session_files) startActivity(new Intent(this, InterviewSessionFilesActivity.class));
        else if (id == R.id.nav_models) startActivity(new Intent(this, ModelLibraryActivity.class));
        binding.drawerLayout.closeDrawer(GravityCompat.START);
        return true;
    }

    @Override
    public void onBackPressed() {
        if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) binding.drawerLayout.closeDrawer(GravityCompat.START);
        else super.onBackPressed();
    }

    // ── Fixed provider cards ──────────────────────────────────────────────────

    private void setupProviderCards() {
        binding.radioOllamaLocal.setOnClickListener(v -> selectProvider(ApiProvider.OLLAMA_LOCAL, null));
        binding.radioOllamaCloud.setOnClickListener(v -> selectProvider(ApiProvider.OLLAMA_CLOUD, null));
        binding.radioOpenRouter.setOnClickListener(v -> selectProvider(ApiProvider.OPENROUTER, null));
        binding.radioGrok.setOnClickListener(v -> selectProvider(ApiProvider.GROK, null));
        binding.radioClaude.setOnClickListener(v -> selectProvider(ApiProvider.CLAUDE, null));

        binding.cardProviderOllamaLocal.setOnClickListener(v -> selectProvider(ApiProvider.OLLAMA_LOCAL, null));
        binding.cardProviderOllamaCloud.setOnClickListener(v -> selectProvider(ApiProvider.OLLAMA_CLOUD, null));
        binding.cardProviderOpenRouter.setOnClickListener(v -> selectProvider(ApiProvider.OPENROUTER, null));
        binding.cardProviderGrok.setOnClickListener(v -> selectProvider(ApiProvider.GROK, null));
        binding.cardProviderClaude.setOnClickListener(v -> selectProvider(ApiProvider.CLAUDE, null));
    }

    private void selectProvider(ApiProvider provider, String customId) {
        selectedProvider = provider;
        selectedCustomId = customId != null ? customId : "";

        binding.radioOllamaLocal.setChecked(provider == ApiProvider.OLLAMA_LOCAL);
        binding.radioOllamaCloud.setChecked(provider == ApiProvider.OLLAMA_CLOUD);
        binding.radioOpenRouter.setChecked(provider == ApiProvider.OPENROUTER);
        binding.radioGrok.setChecked(provider == ApiProvider.GROK);
        binding.radioClaude.setChecked(provider == ApiProvider.CLAUDE);

        binding.localFields.setVisibility(provider == ApiProvider.OLLAMA_LOCAL ? View.VISIBLE : View.GONE);
        binding.cloudFields.setVisibility(provider == ApiProvider.OLLAMA_CLOUD ? View.VISIBLE : View.GONE);
        binding.openRouterFields.setVisibility(provider == ApiProvider.OPENROUTER ? View.VISIBLE : View.GONE);
        binding.grokFields.setVisibility(provider == ApiProvider.GROK ? View.VISIBLE : View.GONE);
        binding.claudeFields.setVisibility(provider == ApiProvider.CLAUDE ? View.VISIBLE : View.GONE);

        // Sync custom provider radio buttons
        refreshCustomProviderRadios();
    }

    // ── Custom provider dynamic list ──────────────────────────────────────────

    private void setupCustomProviderSection() {
        binding.btnAddCustomProvider.setOnClickListener(v -> {
            binding.cardAddCustomProvider.setVisibility(View.VISIBLE);
            binding.btnAddCustomProvider.setEnabled(false);
        });
        binding.btnCancelAddCustom.setOnClickListener(v -> dismissAddForm());
        binding.btnConfirmAddCustom.setOnClickListener(v -> confirmAddCustomProvider());
    }

    private void observeCustomProviders(List<CustomProviderConfig> providers) {
        String activeCustomId = viewModel.getSettingsRepository().getActiveCustomId();
        binding.customProvidersContainer.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        for (CustomProviderConfig config : providers) {
            View row = inflater.inflate(R.layout.item_custom_provider, binding.customProvidersContainer, false);
            bindCustomProviderRow(row, config, activeCustomId);
            binding.customProvidersContainer.addView(row);
        }
        refreshCustomProviderRadios();
    }

    private void bindCustomProviderRow(View row, CustomProviderConfig config, String activeCustomId) {
        RadioButton radio = row.findViewById(R.id.radioCustomProvider);
        TextView tvName = row.findViewById(R.id.tvCustomProviderName);
        TextView tvUrl = row.findViewById(R.id.tvCustomProviderUrl);
        View editFields = row.findViewById(R.id.customProviderEditFields);
        EditText etName = row.findViewById(R.id.etEditCustomName);
        EditText etUrl = row.findViewById(R.id.etEditCustomUrl);
        EditText etKey = row.findViewById(R.id.etEditCustomKey);

        tvName.setText(config.name);
        tvUrl.setText(config.baseUrl);
        radio.setTag(config.id);

        boolean isSelected = selectedProvider == ApiProvider.CUSTOM_OPENAI
                && config.id.equals(selectedCustomId);
        radio.setChecked(isSelected);
        editFields.setVisibility(isSelected ? View.VISIBLE : View.GONE);

        if (isSelected) {
            etName.setText(config.name);
            etUrl.setText(config.baseUrl);
            String key = viewModel.getSettingsRepository().getCustomProviderApiKey(config.id);
            if (!key.isEmpty()) etKey.setText(key);
        }

        View.OnClickListener selectRow = v -> {
            selectProvider(ApiProvider.CUSTOM_OPENAI, config.id);
            editFields.setVisibility(View.VISIBLE);
            etName.setText(config.name);
            etUrl.setText(config.baseUrl);
            String key = viewModel.getSettingsRepository().getCustomProviderApiKey(config.id);
            if (!key.isEmpty()) etKey.setText(key);
        };
        radio.setOnClickListener(selectRow);
        row.setOnClickListener(selectRow);

        row.findViewById(R.id.btnDeleteCustomProvider).setOnClickListener(v -> {
            viewModel.removeCustomProvider(config.id);
            if (config.id.equals(selectedCustomId)) {
                selectProvider(ApiProvider.OLLAMA_LOCAL, null);
            }
        });
    }

    private void refreshCustomProviderRadios() {
        for (int i = 0; i < binding.customProvidersContainer.getChildCount(); i++) {
            View row = binding.customProvidersContainer.getChildAt(i);
            RadioButton radio = row.findViewById(R.id.radioCustomProvider);
            View editFields = row.findViewById(R.id.customProviderEditFields);
            if (radio != null && radio.getTag() instanceof String) {
                String rowId = (String) radio.getTag();
                boolean selected = selectedProvider == ApiProvider.CUSTOM_OPENAI
                        && rowId.equals(selectedCustomId);
                radio.setChecked(selected);
                if (editFields != null) {
                    editFields.setVisibility(selected ? View.VISIBLE : View.GONE);
                }
            }
        }
    }

    private void confirmAddCustomProvider() {
        String name = text(binding.etNewCustomName);
        String url = text(binding.etNewCustomUrl);
        String key = text(binding.etNewCustomKey);

        if (url.isEmpty()) {
            binding.tilNewCustomUrl.setError(getString(R.string.error_invalid_url));
            return;
        }
        binding.tilNewCustomUrl.setError(null);
        if (name.isEmpty()) name = "Custom";

        CustomProviderConfig created = viewModel.addCustomProvider(name, url, key);
        selectProvider(ApiProvider.CUSTOM_OPENAI, created.id);
        dismissAddForm();
        Snackbar.make(binding.getRoot(), "Provider added and set as active", Snackbar.LENGTH_SHORT).show();
    }

    private void dismissAddForm() {
        binding.cardAddCustomProvider.setVisibility(View.GONE);
        binding.btnAddCustomProvider.setEnabled(true);
        binding.etNewCustomName.setText("");
        binding.etNewCustomUrl.setText("");
        binding.etNewCustomKey.setText("");
        binding.tilNewCustomUrl.setError(null);
    }

    // ── Hosts list ────────────────────────────────────────────────────────────

    private void setupFoundHostsList() {
        hostsAdapter = new FoundHostsAdapter(ip -> {
            binding.etServerIp.setText(ip);
            viewModel.saveLocalUrl(ip);
        });
        binding.rvFoundHosts.setLayoutManager(new LinearLayoutManager(this));
        binding.rvFoundHosts.setAdapter(hostsAdapter);
    }

    // ── Observers ─────────────────────────────────────────────────────────────

    private void observeViewModel() {
        viewModel.isConnected.observe(this, connected -> {
            if (connected == null) setStatusDot("#AAAAAA");
            else if (connected) setStatusDot("#4CAF50");
            else setStatusDot("#F44336");
        });
        viewModel.statusMessage.observe(this, msg -> binding.tvStatus.setText(msg));
        viewModel.isScanning.observe(this, scanning -> {
            binding.scanProgressLayout.setVisibility(scanning ? View.VISIBLE : View.GONE);
            binding.rvFoundHosts.setVisibility(View.VISIBLE);
            binding.btnScanNetwork.setEnabled(!scanning);
        });
        viewModel.scanProgress.observe(this, progress -> binding.scanProgress.setProgress(progress));
        viewModel.foundHosts.observe(this, hosts -> hostsAdapter.setHosts(hosts));
        viewModel.customProviders.observe(this, this::observeCustomProviders);
    }

    private void setStatusDot(String colorHex) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(Color.parseColor(colorHex));
        binding.statusDot.setBackground(drawable);
    }

    // ── Load saved settings ───────────────────────────────────────────────────

    private void loadCurrentSettings() {
        SettingsRepository repo = viewModel.getSettingsRepository();
        ApiProvider active = repo.getActiveProvider();
        String activeCustomId = repo.getActiveCustomId();
        selectProvider(active, active == ApiProvider.CUSTOM_OPENAI ? activeCustomId : null);

        String cloudKey = repo.getApiKeyForProvider(ApiProvider.OLLAMA_CLOUD);
        if (!cloudKey.isEmpty()) binding.etApiKey.setText(cloudKey);

        String orKey = repo.getApiKeyForProvider(ApiProvider.OPENROUTER);
        if (!orKey.isEmpty()) binding.etOpenRouterKey.setText(orKey);

        String grokKey = repo.getApiKeyForProvider(ApiProvider.GROK);
        if (!grokKey.isEmpty()) binding.etGrokKey.setText(grokKey);

        String claudeKey = repo.getApiKeyForProvider(ApiProvider.CLAUDE);
        if (!claudeKey.isEmpty()) binding.etClaudeKey.setText(claudeKey);

        String localUrl = repo.getLocalBaseUrl();
        if (!localUrl.isEmpty()) {
            String display = localUrl.replace("http://", "").replace(":11434", "").replace("/", "");
            binding.etServerIp.setText(display);
        }
    }

    // ── Model parameters ──────────────────────────────────────────────────────

    private void setupModelParameters() {
        SettingsRepository repo = viewModel.getSettingsRepository();

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

        int[] ctxValues = {2048, 4096, 8192, 16384, 32768, 65536, 131072, 1048576};
        int[] chipIds = {R.id.chip2k, R.id.chip4k, R.id.chip8k, R.id.chip16k, R.id.chip32k, R.id.chip64k, R.id.chip128k, R.id.chip1m};
        int savedCtx = repo.getContextLength();
        for (int i = 0; i < ctxValues.length; i++) {
            if (ctxValues[i] == savedCtx) { binding.chipGroupCtx.check(chipIds[i]); break; }
        }
        binding.chipGroupCtx.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            int id = checkedIds.get(0);
            for (int i = 0; i < chipIds.length; i++) {
                if (chipIds[i] == id) { repo.setContextLength(ctxValues[i]); break; }
            }
        });
    }

    // ── Dark mode ─────────────────────────────────────────────────────────────

    private void setupDarkMode() {
        SettingsRepository repo = viewModel.getSettingsRepository();
        int mode = repo.getDarkMode();
        if (mode == SettingsRepository.DARK_MODE_LIGHT) binding.darkModeToggleGroup.check(R.id.btnDarkModeLight);
        else if (mode == SettingsRepository.DARK_MODE_DARK) binding.darkModeToggleGroup.check(R.id.btnDarkModeDark);
        else binding.darkModeToggleGroup.check(R.id.btnDarkModeSystem);

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

    // ── Save ──────────────────────────────────────────────────────────────────

    private void onSave() {
        if (!viewModel.getSettingsRepository().isSecureStorageAvailable()
                && selectedProvider.requiresApiKey) {
            Snackbar.make(binding.getRoot(),
                    "Secure storage unavailable on this device — API key cannot be saved",
                    Snackbar.LENGTH_LONG).show();
            return;
        }

        switch (selectedProvider) {
            case OLLAMA_LOCAL: {
                String ip = text(binding.etServerIp);
                if (ip.isEmpty()) { binding.tilServerIp.setError(getString(R.string.error_invalid_url)); return; }
                binding.tilServerIp.setError(null);
                viewModel.saveLocalUrl(ip);
                break;
            }
            case OLLAMA_CLOUD: {
                String key = text(binding.etApiKey);
                if (key.isEmpty()) { binding.tilApiKey.setError("API key is required"); return; }
                binding.tilApiKey.setError(null);
                viewModel.saveProviderConfig(ApiProvider.OLLAMA_CLOUD, key, true);
                break;
            }
            case OPENROUTER: {
                String key = text(binding.etOpenRouterKey);
                if (key.isEmpty()) { binding.tilOpenRouterKey.setError("API key is required"); return; }
                binding.tilOpenRouterKey.setError(null);
                viewModel.saveProviderConfig(ApiProvider.OPENROUTER, key, true);
                break;
            }
            case GROK: {
                String key = text(binding.etGrokKey);
                if (key.isEmpty()) { binding.tilGrokKey.setError("API key is required"); return; }
                binding.tilGrokKey.setError(null);
                viewModel.saveProviderConfig(ApiProvider.GROK, key, true);
                break;
            }
            case CLAUDE: {
                String key = text(binding.etClaudeKey);
                if (key.isEmpty()) { binding.tilClaudeKey.setError("API key is required"); return; }
                binding.tilClaudeKey.setError(null);
                viewModel.saveProviderConfig(ApiProvider.CLAUDE, key, true);
                break;
            }
            case CUSTOM_OPENAI: {
                // Find the selected row's edit fields and update
                View row = findCustomRowById(selectedCustomId);
                if (row == null) { Snackbar.make(binding.getRoot(), "Select a custom provider first", Snackbar.LENGTH_SHORT).show(); return; }
                EditText etName = row.findViewById(R.id.etEditCustomName);
                EditText etUrl = row.findViewById(R.id.etEditCustomUrl);
                EditText etKey = row.findViewById(R.id.etEditCustomKey);
                TextInputLayout tilUrl = row.findViewById(R.id.tilEditCustomUrl);
                String url = text(etUrl);
                if (url.isEmpty()) { tilUrl.setError(getString(R.string.error_invalid_url)); return; }
                tilUrl.setError(null);
                String name = text(etName);
                String key = text(etKey);
                viewModel.updateCustomProvider(selectedCustomId, name.isEmpty() ? "Custom" : name, url, key);
                viewModel.selectCustomProvider(selectedCustomId);
                break;
            }
        }
        Snackbar.make(binding.getRoot(), "Settings saved", Snackbar.LENGTH_SHORT).show();
    }

    private View findCustomRowById(String id) {
        for (int i = 0; i < binding.customProvidersContainer.getChildCount(); i++) {
            View row = binding.customProvidersContainer.getChildAt(i);
            RadioButton radio = row.findViewById(R.id.radioCustomProvider);
            if (radio != null && id.equals(radio.getTag())) return row;
        }
        return null;
    }

    private static String text(EditText et) {
        return et.getText() != null ? et.getText().toString().trim() : "";
    }
}
