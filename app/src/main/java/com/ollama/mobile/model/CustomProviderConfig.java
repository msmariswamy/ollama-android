package com.ollama.mobile.model;

public class CustomProviderConfig {
    public String id;
    public String name;
    public String baseUrl;

    public CustomProviderConfig(String id, String name, String baseUrl) {
        this.id = id;
        this.name = name;
        this.baseUrl = baseUrl;
    }
}
