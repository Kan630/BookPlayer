package com.driot.bookplayer.services;

import static com.driot.bookplayer.utils.Tonio.formatMemPadding;

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
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.helpers.SupportedFilesHelper;
import com.driot.bookplayer.helpers.UriHelper;
import com.driot.bookplayer.helpers.StorageHelper;
import com.driot.bookplayer.objects.LoadBookTaskState;
import com.driot.bookplayer.objects.TaskStateManager;
import com.driot.bookplayer.utils.Tonio;
import com.driot.bookplayer.utils.log.LoggingWorker;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class CopyFileWorker extends LoggingWorker {
    private static final String TASK_NAME = Var.WORKER_TASK_LABEL_COPY;

    private static final int MAX_NB_PIC = 5;

    private final Context context;

    private boolean hasBeenCancelled = false;
    private int nbFileCopied = 0;
    private int nbFileKO = 0;
    private int nbPic = 0;
    private int nbFolder = 0;

    private StorageHelper.MemoryLocationType destinationLocation;
    long last_logged_progress = -1;
    String last_logged_msg = "";
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

        if (state == null) {
            TaskStateManager.markTaskFailed(TASK_NAME, "bookState == null", context.getString(R.string.invalid_resource));
            return Result.failure();
        }

        Uri uri = state.dynamicUri;
        String destinationFolderPath = state.futureFolderPath;
        String destinationFileName = state.originalFile;
        String type = state.dynamicType;
        String fileExtension = state.fileExtension;
        boolean checkSize = true;  //TODO, to check
        long forceSize = -1;
        sourceLocation = Tonio.getSourceLocation(context, uri);
        destinationLocation = StorageHelper.getMemoryLocationType(context, destinationFolderPath);
        if (destinationLocation.equals(StorageHelper.MemoryLocationType.SDCARD_RESERVED) || destinationLocation.equals(StorageHelper.MemoryLocationType.SDCARD_SHARED)) {
            availableMemory = StorageHelper.getAvailableRemovableSDCardSize(context);
        } else {
            availableMemory = StorageHelper.getAvailableInternalMemorySize();
        }

        if (forceSize > 0) {
            totalSize = forceSize;
        } else {
            TaskStateManager.tellProgressText(context.getString(R.string.checking_size));
            totalSize = UriHelper.getSize(context, uri);
        }

        myLogD("----------------------------------------------------");
        myLog("parseIntent() ..   " +
                "\n.    from uri = [" + uri.toString() + "] " +
                "\n.    to folder = [" + destinationFolderPath + "] " +
                "\n.    with name = [" + destinationFileName + "]" +
                "\n.    for type = [" + type + "]" +
                "\n.    extension = [" + fileExtension + "]" +
                "\n.    check size = [" + checkSize + "]" +
                "\n.    force size = [" + forceSize + "]" +
                "\n.    total size = [" + Tonio.getReadableSize(totalSize) + "]" +
                "\n.    available = [" + Tonio.getReadableSize(availableMemory) + "]" +
                "\n.    source Location = [" + sourceLocation + "]" +
                "\n.    destination Location = [" + destinationLocation.toString() + "]"
        );
        myLogD("----------------------------------------------------");

        if (!isSizeOk(fileExtension)) {
            TaskStateManager.tellWarning(context.getString(R.string.Not_enough_memory)
                    + "\n" + Tonio.formatSizeMB(availableMemory) + " " + context.getString(R.string.MB_available_on_device));
            TaskStateManager.markTaskFailed(TASK_NAME, "Not_enough_memory", context.getString(R.string.Not_enough_memory));
            return Result.failure();
        }

        // Create destination folder if needed
        File destinationFolderFile = new File(destinationFolderPath);
        try {
            if (!destinationFolderFile.exists() && !destinationFolderFile.mkdirs()) {
                TaskStateManager.markTaskFailed(TASK_NAME
                        , "Error_Import_Creating_Folders : " + destinationFolderPath
                        , context.getString(R.string.Error_Import_Creating_Folders));
                return Result.failure();
            }
        } catch (Exception e) {
            myLogEE(e, "CopyFileWorker - Error creating destination folder");
            TaskStateManager.markTaskFailed(TASK_NAME
                    , "Catch Error_Import_Creating_Folders : " + destinationFolderPath
                    , context.getString(R.string.Error_Import_Creating_Folders));
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
            myLogI("nbFileCopied = " + nbFileCopied + " .  nbFileKO = " + nbFileKO + " .  nbFolder = " + nbFolder + " .  nbPic = " + nbPic);
            if (nbFileCopied == 0) result = false;
            if (hasBeenCancelled) {
                TaskStateManager.markTaskCancelled(TASK_NAME);
                return Result.failure();
            }
            if (result) {
                if ("Folder".equals(type)) {
                    TaskStateManager.markCopyCompleted(destinationFolderPath);
                } else {
                    TaskStateManager.markCopyCompleted(destinationFolderPath + "/" + destinationFileName);
                }
                return Result.success();
            } else {
                return Result.failure();
            }
        } catch (Exception e) {
            myLogEE(e, "CopyFileWorker - Error during copy");
            TaskStateManager.markTaskFailed(TASK_NAME, e.getMessage(), null);
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
            myLogEE(e, "CopyFileWorker - Error during copy");
            TaskStateManager.markTaskFailed(TASK_NAME, e.getMessage(), null);
            return false;
        }
        nbFileCopied++;
        return true;
    }

    private boolean copyFolder(Uri uri, String destinationFolderPath) {
        try {
            // Uniform wrapper for content:// (tree/single) and file:// paths
            androidx.documentfile.provider.DocumentFile root =
                    com.driot.bookplayer.helpers.UriHelper.getDocumentFileFromAnyUri(context, uri);

            if (root == null || !root.exists() || !root.isDirectory()) {
                TaskStateManager.markTaskFailed(TASK_NAME, "Invalid URI: [" + uri + "]", context.getString(R.string.invalid_resource) + ": [" + uri + "]");
                return false;
            }

            File dest = new File(destinationFolderPath);
            if (!dest.exists() && !dest.mkdirs()) {
                TaskStateManager.markTaskFailed(TASK_NAME,
                        "failed_to_create_destination_folder - [" + dest.getAbsolutePath() + "]", context.getString(R.string.failed_to_create_destination_folder));
                return false;
            }

            copyFolderRecursiveDoc(root, dest);
            return !hasBeenCancelled && nbFileCopied > 0; // keep your success criteria
        } catch (Exception e) {
            TaskStateManager.markTaskFailed(TASK_NAME, e.getMessage(), null);
            return false;
        }
    }

    // --------- Legacy DocumentsContract-based recursion (args removed; uses SupportedFilesHelper) ---------
    private void copyFolderRecursive(Context context, Uri sourceUri, File destinationFolder) {
        if (!destinationFolder.exists()) {
            if (!destinationFolder.mkdirs()) {
                TaskStateManager.markTaskFailed(TASK_NAME
                        , "failed_to_create_destination_folder (recursive) - [" + destinationFolder.getAbsolutePath() + "]"
                        , context.getString(R.string.failed_to_create_destination_folder) + " [" + destinationFolder.getAbsolutePath() + "]");
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

                    Uri documentUri = DocumentsContract.buildDocumentUriUsingTree(sourceUri, documentId);

                    if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mimeType)) {
                        if (isStopped()) {
                            TaskStateManager.markTaskCancelled(TASK_NAME);
                            hasBeenCancelled = true;
                            return;
                        }
                        File subDir = new File(destinationFolder, displayName);
                        myLogD("copyFolder - on folder -> recursive call");
                        copyFolderRecursive(context, documentUri, subDir);  // args removed
                    } else {
                        myLogD("copyFolder - on file -> may call copyFile " + displayName + "  ///  " + mimeType);

                        // Decide copy via SupportedFilesHelper (Context+Uri)
                        String type = SupportedFilesHelper.getType(context, documentUri);
                        boolean isPic = SupportedFilesHelper.FILE_TYPE_IMAGE.equals(type);
                        boolean doCopy = SupportedFilesHelper.FILE_TYPE_AUDIO.equals(type) || SupportedFilesHelper.FILE_TYPE_VIDEO.equals(type)
                                || (isPic && nbPic < MAX_NB_PIC);

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

    private void copyFolderRecursiveDoc(androidx.documentfile.provider.DocumentFile src,
                                        File destinationFolder) {
        if (hasBeenCancelled) return;

        if (!destinationFolder.exists() && !destinationFolder.mkdirs()) {
            TaskStateManager.markTaskFailed(TASK_NAME
                    , "failed_to_create_destination_folder (recursive) - [" + destinationFolder.getAbsolutePath() + "]"
                    , context.getString(R.string.failed_to_create_destination_folder) + " [" + destinationFolder.getAbsolutePath() + "]");
            return;
        } else {
            myLogD("Folder created: " + destinationFolder.getAbsolutePath());
        }

        androidx.documentfile.provider.DocumentFile[] children = src.listFiles();
        for (androidx.documentfile.provider.DocumentFile child : children) {
            if (isStopped()) {
                TaskStateManager.markTaskCancelled(TASK_NAME);
                hasBeenCancelled = true;
                return;
            }
            if (child.isDirectory()) {
                File subDest = new File(destinationFolder, safeName(child.getName()));
                myLogD("copyFolder - on folder -> recursive call");
                copyFolderRecursiveDoc(child, subDest);
                nbFolder++;
            } else if (child.isFile()) {
                String name = safeName(child.getName());

                boolean isPic = SupportedFilesHelper.isImage(child);
                boolean doCopy = SupportedFilesHelper.isAudio(child) || SupportedFilesHelper.isVideo(child)
                        || (isPic && nbPic < MAX_NB_PIC);

                if (doCopy) {
                    File out = new File(destinationFolder, name);
                    if (copyFileFromDoc(child, out)) {
                        if (isPic) nbPic++; else nbFileCopied++;
                    } else {
                        if (!isPic) nbFileKO++;
                    }
                }
            }
        }
    }

    private boolean copyFileFromDoc(androidx.documentfile.provider.DocumentFile docFile, File destinationFile) {
        myLogD("copyFileFromDoc() -> " + destinationFile.getParentFile().getAbsolutePath());
        try (InputStream in = context.getContentResolver().openInputStream(docFile.getUri());
             FileOutputStream out = new FileOutputStream(destinationFile)) {

            if (in == null) throw new IllegalStateException("openInputStream returned null for: " + docFile.getUri());

            byte[] buf = new byte[1024];
            int len, nbBuffCopied = 0;
            while ((len = in.read(buf)) > 0) {
                if (isStopped()) {
                    TaskStateManager.markTaskCancelled(TASK_NAME);
                    hasBeenCancelled = true;
                    return false;
                }
                out.write(buf, 0, len);
                copiedSize += len;
                if (nbBuffCopied % 1024 == 0) buildProgressString();
                nbBuffCopied++;
            }
            return true;
        } catch (Exception e) {
            myLogEE(e, "copyFileFromDoc");
            return false;
        }
    }

    private String safeName(String n) {
        return n == null ? "unnamed" : n;
    }

    private void buildProgressString() {
        long progress = (int) ((copiedSize * 100) / totalSize);

        String progressMsg = sourceLocation.equals("cloud")
                ? context.getString(R.string.Import_Progress_copying_file_from_cloud)
                : context.getString(R.string.Import_Progress_copying_file_from_general_storage);

        progressMsg = progressMsg + (destinationLocation.equals(StorageHelper.MemoryLocationType.SDCARD_RESERVED)
                ? context.getString(R.string.Import_Progress_copying_file_to_sd_card_reserved)
                : context.getString(R.string.Import_Progress_copying_file_to_internal_reserved));

        String msg = progressMsg + "\n\n" +
                context.getString(R.string.Error_Import_NotEnoughMemory_line3) + formatMemPadding(copiedSize / 1024 / 1024, 0) + " " + context.getString(R.string.MB)
                + " / " + formatMemPadding(totalSize / 1024 / 1024, 0) + " " + context.getString(R.string.MB) + "\n" +
                context.getString(R.string.Error_Import_NotEnoughMemory_line2_1) + Tonio.formatMemPadding(availableMemory / 1048576L) + " " + context.getString(R.string.MB);
        //TODO sd card or internal....   + live changing availableMemory ?

        if (!msg.equals(last_logged_msg)) {
            last_logged_msg = msg;
            TaskStateManager.tellProgress(TASK_NAME, (int) progress, msg);
        }
        if (progress != last_logged_progress) {
            last_logged_progress = progress;
            myLogD(progress + "% - " + msg.replace("\n", " . "));
        }
    }

    private boolean isSizeOk(String type) {
        myLogD("checking size");
        try {
            long size_check_inflate_coefficient = 1;
            if ("ZIP".equalsIgnoreCase(type)) {
                size_check_inflate_coefficient = Var.ZIP_SIZE_MAX_COEF;
            } else if ("M4B".equalsIgnoreCase(type)) {
                size_check_inflate_coefficient = Var.M4B_SIZE_MAX_COEF;
            }
            myLogD("size_check_inflate_coefficient = [" + size_check_inflate_coefficient + "]");
            myLogD("totalSize : [" + totalSize + "] => [" + totalSize * size_check_inflate_coefficient + "] - availableMemory : [" + availableMemory + "]");
            if (totalSize > 0 && totalSize * size_check_inflate_coefficient > availableMemory) {
                return false;
            }
        } catch (Exception e) {
            TaskStateManager.tellWarning(context.getString(R.string.error) + " " + context.getString(R.string.checking_size) + " - " + e.getMessage());
        }
        return true;
    }
}
