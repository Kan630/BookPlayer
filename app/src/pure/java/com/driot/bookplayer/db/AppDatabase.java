package com.driot.bookplayer.db;
// created by Antoine Driot -- antoine.driot.com -- on 28/10/2020  -

import static com.driot.bookplayer.db.BaseAppDatabase.APP_DATABASE_VERSION;

import androidx.room.Database;

import com.driot.bookplayer.imports.ImportJob;
import com.driot.bookplayer.player.heatmaps.PlaySession;
import com.driot.bookplayer.player.heatmaps.PlayTick;

@Database(entities = {
        Folder.class, ZikFile.class, BookSource.class, ImportJob.class, PlayTick.class, PlaySession.class
}, version = APP_DATABASE_VERSION)

public abstract class AppDatabase extends BaseAppDatabase {
}