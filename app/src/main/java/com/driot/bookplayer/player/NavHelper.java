package com.driot.bookplayer.player;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.db.ZikFile;
import com.driot.bookplayer.global.Intents;
import com.driot.bookplayer.radio.RadioFavoritesStore;

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

    public static PendingIntent getNavToRadioActivityPendingIntent(Context context) {
        final int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        RadioFavoritesStore store = new RadioFavoritesStore(context);
        if (store.anyFavoriteExists()) {
            androidx.core.app.TaskStackBuilder tsb = androidx.core.app.TaskStackBuilder.create(context);
            tsb.addNextIntent(new Intent(context, com.driot.bookplayer.activities.MainActivity.class));
            tsb.addNextIntent(new Intent(context, com.driot.bookplayer.activities.GetRadioActivity.class));
            tsb.addNextIntent(new Intent(context, com.driot.bookplayer.activities.RadioFavoritesActivity.class));
            return tsb.getPendingIntent(0, flags);
        } else {
            return PendingIntent.getActivity(
                    context,
                    0,
                    new Intent(context, com.driot.bookplayer.activities.GetRadioActivity.class)
                            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP),
                    flags
            );
        }
    }
    public static Intent getNavToRadioActivityIntent(Context context) {
        RadioFavoritesStore store = new RadioFavoritesStore(context);
        if (store.anyFavoriteExists()) {
            // Directly go to favorites when user taps the Radio tab
            return new Intent(context, com.driot.bookplayer.activities.RadioFavoritesActivity.class);
        } else {
            // No favorites yet: go to search
            return new Intent(context, com.driot.bookplayer.activities.GetRadioActivity.class);
        }
    }

    public static void navigateToPodcastSection(Activity activity) {
        AppDatabase.databaseReadExecutor.execute(() -> {
            int nbFavorite = AppDatabase.getDatabase(activity)
                    .podcastDao()
                    .getFavoriteCount();

            Intent intent;
            if (nbFavorite > 0) {
                intent = new Intent(activity, com.driot.bookplayer.activities.PodcastFavoritesActivity.class);
            } else {
                intent = new Intent(activity, com.driot.bookplayer.activities.GetPodcastActivity.class);
            }
            activity.runOnUiThread(() -> {
                activity.startActivity(intent);
                activity.overridePendingTransition(0, 0);
            });
        });
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
