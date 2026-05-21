package com.ollama.mobile.repository;

import android.content.SharedPreferences;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class SettingsRepositoryTest {

    private SharedPreferences mockPrefs;
    private SharedPreferences.Editor mockEditor;
    private SettingsRepository repository;

    @Before
    public void setUp() {
        mockPrefs = mock(SharedPreferences.class);
        mockEditor = mock(SharedPreferences.Editor.class);
        when(mockPrefs.edit()).thenReturn(mockEditor);
        when(mockEditor.putString(anyString(), anyString())).thenReturn(mockEditor);
        repository = new SettingsRepository(mockPrefs);
    }

    @Test
    public void testGetLocalBaseUrlReturnsStoredValue() {
        when(mockPrefs.getString("base_url", "")).thenReturn("http://192.168.1.5:11434/");

        String url = repository.getLocalBaseUrl();

        assertEquals("http://192.168.1.5:11434/", url);
    }

    @Test
    public void testSetLocalBaseUrlWritesToPrefs() {
        repository.setLocalBaseUrl("http://10.0.0.2:11434");

        verify(mockEditor).putString("base_url", "http://10.0.0.2:11434/");
        verify(mockEditor).apply();
    }

    @Test
    public void testGetApiKeyReturnsStoredValue() {
        when(mockPrefs.getString("api_key", "")).thenReturn("my-secret-key");

        assertEquals("my-secret-key", repository.getApiKey());
    }

    @Test
    public void testGetApiKeyReturnsEmptyStringWhenNotSet() {
        when(mockPrefs.getString("api_key", "")).thenReturn("");

        assertEquals("", repository.getApiKey());
    }

    @Test
    public void testSetApiKeyWritesToPrefs() {
        repository.setApiKey("new-key");

        verify(mockEditor).putString("api_key", "new-key");
        verify(mockEditor).apply();
    }
}
