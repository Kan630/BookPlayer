package com.driot.bookplayer.player;

import static com.driot.bookplayer.utils.PermissionRequest.isReadAudioPermissionGranted;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;

import com.driot.bookplayer.R;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.helpers.StorageHelper;
import com.driot.bookplayer.helpers.UriHelper;
import com.driot.bookplayer.utils.MsgBox;
import static com.driot.bookplayer.utils.log.LoggerStaticHelper.*;

import androidx.documentfile.provider.DocumentFile;

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
                myLogD("zikFilePath found: " + zikFilePath);
                pathText = context.getString(R.string.source_file_path) + " = \n[" + Uri.decode(zikFilePath) + "]";
                newErrorMessage = getErrorMessageConsideringZikFilePath(context, zikFilePath);
                myLogD("newErrorMessage from diagnostics: [" + newErrorMessage + "]");
            } else {
                myLogW("zikFilePath is still null after PlayList check");
            }

            if (errMessage == null && newErrorMessage != null) {
                errMessage = newErrorMessage;
            } else if (newErrorMessage != null) {
                errMessage = errMessage + "\n\n" + newErrorMessage;
            }

            if (errMessage == null || errMessage.trim().isEmpty()) {
                myLogW("errMessage is null or empty, using fallback generic error");
                errMessage = context.getString(R.string.error_generic);
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
                alertOrOfferRedownload(context, errMessage, pathText, zikFilePath);
            }

        } catch (Throwable t) {
            myToastEE(t, context.getString(R.string.error_reading_track));
        }
        myLog("displayed error message : [" + errMessage + "]");
    }

    /** A missing file of a book that can be fetched again gets a "Download again" button instead
     *  of a dead-end message. The lookup touches the database, so it runs off the main thread. */
    private static void alertOrOfferRedownload(Context context, String errMessage, String pathText,
            String zikFilePath) {
        Context app = context.getApplicationContext();
        com.driot.bookplayer.db.AppDatabase.databaseReadExecutor.execute(() -> {
            long folderId = -1;
            try {
                // MediaService passes its whole error text ("resolvePlayableUri failed for: <path>")
                // where a path is expected.
                String cleanPath = zikFilePath == null ? null
                        : zikFilePath.replaceFirst("^resolvePlayableUri failed for:\\s*", "");
                com.driot.bookplayer.db.ZikFile zf = cleanPath == null ? null
                        : com.driot.bookplayer.db.AppDatabase.getDatabase(app).zikFileDao().getByPath(cleanPath);
                if (zf == null) {
                    PlayList pl = PlayList.getInstance();
                    zf = pl != null ? pl.getZikFile() : null;
                }
                if (zf != null && UriHelper.resolvePlayableUri(app, zf) == null
                        && com.driot.bookplayer.redownload.RedownloadHelper.isRedownloadable(app, zf.getIdFolder())) {
                    folderId = zf.getIdFolder();
                }
            } catch (Exception e) {
                myLogEE(e, "alertOrOfferRedownload lookup failed");
            }
            final long offerFolderId = folderId;
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                String title = context.getString(R.string.error_reading_track);
                if (offerFolderId < 0) {
                    MsgBox.alert(context, title, errMessage, pathText);
                    return;
                }
                Intent redownload = new Intent(context, com.driot.bookplayer.redownload.RedownloadBookActivity.class)
                        .putExtra(com.driot.bookplayer.redownload.RedownloadHelper.KEY_FOLDER_ID, offerFolderId);
                MsgBox.alertWithNeutral(context, title,
                        errMessage + "\n\n" + context.getString(R.string.redownload_offer_message), pathText,
                        context.getString(R.string.redownload_offer_button), redownload);
            });
        });
    }

    public static String getErrorMessageConsideringZikFilePath(Context context, String zikFilePath) {
        String errMessage;

        Uri uri = UriHelper.resolveUriFromPath(context, zikFilePath);
        boolean exists = (uri != null);
        myLog("playlist file exist = " + exists + " : [" + zikFilePath + "]");

        if (!exists) {
            if (StorageHelper.isInInternalMemory(zikFilePath)) {
                myLogW(Var.SHOULD_NOT_HAPPEN + " : file in app-reserved storage is unreadable [" + zikFilePath + "]");
                errMessage = context.getString(R.string.source_not_found);
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
                long size = 0;
                try {
                    DocumentFile df = UriHelper.getDocumentFileFromAnyUri(context, uri);
                    if (df != null)
                        size = df.length();
                } catch (Throwable t) {
                    myLogEE(t, "getErrorMessage size check failed");
                }

                if (size > 0) {
                    errMessage = context.getString(R.string.error_mediaplayer_unsupported);
                    myLogI("File exists and size > 0 (" + size + "), returning error_mediaplayer_unsupported");
                } else {
                    myLogW("File exists but size is 0: " + zikFilePath);
                    myLogW(Var.SHOULD_NOT_HAPPEN);
                    errMessage = context.getString(R.string.source_not_found) + "\n- this " + Var.SHOULD_NOT_HAPPEN
                            + " -";
                }
            }
        }
        myLog("error message considering ZikFilePath : [" + errMessage + "]");
        return errMessage;
    }
}
