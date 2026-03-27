package com.driot.bookplayer.nav;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import androidx.core.app.TaskStackBuilder;

import com.driot.bookplayer.R;
import com.driot.bookplayer.activities.GetActivity;
import com.driot.bookplayer.activities.SettingsActivity;
import com.driot.bookplayer.activities.MainActivity;
import com.driot.bookplayer.activities.ZikFileActivity;
import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.db.ZikFile;
import com.driot.bookplayer.global.Intents;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.player.PlayActivity;
import com.driot.bookplayer.player.PlayList;

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

    public static PendingIntent getNavToRadioActivityPendingIntent(Context context, String stationUuid) {
        return null;
    }
    public static PendingIntent getNavToRadioActivityPendingIntent(Context context, long id) {
        return null;
    }

    public static boolean handleBottomNavClick(Activity activity, int navId) {
        myLogE("handleBottomNavClick should not be called in pure");
        return false;
    }

    public static void navigateToRadioSection(Activity activity, boolean removeTransitions) {
        myLogE("navigateToRadioSection should not be called in pure");
    }

    public static void navigateToPodcastSection(Activity activity, boolean removeTransitions) {
        myLogE("navigateToPodcastSection should not be called in pure");
    }

    private static void myLogDD(String txt) {
        if (VERBOSE_DEBUG)
            myLogD(txt);
    }

}
