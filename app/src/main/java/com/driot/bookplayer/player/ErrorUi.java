package com.driot.bookplayer.player;

import static com.driot.bookplayer.utils.PermissionRequest.isReadAudioPermissionGranted;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;

import com.driot.bookplayer.R;
import com.driot.bookplayer.helpers.FileHelper;
import com.driot.bookplayer.helpers.StorageHelper;
import com.driot.bookplayer.utils.MsgBox;
import static com.driot.bookplayer.utils.log.LoggerStaticHelper.*;


public class ErrorUi {


    public static void showPlayAudioErrorMessage(Context context, String errMessage) {
        try {
            String pathText = null;
            PlayList pl = PlayList.getInstance();
            if (pl != null && pl.getZikFile() != null) {
                String zikFilePath = pl.getZikFile().getPath();
                pathText =  context.getString(R.string.source_file_path) + " = \n[" + Uri.decode(zikFilePath) + "]";
                boolean exists = FileHelper.exists(zikFilePath);

                if (errMessage == null || errMessage.isEmpty()) {
                    if (!exists) {
                        if (StorageHelper.isInInternalMemory(zikFilePath)) {
                            errMessage = context.getString(R.string.source_not_found);
                        } else {
                            errMessage = context.getString(R.string.source_not_found_deleted);
                        }
                    } else {
                        if (!isReadAudioPermissionGranted(context)) {
                            errMessage = context.getString(R.string.permission_not_set);
                            MsgBox.alertWithNeutral(
                                    context,
                                    context.getString(R.string.error_reading_track),
                                    errMessage,
                                    pathText,
                                    context.getString(R.string.settings),
                                    new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                            .setData(android.net.Uri.fromParts("package", context.getPackageName(), null))
                            );
                            return;
                        } else {
                            errMessage = context.getString(R.string.source_not_found);
                        }
                    }
                }
            } else {
                errMessage = context.getString(R.string.error_playlist_null);
            }

            MsgBox.alert(context, context.getString(R.string.error_reading_track), errMessage, pathText);
        } catch (Throwable t) {
            myToastEE(t, context.getString(R.string.error_reading_track));
        }
        

    }

}
