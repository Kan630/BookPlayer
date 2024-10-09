package com.driot.bookplayer.utils;

import static com.driot.bookplayer.utils.KanLogger.myLog;

import android.content.Context;
import android.media.MediaScannerConnection;

/** Antoine Driot 2023-10-12
 *
 */
//TODO should be run when importing a file....
public class MediaScanner2 {

    public static void scanFile(Context context, String filePath, String mimeType, MediaScannerConnection.OnScanCompletedListener listener) {
        MediaScannerConnection.scanFile(context, new String[] { filePath }, new String[] { mimeType }, listener);
    }

    public static void scanFileAndNotifyMediaScanner(Context context, String filePath, String mimeType) {
        scanFile(context, filePath, mimeType, (path, uri) -> {
            // The file at 'path' has been scanned and is now accessible to other apps
            // You can add additional handling here if needed
            myLog("Medias have been scanned : " + path);
            if (uri != null) {
                myLog("uri found : " + uri.toString());
            } else {
                myLog("no uri found");
            }

        });
    }

}