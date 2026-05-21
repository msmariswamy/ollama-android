package com.ollama.mobile.network;

import android.util.Log;

import com.google.gson.JsonArray;
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
 * Parses OpenAI-compatible SSE streams (data: {...} lines) into ChatChunk objects.
 * Compatible with OpenRouter, Grok (xAI), and any provider using /v1/chat/completions.
 */
public class OpenAIStreamReader {

    public interface ChunkListener {
        void onChunk(ChatChunk chunk);
        void onError(String message);
        void onComplete();
    }

    private static final String TAG = "OpenAIStreamReader";

    public void read(ResponseBody body, ChunkListener listener) {
        try (InputStream is = body.byteStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            String line;
            int chunkCount = 0;
            long start = System.currentTimeMillis();
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) continue;
                if (!line.startsWith("data: ")) continue;
                String data = line.substring(6).trim();
                if ("[DONE]".equals(data)) break;
                try {
                    JsonObject json = JsonParser.parseString(data).getAsJsonObject();
                    if (json.has("error")) {
                        String errMsg = json.getAsJsonObject("error").get("message").getAsString();
                        Log.e(TAG, "stream error: " + errMsg);
                        listener.onError(errMsg);
                        return;
                    }
                    JsonArray choices = json.getAsJsonArray("choices");
                    if (choices == null || choices.size() == 0) continue;
                    JsonObject choice = choices.get(0).getAsJsonObject();
                    JsonObject delta = choice.getAsJsonObject("delta");
                    if (delta == null) continue;
                    String content = delta.has("content") && !delta.get("content").isJsonNull()
                            ? delta.get("content").getAsString() : "";
                    String finishReason = choice.has("finish_reason") && !choice.get("finish_reason").isJsonNull()
                            ? choice.get("finish_reason").getAsString() : null;
                    boolean done = finishReason != null;
                    ChatMessage msg = new ChatMessage("assistant", content);
                    ChatChunk chunk = new ChatChunk();
                    chunk.message = msg;
                    chunk.done = done;
                    chunk.doneReason = finishReason;
                    Log.d(TAG, "chunk #" + (++chunkCount) + " at +" + (System.currentTimeMillis() - start)
                            + "ms: \"" + content + "\" done=" + done);
                    listener.onChunk(chunk);
                    if (done) break;
                } catch (Exception e) {
                    Log.w(TAG, "skipped malformed SSE line: " + data, e);
                }
            }
            Log.d(TAG, "stream complete — " + chunkCount + " chunks in " + (System.currentTimeMillis() - start) + "ms");
            listener.onComplete();
        } catch (IOException e) {
            listener.onError(e.getMessage() != null ? e.getMessage() : "Stream error");
        }
    }
}
