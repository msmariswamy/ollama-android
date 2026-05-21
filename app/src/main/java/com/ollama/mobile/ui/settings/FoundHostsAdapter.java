package com.ollama.mobile.ui.settings;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.ollama.mobile.R;

import java.util.ArrayList;
import java.util.List;

public class FoundHostsAdapter extends RecyclerView.Adapter<FoundHostsAdapter.ViewHolder> {

    public interface OnConnectListener {
        void onConnect(String ip);
    }

    private List<String> hosts = new ArrayList<>();
    private final OnConnectListener listener;

    public FoundHostsAdapter(OnConnectListener listener) {
        this.listener = listener;
    }

    public void setHosts(List<String> hosts) {
        this.hosts = hosts;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_found_host, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        String ip = hosts.get(position);
        holder.tvHostIp.setText(ip + ":11434");
        holder.btnConnect.setOnClickListener(v -> listener.onConnect(ip));
    }

    @Override
    public int getItemCount() {
        return hosts.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvHostIp;
        Button btnConnect;

        ViewHolder(View itemView) {
            super(itemView);
            tvHostIp = itemView.findViewById(R.id.tvHostIp);
            btnConnect = itemView.findViewById(R.id.btnConnect);
        }
    }
}
