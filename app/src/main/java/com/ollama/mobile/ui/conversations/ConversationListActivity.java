package com.ollama.mobile.ui.conversations;

import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.snackbar.Snackbar;
import com.ollama.mobile.R;
import com.ollama.mobile.data.db.entity.Conversation;
import com.ollama.mobile.databinding.ActivityConversationListBinding;
import com.ollama.mobile.ui.chat.ChatActivity;
import com.ollama.mobile.ui.interview.InterviewSessionFilesActivity;
import com.ollama.mobile.ui.interview.InterviewSetupActivity;
import com.ollama.mobile.ui.model.ModelLibraryActivity;
import com.ollama.mobile.ui.settings.SettingsActivity;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ConversationListActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener {

    private ActivityConversationListBinding binding;
    private ConversationListViewModel viewModel;
    private ConversationAdapter adapter;

    private List<Conversation> latestActive = new ArrayList<>();
    private List<Conversation> latestArchived = new ArrayList<>();

    private final Handler deleteHandler = new Handler(Looper.getMainLooper());
    private Runnable pendingDelete;

    private String activeFolder = "All";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityConversationListBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setSupportActionBar(binding.toolbar);
        setTitle(R.string.conversations);

        setupDrawer();

        viewModel = new ViewModelProvider(this).get(ConversationListViewModel.class);
        adapter = new ConversationAdapter(conv -> openConversation(conv.id));

        binding.rvConversations.setLayoutManager(new LinearLayoutManager(this));
        binding.rvConversations.setAdapter(adapter);

        new ItemTouchHelper(new ConversationSwipeCallback()).attachToRecyclerView(binding.rvConversations);

        viewModel.conversations.observe(this, conversations -> {
            latestActive = conversations != null ? conversations : new ArrayList<>();
            setupFolderChips();
            applyFolderFilter();
        });

        viewModel.archivedConversations.observe(this, archived -> {
            latestArchived = archived != null ? archived : new ArrayList<>();
            applyFolderFilter();
        });

        binding.fabNewChat.setOnClickListener(v -> openNewChat());
    }

    private void setupDrawer() {
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, binding.drawerLayout, binding.toolbar,
                R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        binding.drawerLayout.addDrawerListener(toggle);
        toggle.syncState();

        binding.navigationView.setNavigationItemSelectedListener(this);
        binding.navigationView.setCheckedItem(R.id.nav_conversations);
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.nav_conversations) {
            // Already here — just close drawer
        } else if (id == R.id.nav_interview) {
            startActivity(new Intent(this, InterviewSetupActivity.class));
        } else if (id == R.id.nav_session_files) {
            startActivity(new Intent(this, InterviewSessionFilesActivity.class));
        } else if (id == R.id.nav_models) {
            startActivity(new Intent(this, ModelLibraryActivity.class));
        } else if (id == R.id.nav_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
        }
        binding.drawerLayout.closeDrawer(GravityCompat.START);
        return true;
    }

    @Override
    public void onBackPressed() {
        if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
            binding.drawerLayout.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    private boolean folderChipsInitialized = false;

    private void setupFolderChips() {
        if (folderChipsInitialized) return;
        folderChipsInitialized = true;

        long now = System.currentTimeMillis();
        long todayStart = now - (now % 86_400_000L);
        long weekStart = now - 7L * 86_400_000L;

        long todayCount = latestActive.stream().filter(c -> c.updatedAt >= todayStart).count();
        long weekCount = latestActive.stream().filter(c -> c.updatedAt >= weekStart && c.updatedAt < todayStart).count();

        String[][] folders = {
                {"All", String.valueOf(latestActive.size())},
                {"Today", String.valueOf(todayCount)},
                {"This week", String.valueOf(weekCount)},
                {"Archived", String.valueOf(latestArchived.size())}
        };

        binding.chipGroupFolders.removeAllViews();
        for (String[] f : folders) {
            Chip chip = new Chip(this);
            chip.setText(f[0] + "  " + f[1]);
            chip.setCheckable(true);
            chip.setChecked("All".equals(f[0]));
            chip.setChipBackgroundColorResource(R.color.colorCard);
            chip.setTextColor(getColor(R.color.colorTextPrimary));
            binding.chipGroupFolders.addView(chip);
            chip.setOnClickListener(v -> {
                activeFolder = f[0];
                applyFolderFilter();
            });
        }
    }

    private void applyFolderFilter() {
        long now = System.currentTimeMillis();
        long todayStart = now - (now % 86_400_000L);
        long weekStart = now - 7L * 86_400_000L;

        List<Conversation> filtered;
        List<Conversation> archivedFiltered = new ArrayList<>();

        switch (activeFolder) {
            case "Today":
                filtered = latestActive.stream()
                        .filter(c -> c.updatedAt >= todayStart)
                        .collect(Collectors.toList());
                break;
            case "This week":
                filtered = latestActive.stream()
                        .filter(c -> c.updatedAt >= weekStart && c.updatedAt < todayStart)
                        .collect(Collectors.toList());
                break;
            case "Archived":
                filtered = new ArrayList<>();
                archivedFiltered = latestArchived;
                break;
            default:
                filtered = latestActive;
                archivedFiltered = latestArchived;
                break;
        }

        adapter.setData(filtered, archivedFiltered);
        boolean empty = filtered.isEmpty() && archivedFiltered.isEmpty();
        binding.tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
    }

    private void scheduleDelete(long conversationId) {
        if (pendingDelete != null) {
            deleteHandler.removeCallbacks(pendingDelete);
            pendingDelete.run();
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
                scheduleDelete(conv.id);
                Snackbar.make(binding.getRoot(), R.string.conversation_deleted, Snackbar.LENGTH_LONG)
                        .setAction(R.string.undo, v -> cancelDelete())
                        .addCallback(new Snackbar.Callback() {
                            @Override
                            public void onDismissed(Snackbar sb, int event) {}
                        })
                        .show();
            } else {
                if (conv.isArchived == 1) {
                    viewModel.unarchiveConversation(conv.id);
                    Snackbar.make(binding.getRoot(), R.string.conversation_unarchived, Snackbar.LENGTH_SHORT).show();
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
                bgPaint.setColor(Color.parseColor("#EF4444"));
                c.drawRect(itemView.getRight() + dX, top, itemView.getRight(), bottom, bgPaint);
                if (deleteIcon != null) {
                    int iconRight = itemView.getRight() - iconMargin;
                    deleteIcon.setBounds(iconRight - iconSize, iconTop, iconRight, iconBottom);
                    deleteIcon.draw(c);
                }
            } else if (dX > 0) {
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
