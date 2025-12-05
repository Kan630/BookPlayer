package com.driot.bookplayer.podcasts;

import android.os.Parcel;
import android.os.Parcelable;

public class PodcastFeed implements Parcelable {
    public long id;
    public String title;
    public String description;
    public String image;
    public String url;       // RSS URL
    public String author;
    public String language;;

    public PodcastFeed(
              long feedId
            , String title
            , String image
            , String description
    ) {
        this.id = feedId;
        this.title = title;
        this.image = image;
        this.description = description;
    }

    protected PodcastFeed(Parcel in) {
        id = in.readLong();
        title = in.readString();
        description = in.readString();
        image = in.readString();
        url = in.readString();
        author = in.readString();
        language = in.readString();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(id);
        dest.writeString(title);
        dest.writeString(description);
        dest.writeString(image);
        dest.writeString(url);
        dest.writeString(author);
        dest.writeString(language);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<PodcastFeed> CREATOR = new Creator<PodcastFeed>() {
        @Override
        public PodcastFeed createFromParcel(Parcel in) {
            return new PodcastFeed(in);
        }

        @Override
        public PodcastFeed[] newArray(int size) {
            return new PodcastFeed[size];
        }
    };
}
