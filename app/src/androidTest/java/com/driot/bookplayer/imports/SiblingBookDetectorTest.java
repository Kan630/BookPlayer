package com.driot.bookplayer.imports;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import static org.junit.Assume.assumeFalse;

import android.content.Context;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

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

    /**
     * Crashlytics: an epub opened from Download was resolved to its real path, then HashWorker/scan failed
     * with EACCES (Android 11+: the path exists but the app can't open it). resolvePickedFile() must keep
     * the content Uri (return null) in that case, and still return readable files.
     */
    @Test
    public void resolvePickedFile_unreadableRealPath_keepsContentUri() throws Exception {
        Context ctx = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String name = "bp_canread_test_" + System.currentTimeMillis() + ".epub";
        File shellFile = new File("/storage/emulated/0/Download/" + name);
        shell("touch " + shellFile.getAbsolutePath()); // created by shell, not owned by the app (no shell redirects here)
        try {
            assertEquals("setup: shell could not create " + shellFile, true, shellFile.exists());
            assumeFalse("app has broad storage access on this device, case not reproducible", shellFile.canRead());

            Uri docUri = DocumentsContract.buildDocumentUri("com.android.externalstorage.documents",
                    "primary:Download/" + name);
            assertNull(SiblingBookDetector.resolvePickedFile(ctx, docUri));
        } finally {
            shell("rm -f " + shellFile.getAbsolutePath());
        }

        File readable = touch(tmp.getRoot(), "readable.mp3");
        assertEquals(readable.getAbsolutePath(),
                SiblingBookDetector.resolvePickedFile(ctx, Uri.fromFile(readable)).getAbsolutePath());
    }

    private static void shell(String cmd) throws IOException {
        ParcelFileDescriptor pfd = InstrumentationRegistry.getInstrumentation().getUiAutomation().executeShellCommand(cmd);
        try (ParcelFileDescriptor.AutoCloseInputStream in = new ParcelFileDescriptor.AutoCloseInputStream(pfd)) {
            while (in.read() != -1) {
                // drain: the command is finished once its output is closed
            }
        }
    }

    @Test
    public void videoFilesCountAsTracks() throws IOException {
        File dir = bookDir("Lectures");
        File a = touch(dir, "1.mp4");
        touch(dir, "2.mp4");
        assertEquals(2, SiblingBookDetector.detectSiblingsOf(a).siblingTrackCount);
    }
}
