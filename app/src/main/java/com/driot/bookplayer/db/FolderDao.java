package com.driot.bookplayer.db;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.RawQuery;
import androidx.room.Update;
import androidx.sqlite.db.SupportSQLiteQuery;

import com.driot.bookplayer.objects.FolderSummary;

import java.util.List;

/**
 * created by Antoine Driot -- antoine.driot.com -- on 28/10/20
 * LiveData added oct 2023
 */

@Dao
public interface FolderDao {

    @Query("SELECT * FROM Folder ORDER BY lLastAccess DESC")
    List<Folder> getAll();

    @Query("SELECT * FROM Folder ORDER BY lLastAccess DESC")
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
    @Query("UPDATE ZikFile SET folderName = :newFolderName WHERE idFolder = :folderId")
    void updateFolderNameInZikFile(int folderId, String newFolderName);

    @Query("UPDATE Folder SET position = 0, percentdone = 0, " +
            "llastaccess = strftime('%s','now')*1000, " +
            "finished = 0 WHERE id = :id")
    void resetProgression(int id);

    @Update
    void update(Folder Folder);

    @Query("UPDATE Folder SET hash = :hash WHERE path = :path")
    void updateHashForPath(String path, String hash);

    @Query("UPDATE Folder SET hash = :hash WHERE id = :folderId")
    void updateHash(int folderId, String hash);

    @Query("SELECT name FROM Folder WHERE originalHash LIKE :originalHash")
    String originalHashAlreadyExist_getBookName(String originalHash);

    @Query("SELECT name FROM Folder WHERE path LIKE :sFolderPath")
    String folderAlreadyExist_checkFolderPath_getBookName(String sFolderPath);

    @Query("SELECT * FROM Folder WHERE name LIKE :name")
    Folder getByName(String name);

    @Query("SELECT * FROM Folder WHERE id = :folderId")
    Folder getById(long folderId);

    @Query("SELECT COUNT(*) FROM Folder WHERE image LIKE '%' || :imageName")
    boolean doesImageExist(String imageName);


    @Query("UPDATE Folder SET lLastAccess = :timestamp WHERE id = :folderId")
    void updateLastAccess(int folderId, long timestamp);

    @Query("SELECT * FROM Folder WHERE image LIKE 'http%'")
    List<Folder> getAllWithRemoteImage();

    @Query("UPDATE Folder SET image = :imagePath WHERE id = :id")
    void updateImage(int id, String imagePath);

    @Query("SELECT COUNT(*) FROM Folder WHERE hash = :hash")
    boolean hashExists(String hash);

    // Exemple avec dates :
    //@Query("SELECT * FROM user WHERE birthday BETWEEN :from AND :to")
    //List<User> findUsersBornBetweenDates(Date from, Date to);

    @Query("SELECT EXISTS(SELECT 1 FROM Folder WHERE path = :path LIMIT 1)")
    boolean existsByPath(String path);

    // Optional: by original hash if you use it consistently
    @Query("SELECT EXISTS(SELECT 1 FROM Folder WHERE originalHash = :hash LIMIT 1)")
    boolean existsByOriginalHash(String hash);


    @Query("SELECT DISTINCT path, name, id, percentdone as percentDone, sourceLocation, playType, image FROM Folder")
    LiveData<List<FolderSummary>> getFoldersForCleaning();

    @Query("SELECT * FROM Folder WHERE id = :id")
    LiveData<Folder> observeById(long id);

    @Query("SELECT EXISTS(SELECT 1 FROM Folder WHERE playType = 'text')")
    boolean hasSomeTtsBook();

}

