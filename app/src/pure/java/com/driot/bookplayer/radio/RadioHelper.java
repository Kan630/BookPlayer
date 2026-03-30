package com.driot.bookplayer.radio;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import static com.driot.bookplayer.utils.log.LoggerStaticHelper.*;

import com.driot.bookplayer.db.BackupManager;

import java.util.Collections;
import java.util.List;

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
        myLogE("should never happen");
        return false;
    }

    public static boolean backupDataHasRadios(BackupManager.BackupData data) {
        myLog("stub!");
        return false;
    }

    public static void addSecondToTimeListened(Context context, long trackId) {
        myLogE("should never happen");
    }

    public static boolean hasFavorites(Context context) {
        myLog("stub!");
        return false;
    }

    public static List getFavoriteRadios(Context context) {
        myLog("stub!");
        return Collections.emptyList();
    }

    public static void playRadioByUuid(Context context, String uuid, String caller) {
        myLogE("should never happen");
    }

    public static Intent getSectionRootIntent(Context context) {
        return null;
    }

    public static Intent getFavoritesSectionIntent(Context context) {
        return null;
    }

    public static void openRadioStationActivity(Context context, long trackId) {
        myLog("stub!");
    }

    public static void openRadioStationActivityFromUuid(Context context, String uuid) {
        myLog("stub!");
    }

    public static PendingIntent getNavToRadioActivityPendingIntent(Context context, long trackId) {
        return null;
    }

}
