package com.ollama.mobile.viewmodel;

import android.app.Application;
import com.ollama.mobile.repository.SettingsRepository;
import com.ollama.mobile.ui.settings.SettingsViewModel;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class SettingsViewModelTest {

    private SettingsRepository mockSettingsRepository;
    private SettingsViewModel viewModel;

    @Before
    public void setUp() {
        mockSettingsRepository = mock(SettingsRepository.class);
        viewModel = new SettingsViewModel(mock(Application.class), mockSettingsRepository);
    }

    @Test
    public void testGetSettingsRepositoryReturnsSameInstance() {
        assertSame(mockSettingsRepository, viewModel.getSettingsRepository());
    }

    @Test
    public void testInitialStatusMessage() {
        assertNotNull(viewModel.statusMessage);
    }

    @Test
    public void testInitialIsConnectedIsNull() {
        assertNull(viewModel.isConnected.getValue());
    }
}
