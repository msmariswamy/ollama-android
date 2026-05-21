package com.ollama.mobile.network;

import com.ollama.mobile.model.ChatChunk;
import okhttp3.MediaType;
import okhttp3.ResponseBody;
import org.junit.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

public class NdjsonStreamReaderTest {

    private final NdjsonStreamReader reader = new NdjsonStreamReader();

    private ResponseBody bodyOf(String content) {
        return ResponseBody.create(MediaType.parse("application/x-ndjson"), content);
    }

    @Test
    public void testSingleLineParsed() {
        String ndjson = "{\"message\":{\"role\":\"assistant\",\"content\":\"Hi\"},\"done\":false}\n";
        List<ChatChunk> received = new ArrayList<>();

        reader.read(bodyOf(ndjson), new NdjsonStreamReader.ChunkListener() {
            @Override public void onChunk(ChatChunk chunk) { received.add(chunk); }
            @Override public void onError(String message) { fail("Unexpected error: " + message); }
            @Override public void onComplete() {}
        });

        assertEquals(1, received.size());
        assertEquals("Hi", received.get(0).message.content);
        assertFalse(received.get(0).done);
    }

    @Test
    public void testDoneTrueTriggersComplete() {
        String ndjson = "{\"done\":true}\n";
        boolean[] completeCalled = {false};

        reader.read(bodyOf(ndjson), new NdjsonStreamReader.ChunkListener() {
            @Override public void onChunk(ChatChunk chunk) {}
            @Override public void onError(String message) { fail("Unexpected error"); }
            @Override public void onComplete() { completeCalled[0] = true; }
        });

        assertTrue(completeCalled[0]);
    }

    @Test
    public void testMalformedLineSkipped() {
        String ndjson = "not-valid-json\n{\"done\":true}\n";
        List<ChatChunk> received = new ArrayList<>();
        boolean[] errorCalled = {false};

        reader.read(bodyOf(ndjson), new NdjsonStreamReader.ChunkListener() {
            @Override public void onChunk(ChatChunk chunk) { received.add(chunk); }
            @Override public void onError(String message) { errorCalled[0] = true; }
            @Override public void onComplete() {}
        });

        assertFalse("onError should not be called for malformed lines", errorCalled[0]);
    }

    @Test
    public void testMultipleChunks() {
        String ndjson =
            "{\"message\":{\"role\":\"assistant\",\"content\":\"Hello\"},\"done\":false}\n" +
            "{\"message\":{\"role\":\"assistant\",\"content\":\" world\"},\"done\":false}\n" +
            "{\"done\":true}\n";
        List<String> contents = new ArrayList<>();
        boolean[] completeCalled = {false};

        reader.read(bodyOf(ndjson), new NdjsonStreamReader.ChunkListener() {
            @Override public void onChunk(ChatChunk chunk) {
                if (chunk.message != null && chunk.message.content != null) {
                    contents.add(chunk.message.content);
                }
            }
            @Override public void onError(String message) { fail(message); }
            @Override public void onComplete() { completeCalled[0] = true; }
        });

        assertEquals(2, contents.size());
        assertEquals("Hello", contents.get(0));
        assertEquals(" world", contents.get(1));
        assertTrue(completeCalled[0]);
    }
}
