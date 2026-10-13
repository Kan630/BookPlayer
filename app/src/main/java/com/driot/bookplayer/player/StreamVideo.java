package com.driot.bookplayer.player;

import android.os.Handler;
import android.os.Looper;
import android.view.SurfaceView;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import static com.driot.bookplayer.utils.log.LoggerStaticHelper.*;

/**
 * The single wire between whatever engine is playing and a video surface - a TV-style radio stream
 * or a video podcast episode, the two kinds of stream that can carry a picture.
 *
 * The ExoPlayer lives in MediaService and outlives any screen, while the surface belongs to
 * whatever Activity is showing it, so neither can hold the other: the engine registers itself here
 * for as long as it exists, and StreamVideoActivity (full flavor) attaches its SurfaceView while
 * it is on screen.
 *
 * Video tracks are left unselected until something attaches a surface (see ExoVideoSupport), so a
 * listener who only wants the sound never pays for the picture - and we still know the picture is
 * there, because an unselected track is still reported by Tracks.
 *
 * Main thread only: an ExoPlayer may only be touched from the thread that built it, which for
 * these engines is the main one.
 */
public final class StreamVideo {

    private StreamVideo() {
    }

    /** The engine currently playing radio, null between stations and while nothing plays. */
    @Nullable
    private static VideoCapableEngine engine;

    /** The surface a screen wants the video on, null when no screen is showing it. */
    @Nullable
    private static SurfaceView surface;

    /** The screen's hook on the picture's size, so it can letterbox to the right shape. */
    @Nullable
    private static VideoCapableEngine.VideoSizeListener sizeListener;

    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    // -------------------------------------------------------------------------
    // Engine side
    // -------------------------------------------------------------------------

    /** A new radio engine is live. Takes over the surface a screen already asked for, so changing
     * station while watching keeps the video coming instead of going black. */
    static void register(@NonNull VideoCapableEngine newEngine) {
        onMain(() -> {
            engine = newEngine;
            newEngine.setVideoSizeListener(sizeListener);
            if (surface != null) {
                myLogD("StreamVideo: new engine takes over the attached surface");
                newEngine.bindVideoSurface(surface);
            }
        });
    }

    static void unregister(@NonNull VideoCapableEngine oldEngine) {
        onMain(() -> {
            if (engine == oldEngine) {
                engine = null;
            }
        });
    }

    // -------------------------------------------------------------------------
    // Screen side
    // -------------------------------------------------------------------------

    /** Shows the video on this surface, selecting the video track if it was left out. Returns
     * false when no radio engine is playing (nothing to show). */
    @MainThread
    public static boolean attach(@NonNull SurfaceView view) {
        surface = view;
        VideoCapableEngine e = engine;
        if (e == null) {
            myLogW("StreamVideo.attach: nothing is playing - nothing to show yet");
            return false;
        }
        e.bindVideoSurface(view);
        return true;
    }

    /** Drops the surface and unselects the video track again: the station keeps playing as plain
     * audio, on the same connection, with no picture being decoded. */
    @MainThread
    public static void detach() {
        surface = null;
        VideoCapableEngine e = engine;
        if (e != null) {
            e.unbindVideoSurface();
        }
    }

    /** Hook for the picture's size: called as soon as a frame is decoded and on every change,
     * including after a station switch. Cleared with null when the screen goes away. */
    @MainThread
    public static void setSizeListener(@Nullable VideoCapableEngine.VideoSizeListener l) {
        sizeListener = l;
        VideoCapableEngine e = engine;
        if (e != null) {
            e.setVideoSizeListener(l);
        }
    }

    /** Whether the stream being played has a video track (even an unselected one). */
    @MainThread
    public static boolean hasVideo() {
        VideoCapableEngine e = engine;
        return e != null && e.hasVideoTrack();
    }

    private static void onMain(Runnable r) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            r.run();
        } else {
            mainHandler.post(r);
        }
    }
}
