package com.driot.bookplayer.utils;

import android.content.Context;

import java.util.Arrays;

import static com.driot.bookplayer.utils.KanLogger.myLogE;
import static com.driot.bookplayer.utils.TonioCommonStuff.deleteExtension;

/**
 * created by Antoine Driot -- antoine.driot.com -- on 21/08/21
 */


//   log/kanlog_2021-08-21.txt


public class MyFile {

    private final String fileName;
    private final String date;
    private final String title;

    private String text;

    public MyFile(Context c, String fileName) {

        this.fileName = fileName;
        String str;
        String[] separated = new String[4];
        Arrays.fill(separated, "");
        try {
            str = deleteExtension(fileName);
            separated = str.split("_");
        } catch (Exception e) {
            myLogE("error constructeur MyFile :" + e.getMessage());
        }
        this.date = separated[1];
        this.title = separated[0];

        //this.extention = getExtention(fileName);
        //myLog("added :" + fileName + "  =>  " + date + " / " + title + " / " + autorite);
    }

    public String getDate() {
        return date;
    }

    public String getTitle() {
        return title;
    }

    public String getText() {
        return text;
    }

    public String getFileName() {
        return fileName;
    }

    //public String getExtention() {
    //return extention;
    //}

}
