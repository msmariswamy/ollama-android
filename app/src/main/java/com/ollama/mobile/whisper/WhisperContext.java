package com.ollama.mobile.whisper;

public class WhisperContext {

    static {
        System.loadLibrary("whisper-android");
    }

    private long nativePtr;

    private WhisperContext(long ptr) {
        this.nativePtr = ptr;
    }

    /** Returns null if the model file could not be loaded. */
    public static WhisperContext load(String modelPath) {
        long ptr = nativeInit(modelPath);
        return ptr != 0 ? new WhisperContext(ptr) : null;
    }

    /**
     * Transcribe PCM float32 samples recorded at 16 kHz mono (range [-1, 1]).
     * Synchronized so the same context is never used from two threads at once.
     */
    public synchronized String transcribe(float[] pcmSamples) {
        if (nativePtr == 0) return "";
        return nativeTranscribe(nativePtr, pcmSamples);
    }

    public synchronized void free() {
        if (nativePtr != 0) {
            nativeFree(nativePtr);
            nativePtr = 0;
        }
    }

    public boolean isLoaded() {
        return nativePtr != 0;
    }

    private static native long   nativeInit(String modelPath);
    private static native String nativeTranscribe(long ptr, float[] pcm);
    private static native void   nativeFree(long ptr);
}
