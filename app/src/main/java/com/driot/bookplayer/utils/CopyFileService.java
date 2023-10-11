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

import static com.driot.bookplayer.global.Var.ZIP_SIZE_MAX_COEF;
import static com.driot.bookplayer.utils.Tonio.formatMem;
import static com.driot.bookplayer.utils.Tonio.getAvailableInternalMemorySize;

import android.app.Service;
import android.content.ContentResolver;
import android.content.Intent;
import android.net.Uri;
import android.os.Binder;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;

import androidx.annotation.Nullable;

import com.driot.bookplayer.R;
import com.driot.tonylib.KanLogger;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;


public class CopyFileService extends Service {  //IntentService are designed to run in the background....   but let's use an executor or thread

    private final IBinder binder = new CopyFileService.CopyFileServiceBackgroundBinder();
    Callbacks mCallBacks;

    public static boolean isBusy;

    private static final int COPY_BUFFER_SIZE = 1024;

    private Uri uri;
    private String destinationFolderPath;
    private String destinationFileName;

    private File inFile;

    /*
    private File destinationFolderFile;
    private File inFile;
    private File outFile;

     */

    // Callbacks
    //-----------------------------
    public interface Callbacks {
        void tellProgressClient_fromCopy(String progressText, int progressVal);
        void tellErrorClient_fromCopy(String errorText);
        void tellEndClient_fromCopy();
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
    /*
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        parseIntent(intent);
        return super.onStartCommand(intent, flags, startId);
        //return START_NOT_STICKY;
    }
     */

    private void parseIntent(Intent intent) {
        uri = intent.getParcelableExtra("Uri");
        destinationFolderPath = intent.getStringExtra("destinationFolderPath");
        destinationFileName = intent.getStringExtra("destinationFileName");
        myLog("onStartCommand() ..   " +
                "\nfrom uri = [" + uri.toString() + "] " +
                "\nto folder = [" + destinationFolderPath + "] " +
                "\nwith name = [" + destinationFileName + "]");
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
            myLog("Uri Path gives non existing file");
        }
//*************
        /*
        outFile = new File(destinationFolderPath + "/" + destinationFileName);
        String destinationFolderPath = outFile.getParent();
        destinationFolderFile = new File(destinationFolderPath);

         */

        myLog("init() - ** INIT DONE ** launching backThread copy ");
        Thread backgroundThread = new Thread(() -> {
            Boolean ret = copyLocal();
        });
        backgroundThread.start();
    }


    private boolean copyLocal() {
        return copyLocal(true);
    }

    private boolean copyLocal(boolean doCheckSize) {
        /*
        myLog("copyLocal - from inFile to outFile " +
                "\nfrom [" + uri + "] " +
                "\nto [" + outFile + "] " +
                "\nusing [" + destinationFolderFile + "]");
*/
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
        // == Checking memory before copy
        //___________________________________
        File outFile = new File(destinationFolderPath + "/" + destinationFileName);
        int file_size = 0;
        long availableMegs = getAvailableInternalMemorySize() / 1048576L;
        if (doCheckSize) {
            try {
                file_size = Integer.parseInt(String.valueOf(inFile.length() / 1024 / 1024));
            } catch (Exception e) {
                myLogE("getting FileSize raise an error...");
                file_size = -2;
            }
            if (!(file_size > 0 )) {
                try {
                    file_size = (int) getFileSize(uri);
                } catch (IOException e) {
                    myLogE("getting FileSize From URI raise an error... " + e.getMessage() );
                    e.printStackTrace();
                }
            }
            if (file_size > 0 || availableMegs > 0) {

                try {
                    if (file_size * ZIP_SIZE_MAX_COEF > availableMegs) {
                        tellError(getResources().getString(R.string.Error_Import_NotEnoughMemory_line1) + "\n"
                                + getResources().getString(R.string.Error_Import_NotEnoughMemory_line2_1) + formatMem(availableMegs) + "Mo" + "\n"
                                + getResources().getString(R.string.Error_Import_NotEnoughMemory_line3) + file_size + "Mo" + "\n"
                                + getResources().getString(R.string.Error_Import_NotEnoughMemory_line4_1) + ZIP_SIZE_MAX_COEF + getResources().getString(R.string.Error_Import_NotEnoughMemory_line4_2) + "\n"
                                + "\n" + getResources().getString(R.string.Error_Import_NotEnoughMemory_line5)
                        );
                        return false;
                    }
                    tellProgress(0, getResources().getString(R.string.Import_Progress_copying_zip_file)
                            + "\n"
                            + "\n" + getResources().getString(R.string.Error_Import_NotEnoughMemory_line3) + file_size + "Mo"
                            + "\n" + getResources().getString(R.string.Error_Import_NotEnoughMemory_line2_1) + formatMem(availableMegs) + "Mo"
                    );
                } catch (Exception e) {
                    e.printStackTrace();
                    tellError("Error while checking available space for local ZIP copy  -  " + e.getMessage());
                    return false;
                }
            } else { // file_size < 0
                myLogE("Could not get size of original file to be copied");
            }
            myLog("file size : " + file_size + "Mo" +
                    "\navailable memory : " + availableMegs + " Mo");
            myLog("copyLocal - okay check storage space");
        } else {
            file_size = -1;
            myLog("copyLocal - bypass check storage space");
        }

        ////////////////////////////////////////////////////////////////////////////////////////
        // copy of Zip file
        ////////////////////////////////////////////////////////////////////////////////////////
        int nbBuffCopied = 0;
        InputStream is = null;
        ContentResolver resolver = getContentResolver();
        try {
            is = resolver.openInputStream(uri);
            myLog("okay stream in");
            try {
                OutputStream out = new FileOutputStream(outFile);
                myLog("okay stream out");
                try {
                    byte[] buf = new byte[COPY_BUFFER_SIZE];
                    int len;
                    while ((len = is.read(buf)) > 0) {
                        nbBuffCopied++;
                        out.write(buf, 0, len);

                        //display progress
                        if (nbBuffCopied % 1024 == 0) {
                            int nbMoCopied = nbBuffCopied * COPY_BUFFER_SIZE / 1024 / 1024;
                            double progressValue = 0;
                            if (file_size > 0) {
                                progressValue = (double) nbMoCopied / file_size * 100;
                            } else {
                                progressValue = 50;
                            }
                            tellProgress((int) progressValue,
                                    getResources().getString(R.string.Import_Progress_copying_zip_file)
                                            + "\n"
                                            + "\n" + getResources().getString(R.string.Error_Import_NotEnoughMemory_line3) + nbMoCopied + "Mo/" + file_size + "Mo"
                                            + "\n" + getResources().getString(R.string.Error_Import_NotEnoughMemory_line2_1) + formatMem(availableMegs) + "Mo"
                            );
                        }

                    }
                    myLog("okay stream write");
                } catch (Exception e) {
                    tellError("An error occurred while Copying the ZIP file from External Dir to Internal Dir.\n   -  \n" + e.getMessage());
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
        tellEnd();
        return true;
    }

    //-----------------------------
    // Callbacks
    //-----------------------------
    private void tellError(String errorText) {
        mCallBacks.tellErrorClient_fromCopy(errorText);
        isBusy = false;
        stopSelf();
    }
    private void tellEnd() {
        mCallBacks.tellEndClient_fromCopy();
        isBusy = false;
        stopSelf();
    }
    public void tellProgress(int progressVal, String progressText) {
        mCallBacks.tellProgressClient_fromCopy(progressText,  progressVal);
    }

    //-----------------------------
    private void myLog(String str) { KanLogger.myLog(this.getClass().getName(), str); }
    private void myLogE(String str) { KanLogger.myLogE(this.getClass().getName(), str); }
    //-----------------------------

    public long getFileSize(Uri uri) throws IOException {
        ContentResolver contentResolver = this.getContentResolver();
        ParcelFileDescriptor parcelFileDescriptor = contentResolver.openFileDescriptor(uri, "r");

        if (parcelFileDescriptor != null) {
            FileDescriptor fileDescriptor = parcelFileDescriptor.getFileDescriptor();
            FileInputStream fileInputStream = new FileInputStream(fileDescriptor);
            long size = fileInputStream.getChannel().size();

            if (size > 0) size = size  / 1024 / 1024;

            // Close resources
            fileInputStream.close();
            parcelFileDescriptor.close();

            myLog("parcelFileDescriptor return size : " + size);
            return size;
        } else {
            myLogE("parcelFileDescriptor is null");
        }
       return -3;
    }
}
