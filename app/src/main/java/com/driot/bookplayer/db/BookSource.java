package com.driot.bookplayer.db;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "BookSource",
        foreignKeys = @ForeignKey(
                entity = Folder.class,
                parentColumns = "id",
                childColumns = "idFolder",
                onDelete = ForeignKey.SET_NULL
        ),
        indices = {
                @Index("idFolder"),
                @Index(value = {"repoType", "repoName", "repoId"}, unique = true)
        }
)
public class BookSource {
    @PrimaryKey(autoGenerate = true)
    public long id;

    public String imageLocal;     // Local image path (nullable)
    public String imageRemote;    // Remote image URL (nullable)
    public String repoType;       // e.g., "podcast", "audiobook", etc.
    public String repoName;       // e.g., "librivox"
    public String repoId;         // Identifier within the source (e.g., feedId or identifier)

    public Long idFolder;         // Folder ID (nullable)

    @NonNull @ColumnInfo(defaultValue = "") public String book_title;
    @NonNull @ColumnInfo(defaultValue = "") public String source_url;
    @ColumnInfo(defaultValue = "0") public long source_size;
    @ColumnInfo(defaultValue = "0") public boolean is_favorite;
    @ColumnInfo(defaultValue = "0") public long date_add;
    @ColumnInfo(defaultValue = "0") public long date_maj;

    public Long last_checked;     // Nullable timestamp of last verification

    // --- Optional helper constructor ---
    public BookSource(@NonNull String book_title,
                      @NonNull String source_url,
                      String repoType,
                      String repoName,
                      String repoId,
                      String imageLocal,
                      String imageRemote,
                      Long idFolder) {
        this.book_title = book_title;
        this.source_url = source_url;
        this.repoType = repoType;
        this.repoName = repoName;
        this.repoId = repoId;
        this.idFolder = idFolder;
        this.imageRemote = imageRemote;
        this.imageLocal = null;
        this.source_size = 0;
        this.is_favorite = false;
        this.date_add = System.currentTimeMillis();
        this.date_maj = this.date_add;
        this.last_checked = null;
    }
}