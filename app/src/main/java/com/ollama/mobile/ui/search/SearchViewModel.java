package com.ollama.mobile.ui.search;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;

import com.ollama.mobile.OllamaApp;
import com.ollama.mobile.repository.ConversationRepository;

import java.util.List;

public class SearchViewModel extends AndroidViewModel {

    public final MutableLiveData<List<ConversationRepository.SearchResult>> results =
            new MutableLiveData<>();
    public final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);

    private final ConversationRepository conversationRepository;

    public SearchViewModel(@NonNull Application application) {
        super(application);
        conversationRepository = ((OllamaApp) application).getAppContainer().conversationRepository;
    }

    public void search(String query) {
        if (query == null || query.trim().length() < 2) {
            results.setValue(null);
            return;
        }
        isLoading.setValue(true);
        conversationRepository.searchConversations(query.trim(), searchResults -> {
            results.postValue(searchResults);
            isLoading.postValue(false);
        });
    }
}
