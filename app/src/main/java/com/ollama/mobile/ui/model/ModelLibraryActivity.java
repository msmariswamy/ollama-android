package com.ollama.mobile.ui.model;

import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.ollama.mobile.R;
import com.ollama.mobile.model.OllamaModel;
import com.ollama.mobile.databinding.ActivityModelLibraryBinding;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class ModelLibraryActivity extends AppCompatActivity {

    private ActivityModelLibraryBinding binding;
    private ModelLibraryViewModel viewModel;
    private InstalledModelAdapter installedAdapter;
    private DiscoverModelAdapter discoverAdapter;

    private static final List<String[]> DISCOVER_MODELS = Arrays.asList(
            new String[]{"llama3.2", "Meta's latest 3B/1B lightweight model"},
            new String[]{"llama3.1", "Meta Llama 3.1 8B/70B/405B"},
            new String[]{"mistral", "Mistral 7B v0.3"},
            new String[]{"gemma3", "Google Gemma 3 — 1B to 27B"},
            new String[]{"phi4", "Microsoft Phi-4 14B"},
            new String[]{"qwen2.5", "Alibaba Qwen 2.5 series"},
            new String[]{"deepseek-r1", "DeepSeek R1 reasoning model"},
            new String[]{"nomic-embed-text", "Embedding model for RAG"}
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityModelLibraryBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets sys = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(sys.left, sys.top, sys.right, sys.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Model Library");
        }

        viewModel = new ViewModelProvider(this).get(ModelLibraryViewModel.class);
        setupInstalledList();
        setupDiscoverList();
        observeViewModel();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    private void setupInstalledList() {
        installedAdapter = new InstalledModelAdapter(model -> {
            new AlertDialog.Builder(this)
                    .setTitle("Delete " + model.name + "?")
                    .setMessage("This will permanently remove the model.")
                    .setPositiveButton("Delete", (d, w) -> viewModel.deleteModel(model.name))
                    .setNegativeButton("Cancel", null)
                    .show();
        });
        binding.rvInstalledModels.setLayoutManager(new LinearLayoutManager(this));
        binding.rvInstalledModels.setAdapter(installedAdapter);
    }

    private void setupDiscoverList() {
        discoverAdapter = new DiscoverModelAdapter(DISCOVER_MODELS,
                name -> viewModel.pullModel(name),
                name -> viewModel.cancelPull(name));
        binding.rvDiscoverModels.setLayoutManager(new LinearLayoutManager(this));
        binding.rvDiscoverModels.setAdapter(discoverAdapter);
    }

    private void observeViewModel() {
        viewModel.installedModels.observe(this, models -> installedAdapter.setModels(models));

        viewModel.isLoading.observe(this, loading ->
                binding.loadingProgress.setVisibility(loading ? View.VISIBLE : View.GONE));

        viewModel.storageUsed.observe(this, used -> binding.tvStorageUsed.setText(used));
        viewModel.storageAvailable.observe(this, avail -> binding.tvStorageAvailable.setText(avail));

        viewModel.pullProgress.observe(this, progressMap -> discoverAdapter.setProgress(progressMap));

        viewModel.errorMessage.observe(this, msg -> {
            if (msg != null) {
                com.google.android.material.snackbar.Snackbar.make(binding.getRoot(), msg, 3000).show();
            }
        });
    }
}
