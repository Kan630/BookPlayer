package com.driot.bookplayer.imports;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import com.driot.bookplayer.global.Var;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

@Entity(tableName = "ImportJob", indices = {
                @Index(value = { "futureFolderPath" }, unique = true),
                @Index(value = { "status" }, unique = false)
})
public class ImportJob {

        @PrimaryKey
        @NonNull
        public String importId; // "futureFolderName:UUID"

        // --- Copied from ImportBookTaskState (as strings/booleans for Room simplicity)
        // ---
        public String originalUri; // stringified Uri
        public String originalType;
        public String dynamicUri;
        public String dynamicType;
        public String title;
        public String futureFolderName;
        public String futureFolderPath;

        public boolean optionSplit;
        public boolean optionCopy;
        public boolean optionDelete;

        public String originalFile;
        public String originalHash;
        public String sourceLocation;
        public String fileExtension;
        public String mimeType;

        public String imagePath;
        public String progressText;
        public int progressPercent;

        public boolean isLoadingPaused;
        public boolean isPauseAvailable;
        public String currentOperation;

        public String downloadFileUrl;
        public String downloadDestinationFolder;
        public int downloadRetryCount;
        public long downloadStartTime;
        public long downloadDuration;

        public String downloadedFilePath;
        public boolean downloadedFileReady;

        public String dynamicDestinationFolderPath;
        public String dynamicSourceFilePath;

        public boolean doDownload;
        public boolean doCopy;
        public boolean doSplitM4b;
        public boolean doSplitEbook;
        public boolean doUnzip;

        public String playType;
        public String downloadWorkId;
        public String uniqueChainName;

        public long addToExistingFolderId;

        // --- Batch tracking (for MassImport) ---
        public int batchIndex = -1; // 1-based position in batch, -1 if not part of a batch
        public int batchTotal = -1; // Total count in batch, -1 if not part of a batch

        // --- Control / lifecycle ---
        public String status = Var.IMPORT_STATUS_QUEUED;
        public boolean showToUser;
        public long createdAt;
        public long updatedAt;
        public String warningText;
        public String errorTextDev;
        public String errorTextUser;
        public String metadataJson;

        public boolean isFinished() {
                return Var.IMPORT_STATUS_SUCCEEDED.equals(status)
                                || Var.IMPORT_STATUS_FAILED.equals(status)
                                || Var.IMPORT_STATUS_CANCELLED.equals(status);
        }

        public boolean isRunningLike() {
                return Var.IMPORT_STATUS_RUNNING.equals(status)
                                || Var.IMPORT_STATUS_QUEUED.equals(status)
                                || Var.IMPORT_STATUS_PAUSED.equals(status);
        }

        @Override
        public String toString() {
                return "ImportJob{" +
                                "importId='" + importId + '\'' +
                                ", originalUri='" + originalUri + '\'' +
                                ", originalType='" + originalType + '\'' +
                                ", dynamicUri='" + dynamicUri + '\'' +
                                ", dynamicType='" + dynamicType + '\'' +
                                ", title='" + title + '\'' +
                                ", futureFolderName='" + futureFolderName + '\'' +
                                ", futureFolderPath='" + futureFolderPath + '\'' +
                                ", optionSplit=" + optionSplit +
                                ", optionCopy=" + optionCopy +
                                ", optionDelete=" + optionDelete +
                                ", originalFile='" + originalFile + '\'' +
                                ", originalHash='" + originalHash + '\'' +
                                ", sourceLocation='" + sourceLocation + '\'' +
                                ", fileExtension='" + fileExtension + '\'' +
                                ", mimeType='" + mimeType + '\'' +
                                ", imagePath='" + imagePath + '\'' +
                                ", progressText='" + progressText + '\'' +
                                ", progressPercent=" + progressPercent +
                                ", isLoadingPaused=" + isLoadingPaused +
                                ", isPauseAvailable=" + isPauseAvailable +
                                ", currentOperation='" + currentOperation + '\'' +
                                ", downloadFileUrl='" + downloadFileUrl + '\'' +
                                ", downloadDestinationFolder='" + downloadDestinationFolder + '\'' +
                                ", downloadRetryCount=" + downloadRetryCount +
                                ", downloadStartTime=" + downloadStartTime +
                                ", downloadDuration=" + downloadDuration +
                                ", downloadedFilePath='" + downloadedFilePath + '\'' +
                                ", downloadedFileReady=" + downloadedFileReady +
                                ", dynamicDestinationFolderPath='" + dynamicDestinationFolderPath + '\'' +
                                ", dynamicSourceFilePath='" + dynamicSourceFilePath + '\'' +
                                ", doDownload=" + doDownload +
                                ", doCopy=" + doCopy +
                                ", doSplitM4b=" + doSplitM4b +
                                ", doSplitEbook=" + doSplitEbook +
                                ", doUnzip=" + doUnzip +
                                ", playType='" + playType + '\'' +
                                ", downloadWorkId='" + downloadWorkId + '\'' +
                                ", uniqueChainName='" + uniqueChainName + '\'' +
                                ", addToExistingFolderId=" + addToExistingFolderId +
                                ", batchIndex=" + batchIndex +
                                ", batchTotal=" + batchTotal +
                                ", status='" + status + '\'' +
                                ", showToUser=" + showToUser +
                                ", createdAt=" + createdAt +
                                ", updatedAt=" + updatedAt +
                                ", warningText='" + warningText + '\'' +
                                ", errorTextDev='" + errorTextDev + '\'' +
                                ", errorTextUser='" + errorTextUser + '\'' +
                                '}';
        }

        // --- Metadata helpers ---

        public Map<String, Object> getMetadataMap() {
                if (metadataJson == null || metadataJson.isEmpty()) {
                        return new HashMap<>();
                }
                try {
                        Type type = new TypeToken<Map<String, Object>>() {
                        }.getType();
                        return new Gson().fromJson(metadataJson, type);
                } catch (Exception e) {
                        return new HashMap<>();
                }
        }

        public void setMetadataMap(Map<String, Object> map) {
                if (map == null) {
                        metadataJson = null;
                        return;
                }
                try {
                        metadataJson = new Gson().toJson(map);
                } catch (Exception e) {
                        metadataJson = null;
                }
        }

        @SuppressWarnings("unchecked")
        public Map<String, String> getTrackTitles() {
                Map<String, Object> meta = getMetadataMap();
                Object titles = meta.get("track_titles");
                if (titles instanceof Map) {
                        return (Map<String, String>) titles;
                }
                return new LinkedHashMap<>();
        }

        public void setTrackTitles(Map<String, String> titles) {
                Map<String, Object> meta = getMetadataMap();
                meta.put("track_titles", titles);
                setMetadataMap(meta);
        }
}
