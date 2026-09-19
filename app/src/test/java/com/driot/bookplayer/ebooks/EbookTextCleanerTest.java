package com.driot.bookplayer.ebooks;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Reference/footnote stripping feeds straight into TTS, so leftovers get read aloud. */
public class EbookTextCleanerTest {

    @Test
    public void nullAndEmptyPassThrough() {
        assertNull(EbookTextCleaner.removeReferences(null));
        assertEquals("", EbookTextCleaner.removeReferences(""));
    }

    @Test
    public void inTextMarkersAreRemoved() {
        assertEquals("The quick fox jumped.", EbookTextCleaner.removeReferences("The quick[1] fox[12] jumped.[3]"));
    }

    @Test
    public void namedMarkersAreRemovedCaseInsensitively() {
        String out = EbookTextCleaner.removeReferences("Alpha[Note 2] beta[footnote 10] gamma[ENDNOTE 3].");
        assertEquals("Alpha beta gamma.", out);
    }

    @Test
    public void footnoteBlocksAtEndAreRemoved() {
        String text = "Real paragraph.[1]\n\n[1] : This is the explanation.\n[2] Another note line.";
        String out = EbookTextCleaner.removeReferences(text);
        assertEquals("Real paragraph.", out);
    }

    @Test
    public void everyFootnoteInAListIsRemoved_notJustTheLast() {
        String text = "Body.[1][2][3]\n\n[1] First note.\n[2] Second note.\n[3] Third note.";
        assertEquals("Body.", EbookTextCleaner.removeReferences(text));
    }

    @Test
    public void bodyTextAfterAFootnoteLineIsPreserved() {
        String text = "Intro.\n[1] A note in the middle\nMore body text.";
        assertEquals("Intro.\nMore body text.", EbookTextCleaner.removeReferences(text));
    }

    @Test
    public void nonNumericBracketsAreKept() {
        String text = "He said [sic] and then [laughs] left.";
        assertEquals(text, EbookTextCleaner.removeReferences(text));
    }

    @Test
    public void bracketsWithMixedContentAreKept() {
        // "[1984]" is a bare number in brackets and IS matched by design; a range/list is not
        String text = "See [1-3] and [a1].";
        assertEquals(text, EbookTextCleaner.removeReferences(text));
    }

    @Test
    public void multipleBlankLinesAreCollapsed() {
        String out = EbookTextCleaner.removeReferences("One[1]\n\n\n\n\nTwo");
        assertEquals("One\n\nTwo", out);
    }

    @Test
    public void resultIsTrimmed() {
        String out = EbookTextCleaner.removeReferences("   \n Body text[4] \n\n");
        assertFalse(out.startsWith(" "));
        assertFalse(out.endsWith("\n"));
        assertTrue(out.contains("Body text"));
    }
}
