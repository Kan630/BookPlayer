package com.driot.bookplayer.player;

import android.view.SurfaceView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.Tracks;
import androidx.media3.common.VideoSize;
import androidx.media3.exoplayer.ExoPlayer;

import static com.driot.bookplayer.utils.log.LoggerStaticHelper.*;

/**
 * The video half of an ExoPlayer-based engine, in one place because radio and podcasts need
 * exactly the same thing: leave the video track unselected while nobody is watching, notice that
 * there is one anyway, and hand the picture to a surface when a screen asks.
 *
 * Leaving the track out is not a detail: a TV-style radio stream or a video podcast episode (a
 * 1080p mp4, 2.9 GB for an hour of This Week in Tech) would otherwise have its picture fetched and
 * decoded for nothing, on someone's data plan. An unselected track is still reported by
 * {@link Tracks}, which is how we know the picture is there without paying for it.
 *
 * Main thread only: an ExoPlayer may only be touched from the thread that built it.
 */
@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
final class ExoVideoSupport {

    private final ExoPlayer player;

    private volatile boolean hasVideoTrack = false;
    private volatile int videoWidth = 0;
    private volatile int videoHeight = 0;

    /** One track summary per stream in the log, not one per track change. */
    private volatile boolean tracksLogged = false;

    @Nullable
    private volatile VideoCapableEngine.VideoSizeListener sizeListener;

    ExoVideoSupport(@NonNull ExoPlayer player) {
        this.player = player;
        setVideoSelected(false);
    }

    // ---- fed by the engine's Player.Listener ----

    void onTracksChanged(@NonNull Tracks tracks) {
        boolean video = false;
        StringBuilder seen = new StringBuilder();
        for (Tracks.Group group : tracks.getGroups()) {
            seen.append(' ').append(androidx.media3.common.util.Util.getTrackTypeString(group.getType()))
                    .append(group.isSupported() ? "" : "(unsupported)");
            // isSupported(): a track this device has no decoder for is not a picture we could ever
            // show, so it must not light up the "watch" buttons either.
            if (group.getType() == C.TRACK_TYPE_VIDEO && group.isSupported()) {
                video = true;
            }
        }
        // Logged on every stream, found or not: "no watch button" is otherwise impossible to tell
        // apart from "this listener never ran".
        if (!tracksLogged) {
            tracksLogged = true;
            myLogI("stream tracks =>" + (seen.length() == 0 ? " none" : seen) + " | video=" + video);
        }
        if (video != hasVideoTrack) {
            myLogI("stream video track => " + video);
            hasVideoTrack = video;
        }
    }

    void onVideoSizeChanged(@NonNull VideoSize size) {
        myLogD("stream video size => " + size.width + "x" + size.height);
        videoWidth = size.width;
        videoHeight = size.height;
        VideoCapableEngine.VideoSizeListener l = sizeListener;
        if (l != null && size.width > 0 && size.height > 0) {
            l.onVideoSize(size.width, size.height);
        }
    }

    /** Another stream: what the last one carried says nothing about this one. */
    void onNewStream() {
        hasVideoTrack = false;
        tracksLogged = false;
        videoWidth = 0;
        videoHeight = 0;
    }

    // ---- the VideoCapableEngine side ----

    boolean hasVideoTrack() {
        return hasVideoTrack;
    }

    void setVideoSizeListener(@Nullable VideoCapableEngine.VideoSizeListener l) {
        sizeListener = l;
        int w = videoWidth, h = videoHeight;
        if (l != null && w > 0 && h > 0) {
            l.onVideoSize(w, h); // the size is already known when a screen opens mid-stream
        }
    }

    void bind(@NonNull SurfaceView view) {
        myLogI("binding video surface");
        setVideoSelected(true);
        try {
            player.setVideoSurfaceView(view);
        } catch (Throwable t) {
            myLogEE(t, "bindVideoSurface failed");
        }
    }

    void unbind() {
        myLogI("unbinding video surface");
        try {
            player.clearVideoSurface();
        } catch (Throwable t) {
            myLogEE(t, "unbindVideoSurface failed");
        }
        setVideoSelected(false);
        videoWidth = 0;
        videoHeight = 0;
    }

    void dispose() {
        sizeListener = null;
    }

    private void setVideoSelected(boolean selected) {
        try {
            player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, !selected)
                    .build());
        } catch (Throwable t) {
            myLogEE(t, "setVideoSelected(" + selected + ") failed");
        }
    }
}
