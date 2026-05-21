package com.ollama.mobile;

import android.content.Context;

import com.ollama.mobile.data.db.AppDatabase;
import com.ollama.mobile.network.OllamaApiService;
import com.ollama.mobile.network.OllamaClient;
import com.ollama.mobile.repository.ChatRepository;
import com.ollama.mobile.repository.ModelRepository;
import com.ollama.mobile.repository.SettingsRepository;
import com.ollama.mobile.repository.ConversationRepository;

public class AppContainer {

    public final SettingsRepository settingsRepository;
    public final AppDatabase database;
    public final ModelRepository modelRepository;
    public final ChatRepository chatRepository;
    public final ConversationRepository conversationRepository;
    public final OllamaClient ollamaClient;

    public AppContainer(Context context) {
        settingsRepository = new SettingsRepository(context);
        database = AppDatabase.getInstance(context);
        ollamaClient = new OllamaClient(settingsRepository);
        OllamaApiService apiService = ollamaClient.getApiService();
        modelRepository = new ModelRepository(apiService);
        chatRepository = new ChatRepository(ollamaClient);
        conversationRepository = new ConversationRepository(
                database.conversationDao(),
                database.messageDao()
        );
    }
}
