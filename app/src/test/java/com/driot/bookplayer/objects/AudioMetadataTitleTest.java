package com.driot.bookplayer.objects;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.driot.bookplayer.utils.Tonio;
import org.junit.Test;

public class AudioMetadataTitleTest {

    @Test
    public void testIsBetterTitle() {
        // Cases from user:
        assertTrue(Tonio.isBetterTitle("14 - A young Rajah", "secretgarden 14 burnett 64kb", true));
        assertTrue(Tonio.isBetterTitle("26 - It's Mother!", "secretgarden 26 burnett 64kb", true));
        assertTrue(Tonio.isBetterTitle("Chapter 016", "mobydick 016 melville 64kb", true));
        assertTrue(Tonio.isBetterTitle("01 - Bk. II, Ch VII, Pt. 1, Completion of the circumnavigation of New Zealand",
                "firstvoyagejamescookvol2 01 cook 64kb", true));
        assertTrue(Tonio.isBetterTitle("02 - Bk. II, Ch VII, Pt. 2", "firstvoyagejamescookvol2 02 cook 64kb", true));

        // Generic better cases
        assertTrue(Tonio.isBetterTitle("Chapter One", "chapter_01", false));

        // Not Better: Same content
        assertFalse(Tonio.isBetterTitle("Chapter 1", "Chapter 1", false));
        assertFalse(Tonio.isBetterTitle("chapter 1", "CHAPTER 1", false));

        // Not Better: Metadata is just a number
        assertFalse(Tonio.isBetterTitle("1", "Chapter 1", true));
        assertFalse(Tonio.isBetterTitle("01", "01_intro", true));

        // Not Better: Filename is more descriptive than empty/null metadata
        assertFalse(Tonio.isBetterTitle("", "Chapter 1", false));
        assertFalse(Tonio.isBetterTitle(null, "Chapter 1", false));

        // Cautious for non-librivox (it won't detect the "14 - " pattern as easily)
        // Actually, with the "m.length() > fClean.length() + 5" it might still detect
        // it if the filename is short.
        // But for secretgarden, the filename is long, so it should stay false if not
        // librivox.
        assertFalse(Tonio.isBetterTitle("14 - A young Rajah", "secretgarden 14 burnett 64kb", false));
    }

    @Test
    public void testFormatNameForDisplay() {
        assertEquals("[001] - introduction", Tonio.formatNameForDisplay("001_introduction.mp3", true));
        assertEquals("Chapter 1", Tonio.formatNameForDisplay("Chapter_1.mp3", true));
        assertEquals("01 intro", Tonio.formatNameForDisplay("01_intro.mp3", true));
    }
}
