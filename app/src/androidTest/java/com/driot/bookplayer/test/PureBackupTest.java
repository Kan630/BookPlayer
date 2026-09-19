package com.driot.bookplayer.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.driot.bookplayer.db.BackupManager;
import com.driot.bookplayer.utils.Tonio;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Backup/restore for pure. Deliberately never calls importFromJson with book progress or
 * LibriVox enabled: those wipe and rewrite the real library tables. Preferences import is only
 * exercised with a private throwaway preferences file name, so real settings are untouched too.
 */
@RunWith(AndroidJUnit4.class)
public class PureBackupTest {

    private static final String PREFS = "backup_test_prefs_" + PureBackupTest.class.getSimpleName();

    private Context ctx;
    private BackupManager manager;

    @Before
    public void setUp() {
        ctx = ApplicationProvider.getApplicationContext();
        assumeTrue("pure-flavor test", Tonio.isPure(ctx));
        manager = new BackupManager(ctx);
    }

    @After
    public void tearDown() {
        if (ctx != null)
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().commit();
    }

    @Test
    public void export_withEverythingRequested_hasNoRadioPodcastOrHistorySections() throws Exception {
        // ask for every category, like the UI would on full: pure must silently ignore the full-only ones
        String json = manager.exportToJson(true, true, true, true, false, true);
        JSONObject o = new JSONObject(json);
        assertFalse("radios leaked into a pure backup", o.has("radios"));
        assertFalse("podcasts leaked into a pure backup", o.has("podcasts"));
        assertFalse("episodes leaked into a pure backup", o.has("episodes"));
        assertFalse(o.has("pendingEpisodeHistory"));
        assertTrue(o.has("timestamp"));
        assertTrue(o.has("preferences"));
    }

    @Test
    public void export_typesEveryPreferenceValue() throws Exception {
        String json = manager.exportToJson(true, false, false, false, false, false);
        JSONObject prefs = new JSONObject(json).getJSONObject("preferences");
        java.util.Iterator<String> files = prefs.keys();
        while (files.hasNext()) {
            JSONObject file = prefs.getJSONObject(files.next());
            java.util.Iterator<String> keys = file.keys();
            while (keys.hasNext()) {
                JSONObject tp = file.getJSONObject(keys.next());
                String type = tp.getString("type");
                assertTrue(type, type.equals("boolean") || type.equals("float") || type.equals("int")
                        || type.equals("long") || type.equals("string"));
                assertTrue(tp.has("value"));
            }
        }
    }

    @Test
    public void export_withoutPreferences_hasEmptyPreferenceMap() throws Exception {
        String json = manager.exportToJson(false, false, false, false, false, false);
        assertEquals(0, new JSONObject(json).getJSONObject("preferences").length());
    }

    @Test
    public void inspect_roundTripsAnExport() {
        String json = manager.exportToJson(true, false, false, false, false, false);
        BackupManager.BackupData data = manager.inspectJson(json);
        assertNotNull(data);
        assertTrue(data.timestamp > 0);
        assertNotNull(data.preferences);
        assertNotNull(data.folders);
        assertNotNull(data.zikFiles);
    }

    @Test
    public void inspect_aBackupMadeByTheFullFlavorIsReadableAndItsExtrasIgnored() {
        // A user moving full -> pure restores a backup that has radios/podcasts sections.
        String fullBackup = "{ \"timestamp\": 1700000000000, \"preferences\": {}, \"bookSources\": [],"
                + " \"folders\": [], \"zikFiles\": [],"
                + " \"radios\": [ {\"id\": 1, \"name\": \"FIP\", \"url\": \"https://x/y\"} ],"
                + " \"podcasts\": [ {\"id\": 1, \"title\": \"Pod\"} ],"
                + " \"episodes\": [], \"pendingEpisodeHistory\": [] }";
        BackupManager.BackupData data = manager.inspectJson(fullBackup);
        assertNotNull(data);
        assertEquals(1700000000000L, data.timestamp);
        assertTrue(data.folders.isEmpty());
    }

    @Test
    public void inspect_garbageDoesNotProduceAHalfInitialisedResult() {
        try {
            BackupManager.BackupData data = manager.inspectJson("this is not json");
            assertNull("garbage must not look like a valid backup", data);
        } catch (RuntimeException expected) {
            // a parse exception is an acceptable way to reject it too
        }
    }

    @Test
    public void import_nullOrEmptyJsonIsANoOpAndDoesNotCrash() {
        try {
            manager.importFromJson("null", false, false, false, false, false, false);
        } catch (RuntimeException e) {
            org.junit.Assert.fail("importing a null backup must not crash: " + e);
        }
    }

    @Test
    public void import_restoresTypedPreferencesWithTheirOriginalTypes() {
        String json = "{ \"timestamp\": 1, \"preferences\": { \"" + PREFS + "\": {"
                + " \"b\": {\"type\":\"boolean\",\"value\":\"true\"},"
                + " \"f\": {\"type\":\"float\",\"value\":\"1.5\"},"
                + " \"i\": {\"type\":\"int\",\"value\":\"42\"},"
                + " \"l\": {\"type\":\"long\",\"value\":\"9999999999\"},"
                + " \"s\": {\"type\":\"string\",\"value\":\"hello\"},"
                + " \"bad\": {\"type\":\"int\",\"value\":\"not-a-number\"} } },"
                + " \"folders\": [], \"zikFiles\": [], \"bookSources\": [] }";

        manager.importFromJson(json, true, false, false, false, false, false);

        SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        assertTrue(sp.getBoolean("b", false));
        assertEquals(1.5f, sp.getFloat("f", 0f), 0.0001f);
        assertEquals(42, sp.getInt("i", 0));
        assertEquals(9_999_999_999L, sp.getLong("l", 0L));
        assertEquals("hello", sp.getString("s", null));
        assertFalse("a corrupt value is skipped, not fatal", sp.contains("bad"));
    }
}
