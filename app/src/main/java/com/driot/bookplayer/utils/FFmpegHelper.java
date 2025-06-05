package com.driot.bookplayer.utils;

import static com.driot.bookplayer.utils.KanLogger.myLog;
import static com.driot.bookplayer.utils.KanLogger.myLogE;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class FFmpegHelper {

    public static List<ChapterInfo> extractChapters(String inputFile) {
        List<ChapterInfo> chapterList = new ArrayList<>();
        try {
            ProcessBuilder pb = new ProcessBuilder("ffprobe", "-i", inputFile, "-print_format", "json", "-show_chapters", "-loglevel", "error");
            pb.redirectErrorStream(true);
            Process process = pb.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder json = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                json.append(line);
            }

            JSONObject root = new JSONObject(json.toString());
            JSONArray chapters = root.getJSONArray("chapters");

            for (int i = 0; i < chapters.length(); i++) {
                JSONObject ch = chapters.getJSONObject(i);
                String start = ch.getString("start_time");
                String end = ch.getString("end_time");
                chapterList.add(new ChapterInfo(start, end));
            }

            process.waitFor();
        } catch (Exception e) {
            myLogE("Error in extractChapters: " + e.getMessage());
            e.printStackTrace();
        }

        return chapterList;
    }

    public static void splitChapter(String inputFile, String outputFile, String start, String end) {
        try {
            ProcessBuilder pb = new ProcessBuilder("ffmpeg", "-i", inputFile, "-ss", start, "-to", end, "-c", "copy", outputFile);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                myLog("FFmpeg " + line);
            }

            process.waitFor();
        } catch (Exception e) {
            myLogE("Error in splitChapter: " + e.getMessage());
            e.printStackTrace();
        }
    }


}
