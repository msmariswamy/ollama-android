package com.ollama.mobile.ui.model;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.ollama.mobile.databinding.FragmentModelSelectorBinding;
import com.ollama.mobile.network.NetworkResult;

public class ModelSelectorFragment extends BottomSheetDialogFragment {

    public interface OnModelSelectedListener {
        void onModelSelected(String model);
    }

    private FragmentModelSelectorBinding binding;
    private ModelSelectorViewModel viewModel;
    private ModelListAdapter adapter;
    private OnModelSelectedListener listener;

    public static ModelSelectorFragment newInstance() {
        return new ModelSelectorFragment();
    }

    public void setOnModelSelectedListener(OnModelSelectedListener listener) {
        this.listener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentModelSelectorBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(ModelSelectorViewModel.class);

        adapter = new ModelListAdapter(model -> {
            viewModel.selectModel(model);
            if (listener != null) listener.onModelSelected(model);
            dismiss();
        });

        binding.rvModels.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvModels.setAdapter(adapter);

        binding.btnRetry.setOnClickListener(v -> viewModel.loadModels());

        viewModel.modelsWithMeta.observe(getViewLifecycleOwner(), result -> {
            if (result == null) return;
            switch (result.status) {
                case LOADING:
                    binding.progressBar.setVisibility(View.VISIBLE);
                    binding.tvEmpty.setVisibility(View.GONE);
                    binding.btnRetry.setVisibility(View.GONE);
                    binding.rvModels.setVisibility(View.GONE);
                    break;
                case SUCCESS:
                    binding.progressBar.setVisibility(View.GONE);
                    if (result.data == null || result.data.isEmpty()) {
                        binding.tvEmpty.setVisibility(View.VISIBLE);
                        binding.rvModels.setVisibility(View.GONE);
                    } else {
                        binding.tvEmpty.setVisibility(View.GONE);
                        binding.rvModels.setVisibility(View.VISIBLE);
                        String current = viewModel.selectedModel.getValue();
                        adapter.setModelsWithMeta(result.data, current);
                    }
                    break;
                case ERROR:
                    binding.progressBar.setVisibility(View.GONE);
                    binding.tvEmpty.setText(result.error);
                    binding.tvEmpty.setVisibility(View.VISIBLE);
                    binding.btnRetry.setVisibility(View.VISIBLE);
                    break;
            }
        });

        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, windowInsets) -> {
            Insets navBar = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars());
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), navBar.bottom);
            return windowInsets;
        });

        viewModel.loadModels();
    }
}
