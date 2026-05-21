package com.ollama.mobile.ui.model;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.ollama.mobile.R;

import java.util.ArrayList;
import java.util.List;

public class ModelListAdapter extends RecyclerView.Adapter<ModelListAdapter.ViewHolder> {

    public interface OnModelClickListener {
        void onModelClick(String model);
    }

    private List<String> models = new ArrayList<>();
    private final OnModelClickListener listener;

    public ModelListAdapter(OnModelClickListener listener) {
        this.listener = listener;
    }

    public void setModels(List<String> models) {
        this.models = models;
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
        String model = models.get(position);
        holder.tvModelName.setText(model);
        holder.itemView.setOnClickListener(v -> listener.onModelClick(model));
    }

    @Override
    public int getItemCount() {
        return models.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvModelName;

        ViewHolder(View itemView) {
            super(itemView);
            tvModelName = itemView.findViewById(R.id.tvModelName);
        }
    }
}
