package com.ollama.mobile.data.db.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import com.ollama.mobile.data.db.entity.Conversation;

import java.util.List;

@Dao
public interface ConversationDao {

    @Insert
    long insert(Conversation conversation);

    @Query("DELETE FROM conversations WHERE id = :id")
    void deleteById(long id);

    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC")
    LiveData<List<Conversation>> getAll();

    @Query("SELECT * FROM conversations WHERE id = :id LIMIT 1")
    Conversation getById(long id);

    @Query("UPDATE conversations SET updatedAt = :updatedAt WHERE id = :id")
    void updateTimestamp(long id, long updatedAt);

    @Query("UPDATE conversations SET title = :title WHERE id = :id")
    void updateTitle(long id, String title);

    @Query("SELECT * FROM conversations WHERE isArchived = 0 ORDER BY updatedAt DESC")
    LiveData<List<Conversation>> getActive();

    @Query("SELECT * FROM conversations WHERE isArchived = 1 ORDER BY updatedAt DESC")
    LiveData<List<Conversation>> getArchived();

    @Query("UPDATE conversations SET isArchived = :archived WHERE id = :id")
    void setArchived(long id, int archived);

    @Query("UPDATE conversations SET systemPrompt = :systemPrompt WHERE id = :id")
    void setSystemPrompt(long id, String systemPrompt);

    @Query("SELECT * FROM conversations WHERE isArchived = 0 AND title LIKE :query ORDER BY updatedAt DESC")
    List<Conversation> searchByTitle(String query);
}
