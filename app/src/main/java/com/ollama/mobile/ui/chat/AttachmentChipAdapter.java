package com.ollama.mobile.ui.chat;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.ollama.mobile.R;

import java.util.ArrayList;
import java.util.List;

public class AttachmentChipAdapter extends RecyclerView.Adapter<AttachmentChipAdapter.ChipViewHolder> {

    public interface RemoveListener {
        void onRemove(int position);
    }

    private List<UiAttachment> attachments = new ArrayList<>();
    private final boolean showRemoveButton;
    private RemoveListener removeListener;

    public AttachmentChipAdapter(boolean showRemoveButton) {
        this.showRemoveButton = showRemoveButton;
    }

    public void setRemoveListener(RemoveListener listener) {
        this.removeListener = listener;
    }

    public void setAttachments(List<UiAttachment> attachments) {
        this.attachments = attachments != null ? new ArrayList<>(attachments) : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ChipViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_attachment_chip, parent, false);
        return new ChipViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ChipViewHolder holder, int position) {
        UiAttachment attachment = attachments.get(position);

        if (attachment.isImage && attachment.thumbnail != null) {
            holder.ivThumbnail.setImageBitmap(attachment.thumbnail);
            holder.ivThumbnail.setVisibility(View.VISIBLE);
            holder.llFileChip.setVisibility(View.GONE);
        } else {
            holder.llFileChip.setVisibility(View.VISIBLE);
            holder.ivThumbnail.setVisibility(View.GONE);
            holder.tvFileName.setText(attachment.fileName);
            String icon = getFileIcon(attachment.mimeType);
            holder.tvFileIcon.setText(icon);
        }

        if (showRemoveButton) {
            holder.btnRemove.setVisibility(View.VISIBLE);
            holder.btnRemove.setOnClickListener(v -> {
                int pos = holder.getAdapterPosition();
                if (pos != RecyclerView.NO_ID && removeListener != null) {
                    removeListener.onRemove(pos);
                }
            });
        } else {
            holder.btnRemove.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return attachments.size();
    }

    private String getFileIcon(String mimeType) {
        if (mimeType == null) return "📎";
        if (mimeType.startsWith("audio/")) return "🎵";
        if ("application/pdf".equals(mimeType)) return "📄";
        if (mimeType.contains("word")) return "📝";
        if (mimeType.startsWith("text/")) return "📃";
        return "📎";
    }

    static class ChipViewHolder extends RecyclerView.ViewHolder {
        ImageView ivThumbnail;
        LinearLayout llFileChip;
        TextView tvFileIcon;
        TextView tvFileName;
        ImageButton btnRemove;

        ChipViewHolder(View itemView) {
            super(itemView);
            ivThumbnail = itemView.findViewById(R.id.ivThumbnail);
            llFileChip = itemView.findViewById(R.id.llFileChip);
            tvFileIcon = itemView.findViewById(R.id.tvFileIcon);
            tvFileName = itemView.findViewById(R.id.tvFileName);
            btnRemove = itemView.findViewById(R.id.btnRemove);
        }
    }
}
