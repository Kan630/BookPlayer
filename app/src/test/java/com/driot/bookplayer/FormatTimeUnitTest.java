package com.driot.bookplayer;

import org.junit.Test;

import static com.driot.bookplayer.utils.Tonio.FormatTime;
import static org.junit.Assert.assertEquals;

/**
 * created by Antoine Driot -- antoine.driot.com -- on 06/09/21
 */
public class FormatTimeUnitTest {


    @Test
    public void checkMe() {

        assertEquals("10s", FormatTime(10000.0));
        assertEquals("1m 40s", FormatTime(100000.0));
        assertEquals("2h 46m", FormatTime(10000000.0));

        int i = 35;
        assertEquals("35s", FormatTime(i*1000));

    }
}
