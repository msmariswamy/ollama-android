package com.ollama.mobile.ui.conversations;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.ollama.mobile.OllamaApp;
import com.ollama.mobile.data.db.entity.Conversation;
import com.ollama.mobile.repository.ConversationRepository;

import java.util.List;

public class ConversationListViewModel extends AndroidViewModel {

    ConversationRepository conversationRepository;
    public LiveData<List<Conversation>> conversations;
    public LiveData<List<Conversation>> archivedConversations;

    public ConversationListViewModel(@NonNull Application application) {
        super(application);
        conversationRepository = ((OllamaApp) application).getAppContainer().conversationRepository;
        conversations = conversationRepository.getActiveConversations();
        archivedConversations = conversationRepository.getArchivedConversations();
    }

    public ConversationListViewModel(Application app, ConversationRepository convRepo) {
        super(app);
        this.conversationRepository = convRepo;
        this.conversations = convRepo.getActiveConversations();
        this.archivedConversations = convRepo.getArchivedConversations();
    }

    public void deleteConversation(long id) {
        conversationRepository.deleteConversation(id);
    }

    public void archiveConversation(long id) {
        conversationRepository.archiveConversation(id);
    }

    public void unarchiveConversation(long id) {
        conversationRepository.unarchiveConversation(id);
    }

    public void updateConversationTitle(long id, String title) {
        conversationRepository.updateTitle(id, title);
    }
}
