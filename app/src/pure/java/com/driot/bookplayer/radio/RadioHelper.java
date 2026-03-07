package com.driot.bookplayer.radio;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import static com.driot.bookplayer.utils.log.LoggerStaticHelper.*;

import com.driot.bookplayer.db.BackupManager;
import com.driot.bookplayer.global.Intents;
import com.driot.bookplayer.player.StartPlayHelper;

public class RadioHelper {

    public static void handleRadioImages(Context context, long currentTime) {
        myLog("stub!");
    }

    public static void handleDeepLink(Context context, Uri data) {
        myLog("stub!");
    }

    public static void initRadioBrowserServiceFactory(Context context) {
        myLog("stub!");
    }

    public static boolean playStreamIfKnownRadio(Context context, String url) {
        return false;
    }

    public static boolean backupDataHasRadios(BackupManager.BackupData data) {
        return false;
    }

    public static void addSecondToTimeListened(Context context, long trackId) {
        myLogE("should never happen");
    }

    // ---- Android Auto Helpers Stubs ----

    public static boolean hasFavorites(Context context) {
        return false;
    }

    public static List<MediaBrowserCompat.MediaItem> getFavoriteRadios(
            Context context) {
        return Collections.emptyList();
    }

    public static void playRadioByUuid(Context context, String uuid, String caller) {
        myLogE("should never happen");
    }

}
