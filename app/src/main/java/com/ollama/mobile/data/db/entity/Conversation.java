package com.ollama.mobile.data.db.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "conversations")
public class Conversation {

    @PrimaryKey(autoGenerate = true)
    public long id;

    public String title;
    public long createdAt;
    public long updatedAt;
    public int isArchived = 0;
    public String systemPrompt;

    public Conversation(String title, long createdAt, long updatedAt) {
        this.title = title;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
}
