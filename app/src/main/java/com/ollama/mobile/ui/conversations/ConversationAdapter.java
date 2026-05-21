package com.ollama.mobile.ui.conversations;

import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.ollama.mobile.R;
import com.ollama.mobile.data.db.entity.Conversation;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ConversationAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public interface OnConversationClickListener {
        void onClick(Conversation conversation);
    }

    private static final int VIEW_TYPE_HEADER = 0;
    private static final int VIEW_TYPE_CONVERSATION = 1;

    private static final int[] AVATAR_COLORS = {
        0xFF7857FF, 0xFFFF5590, 0xFF14B8A6, 0xFFF97316, 0xFF3B82F6
    };

    private static class DisplayItem {
        final boolean isHeader;
        final String headerText;
        final Conversation conversation;

        DisplayItem(String headerText) {
            this.isHeader = true;
            this.headerText = headerText;
            this.conversation = null;
        }

        DisplayItem(Conversation conversation) {
            this.isHeader = false;
            this.headerText = null;
            this.conversation = conversation;
        }
    }

    private List<DisplayItem> displayList = new ArrayList<>();
    private final OnConversationClickListener clickListener;
    private static final SimpleDateFormat DATE_FORMAT =
            new SimpleDateFormat("MMM d, h:mm a", Locale.getDefault());

    public ConversationAdapter(OnConversationClickListener clickListener) {
        this.clickListener = clickListener;
    }

    public void setData(List<Conversation> active, List<Conversation> archived) {
        displayList = new ArrayList<>();
        if (active != null) {
            for (Conversation c : active) displayList.add(new DisplayItem(c));
        }
        if (archived != null && !archived.isEmpty()) {
            displayList.add(new DisplayItem("Archived"));
            for (Conversation c : archived) displayList.add(new DisplayItem(c));
        }
        notifyDataSetChanged();
    }

    @Nullable
    public Conversation getConversationAt(int position) {
        if (position < 0 || position >= displayList.size()) return null;
        DisplayItem item = displayList.get(position);
        return item.isHeader ? null : item.conversation;
    }

    @Override
    public int getItemViewType(int position) {
        return displayList.get(position).isHeader ? VIEW_TYPE_HEADER : VIEW_TYPE_CONVERSATION;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == VIEW_TYPE_HEADER) {
            View view = inflater.inflate(R.layout.item_conversation_header, parent, false);
            return new HeaderViewHolder(view);
        } else {
            View view = inflater.inflate(R.layout.item_conversation, parent, false);
            return new ConversationViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        DisplayItem item = displayList.get(position);
        if (item.isHeader) {
            ((HeaderViewHolder) holder).tvHeader.setText(item.headerText);
        } else {
            bindConversation((ConversationViewHolder) holder, item.conversation);
        }
    }

    private void bindConversation(ConversationViewHolder holder, Conversation conv) {
        holder.tvTitle.setText(conv.title);
        holder.tvDate.setText(DATE_FORMAT.format(new Date(conv.updatedAt)));
        holder.itemView.setOnClickListener(v -> clickListener.onClick(conv));

        String letter = (conv.title != null && !conv.title.isEmpty())
                ? conv.title.substring(0, 1).toUpperCase() : "?";
        holder.tvAvatar.setText(letter);
        int colorIdx = Math.abs(conv.title != null ? conv.title.hashCode() : 0) % AVATAR_COLORS.length;
        GradientDrawable oval = new GradientDrawable();
        oval.setShape(GradientDrawable.OVAL);
        oval.setColor(AVATAR_COLORS[colorIdx]);
        holder.tvAvatar.setBackground(oval);
    }

    @Override
    public int getItemCount() {
        return displayList.size();
    }

    static class HeaderViewHolder extends RecyclerView.ViewHolder {
        TextView tvHeader;

        HeaderViewHolder(View itemView) {
            super(itemView);
            tvHeader = (TextView) itemView;
        }
    }

    static class ConversationViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle, tvDate, tvAvatar;

        ConversationViewHolder(View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tvTitle);
            tvDate = itemView.findViewById(R.id.tvDate);
            tvAvatar = itemView.findViewById(R.id.tvAvatar);
        }
    }
}
