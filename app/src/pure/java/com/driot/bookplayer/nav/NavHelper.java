package com.driot.bookplayer.nav;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import androidx.core.app.TaskStackBuilder;

import com.driot.bookplayer.R;
import com.driot.bookplayer.activities.GetActivity;
import com.driot.bookplayer.activities.GetLibrivoxActivity;
import com.driot.bookplayer.activities.SettingsActivity;
import com.driot.bookplayer.imports.ImportBookMultipleActivity;
import com.driot.bookplayer.activities.MainActivity;
import com.driot.bookplayer.activities.ZikFileActivity;
import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.db.ZikFile;
import com.driot.bookplayer.global.Intents;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.player.PlayActivity;
import com.driot.bookplayer.player.PlayList;

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

    public static PendingIntent getNavToRadioActivityPendingIntent(Context context, String stationUuid) {
        return null;
    }

    /**
     * Handles clicks on the bottom navigation bar.
     * Starts the requested activity with a full back-stack (Main -> Target).
     */
    public static boolean handleBottomNavClick(Activity activity, int navId) {
        myLogDD("NavHelper.handleBottomNavClick id=" + navId);
        if (navId == R.id.nav_library) {
            myLogD("NavLibrary clicked");
            // Just go back to MainActivity (singleTop will handle it if we are already
            // there,
            // or clearTop if we are deep in stack)
            Intent intent = new Intent(activity, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            activity.startActivity(intent);
            return true;

        } else if (navId == R.id.nav_radio) {
            myLogD("NavRadio clicked");
            navigateToRadioSection(activity, true);
            return true;

        } else if (navId == R.id.nav_podcast) {
            myLogD("NavPodcast clicked");
            navigateToPodcastSection(activity, true);
            return true;

        } else if (navId == R.id.nav_add) {
            myLogD("NavAdd clicked");

            com.driot.bookplayer.imports.MassImportRepository repo = com.driot.bookplayer.imports.MassImportRepository
                    .getInstance();
            if (repo != null) {
                Boolean scanning = repo.getIsScanning().getValue();
                Boolean finished = repo.getIsScanFinished().getValue();

                if (Boolean.TRUE.equals(scanning) || Boolean.TRUE.equals(finished)) {
                    Intent intent = new Intent(activity, ImportBookMultipleActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    activity.startActivity(intent);
                    return true;
                }
            }

            activity.startActivity(new Intent(activity, GetActivity.class));
            return true;

        } else if (navId == R.id.nav_settings) {
            myLogD("NavSettings clicked");
            activity.startActivity(new Intent(activity, SettingsActivity.class));
            return true;
        }

        return false;
    }

    public static void navigateToRadioSection(Activity activity, boolean removeTransitions) {
        myLogD("stub!");
    }

    public static void navigateToPodcastSection(Activity activity, boolean removeTransitions) {
        myLogD("stub!");
    }

    private static void myLogDD(String txt) {
        if (VERBOSE_DEBUG)
            myLogD(txt);
    }

}
