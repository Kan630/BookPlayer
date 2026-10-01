package com.driot.bookplayer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.helpers.FileHelper;
import org.junit.Test;

public class FileHelperSanitizeTest {

    @Test
    public void testSanitizeFilename() {
        // Basic colon replacement
        assertEquals("01 - Chapter_ Title", FileHelper.sanitizeFilename("01 - Chapter: Title"));

        // Multiple forbidden characters
        assertEquals("Title_with_slash_and_colon", FileHelper.sanitizeFilename("Title/with/slash:and:colon"));

        // Windows forbidden characters: \ / : * ? " < > |
        assertEquals("sanitized_________", FileHelper.sanitizeFilename("sanitized\\/:*?\"<>|"));

        // Leading/trailing whitespace
        assertEquals("Clean Title", FileHelper.sanitizeFilename("  Clean Title  "));

        // FAT/exFAT SD cards refuse a trailing space and drop a trailing dot ("Book [00] .m4b" -> "Book [00] ")
        assertEquals("Anthem by Ayn Rand [00]", FileHelper.sanitizeFilename("Anthem by Ayn Rand [00] "));
        assertEquals("Vol. 2", FileHelper.sanitizeFilename("Vol. 2..."));
        assertEquals("a_b", FileHelper.sanitizeFilename("a\tb"));
        assertEquals("untitled", FileHelper.sanitizeFilename(" . "));

        // Empty/null cases
        assertEquals("untitled", FileHelper.sanitizeFilename(""));
        assertEquals(null, FileHelper.sanitizeFilename(null));

        // Long filenames (should be truncated to FILE_NAME_MAX_NB_CHARS)
        String longTitle = "This is a very very very very very very very very very very very very very long title".repeat(3);
        String sanitized = FileHelper.sanitizeFilename(longTitle);
        // (the cut lands on a space here, which is then stripped like any trailing space)
        assertEquals(longTitle.substring(0, Var.FILE_NAME_MAX_NB_CHARS).trim(), sanitized);
        assertTrue(sanitized.length() <= Var.FILE_NAME_MAX_NB_CHARS);
    }
}
