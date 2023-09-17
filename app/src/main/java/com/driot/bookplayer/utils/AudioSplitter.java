package com.driot.bookplayer.utils;

import static com.driot.tonylib.KanLogger.myLog;
import static com.driot.tonylib.KanLogger.myLogE;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.net.Uri;
import android.os.Environment;

import com.driot.bookplayer.db.DatabaseClient;
import com.driot.bookplayer.db.ZikFile;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class AudioSplitter {
    public static void splitM4BToMP3(String m4bFilePath, String outFolderPath) {
        myLog("AudioSplitter Start");
        List<Integer> found_tracks = new ArrayList<Integer>();
        try {
            MediaExtractor extractor = new MediaExtractor();
            myLog("AudioSplitter 01");
            //extractor.setDataSource(m4bFilePath);
            //extractor.setDataSource("/data/user/0/com.driot.bookplayer/cache/CountOfMonteCristoPart1-32kb_librivox.m4b");
            extractor.setDataSource("/storage/sdcard0/AudioBooks/test_m4b/CountOfMonteCristo109-117_librivox.m4b");

            myLog("AudioSplitter 02");
            int trackCount = extractor.getTrackCount();
            myLog("AudioSplitter 03 -- found [" + trackCount + "] tracks.");
            int audioTrackIndex = -1;

            for (int i = 0; i < trackCount; i++) {
                MediaFormat format = extractor.getTrackFormat(i);
                String mime = format.getString(MediaFormat.KEY_MIME);
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i;
                    found_tracks.add(i);
                    break;
                }
            }

            myLog("m4b AudioTrackIndex = [" + audioTrackIndex + "]");
            myLog("m4b found tracks = [" + found_tracks.size() + "]");

            if (audioTrackIndex >= 0) {
                extractor.selectTrack(audioTrackIndex);

                //String outputDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC).getPath();
                String outputDir = outFolderPath;
                File dir = new File(outputDir);
                dir.mkdirs();
                int fileIndex = 1;

                String outputFileName = "track_" + String.format("%03d", fileIndex) + ".mp3";
                File mp3File = new File(outputDir, outputFileName);

                if (!mp3File.exists()) {
                    //TODO check output type....
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
                        bufferInfo.flags = 0;
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
            myLogE("ca chie dans la colle ..." + e.getMessage() );
            e.printStackTrace();
        }
    }
}