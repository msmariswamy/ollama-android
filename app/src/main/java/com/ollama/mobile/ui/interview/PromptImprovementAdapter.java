package com.ollama.mobile.ui.interview;

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

public class PromptImprovementAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_USER = 0;
    private static final int TYPE_AI = 1;

    public interface OnApplyClickListener {
        void onApply(String text);
    }

    private final List<InterviewRoleEditorViewModel.PromptMessage> items = new ArrayList<>();
    private final OnApplyClickListener applyListener;

    public PromptImprovementAdapter(OnApplyClickListener applyListener) {
        this.applyListener = applyListener;
    }

    public void setMessages(List<InterviewRoleEditorViewModel.PromptMessage> messages) {
        items.clear();
        if (messages != null) items.addAll(messages);
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return items.get(position).type == InterviewRoleEditorViewModel.PromptMessage.Type.USER
                ? TYPE_USER : TYPE_AI;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_USER) {
            View view = inflater.inflate(R.layout.item_prompt_user_message, parent, false);
            return new UserViewHolder(view);
        } else {
            View view = inflater.inflate(R.layout.item_prompt_ai_message, parent, false);
            return new AiViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        InterviewRoleEditorViewModel.PromptMessage msg = items.get(position);
        if (holder instanceof UserViewHolder) {
            ((UserViewHolder) holder).tvUserMessage.setText(msg.text);
        } else if (holder instanceof AiViewHolder) {
            AiViewHolder aiHolder = (AiViewHolder) holder;
            aiHolder.tvAiMessage.setText(msg.text);
            aiHolder.btnApply.setOnClickListener(v -> {
                if (applyListener != null) applyListener.onApply(msg.text);
            });
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class UserViewHolder extends RecyclerView.ViewHolder {
        TextView tvUserMessage;

        UserViewHolder(View itemView) {
            super(itemView);
            tvUserMessage = itemView.findViewById(R.id.tvUserMessage);
        }
    }

    static class AiViewHolder extends RecyclerView.ViewHolder {
        TextView tvAiMessage;
        Button btnApply;

        AiViewHolder(View itemView) {
            super(itemView);
            tvAiMessage = itemView.findViewById(R.id.tvAiMessage);
            btnApply = itemView.findViewById(R.id.btnApply);
        }
    }
}
