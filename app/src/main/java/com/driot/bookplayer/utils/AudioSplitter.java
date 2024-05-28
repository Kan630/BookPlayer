package com.driot.bookplayer.utils;

/**
 * 2024-05-28
 */
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Build;
import androidx.annotation.RequiresApi;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;

public class AudioSplitter {

    public static void splitAudio(String inputFilePath, List<Chapter> chapters) throws IOException {
        MediaExtractor extractor = new MediaExtractor();
        extractor.setDataSource(inputFilePath);
        int audioTrackIndex = selectAudioTrack(extractor);

        if (audioTrackIndex == -1) {
            throw new RuntimeException("No audio track found in the file");
        }

        MediaFormat format = extractor.getTrackFormat(audioTrackIndex);
        extractor.selectTrack(audioTrackIndex);

        for (Chapter chapter : chapters) {
            extractor.seekTo(chapter.startUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
            MediaMuxer muxer = new MediaMuxer(chapter.outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            int newTrackIndex = muxer.addTrack(format);
            muxer.start();

            ByteBuffer buffer = ByteBuffer.allocate(1024 * 1024);
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();

            while (true) {
                int sampleSize = extractor.readSampleData(buffer, 0);
                if (sampleSize < 0) {
                    break;
                }

                long sampleTime = extractor.getSampleTime();
                if (sampleTime > chapter.endUs) {
                    break;
                }

                info.offset = 0;
                info.size = sampleSize;
                info.presentationTimeUs = sampleTime;
                info.flags = extractor.getSampleFlags();
                muxer.writeSampleData(newTrackIndex, buffer, info);
                extractor.advance();
            }

            muxer.stop();
            muxer.release();
        }
        extractor.release();
    }

    private static int selectAudioTrack(MediaExtractor extractor) {
        for (int i = 0; i < extractor.getTrackCount(); i++) {
            MediaFormat format = extractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (mime.startsWith("audio/")) {
                return i;
            }
        }
        return -1;
    }

    public static class Chapter {
        long startUs;
        long endUs;
        String outputPath;

        public Chapter(long startUs, long endUs, String outputPath) {
            this.startUs = startUs;
            this.endUs = endUs;
            this.outputPath = outputPath;
        }
    }

    public static void main(String[] args) {
        List<Chapter> chapters = Arrays.asList(
                new Chapter(0, 10000000, "chapter1.mp4"),
                new Chapter(10000000, 20000000, "chapter2.mp4")
                // Add more chapters as needed
        );

        try {
            splitAudio("input.mp4", chapters);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}