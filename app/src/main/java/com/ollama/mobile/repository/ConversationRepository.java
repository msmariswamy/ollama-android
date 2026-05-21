package com.ollama.mobile.repository;

import androidx.lifecycle.LiveData;

import com.ollama.mobile.data.db.dao.ConversationDao;
import com.ollama.mobile.data.db.dao.MessageDao;
import com.ollama.mobile.data.db.entity.Conversation;
import com.ollama.mobile.data.db.entity.Message;
import com.ollama.mobile.model.ChatMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ConversationRepository {

    private final ConversationDao conversationDao;
    private final MessageDao messageDao;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public ConversationRepository(ConversationDao conversationDao, MessageDao messageDao) {
        this.conversationDao = conversationDao;
        this.messageDao = messageDao;
    }

    public LiveData<List<Conversation>> getAllConversations() {
        return conversationDao.getActive();
    }

    public LiveData<List<Conversation>> getActiveConversations() {
        return conversationDao.getActive();
    }

    public LiveData<List<Conversation>> getArchivedConversations() {
        return conversationDao.getArchived();
    }

    public void archiveConversation(long id) {
        executor.execute(() -> conversationDao.setArchived(id, 1));
    }

    public void unarchiveConversation(long id) {
        executor.execute(() -> conversationDao.setArchived(id, 0));
    }

    public void updateTitle(long id, String title) {
        executor.execute(() -> conversationDao.updateTitle(id, title));
    }

    public void createConversation(String title, Callback<Long> callback) {
        executor.execute(() -> {
            long now = System.currentTimeMillis();
            long id = conversationDao.insert(new Conversation(title, now, now));
            callback.onResult(id);
        });
    }

    public void deleteConversation(long id) {
        executor.execute(() -> conversationDao.deleteById(id));
    }

    public void insertMessage(long conversationId, String role, String content, Callback<Long> callback) {
        insertMessage(conversationId, role, content, null, callback);
    }

    public void insertMessage(long conversationId, String role, String content, String attachmentsJson, Callback<Long> callback) {
        executor.execute(() -> {
            long now = System.currentTimeMillis();
            Message msg = new Message(conversationId, role, content, now);
            msg.attachmentsJson = attachmentsJson;
            long id = messageDao.insert(msg);
            conversationDao.updateTimestamp(conversationId, now);
            if (callback != null) callback.onResult(id);
        });
    }

    public void loadMessages(long conversationId, Callback<List<ChatMessage>> callback) {
        executor.execute(() -> {
            List<Message> dbMessages = messageDao.getByConversationId(conversationId);
            List<ChatMessage> result = new ArrayList<>();
            for (Message m : dbMessages) {
                result.add(new ChatMessage(m.role, m.content));
            }
            callback.onResult(result);
        });
    }

    public void getConversationById(long id, Callback<com.ollama.mobile.data.db.entity.Conversation> callback) {
        executor.execute(() -> callback.onResult(conversationDao.getById(id)));
    }

    public void loadMessagesWithMeta(long conversationId, Callback<List<Message>> callback) {
        executor.execute(() -> {
            List<Message> messages = messageDao.getByConversationId(conversationId);
            callback.onResult(messages);
        });
    }

    public void deleteMessagesFrom(long convId, long fromDbId, Runnable onDone) {
        executor.execute(() -> {
            messageDao.deleteMessagesFrom(convId, fromDbId);
            if (onDone != null) onDone.run();
        });
    }

    public void updateMessageContent(long messageId, String content) {
        executor.execute(() -> {
            // Direct SQL update for streaming completions
        });
    }

    public void setSystemPrompt(long conversationId, String systemPrompt) {
        executor.execute(() -> conversationDao.setSystemPrompt(conversationId, systemPrompt));
    }

    public void getSystemPrompt(long conversationId, Callback<String> callback) {
        executor.execute(() -> {
            Conversation conv = conversationDao.getById(conversationId);
            callback.onResult(conv != null ? conv.systemPrompt : null);
        });
    }

    public void searchConversations(String query, Callback<List<SearchResult>> callback) {
        executor.execute(() -> {
            String like = "%" + query + "%";
            List<Conversation> titleMatches = conversationDao.searchByTitle(like);
            List<Message> messageMatches = messageDao.searchByContent(like);

            List<SearchResult> results = new ArrayList<>();
            java.util.Set<Long> seen = new java.util.HashSet<>();

            for (Conversation conv : titleMatches) {
                if (!seen.contains(conv.id)) {
                    seen.add(conv.id);
                    results.add(new SearchResult(conv.id, conv.title, "", "", conv.updatedAt));
                }
            }
            for (Message msg : messageMatches) {
                if (!seen.contains(msg.conversationId)) {
                    seen.add(msg.conversationId);
                    Conversation conv = conversationDao.getById(msg.conversationId);
                    String title = conv != null ? conv.title : "";
                    String snippet = msg.content != null && msg.content.length() > 120
                            ? msg.content.substring(0, 120) + "…" : (msg.content != null ? msg.content : "");
                    results.add(new SearchResult(msg.conversationId, title, snippet, "", msg.timestamp));
                }
            }
            callback.onResult(results);
        });
    }

    public static class SearchResult {
        public final long conversationId;
        public final String title;
        public final String snippet;
        public final String modelName;
        public final long timestamp;

        public SearchResult(long conversationId, String title, String snippet, String modelName, long timestamp) {
            this.conversationId = conversationId;
            this.title = title;
            this.snippet = snippet;
            this.modelName = modelName;
            this.timestamp = timestamp;
        }
    }

    public interface Callback<T> {
        void onResult(T result);
    }
}
