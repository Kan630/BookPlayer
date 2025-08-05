package com.driot.bookplayer.db;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface EpisodeDao {

    // --- INSERT ---
    @Insert
    long insert(Episode episode);

    @Insert
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

    @Query("SELECT * FROM Episode ORDER BY date_add DESC")
    List<Episode> getAll();

    @Query("UPDATE Episode SET date_delete = :now WHERE idZikFile = :zikFileId")
    int updateDateDeleteForZikFileId(long zikFileId, long now);

}
