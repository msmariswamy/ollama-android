package com.ollama.mobile.ui.chat;

import android.content.Intent;
import android.view.View;
import android.widget.ImageButton;

import androidx.appcompat.app.AppCompatActivity;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.ollama.mobile.R;
import com.ollama.mobile.ui.conversations.ConversationListViewModel;
import com.ollama.mobile.ui.settings.SettingsActivity;

public class SidebarController {

    private final AppCompatActivity activity;
    private final DrawerLayout drawerLayout;
    private final View sidebarView;
    private final ChatViewModel chatViewModel;
    private final SidebarConversationAdapter adapter;

    public interface OnConversationSelectedListener {
        void onConversationSelected(long conversationId);
    }

    public SidebarController(AppCompatActivity activity,
                              DrawerLayout drawerLayout,
                              View sidebarView,
                              ChatViewModel chatViewModel,
                              OnConversationSelectedListener conversationListener) {
        this.activity = activity;
        this.drawerLayout = drawerLayout;
        this.sidebarView = sidebarView;
        this.chatViewModel = chatViewModel;

        adapter = new SidebarConversationAdapter();
        adapter.setOnConversationClickListener(convId -> {
            drawerLayout.closeDrawers();
            conversationListener.onConversationSelected(convId);
        });

        RecyclerView rv = sidebarView.findViewById(R.id.rvSidebarConversations);
        rv.setLayoutManager(new LinearLayoutManager(activity));
        rv.setAdapter(adapter);

        ImageButton btnNew = sidebarView.findViewById(R.id.btnNewChat);
        if (btnNew != null) {
            btnNew.setOnClickListener(v -> {
                drawerLayout.closeDrawers();
                chatViewModel.startNewConversation();
            });
        }

        View searchRow = sidebarView.findViewById(R.id.searchRow);
        if (searchRow != null) {
            searchRow.setOnClickListener(v -> {
                drawerLayout.closeDrawers();
                activity.startActivity(new Intent(activity, SearchActivity.class));
            });
        }

        ImageButton btnSettings = sidebarView.findViewById(R.id.btnSidebarSettings);
        if (btnSettings != null) {
            btnSettings.setOnClickListener(v -> {
                drawerLayout.closeDrawers();
                activity.startActivity(new Intent(activity, SettingsActivity.class));
            });
        }

        // Observe conversations
        ConversationListViewModel listViewModel =
                new ViewModelProvider(activity).get(ConversationListViewModel.class);
        listViewModel.conversations.observe(activity, conversations -> {
            long activeId = chatViewModel.getConversationId();
            adapter.setConversations(conversations, activeId);
        });
    }

    public void openDrawer() {
        drawerLayout.openDrawer(sidebarView);
    }

    public void refreshActiveConversation() {
        // Trigger re-bind by re-setting conversations via ViewModel observation (handled automatically)
    }
}
