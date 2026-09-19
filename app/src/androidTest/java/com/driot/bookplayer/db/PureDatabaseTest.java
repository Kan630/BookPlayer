package com.driot.bookplayer.db;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.database.Cursor;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Real Room + real SQLite, in-memory (never touches the user's "BookPlayer" database).
 * Runs on every flavor: the Folder/ZikFile/BookSource core is shared, and the last test
 * asserts the flavor-specific table set.
 */
@RunWith(AndroidJUnit4.class)
public class PureDatabaseTest {

    private AppDatabase db;
    private FolderDao folders;
    private ZikFileDao zikFiles;

    @Before
    public void createDb() {
        Context context = ApplicationProvider.getApplicationContext();
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase.class).allowMainThreadQueries().build();
        folders = db.folderDao();
        zikFiles = db.zikFileDao();
    }

    @After
    public void closeDb() {
        db.close();
    }

    private Folder folder(String name, String path) {
        Folder f = new Folder();
        f.setName(name);
        f.setPath(path);
        f.setPercentdone(0.0);
        return f;
    }

    private ZikFile track(long folderId, String folderName, String name, double order) {
        ZikFile z = new ZikFile();
        z.setIdFolder(folderId);
        z.setFolderName(folderName);
        z.setName(name);
        z.setPath("/books/" + folderName + "/" + name);
        z.setZeorder(order);
        z.setDuration(60_000);
        return z;
    }

    @Test
    public void insertFolder_assignsIdAndRoundTrips() {
        long id = folders.insert(folder("Dune", "/books/Dune"));
        assertTrue(id > 0);
        Folder back = folders.getById(id);
        assertNotNull(back);
        assertEquals("Dune", back.getName());
        assertEquals("/books/Dune", back.getPath());
        assertEquals(0.0, back.getPercentdone(), 0.0001);
    }

    @Test
    public void lookupByNamePathAndExistenceChecks() {
        folders.insert(folder("Dune", "/books/Dune"));
        assertNotNull(folders.getByName("Dune"));
        assertNotNull(folders.getFolderByPath("/books/Dune"));
        assertNull(folders.getFolderByPath("/nope"));
        assertTrue(folders.existsByPath("/books/Dune"));
        assertFalse(folders.existsByPath("/books/Other"));
        assertEquals(1, folders.folderAlreadyExist_checkFolderName("Dune"));
        assertEquals(0, folders.folderAlreadyExist_checkFolderName("Nope"));
        assertEquals("Dune", folders.folderAlreadyExist_checkFolderPath_getBookName("/books/Dune"));
    }

    @Test
    public void originalHashDuplicateDetection() {
        Folder f = folder("Emma", "/books/Emma");
        f.setOriginalHash("abc123");
        folders.insert(f);
        assertTrue(folders.existsByOriginalHash("abc123"));
        assertFalse(folders.existsByOriginalHash("zzz"));
        assertEquals("Emma", folders.originalHashAlreadyExist_getBookName("abc123"));
        assertNull(folders.originalHashAlreadyExist_getBookName("zzz"));
    }

    @Test
    public void renameFolder_andPropagateToTracks() {
        long id = folders.insert(folder("Old", "/books/Old"));
        zikFiles.insert(track(id, "Old", "01.mp3", 1));
        folders.changeName(id, "New");
        folders.updateFolderNameInZikFile(id, "New");
        assertEquals("New", folders.getById(id).getName());
        assertEquals("New", zikFiles.getZikFiles(id).get(0).getFolderName());
    }

    @Test
    public void tracksComeBackInOrder() {
        long id = folders.insert(folder("Ordered", "/books/Ordered"));
        zikFiles.insert(track(id, "Ordered", "c.mp3", 3));
        zikFiles.insert(track(id, "Ordered", "a.mp3", 1));
        zikFiles.insert(track(id, "Ordered", "b.mp3", 2));
        List<ZikFile> tracks = zikFiles.getZikFiles(id);
        assertEquals(3, tracks.size());
        assertEquals("a.mp3", tracks.get(0).getName());
        assertEquals("b.mp3", tracks.get(1).getName());
        assertEquals("c.mp3", tracks.get(2).getName());
        assertEquals(3.0, zikFiles.getMaxOrder(id), 0.0001);
    }

    @Test
    public void tracksAreScopedToTheirFolder() {
        long a = folders.insert(folder("A", "/books/A"));
        long b = folders.insert(folder("B", "/books/B"));
        zikFiles.insert(track(a, "A", "1.mp3", 1));
        zikFiles.insert(track(a, "A", "2.mp3", 2));
        zikFiles.insert(track(b, "B", "1.mp3", 1));
        assertEquals(2, zikFiles.getZikFiles(a).size());
        assertEquals(1, zikFiles.getZikFiles(b).size());
        zikFiles.deleteAllZikFilesInFolder(a);
        assertEquals(0, zikFiles.getZikFiles(a).size());
        assertEquals("deleting one book's tracks must not touch another's", 1, zikFiles.getZikFiles(b).size());
    }

    @Test
    public void playbackProgressPersists() {
        long id = folders.insert(folder("Progress", "/books/Progress"));
        long zid = zikFiles.insert(track(id, "Progress", "1.mp3", 1));
        ZikFile z = zikFiles.getById(zid);
        z.setPosition(12_345);
        z.setPercentdone(20.5);
        z.timeListened = 999;
        assertEquals(1, zikFiles.update(z));
        ZikFile back = zikFiles.getById(zid);
        assertEquals(12_345, back.getPosition(), 0.0001);
        assertEquals(20.5, back.getPercentdone(), 0.0001);
        assertEquals(999, back.timeListened);
    }

    @Test
    public void resetProgression_zerosPositionAndPercent() {
        Folder f = folder("Reset", "/books/Reset");
        f.setPosition(5);
        f.setPercentdone(50.0);
        long id = folders.insert(f);
        folders.resetProgression(id);
        Folder back = folders.getById(id);
        assertEquals(0, back.getPosition());
        assertEquals(0.0, back.getPercentdone(), 0.0001);
    }

    @Test
    public void deleteFolderById() {
        long id = folders.insert(folder("Gone", "/books/Gone"));
        folders.delete(id);
        assertNull(folders.getById(id));
    }

    @Test
    public void deleteAll_emptiesBothTables() {
        long id = folders.insert(folder("X", "/books/X"));
        zikFiles.insert(track(id, "X", "1.mp3", 1));
        zikFiles.deleteAll();
        folders.deleteAll();
        assertTrue(folders.getAll().isEmpty());
        assertTrue(zikFiles.getAll().isEmpty());
    }

    @Test
    public void unicodeAndQuotesInNamesSurvive() {
        String name = "L'été à Paris \"édition\" — 日本語 %_";
        long id = folders.insert(folder(name, "/books/" + name));
        assertEquals(name, folders.getById(id).getName());
        assertNotNull(folders.getByName(name));
    }

    @Test
    public void bookSourceRoundTrip() {
        BookSource bs = new BookSource("Title", "https://example.com", "audiobook", "librivox", "42", null, null, null);
        long id = db.bookSourceDao().insert(bs);
        BookSource back = db.bookSourceDao().getById(id);
        assertEquals("Title", back.book_title);
        assertEquals("librivox", back.repoName);
        assertNull(back.idFolder);
    }

    @Test
    public void tablesMatchTheFlavor() {
        Set<String> tables = new HashSet<>();
        try (Cursor c = db.getOpenHelper().getWritableDatabase()
                .query("SELECT name FROM sqlite_master WHERE type='table'")) {
            while (c.moveToNext())
                tables.add(c.getString(0));
        }
        for (String core : new String[] { "Folder", "ZikFile", "BookSource", "ImportJob", "PlayTick", "PlaySession" })
            assertTrue("core table missing: " + core, tables.contains(core));

        boolean pure = com.driot.bookplayer.utils.Tonio.isPure(ApplicationProvider.getApplicationContext());
        for (String fullOnly : new String[] { "Podcast", "Episode", "RadioStation", "PendingEpisodeHistory" }) {
            assertEquals(fullOnly + (pure ? " must NOT exist on pure" : " must exist on full"), !pure,
                    tables.contains(fullOnly));
        }
    }
}
