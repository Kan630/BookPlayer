package com.driot.bookplayer.utils;

import android.content.Context;
import android.os.Environment;
import android.os.StatFs;

import com.driot.bookplayer.global.Option;

import java.io.File;

public class StorageHelper {

    public static boolean isExternalSDCardAvailable(Context context) {
        File[] externalDirs = context.getExternalFilesDirs(null);
        return externalDirs.length > 1 && externalDirs[1] != null && Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState(externalDirs[1]));
    }

    public static File getPreferredFilesDirs(Context context) {
        if (isExternalSDCardAvailable(context) && Option.getUseSdCard()) {
            File[] externalDirs = context.getExternalFilesDirs(null);
            return externalDirs[1];
        } else {
            return context.getFilesDir();
        }
    }

    public static File getSdCardFilesDirs(Context context) {
        if (isExternalSDCardAvailable(context)) {
            File[] externalDirs = context.getExternalFilesDirs(null);
            return externalDirs[1];
        } else {
            return context.getFilesDir();
        }
    }

    public static long getAvailableInternalMemorySize() {
        File path = Environment.getDataDirectory();
        StatFs stat = new StatFs(path.getPath());
        long blockSize = stat.getBlockSizeLong();
        long availableBlocks = stat.getAvailableBlocksLong();
        return availableBlocks * blockSize;
    }

    public static long getTotaLInternalMemorySize() {
        File path = Environment.getDataDirectory();
        StatFs stat = new StatFs(path.getPath());
        long blockSize = stat.getBlockSizeLong();
        long allBlocks = stat.getBlockCountLong();
        return allBlocks * blockSize;
    }

    // Returns the total size in bytes of the removable SD card, or -1 if not found
    public static long getTotalRemovableSDCardSize(Context context) {
        try {
            File sdCard = getRemovableSDCardPath(context);
            if (sdCard != null) {
                StatFs stat = new StatFs(sdCard.getPath());
                long blockSize = stat.getBlockSizeLong();
                long totalBlocks = stat.getBlockCountLong();
                return totalBlocks * blockSize;
            }
        } catch (Throwable t) {
            myLogEE(t,"getTotalRemovableSDCardSize");
        }
        return -1;
    }

    // Returns the available size in bytes of the removable SD card, or -1 if not found
    public static long getAvailableRemovableSDCardSize(Context context) {
        try {
            File sdCard = getRemovableSDCardPath(context);
            if (sdCard != null) {
                StatFs stat = new StatFs(sdCard.getPath());
                long blockSize = stat.getBlockSizeLong();
                long availableBlocks = stat.getAvailableBlocksLong();
                return availableBlocks * blockSize;
            }
        } catch (Throwable t) {
            myLogEE(t,"getTotalRemovableSDCardSize");
        }
        return -1;
    }

    // Helper method to find the removable SD card directory
    private static File getRemovableSDCardPath(Context context) {
        File[] externalDirs = context.getExternalFilesDirs(null);
        for (File file : externalDirs) {
            if (file != null && Environment.isExternalStorageRemovable(file)) {
                return file;
            }
        }
        return null;
    }




    // ----------------------- LOG -----------------------
    private static final String TAG = "StorageHelper";
    private static void myLog(String str) { KanLogger.myLog(TAG, str); }
    private static void myLogD(String str) { KanLogger.myLogD(TAG, str); }
    private static void myLogI(String str) { KanLogger.myLogI(TAG, str); }
    private static void myLogW(String str) { KanLogger.myLogW(TAG, str); }
    private static void myLogE(String str) { KanLogger.myLogE(TAG, str); }
    private static void myLogEE(Throwable t, String str) { KanLogger.myLogEE(t, TAG, str); }
    private static void myToastEE(Throwable t, String str) { KanLogger.myToastEE(t, TAG, str); }

}
