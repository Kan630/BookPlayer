package com.driot.bookplayer.helpers;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;

import com.coremedia.iso.IsoFile;
import com.coremedia.iso.boxes.Box;
import com.coremedia.iso.boxes.Container;
import com.coremedia.iso.boxes.MetaBox;
import com.coremedia.iso.boxes.UserDataBox;
import com.driot.bookplayer.objects.MyAudioMetadata;
import com.driot.bookplayer.utils.KanLogger;
import com.googlecode.mp4parser.FileDataSourceImpl;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class AudioMetadataHelper {

    public static MyAudioMetadata extractMetadata(Context context, File file) {
        String path = file.getAbsolutePath().toLowerCase();
        if (path.endsWith(".mp3")) {
            return extractFromMp3(file);
        } else if (path.endsWith(".m4b") || path.endsWith(".mp4") || path.endsWith(".m4a")) {
            return extractFromM4b(file);
        }
        return null;
    }

    private static MyAudioMetadata extractFromMp3(File file) {
        MyAudioMetadata metadata = new MyAudioMetadata();
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(file.getAbsolutePath());

            metadata.title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
            metadata.artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
            metadata.album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM);

            byte[] art = retriever.getEmbeddedPicture();
            if (art != null) {
                metadata.cover = BitmapFactory.decodeByteArray(art, 0, art.length);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                retriever.release();
            } catch (IOException e) {
                myLogEE(e, "extractFromMp3");
            }
        }
        return metadata;
    }

    private static MyAudioMetadata extractFromM4b(File file) {
        MyAudioMetadata metadata = new MyAudioMetadata();

        try (FileDataSourceImpl dataSource = new FileDataSourceImpl(file)) {
            IsoFile isoFile = new IsoFile(dataSource);
            List<Box> allBoxes = getAllBoxesRecursively(isoFile);

            for (Box box : allBoxes) {
                String type = box.getType();

                if ("©nam".equals(type)) {
                    metadata.title = extractRawStringFromBox(box);
                } else if ("©ART".equals(type)) {
                    metadata.artist = extractRawStringFromBox(box);
                } else if ("©alb".equals(type)) {
                    metadata.album = extractRawStringFromBox(box);
                } else if ("covr".equals(type)) {
                    metadata.cover = extractImageFromDataBox(box);
                }
            }
        } catch (Exception e) {
            myLogEE(e, "extractFromM4b: raw box scan failed");
        }

        if (metadata.title == null && metadata.album == null && metadata.artist == null) {
            try {
                MediaMetadataRetriever retriever = new MediaMetadataRetriever();
                retriever.setDataSource(file.getAbsolutePath());

                metadata.title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
                metadata.artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
                metadata.album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM);

                byte[] art = retriever.getEmbeddedPicture();
                if (art != null) {
                    metadata.cover = BitmapFactory.decodeByteArray(art, 0, art.length);
                }

                retriever.release();
            } catch (Exception e2) {
                myLogEE(e2, "extractFromM4b: fallback retriever failed");
            }
        }

        myLog("metadata = " + metadata.toString().replace(",", "\n"));
        return metadata;
    }

    private static String extractRawStringFromBox(Box box) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            box.getBox(Channels.newChannel(baos));
            byte[] bytes = baos.toByteArray();
            // skip first 16 bytes (standard MP4 data header offset)
            return new String(bytes, 16, bytes.length - 16, StandardCharsets.UTF_8).trim();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }


    private static List<Box> getAllBoxesRecursively(Container container) {
        List<Box> result = new ArrayList<>();
        for (Box box : container.getBoxes()) {
            result.add(box);
            if (box instanceof Container) {
                result.addAll(getAllBoxesRecursively((Container) box));
            }
        }
        return result;
    }

    private static String extractTextFromDataBox(Box box) {
        if (!(box instanceof Container)) return null;

        for (Box child : ((Container) box).getBoxes()) {
            if ("data".equals(child.getType())) {
                try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                    child.getBox(Channels.newChannel(baos));
                    byte[] fullBox = baos.toByteArray();

                    // Skip first 16 bytes (size + type + header = 4 + 4 + 8)
                    return new String(fullBox, 16, fullBox.length - 16, StandardCharsets.UTF_8).trim();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        return null;
    }

    private static Bitmap extractImageFromDataBox(Box box) {
        if (!(box instanceof Container)) return null;

        for (Box child : ((Container) box).getBoxes()) {
            if ("data".equals(child.getType())) {
                try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                    child.getBox(Channels.newChannel(baos));
                    byte[] fullBox = baos.toByteArray();

                    // Skip first 16 bytes
                    return BitmapFactory.decodeByteArray(fullBox, 16, fullBox.length - 16);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        return null;
    }


    ////////////////////////////////////////////////////////
    private static final String TAG = "AudioMetadataHelper";
    private static void myLog(String str) { KanLogger.myLog(TAG, str); }
    private static void myLogD(String str) { KanLogger.myLogD(TAG, str); }
    private static void myLogI(String str) { KanLogger.myLogI(TAG, str); }
    private static void myLogW(String str) { KanLogger.myLogW(TAG, str); }
    private static void myLogE(String str) { KanLogger.myLogE(TAG, str); }
    private static void myLogEE(Throwable t, String str) { KanLogger.myLogEE(t, TAG, str); }
    private static void myToastEE(Throwable t, String str) { KanLogger.myToastEE(t, TAG, str); }

}
