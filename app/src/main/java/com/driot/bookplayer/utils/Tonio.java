package com.driot.bookplayer.utils;

import static com.driot.bookplayer.utils.Tonio2.removeLongDuplicates;

import android.os.Environment;
import android.os.StatFs;
import android.webkit.MimeTypeMap;

import androidx.annotation.NonNull;

import java.io.File;
import java.sql.Date;
import java.sql.Time;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * created by Antoine Driot -- antoine.driot.com -- on 31/10/20
 */
public class Tonio {

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


    public static String FormatPercentStringForSpeed(Double d) {
        String str = "";
        if (d != null) {
            str = Math.round(d) + "%";
        }
        return str;
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

    public static String getFileNameFromPath(String fileName) {
        File file = new File(fileName);
        return file.getName();
    }

    public static String FormatNameForDisplay(String s) {
        s = stripExtension(s);
        s = removeLongDuplicates(s,10);
        s = s.replace("_", " ");
        return s;
    }

    public static String FormatNameForDisplay_withUnderscore(String s) {
        s = stripExtension(s);
        s = removeLongDuplicates(s,10);
        s = s.replace(" ", "_");
        return s;
    }

    public static String stripFileName(String fullPath) {
            // Find the last index of the file separator (either backslash or forward slash)
            int lastIndex = fullPath.lastIndexOf(File.separator);
            // Check if a file separator was found and get the directory path
            return (lastIndex != -1) ? fullPath.substring(0, lastIndex) : fullPath;
    }

    public static String stripExtension(String fileName) {
        String s = fileName;
        if (s.indexOf(".") > 0) {
            s = s.substring(0, s.lastIndexOf("."));
        }
        return s;
    }

    public static String getExtension(String fileName) {
        String s = fileName;
        int pos = s.lastIndexOf(".");
        if (pos > 0) {
            s = s.substring(pos+1);
        } else {
            s="";
        }
        return s;
    }

    @NonNull
    public static String getMimeType(String fileName) {
        String type = null;
        final String extension = getExtension(fileName);
            type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.toLowerCase());
        if (type == null) type = "*/*";
        return type;
    }

    public static boolean fileExists(String filePath) {
        File file = new File(filePath);
        return file.exists();
    }

    public static long getAvailableInternalMemorySize() {
        File path = Environment.getDataDirectory();
        StatFs stat = new StatFs(path.getPath());
        long blockSize = stat.getBlockSizeLong();
        long availableBlocks = stat.getAvailableBlocksLong();
        return availableBlocks * blockSize;
    }

    public static String formatMem(long mem){
        return NumberFormat.getNumberInstance(Locale.getDefault()).format(mem);
    }

}
