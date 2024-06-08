package com.driot.bookplayer.utils;

import static com.driot.tonylib.KanLogger.myLog;
import static com.driot.tonylib.KanLogger.myLogE;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.util.Pair;
import android.view.View;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import com.driot.tonylib.KanLogger;


/**
 * created by Antoine Driot -- antoine.driot.com -- on 08/11/20
 */
public class Utils {

    private static final String TAG = "Utils";

    /**
     * @param view         View to animate
     * @param toVisibility Visibility at the end of animation
     * @param toAlpha      Alpha at the end of animation
     * @param duration     Animation duration in ms
     */
    public static void animateView(final View view, final int toVisibility, float toAlpha, int duration) {
        boolean show = toVisibility == View.VISIBLE;
        if (show) {
            view.setAlpha(0);
        }
        view.setVisibility(View.VISIBLE);
        view.animate()
                .setDuration(duration)
                .alpha(show ? toAlpha : 0)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        view.setVisibility(toVisibility);
                    }
                });
    }

    /**
     * Directory Names are used as Keys.
     * Filenames are stored in an List<String> for a specific Directory Name.
     * If a file is not stored within a directory, we add to a default root key.
     */

    // we may overload to retrieveListing from a folder !!

    public static HashMap<String, List<String>> retrieveListing(File zipFile) {
        HashMap<String, List<String>> contents = new HashMap<>();
        try  {
            FileInputStream fin = new FileInputStream(zipFile);
            ZipInputStream zin = new ZipInputStream(fin);
            ZipEntry ze = null;
            while ((ze = zin.getNextEntry()) != null) {
                if(ze.isDirectory()) {
                    String directory = ze.getName();
                    if (!contents.containsKey(directory)) {
                        contents.put(directory, new ArrayList<String>());
                    }
                } else {
                    String file = ze.getName();
                    int pos = file.lastIndexOf("/");
                    if (pos != -1) {
                        String directory = file.substring(0, pos+1);
                        String fileName = file.substring(pos+1);
                        if (!contents.containsKey(directory)) {
                            contents.put(directory, new ArrayList<String>());
                            List<String> fileNames = contents.get(directory);
                            fileNames.add(fileName);
                        } else {
                            List<String> fileNames = contents.get(directory);
                            fileNames.add(fileName);
                        }
                    } else {
                        if (!contents.containsKey("root")) {
                            contents.put("root", new ArrayList<String>());
                        }
                        List<String> fileNames = contents.get("root");
                        fileNames.add(file);
                    }
                }
                zin.closeEntry();
            }
            zin.close();
        } catch(Exception e) {
            e.printStackTrace();
        }
        return contents;
    }

    public static void copyStream(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[1024];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
    }

    public static void unzip(File zipFile, File targetDirectory) throws IOException {
        myLog(TAG, "unzipping in : " + targetDirectory);
        myLog(TAG, "unzipping in : " + targetDirectory.getName());
        ZipInputStream zis = new ZipInputStream(
                new BufferedInputStream(new FileInputStream(zipFile)));
        try {
            ZipEntry ze;
            int count;
            byte[] buffer = new byte[8192];

            while ((ze = zis.getNextEntry()) != null) {
                myLog(TAG, "unzipping : " + ze.getName());

                if (ze.getName().equals(targetDirectory.getName()+"/")) {
                    //bypass if zip contains only folder with same name at first level
                    targetDirectory = new File(targetDirectory.getParent());
                    myLog(TAG, "unzipping : bypassing first directory");

                } else {

                    File file = new File(targetDirectory, ze.getName());
                    File dir = ze.isDirectory() ? file : file.getParentFile();

                    if (!dir.isDirectory() && !dir.mkdirs())
                        throw new FileNotFoundException("Failed to ensure directory: " +
                                dir.getAbsolutePath());
                    if (ze.isDirectory())
                        continue;
                    FileOutputStream fout = new FileOutputStream(file);
                    try {
                        while ((count = zis.read(buffer)) != -1)
                            fout.write(buffer, 0, count);
                    } finally {
                        fout.close();
                    }

                }
            /* if time should be restored as well
            long time = ze.getTime();
            if (time > 0)
                file.setLastModified(time);
            */
            }
        } finally {
            zis.close();
        }
    }

    public static boolean recursiveRemove(File file) {
        if(file == null  || !file.exists()) {
            myLogE(TAG, "recursiveRemove() => File does not exist.... [" + file.toString() + "]");
            return false;
        }

        if(file.isDirectory()) {
            File[] list = file.listFiles();
            if(list != null) {
                for(File item : list) {
                    recursiveRemove(item);
                }
            }
        }
        if(file.exists()) {
            if (file.delete()) {
                myLog(TAG, "recursiveRemove() => delete OK.... [" + file.toString() + "]");
            } else {
                myLogE(TAG, "recursiveRemove() => delete KO.... [" + file.toString() + "]");
            }
        }
        return !file.exists();
    }


    public static boolean deleteDir(File dir) {
        if (dir != null && dir.isDirectory()) {
            String[] children = dir.list();
            for (int i = 0; i < children.length; i++) {
                boolean success = deleteDir(new File(dir, children[i]));
                if (!success) {
                    return false;
                }
            }
            return dir.delete();
        } else if(dir!= null && dir.isFile()) {
            return dir.delete();
        } else {
            return false;
        }
    }

    public static class AlphanumericComparator implements Comparator<String> {
        public int compare(String s1, String s2) {
            String[] arr1 = s1.split("(?<=\\D)(?=\\d)|(?<=\\d)(?=\\D)");
            String[] arr2 = s2.split("(?<=\\D)(?=\\d)|(?<=\\d)(?=\\D)");

            int i = 0;
            while (i < arr1.length && i < arr2.length) {
                if (arr1[i].equals(arr2[i])) {
                    i++;
                    continue;
                }

                if (isNumeric(arr1[i]) && isNumeric(arr2[i])) {
                    int num1 = Integer.parseInt(arr1[i]);
                    int num2 = Integer.parseInt(arr2[i]);
                    return Integer.compare(num1, num2);
                }

                return arr1[i].compareTo(arr2[i]);
            }

            return Integer.compare(arr1.length, arr2.length);
        }

        private boolean isNumeric(String s) {
            return s.matches("\\d+");
        }
    }



    public static long getCustomLength(File file) {
        if (file.isFile()) {
            return file.length();
        } else if (file.isDirectory()) {
            return getFolderSize(file);
        }
        return 0;
    }

    private static long getFolderSize(File folder) {
        long totalSize = 0;
        File[] files = folder.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile()) {
                    totalSize += file.length();
                } else if (file.isDirectory()) {
                    totalSize += getFolderSize(file);
                }
            }
        }
        return totalSize;
    }
}
