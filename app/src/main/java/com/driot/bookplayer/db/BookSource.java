package com.driot.bookplayer.db;

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
        indices = {@Index("idFolder")}
)
public class BookSource {
    @PrimaryKey(autoGenerate = true)
    public long id;

    public String imageLocal;     // Local image path (nullable)
    public String imageRemote;    // Remote image URL (nullable)
    public String repoType;       // e.g., "Podcast", "Librivox", etc.
    public String repoName;       // e.g., "Librivox"
    public String repoId;         // Identifier within the source (e.g., feedId or identifier)

    public Long idFolder;         // Folder ID (nullable)
}
