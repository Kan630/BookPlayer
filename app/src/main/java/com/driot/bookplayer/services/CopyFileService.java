package com.driot.bookplayer.services;
/**
 *  2023-10-09 - Tonio
 *
 *  CopyFileService
 *
 *  // IN : Uri  +   destinationFolderPath
 *  // Send Broadcast : Progress, Error, End
 *
 */

import static com.driot.bookplayer.global.Var.ONLY_MIME_AUDIO;
import static com.driot.bookplayer.global.Var.SUPPORTED_AUDIO_EXTENSIONS;
import static com.driot.bookplayer.global.Var.ZIP_SIZE_MAX_COEF;
import static com.driot.bookplayer.utils.StorageHelper.getAvailableInternalMemorySize;
import static com.driot.bookplayer.utils.Tonio.formatMem;
import static com.driot.bookplayer.utils.Tonio.getSourceLocation;

import android.app.Service;
import android.content.ContentResolver;
import android.content.Intent;
import android.net.Uri;
import android.os.Binder;
import android.os.IBinder;

import androidx.annotation.Nullable;

import com.driot.bookplayer.R;
import com.driot.bookplayer.utils.log.LoggingService;
import com.driot.bookplayer.utils.FileUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;


public class CopyFileService extends LoggingService {  //IntentService are designed to run in the background....   but let's use an executor or thread

    public static volatile boolean isCopyRunning = false;

    private final IBinder binder = new CopyFileService.CopyFileServiceBackgroundBinder();
    Callbacks mCallBacks;

    private static final int COPY_BUFFER_SIZE = 1024;

    private Uri uri;
    private String destinationFolderPath;
    private String destinationFileName;
    private String type;
    private boolean checkSize;
    private long forceSize;
    private String sourceLocation;

    // Callbacks
    //-----------------------------
    public interface Callbacks {
        void copyFileService_tellProgressNoLog(String progressText, int progressVal);
        void copyFileService_tellProgress(String progressText, int progressVal);
        void copyFileService_tellError(String errorText);
        void copyFileService_tellEnd(String destinationFolderPath, String destinationFolderName);
    }
    public void registerClient(Service service) {
        this.mCallBacks = (Callbacks) service;
    }

    // binder
    //-----------------------------
    public class CopyFileServiceBackgroundBinder extends Binder {
        public CopyFileService getService() {
            return CopyFileService.this;
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        parseIntent(intent);
        return binder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        //unbindService(add);
    }
    //-----------------------------

    private void parseIntent(Intent intent) {
        uri = intent.getParcelableExtra("Uri");
        destinationFolderPath = intent.getStringExtra("destinationFolderPath");
        destinationFileName = intent.getStringExtra("destinationFileName");
        type = intent.getStringExtra("type");
        checkSize = intent.getBooleanExtra("checkSize", true);
        forceSize = intent.getLongExtra("forceSize", 0);
        sourceLocation = getSourceLocation(uri);

        myLog("parseIntent() ..   " +
                "\n.    from uri = [" + uri.toString() + "] " +
                "\n.    to folder = [" + destinationFolderPath + "] " +
                "\n.    with name = [" + destinationFileName + "]" +
                "\n.    for type = [" + type + "]" +
                "\n.    check size = [" + checkSize + "]" +
                "\n.    force size = [" + forceSize + "]" +
                "\n.    source Location = [" + sourceLocation + "]"
        );
    }

    public void init() {
        myLogD("init()");
        isCopyRunning = true;
        Thread backgroundThread = new Thread(() -> {
            Boolean ret = copyLocal(checkSize);
        });
        backgroundThread.start();
    }

    private boolean copyLocal(boolean doCheckSize) {
        long file_size = -1;
        long availableMegs = getAvailableInternalMemorySize() / 1048576L;
        long size_coef = type.equals("ZIP") ? ZIP_SIZE_MAX_COEF : 1;

        // Determine size
        if (doCheckSize) {
            if ("Folder".equals(type)) {
                file_size = forceSize / 1048576L; // already calculated externally
            } else {
                try {
                    file_size = FileUtils.getFileSize(this, uri);
                    if (file_size > 0) {
                        file_size = file_size / 1024 / 1024; // bytes to MB
                        myLog("File size determined: " + file_size + " Mo");
                    } else {
                        myLogW("Could not determine file size, may be a cloud file.");
                    }
                } catch (Exception e) {
                    myLogEE(e, "Error getting file size from URI");
                }
            }

            if (file_size > 0 && file_size * size_coef > availableMegs) {
                String strErr = getResources().getString(R.string.Error_Import_NotEnoughMemory_line1) + "\n\n" +
                        getResources().getString(R.string.Error_Import_NotEnoughMemory_line2_1) + formatMem(availableMegs) + "Mo" + "\n" +
                        getResources().getString(R.string.Error_Import_NotEnoughMemory_line3) + formatMem(file_size) + "Mo";

                if (size_coef > 1) {
                    strErr += "\n" + getResources().getString(R.string.Error_Import_NotEnoughMemory_line4_1) +
                            size_coef + getResources().getString(R.string.Error_Import_NotEnoughMemory_line4_2);
                }

                strErr += "\n\n" + (type.equals("ZIP") ?
                        getResources().getString(R.string.Error_Import_NotEnoughMemory_line5_zip) :
                        getResources().getString(R.string.Error_Import_NotEnoughMemory_line5_other));

                tellError(strErr);
                return false;
            }

            myLog("copyLocal - Memory check passed.");
        } else {
            myLog("copyLocal - Memory check skipped.");
        }

        // Create destination folder if needed
        File destinationFolderFile = new File(destinationFolderPath);
        try {
            if (!destinationFolderFile.exists() && !destinationFolderFile.mkdirs()) {
                tellError(getResources().getString(R.string.Error_Import_Creating_Folders) + " : " + destinationFolderPath);
                return false;
            }
        } catch (Exception e) {
            myLogEE(e, "Error creating destination folder");
            tellError(getResources().getString(R.string.Error_Import_Creating_Folders));
            return false;
        }

        if ("Folder".equals(type)) {
            return copyFolder(file_size, availableMegs);
        } else {
            return copyFile(file_size, availableMegs);
        }
    }

//Folder
    private boolean copyFolder(long file_size, long availableMegs) {
        tellProgress(0, "starting copy");
        try {
            long[] lastLoggedProgress = {-1}; // effectively final, could have used new AtomicLong(-1);
            FileUtils.copyFolder(this, uri, new File(destinationFolderPath + "/" + destinationFileName)
                    , null, forceSize
                    , ONLY_MIME_AUDIO, SUPPORTED_AUDIO_EXTENSIONS
                    , (progress, nbMoCopied) -> {

                        String progressMsgSource = getResources().getString(R.string.Import_Progress_copying_zip_file);
                        if (sourceLocation.equals("cloud")) {
                            progressMsgSource = getResources().getString(R.string.Import_Progress_copying_zip_file_cloud);
                        }
                        String progress_text = progressMsgSource
                                + "\n"
                                + "\n" + getResources().getString(R.string.Error_Import_NotEnoughMemory_line3) + formatMem(nbMoCopied, 0) + "Mo/" + formatMem(file_size, 0) + "Mo"
                                + "\n" + getResources().getString(R.string.Error_Import_NotEnoughMemory_line2_1) + formatMem(availableMegs) + "Mo";

                        if (progress != lastLoggedProgress[0]) {
                            lastLoggedProgress[0] = progress;
                            String singleLineLog = ("..." + progress + "%\n" + progress_text).replace("\n", " - ");
                            myLog(singleLineLog);
                        }
                        tellProgressNoLog((int) progress, progress_text);
                    }
            );
        } catch (Exception e) {
            myLogEE(e, "copy type folder");
            if (e.getMessage() != null && e.getMessage().equals("Copy canceled")) {
                myLog("Copy was canceled by user.");
                tellError("Copy canceled.");
            } else {
                tellError("Folder copy failed: " + e.getMessage());
            }
            return false;
        }
        myLog("Folder has been copied");
        tellEnd(destinationFolderPath, null);
        return true;
    }
//File
    private boolean copyFile(long file_size, long availableMegs) {
        ////////////////////////////////////////////////////////////////////////////////////////
        // copy of Zip file
        ////////////////////////////////////////////////////////////////////////////////////////
        int nbBuffCopied = 0;
        InputStream is = null;
        ContentResolver resolver = getContentResolver();
        File outFile = new File(destinationFolderPath + "/" + destinationFileName);
        try {
            is = resolver.openInputStream(uri);
            myLogD("okay stream in");
            try {
                OutputStream out = new FileOutputStream(outFile);
                myLogD("okay stream out");
                try {
                    byte[] buf = new byte[COPY_BUFFER_SIZE];
                    int len;
                    int last_logged_progress_percent = -1;
                    while ((len = is.read(buf)) > 0) {
                        if (!isCopyRunning) {
                            myLog("Copy canceled by user.");
                            tellError("Copy canceled.");
                            return false;
                        }
                        nbBuffCopied++;
                        out.write(buf, 0, len);

                        //display progress
                        if (nbBuffCopied % 1024 == 0) {
                            long nbBytesCopied = (long) nbBuffCopied * COPY_BUFFER_SIZE;
                            long nbMoCopied = nbBytesCopied / 1024 / 1024;

                            double progress_percent = 0;
                            if (file_size > 0) {
                                progress_percent = (double) nbMoCopied / file_size * 100;
                            } else {
                                progress_percent = 50;
                            }
                            int current_progress_percent = (int) progress_percent;

                            String progressMsgSource = getResources().getString(R.string.Import_Progress_copying_zip_file);
                            if (sourceLocation.equals("cloud")) {progressMsgSource = getResources().getString(R.string.Import_Progress_copying_zip_file_cloud);}
                            String progress_text = progressMsgSource
                                    + "\n"
                                    + "\n" + getResources().getString(R.string.Error_Import_NotEnoughMemory_line3) + formatMem(nbMoCopied,0) + "Mo/" + formatMem(file_size,0) + "Mo"
                                    + "\n" + getResources().getString(R.string.Error_Import_NotEnoughMemory_line2_1) + formatMem(availableMegs) + "Mo";

                            if (current_progress_percent != last_logged_progress_percent) {
                                last_logged_progress_percent = current_progress_percent;
                                String singleLineLog = ("..." + current_progress_percent + "%\n" + progress_text).replace("\n", " - ");
                                myLogD(singleLineLog);
                            }

                            tellProgressNoLog(current_progress_percent, progress_text);
                        }

                    }
                    myLogD("okay stream write");
                } catch (Exception e) {
                    myLogEE(e, "zip file copy");
                    tellError("An error occurred while Copying the ZIP file from External Dir to Internal Dir. (nb Buffer copied = " + nbBuffCopied + ")\n   -  \n" + e.getMessage());
                    return false;
                } finally {
                    out.close();
                }
            } catch (Exception e) {
                myLogEE(e, "zip file copy");
                tellError("Cannot get StreamOut for ZIP file \nZIP file copy from External Dir to Internal Dir aborted.\n  -  \n" + e.getMessage());
                return false;
            } finally {
                try {
                    if (is!=null) {
                        is.close();
                    }
                } catch (IOException ise) {
                    myLogEE(ise,"is.close();");
                }
            }
        } catch (Exception e) {
            myLogEE(e, "zip file copy");
            tellError("Cannot get StreamIn for ZIP file... \nMaybe this is a broken zip file \n(could be a half downloaded file)      \n\nTechnical message = [" + e.getMessage() + "]");
            return false;
        }
        myLogD("file has been copied");
        tellEnd(destinationFolderPath, destinationFileName);
        return true;
    }

    //-----------------------------
    // Callbacks
    //-----------------------------
    private void tellError(String errorText) {
        mCallBacks.copyFileService_tellError(errorText);
        isCopyRunning = false;
        stopSelf();
    }
    private void tellEnd(String destinationFolderPath, String destinationFolderName) {
        mCallBacks.copyFileService_tellEnd(destinationFolderPath, destinationFolderName);
        isCopyRunning = false;
        stopSelf();
    }
    public void tellProgressNoLog(int progressVal, String progressText) {
        mCallBacks.copyFileService_tellProgressNoLog(progressText,  progressVal);
    }
    public void tellProgress(int progressVal, String progressText) {
        mCallBacks.copyFileService_tellProgress(progressText,  progressVal);
    }


}
