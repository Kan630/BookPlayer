package com.driot.bookplayer.objects;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.driot.bookplayer.helpers.CoverPictureDetection;
import com.driot.bookplayer.helpers.ImageHelper;
import com.driot.bookplayer.utils.log.LoggerHelper;

import java.util.HashMap;
import java.util.Map;

public class AudioInfo extends LoggerHelper implements Parcelable {
    public static final String K_TITLE = "title";
    public static final String K_ARTIST = "artist";
    public static final String K_ALBUM = "album";
    public static final String K_GENRE = "genre";
    public static final String K_YEAR = "year";

    public final Uri uri;
    public final String displayName; // best-effort name shown to the user (file name or MediaStore DISPLAY_NAME)
    public final long durationMs; // 0 if unknown
    @Nullable
    public final Bitmap cover; // may be null
    public Map<String, String> metadata;
    public final String sourceHint; // e.g. "content://media", "file://", "temp-copy"

    public AudioInfo(Uri uri,
            String displayName,
            long durationMs,
            @Nullable Bitmap cover,
            String sourceHint,
            @Nullable Map<String, String> metadata) {
        super(AudioInfo.class);
        this.uri = uri;
        this.displayName = displayName;
        this.durationMs = durationMs;
        this.cover = cover;
        this.sourceHint = sourceHint;
        this.metadata = (metadata == null) ? new HashMap<>() : new HashMap<>(metadata);
    }

    // ---- Parcelable ----
    protected AudioInfo(Parcel in) {
        super(AudioInfo.class);
        uri = in.readParcelable(Uri.class.getClassLoader());
        displayName = in.readString();
        durationMs = in.readLong();
        cover = in.readParcelable(Bitmap.class.getClassLoader());
        sourceHint = in.readString();
        metadata = new HashMap<>();
        in.readMap(metadata, String.class.getClassLoader());
    }

    public static final Creator<AudioInfo> CREATOR = new Creator<AudioInfo>() {
        @Override
        public AudioInfo createFromParcel(Parcel in) {
            return new AudioInfo(in);
        }

        @Override
        public AudioInfo[] newArray(int size) {
            return new AudioInfo[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeParcelable(uri, flags);
        dest.writeString(displayName);
        dest.writeLong(durationMs);
        dest.writeParcelable(cover, flags);
        dest.writeString(sourceHint);
        dest.writeMap(metadata);
    }

    @NonNull
    @Override
    public String toString() {
        return "AudioInfo{" +
                "uri=" + uri +
                ", displayName='" + displayName + '\'' +
                ", durationMs=" + durationMs +
                ", cover=" + (cover != null ? ("bitmap@" + cover.getWidth() + "x" + cover.getHeight()) : "null") +
                ", sourceHint='" + sourceHint + '\'' +
                ", metadata=" + metadata +
                '}';
    }

    public void saveCover(Context context) {
        if (cover != null) {
            myLogD("save temp cover image");
            CoverPictureDetection.saveCoverToTemp(context, cover);
        } else {
            myLogD("no cover");
        }
    }

}
