package com.driot.bookplayer.objects;

import java.util.Comparator;

public class AudioFileInfo {
    private String fileName;
    private long duration;

    public AudioFileInfo(String fileName, long duration) {
        this.fileName = fileName;
        this.duration = duration;
    }

    public String getFileName() {
        return fileName;
    }

    public long getDuration() {
        return duration;
    }

    public static final Comparator<AudioFileInfo> ALPHANUMERIC_COMPARATOR = new Comparator<AudioFileInfo>() {
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
    };
}
