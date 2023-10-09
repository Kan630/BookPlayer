package com.driot.bookplayer.utils;


import static com.driot.bookplayer.utils.Utils.recursiveRemove;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;

import androidx.annotation.Nullable;

import com.driot.bookplayer.R;
import com.driot.tonylib.KanLogger;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.security.spec.ECField;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

public class UnzipService extends Service {

    private final IBinder binder = new UnzipService.UnzipServiceBackgroundBinder();
    Callbacks mCallBacks;

    private String zipFilePath;
    private String destinationFolderPath;

    private File zipFile;
    private File unzipFolder;


    public interface Callbacks{
        void tellProgressClient_fromUnzip(String progressText, int progressVal);
        void tellErrorClient_fromUnzip(String errorText);
        void tellEndClient_fromUnzip();
    }
    public void registerClient(Service service){
        this.mCallBacks = (UnzipService.Callbacks)service;
    }

    public class UnzipServiceBackgroundBinder extends Binder {
        public UnzipService getService() { return UnzipService.this; }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        myLog("onBind()    intent:" + intent.getDataString());
        return binder;
    }
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        zipFilePath = intent.getStringExtra("zipFilePath");
        destinationFolderPath =  intent.getStringExtra("destinationFolderPath");
        myLog("onStartCommand() ..   \nfrom zipFilePath=[" + zipFilePath + "] \nto destinationFolderPath=[" + destinationFolderPath + "]");
        return super.onStartCommand(intent,flags,startId);
        //return START_NOT_STICKY;
    }
    public void init() {
        myLog("init()");
        //-----------------------------
        try {
            unzipFolder = new File(destinationFolderPath);
        } catch (Exception e) {
            tellError("Bad Destination Folder Path - " + e.getMessage());
        }
        try {
            zipFile = new File(zipFilePath);
        } catch (Exception e) {
            tellError("Bad Zip File Path - " + e.getMessage());
        }

        // le lourd dans une background Thread.... Hyper Important !!
        Thread backgroundThread = new Thread(new Runnable() {
            @Override
            public void run() {
                Boolean ret = unzipZipLocal();
            }
        });
        backgroundThread.start();

    }


    private boolean unzipZipLocal() {
        ////////////////////////////////////////////////////////////////////////////////
        /// unzipping....
        ////////////////////////////////////////////////////////////////////////////////
        myLog("unzipZipLocal()");
        tellProgress(0,getResources().getString(R.string.Import_Progress_unzipping_file));
        //unzip(externalZipFile[0], folder[0]);
        try {
            ZipInputStream zis = new ZipInputStream(new BufferedInputStream(new FileInputStream(zipFile)));
            myLog("unzipping in : " + unzipFolder);

            // check number of file in zip
            int nbZip;
            try {
                ZipFile zf = new ZipFile(zipFile.getAbsolutePath());
                nbZip = zf.size();
            } catch (Exception e) {
                myLogE("Couln't count element of zip file");
                nbZip = 10;
            }
            myLog("Zip file has : " + nbZip + " entries");

            int numCurZip = 0;

            try {
                ZipEntry ze;
                int count;
                byte[] buffer = new byte[8192];
                ze = zis.getNextEntry();

                while (ze != null) {
                    myLog("unzipping : " + ze.getName());

                    //bypass if zip contains only folder with same name at first level (doublons de dossier enchevetrés)
                    if (ze.getName().equals(unzipFolder.getName() + "/")) {
                        unzipFolder = new File(unzipFolder.getParent()); //attention faut le remettre à unzipFolder = new File(destinationFolder); après
                        myLog("unzipping : bypassing first directory");

                    } else {
                        numCurZip = numCurZip + 1;
                        double zeProgress = (double) numCurZip / nbZip * 100;
                        tellProgress((int) zeProgress,
                                getResources().getString(R.string.Import_Progress_unzipping_file) + numCurZip + "/" + nbZip
                                        + "\n" + "\n" + ze.getName());

                        File file = new File(unzipFolder, ze.getName());
                        //File dir = ze.isDirectory() ? file : file.getParentFile();
                        File dir;
                        if (ze.isDirectory()) {
                            dir = file;
                        } else {
                            dir = file.getParentFile();
                            myLog("unzipping : get parent file for dir");
                        }

                        if (dir != null && !dir.isDirectory() && !dir.mkdirs()) {
                            myLogE("Failed to ensure directory: " + dir.getAbsolutePath());
                            throw new FileNotFoundException("Failed to ensure directory: " + dir.getAbsolutePath());
                        }
                        if (ze.isDirectory()) {
                            myLog("ze.isDirectory... continue");
                            continue;
                        }


                        FileOutputStream fout = new FileOutputStream(file);
                        try {
                            while ((count = zis.read(buffer)) != -1)
                                fout.write(buffer, 0, count);
                        } finally {
                            fout.close();
                        }

                    }
        /* if time should be restored as well
        long time = ze.getTime();
        if (time > 0)
            file.setLastModified(time);
        */
                    ze = null;
                    try {
                        ze = zis.getNextEntry();
                    } catch (Exception e) {
                        myLogE("error getting next zip file entry : " + e.getMessage());
                        e.printStackTrace();
                        ze = zis.getNextEntry();
                        continue;
                    }
                } // end du while
            } finally {
                myLog("End Zip while loop");
                zis.close();
                //unzipFolder = new File(destinationFolder); // on reaffecte a la bonne valeur
            }
            ////////////////////////////////////////////////////////////////////////////////
            ////////////////////////////////////////////////////////////////////////////////

        } catch (Exception e) {
            myLogE(getResources().getString(R.string.Error_Import_UnableToUnzip_line1) + " : " + e.getMessage());
            tellError(getResources().getString(R.string.Error_Import_UnableToUnzip_line1) + " : " + e.getMessage()
                    + "\n" + "\n" + getResources().getString(R.string.Error_Import_UnableToUnzip_line2));
            e.printStackTrace();
            killLocalUnzipFolder(); //delete files after error
            return false;
        } finally {
            if (zipFile.delete()) {
                myLog("unzip done in folder, internal zip file deleted");
            } else {
                myLogE("unzip done in folder, ERROR deleting internal zip file");
            }
        }
        myLog("file has been unzipped");
        tellEnd();
        return true;
    }

    private void killLocalUnzipFolder() {
        if (!(recursiveRemove(unzipFolder))) {
            myLogE("killLocalUnzipFolder, AfterError, recursiveRemove(unzipFolder) KO");
        }
    }

    private void killLocalZipFile() {
        if (!zipFile.delete()) {
            myLogE("killLocalZipFile, AfterError");
        }
    }

    private void tellError(String errorText) {
        mCallBacks.tellErrorClient_fromUnzip(errorText);
        myLog("killing Service");
        stopSelf();
    }
    private void tellEnd() {
        mCallBacks.tellEndClient_fromUnzip();
        myLog("killing Service");
        stopSelf();
    }
    public void tellProgress(int progressVal, String progressText) {
        mCallBacks.tellProgressClient_fromUnzip(progressText, progressVal);
    }
    private void myLog(String str) { KanLogger.myLog(this.getClass().getName(), str); }
    private void myLogE(String str) { KanLogger.myLogE(this.getClass().getName(), str); }
    // Callbacks
}
