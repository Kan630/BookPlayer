package com.driot.bookplayer.db;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

@Entity(
        foreignKeys = {
                @ForeignKey(
                        entity = Podcast.class,
                        parentColumns = "id",
                        childColumns = "idPodcast",
                        onDelete = ForeignKey.CASCADE
                ),
                @ForeignKey(
                        entity = ZikFile.class,
                        parentColumns = "id",
                        childColumns = "idZikFile",
                        onDelete = ForeignKey.SET_NULL
                )
        },
        indices = {
                @androidx.room.Index(value="idPodcast"),
                @androidx.room.Index(value="idZikFile", unique=true),
                @androidx.room.Index(value="idEpisode", unique=true)
        }
)

public class Episode implements Parcelable {

    @PrimaryKey(autoGenerate = true)
    @NonNull
    public long id;

    @NonNull
    public long idPodcast;

    @NonNull
    public long date_add;

    @Nullable
    public Long idZikFile;

    @Nullable
    public Long date_import;

    @Nullable
    public Long date_delete;

    @Nullable
    public Long lastAccess;

    @NonNull
    public long idEpisode;

    public String title;

    public String description;

    public long duration;

    public String image;

    public String guid;

    public String podcastGuid;

    public String enclosureUrl;

    public String datePublished;

    public long enclosureLength;

    // --- Constructors ---

    public Episode() {}

    @Ignore
    public Episode(long idPodcast, long date_add, @Nullable Long idZikFile,
                   @Nullable Long date_import, @Nullable Long date_delete, @Nullable Long lastAccess) {
        this.idPodcast = idPodcast;
        this.date_add = date_add;
        this.idZikFile = idZikFile;
        this.date_import = date_import;
        this.date_delete = date_delete;
        this.lastAccess = lastAccess;
    }

    // --- Parcelable implementation ---

    protected Episode(Parcel in) {
        id = in.readLong();
        idPodcast = in.readLong();
        date_add = in.readLong();

        idZikFile = in.readByte() == 0 ? null : in.readLong();
        date_import = in.readByte() == 0 ? null : in.readLong();
        date_delete = in.readByte() == 0 ? null : in.readLong();
        lastAccess = in.readByte() == 0 ? null : in.readLong();

        idEpisode = in.readLong();
        title = in.readString();
        description = in.readString();
        duration = in.readLong();
        image = in.readString();
        guid = in.readString();
        podcastGuid = in.readString();
        enclosureUrl = in.readString();
        datePublished = in.readString();
        enclosureLength = in.readLong();

    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeLong(id);
        parcel.writeLong(idPodcast);
        parcel.writeLong(date_add);

        if (idZikFile == null) {
            parcel.writeByte((byte) 0);
        } else {
            parcel.writeByte((byte) 1);
            parcel.writeLong(idZikFile);
        }

        if (date_import == null) {
            parcel.writeByte((byte) 0);
        } else {
            parcel.writeByte((byte) 1);
            parcel.writeLong(date_import);
        }

        if (date_delete == null) {
            parcel.writeByte((byte) 0);
        } else {
            parcel.writeByte((byte) 1);
            parcel.writeLong(date_delete);
        }

        if (lastAccess == null) {
            parcel.writeByte((byte) 0);
        } else {
            parcel.writeByte((byte) 1);
            parcel.writeLong(lastAccess);
        }

        parcel.writeLong(idEpisode);
        parcel.writeString(title);
        parcel.writeString(description);
        parcel.writeLong(duration);
        parcel.writeString(image);
        parcel.writeString(guid);
        parcel.writeString(podcastGuid);
        parcel.writeString(enclosureUrl);
        parcel.writeString(datePublished);
        parcel.writeLong(enclosureLength);

    }

    public static final Creator<Episode> CREATOR = new Creator<Episode>() {
        @Override
        public Episode createFromParcel(Parcel in) {
            return new Episode(in);
        }

        @Override
        public Episode[] newArray(int size) {
            return new Episode[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

}
