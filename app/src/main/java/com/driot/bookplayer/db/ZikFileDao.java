package com.driot.bookplayer.db;

/**
 * created by Antoine Driot -- antoine.driot.com -- on 28/10/20
 *
 *
 * when you add a column.... remake the DB...
 *
 * when you change method signature (par ex: void => int), do Build>Clean Project
 */

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.driot.bookplayer.objects.ZikFileSummary;

import java.util.List;


@Dao
public interface ZikFileDao {

    @Query("SELECT * FROM ZikFile")
    List<ZikFile> getAll();

    //  @Query("SELECT * FROM ZikFile WHERE folderName = :folderName AND REPLACE(name, '_', 'h') = REPLACE(:fileName, '_', 'h') LIMIT 1")
    @Query("SELECT * FROM ZikFile WHERE folderName = :folderName AND name = :fileName LIMIT 1")
    LiveData<ZikFile> getZikFileLive(String folderName, String fileName);

    @Query("SELECT * FROM ZikFile WHERE idFolder = :idFolder ORDER BY zeorder, name")
    List<ZikFile> getZikFiles(long idFolder);

    @Query("SELECT * FROM ZikFile WHERE idFolder = :idFolder AND name >= :startFromName ORDER BY zeorder, name")
    ZikFile[] getNextZikFiles(long idFolder, String startFromName);

    @Query("SELECT * FROM ZikFile WHERE id = :id")
    ZikFile getZikFile(long id);

    //PODCAST LIST
    @Query("SELECT ZikFile.* FROM ZikFile " +
            "INNER JOIN Episode ON Episode.idZikFile = ZikFile.id " +
            "WHERE ZikFile.idFolder = :idFolder " +
            "ORDER BY CAST(Episode.datePublished AS INTEGER) ASC, zeorder, name")
    List<ZikFile> getPodcastZikFilesAsc(long idFolder);

    @Query("SELECT ZikFile.* FROM ZikFile " +
            "INNER JOIN Episode ON Episode.idZikFile = ZikFile.id " +
            "WHERE ZikFile.idFolder = :idFolder " +
            "ORDER BY CAST(Episode.datePublished AS INTEGER) DESC, zeorder, name")
    List<ZikFile> getPodcastZikFilesDesc(long idFolder);

    @Query("""
           SELECT * FROM ZikFile
           WHERE idFolder = (SELECT idFolder FROM Podcast WHERE feedId = :feedId)
             AND lLastAccess > 0
           ORDER BY lLastAccess DESC
           LIMIT 1
           """)
    LiveData<ZikFile> getLastListenedZikFileForPodcast(long feedId);

    @Insert
    long insert(ZikFile zikFile);

    @Delete
    void delete(ZikFile zikFile);

    @Query("DELETE FROM ZikFile WHERE idFolder = :idFolder")
    void deleteAllZikFilesInFolder(int idFolder);

    @Query("DELETE FROM ZikFile WHERE id = :idZikFile")
    void deleteZikFile(int idZikFile);

    @Query("UPDATE ZikFile SET position = 0, percentdone = 0, lFirstAccess = null, lLastAccess=null, finished=0 WHERE idFolder =:idFolder")
    void resetFolderProgression(int idFolder);

    @Query("UPDATE ZikFile SET position = 0, percentdone = 0, lFirstAccess = null, lLastAccess=null, finished=0 WHERE idFolder =:idFolder AND zeorder >= :zeorder")
    void resetProgressionFromThisZikFile(int idFolder, double zeorder);

    @Update
    int update(ZikFile zikFile);

    @Query("UPDATE ZikFile SET FolderName=:folderName WHERE id = :id")
    void updateFolderName(String folderName, int id);

    @Query("SELECT DISTINCT z.path, z.folderName, f.percentdone, z.idFolder, f.sourceLocation " +
            "FROM ZikFile z INNER JOIN Folder f ON z.idFolder = f.id")
    LiveData<List<ZikFileSummary>> getZikFileDistinctLocations();

    /*

    NEVER CHANGE THE NAME... or TRACK will not be able to play, file not found !

    @Query("UPDATE ZikFile SET name=:zikFileName WHERE id = :id")
    void updateZikFileName(String zikFileName, int id);
    */

    @Query("UPDATE ZikFile SET lFirstAccess=:firstAccess WHERE id = :id")
    void updateFirstAccess(long firstAccess, int id);

    @Query("SELECT uri FROM Folder WHERE id = :id")
    String getFolderUri(int id);

    @Query("SELECT path FROM Folder WHERE id = :id")
    String getFolderPath(int id);

    @Query("SELECT path FROM ZikFile WHERE id = :id")
    String getZikFilePath(int id);

    @Query("UPDATE ZikFile SET displayname = :newDisplayName WHERE id =:id")
    void setDisplayName(int id, String newDisplayName);

    @Query("SELECT displayName FROM ZikFile WHERE id = :id")
    String getDisplayName(int id);

    @Query("UPDATE ZikFile SET zeorder = :zeorder WHERE id =:id")
    void changePosition(int id, Double zeorder);

    @Query("SELECT count(*) FROM ZikFile WHERE idFolder = :idFolder ")
    int getCountOfZikFiles(int idFolder);

    @Query("SELECT * FROM ZikFile WHERE idFolder = :idFolder")
    ZikFile getSingleZikFile(long idFolder);

    @Query("SELECT MAX(zeorder) FROM ZikFile WHERE idFolder = :idFolder")
    double getMaxOrder(long idFolder);

    @Query("SELECT id FROM ZikFile WHERE idFolder = :idFolder AND name = :name")
    int getId(long idFolder, String name);

    @Query("SELECT EXISTS(SELECT 1 FROM ZikFile WHERE path = :folderPath AND name = :episodeName LIMIT 1)")
    boolean existsForEpisode(String folderPath, String episodeName);

    @Query("SELECT * FROM ZikFile WHERE path = :folderPath AND name = :episodeName")
    ZikFile getZikFileFromFullPath(String folderPath, String episodeName);

    @Query("SELECT * FROM ZikFile " +
            "WHERE percentDone > :minPercent " +
            "AND lLastAccess IS NOT NULL AND lLastAccess < :thresholdTime " +
            "AND idFolder IN (SELECT id FROM Folder WHERE sourceLocation = 'podcast')")
    List<ZikFile> getListenedPodcastEpisodesToDelete(int minPercent, long thresholdTime);

    @Query("DELETE FROM ZikFile WHERE id IN (:ids)")
    int deleteByIds(List<Long> ids);

    @Query("DELETE FROM ZikFile WHERE id = :id")
    int deleteById(long id);



    // Exemple avec dates :
    //@Query("SELECT * FROM user WHERE birthday BETWEEN :from AND :to")
    //List<User> findUsersBornBetweenDates(Date from, Date to);

}
