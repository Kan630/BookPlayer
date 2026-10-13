package com.driot.bookplayer.podcasts;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * What makes an episode a video one. The urls below are the real ones Podcast Index returns for
 * the TWiT video feeds (prefixed by their analytics redirector, extension still visible) and for
 * ordinary audio shows.
 */
public class VideoEnclosureTest {

    @Test
    public void theApiTypeDecidesWhenWeHaveIt() {
        assertTrue(PodcastHelper.isVideoEnclosure("video/mp4", null));
        assertTrue(PodcastHelper.isVideoEnclosure("VIDEO/MP4", "whatever.mp3"));
        assertFalse(PodcastHelper.isVideoEnclosure("audio/mpeg", null));
        // an audio type wins over a misleading extension: the feed knows best
        assertFalse(PodcastHelper.isVideoEnclosure("audio/mp4", "episode.mp4"));
    }

    @Test
    public void withoutATypeTheExtensionIsWhatIsLeft() {
        assertTrue(PodcastHelper.isVideoEnclosure(null,
                "https://pscrb.fm/rss/p/mgln.ai/e/294/cdn.twit.tv/video/twit/twit1105/twit1105_h264m_1920x1080.mp4"));
        assertTrue(PodcastHelper.isVideoEnclosure(null,
                "https://cdn.twit.tv/video/atg/atg0066/atg0066_h264m_1280x720_1872.mp4"));
        assertTrue(PodcastHelper.isVideoEnclosure("", "https://host/ep.m4v?source=rss"));
        assertTrue(PodcastHelper.isVideoEnclosure(null, "https://host/ep.MOV#t=0"));
    }

    @Test
    public void audioStaysAudio() {
        assertFalse(PodcastHelper.isVideoEnclosure(null, "http://direct.franceinfo.fr/live/franceinfo-midfi.mp3"));
        // same container family as mp4, but it is the audio one
        assertFalse(PodcastHelper.isVideoEnclosure(null, "https://host/episode.m4a"));
        assertFalse(PodcastHelper.isVideoEnclosure(null, "https://host/episode.ogg?x=1"));
        assertFalse(PodcastHelper.isVideoEnclosure(null, "https://host/stream"));
        assertFalse(PodcastHelper.isVideoEnclosure(null, null));
        assertFalse(PodcastHelper.isVideoEnclosure(null, ""));
    }
}
