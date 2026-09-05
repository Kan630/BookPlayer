package com.driot.bookplayer.db;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import com.driot.bookplayer.global.Option;

@Entity(tableName = "Podcast", foreignKeys = @ForeignKey(entity = Folder.class, parentColumns = "id", childColumns = "idFolder", onDelete = ForeignKey.SET_NULL), indices = {
        @Index(value = "idFolder", unique = true),
        @Index(value = "feedId", unique = true)
})
public class Podcast implements Parcelable {

    // Whether episodes of this podcast have their own distinct cover art, determined once by
    // hashing a handful of downloaded episode images (see ImageHelper.determineEpisodeCoverStatus).
    // Not just an enum name for readability - stored as int since Room columns need a primitive.
    public static final int EPISODE_COVER_STATUS_UNKNOWN = 0;
    public static final int EPISODE_COVER_STATUS_CHECKING = 1;
    public static final int EPISODE_COVER_STATUS_DISTINCT = 2;
    public static final int EPISODE_COVER_STATUS_NOT_DISTINCT = 3;

    @PrimaryKey(autoGenerate = true)
    private int id;

    public String source;
    public long feedId;

    @NonNull
    public String title;

    public String image;
    public String imageOriginalUrl;
    public String description;
    public String language;

    public boolean isFavorite;
    public boolean autoDownload;

    public Long idFolder; // nullable
    public long date_added;

    public boolean autoDelete;

    public long lastCheck;
    public long date_maj;

    public boolean sort_newest_top;

    @ColumnInfo(defaultValue = "0")
    public long timeListened;

    @ColumnInfo(defaultValue = "0")
    public int episodeCoverStatus;

    // -------------------------------------------------------

    public Podcast() {
        this.date_added = System.currentTimeMillis();
        this.date_maj = this.date_added;
        this.sort_newest_top = Option.getPodcastEpisodesSortOrder();
    }

    // -------------------------------------------------------

    public void setId(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }

    // --------- Parcelable implementation ---------

    protected Podcast(Parcel in) {
        id = in.readInt();
        source = in.readString();
        feedId = in.readLong();
        title = in.readString();
        image = in.readString();
        description = in.readString();
        language = in.readString();
        isFavorite = in.readByte() != 0;
        autoDownload = in.readByte() != 0;
        autoDelete = in.readByte() != 0;
        if (in.readByte() == 0) {
            idFolder = null;
        } else {
            idFolder = in.readLong();
        }
        date_added = in.readLong();
        lastCheck = in.readLong();
        date_maj = in.readLong();
        sort_newest_top = in.readByte() != 0;
        timeListened = in.readLong();
        episodeCoverStatus = in.readInt();
    }

    public static final Creator<Podcast> CREATOR = new Creator<Podcast>() {
        @Override
        public Podcast createFromParcel(Parcel in) {
            return new Podcast(in);
        }

        @Override
        public Podcast[] newArray(int size) {
            return new Podcast[size];
        }
    };

    @Override
    public void writeToParcel(@NonNull Parcel parcel, int flags) {
        parcel.writeInt(id);
        parcel.writeString(source);
        parcel.writeLong(feedId);
        parcel.writeString(title);
        parcel.writeString(image);
        parcel.writeString(description);
        parcel.writeString(language);
        parcel.writeByte((byte) (isFavorite ? 1 : 0));
        parcel.writeByte((byte) (autoDownload ? 1 : 0));
        parcel.writeByte((byte) (autoDelete ? 1 : 0));
        if (idFolder == null) {
            parcel.writeByte((byte) 0);
        } else {
            parcel.writeByte((byte) 1);
            parcel.writeLong(idFolder);
        }
        parcel.writeLong(date_added);
        parcel.writeLong(lastCheck);
        parcel.writeLong(date_maj);
        parcel.writeByte((byte) (sort_newest_top ? 1 : 0));
        parcel.writeLong(timeListened);
        parcel.writeInt(episodeCoverStatus);
    }

    @Override
    public int describeContents() {
        return 0;
    }
}
