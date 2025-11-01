package com.driot.bookplayer.services;

import static com.driot.bookplayer.db.Sql.updateFolderTable;
import static com.driot.bookplayer.utils.Tonio.formatMemPadding;
import static com.driot.bookplayer.utils.Tonio.formatNameForDisplay;
import static com.driot.bookplayer.utils.Tonio.formatTime;
import static com.driot.bookplayer.utils.Tonio.getExtension;
import static com.driot.bookplayer.utils.Tonio.getFileNameFromPath;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;
import androidx.work.WorkerParameters;

import com.driot.bookplayer.R;
import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.db.DatabaseClient;
import com.driot.bookplayer.db.Folder;
import com.driot.bookplayer.db.ZikFile;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.helpers.FirebaseAnalyticsHelper;
import com.driot.bookplayer.helpers.ImageHelper;
import com.driot.bookplayer.helpers.SupportedFilesHelper;
import com.driot.bookplayer.helpers.UriHelper;
import com.driot.bookplayer.imports.ImportJob;
import com.driot.bookplayer.imports.ImportWorker;
import com.driot.bookplayer.objects.AudioFileInfo;
import com.driot.bookplayer.objects.AudioInfo;
import com.driot.bookplayer.objects.AudioProber;
import com.driot.bookplayer.utils.MetaJson;
import com.driot.bookplayer.utils.Tonio;

import java.util.ArrayList;
import java.util.Objects;

public class FinalParseFolderWorker extends ImportWorker {
    private static final String TASK_NAME = Var.WORKER_TASK_LABEL_SCAN;

    private enum SaveResultEnum {SUCCESS, SKIPPED, FAILED}

    private ArrayList<AudioFileInfo> audioFileInfoArrayList;

    private long fullFolderSize; //to make storage space checks

    // global because recursive method
    private long totalDuration;
    private int nbFileScan;
    private int totalAudioToScan = 0;
    private int nbAudioScanned = 0;
    
    ImportJob importJob;

    public static final String K_DYNAMIC_URI   = "dynamic_uri";
    public static final String K_DYNAMIC_TYPE  = "dynamic_type"; // "Folder" | "File" | "ZIP"
    public static final String K_TITLE         = "title";
    public static final String K_FUTURE_PATH   = "future_folder_path";
    public static final String K_SOURCE_LOC    = "source_location"; // your enum/int/string
    public static final String K_ORIGINAL_HASH = "original_hash";   // optional
    public static final String K_IMAGE_URI     = "image_uri";       // optional

    private final Context context;

    public FinalParseFolderWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        this.context = context.getApplicationContext();
    }
    private static final String K_ERR = "error_msg";

    @NonNull
    @Override
    public Result doWork() {
        emitTaskStart(TASK_NAME, context.getString(R.string.import_task_final_parse) + " " + context.getString(R.string.import_task_start));
        try {
            DocumentFile df;
            importJob = jobOrFail();
            /*
            String originalType = j.originalType;
            String dynamicType = j.dynamicType;
            
            String playType = j.playType;
            String title = j.title;
            String futureFolderPath = j.futureFolderPath;
            String sourceLocation = j.sourceLocation;
            String originalHash = j.originalHash;
            String imagePath = j.imagePath;
            String fileExtension = j.fileExtension;
            boolean optionCopy = j.optionCopy;
            boolean optionDelete = j.optionDelete;
            
             */

                    

/*
            // New: override with InputData if provided
            String inDynUri  = getInputData().getString(K_DYNAMIC_URI);
            String inDynType = getInputData().getString(K_DYNAMIC_TYPE);
            if (inDynUri != null && inDynType != null) {
                // Build a minimal LoadBookTaskState on the fly
                LoadBookTaskState s = new LoadBookTaskState();
                s.dynamicUri = Uri.parse(inDynUri);
                s.dynamicType = inDynType;
                s.title = getInputData().getString(K_TITLE);
                s.futureFolderPath = getInputData().getString(K_FUTURE_PATH); // store SAF uri string
                s.sourceLocation = getInputData().getString(K_SOURCE_LOC);
                s.originalHash = getInputData().getString(K_ORIGINAL_HASH);
                s.imagePath = getInputData().getString(K_IMAGE_URI);
                // sensible defaults
                s.optionCopy = false;
                s.optionDelete = false;
                s.originalType = inDynType;
                bookState = s;
            }

 */

            if (importJob == null) {
                emitFailed(TASK_NAME, "importJob == null", getApplicationContext().getString(R.string.invalid_resource));
                return Result.failure();
            }

            boolean isFolderComputed = UriHelper.isFolder(context, Uri.parse(importJob.dynamicUri));
            myLogD("isFolderComputed : " + isFolderComputed);
            myLogD("original Type : " + importJob.originalType);
            myLogD("dynamic Type : " + importJob.dynamicType + "   <--- we check that");
            myLogD("dynamic Uri : " + importJob.dynamicUri);


            if (importJob.dynamicType.equals("Folder")) {
                try {
                    df = UriHelper.getDocumentFileFromAnyUri(context, Uri.parse(importJob.dynamicUri));
                } catch (Exception e) {
                    myLogEE(e,"Error reading Folder Uri...." + Uri.parse(importJob.dynamicUri));
                    emitFailed(TASK_NAME, "Error_Import_CannotReadFolder_exception", context.getString(R.string.Error_Import_CannotReadFolder));
                    return Result.failure();
                }
                if (df == null) {
                    emitFailed(TASK_NAME, "Error_Import_CannotReadFolder_null", context.getString(R.string.Error_Import_CannotReadFolder));
                    return Result.failure();
                }
                populateArrayListOfTracksFromFolder(df);


            } else {
                try {
                    df = UriHelper.getDocumentFileFromAnyUri(context, Uri.parse(importJob.dynamicUri));
                } catch (Exception e) {
                    myLogEE(e,"Error reading File Uri.... " + Uri.parse(importJob.dynamicUri));
                    emitFailed(TASK_NAME, "Error_Import_CannotReadFile_exception", context.getString(R.string.Error_Import_CannotReadFile));
                    return Result.failure();
                }
                if (df == null) {
                    emitFailed(TASK_NAME, "Error_Import_CannotReadFile_null", context.getString(R.string.Error_Import_CannotReadFile));
                    return Result.failure();
                }
            /*
            try {
                // the temp image is updated at the end..  ImageHelper.finalizeTempFolderImage
                MyAudioMetadata metadata = AudioMetadataHelper.extractMetadata(context, importJob.Uri.parse(importJob.dynamicUri));

            } catch (Throwable t) {
                myLogEE(t, "Error parsing metadata");
            }
             */
                populateArrayListOfTracksFromFile(df);


            }
            return Result.success();

        } catch (ImportAbortException abort) {
            return Result.failure(abort.out);
        } catch (Throwable t) {
            return failResult( // you can still reuse failResult for unknown crashes
                    TASK_NAME,
                    "Unexpected: " + t.getClass().getSimpleName() + " - " + (t.getMessage() == null ? "" : t.getMessage()),
                    getApplicationContext().getString(R.string.unexpected_error)
            );
        }
    }

    // single file
    ///////////////////////////
    private void populateArrayListOfTracksFromFile(DocumentFile dfPickedFile) {
        myLog("populateArrayListOfTracksFromFile [" + dfPickedFile.getUri() + "] - single file");

        if (dfPickedFile.isDirectory()) {
            emitFailed(TASK_NAME,"Error_Import_IsNotFile", context.getString(R.string.Error_Import_IsNotFile));
            return;
        }

        audioFileInfoArrayList = new ArrayList<>();

        if (importJob.playType != null && importJob.playType.equals(Var.PLAY_TYPE_TEXT)) {
            addTextFileUnique(dfPickedFile);
        } else {
            addAudioFileUnique(dfPickedFile);
        }
        goFolder();
    }

    private void populateArrayListOfTracksFromFolder(DocumentFile dfPickedDir) {
        if (dfPickedDir == null) {
            emitFailed(TASK_NAME, "populateArrayListOfTracksFromFolder - dfPickedDir == null", context.getString(R.string.Error_Import_CannotReadFolder));
            return;
        }
        if (!dfPickedDir.isDirectory()) {
            emitFailed(TASK_NAME, "populateArrayListOfTracksFromFolder - dfPickedDir is not directory", context.getString(R.string.Error_Import_IsNotFolder));
            return;
        }

        myLog("populateArrayListOfTracksFromFolder - DocumentFile [" + dfPickedDir + "]");
        emitStepProgress(TASK_NAME, 3, context.getString(R.string.listing_and_sorting_tracks));

        audioFileInfoArrayList = new ArrayList<>();
        Thread backgroundThread;
        myLog("importJob.playType = " + importJob.playType);
        if (importJob.playType != null && importJob.playType.equals(Var.PLAY_TYPE_TEXT)) {
            backgroundThread = new Thread(() -> {
                addTextFileRecursive(dfPickedDir);

                myLogD("addTextFileRecursive done, sorting now...");
                audioFileInfoArrayList.sort(AudioFileInfo.SMART_CHAPTER_COMPARATOR);

                if (audioFileInfoArrayList.isEmpty()) {
                    myLog("No File found in directory : [" + dfPickedDir.getName() + ']');
                } else {
                    myLog(audioFileInfoArrayList.size() + " files found in directory : [" + dfPickedDir.getName() + ']');
                    myLog("Full directory size : [" + formatMemPadding(fullFolderSize/1024/1024,0) + " Mo]");
                    myLogD("-----------------------------");
                }
                goFolder();
            });
        } else {
            myLog("running recursive scan for audio file in a background thread");
            backgroundThread = new Thread(() -> {
                addAudioFileRecursive(dfPickedDir);

                myLogD("addAudioFileRecursive done, sorting now...");
                audioFileInfoArrayList.sort(AudioFileInfo.SMART_CHAPTER_COMPARATOR);

                if (audioFileInfoArrayList.isEmpty()) {
                    myLog("No File found in directory : [" + dfPickedDir.getName() + ']');
                } else {
                    myLog(audioFileInfoArrayList.size() + " files found in directory : [" + dfPickedDir.getName() + ']');
                    myLog("Full directory size : [" + formatMemPadding(fullFolderSize/1024/1024,0) + " Mo]");
                    myLogD("-----------------------------");
                }
                goFolder();
            });
        }
        try {
            backgroundThread.start();
        } catch (Throwable t) {
            String devErr = "backgroundThread addAudioFileRecursive";
            String userErr = context.getString(R.string.Error_while_listing_audio_files);
            if (t instanceof OutOfMemoryError && t.getMessage() != null && t.getMessage().contains("pthread_create")) {
                myLogEE(t,"addAudioFileRecursive : Too many threads or not enough native memory");
                devErr = "addAudioFileRecursive : Too many threads or not enough native memory" + "\n" + t.getMessage();
                userErr = context.getString(R.string.Error_Import_OutOfMemory)
                        + "\n" + context.getString(R.string.Error_Import_This_folder_may_contain_too_many_books);
            } else {
                devErr = devErr + "\n" + t.getMessage();
            }
            failNow(TASK_NAME, devErr, userErr);
        }
    }

    private void addAudioFileUnique(DocumentFile df) {
        myLogD("* New Audio File : [" +  df.getName() + ']');
        AudioInfo audioInfo = AudioProber.probe(context, df.getUri(), true);
        if (audioInfo == null || audioInfo.durationMs <= 0) {
            failNow(
                    TASK_NAME
                    , "Error_Import_extract_audio_data_failed for [" + df.getName() + "]"
                    , context.getString(R.string.Error_Import_extract_audio_data_failed) + " for [" + df.getName() + "]");

        } else {
            myLogD("* Duration : [" +  formatTime(audioInfo.durationMs) + ']');
            AudioFileInfo afi = AudioFileInfo.fromProbe(audioInfo, df.getName());
            audioFileInfoArrayList.add(afi);
            //audioFileInfoArrayList.add(new AudioFileInfo(df.getName(), audioInfo.durationMs, audioInfo.uri.toString()));
            audioInfo.saveCover(this.context);
        }
    }
    private void addAudioFileRecursive(DocumentFile f0) {
        totalDuration = 0;
        nbFileScan = 0;
        fullFolderSize = 0;
        totalAudioToScan = 0;
        nbAudioScanned = 0;
        countAudioFiles(f0);
        addAudioFileRecursive(f0,"");
        if (totalDuration==0) {
            failNow(TASK_NAME
                    , "addAudioFileRecursive - Error_Import_track_duration_extraction"
                    , context.getString(R.string.Error_Import_track_duration_extraction));
        }
    }
    private void addAudioFileRecursive(DocumentFile f0, String recursivFolder) {
        myLogD("-------------------------------------------------------------------------------------------------------------------xxx");
        myLogD("-------------------------------------------------------------------------------------------------------------------xxx");
        String l_audioFilePath;
        long l_audioSize;
        boolean hadImageBefore = importJob.imagePath != null; //dont look in subDir if image found at top dir
        for (DocumentFile f1 : f0.listFiles()) {
            if (f1.isDirectory()) {
                myLog("increase recursive depth for Directory : [" + f1.getName() + "]");
                addAudioFileRecursive(f1,recursivFolder + f1.getName() + '/');
            } else {
                String fileName = SupportedFilesHelper.getFileName(f1);
                String fileExtension = SupportedFilesHelper.getFileExtension(f1);
                String mimeType = SupportedFilesHelper.getMimeType(f1);
                myLogD("* Checking File : [" + fileExtension + "] . [" + fileName + "] - mime = [" + mimeType + "] - subfolder : [" + recursivFolder + "]");

                if (SupportedFilesHelper.isAudio(f1) || SupportedFilesHelper.isVideo(f1)) {
                    nbFileScan = nbFileScan + 1;
                    l_audioFilePath = recursivFolder + f1.getName();
                    l_audioSize = f1.length();

                    long duration = 0;
                    AudioInfo audioInfo = AudioProber.probe(context, f1.getUri(), false);
                    if (audioInfo == null) {
                        emitWarning(context.getString(R.string.Error_Import_extract_audio_data_failed) + " for [" + f1.getName() + "]");
                    } else if (audioInfo.durationMs <= 0) {
                        emitWarning(context.getString(R.string.Error_Import_track_duration_extraction) + " for [" + f1.getName() + "]");
                    } else {
                        duration = audioInfo.durationMs;
                        AudioFileInfo afi = AudioFileInfo.fromProbe(audioInfo, f1.getName());
                        audioFileInfoArrayList.add(afi);
                        //audioFileInfoArrayList.add(new AudioFileInfo(l_audioFilePath, duration, audioInfo.uri.toString()));
                    }
                    myLogD("Audio File : [" + l_audioFilePath + "] - size = [" + l_audioSize + "] - [" +  formatTime(duration) + "]");

                    totalDuration = totalDuration + duration;
                    nbAudioScanned++;
                    double progress = totalAudioToScan > 0 ? (nbAudioScanned / (double) totalAudioToScan) : 0;
                    int scaledProgress = 10 + (int) ((80 - 10) * progress);
                    emitStepProgress(TASK_NAME, scaledProgress, context.getString(R.string.scanning_tracks) + " " + nbAudioScanned + "..... \n[" +  l_audioFilePath + ']');

                    fullFolderSize = fullFolderSize + l_audioSize;

                } else if (SupportedFilesHelper.isImage(f1)) {
                    if (!hadImageBefore) {
                        long imageSize = f1.length();
                        if (importJob.imagePath == null || imageSize > UriHelper.getSize(context, Uri.parse(importJob.imagePath))) {
                            myLogD("New biggest Picture Found, size = [" + Tonio.formatMemPadding(imageSize) + "] - [" + f1.getUri() + "]");
                            importJob.imagePath = f1.getUri().toString();
                            hadImageBefore = true;
                        }
                    } else {
                        myLogD("bypassing image (already got a cover)");
                    }
                } else {
                    emitWarning(context.getString(R.string.Error_Import_unsupported_file) + " - [" + fileExtension + "] : "
                            + "\"" + f1.getName() + "\"");
                    myLogW("Wrong mime/extension - [\" + fileExtension + \"] - Bypassed file: [" + f1.getName() + "]");
                }
            }
        }
        myLogD("-------------------------------------------------------------------------------------------------------------------xxx");
        myLogD("-------------------------------------------------------------------------------------------------------------------xxx");
    }

    private void addTextFileUnique(DocumentFile df) {
        String name = Objects.toString(df.getName());
        String mime = Objects.toString(df.getType());
        myLogD("* New Text File : [" + name + ']');

        long duration = estimateTtsDurationMsFromUri(context, df.getUri(), name, mime);
        myLogD("* TTS Duration (est.): [" + formatTime(duration) + ']');

        audioFileInfoArrayList.add(new AudioFileInfo(name, duration, df.getUri().toString(), null));
    }

    private void addTextFileRecursive(DocumentFile root) {
        totalDuration = 0;      // not used for text, but keep consistent
        nbFileScan = 0;
        fullFolderSize = 0;
        totalAudioToScan = 0;   // reuse counters for progress
        nbAudioScanned = 0;

        countTextFiles(root);
        addTextFileRecursive(root, "");
    }

    private void addTextFileRecursive(DocumentFile dir, String recursiveFolder) {
        boolean hadImageBefore = importJob.imagePath != null; //dont look in subDir if image found at top dir
        for (DocumentFile f1 : dir.listFiles()) {
            if (f1.isDirectory()) {
                myLog("increase recursive depth for Directory : [" + f1.getName() + "]");
                addTextFileRecursive(f1, recursiveFolder + f1.getName() + '/');
            } else {
                String fileName = SupportedFilesHelper.getFileName(f1);
                String fileExtension = SupportedFilesHelper.getFileExtension(f1);
                String mimeType = SupportedFilesHelper.getMimeType(f1);
                myLogD("* Checking File (TEXT): [" + fileExtension + "] . [" + fileName + "] - mime = [" + mimeType + "] - subfolder : [" + recursiveFolder + "]");

                if (SupportedFilesHelper.isText(f1)) {
                    nbFileScan++;
                    String displayPath = recursiveFolder + fileName;
                    long size = f1.length();

                    long duration = estimateTtsDurationMsFromUri(context, f1.getUri(), fileName, mimeType);
                    myLogD("text file duration :" + Tonio.formatTime(duration));
                    audioFileInfoArrayList.add(new AudioFileInfo(displayPath, duration, f1.getUri().toString(), null));
                    fullFolderSize += size;

                    nbAudioScanned++;
                    double progress = totalAudioToScan > 0 ? (nbAudioScanned / (double) totalAudioToScan) : 0;
                    int scaledProgress = 10 + (int) ((80 - 10) * progress);
                    emitStepProgress(TASK_NAME, scaledProgress,
                            context.getString(R.string.scanning_tracks) + " " + nbAudioScanned + "..... \n[" + displayPath + ']');
                } else if (!hadImageBefore && (SupportedFilesHelper.isImage(f1))) {
                    long imageSize = f1.length();
                    if (importJob.imagePath == null || imageSize > UriHelper.getSize(context, Uri.parse(importJob.imagePath))) {
                        myLogD("New biggest Picture Found, size = [" + Tonio.formatMemPadding(imageSize) + "] - [" + f1.getUri() + "]");
                        importJob.imagePath = f1.getUri().toString();
                        hadImageBefore = true;
                    }
                } else {
                    myLogW("Wrong mime/extension for TEXT import - [" + fileExtension + "] - Bypassed file: [" + fileName + "]");
                }
            }
        }
    }

    private void countTextFiles(DocumentFile dir) {
        for (DocumentFile f1 : dir.listFiles()) {
            if (f1.isDirectory()) {
                countTextFiles(f1);
            } else {
                String ext = getExtension(f1.getName());
                String mime = Objects.toString(f1.getType());
                if ((mime != null && mime.startsWith("text/")) || "txt".equalsIgnoreCase(ext)) {
                    totalAudioToScan++; // reusing counter
                }
            }
        }
    }

    private void goFolder() {
        if (audioFileInfoArrayList != null) {
            if (!audioFileInfoArrayList.isEmpty()) {
                myLog(audioFileInfoArrayList.size() + " " + context.getString(R.string.Import_nMediaInFolder));
                if (Option.getCreateCover()) {
                    try {
                        if (importJob.imagePath == null || importJob.imagePath.isEmpty()) {
                            myLog("creating fallback bitmap as cover");
                            String path = ImageHelper.createFallbackManualFolderImagePreInsert(
                                    context,
                                    importJob.title,
                                    importJob.futureFolderPath,
                                    Var.FALL_BACK_COVER_IMAGE_SIZE_IN_PIXELS
                            );
                            if (path != null) {
                                importJob.imagePath = path;
                            }
                        }
                    } catch (Exception e) {
                        myLogEE(e, "Error creating cover" );
                    }
                }
                saveFolder();
            } else {
                failNow(TASK_NAME, "Error_Import_NoMediaInFolder", context.getString(R.string.Error_Import_NoMediaInFolder));
            }
        } else {
            failNow(TASK_NAME, "Error_Import_NoMediaInFolder", context.getString(R.string.Error_Import_NoMediaInFolder));
        }
    }

    private void saveFolder() {
        emitStepProgress(TASK_NAME, 81, context.getString(R.string.saving_folder));

        Folder folder = new Folder();
        folder.setName(importJob.title);
        folder.setPath(importJob.futureFolderPath);
        folder.setUri(importJob.futureFolderPath); //2023-10-22 deprecated
        folder.setHash("0"); //2023-10-22 deprecated
        folder.setPercentdone(0.0);
        folder.setFinished(false);
        folder.setIszipfile(false); //2023-10-22 deprecated (live zip reading - code has been removed)
        folder.setOriginalHash(importJob.originalHash);
        folder.setOriginalFile(importJob.originalFile);
        folder.setOriginalType(importJob.originalType);
        folder.setSourceLocation(importJob.sourceLocation);
        folder.playType = importJob.playType;
        folder.date_added = System.currentTimeMillis();
        folder.image = importJob.imagePath;
        folder.lLastAccess = System.currentTimeMillis(); //used to sort the Book on the main page

        new Thread(() -> {
            int insertedFolderId = (int) DatabaseClient.getInstance(context).getAppDatabase().folderDao().insert(folder);
            myLog("Folder Saved in DB, ID=[" + insertedFolderId + "] - [" + importJob.title + "]");
            ImageHelper.finalizeTempFolderImage(context, insertedFolderId);
            emitStepProgress(TASK_NAME, 83, context.getString(R.string.saving_folder));
            saveFiles(insertedFolderId);
        }).start();
    }

    private void saveFiles(int insertedFolderId) {
        myLogD("--------------------- saving files...");
        if (audioFileInfoArrayList == null) {
            failNow(TASK_NAME, "saveFiles - audioFileArrayList is null", context.getString(R.string.Error_Import_no_valid_media_found));
            return;
        }

        int total = audioFileInfoArrayList.size();
        int saved = 0;
        int skipped = 0;
        int failed = 0;

        for (int i = 0; i < total; i++) {
            AudioFileInfo info = audioFileInfoArrayList.get(i);
            int zeOrder = saved + 1;

            int progress = 85 + ((i + 1) * 100 / total) * (98 - 85) / 100;
            String txtProgress = progress + "% - " + context.getString(R.string.saving_track) +
                    " n°" + i + 1 + "/" + total + "\n" + getFileNameFromPath(info.getDisplayPath());

            myLogD("Registering track [" + info.getDisplayPath() + "]");
            SaveResultEnum result = saveSingleFile(info, insertedFolderId, zeOrder);
            emitStepProgress(TASK_NAME, progress, txtProgress);

            switch (result) {
                case SUCCESS: saved++; break;
                case SKIPPED: skipped++; break;
                case FAILED: failed++; break;
            }
        }

        myLogD("🎧 Import Summary:");
        myLogD("✔️ Saved:   " + saved);
        myLogD("⏭️ Skipped: " + skipped);
        myLogD("❌ Failed:  " + failed);

        if (saved == 0) {
            myLogEE(null,"no saved zikFile, about to delete folder...");
            new Thread(() -> {
                try {
                    DatabaseClient.getInstance(context).getAppDatabase().folderDao().delete(insertedFolderId);
                } catch (Exception e) {
                    myLogEE(e, "error deleting folder (no saved zikFiles)");
                }
                myLog("Folder Deleted");
            }).start();
        }

        // All files done
        myLogD("******************************************************************************************************************");
        myLogD("******************************************************************************************************************");
        myLogD("***************************      All files have been processed. -- OK      ***************************************");
        myLogD("******************************************************************************************************************");
        myLogD("******************************************************************************************************************");
        updateFolderTable(context, insertedFolderId);

        // check we have something in folder...
        if (saved == 0) {
            failNow(TASK_NAME, "saved = 0 - Error_Import_No_Usable_item_Found", context.getString(R.string.Error_Import_No_Usable_item_Found));
            return;
        } else {
            //FirebaseAnalyticsHelper.tellLoadBookSuccess(String.valueOf(importJob.originalUri), importJob.fileExtension, importJob.doDownload);
            if (Var.SOURCE_LOCATION_LIBRIVOX.equals(importJob.sourceLocation)) {
                FirebaseAnalyticsHelper.tellLibrivoxSuccess(String.valueOf(importJob.title));
                AppDatabase.databaseWriteExecutor.execute(() -> {
                    AppDatabase db = AppDatabase.getDatabase(context);
                    db.bookSourceDao().markImported(
                            Var.REPO_TYPE_AUDIOBOOK,               // repoType (lowercase)
                            Var.REPO_NAME_LIBRIVOX,                // repoName (lowercase)
                            importJob.futureFolderName,                // repoId (e.g., "dracula_123")
                            insertedFolderId,                  // the new Folder.id
                            importJob.title,                     // display title
                            importJob.originalUri.toString(),  // source_url
                            null,      // imageLocal if you saved it
                            null       // imageRemote if available
                    );
                });
            }
        }

        myLogD("deleting source ??"
                + "\nOption CopyFile : " + importJob.optionCopy + "  -  is a ZIP : " + importJob.dynamicType.equals("ZIP")
                + "\nOption DeleteSourceFile : " + importJob.optionDelete);
        if ((importJob.optionCopy || "ZIP".equals(importJob.dynamicType)) && importJob.optionDelete) {
            if (!deleteSourceFile()) {
                emitWarning(context.getString(R.string.Error_Import_could_not_delete_source));
            }
        }
        emitSuccess();
    }


    private SaveResultEnum saveSingleFile(AudioFileInfo info, int folderId, int zeOrder) {
        ZikFile file = new ZikFile();
        file.setName(info.getDisplayPath());
        file.setDisplayName(formatNameForDisplay(info.getDisplayPath()));
        file.setIdFolder(folderId);
        file.setZeorder(zeOrder);
        file.setFolderName(importJob.title);
        file.setPercentdone(0.0);
        file.setPosition(0);
        file.setPath(info.getContentUri());
        file.setIszipfile(false);
        file.setFinished(false);
        file.setDuration(info.getDuration());
        file.date_added = System.currentTimeMillis();
        file.metadataJson = MetaJson.toJson(info.getMeta());

        if (file.getDuration() == 0) {
            myLogW("⏭️ Skipped: duration = 0 → " + info.getDisplayPath());
            return SaveResultEnum.SKIPPED;
        }

        long id = AppDatabase.getDatabase(context).zikFileDao().insert(file);
        if (id > 0) {
            //myLog("✔️ ZikFile inserted: id = " + id);
            return SaveResultEnum.SUCCESS;
        } else {
            myLogE("❌ DB insert failed for: " + info.getDisplayPath());
            //TODO, maybe better just a warning...  (anyway, should not happen)
            failNow(TASK_NAME
                    , "Error_Import_CannotSaveInDB [" + info.getDisplayPath() + "]"
                    , context.getString(R.string.Error_Import_CannotSaveInDB) + " [" + info.getDisplayPath() + "]");
            return SaveResultEnum.FAILED;
        }
    }

    private boolean deleteSourceFile() {
        myLog("deleteSourceFile() - uri = [" + importJob.originalUri + "]");
        DocumentFile dfPickedDir = null;
        if (importJob.dynamicType.equals("File") || importJob.dynamicType.equals("ZIP") || importJob.dynamicType.equals("Folder")) {
            try {
                dfPickedDir = UriHelper.getDocumentFileFromAnyUri(context, Uri.parse(importJob.originalUri));
            } catch (Exception e) {
                myLogEE(e,"deleting - error getting DocumentFile.fromSingleUri");
                return false;
            }
        } else {
            myLogEE(null,"Incorrect type : **" + importJob.dynamicType + "**");
            return false;
        }
        if (!(dfPickedDir == null)) {
            boolean okDelete = dfPickedDir.delete();
            if (okDelete) {
                myLogD("source file deletion ok");
                return true;
            } else {
                myLogEE(null,"Error during source file deletion");
                return false;
            }
        } else {
            myLogEE(null,"deleteSourceFile() => could not get ref to picked file");
            return false;
        }
    }

    private void countAudioFiles(DocumentFile f0) {
        for (DocumentFile f1 : f0.listFiles()) {
            if (f1.isDirectory()) {
                countAudioFiles(f1);
            } else {
                String ext = getExtension(f1.getName());
                String mime = Objects.toString(f1.getType());
                if (mime.startsWith(Var.ONLY_MIME_AUDIO) || Var.SUPPORTED_AUDIO_EXTENSIONS.contains(ext)) {
                    totalAudioToScan++;
                    emitStepProgress(TASK_NAME, 2, context.getString(R.string.counting_files) + " : " + totalAudioToScan);
                }
            }
        }
    }

// --- TTS duration estimation (import-time, fixed WPM) ---

    private long estimateTtsDurationMsFromUri(Context ctx, Uri uri, @Nullable String fileName, @Nullable String mimeType) {
        int words = 0;
        try (java.io.InputStream in = ctx.getContentResolver().openInputStream(uri)) {
            if (in == null) return 0;
            boolean htmlLike = isHtmlLike(fileName, mimeType);
            words = countWordsStreaming(in, htmlLike);
        } catch (Exception e) {
            myLogEE(e, "estimateTtsDurationMsFromUri");
        }
        return wordsToMs(words);
    }

    private static boolean isHtmlLike(@Nullable String fileName, @Nullable String mimeType) {
        String fn = fileName == null ? "" : fileName.toLowerCase();
        String mt = mimeType == null ? "" : mimeType.toLowerCase();
        if (mt.contains("html") || mt.contains("xhtml") || mt.contains("xml") || mt.contains("application/xhtml")) return true;
        return fn.endsWith(".html") || fn.endsWith(".htm") || fn.endsWith(".xhtml") || fn.endsWith(".xml");
    }

    /** Streams through the file and counts "words" without loading whole file.
     *  If htmlLike=true, characters inside <...> are ignored (rough tag strip).
     */
    private static int countWordsStreaming(java.io.InputStream in, boolean htmlLike) throws java.io.IOException {
        final java.io.InputStreamReader isr = new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8);
        final char[] buf = new char[8192];
        int read, count = 0;
        boolean inWord = false;
        boolean inTag = false;

        while ((read = isr.read(buf)) != -1) {
            for (int i = 0; i < read; i++) {
                char c = buf[i];

                if (htmlLike) {
                    if (c == '<') { inTag = true; inWord = false; continue; }
                    if (c == '>') { inTag = false; continue; }
                    if (inTag) { continue; }
                }

                if (Character.isLetterOrDigit(c)) {
                    if (!inWord) { count++; inWord = true; }
                } else {
                    inWord = false;
                }
            }
        }
        return count;
    }

    private static long wordsToMs(int words) {
        if (words <= 0) return 0;
        double wpm = Math.max(30, Var.TTS_WPM_IMPORT); // guardrail
        long ms = (long) Math.round((words / wpm) * 60_000.0);
        return Math.max(ms, 1L);
    }
}
