package com.ollama.mobile.viewmodel;

import android.app.Application;
import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import com.ollama.mobile.network.NetworkResult;
import com.ollama.mobile.repository.ModelRepository;
import com.ollama.mobile.repository.SettingsRepository;
import com.ollama.mobile.ui.model.ModelSelectorViewModel;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class ModelSelectorViewModelTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    private ModelRepository mockModelRepository;
    private SettingsRepository mockSettingsRepository;
    private ModelSelectorViewModel viewModel;

    @Before
    public void setUp() {
        mockModelRepository = mock(ModelRepository.class);
        mockSettingsRepository = mock(SettingsRepository.class);
        when(mockSettingsRepository.getSelectedModel()).thenReturn("");

        viewModel = new ModelSelectorViewModel(mock(Application.class), mockModelRepository, mockSettingsRepository);
    }

    @Test
    public void testLoadModelsPopulatesLiveData() throws Exception {
        List<String> modelNames = Arrays.asList("llama3", "mistral");
        doAnswer(inv -> {
            ModelRepository.Callback cb = inv.getArgument(0);
            cb.onResult(NetworkResult.success(modelNames));
            return null;
        }).when(mockModelRepository).fetchModels(any());

        CountDownLatch latch = new CountDownLatch(1);
        viewModel.modelsResult.observeForever(result -> {
            if (result != null && result.status == NetworkResult.Status.SUCCESS) {
                latch.countDown();
            }
        });

        viewModel.loadModels();
        assertTrue("Timed out", latch.await(3, TimeUnit.SECONDS));

        NetworkResult<List<String>> result = viewModel.modelsResult.getValue();
        assertNotNull(result);
        assertEquals(NetworkResult.Status.SUCCESS, result.status);
        assertEquals(2, result.data.size());
    }

    @Test
    public void testErrorMessageSetOnFailure() throws Exception {
        doAnswer(inv -> {
            ModelRepository.Callback cb = inv.getArgument(0);
            cb.onResult(NetworkResult.error("network failure"));
            return null;
        }).when(mockModelRepository).fetchModels(any());

        CountDownLatch latch = new CountDownLatch(1);
        viewModel.modelsResult.observeForever(result -> {
            if (result != null && result.status == NetworkResult.Status.ERROR) {
                latch.countDown();
            }
        });

        viewModel.loadModels();
        assertTrue("Timed out", latch.await(3, TimeUnit.SECONDS));

        NetworkResult<List<String>> result = viewModel.modelsResult.getValue();
        assertNotNull(result);
        assertEquals(NetworkResult.Status.ERROR, result.status);
        assertNotNull(result.error);
    }
}
