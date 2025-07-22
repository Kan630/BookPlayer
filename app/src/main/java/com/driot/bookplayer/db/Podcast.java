package com.driot.bookplayer.db;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "Podcast"
        ,foreignKeys = @ForeignKey(
                entity = Folder.class,
                parentColumns = "id",
                childColumns = "idFolder",
                onDelete = ForeignKey.SET_NULL)  // or CASCADE depending on behavior
        ,indices = {
         @Index(value = "idFolder", unique = true)
        ,@Index(value = "feedId", unique = true)
        }
)

public class Podcast {
    @PrimaryKey(autoGenerate = true)
    private int id;

    public String source;

    public long feedId;

    @NonNull
    public String title;

    public String image;

    public String description;

    public String language;

    public boolean isFavorite;
    public boolean autoDownload;

    public Long idFolder;


    public void setId(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }


    public Podcast() {
        title = ""; //useless but whatever
    }
}