package com.driot.bookplayer.objects;

import android.graphics.Bitmap;
import android.os.Parcel;
import android.os.Parcelable;

// Add this inner class or top-level class in your codebase
public class AudioMetadata implements Parcelable {
    public String title;
    public String artist;
    public String album;
    public Bitmap coverBitmap;

    public AudioMetadata() {}

    protected AudioMetadata(Parcel in) {
        title = in.readString();
        artist = in.readString();
        album = in.readString();
        coverBitmap = in.readParcelable(Bitmap.class.getClassLoader());
    }

    public static final Creator<AudioMetadata> CREATOR = new Creator<AudioMetadata>() {
        @Override
        public AudioMetadata createFromParcel(Parcel in) {
            return new AudioMetadata(in);
        }

        @Override
        public AudioMetadata[] newArray(int size) {
            return new AudioMetadata[size];
        }
    };

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(title);
        dest.writeString(artist);
        dest.writeString(album);
        dest.writeParcelable(coverBitmap, flags);
    }

    @Override
    public int describeContents() {
        return 0;
    }
}
