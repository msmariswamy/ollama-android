package com.ollama.mobile.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class AnthropicRequest {

    public String model;
    public List<Message> messages;
    /** System message content — extracted from the messages list and placed here per Anthropic spec. */
    public String system;
    @SerializedName("max_tokens")
    public int maxTokens;
    public boolean stream;
    public Float temperature;
    @SerializedName("top_p")
    public Float topP;

    public AnthropicRequest(String model, List<Message> messages, String system, int maxTokens, boolean stream) {
        this.model = model;
        this.messages = messages;
        this.system = system;
        this.maxTokens = maxTokens;
        this.stream = stream;
    }

    public static class Message {
        public String role;
        public String content;

        public Message(String role, String content) {
            this.role = role;
            this.content = content;
        }
    }
}
