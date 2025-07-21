package com.driot.bookplayer.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface PodcastDao {
    @Insert
    void insert(Podcast podcast);

    @Update
    void update(Podcast podcast);

    @Query("SELECT * FROM Podcast WHERE feedId = :id")
    Podcast getById(long id);

    @Query("SELECT * FROM Podcast WHERE isFavorite = 1")
    List<Podcast> getFavorites();

    @Query("SELECT * FROM Podcast WHERE autoDownload = 1")
    List<Podcast> getAutoDownloads();

    @Query("DELETE FROM Podcast WHERE feedId = :id")
    void deleteById(long id);

    @Query("UPDATE Podcast SET isFavorite = :isFav WHERE feedId = :feedId")
    void updateFavoriteStatus(long feedId, boolean isFav);

    @Query("UPDATE Podcast SET autoDownload = :auto WHERE feedId = :feedId")
    void updateAutoDownloadStatus(long feedId, boolean auto);

    @Query("SELECT * FROM Podcast WHERE feedId = :feedId LIMIT 1")
    Podcast getPodcastById(long feedId);
}
