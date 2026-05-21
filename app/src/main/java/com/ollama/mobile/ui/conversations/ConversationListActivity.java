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
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.AdapterView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;
import com.google.android.material.snackbar.Snackbar;
import com.ollama.mobile.R;
import com.ollama.mobile.data.db.entity.Conversation;
import com.ollama.mobile.databinding.ActivityConversationListBinding;
import com.ollama.mobile.model.InterviewRole;
import com.ollama.mobile.ui.chat.ChatActivity;
import com.ollama.mobile.ui.interview.InterviewActivity;
import com.ollama.mobile.ui.interview.InterviewHomepageViewModel;
import com.ollama.mobile.ui.settings.SettingsActivity;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ConversationListActivity extends AppCompatActivity {

    private ActivityConversationListBinding binding;
    private ConversationListViewModel viewModel;
    private InterviewHomepageViewModel interviewViewModel;
    private ConversationAdapter adapter;

    private List<Conversation> latestActive = new ArrayList<>();
    private List<Conversation> latestArchived = new ArrayList<>();

    private final Handler deleteHandler = new Handler(Looper.getMainLooper());
    private Runnable pendingDelete;

    private String activeFolder = "All";

    private List<InterviewRole> interviewRoles = new ArrayList<>();
    private List<String> cloudModelNames = new ArrayList<>();
    private ArrayAdapter<String> modelSpinnerAdapter;
    private ArrayAdapter<String> roleSpinnerAdapter;

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
            setupFolderChips();
            applyFolderFilter();
        });

        viewModel.archivedConversations.observe(this, archived -> {
            latestArchived = archived != null ? archived : new ArrayList<>();
            applyFolderFilter();
        });

        binding.fabNewChat.setOnClickListener(v -> openNewChat());

        setupInterviewSection();
    }

    private void setupInterviewSection() {
        interviewViewModel = new ViewModelProvider(this).get(InterviewHomepageViewModel.class);

        // Expand/collapse state
        boolean expanded = interviewViewModel.isInterviewSectionExpanded();
        binding.interviewSectionBody.setVisibility(expanded ? View.VISIBLE : View.GONE);
        binding.ivInterviewExpand.setRotation(expanded ? 180f : 0f);

        binding.interviewSectionHeader.setOnClickListener(v -> {
            boolean isExpanded = binding.interviewSectionBody.getVisibility() == View.VISIBLE;
            boolean nowExpanded = !isExpanded;
            binding.interviewSectionBody.setVisibility(nowExpanded ? View.VISIBLE : View.GONE);
            binding.ivInterviewExpand.animate().rotation(nowExpanded ? 180f : 0f).setDuration(200).start();
            interviewViewModel.setInterviewSectionExpanded(nowExpanded);
        });

        // Model spinner setup
        modelSpinnerAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, cloudModelNames);
        modelSpinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.spinnerInterviewModel.setAdapter(modelSpinnerAdapter);

        // Role spinner setup
        interviewRoles = interviewViewModel.loadRoles();
        List<String> roleTitles = new ArrayList<>();
        for (InterviewRole r : interviewRoles) roleTitles.add(r.title);
        roleTitles.add("Custom");
        roleSpinnerAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, roleTitles);
        roleSpinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.spinnerInterviewRole.setAdapter(roleSpinnerAdapter);

        binding.spinnerInterviewRole.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                boolean isCustom = pos == interviewRoles.size();
                binding.tilCustomRole.setVisibility(isCustom ? View.VISIBLE : View.GONE);
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        // Observe cloud models
        interviewViewModel.getApiKeyMissing().observe(this, missing -> {
            if (Boolean.TRUE.equals(missing)) {
                binding.tvInterviewApiKeyWarning.setVisibility(View.VISIBLE);
                binding.spinnerInterviewModel.setEnabled(false);
                binding.btnStartInterviewSession.setEnabled(false);
            } else {
                binding.tvInterviewApiKeyWarning.setVisibility(View.GONE);
                binding.spinnerInterviewModel.setEnabled(true);
                binding.btnStartInterviewSession.setEnabled(true);
            }
        });

        interviewViewModel.getCloudModels().observe(this, models -> {
            cloudModelNames.clear();
            if (models != null) cloudModelNames.addAll(models);
            modelSpinnerAdapter.notifyDataSetChanged();

            // Pre-select saved model
            String saved = interviewViewModel.getSavedInterviewModel();
            if (!saved.isEmpty()) {
                int idx = cloudModelNames.indexOf(saved);
                if (idx >= 0) binding.spinnerInterviewModel.setSelection(idx);
            }
        });

        // Start Session button
        binding.btnStartInterviewSession.setOnClickListener(v -> {
            int modelIdx = binding.spinnerInterviewModel.getSelectedItemPosition();
            if (cloudModelNames.isEmpty() || modelIdx < 0 || modelIdx >= cloudModelNames.size()) {
                Snackbar.make(binding.getRoot(), "Select a cloud model first", Snackbar.LENGTH_SHORT).show();
                return;
            }
            String selectedModel = cloudModelNames.get(modelIdx);
            interviewViewModel.saveInterviewModel(selectedModel);

            int roleIdx = binding.spinnerInterviewRole.getSelectedItemPosition();
            String customName = "";
            InterviewRole role;
            if (roleIdx >= interviewRoles.size()) {
                // Custom
                customName = binding.etCustomRole.getText() != null
                        ? binding.etCustomRole.getText().toString().trim() : "";
                if (customName.isEmpty()) {
                    Snackbar.make(binding.getRoot(), "Enter a custom role name", Snackbar.LENGTH_SHORT).show();
                    return;
                }
                role = InterviewRole.CUSTOM;
            } else {
                role = interviewRoles.get(roleIdx);
            }

            Intent intent = new Intent(this, InterviewActivity.class);
            intent.putExtra(InterviewActivity.EXTRA_ROLE_ID, role.id);
            intent.putExtra(InterviewActivity.EXTRA_ROLE_TITLE, role.title);
            intent.putExtra(InterviewActivity.EXTRA_ROLE_DESCRIPTION, role.description);
            if (role.technicalSkills != null) {
                intent.putExtra(InterviewActivity.EXTRA_ROLE_SKILLS,
                        role.technicalSkills.toArray(new String[0]));
            }
            intent.putExtra(InterviewActivity.EXTRA_CUSTOM_NAME, customName);
            intent.putExtra(InterviewActivity.EXTRA_CLOUD_MODEL, selectedModel);
            startActivity(intent);
        });

        // Load cloud models
        interviewViewModel.loadCloudModels();
    }

    private void updateEmptyView() {
        boolean empty = latestActive.isEmpty() && latestArchived.isEmpty();
        binding.tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
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
