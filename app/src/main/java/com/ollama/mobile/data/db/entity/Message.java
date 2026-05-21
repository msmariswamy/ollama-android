package com.ollama.mobile.data.db.entity;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
    tableName = "messages",
    foreignKeys = @ForeignKey(
        entity = Conversation.class,
        parentColumns = "id",
        childColumns = "conversationId",
        onDelete = ForeignKey.CASCADE
    ),
    indices = {@Index("conversationId")}
)
public class Message {

    @PrimaryKey(autoGenerate = true)
    public long id;

    public long conversationId;
    public String role;
    public String content;
    public long timestamp;
    public String attachmentsJson;

    public Message(long conversationId, String role, String content, long timestamp) {
        this.conversationId = conversationId;
        this.role = role;
        this.content = content;
        this.timestamp = timestamp;
    }
}
