package com.driot.bookplayer.services;

import static com.driot.bookplayer.global.Var.ONLY_MIME_AUDIO;
import static com.driot.bookplayer.global.Var.SUPPORTED_AUDIO_EXTENSIONS;
import static com.driot.bookplayer.global.Var.SUPPORTED_COVER_PICTURE_EXTENSIONS;
import static com.driot.bookplayer.utils.Tonio.getExtension;
import static com.driot.bookplayer.utils.Tonio.getMimeType;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.WorkerParameters;

import com.driot.bookplayer.R;
import com.driot.bookplayer.global.Pref;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.helpers.FileHelper;
import com.driot.bookplayer.objects.LoadBookTaskState;
import com.driot.bookplayer.objects.TaskStateManager;
import com.driot.bookplayer.utils.log.LoggingWorker;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.Locale;
import java.util.zip.*;

public class UnzipWorker extends LoggingWorker {

    private static final String TASK_NAME = Var.WORKER_TASK_LABEL_UNZIP;


    public UnzipWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();

        LoadBookTaskState bookState = Pref.getLoadBookTaskState();
        if (bookState == null) {
            TaskStateManager.markTaskFailed(TASK_NAME, "bookState == null");
            return Result.failure();
        }

        String zipFilePath = bookState.dynamicSourceFilePath;
        String destinationFolderPath = bookState.futureFolderPath;
        myLog("From: " + zipFilePath);
        myLog("To: " + destinationFolderPath);

        if (zipFilePath == null || destinationFolderPath == null) {
            TaskStateManager.markTaskFailed(TASK_NAME, "Missing input data");
            return Result.failure();
        }

        File zipFile = new File(zipFilePath);
        File unzipFolder = new File(destinationFolderPath);

        if (!zipFile.exists()) {
            TaskStateManager.markTaskFailed(TASK_NAME, "Zip file not found");
            return Result.failure();
        }

        if (!unzipFolder.exists() && !unzipFolder.mkdirs()) {
            TaskStateManager.markTaskFailed(TASK_NAME, "Could not create destination folder");
            return Result.failure();
        }

        try {
            myLog("unzipping in: " + unzipFolder);
            int nbZip;

            try (ZipFile zf = new ZipFile(zipFile)) {
                nbZip = zf.size();
            } catch (Exception e) {
                myLogEE(e, "Could not count zip entries");
                nbZip = 10;
            }

            int numCurZip = 0;
            Charset charset = getCharset(zipFile);
            if (charset == null) charset = Charset.defaultCharset();

            try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(new FileInputStream(zipFile)), charset)) {
                ZipEntry ze;
                byte[] buffer = new byte[8192];

                while ((ze = zis.getNextEntry()) != null) {
                    if (isStopped()) {
                        TaskStateManager.markTaskCancelled(TASK_NAME);
                        return Result.failure();
                    }

                    if (ze.isDirectory()) continue;

                    String audioFileName = shortenAudioFileName(ze.getName(), unzipFolder.getName());

                    numCurZip++;
                    int progress = (int) ((double) numCurZip / nbZip * 100);
                    String progressText = getApplicationContext().getString(R.string.Import_Progress_unzipping_file) + numCurZip + "/" + nbZip + "\n" + audioFileName;

                    TaskStateManager.tellProgress(TASK_NAME, progress, progressText);

                    File unzippedFile = new File(unzipFolder, audioFileName);
                    if (!(unzippedFile.getParentFile()==null) && !unzippedFile.getParentFile().exists() && !unzippedFile.getParentFile().mkdirs()) {
                        TaskStateManager.markTaskFailed(TASK_NAME, "Failed to create output dir: " + unzippedFile);
                        return Result.failure();
                    }

                    try (FileOutputStream fout = new FileOutputStream(unzippedFile)) {
                        int count;
                        while ((count = zis.read(buffer)) != -1) {
                            if (isStopped()) {
                                TaskStateManager.markTaskCancelled(TASK_NAME);
                                return Result.failure();
                            }
                            fout.write(buffer, 0, count);
                        }
                    }
                }
            }

            if (!zipFile.delete()) myLogEE(null,"Error deleting internal zip file");

            // Clean non-audio/image
            for (File f : unzipFolder.listFiles()) {
                String mime = getMimeType(f);
                String ext = getExtension(f.getName());
                if (!(mime.startsWith(ONLY_MIME_AUDIO) || SUPPORTED_AUDIO_EXTENSIONS.contains(ext) || SUPPORTED_COVER_PICTURE_EXTENSIONS.contains(ext))) {
                    if (!f.delete()) myLogE("Could not delete non-audio/image: " + f.getName());
                }
            }

            TaskStateManager.markUnzipCompleted(TASK_NAME, destinationFolderPath);
            return Result.success();

        } catch (Exception e) {
            TaskStateManager.markTaskFailed(TASK_NAME, e.getMessage());
            FileHelper.recursiveRemove(unzipFolder);
            return Result.failure();
        }
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


}
