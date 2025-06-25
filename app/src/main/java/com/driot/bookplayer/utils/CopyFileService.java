package com.driot.bookplayer.utils;
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
import static com.driot.bookplayer.utils.FileUtils.getFileSize;
import static com.driot.bookplayer.utils.Tonio.formatMem;
import static com.driot.bookplayer.utils.Tonio.getAvailableInternalMemorySize;
import static com.driot.bookplayer.utils.Tonio.getSourceLocation;

import android.app.Service;
import android.content.ContentResolver;
import android.content.Intent;
import android.net.Uri;
import android.os.Binder;
import android.os.IBinder;

import androidx.annotation.Nullable;

import com.driot.bookplayer.R;
import com.driot.bookplayer.activities.LifecycleLoggingService;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;


public class CopyFileService extends LifecycleLoggingService {  //IntentService are designed to run in the background....   but let's use an executor or thread

    private final IBinder binder = new CopyFileService.CopyFileServiceBackgroundBinder();
    Callbacks mCallBacks;

    public static boolean isBusy;

    private static final int COPY_BUFFER_SIZE = 1024;

    private Uri uri;
    private String destinationFolderPath;
    private String destinationFileName;
    private String type;
    private boolean checkSize;
    private long forceSize;
    private String sourceLocation;

    private File inFile;

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
        myLog("onBind()    intent:" + intent.getDataString());
        parseIntent(intent);
        return binder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        myLog("onUnBind()    intent:" + intent.getDataString());
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        myLog("onDestroy()");
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
        myLog("init()");
        isBusy = true;

// All the Stuff around inFile is non mandatory...
// will later use Uri anyway
//*************
        String originalFilePath = uri.getPath();
        if (originalFilePath != null) {
            inFile = new File(originalFilePath);
        } else {
            myLog("Could not get Uri Path");
        }
        if (inFile != null && inFile.exists()) {
            myLog("inFile correctly populated from Uri - Length = " + inFile.length());
        } else {
            myLog("Uri Path gives non existing file (cloud?)");
        }
//*************
        /*
        outFile = new File(destinationFolderPath + "/" + destinationFileName);
        String destinationFolderPath = outFile.getParent();
        destinationFolderFile = new File(destinationFolderPath);

         */

        myLog("init() - ** INIT DONE ** launching backThread copy ");
        Thread backgroundThread = new Thread(() -> {
            Boolean ret = copyLocal(checkSize);
        });
        backgroundThread.start();
    }



    private boolean copyLocal(boolean doCheckSize) {
        //___________________________________
        // == Checking memory before copy
        //___________________________________
        long file_size = 0;
        long availableMegs = getAvailableInternalMemorySize() / 1048576L;
        if (doCheckSize) {
            long size_coef = type.equals("ZIP") ? ZIP_SIZE_MAX_COEF : 1;
//Folder
            if (type.equals("Folder")) {
                file_size = forceSize;
                /*   // Below produce StackOverFlow !!
                try {
                    file_size = FileUtils.calculateFolderSize(this, uri);
                } catch (Exception e) {
                    myLogE("Folder getSize - KO : " + e.getMessage());
                }
                 */
//File
            } else {
                try {
                    file_size = Long.parseLong(String.valueOf(inFile.length() / 1048576L));
                } catch (Exception e) {
                    myLogE("getting FileSize raise an error...");
                    file_size = -2;
                }
                if (!(file_size > 0)) {
                    try {
                        file_size = (int) getFileSize(this, uri);
                    } catch (IOException e) {
                        myLogE("copyLocal() - getting FileSize From URI raise an error... " + e.getMessage());
                    }
                }
            }
            file_size = file_size / 1024 / 1024;
            myLog("file size : " + file_size + "Mo" +
                    "\navailable memory : " + availableMegs + " Mo");
//////
            if (file_size > 0 || availableMegs > 0) {
                try {
                    if (file_size * size_coef > availableMegs) {
                        String strErr =
                                getResources().getString(R.string.Error_Import_NotEnoughMemory_line1)
                                        + "\n\n" + getResources().getString(R.string.Error_Import_NotEnoughMemory_line2_1) + formatMem(availableMegs) + "Mo"
                                        + "\n" + getResources().getString(R.string.Error_Import_NotEnoughMemory_line3) + formatMem(file_size) + "Mo" + "\n";
                        if (size_coef > 1) strErr = strErr + "\n" + getResources().getString(R.string.Error_Import_NotEnoughMemory_line4_1) + size_coef + getResources().getString(R.string.Error_Import_NotEnoughMemory_line4_2);
                        if (type.equals("ZIP")) {
                            strErr = strErr + "\n\n" + getResources().getString(R.string.Error_Import_NotEnoughMemory_line5_zip);
                        } else {
                            strErr = strErr + "\n\n" + getResources().getString(R.string.Error_Import_NotEnoughMemory_line5_other);
                        }
                        tellError(strErr);
                        return false;
                    }
                    String progressMsgSource = getResources().getString(R.string.Import_Progress_copying_zip_file);
                    if (sourceLocation.equals("cloud")) {progressMsgSource = getResources().getString(R.string.Import_Progress_copying_zip_file_cloud);}
                    tellProgress(0, progressMsgSource
                            + "\n"
                            + "\n" + getResources().getString(R.string.Error_Import_NotEnoughMemory_line3) + formatMem(file_size,0) + "Mo"
                            + "\n" + getResources().getString(R.string.Error_Import_NotEnoughMemory_line2_1) + formatMem(availableMegs) + "Mo"
                    );
                } catch (Exception e) {
                    e.printStackTrace();
                    tellError("Error while checking available space for local ZIP copy  -  " + e.getMessage());
                    return false;
                }
            } else {
                myLogE("Could not get size of source file [" + file_size + "] or available space [" + availableMegs + "]");
            }
            myLog("copyLocal - okay check storage space");
        } else {
            file_size = -1;
            myLog("WARNING - copyLocal - bypass check storage space");
        }

        //___________________________________
        // == Make Folder
        //___________________________________
        File destinationFolderFile = new File(destinationFolderPath);
        try {
            if (!destinationFolderFile.exists()) {
                if (!destinationFolderFile.mkdirs()) {
                    tellError(getResources().getString(R.string.Error_Import_Creating_Folders) + " for path : " + destinationFolderFile);
                    return false;
                } else {
                    myLog("folder created : [" +  destinationFolderPath + "]");
                }
            }
        } catch (Exception e) {
            tellError(getResources().getString(R.string.Error_Import_Creating_Folders));
            return false;
        }
        myLog("okay folder");



        //___________________________________
        // == start COPY
        //___________________________________

//Folder
        if (type.equals("Folder")) {
            tellProgress(0,"starting copy");
            try {
                //FileUtils.copyFolder(this, uri, destinationFolderPath , progress -> runOnUiThread(() ->
                long finalFile_size = file_size;
                long[] lastLoggedProgress = {-1}; // effectively final, could have used new AtomicLong(-1);
                FileUtils.copyFolder(this, uri, new File(destinationFolderPath)
                        , null , forceSize
                        , ONLY_MIME_AUDIO, SUPPORTED_AUDIO_EXTENSIONS
                        , (progress, nbMoCopied) -> {
                            //this.progress = progress;
                            //this.mbCopied = mbCopied;
                            //runOnUiThread(() -> { Updating the UI with progress and MB copied values, like progressBar.setProgress(progress);

                            String progressMsgSource = getResources().getString(R.string.Import_Progress_copying_zip_file);
                            if (sourceLocation.equals("cloud")) {progressMsgSource = getResources().getString(R.string.Import_Progress_copying_zip_file_cloud);}
                            String progress_text = progressMsgSource
                            + "\n"
                            + "\n" + getResources().getString(R.string.Error_Import_NotEnoughMemory_line3) + formatMem(nbMoCopied,0) + "Mo/" + formatMem(finalFile_size,0) + "Mo"
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
                tellError("Folder copy - KO : " + e.getMessage());
                return false;
            }
            myLog("Folder has been copied");
            tellEnd(destinationFolderPath,null);
            return true;
//File
        } else {
            ////////////////////////////////////////////////////////////////////////////////////////
            // copy of Zip file
            ////////////////////////////////////////////////////////////////////////////////////////
            int nbBuffCopied = 0;
            InputStream is = null;
            ContentResolver resolver = getContentResolver();
            File outFile = new File(destinationFolderPath + "/" + destinationFileName);
            try {
                is = resolver.openInputStream(uri);
                myLog("okay stream in");
                try {
                    OutputStream out = new FileOutputStream(outFile);
                    myLog("okay stream out");
                    try {
                        byte[] buf = new byte[COPY_BUFFER_SIZE];
                        int len;
                        int last_logged_progress_percent = -1;
                        while ((len = is.read(buf)) > 0) {
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
                                    myLog(singleLineLog);
                                }

                                tellProgressNoLog(current_progress_percent, progress_text);
                            }

                        }
                        myLog("okay stream write");
                    } catch (Exception e) {
                        tellError("An error occurred while Copying the ZIP file from External Dir to Internal Dir. (nb Buffer copied = " + nbBuffCopied + ")\n   -  \n" + e.getMessage());
                        e.printStackTrace();
                        return false;
                    } finally {
                        out.close();
                    }
                } catch (Exception e) {
                    tellError("Cannot get StreamOut for ZIP file \nZIP file copy from External Dir to Internal Dir aborted.\n  -  \n" + e.getMessage());
                    e.printStackTrace();
                    return false;
                } finally {
                    is.close();
                }
            } catch (Exception e) {
                tellError("Cannot get StreamIn for ZIP file... \nMaybe this is a broken zip file \n(could be a half downloaded file)      \n\nTechnical message = [" + e.getMessage() + "]");
                myLogE(e.getMessage());
                return false;
            }
            myLog("file has been copied");
            tellEnd(destinationFolderPath, destinationFileName);
            return true;
        }
    }

    //-----------------------------
    // Callbacks
    //-----------------------------
    private void tellError(String errorText) {
        mCallBacks.copyFileService_tellError(errorText);
        isBusy = false;
        stopSelf();
    }
    private void tellEnd(String destinationFolderPath, String destinationFolderName) {
        mCallBacks.copyFileService_tellEnd(destinationFolderPath, destinationFolderName);
        isBusy = false;
        stopSelf();
    }
    public void tellProgressNoLog(int progressVal, String progressText) {
        mCallBacks.copyFileService_tellProgressNoLog(progressText,  progressVal);
    }
    public void tellProgress(int progressVal, String progressText) {
        mCallBacks.copyFileService_tellProgress(progressText,  progressVal);
    }


    //-----------------------------
    private void myLog(String str) { KanLogger.myLog(this.getClass().getName(), str); }
    private void myLogE(String str) { KanLogger.myLogE(this.getClass().getName(), str); }
    private void myToast(String str) { KanLogger.myToast(this.getClass().getName(), str); }
    //-----------------------------


}
