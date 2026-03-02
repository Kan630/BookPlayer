package com.driot.bookplayer.db;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface PodcastDao {
    @Insert
    long insert(Podcast podcast);

    @Update
    void update(Podcast podcast);

    @Query("SELECT * FROM Podcast WHERE feedId = :feedId LIMIT 1")
    LiveData<Podcast> getPodcastLiveByFeedId(long feedId);

    @Query("SELECT * FROM Podcast WHERE id = :id")
    Podcast getById(long id);

    @Query("SELECT * FROM Podcast WHERE feedId = :feedId LIMIT 1")
    Podcast getPodcastByFeedId(long feedId);

    @Query("SELECT * FROM Podcast WHERE idFolder = :folderId LIMIT 1")
    Podcast getPodcastByFolderId(long folderId);

    @Query("SELECT count(*) FROM Podcast WHERE isFavorite = 1")
    int getFavoriteCount();

    @Query("SELECT * FROM Podcast WHERE isFavorite = 1 ORDER BY date_added DESC")
    LiveData<List<Podcast>> getFavoritePodcastsLive();

    @Query("SELECT * FROM Podcast WHERE autoDownload = 1")
    List<Podcast> getAutoDownloads();

    @Query("DELETE FROM Podcast WHERE feedId = :id")
    void deleteByFeedId(long id);

    @Query("UPDATE Podcast SET isFavorite = :isFav WHERE feedId = :feedId")
    void updateFavoriteStatus(long feedId, boolean isFav);

    @Query("UPDATE Podcast SET autoDownload = :auto WHERE idFolder = :folderId")
    void updateAutoDownloadStatus_fromFolderId(long folderId, boolean auto);

    @Query("UPDATE Podcast SET autoDownload = :auto WHERE feedId = :feedId")
    void updateAutoDownloadStatus_fromFeedId(long feedId, boolean auto);

    @Query("UPDATE Podcast SET idFolder = :idFolder WHERE feedId = :feedId")
    void updateFolderIdByFeedId(long feedId, Long idFolder);

    @Query("SELECT * FROM Podcast WHERE image IS NOT NULL AND image LIKE 'http%' AND :now - date_maj > 24*60*60*1000")
    List<Podcast> getAllWithExternalImagesUnchangedSince24h(long now);

    @Query("SELECT id FROM Podcast WHERE feedId = :feedId")
    Long getIdByFeedId(long feedId);

    @Query("UPDATE Podcast SET lastCheck = :lastCheck WHERE feedId = :feedId")
    void updateLastCheck(long feedId, long lastCheck);

    @Query("SELECT COUNT(*) FROM Podcast WHERE autoDownload = 1")
    int getNbAutoDownload();

    @Query("SELECT * FROM Podcast")
    List<Podcast> getAll();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<Podcast> podcasts);

    @Query("UPDATE Podcast SET image = :imagePath WHERE idFolder = :folderId")
    void updateImageForFolderId(long folderId, String imagePath);


}
