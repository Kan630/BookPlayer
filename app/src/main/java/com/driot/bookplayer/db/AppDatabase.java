package com.driot.bookplayer.db;

/**
 * created by Antoine Driot -- antoine.driot.com -- on 28/10/2020  -
 *
 * modified 05/2024
 */
import static com.driot.bookplayer.db.AppDatabase.APPDATABASE_VERSION;

import android.content.Context;
import android.os.TestLooperManager;

import androidx.room.Database;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
@Database(entities = {
         Folder.class
        ,ZikFile.class
        ,Podcast.class
        ,BookSource.class
}
                     , version = APPDATABASE_VERSION
                    // on se fait les migrations a la main, en les ecrivant...... mais bon...
                    //, autoMigrations = {@AutoMigration(from = 1, to = 2),@AutoMigration(from = 2, to = 3)}
        )

// v2 for field zeOrder ?????
// v3 for field displayName -- 17 sept 2023

@TypeConverters({Converters.class})

public abstract class AppDatabase extends RoomDatabase {
    public static final int APPDATABASE_VERSION = 8;

    public abstract FolderDao FolderDao();
    public abstract ZikFileDao ZikFileDao();
    public abstract PodcastDao PodcastDao();
    public abstract BookSourceDao BookSourceDao();



    private static volatile AppDatabase INSTANCE;
    private static final int NUMBER_OF_WRITE_THREADS = 4;
    private static final int NUMBER_OF_READ_THREADS = 4;
    public static final ExecutorService databaseWriteExecutor = Executors.newFixedThreadPool(NUMBER_OF_WRITE_THREADS);
    public static final ExecutorService databaseReadExecutor = Executors.newFixedThreadPool(NUMBER_OF_READ_THREADS);


    public static AppDatabase getDatabase(final Context context) {
        return DatabaseClient.getInstance(context).getAppDatabase();
    }


}

