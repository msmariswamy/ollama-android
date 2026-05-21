package com.ollama.mobile.viewmodel;

import android.app.Application;
import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.MutableLiveData;
import com.ollama.mobile.data.db.entity.Conversation;
import com.ollama.mobile.repository.ConversationRepository;
import com.ollama.mobile.ui.conversations.ConversationListViewModel;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class ConversationListViewModelTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    private ConversationRepository mockRepository;
    private ConversationListViewModel viewModel;

    @Before
    public void setUp() {
        mockRepository = mock(ConversationRepository.class);
        Conversation c1 = new Conversation("Chat 1", 1000L, 1000L);
        Conversation c2 = new Conversation("Chat 2", 2000L, 2000L);
        MutableLiveData<List<Conversation>> liveData = new MutableLiveData<>(Arrays.asList(c1, c2));
        when(mockRepository.getAllConversations()).thenReturn(liveData);

        viewModel = new ConversationListViewModel(mock(Application.class), mockRepository);
    }

    @Test
    public void testConversationsLiveDataReflectsRepository() {
        List<Conversation> conversations = viewModel.conversations.getValue();
        assertNotNull(conversations);
        assertEquals(2, conversations.size());
    }

    @Test
    public void testDeleteDelegatesToRepository() {
        viewModel.deleteConversation(42L);
        verify(mockRepository).deleteConversation(42L);
    }
}
