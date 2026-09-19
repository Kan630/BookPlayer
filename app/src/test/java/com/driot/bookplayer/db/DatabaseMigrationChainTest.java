package com.driot.bookplayer.db;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import androidx.room.migration.Migration;

import org.junit.Test;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A user upgrading from any old version walks the whole migration chain. One missing step or one
 * migration forgotten in DatabaseClient's addMigrations() list = the DB cannot open = app crash
 * on start for that user, and nothing in a fresh-install test would ever show it.
 * Package db so the package-private migration constants are reachable.
 */
public class DatabaseMigrationChainTest {

    private static File src(String relative) {
        for (String base : new String[] { "src/", "app/src/" }) {
            File f = new File(base + relative);
            if (f.exists())
                return f;
        }
        throw new IllegalStateException("not found: " + relative + " from " + new File(".").getAbsolutePath());
    }

    private static TreeMap<Integer, Migration> allMigrations() throws Exception {
        TreeMap<Integer, Migration> byStart = new TreeMap<>();
        for (Field f : DatabaseMigrations.class.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers()) && Migration.class.isAssignableFrom(f.getType())) {
                f.setAccessible(true);
                Migration m = (Migration) f.get(null);
                assertNotNull(f.getName(), m);
                if (byStart.put(m.startVersion, m) != null)
                    fail("two migrations start at version " + m.startVersion);
            }
        }
        return byStart;
    }

    @Test
    public void chainIsContiguousFromV1ToCurrentVersion() throws Exception {
        TreeMap<Integer, Migration> ms = allMigrations();
        int expectedStart = 1;
        for (Migration m : ms.values()) {
            assertEquals("gap in the chain before " + m.startVersion, expectedStart, m.startVersion);
            assertEquals("each step must be +1 (start " + m.startVersion + ")", m.startVersion + 1, m.endVersion);
            expectedStart = m.endVersion;
        }
        assertEquals("last migration must reach APP_DATABASE_VERSION - did you bump the version "
                + "without adding a migration?", BaseAppDatabase.APP_DATABASE_VERSION, expectedStart);
    }

    @Test
    public void everyMigrationIsRegisteredInDatabaseClient() throws Exception {
        String client = new String(Files.readAllBytes(src("main/java/com/driot/bookplayer/db/DatabaseClient.java").toPath()),
                StandardCharsets.UTF_8);
        List<String> missing = new ArrayList<>();
        for (Field f : DatabaseMigrations.class.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers()) && Migration.class.isAssignableFrom(f.getType())) {
                if (!client.contains("DatabaseMigrations." + f.getName()))
                    missing.add(f.getName());
            }
        }
        assertTrue("Declared but never passed to addMigrations(): " + missing, missing.isEmpty());
    }

    @Test
    public void everyExportedSchemaVersionExists_andMatchesItsFilename() throws Exception {
        File root = src("../schemas");
        Pattern versionField = Pattern.compile("\"version\"\\s*:\\s*(\\d+)");
        List<String> problems = new ArrayList<>();
        for (int v = 1; v <= BaseAppDatabase.APP_DATABASE_VERSION; v++) {
            File f = null;
            for (String dir : new String[] { "com.driot.bookplayer.db.AppDatabase",
                    "com.driot.bookplayer.db.BaseAppDatabase" }) {
                File c = new File(root, dir + "/" + v + ".json");
                if (c.isFile()) {
                    f = c;
                    break;
                }
            }
            if (f == null) {
                problems.add("no exported schema for version " + v);
                continue;
            }
            String json = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
            Matcher m = versionField.matcher(json);
            if (!m.find() || Integer.parseInt(m.group(1)) != v)
                problems.add(f + " does not declare version " + v);
        }
        assertTrue("Room schema history is incomplete/corrupt (migration tests rely on it):\n"
                + String.join("\n", problems), problems.isEmpty());
    }
}
