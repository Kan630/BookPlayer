package com.driot.bookplayer.services;

import static com.driot.bookplayer.db.Sql.updateFolderTable;
import static com.driot.bookplayer.global.Var.ONLY_MIME_AUDIO;
import static com.driot.bookplayer.global.Var.SUPPORTED_AUDIO_EXTENSIONS;
import static com.driot.bookplayer.global.Var.SUPPORTED_COVER_PICTURE_EXTENSIONS;
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
import com.driot.bookplayer.global.Pref;
import com.driot.bookplayer.objects.AudioFileInfo;
import com.driot.bookplayer.objects.TaskStateManager;
import com.driot.bookplayer.objects.LoadBookTaskState;
import com.driot.bookplayer.utils.FileUtils;
import com.driot.bookplayer.utils.Tonio;
import com.driot.bookplayer.utils.Utils;
import com.driot.bookplayer.utils.log.LoggingWorker;

import java.util.ArrayList;
import java.util.Objects;

public class ParseFinalFolderWorker extends LoggingWorker {
    private static final String TASK_NAME = "final step";

    Context context = getApplicationContext();
    
    public static final int[] PROGRESS_DOWNLOAD = {2, 10, 50, 55, 65, 65, 65, 90, 99};
    public static final int[] PROGRESS_ZIP_COPY = {5, 5, 5, 10, 20, 25, 50, 75, 90};
    public static final int[] PROGRESS_ZIP_NOCOPY = {5, 5, 5, 10, 20, 25, 50, 75, 90};
    public static final int[] PROGRESS_FILE_COPY = {5, 5, 5, 15, 30, 45, 90, 90, 95};
    public static final int[] PROGRESS_FILE_NOCOPY = {5, 5, 20, 40, 60, 90, 90, 90, 95};
    public static final int[] PROGRESS_FOLDER_COPY = {2, 2, 2, 15, 30, 45, 90, 90, 95};
    public static final int[] PROGRESS_FOLDER_NOCOPY = {5, 5, 20, 30, 40, 40, 40, 50, 95};
    public static final String[] PROGRESS_TEXT = {
            "Initialization"
            , "Download"
            , "listing and sorting Tracks"
            , "Checking audio hasn't already been added"
            , "Check enough space on Disk"
            , "Copy"
            , "Unzip"
            , "Duration"
            , "End"
    };
    public static int[] PROGRESS = PROGRESS_DOWNLOAD;

    private enum SaveResultEnum {SUCCESS, SKIPPED, FAILED}

    private ArrayList<AudioFileInfo> audioFileArrayList;

    private long fullFolderSize; //to make storage space checks

    private int nbFileScan; // global because recursive method

    LoadBookTaskState bookState;


    public ParseFinalFolderWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        DocumentFile df;
        Context context = getApplicationContext();
        bookState = Pref.getLoadBookTaskState();
        if (bookState == null) {
            TaskStateManager.markTaskFailed(TASK_NAME, "bookState == null");
            return Result.failure();
        }

        boolean isFolderComputed = Tonio.isFolder(context, bookState.dynamicUri);
        myLogD("original Type : " + bookState.originalType);
        myLogD("dynamic Type : " + bookState.dynamicType);
        myLogD("isFolderComputed : " + isFolderComputed);

        if (isFolderComputed) {
            try {
                df = DocumentFile.fromTreeUri(context, bookState.dynamicUri);
            } catch (Exception e) {
                myLogEE(e,"Error reading picked Folder.... DocumentFile.fromTreeUri");
                TaskStateManager.markTaskFailed(TASK_NAME, context.getString(R.string.Error_Import_CannotReadFolder));
                return Result.failure();
            }
            populateArrayListOfTracksFromFolder(df);
        } else {
            try {
                df = DocumentFile.fromSingleUri(context, bookState.dynamicUri);
            } catch (Exception e) {
                myLogEE(e,"Error reading picked Folder.... DocumentFile.fromTreeUri");
                TaskStateManager.markTaskFailed(TASK_NAME, context.getString(R.string.Error_Import_CannotReadFolder));
                return Result.failure();
            }
            populateArrayListOfTracksFromFile(df);
        }
        return Result.success();
    }

    // single file
    ///////////////////////////
    private void populateArrayListOfTracksFromFile(DocumentFile dfPickedDir) {
        myLog("populateArrayListOfTracksFromFile [" + dfPickedDir.getUri() + "] - single file");

        //mCallBacks.tellHeader(myFolder.getFolderName());

        if (dfPickedDir != null && !(dfPickedDir.isDirectory())) {
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
        TaskStateManager.tellProgress(PROGRESS[2], PROGRESS_TEXT[2]);


        //mCallBacks.tellHeader(myFolder.getFolderName());

        myLog("running recursive scan for audio file in a background thread");

        audioFileArrayList = new ArrayList<>();

        DocumentFile finalDfPickedDir = dfPickedDir; //thread needs 'final' arg
        Thread backgroundThread = new Thread(() -> {
            addAudioFileRecursive(finalDfPickedDir);

            myLogD("addAudioFileRecursive done, sorting now...");
            audioFileArrayList.sort(new Utils.AlphanumericComparator());

            if (audioFileArrayList.isEmpty()) {
                myLog("No File found in directory : [" + finalDfPickedDir.getName() + ']');
            } else {
                myLog(audioFileArrayList.size() + " files found in directory : [" + finalDfPickedDir.getName() + ']');
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
        long duration = getMediaDurationFromUri(context, df.getUri());
        myLogD("* Duration : [" +  formatTime(duration) + ']');
        audioFileArrayList.add(new AudioFileInfo(df.getName(), duration));
    }
    private void addAudioFileRecursive(DocumentFile f0) {
        TaskStateManager.tellProgress(PROGRESS[2], PROGRESS_TEXT[2]);
        nbFileScan = 0;
        fullFolderSize = 0;
        addAudioFileRecursive(f0,"");
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
                if (mimeType.startsWith(ONLY_MIME_AUDIO) || SUPPORTED_AUDIO_EXTENSIONS.contains(fileExtension)) {
                    nbFileScan = nbFileScan + 1;
                    l_audioFilePath = recursivFolder + f1.getName();
                    l_audioSize = f1.length();
                    myLogD("* New Audio File : [" + l_audioFilePath + "] - size = [" + l_audioSize + "]");
                    long duration = getMediaDurationFromUri(context, f1.getUri());
                    myLogD("* Duration : [" +  formatTime(duration) + ']');
                    double progress = (double) nbFileScan%10/10;
                    TaskStateManager.tellProgress((int) (PROGRESS[2] + (PROGRESS[3] - PROGRESS[2]) * progress), "Scanning for Audio Files..... \n[" +  l_audioFilePath + ']');
                    audioFileArrayList.add(new AudioFileInfo(l_audioFilePath, duration));
                    fullFolderSize = fullFolderSize + l_audioSize;
                } else if (!hadImageBefore && SUPPORTED_COVER_PICTURE_EXTENSIONS.contains(fileExtension)) {
                    long imageSize = f1.length();
                    if (bookState.imagePath == null || imageSize > FileUtils.getFileSize(context, Uri.parse(bookState.imagePath))) {
                        myLogD("New biggest Picture Found, size = [" + Tonio.formatMemPadding(imageSize) + "] - [" + f1.getUri() + "]");
                        bookState.imagePath = f1.getUri().toString();
                        hadImageBefore = true;
                    }
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
                saveFolder();
            }
        } else {
            TaskStateManager.markTaskFailed(TASK_NAME, context.getString(R.string.Error_Import_NoMediaInFolder));
        }
    }

    private void saveFolder() {
        TaskStateManager.tellProgress(PROGRESS[7], PROGRESS_TEXT[7]);

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
            TaskStateManager.tellProgress(PROGRESS[7], PROGRESS_TEXT[7]);
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

            int progress = PROGRESS[7] + ((i + 1) * 100 / total) * (PROGRESS[8] - PROGRESS[7]) / 100;
            String txtProgress = progress + "% - " + context.getString(R.string.Add_resource_reading_file) +
                    " n°" + i + 1 + "/" + total + "\n" + getFileNameFromPath(info.getFileName());

            myLog("Registering track [" + info.getFileName() + "]");
            SaveResultEnum result = saveSingleFile(info, insertedFolderId, zeOrder);
            TaskStateManager.tellProgress(progress, txtProgress);

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
        file.setName(info.getFileName());
        file.setDisplayName(formatNameForDisplay(info.getFileName()));
        file.setIdFolder(folderId);
        file.setZeorder(zeOrder);
        file.setFolderName(bookState.title);
        file.setPercentdone(0.0);
        file.setPosition(0);
        file.setPath(bookState.futureFolderPath);
        file.setIszipfile(false);
        file.setFinished(false);
        file.setDuration(info.getDuration());
        file.date_added = System.currentTimeMillis();

        if (file.getDuration() == 0) {
            myLog("⏭️ Skipped: duration = 0 → " + info.getFileName());
            return SaveResultEnum.SKIPPED;
        }

        long id = AppDatabase.getDatabase(context).ZikFileDao().insert(file);
        if (id > 0) {
            myLog("✔️ ZikFile inserted: id = " + id);
            return SaveResultEnum.SUCCESS;
        } else {
            myLogE("❌ DB insert failed for: " + info.getFileName());
            TaskStateManager.markTaskFailed(TASK_NAME, context.getString(R.string.Error_Import_CannotSaveInDB) + " [" + info.getFileName() + "]");
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

    private long getMediaDurationFromUri(Context context, Uri uri) {
        long duration = 0;
        try {
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            retriever.setDataSource(context, uri);
            String durStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            duration = Long.parseLong(durStr);
        } catch (Exception e) {
            TaskStateManager.markTaskFailed(TASK_NAME, context.getString(R.string.Error_Import_track_duration_extraction) + " // uri: " + uri);
            myLogEE(e,"error getting duration of media for uri: [" + uri + "]");
        }
        //myLogD("duration for [" + uri + "] is " + duration);
        return duration;
    }


}
