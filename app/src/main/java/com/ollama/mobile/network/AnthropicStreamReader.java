package com.ollama.mobile.network;

import android.util.Log;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.ollama.mobile.model.ChatChunk;
import com.ollama.mobile.model.ChatMessage;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import okhttp3.ResponseBody;

/**
 * Parses Anthropic SSE streams into ChatChunk objects.
 * Handles event types: content_block_delta, message_delta, message_stop, error.
 */
public class AnthropicStreamReader {

    public interface ChunkListener {
        void onChunk(ChatChunk chunk);
        void onError(String message);
        void onComplete();
    }

    private static final String TAG = "AnthropicStreamReader";

    public void read(ResponseBody body, ChunkListener listener) {
        try (InputStream is = body.byteStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            String line;
            String currentEvent = null;
            int chunkCount = 0;
            long start = System.currentTimeMillis();
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("event: ")) {
                    currentEvent = line.substring(7).trim();
                    continue;
                }
                if (!line.startsWith("data: ")) continue;
                String data = line.substring(6).trim();
                try {
                    JsonObject json = JsonParser.parseString(data).getAsJsonObject();
                    String type = json.has("type") ? json.get("type").getAsString() : "";
                    if ("error".equals(type)) {
                        String errMsg = json.has("error")
                                ? json.getAsJsonObject("error").get("message").getAsString()
                                : "Unknown Anthropic error";
                        Log.e(TAG, "stream error: " + errMsg);
                        listener.onError(errMsg);
                        return;
                    }
                    if ("content_block_delta".equals(type)) {
                        JsonObject delta = json.getAsJsonObject("delta");
                        if (delta != null && "text_delta".equals(delta.get("type").getAsString())) {
                            String text = delta.get("text").getAsString();
                            ChatMessage msg = new ChatMessage("assistant", text);
                            ChatChunk chunk = new ChatChunk();
                            chunk.message = msg;
                            chunk.done = false;
                            Log.d(TAG, "chunk #" + (++chunkCount) + " at +" + (System.currentTimeMillis() - start)
                                    + "ms: \"" + text + "\"");
                            listener.onChunk(chunk);
                        }
                    } else if ("message_stop".equals(type)) {
                        ChatChunk doneChunk = new ChatChunk();
                        doneChunk.message = new ChatMessage("assistant", "");
                        doneChunk.done = true;
                        doneChunk.doneReason = "stop";
                        listener.onChunk(doneChunk);
                        break;
                    } else if ("message_delta".equals(type)) {
                        JsonObject delta = json.getAsJsonObject("delta");
                        if (delta != null && delta.has("stop_reason")) {
                            // message_stop event will follow; let it handle completion
                        }
                    }
                } catch (Exception e) {
                    Log.w(TAG, "skipped malformed Anthropic SSE: " + data, e);
                }
            }
            Log.d(TAG, "stream complete — " + chunkCount + " chunks in " + (System.currentTimeMillis() - start) + "ms");
            listener.onComplete();
        } catch (IOException e) {
            listener.onError(e.getMessage() != null ? e.getMessage() : "Stream error");
        }
    }
}
