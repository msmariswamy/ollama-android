package com.ollama.mobile.ui.model;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.ollama.mobile.R;
import com.ollama.mobile.model.OllamaModel;

import java.util.ArrayList;
import java.util.List;

public class InstalledModelAdapter extends RecyclerView.Adapter<InstalledModelAdapter.ViewHolder> {

    public interface OnDeleteListener {
        void onDelete(OllamaModel model);
    }

    private List<OllamaModel> models = new ArrayList<>();
    private final OnDeleteListener deleteListener;

    public InstalledModelAdapter(OnDeleteListener deleteListener) {
        this.deleteListener = deleteListener;
    }

    public void setModels(List<OllamaModel> models) {
        this.models = models != null ? models : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_installed_model, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        OllamaModel model = models.get(position);
        // Show name without tag for cleanliness
        String displayName = model.name.contains(":") ? model.name : model.name;
        holder.tvName.setText(displayName);
        holder.tvSize.setText(formatBytes(model.size));

        holder.btnMenu.setOnClickListener(v -> {
            PopupMenu popup = new PopupMenu(v.getContext(), v);
            popup.getMenu().add("Delete");
            popup.setOnMenuItemClickListener(item -> {
                if ("Delete".equals(item.getTitle().toString())) {
                    if (deleteListener != null) deleteListener.onDelete(model);
                    return true;
                }
                return false;
            });
            popup.show();
        });
    }

    @Override
    public int getItemCount() {
        return models.size();
    }

    private static String formatBytes(long bytes) {
        if (bytes >= 1_000_000_000L) return String.format("%.1f GB", bytes / 1e9);
        if (bytes >= 1_000_000L) return String.format("%.0f MB", bytes / 1e6);
        return String.format("%.0f KB", bytes / 1e3);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvSize;
        ImageButton btnMenu;

        ViewHolder(View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvModelName);
            tvSize = itemView.findViewById(R.id.tvModelSize);
            btnMenu = itemView.findViewById(R.id.btnModelMenu);
        }
    }
}
