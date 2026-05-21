package com.ollama.mobile.audio;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Captures microphone audio at 16 kHz mono using AudioRecord.
 * Emits fixed 5-second float[] chunks for Whisper and simultaneously
 * writes raw PCM bytes to a file for WAV export.
 */
public class AudioCaptureManager {

    private static final String TAG = "AudioCapture";

    public static final int SAMPLE_RATE    = 16_000;
    public static final int CHUNK_SECONDS  = 3;
    private static final int CHUNK_SAMPLES = SAMPLE_RATE * CHUNK_SECONDS;
    private static final int FRAME_SAMPLES = 1_600; // 100 ms read frame

    public interface ChunkCallback {
        void onChunkReady(float[] pcmFloat);
    }

    private AudioRecord audioRecord;
    private volatile boolean running = false;
    private final ExecutorService captureThread = Executors.newSingleThreadExecutor();

    /** @param rawPcmOutput file to stream raw 16-bit LE PCM into (may be null). */
    public void start(ChunkCallback callback, File rawPcmOutput) {
        if (running) return;
        running = true;

        int minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        int bufSize = Math.max(minBuf, CHUNK_SAMPLES * 2);

        audioRecord = new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, bufSize);

        if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord failed to initialise");
            running = false;
            return;
        }

        audioRecord.startRecording();
        captureThread.execute(() -> captureLoop(callback, rawPcmOutput));
    }

    public void stop() {
        running = false;
        if (audioRecord != null) {
            try { audioRecord.stop(); } catch (Exception ignored) {}
            audioRecord.release();
            audioRecord = null;
        }
    }

    private void captureLoop(ChunkCallback callback, File rawPcmOutput) {
        short[] chunkBuf   = new short[CHUNK_SAMPLES];
        int     samplesIn  = 0;

        FileOutputStream pcmOut = null;
        if (rawPcmOutput != null) {
            try { pcmOut = new FileOutputStream(rawPcmOutput, false); }
            catch (IOException e) { Log.e(TAG, "Cannot open PCM output file", e); }
        }

        try {
            while (running) {
                if (audioRecord == null) break;
                int toRead = Math.min(FRAME_SAMPLES, CHUNK_SAMPLES - samplesIn);
                int read   = audioRecord.read(chunkBuf, samplesIn, toRead);
                if (read <= 0) continue;

                if (pcmOut != null) {
                    try { pcmOut.write(shortsToBytes(chunkBuf, samplesIn, read)); }
                    catch (IOException ignored) {}
                }

                samplesIn += read;

                if (samplesIn >= CHUNK_SAMPLES) {
                    Log.d(TAG, "Chunk emitted: " + CHUNK_SECONDS + "s");
                    callback.onChunkReady(shortsToFloats(chunkBuf, samplesIn));
                    samplesIn = 0;
                }
            }

            // Emit partial final chunk if at least 1 s of audio remains
            if (samplesIn >= SAMPLE_RATE) {
                Log.d(TAG, "Final chunk emitted: " + samplesIn + " samples");
                callback.onChunkReady(shortsToFloats(chunkBuf, samplesIn));
            }
        } finally {
            if (pcmOut != null) try { pcmOut.close(); } catch (IOException ignored) {}
        }
        Log.d(TAG, "Capture loop ended");
    }

    private static byte[] shortsToBytes(short[] src, int offset, int len) {
        ByteBuffer buf = ByteBuffer.allocate(len * 2).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = offset; i < offset + len; i++) buf.putShort(src[i]);
        return buf.array();
    }

    private static float[] shortsToFloats(short[] src, int len) {
        float[] out = new float[len];
        for (int i = 0; i < len; i++) out[i] = src[i] / 32768f;
        return out;
    }
}
