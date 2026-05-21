package com.ollama.mobile.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class ChatRequest {
    @SerializedName("model")
    public String model;

    @SerializedName("messages")
    public List<ChatMessage> messages;

    @SerializedName("stream")
    public boolean stream;

    public ChatRequest(String model, List<ChatMessage> messages, boolean stream) {
        this.model = model;
        this.messages = messages;
        this.stream = stream;
    }
}
