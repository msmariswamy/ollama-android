package com.ollama.mobile.repository;

import com.ollama.mobile.model.ChatChunk;
import com.ollama.mobile.model.ChatMessage;
import com.ollama.mobile.model.ChatRequest;
import com.ollama.mobile.network.OllamaApiService;
import com.ollama.mobile.network.OllamaClient;
import okhttp3.MediaType;
import okhttp3.ResponseBody;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import retrofit2.Call;
import retrofit2.Response;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class ChatRepositoryTest {

    private OllamaClient mockOllamaClient;
    private OllamaApiService mockApiService;
    private ChatRepository repository;

    @Before
    public void setUp() {
        mockOllamaClient = mock(OllamaClient.class);
        mockApiService = mock(OllamaApiService.class);
        when(mockOllamaClient.getApiService()).thenReturn(mockApiService);
        repository = new ChatRepository(mockOllamaClient);
    }

    @Test
    public void testSendMessagePassesCorrectModel() throws Exception {
        // Arrange
        @SuppressWarnings("unchecked")
        Call<ResponseBody> mockCall = mock(Call.class);
        when(mockApiService.chat(any(ChatRequest.class))).thenReturn(mockCall);
        Response<ResponseBody> errorResponse = Response.error(500,
                ResponseBody.create(MediaType.parse("text/plain"), "error"));
        when(mockCall.execute()).thenReturn(errorResponse);

        CountDownLatch latch = new CountDownLatch(1);
        ArgumentCaptor<ChatRequest> captor = ArgumentCaptor.forClass(ChatRequest.class);

        List<ChatMessage> messages = Collections.singletonList(new ChatMessage("user", "hi"));

        // Act
        repository.sendMessage("llama3", messages, new ChatRepository.StreamCallback() {
            @Override public void onChunk(ChatChunk chunk) {}
            @Override public void onError(String message) { latch.countDown(); }
            @Override public void onComplete() { latch.countDown(); }
            @Override public void onCancelled() { latch.countDown(); }
        });

        assertTrue("Timed out waiting for callback", latch.await(3, TimeUnit.SECONDS));
        verify(mockApiService).chat(captor.capture());
        assertEquals("llama3", captor.getValue().model);
        assertEquals(1, captor.getValue().messages.size());
    }

    @Test
    public void testChunkCallbackForwarded() throws Exception {
        // Arrange
        String ndjson = "{\"message\":{\"role\":\"assistant\",\"content\":\"token\"},\"done\":true}\n";
        ResponseBody body = ResponseBody.create(MediaType.parse("application/x-ndjson"), ndjson);

        @SuppressWarnings("unchecked")
        Call<ResponseBody> mockCall = mock(Call.class);
        when(mockApiService.chat(any())).thenReturn(mockCall);
        when(mockCall.execute()).thenReturn(Response.success(body));

        CountDownLatch latch = new CountDownLatch(1);
        String[] receivedContent = {null};

        // Act
        repository.sendMessage("llama3", Collections.emptyList(), new ChatRepository.StreamCallback() {
            @Override public void onChunk(ChatChunk chunk) {
                if (chunk.message != null) receivedContent[0] = chunk.message.content;
            }
            @Override public void onError(String message) { latch.countDown(); }
            @Override public void onComplete() { latch.countDown(); }
            @Override public void onCancelled() { latch.countDown(); }
        });

        assertTrue("Timed out", latch.await(3, TimeUnit.SECONDS));
        assertEquals("token", receivedContent[0]);
    }
}
