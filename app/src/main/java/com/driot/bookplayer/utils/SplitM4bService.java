package com.driot.bookplayer.utils;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;

import androidx.annotation.Nullable;

import com.driot.bookplayer.R;
import com.driot.bookplayer.activities.LifecycleLoggingService;
import com.googlecode.mp4parser.authoring.Movie;
import com.googlecode.mp4parser.authoring.Sample;
import com.googlecode.mp4parser.authoring.Track;
import com.googlecode.mp4parser.authoring.container.mp4.MovieCreator;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


public class SplitM4bService extends LifecycleLoggingService {

    private final IBinder binder = new SplitM4bService.SplitM4bServiceBackgroundBinder();
    Callbacks mCallBacks;
    Thread backgroundThread;

    // Intent Extras
    private String m4bFilePath;
    private String destinationFolderPath;

    public interface Callbacks{
        void splitM4bService_tellProgress(String progressText, int progressVal);
        void splitM4bService_tellError(String errorText);
        void splitM4bService_tellEnd(String destinationFolderPath);
        void tellNonBlockingError(String txt);
    }
    public void registerClient(Service service){
        this.mCallBacks = (SplitM4bService.Callbacks)service;
    }

    public class SplitM4bServiceBackgroundBinder extends Binder {
        public SplitM4bService getService() { return SplitM4bService.this; }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        myLog("onBind()    intent:" + intent.getDataString());
        parseIntent(intent);
        return binder;
    }
    @Override
    public boolean onUnbind(Intent intent) {
        myLog("onUnBind()    intent:" + intent.getDataString());
        return super.onUnbind(intent);
    }
    @Override
    public void onDestroy() {
        myLog("onDestroy()");
        super.onDestroy();
    }
    private void parseIntent(Intent intent) {
        m4bFilePath = intent.getStringExtra("m4bFilePath");
        destinationFolderPath =  intent.getStringExtra("destinationFolderPath");
        if (m4bFilePath==null || destinationFolderPath==null) {
            tellError("Wrong arguments for SplitM4b Service");
            myLogE("Wrong arguments for SplitM4b Service");
        }
        myLog("parse intent :   " +
                "\nfrom m4bFilePath = [" + m4bFilePath + "] " +
                "\nto destinationFolderPath = [" + destinationFolderPath + "]");
    }

    public void init() {
        myLog("init()");
        //-----------------------------
        // heavy stuff in background Thread.... Hyper Important !! // TODO how to kill such thread in cae of error ?
        backgroundThread = new Thread(() -> {
            Boolean ret = splitM4bZipLocal();
        });
        backgroundThread.start();

    }


    private boolean splitM4bZipLocal() {


        ////////////////////////////////////////////////////////////////////////////////
        /// getting Args....
        ////////////////////////////////////////////////////////////////////////////////
        myLog("SplitM4bLocal()");
        File m4bFile = null;
        File splitM4bFolder = null;
        try {
            splitM4bFolder = new File(destinationFolderPath);
            if (!splitM4bFolder.exists()) splitM4bFolder.mkdirs();
        } catch (Exception e) {
            tellError("Bad Destination Folder Path - " + e.getMessage());
            return false;
        }
        try {
            m4bFile = new File(m4bFilePath);
        } catch (Exception e) {
            tellError("Bad Zip File Path - " + e.getMessage());
            return false;
        }
        if (!m4bFile.exists()) {
            tellError("Bad Zip File Path - [" + m4bFilePath + "]" );
            return false;
        }


        ////////////////////////////////////////////////////////////////////////////////
        /// Splitting M4b File
        ////////////////////////////////////////////////////////////////////////////////
        tellProgress(0,getResources().getString(R.string.Import_Progress_splitting_m4b_files));

        try {
            myLog("Splitting M4b File");
            Movie movie = MovieCreator.build(m4bFilePath);
            Track aacTrack = null;
            Track chapterTrack = null;

            for (Track track : movie.getTracks()) {
                if ("soun".equals(track.getHandler()) &&
                        track.getSampleDescriptionBox().getSampleEntry().getType().equals("mp4a")) {
                    aacTrack = track;
                } else if ("text".equals(track.getHandler()) || "sbtl".equals(track.getHandler())) {
                    chapterTrack = track;
                }
            }

            if (aacTrack == null || chapterTrack == null) {
                throw new RuntimeException("Required audio or chapter track not found");
            }

            long audioTimescale = aacTrack.getTrackMetaData().getTimescale();
            long[] audioDurations = aacTrack.getSampleDurations();
            List<Sample> audioSamples = aacTrack.getSamples();

            long chapterTime = 0;
            int chapterIndex = 1;

            List<Sample> chapterSamples = chapterTrack.getSamples();
            long[] chapterDurations = chapterTrack.getSampleDurations();
            long chapterTimescale = chapterTrack.getTrackMetaData().getTimescale();

            int nbChapters = chapterSamples.size();

            Set<String> usedNames = new HashSet<>();
            DecimalFormat chapterFormat = new DecimalFormat("000");

            for (int c = 0; c < chapterSamples.size(); c++) {
/*
                ByteBuffer buffer = chapterSamples.get(c).asByteBuffer();
                byte[] data = new byte[buffer.remaining()];
                buffer.get(data);

                // some hex logging for debug....
                StringBuilder hex = new StringBuilder();
                for (byte b : data) {
                    hex.append(String.format("%02X ", b));
                }
                myLog("Chapter sample raw bytes (hex): " + hex.toString());

                // Skip first 2 bytes (likely length or style info)
                int offset = 2;
                if (data.length <= offset) return "chapter";

                byte[] trimmed = Arrays.copyOfRange(data, offset, data.length);
                String raw = new String(trimmed, StandardCharsets.UTF_8);

                String cleaned = raw.replaceAll("[^\\x20-\\x7E]", "").trim(); // Remove non-printables


                //if (cleaned.toLowerCase().endsWith("encd")) {
                //    cleaned = cleaned.substring(0, cleaned.length() - 4).trim();
                //}

                cleaned = cleaned.replaceAll("encd.*$", "").trim();

                String chapterFileName = cleaned.replaceAll("[\\\\/:*?\"<>|]", "_"); // Windows-safe  -- sanitize filename to avoid illegal characters
*/
                String chapterFileName = extractCleanChapterTitle(chapterSamples.get(c));

                if (usedNames.contains(chapterFileName) || chapterFileName.isEmpty()) {
                    chapterFileName = "chapter" + chapterFormat.format(c + 1); // e.g., chapter001
                }
                usedNames.add(chapterFileName);
                String aacFileName = chapterFileName + ".aac";

                //myLog("Using chapter title: [" + raw + "] -> filename: [" + aacFileName + "]");
                myLog("=> filename: [" + aacFileName + "]");
                long chapterStartTime = chapterTime;
                long chapterDuration = chapterDurations[c];
                chapterTime += chapterDuration;

                double startSec = (double) chapterStartTime / chapterTimescale;
                double endSec = (double) chapterTime / chapterTimescale;

                int startSample = findSampleIndexForTime(audioDurations, audioTimescale, startSec);
                int endSample = findSampleIndexForTime(audioDurations, audioTimescale, endSec);

                myLog("Chapter " + chapterIndex + ": samples " + startSample + " to " + endSample);
                double zeProgress = (double) chapterIndex / nbChapters * 100;
                tellProgress((int) zeProgress,
                        getResources().getString(R.string.Import_Progress_splitting_m4b_file) + chapterIndex + "/" + nbChapters
                                + "\n" + "\n" + chapterFileName);


                // Write to AAC file
                FileOutputStream fos = new FileOutputStream(new File(destinationFolderPath, aacFileName));
                for (int i = startSample; i < endSample && i < audioSamples.size(); i++) {
                    ByteBuffer audioBuffer = audioSamples.get(i).asByteBuffer();
                    byte[] frame = new byte[audioBuffer.remaining()];
                    audioBuffer.get(frame);
                    byte[] adtsHeader = buildAdtsHeader(frame.length, 2, 4, 2);
                    fos.write(adtsHeader);
                    fos.write(frame);
                }
                fos.close();
                chapterIndex++;
            }

        } catch (Exception e) {

            tellError(getString(R.string.Error_Import_Split_M4B) + "\n\n" + e.getMessage());
            //mCallBacks.tellNonBlockingError("Error : blablabla... " + e.getMessage());

        } finally {
            try {
                if (m4bFile.delete()) { // if Exception, the catch delete the all folder...
                    myLog("M4b split done in folder, internal m4b file deleted");
                } else {
                    myLogE("M4b split done in folder, ERROR deleting internal m4b file");
                }
            } catch (Exception e2) {
                myLogE("could not delete the original m4b file - " + e2.getMessage());
            }
        }

        myLog("file has been split");
        tellEnd(destinationFolderPath);
        return true;
    }


    private static int findSampleIndexForTime(long[] durations, long timescale, double timeInSec) {
        long targetTime = (long) (timeInSec * timescale);
        long accumulated = 0;
        for (int i = 0; i < durations.length; i++) {
            if (accumulated >= targetTime) {
                return i;
            }
            accumulated += durations[i];
        }
        return durations.length - 1;
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

    private String extractCleanChapterTitle(Sample sample) {
        ByteBuffer buffer = sample.asByteBuffer();
        byte[] data = new byte[buffer.remaining()];
        buffer.get(data);

        // Log hex for debugging (optional)
        StringBuilder hex = new StringBuilder();
        for (byte b : data) {
            hex.append(String.format("%02X ", b));
        }
        myLog("Chapter sample raw bytes (hex): " + hex.toString());
        myLog("Chapter sample ASCII Text: " + new String(data, StandardCharsets.UTF_8));

        // Skip first 2 bytes (likely length or style info)
        int offset = 2;
        if (data.length <= offset) return "chapter";

        byte[] trimmed = Arrays.copyOfRange(data, offset, data.length);
        String raw = new String(trimmed, StandardCharsets.UTF_8);

        // Clean trailing junk: remove 'encd' and anything after
        raw = raw.replaceAll("encd.*$", "");

        // Remove non-printable characters, BOM, etc.
        String cleaned = raw.replaceAll("[\\p{Cntrl}&&[^\r\n\t]]", "").replace("\uFEFF", "").trim();

        return cleaned.isEmpty() ? "chapter" : cleaned;
    }


    //////////////////////////////////////////////////////////////////////////////////////////
    // Callbacks
    //////////////////////////////////////////////////////////////////////////////////////////
    private void tellError(String errorText) {
        myLogE(errorText);
        if (mCallBacks != null) {
            mCallBacks.splitM4bService_tellError(errorText);
        }
        myLog("killing Service");
        if (backgroundThread != null && backgroundThread.isAlive()) {
            backgroundThread.interrupt();
        }
        stopSelf();
    }
    private void tellEnd(String destinationFolderPath) {
        mCallBacks.splitM4bService_tellEnd(destinationFolderPath);
        myLog("killing Service");
        stopSelf();
    }
    public void tellProgress(int progressVal, String progressText) {
        mCallBacks.splitM4bService_tellProgress(progressText, progressVal);
    }
    //////////////////////////////////////////////////////////////////////////////////////////
    private void myLog(String str) { KanLogger.myLog(this.getClass().getName(), str); }
    private void myLogE(String str) { KanLogger.myLogE(this.getClass().getName(), str); }

}
