package com.driot.bookplayer.utils;

import com.coremedia.iso.IsoFile;
import com.coremedia.iso.boxes.Container;
import com.googlecode.mp4parser.authoring.Movie;
import com.googlecode.mp4parser.authoring.Sample;
import com.googlecode.mp4parser.authoring.Track;
import com.googlecode.mp4parser.authoring.container.mp4.MovieCreator;
import com.googlecode.mp4parser.boxes.mp4.ESDescriptorBox;
import com.googlecode.mp4parser.boxes.mp4.objectdescriptors.BaseDescriptor;
import com.googlecode.mp4parser.boxes.mp4.objectdescriptors.DecoderConfigDescriptor;
import com.googlecode.mp4parser.boxes.mp4.objectdescriptors.DecoderSpecificInfo;
import com.googlecode.mp4parser.boxes.mp4.objectdescriptors.ESDescriptor;
import com.googlecode.mp4parser.util.Path;

import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
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

                    long[] sampleDurations = track.getSampleDurations();
                    long timescale = track.getTrackMetaData().getTimescale();

                    List<com.googlecode.mp4parser.authoring.Sample> samples = track.getSamples();

                    long currentTime = 0;
                    int chapterIndex = 1;

                    for (int i = 0; i < samples.size(); i++) {
                        com.googlecode.mp4parser.authoring.Sample sample = samples.get(i);
                        ByteBuffer buffer = sample.asByteBuffer();
                        byte[] data = new byte[buffer.remaining()];
                        buffer.get(data);

                        // Safely convert bytes to string
                        String raw = new String(data, StandardCharsets.UTF_8);
                        String cleaned = raw.replaceAll("[^\\x20-\\x7E]", "").trim();

                        // Compute duration
                        long duration = (i + 1 < sampleDurations.length) ? sampleDurations[i] : 0;
                        double startSeconds = (double) currentTime / timescale;
                        double durationSeconds = (double) duration / timescale;

                        //myLog("Chapter " + chapterIndex++ + ": " + cleaned);

                        myLog(String.format("Chapter %d: %s  -  (Start: %.2f s, Duration: %.2f s)", chapterIndex++, cleaned, startSeconds, durationSeconds));

                        currentTime += duration;                    }
                }
            }
        } catch (IOException e) {
            myLogE("Error extracting chapters: " + e.getMessage());
        }
    }


    public static void extractAacTrackAsAdts(String mp4Path, String outputAacPath) throws IOException {
        Movie movie = MovieCreator.build(mp4Path);
        Track aacTrack = null;

        for (Track track : movie.getTracks()) {
            if ("soun".equals(track.getHandler()) &&
                    track.getSampleDescriptionBox().getSampleEntry().getType().equals("mp4a")) {
                aacTrack = track;
                break;
            }
        }

        if (aacTrack == null) {
            throw new RuntimeException("No AAC audio track found");
        }

        ESDescriptorBox esds = Path.getPath((Container) aacTrack.getSampleDescriptionBox(), "mp4a/esds");
        if (esds == null) {
            throw new RuntimeException("No ESDescriptorBox found in AAC track");
        }

        ESDescriptor esDescriptor = (ESDescriptor) esds.getDescriptor();
        if (esDescriptor == null) {
            throw new RuntimeException("No ESDescriptor found");
        }

        DecoderConfigDescriptor config = esDescriptor.getDecoderConfigDescriptor();
        if (config == null) {
            throw new RuntimeException("No DecoderConfigDescriptor found");
        }

        // AAC config: AOT=2, FreqIdx=4, Channels=1
        //byte[] audioSpecificConfig = config.getAudioSpecificInfo().getConfigBytes();

        //// Hard Coded : standard AAC files (e.g., AAC LC, 44.1 kHz, 2 channels),
        // AAC config: AOT=2, FreqIdx=4, Channels=2
        byte[] audioSpecificConfig = new byte[] { (byte) 0x12, (byte) 0x10 };

        if (audioSpecificConfig == null || audioSpecificConfig.length < 2) {
            throw new RuntimeException("Invalid or missing AudioSpecificConfig");
        }

        // Parse AudioSpecificConfig bytes manually (usually 2 bytes)
        int audioObjectType = (audioSpecificConfig[0] >> 3) & 0x1F;
        int samplingFreqIndex = ((audioSpecificConfig[0] & 0x07) << 1) | ((audioSpecificConfig[1] >> 7) & 0x01);
        int channelConfig = (audioSpecificConfig[1] >> 3) & 0x0F;

        myLog("AAC config: AOT=" + audioObjectType + ", FreqIdx=" + samplingFreqIndex + ", Channels=" + channelConfig);



        try (FileOutputStream fos = new FileOutputStream(outputAacPath)) {
            for (Sample sample : aacTrack.getSamples()) {
                ByteBuffer buffer = sample.asByteBuffer();
                byte[] frame = new byte[buffer.remaining()];
                buffer.get(frame);

                byte[] adtsHeader = buildAdtsHeader(frame.length, audioObjectType, samplingFreqIndex, channelConfig);
                fos.write(adtsHeader);
                fos.write(frame);
            }
        }
    }

    private static byte[] buildAdtsHeader(int aacFrameLength, int audioObjectType, int samplingFreqIndex, int channelConfig) {
        int adtsLength = aacFrameLength + 7;

        byte[] header = new byte[7];
        header[0] = (byte) 0xFF;
        header[1] = (byte) 0xF1; // Sync + MPEG-4 + Layer
        //header[1] = (byte) (((1 << 3) | (1 << 1) | 1)); // MPEG-4, Layer=00, protection_absent=1

        header[2] = (byte) (((audioObjectType - 1) << 6) | (samplingFreqIndex << 2) | (channelConfig >> 2));
        header[3] = (byte) (((channelConfig & 3) << 6) | ((adtsLength >> 11) & 0x03));
        header[4] = (byte) ((adtsLength >> 3) & 0xFF);
        header[5] = (byte) (((adtsLength & 7) << 5) | 0x1F);
        header[6] = (byte) 0xFC;

        return header;
    }

    private static DecoderSpecificInfo getDecoderSpecificInfo(DecoderConfigDescriptor config) {
        if (config == null) return null;

        try {
            Field field = DecoderConfigDescriptor.class.getDeclaredField("configDescriptorChildren");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            List<BaseDescriptor> children = (List<BaseDescriptor>) field.get(config);
            if (children != null) {
                for (BaseDescriptor desc : children) {
                    if (desc instanceof DecoderSpecificInfo) {
                        return (DecoderSpecificInfo) desc;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }
    private static byte[] getAudioSpecificConfig(DecoderSpecificInfo info) {
        try {
            Field dataField = DecoderSpecificInfo.class.getDeclaredField("data");
            dataField.setAccessible(true);
            return (byte[]) dataField.get(info);
        } catch (Exception e) {
            throw new RuntimeException("Unable to access AudioSpecificConfig", e);
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
