package com.driot.bookplayer.utils;

import com.coremedia.iso.IsoFile;
import com.googlecode.mp4parser.authoring.Movie;
import com.googlecode.mp4parser.authoring.Track;
import com.googlecode.mp4parser.authoring.container.mp4.MovieCreator;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class Mp4Parser {
    final static String TAG = "Mp4Parser";

    public static void inspect(String filePath) {   //throws IOException
        myLog("inspect : " + filePath);
        try {
            IsoFile isoFile = new IsoFile(filePath);
            isoFile.getBoxes().stream().map(box -> box.toString().replace(";", "\n")).forEach(Mp4Parser::myLog);
        } catch (IOException e) {
            //throw new RuntimeException(e);
            myLogE("error : " + e.getMessage());
        }

        // FragmentedMp4SampleList f = new FragmentedMp4SampleList(0, isoFile, new FileRandomAccessSourceImpl(new RandomAccessFile("/Users/sannies/dev/mp4parser/tos_vp9.MP4", "r")));


    }

    public static void extractChapters(String filePath) {
        try {
            Movie movie = MovieCreator.build(filePath);
            List<Track> tracks = movie.getTracks();

            for (Track track : tracks) {
                String handler = track.getHandler();

                myLog("Track handler: " + handler);

                if ("text".equals(handler) || "sbtl".equals(handler)) {
                    myLog("Chapter track found:");
                    List<com.googlecode.mp4parser.authoring.Sample> samples = track.getSamples();
                    int chapterIndex = 1;
                    for (com.googlecode.mp4parser.authoring.Sample sample : samples) {
                        ByteBuffer buffer = sample.asByteBuffer();
                        byte[] data = new byte[buffer.remaining()];
                        buffer.get(data);

                        // Safely convert bytes to string
                        String raw = new String(data, StandardCharsets.UTF_8);

                        // Remove non-printable/control characters
                        String cleaned = raw.replaceAll("[^\\x20-\\x7E]", "").trim();

                        myLog("Chapter " + chapterIndex++ + ": " + cleaned);
                    }
                }
            }
        } catch (IOException e) {
            myLogE("Error extracting chapters: " + e.getMessage());
        }
    }





    //--- FULL STATIC LOG --------------------------
    private static void myLog(String str) { KanLogger.myLog(TAG, str); }
    private static void myLogE(String str) { KanLogger.myLogE(TAG, str); }
    private void myLogInFile(String str) { KanLogger.myLogInFile(this.getClass().getName(), str); }
    private void myLogD(String str) { KanLogger.myLogD(this.getClass().getName(), str); }
    private void myLogI(String str) { KanLogger.myLogI(this.getClass().getName(), str); }
    private void myToast(String str) { KanLogger.myToast(this.getClass().getName(), str); }
    private void myToastE(String str) { KanLogger.myToastE(this.getClass().getName(), str); }

}
