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
import com.driot.bookplayer.helpers.FirebaseAnalyticsHelper;

import static com.driot.bookplayer.utils.log.LoggerStaticHelper.*;

public final class PlaybackCommands {
    private PlaybackCommands() {}

    @Nullable
    public static MediaControllerCompat mcOrNull(@Nullable Context ctx) {
        try {
            MediaControllerCompat mc = MediaControllerHolder.get();
            if (mc == null && ctx != null) {
                MediaControllerHolder.ensureConnected(ctx.getApplicationContext());
                mc = MediaControllerHolder.get();
            }
            return mc;
        } catch (Throwable ignore) {
            return null;
        }
    }

    public static void playPause(Context ctx) {
        MediaControllerCompat mc = mcOrNull(ctx);
        if (mc != null) {
            PlaybackStateCompat st = mc.getPlaybackState();
            if (st != null) {
                myLogD("MediaControllerCompat, previous State = " + stateToString(st.getState()));
            } else {
                myLogE("MediaControllerCompat, PlaybackStateCompat is null");
            }
            if (st != null && st.getState() == PlaybackStateCompat.STATE_PLAYING) {
                mc.getTransportControls().pause();
                FirebaseAnalyticsHelper.tellAnalyticsPlayAction("pause", "");
            } else {
                mc.getTransportControls().play();
                FirebaseAnalyticsHelper.tellAnalyticsPlayAction("play", "");
            }
            return;
        }
        myLog("MediaControllerCompat fallback");
        // Fallback: media buttons (for legacy/device quirks)
        MediaButtonReceiver.handleIntent(null,
                new android.content.Intent(Intent.ACTION_MEDIA_BUTTON)
                        .putExtra(Intent.EXTRA_KEY_EVENT,
                                new android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN,
                                        android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)));
    }

    public static void next(Context ctx) {
        MediaControllerCompat mc = mcOrNull(ctx);
        FirebaseAnalyticsHelper.tellAnalyticsPlayAction("next", "");
        if (mc != null) { mc.getTransportControls().skipToNext(); return; }
        sendMediaButton(ctx, android.view.KeyEvent.KEYCODE_MEDIA_NEXT);
    }

    public static void prev(Context ctx) {
        MediaControllerCompat mc = mcOrNull(ctx);
        FirebaseAnalyticsHelper.tellAnalyticsPlayAction("prev", "");
        if (mc != null) { mc.getTransportControls().skipToPrevious(); return; }
        sendMediaButton(ctx, android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS);
    }

    public static void seekTo(Context ctx, long ms) {
        MediaControllerCompat mc = mcOrNull(ctx);
        FirebaseAnalyticsHelper.tellAnalyticsPlayAction("seekTo", "");
        if (mc != null) { mc.getTransportControls().seekTo(ms); return; }
        // No reliable fallback → ignore (UI state will catch up when service updates)
    }

    public static void stop(Context ctx) {
        // Hard stop via intent keeps working even if controller is not attached
        try {
            ContextCompat.startForegroundService(ctx,
                    new Intent(ctx, MediaService.class)
                            .setAction("CMD_STOP")
                            .putExtra(Intents.EXTRA_CALLER, "PlaybackCommands.stop"));
        } catch (IllegalStateException ignored) {
            ctx.stopService(new Intent(ctx, MediaService.class));
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
                new Intent(ctx, MediaService.class)
                        .setAction(Intents.CMD_SET_SPEED)
                        .putExtra(Intents.EXTRA_SPEED, speed)
                        .putExtra(Intents.EXTRA_FOREGROUND, true));
    }

    public static void updateSleepTimer(Context ctx, int minutes) {
        MediaControllerCompat mc = mcOrNull(ctx);
        if (mc != null) {
            Bundle b = new Bundle();
            b.putInt(Intents.EXTRA_CUSTOM_SLEEP_MINUTES, minutes);
            mc.getTransportControls().sendCustomAction(Intents.CMD_UPDATE_SLEEP, b);
            return;
        }
        ContextCompat.startForegroundService(ctx,
                new Intent(ctx, MediaService.class)
                        .setAction(Intents.CMD_UPDATE_SLEEP)
                        .putExtra(Intents.EXTRA_CUSTOM_SLEEP_MINUTES, minutes)
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


    public static void setTtsStartOffset(Context ctx, int startChars) {
        MediaControllerCompat mc = mcOrNull(ctx);
        Bundle b = new Bundle();
        b.putInt(Intents.EXTRA_TTS_START_OFFSET, startChars);
        if (mc != null) {
            mc.getTransportControls().sendCustomAction(Intents.CMD_TTS_SET_START, b);
            return;
        }
        myLogE("Fallback for setTtsStartOffset");
        // Fallback: start service
        ContextCompat.startForegroundService(ctx,
                new Intent(ctx, MediaService.class)
                        .setAction(Intents.CMD_TTS_SET_START)
                        .putExtra(Intents.EXTRA_TTS_START_OFFSET, startChars)
                        .putExtra(Intents.EXTRA_FOREGROUND, true)
                        .putExtra(Intents.EXTRA_CALLER, "PlaybackCommands.setTtsStartOffset"));
    }

    public static void setTtsVoice(Context ctx, @Nullable String voiceName) {
        MediaControllerCompat mc = mcOrNull(ctx);
        Bundle b = new Bundle();
        b.putString(Intents.EXTRA_TTS_VOICE_NAME, voiceName);
        if (mc != null) {
            mc.getTransportControls().sendCustomAction(Intents.CMD_TTS_SET_VOICE, b);
            return;
        }
        ContextCompat.startForegroundService(ctx,
                new Intent(ctx, MediaService.class)
                        .setAction(Intents.CMD_TTS_SET_VOICE)
                        .putExtra(Intents.EXTRA_TTS_VOICE_NAME, voiceName)
                        .putExtra(Intents.EXTRA_FOREGROUND, true)
                        .putExtra(Intents.EXTRA_CALLER, "PlaybackCommands.setTtsVoice"));
    }

    /** Optional: one-shot query using a ResultReceiver; post the result in your VM. */
    public static void requestTtsText(Context ctx, android.os.ResultReceiver rr) {
        MediaControllerCompat mc = mcOrNull(ctx);
        Bundle b = new Bundle();
        b.putParcelable(Intents.EXTRA_RESULT_RECEIVER, rr);
        if (mc != null) {
            mc.getTransportControls().sendCustomAction(Intents.CMD_TTS_GET_TEXT, b);
            return;
        }
        myLogE("requestTtsText fallback");
        ContextCompat.startForegroundService(ctx,
                new Intent(ctx, MediaService.class)
                        .setAction(Intents.CMD_TTS_GET_TEXT)
                        .putExtra(Intents.EXTRA_RESULT_RECEIVER, rr)
                        .putExtra(Intents.EXTRA_FOREGROUND, true)
                        .putExtra(Intents.EXTRA_CALLER, "PlaybackCommands.requestTtsText"));
    }

    private static String stateToString(int s){
        switch(s){
            case PlaybackStateCompat.STATE_NONE: return "NONE";
            case PlaybackStateCompat.STATE_STOPPED: return "STOPPED";
            case PlaybackStateCompat.STATE_PAUSED: return "PAUSED";
            case PlaybackStateCompat.STATE_PLAYING: return "PLAYING";
            case PlaybackStateCompat.STATE_FAST_FORWARDING: return "FF";
            case PlaybackStateCompat.STATE_REWINDING: return "REW";
            case PlaybackStateCompat.STATE_BUFFERING: return "BUFFERING";
            case PlaybackStateCompat.STATE_CONNECTING: return "CONNECTING";
            case PlaybackStateCompat.STATE_ERROR: return "ERROR";
            default: return "?" + s;
        }
    }
}
