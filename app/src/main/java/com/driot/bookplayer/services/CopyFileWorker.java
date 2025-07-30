package com.driot.bookplayer.services;

import static com.driot.bookplayer.global.Var.ONLY_MIME_AUDIO;
import static com.driot.bookplayer.global.Var.SUPPORTED_AUDIO_EXTENSIONS;
import static com.driot.bookplayer.global.Var.SUPPORTED_COVER_PICTURE_EXTENSIONS;
import static com.driot.bookplayer.global.Var.ZIP_SIZE_MAX_COEF;
import static com.driot.bookplayer.utils.StorageHelper.getAvailableInternalMemorySize;
import static com.driot.bookplayer.utils.Tonio.formatMemPadding;
import static com.driot.bookplayer.utils.Tonio.getExtension;
import static com.driot.bookplayer.utils.Tonio.getSourceLocation;

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
import com.driot.bookplayer.objects.LoadBookTaskState;
import com.driot.bookplayer.objects.TaskStateManager;
import com.driot.bookplayer.utils.Tonio;
import com.driot.bookplayer.utils.log.LoggingWorker;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
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

    long last_logged_progress = -1;
    private String sourceLocation = "unknown";
    private long totalSize = -1;
    long availableMegs = -1;
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
        String destinationFileName = state.futureFolderName;
        String type = state.dynamicType;
        boolean checkSize = true;  //TODO
        long forceSize = -1;
        sourceLocation = getSourceLocation(context, uri);

        if (!type.equalsIgnoreCase("folder")) {
            destinationFileName = state.futureFolderName + "." + state.fileExtension;
        } else {
            destinationFileName = null;
        }

        availableMegs = getAvailableInternalMemorySize() / 1048576L;
        
        
        myLog("parseIntent() ..   " +
                "\n.    from uri = [" + uri.toString() + "] " +
                "\n.    to folder = [" + destinationFolderPath + "] " +
                "\n.    with name = [" + destinationFileName + "]" +
                "\n.    for type = [" + type + "]" +
                "\n.    check size = [" + checkSize + "]" +
                "\n.    force size = [" + forceSize + "]" +
                "\n.    source Location = [" + sourceLocation + "]"
        );

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
                result = copyFolder(uri, destinationFolderPath, forceSize, sourceLocation);
            } else {
                result = copyFile(uri, destinationFolderPath, destinationFileName, type, checkSize, forceSize, sourceLocation);
            }
            myLog("nbFileCopied = " + nbFileCopied + " .  nbFileKO = " + nbFileKO + " .  nbFolder = " + nbFolder + " .  nbPic = " + nbPic);
            if (nbFileCopied==0) result=false;
            if (hasBeenCancelled) {
                TaskStateManager.markTaskCancelled(TASK_NAME);
                return Result.failure();
            }
            if (result) {
                TaskStateManager.markTaskCompleted(TASK_NAME, destinationFolderPath);
                return Result.success();
            } else {
                return Result.failure();
            }
        } catch (Exception e) {
            TaskStateManager.markTaskFailed(TASK_NAME, e.getMessage());
            return Result.failure();
        }
    }

    private boolean copyFile(Uri uri, String destinationFolderPath, String destinationFileName, String type, boolean checkSize, long forceSize, String sourceLocation) {
        int nbBuffCopied = 0;
        totalSize = checkSize ? getFileSize(context, uri) : -1;
        long size_check_inflate_coefficient = "ZIP".equals(type) ? ZIP_SIZE_MAX_COEF : 1;

        if (checkSize && totalSize > 0 && totalSize * size_check_inflate_coefficient > availableMegs * 1048576L) {
            TaskStateManager.markTaskFailed(TASK_NAME, "Not enough memory");
            return false;
        }

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




    private boolean copyFolder(Uri uri, String destinationFolderPath, long forceSize, String sourceLocation) {
        try {
            copyFolderRecursive(context, uri, new File(destinationFolderPath),
                    null, forceSize, ONLY_MIME_AUDIO, SUPPORTED_AUDIO_EXTENSIONS, SUPPORTED_COVER_PICTURE_EXTENSIONS);
        } catch (Exception e) {
            TaskStateManager.markTaskFailed(TASK_NAME, e.getMessage());
            return false;
        }
        return true;
    }
    
    private void copyFolderRecursive(Context context, Uri sourceUri, File destinationFolder
            , long[] copiedSize, long forceSize
            , String onlyMime, Set<String> onlyAudioExtensions, Set<String> onlyImageExtensions
            ) throws IOException {
        if (!destinationFolder.exists()) {
            if (!destinationFolder.mkdirs()) {
                TaskStateManager.markTaskFailed(TASK_NAME, "Error creating destination folder in recursive folder copy for " + destinationFolder.getAbsolutePath());
                return;
            } else {
                myLogD("Folder created: " + destinationFolder.getAbsolutePath());
            }
        }
        if (copiedSize == null) copiedSize = new long[]{0};

        ContentResolver contentResolver = context.getContentResolver();
        Uri childrenUri;
        try {
            childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(sourceUri, DocumentsContract.getDocumentId(sourceUri));
            myLogD("children");
        } catch (Exception e) {
            childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(sourceUri, DocumentsContract.getTreeDocumentId(sourceUri));
            myLogD("parent");
        }

        if (forceSize > 0) {
            totalSize = forceSize;
        } else {
            totalSize = calculateFolderSize(context, sourceUri);
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
                        copyFolderRecursive(context, documentUri, subDir, copiedSize, forceSize, onlyMime, onlyAudioExtensions, onlyImageExtensions);  // Corrected parameters
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

    private long calculateFolderSize(Context context, Uri uri) throws IOException {
        myLog("calculateFolderSize()");
        long totalSize = 0;
        ContentResolver contentResolver = context.getContentResolver();
        Uri childrenUri;
        try {
            //for children and sub child dirs
            childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(uri, DocumentsContract.getDocumentId(uri));
        } catch (Exception e) {
            //for parent dir
            childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri));
        }

        try (Cursor cursor = contentResolver.query(childrenUri, new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_MIME_TYPE}, null, null, null)) {
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    String documentId = cursor.getString(0);
                    String mimeType = cursor.getString(1);

                    Uri documentUri = DocumentsContract.buildDocumentUriUsingTree(uri, documentId);

                    if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mimeType)) {
                        totalSize += calculateFolderSize(context, documentUri);
                    } else {
                        totalSize += getFileSize(context, documentUri);
                    }
                }
            }
        }
        return totalSize;
    }

    public long getFileSize(Context context, Uri uri) {
        if ("file".equalsIgnoreCase(uri.getScheme())) {
            try {
                if (uri.getPath() != null) {
                    return new File(uri.getPath()).length();
                }
            } catch (Exception e) {
                myLogEE(e, "getFileSize() - file://");
                return -1;
            }
        } else {
            try (ParcelFileDescriptor pfd = context.getContentResolver().openFileDescriptor(uri, "r")) {
                return (pfd != null) ? pfd.getStatSize() : -1;
            } catch (Exception e) {
                myLogEE(e, "getFileSize() - content://");
                return -1;
            }
        }
        return -1;
    }

    private void buildProgressString() {
        long progress = (int) ((copiedSize * 100) / totalSize);
        if (progress != last_logged_progress) {
            last_logged_progress = progress;

            String progressMsg = sourceLocation.equals("cloud")
                    ? context.getString(R.string.Import_Progress_copying_zip_file_cloud)
                    : context.getString(R.string.Import_Progress_copying_zip_file);

            String msg = progressMsg + "\n\n" +
                    context.getString(R.string.Error_Import_NotEnoughMemory_line3) + formatMemPadding(copiedSize/1024/1024, 0) + "Mo/" + formatMemPadding(totalSize/1024/1024, 0) + "Mo\n" +
                    context.getString(R.string.Error_Import_NotEnoughMemory_line2_1) + Tonio.formatMemPadding(getAvailableInternalMemorySize() / 1048576L) + "Mo";

            TaskStateManager.tellProgress((int) progress, msg);
        }
    }

}
