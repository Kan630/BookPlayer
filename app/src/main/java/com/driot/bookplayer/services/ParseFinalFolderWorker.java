package com.driot.bookplayer.services;

import static com.driot.bookplayer.db.Sql.updateFolderTable;
import static com.driot.bookplayer.utils.Tonio.formatMemPadding;
import static com.driot.bookplayer.utils.Tonio.formatNameForDisplay;
import static com.driot.bookplayer.utils.Tonio.formatTime;
import static com.driot.bookplayer.utils.Tonio.getExtension;
import static com.driot.bookplayer.utils.Tonio.getFileNameFromPath;

import android.content.Context;
import android.media.MediaMetadataRetriever;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.documentfile.provider.DocumentFile;
import androidx.work.WorkerParameters;

import com.driot.bookplayer.R;
import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.db.DatabaseClient;
import com.driot.bookplayer.db.Folder;
import com.driot.bookplayer.db.ZikFile;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.global.Pref;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.helpers.AudioMetadataHelper;
import com.driot.bookplayer.helpers.ImageHelper;
import com.driot.bookplayer.helpers.UriHelper;
import com.driot.bookplayer.objects.AudioFileInfo;
import com.driot.bookplayer.objects.MyAudioMetadata;
import com.driot.bookplayer.objects.TaskStateManager;
import com.driot.bookplayer.objects.LoadBookTaskState;
import com.driot.bookplayer.utils.Tonio;
import com.driot.bookplayer.utils.log.LoggingWorker;

import java.util.ArrayList;
import java.util.Objects;

public class ParseFinalFolderWorker extends LoggingWorker {
    private static final String TASK_NAME = Var.WORKER_TASK_LABEL_SCAN;

    Context context = getApplicationContext();

    private enum SaveResultEnum {SUCCESS, SKIPPED, FAILED}

    private ArrayList<AudioFileInfo> audioFileArrayList;

    private long fullFolderSize; //to make storage space checks

    // global because recursive method
    private long totalDuration;
    private int nbFileScan;
    private int totalAudioToScan = 0;
    private int nbAudioScanned = 0;

    LoadBookTaskState bookState;

    public static final String K_DYNAMIC_URI   = "dynamic_uri";
    public static final String K_DYNAMIC_TYPE  = "dynamic_type"; // "Folder" | "File" | "ZIP"
    public static final String K_TITLE         = "title";
    public static final String K_FUTURE_PATH   = "future_folder_path";
    public static final String K_SOURCE_LOC    = "source_location"; // your enum/int/string
    public static final String K_ORIGINAL_HASH = "original_hash";   // optional
    public static final String K_IMAGE_URI     = "image_uri";       // optional

    public ParseFinalFolderWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        DocumentFile df;
        Context context = getApplicationContext();
        bookState = Pref.getLoadBookTaskState();

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

        if (bookState == null) {
            TaskStateManager.markTaskFailed(TASK_NAME, "bookState == null");
            return Result.failure();
        }
        TaskStateManager.tellProgress(TASK_NAME, 1, context.getString(R.string.listing_and_sorting_tracks));


        boolean isFolderComputed = UriHelper.isFolder(context, bookState.dynamicUri);
        myLogD("isFolderComputed : " + isFolderComputed);
        myLogD("original Type : " + bookState.originalType);
        myLogD("dynamic Type : " + bookState.dynamicType);
        myLogD("dynamic Uri : " + bookState.dynamicUri);

        if (bookState.dynamicType.equals("Folder")) {
            try {
                df = UriHelper.getDocumentFileFromAnyUri(context, bookState.dynamicUri);
            } catch (Exception e) {
                myLogEE(e,"Error reading Folder Uri...." + bookState.dynamicUri);
                TaskStateManager.markTaskFailed(TASK_NAME, context.getString(R.string.Error_Import_CannotReadFolder));
                return Result.failure();
            }
            populateArrayListOfTracksFromFolder(df);
        } else {
            try {
                df = UriHelper.getDocumentFileFromAnyUri(context, bookState.dynamicUri);
            } catch (Exception e) {
                myLogEE(e,"Error reading File Uri.... " + bookState.dynamicUri);
                TaskStateManager.markTaskFailed(TASK_NAME, context.getString(R.string.Error_Import_CannotReadFile));
                return Result.failure();
            }
            if (df == null) {
                TaskStateManager.markTaskFailed(TASK_NAME, context.getString(R.string.Error_Import_CannotReadFile));
                return Result.failure();
            }
// METADATA
            MyAudioMetadata metadata = AudioMetadataHelper.extractMetadata(context, bookState.dynamicUri);
// THE REST
            populateArrayListOfTracksFromFile(df);
        }
        return Result.success();
    }

    // single file
    ///////////////////////////
    private void populateArrayListOfTracksFromFile(DocumentFile dfPickedDir) {
        myLog("populateArrayListOfTracksFromFile [" + dfPickedDir.getUri() + "] - single file");

        if (!(dfPickedDir.isDirectory())) {
            audioFileArrayList = new ArrayList<>();
            addAudioFileUnique(dfPickedDir);
            goFolder();
        } else {
            TaskStateManager.markTaskFailed(TASK_NAME, context.getString(R.string.Error_Import_IsNotFile));
        }

    }

    private void populateArrayListOfTracksFromFolder(DocumentFile dfPickedDir) {
        if (dfPickedDir == null) {
            myLogEE(null,"populateArrayListOfTracksFromFolder - dfPickedDir == null");
            TaskStateManager.markTaskFailed(TASK_NAME, context.getString(R.string.Error_Import_CannotReadFolder));
            return;
        }
        if (!dfPickedDir.isDirectory()) {
            myLogEE(null,"populateArrayListOfTracksFromFolder - dfPickedDir is not directory");
            TaskStateManager.markTaskFailed(TASK_NAME, context.getString(R.string.Error_Import_IsNotFolder));
            return;
        }

        myLog("populateArrayListOfTracksFromFolder - DocumentFile [" + dfPickedDir + "]");
        TaskStateManager.tellProgress(TASK_NAME, 3, context.getString(R.string.listing_and_sorting_tracks));

        myLog("running recursive scan for audio file in a background thread");

        audioFileArrayList = new ArrayList<>();

        Thread backgroundThread = new Thread(() -> {
            addAudioFileRecursive(dfPickedDir);

            myLogD("addAudioFileRecursive done, sorting now...");
            audioFileArrayList.sort(AudioFileInfo.ALPHANUMERIC_COMPARATOR);

            if (audioFileArrayList.isEmpty()) {
                myLog("No File found in directory : [" + dfPickedDir.getName() + ']');
            } else {
                myLog(audioFileArrayList.size() + " files found in directory : [" + dfPickedDir.getName() + ']');
                myLog("Full directory size : [" + formatMemPadding(fullFolderSize/1024/1024,0) + " Mo]");
                myLogD("-----------------------------");
            }
            goFolder();
        });
        try {
            backgroundThread.start();
        } catch (Throwable t) {
            String strErr = "Error while listing audio files";
            if (t instanceof OutOfMemoryError && t.getMessage() != null && t.getMessage().contains("pthread_create")) {
                myLogEE(t,"addAudioFileRecursive : Too many threads or not enough native memory");
                strErr = context.getString(R.string.Error_Import_OutOfMemory)
                        + "\n" + context.getString(R.string.Error_Import_This_folder_may_contain_too_many_books);
            } else {
                myLogEE(t,"addAudioFileRecursive");
                strErr = strErr + "\n" + t.getMessage();
            }
            TaskStateManager.markTaskFailed(TASK_NAME, strErr);
        }
    }

    private void addAudioFileUnique(DocumentFile df) {
        myLogD("* New Audio File : [" +  df.getName() + ']');
        long duration = getMediaDurationFromUri(context, df.getUri(), df.getName());
        if (duration==0) TaskStateManager.markTaskFailed(TASK_NAME, context.getString(R.string.Error_Import_track_duration_extraction) + " for " + df.getName());
        myLogD("* Duration : [" +  formatTime(duration) + ']');
        audioFileArrayList.add(new AudioFileInfo(df.getName(), duration, df.getUri().toString()));
    }
    private void addAudioFileRecursive(DocumentFile f0) {
        totalDuration = 0;
        nbFileScan = 0;
        fullFolderSize = 0;
        totalAudioToScan = 0;
        nbAudioScanned = 0;
        TaskStateManager.tellProgress(TASK_NAME, 5, context.getString(R.string.listing_and_sorting_tracks));
        countAudioFiles(f0);
        addAudioFileRecursive(f0,"");
        if (totalDuration==0) TaskStateManager.markTaskFailed(TASK_NAME, context.getString(R.string.Error_Import_track_duration_extraction));
    }
    private void addAudioFileRecursive(DocumentFile f0, String recursivFolder) {
        String l_audioFilePath;
        long l_audioSize;
        boolean hadImageBefore = bookState.imagePath != null; //dont look in subDir if image found at top dir
        for (DocumentFile f1 : f0.listFiles()) {
            if (f1.isDirectory()) {
                myLog("increase recursive depth for Directory : [" + f1.getName() + "]");
                addAudioFileRecursive(f1,recursivFolder + f1.getName() + '/');
            } else {
                String fileName = Objects.toString(f1.getName());
                String fileExtension = getExtension(fileName);
                String mimeType = Objects.toString(f1.getType());
                myLogD("* Checking File : [" + fileExtension + "] . [" + fileName + "] - mime = [" + mimeType + "] - subfolder : [" + recursivFolder + "]");
                if (mimeType.startsWith(Var.ONLY_MIME_AUDIO) || Var.SUPPORTED_AUDIO_EXTENSIONS.contains(fileExtension)) {
                    nbFileScan = nbFileScan + 1;
                    l_audioFilePath = recursivFolder + f1.getName();
                    l_audioSize = f1.length();
                    long duration = getMediaDurationFromUri(context, f1.getUri(), l_audioFilePath);
                    myLogD("Audio File : [" + l_audioFilePath + "] - size = [" + l_audioSize + "] - [" +  formatTime(duration) + "]");
                    totalDuration = totalDuration + duration;
                    nbAudioScanned++;
                    double progress = totalAudioToScan > 0 ? (nbAudioScanned / (double) totalAudioToScan) : 0;
                    int scaledProgress = 10 + (int) ((80 - 10) * progress);
                    TaskStateManager.tellProgress(TASK_NAME, scaledProgress, context.getString(R.string.scanning_tracks) + "..... \n[" +  l_audioFilePath + ']');
                    audioFileArrayList.add(new AudioFileInfo(l_audioFilePath, duration, f1.getUri().toString()));
                    fullFolderSize = fullFolderSize + l_audioSize;
                } else if (mimeType.startsWith(Var.ONLY_MIME_VIDEO) || Var.SUPPORTED_VIDEO_EXTENSIONS.contains(fileExtension)) {
                    myLog("Video");
                } else if (!hadImageBefore && Var.SUPPORTED_COVER_PICTURE_EXTENSIONS.contains(fileExtension)) {
                    long imageSize = f1.length();
                    if (bookState.imagePath == null || imageSize > UriHelper.getSize(context, Uri.parse(bookState.imagePath))) {
                        myLogD("New biggest Picture Found, size = [" + Tonio.formatMemPadding(imageSize) + "] - [" + f1.getUri() + "]");
                        bookState.imagePath = f1.getUri().toString();
                        hadImageBefore = true;
                    }
                } else {
                myLogW("Wrong mime/extension - [" + fileExtension + "] - Bypassed file: [" + f1.getName() + "]");
                }
            }
        }
    }

    private void goFolder() {
        if (audioFileArrayList != null) {
            if (audioFileArrayList.isEmpty()) {
                TaskStateManager.markTaskFailed(TASK_NAME, context.getString(R.string.Error_Import_NoMediaInFolder));
            } else {
                myLog(audioFileArrayList.size() + " " + context.getString(R.string.Import_nMediaInFolder));
                if (Option.getCreateCover()) {
                    try {
                        if (bookState.imagePath == null || bookState.imagePath.isEmpty()) {
                            String path = ImageHelper.createFallbackManualFolderImagePreInsert(
                                    context,
                                    bookState.title,
                                    bookState.futureFolderPath,
                                    512
                            );
                            if (path != null) {
                                bookState.imagePath = path;
                            }
                        }
                    } catch (Exception e) {
                        myLogEE(e, "Error creating cover" );
                    }
                }
                saveFolder();
            }
        } else {
            TaskStateManager.markTaskFailed(TASK_NAME, context.getString(R.string.Error_Import_NoMediaInFolder));
        }
    }

    private void saveFolder() {
        TaskStateManager.tellProgress(TASK_NAME, 81, context.getString(R.string.saving_folder));

        Folder folder = new Folder();
        folder.setName(bookState.title);
        folder.setPath(bookState.futureFolderPath);
        folder.setUri(bookState.futureFolderPath); //2023-10-22 deprecated
        folder.setHash("0"); //2023-10-22 deprecated
        folder.setPercentdone(0.0);
        folder.setFinished(false);
        folder.setIszipfile(false); //2023-10-22 deprecated (live zip reading - code has been removed)
        folder.setOriginalHash(bookState.originalHash);
        folder.setSourceLocation(bookState.sourceLocation);
        folder.date_added = System.currentTimeMillis();
        folder.image = bookState.imagePath;
        folder.lLastAccess = System.currentTimeMillis(); //used to sort the Book on the main page

        new Thread(() -> {
            int insertedFolderId = (int) DatabaseClient.getInstance(context).getAppDatabase().FolderDao().insert(folder);
            myLog("Folder Saved in DB, ID=[" + insertedFolderId + "] - [" + bookState.title + "]");
            ImageHelper.finalizeTempFolderImage(context, insertedFolderId);
            TaskStateManager.tellProgress(TASK_NAME, 83, context.getString(R.string.saving_folder));
            saveFiles(insertedFolderId);
        }).start();
    }

    private void saveFiles(int insertedFolderId) {
        if (audioFileArrayList == null) {
            TaskStateManager.markTaskFailed(TASK_NAME, "audioFileArrayList is null");
            return;
        }

        int total = audioFileArrayList.size();
        int saved = 0;
        int skipped = 0;
        int failed = 0;

        for (int i = 0; i < total; i++) {
            AudioFileInfo info = audioFileArrayList.get(i);
            int zeOrder = saved + 1;

            int progress = 85 + ((i + 1) * 100 / total) * (98 - 85) / 100;
            String txtProgress = progress + "% - " + context.getString(R.string.saving_track) +
                    " n°" + i + 1 + "/" + total + "\n" + getFileNameFromPath(info.getDisplayPath());

            myLogD("Registering track [" + info.getDisplayPath() + "]");
            SaveResultEnum result = saveSingleFile(info, insertedFolderId, zeOrder);
            TaskStateManager.tellProgress(TASK_NAME, progress, txtProgress);

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

        // All files done
        myLogD("******************************************************************************************************************");
        myLogD("******************************************************************************************************************");
        myLogD("***************************      All files have been processed. -- OK      ***************************************");
        myLogD("******************************************************************************************************************");
        myLogD("******************************************************************************************************************");
        updateFolderTable(context, insertedFolderId);

        myLogD("deleting source ??"
                + "\nOption CopyFile : " + bookState.optionCopy + "  -  is a ZIP : " + bookState.dynamicType.equals("ZIP")
                + "\nOption DeleteSourceFile : " + bookState.optionDelete);
        if ((bookState.optionCopy || "ZIP".equals(bookState.dynamicType)) && bookState.optionDelete) {
            deleteSourceFile();
        }
        TaskStateManager.tellEnd();
    }


    private SaveResultEnum saveSingleFile(AudioFileInfo info, int folderId, int zeOrder) {
        ZikFile file = new ZikFile();
        file.setName(info.getDisplayPath());
        file.setDisplayName(formatNameForDisplay(info.getDisplayPath()));
        file.setIdFolder(folderId);
        file.setZeorder(zeOrder);
        file.setFolderName(bookState.title);
        file.setPercentdone(0.0);
        file.setPosition(0);
        file.setPath(info.getContentUri());
        file.setIszipfile(false);
        file.setFinished(false);
        file.setDuration(info.getDuration());
        file.date_added = System.currentTimeMillis();

        if (file.getDuration() == 0) {
            myLogW("⏭️ Skipped: duration = 0 → " + info.getDisplayPath());
            return SaveResultEnum.SKIPPED;
        }

        long id = AppDatabase.getDatabase(context).ZikFileDao().insert(file);
        if (id > 0) {
            //myLog("✔️ ZikFile inserted: id = " + id);
            return SaveResultEnum.SUCCESS;
        } else {
            myLogE("❌ DB insert failed for: " + info.getDisplayPath());
            TaskStateManager.markTaskFailed(TASK_NAME, context.getString(R.string.Error_Import_CannotSaveInDB) + " [" + info.getDisplayPath() + "]");
            return SaveResultEnum.FAILED;
        }
    }

    private void deleteSourceFile() {
        myLog("deleteSourceFile() - uri = [" + bookState.dynamicUri + "] [" + bookState.dynamicType + "]");
        DocumentFile dfPickedDir = null;
        if (bookState.dynamicType.equals("File") || bookState.dynamicType.equals("ZIP")) {
            try {
                dfPickedDir = DocumentFile.fromSingleUri(context, bookState.dynamicUri);
            } catch (Exception e) {
                myLogEE(e,"deleting - error getting DocumentFile.fromSingleUri");
                TaskStateManager.markTaskFailed(TASK_NAME, context.getString(R.string.Error_Import_CannotDeleteSource));
            }
        } else if (bookState.dynamicType.equals("Folder")) {
            try {
                dfPickedDir = DocumentFile.fromTreeUri(context, bookState.dynamicUri);
            } catch (Exception e) {
                myLogEE(e,"deleting - error getting DocumentFile.fromTreeUri");
                TaskStateManager.markTaskFailed(TASK_NAME, context.getString(R.string.Error_Import_CannotDeleteSource));
            }
        } else {
            myLogEE(null,"Incorrect type : **" + bookState.dynamicType + "**");
        }
        if (!(dfPickedDir == null)) {
            boolean okDelete = dfPickedDir.delete();
            if (okDelete) {
                myLogD("source file deletion ok");
            } else {
                myLogEE(null,"Error during source file deletion");
            }
        } else {
            myLogEE(null,"deleteSourceFile() => could not get ref to picked file");
        }
    }

    private long getMediaDurationFromUri(Context context, Uri uri, String audioName) {
        long duration = 0;
        try {
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            retriever.setDataSource(context, uri);
            String durStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            duration = Long.parseLong(durStr);
        } catch (Exception e) {
            TaskStateManager.tellWarning(context.getString(R.string.Error_Import_track_duration_extraction) + " for " + audioName);
            myLogEE(e,"error getting duration of media for uri: [" + uri + "]");
        }
        return duration;
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
                }
            }
        }
    }
}
