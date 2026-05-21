package com.ollama.mobile.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class ChatMessage {
    @SerializedName("role")
    public String role;

    @SerializedName("content")
    public String content;

    @SerializedName("thinking")
    public String thinking;

    @SerializedName("images")
    public List<String> images;

    public ChatMessage(String role, String content) {
        this.role = role;
        this.content = content;
    }

    public static final String ROLE_USER = "user";
    public static final String ROLE_ASSISTANT = "assistant";
    public static final String ROLE_SYSTEM = "system";
}
