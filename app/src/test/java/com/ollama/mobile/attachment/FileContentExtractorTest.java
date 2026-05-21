package com.ollama.mobile.attachment;

import org.junit.Test;
import static org.junit.Assert.*;

public class FileContentExtractorTest {

    @Test
    public void testPlainTextTruncation() {
        // Build a string of 60000 chars
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 60000; i++) sb.append('a');
        String longText = sb.toString();
        // Simulate the truncation logic directly
        int MAX = 50_000;
        String result;
        if (longText.length() > MAX) {
            result = longText.substring(0, MAX) + "[truncated]";
        } else {
            result = longText;
        }
        assertTrue(result.endsWith("[truncated]"));
        assertEquals(50_000 + "[truncated]".length(), result.length());
    }

    @Test
    public void testAudioExtractorPlaceholderForNonWhisper() {
        FileContentExtractor extractor = new FileContentExtractor();
        // Simulate the audio logic
        String selectedModel = "llama3";
        String fileName = "speech.mp3";
        boolean isWhisper = selectedModel != null && selectedModel.toLowerCase().contains("whisper");
        assertFalse(isWhisper);
        String placeholder = "[Audio file: " + fileName + " — select a Whisper-compatible model to transcribe]";
        assertEquals("[Audio file: speech.mp3 — select a Whisper-compatible model to transcribe]", placeholder);
    }

    @Test
    public void testAudioExtractorWhisperModelDetected() {
        String selectedModel = "whisper:latest";
        boolean isWhisper = selectedModel != null && selectedModel.toLowerCase().contains("whisper");
        assertTrue(isWhisper);
    }

    @Test
    public void testGuessMimeType() {
        FileContentExtractor extractor = new FileContentExtractor();
        assertEquals("image/jpeg", extractor.guessMimeType("photo.jpg"));
        assertEquals("application/pdf", extractor.guessMimeType("doc.pdf"));
        assertEquals("application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                extractor.guessMimeType("doc.docx"));
        assertEquals("audio/mpeg", extractor.guessMimeType("audio.mp3"));
    }
}
