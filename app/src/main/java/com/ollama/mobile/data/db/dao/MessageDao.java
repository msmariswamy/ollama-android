package com.ollama.mobile.data.db.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import com.ollama.mobile.data.db.entity.Message;

import java.util.List;

@Dao
public interface MessageDao {

    @Insert
    long insert(Message message);

    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    void deleteByConversationId(long conversationId);

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    List<Message> getByConversationId(long conversationId);

    @Query("DELETE FROM messages WHERE conversationId = :convId AND id >= :fromId")
    void deleteMessagesFrom(long convId, long fromId);

    @Query("SELECT * FROM messages WHERE content LIKE :query ORDER BY timestamp DESC LIMIT 50")
    List<Message> searchByContent(String query);
}
