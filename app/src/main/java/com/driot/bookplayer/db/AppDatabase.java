package com.driot.bookplayer.db;
// created by Antoine Driot -- antoine.driot.com -- on 28/10/2020  -


import static com.driot.bookplayer.db.AppDatabase.APP_DATABASE_VERSION;

import android.content.Context;

import androidx.room.Database;
import androidx.room.RoomDatabase;

import com.driot.bookplayer.imports.ImportJob;
import com.driot.bookplayer.imports.ImportJobDao;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
@Database(entities = {
         Folder.class
        ,ZikFile.class
        ,Podcast.class
        ,BookSource.class
        ,Episode.class
        ,ImportJob.class
        }, version = APP_DATABASE_VERSION
        )

public abstract class AppDatabase extends RoomDatabase {
    public static final int APP_DATABASE_VERSION = 20;

    public abstract FolderDao folderDao();
    public abstract ZikFileDao zikFileDao();
    public abstract PodcastDao podcastDao();
    public abstract BookSourceDao bookSourceDao();
    public abstract EpisodeDao episodeDao();
    public abstract ImportJobDao importJobDao();

    private static final int NUMBER_OF_WRITE_THREADS = 4;
    private static final int NUMBER_OF_READ_THREADS = 4;
    public static final ExecutorService databaseWriteExecutor = Executors.newFixedThreadPool(NUMBER_OF_WRITE_THREADS);
    public static final ExecutorService databaseReadExecutor = Executors.newFixedThreadPool(NUMBER_OF_READ_THREADS);

    public static AppDatabase getDatabase(final Context context) {
        return DatabaseClient.getInstance(context).getAppDatabase();
    }
    public static AppDatabase getInstance(final Context context) {
        return DatabaseClient.getInstance(context).getAppDatabase();
    }


}

