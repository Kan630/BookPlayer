package com.driot.bookplayer.helpers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.driot.bookplayer.global.Var;

import org.junit.Test;

/**
 * File-type classification decides what the import pipeline does with every file the user
 * picks, so a regression here silently breaks importing. Filename-only overloads (no Context).
 */
public class SupportedFilesHelperTest {

    @Test
    public void extension_isLowercasedAndTakenAfterLastDot() {
        assertEquals("mp3", SupportedFilesHelper.getFileExtension("Track 01.MP3"));
        assertEquals("gz", SupportedFilesHelper.getFileExtension("archive.tar.gz"));
        assertEquals("epub", SupportedFilesHelper.getFileExtension("a.b.c.EPUB"));
    }

    @Test
    public void extension_missingOrTrailingDotIsEmpty() {
        assertEquals("", SupportedFilesHelper.getFileExtension("README"));
        assertEquals("", SupportedFilesHelper.getFileExtension("weird."));
        assertEquals("", SupportedFilesHelper.getFileExtension(""));
        assertEquals("", SupportedFilesHelper.getFileExtension((String) null));
    }

    @Test
    public void getType_audio() {
        for (String n : new String[] { "a.mp3", "a.MP3", "a.m4a", "a.ogg", "a.flac", "a.opus" }) {
            assertEquals(n, SupportedFilesHelper.FILE_TYPE_AUDIO, SupportedFilesHelper.getType(n));
        }
    }

    @Test
    public void getType_ebooks() {
        for (String n : new String[] { "a.epub", "a.fb2", "a.odt", "a.docx", "a.txt", "a.html" }) {
            assertEquals(n, SupportedFilesHelper.FILE_TYPE_EBOOK, SupportedFilesHelper.getType(n));
        }
    }

    @Test
    public void getType_bundles() {
        for (String n : new String[] { "a.zip", "a.7z", "a.tar" }) {
            assertEquals(n, SupportedFilesHelper.FILE_TYPE_BUNDLE, SupportedFilesHelper.getType(n));
        }
    }

    @Test
    public void getType_images() {
        assertEquals(SupportedFilesHelper.FILE_TYPE_IMAGE, SupportedFilesHelper.getType("cover.jpg"));
        assertEquals(SupportedFilesHelper.FILE_TYPE_IMAGE, SupportedFilesHelper.getType("cover.PNG"));
    }

    @Test
    public void getType_unknownAndNullAreNull() {
        assertNull(SupportedFilesHelper.getType("notes.xyz123"));
        assertNull(SupportedFilesHelper.getType("noextension"));
        assertNull(SupportedFilesHelper.getType((String) null));
    }

    @Test
    public void specialType_mapsKnownExtensions() {
        assertEquals(SupportedFilesHelper.SPECIAL_TYPE_ZIP, SupportedFilesHelper.getSpecialType("b.zip"));
        assertEquals(SupportedFilesHelper.SPECIAL_TYPE_7Z, SupportedFilesHelper.getSpecialType("b.7z"));
        assertEquals(SupportedFilesHelper.SPECIAL_TYPE_M4B, SupportedFilesHelper.getSpecialType("b.M4B"));
        assertEquals(SupportedFilesHelper.SPECIAL_TYPE_EPUB, SupportedFilesHelper.getSpecialType("b.epub"));
        assertEquals(SupportedFilesHelper.SPECIAL_TYPE_FB2, SupportedFilesHelper.getSpecialType("b.fb2"));
        assertEquals(SupportedFilesHelper.SPECIAL_TYPE_ODT, SupportedFilesHelper.getSpecialType("b.odt"));
        assertEquals(SupportedFilesHelper.SPECIAL_TYPE_DOCX, SupportedFilesHelper.getSpecialType("b.docx"));
        assertEquals(SupportedFilesHelper.SPECIAL_TYPE_TXT, SupportedFilesHelper.getSpecialType("b.txt"));
        assertEquals(SupportedFilesHelper.SPECIAL_TYPE_HTML, SupportedFilesHelper.getSpecialType("b.htm"));
        assertEquals(SupportedFilesHelper.SPECIAL_TYPE_HTML, SupportedFilesHelper.getSpecialType("b.html"));
    }

    @Test
    public void specialType_allTarVariantsCollapseToTar() {
        for (String ext : new String[] { "tar", "tgz", "tbz2", "txz", "tar.bz2", "tar.xz" }) {
            assertEquals(ext, SupportedFilesHelper.SPECIAL_TYPE_TAR, SupportedFilesHelper.getSpecialTypeFromExt(ext));
        }
    }

    @Test
    public void specialType_plainAudioHasNone() {
        assertNull(SupportedFilesHelper.getSpecialType("a.mp3"));
        assertNull(SupportedFilesHelper.getSpecialType((String) null));
    }

    @Test
    public void isBookSupported_audioEbookBundleYes_imageAndUnknownNo() {
        assertTrue(SupportedFilesHelper.isBookSupported("a.mp3"));
        assertTrue(SupportedFilesHelper.isBookSupported("a.epub"));
        assertTrue(SupportedFilesHelper.isBookSupported("a.zip"));
        assertFalse(SupportedFilesHelper.isBookSupported("cover.jpg"));
        assertFalse(SupportedFilesHelper.isBookSupported("a.xyz123"));
        assertFalse(SupportedFilesHelper.isBookSupported((String) null));
    }

    @Test
    public void playType_textForEbooks_audioForAudioVideoBundle() {
        assertEquals(Var.PLAY_TYPE_TEXT, SupportedFilesHelper.getPlayType("a.epub"));
        assertEquals(Var.PLAY_TYPE_TEXT, SupportedFilesHelper.getPlayType("a.txt"));
        assertEquals(Var.PLAY_TYPE_AUDIO, SupportedFilesHelper.getPlayType("a.mp3"));
        assertEquals(Var.PLAY_TYPE_AUDIO, SupportedFilesHelper.getPlayType("a.zip"));
        assertNull(SupportedFilesHelper.getPlayType("cover.jpg"));
        assertNull(SupportedFilesHelper.getPlayType(null));
    }

    @Test
    public void groupHelpers() {
        assertTrue(SupportedFilesHelper.isBundleSpecial(SupportedFilesHelper.SPECIAL_TYPE_ZIP));
        assertTrue(SupportedFilesHelper.isBundleSpecial(SupportedFilesHelper.SPECIAL_TYPE_7Z));
        assertTrue(SupportedFilesHelper.isBundleSpecial(SupportedFilesHelper.SPECIAL_TYPE_TAR));
        assertFalse(SupportedFilesHelper.isBundleSpecial(SupportedFilesHelper.SPECIAL_TYPE_EPUB));
        assertFalse(SupportedFilesHelper.isBundleSpecial(null));

        assertTrue(SupportedFilesHelper.isM4bSpecial(SupportedFilesHelper.SPECIAL_TYPE_M4B));
        assertFalse(SupportedFilesHelper.isM4bSpecial(SupportedFilesHelper.SPECIAL_TYPE_ZIP));

        assertTrue(SupportedFilesHelper.isEbookSpecial(SupportedFilesHelper.SPECIAL_TYPE_TXT));
        assertFalse(SupportedFilesHelper.isEbookSpecial(SupportedFilesHelper.SPECIAL_TYPE_ZIP));

        // txt is an ebook but never needs the split worker
        assertFalse(SupportedFilesHelper.isSplittableEbookSpecial(SupportedFilesHelper.SPECIAL_TYPE_TXT));
        assertTrue(SupportedFilesHelper.isSplittableEbookSpecial(SupportedFilesHelper.SPECIAL_TYPE_EPUB));
        assertTrue(SupportedFilesHelper.isSplittableEbookSpecial(SupportedFilesHelper.SPECIAL_TYPE_DOCX));

        assertTrue(SupportedFilesHelper.isPureEbook("epub"));
        assertTrue(SupportedFilesHelper.isPureEbook(SupportedFilesHelper.SPECIAL_TYPE_FB2));
        assertFalse(SupportedFilesHelper.isPureEbook(SupportedFilesHelper.SPECIAL_TYPE_DOCX));
    }

    @Test
    public void everySplittableEbookSpecialIsAlsoAnEbookSpecial() {
        for (String s : new String[] { "EPUB", "FB2", "ODT", "DOCX", "HTML", "TXT", "ZIP", "M4B", "7Z", "TAR" }) {
            if (SupportedFilesHelper.isSplittableEbookSpecial(s)) {
                assertTrue(s, SupportedFilesHelper.isEbookSpecial(s));
            }
        }
    }
}
