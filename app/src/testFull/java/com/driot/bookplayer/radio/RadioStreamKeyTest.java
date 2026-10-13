package com.driot.bookplayer.radio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * What makes two result rows the same stream. The cases below are the real ones radio-browser
 * returns for "france info": 15 entries for 5 streams, the HLS one registered twice - once with a
 * "?id=radiofrance" tracking parameter - and the AAC one eight times over http.
 *
 * In the full flavour's test source set: RadioHelper does not exist in the pure build.
 */
public class RadioStreamKeyTest {

    private static void same(String a, String b) {
        assertEquals("should be the same stream", RadioHelper.streamKey(a), RadioHelper.streamKey(b));
    }

    private static void different(String a, String b) {
        assertNotEquals("should be different streams", RadioHelper.streamKey(a),
                RadioHelper.streamKey(b));
    }

    @Test
    public void trackingQueryIsNotPartOfTheStream() {
        same("https://stream.radiofrance.fr/franceinfo/franceinfo_hifi.m3u8?id=radiofrance",
                "https://stream.radiofrance.fr/franceinfo/franceinfo_hifi.m3u8");
    }

    @Test
    public void schemeIsNotPartOfTheStream() {
        same("http://icecast.radiofrance.fr/franceinfo-hifi.aac",
                "https://icecast.radiofrance.fr/franceinfo-hifi.aac");
    }

    @Test
    public void trailingSlashAndHostCaseAreIgnored() {
        same("http://Direct.FranceInfo.fr/live/", "http://direct.franceinfo.fr/live");
    }

    @Test
    public void fragmentIsIgnored() {
        same("http://host.com/live.mp3#now", "http://host.com/live.mp3");
    }

    @Test
    public void differentStreamsOfTheSameStationStayApart() {
        // the five France Info streams must remain five choices
        different("http://direct.franceinfo.fr/live/franceinfo-midfi.mp3",
                "http://direct.franceinfo.fr/live/franceinfo-lofi.mp3");
        different("http://direct.franceinfo.fr/live/franceinfo-midfi.mp3",
                "http://direct.franceinfo.fr/live/franceinfo-midfi.aac");
        different("http://icecast.radiofrance.fr/franceinfo-hifi.aac",
                "https://stream.radiofrance.fr/franceinfo/franceinfo_hifi.m3u8");
    }

    @Test
    public void portMatters() {
        different("http://host.com:8000/stream", "http://host.com/stream");
    }

    @Test
    public void pathCaseMatters() {
        // paths are case-sensitive on most servers, so they are two streams until proven otherwise
        different("http://host.com/Live.mp3", "http://host.com/live.mp3");
    }

    @Test
    public void noUrlGivesNoKey() {
        assertEquals("", RadioHelper.streamKey(null));
        assertEquals("", RadioHelper.streamKey(""));
        assertEquals("", RadioHelper.streamKey("   "));
    }

    @Test
    public void bareHostWithoutPathWorks() {
        same("http://host.com", "https://host.com/");
    }

    @Test
    public void videoCodecHint() {
        assertTrue(RadioHelper.codecLooksLikeVideo("H.264"));
        assertTrue(RadioHelper.codecLooksLikeVideo("AAC,H264"));
        assertTrue(RadioHelper.codecLooksLikeVideo("hevc"));
        assertFalse(RadioHelper.codecLooksLikeVideo("MP3"));
        assertFalse(RadioHelper.codecLooksLikeVideo("AAC"));
        // radio-browser reports this for every HLS entry, video or not
        assertFalse(RadioHelper.codecLooksLikeVideo("UNKNOWN"));
        assertFalse(RadioHelper.codecLooksLikeVideo(null));
    }
}
