package com.ollama.mobile.attachment;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.util.Base64;
import android.util.Log;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class FileContentExtractor {

    private static final String TAG = "FileContentExtractor";
    private static final int MAX_IMAGE_DIM = 1024;
    private static final int MAX_PDF_PAGES = 10;
    private static final int MAX_TEXT_CHARS = 50_000;

    public ExtractResult extract(Context ctx, Uri uri, String selectedModel) {
        String fileName = getFileName(ctx, uri);
        String mimeType = ctx.getContentResolver().getType(uri);
        if (mimeType == null) mimeType = guessMimeType(fileName);

        if (mimeType.startsWith("image/")) {
            return extractImage(ctx, uri, fileName, mimeType);
        } else if ("application/pdf".equals(mimeType)) {
            return extractPdf(ctx, uri, fileName, mimeType);
        } else if (mimeType.contains("wordprocessingml") || fileName.endsWith(".docx")) {
            return extractDocx(ctx, uri, fileName, mimeType);
        } else if (mimeType.startsWith("audio/") || mimeType.startsWith("video/")) {
            return extractAudio(ctx, uri, fileName, mimeType, selectedModel);
        } else {
            return extractPlainText(ctx, uri, fileName, mimeType);
        }
    }

    private ExtractResult extractImage(Context ctx, Uri uri, String fileName, String mimeType) {
        try (InputStream is = ctx.getContentResolver().openInputStream(uri)) {
            if (is == null) return errorResult(fileName, mimeType, "[Could not open image]");
            Bitmap bitmap = BitmapFactory.decodeStream(is);
            if (bitmap == null) return errorResult(fileName, mimeType, "[Could not decode image]");
            bitmap = scaleBitmap(bitmap, MAX_IMAGE_DIM);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, baos);
            String b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);
            return new ExtractResult(Collections.singletonList(b64), null, fileName, mimeType);
        } catch (Exception e) {
            Log.w(TAG, "Image extraction failed", e);
            return errorResult(fileName, mimeType, "[Could not extract image]");
        }
    }

    private ExtractResult extractPdf(Context ctx, Uri uri, String fileName, String mimeType) {
        StringBuilder sb = new StringBuilder();
        try {
            ParcelFileDescriptor pfd = ctx.getContentResolver().openFileDescriptor(uri, "r");
            if (pfd == null) return errorResult(fileName, mimeType, "[Could not open PDF]");
            PdfRenderer renderer = new PdfRenderer(pfd);
            int pageCount = Math.min(renderer.getPageCount(), MAX_PDF_PAGES);
            boolean truncated = renderer.getPageCount() > MAX_PDF_PAGES;

            TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

            for (int i = 0; i < pageCount; i++) {
                PdfRenderer.Page page = renderer.openPage(i);
                int width = (int) (page.getWidth() * 150f / 72f);
                int height = (int) (page.getHeight() * 150f / 72f);
                Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                page.close();

                sb.append("\n--- Page ").append(i + 1).append(" ---\n");
                // Synchronous ML Kit call via CountDownLatch
                CountDownLatch latch = new CountDownLatch(1);
                AtomicReference<String> pageText = new AtomicReference<>("");
                InputImage image = InputImage.fromBitmap(bitmap, 0);
                recognizer.process(image)
                    .addOnSuccessListener(result -> {
                        pageText.set(result.getText());
                        latch.countDown();
                    })
                    .addOnFailureListener(e -> latch.countDown());
                latch.await();
                sb.append(pageText.get());
            }

            renderer.close();
            pfd.close();

            if (truncated) sb.append("\n[truncated at 10 pages]");
            return new ExtractResult(null, sb.toString().trim(), fileName, mimeType);
        } catch (Exception e) {
            Log.w(TAG, "PDF extraction failed", e);
            return errorResult(fileName, mimeType, "[Could not extract text from PDF]");
        }
    }

    private ExtractResult extractDocx(Context ctx, Uri uri, String fileName, String mimeType) {
        StringBuilder sb = new StringBuilder();
        try (InputStream is = ctx.getContentResolver().openInputStream(uri);
             ZipInputStream zip = new ZipInputStream(is)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if ("word/document.xml".equals(entry.getName())) {
                    XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
                    XmlPullParser parser = factory.newPullParser();
                    parser.setInput(zip, "UTF-8");
                    boolean inParagraph = false;
                    int eventType = parser.getEventType();
                    while (eventType != XmlPullParser.END_DOCUMENT) {
                        String name = parser.getName();
                        if (eventType == XmlPullParser.START_TAG) {
                            if ("w:p".equals(name)) inParagraph = true;
                            else if ("w:t".equals(name)) {
                                String text = parser.nextText();
                                sb.append(text);
                            }
                        } else if (eventType == XmlPullParser.END_TAG) {
                            if ("w:p".equals(name) && inParagraph) {
                                sb.append('\n');
                                inParagraph = false;
                            }
                        }
                        eventType = parser.next();
                    }
                    break;
                }
                zip.closeEntry();
            }
        } catch (Exception e) {
            Log.w(TAG, "DOCX extraction failed", e);
            return errorResult(fileName, mimeType, "[Could not extract text from DOCX]");
        }
        String result = sb.toString().trim();
        return new ExtractResult(null, result.isEmpty() ? "[No text found in DOCX]" : result, fileName, mimeType);
    }

    private ExtractResult extractPlainText(Context ctx, Uri uri, String fileName, String mimeType) {
        try (InputStream is = ctx.getContentResolver().openInputStream(uri);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[4096];
            int read;
            while ((read = reader.read(buf)) != -1) {
                sb.append(buf, 0, read);
                if (sb.length() > MAX_TEXT_CHARS) {
                    sb.setLength(MAX_TEXT_CHARS);
                    sb.append("[truncated]");
                    break;
                }
            }
            return new ExtractResult(null, sb.toString(), fileName, mimeType);
        } catch (Exception e) {
            Log.w(TAG, "Text extraction failed", e);
            return errorResult(fileName, mimeType, "[Could not read file]");
        }
    }

    private ExtractResult extractAudio(Context ctx, Uri uri, String fileName, String mimeType, String selectedModel) {
        if (selectedModel != null && selectedModel.toLowerCase().contains("whisper")) {
            // Base64 encode the audio and return it as "image" payload for Whisper model
            try (InputStream is = ctx.getContentResolver().openInputStream(uri)) {
                if (is == null) return errorResult(fileName, mimeType, "[Could not open audio file]");
                byte[] bytes = toByteArray(is);
                String b64 = Base64.encodeToString(bytes, Base64.NO_WRAP);
                // For Whisper, we pass audio as base64 in a special marker that ChatViewModel handles
                return new ExtractResult(null, "[WHISPER_AUDIO_B64:" + b64 + "]", fileName, mimeType);
            } catch (Exception e) {
                Log.w(TAG, "Audio read failed", e);
            }
        }
        return new ExtractResult(null,
            "[Audio file: " + fileName + " — select a Whisper-compatible model to transcribe]",
            fileName, mimeType);
    }

    private Bitmap scaleBitmap(Bitmap src, int maxDim) {
        int w = src.getWidth(), h = src.getHeight();
        if (w <= maxDim && h <= maxDim) return src;
        float scale = Math.min((float) maxDim / w, (float) maxDim / h);
        Matrix matrix = new Matrix();
        matrix.postScale(scale, scale);
        return Bitmap.createBitmap(src, 0, 0, w, h, matrix, true);
    }

    private ExtractResult errorResult(String fileName, String mimeType, String msg) {
        return new ExtractResult(null, msg, fileName, mimeType);
    }

    String getFileName(Context ctx, Uri uri) {
        String name = "attachment";
        Cursor cursor = ctx.getContentResolver().query(uri, null, null, null, null);
        if (cursor != null && cursor.moveToFirst()) {
            int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
            if (idx >= 0) name = cursor.getString(idx);
            cursor.close();
        }
        return name;
    }

    String guessMimeType(String fileName) {
        if (fileName == null) return "application/octet-stream";
        if (fileName.endsWith(".pdf")) return "application/pdf";
        if (fileName.endsWith(".docx")) return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        if (fileName.endsWith(".md") || fileName.endsWith(".txt")) return "text/plain";
        if (fileName.endsWith(".mp3")) return "audio/mpeg";
        if (fileName.endsWith(".wav")) return "audio/wav";
        if (fileName.endsWith(".m4a")) return "audio/mp4";
        if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) return "image/jpeg";
        if (fileName.endsWith(".png")) return "image/png";
        return "application/octet-stream";
    }

    private byte[] toByteArray(InputStream is) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int read;
        while ((read = is.read(buf)) != -1) baos.write(buf, 0, read);
        return baos.toByteArray();
    }
}
