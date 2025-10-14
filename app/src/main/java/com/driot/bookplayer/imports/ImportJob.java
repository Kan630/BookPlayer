package com.driot.bookplayer.imports;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import java.util.Objects;

@Entity(
        tableName = "ImportJob",
        indices = {
                @Index(value = {"futureFolderPath"}, unique = true),
                @Index(value = {"status"}, unique = false)
        }
)
public class ImportJob {

    @PrimaryKey @NonNull
    public String importId;               // "futureFolderName:UUID"

    // --- Copied from LoadBookTaskState (as strings/booleans for Room simplicity) ---
    public String originalUri;            // stringified Uri
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

    // --- Control / lifecycle ---
    public String status;// QUEUED/RUNNING/SUCCEEDED/FAILED/CANCELLED/PAUSED
    public boolean showToUser;
    public long createdAt;
    public long updatedAt;
    public String warningText;
    public String errorTextDev;
    public String errorTextUser;


    public static final String S_QUEUED    = "QUEUED";
    public static final String S_RUNNING   = "RUNNING";
    public static final String S_SUCCEEDED = "SUCCEEDED";
    public static final String S_FAILED    = "FAILED";
    public static final String S_CANCELLED = "CANCELLED";
    public static final String S_PAUSED    = "PAUSED";

    public boolean isFinished() { return Objects.equals(status, S_SUCCEEDED) || Objects.equals(status, S_FAILED) || Objects.equals(status, S_CANCELLED); }
}
