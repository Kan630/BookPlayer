package com.driot.bookplayer.db;

/**
 * created by Antoine Driot -- antoine.driot.com -- on 28/10/20
 */
import androidx.room.Database;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;

@Database(entities = {
                        Folder.class,
                        ZikFile.class
                     }
                     , version = 3)

// v2 for field zeOrder
// v3 for field displayName

@TypeConverters({Converters.class})

public abstract class AppDatabase extends RoomDatabase {
    public abstract FolderDao FolderDao();
    public abstract ZikFileDao ZikFileDao();
}

