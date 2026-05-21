package com.ollama.mobile.ui.chat;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.ollama.mobile.R;
import com.ollama.mobile.model.ChatMessage;

import io.noties.markwon.Markwon;

import java.util.ArrayList;
import java.util.List;

public class ChatMessageAdapter extends RecyclerView.Adapter<ChatMessageAdapter.MessageViewHolder> {

    private static final int VIEW_TYPE_USER = 0;
    private static final int VIEW_TYPE_ASSISTANT = 1;
    private static final int MENU_COPY_ALL = 9901;

    private List<UiMessage> messages = new ArrayList<>();
    private OnMessageLongPressListener longPressListener;
    private OnRegenerateListener regenerateListener;
    private OnEditListener editListener;

    public interface OnMessageLongPressListener {
        void onLongPress(int position, UiMessage message);
    }

    public interface OnRegenerateListener {
        void onRegenerate(int position, UiMessage message);
    }

    public interface OnEditListener {
        void onEdit(int position, UiMessage message);
    }

    public void setOnMessageLongPressListener(OnMessageLongPressListener listener) {
        this.longPressListener = listener;
    }

    public void setOnRegenerateListener(OnRegenerateListener listener) {
        this.regenerateListener = listener;
    }

    public void setOnEditListener(OnEditListener listener) {
        this.editListener = listener;
    }

    public void setMessages(List<UiMessage> newMessages) {
        DiffUtil.DiffResult result = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override public int getOldListSize() { return messages.size(); }
            @Override public int getNewListSize() { return newMessages.size(); }

            @Override
            public boolean areItemsTheSame(int oldPos, int newPos) {
                return oldPos == newPos;
            }

            @Override
            public boolean areContentsTheSame(int oldPos, int newPos) {
                UiMessage o = messages.get(oldPos);
                UiMessage n = newMessages.get(newPos);
                return o.content.equals(n.content)
                        && o.isStreaming == n.isStreaming
                        && o.isError == n.isError
                        && (o.thinking == null ? n.thinking == null : o.thinking.equals(n.thinking));
            }
        });
        messages = new ArrayList<>(newMessages);
        result.dispatchUpdatesTo(this);
    }

    @Override
    public int getItemViewType(int position) {
        return ChatMessage.ROLE_USER.equals(messages.get(position).role)
                ? VIEW_TYPE_USER : VIEW_TYPE_ASSISTANT;
    }

    @NonNull
    @Override
    public MessageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layout = viewType == VIEW_TYPE_USER
                ? R.layout.item_message_user
                : R.layout.item_message_assistant;
        View view = LayoutInflater.from(parent.getContext()).inflate(layout, parent, false);
        return new MessageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MessageViewHolder holder, int position) {
        UiMessage msg = messages.get(position);

        if (holder.thinkingContainer != null) {
            boolean hasThinking = msg.thinking != null && !msg.thinking.isEmpty();
            holder.thinkingContainer.setVisibility(hasThinking ? View.VISIBLE : View.GONE);
            if (hasThinking && holder.tvThinkingContent != null) {
                holder.tvThinkingContent.setText(msg.thinking);
            }
        }

        boolean contentEmpty = msg.content == null || msg.content.isEmpty();
        final String fullContent = msg.content != null ? msg.content : "";

        if (getItemViewType(position) == VIEW_TYPE_ASSISTANT) {
            bindAssistantContent(holder, msg, fullContent, contentEmpty);

            // Action row visibility
            if (holder.actionRow != null) {
                boolean showActions = !msg.isStreaming && !msg.isError && !fullContent.isEmpty();
                holder.actionRow.setVisibility(showActions ? View.VISIBLE : View.GONE);
            }

            // Wire action buttons
            if (holder.btnCopy != null) {
                holder.btnCopy.setOnClickListener(v -> copyToClipboard(v.getContext(), fullContent));
            }
            if (holder.btnRegenerate != null) {
                final int pos = position;
                holder.btnRegenerate.setOnClickListener(v -> {
                    if (regenerateListener != null) regenerateListener.onRegenerate(pos, msg);
                });
            }
            if (holder.btnThumbUp != null && holder.btnThumbDown != null) {
                updateThumbTints(holder, msg.rating, holder.btnCopy.getContext());
                holder.btnThumbUp.setOnClickListener(v -> {
                    UiMessage.Rating next = msg.rating == UiMessage.Rating.UP
                            ? UiMessage.Rating.NONE : UiMessage.Rating.UP;
                    msg.rating = next;
                    updateThumbTints(holder, next, v.getContext());
                });
                holder.btnThumbDown.setOnClickListener(v -> {
                    UiMessage.Rating next = msg.rating == UiMessage.Rating.DOWN
                            ? UiMessage.Rating.NONE : UiMessage.Rating.DOWN;
                    msg.rating = next;
                    updateThumbTints(holder, next, v.getContext());
                });
            }
        } else {
            // User message — plain text only
            if (holder.tvContent != null) {
                if (msg.isStreaming && contentEmpty) {
                    holder.tvContent.setVisibility(View.VISIBLE);
                    holder.tvContent.setText("…");
                } else {
                    holder.tvContent.setVisibility(View.VISIBLE);
                    holder.tvContent.setText(fullContent);
                }
                holder.tvContent.setTextIsSelectable(!msg.isStreaming);
                holder.tvContent.setCustomSelectionActionModeCallback(
                        makeCopyAllCallback(holder.tvContent.getContext(), fullContent));
            }

            // Show edit button when not streaming
            if (holder.userActionRow != null) {
                holder.userActionRow.setVisibility(msg.isStreaming ? View.GONE : View.VISIBLE);
            }
            if (holder.btnEdit != null) {
                final int pos = position;
                holder.btnEdit.setOnClickListener(v -> {
                    if (editListener != null) editListener.onEdit(pos, msg);
                });
            }
        }

        // Thinking block: always selectable once visible
        if (holder.tvThinkingContent != null) {
            final String thinking = msg.thinking != null ? msg.thinking : "";
            holder.tvThinkingContent.setTextIsSelectable(!thinking.isEmpty());
            if (!thinking.isEmpty()) {
                holder.tvThinkingContent.setCustomSelectionActionModeCallback(
                        makeCopyAllCallback(holder.tvThinkingContent.getContext(), thinking));
            }
        }

        // Long-press on user bubbles: show Edit / Copy dialog
        if (getItemViewType(position) == VIEW_TYPE_USER) {
            if (!msg.isStreaming && longPressListener != null) {
                final int pos = position;
                holder.itemView.setOnLongClickListener(v -> {
                    longPressListener.onLongPress(pos, msg);
                    return true;
                });
            } else {
                holder.itemView.setOnLongClickListener(null);
            }
        }

        // Bind attachment chips for user messages
        if (holder.rvAttachments != null) {
            boolean hasAttachments = msg.attachments != null && !msg.attachments.isEmpty();
            if (hasAttachments) {
                holder.rvAttachments.setVisibility(View.VISIBLE);
                if (holder.rvAttachments.getLayoutManager() == null) {
                    LinearLayoutManager lm = new LinearLayoutManager(
                        holder.rvAttachments.getContext(), LinearLayoutManager.HORIZONTAL, false);
                    holder.rvAttachments.setLayoutManager(lm);
                }
                AttachmentChipAdapter chipAdapter = new AttachmentChipAdapter(false);
                chipAdapter.setAttachments(msg.attachments);
                holder.rvAttachments.setAdapter(chipAdapter);
            } else {
                holder.rvAttachments.setVisibility(View.GONE);
            }
        }
    }

    // ── Assistant content rendering ───────────────────────────────────────────

    private void bindAssistantContent(MessageViewHolder holder, UiMessage msg,
                                      String fullContent, boolean contentEmpty) {
        if (holder.contentContainer == null) return;
        LinearLayout container = holder.contentContainer;
        Context ctx = container.getContext();
        container.removeAllViews();

        // Streaming with no content yet — show ellipsis placeholder
        if (msg.isStreaming && contentEmpty) {
            boolean hasThinking = msg.thinking != null && !msg.thinking.isEmpty();
            if (!hasThinking) {
                TextView tv = makePlainTextView(ctx, msg.isError);
                tv.setText("…");
                container.addView(tv);
            }
            return;
        }

        // During streaming: skip segment parsing to avoid layout thrash; render as Markwon text
        if (msg.isStreaming) {
            TextView tv = makeMarkwonTextView(ctx, fullContent, msg.isError);
            container.addView(tv);
            return;
        }

        // Final content: segment-based rendering with proper code blocks
        List<ContentSegment> segments = CodeBlockParser.parse(fullContent);
        if (segments.isEmpty()) {
            TextView tv = makePlainTextView(ctx, msg.isError);
            tv.setText("…");
            container.addView(tv);
            return;
        }
        for (ContentSegment segment : segments) {
            if (segment instanceof ContentSegment.Code) {
                ContentSegment.Code cs = (ContentSegment.Code) segment;
                View codeView = LayoutInflater.from(ctx)
                        .inflate(R.layout.layout_code_block, container, false);
                bindCodeBlock(codeView, cs.language, cs.code, ctx);
                container.addView(codeView);
            } else {
                ContentSegment.Text ts = (ContentSegment.Text) segment;
                TextView tv = makeMarkwonTextView(ctx, ts.markdown, msg.isError);
                container.addView(tv);
            }
        }
    }

    private TextView makeMarkwonTextView(Context ctx, String markdown, boolean isError) {
        TextView tv = makePlainTextView(ctx, isError);
        Markwon markwon = MarkwonProvider.get(ctx);
        markwon.setMarkdown(tv, markdown);
        tv.setCustomSelectionActionModeCallback(makeCopyAllCallback(ctx, markdown));
        return tv;
    }

    private TextView makePlainTextView(Context ctx, boolean isError) {
        TextView tv = new TextView(ctx);
        tv.setTextSize(15);
        tv.setLineSpacing(2f, 1f);
        tv.setTextIsSelectable(true);
        tv.setTextColor(isError ? 0xFFEF4444 : ContextCompat.getColor(ctx, R.color.colorTextPrimary));
        return tv;
    }

    private void bindCodeBlock(View codeView, String language, String code, Context ctx) {
        TextView tvLang = codeView.findViewById(R.id.tvCodeLanguage);
        TextView tvCode = codeView.findViewById(R.id.tvCodeContent);
        MaterialButton btnCopy = codeView.findViewById(R.id.btnCopyCode);

        tvLang.setText(language.isEmpty() ? "" : language);
        tvCode.setText(code);

        btnCopy.setOnClickListener(v -> copyToClipboard(ctx, code));
    }

    // ── Shared helpers ────────────────────────────────────────────────────────

    private ActionMode.Callback makeCopyAllCallback(Context ctx, String fullText) {
        return new ActionMode.Callback() {
            @Override
            public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                menu.add(Menu.NONE, MENU_COPY_ALL, Menu.NONE, "Copy All")
                        .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
                return true;
            }

            @Override public boolean onPrepareActionMode(ActionMode mode, Menu menu) { return false; }

            @Override
            public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                if (item.getItemId() == MENU_COPY_ALL) {
                    copyToClipboard(ctx, fullText);
                    mode.finish();
                    return true;
                }
                return false;
            }

            @Override public void onDestroyActionMode(ActionMode mode) {}
        };
    }

    private void copyToClipboard(Context ctx, String text) {
        ClipboardManager cm = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("message", text));
            Toast.makeText(ctx, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show();
        }
    }

    private void updateThumbTints(MessageViewHolder holder, UiMessage.Rating rating, Context ctx) {
        int mutedColor = ContextCompat.getColor(ctx, R.color.colorTextMuted);
        int activeColor = ContextCompat.getColor(ctx, R.color.colorPrimary);
        if (holder.btnThumbUp != null) {
            holder.btnThumbUp.setColorFilter(rating == UiMessage.Rating.UP ? activeColor : mutedColor);
        }
        if (holder.btnThumbDown != null) {
            holder.btnThumbDown.setColorFilter(rating == UiMessage.Rating.DOWN ? activeColor : mutedColor);
        }
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    static class MessageViewHolder extends RecyclerView.ViewHolder {
        /** For user messages only. */
        @Nullable TextView tvContent;
        /** For assistant messages only. */
        @Nullable LinearLayout contentContainer;
        @Nullable View thinkingContainer;
        @Nullable TextView tvThinkingContent;
        @Nullable RecyclerView rvAttachments;
        @Nullable View actionRow;
        @Nullable ImageButton btnCopy;
        @Nullable ImageButton btnRegenerate;
        @Nullable ImageButton btnThumbUp;
        @Nullable ImageButton btnThumbDown;
        @Nullable View userActionRow;
        @Nullable ImageButton btnEdit;

        MessageViewHolder(View itemView) {
            super(itemView);
            tvContent = itemView.findViewById(R.id.tvContent);
            contentContainer = itemView.findViewById(R.id.contentContainer);
            thinkingContainer = itemView.findViewById(R.id.tvThinking);
            tvThinkingContent = itemView.findViewById(R.id.tvThinkingContent);
            rvAttachments = itemView.findViewById(R.id.rvAttachments);
            actionRow = itemView.findViewById(R.id.actionRow);
            btnCopy = itemView.findViewById(R.id.btnCopy);
            btnRegenerate = itemView.findViewById(R.id.btnRegenerate);
            btnThumbUp = itemView.findViewById(R.id.btnThumbUp);
            btnThumbDown = itemView.findViewById(R.id.btnThumbDown);
            userActionRow = itemView.findViewById(R.id.userActionRow);
            btnEdit = itemView.findViewById(R.id.btnEdit);
        }
    }
}
