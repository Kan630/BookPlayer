package com.driot.bookplayer.helpers;

import android.content.Context;
import android.os.ParcelFileDescriptor;

import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.db.FolderDao;
import com.driot.bookplayer.imports.ImportJob;
import com.driot.bookplayer.imports.ImportJobRepository;
import com.driot.bookplayer.imports.ImportWorker;
import com.driot.bookplayer.services.FinalParseFolderWorker;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.driot.bookplayer.utils.log.LoggerStaticHelper.*;

public class NearbyShareReceiverHelper {

    private final Context context;
    private JSONObject metadata;
    private String bookName;
    private String bookFolderPath;
    private boolean hasCover;
    private int totalFileCount;
    private long totalSize;
    private List<FileInfo> fileList;

    private int expectedPayloads; // metadata + cover (if exists) + audio files
    private int receivedPayloads;
    private long totalBytesReceived;

    private ProgressCallback progressCallback;

    public static class FileInfo {
        public String name;
        public String displayName;
        public long size;
        public long duration;
    }

    public interface ProgressCallback {
        void onProgress(String message, int currentFile, int totalFiles, long bytesReceived, long totalBytes);

        void onComplete(String bookName);

        void onError(String error);
    }

    public NearbyShareReceiverHelper(Context context) {
        this.context = context.getApplicationContext();
        this.fileList = new ArrayList<>();
    }

    public void setProgressCallback(ProgressCallback callback) {
        this.progressCallback = callback;
    }

    /**
     * Parse metadata from first payload
     */
    public boolean parseMetadata(byte[] data) {
        try {
            String jsonString = new String(data);
            metadata = new JSONObject(jsonString);

            bookName = metadata.getString("bookName");
            totalFileCount = metadata.getInt("fileCount");
            hasCover = metadata.getBoolean("hasCover");
            totalSize = metadata.optLong("totalSize", 0);

            // Parse file list
            JSONArray filesArray = metadata.getJSONArray("files");
            for (int i = 0; i < filesArray.length(); i++) {
                JSONObject fileObj = filesArray.getJSONObject(i);
                FileInfo info = new FileInfo();
                info.name = fileObj.getString("name");
                info.displayName = fileObj.optString("displayName", info.name);
                info.size = fileObj.optLong("size", 0);
                info.duration = fileObj.optLong("duration", 0);
                fileList.add(info);
            }

            // Calculate expected payloads: 1 metadata + (1 cover if exists) + N audio files
            expectedPayloads = 1 + (hasCover ? 1 : 0) + totalFileCount;
            receivedPayloads = 1; // Metadata already received

            myLogI("Metadata parsed: " + bookName + ", " + totalFileCount + " files, hasCover=" + hasCover);

            return true;
        } catch (Exception e) {
            myLogEE(e, "Failed to parse metadata");
            return false;
        }
    }

    /**
     * Create book folder with unique name
     */
    public boolean createBookFolder() {
        try {
            String basePath = StorageHelper.getBooksFolderPathForReceivedBooks(context);
            File baseDir = new File(basePath);
            if (!baseDir.exists()) {
                baseDir.mkdirs();
            }

            // Check for conflicts and create unique folder name
            bookFolderPath = getUniqueFolderPath(basePath, bookName);
            File bookDir = new File(bookFolderPath);

            if (!bookDir.exists() && !bookDir.mkdirs()) {
                myLogE("Failed to create folder: " + bookFolderPath);
                return false;
            }

            myLogI("Created book folder: " + bookFolderPath);
            return true;
        } catch (Exception e) {
            myLogEE(e, "Error creating book folder");
            return false;
        }
    }

    /**
     * Get unique folder path by checking DB and filesystem
     */
    private String getUniqueFolderPath(String basePath, String folderName) {
        FolderDao folderDAO = AppDatabase.getInstance(context).folderDao();

        String candidateName = folderName;
        int suffix = 2;

        while (true) {
            // Check if exists in DB
            boolean existsInDb = folderDAO.folderAlreadyExist_checkFolderName(candidateName) > 0;

            // Check if exists on filesystem
            File candidateDir = new File(basePath, candidateName);
            boolean existsOnDisk = candidateDir.exists();

            if (!existsInDb && !existsOnDisk) {
                return candidateDir.getAbsolutePath();
            }

            // Try next suffix
            candidateName = folderName + " (" + suffix + ")";
            suffix++;

            // Safety limit
            if (suffix > 100) {
                // Fallback to UUID
                candidateName = folderName + " (" + UUID.randomUUID().toString().substring(0, 8) + ")";
                return new File(basePath, candidateName).getAbsolutePath();
            }
        }
    }

    /**
     * Save cover image file
     */
    public boolean saveCoverFile(ParcelFileDescriptor pfd) {
        try {
            File coverFile = new File(bookFolderPath, "cover.jpg");

            try (InputStream in = new FileInputStream(pfd.getFileDescriptor());
                    FileOutputStream out = new FileOutputStream(coverFile)) {

                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
            }

            receivedPayloads++;
            myLogI("Saved cover image: " + coverFile.getAbsolutePath());

            if (progressCallback != null) {
                progressCallback.onProgress("Received cover image", 0, totalFileCount,
                        totalBytesReceived, totalSize);
            }

            return true;
        } catch (Exception e) {
            myLogEE(e, "Failed to save cover file");
            return false;
        }
    }

    /**
     * Save audio file
     */
    public boolean saveAudioFile(ParcelFileDescriptor pfd, long payloadSize) {
        try {
            // Calculate which file this is (skip metadata and cover)
            int fileIndex = receivedPayloads - 1 - (hasCover ? 1 : 0);

            if (fileIndex < 0 || fileIndex >= fileList.size()) {
                myLogE("Invalid file index: " + fileIndex);
                return false;
            }

            FileInfo fileInfo = fileList.get(fileIndex);
            File audioFile = new File(bookFolderPath, fileInfo.name);

            long bytesWritten = 0;
            try (InputStream in = new FileInputStream(pfd.getFileDescriptor());
                    FileOutputStream out = new FileOutputStream(audioFile)) {

                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                    bytesWritten += bytesRead;
                    totalBytesReceived += bytesRead;

                    // Report progress periodically
                    if (bytesWritten % (512 * 1024) == 0 && progressCallback != null) {
                        progressCallback.onProgress(
                                "Receiving " + fileInfo.displayName,
                                fileIndex + 1,
                                totalFileCount,
                                totalBytesReceived,
                                totalSize);
                    }
                }
            }

            receivedPayloads++;
            myLogI("Saved audio file " + (fileIndex + 1) + "/" + totalFileCount + ": " +
                    audioFile.getName() + " (" + bytesWritten + " bytes)");

            if (progressCallback != null) {
                progressCallback.onProgress(
                        "Received " + fileInfo.displayName,
                        fileIndex + 1,
                        totalFileCount,
                        totalBytesReceived,
                        totalSize);
            }

            // Check if all files received
            if (receivedPayloads >= expectedPayloads) {
                onTransferComplete();
            }

            return true;
        } catch (Exception e) {
            myLogEE(e, "Failed to save audio file");
            return false;
        }
    }

    /**
     * Called when all files are received
     */
    private void onTransferComplete() {
        myLogI("All files received, triggering FinalParseFolderWorker for: " + bookFolderPath);

        try {
            // Create import job for FinalParseFolderWorker
            String importId = "nearby:" + UUID.randomUUID();

            ImportJob job = new ImportJob();
            job.importId = importId;
            job.title = bookName;
            job.futureFolderName = new File(bookFolderPath).getName();
            job.futureFolderPath = bookFolderPath;
            job.createdAt = job.updatedAt = System.currentTimeMillis();

            ImportJobRepository repo = new ImportJobRepository(context);
            repo.upsert(job);

            // Launch FinalParseFolderWorker
            Data inputData = new Data.Builder()
                    .putString(ImportWorker.KEY_IMPORT_ID, importId)
                    .build();

            OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(FinalParseFolderWorker.class)
                    .setInputData(inputData)
                    .addTag("nearby_share_import")
                    .build();

            WorkManager.getInstance(context).enqueue(workRequest);

            myLogI("FinalParseFolderWorker enqueued for importId: " + importId);

            if (progressCallback != null) {
                progressCallback.onComplete(bookName);
            }

        } catch (Exception e) {
            myLogEE(e, "Failed to trigger FinalParseFolderWorker");
            if (progressCallback != null) {
                progressCallback.onError("Failed to import book: " + e.getMessage());
            }
        }
    }

    /**
     * Clean up partial files on error
     */
    public void cleanupOnError() {
        if (bookFolderPath != null) {
            try {
                File bookDir = new File(bookFolderPath);
                if (bookDir.exists()) {
                    deleteRecursive(bookDir);
                    myLogI("Cleaned up partial folder: " + bookFolderPath);
                }
            } catch (Exception e) {
                myLogEE(e, "Failed to cleanup folder");
            }
        }
    }

    private void deleteRecursive(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        file.delete();
    }

    public String getBookName() {
        return bookName;
    }

    public int getTotalFileCount() {
        return totalFileCount;
    }

    public boolean hasMetadata() {
        return metadata != null;
    }
}
