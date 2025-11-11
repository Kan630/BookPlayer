// MediaControllerHolder.java
package com.driot.bookplayer.player;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.os.Bundle;

import androidx.annotation.MainThread;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;

import java.lang.ref.WeakReference;

public final class MediaControllerHolder {
    private static volatile MediaBrowserCompat   sBrowser;
    private static volatile MediaControllerCompat sController;

    // remember last Activity to (re)bind controller to it automatically
    private static volatile WeakReference<Activity> sLastActivityRef;

    private MediaControllerHolder() {}

    /** Ensure we have a connected MediaBrowser; idempotent. Use APP context here. */
    @MainThread
    public static void ensureConnected(Context appContext) {
        Context app = appContext.getApplicationContext();
        if (sBrowser != null && sBrowser.isConnected()) return;

        // (Re)create browser
        sBrowser = new MediaBrowserCompat(
                app,
                new ComponentName(app, MediaService.class),
                new MediaBrowserCompat.ConnectionCallback() {
                    @Override public void onConnected() {
                        try {
                            MediaSessionCompat.Token token = sBrowser.getSessionToken();
                            MediaControllerCompat controller = new MediaControllerCompat(app, token);
                            setController(controller); // installs callback & rebinds to last activity
                        } catch (Throwable ignored) {
                            // leave sController null; caller can fallback to service CMDs
                        }
                    }
                    @Override public void onConnectionSuspended() { /* no-op */ }
                    @Override public void onConnectionFailed()    { /* no-op */ }
                },
                (Bundle) null
        );
        sBrowser.connect();
    }

    /** Attach current controller to an Activity so MediaControllerCompat.getMediaController(activity) works. */
    @MainThread
    public static void attachTo(Activity activity) {
        sLastActivityRef = new WeakReference<>(activity);
        MediaControllerCompat ctl = sController;
        if (ctl != null) {
            try {
                MediaControllerCompat.setMediaController(activity, ctl); // requires Activity
            } catch (Throwable ignored) {}
        } else {
            // no controller yet → ensure browser connects; we'll rebind in setController(...)
            ensureConnected(activity.getApplicationContext());
        }
    }

    /** Detach the controller from this Activity (call in onStop if you want to be neat). */
    @MainThread
    public static void detachFrom(Activity activity) {
        try {
            MediaControllerCompat current = MediaControllerCompat.getMediaController(activity);
            if (current != null) MediaControllerCompat.setMediaController(activity, null);
        } catch (Throwable ignored) {}
        WeakReference<Activity> r = sLastActivityRef;
        if (r != null && r.get() == activity) sLastActivityRef = null;
    }

    @Nullable
    public static MediaControllerCompat get() { return sController; }

    /** Convenience: return the Activity-bound controller if present, else the static one. */
    @Nullable
    public static MediaControllerCompat forActivityOrGlobal(@Nullable Activity activity) {
        try {
            if (activity != null) {
                MediaControllerCompat c = MediaControllerCompat.getMediaController(activity);
                if (c != null) return c;
            }
        } catch (Throwable ignored) {}
        return sController;
    }

    // ---- internals ----

    private static final MediaControllerCompat.Callback sControllerCb = new MediaControllerCompat.Callback() {
        @Override public void onSessionDestroyed() {
            // The service called session.release() (e.g., after playlist finished & shutdown)
            // → drop controller and reconnect to fetch a fresh token
            clearController();
            if (sBrowser != null) {
                try { if (sBrowser.isConnected()) sBrowser.disconnect(); } catch (Throwable ignored) {}
                try { sBrowser.connect(); } catch (Throwable ignored) {}
            }
        }

        @Override public void onPlaybackStateChanged(@Nullable PlaybackStateCompat state) {
            // optional: log or notify UI
        }
    };

    private static void setController(@Nullable MediaControllerCompat controller) {
        clearController(); // unregister old callback if any
        sController = controller;
        if (sController != null) {
            try { sController.registerCallback(sControllerCb); } catch (Throwable ignored) {}
            // rebind to last Activity so transport controls work immediately
            Activity a = (sLastActivityRef != null) ? sLastActivityRef.get() : null;
            if (a != null) {
                try { MediaControllerCompat.setMediaController(a, sController); } catch (Throwable ignored) {}
            }
        }
    }

    private static void clearController() {
        if (sController != null) {
            try { sController.unregisterCallback(sControllerCb); } catch (Throwable ignored) {}
        }
        sController = null;
    }
}
