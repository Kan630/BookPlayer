package com.driot.bookplayer.services.m4b;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.media.MediaExtractor;
import android.os.ParcelFileDescriptor;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * M4bSplitter on real audiobooks and on modified copies of them. The promise under test: a book is split only when
 * every chapter file is proven readable, otherwise the M4B is left exactly as it was (imported unsplit).
 * <p>
 * Inputs are pushed beforehand to /data/local/tmp/bp_m4b/ (built with tools/m4b_variants.py, see its header):
 * frost, anthem, elements, piper (untouched fixtures) + elements_broken_vide, frost_chpl_only, frost_bad_chapters,
 * anthem_uint64. Tests whose input is missing are skipped.
 */
@RunWith(AndroidJUnit4.class)
public class M4bSplitterDeviceTest {

    private static final String INPUT_DIR = "/data/local/tmp/bp_m4b/";

    private Context ctx;
    private File root;

    @Before
    public void setUp() {
        ctx = InstrumentationRegistry.getInstrumentation().getTargetContext();
        root = new File(ctx.getFilesDir(), "m4bsplitter_test");
        deleteRecursively(root);
        assertTrue(root.mkdirs());
    }

    @After
    public void tearDown() {
        deleteRecursively(root);
    }

    // ------------------------------------------------------------------------------ helpers

    private static final class Run {
        File m4b;
        File dest;
        byte[] originalHash;
        M4bSplitter.Outcome outcome;
    }

    private Run split(String name, M4bSplitter.Host host) throws Exception {
        Run r = new Run();
        File dir = new File(root, name);
        assertTrue(dir.mkdirs());
        r.m4b = new File(dir, name + ".m4b");
        assumeTrue("input missing: " + name, copyFromShell(INPUT_DIR + name + ".m4b", r.m4b));
        r.originalHash = sha256(r.m4b);
        r.dest = new File(dir, "book");
        assertTrue(r.dest.mkdirs());
        r.outcome = new M4bSplitter(host).split(r.m4b, r.dest);
        return r;
    }

    private Run split(String name) throws Exception {
        return split(name, new M4bSplitter.Host() {
            @Override
            public boolean isCancelled() {
                return false;
            }

            @Override
            public void onChapter(int index, int count, String title) {
            }
        });
    }

    /** SPLIT: files in place, m4b gone, no temp folder, every file readable, nothing else in the folder. */
    private static void assertCleanSplit(Run r) throws Exception {
        assertEquals(r.outcome.reason, M4bSplitter.Outcome.Kind.SPLIT, r.outcome.kind);
        assertFalse("source M4B must be deleted after a verified split", r.m4b.exists());
        File[] left = r.dest.listFiles();
        assertEquals("only the chapter files in the book folder", r.outcome.files.size(), left.length);
        for (File f : left) {
            assertTrue(f.getName(), f.getName().endsWith(".aac"));
            assertTrue(f.getName(), f.length() > 0);
        }
        assertTrue(r.outcome.files.size() >= 2);
    }

    /** NOT_SPLIT/CANCELLED: the M4B is byte for byte what it was, and nothing was left behind. */
    private static void assertUntouched(Run r) throws Exception {
        assertNotEquals(M4bSplitter.Outcome.Kind.SPLIT, r.outcome.kind);
        assertTrue("M4B kept", r.m4b.exists());
        assertArrayEquals("M4B unchanged", r.originalHash, sha256(r.m4b));
        File[] left = r.dest.listFiles();
        assertEquals("no chapter or temp file left: " + Arrays.toString(left), 0, left.length);
    }

    /** Total audio of the chapter files, from the platform extractor (frames x 1024 / sample rate). */
    private static long totalDurationMs(List<File> files) throws Exception {
        long us = 0;
        for (File f : files) {
            MediaExtractor ex = new MediaExtractor();
            try {
                ex.setDataSource(f.getAbsolutePath());
                ex.selectTrack(0);
                int rate = ex.getTrackFormat(0).getInteger(android.media.MediaFormat.KEY_SAMPLE_RATE);
                ByteBuffer b = ByteBuffer.allocate(64 * 1024);
                long frames = 0;
                while (ex.readSampleData(b, 0) >= 0) {
                    frames++;
                    ex.advance();
                }
                us += frames * 1024L * 1_000_000L / rate;
            } finally {
                ex.release();
            }
        }
        return us / 1000;
    }

    private static long durationMs(File m4b) throws Exception {
        MediaExtractor ex = new MediaExtractor();
        try {
            ex.setDataSource(m4b.getAbsolutePath());
            for (int i = 0; i < ex.getTrackCount(); i++)
                if ("audio/mp4a-latm".equals(ex.getTrackFormat(i).getString(android.media.MediaFormat.KEY_MIME)))
                    return ex.getTrackFormat(i).getLong(android.media.MediaFormat.KEY_DURATION) / 1000;
        } finally {
            ex.release();
        }
        return -1;
    }

    private static List<String> names(List<File> files) {
        List<String> n = new ArrayList<>();
        for (File f : files)
            n.add(f.getName());
        return n;
    }

    // ------------------------------------------------------------------------------ untouched books: mp4parser path

    private void splitsWithMp4parserAndKeepsAllAudio(String name) throws Exception {
        Run r = split(name);
        long originalMs = -1;
        // duration of the source, measured on a fresh copy (the split deleted it)
        File copy = new File(root, name + "_dur.m4b");
        if (copyFromShell(INPUT_DIR + name + ".m4b", copy))
            originalMs = durationMs(copy);
        assertCleanSplit(r);
        assertEquals(M4bSplitter.METHOD_MP4PARSER, r.outcome.method);
        long splitMs = totalDurationMs(r.outcome.files);
        assertTrue(name + ": split " + splitMs + " ms vs source " + originalMs + " ms",
                Math.abs(splitMs - originalMs) <= 1_000);
    }

    @Test
    public void frost() throws Exception {
        splitsWithMp4parserAndKeepsAllAudio("frost");
    }

    @Test
    public void anthem() throws Exception {
        splitsWithMp4parserAndKeepsAllAudio("anthem");
    }

    @Test
    public void elements() throws Exception {
        splitsWithMp4parserAndKeepsAllAudio("elements");
    }

    @Test
    public void piper() throws Exception {
        splitsWithMp4parserAndKeepsAllAudio("piper");
    }

    // ------------------------------------------------------------------------------ rescued by the fallback

    /** Crashlytics NPE shape: same chapters as the intact book, split by the fallback. */
    @Test
    public void brokenCoverTrack_splitByFallback_sameChaptersAsIntact() throws Exception {
        Run intact = split("elements");
        assertCleanSplit(intact);
        Run broken = split("elements_broken_vide");
        assertCleanSplit(broken);
        assertEquals("extractor+" + Mp4ChapterReader.SOURCE_CHAPTER_TRACK, broken.outcome.method);
        assertEquals(names(intact.outcome.files), names(broken.outcome.files));
        long a = totalDurationMs(intact.outcome.files), b = totalDurationMs(broken.outcome.files);
        assertTrue("same audio: " + a + " vs " + b, Math.abs(a - b) <= 100);
    }

    @Test
    public void chplOnly_splitByFallback() throws Exception {
        Run intact = split("frost");
        assertCleanSplit(intact);
        Run chpl = split("frost_chpl_only");
        assertCleanSplit(chpl);
        assertEquals("extractor+" + Mp4ChapterReader.SOURCE_CHPL, chpl.outcome.method);
        assertEquals(intact.outcome.files.size(), chpl.outcome.files.size());
        long a = totalDurationMs(intact.outcome.files), b = totalDurationMs(chpl.outcome.files);
        assertTrue("same audio: " + a + " vs " + b, Math.abs(a - b) <= 100);
    }

    /** "I don't know how to deal with UInt64": split by the fallback, or refused cleanly - never half done. */
    @Test
    public void uint64Box_splitOrUntouched() throws Exception {
        Run r = split("anthem_uint64");
        if (r.outcome.kind == M4bSplitter.Outcome.Kind.SPLIT)
            assertCleanSplit(r);
        else
            assertUntouched(r);
    }

    // ------------------------------------------------------------------------------ never worse

    @Test
    public void badChapterTimeline_refused_m4bUntouched() throws Exception {
        Run r = split("frost_bad_chapters");
        assertEquals(M4bSplitter.Outcome.Kind.NOT_SPLIT, r.outcome.kind);
        assertTrue(r.outcome.reason, r.outcome.reason.startsWith("chapters refused"));
        assertUntouched(r);
    }

    @Test
    public void cancelledMidSplit_m4bUntouched() throws Exception {
        AtomicInteger chapters = new AtomicInteger();
        Run r = split("anthem", new M4bSplitter.Host() {
            @Override
            public boolean isCancelled() {
                return chapters.get() >= 3; // cancel while writing the 3rd chapter
            }

            @Override
            public void onChapter(int index, int count, String title) {
                chapters.incrementAndGet();
            }
        });
        assertEquals(M4bSplitter.Outcome.Kind.CANCELLED, r.outcome.kind);
        assertUntouched(r);
    }

    // ------------------------------------------------------------------------------ io

    /** /data/local/tmp is readable by the shell, not by the app: stream it through UiAutomation. */
    private static boolean copyFromShell(String path, File to) throws Exception {
        ParcelFileDescriptor pfd = InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .executeShellCommand("cat " + path);
        long n = 0;
        try (InputStream in = new ParcelFileDescriptor.AutoCloseInputStream(pfd);
                OutputStream out = new FileOutputStream(to)) {
            byte[] b = new byte[256 * 1024];
            int r;
            while ((r = in.read(b)) > 0) {
                out.write(b, 0, r);
                n += r;
            }
        }
        return n > 0;
    }

    private static byte[] sha256(File f) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new FileInputStream(f)) {
            byte[] b = new byte[256 * 1024];
            int r;
            while ((r = in.read(b)) > 0)
                md.update(b, 0, r);
        }
        return md.digest();
    }

    private static void deleteRecursively(File f) {
        File[] kids = f.listFiles();
        if (kids != null)
            for (File k : kids)
                deleteRecursively(k);
        //noinspection ResultOfMethodCallIgnored
        f.delete();
    }
}
