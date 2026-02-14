package com.driot.bookplayer.imports;

import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.driot.bookplayer.utils.log.KanLogger;

public class ImportBookTaskState implements Parcelable {
    public Uri originalUri;
    public String sourceType;
    public Uri dynamicUri;
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
    public String downloadedFilePath;
    public boolean downloadedFileReady;
    public boolean onGoingLoading;
    public String imagePath;
    public String progressText;
    public int progressPercent;
    public boolean isLoadingPaused;
    public String currentOperation;
    public String downloadFileUrl;
    public String downloadDestinationFolder;
    public int downloadRetryCount;
    public long downloadStartTime;
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
    public int addToExistingFolderId;

    // --- Batch tracking (for MassImport) ---
    public int batchIndex = -1; // 1-based position in batch, -1 if not part of a batch
    public int batchTotal = -1; // Total count in batch, -1 if not part of a batch

    public ImportBookTaskState() {
        myLog("ImportBookTaskState() constructor - creating new Workflow");
    }

    protected ImportBookTaskState(Parcel in) {
        originalUri = in.readParcelable(Uri.class.getClassLoader());
        sourceType = in.readString();
        dynamicUri = in.readParcelable(Uri.class.getClassLoader());
        dynamicType = in.readString();
        title = trimOrNull(in.readString());
        futureFolderName = trimOrNull(in.readString());
        futureFolderPath = trimOrNull(in.readString());
        optionSplit = in.readByte() != 0;
        optionCopy = in.readByte() != 0;
        optionDelete = in.readByte() != 0;
        originalFile = in.readString();
        originalHash = in.readString();
        sourceLocation = in.readString();
        fileExtension = in.readString();
        mimeType = in.readString();
        downloadedFilePath = in.readString();
        downloadedFileReady = in.readByte() != 0;
        onGoingLoading = in.readByte() != 0;
        imagePath = in.readString();
        progressText = in.readString();
        progressPercent = in.readInt();
        isLoadingPaused = in.readByte() != 0;
        currentOperation = in.readString();
        downloadFileUrl = in.readString();
        downloadDestinationFolder = in.readString();
        downloadRetryCount = in.readInt();
        downloadStartTime = in.readLong();
        dynamicDestinationFolderPath = in.readString();
        dynamicSourceFilePath = in.readString();
        doDownload = in.readByte() != 0;
        doCopy = in.readByte() != 0;
        doSplitM4b = in.readByte() != 0;
        doSplitEbook = in.readByte() != 0;
        doUnzip = in.readByte() != 0;
        playType = in.readString();
        downloadWorkId = in.readString();
        uniqueChainName = in.readString();
        addToExistingFolderId = in.readInt();
        batchIndex = in.readInt();
        batchTotal = in.readInt();
    }

    public static final Creator<ImportBookTaskState> CREATOR = new Creator<ImportBookTaskState>() {
        @Override
        public ImportBookTaskState createFromParcel(Parcel in) {
            return new ImportBookTaskState(in);
        }

        @Override
        public ImportBookTaskState[] newArray(int size) {
            return new ImportBookTaskState[size];
        }
    };

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeParcelable(originalUri, flags);
        dest.writeString(sourceType);
        dest.writeParcelable(dynamicUri, flags);
        dest.writeString(dynamicType);
        dest.writeString(title);
        dest.writeString(futureFolderName);
        dest.writeString(futureFolderPath);
        dest.writeByte((byte) (optionSplit ? 1 : 0));
        dest.writeByte((byte) (optionCopy ? 1 : 0));
        dest.writeByte((byte) (optionDelete ? 1 : 0));
        dest.writeString(originalFile);
        dest.writeString(originalHash);
        dest.writeString(sourceLocation);
        dest.writeString(fileExtension);
        dest.writeString(mimeType);
        dest.writeString(downloadedFilePath);
        dest.writeByte((byte) (downloadedFileReady ? 1 : 0));
        dest.writeByte((byte) (onGoingLoading ? 1 : 0));
        dest.writeString(imagePath);
        dest.writeString(progressText);
        dest.writeInt(progressPercent);
        dest.writeByte((byte) (isLoadingPaused ? 1 : 0));
        dest.writeString(currentOperation);
        dest.writeString(downloadFileUrl);
        dest.writeString(downloadDestinationFolder);
        dest.writeInt(downloadRetryCount);
        dest.writeLong(downloadStartTime);
        dest.writeString(dynamicDestinationFolderPath);
        dest.writeString(dynamicSourceFilePath);
        dest.writeByte((byte) (doDownload ? 1 : 0));
        dest.writeByte((byte) (doCopy ? 1 : 0));
        dest.writeByte((byte) (doSplitM4b ? 1 : 0));
        dest.writeByte((byte) (doSplitEbook ? 1 : 0));
        dest.writeByte((byte) (doUnzip ? 1 : 0));
        dest.writeString(playType);
        dest.writeString(downloadWorkId);
        dest.writeString(uniqueChainName);
        dest.writeInt(addToExistingFolderId);
        dest.writeInt(batchIndex);
        dest.writeInt(batchTotal);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    @NonNull
    public String toString() {
        return "ImportBookTaskState{" +
                "uri=" + originalUri +
                ", type='" + sourceType + '\'' +
                ", dynamicUri=" + dynamicUri +
                ", type='" + dynamicType + '\'' +
                ", title='" + title + '\'' +
                ", futureFolder='" + futureFolderName + '\'' +
                ", futurePath='" + futureFolderPath + '\'' +
                ", split=" + optionSplit +
                ", copy=" + optionCopy +
                ", delete=" + optionDelete +
                ", originalFile='" + originalFile + '\'' +
                ", originalHash='" + originalHash + '\'' +
                ", sourceLocation='" + sourceLocation + '\'' +
                ", fileExtension='" + fileExtension + '\'' +
                ", mimeType='" + mimeType + '\'' +
                ", downloadedFilePath='" + downloadedFilePath + '\'' +
                ", downloadedFileReady=" + downloadedFileReady +
                ", onGoingLoading=" + onGoingLoading +
                ", imagePath='" + imagePath + '\'' +
                ", progressText='" + progressText + '\'' +
                ", progressPercent=" + progressPercent +
                ", isLoadingPaused=" + isLoadingPaused +
                ", currentLoadingOperation='" + currentOperation + '\'' +
                ", downloadFileUrl='" + downloadFileUrl + '\'' +
                ", downloadDestinationFolder='" + downloadDestinationFolder + '\'' +
                ", downloadRetryCount=" + downloadRetryCount +
                ", downloadStartTime=" + downloadStartTime +
                ", dynamicDestinationFolderPath='" + dynamicDestinationFolderPath + '\'' +
                ", dynamicSourceFilePath='" + dynamicSourceFilePath + '\'' +
                ", doDownload=" + doDownload +
                ", doCopy=" + doCopy +
                ", doSplitM4b=" + doSplitM4b +
                ", doSplitEpub=" + doSplitEbook +
                ", doUnzip=" + doUnzip +
                ", playType='" + playType + "'" +
                ", downloadWorkId='" + downloadWorkId + '\'' +
                ", uniqueChainName='" + uniqueChainName + '\'' +
                ", addToExistingFolderId=" + addToExistingFolderId +
                '}';
    }

    public String toStringN() {
        return toString().replace(", ", "\n");
    }

    private static String trimOrNull(String s) {
        return s == null ? null : s.trim();
    }

    public void setDownloadWorkId(@Nullable java.util.UUID id) {
        this.downloadWorkId = (id != null) ? id.toString() : null;
    }

    @Nullable
    public java.util.UUID getDownloadWorkUUID() {
        try {
            return (downloadWorkId != null) ? java.util.UUID.fromString(downloadWorkId) : null;
        } catch (IllegalArgumentException ignore) {
            return null;
        }
    }

    ////////////////////////////////////////////////////////
    ///////// Loggers
    ////////////////////////////////////////////////////////
    private static final String TAG = "ImportBookTaskState";

    private static void myLog(String str) {
        KanLogger.myLog(TAG, str);
    }

    private static void myLogD(String str) {
        KanLogger.myLogD(TAG, str);
    }

    private static void myLogI(String str) {
        KanLogger.myLogI(TAG, str);
    }

    private static void myLogE(String str) {
        KanLogger.myLogE(TAG, str);
    }
}
