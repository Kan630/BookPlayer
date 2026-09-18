package com.driot.bookplayer.nav;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import androidx.core.app.TaskStackBuilder;

import com.driot.bookplayer.R;
import com.driot.bookplayer.player.PlayActivity;
import com.driot.bookplayer.player.PlayList;
import com.driot.bookplayer.activities.MainActivity;
import com.driot.bookplayer.db.ZikFile;
import com.driot.bookplayer.global.Intents;
import com.driot.bookplayer.radio.RadioHelper;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class NavHelper {

    private final NavState navState;
    /** Also read by NavState (its own myLogDD helper) - not just this class. */
    public static final boolean VERBOSE_DEBUG = true;

    @Inject
    public NavHelper(NavState navState) {
        this.navState = navState;
    }

    public void resetAddBookNav() {
        navState.clear(R.id.nav_add);
    }

    public void removeAddBookNavSpecial() {
        navState.removeAddBookNavSpecial();
    }

    public void reInitNavState() {
        navState.reInitNavState();
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

        // 1) Always start at Main - if multiple tracks, ask it to navigate its own internal
        // Library NavController straight to the track list (formerly a separate ZikFileActivity
        // TaskStackBuilder entry - MainActivity.handleIntentNavigation() does the equivalent
        // navigation internally now, so pressing back from PlayActivity still lands on the same
        // track list it always did, just one Activity layer thinner).
        // (radio/podcast/preview are stream playlists with no ZikFile backing - skip the lookup,
        // it would just log a spurious "out of bounds" error and return null anyway)
        Intent mainIntent = new Intent(context, MainActivity.class);
        PlayList pl = PlayList.getInstance();
        ZikFile z = (pl != null && !pl.isStream()) ? pl.getZikFile() : null;
        long folderId = (z != null) ? z.getIdFolder() : -1;
        if (folderId > 0 && pl.getSize() > 1) {
            mainIntent.putExtra(Intents.EXTRA_FOLDER_ID, folderId);
        }
        tsb.addNextIntent(mainIntent);

        // 2) Finally PlayActivity (singleTop/clearTop like you already do)
        tsb.addNextIntent(new Intent(context, PlayActivity.class)
                .putExtra(Intents.EXTRA_AUTOPLAY, false));

        return tsb.getPendingIntent(0, flags);
    }

    public static void openRadioStationActivity(Context context, long trackId) {
        RadioHelper.openRadioStationActivity(context, trackId);
    }

    public static void openRadioStationActivityFromUuid(Context context, String uuid) {
        RadioHelper.openRadioStationActivityFromUuid(context, uuid);
    }

    public static PendingIntent getNavToRadioActivityPendingIntent(Context context, long trackId) {
        return RadioHelper.getNavToRadioActivityPendingIntent(context, trackId);
    }

}
