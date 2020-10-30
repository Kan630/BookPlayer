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
public interface FolderDao {

    @Query("SELECT * FROM Folder")
    List<Folder> getAll();

    @Query("SELECT COUNT(id) FROM Folder WHERE uri LIKE :sUri AND hash LIKE :iHash")
    long folderAlreadyExist(String sUri, String iHash);

    @Insert
    long insert(Folder Folder);

    @Delete
    void delete(Folder Folder);

    @Update
    void update(Folder Folder);

    // Exemple avec dates :
    //@Query("SELECT * FROM user WHERE birthday BETWEEN :from AND :to")
    //List<User> findUsersBornBetweenDates(Date from, Date to);

}
