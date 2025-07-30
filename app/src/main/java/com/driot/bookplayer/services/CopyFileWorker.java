package com.driot.bookplayer.services;

import static com.driot.bookplayer.global.Var.ONLY_MIME_AUDIO;
import static com.driot.bookplayer.global.Var.SUPPORTED_AUDIO_EXTENSIONS;
import static com.driot.bookplayer.global.Var.ZIP_SIZE_MAX_COEF;
import static com.driot.bookplayer.utils.StorageHelper.getAvailableInternalMemorySize;
import static com.driot.bookplayer.utils.Tonio.formatMemPadding;
import static com.driot.bookplayer.utils.Tonio.getSourceLocation;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.work.WorkerParameters;

import com.driot.bookplayer.R;
import com.driot.bookplayer.global.Pref;
import com.driot.bookplayer.objects.LoadBookTaskState;
import com.driot.bookplayer.objects.TaskStateManager;
import com.driot.bookplayer.utils.FileUtils;
import com.driot.bookplayer.utils.Tonio;
import com.driot.bookplayer.utils.log.LoggingWorker;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class CopyFileWorker extends LoggingWorker {
    private static final String TASK_NAME = "copy file";

    private final Context context;

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
        String sourceLocation = getSourceLocation(uri);

        myLog("parseIntent() ..   " +
                "\n.    from uri = [" + uri.toString() + "] " +
                "\n.    to folder = [" + destinationFolderPath + "] " +
                "\n.    with name = [" + destinationFileName + "]" +
                "\n.    for type = [" + type + "]" +
                "\n.    check size = [" + checkSize + "]" +
                "\n.    force size = [" + forceSize + "]" +
                "\n.    source Location = [" + sourceLocation + "]"
        );

        try {
            boolean result;
            if ("Folder".equals(type)) {
                result = copyFolder(uri, destinationFolderPath, destinationFileName, forceSize, sourceLocation);
            } else {
                result = copyFile(uri, destinationFolderPath, destinationFileName, type, checkSize, forceSize, sourceLocation);
            }

            if (result) {
                TaskStateManager.markTaskCompleted(context, TASK_NAME, destinationFolderPath);
                return Result.success();
            } else {
                return Result.failure();
            }
        } catch (Exception e) {
            TaskStateManager.markTaskFailed(context, TASK_NAME, e.getMessage());
            return Result.failure();
        }
    }

    private boolean copyFolder(Uri uri, String destinationFolderPath, String destinationFolderName, long forceSize, String sourceLocation) {
        try {
            long[] lastLoggedProgress = {-1};
            FileUtils.copyFolder(context, uri, new File(destinationFolderPath + "/" + destinationFolderName),
                    null, forceSize, ONLY_MIME_AUDIO, SUPPORTED_AUDIO_EXTENSIONS,
                    (progress, nbMoCopied) -> {
                        String progressMsg = sourceLocation.equals("cloud")
                                ? context.getString(R.string.Import_Progress_copying_zip_file_cloud)
                                : context.getString(R.string.Import_Progress_copying_zip_file);

                        String msg = progressMsg + "\n\n" +
                                context.getString(R.string.Error_Import_NotEnoughMemory_line3) + formatMemPadding(nbMoCopied, 0) + "Mo/" + formatMemPadding(forceSize, 0) + "Mo\n" +
                                context.getString(R.string.Error_Import_NotEnoughMemory_line2_1) + Tonio.formatMemPadding(getAvailableInternalMemorySize() / 1048576L) + "Mo";

                        if (progress != lastLoggedProgress[0]) {
                            lastLoggedProgress[0] = progress;
                            TaskStateManager.tellProgress((int) progress, msg);
                        }
                    });
        } catch (Exception e) {
            TaskStateManager.markTaskFailed(context, TASK_NAME, e.getMessage());
            return false;
        }
        return true;
    }

    private boolean copyFile(Uri uri, String destinationFolderPath, String destinationFileName, String type, boolean checkSize, long forceSize, String sourceLocation) {
        int nbBuffCopied = 0;
        long file_size = checkSize ? FileUtils.getFileSize(context, uri) / 1024 / 1024 : -1;
        long availableMegs = getAvailableInternalMemorySize() / 1048576L;
        long size_coef = "ZIP".equals(type) ? ZIP_SIZE_MAX_COEF : 1;

        if (checkSize && file_size > 0 && file_size * size_coef > availableMegs) {
            TaskStateManager.markTaskFailed(context, TASK_NAME, "Not enough memory");
            return false;
        }

        try (InputStream is = context.getContentResolver().openInputStream(uri);
             OutputStream out = new FileOutputStream(new File(destinationFolderPath + "/" + destinationFileName))) {

            byte[] buf = new byte[1024];
            int len;
            int last_logged_progress = -1;

            while ((len = is.read(buf)) > 0) {
                if (isStopped()) {
                    TaskStateManager.markTaskCancelled(context, TASK_NAME);
                    return false;
                }
                nbBuffCopied++;
                out.write(buf, 0, len);

                if (nbBuffCopied % 1024 == 0) {
                    long nbMoCopied = ((long) nbBuffCopied * 1024) / 1024 / 1024;
                    double percent = file_size > 0 ? (double) nbMoCopied / file_size * 100 : 50;
                    int progress = (int) percent;

                    if (progress != last_logged_progress) {
                        last_logged_progress = progress;
                        String msg = context.getString(R.string.Import_Progress_copying_zip_file) + "\n\n" +
                                context.getString(R.string.Error_Import_NotEnoughMemory_line3) + formatMemPadding(nbMoCopied, 0) + "Mo/" + formatMemPadding(file_size, 0) + "Mo\n" +
                                context.getString(R.string.Error_Import_NotEnoughMemory_line2_1) + Tonio.formatMemPadding(availableMegs) + "Mo";
                        TaskStateManager.tellProgress(progress, msg);
                    }
                }
            }
        } catch (Exception e) {
            TaskStateManager.markTaskFailed(context, TASK_NAME, e.getMessage());
            return false;
        }
        return true;
    }
}
