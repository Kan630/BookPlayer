package com.driot.bookplayer.objects;

import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

import com.driot.bookplayer.utils.KanLogger;

public class LoadBookTaskState implements Parcelable {
    public Uri originalUri;
    public String originalType;
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
    public String currentLoadingOperation;
    public String downloadFileUrl;
    public String downloadDestinationFolder;
    public int downloadRetryCount;
    public long downloadStartTime;
    public String dynamicDestinationFolderPath;
    public String dynamicSourceFilePath;



    public LoadBookTaskState() {
        myLog("LoadBookTaskState() constructor - creating new Workflow");
    }

    protected LoadBookTaskState(Parcel in) {
        originalUri = in.readParcelable(Uri.class.getClassLoader());
        originalType = in.readString();
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
        currentLoadingOperation = in.readString();
        downloadFileUrl = in.readString();
        downloadDestinationFolder = in.readString();
        downloadRetryCount = in.readInt();
        downloadStartTime = in.readLong();
        dynamicDestinationFolderPath = in.readString();
        dynamicSourceFilePath = in.readString();
    }

    public static final Creator<LoadBookTaskState> CREATOR = new Creator<LoadBookTaskState>() {
        @Override
        public LoadBookTaskState createFromParcel(Parcel in) {
            return new LoadBookTaskState(in);
        }

        @Override
        public LoadBookTaskState[] newArray(int size) {
            return new LoadBookTaskState[size];
        }
    };

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeParcelable(originalUri, flags);
        dest.writeString(originalType);
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
        dest.writeString(currentLoadingOperation);
        dest.writeString(downloadFileUrl);
        dest.writeString(downloadDestinationFolder);
        dest.writeInt(downloadRetryCount);
        dest.writeLong(downloadStartTime);
        dest.writeString(dynamicDestinationFolderPath);
        dest.writeString(dynamicSourceFilePath);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    @NonNull
    public String toString() {
        return "LoadBookTaskState{" +
                "uri=" + originalUri +
                ", type='" + originalType + '\'' +
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
                ", currentLoadingOperation='" + currentLoadingOperation + '\'' +
                ", downloadFileUrl='" + downloadFileUrl + '\'' +
                ", downloadDestinationFolder='" + downloadDestinationFolder + '\'' +
                ", downloadRetryCount=" + downloadRetryCount +
                ", downloadStartTime=" + downloadStartTime +
                ", dynamicDestinationFolderPath='" + dynamicDestinationFolderPath + '\'' +
                ", dynamicSourceFilePath='" + dynamicSourceFilePath + '\'' +
                '}';
    }

    private static String trimOrNull(String s) {
        return s == null ? null : s.trim();
    }





    ////////////////////////////////////////////////////////
    ///////// Loggers
    ////////////////////////////////////////////////////////
    private static final String TAG = "LoadBookTaskState";
    private static void myLog(String str) { KanLogger.myLog(TAG, str); }
    private static void myLogD(String str) { KanLogger.myLogD(TAG, str); }
    private static void myLogI(String str) { KanLogger.myLogI(TAG, str); }
    private static void myLogE(String str) { KanLogger.myLogE(TAG, str); }
}
