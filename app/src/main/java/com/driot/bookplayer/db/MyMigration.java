package com.driot.bookplayer.db;
/*
import static com.driot.tonylib.KanLogger.myLog;

import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import io.reactivex.rxjava3.annotations.NonNull;

public class MyMigration extends Migration {
    public MyMigration(int startVersion, int endVersion) {
        super(startVersion, endVersion);
        myLog("Migration :  " + startVersion + " => " + endVersion);
    }

    @Override
    public void migrate(@NonNull SupportSQLiteDatabase database) {
        myLog("Migration -> migrate");
        // Write your migration logic here
        if (startVersion == 1) {
            myLog("Migration -> executing step 1");
            database.execSQL("ALTER TABLE ZikFile ADD COLUMN zeorder REAL NOT NULL default 0");
        }
        if (startVersion >= 2) {
            myLog("Migration -> executing step 2");
            database.execSQL("ALTER TABLE ZikFile ADD COLUMN displayName TEXT default '---'");
            database.execSQL("UPDATE ZikFile SET displayName = Name");
        }
    }
}
*/