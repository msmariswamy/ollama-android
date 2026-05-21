package com.ollama.mobile.ui.interview;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.ollama.mobile.R;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class InterviewSessionFilesAdapter extends RecyclerView.Adapter<InterviewSessionFilesAdapter.FileViewHolder> {

    public interface OnFileClickListener { void onClick(File file); }
    public interface OnDownloadClickListener { void onDownload(File file); }
    public interface OnShareClickListener { void onShare(File file); }
    public interface OnDeleteClickListener { void onDelete(File file); }

    private List<File> files = new ArrayList<>();
    private final OnFileClickListener clickListener;
    private final OnDownloadClickListener downloadListener;
    private final OnShareClickListener shareListener;
    private final OnDeleteClickListener deleteListener;

    public InterviewSessionFilesAdapter(OnFileClickListener clickListener,
                                        OnDownloadClickListener downloadListener,
                                        OnShareClickListener shareListener,
                                        OnDeleteClickListener deleteListener) {
        this.clickListener = clickListener;
        this.downloadListener = downloadListener;
        this.shareListener = shareListener;
        this.deleteListener = deleteListener;
    }

    public void setFiles(List<File> files) {
        this.files = files != null ? files : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public FileViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_session_file, parent, false);
        return new FileViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull FileViewHolder holder, int position) {
        File file = files.get(position);
        holder.tvFileName.setText(file.getName());
        long sizeKb = file.length() / 1024;
        holder.tvFileSize.setText(sizeKb > 0 ? sizeKb + " KB" : "< 1 KB");
        holder.itemView.setOnClickListener(v -> clickListener.onClick(file));
        holder.btnDownload.setOnClickListener(v -> downloadListener.onDownload(file));
        holder.btnShare.setOnClickListener(v -> shareListener.onShare(file));
        holder.btnDelete.setOnClickListener(v -> deleteListener.onDelete(file));
    }

    @Override
    public int getItemCount() { return files.size(); }

    static class FileViewHolder extends RecyclerView.ViewHolder {
        final TextView tvFileName;
        final TextView tvFileSize;
        final ImageButton btnDownload;
        final ImageButton btnShare;
        final ImageButton btnDelete;

        FileViewHolder(@NonNull View itemView) {
            super(itemView);
            tvFileName = itemView.findViewById(R.id.tvFileName);
            tvFileSize = itemView.findViewById(R.id.tvFileSize);
            btnDownload = itemView.findViewById(R.id.btnDownload);
            btnShare = itemView.findViewById(R.id.btnShare);
            btnDelete = itemView.findViewById(R.id.btnDelete);
        }
    }
}
