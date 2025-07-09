package com.driot.bookplayer.objects;

import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

import com.driot.bookplayer.utils.KanLogger;

public class LoadBookTaskState implements Parcelable {
    public Uri uri;
    public String type;
    public String title;
    public boolean split;
    public boolean copy;
    public boolean delete;
    public String originalType;
    public String originalFile;
    public String originalHash;
    public String sourceLocation;
    public String fileExtension;
    public String mimeType;

    //
    public String downloadedFilePath;
    public boolean downloadedFileReady; //so that you never get stuck if app crashes
    public boolean onGoing;

    public LoadBookTaskState() {
        myLog("LoadBookTaskState() constructor - creating new Workflow");
    }

    protected LoadBookTaskState(Parcel in) {
        uri = in.readParcelable(Uri.class.getClassLoader());  // ✅ Read Uri
        type = in.readString();
        title = in.readString();
        split = in.readByte() != 0;
        copy = in.readByte() != 0;
        delete = in.readByte() != 0;
        originalType = in.readString();
        originalFile = in.readString();
        originalHash = in.readString();
        sourceLocation = in.readString();
        fileExtension = in.readString();
        mimeType = in.readString();
        downloadedFilePath = in.readString();
        downloadedFileReady = in.readByte() != 0;
        onGoing = in.readByte() != 0;
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
        dest.writeParcelable(uri, flags);  // ✅ Write Uri
        dest.writeString(type);
        dest.writeString(title);
        dest.writeByte((byte) (split ? 1 : 0));
        dest.writeByte((byte) (copy ? 1 : 0));
        dest.writeByte((byte) (delete ? 1 : 0));
        dest.writeString(originalType);
        dest.writeString(originalFile);
        dest.writeString(originalHash);
        dest.writeString(sourceLocation);
        dest.writeString(fileExtension);
        dest.writeString(mimeType);
        dest.writeString(downloadedFilePath);
        dest.writeByte((byte) (downloadedFileReady ? 1 : 0));
        dest.writeByte((byte) (onGoing ? 1 : 0));
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    @NonNull
    public String toString() {
        return "LoadBookTaskState{" +
                "uri=" + uri +
                ", type='" + type + '\'' +
                ", title='" + title + '\'' +
                ", split=" + split +
                ", copy=" + copy +
                ", delete=" + delete +
                ", originalType='" + originalType + '\'' +
                ", originalFile='" + originalFile + '\'' +
                ", originalHash='" + originalHash + '\'' +
                ", sourceLocation='" + sourceLocation + '\'' +
                ", fileExtension='" + fileExtension + '\'' +
                ", mimeType='" + mimeType + '\'' +
                ", downloadedFilePath='" + downloadedFilePath + '\'' +
                ", onGoingDownload=" + downloadedFileReady +
                ", onGoing=" + onGoing +
                '}';
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
