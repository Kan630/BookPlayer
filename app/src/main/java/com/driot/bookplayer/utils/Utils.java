package com.driot.bookplayer.utils;

import static com.driot.bookplayer.utils.KanLogger.myLog;
import static com.driot.bookplayer.utils.KanLogger.myLogE;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.view.View;

import com.driot.bookplayer.objects.AudioFileInfo;

import java.io.File;
import java.util.Comparator;


/**
 * created by Antoine Driot -- antoine.driot.com -- on 08/11/20
 */
public class Utils {

    private static final String TAG = "Utils";


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


    public static class AlphanumericComparator implements Comparator<AudioFileInfo> {
        @Override
        public int compare(AudioFileInfo a1, AudioFileInfo a2) {
            String s1 = a1.getFileName();
            String s2 = a2.getFileName();

            String[] arr1 = s1.split("(?<=\\D)(?=\\d)|(?<=\\d)(?=\\D)");
            String[] arr2 = s2.split("(?<=\\D)(?=\\d)|(?<=\\d)(?=\\D)");

            int i = 0;
            while (i < arr1.length && i < arr2.length) {
                if (arr1[i].equals(arr2[i])) {
                    i++;
                    continue;
                }

                if (isNumeric(arr1[i]) && isNumeric(arr2[i])) {
                    try {
                        long num1 = Long.parseLong(arr1[i]);
                        long num2 = Long.parseLong(arr2[i]);
                        return Long.compare(num1, num2);
                    } catch (NumberFormatException e) {
                        // fallback to string comparison
                    }
                }

                return arr1[i].compareTo(arr2[i]);
            }

            return Integer.compare(arr1.length, arr2.length);
        }

        private boolean isNumeric(String s) {
            return s.matches("\\d+");
        }
    }
}
