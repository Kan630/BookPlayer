package com.driot.bookplayer.player.heatmaps;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import com.driot.bookplayer.db.ZikFile;

@Entity(
        tableName = "PlaySession",
        foreignKeys = @ForeignKey(
                entity = ZikFile.class,
                parentColumns = "id",
                childColumns = "zikFileId",
                onDelete = ForeignKey.CASCADE
        ),
        indices = {
                @Index("zikFileId"),
                @Index(value = {"zikFileId", "positionStart"})
        }
)
public class PlaySession {

    @PrimaryKey(autoGenerate = true)
    public long id;

    public long zikFileId;
    public long timestampStart;
    public long timestampEnd;
    public long positionStart;
    public long positionEnd;

    public PlaySession(long zikFileId,
                       long timestampStart,
                       long timestampEnd,
                       long positionStart,
                       long positionEnd) {
        this.zikFileId = zikFileId;
        this.timestampStart = timestampStart;
        this.timestampEnd = timestampEnd;
        this.positionStart = positionStart;
        this.positionEnd = positionEnd;
    }
}

