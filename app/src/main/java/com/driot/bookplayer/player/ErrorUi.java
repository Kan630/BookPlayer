package com.driot.bookplayer.player;

import static com.driot.bookplayer.utils.PermissionRequest.isReadAudioPermissionGranted;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;

import com.driot.bookplayer.R;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.helpers.FileHelper;
import com.driot.bookplayer.helpers.StorageHelper;
import com.driot.bookplayer.helpers.UriHelper;
import com.driot.bookplayer.utils.MsgBox;
import static com.driot.bookplayer.utils.log.LoggerStaticHelper.*;

public class ErrorUi {

    public static void showPlayAudioErrorMessage(Context context, String errMessage, String zikFilePath) {
        myLogW("showPlayAudioErrorMessage(" + errMessage + ", " + zikFilePath + ")");
        String pathText = null;
        String newErrorMessage = null;
        try {
            if (zikFilePath == null) {
                PlayList pl = PlayList.getInstance();
                if (pl != null && pl.getZikFile() != null) {
                    zikFilePath = pl.getZikFile().getPath();
                }
            }
            if (zikFilePath != null) {
                pathText = context.getString(R.string.source_file_path) + " = \n[" + Uri.decode(zikFilePath) + "]";
                newErrorMessage = getErrorMessage(context, zikFilePath);
            }

            if (errMessage == null && newErrorMessage != null) {
                errMessage = newErrorMessage;
            } else if (newErrorMessage != null) {
                errMessage = errMessage + "\n\n" + newErrorMessage;
            }

            if (newErrorMessage != null && newErrorMessage.equals(context.getString(R.string.permission_not_set))) {
                // add button
                Intent appDetails = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        .setData(Uri.fromParts("package", context.getPackageName(), null));
                if (!(context instanceof android.app.Activity)) {
                    appDetails.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                }
                MsgBox.alertWithNeutral(
                        context,
                        context.getString(R.string.error_reading_track),
                        errMessage,
                        pathText,
                        context.getString(R.string.settings),
                        appDetails);
            } else {
                MsgBox.alert(context, context.getString(R.string.error_reading_track), errMessage, pathText);
            }

        } catch (Throwable t) {
            myToastEE(t, context.getString(R.string.error_reading_track));
        }
    }

    public static String getErrorMessage(Context context, String zikFilePath) {
        String errMessage;

        boolean exists = (UriHelper.resolveUriFromPath(context, zikFilePath) != null);
        myLog("playlist file exist = " + exists + " : [" + zikFilePath + "]");

        if (!exists) {
            if (StorageHelper.isInInternalMemory(zikFilePath)) {
                myLogW(Var.SHOULD_NOT_HAPPEN);
                errMessage = context.getString(R.string.source_not_found) + "\n- this " + Var.SHOULD_NOT_HAPPEN + " -";
            } else {
                errMessage = context.getString(R.string.source_not_found_deleted);
            }
        } else {
            StorageHelper.MemoryLocationType location = StorageHelper.getMemoryLocationType(context, zikFilePath);
            boolean isReserved = (location == StorageHelper.MemoryLocationType.INTERNAL_RESERVED
                    || location == StorageHelper.MemoryLocationType.SDCARD_RESERVED);

            if (!isReadAudioPermissionGranted(context) && !isReserved) {
                errMessage = context.getString(R.string.permission_not_set);
            } else {
                myLogW(Var.SHOULD_NOT_HAPPEN);
                errMessage = context.getString(R.string.source_not_found) + "\n- this " + Var.SHOULD_NOT_HAPPEN + " -";
            }
        }
        return errMessage;
    }

}
