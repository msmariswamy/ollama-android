package com.ollama.mobile.ui.conversations;

import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.snackbar.Snackbar;
import com.ollama.mobile.R;
import com.ollama.mobile.data.db.entity.Conversation;
import com.ollama.mobile.databinding.ActivityConversationListBinding;
import com.ollama.mobile.ui.chat.ChatActivity;
import com.ollama.mobile.ui.settings.SettingsActivity;

import java.util.ArrayList;
import java.util.List;

public class ConversationListActivity extends AppCompatActivity {

    private ActivityConversationListBinding binding;
    private ConversationListViewModel viewModel;
    private ConversationAdapter adapter;

    private List<Conversation> latestActive = new ArrayList<>();
    private List<Conversation> latestArchived = new ArrayList<>();

    private final Handler deleteHandler = new Handler(Looper.getMainLooper());
    private Runnable pendingDelete;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityConversationListBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setSupportActionBar(binding.toolbar);
        setTitle(R.string.conversations);

        viewModel = new ViewModelProvider(this).get(ConversationListViewModel.class);
        adapter = new ConversationAdapter(conv -> openConversation(conv.id));

        binding.rvConversations.setLayoutManager(new LinearLayoutManager(this));
        binding.rvConversations.setAdapter(adapter);

        new ItemTouchHelper(new ConversationSwipeCallback()).attachToRecyclerView(binding.rvConversations);

        viewModel.conversations.observe(this, conversations -> {
            latestActive = conversations != null ? conversations : new ArrayList<>();
            adapter.setData(latestActive, latestArchived);
            updateEmptyView();
        });

        viewModel.archivedConversations.observe(this, archived -> {
            latestArchived = archived != null ? archived : new ArrayList<>();
            adapter.setData(latestActive, latestArchived);
            updateEmptyView();
        });

        binding.fabNewChat.setOnClickListener(v -> openNewChat());
    }

    private void updateEmptyView() {
        boolean empty = latestActive.isEmpty() && latestArchived.isEmpty();
        binding.tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
    }

    private void scheduleDelete(long conversationId) {
        if (pendingDelete != null) {
            deleteHandler.removeCallbacks(pendingDelete);
            pendingDelete.run(); // commit any previously pending delete immediately
        }
        pendingDelete = () -> {
            viewModel.deleteConversation(conversationId);
            pendingDelete = null;
        };
        deleteHandler.postDelayed(pendingDelete, 5000);
    }

    private void cancelDelete() {
        if (pendingDelete != null) {
            deleteHandler.removeCallbacks(pendingDelete);
            pendingDelete = null;
        }
    }

    private void openConversation(long conversationId) {
        Intent intent = new Intent(this, ChatActivity.class);
        intent.putExtra(ChatActivity.EXTRA_CONVERSATION_ID, conversationId);
        startActivity(intent);
    }

    private void openNewChat() {
        startActivity(new Intent(this, ChatActivity.class));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_conversation_list, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // ── Swipe callback ────────────────────────────────────────────────────────

    private class ConversationSwipeCallback extends ItemTouchHelper.SimpleCallback {

        private final Paint bgPaint = new Paint();
        private final Drawable deleteIcon;
        private final Drawable archiveIcon;
        private final int iconSize;
        private final int iconMargin;

        ConversationSwipeCallback() {
            super(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT);
            deleteIcon = ContextCompat.getDrawable(ConversationListActivity.this, R.drawable.ic_delete_white);
            archiveIcon = ContextCompat.getDrawable(ConversationListActivity.this, R.drawable.ic_archive_white);
            iconSize = (int) (24 * getResources().getDisplayMetrics().density);
            iconMargin = (int) (16 * getResources().getDisplayMetrics().density);
        }

        @Override
        public int getSwipeDirs(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh) {
            // Disable swipe on header rows
            if (adapter.getConversationAt(vh.getAdapterPosition()) == null) return 0;
            return super.getSwipeDirs(rv, vh);
        }

        @Override
        public boolean onMove(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh,
                              @NonNull RecyclerView.ViewHolder target) {
            return false;
        }

        @Override
        public void onSwiped(@NonNull RecyclerView.ViewHolder vh, int direction) {
            int position = vh.getAdapterPosition();
            Conversation conv = adapter.getConversationAt(position);
            if (conv == null) return;

            if (direction == ItemTouchHelper.LEFT) {
                // Delete with undo
                scheduleDelete(conv.id);
                Snackbar.make(binding.getRoot(), R.string.conversation_deleted, Snackbar.LENGTH_LONG)
                        .setAction(R.string.undo, v -> cancelDelete())
                        .addCallback(new Snackbar.Callback() {
                            @Override
                            public void onDismissed(Snackbar sb, int event) {
                                // pendingDelete fires on its own after 5 s via Handler
                            }
                        })
                        .show();
            } else {
                // RIGHT swipe — archive or unarchive depending on current state
                if (conv.isArchived == 1) {
                    viewModel.unarchiveConversation(conv.id);
                    Snackbar.make(binding.getRoot(), R.string.conversation_unarchived, Snackbar.LENGTH_SHORT)
                            .show();
                } else {
                    viewModel.archiveConversation(conv.id);
                    Snackbar.make(binding.getRoot(), R.string.conversation_archived, Snackbar.LENGTH_SHORT)
                            .setAction(R.string.undo, v -> viewModel.unarchiveConversation(conv.id))
                            .show();
                }
            }
        }

        @Override
        public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView rv,
                                @NonNull RecyclerView.ViewHolder vh, float dX, float dY,
                                int actionState, boolean isCurrentlyActive) {
            View itemView = vh.itemView;
            int top = itemView.getTop();
            int bottom = itemView.getBottom();
            int iconTop = top + (bottom - top - iconSize) / 2;
            int iconBottom = iconTop + iconSize;

            if (dX < 0) {
                // Swiping left → red background, trash icon on right
                bgPaint.setColor(Color.parseColor("#EF4444"));
                c.drawRect(itemView.getRight() + dX, top, itemView.getRight(), bottom, bgPaint);
                if (deleteIcon != null) {
                    int iconRight = itemView.getRight() - iconMargin;
                    deleteIcon.setBounds(iconRight - iconSize, iconTop, iconRight, iconBottom);
                    deleteIcon.draw(c);
                }
            } else if (dX > 0) {
                // Swiping right → amber background, archive icon on left
                bgPaint.setColor(Color.parseColor("#F97316"));
                c.drawRect(itemView.getLeft(), top, itemView.getLeft() + dX, bottom, bgPaint);
                if (archiveIcon != null) {
                    int iconLeft = itemView.getLeft() + iconMargin;
                    archiveIcon.setBounds(iconLeft, iconTop, iconLeft + iconSize, iconBottom);
                    archiveIcon.draw(c);
                }
            }

            super.onChildDraw(c, rv, vh, dX, dY, actionState, isCurrentlyActive);
        }
    }
}
