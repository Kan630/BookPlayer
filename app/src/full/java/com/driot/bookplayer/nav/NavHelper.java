package com.driot.bookplayer.nav;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import androidx.core.app.TaskStackBuilder;

import com.driot.bookplayer.R;
import com.driot.bookplayer.activities.GetLibrivoxActivity;
import com.driot.bookplayer.player.PlayActivity;
import com.driot.bookplayer.player.PlayList;
import com.driot.bookplayer.podcasts.GetPodcastActivity;
import com.driot.bookplayer.activities.SettingsActivity;
import com.driot.bookplayer.imports.ImportBookMultipleActivity;
import com.driot.bookplayer.radio.GetRadioActivity;
import com.driot.bookplayer.activities.MainActivity;
import com.driot.bookplayer.podcasts.PodcastFavoritesActivity;
import com.driot.bookplayer.radio.RadioFavoritesActivity;
import com.driot.bookplayer.activities.ZikFileActivity;
import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.db.ZikFile;
import com.driot.bookplayer.global.Intents;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.radio.RadioStationActivity;
import com.driot.bookplayer.db.RadioStationDao;
import com.driot.bookplayer.activities.AddResourceActivity; // Assuming this is the correct import for AddResourceActivity

import static com.driot.bookplayer.utils.log.LoggerStaticHelper.*;

public class NavHelper {

    private static final boolean VERBOSE_DEBUG = false;

    public static PendingIntent navigateToMain(Context context) {
        final int pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getActivity(
                context,
                0,
                new Intent(context, MainActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP),
                pendingFlags);
    }

    public static PendingIntent mediaServiceClickNavigateToActivity(Context context) {
        final int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;

        TaskStackBuilder tsb = TaskStackBuilder.create(context);
        // 1) Always start at Main
        tsb.addNextIntent(new Intent(context, MainActivity.class));

        // 2) If multiple tracks, insert the track list screen before PlayActivity
        PlayList pl = PlayList.getInstance();
        ZikFile z = (pl != null) ? pl.getZikFile() : null;
        long folderId = (z != null) ? z.getIdFolder() : -1;
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

    public static void openRadioStationActivity(Context context, long trackId) {
        if (trackId <= 0) {
            myLogE("openRadioStationActivity => no trackId");
            context.startActivity(new Intent(context, GetRadioActivity.class));
            return;
        }

        AppDatabase.databaseWriteExecutor.execute(() -> {
            String uuid = null;
            try {
                RadioStationDao dao = AppDatabase.getDatabase(context).radioStationDao();
                uuid = dao.findById(trackId).stationuuid;
            } catch (Exception e) {
                myLogEE(e, "openRadioStationActivity: UUID lookup failed for trackId=" + trackId);
            }

            openRadioStationActivityFromUuid(context, uuid);
        });
    }

    public static void openRadioStationActivityFromUuid(Context context, String uuid) {
        if (uuid == null || uuid.isEmpty()) {
            context.startActivity(new Intent(context, GetRadioActivity.class));
            return;
        }
        context.startActivity(
                new Intent(context, RadioStationActivity.class).putExtra(Intents.EXTRA_STATION_UUID, uuid));
    }

    public static PendingIntent getNavToRadioActivityPendingIntent(Context context, long trackId) {
        final int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        Context appCtx = context.getApplicationContext();

        if (trackId > 0) {
            String uuid = null;
            try {
                uuid = AppDatabase.databaseReadExecutor
                        .submit(() -> {
                            RadioStationDao dao = AppDatabase.getInstance(appCtx).radioStationDao();
                            return dao.findById(trackId).stationuuid;
                        })
                        .get(); // because off main thread
            } catch (Exception e) {
                myLogEE(e, "getNavToRadioActivityPendingIntent: UUID lookup failed for trackId=" + trackId);
            }

            TaskStackBuilder tsb = TaskStackBuilder.create(context);
            tsb.addNextIntent(new Intent(context, MainActivity.class));
            // tsb.addNextIntent(new Intent(context, GetRadioActivity.class));
            tsb.addNextIntent(new Intent(context, RadioFavoritesActivity.class)
                    .putExtra(Intents.EXTRA_OPEN_FROM_TRACK_ID, trackId));
            tsb.addNextIntent(new Intent(context, RadioStationActivity.class)
                    .putExtra(Intents.EXTRA_STATION_UUID, uuid));
            return tsb.getPendingIntent(0, flags);
        }

        // ... rest of the logic for no stationUuid ...
        boolean hasFavOrHistory = false;
        try {
            hasFavOrHistory = AppDatabase.databaseReadExecutor
                    .submit(() -> {
                        RadioStationDao dao = AppDatabase.getInstance(appCtx).radioStationDao();
                        return dao.anyFavoriteOrHistoryExists();
                    })
                    .get();
        } catch (Exception e) {
            myLogEE(e, "getNavToRadioActivityPendingIntent: DB check failed");
        }

        if (Option.getRadioOpenFavoritesFirst() && hasFavOrHistory) {
            TaskStackBuilder tsb = TaskStackBuilder.create(context);
            tsb.addNextIntent(new Intent(context, MainActivity.class));
            tsb.addNextIntent(new Intent(context, GetRadioActivity.class));
            tsb.addNextIntent(new Intent(context, RadioFavoritesActivity.class)
                    .putExtra(Intents.EXTRA_OPEN_FROM_TRACK_ID, trackId));
            return tsb.getPendingIntent(0, flags);
        } else {
            return PendingIntent.getActivity(
                    context,
                    0,
                    new Intent(context, GetRadioActivity.class), flags);
        }
    }

    /**
     * Handles clicks on the bottom navigation bar.
     * Starts the requested activity with a full back-stack (Main -> Target).
     */
    public static boolean handleBottomNavClick(Activity activity, int itemId) {
        myLogDD("NavHelper.handleBottomNavClick id=" + itemId);

        int currentItemId = NavState.getInstance().getCurrentBottomNavId();

        // 1. Resolve target Intent
        Intent targetIntent = null;
        if (itemId == R.id.nav_radio) {
                targetIntent = NavState.getInstance().getLastRadioIntent();
                if (targetIntent == null) {
                        targetIntent = new Intent(activity, GetRadioActivity.class);
                }
        } else if (itemId == R.id.nav_podcast) {
                targetIntent = NavState.getInstance().getLastPodcastIntent();
                if (targetIntent == null) {
                        targetIntent = new Intent(activity, GetPodcastActivity.class);
                }
        } else if (itemId == R.id.nav_settings) {
                targetIntent = new Intent(activity, SettingsActivity.class);
        } else if (itemId == R.id.nav_library) {
                targetIntent = new Intent(activity, MainActivity.class);
        } else if (itemId == R.id.nav_add) {
                targetIntent = new Intent(activity, AddResourceActivity.class);
        }

        if (targetIntent == null) return false; // Changed from return; to return false; to match method signature

        // 2. Handle same-tab click (refresh/back to root)
        if (itemId == currentItemId) {
                // User clicked the tab they are already on.
                // Standard behavior: pop to root of this tab.
                // If it's already the root (e.g. GetRadioActivity), we might want to refresh.
                if (activity.getClass().getName().equals(GetRadioActivity.class.getName()) ||
                    activity.getClass().getName().equals(GetPodcastActivity.class.getName()) ||
                    activity.getClass().getName().equals(MainActivity.class.getName())) {
                        // Already at root, just refresh if needed or do nothing.
                        return true; // Changed from return; to return true;
                }
                // Otherwise, start the root activity of this tab to "pop"
                Intent rootIntent = null;
                if (itemId == R.id.nav_radio) rootIntent = new Intent(activity, GetRadioActivity.class);
                else if (itemId == R.id.nav_podcast) rootIntent = new Intent(activity, GetPodcastActivity.class);
                else if (itemId == R.id.nav_library) rootIntent = new Intent(activity, MainActivity.class);

                if (rootIntent != null) {
                        rootIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                        activity.startActivity(rootIntent);
                }
                return true; // Changed from return; to return true;
        }

        // 3. Switch to different tab
        targetIntent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        activity.startActivity(targetIntent);
        activity.overridePendingTransition(0, 0);

        NavState.getInstance().setCurrentBottomNavId(itemId);
        NavState.getInstance().setLastIntent(itemId, targetIntent);

        return true;
    }

    public static void navigateToRadioSection(Activity activity, boolean removeTransitions) {
        myLogD("navigateToRadioSection start");
        Context appCtx = activity.getApplicationContext();

        AppDatabase.databaseWriteExecutor.execute(() -> {
            myLogD("navigateToRadioSection: inside executor");
            RadioStationDao dao = AppDatabase.getInstance(appCtx).radioStationDao();
            boolean hasFavOrHistory = dao.anyFavoriteOrHistoryExists();
            myLogD("navigateToRadioSection: hasFavOrHistory=" + hasFavOrHistory);

            activity.runOnUiThread(() -> {
                myLogD("navigateToRadioSection: runOnUiThread start");
                // Prepare base intent (MainActivity) to ensure correct back stack
                Intent mainIntent = new Intent(activity, MainActivity.class);
                mainIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

                if (Option.getRadioOpenFavoritesFirst() && hasFavOrHistory) {
                    // 1) Base screen in stack: radio search/list
                    Intent listIntent = new Intent(activity, GetRadioActivity.class);

                    // 2) Top screen shown to the user: favorites/history screen
                    Intent favIntent = new Intent(activity, RadioFavoritesActivity.class);

                    // Build back stack: Main -> GetRadio -> RadioFavorites
                    activity.startActivities(new Intent[] { mainIntent, listIntent, favIntent });

                } else {
                    // No favorites and no history yet: go to radio search
                    Intent intent = new Intent(activity, GetRadioActivity.class);

                    // Build back stack: Main -> GetRadio
                    activity.startActivities(new Intent[] { mainIntent, intent });
                }

                if (removeTransitions) {
                    activity.overridePendingTransition(0, 0);
                }
                myLogD("navigateToRadioSection: runOnUiThread done");
            });
        });
    }

    public static void navigateToPodcastSection(Activity activity, boolean removeTransitions) {
        myLogD("navigateToPodcastSection start");
        AppDatabase.databaseReadExecutor.execute(() -> {
            myLogD("navigateToPodcastSection: inside executor");
            int nbFavorite = AppDatabase.getDatabase(activity)
                    .podcastDao()
                    .getFavoriteCount();
            myLogD("navigateToPodcastSection: nbFavorite=" + nbFavorite);

            activity.runOnUiThread(() -> {
                myLogD("navigateToPodcastSection: runOnUiThread start");
                // Prepare base intent (MainActivity) to ensure correct back stack
                Intent mainIntent = new Intent(activity, MainActivity.class);
                mainIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

                if (Option.getPodcastOpenFavoritesFirst() && nbFavorite > 0) {
                    // 1) Base screen in stack
                    Intent listIntent = new Intent(activity, GetPodcastActivity.class);

                    // 2) Top screen shown to the user
                    Intent favIntent = new Intent(activity, PodcastFavoritesActivity.class);

                    // Build back stack: Main -> GetPodcast -> PodcastFavorites
                    activity.startActivities(new Intent[] { mainIntent, listIntent, favIntent });
                } else {
                    // No favorites → go directly to GetPodcast
                    Intent intent = new Intent(activity, GetPodcastActivity.class);
                    // Build back stack: Main -> GetPodcast
                    activity.startActivities(new Intent[] { mainIntent, intent });
                }
                if (removeTransitions)
                    activity.overridePendingTransition(0, 0);
                myLogD("navigateToPodcastSection: runOnUiThread done");
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
                flags);
    }

    public static PendingIntent navigateToLibrivoxActivity(Context context) {
        final int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getActivity(
                context,
                0,
                new Intent(context, GetLibrivoxActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP),
                flags);
    }

    private static void myLogDD(String txt) {
        if (VERBOSE_DEBUG)
            myLogD(txt);
    }

}
