package com.driot.bookplayer.player;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import android.support.v4.media.session.MediaSessionCompat;

public final class PlaybackNotificationManager {

    public interface ActionProvider {
        PendingIntent rewind();
        PendingIntent play();
        PendingIntent pause();
        PendingIntent fastForward();
        PendingIntent content();
    }

    private final Context app;
    private final String channelId;
    private final int smallIconRes;

    public PlaybackNotificationManager(@NonNull Context context,
                                       @NonNull String channelId,
                                       int smallIconRes) {
        this.app = context.getApplicationContext();
        this.channelId = channelId;
        this.smallIconRes = smallIconRes;
    }

    public void ensureChannel(@NonNull String channelName, @NonNull String channelDesc) {
        NotificationManager nm = app.getSystemService(NotificationManager.class);
        if (nm == null) return;
        NotificationChannel ch = new NotificationChannel(
                    channelId, channelName, NotificationManager.IMPORTANCE_LOW);
        ch.setDescription(channelDesc);
        nm.createNotificationChannel(ch);
    }

    public @NonNull Notification build(@NonNull MediaSessionCompat mediaSession,
                                       boolean playing,
                                       @NonNull CharSequence title,
                                       @NonNull CharSequence text,
                                       @NonNull ActionProvider actions) {

        PendingIntent playPause = playing ? actions.pause() : actions.play();
        int playPauseIcon = playing ? android.R.drawable.ic_media_pause
                : android.R.drawable.ic_media_play;
        String playPauseLabel = playing ? "Pause" : "Play";

        return new NotificationCompat.Builder(app, channelId)
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(smallIconRes)
                .setContentIntent(actions.content())
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .addAction(android.R.drawable.ic_media_rew, "Rewind", actions.rewind())
                .addAction(playPauseIcon, playPauseLabel, playPause)
                .addAction(android.R.drawable.ic_media_ff, "Forward", actions.fastForward())
                .setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
                        .setMediaSession(mediaSession.getSessionToken())
                        .setShowActionsInCompactView(0, 1, 2))
                .build();
    }

    public void cancel(int notificationId) {
        NotificationManager nm = (NotificationManager) app.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.cancel(notificationId);
    }

    public @NonNull Notification buildPreparing(
            @NonNull CharSequence title,
            @NonNull CharSequence text,
            @NonNull PendingIntent contentIntent) {
        return new NotificationCompat.Builder(app, channelId)
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(smallIconRes)
                .setContentIntent(contentIntent)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                // No transport actions here
                .build();
    }

}

/*
// 13-56
// StatusBarNotification(pkg=com.driot.bookplayer user=UserHandle{0} id=1 tag=null key=0|com.driot.bookplayer|1|null|10333:
// Notification(channel=audio_channel_of_bookplayer shortcut=null contentView=null vibrate=null sound=null defaults=0
// flags=ONGOING_EVENT|ONLY_ALERT_ONCE|NO_CLEAR|FOREGROUND_SERVICE color=0x00000000 category=transport actions=3 vis=PUBLIC semFlags=0x0 semPriority=0 semMissedCount=0))
private boolean isNotificationActive(Context context, int notificationId) {
    NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
    StatusBarNotification[] notifications = ((NotificationManager) manager).getActiveNotifications();
    for (StatusBarNotification sbn : notifications) {
        myLog(sbn.toString());
        if (sbn.getId() == notificationId) {
            return true;
        }
    }
    return false;
}

 */

