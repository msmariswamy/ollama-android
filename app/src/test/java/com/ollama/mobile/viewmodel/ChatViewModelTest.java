package com.ollama.mobile.viewmodel;

import android.app.Application;
import android.content.Context;
import android.net.Uri;
import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import com.ollama.mobile.attachment.ExtractResult;
import com.ollama.mobile.attachment.FileContentExtractor;
import com.ollama.mobile.model.ChatChunk;
import com.ollama.mobile.model.ChatMessage;
import com.ollama.mobile.repository.ChatRepository;
import com.ollama.mobile.repository.ConversationRepository;
import com.ollama.mobile.repository.SettingsRepository;
import com.ollama.mobile.ui.chat.ChatViewModel;
import com.ollama.mobile.ui.chat.UiAttachment;
import com.ollama.mobile.ui.chat.UiMessage;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class ChatViewModelTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    private ChatRepository mockChatRepository;
    private ConversationRepository mockConvRepository;
    private SettingsRepository mockSettingsRepository;
    private ChatViewModel viewModel;

    @Before
    public void setUp() {
        mockChatRepository = mock(ChatRepository.class);
        mockConvRepository = mock(ConversationRepository.class);
        mockSettingsRepository = mock(SettingsRepository.class);
        when(mockSettingsRepository.getSelectedModel()).thenReturn("llama3");

        viewModel = new ChatViewModel(
            mock(Application.class),
            mockChatRepository,
            mockConvRepository,
            mockSettingsRepository,
            Runnable::run
        );
    }

    @Test
    public void testUserMessageAppearsInLiveDataAfterSend() throws Exception {
        // Arrange: mock createConversation to call callback immediately
        doAnswer(inv -> {
            ConversationRepository.Callback<Long> cb = inv.getArgument(1);
            cb.onResult(1L);
            return null;
        }).when(mockConvRepository).createConversation(anyString(), any());

        doAnswer(inv -> null).when(mockConvRepository).insertMessage(anyLong(), anyString(), anyString(), any(), any());

        CountDownLatch latch = new CountDownLatch(1);
        doAnswer(inv -> {
            ChatRepository.StreamCallback cb = inv.getArgument(2);
            cb.onComplete();
            latch.countDown();
            return null;
        }).when(mockChatRepository).sendMessage(anyString(), any(), any());

        // Act
        viewModel.sendMessage("hello");
        assertTrue("Timed out", latch.await(3, TimeUnit.SECONDS));

        // Assert
        List<UiMessage> messages = viewModel.messages.getValue();
        assertNotNull(messages);
        assertTrue(messages.stream().anyMatch(m -> "user".equals(m.role) && "hello".equals(m.content)));
    }

    @Test
    public void testAssistantChunkAppendedToLiveData() throws Exception {
        doAnswer(inv -> {
            ConversationRepository.Callback<Long> cb = inv.getArgument(1);
            cb.onResult(1L);
            return null;
        }).when(mockConvRepository).createConversation(anyString(), any());

        doAnswer(inv -> null).when(mockConvRepository).insertMessage(anyLong(), anyString(), anyString(), any(), any());

        CountDownLatch latch = new CountDownLatch(1);
        doAnswer(inv -> {
            ChatRepository.StreamCallback cb = inv.getArgument(2);
            ChatChunk chunk = new com.google.gson.Gson().fromJson(
                "{\"message\":{\"role\":\"assistant\",\"content\":\"world\"},\"done\":false}",
                ChatChunk.class);
            cb.onChunk(chunk);
            cb.onComplete();
            latch.countDown();
            return null;
        }).when(mockChatRepository).sendMessage(anyString(), any(), any());

        viewModel.sendMessage("hello");
        assertTrue("Timed out", latch.await(3, TimeUnit.SECONDS));

        List<UiMessage> messages = viewModel.messages.getValue();
        assertNotNull(messages);
        assertTrue(messages.stream().anyMatch(m -> "assistant".equals(m.role) && "world".equals(m.content)));
    }

    @Test
    public void testNoModelPostsError() {
        when(mockSettingsRepository.getSelectedModel()).thenReturn("");
        ChatViewModel noModelVm = new ChatViewModel(
            mock(Application.class),
            mockChatRepository,
            mockConvRepository,
            mockSettingsRepository,
            Runnable::run
        );

        noModelVm.sendMessage("hello");

        assertEquals("no_model", noModelVm.errorEvent.getValue());
    }

    @Test
    public void testAddAttachmentsPopulatesPendingAttachments() throws Exception {
        // Arrange
        FileContentExtractor mockExtractor = mock(FileContentExtractor.class);
        viewModel.setFileContentExtractor(mockExtractor);

        Uri mockUri = mock(Uri.class);
        Context mockCtx = mock(Context.class);
        ExtractResult fakeResult = new ExtractResult(null, "Hello from PDF", "doc.pdf", "application/pdf");
        when(mockExtractor.extract(any(), eq(mockUri), anyString())).thenReturn(fakeResult);

        CountDownLatch latch = new CountDownLatch(1);
        viewModel.isProcessingAttachments.observeForever(processing -> {
            if (Boolean.FALSE.equals(processing) && viewModel.pendingAttachments.getValue() != null
                    && !viewModel.pendingAttachments.getValue().isEmpty()) {
                latch.countDown();
            }
        });

        // Act
        viewModel.addAttachments(mockCtx, Collections.singletonList(mockUri));

        // Assert
        assertTrue("Timed out waiting for addAttachments", latch.await(3, TimeUnit.SECONDS));
        List<UiAttachment> pending = viewModel.pendingAttachments.getValue();
        assertNotNull(pending);
        assertEquals(1, pending.size());
        assertEquals("doc.pdf", pending.get(0).fileName);
        assertEquals("Hello from PDF", pending.get(0).extractedText);
        assertFalse(pending.get(0).isImage);
    }

    @Test
    public void testAddAttachmentsFlipsProcessingFlag() throws Exception {
        // Arrange
        FileContentExtractor mockExtractor = mock(FileContentExtractor.class);
        viewModel.setFileContentExtractor(mockExtractor);

        Uri mockUri = mock(Uri.class);
        Context mockCtx = mock(Context.class);
        ExtractResult fakeResult = new ExtractResult(null, "text", "file.txt", "text/plain");
        when(mockExtractor.extract(any(), eq(mockUri), anyString())).thenReturn(fakeResult);

        // Track true→false transition (initial false doesn't count)
        CountDownLatch seenTrue = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(1);
        viewModel.isProcessingAttachments.observeForever(processing -> {
            if (Boolean.TRUE.equals(processing)) {
                seenTrue.countDown();
            } else if (seenTrue.getCount() == 0) {
                doneLatch.countDown();
            }
        });

        // Act
        viewModel.addAttachments(mockCtx, Collections.singletonList(mockUri));

        // Assert: processing should flip true then false
        assertTrue("isProcessingAttachments never went false", doneLatch.await(3, TimeUnit.SECONDS));
        assertFalse(Boolean.TRUE.equals(viewModel.isProcessingAttachments.getValue()));
    }

    @Test
    public void testRemoveAttachmentReducesList() {
        // Pre-populate pendingAttachments
        UiAttachment a1 = new UiAttachment("a.pdf", "application/pdf", false);
        UiAttachment a2 = new UiAttachment("b.png", "image/png", true);
        viewModel.pendingAttachments.setValue(new java.util.ArrayList<>(java.util.Arrays.asList(a1, a2)));

        // Act
        viewModel.removeAttachment(0);

        // Assert
        List<UiAttachment> remaining = viewModel.pendingAttachments.getValue();
        assertNotNull(remaining);
        assertEquals(1, remaining.size());
        assertEquals("b.png", remaining.get(0).fileName);
    }

    @Test
    public void testRemoveAttachmentOutOfBoundsIsNoOp() {
        UiAttachment a1 = new UiAttachment("a.pdf", "application/pdf", false);
        viewModel.pendingAttachments.setValue(new java.util.ArrayList<>(Collections.singletonList(a1)));

        viewModel.removeAttachment(5); // out of bounds

        assertEquals(1, viewModel.pendingAttachments.getValue().size());
    }
}
