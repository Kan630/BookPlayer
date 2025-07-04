package com.driot.bookplayer.db;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.RawQuery;
import androidx.room.Transaction;
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

    @Query("SELECT COUNT(id) FROM Folder WHERE name LIKE :sFolderName")
    long folderAlreadyExist_checkFolderName(String sFolderName);

    @Query("SELECT COUNT(id) FROM Folder WHERE path LIKE :sFolderPath")
    long folderAlreadyExist_checkFolderPath(String sFolderPath);

    @Query("SELECT path FROM Folder WHERE id =:folderId")
    long getFolderPath(int folderId);

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

    @Query("UPDATE Folder SET position = 0, percentdone = 0, " +
            "lastAccess = datetime('now'), " +
            "lastAccessTime = strftime('%s','now')*1000, " +  // Unix timestamp in milliseconds
            "finished = 0 WHERE id = :id")
    void resetProgression(int id);

    @Update
    void update(Folder Folder);

    @Query("UPDATE Folder SET hash = :hash WHERE path = :path")
    void updateHashForPath(String path, String hash);

    @Query("UPDATE Folder SET hash = :hash WHERE id = :folderId")
    void updateHash(int folderId, String hash);

    // Exemple avec dates :
    //@Query("SELECT * FROM user WHERE birthday BETWEEN :from AND :to")
    //List<User> findUsersBornBetweenDates(Date from, Date to);

}

