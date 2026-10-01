package com.driot.bookplayer.ebooks;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Map;

public class EpubTocLabelsTest {

    private static final String NCX = "<?xml version='1.0' encoding='utf-8'?>"
            + "<ncx xmlns=\"http://www.daisy.org/z3986/2005/ncx/\" version=\"2005-1\"><navMap>"
            + "<navPoint id=\"a\" playOrder=\"1\"><navLabel><text>The Hobbit</text></navLabel><content src=\"title.html\"/></navPoint>"
            + "<navPoint id=\"b\" playOrder=\"2\"><navLabel><text>Chapter I -\n   An Unexpected Party</text></navLabel>"
            + "<content src=\"text/ch%2001.html#start\"/>"
            + "<navPoint id=\"b1\" playOrder=\"3\"><navLabel><text>Tolkien sings</text></navLabel><content src=\"text/ch%2001.html#song\"/></navPoint>"
            + "<navPoint id=\"b2\" playOrder=\"4\"><navLabel><text>Audio page</text></navLabel><content src=\"text/audio1.html\"/></navPoint>"
            + "</navPoint>"
            + "<navPoint id=\"c\" playOrder=\"5\"><navLabel><text>Chapter II - Roast Mutton</text></navLabel><content src=\"../ch02.html\"/></navPoint>"
            + "</navMap></ncx>";

    @Test
    public void ncx_firstLabelPerFile_relativeToTheNcx_fragmentAndEncodingRemoved() {
        Map<String, String> labels = EpubTocLabels.parse(NCX, "OEBPS/toc.ncx");
        assertEquals("The Hobbit", labels.get("OEBPS/title.html"));
        // own label, not the nested one that points into the same file; whitespace collapsed; %20 decoded
        assertEquals("Chapter I - An Unexpected Party", labels.get("OEBPS/text/ch 01.html"));
        assertEquals("Audio page", labels.get("OEBPS/text/audio1.html"));
        assertEquals("Chapter II - Roast Mutton", labels.get("ch02.html"));
        assertEquals(4, labels.size());
        // TOC order is kept
        assertEquals("OEBPS/title.html", new ArrayList<>(labels.keySet()).get(0));
    }

    @Test
    public void epub3Nav_usesTheTocNav_andSkipsPageNumbers() {
        String nav = "<html xmlns:epub=\"http://www.idpf.org/2007/ops\"><body>"
                + "<nav epub:type=\"toc\"><ol>"
                + "<li><a href=\"c1.xhtml\">One</a></li>"
                + "<li><a href=\"c2.xhtml#x\">Two</a><ol><li><a href=\"c2.xhtml#y\">Two bis</a></li></ol></li>"
                + "<li><a href=\"c3.xhtml\">[12]</a></li>"
                + "</ol></nav>"
                + "<nav epub:type=\"page-list\"><ol><li><a href=\"c9.xhtml\">9</a></li></ol></nav>"
                + "</body></html>";
        Map<String, String> labels = EpubTocLabels.parse(nav, "nav.xhtml");
        assertEquals("One", labels.get("c1.xhtml"));
        assertEquals("Two", labels.get("c2.xhtml"));
        assertEquals(2, labels.size());
    }

    @Test
    public void emptyOrGarbage_givesNoLabels() {
        assertTrue(EpubTocLabels.parse(null, "toc.ncx").isEmpty());
        assertTrue(EpubTocLabels.parse("", "toc.ncx").isEmpty());
        assertTrue(EpubTocLabels.parse("not a toc at all", "toc.ncx").isEmpty());
    }
}
