package com.driot.bookplayer.db;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Update;
import androidx.room.Delete;
import androidx.room.Upsert;

import com.driot.bookplayer.librivox.ArchiveItem;

import java.util.List;

@Dao
public interface BookSourceDao {

    @Insert
    long insert(BookSource bookSource);

    @Update
    void update(BookSource bookSource);

    @Delete
    void delete(BookSource bookSource);

    @Query("SELECT * FROM BookSource WHERE id = :id")
    BookSource getById(long id);

    @Query("SELECT * FROM BookSource ORDER BY id DESC")
    List<BookSource> getAll();




    // ---- Toggle favorite (MUST include repoName too) ----
    @Query("""
        UPDATE BookSource
        SET is_favorite = :fav, date_maj = :now
        WHERE repoType = :repoType AND repoName = :repoName AND repoId = :repoId
    """)
    int updateFavoriteFlag(String repoType, String repoName, String repoId, boolean fav, long now);

    // ---- Upsert (Room 2.5+) ----
    @Upsert
    long upsert(BookSource entity);

    // ---- State for a batch of ids (enrich search results) ----
    @Query("""
        SELECT repoId, is_favorite, idFolder
        FROM BookSource
        WHERE repoType = :repoType AND repoName = :repoName AND repoId IN (:ids)
    """)
    List<RepoStateRow> getStateFor(String repoType, String repoName, List<String> ids);

    // Helper projection
    class RepoStateRow {
        public String repoId;
        public boolean is_favorite;
        public Long idFolder;     // null if not imported
    }


    @Query("""
        SELECT 
            repoId AS identifier,
            book_title AS title,
            '' AS date,
            0.0 AS avg_rating,
            0 AS num_reviews,
            idFolder,
            is_favorite,
            '' AS author
        FROM BookSource
        WHERE repoType = :repoType 
          AND repoName = :repoName
          AND (is_favorite = 1 OR idFolder IS NOT NULL)
        ORDER BY date_maj DESC
    """)
    LiveData<List<ArchiveItem>> getFavoriteLibrivoxItems(String repoType, String repoName);



    @Query("""
        UPDATE BookSource
        SET idFolder = :folderId,
            is_favorite = :forceFavorite,
            book_title = :bookTitle,
            source_url = :sourceUrl,
            imageLocal = :imageLocal,
            imageRemote = :imageRemote,
            date_maj = :now
        WHERE repoType = :repoType AND repoName = :repoName AND repoId = :repoId
    """)
    int updateOnIntegration(String repoType, String repoName, String repoId,
                            long folderId, boolean forceFavorite,
                            String bookTitle, String sourceUrl,
                            @Nullable String imageLocal, @Nullable String imageRemote,
                            long now);

    // Default method wrapper (Java 8 interface default)
    @Transaction
    default void markImported(String repoType, String repoName, String repoId,
                              long folderId,
                              String bookTitle, String sourceUrl,
                              @Nullable String imageLocal, @Nullable String imageRemote) {
        long now = System.currentTimeMillis();

        // Try update existing row
        int u = updateOnIntegration(repoType, repoName, repoId,
                folderId, true, // force favorite on import
                bookTitle != null ? bookTitle : "",
                sourceUrl != null ? sourceUrl : "",
                imageLocal, imageRemote, now);

        if (u == 0) {
            // Insert new row with favorite=true
            BookSource bs = new BookSource(
                    bookTitle != null ? bookTitle : "",
                    sourceUrl != null ? sourceUrl : "",
                    repoType, repoName, repoId, folderId
            );
            bs.is_favorite = true;   // new imports are favorited by default
            bs.imageLocal = imageLocal;
            bs.imageRemote = imageRemote;
            bs.date_add = now;
            bs.date_maj = now;
            upsert(bs);
        }
    }

}