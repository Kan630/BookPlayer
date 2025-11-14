package com.driot.bookplayer.player;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import com.driot.bookplayer.db.ZikFile;
import com.driot.bookplayer.global.Intents;

public class NavHelper {
    public static PendingIntent navigateToMain(Context context) {
        final int pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getActivity(
                context,
                0,
                new Intent(context, com.driot.bookplayer.activities.MainActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP),
                pendingFlags
        );
    }

    public static PendingIntent navigateToActivity(Context context) {
        final int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;

        androidx.core.app.TaskStackBuilder tsb = androidx.core.app.TaskStackBuilder.create(context);
        // 1) Always start at Main
        tsb.addNextIntent(new Intent(context, com.driot.bookplayer.activities.MainActivity.class));

        // 2) If multiple tracks, insert the track list screen before PlayActivity
        PlayList pl = PlayList.getInstance();
        ZikFile z = (pl != null) ? pl.getZikFile() : null;
        int folderId = (z != null) ? z.getIdFolder() : -1;
        if (folderId > 0 && pl.getSize() > 1) {
            Intent trackList = new Intent(context, com.driot.bookplayer.activities.ZikFileActivity.class)
                    .putExtra(Intents.EXTRA_FOLDER_ID, folderId);
            tsb.addNextIntent(trackList);
        }

        // 3) Finally PlayActivity (singleTop/clearTop like you already do)
        tsb.addNextIntent(new Intent(context, PlayActivity.class)
                .putExtra(Intents.EXTRA_AUTOPLAY, false));

        return tsb.getPendingIntent(0, flags);
    }

    public static PendingIntent navigateToRadioActivity(Context context) {
            /*
        AppDatabase.databaseReadExecutor.execute(() -> {
            //TODO boolean hasRadioFavorite = AppDatabase.getDatabase(this).radioDao()...
            boolean hasRadioFavorite = false;
            if (hasRadioFavorite) {
                context.startActivity(new Intent(context.getApplicationContext(), RadioFavoritesActivity.class));
            } else {
            }
            );
        });
             */
        final int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getActivity(
                context,
                0,
                new Intent(context, com.driot.bookplayer.activities.GetRadioActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP),
                flags
        );
    }

    public static PendingIntent navigateToPodcastActivity(Context context) {
        final int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getActivity(
                context,
                0,
                new Intent(context, com.driot.bookplayer.activities.GetPodcastActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP),
                flags
        );
    }

    public static PendingIntent navigateToLibrivoxActivity(Context context) {
        final int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getActivity(
                context,
                0,
                new Intent(context, com.driot.bookplayer.activities.GetAudiobookActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP),
                flags
        );
    }
}
