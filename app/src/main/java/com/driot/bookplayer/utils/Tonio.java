package com.driot.bookplayer.utils;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.webkit.MimeTypeMap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;

import com.driot.bookplayer.R;
import com.driot.bookplayer.helpers.LocaleHelper;
import com.driot.bookplayer.helpers.StorageHelper;
import static com.driot.bookplayer.utils.log.LoggerStaticHelper.*;

import java.io.File;
import java.sql.Date;
import java.text.DateFormat;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * created by Antoine Driot -- antoine.driot.com -- on 31/10/2020
 */
public class Tonio {

    public static String getReadableSize(String size) {
        try {
            long bytes = Long.parseLong(size);
            return getReadableSize(bytes);
        } catch (Exception e) {
            return "Unknown size";
        }
    }

    public static String getReadableSize(long sizeBytes) {
        if (sizeBytes <= 0)
            return "0 B";
        if (sizeBytes < 1024)
            return sizeBytes + " B";
        if (sizeBytes < 1024 * 1024)
            return String.format(Locale.US, "%.1f KB", sizeBytes / 1024.0);
        if (sizeBytes < 1024L * 1024L * 1024L)
            return String.format(Locale.US, "%.1f MB", sizeBytes / (1024.0 * 1024.0));
        return String.format(Locale.US, "%.1f GB", sizeBytes / (1024.0 * 1024.0 * 1024.0));
    }

    public static String getReadableSizeForCleanActivity(long sizeBytes) {
        // if (sizeBytes <= 0) return "0 B";

        double value;
        String unit;

        if (sizeBytes < 1024) {
            value = sizeBytes;
            unit = "B";
        } else if (sizeBytes < 1024L * 1024L) {
            value = sizeBytes / 1024.0;
            unit = "KB";
        } else if (sizeBytes < 1024L * 1024L * 1024L) {
            value = sizeBytes / (1024.0 * 1024.0);
            unit = "MB";
        } else {
            value = sizeBytes / (1024.0 * 1024.0 * 1024.0);
            unit = "GB";
        }

        // %7.3g = largeur min 7, 3 chiffres significatifs
        return String.format(Locale.US, "%5.3g %s", value, unit);
    }

    public static String formatTime(double doubleTimeMS) {
        return formatTime(doubleTimeMS, false, true);
    }

    public static String formatTime(double doubleTimeMS, boolean doDisplaySec) {
        return formatTime(doubleTimeMS, doDisplaySec, true);
    }

    public static String formatTime(double doubleTimeMS, boolean doDisplaySec, boolean doDisplayMin) {
        String s;
        long sec, min, hou, day, year;

        if (doubleTimeMS > 0) {

            long ms = (long) doubleTimeMS;

            year = ms / (365L * 24L * 3600L * 1000L);
            ms -= year * (365L * 24L * 3600L * 1000L);

            day = TimeUnit.MILLISECONDS.toDays(ms);
            ms -= TimeUnit.DAYS.toMillis(day);

            hou = TimeUnit.MILLISECONDS.toHours(ms);
            min = TimeUnit.MILLISECONDS.toMinutes(ms) - TimeUnit.HOURS.toMinutes(hou);
            sec = TimeUnit.MILLISECONDS.toSeconds(ms) - TimeUnit.HOURS.toSeconds(hou) - TimeUnit.MINUTES.toSeconds(min);

            if (year != 0) {
                // Example: "1y 23d"
                s = String.format(Locale.US, "%dy %dd", year, day);
            } else if (day != 0) {
                // Example: "3d 4h"
                s = String.format(Locale.US, "%dd %dh", day, hou);
            } else if (hou != 0) {
                if (!doDisplayMin) {
                    s = String.format(Locale.US, "%dh", hou);
                } else if (!doDisplaySec) {
                    s = String.format(Locale.US, "%dh %dm", hou, min);
                } else {
                    s = String.format(Locale.US, "%dh %dm %ds", hou, min, sec);
                }
            } else if (min != 0) {
                s = String.format(Locale.US, "%dm %ds", min, sec);
            } else {
                s = String.format(Locale.US, "%ds", sec);
            }
        } else {
            s = "";
        }
        return s;
    }

    public static String formatPercentStringForSpeed(Double d) {
        String str = "";
        if (d != null) {
            str = Math.round(d) + "%";
        }
        return str;
    }

    public static String formatPercentString(Double d) {
        if (d == null || d == 0.0)
            return "";
        if (d == 100.0)
            return "100 %";
        return String.format(Locale.US, "%.1f %%", d);
    }

    public static int formatPercentForProgressBar(Double d) {
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

    public static String getCurrentDateTimeString() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd_HH'h'mm'm'ss's'", Locale.US);
        return sdf.format(new java.util.Date());
    }

    public static String formatLastAccess(Long lastAccess, Context context) {
        String s;
        if (lastAccess != null && lastAccess > 0) {
            Date accessDate = new Date(lastAccess);
            Date today = new Date(System.currentTimeMillis());

            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.DATE, -1);
            Date yesterday = new Date(cal.getTimeInMillis());

            Locale locale = LocaleHelper.getLocale(context);
            SimpleDateFormat dayFormat = new SimpleDateFormat("yyyy-MM-dd", locale);
            String accessDay = dayFormat.format(accessDate);
            String todayDay = dayFormat.format(today);
            String yesterdayDay = dayFormat.format(yesterday);

            if (accessDay.equals(todayDay)) {
                SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", locale);
                s = timeFormat.format(accessDate);
            } else if (accessDay.equals(yesterdayDay)) {
                s = context.getString(R.string.yesterday);
            } else if ((today.getTime() - accessDate.getTime()) / (1000 * 60 * 60 * 24) < 7) {
                SimpleDateFormat weekdayFormat = new SimpleDateFormat("EEEE", locale);
                s = weekdayFormat.format(accessDate);
            } else {
                s = accessDay;
            }
        } else {
            s = " ";
        }
        return s;
    }

    public static String formatLastAccessInDays(Long lastAccess, Context context) {
        String zeReturn = context.getString(R.string.never_accessed);
        try {
            if (lastAccess != null && lastAccess <= 0)
                return zeReturn;

            long now = System.currentTimeMillis();
            long diffInMillis = now - lastAccess;
            long diffInDays = diffInMillis / (1000 * 60 * 60 * 24);

            if (diffInDays > 400) {
                int years = (int) (diffInDays / 365);
                zeReturn = context.getResources().getQuantityString(R.plurals.years_ago, years, years);
            } else if (diffInDays > 50) {
                int months = (int) (diffInDays / 30);
                zeReturn = context.getResources().getQuantityString(R.plurals.months_ago, months, months);
            } else {
                zeReturn = context.getResources().getQuantityString(R.plurals.days_ago, (int) diffInDays,
                        (int) diffInDays);
            }
        } catch (Exception e) {
            myLogEE(e, "formatLastAccessInDays");
        }
        return zeReturn;
    }

    public static String formatLastAccessAsDate(Long lastAccess, Context context) {
        if (lastAccess == null || lastAccess <= 0) {
            return context.getString(R.string.never_accessed);
        }

        try {
            Date date = new Date(lastAccess);
            DateFormat dateFormat = DateFormat.getDateInstance(DateFormat.MEDIUM, LocaleHelper.getLocale(context));
            return dateFormat.format(date);
        } catch (Exception e) {
            return context.getString(R.string.invalid_date);
        }
    }

    public static String getFileNameFromPath(String fileName) {
        File file = new File(fileName);
        return file.getName();
    }

    public static String formatNameForDisplay(String s) {
        return formatNameForDisplay(s, true);
    }

    public static String formatNameForDisplay(String s, boolean stripExtension) {
        if (stripExtension)
            s = stripExtension(s);
        s = removeLongDuplicates(s, 10);

        // Format numbered prefixes like "002_" or "0002_" as "[002] - " for better
        // readability
        Matcher matcher = Pattern.compile("^(\\d{3,})_(.*)").matcher(s);
        if (matcher.matches()) {
            String prefix = matcher.group(1);
            String rest = matcher.group(2).trim();
            s = "[" + prefix + "] - " + rest;
        } else {
            s = s.replace("_", " ");
        }
        return s;
    }

    /**
     * librivox and other audiobooks often have better titles in metadata than file
     * names.
     * but sometimes it's the opposite (metadata is just "1" or same as filename).
     */
    /**
     * librivox and other audiobooks often have better titles in metadata than file
     * names.
     * but sometimes it's the opposite (metadata is just "1" or same as filename).
     */
    public static boolean isBetterTitle(@Nullable String metaTitle, @Nullable String filenameDisplay,
            boolean isLibrivox) {
        if (metaTitle == null || metaTitle.trim().isEmpty())
            return false;
        if (filenameDisplay == null || filenameDisplay.trim().isEmpty())
            return true;

        String m = metaTitle.trim();
        String f = filenameDisplay.trim();

        // if they are the same (ignoring case), metadata isn't "better"
        if (m.equalsIgnoreCase(f))
            return false;

        // Strip noise from filename for comparison (LibriVox often adds
        // bitrate/author/identifier)
        String fClean = f.toLowerCase(Locale.ROOT)
                .replaceAll("\\d+kb", "") // remove 64kb, 128kb etc
                .replaceAll("[._-]", " ")
                .replaceAll("\\s+", " ")
                .trim();

        int mWords = m.split("\\s+").length;
        int fWords = fClean.split("\\s+").length;

        // If LibriVox, we are more aggressive
        if (isLibrivox) {
            // Pattern "14 - A young Rajah" or "Chapter 016" or "01 - Bk. II..."
            if (m.contains(" - ") || m.contains(": "))
                return true;
            if (m.toLowerCase(Locale.ROOT)
                    .matches(".*(chapter|section|kapitel|chapitre|capitulo|parte|book|bk|vol).*"))
                return true;
            if (mWords > fWords)
                return true;
            if (m.length() > fClean.length() + 2)
                return true;
        }

        // if metadata is just a number, it's probably not better than a filename
        if (m.matches("\\d+"))
            return false;

        // Conservative improvements for all sources (e.g. if filename is very messy)
        if (mWords > fWords + 3)
            return true;
        if (m.length() > fClean.length() + 15)
            return true;

        // if filename contains underscores and metadata looks like plain English and
        // is decent length
        if (f.contains("_") && !m.contains("_") && m.length() > 8)
            return true;

        return false;
    }

    public static String formatDateForDisplay(long timestamp) {
        Date date = new Date(timestamp);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US); // any local would work, just digits
                                                                                    // here
        return sdf.format(date);
    }

    public static String FormatNameForDisplay_withUnderscore(String s) {
        s = stripExtension(s);
        s = removeLongDuplicates(s, 10);
        s = s.replace(" ", "_");
        return s;
    }

    // Garde que le path
    public static String stripFileName(String fullPath) {
        // Find the last index of the file separator (either backslash or forward slash)
        int lastIndex = -1;
        if (fullPath != null) {
            lastIndex = fullPath.lastIndexOf(File.separator);
        }
        // Check if a file separator was found and get the directory path
        return (lastIndex != -1) ? fullPath.substring(0, lastIndex) : fullPath;
    }

    public static String stripExtension(String fileName) {
        String s = fileName.trim();
        if (s.indexOf(".") > 0) {
            s = s.substring(0, s.lastIndexOf("."));
        }
        return s;
    }

    public static String getExtension(String fileName) {
        String s = fileName;
        int pos = s.lastIndexOf(".");
        if (pos > 0) {
            s = s.substring(pos + 1);
        } else {
            s = "";
        }
        return s.toLowerCase();
    }

    public static String getSubFolders(String strFrom, String strPath) {
        myLog("getSubFolders key = " + strFrom + " , Path = " + strPath);
        String ret = strPath;
        ret = Tonio.stripFileName(ret);
        int pos = ret.indexOf(strFrom);
        if (pos > 0) {
            ret = ret.substring(pos + strFrom.length());
        } else {
            ret = "";
        }
        myLog("getSubFolders => " + ret);
        return ret;
    }

    public static String getLastFolder(@NonNull String strFolderPath) {
        try {
            String ret = strFolderPath;
            myLog("getLastFolder - Path = " + ret);
            int pos = ret.lastIndexOf("/");
            if (pos > 0) {
                ret = ret.substring(pos + 1);
            } else {
                ret = "";
            }
            myLog("getLastFolder => " + ret);
            return ret;
        } catch (Exception e) {
            myLogEE(e, "getLastFolder()");
            return "";
        }
    }

    @NonNull
    public static String getMimeType(String fileName) {
        // other possibility : library that read the beggining of file like "Apache
        // Tika"
        // DocumentContract
        // DocumentFile.fromSingleUri(this, uri_given).getType;
        String type;
        final String extension = getExtension(fileName);
        type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.toLowerCase());
        if (type == null)
            type = "*/*";
        return type;
    }

    public static String getSourceLocation(Context context, Uri uri) {
        if (Objects.isNull(uri) || uri.toString().isEmpty()) {
            myLogE("getSourceLocation - empty uri");
            return "xxx";
        }
        // myLogD("getSourceLocation - uri = [" + uri + "] - Authority = " +
        // uri.getAuthority());
        if (!Objects.isNull(uri) && !Objects.isNull(uri.getAuthority())) {
            String uriAuthority = uri.getAuthority();
            Set<String> cloudAuthorities = new HashSet<>();
            cloudAuthorities.add("com.google.android.apps.docs.storage"); // Google Drive
            cloudAuthorities.add("com.microsoft.skydrive.content"); // OneDrive
            cloudAuthorities.add("com.microsoft.skydrive.content.StorageAccessProvider"); // OneDrive
            cloudAuthorities.add("com.dropbox.product.android.dbapp.document_provider.documents"); // DropBox
            if (uriAuthority != null && cloudAuthorities.contains(uriAuthority)) {
                return "cloud";
            } else if (uri.toString().startsWith("http")) {
                return "web";
            } else if (StorageHelper.isOnSdCard(context, uri)) {
                return "sdcard";
            } else {
                return "local";
            }
        }
        return "xxx";
    }

    public static String addThousandSeparator(int n) {
        return String.format("%,d", n).replace(',', '.');
    }

    public static String getMimeType(Context context, Uri uri) {
        // Try content resolver first
        String mime = null;

        try {
            mime = context.getContentResolver().getType(uri);
        } catch (Exception ignored) {
        }

        // If null, fallback using extension
        if (mime == null) {
            String extension = null;
            try {
                // Try to get file name from uri
                if ("content".equals(uri.getScheme())) {
                    Cursor cursor = context.getContentResolver().query(uri, null, null, null, null);
                    if (cursor != null) {
                        try {
                            int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                            if (nameIndex != -1 && cursor.moveToFirst()) {
                                String fileName = cursor.getString(nameIndex);
                                int dotIndex = fileName.lastIndexOf('.');
                                if (dotIndex >= 0) {
                                    extension = fileName.substring(dotIndex + 1).toLowerCase();
                                }
                            }
                        } finally {
                            cursor.close();
                        }
                    }
                } else if ("file".equals(uri.getScheme())) {
                    String path = uri.getPath();
                    if (path != null) {
                        int dotIndex = path.lastIndexOf('.');
                        if (dotIndex >= 0) {
                            extension = path.substring(dotIndex + 1).toLowerCase();
                        }
                    }
                }

                // Use extension to guess mime type
                if (extension != null) {
                    mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
                    if (mime == null && extension.equals("m4b")) {
                        mime = "audio/m4b"; // custom fallback for m4b
                    }
                }
            } catch (Exception e) {
                myLogEE(e, "getMimeType");
            }
        }

        return mime;
    }

    public static String getMimeType(File f) {
        String m = "*/*";
        try {
            m = DocumentFile.fromFile(f).getType();
        } catch (Exception e) {
            myLogEE(e, "getMimeType");
        }
        return m;
    }

    public static String formatMemPadding(Context context, long mem) {
        return formatMemPadding(context, mem, 9);
    }

    public static String formatMemPadding(Context context, long mem, int padding) {
        // %3s => left padding
        if (padding < 1) {
            return String.valueOf(mem);
        } else {
            try {
                Locale locale = LocaleHelper.getLocale(context);
                return String.format("%" + padding + "s",
                        NumberFormat.getNumberInstance(locale).format(mem));
            } catch (Exception e) {
                myLogEE(e, "formatMem");
                return String.valueOf(mem);
            }
        }
    }

    public static String formatSizeMB(long bytes) {
        double mb = bytes / (1024.0 * 1024.0);
        return String.format(Locale.US, "%.1fMB", mb);
    }

    public static String formatSizeMB_translate(Context context, long bytes) {
        double mb = bytes / (1024.0 * 1024.0);
        String smb = context.getApplicationContext().getString(R.string.MB);
        String zeReturn = String.format(Locale.US, "%.1f", mb);
        return zeReturn + " " + smb;
    }

    public static String getFileNameFromUrl(String url) {
        return Uri.parse(url).getLastPathSegment();
    }

    public static String formatMmSs(long ms) {
        long s = ms / 1000;
        long m = s / 60;
        long sec = s % 60;
        return String.format(java.util.Locale.US, "%d:%02d", m, sec);
    }

    public static String formatHhMmSs(long ms) {
        long totalSeconds = ms / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        if (hours > 0) {
            return String.format(java.util.Locale.US, "%02d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format(java.util.Locale.US, "%02d:%02d", minutes, seconds);
        }
    }

    public static String formatMS(long ms) {
        double seconds = ms / 1000.0;
        return String.format(java.util.Locale.US, "%.3f ms", seconds);
    }

    public static String formatMmSsMs(long ms) {
        long s = ms / 1000;
        long m = s / 60;
        long sec = s % 60;
        long millis = ms % 1000;
        return String.format(java.util.Locale.US, "%d:%02d.%03d", m, sec, millis);
    }

    public static String formatHhMmSsMs(long ms) {
        long s = ms / 1000;
        long m = s / 60;
        long h = m / 60;
        long sec = s % 60;
        long millis = ms % 1000;

        if (h > 0) {
            m = m % 60; // keep minutes 0–59
            return String.format(java.util.Locale.US, "%d:%02d:%02d.%03d", h, m, sec, millis);
        } else {
            return String.format(java.util.Locale.US, "%d:%02d.%03d", m, sec, millis);
        }
    }

    public static String removeLongDuplicates(String input, int duplicate_min_length) {
        // Define a regex pattern to match duplicate substrings longer than 10
        // characters
        Pattern pattern = Pattern.compile("(.{" + duplicate_min_length + ",}).*\\1");

        Matcher matcher = pattern.matcher(input);
        while (matcher.find()) {
            String duplicate = matcher.group(1);
            input = input.replaceFirst(Pattern.quote(duplicate), ""); // Remove the first occurrence of the duplicate
        }

        return input;
    }

    public static String cleanSearchString(String query) {
        if (query == null)
            return "";
        String cleanedString = query.replaceAll("[\\r\\n\\t\\u0000-\\u001F\\u007F]+", "").trim();
        if (!cleanedString.equals(query))
            myLog("cleanSearchString : [" + query + "] => [" + cleanedString + "]");
        return cleanedString;
    }

    public static String getStringFromFloatArray2digits2decimals(float[] values) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < values.length; i++) {
            if (i > 0)
                sb.append(", ");
            // %5.2f means: total width 5 chars, 2 decimals
            // numbers <10 get a leading space
            sb.append(String.format(Locale.US, "%5.2f", values[i]));
        }
        sb.append("]");
        return sb.toString();
    }

    public static String lpad(int value, int width) {
        return String.format(Locale.US, "%" + width + "d", value);
    }

    public static String lpad(long value, int width) {
        return String.format(Locale.US, "%" + width + "d", value);
    }

    @Nullable
    public static String getParentFolder(@Nullable String fullPath) {
        if (fullPath == null || fullPath.isEmpty()) {
            return null;
        }
        try {
            File f = new File(fullPath);
            return f.getParent();
        } catch (Exception e) {
            return null;
        }
    }

    @NonNull
    public static String getParentFolderOrEmpty(@Nullable String fullPath) {
        String parent = getParentFolder(fullPath);
        return parent != null ? parent : "";
    }

    public static float dpToPx(float dp, Context context) {
        return dp * context.getResources().getDisplayMetrics().density;
    }

    public static boolean isPure(Context context) {
        // pure does not have podcasts, radios, and bottomNavigation Bar
        return context.getPackageName().contains("com.driot.bookplayerpure");
    }
}
