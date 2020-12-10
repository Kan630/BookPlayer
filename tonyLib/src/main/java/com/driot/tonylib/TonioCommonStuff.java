package com.driot.tonylib;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;

/**
 * created by Antoine Driot -- antoine.driot.com -- on 10/12/20
 */
public class TonioCommonStuff {


    public static void myLog(String str) {
        Log.d("toto", str);
    }

    public static void myLogE(String str) {
        Log.e("toto", str);
    }

    public static void myToast(Context c, String str) {
        Toast.makeText(c, str, Toast.LENGTH_SHORT).show();
        myLog(str);
    }

    public static void myToastE(Context c, String str) {
        Toast.makeText(c, str, Toast.LENGTH_SHORT).show();
        myLogE(str);
    }

    public static String deleteExtention(String fileName) {
        if (fileName.lastIndexOf(".") > 0) {
            return fileName.substring(0,fileName.lastIndexOf("."));
        } else {
            return fileName;
        }
    }

    public static String getExtention(String fileName) {
        if (fileName.lastIndexOf(".") > 0) {
            return fileName.substring(fileName.lastIndexOf(".") + 1);
        } else {
            return "";
        }
    }
}
