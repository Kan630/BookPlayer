package com.driot.bookplayer.db;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "Podcast",
        foreignKeys = @ForeignKey(
                entity = Folder.class,
                parentColumns = "id",
                childColumns = "idFolder",
                onDelete = ForeignKey.SET_NULL  // or CASCADE depending on behavior
        ),
        indices = {@Index("idFolder")}
)

public class Podcast {
    @PrimaryKey
    public long feedId;

    //public String title;
    //public String image;
    //public String language;

    public boolean isFavorite;
    public boolean autoDownload;

    public Long idFolder;
}