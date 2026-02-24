// PlaybackCommands.java
package com.driot.bookplayer.player;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.view.KeyEvent;

import com.driot.bookplayer.global.Intents;
import com.driot.bookplayer.helpers.FirebaseAnalyticsHelper;

import static com.driot.bookplayer.utils.log.LoggerStaticHelper.*;

public final class PlaybackCommands {
    private PlaybackCommands() {
    }

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
                if (st.getState() == PlaybackStateCompat.STATE_PLAYING) {
                    mc.getTransportControls().pause();
                    FirebaseAnalyticsHelper.tellAnalyticsPlayAction("pause", "");
                } else {
                    mc.getTransportControls().play();
                    FirebaseAnalyticsHelper.tellAnalyticsPlayAction("play", "");
                }
                return;
            } else {
                myLogE("MediaControllerCompat, PlaybackStateCompat is null");
            }
        } else {
            myLogE("MediaControllerCompat is null");
        }
        myLog("MediaControllerCompat fallback => call Service CMD");
        // Fallback: use your app’s own UI bus to decide
        boolean currentlyPlaying = false;
        var s = PlaybackUiBus.get().state().getValue();
        if (s != null)
            currentlyPlaying = s.playing;

        String action = currentlyPlaying ? "CMD_PAUSE" : "CMD_PLAY";
        ContextCompat.startForegroundService(
                ctx,
                new Intent(ctx, MediaService.class)
                        .setAction(action)
                        .putExtra(Intents.EXTRA_FOREGROUND, true)
                        .putExtra(Intents.EXTRA_CALLER, "PlaybackCommands.playPause.fallback"));
        // other possible Fallback: media buttons (for legacy/device quirks) like :
        /*
         * private static void sendMediaButtonToService(Context ctx, int keyCode) {
         * Intent down = new Intent(ctx, MediaService.class)
         * .setAction(Intent.ACTION_MEDIA_BUTTON)
         * .putExtra(Intent.EXTRA_KEY_EVENT,
         * new android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, keyCode));
         * ContextCompat.startForegroundService(ctx, down);
         * 
         * Intent up = new Intent(ctx, MediaService.class)
         * .setAction(Intent.ACTION_MEDIA_BUTTON)
         * .putExtra(Intent.EXTRA_KEY_EVENT,
         * new android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, keyCode));
         * ContextCompat.startForegroundService(ctx, up);
         * }
         */
    }

    public static void next(Context ctx) {
        FirebaseAnalyticsHelper.tellAnalyticsPlayAction("next", "");
        MediaControllerCompat mc = mcOrNull(ctx);
        if (mc == null) {
            myLogE("MediaControllerCompat is null");
        } else {
            if (mc.getPlaybackState() == null) {
                myLogE("PlaybackState == null");
            } else {
                MediaControllerCompat.TransportControls tc = mc.getTransportControls();
                if (tc == null) {
                    myLogE("MediaControllerCompat, TransportControls is null");
                } else {
                    tc.fastForward();
                    return;
                }
            }
        }
        myLog("fallback sendMediaButtonToService");
        sendMediaButtonToService(ctx, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD);
    }

    public static void prev(Context ctx) {
        MediaControllerCompat mc = mcOrNull(ctx);
        FirebaseAnalyticsHelper.tellAnalyticsPlayAction("prev", "");
        if (mc != null) {
            mc.getTransportControls().rewind();
            return;
        }
        myLogE("MediaControllerCompat is null => fallback sendMediaButtonToService");
        sendMediaButtonToService(ctx, KeyEvent.KEYCODE_MEDIA_REWIND);
    }

    public static void seekTo(Context ctx, long ms) {
        MediaControllerCompat mc = mcOrNull(ctx);
        FirebaseAnalyticsHelper.tellAnalyticsPlayAction("seekTo", "");
        if (mc != null) {
            mc.getTransportControls().seekTo(ms);
            return;
        }
        myLogE("MediaControllerCompat is null => no fallback");
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

    public static void pause(Context ctx) {
        try {
            ContextCompat.startForegroundService(ctx,
                    new Intent(ctx, MediaService.class)
                            .setAction("CMD_PAUSE")
                            .putExtra(Intents.EXTRA_FOREGROUND, true)
                            .putExtra(Intents.EXTRA_CALLER, "PlaybackCommands.pause"));
        } catch (Throwable ignored) {
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
        myLogE("Fallback");
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
        myLogE("Fallback");
        ContextCompat.startForegroundService(ctx,
                new Intent(ctx, MediaService.class)
                        .setAction(Intents.CMD_UPDATE_SLEEP)
                        .putExtra(Intents.EXTRA_CUSTOM_SLEEP_MINUTES, minutes)
                        .putExtra(Intents.EXTRA_FOREGROUND, true));
    }

    public static void resetLastUserAction(Context ctx) {
        MediaControllerCompat mc = mcOrNull(ctx);
        if (mc != null) {
            mc.getTransportControls().sendCustomAction("CMD_RESET_LAST_USER_ACTION", null);
            return;
        }
        myLogE("Fallback resetSleepTimer");
        ContextCompat.startForegroundService(ctx,
                new Intent(ctx, MediaService.class)
                        .setAction("CMD_RESET_LAST_USER_ACTION")
                        .putExtra(Intents.EXTRA_FOREGROUND, true));
    }

    private static void sendMediaButtonToService(Context ctx, int keyCode) {
        Intent down = new Intent(ctx, MediaService.class)
                .setAction(Intent.ACTION_MEDIA_BUTTON)
                .putExtra(Intents.EXTRA_CALLER, "PlaybackCommands.sendMediaButtonToService : " + keyCode)
                .putExtra(Intent.EXTRA_KEY_EVENT,
                        new android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, keyCode));
        ContextCompat.startForegroundService(ctx, down);

        Intent up = new Intent(ctx, MediaService.class)
                .setAction(Intent.ACTION_MEDIA_BUTTON)
                .putExtra(Intents.EXTRA_CALLER, "PlaybackCommands.sendMediaButtonToService : " + keyCode)
                .putExtra(Intent.EXTRA_KEY_EVENT,
                        new android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, keyCode));
        ContextCompat.startForegroundService(ctx, up);
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

    /**
     * Optional: one-shot query using a ResultReceiver; post the result in your VM.
     */
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

    public static String stateToString(int s) {
        switch (s) {
            case PlaybackStateCompat.STATE_NONE:
                return "NONE";
            case PlaybackStateCompat.STATE_STOPPED:
                return "STOPPED";
            case PlaybackStateCompat.STATE_PAUSED:
                return "PAUSED";
            case PlaybackStateCompat.STATE_PLAYING:
                return "PLAYING";
            case PlaybackStateCompat.STATE_FAST_FORWARDING:
                return "FF";
            case PlaybackStateCompat.STATE_REWINDING:
                return "REW";
            case PlaybackStateCompat.STATE_BUFFERING:
                return "BUFFERING";
            case PlaybackStateCompat.STATE_CONNECTING:
                return "CONNECTING";
            case PlaybackStateCompat.STATE_ERROR:
                return "ERROR";
            default:
                return "?" + s;
        }
    }

    public static String decodeActions(long actions) {
        StringBuilder sb = new StringBuilder();

        add(sb, actions, PlaybackStateCompat.ACTION_STOP, "STOP");
        add(sb, actions, PlaybackStateCompat.ACTION_PAUSE, "PAUSE");
        add(sb, actions, PlaybackStateCompat.ACTION_PLAY, "PLAY");
        add(sb, actions, PlaybackStateCompat.ACTION_REWIND, "REWIND");
        add(sb, actions, PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS, "SKIP_PREV");
        add(sb, actions, PlaybackStateCompat.ACTION_SKIP_TO_NEXT, "SKIP_NEXT");
        add(sb, actions, PlaybackStateCompat.ACTION_FAST_FORWARD, "FAST_FWD");
        add(sb, actions, PlaybackStateCompat.ACTION_SEEK_TO, "SEEK_TO");
        add(sb, actions, PlaybackStateCompat.ACTION_PLAY_PAUSE, "PLAY_PAUSE");
        add(sb, actions, PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID, "PLAY_FROM_MEDIA_ID");
        add(sb, actions, PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH, "PLAY_FROM_SEARCH");
        add(sb, actions, PlaybackStateCompat.ACTION_SKIP_TO_QUEUE_ITEM, "SKIP_QUEUE_ITEM");
        add(sb, actions, PlaybackStateCompat.ACTION_PLAY_FROM_URI, "PLAY_FROM_URI");
        add(sb, actions, PlaybackStateCompat.ACTION_PREPARE, "PREPARE");
        add(sb, actions, PlaybackStateCompat.ACTION_PREPARE_FROM_MEDIA_ID, "PREPARE_FROM_MEDIA_ID");
        add(sb, actions, PlaybackStateCompat.ACTION_PREPARE_FROM_SEARCH, "PREPARE_FROM_SEARCH");
        add(sb, actions, PlaybackStateCompat.ACTION_PREPARE_FROM_URI, "PREPARE_FROM_URI");
        add(sb, actions, PlaybackStateCompat.ACTION_SET_REPEAT_MODE, "SET_REPEAT_MODE");
        add(sb, actions, PlaybackStateCompat.ACTION_SET_SHUFFLE_MODE, "SET_SHUFFLE_MODE");
        add(sb, actions, PlaybackStateCompat.ACTION_SET_RATING, "SET_RATING");

        if (sb.length() == 0)
            return "NONE";
        return sb.toString();
    }

    private static void add(StringBuilder sb, long actions, long flag, String label) {
        if ((actions & flag) != 0) {
            if (sb.length() > 0)
                sb.append(", ");
            sb.append(label);
        }
    }

}
