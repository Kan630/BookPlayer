package com.driot.bookplayer.db;

/**
 * created by Antoine Driot -- antoine.driot.com -- on 28/10/2020  -
 *
 * modified 05/2024
 */
import static com.driot.bookplayer.db.AppDatabase.APP_DATABASE_VERSION;

import android.content.Context;

import androidx.room.Database;
import androidx.room.RoomDatabase;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
@Database(entities = {
         Folder.class
        ,ZikFile.class
        ,Podcast.class
        ,BookSource.class
        ,Episode.class
        }, version = APP_DATABASE_VERSION
        )

public abstract class AppDatabase extends RoomDatabase {
    public static final int APP_DATABASE_VERSION = 12;

    public abstract FolderDao FolderDao();
    public abstract ZikFileDao ZikFileDao();
    public abstract PodcastDao PodcastDao();
    public abstract BookSourceDao BookSourceDao();
    public abstract EpisodeDao EpisodeDao();

    private static volatile AppDatabase INSTANCE;
    private static final int NUMBER_OF_WRITE_THREADS = 4;
    private static final int NUMBER_OF_READ_THREADS = 4;
    public static final ExecutorService databaseWriteExecutor = Executors.newFixedThreadPool(NUMBER_OF_WRITE_THREADS);
    public static final ExecutorService databaseReadExecutor = Executors.newFixedThreadPool(NUMBER_OF_READ_THREADS);

    public static AppDatabase getDatabase(final Context context) {
        return DatabaseClient.getInstance(context).getAppDatabase();
    }


}

