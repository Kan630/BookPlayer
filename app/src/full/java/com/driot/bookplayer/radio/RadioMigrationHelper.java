package com.driot.bookplayer.radio;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;

import androidx.annotation.NonNull;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.driot.bookplayer.global.Pref;

import java.util.List;

import static android.content.Context.MODE_PRIVATE;
import static com.driot.bookplayer.utils.log.LoggerStaticHelper.*;

public class RadioMigrationHelper {

    // Flag to ensure we only do this once per install
    private static final String KEY_RADIO_FAVORITES_MOVED_TO_ROOM = "radio_favorites_moved_to_room";

    private RadioMigrationHelper() {
    }

    /**
     * One-off migration: copy favorites from SharedPreferences
     * (RadioFavoritesStore)
     * into the new Room table RadioStation.
     *
     * Called from RoomDatabase.Callback.onOpen(), AFTER Room has run all schema
     * migrations.
     */
    public static void migrateFavoritesFromPrefsToRoomOnce(@NonNull Context context,
            @NonNull SupportSQLiteDatabase db) {
        Context appCtx = context.getApplicationContext();
        SharedPreferences migrationPrefs = Pref.getMigrationPrefs(appCtx);

        boolean alreadyDone = migrationPrefs.getBoolean(KEY_RADIO_FAVORITES_MOVED_TO_ROOM, false);
        if (alreadyDone) {
            myLogD("RadioMigration: migrationPrefs→Room already done, skipping.");
            return;
        }

        try {
            // If table already has rows, don't import from migrationPrefs (probably fresh
            // install or manual test)
            long count = 0;
            try (Cursor c = db.query("SELECT COUNT(*) FROM RadioStation")) {
                if (c.moveToFirst()) {
                    count = c.getLong(0);
                }
            }

            if (count > 0) {
                myLogI("RadioMigration: RadioStation already has " + count + " rows, skip migrationPrefs migration.");
                migrationPrefs.edit().putBoolean(KEY_RADIO_FAVORITES_MOVED_TO_ROOM, true).apply();
                return;
            }
        } catch (Exception e) {
            // In case table does not exist or query fails, log & bail (Room will handle
            // schema issues)
            myLogW("RadioMigration: could not count RadioStation rows, skipping migrationPrefs migration: " + e);
            return;
        }

        try {
            RadioFavoritesStore store = new RadioFavoritesStore(appCtx);
            List<RadioFavoriteItem> favItems = store.getAll();

            if (favItems == null || favItems.isEmpty()) {
                myLogI("RadioMigration: no favorites in RadioFavoritesStore, nothing to migrate.");
                migrationPrefs.edit().putBoolean(KEY_RADIO_FAVORITES_MOVED_TO_ROOM, true).apply();
                return;
            }

            long now = System.currentTimeMillis();
            int order = 0;

            db.beginTransaction();
            try {
                for (RadioFavoriteItem f : favItems) {
                    if (f.stationuuid == null || f.stationuuid.isEmpty())
                        continue;

                    // Insert OR REPLACE into RadioStation
                    db.execSQL(
                            "INSERT OR REPLACE INTO RadioStation(" +
                                    "stationuuid, name, url, url_resolved, codec, " +
                                    "bitrate, hls, favicon, country, countrycode, language, tags, " +
                                    "clickcount, lastcheckok, display_order, isFavorite, " +
                                    "date_last_played, date_added, date_maj" +
                                    ") VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                            new Object[] {
                                    f.stationuuid,
                                    f.name,
                                    f.url,
                                    f.url_resolved,
                                    f.codec,
                                    f.bitrate,
                                    f.hls,
                                    f.favicon,
                                    f.country,
                                    f.countrycode,
                                    f.language,
                                    f.tags,
                                    f.clickcount,
                                    f.lastcheckok,
                                    order++, // display_order based on old order
                                    1, // isFavorite = true
                                    null, // date_last_played
                                    now, // date_added
                                    now // date_maj
                            });
                }

                db.setTransactionSuccessful();
                myLogI("RadioMigration: migrated " + favItems.size() + " radio favorites from migrationPrefs to Room.");
            } finally {
                db.endTransaction();
            }

            // Mark as done
            migrationPrefs.edit().putBoolean(KEY_RADIO_FAVORITES_MOVED_TO_ROOM, true).apply();

        } catch (Exception e) {
            myLogEE(e, "RadioMigration: error migrating favorites from migrationPrefs to Room");
            // Do NOT set the flag here so we can retry next launch if desired.
        }
    }
}
