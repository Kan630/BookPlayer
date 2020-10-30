package com.driot.bookplayer;

/**
 * created by Antoine Driot -- antoine.driot.com -- on 28/10/20
 */

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;


@Dao
public interface ZikFileDao {

    @Query("SELECT * FROM ZikFile")
    List<ZikFile> getAll();

    @Insert
    void insert(ZikFile ZikFile);

    @Delete
    void delete(ZikFile ZikFile);

    @Update
    void update(ZikFile ZikFile);

    // Exemple avec dates :
    //@Query("SELECT * FROM user WHERE birthday BETWEEN :from AND :to")
    //List<User> findUsersBornBetweenDates(Date from, Date to);

}
