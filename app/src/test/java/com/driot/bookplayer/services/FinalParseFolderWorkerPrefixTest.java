package com.driot.bookplayer.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class FinalParseFolderWorkerPrefixTest {

    @Test
    public void testHasNumericPrefix() {
        // Hyphens/Spaces (Requested by user logs: "00 - I. Introductory.aac")
        assertTrue(FinalParseFolderWorker.hasNumericPrefix("00 - I. Introductory.aac"));
        assertTrue(FinalParseFolderWorker.hasNumericPrefix("01 - title.mp3"));

        // Underscores (Existing support)
        assertTrue(FinalParseFolderWorker.hasNumericPrefix("001_title.mp3"));

        // Should NOT match if no separator
        assertFalse(FinalParseFolderWorker.hasNumericPrefix("01title.mp3"));

        // Should NOT match if separator is too far
        assertFalse(FinalParseFolderWorker.hasNumericPrefix("00000_tooLong.mp3"));
    }

    @Test
    public void testExtractNumericPrefix() {
        assertEquals(Integer.valueOf(0), FinalParseFolderWorker.extractNumericPrefix("00 - I. Introductory.aac"));
        assertEquals(Integer.valueOf(1), FinalParseFolderWorker.extractNumericPrefix("01 - title.mp3"));
        assertEquals(Integer.valueOf(123), FinalParseFolderWorker.extractNumericPrefix("123_title.mp3"));
        assertEquals(null, FinalParseFolderWorker.extractNumericPrefix("NoPrefix.mp3"));
    }
}
