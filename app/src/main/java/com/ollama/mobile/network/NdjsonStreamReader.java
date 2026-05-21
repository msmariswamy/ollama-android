package com.ollama.mobile.network;

import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.ollama.mobile.model.ChatChunk;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import okhttp3.ResponseBody;

public class NdjsonStreamReader {

    public interface ChunkListener {
        void onChunk(ChatChunk chunk);
        void onError(String message);
        void onComplete();
    }

    private final Gson gson = new Gson();

    public void read(ResponseBody body, ChunkListener listener) {
        try (InputStream is = body.byteStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            String line;
            int chunkCount = 0;
            long streamStart = System.currentTimeMillis();
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) continue;
                try {
                    // Check for an error object before attempting chunk parse
                    JsonObject json = JsonParser.parseString(line).getAsJsonObject();
                    if (json.has("error")) {
                        String errMsg = json.get("error").getAsString();
                        Log.e("NdjsonStream", "stream error from server: " + errMsg);
                        listener.onError(errMsg);
                        return;
                    }
                    ChatChunk chunk = gson.fromJson(line, ChatChunk.class);
                    long elapsed = System.currentTimeMillis() - streamStart;
                    Log.d("NdjsonStream", "chunk #" + (++chunkCount) + " at +" + elapsed + "ms: " +
                            (chunk.message != null ? chunk.message.content : "") + " done=" + chunk.done);
                    listener.onChunk(chunk);
                    if (chunk.done) {
                        break;
                    }
                } catch (Exception e) {
                    Log.w("NdjsonStream", "skipped malformed line: " + line, e);
                }
            }
            Log.d("NdjsonStream", "stream complete — " + chunkCount + " chunks in " +
                    (System.currentTimeMillis() - streamStart) + "ms");
            listener.onComplete();
        } catch (IOException e) {
            listener.onError(e.getMessage() != null ? e.getMessage() : "Stream error");
        }
    }
}
