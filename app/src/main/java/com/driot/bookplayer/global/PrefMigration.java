package com.driot.bookplayer.global;

import static android.content.Context.MODE_PRIVATE;

import android.content.Context;
import android.content.SharedPreferences;

import static com.driot.bookplayer.utils.log.LoggerStaticHelper.*;


public final class PrefMigration {

    private static final String PREF_MIGRATION = "PREF_MIGRATION";
    private static final String KEY_PREF_VERSION = "KEY_PREF_VERSION";

    // Define versions
    private static final int VERSION_INITIAL = 100; // before stats split
    private static final int VERSION_STATS_SPLIT = 192;

    private PrefMigration() {}

    public static void run(Context context) {
        SharedPreferences migration = context.getSharedPreferences(PREF_MIGRATION, MODE_PRIVATE);
        int currentVersion = migration.getInt(KEY_PREF_VERSION, VERSION_INITIAL);

        if (currentVersion < VERSION_STATS_SPLIT) {
            migrateStatsToOwnFile(context);
            migration.edit().putInt(KEY_PREF_VERSION, VERSION_STATS_SPLIT).apply();
            myLogI("Stats migrated to own file");
        }

        // Future migrations:
        // if (currentVersion < 3) { migrateSomethingElse(context); ... }
    }

    private static void migrateStatsToOwnFile(Context context) {
        SharedPreferences oldPrefs = context.getSharedPreferences("SHARED_PREFERENCES_DIVERSE", MODE_PRIVATE);
        SharedPreferences newStats = context.getSharedPreferences("SHARED_PREFERENCES_STATS", MODE_PRIVATE);

        boolean newHasStats =
                newStats.contains("FIRST_OPEN_TIMESTAMP")
                        || newStats.contains("TOTAL_PLAY_IN_MS")
                        || newStats.contains("FIRST_OPEN_DATE");

        boolean oldHasStats =
                oldPrefs.contains("FIRST_OPEN_TIMESTAMP")
                        || oldPrefs.contains("TOTAL_PLAY_IN_MS")
                        || oldPrefs.contains("FIRST_OPEN_DATE");

        if (!newHasStats && oldHasStats) {
            long firstTs   = oldPrefs.getLong("FIRST_OPEN_TIMESTAMP", 0L);
            String firstDt = oldPrefs.getString("FIRST_OPEN_DATE", "");
            long totalMs   = oldPrefs.getLong("TOTAL_PLAY_IN_MS", 0L);

            SharedPreferences.Editor e = newStats.edit();
            if (firstTs != 0L) {
                e.putLong("FIRST_OPEN_TIMESTAMP", firstTs);
            }
            if (!firstDt.isEmpty()) {
                e.putString("FIRST_OPEN_DATE", firstDt);
            }
            if (totalMs != 0L) {
                e.putLong("TOTAL_PLAY_IN_MS", totalMs);
            }
            e.apply();
        }
    }
}
