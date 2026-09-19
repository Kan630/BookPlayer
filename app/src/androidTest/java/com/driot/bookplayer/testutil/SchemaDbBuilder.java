package com.driot.bookplayer.testutil;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import androidx.annotation.Nullable;
import androidx.test.platform.app.InstrumentationRegistry;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * Rebuilds an old-version Room database file from the exported schema JSON (app/schemas, packaged
 * as androidTest assets) so a migration test can open it with the current code, exactly like a
 * user updating the app. Deliberately independent of androidx.room:room-testing.
 */
public final class SchemaDbBuilder {

    private static final String[] SCHEMA_DIRS = {
            "com.driot.bookplayer.db.AppDatabase", "com.driot.bookplayer.db.BaseAppDatabase" };

    private SchemaDbBuilder() {
    }

    /** Loads schemas/.../{version}.json, or null if that version was never exported. */
    @Nullable
    public static JSONObject loadSchema(int version) throws Exception {
        Context testCtx = InstrumentationRegistry.getInstrumentation().getContext();
        for (String dir : SCHEMA_DIRS) {
            try (InputStream in = testCtx.getAssets().open(dir + "/" + version + ".json")) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                for (int n; (n = in.read(buf)) > 0;)
                    out.write(buf, 0, n);
                return new JSONObject(new String(out.toByteArray(), StandardCharsets.UTF_8)).getJSONObject("database");
            } catch (IOException notInThisDir) {
                // try the next dir
            }
        }
        return null;
    }

    /**
     * Creates {@code targetCtx.getDatabasePath(dbName)} at {@code version} with the tables of that
     * exported schema (minus {@code excludedTables}) plus one seeded Folder and one ZikFile row.
     * The file is created under a caller-chosen throwaway name, never the real "BookPlayer" DB.
     */
    public static void create(Context targetCtx, String dbName, int version, Set<String> excludedTables)
            throws Exception {
        JSONObject schema = loadSchema(version);
        if (schema == null)
            throw new IllegalStateException("no exported schema for version " + version);

        targetCtx.deleteDatabase(dbName);
        File file = targetCtx.getDatabasePath(dbName);
        //noinspection ResultOfMethodCallIgnored
        file.getParentFile().mkdirs();

        SQLiteDatabase db = SQLiteDatabase.openOrCreateDatabase(file, null);
        try {
            JSONArray entities = schema.getJSONArray("entities");
            for (int i = 0; i < entities.length(); i++) {
                JSONObject e = entities.getJSONObject(i);
                String table = e.getString("tableName");
                if (excludedTables.contains(table))
                    continue;
                db.execSQL(e.getString("createSql").replace("${TABLE_NAME}", table));
                JSONArray indices = e.optJSONArray("indices");
                for (int j = 0; indices != null && j < indices.length(); j++) {
                    db.execSQL(indices.getJSONObject(j).getString("createSql").replace("${TABLE_NAME}", table));
                }
            }

            seed(db, entities, "Folder", 0, "Seed Book", "/seed/path");
            seed(db, entities, "ZikFile", 1, "seed.mp3", "/seed/path/seed.mp3");
            db.setVersion(version);
        } finally {
            db.close();
        }
    }

    /** Inserts one row filling every NOT NULL column that has no default; name/path/idFolder set explicitly. */
    private static void seed(SQLiteDatabase db, JSONArray entities, String table, long idFolder, String name,
            String path) throws Exception {
        JSONObject entity = null;
        for (int i = 0; i < entities.length(); i++) {
            if (table.equals(entities.getJSONObject(i).getString("tableName")))
                entity = entities.getJSONObject(i);
        }
        if (entity == null)
            return;

        ContentValues cv = new ContentValues();
        JSONArray fields = entity.getJSONArray("fields");
        for (int i = 0; i < fields.length(); i++) {
            JSONObject f = fields.getJSONObject(i);
            String col = f.getString("columnName");
            if (col.equals("id"))
                continue;
            if (col.equals("name") || col.equals("folderName")) {
                cv.put(col, name);
            } else if (col.equals("path")) {
                cv.put(col, path);
            } else if (col.equals("idFolder") && idFolder > 0) {
                cv.put(col, idFolder);
            } else if (f.optBoolean("notNull", false) && !f.has("defaultValue")) {
                String affinity = f.optString("affinity", "TEXT");
                if (affinity.equals("INTEGER"))
                    cv.put(col, 0L);
                else if (affinity.equals("REAL"))
                    cv.put(col, 0.0);
                else
                    cv.put(col, "");
            }
        }
        long rowId = db.insertOrThrow(table, null, cv);
        if (rowId < 0)
            throw new IllegalStateException("could not seed " + table);
    }
}
