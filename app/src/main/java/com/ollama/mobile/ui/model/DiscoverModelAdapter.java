package com.ollama.mobile.ui.model;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.ollama.mobile.R;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DiscoverModelAdapter extends RecyclerView.Adapter<DiscoverModelAdapter.ViewHolder> {

    public interface OnPullListener {
        void onPull(String name);
    }

    public interface OnCancelListener {
        void onCancel(String name);
    }

    private final List<String[]> models; // [name, description]
    private final OnPullListener pullListener;
    private final OnCancelListener cancelListener;
    private Map<String, Integer> progressMap = new HashMap<>();

    public DiscoverModelAdapter(List<String[]> models, OnPullListener pullListener, OnCancelListener cancelListener) {
        this.models = models;
        this.pullListener = pullListener;
        this.cancelListener = cancelListener;
    }

    public void setProgress(Map<String, Integer> progressMap) {
        this.progressMap = progressMap != null ? progressMap : new HashMap<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_discover_model, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        String[] model = models.get(position);
        String name = model[0];
        String desc = model[1];

        holder.tvName.setText(name);
        holder.tvDesc.setText(desc);

        Integer progress = progressMap.get(name);
        boolean isPulling = progress != null;

        holder.btnPull.setVisibility(isPulling ? View.GONE : View.VISIBLE);
        holder.btnCancel.setVisibility(isPulling ? View.VISIBLE : View.GONE);
        holder.progressBar.setVisibility(isPulling ? View.VISIBLE : View.GONE);

        if (isPulling) {
            if (progress < 0) {
                holder.progressBar.setIndeterminate(true);
            } else {
                holder.progressBar.setIndeterminate(false);
                holder.progressBar.setProgress(progress);
            }
        }

        holder.btnPull.setOnClickListener(v -> {
            if (pullListener != null) pullListener.onPull(name);
        });
        holder.btnCancel.setOnClickListener(v -> {
            if (cancelListener != null) cancelListener.onCancel(name);
        });
    }

    @Override
    public int getItemCount() {
        return models.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvDesc;
        MaterialButton btnPull;
        ImageButton btnCancel;
        ProgressBar progressBar;

        ViewHolder(View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvDiscoverName);
            tvDesc = itemView.findViewById(R.id.tvDiscoverDesc);
            btnPull = itemView.findViewById(R.id.btnPull);
            btnCancel = itemView.findViewById(R.id.btnCancelPull);
            progressBar = itemView.findViewById(R.id.pullProgress);
        }
    }
}
