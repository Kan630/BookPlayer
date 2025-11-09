// PlaybackCommands.java
package com.driot.bookplayer.player;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.media.session.MediaButtonReceiver;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.PlaybackStateCompat;

import com.driot.bookplayer.global.Intents;

public final class PlaybackCommands {
    private PlaybackCommands() {}

    @Nullable
    public static MediaControllerCompat mcOrNull(@Nullable Context ctx) {
        try {
            return MediaControllerCompat.getMediaController(
                    com.driot.bookplayer.utils.ContextHolder.activityOrNull());
        } catch (Throwable ignore) { return null; }
    }

    public static void playPause(Context ctx) {
        MediaControllerCompat mc = mcOrNull(ctx);
        if (mc != null) {
            PlaybackStateCompat st = mc.getPlaybackState();
            if (st != null && st.getState() == PlaybackStateCompat.STATE_PLAYING) {
                mc.getTransportControls().pause();
            } else {
                mc.getTransportControls().play();
            }
            return;
        }
        // Fallback: media buttons (for legacy/device quirks)
        MediaButtonReceiver.handleIntent(null,
                new android.content.Intent(Intent.ACTION_MEDIA_BUTTON)
                        .putExtra(Intent.EXTRA_KEY_EVENT,
                                new android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN,
                                        android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)));
    }

    public static void next(Context ctx) {
        MediaControllerCompat mc = mcOrNull(ctx);
        if (mc != null) { mc.getTransportControls().skipToNext(); return; }
        sendMediaButton(ctx, android.view.KeyEvent.KEYCODE_MEDIA_NEXT);
    }

    public static void prev(Context ctx) {
        MediaControllerCompat mc = mcOrNull(ctx);
        if (mc != null) { mc.getTransportControls().skipToPrevious(); return; }
        sendMediaButton(ctx, android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS);
    }

    public static void seekTo(Context ctx, long ms) {
        MediaControllerCompat mc = mcOrNull(ctx);
        if (mc != null) { mc.getTransportControls().seekTo(ms); return; }
        // No reliable fallback → ignore (UI state will catch up when service updates)
    }

    public static void stop(Context ctx) {
        // Hard stop via intent keeps working even if controller is not attached
        try {
            ContextCompat.startForegroundService(ctx,
                    new Intent(ctx, AudioService.class)
                            .setAction("CMD_STOP")
                            .putExtra(Intents.EXTRA_CALLER, "PlaybackCommands.stop"));
        } catch (IllegalStateException ignored) {
            ctx.stopService(new Intent(ctx, AudioService.class));
        }
    }

    /** Example custom actions via MediaSession for speed/sleep timer. */
    public static void setSpeed(Context ctx, double speed) {
        MediaControllerCompat mc = mcOrNull(ctx);
        if (mc != null) {
            Bundle b = new Bundle();
            b.putDouble(Intents.EXTRA_SPEED, speed);
            mc.getTransportControls().sendCustomAction(Intents.CMD_SET_SPEED, b);
            return;
        }
        // Fallback: foreground service command
        ContextCompat.startForegroundService(ctx,
                new Intent(ctx, AudioService.class)
                        .setAction(Intents.CMD_SET_SPEED)
                        .putExtra(Intents.EXTRA_SPEED, speed)
                        .putExtra(Intents.EXTRA_FOREGROUND, true));
    }

    public static void updateSleepTimer(Context ctx, int minutes) {
        MediaControllerCompat mc = mcOrNull(ctx);
        if (mc != null) {
            Bundle b = new Bundle();
            b.putInt(Intents.EXTRA_MINUTES, minutes);
            mc.getTransportControls().sendCustomAction(Intents.CMD_UPDATE_SLEEP, b);
            return;
        }
        ContextCompat.startForegroundService(ctx,
                new Intent(ctx, AudioService.class)
                        .setAction(Intents.CMD_UPDATE_SLEEP)
                        .putExtra(Intents.EXTRA_MINUTES, minutes)
                        .putExtra(Intents.EXTRA_FOREGROUND, true));
    }

    private static void sendMediaButton(Context ctx, int keyCode) {
        Intent down = new Intent(Intent.ACTION_MEDIA_BUTTON)
                .putExtra(Intent.EXTRA_KEY_EVENT,
                        new android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, keyCode));
        MediaButtonReceiver.handleIntent(null, down);

        Intent up = new Intent(Intent.ACTION_MEDIA_BUTTON)
                .putExtra(Intent.EXTRA_KEY_EVENT,
                        new android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, keyCode));
        MediaButtonReceiver.handleIntent(null, up);
    }
}
