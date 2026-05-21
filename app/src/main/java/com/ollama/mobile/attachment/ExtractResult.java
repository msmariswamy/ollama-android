package com.ollama.mobile.attachment;

import java.util.List;

public class ExtractResult {
    public final List<String> base64Images;
    public final String extractedText;
    public final String fileName;
    public final String mimeType;

    public ExtractResult(List<String> base64Images, String extractedText, String fileName, String mimeType) {
        this.base64Images = base64Images;
        this.extractedText = extractedText;
        this.fileName = fileName;
        this.mimeType = mimeType;
    }

    public boolean hasImages() {
        return base64Images != null && !base64Images.isEmpty();
    }

    public boolean hasText() {
        return extractedText != null && !extractedText.isEmpty();
    }
}
