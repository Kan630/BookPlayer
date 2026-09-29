package com.driot.bookplayer.services.m4b;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class ChapterPlanAndAacTest {

    private static Mp4ChapterReader.Chapter ch(String t, long s, long d) {
        return new Mp4ChapterReader.Chapter(t, s, d);
    }

    // ---------------------------------------------------------------- ChapterPlan: accepted

    @Test
    public void segmentsCoverTheWholeAudio() {
        List<Mp4ChapterReader.Chapter> c = Arrays.asList(ch("a", 2_000, 98_000), ch("b", 100_000, 99_000));
        ChapterPlan p = ChapterPlan.of(c, 200_000);
        assertTrue(p.rejectReason, p.ok());
        assertEquals(0, p.segments.get(0).startMs); // audio before chapter 1 kept in chapter 1
        assertEquals(100_000, p.segments.get(0).endMs);
        assertEquals(100_000, p.segments.get(1).startMs);
        assertEquals(200_000, p.segments.get(1).endMs); // trailing 1 s kept in the last chapter
    }

    // ---------------------------------------------------------------- ChapterPlan: refused (book stays unsplit)

    private static void refused(String why, List<Mp4ChapterReader.Chapter> c, long audioMs) {
        ChapterPlan p = ChapterPlan.of(c, audioMs);
        assertFalse(why, p.ok());
        assertTrue(p.segments.isEmpty());
        assertNotNull(p.rejectReason);
    }

    @Test
    public void refusals() {
        refused("single chapter", Arrays.asList(ch("a", 0, 10_000)), 10_000);
        refused("no chapters", Arrays.asList(), 10_000);
        refused("unknown duration", Arrays.asList(ch("a", 0, 5), ch("b", 5, 5)), -1);
        refused("not increasing", Arrays.asList(ch("a", 0, 5_000), ch("b", 5_000, 5_000), ch("c", 4_000, 5_000)), 20_000);
        refused("duplicate start", Arrays.asList(ch("a", 0, 5_000), ch("b", 0, 5_000)), 20_000);
        refused("starts after end", Arrays.asList(ch("a", 0, 5_000), ch("b", 30_000, 5_000)), 20_000);
        refused("negative start", Arrays.asList(ch("a", -5, 5_000), ch("b", 5_000, 5_000)), 20_000);
        // timeline 10x the audio: wrong timescale
        refused("timeline too long", Arrays.asList(ch("a", 0, 1_000_000), ch("b", 1_000_000, 1_000_000)), 1_000_000 + 10);
        // chapters only cover a tiny part: suspicious
        refused("timeline too short", Arrays.asList(ch("a", 0, 1_000), ch("b", 1_000, 1_000)), 3_600_000);
    }

    @Test
    public void smallTimelineOverrunIsTolerated() {
        // encoder padding: chapters end 3 s after the audio track's declared duration
        ChapterPlan p = ChapterPlan.of(Arrays.asList(ch("a", 0, 60_000), ch("b", 60_000, 63_000)), 120_000);
        assertTrue(p.rejectReason, p.ok());
        assertEquals(120_000, p.segments.get(1).endMs);
    }

    // ---------------------------------------------------------------- AacConfig

    @Test
    public void lcFromCsd() {
        AacConfig c = AacConfig.fromAudioSpecificConfig(new byte[] { 0x12, 0x10 }); // AAC-LC 44.1 kHz stereo
        assertNotNull(c);
        assertEquals(2, c.objectType);
        assertEquals(4, c.samplingFrequencyIndex);
        assertEquals(2, c.channelConfiguration);
        assertEquals(44100, c.sampleRate());
    }

    @Test
    public void explicitHeAac_writtenAsLcCore() {
        // aot 5 (SBR), sfi 6 (24 kHz core), mono, ext sfi 3 (48 kHz), core aot 2
        // bits: 00101 0110 0001 0011 00010 -> 0x2B 0x09 0x88 0x00 (padded)
        AacConfig c = AacConfig.fromAudioSpecificConfig(new byte[] { 0x2B, 0x09, (byte) 0x88, 0x00 });
        assertNotNull(c);
        assertEquals(2, c.objectType); // ADTS profile field fits (the old code wrote 5-1 = 4: overflow)
        assertEquals(6, c.samplingFrequencyIndex);
        assertEquals(1, c.channelConfiguration);
    }

    @Test
    public void unsupportedConfigs() {
        assertNull(AacConfig.of(23, 4, 2)); // AAC-LD: not ADTS
        assertNull(AacConfig.of(2, 13, 2)); // reserved frequency index
        assertNull(AacConfig.of(2, 4, 0)); // channel config in the PCE: not handled
        assertNull(AacConfig.fromAudioSpecificConfig(new byte[] { 0x12 }));
    }

    @Test
    public void adtsHeaderMatchesTheOriginalImplementation() {
        AacConfig c = AacConfig.of(2, 4, 2);
        // same bytes the previous M4bSplitWorker.buildAdtsHeader produced for AAC-LC (unchanged for healthy files)
        assertArrayEquals(new byte[] { (byte) 0xFF, (byte) 0xF1, (byte) 0x50, (byte) 0x80, (byte) 0x2E, (byte) 0x7F,
                (byte) 0xFC }, c.adtsHeader(364));
    }
}
