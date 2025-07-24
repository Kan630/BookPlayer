package com.driot.bookplayer.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;
import androidx.room.Delete;

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
}