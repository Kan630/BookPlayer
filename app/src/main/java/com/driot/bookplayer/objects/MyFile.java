package com.driot.bookplayer.objects;

import android.content.Context;
import android.net.Uri;

import java.io.File;
import java.util.Arrays;

import static com.driot.bookplayer.utils.log.KanLogger.myLogE;
import static com.driot.bookplayer.utils.TonioCommonStuff.deleteExtension;

import androidx.core.content.FileProvider;

import com.driot.bookplayer.utils.log.KanLogger;

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
            myLogEE(e,"error constructeur MyFile");
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

    public static Uri getUriFromMyFile(Context context, MyFile myFile) {
        File file = new File(context.getFilesDir(), "log/" + myFile.getFileName());
                  //new File(context.getFilesDir(), "subfolder/" + myFile.getFileName());

        if (!file.exists()) {
            KanLogger.myLogE("getUriFromMyFile: File does not exist -> " + file.getAbsolutePath());
            return null;
        }

        return FileProvider.getUriForFile(
                context,
                context.getPackageName() + ".FileProvider",
                file
        );
    }
    // ----------------------- LOG -----------------------
    private static final String TAG = "MyFile";
    private static void myLog(String str) { KanLogger.myLog(TAG, str); }
    private static void myLogD(String str) { KanLogger.myLogD(TAG, str); }
    private static void myLogI(String str) { KanLogger.myLogI(TAG, str); }
    private static void myLogW(String str) { KanLogger.myLogW(TAG, str); }
    private static void myLogE(String str) { KanLogger.myLogE(TAG, str); }
    private static void myLogEE(Throwable t, String str) { KanLogger.myLogEE(t, TAG, str); }
    private static void myToastEE(Throwable t, String str) { KanLogger.myToastEE(t, TAG, str); }

}
