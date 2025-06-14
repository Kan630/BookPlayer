package com.driot.bookplayer.utils;
/*
import android.util.Log;

import com.arthenica.ffmpegkit.*;

import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class M4BChapterExtractor {

    public static void extractChapters(String inputPath, File outputDir) {
        if (!outputDir.exists()) outputDir.mkdirs();

        FFmpegKit.executeAsync("-i \"" + inputPath + "\" -f ffmetadata -", session -> {
            String metadata = session.getOutput();
            if (metadata == null || !metadata.contains("[CHAPTER]")) {
                myLog("FFmpeg - No chapters found.");
                // fallback: call populateArrayListOfTracksFromFile()
                return;
            }

            // Parse chapter info
            Pattern chapterPattern = Pattern.compile(
                    "START=(\\d+)\\s+END=(\\d+).*?title=(.*)", Pattern.DOTALL);
            Matcher matcher = chapterPattern.matcher(metadata);

            int chapterIndex = 1;
            while (matcher.find()) {
                long startMs = Long.parseLong(matcher.group(1));
                long endMs = Long.parseLong(matcher.group(2));
                String title = sanitizeFileName(matcher.group(3).trim());
                if (title.isEmpty()) title = "Chapter_" + chapterIndex;

                String outputFile = new File(outputDir, title + ".aac").getAbsolutePath();
                String command = String.format(
                        "-i \"%s\" -ss %.3f -to %.3f -c copy \"%s\"",
                        inputPath, startMs / 1000.0, endMs / 1000.0, outputFile
                );

                String finalTitle = title;
                FFmpegKit.executeAsync(command, extractSession -> {
                    if (extractSession.getReturnCode().isValueSuccess()) {
                        myLog("FFmpeg - Chapter extracted: " + finalTitle);
                    } else {
                        myLogE("FFmpeg - Failed to extract chapter: " + finalTitle);
                    }
                });

                chapterIndex++;
            }
        });
    }

    private static String sanitizeFileName(String input) {
        return input.replaceAll("[\\\\/:*?\"<>|]", "_");
    }
    //--- LOG --------------------------
    private static void myLog(String str) { KanLogger.myLog("M4BChapterExtractor", str); }
    private void myLogD(String str) { KanLogger.myLogD(this.getClass().getName(), str); }
    private void myLogI(String str) { KanLogger.myLogI(this.getClass().getName(), str); }
    private static void myLogE(String str) { KanLogger.myLogE("M4BChapterExtractor", str); }
}
*/