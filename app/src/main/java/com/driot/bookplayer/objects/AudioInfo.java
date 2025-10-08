package com.driot.bookplayer.objects;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.Nullable;

import com.driot.bookplayer.helpers.ImageHelper;
import com.driot.bookplayer.utils.KanLogger;

public class AudioInfo implements Parcelable {
    public final Uri uri;
    public final String displayName;   // best-effort name shown to the user (file name or MediaStore DISPLAY_NAME)
    public final long durationMs;      // 0 if unknown
    @Nullable public final String title;
    @Nullable public final String artist;
    @Nullable public final String album;
    @Nullable public final Bitmap cover; // may be null
    public final String sourceHint;    // e.g. "content://media", "file://", "temp-copy"

    public AudioInfo(Uri uri,
                     String displayName,
                     long durationMs,
                     @Nullable String title,
                     @Nullable String artist,
                     @Nullable String album,
                     @Nullable Bitmap cover,
                     String sourceHint) {
        this.uri = uri;
        this.displayName = displayName;
        this.durationMs = durationMs;
        this.title = title;
        this.artist = artist;
        this.album = album;
        this.cover = cover;
        this.sourceHint = sourceHint;
    }

    // ---- Parcelable ----
    protected AudioInfo(Parcel in) {
        uri = in.readParcelable(Uri.class.getClassLoader());
        displayName = in.readString();
        durationMs = in.readLong();
        title = in.readString();
        artist = in.readString();
        album = in.readString();
        cover = in.readParcelable(Bitmap.class.getClassLoader());
        sourceHint = in.readString();
    }

    public static final Creator<AudioInfo> CREATOR = new Creator<AudioInfo>() {
        @Override public AudioInfo createFromParcel(Parcel in) { return new AudioInfo(in); }
        @Override public AudioInfo[] newArray(int size) { return new AudioInfo[size]; }
    };

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeParcelable(uri, flags);
        dest.writeString(displayName);
        dest.writeLong(durationMs);
        dest.writeString(title);
        dest.writeString(artist);
        dest.writeString(album);
        dest.writeParcelable(cover, flags);
        dest.writeString(sourceHint);
    }

    @Override public String toString() {
        return "AudioInfo{" +
                "uri=" + uri +
                ", displayName='" + displayName + '\'' +
                ", durationMs=" + durationMs +
                ", title='" + title + '\'' +
                ", artist='" + artist + '\'' +
                ", album='" + album + '\'' +
                ", cover=" + (cover != null ? ("bitmap@" + cover.getWidth()+"x"+cover.getHeight()) : "null") +
                ", sourceHint='" + sourceHint + '\'' +
                '}';
    }

    public void saveCover(Context context) {
        if (cover != null) {
            myLogD("save temp cover image");
            ImageHelper.saveTempBitmap(context, cover);
        } else {
            myLogD("no cover");
        }
    }

    // ---- Logging helpers ----
    private static final String TAG = "AudioInfo";
    @SuppressWarnings("unused") private static void myLog(String s){ KanLogger.myLog(TAG, s); }
    @SuppressWarnings("unused") private static void myLogD(String s){ KanLogger.myLogD(TAG, s); }
    @SuppressWarnings("unused") private static void myLogW(String s){ KanLogger.myLogW(TAG, s); }
    @SuppressWarnings("unused") private static void myLogEE(Throwable t, String s){ KanLogger.myLogEE(t, TAG, s); }
}
