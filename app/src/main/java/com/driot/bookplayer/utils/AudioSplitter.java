package com.driot.bookplayer.utils;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Environment;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

public class AudioSplitter {
    public static void splitM4BToMP3(String m4bFilePath, String OutFolderPath) {
        try {
            MediaExtractor extractor = new MediaExtractor();
            extractor.setDataSource(m4bFilePath);

            int trackCount = extractor.getTrackCount();
            int audioTrackIndex = -1;

            for (int i = 0; i < trackCount; i++) {
                MediaFormat format = extractor.getTrackFormat(i);
                String mime = format.getString(MediaFormat.KEY_MIME);
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i;
                    break;
                }
            }

            if (audioTrackIndex >= 0) {
                extractor.selectTrack(audioTrackIndex);

                //String outputDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC).getPath();
                String outputDir = OutFolderPath;
                int fileIndex = 1;

                String outputFileName = "output" + fileIndex + ".mp3";
                File mp3File = new File(outputDir, outputFileName);

                if (!mp3File.exists()) {
                    MediaMuxer muxer = new MediaMuxer(mp3File.getPath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
                    int audioTrack = muxer.addTrack(extractor.getTrackFormat(audioTrackIndex));
                    muxer.start();

                    ByteBuffer buffer = ByteBuffer.allocate(1024 * 1024); // Adjust buffer size as needed

                    MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
                    int sampleSize;

                    while ((sampleSize = extractor.readSampleData(buffer, 0)) >= 0) {

                        bufferInfo.offset = 0;
                        bufferInfo.size = sampleSize;
                        bufferInfo.presentationTimeUs = extractor.getSampleTime();
                        //bufferInfo.flags = extractor.getSampleFlags();
                        muxer.writeSampleData(audioTrack, buffer, bufferInfo);

                        extractor.advance();
                    }

                    muxer.stop();
                    muxer.release();

                    fileIndex++;
                }

                extractor.release();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}