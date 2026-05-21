package com.ollama.mobile.model;

import com.google.gson.annotations.SerializedName;

public class ChatChunk {
    @SerializedName("model")
    public String model;

    @SerializedName("message")
    public ChatMessage message;

    @SerializedName("done")
    public boolean done;

    @SerializedName("done_reason")
    public String doneReason;
}
