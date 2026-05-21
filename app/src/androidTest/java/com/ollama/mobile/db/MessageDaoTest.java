package com.ollama.mobile.db;

import android.content.Context;
import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.ollama.mobile.data.db.AppDatabase;
import com.ollama.mobile.data.db.dao.ConversationDao;
import com.ollama.mobile.data.db.dao.MessageDao;
import com.ollama.mobile.data.db.entity.Conversation;
import com.ollama.mobile.data.db.entity.Message;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.List;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class MessageDaoTest {

    private AppDatabase db;
    private MessageDao messageDao;
    private ConversationDao conversationDao;

    @Before
    public void createDb() {
        Context context = ApplicationProvider.getApplicationContext();
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .build();
        messageDao = db.messageDao();
        conversationDao = db.conversationDao();
    }

    @After
    public void closeDb() {
        db.close();
    }

    private long createConversation(String title) {
        return conversationDao.insert(new Conversation(title, 1000L, 1000L));
    }

    @Test
    public void testInsertAndRetrieveByConversationId() {
        long convId = createConversation("Test");
        messageDao.insert(new Message(convId, "user", "msg 1", 1000L));
        messageDao.insert(new Message(convId, "assistant", "msg 2", 2000L));
        messageDao.insert(new Message(convId, "user", "msg 3", 3000L));

        List<Message> messages = messageDao.getByConversationId(convId);
        assertEquals(3, messages.size());
    }

    @Test
    public void testIsolationBetweenConversations() {
        long convId1 = createConversation("Conv 1");
        long convId2 = createConversation("Conv 2");

        messageDao.insert(new Message(convId1, "user", "hello from 1", 1000L));
        messageDao.insert(new Message(convId2, "user", "hello from 2", 2000L));

        List<Message> msgs1 = messageDao.getByConversationId(convId1);
        List<Message> msgs2 = messageDao.getByConversationId(convId2);

        assertEquals(1, msgs1.size());
        assertEquals("hello from 1", msgs1.get(0).content);
        assertEquals(1, msgs2.size());
        assertEquals("hello from 2", msgs2.get(0).content);
    }

    @Test
    public void testDeleteAllForConversation() {
        long convId = createConversation("Test");
        messageDao.insert(new Message(convId, "user", "msg", 1000L));
        messageDao.deleteByConversationId(convId);

        List<Message> messages = messageDao.getByConversationId(convId);
        assertEquals(0, messages.size());
    }

    @Test
    public void testChronologicalOrder() {
        long convId = createConversation("Test");
        messageDao.insert(new Message(convId, "user", "first", 1000L));
        messageDao.insert(new Message(convId, "assistant", "second", 2000L));
        messageDao.insert(new Message(convId, "user", "third", 3000L));

        List<Message> messages = messageDao.getByConversationId(convId);
        assertEquals(3, messages.size());
        assertEquals("first", messages.get(0).content);
        assertEquals("second", messages.get(1).content);
        assertEquals("third", messages.get(2).content);
    }

    @Test
    public void testInsertReturnsPositiveId() {
        long convId = createConversation("Test");
        long msgId = messageDao.insert(new Message(convId, "user", "test", 1000L));
        assertTrue(msgId > 0);
    }
}
