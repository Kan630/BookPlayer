package com.driot.bookplayer.radio;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface RadioStationDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(RadioStation station);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    List<Long> insertAll(List<RadioStation> stations);

    @Update
    int update(RadioStation station);

    @Delete
    int delete(RadioStation station);

    @Query("SELECT * FROM RadioStation ORDER BY display_order ASC, name COLLATE NOCASE ASC")
    List<RadioStation> getAll();

    @Query("SELECT * FROM RadioStation WHERE stationuuid = :uuid LIMIT 1")
    RadioStation findByUuid(String uuid);

    @Query("SELECT * FROM RadioStation WHERE id = :id LIMIT 1")
    RadioStation findById(long id);

    // Favorites only
    @Query("SELECT * FROM RadioStation WHERE isFavorite = 1 ORDER BY display_order ASC")
    List<RadioStation> getFavorites();

    // Play History only
    @Query("SELECT * FROM RadioStation WHERE date_last_played is not null ORDER BY date_last_played DESC")
    List<RadioStation> getAlreadyPlayed();

        @Query("UPDATE RadioStation SET isFavorite = :favorite WHERE stationuuid = :uuid")
    void setFavoriteByUuid(String uuid, boolean favorite);

    @Query("UPDATE RadioStation SET date_last_played = :timestamp WHERE stationuuid = :uuid")
    void updateLastPlayByUuid(String uuid, Long timestamp);

    @Query("UPDATE RadioStation SET display_order = :order WHERE stationuuid = :uuid")
    void updateDisplayOrder(String uuid, int order);

    @Query("DELETE FROM RadioStation")
    void deleteAll();

    @Query("SELECT COUNT(*) FROM RadioStation")
    int countAll();

    @Query("SELECT COUNT(*) FROM RadioStation WHERE isFavorite = 1")
    int countFavorites();

    @Query("SELECT COUNT(*) FROM RadioStation WHERE date_last_played IS NOT NULL")
    int countHistory();

}
