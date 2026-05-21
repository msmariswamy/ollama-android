package com.ollama.mobile.model;

import org.junit.Test;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.*;

public class ChatRequestTest {

    @Test
    public void testModelAndMessages() {
        ChatMessage msg = new ChatMessage("user", "hi");
        List<ChatMessage> messages = Collections.singletonList(msg);
        ChatRequest request = new ChatRequest("llama3", messages, true);
        assertEquals("llama3", request.model);
        assertEquals(1, request.messages.size());
        assertTrue(request.stream);
    }

    @Test
    public void testMessagesListReference() {
        List<ChatMessage> messages = Collections.singletonList(new ChatMessage("user", "test"));
        ChatRequest request = new ChatRequest("mistral", messages, false);
        assertSame(messages, request.messages);
    }
}
