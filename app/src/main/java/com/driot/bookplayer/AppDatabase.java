package com.driot.bookplayer;

/**
 * created by Antoine Driot -- antoine.driot.com -- on 28/10/20
 */
import androidx.room.Database;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;

import java.sql.Date;

@Database(entities = {
        com.driot.bookplayer.Folder.class,
        com.driot.bookplayer.ZikFile.class
}, version = 5)

@TypeConverters({Converters.class})

public abstract class AppDatabase extends RoomDatabase {
    public abstract com.driot.bookplayer.FolderDao FolderDao();
    public abstract com.driot.bookplayer.ZikFileDao ZikFileDao();
}

