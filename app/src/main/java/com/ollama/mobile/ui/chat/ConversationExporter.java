package com.ollama.mobile.ui.chat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.List;

public class ConversationExporter {

    public static String toMarkdown(List<UiMessage> messages) {
        StringBuilder sb = new StringBuilder();
        for (UiMessage msg : messages) {
            if ("user".equals(msg.role)) {
                sb.append("## User\n\n").append(msg.content).append("\n\n");
            } else if ("assistant".equals(msg.role)) {
                sb.append("## Assistant\n\n").append(msg.content).append("\n\n");
            }
        }
        return sb.toString().trim();
    }

    public static String toJson(List<UiMessage> messages) {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        java.util.List<java.util.Map<String, String>> list = new java.util.ArrayList<>();
        for (UiMessage msg : messages) {
            java.util.Map<String, String> entry = new java.util.LinkedHashMap<>();
            entry.put("role", msg.role);
            entry.put("content", msg.content);
            list.add(entry);
        }
        return gson.toJson(list);
    }
}
