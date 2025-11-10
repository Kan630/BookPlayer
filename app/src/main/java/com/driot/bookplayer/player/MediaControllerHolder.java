// MediaControllerHolder.java
package com.driot.bookplayer.player;

import android.content.ComponentName;
import android.content.Context;

import androidx.annotation.MainThread;
import androidx.annotation.Nullable;

import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;

public final class MediaControllerHolder {
    private static volatile MediaBrowserCompat sBrowser;
    private static volatile MediaControllerCompat sController;

    private MediaControllerHolder() {}

    @MainThread
    public static void ensureConnected(Context appContext) {
        if (sController != null || sBrowser != null) return;

        sBrowser = new MediaBrowserCompat(
                appContext,
                // IMPORTANT: we connect to MediaService (your MediaBrowserServiceCompat)
                new ComponentName(appContext, MediaService.class),
                new MediaBrowserCompat.ConnectionCallback() {
                    @Override public void onConnected() {
                        try {
                            MediaSessionCompat.Token token = sBrowser.getSessionToken();
                            sController = new MediaControllerCompat(appContext, token);
                        } catch (Throwable ignored) { /* keep null */ }
                    }
                    @Override public void onConnectionSuspended() { /* no-op */ }
                    @Override public void onConnectionFailed()    { /* no-op */ }
                },
                null
        );
        sBrowser.connect();


    }

    @Nullable
    public static MediaControllerCompat get() { return sController; }
}
