package com.driot.bookplayer.global;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The supported-extension / MIME tables drive every import decision. Typos here (a leading dot,
 * an uppercase letter, an extension listed as both audio and video) never crash - they silently
 * make a file type "unsupported" or mis-route it.
 */
public class VarSupportedTypesTest {

    private static void assertCleanExtensions(String label, Set<String> exts) {
        List<String> bad = new ArrayList<>();
        for (String e : exts) {
            if (e == null || e.isEmpty() || e.startsWith(".") || !e.equals(e.toLowerCase(Locale.ROOT))
                    || !e.equals(e.trim()))
                bad.add("[" + e + "]");
        }
        assertTrue(label + " has malformed entries (must be lowercase, no dot, trimmed): " + bad, bad.isEmpty());
    }

    private static void assertCleanMimes(String label, Set<String> mimes) {
        List<String> bad = new ArrayList<>();
        for (String m : mimes) {
            if (m == null || !m.equals(m.toLowerCase(Locale.ROOT)) || !m.matches("[a-z]+/[a-z0-9.+-]+"))
                bad.add("[" + m + "]");
        }
        assertTrue(label + " has malformed MIME types: " + bad, bad.isEmpty());
    }

    @Test
    public void extensionSetsAreWellFormed() {
        assertCleanExtensions("audio", Var.SUPPORTED_AUDIO_EXTENSIONS);
        assertCleanExtensions("video", Var.SUPPORTED_VIDEO_EXTENSIONS);
        assertCleanExtensions("image", Var.SUPPORTED_IMAGE_EXTENSIONS);
        assertCleanExtensions("ebook", Var.SUPPORTED_EBOOK_EXTENSIONS);
        assertCleanExtensions("compressed", Var.SUPPORTED_COMPRESSED_FILE_EXTENSIONS);
        assertCleanExtensions("cover", Var.SUPPORTED_COVER_PICTURE_EXTENSIONS);
    }

    @Test
    public void mimeSetsAreWellFormed() {
        assertCleanMimes("audio", Var.SUPPORTED_AUDIO_MIMES);
        assertCleanMimes("video", Var.SUPPORTED_VIDEO_MIMES);
        assertCleanMimes("image", Var.SUPPORTED_IMAGE_MIMES);
        assertCleanMimes("ebook", Var.SUPPORTED_EBOOK_MIMES);
    }

    @Test
    public void mimeSetsUseTheirOwnTopLevelType() {
        for (String m : Var.SUPPORTED_AUDIO_MIMES)
            assertTrue(m, m.startsWith("audio/"));
        for (String m : Var.SUPPORTED_VIDEO_MIMES)
            assertTrue(m, m.startsWith("video/"));
        for (String m : Var.SUPPORTED_IMAGE_MIMES)
            assertTrue(m, m.startsWith("image/"));
    }

    @Test
    public void setsAreNonEmpty() {
        assertFalse(Var.SUPPORTED_AUDIO_EXTENSIONS.isEmpty());
        assertFalse(Var.SUPPORTED_EBOOK_EXTENSIONS.isEmpty());
        assertFalse(Var.SUPPORTED_COMPRESSED_FILE_EXTENSIONS.isEmpty());
        assertFalse(Var.SUPPORTED_AUDIO_MIMES.isEmpty());
        assertFalse(Var.SUPPORTED_EBOOK_MIMES.isEmpty());
    }

    /**
     * typeFromExtension() checks audio, then video, image, ebook, bundle: an extension in two
     * sets is silently classified by list order. Known intentional overlaps go in ALLOWED.
     */
    @Test
    public void noExtensionIsClassifiedAsTwoDifferentKinds() {
        // mp4 = audio-or-video container by design (see Var comments)
        Set<String> allowed = new HashSet<>(java.util.Arrays.asList("mp4"));
        List<Set<String>> sets = new ArrayList<>();
        sets.add(Var.SUPPORTED_AUDIO_EXTENSIONS);
        sets.add(Var.SUPPORTED_VIDEO_EXTENSIONS);
        sets.add(Var.SUPPORTED_IMAGE_EXTENSIONS);
        sets.add(Var.SUPPORTED_EBOOK_EXTENSIONS);
        sets.add(Var.SUPPORTED_COMPRESSED_FILE_EXTENSIONS);
        String[] names = { "audio", "video", "image", "ebook", "compressed" };

        List<String> overlaps = new ArrayList<>();
        for (int i = 0; i < sets.size(); i++) {
            for (int j = i + 1; j < sets.size(); j++) {
                Set<String> both = new HashSet<>(sets.get(i));
                both.retainAll(sets.get(j));
                both.removeAll(allowed);
                if (!both.isEmpty())
                    overlaps.add(names[i] + " & " + names[j] + ": " + both);
            }
        }
        assertTrue("extension in more than one kind: " + overlaps, overlaps.isEmpty());
    }

    @Test
    public void everyKindHasTheEssentialFormats() {
        for (String e : new String[] { "mp3", "m4a", "m4b", "ogg", "flac" })
            assertTrue(e, Var.SUPPORTED_AUDIO_EXTENSIONS.contains(e));
        for (String e : new String[] { "epub", "txt" })
            assertTrue(e, Var.SUPPORTED_EBOOK_EXTENSIONS.contains(e));
        for (String e : new String[] { "zip" })
            assertTrue(e, Var.SUPPORTED_COMPRESSED_FILE_EXTENSIONS.contains(e));
        for (String e : new String[] { "jpg", "png" })
            assertTrue(e, Var.SUPPORTED_IMAGE_EXTENSIONS.contains(e));
    }

    @Test
    public void importStatusConstantsAreDistinct() {
        Set<String> all = new HashSet<>(java.util.Arrays.asList(
                Var.IMPORT_STATUS_IDLE, Var.IMPORT_STATUS_QUEUED, Var.IMPORT_STATUS_RUNNING,
                Var.IMPORT_STATUS_PAUSED, Var.IMPORT_STATUS_SUCCEEDED, Var.IMPORT_STATUS_FAILED,
                Var.IMPORT_STATUS_CANCELLED));
        assertEquals(7, all.size());
    }

    @Test
    public void playTypesAreDistinct() {
        Set<String> all = new HashSet<>(java.util.Arrays.asList(
                Var.PLAY_TYPE_TEXT, Var.PLAY_TYPE_AUDIO, Var.PLAY_TYPE_MUSIC, Var.PLAY_TYPE_RADIO));
        assertEquals(4, all.size());
    }
}
