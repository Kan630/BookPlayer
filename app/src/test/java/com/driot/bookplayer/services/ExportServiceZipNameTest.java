package com.driot.bookplayer.services;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

/** Zip export threw "ZipException: duplicate entry: cover.jpg" (Crashlytics): entry names must be unique. */
public class ExportServiceZipNameTest {

    @Test
    public void duplicatesGetANumberedSuffix() {
        Set<String> used = new HashSet<>();
        assertEquals("cover.jpg", ExportService.uniqueEntryName("cover.jpg", used));
        assertEquals("cover (2).jpg", ExportService.uniqueEntryName("cover.jpg", used));
        assertEquals("cover (3).jpg", ExportService.uniqueEntryName("cover.jpg", used));
        assertEquals("01.mp3", ExportService.uniqueEntryName("01.mp3", used));
    }

    @Test
    public void namesWithoutExtensionOrLeadingDot() {
        Set<String> used = new HashSet<>();
        assertEquals("README", ExportService.uniqueEntryName("README", used));
        assertEquals("README (2)", ExportService.uniqueEntryName("README", used));
        assertEquals(".nomedia", ExportService.uniqueEntryName(".nomedia", used));
        assertEquals(".nomedia (2)", ExportService.uniqueEntryName(".nomedia", used));
    }
}
