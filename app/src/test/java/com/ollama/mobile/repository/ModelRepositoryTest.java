package com.ollama.mobile.repository;

import com.ollama.mobile.model.OllamaModel;
import com.ollama.mobile.network.NetworkResult;
import com.ollama.mobile.network.OllamaApiService;
import org.junit.Before;
import org.junit.Test;
import retrofit2.Call;
import retrofit2.Response;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class ModelRepositoryTest {

    private OllamaApiService mockApiService;
    private ModelRepository repository;

    @Before
    public void setUp() {
        mockApiService = mock(OllamaApiService.class);
        repository = new ModelRepository(mockApiService);
    }

    @Test
    public void testFetchModelsDeliversList() throws Exception {
        OllamaModel m1 = new OllamaModel(); m1.name = "llama3";
        OllamaModel m2 = new OllamaModel(); m2.name = "mistral";
        OllamaModel.TagsResponse tags = new OllamaModel.TagsResponse();
        tags.models = Arrays.asList(m1, m2);

        @SuppressWarnings("unchecked")
        Call<OllamaModel.TagsResponse> mockCall = mock(Call.class);
        when(mockApiService.getTags()).thenReturn(mockCall);
        when(mockCall.execute()).thenReturn(Response.success(tags));

        CountDownLatch latch = new CountDownLatch(1);
        List<String>[] result = new List[1];

        repository.fetchModels(networkResult -> {
            if (networkResult.status == NetworkResult.Status.SUCCESS) {
                result[0] = networkResult.data;
            }
            latch.countDown();
        });

        assertTrue("Timed out", latch.await(3, TimeUnit.SECONDS));
        assertNotNull(result[0]);
        assertEquals(2, result[0].size());
        assertTrue(result[0].contains("llama3"));
        assertTrue(result[0].contains("mistral"));
    }

    @Test
    public void testEmptyListWhenNoModels() throws Exception {
        OllamaModel.TagsResponse tags = new OllamaModel.TagsResponse();
        tags.models = Collections.emptyList();

        @SuppressWarnings("unchecked")
        Call<OllamaModel.TagsResponse> mockCall = mock(Call.class);
        when(mockApiService.getTags()).thenReturn(mockCall);
        when(mockCall.execute()).thenReturn(Response.success(tags));

        CountDownLatch latch = new CountDownLatch(1);
        List<String>[] result = new List[1];

        repository.fetchModels(networkResult -> {
            result[0] = networkResult.data != null ? networkResult.data : Collections.emptyList();
            latch.countDown();
        });

        assertTrue("Timed out", latch.await(3, TimeUnit.SECONDS));
        assertNotNull(result[0]);
        assertEquals(0, result[0].size());
    }
}
