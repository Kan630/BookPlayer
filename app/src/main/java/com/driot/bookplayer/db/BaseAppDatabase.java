package com.driot.bookplayer.db;
// created by Antoine Driot -- antoine.driot.com -- on 01/03/2026  -

import static com.driot.bookplayer.db.AppDatabase.APP_DATABASE_VERSION;

import android.content.Context;

import androidx.room.RoomDatabase;

import com.driot.bookplayer.imports.ImportJobDao;
import com.driot.bookplayer.player.heatmaps.PlaySessionDao;
import com.driot.bookplayer.player.heatmaps.PlayTickDao;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public abstract class BaseAppDatabase extends RoomDatabase {
    public static final int APP_DATABASE_VERSION = 30;

    public abstract FolderDao folderDao();

    public abstract ZikFileDao zikFileDao();

    public abstract BookSourceDao bookSourceDao();

    public abstract ImportJobDao importJobDao();

    public abstract PlaySessionDao playSessionDao();

    public abstract PlayTickDao playTickDao();

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
