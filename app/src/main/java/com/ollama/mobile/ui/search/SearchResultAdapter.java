package com.ollama.mobile.ui.search;

import android.graphics.Color;
import android.text.SpannableString;
import android.text.style.BackgroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.ollama.mobile.R;
import com.ollama.mobile.repository.ConversationRepository;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class SearchResultAdapter extends RecyclerView.Adapter<SearchResultAdapter.ViewHolder> {

    private List<ConversationRepository.SearchResult> results = new ArrayList<>();
    private String query = "";
    private OnResultClickListener clickListener;

    public interface OnResultClickListener {
        void onClick(long conversationId);
    }

    public void setOnResultClickListener(OnResultClickListener listener) {
        this.clickListener = listener;
    }

    public void setResults(List<ConversationRepository.SearchResult> results, String query) {
        this.results = results != null ? results : new ArrayList<>();
        this.query = query != null ? query : "";
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_search_result, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ConversationRepository.SearchResult result = results.get(position);

        holder.tvTitle.setText(highlight(result.title, query, holder.tvTitle.getContext()));
        holder.tvSnippet.setText(highlight(result.snippet, query, holder.tvSnippet.getContext()));
        holder.tvTime.setText(formatTime(result.timestamp));

        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null) clickListener.onClick(result.conversationId);
        });
    }

    private CharSequence highlight(String text, String query, android.content.Context ctx) {
        if (text == null || text.isEmpty() || query == null || query.isEmpty()) {
            return text != null ? text : "";
        }
        int idx = text.toLowerCase(Locale.getDefault()).indexOf(query.toLowerCase(Locale.getDefault()));
        if (idx < 0) return text;
        SpannableString span = new SpannableString(text);
        span.setSpan(new BackgroundColorSpan(0x337857FF), idx, idx + query.length(), 0);
        return span;
    }

    private String formatTime(long ms) {
        if (ms == 0) return "";
        long age = System.currentTimeMillis() - ms;
        long dayMs = 86400000L;
        if (age < dayMs) return "Today";
        if (age < 2 * dayMs) return "Yesterday";
        if (age < 7 * dayMs) return new SimpleDateFormat("EEE", Locale.getDefault()).format(ms);
        return new SimpleDateFormat("MMM d", Locale.getDefault()).format(ms);
    }

    @Override
    public int getItemCount() {
        return results.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle, tvSnippet, tvTime;

        ViewHolder(View v) {
            super(v);
            tvTitle = v.findViewById(R.id.tvTitle);
            tvSnippet = v.findViewById(R.id.tvSnippet);
            tvTime = v.findViewById(R.id.tvTime);
        }
    }
}
