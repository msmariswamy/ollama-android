package com.ollama.mobile.repository;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.ollama.mobile.data.db.dao.ConversationDao;
import com.ollama.mobile.data.db.dao.MessageDao;
import com.ollama.mobile.data.db.entity.Conversation;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class ConversationRepositoryTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    private ConversationDao mockConversationDao;
    private MessageDao mockMessageDao;
    private ConversationRepository repository;

    @Before
    public void setUp() {
        mockConversationDao = mock(ConversationDao.class);
        mockMessageDao = mock(MessageDao.class);
        repository = new ConversationRepository(mockConversationDao, mockMessageDao);
    }

    @Test
    public void testGetAllConversationsDelegatesToDao() {
        MutableLiveData<List<Conversation>> liveData = new MutableLiveData<>(Collections.emptyList());
        when(mockConversationDao.getAll()).thenReturn(liveData);

        LiveData<List<Conversation>> result = repository.getAllConversations();

        assertSame(liveData, result);
        verify(mockConversationDao).getAll();
    }

    @Test
    public void testInsertConversationDelegatesToDao() throws Exception {
        when(mockConversationDao.insert(any(Conversation.class))).thenReturn(1L);
        CountDownLatch latch = new CountDownLatch(1);

        repository.createConversation("Test Title", id -> latch.countDown());

        assertTrue("Timed out", latch.await(3, TimeUnit.SECONDS));
        verify(mockConversationDao).insert(any(Conversation.class));
    }

    @Test
    public void testDeleteConversationDelegatesToDao() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        doAnswer(inv -> { latch.countDown(); return null; })
            .when(mockConversationDao).deleteById(anyLong());

        repository.deleteConversation(42L);

        assertTrue("Timed out", latch.await(3, TimeUnit.SECONDS));
        verify(mockConversationDao).deleteById(42L);
    }

    @Test
    public void testGetAllReturnsLiveDataMatchingMock() {
        MutableLiveData<List<Conversation>> mockLiveData = new MutableLiveData<>();
        when(mockConversationDao.getAll()).thenReturn(mockLiveData);

        LiveData<List<Conversation>> returned = repository.getAllConversations();

        assertSame(mockLiveData, returned);
    }
}
