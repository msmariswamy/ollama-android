package com.ollama.mobile.model;

import com.google.gson.Gson;
import org.junit.Test;
import static org.junit.Assert.*;

public class ChatMessageTest {

    private final Gson gson = new Gson();

    @Test
    public void testFieldsSetByConstructor() {
        ChatMessage msg = new ChatMessage("user", "hello");
        assertEquals("user", msg.role);
        assertEquals("hello", msg.content);
    }

    @Test
    public void testGsonRoundTrip() {
        ChatMessage msg = new ChatMessage("user", "hello");
        String json = gson.toJson(msg);
        assertTrue(json.contains("\"role\":\"user\""));
        assertTrue(json.contains("\"content\":\"hello\""));
    }

    @Test
    public void testRoleConstants() {
        assertEquals("user", ChatMessage.ROLE_USER);
        assertEquals("assistant", ChatMessage.ROLE_ASSISTANT);
        assertEquals("system", ChatMessage.ROLE_SYSTEM);
    }

    @Test
    public void testDeserializeFromJson() {
        String json = "{\"role\":\"user\",\"content\":\"hello\"}";
        ChatMessage msg = gson.fromJson(json, ChatMessage.class);
        assertEquals("user", msg.role);
        assertEquals("hello", msg.content);
    }
}
