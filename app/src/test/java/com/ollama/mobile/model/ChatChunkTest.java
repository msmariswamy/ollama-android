package com.ollama.mobile.model;

import com.google.gson.Gson;
import org.junit.Test;
import static org.junit.Assert.*;

public class ChatChunkTest {

    private final Gson gson = new Gson();

    @Test
    public void testDoneField() {
        String json = "{\"done\":true,\"done_reason\":\"stop\"}";
        ChatChunk chunk = gson.fromJson(json, ChatChunk.class);
        assertTrue(chunk.done);
        assertEquals("stop", chunk.doneReason);
    }

    @Test
    public void testContentField() {
        String json = "{\"message\":{\"role\":\"assistant\",\"content\":\"hello\"},\"done\":false}";
        ChatChunk chunk = gson.fromJson(json, ChatChunk.class);
        assertFalse(chunk.done);
        assertNotNull(chunk.message);
        assertEquals("hello", chunk.message.content);
        assertEquals("assistant", chunk.message.role);
    }

    @Test
    public void testNotDoneByDefault() {
        ChatChunk chunk = gson.fromJson("{}", ChatChunk.class);
        assertFalse(chunk.done);
    }
}
