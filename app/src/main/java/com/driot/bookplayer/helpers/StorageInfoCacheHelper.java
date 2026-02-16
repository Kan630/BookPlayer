package com.driot.bookplayer.helpers;

import android.app.Application;
import android.content.Context;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.db.Folder;
import com.driot.bookplayer.db.ZikFile;
import com.driot.bookplayer.global.Pref;
import com.driot.bookplayer.helpers.StorageHelper.MemoryLocationType;
import com.driot.bookplayer.utils.Tonio;

import org.json.JSONObject;

import java.io.File;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.driot.bookplayer.utils.log.LoggerStaticHelper.*;

/**
 * Helper class to calculate and cache storage information.
 * Values are stored in preferences and calculated at app startup.
 */
public class StorageInfoCacheHelper {

    private static final boolean VERBOSE_DEBUG = false;

    /**
     * Callback for recalculate progress (invoked from background thread; post to main thread in UI).
     */
    public interface RecalculateProgressCallback {
        void onProgress(int elapsedSec, String phaseMessage, boolean isSdCardPhase);
        void onComplete();
    }

    private static final boolean DEBUG = false;

    private static final ExecutorService executorService = Executors.newSingleThreadExecutor();

    /**
     * Initialize storage calculation at app startup.
     * Should be called from MyApp.onCreate() after AppTtsManager.init()
     */
    public static void init(Context context) {
        Application app = (Application) context.getApplicationContext();
        executorService.execute(() -> {
            try {
                myLogDD("Starting storage calculation at startup");
                // First populate missing sizes in database
                populateZikFileSizes(app);
                calculateInternalStorage(app);
                calculateSDCardStorage(app);
                calculateLinkedAudios(app);
                myLogDD("Storage calculation completed");
            } catch (Exception e) {
                myLogEE(e, "Error calculating storage at startup");
            }
        });
    }

    /**
     * Populate missing size fields in ZikFile database by checking actual file
     * sizes on filesystem
     */
    private static void populateZikFileSizes(Application app) {
        try {
            myLogDD("Populating missing ZikFile sizes from filesystem");
            long startTime = System.currentTimeMillis();

            // Get all ZikFiles where size is 0 or missing
            List<ZikFile> allZikFiles = AppDatabase.getDatabase(app).zikFileDao().getAll();
            int updatedCount = 0;
            int skippedCount = 0;
            int errorCount = 0;

            for (ZikFile zikFile : allZikFiles) {
                // Skip if size is already set (greater than 0)
                if (zikFile.getSize() > 0) {
                    skippedCount++;
                    continue;
                }

                String path = zikFile.getPath();
                if (path == null || path.isEmpty()) {
                    skippedCount++;
                    continue;
                }

                try {
                    // Get file size from filesystem
                    long fileSize = getFileSizeFromPath(app, path);

                    if (fileSize > 0) {
                        // Update the database
                        zikFile.setSize(fileSize);
                        AppDatabase.getDatabase(app).zikFileDao().update(zikFile);
                        updatedCount++;
                        myLogDD("Size updated  (" + fileSize + ") for ZikFile id=" + zikFile.getId() + ", path=" + path);
                    } else {
                        // File not found or inaccessible
                        skippedCount++;
                        // myLogE("Size update -File Not found- for ZikFile id=" + zikFile.getId() + ",
                        // path=" + path);
                    }
                } catch (Exception e) {
                    errorCount++;
                    myLogW("Error getting size for ZikFile id=" + zikFile.getId() + ", path="
                            + path + ": " + e.getMessage());
                }
            }

            long duration = System.currentTimeMillis() - startTime;
            myLogDD("ZikFile size population completed in " + Tonio.formatTime(duration) +
                    " - Updated: " + updatedCount + ", Skipped: " + skippedCount + ", Errors: " + errorCount);
        } catch (Exception e) {
            myLogEE(e, "Error populating ZikFile sizes");
        }
    }

    /**
     * Get file size from path (handles both file:// and content:// URIs)
     */
    private static long getFileSizeFromPath(Application app, String path) {
        if (path == null || path.isEmpty()) {
            return -1;
        }

        try {
            Uri uri;
            // Check if path is already a URI
            if (path.startsWith("content://") || path.startsWith("file://")) {
                uri = Uri.parse(path);
            } else {
                // Assume it's a file path
                uri = Uri.parse("file://" + path);
            }

            return getFileSize(app, uri);
        } catch (Exception e) {
            myLogEE(e, "Error getting file size for path: " + path);
            return -1;
        }
    }

    /**
     * Get file size from URI (handles both file:// and content:// URIs)
     */
    private static long getFileSize(Context context, Uri uri) {
        if (uri == null) {
            return -1;
        }

        if ("file".equalsIgnoreCase(uri.getScheme())) {
            try {
                if (uri.getPath() != null) {
                    File file = new File(uri.getPath());
                    if (file.exists() && file.isFile()) {
                        return file.length();
                    }
                }
            } catch (Exception e) {
                myLogEE(e, "getFileSize() - file://");
                return -1;
            }
        } else {
            // content:// URI
            try (ParcelFileDescriptor pfd = context.getContentResolver().openFileDescriptor(uri, "r")) {
                return (pfd != null) ? pfd.getStatSize() : -1;
            } catch (Exception e) {
                myLogEE(e, "getFileSize() - content://");
                return -1;
            }
        }
        return -1;
    }

    /**
     * Calculate and cache internal storage information
     */
    private static void calculateInternalStorage(Application app) {
        try {
            myLogDD("Calculating internal storage");

            long totalMemory = StorageHelper.getTotaLInternalMemorySize() / 1048576L;
            long availableMegs2 = StorageHelper.getAvailableInternalMemorySize() / 1048576L;
            long currentAppSize = StorageHelper.getAppSize(app) / 1048576L;

            // Calculate per-folder sizes for Clean Memory (internal unzip subfolders)
            File unzipFolder = StorageHelper.getUnzipFolder(app, false);
            long currentAudiosSizeInternal = 0;
            Map<String, Long> internalFolderSizes = new HashMap<>();
            if (unzipFolder != null && unzipFolder.exists()) {
                File[] children = unzipFolder.listFiles();
                if (children != null) {
                    myLogDD("Calculating getFolderSize for internal unzipped subfolders: "
                            + unzipFolder.getAbsolutePath());
                    long startTime = System.currentTimeMillis();
                    for (File f : children) {
                        if (f.isDirectory()) {
                            long sizeBytes = StorageHelper.getFolderSize(f);
                            internalFolderSizes.put(f.getAbsolutePath(), sizeBytes);
                            currentAudiosSizeInternal += sizeBytes / 1048576L;
                        }
                    }
                    long duration = System.currentTimeMillis() - startTime;
                    myLogDD("internal unzipped subfolders completed in "
                            + Tonio.formatTime(duration) + ", total: "
                            + Tonio.getReadableSize(currentAudiosSizeInternal * 1048576L));
                    Pref.setStorageInternalFolderSizesJson(folderSizesMapToJson(internalFolderSizes));
                }
            }

            File imagesFolder = new File(app.getFilesDir(), "images");
            long sizeImages = 0;
            if (imagesFolder.exists()) {
                myLogDD("Calculating getFolderSize for images folder: "
                        + imagesFolder.getAbsolutePath());
                long startTime = System.currentTimeMillis();
                sizeImages = StorageHelper.getFolderSize(imagesFolder.getPath()) / 1048576L;
                long duration = System.currentTimeMillis() - startTime;
                myLogDD("getFolderSize for images completed in " + Tonio.formatTime(duration)
                        + ", size: " + Tonio.getReadableSize(sizeImages * 1048576L));
            }

            File logsFolder = new File(app.getFilesDir(), "log");
            long sizeLogs = 0;
            if (logsFolder.exists()) {
                myLogDD("Calculating getFolderSize for log folder: "
                        + logsFolder.getAbsolutePath());
                long startTime = System.currentTimeMillis();
                sizeLogs = StorageHelper.getFolderSize(logsFolder.getPath()) / 1048576L;
                long duration = System.currentTimeMillis() - startTime;
                myLogDD("getFolderSize for log completed in " + Tonio.formatTime(duration)
                        + ", size: " + Tonio.getReadableSize(sizeLogs * 1048576L));
            }

            long sizeDB = 0;
            File parentFile = app.getFilesDir().getParentFile();
            if (parentFile != null) {
                File dbFolder = new File(parentFile, "databases");
                if (dbFolder.exists()) {
                    myLogDD("Calculating getFolderSize for databases folder: "
                            + dbFolder.getAbsolutePath());
                    long startTime = System.currentTimeMillis();
                    sizeDB = StorageHelper.getFolderSize(dbFolder.getPath()) / 1048576L;
                    long duration = System.currentTimeMillis() - startTime;
                    myLogDD("getFolderSize for databases completed in "
                            + Tonio.formatTime(duration) + ", size: " + Tonio.getReadableSize(sizeDB * 1048576L));
                }
            }

            File cachedImagesFolder = new File(app.getFilesDir(), "cached_images");
            long sizeCachedImages = 0;
            if (cachedImagesFolder.exists()) {
                myLogDD("Calculating getFolderSize for cached_images folder: "
                        + cachedImagesFolder.getAbsolutePath());
                long startTime = System.currentTimeMillis();
                sizeCachedImages = StorageHelper.getFolderSize(cachedImagesFolder.getPath()) / 1048576L;
                long duration = System.currentTimeMillis() - startTime;
                myLogDD("getFolderSize for cached_images completed in "
                        + Tonio.formatTime(duration) + ", size: " + Tonio.getReadableSize(sizeCachedImages * 1048576L));
            }

            // Calculate BookPlayer app storage (app + db + logs + images + cached images,
            // excluding audio files)
            long appStorageMB = currentAppSize + sizeDB + sizeLogs + sizeImages + sizeCachedImages;

            // Calculate BookPlayer total usage (app + audios + images + logs + db + cached
            // images)
            long usedByBookPlayerMB = currentAppSize + currentAudiosSizeInternal + sizeImages + sizeLogs + sizeDB
                    + sizeCachedImages;

            if (totalMemory > 0) {
                long totalMemoryBytes = totalMemory * 1048576L;
                long availableBytes = availableMegs2 * 1048576L;
                long usedByBookPlayerBytes = usedByBookPlayerMB * 1048576L;
                long usedTotalBytes = totalMemoryBytes - availableBytes;
                long usedByOthersBytes = usedTotalBytes - usedByBookPlayerBytes;

                if (usedByOthersBytes < 0)
                    usedByOthersBytes = 0;
                if (usedByBookPlayerBytes < 0)
                    usedByBookPlayerBytes = 0;

                // Store in preferences
                Pref.setStorageInternalTotal(totalMemoryBytes);
                Pref.setStorageInternalUsedByOthers(usedByOthersBytes);
                Pref.setStorageInternalUsedByBookPlayer(usedByBookPlayerBytes);
                Pref.setStorageInternalApp(appStorageMB * 1048576L); // Store in bytes
                Pref.setStorageInternalTimestamp(System.currentTimeMillis());

                myLog("Internal storage cached - Total: "
                        + Tonio.getReadableSize(totalMemoryBytes) + ", Used by BookPlayer: "
                        + Tonio.getReadableSize(usedByBookPlayerBytes) + ", Used by others: "
                        + Tonio.getReadableSize(usedByOthersBytes));
            }
        } catch (Exception e) {
            myLogEE(e, "Error calculating internal storage");
        }
    }

    /**
     * Calculate and cache SD card storage information
     */
    private static void calculateSDCardStorage(Application app) {
        try {
            myLogDD("Calculating SD card storage");
            long startTimeMain = System.currentTimeMillis();

            long total = StorageHelper.getTotalRemovableSDCardSize(app) / 1048576L;
            if (total > 0) {
                long available = StorageHelper.getAvailableRemovableSDCardSize(app) / 1048576L;

                // Calculate per-folder sizes for Clean Memory (SD card unzip subfolders)
                File sdUnzipFolder = StorageHelper.getUnzipFolder(app, true);
                long currentAudiosSizeSD = 0;
                Map<String, Long> sdCardFolderSizes = new HashMap<>();
                if (sdUnzipFolder != null && sdUnzipFolder.exists()) {
                    File[] children = sdUnzipFolder.listFiles();
                    if (children != null) {
                        myLogDD("Calculating getFolderSize for SD card subfolders: "
                                + sdUnzipFolder.getAbsolutePath());
                        long startTime = System.currentTimeMillis();
                        for (File f : children) {
                            if (f.isDirectory()) {
                                long sizeBytes = StorageHelper.getFolderSize(f);
                                sdCardFolderSizes.put(f.getAbsolutePath(), sizeBytes);
                                currentAudiosSizeSD += sizeBytes / 1048576L;
                            }
                        }
                        long duration = System.currentTimeMillis() - startTime;
                        myLogDD("SD card subfolders completed in "
                                + Tonio.formatTime(duration) + ", total: "
                                + Tonio.getReadableSize(currentAudiosSizeSD * 1048576L));
                        Pref.setStorageSDCardFolderSizesJson(folderSizesMapToJson(sdCardFolderSizes));
                    }
                } else {
                    myLogDD("SD card unzip folder does not exist, skipping folder size calculation");
                }

                long totalSDCardBytes = total * 1048576L;
                long availableSDCardBytes = available * 1048576L;
                long usedTotalSDCardBytes = totalSDCardBytes - availableSDCardBytes;
                long usedByBookPlayerSDCardBytes = currentAudiosSizeSD * 1048576L;
                long usedByOthersSDCardBytes = usedTotalSDCardBytes - usedByBookPlayerSDCardBytes;

                if (usedByOthersSDCardBytes < 0)
                    usedByOthersSDCardBytes = 0;
                if (usedByBookPlayerSDCardBytes < 0)
                    usedByBookPlayerSDCardBytes = 0;

                // Store in preferences
                Pref.setStorageSDCardTotal(totalSDCardBytes);
                Pref.setStorageSDCardUsedByOthers(usedByOthersSDCardBytes);
                Pref.setStorageSDCardUsedByBookPlayer(usedByBookPlayerSDCardBytes);
                Pref.setStorageSDCardTimestamp(System.currentTimeMillis());

                long durationMain = System.currentTimeMillis() - startTimeMain;
                myLog("SD card storage cached - completed in "
                        + Tonio.formatTime(durationMain) + ", Total: "
                        + Tonio.getReadableSize(totalSDCardBytes) + ", Used by BookPlayer: "
                        + Tonio.getReadableSize(usedByBookPlayerSDCardBytes) + ", Used by others: "
                        + Tonio.getReadableSize(usedByOthersSDCardBytes));
            } else {
                // No SD card available, clear cached values
                Pref.setStorageSDCardTotal(0);
                Pref.setStorageSDCardUsedByOthers(0);
                Pref.setStorageSDCardUsedByBookPlayer(0);
                Pref.setStorageSDCardTimestamp(0);
                myLogDD("No SD card available, cleared SD card storage cache");
            }
        } catch (Exception e) {
            myLogEE(e, "Error calculating SD card storage");
        }
    }

    /**
     * Get cached internal storage info
     */
    public static long getCachedInternalTotal() {
        return Pref.getStorageInternalTotal();
    }

    public static long getCachedInternalUsedByOthers() {
        return Pref.getStorageInternalUsedByOthers();
    }

    public static long getCachedInternalUsedByBookPlayer() {
        return Pref.getStorageInternalUsedByBookPlayer();
    }

    public static long getCachedInternalTimestamp() {
        return Pref.getStorageInternalTimestamp();
    }

    public static long getCachedInternalApp() {
        return Pref.getStorageInternalApp();
    }

    /**
     * Get cached SD card storage info
     */
    public static long getCachedSDCardTotal() {
        return Pref.getStorageSDCardTotal();
    }

    public static long getCachedSDCardUsedByOthers() {
        return Pref.getStorageSDCardUsedByOthers();
    }

    public static long getCachedSDCardUsedByBookPlayer() {
        return Pref.getStorageSDCardUsedByBookPlayer();
    }

    public static long getCachedSDCardTimestamp() {
        return Pref.getStorageSDCardTimestamp();
    }

    /**
     * Calculate and cache linked audios (files outside BookPlayer reserved space)
     */
    private static void calculateLinkedAudios(Application app) {
        try {
            myLogDD("Calculating linked audios");
            long startTime = System.currentTimeMillis();

            long internalLinkedAudiosBytes = 0;
            long sdCardLinkedAudiosBytes = 0;

            // Get all folders from database
            List<Folder> allFolders = AppDatabase.getDatabase(app).folderDao().getAll();
            myLogDD("Found " + allFolders.size() + " folders to check for linked audios");

            for (Folder folder : allFolders) {
                if (folder.getPath() == null || folder.getPath().isEmpty()) {
                    continue;
                }

                // Check if folder path is outside BookPlayer reserved space
                MemoryLocationType locationType = StorageHelper.getMemoryLocationType(app, folder.getPath());

                if (locationType == MemoryLocationType.PHONE_SHARED) {
                    // Linked audio on internal/shared storage
                    long folderSize = calculateFolderLinkedSize(app, folder.getId());
                    internalLinkedAudiosBytes += folderSize;
                } else if (locationType == MemoryLocationType.SDCARD_SHARED) {
                    // Linked audio on SD card shared storage
                    long folderSize = calculateFolderLinkedSize(app, folder.getId());
                    sdCardLinkedAudiosBytes += folderSize;
                }
            }

            // Store in preferences
            Pref.setStorageInternalLinkedAudios(internalLinkedAudiosBytes);
            Pref.setStorageSDCardLinkedAudios(sdCardLinkedAudiosBytes);

            long duration = System.currentTimeMillis() - startTime;
            myLog("Linked audios calculated in " + Tonio.formatTime(duration) +
                    " - Internal: " + Tonio.getReadableSize(internalLinkedAudiosBytes) +
                    ", SD Card: " + Tonio.getReadableSize(sdCardLinkedAudiosBytes));
        } catch (Exception e) {
            myLogEE(e, "Error calculating linked audios");
        }
    }

    /**
     * Calculate total size of all ZikFile tracks for a folder
     */
    private static long calculateFolderLinkedSize(Application app, int folderId) {
        try {
            List<ZikFile> zikFiles = AppDatabase.getDatabase(app).zikFileDao().getZikFiles(folderId);
            long totalSize = 0;
            for (ZikFile zikFile : zikFiles) {
                // ZikFile.size is stored as double in bytes
                totalSize += (long) zikFile.getSize();
            }
            return totalSize;
        } catch (Exception e) {
            myLogEE(e, "Error calculating folder linked size for folderId: " + folderId);
            return 0;
        }
    }

    /**
     * Get cached linked audios info
     */
    public static long getCachedInternalLinkedAudios() {
        return Pref.getStorageInternalLinkedAudios();
    }

    public static long getCachedSDCardLinkedAudios() {
        return Pref.getStorageSDCardLinkedAudios();
    }

    /**
     * Force recalculation of storage info (can be called from activities if needed)
     */
    public static void recalculate(Context context) {
        recalculate(context, true, true, null);
    }

    /**
     * Force recalculation with progress callback.
     * @param internalOnly if true, only compute internal storage (and linked audios for internal)
     * @param sdCardOnly   if true, only compute SD card storage (and linked audios for SD)
     * @param callback     invoked from background thread; post to main thread in UI
     */
    public static void recalculate(Context context, boolean internalOnly, boolean sdCardOnly,
                                    RecalculateProgressCallback callback) {
        Application app = (Application) context.getApplicationContext();
        executorService.execute(() -> {
            long startTime = System.currentTimeMillis();
            try {
                myLogDD("Force recalculating storage");
                boolean fullRecalc = internalOnly && sdCardOnly;
                if (fullRecalc) {
                    if (callback != null) {
                        callback.onProgress((int) ((System.currentTimeMillis() - startTime) / 1000),
                                "zikfiles", false);
                    }
                    populateZikFileSizes(app);
                }
                if (internalOnly) {
                    if (callback != null) {
                        callback.onProgress((int) ((System.currentTimeMillis() - startTime) / 1000),
                                "internal", false);
                    }
                    calculateInternalStorage(app);
                }
                if (sdCardOnly) {
                    if (callback != null) {
                        callback.onProgress((int) ((System.currentTimeMillis() - startTime) / 1000),
                                "sdcard", true);
                    }
                    calculateSDCardStorage(app);
                }
                if (callback != null) {
                    callback.onProgress((int) ((System.currentTimeMillis() - startTime) / 1000),
                            "linked", false);
                }
                calculateLinkedAudios(app);
                if (callback != null) {
                    callback.onComplete();
                }
            } catch (Exception e) {
                myLogEE(e, "Error recalculating storage");
                if (callback != null) {
                    callback.onComplete();
                }
            }
        });
    }

    /**
     * Get cached per-folder sizes (path -> size in bytes) for Clean Memory list.
     * @param internal true for internal storage unzip subfolders, false for SD card
     */
    public static Map<String, Long> getCachedFolderSizes(boolean internal) {
        String json = internal ? Pref.getStorageInternalFolderSizesJson() : Pref.getStorageSDCardFolderSizesJson();
        return jsonToFolderSizesMap(json);
    }

    private static String folderSizesMapToJson(Map<String, Long> map) {
        try {
            JSONObject j = new JSONObject();
            for (Map.Entry<String, Long> e : map.entrySet()) {
                j.put(e.getKey(), e.getValue());
            }
            return j.toString();
        } catch (Exception e) {
            myLogEE(e, "folderSizesMapToJson");
            return "{}";
        }
    }

    private static Map<String, Long> jsonToFolderSizesMap(String json) {
        Map<String, Long> map = new HashMap<>();
        if (json == null || json.isEmpty()) return map;
        try {
            JSONObject j = new JSONObject(json);
            Iterator<String> it = j.keys();
            while (it.hasNext()) {
                String k = it.next();
                map.put(k, j.getLong(k));
            }
        } catch (Exception e) {
            myLogEE(e, "jsonToFolderSizesMap");
        }
        return map;
    }

    /**
     * Public method to populate ZikFile sizes (can be called independently if
     * needed)
     */
    public static void populateSizes(Context context) {
        Application app = (Application) context.getApplicationContext();
        executorService.execute(() -> {
            try {
                populateZikFileSizes(app);
            } catch (Exception e) {
                myLogEE(e, "Error populating ZikFile sizes");
            }
        });
    }

    private static void myLogDD(String msg) {
        if (VERBOSE_DEBUG)
            myLogD(msg);
    }
}
