package com.ollama.mobile.ui.chat;

import android.graphics.Bitmap;

import java.util.List;

public class UiAttachment {
    public final String fileName;
    public final String mimeType;
    public final boolean isImage;
    public Bitmap thumbnail;
    public String extractedText;
    public List<String> base64Images;

    public UiAttachment(String fileName, String mimeType, boolean isImage) {
        this.fileName = fileName;
        this.mimeType = mimeType;
        this.isImage = isImage;
    }

    public UiAttachment(String fileName, String mimeType, boolean isImage, Bitmap thumbnail) {
        this.fileName = fileName;
        this.mimeType = mimeType;
        this.isImage = isImage;
        this.thumbnail = thumbnail;
    }
}
