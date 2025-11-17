package com.driot.bookplayer.player;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import androidx.core.app.TaskStackBuilder;

import com.driot.bookplayer.activities.GetAudiobookActivity;
import com.driot.bookplayer.activities.GetPodcastActivity;
import com.driot.bookplayer.activities.GetRadioActivity;
import com.driot.bookplayer.activities.MainActivity;
import com.driot.bookplayer.activities.PodcastFavoritesActivity;
import com.driot.bookplayer.activities.RadioFavoritesActivity;
import com.driot.bookplayer.activities.ZikFileActivity;
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
                new Intent(context, MainActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP),
                pendingFlags
        );
    }

    public static PendingIntent navigateToActivity(Context context) {
        final int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;

        TaskStackBuilder tsb = TaskStackBuilder.create(context);
        // 1) Always start at Main
        tsb.addNextIntent(new Intent(context, MainActivity.class));

        // 2) If multiple tracks, insert the track list screen before PlayActivity
        PlayList pl = PlayList.getInstance();
        ZikFile z = (pl != null) ? pl.getZikFile() : null;
        int folderId = (z != null) ? z.getIdFolder() : -1;
        if (folderId > 0 && pl.getSize() > 1) {
            Intent trackList = new Intent(context, ZikFileActivity.class)
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
            TaskStackBuilder tsb = TaskStackBuilder.create(context);
            tsb.addNextIntent(new Intent(context, MainActivity.class));
            tsb.addNextIntent(new Intent(context, GetRadioActivity.class));
            tsb.addNextIntent(new Intent(context, RadioFavoritesActivity.class));
            return tsb.getPendingIntent(0, flags);
        } else {
            return PendingIntent.getActivity(
                    context,
                    0,
                    new Intent(context, GetRadioActivity.class)
                            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP),
                    flags
            );
        }
    }
    public static Intent getNavToRadioActivityIntent(Context context) {
        RadioFavoritesStore store = new RadioFavoritesStore(context);
        if (store.anyFavoriteExists()) {
            // Directly go to favorites when user taps the Radio tab
            return new Intent(context, RadioFavoritesActivity.class);
        } else {
            // No favorites yet: go to search
            return new Intent(context, GetRadioActivity.class);
        }
    }

    public static void navigateToRadioSection(Activity activity, boolean removeTransitions) {
        RadioFavoritesStore store = new RadioFavoritesStore(activity.getApplicationContext());

        if (store.anyFavoriteExists()) {
            // 1) Base screen in stack: radio search/list
            Intent listIntent = new Intent(
                    activity,
                    GetRadioActivity.class
            );
            // Optional: reuse existing if already on top
            //listIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

            // 2) Top screen shown to the user: favorites
            Intent favIntent = new Intent(
                    activity,
                    RadioFavoritesActivity.class
            );

            // Build back stack: GetRadio -> RadioFavorites
            activity.startActivities(new Intent[]{ listIntent, favIntent });

        } else {
            // No favorites yet: go to radio search
            Intent intent = new Intent(activity, GetRadioActivity.class);
            activity.startActivity(intent);
        }
        if (removeTransitions) activity.overridePendingTransition(0, 0);
    }


    public static void navigateToPodcastSection(Activity activity, boolean removeTransitions) {
        AppDatabase.databaseReadExecutor.execute(() -> {
            int nbFavorite = AppDatabase.getDatabase(activity)
                    .podcastDao()
                    .getFavoriteCount();

            activity.runOnUiThread(() -> {
                if (nbFavorite > 0) {
                    // 1) Base screen in stack
                    Intent listIntent = new Intent(
                            activity,
                            GetPodcastActivity.class
                    );
                    //listIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

                    // 2) Top screen shown to the user
                    Intent favIntent = new Intent(
                            activity,
                            PodcastFavoritesActivity.class
                    );

                    // Build back stack: GetPodcast -> PodcastFavorites
                    activity.startActivities(new Intent[]{ listIntent, favIntent });
                } else {
                    // No favorites → go directly to GetPodcast
                    Intent intent = new Intent(activity, GetPodcastActivity.class);
                    activity.startActivity(intent);
                }
                if (removeTransitions) activity.overridePendingTransition(0, 0);
            });
        });
    }





    public static PendingIntent navigateToPodcastActivity(Context context) {
        final int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getActivity(
                context,
                0,
                new Intent(context, GetPodcastActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP),
                flags
        );
    }

    public static PendingIntent navigateToLibrivoxActivity(Context context) {
        final int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getActivity(
                context,
                0,
                new Intent(context, GetAudiobookActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP),
                flags
        );
    }
}
