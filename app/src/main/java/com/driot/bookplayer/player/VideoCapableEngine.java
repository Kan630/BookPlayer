package com.driot.bookplayer.player;

import android.view.SurfaceView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * An engine whose stream may carry a picture, and which can put it on a surface when a screen asks
 * for one: web radio (a TV-style HLS stream) and podcasts (an episode whose enclosure is an mp4 -
 * a "video podcast", which is a plain video file in the feed).
 *
 * The picture is never decoded until a surface is attached, so a listener who only wants the sound
 * pays nothing for it. See {@link StreamVideo}, which is what connects the two ends, and
 * {@link ExoVideoSupport}, which both engines use to do the actual work.
 */
public interface VideoCapableEngine {

    /** Told the picture's size as soon as the decoder knows it, and on every change. */
    interface VideoSizeListener {
        void onVideoSize(int width, int height);
    }

    /** Whether what is playing has a video track this device could show. */
    boolean hasVideoTrack();

    /** Hook for the picture's size, so a screen can letterbox to the right shape. */
    void setVideoSizeListener(@Nullable VideoSizeListener listener);

    /** Selects the video track and shows it on this surface. Main thread. */
    void bindVideoSurface(@NonNull SurfaceView view);

    /** Back to sound only, on the same connection: no picture is decoded. Main thread. */
    void unbindVideoSurface();
}
