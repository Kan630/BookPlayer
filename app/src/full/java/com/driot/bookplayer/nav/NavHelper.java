package com.driot.bookplayer.nav;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import androidx.core.app.TaskStackBuilder;

import com.driot.bookplayer.R;
import com.driot.bookplayer.activities.GetActivity;
import com.driot.bookplayer.player.PlayActivity;
import com.driot.bookplayer.player.PlayList;
import com.driot.bookplayer.podcasts.GetPodcastActivity;
import com.driot.bookplayer.activities.SettingsActivity;
import com.driot.bookplayer.radio.GetRadioActivity;
import com.driot.bookplayer.activities.MainActivity;
import com.driot.bookplayer.radio.RadioFavoritesActivity;
import com.driot.bookplayer.activities.ZikFileActivity;
import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.db.ZikFile;
import com.driot.bookplayer.global.Intents;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.radio.RadioStationActivity;
import com.driot.bookplayer.db.RadioStationDao;

import static com.driot.bookplayer.utils.log.LoggerStaticHelper.*;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class NavHelper {

    private final NavState navState;
    private static final boolean VERBOSE_DEBUG = false;

    @Inject
    public NavHelper(NavState navState) {
        this.navState = navState;
    }

    public static PendingIntent navigateToMain(Context context) {
        // ... (existing static methods stay as they are if they don't need NavState)
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
            tsb.addNextIntent(new Intent(context, RadioFavoritesActivity.class)
                    .putExtra(Intents.EXTRA_OPEN_FROM_TRACK_ID, trackId));
            tsb.addNextIntent(new Intent(context, RadioStationActivity.class)
                    .putExtra(Intents.EXTRA_STATION_UUID, uuid));
            return tsb.getPendingIntent(0, flags);
        }

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
     * Uses NavState to restore the last activity for the tab if available.
     */
    public boolean handleBottomNavClick(Activity activity, int itemId) {
        myLogDD("NavHelper.handleBottomNavClick id=" + itemId);

        int currentItemId = navState.getCurrentBottomNavId();

        // 1. Resolve target Intent
        Intent targetIntent = navState.getLastIntent(itemId);
        
        if (targetIntent == null) {
            // No saved state for this tab — create a fresh root intent and mark it as section root
            if (itemId == R.id.nav_radio) {
                targetIntent = new Intent(activity, GetRadioActivity.class);
            } else if (itemId == R.id.nav_podcast) {
                targetIntent = new Intent(activity, GetPodcastActivity.class);
            } else if (itemId == R.id.nav_settings) {
                targetIntent = new Intent(activity, SettingsActivity.class);
            } else if (itemId == R.id.nav_library) {
                targetIntent = new Intent(activity, MainActivity.class);
            } else if (itemId == R.id.nav_add) {
                targetIntent = new Intent(activity, GetActivity.class);
            }
            if (targetIntent != null) {
                targetIntent.putExtra(Intents.EXTRA_IS_SECTION_ROOT, true);
            }
        } else {
            // Ensure we don't carry over stale flags from previous saves
            targetIntent.setFlags(0);
        }

        if (targetIntent == null) return false;

        // 2. Handle same-tab click (refresh/back to root)
        if (itemId == currentItemId) {
            // Standard behavior: pop to root of this tab.
            Intent rootIntent = null;
            if (itemId == R.id.nav_radio) rootIntent = new Intent(activity, GetRadioActivity.class);
            else if (itemId == R.id.nav_podcast) rootIntent = new Intent(activity, GetPodcastActivity.class);
            else if (itemId == R.id.nav_library) rootIntent = new Intent(activity, MainActivity.class);
            else if (itemId == R.id.nav_add) rootIntent = new Intent(activity, GetActivity.class);
            else if (itemId == R.id.nav_settings) rootIntent = new Intent(activity, SettingsActivity.class);

            if (rootIntent != null) {
                rootIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                rootIntent.putExtra(Intents.EXTRA_IS_SECTION_ROOT, true);
                activity.startActivity(rootIntent);
            }
            return true;
        }

        // 3. Switch to different tab
        targetIntent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        activity.startActivity(targetIntent);
        activity.overridePendingTransition(0, 0);

        navState.setCurrentBottomNavId(itemId);

        return true;
    }

    private static void myLogDD(String txt) {
        if (VERBOSE_DEBUG)
            myLogD(txt);
    }

}
