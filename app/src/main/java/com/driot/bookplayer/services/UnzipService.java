package com.driot.bookplayer.services;


import static com.driot.bookplayer.global.Var.ONLY_MIME_AUDIO;
import static com.driot.bookplayer.global.Var.SUPPORTED_AUDIO_EXTENSIONS;
import static com.driot.bookplayer.utils.Tonio.getExtension;
import static com.driot.bookplayer.utils.Tonio.getMimeType;
import static com.driot.bookplayer.utils.Utils.recursiveRemove;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;

import androidx.annotation.Nullable;

import com.driot.bookplayer.R;
import com.driot.bookplayer.utils.log.LoggingService;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

public class UnzipService extends LoggingService {

    public static volatile boolean isUnzipRunning = false;

    private final IBinder binder = new UnzipService.UnzipServiceBackgroundBinder();
    Callbacks mCallBacks;
    Thread backgroundThread;

    // Intent Extras
    private String zipFilePath;
    private String destinationFolderPath;

    public interface Callbacks{
        void unzipService_tellProgress(String progressText, int progressVal);
        void unzipService_tellError(String errorText);
        void unzipService_tellEnd(String destinationFolderPath);
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
    private void parseIntent(Intent intent) {
        zipFilePath = intent.getStringExtra("zipFilePath");
        destinationFolderPath =  intent.getStringExtra("destinationFolderPath");
        if (zipFilePath==null || destinationFolderPath==null) {
            tellError("Wrong arguments for Unzip Service");
        }
        myLog("parse intent :   " +
                "\nfrom zipFilePath = [" + zipFilePath + "] " +
                "\nto destinationFolderPath = [" + destinationFolderPath + "]");
    }
    public void init() {
        myLog("init()");
        isUnzipRunning = true;
        //-----------------------------
        // le lourd dans une background Thread.... Hyper Important !! // TODO how to kill such thread in cae of error ?
        backgroundThread = new Thread(() -> {
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
            myLog("unzipping in : " + unzipFolder);


            // check number of file in zip
            int nbZip;
            try (ZipFile zf = new ZipFile(zipFile.getAbsolutePath())) {
                nbZip = zf.size();
            } catch (Exception e) {
                myLogEE(e,"Could not count element of zip file");
                nbZip = 10;
            }

            myLog("Zip file has : " + nbZip + " entries");
            myLog("---------------------------------------------------------");

            ////////////////////////////////////////////////////////////////////////////////
            /// Looping on Entries
            ////////////////////////////////////////////////////////////////////////////////
            int numCurZip = 0;
            Charset charset = getCharset(zipFile);
            if (charset == null) { charset = Charset.defaultCharset(); }
            myLog("---------------------------------------------------------");
            try (ZipInputStream zis = new ZipInputStream(
                    new BufferedInputStream(new FileInputStream(zipFile)), charset)) {

                ZipEntry ze;
                int count;
                byte[] buffer = new byte[8192];
                ze = zis.getNextEntry();

                while (ze != null) {
                    if (!UnzipService.isUnzipRunning) {
                        myLog("Unzip canceled during entry loop");
                        throw new InterruptedException("Unzip canceled");
                    }

                    String audioFileName = ze.getName();
                    myLog(String.valueOf(numCurZip+1) + " - Zip entry : " + ze.getName());

                    //bypass if zip contains only folder with same name at first level
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
                            while ((count = zis.read(buffer)) != -1) {
                                if (!UnzipService.isUnzipRunning) {
                                    myLog("Unzip canceled during stream write");
                                    throw new InterruptedException("Unzip canceled");
                                }
                                fout.write(buffer, 0, count);
                            }
                        } catch (InterruptedException e) {
                            myLogE("Unzip was canceled");
                            tellError("Unzip canceled.");
                            return false;
                        } finally {
                            fout.close();
                        }
                    }
                    // end of loop - get next record
                    ze = null;
                    try {
                        ze = zis.getNextEntry();
                    } catch (Exception e) {
                        myLogEE(e,"error getting next zip file entry");
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
                myLog("End Zip while loop");
            } catch (Exception e) {
                myLogEE(e, "ZipInputStream error");
            } finally { //of the try of the loop
                if (zipFile.delete()) { // if Exception, the catch delete the all folder...
                    myLog("unzip done in folder, internal zip file deleted");
                } else {
                    myLogE("unzip done in folder, ERROR deleting internal zip file");
                }
            }
            ////////////////////////////////////////////////////////////////////////////////
            ////////////////////////////////////////////////////////////////////////////////

        } catch (Exception e) {
            myLogEE(e,"Error unzipping compressed file");
            tellError(getResources().getString(R.string.Error_Import_UnableToUnzip_line1) + " : " + e.getMessage()
                    + "\n" + "\n" + getResources().getString(R.string.Error_Import_UnableToUnzip_line2));
            e.printStackTrace();
            //delete files after error
            if (!(recursiveRemove(unzipFolder))) {
                myLogEE(null,"killLocalUnzipFolder, AfterError, recursiveRemove(unzipFolder) KO");
            }

            return false;
        }
        myLog("file has been unzipped");

        // Lets delete non audio files
        try {
            for (File f : new File(destinationFolderPath).listFiles()) {
                String mime = getMimeType(f);
                String fileExtension = getExtension(f.getName());
                if (!(mime.startsWith(ONLY_MIME_AUDIO) || SUPPORTED_AUDIO_EXTENSIONS.contains(fileExtension))) {
                    if (f.delete()) {
                        myLog("deleting non audio file [" + f.getName() + "] - [" + mime + "]");
                    } else {
                        myLogE("error deleting non audio file " + f.getName());
                    }
                }
            }
        } catch (Exception e) {
            myLogEE(e,"error getting MIME and deleting non audio files");
        }
        myLog("unzipped folder has been pruned of non audio files");
        tellEnd(destinationFolderPath);
        return true;
    }

    private String shortenAudioFileName(String audioFileName, String folderName) {
        String tmp = audioFileName;
        //tmp = Paths.get(tmp).normalize().toString();
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
        if (tmp.length() < 5 ) {
            tmp = audioFileName;
        }
        //// tell result
        if (!tmp.equals(audioFileName)) {
            myLog("name shortened : [" + tmp + "] => [" + audioFileName + "]");
        }
        return tmp;
    }

    private Charset getCharset(File zipFile) {
        Charset charset;
        charset = Charset.forName("CP437"); //=IBM437
        if (checkCharset(zipFile, charset)) { return charset; }
        charset = Charset.forName("IBM850");
        if (checkCharset(zipFile, charset)) { return charset; }
        charset = StandardCharsets.UTF_8;
        if (checkCharset(zipFile, charset)) { return charset; }
        charset = StandardCharsets.ISO_8859_1;
        if (checkCharset(zipFile, charset)) { return charset; }
        charset = StandardCharsets.US_ASCII;
        if (checkCharset(zipFile, charset)) { return charset; }
        charset = StandardCharsets.UTF_16;
        if (checkCharset(zipFile, charset)) { return charset; }
        charset = StandardCharsets.UTF_16BE;
        if (checkCharset(zipFile, charset)) { return charset; }
        charset = StandardCharsets.UTF_16LE;
        if (checkCharset(zipFile, charset)) { return charset; }
        charset = Charset.defaultCharset();
        if (checkCharset(zipFile, charset)) { return charset; }
        myLogE("No correct charset found for zipFile");
        return null;
    }

    private boolean checkCharset(File zipFile, Charset charset) {
        int i = 1;
        try (ZipFile zf = new ZipFile(zipFile, charset)) {
            for (Enumeration<? extends ZipEntry> e = zf.entries(); e.hasMoreElements(); ) {
                ZipEntry entry = e.nextElement();
                i = i + 1;
            }
            myLog("Charset found : [" + charset.toString() + "]");
            return true;
        } catch (Exception e) {
            myLog("Charset tested : [" + charset.toString() + "] => KO after " + i + " entries.");
            return false;
        }
    }


    //////////////////////////////////////////////////////////////////////////////////////////
    // Callbacks
    //////////////////////////////////////////////////////////////////////////////////////////
    private void tellError(String errorText) {
        myLogE(errorText);
        if (mCallBacks != null) {
            mCallBacks.unzipService_tellError(errorText);
        }
        myLog("killing Service");
        if (backgroundThread != null && backgroundThread.isAlive()) {
            backgroundThread.interrupt();
        }
        stopSelf();
        isUnzipRunning = false;
    }
    private void tellEnd(String destinationFolderPath) {
        mCallBacks.unzipService_tellEnd(destinationFolderPath);
        myLog("killing Service");
        stopSelf();
        isUnzipRunning = false;
    }
    public void tellProgress(int progressVal, String progressText) {
        mCallBacks.unzipService_tellProgress(progressText, progressVal);
    }

}
