package com.driot.bookplayer.services;

import android.content.Intent;
import android.net.Uri;
import android.os.IBinder;
import android.os.Parcelable;

import androidx.core.content.FileProvider;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.driot.bookplayer.BuildConfig;
import com.driot.bookplayer.R;
import com.driot.bookplayer.activities.ExportActivity;
import com.driot.bookplayer.db.Folder;
import com.driot.bookplayer.global.Intents;
import com.driot.bookplayer.utils.Tonio;
import com.driot.bookplayer.utils.log.LoggingService;

import com.driot.bookplayer.helpers.UriHelper;
import com.driot.bookplayer.services.ExportService;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream; // Keep for legacy
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import androidx.documentfile.provider.DocumentFile;

public class ExportService extends LoggingService {

    private int totalFiles = 0;
    private long totalSize = 0;

    private long lastUpdateTime = 0;
    private static final long MIN_UPDATE_INTERVAL = 100; // ms

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Folder folder = intent.getParcelableExtra(Intents.EXTRA_BOOK_SOURCE_FOLDER);
        if (folder == null) {
            myLogEE(null, "folder is null");
            stopSelf();
            return START_NOT_STICKY;
        }
        String destFileFullPath = intent.getStringExtra(ExportActivity.EXTRA_DEST_FILE_FULL_PATH); // legacy path
        String destUriStr = intent.getStringExtra(ExportActivity.EXTRA_DEST_URI); // SAF Uri (preferred)

        myLogD("------------------------------------------------------------------------------------------------");
        myLog("folderPath: " + folder.getName());
        myLog("folderPath: " + folder.getPath());
        myLog("destFileFullPath: " + destFileFullPath);
        myLog("destUriStr: " + destUriStr);
        myLogD("------------------------------------------------------------------------------------------------");

        if (folder.getPath() != null) {
            new Thread(() -> zipFolder(folder, destFileFullPath, destUriStr)).start();
        } else {
            myLogEE(null, "path is null");
        }
        return START_NOT_STICKY;
    }

    private void zipFolder(Folder folder, String destFileFullPath, String destUriStr) {
        String folderPath = folder.getPath();
        Uri folderUri = Uri.parse(folderPath);

        // abstraction for "files to zip"
        List<Uri> filesToZip = new ArrayList<>();
        List<String> fileNames = new ArrayList<>();
        List<Long> fileSizes = new ArrayList<>();

        // 1. Resolve source files
        if (UriHelper.isFolder(this, folderUri)) {
            // Is it a SAF folder or File folder?
            DocumentFile docFolder = UriHelper.getDocumentFileFromAnyUri(this, folderUri);
            if (docFolder != null && docFolder.exists()) {
                // SAF or mixed
                DocumentFile[] docs = docFolder.listFiles();
                for (DocumentFile doc : docs) {
                    if (doc.isFile()) {
                        filesToZip.add(doc.getUri());
                        fileNames.add(doc.getName());
                        fileSizes.add(doc.length());
                    }
                }
            } else {
                // Fallback legacy "File" check if UriHelper said true but docFolder failed
                // (unlikely)
                File fileFolder = new File(folderPath);
                if (fileFolder.exists() && fileFolder.isDirectory()) {
                    File[] audioFiles = fileFolder.listFiles(File::isFile);
                    if (audioFiles != null) {
                        for (File f : audioFiles) {
                            filesToZip.add(Uri.fromFile(f));
                            fileNames.add(f.getName());
                            fileSizes.add(f.length());
                        }
                    }
                }
            }
        } else {
            // Check if it was just a raw path that needed file://
            File fileFolder = new File(folderPath);
            if (fileFolder.exists() && fileFolder.isDirectory()) {
                File[] audioFiles = fileFolder.listFiles(File::isFile);
                if (audioFiles != null) {
                    for (File f : audioFiles) {
                        filesToZip.add(Uri.fromFile(f));
                        fileNames.add(f.getName());
                        fileSizes.add(f.length());
                    }
                }
            } else {
                myLogEE(null, "Export aborted: invalid folderPath: " + folderPath);
                sendFail("invalid folder path: " + folderPath);
                return;
            }
        }

        if (filesToZip.isEmpty()) {
            myLogEE(null, "no audio file found in folder");
            sendFail("no audio file found in folder");
            return;
        }

        totalFiles = filesToZip.size();
        totalSize = 0L;
        for (Long s : fileSizes)
            totalSize += s;

        myLog("total audio size = " + Tonio.getReadableSize(totalSize));

        // Image handling (legacy path string in DB?)
        String pathImage = folder.image;
        if (pathImage != null && !pathImage.isEmpty()) {
            // Try to resolve image
            Uri imageUri = UriHelper.resolveUriFromPath(this, pathImage);
            if (imageUri != null) {
                // We need a name and size.
                // For simplicity, let's try to get them.
                long iSize = UriHelper.getSize(this, imageUri);
                String iName = Tonio.getFileNameFromPath(pathImage); // fallback name
                // if SAF, maybe query name? simpler to trust generic helper or just use
                // filename
                filesToZip.add(imageUri);
                fileNames.add(iName);
                fileSizes.add(iSize);
                totalSize += iSize;
                totalFiles++;
                myLog("image added: " + iName);
            }
        }

        boolean useSaf = (destUriStr != null);
        Uri safDestUri = null;
        File outputFile = null;

        // Prepare output stream (SAF or legacy path)
        try {
            OutputStream rawOut;
            if (useSaf) {
                safDestUri = Uri.parse(destUriStr);
                rawOut = getContentResolver().openOutputStream(safDestUri, "w");
                if (rawOut == null)
                    throw new IOException("openOutputStream returned null for " + safDestUri);
            } else {
                if (destFileFullPath == null) {
                    throw new IOException("Destination path is null (no SAF Uri, no legacy path)");
                }
                outputFile = new File(destFileFullPath);
                File parent = outputFile.getParentFile();
                if (parent != null && !parent.exists())
                    parent.mkdirs();
                rawOut = new FileOutputStream(outputFile);
            }

            try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(rawOut))) {
                long zippedSoFar = 0;
                int currentIndex = 0;

                for (int i = 0; i < filesToZip.size(); i++) {
                    Uri fileUri = filesToZip.get(i);
                    String fileName = fileNames.get(i);
                    currentIndex++;
                    sendProgress(fileName, zippedSoFar, currentIndex);

                    try (InputStream fis = getContentResolver().openInputStream(fileUri)) {
                        if (fis == null) {
                            myLogE("Could not open stream for " + fileUri);
                            continue;
                        }

                        ZipEntry entry = new ZipEntry(fileName);
                        zos.putNextEntry(entry);

                        byte[] buffer = new byte[8192];
                        int length;
                        while ((length = fis.read(buffer)) != -1) {
                            zos.write(buffer, 0, length);
                            zippedSoFar += length;
                            sendProgress(fileName, zippedSoFar, currentIndex);
                        }
                        zos.closeEntry();
                    } catch (Exception e) {
                        myLogEE(e, "Error zipping file " + fileName);
                    }
                }

                zos.flush();
                sendProgress(getString(R.string.Export_done_Excl), totalSize, totalFiles);
            }

            // Success: broadcast EXPORT_DONE with the right Uri
            if (useSaf) {
                // SAF: we already have the Uri
                Intent done = new Intent("EXPORT_DONE");
                done.putExtra("zipUri", safDestUri);
                done.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                LocalBroadcastManager.getInstance(this).sendBroadcast(done);
            } else {
                // Legacy path: verify min size, wrap with FileProvider
                long minSize = 1024; // 1 KB
                if (outputFile != null && outputFile.exists() && outputFile.length() >= minSize) {
                    Uri zipUri = FileProvider.getUriForFile(
                            this,
                            BuildConfig.APPLICATION_ID + ".FileProvider",
                            outputFile);
                    Intent done = new Intent("EXPORT_DONE");
                    done.putExtra("zipUri", zipUri);
                    done.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    LocalBroadcastManager.getInstance(this).sendBroadcast(done);
                } else {
                    myLogEE(null, "Export failed or incomplete: file too small or missing.");
                    if (outputFile != null && outputFile.exists() && outputFile.length() < minSize) {
                        boolean deleted = outputFile.delete();
                        myLog("Deleted bad ZIP: " + deleted);
                    }
                    sendFail("Export failed or incomplete: file too small or missing.");
                }
            }

        } catch (Throwable e) {
            myLogEE(e, "export general error");
            e.printStackTrace();
            sendFail(getString(R.string.Export_error) + ": " + e.getMessage());
        }
    }

    private void sendFail(String errMessage) {
        Intent i = new Intent("EXPORT_FAIL");
        i.putExtra("zipUri", (Parcelable) null);
        i.putExtra("displayText", errMessage);
        LocalBroadcastManager.getInstance(this).sendBroadcast(i);
    }

    private void sendProgress(String currentTrack, long zippedSoFar, int fileIndex) {
        long now = System.currentTimeMillis();
        if (now - lastUpdateTime > MIN_UPDATE_INTERVAL || zippedSoFar == totalSize) {
            int percent = (totalSize == 0) ? 0 : (int) ((zippedSoFar * 100) / totalSize);
            String display = "Track " + fileIndex + " of " + totalFiles +
                    "\n" + (zippedSoFar / 1024 / 1024) + " MB / " + (totalSize / 1024 / 1024) + " MB";

            Intent intent = new Intent("EXPORT_PROGRESS");
            intent.putExtra("currentTrack", currentTrack);
            intent.putExtra("progressPercent", percent);
            intent.putExtra("displayText", display);
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
            lastUpdateTime = now;
        }
        // (Optional) you can promote this to a foreground service with a progress
        // notification if exports are large.
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
