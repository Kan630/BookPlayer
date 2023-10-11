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
import java.util.Enumeration;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

public class UnzipService extends Service {

    private final IBinder binder = new UnzipService.UnzipServiceBackgroundBinder();
    Callbacks mCallBacks;

    // Intents
    private String zipFilePath;
    private String destinationFolderPath;

    public interface Callbacks{
        void tellProgressClient_fromUnzip(String progressText, int progressVal);
        void tellErrorClient_fromUnzip(String errorText);
        void tellEndClient_fromUnzip(String destinationFolderPath);
        void tellNonBlockingError(String txt);
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
    }
    /*
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        parseIntent(intent);
        return super.onStartCommand(intent,flags,startId);
        //return START_NOT_STICKY;
    }
     */
    private void parseIntent(Intent intent) {
        zipFilePath = intent.getStringExtra("zipFilePath");
        destinationFolderPath =  intent.getStringExtra("destinationFolderPath");
        myLog("parse intent :   " +
                "\nfrom zipFilePath = [" + zipFilePath + "] " +
                "\nto destinationFolderPath = [" + destinationFolderPath + "]");
    }
    public void init() {
        myLog("init()");
        //-----------------------------
        // le lourd dans une background Thread.... Hyper Important !!
        Thread backgroundThread = new Thread(() -> {
            Boolean ret = unzipZipLocal();
        });
        backgroundThread.start();

    }


    private boolean unzipZipLocal() {
        ////////////////////////////////////////////////////////////////////////////////
        /// getting Args....
        ////////////////////////////////////////////////////////////////////////////////
        myLog("unzipZipLocal()");
        File zipFile = null;
        File unzipFolder = null;
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
        if (zipFile == null || unzipFolder == null) {
            tellError("Bad Arguments");
        }
        ////////////////////////////////////////////////////////////////////////////////
        /// Reading Zip File
        ////////////////////////////////////////////////////////////////////////////////
        tellProgress(0,getResources().getString(R.string.Import_Progress_unzipping_file));
        try {
            ZipInputStream zis = new ZipInputStream(new BufferedInputStream(new FileInputStream(zipFile)));
            myLog("unzipping in : " + unzipFolder);

            // check number of file in zip
            int nbZip;
            try {
                ZipFile zf = new ZipFile(zipFile.getAbsolutePath());
                nbZip = zf.size();
            } catch (Exception e) {
                myLogE("Couln't count element of zip file : " + e.getMessage());
                nbZip = 10;
            }
            myLog("Zip file has : " + nbZip + " entries");
            myLog("---------------------------------------------------------");
            // un pti enum pour verif en log...
            try {
                for (Enumeration<? extends ZipEntry> e = new ZipFile(zipFile).entries(); e.hasMoreElements(); ) {
                    ZipEntry entry = (ZipEntry) e.nextElement();
                    String zeName = entry.getName();
                    if (!entry.isDirectory()) {
                        //zipFileListing.add(zeName);
                        myLog("Enumeration zipFile.entries : [" + zeName + "]");
                    } else {
                        myLog("Enumeration zipFile.entries isDIR : [" + zeName + "]");
                    }
                }
            } catch (Exception e) {
                myLogE("Couln't do enumeration of zip file : " + e.getMessage());
            }

            int numCurZip = 0;
            myLog("---------------------------------------------------------");

            ////////////////////////////////////////////////////////////////////////////////
            /// Looping on Entries
            ////////////////////////////////////////////////////////////////////////////////
            try {
                ZipEntry ze;
                int count;
                byte[] buffer = new byte[8192];
                ze = zis.getNextEntry();

                while (ze != null) {
                    String audioFileName = ze.getName();
                    myLog(String.valueOf(numCurZip+1) + " - Zip entry : " + ze.getName());

                    //bypass if zip contains only folder with same name at first level
                    // // (doublons de dossier enchevetrés)
                    if (ze.isDirectory()) {
                        myLog("ze.isDirectory... goto next record");
                        if (ze.getName().equals(unzipFolder.getName() + "/")) {
                            myLogE("ze.isDirectory and same name !!... ");
                        }
                    } else {
                        //shorter the audio file name if it contains the folder name
                        audioFileName = shortenAudioFileName(audioFileName, unzipFolder.getName());

                        numCurZip = numCurZip + 1;
                        double zeProgress = (double) numCurZip / nbZip * 100;
                        tellProgress((int) zeProgress,
                                getResources().getString(R.string.Import_Progress_unzipping_file) + numCurZip + "/" + nbZip
                                        + "\n" + "\n" + audioFileName);

                        File unzippedAudioFile = new File(unzipFolder, audioFileName);

                        if (!unzipFolder.isDirectory() && !unzipFolder.mkdirs()) {
                            myLogE("Failed to ensure directory: " + unzipFolder.getAbsolutePath());
                            throw new FileNotFoundException("Failed to ensure directory: " + unzipFolder.getAbsolutePath());
                        }
// TODO.... should only unzip actual audio files ... or delete non audio files after unzip

                        FileOutputStream fout = new FileOutputStream(unzippedAudioFile);
                        try {
                            while ((count = zis.read(buffer)) != -1)
                                fout.write(buffer, 0, count);
                        } finally {
                            fout.close();
                        }
                    }
                    // end of loop - get next record
                    ze = null;
                    try {
                        ze = zis.getNextEntry();
                    } catch (Exception e) {
                        myLogE("error getting next zip file entry : " + e.getMessage());
                        mCallBacks.tellNonBlockingError("Error : Zip may be incompletely extracted... " + e.getMessage());
                        e.printStackTrace();
                        try {
                            ze = zis.getNextEntry();
                            if (ze == null) myLog("next next zip file entry is null");
                        } catch (Exception e2) {
                            myLogE("error getting next next zip file entry : " + e2.getMessage());
                        }
                    }
                }
// end loop
            } finally { //of the try of the loop
                myLog("End Zip while loop");
                zis.close();
                if (zipFile.delete()) { // if Exception, the catch delete the all folder...
                    myLog("unzip done in folder, internal zip file deleted");
                } else {
                    myLogE("unzip done in folder, ERROR deleting internal zip file");
                }
            }
            ////////////////////////////////////////////////////////////////////////////////
            ////////////////////////////////////////////////////////////////////////////////

        } catch (Exception e) {
            myLogE(getResources().getString(R.string.Error_Import_UnableToUnzip_line1) + " : " + e.getMessage());
            tellError(getResources().getString(R.string.Error_Import_UnableToUnzip_line1) + " : " + e.getMessage()
                    + "\n" + "\n" + getResources().getString(R.string.Error_Import_UnableToUnzip_line2));
            e.printStackTrace();
            //delete files after error
            if (!(recursiveRemove(unzipFolder))) {
                myLogE("killLocalUnzipFolder, AfterError, recursiveRemove(unzipFolder) KO");
            }

            return false;
        }
        myLog("file has been unzipped");
        tellEnd(destinationFolderPath);
        return true;
    }

    private String shortenAudioFileName(String audioFileName, String folderName) {
        String tmp = audioFileName;
        if (tmp.toLowerCase(Locale.ROOT).startsWith(folderName.toLowerCase(Locale.ROOT))) {
            tmp = tmp.substring((folderName).length());
        }
        if (tmp.startsWith("/") || tmp.startsWith("\\")) {
            tmp = tmp.substring(1);
        } // a second time, needed sometimes...
        if (tmp.toLowerCase(Locale.ROOT).startsWith(folderName.toLowerCase(Locale.ROOT))) {
            tmp = tmp.substring((folderName).length());
        }
        tmp = tmp.replace("\\","_");
        tmp = tmp.replace("/","_");
        if (tmp.startsWith("_") || tmp.startsWith(" ")) {
            tmp = tmp.substring(1);
        }
        if (tmp.length() < 2 ) {
            tmp = audioFileName;
        }
        //// tell result
        if (!tmp.equals(audioFileName)) {
            myLog("name shortened : [" + tmp + "] => [" + audioFileName + "]");
        }
        return tmp;
    }


    //////////////////////////////////////////////////////////////////////////////////////////
    // Callbacks
    //////////////////////////////////////////////////////////////////////////////////////////
    private void tellError(String errorText) {
        mCallBacks.tellErrorClient_fromUnzip(errorText);
        myLogE(errorText);
        myLog("killing Service");
        stopSelf();
    }
    private void tellEnd(String destinationFolderPath) {
        mCallBacks.tellEndClient_fromUnzip(destinationFolderPath);
        myLog("killing Service");
        stopSelf();
    }
    public void tellProgress(int progressVal, String progressText) {
        mCallBacks.tellProgressClient_fromUnzip(progressText, progressVal);
    }
    //////////////////////////////////////////////////////////////////////////////////////////
    private void myLog(String str) { KanLogger.myLog(this.getClass().getName(), str); }
    private void myLogE(String str) { KanLogger.myLogE(this.getClass().getName(), str); }

}
