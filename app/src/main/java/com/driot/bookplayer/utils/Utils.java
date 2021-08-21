package com.driot.bookplayer.utils;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.view.View;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * created by Antoine Driot -- antoine.driot.com -- on 08/11/20
 */
public class Utils {

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


}
