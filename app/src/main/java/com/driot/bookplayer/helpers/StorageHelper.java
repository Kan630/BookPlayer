package com.driot.bookplayer.helpers;

import static com.driot.bookplayer.global.Var.FOLDER_DOWNLOAD;
import static com.driot.bookplayer.global.Var.FOLDER_IMAGE;
import static com.driot.bookplayer.global.Var.FOLDER_UNZIPPED;

import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.os.StatFs;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.provider.DocumentsContract;

import androidx.annotation.Nullable;

import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.utils.KanLogger;

import java.io.File;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;

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
        if (path == null) return MemoryLocationType.NOT_FOUND;
        boolean onSDcard = false;
        try {
            onSDcard = isOnSdCard(context, Uri.parse(path));
        } catch (Exception e1) {
            myLogEE(e1, "MemoryLocationType uri parse" );
        }
        try {
            String pathLower = path.toLowerCase();
            String reservedInternal = context.getFilesDir().getAbsolutePath();
            myLog(reservedInternal);
            File sdBase = getPreferredBaseDir(context, true);
            String reservedSD = sdBase != null ? sdBase.getAbsolutePath() : "";

            if (pathLower.startsWith(reservedInternal.toLowerCase())) {
                return MemoryLocationType.INTERNAL_RESERVED;
            } else if (onSDcard && pathLower.contains(Var.PATH_CHECK_AUDIO_FILE_INTERNAL)) {
                return MemoryLocationType.SDCARD_RESERVED;
            } else if (onSDcard && !pathLower.contains(Var.PATH_CHECK_AUDIO_FILE_INTERNAL)) {
                return MemoryLocationType.SDCARD_SHARED;
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

    public static long getTotalStorageMB(Context context, boolean internal) {
        return internal ? getTotaLInternalMemorySize() / 1048576L : getTotalRemovableSDCardSize(context) / 1048576L;
    }

    public static long getAvailableStorageMB(Context context, boolean internal) {
        return internal ? getAvailableInternalMemorySize() / 1048576L : getAvailableRemovableSDCardSize(context) / 1048576L;
    }



    public static boolean isOnSdCard(Context appContext, Uri uri) {
        if (uri == null) return false;

        // Method 2: API 24+ use StorageVolume.isRemovable() + UUID comparison
        try {
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
        } catch (Exception e) {
            myLogEE(e,"isOnSdCard - StorageManager");
        }

        try {
            // Method 1: Tree URI with non-primary volume (works API 21+)
            if ("com.android.externalstorage.documents".equals(uri.getAuthority())) {
                String docId;
                if (DocumentsContract.isTreeUri(uri)) {
                    docId = DocumentsContract.getTreeDocumentId(uri);   // e.g. "3334-3933:Audiobooks/mybook"
                } else {
                    docId = DocumentsContract.getDocumentId(uri);
                }
                if (docId != null && docId.contains(":")) {
                    String volumeName = docId.split(":")[0];
                    if (!"primary".equalsIgnoreCase(volumeName)) {
                        myLogD("Detected removable SD card via volume name: " + volumeName);
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            myLogEE(e,"isOnSdCard - DocumentsContract");
        }

        return false;
    }
    @Nullable
    private static String getVolumeUuid(StorageVolume volume) {
        try {
            // Use reflection to get UUID if not directly accessible
            Method getUuid = StorageVolume.class.getDeclaredMethod("getUuid");
            Object uuid = getUuid.invoke(volume);
            return uuid != null ? uuid.toString() : null;
        } catch (Exception e) {
            myLogEE(e, "Could not access volume UUID");
        }
        return null;
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
