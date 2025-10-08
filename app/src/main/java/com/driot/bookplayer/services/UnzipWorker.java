package com.driot.bookplayer.services;

import static com.driot.bookplayer.global.Var.ONLY_MIME_AUDIO;
import static com.driot.bookplayer.global.Var.SUPPORTED_AUDIO_EXTENSIONS;
import static com.driot.bookplayer.global.Var.SUPPORTED_COVER_PICTURE_EXTENSIONS;
import static com.driot.bookplayer.utils.Tonio.getExtension;
import static com.driot.bookplayer.utils.Tonio.getMimeType;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.work.WorkerParameters;

import com.driot.bookplayer.R;
import com.driot.bookplayer.global.Pref;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.helpers.FileHelper;
import com.driot.bookplayer.helpers.FirebaseAnalyticsHelper;
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
            TaskStateManager.markTaskFailed(TASK_NAME, "bookState == null", getApplicationContext().getString(R.string.invalid_resource));
            return Result.failure();
        }

        String zipFilePath = bookState.dynamicSourceFilePath;
        String destinationFolderPath = bookState.futureFolderPath;
        myLogD("----------------------------------------------------");
        myLog("From: " + zipFilePath);
        myLog("To: " + destinationFolderPath);
        myLogD("----------------------------------------------------");

        if (zipFilePath == null || destinationFolderPath == null) {
            TaskStateManager.markTaskFailed(TASK_NAME, "Missing input data", getApplicationContext().getString(R.string.invalid_resource));
            return Result.failure();
        }

        File zipFile = new File(zipFilePath);
        File unzipFolder = new File(destinationFolderPath);

        if (!zipFile.exists()) {
            TaskStateManager.markTaskFailed(TASK_NAME, "Zip file not found", getApplicationContext().getString(R.string.invalid_resource));
            return Result.failure();
        }

        if (!unzipFolder.exists() && !unzipFolder.mkdirs()) {
            TaskStateManager.markTaskFailed(TASK_NAME, "Could not create destination folder", getApplicationContext().getString(R.string.failed_to_create_destination_folder));
            return Result.failure();
        }

        FirebaseAnalyticsHelper.logEvent("zip_worker");

        try {
            myLogD("unzipping in: " + unzipFolder);
            int nbZip;
            myLogD("---------------------------------------------------------");
            myLogD(unzipFolder.getName());
            myLogD("---------------------------------------------------------");
            ////////////////////////////////////////////////////////////////////////////////
            /// Reading Zip File
            ////////////////////////////////////////////////////////////////////////////////
            try (ZipFile zf = new ZipFile(zipFile)) {
                nbZip = zf.size();
            } catch (Exception e) {
                myLogEE(e, "Could not count zip entries");
                nbZip = 10;
            }
            myLogD("Zip file has : " + nbZip + " entries");
            myLogD("---------------------------------------------------------");

            int numCurZip = 0;
            Charset charset = detectZipCharset(zipFile);
            if (charset == null) charset = StandardCharsets.UTF_8;
            myLogD("---------------------------------------------------------");

            ////////////////////////////////////////////////////////////////////////////////
            /// Looping on Entries
            ////////////////////////////////////////////////////////////////////////////////
            try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(new FileInputStream(zipFile)), charset)) {
                ZipEntry ze;
                byte[] buffer = new byte[8192];
                ze = zis.getNextEntry();

                while (ze != null) {
                    if (isStopped()) {
                        TaskStateManager.markTaskCancelled(TASK_NAME);
                        return Result.failure();
                    }

                    myLog(String.valueOf(numCurZip+1) + " - Zip entry : " + ze.getName());

                    //bypass if zip contains only folder with same name at first level
                    if (ze.isDirectory()) {
                        myLog("ze.isDirectory... goto next record");
                        if (ze.getName().equals(unzipFolder.getName() + "/")) {
                            myLogE("ze.isDirectory and same name !!... ");
                        }
                    } else {
                        String audioFileName = shortenAudioFileName(ze.getName(), unzipFolder.getName());

                        numCurZip++;
                        int progress = (int) ((double) numCurZip / nbZip * 100);
                        String progressText = getApplicationContext().getString(R.string.Import_Progress_unzipping_file) + numCurZip + "/" + nbZip + "\n" + audioFileName;

                        TaskStateManager.tellProgress(TASK_NAME, progress, progressText);

                        File unzippedFile = new File(unzipFolder, audioFileName);
                        if (!(unzippedFile.getParentFile() == null) && !unzippedFile.getParentFile().exists() && !unzippedFile.getParentFile().mkdirs()) {
                            TaskStateManager.markTaskFailed(TASK_NAME, "Failed to create output dir: " + unzippedFile, getApplicationContext().getString(R.string.failed_to_create_destination_folder));
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
// end of loop - get next record
                    ze = null;
                    try {
                        ze = zis.getNextEntry();
                    } catch (Exception e) {
                        myLogE("error getting next zip file entry : " + e.getMessage());
                        try {
                            ze = zis.getNextEntry();
                            if (ze == null) myLog("next next zip file entry is null");
                        } catch (Exception e2) {
                            myLogE("error getting next next zip file entry : " + e2.getMessage());
                        }
                    }
                } //end while
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
            String msg = (e.getMessage() != null ? e.getMessage() : "");
            myLogE("main catch : " + msg);

            // --- Check for ENOSPC ("no space left on device") ---
            boolean noSpace = msg.contains("ENOSPC")
                    || msg.contains("No space left on device")
                    || (e.getCause() != null && String.valueOf(e.getCause().getMessage()).contains("ENOSPC"));

            if (noSpace) {
                myLogEE(e, "splitM4bLocal - disk full (ENOSPC)");
                String userMsg = context.getString(R.string.error_no_space_left)
                        + "\n\n" + context.getString(R.string.solution_free_space);
                TaskStateManager.tellWarning(userMsg);
                TaskStateManager.markTaskFailed(TASK_NAME, "No space left on device", context.getString(R.string.error_no_space_left));
            } else {
                TaskStateManager.markTaskFailed(TASK_NAME, e.getMessage(), null);
            }
            FileHelper.recursiveRemove(unzipFolder);
            return Result.failure();
        }
    }

    private static final Charset[] ZIP_CHARSET_CANDIDATES = new Charset[] {
            StandardCharsets.UTF_8,
            Charset.forName("CP437"),          // PKZIP default if EFS not set
            Charset.forName("windows-1252"),   // very common on legacy zips
            StandardCharsets.ISO_8859_1,
            Charset.forName("IBM850"),       //added by kan (here and below)
            StandardCharsets.US_ASCII,
            StandardCharsets.UTF_16,
            StandardCharsets.UTF_16BE,
            StandardCharsets.UTF_16LE,
            Charset.defaultCharset()
    };


    @Nullable
    private Charset detectZipCharset(File zipFile) {
        Charset best = null;
        double bestScore = Double.NEGATIVE_INFINITY;

        for (Charset cs : ZIP_CHARSET_CANDIDATES) {
            double score = scoreZipNames(zipFile, cs);
            myLogD("Charset score " + cs + " = " + score);
            if (score > bestScore) { bestScore = score; best = cs; }
        }
        if (best == null) {
            myLogEE(null, "No charset scored > -Inf, using default");
            return Charset.defaultCharset();
        }
        myLog("Chosen charset: " + best);
        return best;
    }

    private double scoreZipNames(File zip, Charset cs) {
        int names = 0;
        int goodChars = 0;
        int badChars = 0;
        int suspicious = 0;

        try (ZipFile zf = new ZipFile(zip, cs)) {
            Enumeration<? extends ZipEntry> it = zf.entries();
            while (it.hasMoreElements()) {
                ZipEntry e = it.nextElement();
                String name = e.getName();
                names++;

                for (int i = 0; i < name.length(); i++) {
                    char c = name.charAt(i);
                    if (c == '\uFFFD') { badChars++; continue; } // replacement char
                    if (Character.isISOControl(c) && c != '/' && c != '\\') { badChars++; continue; }
                    if (c >= 0x2500 && c <= 0x257F) { suspicious++; continue; } // box-drawing etc.
                    // treat letters/digits/basic punct/space as good
                    if (Character.isLetterOrDigit(c) || " .-_()+[]{}'.,".indexOf(c) >= 0 || c=='/' || c=='\\') {
                        goodChars++;
                    } else {
                        // rare symbols count slightly against
                        suspicious++;
                    }
                }
            }
        } catch (Exception ex) {
            // strong penalty if we can’t even iterate
            myLog("Charset " + cs + " failed during listing");
            return -1_000_000;
        }

        if (names == 0) return -1; // empty zip: meh

        // Weighted score: maximize good, minimize bad/suspicious.
        return goodChars - (4.0 * badChars) - (0.5 * suspicious);
    }

    private String shortenAudioFileName(String audioFileName, String folderName) {
        String tmp = audioFileName;

        // Normalize names for comparison
        String folderNorm = normalizeName(folderName);
        String tmpNorm = normalizeName(tmp);

        // Try to remove folder name prefix
        if (tmpNorm.startsWith(folderNorm)) {
            tmp = tmp.substring(folderName.length());
        }

        // Remove leading slashes
        if (tmp.startsWith("/") || tmp.startsWith("\\")) {
            tmp = tmp.substring(1);
        }

        // Repeat check in case of residual
        tmpNorm = normalizeName(tmp);
        if (tmpNorm.startsWith(folderNorm)) {
            tmp = tmp.substring(folderName.length());
        }

        // Sanitize slashes to underscores
        tmp = tmp.replace("\\", "_").replace("/", "_");

        // Remove leading underscores or spaces
        while (tmp.startsWith("_") || tmp.startsWith(" ")) {
            tmp = tmp.substring(1);
        }

        // Fallback to original if it's too short
        if (tmp.length() < 5) {
            tmp = audioFileName;
        }

        // Logging
        if (!tmp.equals(audioFileName)) {
            myLogD("name shortened = [" + tmp + "] .   was [" + audioFileName + "]");
        }

        return tmp;
    }

    // Helper: normalize underscores and case for comparison
    private String normalizeName(String s) {
        return s.toLowerCase(Locale.ROOT)
                .replace("_", " ")
                .replaceAll("[\\\\/]", " ") // slashes to space for matching
                .trim();
    }



}
