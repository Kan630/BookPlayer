package com.driot.bookplayer.utils;

import android.util.Log;
import android.webkit.MimeTypeMap;

import androidx.annotation.NonNull;

import java.io.File;
import java.sql.Date;
import java.sql.Time;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * created by Antoine Driot -- antoine.driot.com -- on 31/10/20
 */
public class Tonio {

    private void myLog(String str) {
        String TAG = this.getClass().getName().substring(this.getClass().getName().lastIndexOf(".")+1);
        Log.d("titi " + TAG + " ",str);
        System.out.println(str);
    }

    public static String FormatTime(double doubleTime) {
        String s;
        long sec,min,hou;
        if (doubleTime>0) {

            hou = TimeUnit.MILLISECONDS.toHours((long) doubleTime);
            min = TimeUnit.MILLISECONDS.toMinutes((long) doubleTime)-TimeUnit.HOURS.toMinutes(hou);
            sec = TimeUnit.MILLISECONDS.toSeconds((long) doubleTime)-TimeUnit.HOURS.toSeconds(hou)-TimeUnit.MINUTES.toSeconds(min);
            if (hou !=0) {
                s = String.format(Locale.getDefault(),"%dh %dm", hou, min);
            } else if (min !=0) {
                s = String.format(Locale.getDefault(), "%dm %ds", min, sec);
            } else {
                s = String.format(Locale.getDefault(), "%ds", sec);
            }

        } else {
            s = "";
        }
        return s;
    }

    public static String FormatPercentString(Double d) {
        String str;
        if (d != null) {
            if (d == 100.0) {
                str = "100 %";
            } else if (d == 0.0) {
                str = "";
            } else if (d < 10.0) {
                str = d.toString().substring(0, 3);
                str = str + " %";
            } else {
                str = d.toString().substring(0, 2);
                str = str + " %";
            }
        } else {
            str = "";
        }
        return str;
    }
    public static int FormatPercentForProgressBar(Double d) {
        int i;
        if (d != null) {
            i = d.intValue();
            if (i < 0) {
                i = 0;
            }
            if (i > 100) {
                i = 100;
            }
        } else {
            i = 0;
        }
        return i;
    }
    public static double FormatPercentDouble(Double d) {
        if (d != null) {
            d = d * 100;
            if (d < 0) {
                d = 0.0;
            }
            if (d > 100) {
                d = 100.0;
            }
        } else {
            d = 0.0;
        }
        return d;
    }
    public static String FormatLastAccess(Date d, Time t, String NameForYesterday) {
        String s;
        if (d != null && t != null) {
            Date d2 = new Date(System.currentTimeMillis());
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.DATE,-1);
            Date d3 = new Date(cal.getTimeInMillis()); // yesterday
            String s1 = d.toString();
            String s2 = d2.toString();
            String s3 = d3.toString();
            // check if date same as today
            if (s1.equals(s2)) {
                // Give time :
                s = t.toString();
                s = s.substring(0, 5);
                // check if yesterday
            } else if (s1.equals(s3)) {
                //RelativeDateTimeFormatter fmt = RelativeDateTimeFormatter.getInstance(); // require API 24
                //fmt.format(Direction.LAST, AbsoluteUnit.DAY);
                s = NameForYesterday;
                // give name of the day
            } else if ((d2.getTime()-d.getTime())/ (1000 * 60 * 60 * 24)<7) {
                // give name of the day
                SimpleDateFormat outFormat = new SimpleDateFormat("EEEE");
                s = outFormat.format(d);
            } else {
                //give date :
                SimpleDateFormat simpleDate =  new SimpleDateFormat("dd/MM/yyyy");
                s = simpleDate.format(d);
            }
        } else {
            s = " ";
        }
        return s;
    }

    public static String stripExtension(String fileName) {
        String s = fileName;
        if (s.indexOf(".") > 0) {
            s = s.substring(0, s.lastIndexOf("."));
        }
        s = s.replace("_"," ");
        return s;
    }

    public static String getExtension(String fileName) {
        String s = fileName;
        int pos = s.lastIndexOf(".");
        if (pos > 0) {
            s = s.substring(pos);
        } else {
            s="";
        }
        return s;
    }

    @NonNull
    public static String getMimeType(@NonNull File file) {
        String type = null;
        final String url = file.toString();
        final String extension = MimeTypeMap.getFileExtensionFromUrl(url);
        if (extension != null) {
            type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.toLowerCase());
        }
        if (type == null) {
            type = "*/*"; // fallback type. You might set it to image/*
        }
        return type;
    }

    public static boolean fileExists(String filePath) {
        File file = new File(filePath);
        return file.exists();
    }

}
