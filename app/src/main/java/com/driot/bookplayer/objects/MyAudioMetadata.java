package com.driot.bookplayer.objects;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Parcel;
import android.os.Parcelable;

import com.driot.bookplayer.helpers.ImageHelper;

// Add this inner class or top-level class in your codebase
public class MyAudioMetadata implements Parcelable {
    public String title;
    public String artist;
    public String album;
    public Bitmap cover;

    public MyAudioMetadata() {}

    protected MyAudioMetadata(Parcel in) {
        title = in.readString();
        artist = in.readString();
        album = in.readString();
        cover = in.readParcelable(Bitmap.class.getClassLoader());
    }

    public static final Creator<MyAudioMetadata> CREATOR = new Creator<MyAudioMetadata>() {
        @Override
        public MyAudioMetadata createFromParcel(Parcel in) {
            return new MyAudioMetadata(in);
        }

        @Override
        public MyAudioMetadata[] newArray(int size) {
            return new MyAudioMetadata[size];
        }
    };

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(title);
        dest.writeString(artist);
        dest.writeString(album);
        dest.writeParcelable(cover, flags);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public String toString() {
        return "MyAudioMetadata{" +
                "title='" + title + '\'' +
                ", artist='" + artist + '\'' +
                ", album='" + album + '\'' +
                ", cover=" + cover +
                '}';
    }

    public void saveCover(Context context) {
        if (cover != null) {
            ImageHelper.saveTempBitmap(context, cover);
        }
    }

}
