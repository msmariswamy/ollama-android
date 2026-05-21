package com.ollama.mobile.ui.chat;

import java.util.List;

public class UiMessage {

    public enum Rating { NONE, UP, DOWN }

    public String role;
    public String content;
    public String thinking;
    public boolean isStreaming;
    public boolean isError;
    public List<UiAttachment> attachments;
    public long dbMessageId = -1;
    public Rating rating = Rating.NONE;

    public UiMessage(String role, String content, String thinking, boolean isStreaming) {
        this.role = role;
        this.content = content;
        this.thinking = thinking;
        this.isStreaming = isStreaming;
        this.isError = false;
    }
}
