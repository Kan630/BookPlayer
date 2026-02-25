package com.driot.bookplayer.objects;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.driot.bookplayer.utils.Tonio;
import org.junit.Test;

public class AudioMetadataTitleTest {

    @Test
    public void testIsBetterTitle() {
        // Better: Metadata has more info
        assertTrue(Tonio.isBetterTitle("Chapter 1: The Beginning", "01_chap1"));
        assertTrue(Tonio.isBetterTitle("Introduction - A Hero's Journey", "intro"));

        // Better: Filename has underscores, metadata is clean
        assertTrue(Tonio.isBetterTitle("Chapter One", "chapter_01"));

        // Not Better: Same content
        assertFalse(Tonio.isBetterTitle("Chapter 1", "Chapter 1"));
        assertFalse(Tonio.isBetterTitle("chapter 1", "CHAPTER 1"));

        // Not Better: Metadata is just a number
        assertFalse(Tonio.isBetterTitle("1", "Chapter 1"));
        assertFalse(Tonio.isBetterTitle("01", "01_intro"));

        // Not Better: Filename is more descriptive than empty/null metadata
        assertFalse(Tonio.isBetterTitle("", "Chapter 1"));
        assertFalse(Tonio.isBetterTitle(null, "Chapter 1"));

        // Not Better: Metadata is too short/generic
        assertFalse(Tonio.isBetterTitle("Ch1", "Chapter 1"));
    }

    @Test
    public void testFormatNameForDisplay() {
        assertEquals("[001] - introduction", Tonio.formatNameForDisplay("001_introduction.mp3", true));
        assertEquals("Chapter 1", Tonio.formatNameForDisplay("Chapter_1.mp3", true));
        assertEquals("01 intro", Tonio.formatNameForDisplay("01_intro.mp3", true));
    }
}
