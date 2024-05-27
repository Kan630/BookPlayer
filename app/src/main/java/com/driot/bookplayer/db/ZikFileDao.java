package com.driot.bookplayer.db;

/**
 * created by Antoine Driot -- antoine.driot.com -- on 28/10/20
 *
 *
 * when you add a column.... remake the DB...
 *
 */

import androidx.lifecycle.LiveData;
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

    @Query("SELECT * FROM ZikFile WHERE idFolder = :idFolder ORDER BY zeorder, name")
    List<ZikFile> getZikFiles(long idFolder);

    @Query("SELECT * FROM ZikFile WHERE idFolder = :idFolder AND name >= :startFromName ORDER BY zeorder, name")
    ZikFile[] getNextZikFiles(long idFolder, String startFromName);

    @Query("SELECT * FROM ZikFile WHERE id = :id")
    ZikFile getZikFile(long id);

    @Insert
    long insert(ZikFile zikFile);

    @Delete
    void delete(ZikFile zikFile);

    @Query("DELETE FROM ZikFile WHERE idFolder = :idFolder")
    void deleteFolder(int idFolder);

    @Query("DELETE FROM ZikFile WHERE id = :idZikFile")
    void deleteZikFile(int idZikFile);

    @Query("UPDATE ZikFile SET position = 0, percentdone = 0, firstAccess = null, lastAccess=null, lastAccessTime=null, finished=0 WHERE idFolder =:idFolder")
    void resetFolderProgression(int idFolder);

    @Query("UPDATE ZikFile SET position = 0, percentdone = 0, firstAccess = null, lastAccess=null, lastAccessTime=null, finished=0 WHERE idFolder =:idFolder AND name >= :name")
    void resetProgressionFromThisZikFile(int idFolder, String name);

    @Update
    void update(ZikFile zikFile);

    @Query("UPDATE ZikFile SET FolderName=:folderName WHERE id = :id")
    void updateFolderName(String folderName, int id);

    @Query("select distinct z.path, z.folderName, f.percentdone as percentdone, z.idFolder, null as id, null as position, null as duration, null as size, null as iszipfile, null as finished, null as zeorder from ZikFile z inner join Folder f on z.idFolder = f.id")
    LiveData<List<ZikFile>> getZikFileDistinctLocations(); // for cache files cleaning activity...

    /*

    NEVER CHANGE THE NAME... or TRACK will not be able to play, file not found !

    @Query("UPDATE ZikFile SET name=:zikFileName WHERE id = :id")
    void updateZikFileName(String zikFileName, int id);
    */

    @Query("UPDATE ZikFile SET firstaccess=:firstAccess WHERE id = :id")
    void updateFirstAccess(Time firstAccess, int id);

    @Query("SELECT uri FROM Folder WHERE id = :id")
    String getFolderUri(int id);

    @Query("SELECT path FROM ZikFile WHERE id = :id")
    String getZikFilePath(int id);

    @Query("UPDATE ZikFile SET displayname = :newDisplayName WHERE id =:id")
    void setDisplayName(int id, String newDisplayName);

    @Query("SELECT displayName FROM ZikFile WHERE id = :id")
    String getDisplayName(int id);

    @Query("UPDATE ZikFile SET zeorder = :zeorder WHERE id =:id")
    void changePosition(int id, Double zeorder);




    // Exemple avec dates :
    //@Query("SELECT * FROM user WHERE birthday BETWEEN :from AND :to")
    //List<User> findUsersBornBetweenDates(Date from, Date to);

}
