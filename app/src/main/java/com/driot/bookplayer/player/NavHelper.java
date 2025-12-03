package com.driot.bookplayer.player;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import androidx.core.app.TaskStackBuilder;

import com.driot.bookplayer.activities.GetLibrivoxActivity;
import com.driot.bookplayer.activities.GetPodcastActivity;
import com.driot.bookplayer.activities.GetRadioActivity;
import com.driot.bookplayer.activities.MainActivity;
import com.driot.bookplayer.activities.PodcastFavoritesActivity;
import com.driot.bookplayer.activities.RadioFavoritesActivity;
import com.driot.bookplayer.activities.ZikFileActivity;
import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.db.ZikFile;
import com.driot.bookplayer.global.Intents;
import com.driot.bookplayer.radio.RadioStationDao;

import static com.driot.bookplayer.utils.log.LoggerStaticHelper.*;

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

        Context appCtx = context.getApplicationContext();

        boolean hasFavOrHistory = false;
        try {
            // Run the Room query on the DB executor, not on the main thread
            hasFavOrHistory = AppDatabase.databaseReadExecutor
                    .submit(() -> {
                        RadioStationDao dao = AppDatabase.getInstance(appCtx).radioStationDao();
                        return dao.anyFavoriteOrHistoryExists();
                    })
                    .get(); // wait for result (fast)
        } catch (Exception e) {
            myLogEE(e, "getNavToRadioActivityPendingIntent: DB check failed");
        }

        if (hasFavOrHistory) {
            // Build back stack: Main -> GetRadio -> RadioFavorites
            TaskStackBuilder tsb = TaskStackBuilder.create(context);
            tsb.addNextIntent(new Intent(context, MainActivity.class));
            tsb.addNextIntent(new Intent(context, GetRadioActivity.class));
            tsb.addNextIntent(new Intent(context, RadioFavoritesActivity.class));
            return tsb.getPendingIntent(0, flags);
        } else {
            // No favorites/history → go straight to radio search
            return PendingIntent.getActivity(
                    context,
                    0,
                    new Intent(context, GetRadioActivity.class)
                            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP),
                    flags
            );
        }
    }

    public static void navigateToRadioSection(Activity activity, boolean removeTransitions) {
        Context appCtx = activity.getApplicationContext();

        AppDatabase.databaseWriteExecutor.execute(() -> {
            RadioStationDao dao = AppDatabase.getInstance(appCtx).radioStationDao();
            boolean hasFavOrHistory = dao.anyFavoriteOrHistoryExists();

            activity.runOnUiThread(() -> {
                if (hasFavOrHistory) {
                    // 1) Base screen in stack: radio search/list
                    Intent listIntent = new Intent(
                            activity,
                            GetRadioActivity.class
                    );

                    // 2) Top screen shown to the user: favorites/history screen
                    Intent favIntent = new Intent(
                            activity,
                            RadioFavoritesActivity.class
                    );

                    // Build back stack: GetRadio -> RadioFavorites
                    activity.startActivities(new Intent[]{ listIntent, favIntent });

                } else {
                    // No favorites and no history yet: go to radio search
                    Intent intent = new Intent(activity, GetRadioActivity.class);
                    activity.startActivity(intent);
                }

                if (removeTransitions) {
                    activity.overridePendingTransition(0, 0);
                }
            });
        });
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
                new Intent(context, GetLibrivoxActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP),
                flags
        );
    }
}
