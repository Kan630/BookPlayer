package com.driot.bookplayer.utils;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class FileSorter {

    public static List<File> getFilesSortedBySize(File cacheDir) {
        // Get the list of files from the directory
        List<File> files = Arrays.asList(cacheDir.listFiles());

        // Sort the list using a comparator based on file size
        Collections.sort(files, new Comparator<File>() {
            @Override
            public int compare(File file1, File file2) {
                return Long.compare(file1.length(), file2.length());
            }
        });

        return files;
    }

    public static void main(String[] args) {
        // Example usage
        File cacheDir = new File("path/to/your/cacheDir");
        List<File> sortedFiles = getFilesSortedBySize(cacheDir);

        // Print sorted files
        for (File file : sortedFiles) {
            System.out.println("File: " + file.getName() + " Size: " + file.length());
        }
    }
}