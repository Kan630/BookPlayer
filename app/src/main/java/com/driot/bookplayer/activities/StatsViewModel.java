package com.driot.bookplayer.activities;

import static com.driot.bookplayer.helpers.StorageHelper.getAvailableInternalMemorySize;
import static com.driot.bookplayer.helpers.StorageHelper.getAvailableRemovableSDCardSize;
import static com.driot.bookplayer.helpers.StorageHelper.getTotaLInternalMemorySize;
import static com.driot.bookplayer.helpers.StorageHelper.getTotalRemovableSDCardSize;
import static com.driot.bookplayer.helpers.StorageHelper.getAppSize;
import static com.driot.bookplayer.helpers.StorageHelper.getFolderSize;

import android.app.Application;
import android.content.Context;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.driot.bookplayer.R;
import com.driot.bookplayer.db.Sql;
import com.driot.bookplayer.helpers.StorageHelper;
import com.driot.bookplayer.helpers.StorageInfoCacheHelper;
import com.driot.bookplayer.utils.Tonio;
import com.driot.bookplayer.utils.log.LoggingAndroidViewModel;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class StatsViewModel extends LoggingAndroidViewModel {

    private final MutableLiveData<StorageInfo> internalStorageInfo = new MutableLiveData<>();
    private final MutableLiveData<StorageInfo> sdCardStorageInfo = new MutableLiveData<>();
    private final MutableLiveData<CharSequence> internalStorageText = new MutableLiveData<>();
    private final MutableLiveData<CharSequence> sdCardStorageText = new MutableLiveData<>();
    private final MutableLiveData<String> dbStats = new MutableLiveData<>();
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    public StatsViewModel(@NonNull Application application) {
        super(application);
        loadStorageInfo();
    }

    /**
     * Get a context wrapped with the app's selected locale.
     * Use this instead of getApplication() when calling getString() to ensure
     * strings are in the user's selected language, not the system locale.
     */
    private Context getLocalizedContext() {
        return com.driot.bookplayer.helpers.LocaleHelper.wrapContextWithAppLocale(getApplication());
    }

    public LiveData<StorageInfo> getInternalStorageInfo() {
        return internalStorageInfo;
    }

    public LiveData<StorageInfo> getSdCardStorageInfo() {
        return sdCardStorageInfo;
    }

    public LiveData<CharSequence> getInternalStorageText() {
        return internalStorageText;
    }

    public LiveData<CharSequence> getSdCardStorageText() {
        return sdCardStorageText;
    }

    public LiveData<String> getDbStats() {
        return dbStats;
    }

    public void loadStorageInfo() {
        executorService.execute(() -> {
            try {
                // Use cached values if available, otherwise calculate
                long cachedInternalTimestamp = StorageInfoCacheHelper.getCachedInternalTimestamp();
                if (cachedInternalTimestamp > 0) {
                    myLogI("StatsViewModel: Using cached internal storage info");
                    loadInternalStorageFromCache();
                } else {
                    myLogI("StatsViewModel: No cached internal storage, calculating now");
                    calculateInternalStorage();
                }

                long cachedSDCardTimestamp = StorageInfoCacheHelper.getCachedSDCardTimestamp();
                if (cachedSDCardTimestamp > 0) {
                    myLogI("StatsViewModel: Using cached SD card storage info");
                    loadSDCardStorageFromCache();
                } else {
                    myLogI("StatsViewModel: No cached SD card storage, calculating now");
                    calculateSDCardStorage();
                }
            } catch (Exception e) {
                myLogEE(e, "Error loading storage info");
            }

            try {
                dbStats.postValue(Sql.getDbStats(getApplication()));
            } catch (Exception e) {
                myLogEE(e, "Error loading DB stats");
            }
        });
    }

    private void loadInternalStorageFromCache() {
        Application app = getApplication();
        Context ctx = getLocalizedContext();
        long totalMemoryBytes = StorageInfoCacheHelper.getCachedInternalTotal();
        long usedByOthersBytes = StorageInfoCacheHelper.getCachedInternalUsedByOthers();
        long usedByBookPlayerBytes = StorageInfoCacheHelper.getCachedInternalUsedByBookPlayer();

        if (totalMemoryBytes > 0) {
            // Still need to calculate display text with detailed breakdown
            // But use cached values for the storage bar
            long totalMemory = totalMemoryBytes / 1048576L;
            long availableBytes = StorageHelper.getAvailableInternalMemorySize();
            long availableMegs2 = availableBytes / 1048576L;
            long currentAppSize = getAppSize(app) / 1048576L;

            // For display text, we still calculate folder sizes for detailed breakdown
            // But this is faster since we can skip if folders don't exist
            File unzipFolder = StorageHelper.getUnzipFolder(app, false);
            long currentAudiosSizeInternal = 0;
            if (unzipFolder != null && unzipFolder.exists()) {
                currentAudiosSizeInternal = StorageHelper.getFolderSize(unzipFolder) / 1048576L;
            }

            File imagesFolder = new File(app.getFilesDir(), "images");
            long sizeImages = 0;
            if (imagesFolder.exists()) {
                sizeImages = getFolderSize(imagesFolder.getPath()) / 1048576L;
            }

            File logsFolder = new File(app.getFilesDir(), "log");
            long sizeLogs = 0;
            if (logsFolder.exists()) {
                sizeLogs = getFolderSize(logsFolder.getPath()) / 1048576L;
            }

            long sizeDB = 0;
            File parentFile = app.getFilesDir().getParentFile();
            if (parentFile != null) {
                File dbFolder = new File(parentFile, "databases");
                if (dbFolder.exists()) {
                    sizeDB = getFolderSize(dbFolder.getPath()) / 1048576L;
                }
            }

            long linkedAudios = StorageInfoCacheHelper.getCachedInternalLinkedAudios();
            long linkedAudiosMB = linkedAudios / 1048576L;

            String internalTextPlain = Tonio.formatMemPadding(app, totalMemory)
                    + ctx.getString(R.string.MB_device_memory)
                    + "\n" + "\n" + Tonio.formatMemPadding(app, availableMegs2)
                    + ctx.getString(R.string.MB_available_on_device)
                    + "\n" + "\n" + Tonio.formatMemPadding(app, currentAudiosSizeInternal)
                    + ctx.getString(R.string.MB_taken_by_audio_files)
                    + "\n" + "\n" + Tonio.formatMemPadding(app, currentAppSize)
                    + ctx.getString(R.string.MB_taken_by_BookPlayer_app)
                    + "\n" + "\n" + Tonio.formatMemPadding(app, linkedAudiosMB)
                    + ctx.getString(R.string.MB_taken_by_linked_audios)
                    + "\n" + "\n" + Tonio.formatMemPadding(app, sizeImages) + ctx.getString(R.string.MB_taken_by_images)
                    + "\n" + "\n" + Tonio.formatMemPadding(app, sizeLogs) + ctx.getString(R.string.MB_taken_by_logs)
                    + "\n" + "\n" + Tonio.formatMemPadding(app, sizeDB) + ctx.getString(R.string.MB_taken_by_databases);

            // Create SpannableString with colors matching storage bar
            SpannableString internalText = new SpannableString(internalTextPlain);
            int lightBlueColor = ContextCompat.getColor(app, R.color.pastel_blue_500); // Light blue for BookPlayer
                                                                                       // audio files
            int darkBlueColor = ContextCompat.getColor(app, R.color.pastel_blue_900); // Dark blue for BookPlayer app
                                                                                      // (app + db + logs + images)
            int greenColor = ContextCompat.getColor(app, R.color.green_500); // Green for linked audios

            // Color "MB taken by audio files" (copied audio) - LIGHT BLUE
            String audioFilesLine = Tonio.formatMemPadding(app, currentAudiosSizeInternal)
                    + ctx.getString(R.string.MB_taken_by_audio_files);
            int audioFilesStart = internalTextPlain.indexOf(audioFilesLine);
            if (audioFilesStart >= 0) {
                internalText.setSpan(new ForegroundColorSpan(lightBlueColor),
                        audioFilesStart,
                        audioFilesStart + audioFilesLine.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }

            // Color "MB taken by BookPlayer app" - DARK BLUE (app + db + logs + images)
            String appLine = Tonio.formatMemPadding(app, currentAppSize)
                    + ctx.getString(R.string.MB_taken_by_BookPlayer_app);
            int appStart = internalTextPlain.indexOf(appLine);
            if (appStart >= 0) {
                internalText.setSpan(new ForegroundColorSpan(darkBlueColor),
                        appStart,
                        appStart + appLine.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }

            // Color "MB taken by images" - DARK BLUE (part of app storage)
            String imagesLine = Tonio.formatMemPadding(app, sizeImages) + ctx.getString(R.string.MB_taken_by_images);
            int imagesStart = internalTextPlain.indexOf(imagesLine);
            if (imagesStart >= 0) {
                internalText.setSpan(new ForegroundColorSpan(darkBlueColor),
                        imagesStart,
                        imagesStart + imagesLine.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }

            // Color "MB taken by logs" - DARK BLUE (part of app storage)
            String logsLine = Tonio.formatMemPadding(app, sizeLogs) + ctx.getString(R.string.MB_taken_by_logs);
            int logsStart = internalTextPlain.indexOf(logsLine);
            if (logsStart >= 0) {
                internalText.setSpan(new ForegroundColorSpan(darkBlueColor),
                        logsStart,
                        logsStart + logsLine.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }

            // Color "MB taken by databases" - DARK BLUE (part of app storage)
            String dbLine = Tonio.formatMemPadding(app, sizeDB) + ctx.getString(R.string.MB_taken_by_databases);
            int dbStart = internalTextPlain.indexOf(dbLine);
            if (dbStart >= 0) {
                internalText.setSpan(new ForegroundColorSpan(darkBlueColor),
                        dbStart,
                        dbStart + dbLine.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }

            // Color "MB taken by linked audios" - GREEN
            String linkedAudiosLine = Tonio.formatMemPadding(app, linkedAudiosMB)
                    + ctx.getString(R.string.MB_taken_by_linked_audios);
            int linkedAudiosStart = internalTextPlain.indexOf(linkedAudiosLine);
            if (linkedAudiosStart >= 0) {
                internalText.setSpan(new ForegroundColorSpan(greenColor),
                        linkedAudiosStart,
                        linkedAudiosStart + linkedAudiosLine.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }

            long appStorageBytes = StorageInfoCacheHelper.getCachedInternalApp();
            internalStorageInfo.postValue(new StorageInfo(
                    totalMemoryBytes,
                    usedByOthersBytes,
                    usedByBookPlayerBytes,
                    0, // expectedAddedMemory
                    linkedAudios,
                    appStorageBytes,
                    internalTextPlain // Store plain text for StorageInfo
            ));
            internalStorageText.postValue(internalText); // Post SpannableString directly (don't convert to String!)
        }
    }

    private void loadSDCardStorageFromCache() {
        Application app = getApplication();
        Context ctx = getLocalizedContext();
        long totalSDCardBytes = StorageInfoCacheHelper.getCachedSDCardTotal();
        long usedByOthersBytes = StorageInfoCacheHelper.getCachedSDCardUsedByOthers();
        long usedByBookPlayerBytes = StorageInfoCacheHelper.getCachedSDCardUsedByBookPlayer();

        if (totalSDCardBytes > 0) {
            long total = totalSDCardBytes / 1048576L;
            long availableBytes = StorageHelper.getAvailableRemovableSDCardSize(app);
            long available = availableBytes > 0 ? availableBytes / 1048576L : 0;
            long currentAudiosSizeSD = usedByBookPlayerBytes / 1048576L;

            long linkedAudios = StorageInfoCacheHelper.getCachedSDCardLinkedAudios();
            long linkedAudiosMB = linkedAudios / 1048576L;

            String sdCardTextPlain = Tonio.formatMemPadding(app, total) + ctx.getString(R.string.MB_SD_card_memory)
                    + "\n\n" + Tonio.formatMemPadding(app, available) + ctx.getString(R.string.MB_available_on_SD_card)
                    + "\n\n" + Tonio.formatMemPadding(app, currentAudiosSizeSD)
                    + ctx.getString(R.string.MB_taken_by_audio_files)
                    + "\n\n" + Tonio.formatMemPadding(app, linkedAudiosMB)
                    + ctx.getString(R.string.MB_taken_by_linked_audios);

            // Create SpannableString with colors matching storage bar
            SpannableString sdCardText = new SpannableString(sdCardTextPlain);
            int blueColor = ContextCompat.getColor(app, R.color.pastel_blue_500); // Blue for BookPlayer used
            int greenColor = ContextCompat.getColor(app, R.color.green_500); // Green for linked audios

            // Color "MB taken by audio files" (copied audio) - BLUE
            String audioFilesLine = Tonio.formatMemPadding(app, currentAudiosSizeSD)
                    + ctx.getString(R.string.MB_taken_by_audio_files);
            int audioFilesStart = sdCardTextPlain.indexOf(audioFilesLine);
            if (audioFilesStart >= 0) {
                sdCardText.setSpan(new ForegroundColorSpan(blueColor),
                        audioFilesStart,
                        audioFilesStart + audioFilesLine.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }

            // Color "MB taken by linked audios" - GREEN
            String linkedAudiosLine = Tonio.formatMemPadding(app, linkedAudiosMB)
                    + ctx.getString(R.string.MB_taken_by_linked_audios);
            int linkedAudiosStart = sdCardTextPlain.indexOf(linkedAudiosLine);
            if (linkedAudiosStart >= 0) {
                sdCardText.setSpan(new ForegroundColorSpan(greenColor),
                        linkedAudiosStart,
                        linkedAudiosStart + linkedAudiosLine.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }

            sdCardStorageInfo.postValue(new StorageInfo(
                    totalSDCardBytes,
                    usedByOthersBytes,
                    usedByBookPlayerBytes,
                    0, // expectedAddedMemory
                    linkedAudios,
                    0, // appStorage (only for internal storage)
                    sdCardTextPlain // Store plain text for StorageInfo
            ));
            sdCardStorageText.postValue(sdCardText); // Post SpannableString directly (don't convert to String!)
        }
    }

    private void calculateInternalStorage() {
        Application app = getApplication();
        Context ctx = getLocalizedContext();

        // Calculate internal storage - post results immediately when ready
        long totalMemory = getTotaLInternalMemorySize() / 1048576L;
        long availableMegs2 = getAvailableInternalMemorySize() / 1048576L;
        long currentAppSize = getAppSize(app) / 1048576L;

        // Only calculate folder sizes if folders exist (faster)
        File unzipFolder = StorageHelper.getUnzipFolder(app, false);
        long currentAudiosSizeInternal = 0;
        if (unzipFolder != null && unzipFolder.exists()) {
            myLogI("StatsViewModel: Calculating getFolderSize for internal unzipped folder: "
                    + unzipFolder.getAbsolutePath());
            long startTime = System.currentTimeMillis();
            currentAudiosSizeInternal = StorageHelper.getFolderSize(unzipFolder) / 1048576L;
            long duration = System.currentTimeMillis() - startTime;
            myLogI("StatsViewModel: getFolderSize for internal unzipped completed in " + duration + "ms, size: "
                    + currentAudiosSizeInternal + " MB");
        }

        File imagesFolder = new File(app.getFilesDir(), "images");
        long sizeImages = 0;
        if (imagesFolder.exists()) {
            myLogI("StatsViewModel: Calculating getFolderSize for images folder: " + imagesFolder.getAbsolutePath());
            long startTime = System.currentTimeMillis();
            sizeImages = getFolderSize(imagesFolder.getPath()) / 1048576L;
            long duration = System.currentTimeMillis() - startTime;
            myLogI("StatsViewModel: getFolderSize for images completed in " + duration + "ms, size: " + sizeImages
                    + " MB");
        }

        File logsFolder = new File(app.getFilesDir(), "log");
        long sizeLogs = 0;
        if (logsFolder.exists()) {
            myLogI("StatsViewModel: Calculating getFolderSize for log folder: " + logsFolder.getAbsolutePath());
            long startTime = System.currentTimeMillis();
            sizeLogs = getFolderSize(logsFolder.getPath()) / 1048576L;
            long duration = System.currentTimeMillis() - startTime;
            myLogI("StatsViewModel: getFolderSize for log completed in " + duration + "ms, size: " + sizeLogs + " MB");
        }

        long sizeDB = 0;
        File parentFile = app.getFilesDir().getParentFile();
        if (parentFile != null) {
            File dbFolder = new File(parentFile, "databases");
            if (dbFolder.exists()) {
                myLogI("StatsViewModel: Calculating getFolderSize for databases folder: " + dbFolder.getAbsolutePath());
                long startTime = System.currentTimeMillis();
                sizeDB = getFolderSize(dbFolder.getPath()) / 1048576L;
                long duration = System.currentTimeMillis() - startTime;
                myLogI("StatsViewModel: getFolderSize for databases completed in " + duration + "ms, size: " + sizeDB
                        + " MB");
            }
        }

        File cachedImagesFolder = new File(app.getFilesDir(), "cached_images");
        long sizeCachedImages = 0;
        if (cachedImagesFolder.exists()) {
            myLogI("StatsViewModel: Calculating getFolderSize for cached_images folder: "
                    + cachedImagesFolder.getAbsolutePath());
            long startTime = System.currentTimeMillis();
            sizeCachedImages = getFolderSize(cachedImagesFolder.getPath()) / 1048576L;
            long duration = System.currentTimeMillis() - startTime;
            myLogI("StatsViewModel: getFolderSize for cached_images completed in " + duration + "ms, size: "
                    + sizeCachedImages + " MB");
        }

        // Build display text for internal storage
        long linkedAudios = StorageInfoCacheHelper.getCachedInternalLinkedAudios();
        long linkedAudiosMB = linkedAudios / 1048576L;

        String internalTextPlain = Tonio.formatMemPadding(app, totalMemory) + ctx.getString(R.string.MB_device_memory)
                + "\n" + "\n" + Tonio.formatMemPadding(app, availableMegs2)
                + ctx.getString(R.string.MB_available_on_device)
                + "\n" + "\n" + Tonio.formatMemPadding(app, currentAudiosSizeInternal)
                + ctx.getString(R.string.MB_taken_by_audio_files)
                + "\n" + "\n" + Tonio.formatMemPadding(app, linkedAudiosMB)
                + ctx.getString(R.string.MB_taken_by_linked_audios)
                + "\n" + "\n" + Tonio.formatMemPadding(app, currentAppSize)
                + ctx.getString(R.string.MB_taken_by_BookPlayer_app)
                + "\n" + "\n" + Tonio.formatMemPadding(app, sizeImages) + ctx.getString(R.string.MB_taken_by_images)
                + "\n" + "\n" + Tonio.formatMemPadding(app, sizeLogs) + ctx.getString(R.string.MB_taken_by_logs)
                + "\n" + "\n" + Tonio.formatMemPadding(app, sizeDB) + ctx.getString(R.string.MB_taken_by_databases);

        // Create SpannableString with colors matching storage bar
        SpannableString internalText = new SpannableString(internalTextPlain);
        int lightBlueColor = ContextCompat.getColor(app, R.color.pastel_blue_500); // Light blue for BookPlayer audio
                                                                                   // files
        int darkBlueColor = ContextCompat.getColor(app, R.color.pastel_blue_900); // Dark blue for BookPlayer app (app +
                                                                                  // db + logs + images)
        int greenColor = ContextCompat.getColor(app, R.color.green_500); // Green for linked audios

        // Color "MB taken by audio files" (copied audio) - LIGHT BLUE
        String audioFilesLine = Tonio.formatMemPadding(app, currentAudiosSizeInternal)
                + ctx.getString(R.string.MB_taken_by_audio_files);
        int audioFilesStart = internalTextPlain.indexOf(audioFilesLine);
        if (audioFilesStart >= 0) {
            internalText.setSpan(new ForegroundColorSpan(lightBlueColor),
                    audioFilesStart,
                    audioFilesStart + audioFilesLine.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        // Color "MB taken by BookPlayer app" - DARK BLUE (app + db + logs + images)
        String appLine = Tonio.formatMemPadding(app, currentAppSize)
                + ctx.getString(R.string.MB_taken_by_BookPlayer_app);
        int appStart = internalTextPlain.indexOf(appLine);
        if (appStart >= 0) {
            internalText.setSpan(new ForegroundColorSpan(darkBlueColor),
                    appStart,
                    appStart + appLine.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        // Color "MB taken by images" - DARK BLUE (part of app storage)
        String imagesLine = Tonio.formatMemPadding(app, sizeImages) + ctx.getString(R.string.MB_taken_by_images);
        int imagesStart = internalTextPlain.indexOf(imagesLine);
        if (imagesStart >= 0) {
            internalText.setSpan(new ForegroundColorSpan(darkBlueColor),
                    imagesStart,
                    imagesStart + imagesLine.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        // Color "MB taken by logs" - DARK BLUE (part of app storage)
        String logsLine = Tonio.formatMemPadding(app, sizeLogs) + ctx.getString(R.string.MB_taken_by_logs);
        int logsStart = internalTextPlain.indexOf(logsLine);
        if (logsStart >= 0) {
            internalText.setSpan(new ForegroundColorSpan(darkBlueColor),
                    logsStart,
                    logsStart + logsLine.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        // Color "MB taken by databases" - DARK BLUE (part of app storage)
        String dbLine = Tonio.formatMemPadding(app, sizeDB) + ctx.getString(R.string.MB_taken_by_databases);
        int dbStart = internalTextPlain.indexOf(dbLine);
        if (dbStart >= 0) {
            internalText.setSpan(new ForegroundColorSpan(darkBlueColor),
                    dbStart,
                    dbStart + dbLine.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        // Color "MB taken by linked audios" - GREEN
        String linkedAudiosLine = Tonio.formatMemPadding(app, linkedAudiosMB)
                + ctx.getString(R.string.MB_taken_by_linked_audios);
        int linkedAudiosStart = internalTextPlain.indexOf(linkedAudiosLine);
        if (linkedAudiosStart >= 0) {
            internalText.setSpan(new ForegroundColorSpan(greenColor),
                    linkedAudiosStart,
                    linkedAudiosStart + linkedAudiosLine.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        // Post internal storage results immediately (don't wait for SD card)
        if (totalMemory > 0) {
            long totalMemoryBytes = totalMemory * 1048576L;
            long availableBytes = availableMegs2 * 1048576L;
            long usedByBookPlayerBytes = currentAppSize * 1048576L;
            long usedTotalBytes = totalMemoryBytes - availableBytes;
            long usedByOthersBytes = usedTotalBytes - usedByBookPlayerBytes;

            if (usedByOthersBytes < 0)
                usedByOthersBytes = 0;
            if (usedByBookPlayerBytes < 0)
                usedByBookPlayerBytes = 0;

            long appStorageBytes = StorageInfoCacheHelper.getCachedInternalApp();
            internalStorageInfo.postValue(new StorageInfo(
                    totalMemoryBytes,
                    usedByOthersBytes,
                    usedByBookPlayerBytes,
                    0, // expectedAddedMemory
                    linkedAudios,
                    appStorageBytes,
                    internalTextPlain // Store plain text for StorageInfo
            ));
            internalStorageText.postValue(internalText); // Post SpannableString directly (don't convert to String!)
        }
    }

    private void calculateSDCardStorage() {
        Application app = getApplication();
        Context ctx = getLocalizedContext();

        // Calculate SD card storage separately (can be slow)
        long total = getTotalRemovableSDCardSize(app) / 1048576L;
        if (total > 0) {
            long available = getAvailableRemovableSDCardSize(app) / 1048576L;

            // Post initial SD card info without folder size (fast)
            long linkedAudiosInitial = StorageInfoCacheHelper.getCachedSDCardLinkedAudios();
            long linkedAudiosInitialMB = linkedAudiosInitial / 1048576L;

            String sdCardTextInitialPlain = Tonio.formatMemPadding(app, total)
                    + ctx.getString(R.string.MB_SD_card_memory)
                    + "\n\n" + Tonio.formatMemPadding(app, available) + ctx.getString(R.string.MB_available_on_SD_card)
                    + "\n\n" + ctx.getString(R.string.calculating_storage)
                    + " " + ctx.getString(R.string.MB_taken_by_audio_files)
                    + "\n\n" + Tonio.formatMemPadding(app, linkedAudiosInitialMB)
                    + ctx.getString(R.string.MB_taken_by_linked_audios);

            // Create SpannableString with colors matching storage bar
            SpannableString sdCardTextInitial = new SpannableString(sdCardTextInitialPlain);
            int blueColor = ContextCompat.getColor(app, R.color.pastel_blue_500); // Blue for BookPlayer used
            int greenColor = ContextCompat.getColor(app, R.color.green_500); // Green for linked audios

            // Color "MB taken by linked audios" - GREEN (audio files line shows
            // "calculating..." so skip it)
            String linkedAudiosLine = Tonio.formatMemPadding(app, linkedAudiosInitialMB)
                    + ctx.getString(R.string.MB_taken_by_linked_audios);
            int linkedAudiosStart = sdCardTextInitialPlain.indexOf(linkedAudiosLine);
            if (linkedAudiosStart >= 0) {
                sdCardTextInitial.setSpan(new ForegroundColorSpan(greenColor),
                        linkedAudiosStart,
                        linkedAudiosStart + linkedAudiosLine.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }

            long totalSDCardBytes = total * 1048576L;
            long availableSDCardBytes = available * 1048576L;
            long usedTotalSDCardBytes = totalSDCardBytes - availableSDCardBytes;
            long usedByOthersSDCardBytes = usedTotalSDCardBytes; // Approximate initially

            if (usedByOthersSDCardBytes < 0)
                usedByOthersSDCardBytes = 0;

            sdCardStorageInfo.postValue(new StorageInfo(
                    totalSDCardBytes,
                    usedByOthersSDCardBytes,
                    0, // Will be updated after folder scan
                    0, // expectedAddedMemory
                    linkedAudiosInitial,
                    0, // appStorage (only for internal storage)
                    sdCardTextInitialPlain // Store plain text for StorageInfo
            ));
            sdCardStorageText.postValue(sdCardTextInitial); // Post SpannableString directly (don't convert to String!)

            // Now calculate folder size in background (can be slow, but doesn't block UI)
            executorService.execute(() -> {
                try {
                    myLogI("StatsViewModel: Starting SD card folder size calculation");
                    File sdUnzipFolder = StorageHelper.getUnzipFolder(app, true);
                    long currentAudiosSizeSD = 0;
                    if (sdUnzipFolder != null && sdUnzipFolder.exists()) {
                        myLogI("StatsViewModel: Calculating getFolderSize for SD card: "
                                + sdUnzipFolder.getAbsolutePath());
                        long startTime = System.currentTimeMillis();
                        currentAudiosSizeSD = StorageHelper.getFolderSize(sdUnzipFolder) / 1048576L;
                        long duration = System.currentTimeMillis() - startTime;
                        myLogI("StatsViewModel: getFolderSize for SD card completed in " + Tonio.formatTime(duration)
                                + ", size: " + currentAudiosSizeSD + " MB");
                    } else {
                        myLogI("StatsViewModel: SD card unzip folder does not exist, skipping folder size calculation");
                    }

                    // Update with actual folder size
                    long linkedAudios = StorageInfoCacheHelper.getCachedSDCardLinkedAudios();
                    long linkedAudiosMB = linkedAudios / 1048576L;

                    String sdCardTextPlain = Tonio.formatMemPadding(app, total)
                            + ctx.getString(R.string.MB_SD_card_memory)
                            + "\n\n" + Tonio.formatMemPadding(app, available)
                            + ctx.getString(R.string.MB_available_on_SD_card)
                            + "\n\n" + Tonio.formatMemPadding(app, currentAudiosSizeSD)
                            + ctx.getString(R.string.MB_taken_by_audio_files)
                            + "\n\n" + Tonio.formatMemPadding(app, linkedAudiosMB)
                            + ctx.getString(R.string.MB_taken_by_linked_audios);

                    // Create SpannableString with colors matching storage bar
                    SpannableString sdCardText = new SpannableString(sdCardTextPlain);
                    int blueColorInner = ContextCompat.getColor(app, R.color.pastel_blue_500); // Blue for BookPlayer
                                                                                               // used
                    int greenColorInner = ContextCompat.getColor(app, R.color.green_500); // Green for linked audios

                    // Color "MB taken by audio files" (copied audio) - BLUE
                    String audioFilesLineInner = Tonio.formatMemPadding(app, currentAudiosSizeSD)
                            + ctx.getString(R.string.MB_taken_by_audio_files);
                    int audioFilesStartInner = sdCardTextPlain.indexOf(audioFilesLineInner);
                    if (audioFilesStartInner >= 0) {
                        sdCardText.setSpan(new ForegroundColorSpan(blueColorInner),
                                audioFilesStartInner,
                                audioFilesStartInner + audioFilesLineInner.length(),
                                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }

                    // Color "MB taken by linked audios" - GREEN
                    String linkedAudiosLineInner = Tonio.formatMemPadding(app, linkedAudiosMB)
                            + ctx.getString(R.string.MB_taken_by_linked_audios);
                    int linkedAudiosStartInner = sdCardTextPlain.indexOf(linkedAudiosLineInner);
                    if (linkedAudiosStartInner >= 0) {
                        sdCardText.setSpan(new ForegroundColorSpan(greenColorInner),
                                linkedAudiosStartInner,
                                linkedAudiosStartInner + linkedAudiosLineInner.length(),
                                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }

                    long usedByBookPlayerSDCardBytes = currentAudiosSizeSD * 1048576L;
                    long usedByOthersSDCardBytesUpdated = usedTotalSDCardBytes - usedByBookPlayerSDCardBytes;

                    if (usedByOthersSDCardBytesUpdated < 0)
                        usedByOthersSDCardBytesUpdated = 0;
                    if (usedByBookPlayerSDCardBytes < 0)
                        usedByBookPlayerSDCardBytes = 0;

                    sdCardStorageInfo.postValue(new StorageInfo(
                            totalSDCardBytes,
                            usedByOthersSDCardBytesUpdated,
                            usedByBookPlayerSDCardBytes,
                            0, // expectedAddedMemory
                            linkedAudios,
                            0, // appStorage (only for internal storage)
                            sdCardTextPlain // Store plain text for StorageInfo
                    ));
                    sdCardStorageText.postValue(sdCardText); // Post SpannableString directly (don't convert to String!)
                    myLogI("StatsViewModel: SD card storage info updated with folder size");
                } catch (Exception e) {
                    myLogEE(e, "StatsViewModel: Error calculating SD card folder size");
                }
            });
        }
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        executorService.shutdown();
    }
}
