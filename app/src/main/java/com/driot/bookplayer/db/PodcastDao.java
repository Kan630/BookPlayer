package com.driot.bookplayer.db;

import androidx.lifecycle.LiveData;
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

    @Query("SELECT * FROM Podcast WHERE id = :id")
    Podcast getById(long id);

    @Query("SELECT * FROM Podcast WHERE isFavorite = 1")
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

    @Query("SELECT * FROM Podcast WHERE feedId = :feedId LIMIT 1")
    Podcast getPodcastByFeedId(long feedId);

    @Query("UPDATE Podcast SET idFolder = :idFolder WHERE feedId = :feedId")
    void updateFolderIdByFeedId(long feedId, Long idFolder);


}
