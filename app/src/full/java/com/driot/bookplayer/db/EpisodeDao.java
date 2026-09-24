package com.driot.bookplayer.db;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface EpisodeDao {

    // --- INSERT ---
    @Insert
    long insert(Episode episode);

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    List<Long> insertAll(List<Episode> episodes);

    // --- UPDATE ---
    @Update
    int update(Episode episode);

    // --- DELETE ---
    @Delete
    int delete(Episode episode);

    @Query("DELETE FROM Episode WHERE id = :episodeId")
    int deleteById(long episodeId);

    // --- GET ---
    @Query("SELECT * FROM Episode WHERE id = :episodeId")
    Episode getById(long episodeId);

    @Query("SELECT * FROM Episode WHERE idPodcast = :podcastId ORDER BY date_add ASC")
    List<Episode> getByPodcastId(long podcastId);

    @Query("SELECT * FROM Episode WHERE idZikFile = :zikFileId")
    Episode getByZikFileId(long zikFileId);

    @Query("SELECT * FROM Episode WHERE idEpisode = :idEpisode")
    Episode getByEpisodeId(long idEpisode);

    @Query("SELECT * FROM Episode WHERE enclosureUrl = :url LIMIT 1")
    Episode getFromUrl(String url);

    @Query("SELECT * FROM Episode ORDER BY date_add DESC")
    List<Episode> getAll();

    // Folders where EVERY track is a podcast episode with a saved download address - a folder
    // holding even one file that isn't re-downloadable must keep going into the backup.
    @Query("SELECT DISTINCT z.idFolder FROM ZikFile z WHERE z.idFolder NOT IN ("
            + "SELECT z2.idFolder FROM ZikFile z2 WHERE z2.id NOT IN ("
            + "SELECT e.idZikFile FROM Episode e WHERE e.idZikFile IS NOT NULL "
            + "AND e.enclosureUrl IS NOT NULL AND e.enclosureUrl != ''))")
    List<Long> getFoldersWhollyRedownloadable();

    @Query("SELECT * FROM Episode WHERE idZikFile IS NOT NULL")
    List<Episode> getDownloadedEpisodes();

    @Query("UPDATE Episode SET date_delete = :now WHERE idZikFile = :zikFileId")
    int updateDateDeleteForZikFileId(long zikFileId, long now);

    @Query("SELECT * FROM Episode WHERE idPodcast = :podcastId ORDER BY datePublished DESC")
    List<Episode> getAllEpisodesForPodcastNewestFirst(int podcastId);

    @Query("SELECT * FROM Episode WHERE idPodcast = :podcastId ORDER BY datePublished ASC")
    List<Episode> getAllEpisodesForPodcastOldestFirst(int podcastId);


    @Query("SELECT MAX(datePublished) FROM Episode WHERE idPodcast = :podcastId")
    Long getMaxDatePublishedForPodcast(long podcastId);

    @Query("UPDATE Episode SET timeListened = timeListened + 1 WHERE id = :id")
    void addSecondToTimeListened(long id);

    // Sets (not increments) timeListened - used to re-apply a recovered value from
    // PendingEpisodeHistory after a restore, see PodcastEpisodeViewModel.
    @Query("UPDATE Episode SET timeListened = :timeListened WHERE id = :id")
    void setTimeListened(long id, long timeListened);

    // For the "Podcast history" backup category - only episodes with real user data
    // (downloaded, or ever listened to), never the full catalog. See PendingEpisodeHistory.
    @Query("SELECT 0 AS id, p.feedId AS feedId, p.title AS podcastTitle, e.idEpisode AS idEpisode, " +
            "e.title AS episodeTitle, e.datePublished AS datePublished, e.timeListened AS timeListened " +
            "FROM Episode e JOIN Podcast p ON e.idPodcast = p.id " +
            "WHERE e.idZikFile IS NOT NULL OR e.timeListened > 0")
    List<PendingEpisodeHistory> getEngagedEpisodesForBackup();

}
