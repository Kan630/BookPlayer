package com.driot.bookplayer.services.m4b;

import static com.driot.bookplayer.services.m4b.Mp4TestFiles.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.ByteBuffer;

public class Mp4ChapterReaderTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private static final String[] TITLES = { "Opening Credits", "Chapter 1 – L'été", "Chapitre 2" };
    private static final long[] DURS = { 5_000, 600_000, 594_000 };

    @Test
    public void chapterTrack_titlesAndTimes() throws Exception {
        File f = write(tmp.getRoot(), "a.m4b", withChapterTrack(TITLES, DURS, 1_199_000, null, null));
        Mp4ChapterReader.Result r = Mp4ChapterReader.read(f);

        assertEquals(Mp4ChapterReader.SOURCE_CHAPTER_TRACK, r.source);
        assertEquals(1_199_000, r.audioDurationMs);
        assertEquals(3, r.chapters.size());
        assertEquals("Opening Credits", r.chapters.get(0).title);
        assertEquals("Chapter 1 – L'été", r.chapters.get(1).title); // UTF-8, "encd" atom not part of the title
        assertEquals(0, r.chapters.get(0).startMs);
        assertEquals(5_000, r.chapters.get(1).startMs);
        assertEquals(605_000, r.chapters.get(2).startMs);
        assertEquals(594_000, r.chapters.get(2).durationMs);
        assertNull(r.problem);
    }

    /** What makes mp4parser throw "getChunkOffsetBox() on a null object": must not stop this reader. */
    @Test
    public void brokenExtraVideoTrack_stillReadsChapters() throws Exception {
        File f = write(tmp.getRoot(), "b.m4b", withChapterTrack(TITLES, DURS, 1_199_000, brokenVideoTrak(), null));
        Mp4ChapterReader.Result r = Mp4ChapterReader.read(f);
        assertEquals(3, r.chapters.size());
        assertEquals("Chapitre 2", r.chapters.get(2).title);
        assertTrue(r.boxMap, r.boxMap.contains("hdlr:vide"));
    }

    @Test
    public void chplOnly() throws Exception {
        byte[] udta = udtaWithChpl(new String[] { "Intro", "Part One", "Part Two" }, new long[] { 0, 60_000, 3_600_000 });
        File f = write(tmp.getRoot(), "c.m4b", audioOnly(7_200_000, udta));
        Mp4ChapterReader.Result r = Mp4ChapterReader.read(f);

        assertEquals(Mp4ChapterReader.SOURCE_CHPL, r.source);
        assertEquals(3, r.chapters.size());
        assertEquals("Part One", r.chapters.get(1).title);
        assertEquals(60_000, r.chapters.get(1).startMs);
        assertEquals(3_540_000, r.chapters.get(1).durationMs);
        assertEquals(3_600_000, r.chapters.get(2).durationMs); // last one runs to the audio end
    }

    @Test
    public void chapterTrackPreferredOverChpl() throws Exception {
        byte[] udta = udtaWithChpl(new String[] { "X", "Y" }, new long[] { 0, 1_000 });
        File f = write(tmp.getRoot(), "d.m4b", withChapterTrack(TITLES, DURS, 1_199_000, null, udta));
        assertEquals(Mp4ChapterReader.SOURCE_CHAPTER_TRACK, Mp4ChapterReader.read(f).source);
    }

    @Test
    public void noChapters_emptyButNoThrow() throws Exception {
        File f = write(tmp.getRoot(), "e.m4b", audioOnly(60_000, null));
        Mp4ChapterReader.Result r = Mp4ChapterReader.read(f);
        assertTrue(r.chapters.isEmpty());
        assertNull(r.source);
        assertEquals(60_000, r.audioDurationMs);
    }

    /** "I don't know how to deal with UInt64": a 64-bit box size above Long.MAX_VALUE after the moov. */
    @Test
    public void hugeLargeSizeBox_isReportedNotThrown() throws Exception {
        ByteBuffer bad = ByteBuffer.allocate(16);
        bad.putInt(1).put("free".getBytes()).putLong(0xFFFF_FFFF_FFFF_FFF0L);
        byte[] data = cat(withChapterTrack(TITLES, DURS, 1_199_000, null, null), bad.array());
        File f = write(tmp.getRoot(), "f.m4b", data);
        Mp4ChapterReader.Result r = Mp4ChapterReader.read(f);
        assertEquals(3, r.chapters.size());
        assertNotNull(r.problem);
        assertTrue(r.problem, r.problem.contains("free"));
    }

    /** Seen in a real audiobook: one stray byte before "ftyp". mp4parser loops forever on it. */
    @Test
    public void leadingJunkByte_noMoov_soTheSplitterSkipsMp4parser() throws Exception {
        byte[] ok = withChapterTrack(TITLES, DURS, 1_199_000, null, null);
        byte[] junk = cat(new byte[] { 0x0A }, ok);
        Mp4ChapterReader.Result r = Mp4ChapterReader.read(write(tmp.getRoot(), "j.m4b", junk));
        assertTrue(r.chapters.isEmpty());
        assertNotNull(r.problem);
        assertTrue(r.boxMap, !M4bSplitter.hasBox(r.boxMap, "moov"));
        assertTrue(M4bSplitter.hasBox(Mp4ChapterReader.read(write(tmp.getRoot(), "k.m4b", ok)).boxMap, "moov"));
    }

    @Test
    public void garbageFile() throws Exception {
        File f = write(tmp.getRoot(), "g.m4b", "not an mp4 at all, just text".getBytes());
        Mp4ChapterReader.Result r = Mp4ChapterReader.read(f);
        assertTrue(r.chapters.isEmpty());
        assertNotNull(r.problem);
    }

    @Test
    public void missingFile() {
        Mp4ChapterReader.Result r = Mp4ChapterReader.read(new File(tmp.getRoot(), "nope.m4b"));
        assertTrue(r.chapters.isEmpty());
        assertNotNull(r.problem);
    }
}
