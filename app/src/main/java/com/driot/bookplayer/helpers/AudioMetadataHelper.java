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
import com.googlecode.mp4parser.FileDataSourceImpl;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.channels.Channels;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class AudioMetadataHelper {

    public static class AudioMetadata {
        public String title;
        public String artist;
        public String album;
        public Bitmap cover;
    }

    public static AudioMetadata extractMetadata(Context context, File file) {
        String path = file.getAbsolutePath().toLowerCase();
        if (path.endsWith(".mp3")) {
            return extractFromMp3(file);
        } else if (path.endsWith(".m4b") || path.endsWith(".mp4") || path.endsWith(".m4a")) {
            return extractFromM4b(file);
        }
        return null;
    }

    private static AudioMetadata extractFromMp3(File file) {
        AudioMetadata metadata = new AudioMetadata();
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
            retriever.release();
        }
        return metadata;
    }

    private static AudioMetadata extractFromM4b(File m4bFilePath) {
        AudioMetadata metadata = new AudioMetadata();

        try (FileDataSourceImpl dataSource = new FileDataSourceImpl(m4bFilePath)) {
            IsoFile isoFile = new IsoFile(dataSource);

            UserDataBox udta = isoFile.getBoxes(UserDataBox.class).stream().findFirst().orElse(null);
            if (udta != null) {
                MetaBox metaBox = udta.getBoxes(MetaBox.class).stream().findFirst().orElse(null);
                if (metaBox != null) {
                    List<Box> metaChildren = metaBox.getBoxes();

                    for (Box box : metaChildren) {
                        String type = box.getType();

                        if ("ilst".equals(type)) {
                            // iterate inside the ilst container
                            for (Box entry : ((Container) box).getBoxes()) {
                                String entryType = entry.getType();

                                if ("©nam".equals(entryType)) {
                                    metadata.title = extractTextFromDataBox(entry);
                                } else if ("©ART".equals(entryType)) {
                                    metadata.artist = extractTextFromDataBox(entry);
                                } else if ("©alb".equals(entryType)) {
                                    metadata.album = extractTextFromDataBox(entry);
                                } else if ("covr".equals(entryType)) {
                                    metadata.cover = extractImageFromDataBox(entry);
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return metadata;
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



}
