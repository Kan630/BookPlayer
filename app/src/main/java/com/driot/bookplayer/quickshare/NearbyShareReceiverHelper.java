package com.driot.bookplayer.quickshare;

import android.content.Context;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.driot.bookplayer.R;
import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.db.FolderDao;
import com.driot.bookplayer.helpers.StorageHelper;
import com.driot.bookplayer.imports.ImportJob;
import com.driot.bookplayer.imports.ImportJobRepository;
import com.driot.bookplayer.imports.ImportWorker;
import com.driot.bookplayer.services.FinalParseFolderWorker;
import com.driot.bookplayer.utils.Tonio;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.driot.bookplayer.utils.log.LoggerStaticHelper.*;

public class NearbyShareReceiverHelper {

    private final Context context;
    private JSONObject metadata;
    private String bookName;
    private String bookFolderPath;
    // remove hasCover
    private volatile int totalFileCount;
    private volatile long totalSize;
    private List<FileInfo> fileList;

    private volatile int expectedPayloads; // metadata + cover (if exists) + audio files
    private volatile int receivedPayloads;
    private volatile long totalBytesReceived;

    private volatile boolean isSuccess = false;
    private volatile boolean isCancelled = false;

    private ProgressCallback progressCallback;

    public static class FileInfo {
        public String name;
        public String displayName;
        public long size;
        public long duration;
        public JSONObject progressMeta;
    }

    public interface ProgressCallback {
        void onProgress(String fileName, int currentFile, int totalFiles, long bytesReceived, long totalBytes,
                int currentFileProgress);

        void onCoverReceived(String path);

        void onComplete(String bookName);

        void onError(String error);
    }

    public NearbyShareReceiverHelper(Context context) {
        this.context = context.getApplicationContext();
        this.fileList = new ArrayList<>();
    }

    private final Map<Long, FileInfo> payloadIdToFileInfo = new HashMap<>();

    public void reset() {
        receivedPayloads = 0;
        totalBytesReceived = 0;
        totalSize = 0;
        totalFileCount = 0;
        fileList.clear();
        payloadIdToFileInfo.clear();
        bookFolderPath = null;
        bookName = null;
        isSuccess = false;
        isCancelled = false;
        metadata = null;
    }

    public void cancel() {
        isCancelled = true;
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
            int trackCount = metadata.optInt("trackCount", totalFileCount);
            totalSize = metadata.optLong("totalSize", 0);

            // Parse file list
            JSONArray filesArray = metadata.getJSONArray("files");
            boolean transferProgress = metadata.optBoolean("transferProgress", false);

            for (int i = 0; i < filesArray.length(); i++) {
                JSONObject fileObj = filesArray.getJSONObject(i);
                FileInfo info = new FileInfo();
                info.name = fileObj.getString("name");
                info.displayName = fileObj.optString("displayName", info.name);
                info.size = fileObj.optLong("size", 0);
                info.duration = fileObj.optLong("duration", 0);

                long payloadId = fileObj.optLong("payloadId", -1);
                if (payloadId != -1) {
                    payloadIdToFileInfo.put(payloadId, info);
                }

                if (transferProgress && !info.name.equalsIgnoreCase("cover.jpg")) {
                    JSONObject pMeta = new JSONObject();
                    if (fileObj.has("position"))
                        pMeta.put("position", fileObj.getDouble("position"));
                    if (fileObj.has("percentdone"))
                        pMeta.put("percentdone", fileObj.getDouble("percentdone"));
                    if (fileObj.has("finished"))
                        pMeta.put("finished", fileObj.getBoolean("finished"));
                    if (fileObj.has("lFirstAccess"))
                        pMeta.put("lFirstAccess", fileObj.optLong("lFirstAccess", 0));
                    if (fileObj.has("lLastAccess"))
                        pMeta.put("lLastAccess", fileObj.optLong("lLastAccess", 0));
                    if (fileObj.has("zeorder"))
                        pMeta.put("zeorder", fileObj.getDouble("zeorder"));
                    if (fileObj.has("playSessions"))
                        pMeta.put("playSessions", fileObj.getJSONArray("playSessions"));
                    info.progressMeta = pMeta;
                }

                fileList.add(info);
            }

            // Calculate expected payloads: 1 metadata + N files
            expectedPayloads = 1 + totalFileCount;
            receivedPayloads = 1; // Metadata already received

            myLogI("Metadata parsed: " + bookName + ", " + trackCount + " tracks (" + totalFileCount + " payloads)");

            if (progressCallback != null) {
                progressCallback.onProgress(context.getString(R.string.metadata), 0, trackCount, 0, totalSize, 0);
            }

            return true;
        } catch (Exception e) {
            myLogEE(e, "Failed to parse metadata");
            return false;
        }
    }

    /**
     * Create book folder with unique name
     * 
     * @return null if success, error message if failure
     */
    public String createBookFolder() {
        try {
            File baseDir = StorageHelper.getUnzipFolder(context);
            if (!baseDir.exists()) {
                if (!baseDir.mkdirs()) {
                    return context.getString(R.string.nearby_share_error_base_folder, baseDir.getAbsolutePath());
                }
            }

            String basePath = baseDir.getAbsolutePath();

            // Check for conflicts and create unique folder name
            bookFolderPath = getUniqueFolderPath(basePath, bookName);
            File bookDir = new File(bookFolderPath);

            if (!bookDir.exists() && !bookDir.mkdirs()) {
                String err = context.getString(R.string.nearby_share_error_create_folder, bookFolderPath);
                myLogE(err);
                return err;
            }

            myLog("Created book folder: " + bookFolderPath);
            return null;
        } catch (Exception e) {
            String err = context.getString(R.string.nearby_share_error_creating_book_folder, e.getMessage());
            myLogEE(e, "Error creating book folder");
            return err;
        }
    }

    /**
     * Get unique folder path by checking DB and filesystem
     */
    private String getUniqueFolderPath(String basePath, String folderName) throws IOException {
        FolderDao folderDAO = AppDatabase.getInstance(context).folderDao();

        // Check if exists in DB
        if (folderDAO.folderAlreadyExist_checkFolderName(folderName) > 0) {
            throw new IOException(context.getString(R.string.nearby_share_error_book_exists_db, folderName));
        }

        // Check if exists on filesystem
        File candidateDir = new File(basePath, folderName);
        if (candidateDir.exists()) {
            throw new IOException(context.getString(R.string.nearby_share_error_folder_exists_fs, folderName));
        }

        return candidateDir.getAbsolutePath();
    }

    /**
     * Save received file
     */
    public boolean saveFile(long payloadId, ParcelFileDescriptor pfd, long payloadSize) {
        if (bookFolderPath == null) {
            myLogE("Cannot save file: bookFolderPath is null. Handshake might have failed.");
            return false;
        }
        try {
            // Calculate which file this is using Payload ID
            FileInfo fileInfo = payloadIdToFileInfo.get(payloadId);
            int fileIndex = -1;

            if (fileInfo == null) {
                // Fallback integration: if map is empty, try index
                int fallbackIndex = receivedPayloads - 1;
                if (fallbackIndex >= 0 && fallbackIndex < fileList.size()) {
                    fileInfo = fileList.get(fallbackIndex);
                    fileIndex = fallbackIndex;
                    myLogW("Payload ID " + payloadId + " not found, using fallback index " + fileIndex);
                } else {
                    myLogE("Invalid file index and Payload ID not found: " + payloadId);
                    return false;
                }
            } else {
                fileIndex = fileList.indexOf(fileInfo);
            }

            if (fileIndex < 0 || fileIndex >= fileList.size()) {
                myLogE("Invalid file index: " + fileIndex);
                return false;
            }

            File destFile = new File(bookFolderPath, fileInfo.name);

            long bytesWritten = 0;
            long fileSize = fileInfo.size;
            int lastLoggedStep = -1;

            if (progressCallback != null) {
                progressCallback.onProgress(
                        fileInfo.displayName,
                        fileIndex + 1,
                        totalFileCount,
                        totalBytesReceived,
                        totalSize,
                        0);
            }

            try (InputStream in = new FileInputStream(pfd.getFileDescriptor());
                    FileOutputStream out = new FileOutputStream(destFile)) {

                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    if (isCancelled) {
                        myLogW("File writing cancelled for: " + fileInfo.name);
                        return false;
                    }
                    out.write(buffer, 0, bytesRead);
                    bytesWritten += bytesRead;
                    totalBytesReceived += bytesRead;

                    // Log progress at steps: 0, 25, 50, 75, 100
                    if (fileSize > 0) {
                        int percent = (int) (bytesWritten * 100 / fileSize);
                        int step = (percent / 25) * 25;
                        if (step > lastLoggedStep) {
                            myLogD("Writing " + fileInfo.name + ": " + step + "% ("
                                    + Tonio.getReadableSize(bytesWritten) + "/" + Tonio.getReadableSize(fileSize)
                                    + ")");
                            lastLoggedStep = step;
                        }
                    }

                    // Report progress periodically - for UI - (throttled to avoid flooding
                    // LiveData)
                    if (bytesWritten % (512 * 1024) == 0 && progressCallback != null) {
                        int currentFileProgress = fileSize > 0 ? (int) (bytesWritten * 100 / fileSize) : 0;
                        progressCallback.onProgress(
                                fileInfo.displayName,
                                fileIndex + 1,
                                totalFileCount,
                                totalBytesReceived,
                                totalSize,
                                currentFileProgress);
                    }
                }
                out.getFD().sync();
            }

            receivedPayloads++;
            myLog("Saved file " + (fileIndex + 1) + "/" + totalFileCount + ": " +
                    destFile.getName() + " (" + Tonio.getReadableSize(bytesWritten) + ") in [" + destFile.getPath()
                    + "]");

            // Check for cover image name to separate UI callback if needed, or just let it
            // pass
            if (destFile.getName().toLowerCase().contains("cover") || destFile.getName().toLowerCase().endsWith(".jpg")
                    || destFile.getName().toLowerCase().endsWith(".png")) {
                if (progressCallback != null) {
                    progressCallback.onCoverReceived(destFile.getAbsolutePath());
                }
            }

            if (progressCallback != null) {
                int currentFileProgress = fileSize > 0 ? (int) (bytesWritten * 100 / fileSize) : 100;
                progressCallback.onProgress(
                        fileInfo.displayName,
                        fileIndex + 1,
                        totalFileCount,
                        totalBytesReceived,
                        totalSize,
                        currentFileProgress);
            }

            // Check if all files received
            if (receivedPayloads >= expectedPayloads) {
                onTransferComplete();
            }

            return true;
        } catch (Exception e) {
            myLogEE(e, "Failed to save file");
            return false;
        }
    }

    /**
     * Called when all files are received
     */
    private void onTransferComplete() {
        isSuccess = true;
        myLog("------------------------------------------------------------------");
        myLogI("All files received, triggering FinalParseFolderWorker for: " + bookFolderPath);
        myLog("------------------------------------------------------------------");

        try {
            // Create import job for FinalParseFolderWorker
            String importId = "nearby:" + UUID.randomUUID();

            ImportJob job = new ImportJob();
            job.importId = importId;
            job.title = bookName;
            job.futureFolderName = new File(bookFolderPath).getName();
            job.futureFolderPath = bookFolderPath;
            job.sourceLocation = "NEARBY_SHARE";
            job.originalUri = Uri.fromFile(new File(bookFolderPath)).getPath();
            job.dynamicUri = Uri.fromFile(new File(bookFolderPath)).getPath();
            job.originalType = "Folder";
            job.dynamicType = "Folder";
            job.futureFolderPath = bookFolderPath;
            job.sourceLocation = "NEARBY_SHARE";
            job.createdAt = job.updatedAt = System.currentTimeMillis();

            // Store progress data in metadataJson if present
            if (metadata.optBoolean("transferProgress", false)) {
                JSONObject extras = new JSONObject();
                JSONArray filesExtras = new JSONArray();
                for (FileInfo fi : fileList) {
                    if (fi.progressMeta != null) {
                        JSONObject feId = new JSONObject();
                        feId.put("name", fi.name);
                        feId.put("progress", fi.progressMeta);
                        filesExtras.put(feId);
                    }
                }
                if (filesExtras.length() > 0) {
                    extras.put("nearby_progress", filesExtras);
                    job.metadataJson = extras.toString();
                    myLogD("Stored nearby_progress in ImportJob metadataJson");
                }
            }

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

            myLogD("FinalParseFolderWorker enqueued for importId: " + importId);

            if (progressCallback != null) {
                progressCallback.onComplete(bookName);
            }

        } catch (Exception e) {
            myLogEE(e, "Failed to trigger FinalParseFolderWorker");
            if (progressCallback != null) {
                progressCallback.onError(context.getString(R.string.nearby_share_error_import, e.getMessage()));
            }
        }
    }

    /**
     * Clean up partial files on error
     */
    public void cleanupOnError() {
        if (isSuccess) {
            return;
        }
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
        if (!file.delete()) {
            myLogE("Failed to delete file: " + file.getAbsolutePath());
        }
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

    public boolean isSuccess() {
        return isSuccess;
    }
}
