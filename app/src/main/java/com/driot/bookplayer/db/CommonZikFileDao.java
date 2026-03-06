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
import androidx.room.Transaction;
import androidx.room.Update;

import com.driot.bookplayer.objects.AudioFileInfo;

import java.util.Collections;
import java.util.List;

@Dao
public interface CommonZikFileDao {

    @Query("SELECT * FROM ZikFile")
    List<ZikFile> getAll();

    @Query("SELECT * FROM ZikFile WHERE idFolder = :folderId ORDER BY zeorder, name")
    LiveData<List<ZikFile>> getZikFilesLive(long folderId);

    // @Query("SELECT * FROM ZikFile WHERE folderName = :folderName AND
    // REPLACE(name, '_', 'h') = REPLACE(:fileName, '_', 'h') LIMIT 1")
    @Query("SELECT * FROM ZikFile WHERE folderName = :folderName AND name = :fileName LIMIT 1")
    LiveData<ZikFile> getZikFileLive(String folderName, String fileName);

    @Query("SELECT * FROM ZikFile WHERE idFolder = :idFolder ORDER BY zeorder, name")
    List<ZikFile> getZikFiles(long idFolder);

    // Alias for clarity in sharing context
    default List<ZikFile> getZikFilesForFolder(long folderId) {
        return getZikFiles(folderId);
    }

    @Query("SELECT * FROM ZikFile WHERE idFolder = :idFolder AND name >= :startFromName ORDER BY zeorder, name")
    ZikFile[] getNextZikFiles(long idFolder, String startFromName);

    @Query("SELECT * FROM ZikFile WHERE id = :id")
    ZikFile getById(long id);

    @Query("""
            SELECT * FROM ZikFile
            WHERE idFolder = :folderId
            ORDER BY (position > 0 AND position < duration - 5000) DESC, lLastAccess DESC
            LIMIT 1
            """)
    ZikFile getLastListenedZikFileOfFolder(long folderId);

    @Query("""
            SELECT * FROM ZikFile
            ORDER BY (position > 0 AND position < duration - 5000) DESC, lLastAccess DESC
            LIMIT 1
            """)
    ZikFile getLastListenedZikFile();

    @Insert
    long insert(ZikFile zikFile);

    @Delete
    void delete(ZikFile zikFile);

    @Query("DELETE FROM ZikFile WHERE idFolder = :idFolder")
    void deleteAllZikFilesInFolder(long idFolder);

    @Query("DELETE FROM ZikFile WHERE id = :idZikFile")
    void deleteZikFile(long idZikFile);

    @Update
    int update(ZikFile zikFile);

    @Query("UPDATE ZikFile SET FolderName=:folderName WHERE id = :id")
    void updateFolderName(String folderName, long id);

    /*
     * 
     * NEVER CHANGE THE NAME... or TRACK will not be able to play, file not found !
     * 
     * @Query("UPDATE ZikFile SET name=:zikFileName WHERE id = :id")
     * void updateZikFileName(String zikFileName, int id);
     */

    @Query("UPDATE ZikFile SET lFirstAccess=:firstAccess WHERE id = :id")
    void updateFirstAccess(long firstAccess, long id);

    @Query("SELECT uri FROM Folder WHERE id = :id")
    String getFolderUri(int id);

    @Query("SELECT path FROM Folder WHERE id = :id")
    String getFolderPath(int id);

    @Query("SELECT path FROM ZikFile WHERE id = :id")
    String getZikFilePath(long id);

    @Query("UPDATE ZikFile SET displayname = :newDisplayName WHERE id =:id")
    void setDisplayName(long id, String newDisplayName);

    @Query("SELECT displayName FROM ZikFile WHERE id = :id")
    String getDisplayName(long id);

    @Query("SELECT MAX(zeorder) FROM ZikFile WHERE idFolder = :idFolder")
    double getMaxOrder(long idFolder);

    @Query("SELECT id FROM ZikFile WHERE idFolder = :idFolder AND name = :name")
    int getId(long idFolder, String name);

    @Query("SELECT * FROM ZikFile " +
            "WHERE percentDone > :minPercent " +
            "AND lLastAccess IS NOT NULL AND lLastAccess < :thresholdTime " +
            "AND idFolder IN (SELECT id FROM Folder WHERE sourceLocation = 'podcast')")
    List<ZikFile> getListenedPodcastEpisodesToDelete(int minPercent, long thresholdTime);

    @Query("DELETE FROM ZikFile WHERE id = :id")
    int deleteById(long id);

    @Query("SELECT COUNT(*) FROM ZikFile WHERE idFolder=:folderId")
    int countTracks(long folderId);

    @Query("SELECT * FROM ZikFile WHERE idFolder=:folderId ORDER BY zeorder, name, id LIMIT 1")
    ZikFile getFirstInFolder(long folderId);

    // Exemple avec dates :
    // @Query("SELECT * FROM user WHERE birthday BETWEEN :from AND :to")
    // List<User> findUsersBornBetweenDates(Date from, Date to);

    @Query("UPDATE ZikFile SET zeorder = :order WHERE id = :id")
    void updateZeorderById(long id, int order);

    @Transaction
    default void persistOrder(List<ZikFile> inOrder) {
        int i = 1;
        for (ZikFile z : inOrder) {
            updateZeorderById(z.getId(), i++);
        }
    }

    // NEW: reset order of one folder using the same SMART_CHAPTER comparator
    @Transaction
    default void resetSmartChapterOrderForFolder(long folderId) {
        // Load current tracks
        List<ZikFile> files = getZikFiles(folderId);
        if (files == null || files.size() <= 1)
            return;

        // Sort using the *same* logic as when importing (SMART_CHAPTER_COMPARATOR)
        Collections.sort(files, (z1, z2) -> {
            String p1 = buildDisplayPathKey(z1);
            String p2 = buildDisplayPathKey(z2);

            // We only care about displayPath for the comparator, so
            // duration/contentUri/meta can be dummy
            AudioFileInfo a1 = new AudioFileInfo(p1, 0L, "", null);
            AudioFileInfo a2 = new AudioFileInfo(p2, 0L, "", null);

            return AudioFileInfo.SMART_CHAPTER_COMPARATOR.compare(a1, a2);
        });

        // Persist new zeorder (1,2,3,...)
        persistOrder(files);
    }

    // helper used inside the default method (kept package-private to avoid
    // visibility issues)
    static String buildDisplayPathKey(ZikFile z) {
        if (z.getDisplayName() != null && !z.getDisplayName().isEmpty()) {
            return z.getDisplayName();
        }
        if (z.getName() != null && !z.getName().isEmpty()) {
            return z.getName();
        }
        return (z.getPath() != null) ? z.getPath() : "";
    }

    /**
     * Insert only if no ZikFile with the same name exists.
     * 
     * @return existing id if found, or new id if inserted.
     */
    @Query("SELECT id FROM ZikFile WHERE name = :name LIMIT 1")
    Long findIdByName(String name);

    @Transaction
    default long insertIfNameNotExists(ZikFile zikFile) {
        Long existingId = findIdByName(zikFile.getName());
        if (existingId != null) {
            return existingId; // or return -1 if you prefer "no insert done"
        }
        return insert(zikFile);
    }

    // -----------------------------------------------------
    /// PROGRESS
    // -----------------------------------------------------

    @Query("UPDATE ZikFile SET position = 0, percentdone = 0, lFirstAccess = null, lLastAccess=null, finished=0 WHERE idFolder =:idFolder")
    void resetFolderProgression(long idFolder);

    @Query("UPDATE ZikFile SET position = 0, percentdone = 0, lFirstAccess = null, lLastAccess=null, finished=0 WHERE id =:id")
    void resetProgression(long id);

    @Query("DELETE FROM PlayTick WHERE zikFileId = :id")
    void deletePlayTicks(long id);

    @Query("DELETE FROM PlaySession WHERE zikFileId = :id")
    void deletePlaySessions(long id);

    @Query("UPDATE ZikFile SET position = 0, percentdone = 0, lFirstAccess = null, lLastAccess=null, finished=0 WHERE idFolder =:idFolder AND zeorder >= :zeorder")
    void resetProgressionFromThisZikFile(long idFolder, double zeorder);

    @Query("""
                DELETE FROM PlayTick
                WHERE zikFileId IN (
                    SELECT id FROM ZikFile
                    WHERE idFolder = :idFolder
                      AND zeorder >= :zeorder
                )
            """)
    void deletePlayTicksFromZikFileOrder(long idFolder, double zeorder);

    @Query("""
                DELETE FROM PlaySession
                WHERE zikFileId IN (
                    SELECT id FROM ZikFile
                    WHERE idFolder = :idFolder
                      AND zeorder >= :zeorder
                )
            """)
    void deletePlaySessionsFromZikFileOrder(long idFolder, double zeorder);

    @Transaction
    default void resetProgressionFully(long id) {
        deletePlayTicks(id);
        deletePlaySessions(id);
        resetProgression(id);
    }

    @Transaction
    default void resetProgressionFromThisZikFileFully(long idFolder, double zeorder) {
        deletePlayTicksFromZikFileOrder(idFolder, zeorder);
        deletePlaySessionsFromZikFileOrder(idFolder, zeorder);
        resetProgressionFromThisZikFile(idFolder, zeorder);
    }

}
