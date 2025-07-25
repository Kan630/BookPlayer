package com.driot.bookplayer.utils;

import static com.driot.bookplayer.global.Var.FOLDER_DOWNLOAD;
import static com.driot.bookplayer.global.Var.FOLDER_IMAGE;
import static com.driot.bookplayer.global.Var.FOLDER_UNZIPPED;
import static com.driot.bookplayer.utils.KanFiles.sanitizeFilename;

import android.content.Context;
import android.os.Environment;
import android.os.StatFs;

import com.driot.bookplayer.global.Option;

import java.io.File;

public class StorageHelper {

    public enum MemoryLocationType {
        INTERNAL_RESERVED,
        SDCARD_RESERVED,
        SDCARD_SHARED,
        PHONE_SHARED,
        NOT_FOUND
    }

    //TODO should also check if the path/uri whatever is reachable and if not, another serie of 4 icons with a big red cross in front
    public static MemoryLocationType getMemoryLocationType(Context context, String path) {
        try {
            String pathLower = path.toLowerCase();
            String reservedInternal = context.getFilesDir().getAbsolutePath();
            File sdBase = getPreferredBaseDir(context, true);
            String reservedSD = sdBase != null ? sdBase.getAbsolutePath() : "";

            if (pathLower.startsWith(reservedInternal.toLowerCase())) {
                return MemoryLocationType.INTERNAL_RESERVED;
            } else if (!reservedSD.isEmpty() && pathLower.startsWith(reservedSD.toLowerCase())) {
                if (pathLower.contains("/android/data/" + context.getPackageName().toLowerCase())) {
                    return MemoryLocationType.SDCARD_RESERVED;
                } else {
                    return MemoryLocationType.SDCARD_SHARED;
                }
            } else {
                return MemoryLocationType.PHONE_SHARED;
            }
        } catch (Exception e) {
            myLogEE(e, "getMemoryLocationType()");
            return MemoryLocationType.NOT_FOUND;
        }
    }

    // === PUBLIC FOLDER RESOLVERS ===

    // UNZIPPED
    public static File getUnzipFolder(Context context) {
        return getFolder(context, FOLDER_UNZIPPED, Option.getUseSdCard());
    }

    public static File getUnzipFolder(Context context, boolean forceSdCard) {
        return getFolder(context, FOLDER_UNZIPPED, forceSdCard);
    }

    public static String getUnzipFolderPath(Context context) {
        return getUnzipFolder(context).getAbsolutePath();
    }

    // DOWNLOAD
    public static File getDownloadFolder(Context context) {
        return getFolder(context, FOLDER_DOWNLOAD, Option.getUseSdCard());
    }

    public static String getDownloadFolderPath(Context context) {
        return getDownloadFolder(context).getAbsolutePath();
    }

    // IMAGES
    public static File getImageFolder(Context context) {
        return getFolder(context, FOLDER_IMAGE, false);
    }


    // === GENERIC FOLDER RESOLVER ===
    public static File getFolder(Context context, String subfolder, boolean useSdCard) {
        File baseDir = getPreferredBaseDir(context, useSdCard);
        return new File(baseDir, subfolder);
    }

    public static File getPreferredBaseDir(Context context, boolean useSdCard) {
        if (useSdCard && isExternalSDCardAvailable(context)) {
            return getRemovableSDCardPath(context);
        } else {
            return context.getFilesDir();
        }
    }

    public static File getPreferredBaseDir(Context context) {
        return getPreferredBaseDir(context, Option.getUseSdCard());
    }

    // === SD CARD HANDLING ===

    public static boolean isExternalSDCardAvailable(Context context) {
        File[] dirs = context.getExternalFilesDirs(null);
        return dirs.length > 1 && dirs[1] != null &&
                Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState(dirs[1])) &&
                Environment.isExternalStorageRemovable(dirs[1]);
    }

    private static File getRemovableSDCardPath(Context context) {
        File[] dirs = context.getExternalFilesDirs(null);
        for (File dir : dirs) {
            if (dir != null && Environment.isExternalStorageRemovable(dir)) {
                return dir;
            }
        }
        return null;
    }

    public static String getSdCardUnzippedFolder(Context context) {
        File base = getRemovableSDCardPath(context);
        if (base == null) {
            myLogI("No SD card available");
            return null;
        }
        File unzipped = new File(base, FOLDER_UNZIPPED);
        myLogD("Checking SD folder: " + unzipped.getAbsolutePath());

        if (unzipped.exists() && unzipped.isDirectory()) {
            myLogD("Found: " + unzipped.getAbsolutePath());
            return unzipped.getAbsolutePath();
        } else {
            myLogW("Not found: " + unzipped.getAbsolutePath());
            return null;
        }
    }

    // === STORAGE SPACE ===

    public static long getAvailableInternalMemorySize() {
        return getAvailableSpace(Environment.getDataDirectory());
    }

    public static long getTotaLInternalMemorySize() {
        return getTotalSpace(Environment.getDataDirectory());
    }

    public static long getAvailableRemovableSDCardSize(Context context) {
        File sd = getRemovableSDCardPath(context);
        return sd != null ? getAvailableSpace(sd) : -1;
    }

    public static long getTotalRemovableSDCardSize(Context context) {
        File sd = getRemovableSDCardPath(context);
        return sd != null ? getTotalSpace(sd) : -1;
    }

    private static long getAvailableSpace(File path) {
        try {
            StatFs stat = new StatFs(path.getPath());
            return stat.getBlockSizeLong() * stat.getAvailableBlocksLong();
        } catch (Exception e) {
            myLogEE(e, "getAvailableSpace failed for " + path);
            return -1;
        }
    }

    private static long getTotalSpace(File path) {
        try {
            StatFs stat = new StatFs(path.getPath());
            return stat.getBlockSizeLong() * stat.getBlockCountLong();
        } catch (Exception e) {
            myLogEE(e, "getTotalSpace failed for " + path);
            return -1;
        }
    }



    // === LOGGING ===
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
