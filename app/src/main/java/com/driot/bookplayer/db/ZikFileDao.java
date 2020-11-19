package com.driot.bookplayer.db;

/**
 * created by Antoine Driot -- antoine.driot.com -- on 28/10/20
 */

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.sql.Time;
import java.util.List;


@Dao
public interface ZikFileDao {

    @Query("SELECT * FROM ZikFile")
    List<ZikFile> getAll();

    @Query("SELECT * FROM ZikFile WHERE idFolder = :idFolder ORDER BY name")
    List<ZikFile> getZikFiles(long idFolder);

    @Query("SELECT * FROM ZikFile WHERE idFolder = :idFolder AND name >= :startFromName ORDER BY name")
    ZikFile[] getNextZikFiles(long idFolder, String startFromName);

    @Query("SELECT * FROM ZikFile WHERE id = :id")
    ZikFile getZikFile(long id);

    @Insert
    long insert(ZikFile zikFile);

    @Delete
    void delete(ZikFile zikFile);

    @Query("DELETE FROM ZikFile WHERE idFolder = :idFolder")
    void deleteFolder(int idFolder);

    @Query("UPDATE ZikFile SET position = 0, percentdone = 0, firstAccess = null, lastAccess=null, lastAccessTime=null, finished=0 WHERE idFolder =:idFolder")
    void resetFolderProgression(int idFolder);

    @Update
    void update(ZikFile zikFile);

    @Query("UPDATE ZikFile SET FolderName=:folderName WHERE id = :id")
    void updateFolderName(String folderName, int id);

    @Query("UPDATE ZikFile SET firstaccess=:firstAccess WHERE id = :id")
    void updateFirstAccess(Time firstAccess, int id);



    // Exemple avec dates :
    //@Query("SELECT * FROM user WHERE birthday BETWEEN :from AND :to")
    //List<User> findUsersBornBetweenDates(Date from, Date to);

}
