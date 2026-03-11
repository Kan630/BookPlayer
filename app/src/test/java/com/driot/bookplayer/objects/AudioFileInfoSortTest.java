package com.driot.bookplayer.objects;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class AudioFileInfoSortTest {

    @Test
    public void testSmartChapterComparatorWithZero() {
        AudioFileInfo a0 = new AudioFileInfo("00 - I. Introductory.aac", 1000, 1000, "uri0", null);
        AudioFileInfo a1 = new AudioFileInfo("01 - II. Elementary Rules.aac", 1000, 1000, "uri1", null);

        // a0 should come before a1
        int cmp = AudioFileInfo.SMART_CHAPTER_COMPARATOR.compare(a0, a1);
        assertTrue("00 should come before 01", cmp < 0);

        List<AudioFileInfo> list = new ArrayList<>();
        list.add(a1);
        list.add(a0);
        list.sort(AudioFileInfo.SMART_CHAPTER_COMPARATOR);

        assertEquals("00 - I. Introductory.aac", list.get(0).getDisplayPath());
        assertEquals("01 - II. Elementary Rules.aac", list.get(1).getDisplayPath());
    }

    @Test
    public void testAlphanumericComparatorWithZero() {
        // Alphanumeric should also handle this naturally, but SMART_CHAPTER is what's
        // used
        AudioFileInfo a0 = new AudioFileInfo("00 - I. Introductory.aac", 1000, 1000, "uri0", null);
        AudioFileInfo a1 = new AudioFileInfo("01 - II. Elementary Rules.aac", 1000, 1000, "uri1", null);

        int cmp = AudioFileInfo.ALPHANUMERIC_COMPARATOR.compare(a0, a1);
        assertTrue("00 should come before 01 in alphanumeric too", cmp < 0);
    }
}
