package com.driot.bookplayer.db;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;


@Entity(
        tableName = "PlayTick",
        foreignKeys = @ForeignKey(
                entity = ZikFile.class,
                parentColumns = "id",
                childColumns = "zikFileId",
                onDelete = ForeignKey.CASCADE
        )
        /*
        ,
        indices = {
                @Index("zikFileId"),
                @Index(value = {"zikFileId", "position"})
        }
         */
)
public class PlayTick {

    @PrimaryKey(autoGenerate = true)
    public long id;

    @ColumnInfo(name = "timestamp")
    public long timestamp;

    @ColumnInfo(name = "zikFileId")
    public long zikFileId;

    @ColumnInfo(name = "position", defaultValue = "0")
    public long position;

    public PlayTick(long timestamp, long zikFileId, long position) {
        this.timestamp = timestamp;
        this.zikFileId = zikFileId;
        this.position = position;
    }
}
