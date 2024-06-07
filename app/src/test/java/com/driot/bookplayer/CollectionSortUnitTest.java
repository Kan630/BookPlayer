package com.driot.bookplayer;

import static com.driot.bookplayer.utils.Tonio.FormatTime;
import static org.junit.Assert.assertEquals;

import com.driot.bookplayer.utils.Utils;

import org.junit.Test;

import java.util.ArrayList;

import java.util.Collections;

/**
 * created by Antoine Driot -- antoine.driot.com -- on 06/09/21
 */
public class CollectionSortUnitTest {

    @Test
    public void checkMe() {

        ArrayList<String> audioFileArrayList = new ArrayList<String>();
        /*
        audioFileArrayList.add("toto 04");
        audioFileArrayList.add("toto 01");
        audioFileArrayList.add("toto 07");
        audioFileArrayList.add("toto 02");
        audioFileArrayList.add("titi04");
        audioFileArrayList.add("titi01");
        audioFileArrayList.add("titi07");
        audioFileArrayList.add("titi02");
        audioFileArrayList.add("tutu4");
        audioFileArrayList.add("tutu11");
        audioFileArrayList.add("tutu7");
        audioFileArrayList.add("tutu22");
        */
        /*
        audioFileArrayList.add("le compte 5 / le compte_5_toto_11");
        audioFileArrayList.add("le compte 5 / le compte_5_toto_1");
        audioFileArrayList.add("le compte 5 / le compte_5_toto_7");
        audioFileArrayList.add("le compte 5 / le compte_5_toto_22");
         */
        audioFileArrayList.add("le compte 5aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaale compte_5_toto11");
        audioFileArrayList.add("le compte 5aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaale compte_5_toto1");
        audioFileArrayList.add("le compte 5aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaale compte_5_toto7");
        audioFileArrayList.add("le compte 5aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaale compte_5_toto22");


        Collections.sort(audioFileArrayList, new Utils.AlphanumericComparator());

        System.out.println("Hello, world!");

        for (String s : audioFileArrayList) {
            System.out.println(s);
        }
/*
        assertEquals("toto 01", audioFileArrayList(1));
        assertEquals("1m 40s", FormatTime(100000.0));
        assertEquals("2h 46m", FormatTime(10000000.0));

        int i = 35;
        assertEquals("35s", FormatTime(i*1000));


 */
    }
}
