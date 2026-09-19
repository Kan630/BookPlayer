package com.driot.bookplayer.imports;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;

/**
 * "Open with" on one mp3 offers to import the whole folder only when that folder really is one
 * book. Wrong guesses either merge unrelated files or hide a genuine multi-chapter book.
 *
 * Instrumented (not JVM) because its static init reads android.os.Environment constants, which
 * are null in the JVM android.jar stubs.
 */
@RunWith(AndroidJUnit4.class)
public class SiblingBookDetectorTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private File bookDir(String name) throws IOException {
        return tmp.newFolder(name);
    }

    private static File touch(File dir, String name) throws IOException {
        File f = new File(dir, name);
        if (!f.createNewFile())
            throw new IOException("exists: " + f);
        return f;
    }

    @Test
    public void multiTrackFolder_isReportedWithTrackCount() throws IOException {
        File dir = bookDir("My Audiobook");
        File first = touch(dir, "01.mp3");
        touch(dir, "02.mp3");
        touch(dir, "03.mp3");
        touch(dir, "cover.jpg"); // non-audio sibling must not be counted

        SiblingBookDetector.Result r = SiblingBookDetector.detectSiblingsOf(first);
        assertSame(first, r.pickedFile);
        assertEquals(dir, r.parentDir);
        assertEquals(3, r.siblingTrackCount);
    }

    @Test
    public void singleTrack_noSuggestion_butPickedFileKept() throws IOException {
        File dir = bookDir("Single");
        File only = touch(dir, "only.mp3");
        touch(dir, "notes.txt");

        SiblingBookDetector.Result r = SiblingBookDetector.detectSiblingsOf(only);
        assertSame(only, r.pickedFile);
        assertNull(r.parentDir);
        assertEquals(0, r.siblingTrackCount);
    }

    @Test
    public void subfolderNextToTrack_meansNotASingleBook() throws IOException {
        File dir = bookDir("Library");
        File a = touch(dir, "a.mp3");
        touch(dir, "b.mp3");
        new File(dir, "Other Book").mkdir();

        SiblingBookDetector.Result r = SiblingBookDetector.detectSiblingsOf(a);
        assertNull(r.parentDir);
        assertEquals(0, r.siblingTrackCount);
    }

    @Test
    public void zipOrM4bSibling_meansSeveralDistinctBooks() throws IOException {
        File dir = bookDir("Mixed");
        File a = touch(dir, "a.mp3");
        touch(dir, "b.mp3");
        touch(dir, "whole-book.m4b");
        assertNull(SiblingBookDetector.detectSiblingsOf(a).parentDir);

        File dir2 = bookDir("Mixed2");
        File c = touch(dir2, "c.mp3");
        touch(dir2, "d.mp3");
        touch(dir2, "other.zip");
        assertNull(SiblingBookDetector.detectSiblingsOf(c).parentDir);
    }

    @Test
    public void genericSharedFolders_areNeverSuggested() throws IOException {
        for (String generic : new String[] { "Download", "Music", "Podcasts", "DCIM" }) {
            File dir = bookDir(generic);
            File a = touch(dir, "a.mp3");
            touch(dir, "b.mp3");
            SiblingBookDetector.Result r = SiblingBookDetector.detectSiblingsOf(a);
            assertNotNull(generic, r);
            assertNull(generic, r.parentDir);
            assertEquals(generic, 0, r.siblingTrackCount);
        }
    }

    @Test
    public void bookFolderInsideGenericFolder_isStillSuggested() throws IOException {
        File download = bookDir("Download");
        File book = new File(download, "My Book");
        assertEquals(true, book.mkdir());
        File a = touch(book, "01.mp3");
        touch(book, "02.mp3");

        SiblingBookDetector.Result r = SiblingBookDetector.detectSiblingsOf(a);
        assertEquals(book, r.parentDir);
        assertEquals(2, r.siblingTrackCount);
    }

    @Test
    public void videoFilesCountAsTracks() throws IOException {
        File dir = bookDir("Lectures");
        File a = touch(dir, "1.mp4");
        touch(dir, "2.mp4");
        assertEquals(2, SiblingBookDetector.detectSiblingsOf(a).siblingTrackCount);
    }
}
