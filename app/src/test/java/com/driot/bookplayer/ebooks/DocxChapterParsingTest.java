package com.driot.bookplayer.ebooks;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.driot.bookplayer.helpers.FileHelper;
import org.junit.Test;

import java.util.List;

public class DocxChapterParsingTest {

    @Test
    public void testRefinedBehavior_ReducedChapters() {
        // h1=1, h2=2, h3=1 -> bestTag=h2
        String html = "<h1>Title</h1>" +
                "<p>Some text that is long enough to avoid immediate merging.</p>" +
                "<h2>Chapter 1</h2>" +
                "<p>Chapter 1 text. This needs to be long enough so it doesn't merge. " + "a".repeat(200) + "</p>"
                +
                "<h3>Section 1.1</h3>" +
                "<p>Section 1.1 text</p>" +
                "<h2>Chapter 2</h2>" +
                "<p>Chapter 2 text. " + "b".repeat(200) + "</p>";

        List<DocxLowLevelHelper.Chapter> chapters = DocxLowLevelHelper.parseChapters(html, true);

        // Find which one is Chapter 1
        boolean foundCh1 = false;
        for (DocxLowLevelHelper.Chapter c : chapters) {
            if (c.title.equals("Chapter 1")) {
                foundCh1 = true;
                // Section 1.1 (h3) is NOT a chapter break because bestTag=h2
                assertTrue(c.buf.toString().contains("Section 1.1"));
            }
        }
        assertTrue(foundCh1);
    }

    @Test
    public void testH4Detection() {
        // No h1, h2, h3. Only h4s. bestTag should be h4.
        String html = "<p>Book Title</p>" +
                "<h4>Chapter 1</h4><p>Text 1. " + "a".repeat(200) + "</p>"
                +
                "<h4>Chapter 2</h4><p>Text 2. " + "b".repeat(200) + "</p>";

        List<DocxLowLevelHelper.Chapter> chapters = DocxLowLevelHelper.parseChapters(html, true);
        assertEquals(2, chapters.size());
        assertEquals("Chapter 1", chapters.get(0).title);
        assertEquals("Chapter 2", chapters.get(1).title);
    }

    @Test
    public void testSpecialKeywords() {
        String html = "<h1>Book Title</h1>" +
                "<p>Introduction</p>" +
                "<p>Intro text... " + "a".repeat(200) + "</p>" +
                "<h2>Chapter 1</h2>" +
                "<p>Content... " + "b".repeat(200) + "</p>" +
                "<p>End</p>" +
                "<p>The end text. " + "c".repeat(200) + "</p>";

        List<DocxLowLevelHelper.Chapter> chapters = DocxLowLevelHelper.parseChapters(html, true);

        // Even if bestTag is h2 (h1=1, h2=1), "Introduction" and "End" should trigger
        // splits
        boolean foundIntro = false;
        boolean foundEnd = false;
        for (DocxLowLevelHelper.Chapter c : chapters) {
            if (c.title.equalsIgnoreCase("Introduction") || c.title.equalsIgnoreCase("Intro"))
                foundIntro = true;
            if (c.title.equalsIgnoreCase("End"))
                foundEnd = true;
        }
        assertTrue(foundIntro);
        assertTrue(foundEnd);
    }

    @Test
    public void testH1FragmentationFix() {
        // Multiple h1s should all be chapters
        String html = "<h1>Chapter 1</h1><p>Text 1. " + "a".repeat(200) + "</p><h1>Chapter 2</h1><p>Text 2. "
                + "b".repeat(200) + "</p>";
        List<DocxLowLevelHelper.Chapter> chapters = DocxLowLevelHelper.parseChapters(html, true);
        assertEquals(2, chapters.size());
        assertEquals("Chapter 1", chapters.get(0).title);
        assertEquals("Chapter 2", chapters.get(1).title);
    }

    @Test
    public void testNoSplit() {
        String html = "<h1>Title</h1><p>Text</p>";
        List<DocxLowLevelHelper.Chapter> chapters = DocxLowLevelHelper.parseChapters(html, false);
        assertEquals(1, chapters.size());
        assertEquals("Full Document", chapters.get(0).title);
    }

    @Test
    public void testSafeSanitization() {
        assertEquals("L'été à Paris", FileHelper.sanitizeFilename("L'été à Paris"));
        assertEquals("Title_ Subtitle", FileHelper.sanitizeFilename("Title: Subtitle"));
        assertEquals("Mémoire_de fin d'études", FileHelper.sanitizeFilename("Mémoire/de fin d'études"));
        assertEquals("untitled", FileHelper.sanitizeFilename("   "));
        assertEquals("a".repeat(60), FileHelper.sanitizeFilename("a".repeat(70)));
    }

    @Test
    public void testSafeSlugSanitization() {
        assertEquals("l-été-à-paris", FileHelper.sanitizeSlug("L'été à Paris"));
        assertEquals("chapitre-1-introduction", FileHelper.sanitizeSlug("Chapitre 1: Introduction"));
        assertEquals("mémoire-de-recherche", FileHelper.sanitizeSlug("Mémoire de recherche"));
        assertEquals("chapter", FileHelper.sanitizeSlug("   "));
        assertEquals("a".repeat(60), FileHelper.sanitizeSlug("a".repeat(70)));
    }
}
