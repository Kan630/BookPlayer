package com.driot.bookplayer.db;

import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

public class LoadBookTaskState implements Parcelable {
    public Uri uri;
    public String type;
    public String title;
    public boolean split;
    public boolean copy;
    public boolean delete;
    public String downloadedFilePath;
    public boolean downloadedFileReady; //so that you never get stuck if app crashes
    public boolean onGoing;

    public LoadBookTaskState() {}

    protected LoadBookTaskState(Parcel in) {
        uri = in.readParcelable(Uri.class.getClassLoader());  // ✅ Read Uri
        type = in.readString();
        title = in.readString();
        split = in.readByte() != 0;
        copy = in.readByte() != 0;
        delete = in.readByte() != 0;
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
                ", downloadedFilePath='" + downloadedFilePath + '\'' +
                ", onGoingDownload=" + downloadedFileReady +
                ", onGoing=" + onGoing +
                '}';
    }

}
