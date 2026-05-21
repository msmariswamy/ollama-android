package com.ollama.mobile.data.db;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.ollama.mobile.data.db.dao.ConversationDao;
import com.ollama.mobile.data.db.dao.MessageDao;
import com.ollama.mobile.data.db.entity.Conversation;
import com.ollama.mobile.data.db.entity.Message;

@Database(entities = {Conversation.class, Message.class}, version = 4, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

    private static volatile AppDatabase instance;

    public abstract ConversationDao conversationDao();
    public abstract MessageDao messageDao();

    static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE messages ADD COLUMN attachmentsJson TEXT");
        }
    };

    static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE conversations ADD COLUMN isArchived INTEGER NOT NULL DEFAULT 0");
        }
    };

    static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE conversations ADD COLUMN systemPrompt TEXT");
        }
    };

    public static AppDatabase getInstance(Context context) {
        if (instance == null) {
            synchronized (AppDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(
                            context.getApplicationContext(),
                            AppDatabase.class,
                            "ollama_db"
                    ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build();
                }
            }
        }
        return instance;
    }
}
