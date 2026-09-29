package com.driot.bookplayer.services.m4b;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.googlecode.mp4parser.FileDataSourceImpl;
import com.googlecode.mp4parser.authoring.Movie;
import com.googlecode.mp4parser.authoring.Sample;
import com.googlecode.mp4parser.authoring.Track;
import com.googlecode.mp4parser.authoring.container.mp4.MovieCreator;

import org.junit.Test;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Cross-check on real audiobooks: wherever mp4parser (the current split path) reads chapters, Mp4ChapterReader must
 * find the same count, starts and titles. Runs on the dev machine only: set BP_M4B_DIRS (':'-separated folders,
 * searched recursively), default /mnt/sda1/AudioBooks; skipped when none exists.
 */
public class Mp4ChapterReaderRealFilesTest {

    private static final long MAX_BYTES = 600L * 1024 * 1024;

    private static List<File> m4bFiles() {
        String dirs = System.getenv("BP_M4B_DIRS");
        if (dirs == null || dirs.isEmpty())
            dirs = "/mnt/sda1/AudioBooks";
        List<File> out = new ArrayList<>();
        for (String d : dirs.split(":"))
            collect(new File(d), out);
        return out;
    }

    private static void collect(File f, List<File> out) {
        if (f.isDirectory()) {
            File[] kids = f.listFiles();
            if (kids != null)
                for (File k : kids)
                    collect(k, out);
        } else if (f.getName().toLowerCase().endsWith(".m4b") && f.length() < MAX_BYTES) {
            out.add(f); // mp4parser loads whole sample tables: a 1.2 GB book takes ages in a test JVM
        }
    }

    @Test
    public void sameChaptersAsMp4parser() throws Exception {
        List<File> files = m4bFiles();
        assumeTrue("no real m4b files on this machine", !files.isEmpty());

        int compared = 0, mp4parserFailed = 0, rescued = 0;
        StringBuilder report = new StringBuilder();
        java.io.PrintWriter progress = new java.io.PrintWriter(new java.io.FileWriter(
                new File(System.getProperty("java.io.tmpdir"), "m4b_crosscheck_progress.txt")), true);
        for (File f : files) {
            progress.println(System.currentTimeMillis() + " " + f.length() / 1_000_000 + " MB " + f.getName());
            Mp4ChapterReader.Result mine = Mp4ChapterReader.read(f);
            if (!M4bSplitter.hasBox(mine.boxMap, "moov")) {
                // same guard as M4bSplitter: mp4parser loops forever on such files
                report.append("  unreadable MP4 (no moov), mp4parser skipped: ").append(f.getName()).append(" | ")
                        .append(mine.problem).append('\n');
                continue;
            }
            List<long[]> ref = new ArrayList<>(); // {startMs}
            List<String> refTitles = new ArrayList<>();
            try (FileDataSourceImpl ds = new FileDataSourceImpl(f)) {
                Movie movie = MovieCreator.build(ds);
                Track chapters = null;
                for (Track t : movie.getTracks())
                    if ("text".equals(t.getHandler()) || "sbtl".equals(t.getHandler()))
                        chapters = t;
                if (chapters == null) {
                    report.append("  no mp4parser chapter track: ").append(f.getName()).append(" -> mine: ")
                            .append(mine.chapters.size()).append(" from ").append(mine.source).append('\n');
                    continue;
                }
                long ts = chapters.getTrackMetaData().getTimescale();
                long t0 = 0;
                long[] durs = chapters.getSampleDurations();
                List<Sample> samples = chapters.getSamples();
                for (int i = 0; i < samples.size(); i++) {
                    ref.add(new long[] { t0 * 1000 / ts });
                    refTitles.add(title(samples.get(i)));
                    t0 += durs[i];
                }
            } catch (Throwable t) {
                mp4parserFailed++;
                if (mine.chapters.size() >= 2)
                    rescued++;
                report.append("  mp4parser FAILED (").append(t.getClass().getSimpleName()).append(") on ")
                        .append(f.getName()).append(" -> mine: ").append(mine.chapters.size()).append(" chapters from ")
                        .append(mine.source).append(" | ").append(mine.boxMap).append('\n');
                continue;
            }

            String where = f.getName();
            assertEquals(where + " chapter count", ref.size(), mine.chapters.size());
            for (int i = 0; i < ref.size(); i++) {
                assertEquals(where + " start of chapter " + (i + 1), ref.get(i)[0], mine.chapters.get(i).startMs);
                assertEquals(where + " title of chapter " + (i + 1), refTitles.get(i), mine.chapters.get(i).title);
            }
            assertTrue(where + " audio duration", mine.audioDurationMs > 0);
            compared++;
        }
        System.out.println("Mp4ChapterReader vs mp4parser: " + files.size() + " files, " + compared
                + " identical, mp4parser failed on " + mp4parserFailed + " (" + rescued + " rescued)\n" + report);
        assertTrue("nothing compared", compared > 0);
    }

    /** Same raw text as the chapter reader (length-prefixed), cleaned the same way. */
    private static String title(Sample s) {
        ByteBuffer b = s.asByteBuffer();
        byte[] d = new byte[b.remaining()];
        b.get(d);
        if (d.length < 2)
            return "";
        int len = Math.min(((d[0] & 0xFF) << 8) | (d[1] & 0xFF), d.length - 2);
        String raw;
        if (len >= 2 && (d[2] & 0xFF) == 0xFE && (d[3] & 0xFF) == 0xFF)
            raw = new String(d, 4, len - 2, StandardCharsets.UTF_16BE);
        else if (len >= 2 && (d[2] & 0xFF) == 0xFF && (d[3] & 0xFF) == 0xFE)
            raw = new String(d, 4, len - 2, StandardCharsets.UTF_16LE);
        else
            raw = new String(d, 2, len, StandardCharsets.UTF_8);
        return Mp4ChapterReader.cleanTitle(raw);
    }
}
