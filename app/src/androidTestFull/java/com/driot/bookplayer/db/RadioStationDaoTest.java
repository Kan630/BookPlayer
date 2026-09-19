package com.driot.bookplayer.db;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@RunWith(AndroidJUnit4.class)
public class RadioStationDaoTest {

    private AppDatabase db;
    private RadioStationDao dao;

    @Before
    public void createDb() {
        Context context = ApplicationProvider.getApplicationContext();
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase.class).build();
        dao = db.radioStationDao();
    }

    @After
    public void closeDb() {
        db.close();
    }

    @Test
    public void testGetFavoritesByLastPlayed() {
        // 1. Create stations
        RadioStation s1 = new RadioStation();
        s1.stationuuid = "uuid1";
        s1.name = "Station A";
        s1.isFavorite = true;
        s1.date_last_played = 1000L;

        RadioStation s2 = new RadioStation();
        s2.stationuuid = "uuid2";
        s2.name = "Station B";
        s2.isFavorite = true;
        s2.date_last_played = 2000L;

        RadioStation s3 = new RadioStation();
        s3.stationuuid = "uuid3";
        s3.name = "Station C";
        s3.isFavorite = false;
        s3.date_last_played = 3000L;

        RadioStation s4 = new RadioStation();
        s4.stationuuid = "uuid4";
        s4.name = "Station D";
        s4.isFavorite = true;
        s4.date_last_played = null;

        dao.insert(s1);
        dao.insert(s2);
        dao.insert(s3);
        dao.insert(s4);

        // 2. Fetch favorites by last played
        List<RadioStation> favorites = dao.getFavoritesByLastPlayed();

        // 3. Verify
        assertEquals(3, favorites.size());
        assertEquals("Station B", favorites.get(0).name); // Played at 2000
        assertEquals("Station A", favorites.get(1).name); // Played at 1000
        assertEquals("Station D", favorites.get(2).name); // Not played yet (null)
    }
}
