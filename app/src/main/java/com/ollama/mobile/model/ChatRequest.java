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

    @SerializedName("options")
    public Options options;

    public ChatRequest(String model, List<ChatMessage> messages, boolean stream) {
        this.model = model;
        this.messages = messages;
        this.stream = stream;
    }

    public static class Options {
        @SerializedName("temperature")
        public float temperature;

        @SerializedName("top_p")
        public float top_p;

        @SerializedName("num_ctx")
        public int num_ctx;

        public Options(float temperature, float top_p, int num_ctx) {
            this.temperature = temperature;
            this.top_p = top_p;
            this.num_ctx = num_ctx;
        }
    }
}
