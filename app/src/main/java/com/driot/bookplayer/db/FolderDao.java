package com.driot.bookplayer.db;

/**
 * created by Antoine Driot -- antoine.driot.com -- on 28/10/20
 */

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.RawQuery;
import androidx.room.Update;
import androidx.sqlite.db.SupportSQLiteQuery;

import java.util.List;


@Dao
public interface FolderDao {

    @Query("SELECT * FROM Folder ORDER BY lastaccess DESC")
    List<Folder> getAll();

    @Query("SELECT COUNT(id) FROM Folder WHERE uri LIKE :sUri AND hash LIKE :iHash")
    long folderAlreadyExist(String sUri, String iHash);

    @RawQuery
    int runRawSql(SupportSQLiteQuery query);

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
