package com.driot.bookplayer.ebooks;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

public class DocxChapterParsingTest {

    @Test
    public void testRefinedBehavior_ReducedChapters() {
        // h1=1, h2=2, h3=1 -> bestTag=h2
        String html = "<h1>Title</h1>" +
                "<p>Some text that is long enough to avoid immediate merging.</p>" +
                "<h2>Chapter 1</h2>" +
                "<p>Chapter 1 text. This needs to be long enough so it doesn't merge. Let's add some more text here. Blah blah blah blah blah blah blah blah blah blah blah blah.</p>"
                +
                "<h3>Section 1.1</h3>" +
                "<p>Section 1.1 text</p>" +
                "<h2>Chapter 2</h2>" +
                "<p>Chapter 2 text</p>";

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
                "<h4>Chapter 1</h4><p>Text 1. This needs to be long enough. Blah blah blah blah blah blah blah blah blah blah.</p>"
                +
                "<h4>Chapter 2</h4><p>Text 2. This needs to be long enough. Blah blah blah blah blah blah blah blah blah blah.</p>";

        List<DocxLowLevelHelper.Chapter> chapters = DocxLowLevelHelper.parseChapters(html, true);
        assertEquals(2, chapters.size());
        assertEquals("Chapter 1", chapters.get(0).title);
        assertEquals("Chapter 2", chapters.get(1).title);
    }

    @Test
    public void testSpecialKeywords() {
        String html = "<h1>Book Title</h1>" +
                "<p>Introduction</p>" +
                "<p>Intro text...</p>" +
                "<h2>Chapter 1</h2>" +
                "<p>Content...</p>" +
                "<p>End</p>" +
                "<p>The end text.</p>";

        List<DocxLowLevelHelper.Chapter> chapters = DocxLowLevelHelper.parseChapters(html, true);

        // Even if bestTag is h2 (h1=1, h2=1), "Introduction" and "End" should trigger
        // splits
        // if they are recognized as headings or if they are standalone tags.
        // Wait, in my current implementation, special keywords ONLY trigger split if
        // they are the text of the element.

        // Let's check for "Introduction"
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
        String html = "<h1>Chapter 1</h1><p>Text 1</p><h1>Chapter 2</h1><p>Text 2</p>";
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
        assertEquals("L'été à Paris", DocxLowLevelHelper.safe("L'été à Paris"));
        assertEquals("Title_Subtitle", DocxLowLevelHelper.safe("Title: Subtitle"));
        assertEquals("Mémoire_de_fin_d'études", DocxLowLevelHelper.safe("Mémoire/de fin d'études"));
    }

    @Test
    public void testSafeSlugSanitization() {
        assertEquals("l'été-à-paris", DocxLowLevelHelper.safeSlug("L'été à Paris"));
        assertEquals("chapitre-1-introduction", DocxLowLevelHelper.safeSlug("Chapitre 1: Introduction"));
        assertEquals("mémoire-de-recherche", DocxLowLevelHelper.safeSlug("Mémoire de recherche"));
    }
}
