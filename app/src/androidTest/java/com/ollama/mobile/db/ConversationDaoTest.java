package com.ollama.mobile.db;

import android.content.Context;
import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.LiveData;
import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.ollama.mobile.data.db.AppDatabase;
import com.ollama.mobile.data.db.dao.ConversationDao;
import com.ollama.mobile.data.db.entity.Conversation;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class ConversationDaoTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    private AppDatabase db;
    private ConversationDao dao;

    @Before
    public void createDb() {
        Context context = ApplicationProvider.getApplicationContext();
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .build();
        dao = db.conversationDao();
    }

    @After
    public void closeDb() {
        db.close();
    }

    private List<Conversation> getAll() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        List<Conversation>[] result = new List[1];
        dao.getAll().observeForever(list -> {
            result[0] = list;
            latch.countDown();
        });
        assertTrue(latch.await(3, TimeUnit.SECONDS));
        return result[0];
    }

    @Test
    public void testInsertAndRetrieveAll() throws Exception {
        dao.insert(new Conversation("Chat 1", 1000L, 1000L));
        dao.insert(new Conversation("Chat 2", 2000L, 2000L));

        List<Conversation> all = getAll();
        assertEquals(2, all.size());
    }

    @Test
    public void testDeleteRemovesConversation() throws Exception {
        long id = dao.insert(new Conversation("Temp", 1000L, 1000L));
        dao.deleteById(id);

        List<Conversation> all = getAll();
        assertEquals(0, all.size());
    }

    @Test
    public void testInsertReturnsPositiveRowId() {
        long id = dao.insert(new Conversation("Test", 1000L, 1000L));
        assertTrue(id > 0);
    }

    @Test
    public void testUpdateTitlePersists() throws Exception {
        long id = dao.insert(new Conversation("Original", 1000L, 1000L));
        dao.updateTitle(id, "Updated");

        Conversation conv = dao.getById(id);
        assertEquals("Updated", conv.title);
    }
}
