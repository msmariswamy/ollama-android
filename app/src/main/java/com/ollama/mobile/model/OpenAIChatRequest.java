package com.ollama.mobile.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class OpenAIChatRequest {

    public String model;
    public List<Message> messages;
    public boolean stream;
    @SerializedName("max_tokens")
    public Integer maxTokens;
    public Float temperature;
    @SerializedName("top_p")
    public Float topP;

    public OpenAIChatRequest(String model, List<Message> messages, boolean stream) {
        this.model = model;
        this.messages = messages;
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
