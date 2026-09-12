package com.driot.bookplayer.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface PendingEpisodeHistoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<PendingEpisodeHistory> entries);

    @Query("DELETE FROM PendingEpisodeHistory")
    void deleteAll();

    @Query("SELECT * FROM PendingEpisodeHistory WHERE feedId = :feedId")
    List<PendingEpisodeHistory> getByFeedId(long feedId);

    @Query("DELETE FROM PendingEpisodeHistory WHERE feedId = :feedId AND idEpisode = :idEpisode")
    void deleteOne(long feedId, long idEpisode);

    @Query("SELECT EXISTS(SELECT 1 FROM PendingEpisodeHistory LIMIT 1)")
    boolean hasAny();
}
