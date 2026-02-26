package com.driot.bookplayer.db;

/**
 * created by Antoine Driot -- antoine.driot.com -- on 2026-02-26
 */

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Query;

import java.util.List;

@Dao
public interface ZikFileDao extends CommonZikFileDao {

    // PODCAST LIST
    @Query("SELECT ZikFile.* FROM ZikFile " +
            "INNER JOIN Episode ON Episode.idZikFile = ZikFile.id " +
            "WHERE ZikFile.idFolder = :idFolder " +
            "ORDER BY CAST(Episode.datePublished AS INTEGER) ASC, zeorder, name")
    List<ZikFile> getPodcastZikFilesAsc(long idFolder);

    @Query("SELECT ZikFile.* FROM ZikFile " +
            "INNER JOIN Episode ON Episode.idZikFile = ZikFile.id " +
            "WHERE ZikFile.idFolder = :idFolder " +
            "ORDER BY CAST(Episode.datePublished AS INTEGER) DESC, zeorder, name")
    List<ZikFile> getPodcastZikFilesDesc(long idFolder); // Newest first

    @Query("""
            SELECT * FROM ZikFile
            WHERE idFolder = (SELECT idFolder FROM Podcast WHERE feedId = :feedId)
              AND lLastAccess > 0
            ORDER BY lLastAccess DESC
            LIMIT 1
            """)
    LiveData<ZikFile> getLastListenedZikFileForPodcast(long feedId);
}
