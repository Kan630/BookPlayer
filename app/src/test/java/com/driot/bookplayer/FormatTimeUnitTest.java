package com.driot.bookplayer;

import org.junit.Test;

import static com.driot.bookplayer.utils.Tonio.formatTime;
import static org.junit.Assert.assertEquals;

import com.driot.bookplayer.utils.Tonio;

/**
 * created by Antoine Driot -- antoine.driot.com -- on 06/09/21
 */
public class FormatTimeUnitTest {


    @Test
    public void checkMe() {

        assertEquals("10s", Tonio.formatTime(10000.0));
        assertEquals("1m 40s", Tonio.formatTime(100000.0));
        assertEquals("2h 46m", Tonio.formatTime(10000000.0));

        int i = 35;
        assertEquals("35s", Tonio.formatTime(i*1000));

    }
}
