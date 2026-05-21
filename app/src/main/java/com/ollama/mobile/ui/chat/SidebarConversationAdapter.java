package com.ollama.mobile.ui.chat;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.ollama.mobile.R;
import com.ollama.mobile.data.db.entity.Conversation;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class SidebarConversationAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_HEADER = 0;
    private static final int VIEW_TYPE_ITEM = 1;

    private List<Object> items = new ArrayList<>(); // String headers or Conversation items
    private long activeConversationId = -1;
    private OnConversationClickListener clickListener;

    public interface OnConversationClickListener {
        void onClick(long conversationId);
    }

    public void setOnConversationClickListener(OnConversationClickListener listener) {
        this.clickListener = listener;
    }

    public void setConversations(List<Conversation> conversations, long activeId) {
        this.activeConversationId = activeId;
        items.clear();

        List<Conversation> today = new ArrayList<>();
        List<Conversation> yesterday = new ArrayList<>();
        List<Conversation> thisWeek = new ArrayList<>();
        List<Conversation> older = new ArrayList<>();

        Calendar cal = Calendar.getInstance();
        long nowMs = cal.getTimeInMillis();
        long dayMs = 86400000L;

        for (Conversation conv : conversations) {
            long age = nowMs - conv.updatedAt;
            if (age < dayMs) {
                today.add(conv);
            } else if (age < 2 * dayMs) {
                yesterday.add(conv);
            } else if (age < 7 * dayMs) {
                thisWeek.add(conv);
            } else {
                older.add(conv);
            }
        }

        addGroup("Today", today);
        addGroup("Yesterday", yesterday);
        addGroup("This week", thisWeek);
        addGroup("Older", older);

        notifyDataSetChanged();
    }

    private void addGroup(String label, List<Conversation> group) {
        if (!group.isEmpty()) {
            items.add(label);
            items.addAll(group);
        }
    }

    @Override
    public int getItemViewType(int position) {
        return items.get(position) instanceof String ? VIEW_TYPE_HEADER : VIEW_TYPE_ITEM;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == VIEW_TYPE_HEADER) {
            View v = inflater.inflate(R.layout.item_conversation_header, parent, false);
            return new HeaderViewHolder(v);
        } else {
            View v = inflater.inflate(R.layout.item_sidebar_conversation, parent, false);
            return new ItemViewHolder(v);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof HeaderViewHolder) {
            ((HeaderViewHolder) holder).tvHeader.setText((String) items.get(position));
        } else if (holder instanceof ItemViewHolder) {
            Conversation conv = (Conversation) items.get(position);
            ItemViewHolder vh = (ItemViewHolder) holder;
            vh.tvTitle.setText(conv.title != null ? conv.title : "New Chat");
            vh.tvTime.setText(formatTime(conv.updatedAt));
            boolean isActive = conv.id == activeConversationId;
            vh.itemView.setBackgroundColor(isActive
                    ? 0x1A7857FF  // subtle primary tint
                    : android.graphics.Color.TRANSPARENT);
            vh.itemView.setOnClickListener(v -> {
                if (clickListener != null) clickListener.onClick(conv.id);
            });
        }
    }

    private String formatTime(long ms) {
        if (ms == 0) return "";
        Calendar msgCal = Calendar.getInstance();
        msgCal.setTimeInMillis(ms);
        Calendar now = Calendar.getInstance();
        long age = now.getTimeInMillis() - ms;
        long dayMs = 86400000L;
        if (age < dayMs) {
            return new SimpleDateFormat("h:mm a", Locale.getDefault()).format(ms);
        } else if (age < 7 * dayMs) {
            return new SimpleDateFormat("EEE", Locale.getDefault()).format(ms);
        } else {
            return new SimpleDateFormat("MMM d", Locale.getDefault()).format(ms);
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class HeaderViewHolder extends RecyclerView.ViewHolder {
        TextView tvHeader;
        HeaderViewHolder(View v) {
            super(v);
            // item_conversation_header.xml root is a TextView
            tvHeader = (v instanceof TextView) ? (TextView) v : (TextView) v.findViewById(android.R.id.text1);
        }
    }

    static class ItemViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle;
        TextView tvTime;
        ItemViewHolder(View v) {
            super(v);
            tvTitle = v.findViewById(R.id.tvTitle);
            tvTime = v.findViewById(R.id.tvTime);
        }
    }
}
