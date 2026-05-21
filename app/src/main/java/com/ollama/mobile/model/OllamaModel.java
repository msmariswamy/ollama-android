package com.ollama.mobile.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class OllamaModel {
    @SerializedName("name")
    public String name;

    @SerializedName("modified_at")
    public String modifiedAt;

    @SerializedName("size")
    public long size;

    public static class TagsResponse {
        @SerializedName("models")
        public List<OllamaModel> models;
    }
}
