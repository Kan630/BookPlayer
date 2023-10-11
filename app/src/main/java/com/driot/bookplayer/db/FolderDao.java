package com.driot.bookplayer.db;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.RawQuery;
import androidx.room.Update;
import androidx.sqlite.db.SupportSQLiteQuery;

import java.util.List;

/**
 * created by Antoine Driot -- antoine.driot.com -- on 28/10/20
 * LiveData added oct 2023
 */

@Dao
public interface FolderDao {

    @Query("SELECT * FROM Folder ORDER BY lastaccess DESC")
    List<Folder> getAll();

    @Query("SELECT * FROM Folder ORDER BY lastaccess DESC")
    LiveData<List<Folder>> getAllLiveData();

    @Query("SELECT COUNT(id) FROM Folder WHERE uri LIKE :sUri AND hash LIKE :iHash")
    long folderAlreadyExist(String sUri, String iHash);

    @Query("SELECT COUNT(id) FROM Folder WHERE name LIKE :sFolderName")
    long folderAlreadyExist_checkFolderName(String sFolderName);

    @RawQuery
    int runRawSql(SupportSQLiteQuery query);

    @Insert
    long insert(Folder Folder);

    @Delete
    void delete(Folder Folder);

    @Query("DELETE FROM Folder WHERE id =:id")
    void delete(int id);

    @Query("UPDATE Folder SET name = :newName WHERE id =:id")
    void changeName(int id, String newName);

    @Query("UPDATE Folder SET position = 0, percentdone = 0, lastAccess=null, lastAccessTime=null, finished=0 WHERE id =:id")
    void resetProgression(int id);

    @Update
    void update(Folder Folder);

    // Exemple avec dates :
    //@Query("SELECT * FROM user WHERE birthday BETWEEN :from AND :to")
    //List<User> findUsersBornBetweenDates(Date from, Date to);

}
