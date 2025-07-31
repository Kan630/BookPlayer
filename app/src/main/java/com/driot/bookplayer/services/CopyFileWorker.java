package com.driot.bookplayer.services;

import static com.driot.bookplayer.global.Var.ONLY_MIME_AUDIO;
import static com.driot.bookplayer.global.Var.SUPPORTED_AUDIO_EXTENSIONS;
import static com.driot.bookplayer.global.Var.SUPPORTED_COVER_PICTURE_EXTENSIONS;
import static com.driot.bookplayer.global.Var.ZIP_SIZE_MAX_COEF;
import static com.driot.bookplayer.helpers.StorageHelper.getAvailableInternalMemorySize;
import static com.driot.bookplayer.utils.Tonio.formatMemPadding;
import static com.driot.bookplayer.utils.Tonio.getExtension;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;

import androidx.annotation.NonNull;
import androidx.work.WorkerParameters;

import com.driot.bookplayer.R;
import com.driot.bookplayer.global.Pref;
import com.driot.bookplayer.helpers.UriHelper;
import com.driot.bookplayer.objects.LoadBookTaskState;
import com.driot.bookplayer.helpers.StorageHelper;
import com.driot.bookplayer.objects.TaskStateManager;
import com.driot.bookplayer.utils.Tonio;
import com.driot.bookplayer.utils.log.LoggingWorker;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;

public class CopyFileWorker extends LoggingWorker {
    private static final String TASK_NAME = "copy file";

    private static final int MAX_NB_PIC = 5;

    private final Context context;

    private boolean hasBeenCancelled = false;
    private int nbFileCopied = 0;
    private int nbFileKO = 0;
    private int nbPic = 0;
    private int nbFolder = 0;

    private StorageHelper.MemoryLocationType destinationLocation;
    long last_logged_progress = -1;
    private String sourceLocation = "unknown";
    private long totalSize = -1;
    long availableMemory = -1;
    long copiedSize = -1;

    public CopyFileWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
        this.context = context;
    }

    @NonNull
    @Override
    public Result doWork() {
        LoadBookTaskState state = Pref.getLoadBookTaskState();

        Uri uri = state.dynamicUri;
        String destinationFolderPath = state.futureFolderPath;
        String destinationFileName = state.originalFile;
        String type = state.dynamicType;
        boolean checkSize = true;  //TODO
        long forceSize = -1;
        sourceLocation = Tonio.getSourceLocation(context, uri);
        availableMemory = getAvailableInternalMemorySize();
        destinationLocation = StorageHelper.getMemoryLocationType(context, destinationFolderPath);

        if (forceSize > 0) {
            totalSize = forceSize;
        } else {
            TaskStateManager.tellProgressText("checking size");
            totalSize = UriHelper.getSize(context, uri);
        }
        
        myLog("parseIntent() ..   " +
                "\n.    from uri = [" + uri.toString() + "] " +
                "\n.    to folder = [" + destinationFolderPath + "] " +
                "\n.    with name = [" + destinationFileName + "]" +
                "\n.    for type = [" + type + "]" +
                "\n.    check size = [" + checkSize + "]" +
                "\n.    force size = [" + forceSize + "]" +
                "\n.    total size = [" + Tonio.getReadableSize(totalSize)  + "]" +
                "\n.    available = [" + Tonio.getReadableSize(availableMemory)  + "]" +
                "\n.    source Location = [" + sourceLocation + "]" +
                "\n.    destination Location = [" + destinationLocation.toString() + "]"
        );

        if (checkSize && !doCheckSize(uri, forceSize, type)) {
            TaskStateManager.markTaskFailed(TASK_NAME, context.getString(R.string.Not_enough_memory));
            return Result.failure();
        }

        // Create destination folder if needed
        File destinationFolderFile = new File(destinationFolderPath);
        try {
            if (!destinationFolderFile.exists() && !destinationFolderFile.mkdirs()) {
                TaskStateManager.markTaskFailed(TASK_NAME, context.getString(R.string.Error_Import_Creating_Folders) + " : " + destinationFolderPath);
                return Result.failure();
            }
        } catch (Exception e) {
            myLogEE(e, "CopyFileWorker - Error creating destination folder");
            TaskStateManager.markTaskFailed(TASK_NAME, context.getString(R.string.Error_Import_Creating_Folders));
            return Result.failure();
        }

        //run the actual stuff
        try {
            boolean result;
            if ("Folder".equals(type)) {
                result = copyFolder(uri, destinationFolderPath);
            } else {
                result = copyFile(uri, destinationFolderPath, destinationFileName);
            }
            myLog("nbFileCopied = " + nbFileCopied + " .  nbFileKO = " + nbFileKO + " .  nbFolder = " + nbFolder + " .  nbPic = " + nbPic);
            if (nbFileCopied==0) result=false;
            if (hasBeenCancelled) {
                TaskStateManager.markTaskCancelled(TASK_NAME);
                return Result.failure();
            }
            if (result) {
                if ("Folder".equals(type)) {
                    TaskStateManager.markTaskCompleted(TASK_NAME, destinationFolderPath);
                } else {
                    TaskStateManager.markTaskCompleted(TASK_NAME, destinationFolderPath + "/" + destinationFileName);
                }
                return Result.success();
            } else {
                return Result.failure();
            }
        } catch (Exception e) {
            TaskStateManager.markTaskFailed(TASK_NAME, e.getMessage());
            return Result.failure();
        }
    }

    private boolean copyFile(Uri uri, String destinationFolderPath, String destinationFileName) {
        int nbBuffCopied = 0;

        try (InputStream is = context.getContentResolver().openInputStream(uri);
             OutputStream out = new FileOutputStream(new File(destinationFolderPath + "/" + destinationFileName))) {

            byte[] buf = new byte[1024];
            int len;

            while ((len = is.read(buf)) > 0) {
                if (isStopped()) {
                    TaskStateManager.markTaskCancelled(TASK_NAME);
                    return false;
                }
                nbBuffCopied++;
                out.write(buf, 0, len);
                copiedSize += len;
                if (nbBuffCopied % 1024 == 0) {
                    buildProgressString();
                }
            }
        } catch (Exception e) {
            TaskStateManager.markTaskFailed(TASK_NAME, e.getMessage());
            return false;
        }
        nbFileCopied++;
        return true;
    }




    private boolean copyFolder(Uri uri, String destinationFolderPath) {
        try {
            copyFolderRecursive(context, uri, new File(destinationFolderPath), ONLY_MIME_AUDIO, SUPPORTED_AUDIO_EXTENSIONS, SUPPORTED_COVER_PICTURE_EXTENSIONS);
        } catch (Exception e) {
            TaskStateManager.markTaskFailed(TASK_NAME, e.getMessage());
            return false;
        }
        return true;
    }
    
    private void copyFolderRecursive(Context context, Uri sourceUri, File destinationFolder
            , String onlyMime, Set<String> onlyAudioExtensions, Set<String> onlyImageExtensions
            ) {
        if (!destinationFolder.exists()) {
            if (!destinationFolder.mkdirs()) {
                TaskStateManager.markTaskFailed(TASK_NAME, "Error creating destination folder in recursive folder copy for " + destinationFolder.getAbsolutePath());
                return;
            } else {
                myLogD("Folder created: " + destinationFolder.getAbsolutePath());
            }
        }
        ContentResolver contentResolver = context.getContentResolver();
        Uri childrenUri;
        try {
            childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(sourceUri, DocumentsContract.getDocumentId(sourceUri));
            myLogD("children");
        } catch (Exception e) {
            childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(sourceUri, DocumentsContract.getTreeDocumentId(sourceUri));
            myLogD("parent");
        }

        try (Cursor cursor = contentResolver.query(childrenUri, new String[]{
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE}, null, null, null)) {

            if (cursor != null) {
                while (cursor.moveToNext()) {
                    if (isStopped()) {
                        TaskStateManager.markTaskCancelled(TASK_NAME);
                        hasBeenCancelled = true;
                        return;
                    }
                    String documentId = cursor.getString(0);
                    String displayName = cursor.getString(1);
                    String mimeType = cursor.getString(2);
                    String fileExtension = getExtension(displayName);

                    Uri documentUri = DocumentsContract.buildDocumentUriUsingTree(sourceUri, documentId);

                    if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mimeType)) {
                        if (isStopped()) {
                            TaskStateManager.markTaskCancelled(TASK_NAME);
                            hasBeenCancelled = true;
                            return;
                        }
                        File subDir = new File(destinationFolder, displayName);
                        myLogD("copyFolder - on folder -> recursive call");
                        copyFolderRecursive(context, documentUri, subDir, onlyMime, onlyAudioExtensions, onlyImageExtensions);  // Corrected parameters
                    } else {
                        myLogD("copyFolder - on file -> may call copyFile " + displayName + "  ///  " + mimeType);
                        boolean doCopy = false;
                        boolean isPic = false;
                        if (onlyMime != null && !onlyMime.isEmpty()) {
                            if (mimeType.startsWith(onlyMime)) {
                                doCopy = true;
                            }
                        }
                        if (!onlyAudioExtensions.isEmpty() && onlyAudioExtensions.contains(fileExtension)) {
                            doCopy = true;
                        }
                        if (!onlyImageExtensions.isEmpty() && onlyImageExtensions.contains(fileExtension) && nbPic < MAX_NB_PIC) {
                            doCopy = true;
                            isPic = true;
                        }
                        if (doCopy) {
                            if (isStopped()) {
                                TaskStateManager.markTaskCancelled(TASK_NAME);
                                hasBeenCancelled = true;
                                return;
                            }
                            if (copyFile2(context, documentUri, new File(destinationFolder, displayName))) {
                                if (isPic) {
                                    nbPic++;
                                } else {
                                    nbFileCopied++;
                                }
                            } else {
                                if (!isPic) nbFileKO++;
                            }
                        }
                    }
                }
            }
        }
        nbFolder++;
    }

    //used locally by copyFolder
    private boolean copyFile2(Context context, Uri sourceUri, File destinationFile) {
        myLogD("copyFile() in " + destinationFile.getParentFile().getAbsolutePath());
        try (ParcelFileDescriptor pfd = context.getContentResolver().openFileDescriptor(sourceUri, "r");
             FileInputStream inputStream = new FileInputStream(pfd.getFileDescriptor());
             FileOutputStream outputStream = new FileOutputStream(destinationFile)) {

            int nbBuffCopied = 0;
            byte[] buffer = new byte[1024];
            int length;
            while ((length = inputStream.read(buffer)) > 0) {
                if (isStopped()) {
                    TaskStateManager.markTaskCancelled(TASK_NAME);
                    hasBeenCancelled = true;
                    return false;
                }
                outputStream.write(buffer, 0, length);
                copiedSize += length;
                if (nbBuffCopied % 1024 == 0) buildProgressString();
                nbBuffCopied += 1;
            }
            nbFileCopied++;
            return true;
        } catch (Exception e) {
            myLogEE(e, "copyFile2 (recursive)");
            return false;
        }
    }

    private void buildProgressString() {
        long progress = (int) ((copiedSize * 100) / totalSize);
        if (progress != last_logged_progress) {
            last_logged_progress = progress;

            String progressMsg = sourceLocation.equals("cloud")
                    ? context.getString(R.string.Import_Progress_copying_file_from_cloud)
                    : context.getString(R.string.Import_Progress_copying_file_from_general_storage);


            progressMsg = progressMsg + (destinationLocation.equals(StorageHelper.MemoryLocationType.SDCARD_RESERVED)
                    ? context.getString(R.string.Import_Progress_copying_file_to_sd_card_reserved)
                    : context.getString(R.string.Import_Progress_copying_file_to_internal_reserved));

            String msg = progressMsg + "\n\n" +
                    context.getString(R.string.Error_Import_NotEnoughMemory_line3) + formatMemPadding(copiedSize/1024/1024, 0) + "Mo/" + formatMemPadding(totalSize/1024/1024, 0) + "Mo\n" +
                    context.getString(R.string.Error_Import_NotEnoughMemory_line2_1) + Tonio.formatMemPadding(availableMemory / 1048576L) + "Mo";

            TaskStateManager.tellProgress((int) progress, msg);
        }
    }

    private boolean doCheckSize(Uri uri, long forceSize, String type) {
        try {
            totalSize = forceSize < 0 ? UriHelper.getSize(context, uri) : forceSize;
            long size_check_inflate_coefficient = "ZIP".equals(type) ? ZIP_SIZE_MAX_COEF : 1;

            if (totalSize > 0 && totalSize * size_check_inflate_coefficient > availableMemory) {
                return false;
            }
        } catch (Exception e) {
            TaskStateManager.tellWarning("error checking size : " + e.getMessage());
        }
        return true;
    }

}
