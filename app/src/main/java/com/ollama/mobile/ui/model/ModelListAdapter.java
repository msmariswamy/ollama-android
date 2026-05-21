package com.ollama.mobile.ui.model;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.ollama.mobile.R;
import com.ollama.mobile.model.OllamaModel;

import java.util.ArrayList;
import java.util.List;

public class ModelListAdapter extends RecyclerView.Adapter<ModelListAdapter.ViewHolder> {

    public interface OnModelClickListener {
        void onModelClick(String model);
    }

    private List<String> models = new ArrayList<>();
    private List<OllamaModel> modelsWithMeta = new ArrayList<>();
    private String selectedModel = "";
    private final OnModelClickListener listener;

    public ModelListAdapter(OnModelClickListener listener) {
        this.listener = listener;
    }

    public void setModels(List<String> models) {
        this.models = models != null ? models : new ArrayList<>();
        notifyDataSetChanged();
    }

    public void setModelsWithMeta(List<OllamaModel> meta, String selected) {
        this.modelsWithMeta = meta != null ? meta : new ArrayList<>();
        this.selectedModel = selected != null ? selected : "";
        notifyDataSetChanged();
    }

    public void setSelectedModel(String model) {
        this.selectedModel = model != null ? model : "";
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_model, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        if (!modelsWithMeta.isEmpty() && position < modelsWithMeta.size()) {
            OllamaModel model = modelsWithMeta.get(position);
            holder.tvModelName.setText(model.name);
            if (holder.tvModelMeta != null) {
                holder.tvModelMeta.setVisibility(View.VISIBLE);
                holder.tvModelMeta.setText(formatBytes(model.size));
            }
            boolean isSelected = model.name.equals(selectedModel);
            if (holder.ivSelected != null) {
                holder.ivSelected.setVisibility(isSelected ? View.VISIBLE : View.GONE);
            }
            holder.itemView.setOnClickListener(v -> listener.onModelClick(model.name));
        } else {
            String model = models.get(position);
            holder.tvModelName.setText(model);
            if (holder.tvModelMeta != null) holder.tvModelMeta.setVisibility(View.GONE);
            boolean isSelected = model.equals(selectedModel);
            if (holder.ivSelected != null) {
                holder.ivSelected.setVisibility(isSelected ? View.VISIBLE : View.GONE);
            }
            holder.itemView.setOnClickListener(v -> listener.onModelClick(model));
        }
    }

    @Override
    public int getItemCount() {
        return modelsWithMeta.isEmpty() ? models.size() : modelsWithMeta.size();
    }

    private static String formatBytes(long bytes) {
        if (bytes >= 1_000_000_000L) return String.format("%.1f GB", bytes / 1e9);
        if (bytes >= 1_000_000L) return String.format("%.0f MB", bytes / 1e6);
        return "";
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvModelName;
        TextView tvModelMeta;
        ImageView ivSelected;

        ViewHolder(View itemView) {
            super(itemView);
            tvModelName = itemView.findViewById(R.id.tvModelName);
            tvModelMeta = itemView.findViewById(R.id.tvModelMeta);
            ivSelected = itemView.findViewById(R.id.ivSelected);
        }
    }
}
