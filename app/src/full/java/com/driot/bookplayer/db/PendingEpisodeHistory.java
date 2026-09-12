package com.driot.bookplayer.db;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Holds "you've listened to this episode" facts recovered from a backup, waiting to be
 * re-applied once the owning podcast's episodes are fetched again after a restore (episode
 * catalogs are deliberately never part of any backup - see EpisodeDao.getEngagedEpisodesForBackup
 * and PodcastEpisodeViewModel's reconciliation step). Rows are consumed (deleted) once matched
 * against a freshly-fetched Episode row, so this table only ever holds not-yet-reconciled
 * leftovers from a restore - it isn't a permanent store.
 */
@Entity(indices = {
        @Index(value = { "feedId", "idEpisode" }, unique = true),
        @Index(value = "feedId")
})
public class PendingEpisodeHistory {

    @PrimaryKey(autoGenerate = true)
    public long id;

    public long feedId;
    public String podcastTitle;
    public long idEpisode;
    public String episodeTitle;
    public String datePublished;

    @ColumnInfo(defaultValue = "0")
    public long timeListened;
}
