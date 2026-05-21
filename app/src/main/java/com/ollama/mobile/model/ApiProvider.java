package com.ollama.mobile.model;

public enum ApiProvider {
    OLLAMA_LOCAL("Ollama Local", false),
    OLLAMA_CLOUD("Ollama Cloud", true),
    OPENROUTER("OpenRouter", true),
    GROK("Grok (xAI)", true),
    CLAUDE("Claude (Anthropic)", true),
    CUSTOM_OPENAI("Custom (OpenAI-compatible)", true);

    public final String displayName;
    public final boolean requiresApiKey;

    ApiProvider(String displayName, boolean requiresApiKey) {
        this.displayName = displayName;
        this.requiresApiKey = requiresApiKey;
    }
}
