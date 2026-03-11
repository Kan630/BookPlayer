package com.driot.bookplayer;

import static org.junit.Assert.assertEquals;
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

        // Empty/null cases
        assertEquals("untitled", FileHelper.sanitizeFilename(""));
        assertEquals(null, FileHelper.sanitizeFilename(null));

        // Long filenames (should be truncated to 60)
        String longTitle = "This is a very very very very very very very very very very very very very long title";
        String sanitized = FileHelper.sanitizeFilename(longTitle);
        assertEquals(60, sanitized.length());
        assertEquals(longTitle.substring(0, 60), sanitized);
    }
}
