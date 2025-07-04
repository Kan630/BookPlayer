package com.driot.bookplayer.utils;

import static com.driot.bookplayer.utils.Utils.recursiveRemove;

import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.provider.DocumentsContract;

import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.List;

public class KanFiles {

    public static void copyFile(File source, File dest) throws IOException {
        copyFileUsingStream(source, dest);
    }

    private static void copyFileUsingStream(File source, File dest) throws IOException {
        InputStream is = null;
        OutputStream os = null;
        try {
            is = new FileInputStream(source);
            os = new FileOutputStream(dest);
            byte[] buffer = new byte[1024];
            int length;
            while ((length = is.read(buffer)) > 0) {
                os.write(buffer, 0, length);
            }
        } finally {
            is.close();
            os.close();
        }
    }


    public static boolean deleteFolderRecursive(String strPath) {
        String starter = "file:///";
        if (strPath.length()>5) {
            strPath = strPath.replace(starter,"");
            try {
                File zikFileToDelete = new File(strPath);
                if(zikFileToDelete.exists()) {
                    if (recursiveRemove(zikFileToDelete)) {
                        myLog("Deleted from Disk : [" + strPath + "]");
                        return true;
                    } else {
                        myLog("NOT Deleted from Disk : [" + strPath + "]");
                        return false;
                    }
                } else {
                    myLogE("file does not exist : [" + strPath + "]");
                    return false;
                }
            } catch (Exception e) {
                myLogEE(e,"Error remove ZikFile from Disk : [" + strPath + "]");
                return false;
            }
        } else {
            myLogE("should not happen uri less than 5 chars for path [" + strPath + "]");
            return false;
        }
    }

    public boolean isUriDirectory(Context c, Uri uri) {
        try {
            DocumentFile doc = DocumentFile.fromTreeUri(c, uri);
            return doc != null && doc.isDirectory();
        } catch (Exception e) {
            myLogEE(e, "isDirectoryFromUri failed");
            return false;
        }
    }

    public static boolean isOnSdCard(Context appContext, Uri uri) {
        if (uri == null) return false;

        // Method 1: Tree URI with non-primary volume (works API 21+)
        if ("com.android.externalstorage.documents".equals(uri.getAuthority())) {
            String docId = DocumentsContract.getTreeDocumentId(uri); // e.g. "3334-3933:Audiobooks/mybook"
            if (docId != null && docId.contains(":")) {
                String volumeName = docId.split(":")[0];
                if (!"primary".equalsIgnoreCase(volumeName)) {
                    myLogD("Detected removable SD card via volume name: " + volumeName);
                    return true;
                }
            }
        }

        // Method 2: API 24+ use StorageVolume.isRemovable() + UUID comparison
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            StorageManager sm = (StorageManager) appContext.getSystemService(Context.STORAGE_SERVICE);
            if (sm == null) return false;

            List<StorageVolume> volumes = sm.getStorageVolumes();
            for (StorageVolume volume : volumes) {
                if (volume.isRemovable()) {
                    // Match SD card by checking volume's UUID prefix in the uri string
                    String uuid = getVolumeUuid(volume);
                    if (uuid != null && uri.toString().contains(uuid)) {
                        myLogD("Matched SD card volume UUID in URI: " + uuid);
                        return true;
                    }
                }
            }
        }

        return false;
    }
    @Nullable
    private static String getVolumeUuid(StorageVolume volume) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                // Use reflection to get UUID if not directly accessible
                Method getUuid = StorageVolume.class.getDeclaredMethod("getUuid");
                Object uuid = getUuid.invoke(volume);
                return uuid != null ? uuid.toString() : null;
            } catch (Exception e) {
                myLogEE(e, "Could not access volume UUID");
            }
        }
        return null;
    }

    ////////////////////////////////////////////////////////
    private static final String TAG = "KanFiles";
    private static void myLog(String str) { KanLogger.myLog(TAG, str); }
    private static void myLogD(String str) { KanLogger.myLogD(TAG, str); }
    private static void myLogI(String str) { KanLogger.myLogI(TAG, str); }
    private static void myLogW(String str) { KanLogger.myLogW(TAG, str); }
    private static void myLogE(String str) { KanLogger.myLogE(TAG, str); }
    private static void myLogEE(Throwable t, String str) { KanLogger.myLogEE(t, TAG, str); }
    private static void myToastEE(Throwable t, String str) { KanLogger.myToastEE(t, TAG, str); }


}
