package com.ollama.mobile.ui.chat;

import android.app.Application;
import android.content.Context;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;

import com.ollama.mobile.OllamaApp;
import com.ollama.mobile.attachment.ExtractResult;
import com.ollama.mobile.attachment.FileContentExtractor;
import com.ollama.mobile.model.ChatMessage;
import com.ollama.mobile.repository.ChatRepository;
import com.ollama.mobile.repository.ConversationRepository;
import com.ollama.mobile.repository.SettingsRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ChatViewModel extends AndroidViewModel {

    private static final String TAG = "ChatViewModel";
    public static final int CONTEXT_WINDOW = 200;

    public final MutableLiveData<List<UiMessage>> messages = new MutableLiveData<>(new ArrayList<>());
    public final MutableLiveData<Boolean> isStreaming = new MutableLiveData<>(false);
    public final MutableLiveData<String> errorEvent = new MutableLiveData<>();
    public final MutableLiveData<String> selectedModel = new MutableLiveData<>();
    public final MutableLiveData<List<UiAttachment>> pendingAttachments = new MutableLiveData<>(new ArrayList<>());
    public final MutableLiveData<Boolean> isProcessingAttachments = new MutableLiveData<>(false);
    public final MutableLiveData<String> conversationTitle = new MutableLiveData<>("");
    public final MutableLiveData<String> systemPrompt = new MutableLiveData<>(null);

    ChatRepository chatRepository;
    ConversationRepository conversationRepository;
    SettingsRepository settingsRepository;

    private long conversationId = -1;
    private final java.util.function.Consumer<Runnable> mainPoster;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private FileContentExtractor fileContentExtractor = new FileContentExtractor();

    public ChatViewModel(@NonNull Application application) {
        super(application);
        this.mainPoster = r -> new Handler(Looper.getMainLooper()).post(r);
        OllamaApp app = (OllamaApp) application;
        chatRepository = app.getAppContainer().chatRepository;
        conversationRepository = app.getAppContainer().conversationRepository;
        settingsRepository = app.getAppContainer().settingsRepository;
        selectedModel.setValue(settingsRepository.getSelectedModel());
    }

    public ChatViewModel(Application app,
                  ChatRepository chatRepo,
                  ConversationRepository convRepo,
                  SettingsRepository settingsRepo,
                  java.util.function.Consumer<Runnable> mainPoster) {
        super(app);
        this.chatRepository = chatRepo;
        this.conversationRepository = convRepo;
        this.settingsRepository = settingsRepo;
        this.mainPoster = mainPoster;
        selectedModel.setValue(settingsRepo.getSelectedModel());
    }

    public void setFileContentExtractor(FileContentExtractor extractor) {
        this.fileContentExtractor = extractor;
    }

    public void loadConversation(long convId) {
        this.conversationId = convId;
        conversationRepository.getConversationById(convId, conv -> {
            if (conv != null) {
                conversationTitle.postValue(conv.title);
                systemPrompt.postValue(conv.systemPrompt);
            }
        });
        conversationRepository.loadMessagesWithMeta(convId, dbMessages -> {
            List<UiMessage> ui = new ArrayList<>();
            for (com.ollama.mobile.data.db.entity.Message m : dbMessages) {
                UiMessage uiMsg = new UiMessage(m.role, m.content, null, false);
                uiMsg.dbMessageId = m.id;
                if (m.attachmentsJson != null && !m.attachmentsJson.isEmpty()) {
                    try {
                        com.google.gson.Gson gson = new com.google.gson.Gson();
                        com.google.gson.reflect.TypeToken<List<AttachmentMeta>> token =
                            new com.google.gson.reflect.TypeToken<List<AttachmentMeta>>(){};
                        List<AttachmentMeta> metas = gson.fromJson(m.attachmentsJson, token.getType());
                        List<UiAttachment> attachments = new ArrayList<>();
                        for (AttachmentMeta meta : metas) {
                            UiAttachment att = new UiAttachment(meta.fileName, meta.mimeType, meta.isImage);
                            if (meta.isImage && meta.thumbnailBase64 != null && !meta.thumbnailBase64.isEmpty()) {
                                try {
                                    byte[] bytes = android.util.Base64.decode(meta.thumbnailBase64, android.util.Base64.NO_WRAP);
                                    att.thumbnail = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                                } catch (Exception ignored) {}
                            }
                            attachments.add(att);
                        }
                        uiMsg.attachments = attachments;
                    } catch (Exception ignored) {}
                }
                ui.add(uiMsg);
            }
            messages.postValue(ui);
        });
    }

    public void addAttachments(Context ctx, List<Uri> uris) {
        isProcessingAttachments.postValue(true);
        executor.execute(() -> {
            String model = settingsRepository.getSelectedModel();
            List<UiAttachment> current = new ArrayList<>(
                pendingAttachments.getValue() != null ? pendingAttachments.getValue() : new ArrayList<>());
            for (Uri uri : uris) {
                ExtractResult result = fileContentExtractor.extract(ctx, uri, model);
                UiAttachment uiAttachment;
                if (result.hasImages() && !result.base64Images.isEmpty()) {
                    try {
                        byte[] bytes = android.util.Base64.decode(result.base64Images.get(0), android.util.Base64.NO_WRAP);
                        android.graphics.Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                        uiAttachment = new UiAttachment(result.fileName, result.mimeType, true, bmp);
                    } catch (Exception e) {
                        uiAttachment = new UiAttachment(result.fileName, result.mimeType, true);
                    }
                } else {
                    uiAttachment = new UiAttachment(result.fileName, result.mimeType, false);
                }
                uiAttachment.extractedText = result.extractedText;
                uiAttachment.base64Images = result.base64Images;
                current.add(uiAttachment);
            }
            pendingAttachments.postValue(current);
            isProcessingAttachments.postValue(false);
        });
    }

    public void removeAttachment(int index) {
        List<UiAttachment> current = new ArrayList<>(
            pendingAttachments.getValue() != null ? pendingAttachments.getValue() : new ArrayList<>());
        if (index >= 0 && index < current.size()) {
            current.remove(index);
            pendingAttachments.setValue(current);
        }
    }

    public void sendMessage(String text) {
        String model = settingsRepository.getSelectedModel();
        if (model == null || model.isEmpty()) {
            errorEvent.postValue("no_model");
            return;
        }

        // Consume pending attachments
        List<UiAttachment> attachments = new ArrayList<>(
            pendingAttachments.getValue() != null ? pendingAttachments.getValue() : new ArrayList<>());
        pendingAttachments.setValue(new ArrayList<>());

        List<UiMessage> current = new ArrayList<>(messages.getValue() != null ? messages.getValue() : new ArrayList<>());
        UiMessage userMsg = new UiMessage(ChatMessage.ROLE_USER, text, null, false);
        userMsg.attachments = attachments;
        current.add(userMsg);

        UiMessage assistantMsg = new UiMessage(ChatMessage.ROLE_ASSISTANT, "", null, true);
        current.add(assistantMsg);
        messages.postValue(current);

        isStreaming.postValue(true);

        // Persist user message and create conversation if needed
        if (conversationId == -1) {
            String title = text.length() > 40 ? text.substring(0, 40) : text;
            conversationTitle.postValue(title);
            conversationRepository.createConversation(title, id -> {
                conversationId = id;
                persistAndSend(model, text, current, assistantMsg, attachments);
            });
        } else {
            persistAndSend(model, text, current, assistantMsg, attachments);
        }
    }

    private void persistAndSend(String model, String userText, List<UiMessage> current,
                                 UiMessage assistantMsg, List<UiAttachment> attachments) {
        // Build content with attachment text prepended
        StringBuilder contentBuilder = new StringBuilder();
        List<String> allImages = new ArrayList<>();
        if (attachments != null) {
            for (UiAttachment att : attachments) {
                if (att.base64Images != null && !att.base64Images.isEmpty()) {
                    allImages.addAll(att.base64Images);
                }
                if (att.extractedText != null && !att.extractedText.isEmpty()) {
                    contentBuilder.append("[Attachment: ").append(att.fileName).append("]\n")
                        .append(att.extractedText).append("\n---\n");
                }
            }
        }
        contentBuilder.append(userText);
        String fullContent = contentBuilder.toString().trim();
        // Ollama API requires non-empty content even for image-only messages
        if (fullContent.isEmpty() && !allImages.isEmpty()) {
            fullContent = ".";
        }

        // Serialize attachment metadata; image chips store a small 100×100 thumbnail for reload
        String attachmentsJson = null;
        if (attachments != null && !attachments.isEmpty()) {
            try {
                List<AttachmentMeta> metas = new ArrayList<>();
                for (UiAttachment att : attachments) {
                    String thumbB64 = null;
                    if (att.isImage) {
                        android.graphics.Bitmap src = att.thumbnail;
                        if (src == null && att.base64Images != null && !att.base64Images.isEmpty()) {
                            // Decode from the full-size base64 if thumbnail not cached
                            byte[] raw = android.util.Base64.decode(att.base64Images.get(0), android.util.Base64.NO_WRAP);
                            src = android.graphics.BitmapFactory.decodeByteArray(raw, 0, raw.length);
                        }
                        if (src != null) {
                            // Scale down to 100×100 max and encode as compact JPEG
                            android.graphics.Bitmap thumb = scaleBitmapToMax(src, 100);
                            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                            thumb.compress(android.graphics.Bitmap.CompressFormat.JPEG, 60, baos);
                            thumbB64 = android.util.Base64.encodeToString(baos.toByteArray(), android.util.Base64.NO_WRAP);
                        }
                    }
                    metas.add(new AttachmentMeta(att.fileName, att.mimeType, att.isImage, thumbB64));
                }
                attachmentsJson = new com.google.gson.Gson().toJson(metas);
            } catch (Exception ignored) {}
        }

        final String finalAttachmentsJson = attachmentsJson;
        conversationRepository.insertMessage(conversationId, ChatMessage.ROLE_USER, userText,
                finalAttachmentsJson, null);

        List<ChatMessage> apiMessages = buildContextWindow(current);
        // Patch the last user message to include full content (with attachment text) and images
        if (!apiMessages.isEmpty()) {
            ChatMessage lastMsg = apiMessages.get(apiMessages.size() - 1);
            lastMsg.content = fullContent;
            if (!allImages.isEmpty()) lastMsg.images = allImages;
        }
        android.util.Log.d("ChatViewModel", "sendMessage: messages=" + apiMessages.size()
            + " images=" + allImages.size() + " content=" + fullContent.substring(0, Math.min(80, fullContent.length())));

        final int assistantIndex = current.size() - 1;

        chatRepository.sendMessage(model, apiMessages, new ChatRepository.StreamCallback() {
            private final StringBuilder fullResponse = new StringBuilder();
            private final StringBuilder fullThinking = new StringBuilder();

            @Override
            public void onChunk(com.ollama.mobile.model.ChatChunk chunk) {
                if (chunk.message == null) return;
                boolean changed = false;
                if (chunk.message.thinking != null && !chunk.message.thinking.isEmpty()) {
                    fullThinking.append(chunk.message.thinking);
                    changed = true;
                }
                if (chunk.message.content != null && !chunk.message.content.isEmpty()) {
                    fullResponse.append(chunk.message.content);
                    changed = true;
                }
                if (!changed) return;
                final String responseText = fullResponse.toString();
                final String thinkingText = fullThinking.length() > 0 ? fullThinking.toString() : null;
                mainPoster.accept(() -> {
                    List<UiMessage> snapshot = messages.getValue();
                    if (snapshot == null) return;
                    List<UiMessage> updated = new ArrayList<>(snapshot);
                    updated.set(assistantIndex, new UiMessage(ChatMessage.ROLE_ASSISTANT, responseText, thinkingText, true));
                    messages.setValue(updated);
                });
            }

            @Override
            public void onError(String errorMsg) {
                Log.e(TAG, "onError: " + errorMsg);
                final String text = fullResponse.toString();
                final String thinkingText = fullThinking.length() > 0 ? fullThinking.toString() : null;
                mainPoster.accept(() -> {
                    List<UiMessage> snapshot = messages.getValue();
                    if (snapshot == null) return;
                    List<UiMessage> updated = new ArrayList<>(snapshot);
                    UiMessage err = new UiMessage(ChatMessage.ROLE_ASSISTANT, text, thinkingText, false);
                    err.isError = true;
                    updated.set(assistantIndex, err);
                    messages.setValue(updated);
                    isStreaming.setValue(false);
                    errorEvent.setValue(errorMsg);
                });
            }

            @Override
            public void onComplete() {
                final String text = fullResponse.toString();
                final String thinkingText = fullThinking.length() > 0 ? fullThinking.toString() : null;
                mainPoster.accept(() -> {
                    List<UiMessage> snapshot = messages.getValue();
                    if (snapshot == null) return;
                    List<UiMessage> updated = new ArrayList<>(snapshot);
                    updated.set(assistantIndex, new UiMessage(ChatMessage.ROLE_ASSISTANT, text, thinkingText, false));
                    messages.setValue(updated);
                    isStreaming.setValue(false);
                    conversationRepository.insertMessage(
                            conversationId, ChatMessage.ROLE_ASSISTANT, text, null, null);
                });
            }

            @Override
            public void onCancelled() {
                final String text = fullResponse.toString();
                final String thinkingText = fullThinking.length() > 0 ? fullThinking.toString() : null;
                mainPoster.accept(() -> {
                    List<UiMessage> snapshot = messages.getValue();
                    if (snapshot == null) return;
                    List<UiMessage> updated = new ArrayList<>(snapshot);
                    updated.set(assistantIndex, new UiMessage(ChatMessage.ROLE_ASSISTANT, text, thinkingText, false));
                    messages.setValue(updated);
                    isStreaming.setValue(false);
                });
            }
        });
    }

    private List<ChatMessage> buildContextWindow(List<UiMessage> all) {
        // Exclude last (still-streaming) assistant message
        List<UiMessage> completed = all.subList(0, Math.max(0, all.size() - 1));
        int start = Math.max(0, completed.size() - CONTEXT_WINDOW);
        List<ChatMessage> result = new ArrayList<>();

        // Prepend system prompt if set
        String sp = systemPrompt.getValue();
        if (sp != null && !sp.trim().isEmpty()) {
            result.add(new ChatMessage("system", sp.trim()));
        }

        for (UiMessage m : completed.subList(start, completed.size())) {
            result.add(new ChatMessage(m.role, m.content));
        }
        return result;
    }

    public void updateConversationTitle(String title) {
        if (conversationId == -1 || title == null || title.trim().isEmpty()) return;
        String trimmed = title.trim();
        conversationTitle.postValue(trimmed);
        conversationRepository.updateTitle(conversationId, trimmed);
    }

    public void stopStream() {
        chatRepository.cancelStream();
    }

    public void editMessage(int position, String newText) {
        List<UiMessage> current = messages.getValue();
        if (current == null || position < 0 || position >= current.size()) return;

        UiMessage target = current.get(position);
        long dbId = target.dbMessageId;

        Runnable doEdit = () -> {
            List<UiMessage> truncated = new ArrayList<>(
                    messages.getValue() != null ? messages.getValue() : new ArrayList<>());
            // Keep only messages before the edited position
            while (truncated.size() > position) truncated.remove(truncated.size() - 1);
            messages.postValue(truncated);
            sendMessage(newText);
        };

        if (dbId != -1 && conversationId != -1) {
            conversationRepository.deleteMessagesFrom(conversationId, dbId, () ->
                    mainPoster.accept(doEdit));
        } else {
            // No stable DB anchor — reset conversation entirely
            conversationId = -1;
            mainPoster.accept(() -> {
                messages.setValue(new ArrayList<>());
                sendMessage(newText);
            });
        }
    }

    public void regenerateMessage(int position) {
        List<UiMessage> current = messages.getValue();
        if (current == null || position <= 0 || position >= current.size()) return;

        UiMessage assistantMsg = current.get(position);
        long dbId = assistantMsg.dbMessageId;

        // Find the preceding user message
        UiMessage preceding = null;
        for (int i = position - 1; i >= 0; i--) {
            if (ChatMessage.ROLE_USER.equals(current.get(i).role)) {
                preceding = current.get(i);
                break;
            }
        }
        if (preceding == null) return;

        final String userText = preceding.content;
        final List<UiAttachment> userAttachments = preceding.attachments != null
                ? preceding.attachments : new ArrayList<>();

        Runnable doRegenerate = () -> {
            List<UiMessage> truncated = new ArrayList<>(
                    messages.getValue() != null ? messages.getValue() : new ArrayList<>());
            // Remove the assistant message and anything after it
            while (truncated.size() > position) truncated.remove(truncated.size() - 1);
            messages.postValue(truncated);
            // Re-add the pending attachments so sendMessage can consume them
            pendingAttachments.setValue(new ArrayList<>(userAttachments));
            sendMessage(userText);
        };

        if (dbId != -1 && conversationId != -1) {
            conversationRepository.deleteMessagesFrom(conversationId, dbId, () ->
                    mainPoster.accept(doRegenerate));
        } else {
            mainPoster.accept(doRegenerate);
        }
    }

    public void startNewConversation() {
        conversationId = -1;
        messages.setValue(new ArrayList<>());
        conversationTitle.setValue("");
    }

    public void refreshSelectedModel() {
        selectedModel.postValue(settingsRepository.getSelectedModel());
    }

    public long getConversationId() {
        return conversationId;
    }

    private static android.graphics.Bitmap scaleBitmapToMax(android.graphics.Bitmap src, int maxDim) {
        int w = src.getWidth(), h = src.getHeight();
        if (w <= maxDim && h <= maxDim) return src;
        float scale = Math.min((float) maxDim / w, (float) maxDim / h);
        return android.graphics.Bitmap.createScaledBitmap(src, (int)(w * scale), (int)(h * scale), true);
    }

    /** Metadata POJO persisted to DB. thumbnailBase64 is a small 100×100 JPEG for image chips. */
    private static class AttachmentMeta {
        String fileName;
        String mimeType;
        boolean isImage;
        String thumbnailBase64;

        AttachmentMeta(String fileName, String mimeType, boolean isImage, String thumbnailBase64) {
            this.fileName = fileName;
            this.mimeType = mimeType;
            this.isImage = isImage;
            this.thumbnailBase64 = thumbnailBase64;
        }
    }
}
