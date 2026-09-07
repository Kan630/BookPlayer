package com.driot.bookplayer.player;

import android.content.Context;

import com.driot.bookplayer.utils.log.LoggerHelper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Records a live radio stream to a local file by tapping the bytes ExoPlayer itself reads for
 * playback (see {@link RecordingTapDataSource}) - not a second, independent HTTP connection. This
 * guarantees the recording is byte-for-byte what's actually playing: no separate connection means
 * no separate server-side "burst", and no drift between what's heard and what's saved. Only
 * progressive (plain HTTP audio) streams are supported: {@link #canRecord(String)} returns false
 * for HLS (.m3u8) streams, whose tap would also capture the manifest/segment-boundary bytes.
 */
public class RadioRecorder extends LoggerHelper {

    public interface Listener {
        /** Called once a recording stops, successful or not (file == null on failure/empty
         * capture - already cleaned up). */
        void onRecordingFinished(File file, String stationName, String coverUrl, String streamUrl, long stationId,
                long elapsedMs, long bytesWritten);
    }

    private final Context appCtx;
    private final Listener listener;
    private final Object lock = new Object();

    private volatile boolean active = false;
    private volatile long startTimeMs = 0;
    private final AtomicLong bytesWritten = new AtomicLong(0);

    // Guarded by lock
    private FileOutputStream out;
    private File outFile;
    private String stationName;
    private String coverUrl;
    private String streamUrl;
    private long stationId;

    public RadioRecorder(Context context, Listener listener) {
        super(RadioRecorder.class);
        this.appCtx = context.getApplicationContext();
        this.listener = listener;
    }

    public static boolean canRecord(String url) {
        return url != null && !url.isEmpty() && !ExoRadioPlayerEngine.isHlsUrl(url.toLowerCase());
    }

    public boolean isRecording() {
        return active;
    }

    public long getElapsedMs() {
        return active ? System.currentTimeMillis() - startTimeMs : 0;
    }

    public long getBytesWritten() {
        return bytesWritten.get();
    }

    public synchronized boolean start(String url, String stationName, String coverUrl, long stationId) {
        if (active) {
            myLogW("start() ignored: already recording");
            return false;
        }
        if (!canRecord(url)) {
            myLogW("start() refused: not a recordable (progressive) stream - " + url);
            return false;
        }

        String name = (stationName != null && !stationName.isEmpty()) ? stationName : "Radio";
        File folder = RadioRecordingHelper.buildRecordingFolder(appCtx, name);
        if (!folder.exists())
            folder.mkdirs();
        File file = new File(folder, RadioRecordingHelper.buildRecordingFileName());

        FileOutputStream fos;
        try {
            fos = new FileOutputStream(file);
        } catch (IOException e) {
            myLogEE(e, "start(): failed to open output file " + file.getAbsolutePath());
            return false;
        }

        synchronized (lock) {
            this.out = fos;
            this.outFile = file;
            this.stationName = name;
            this.coverUrl = coverUrl;
            this.streamUrl = url;
            this.stationId = stationId;
        }
        bytesWritten.set(0);
        startTimeMs = System.currentTimeMillis();
        active = true;
        myLogI("start(): recording station=[" + name + "] -> " + file.getAbsolutePath());
        return true;
    }

    /** Called by {@link RecordingTapDataSource} - on ExoPlayer's own loading thread, not the main
     * thread - with bytes it just read for playback. Cheap no-op when not recording. */
    void feed(byte[] buffer, int offset, int length) {
        if (!active)
            return;
        synchronized (lock) {
            if (out == null)
                return;
            try {
                out.write(buffer, offset, length);
                bytesWritten.addAndGet(length);
            } catch (IOException e) {
                myLogEE(e, "feed(): write failed, stopping recording");
                stopInternal();
            }
        }
    }

    public void stop() {
        if (!active)
            return;
        myLogI("stop() requested");
        stopInternal();
    }

    private void stopInternal() {
        File file;
        String name;
        String cover;
        String stream;
        long station;
        long bytes;
        long elapsed;
        synchronized (lock) {
            if (!active)
                return;
            active = false;
            try {
                if (out != null)
                    out.close();
            } catch (IOException ignored) {
            }
            file = outFile;
            name = stationName;
            cover = coverUrl;
            stream = streamUrl;
            station = stationId;
            bytes = bytesWritten.get();
            elapsed = System.currentTimeMillis() - startTimeMs;
            out = null;
            outFile = null;
        }

        if (bytes > 0) {
            myLogI("RadioRecorder finished: station=" + name + " bytes=" + bytes + " elapsedMs=" + elapsed
                    + " file=" + (file != null ? file.getAbsolutePath() : "?"));
            listener.onRecordingFinished(file, name, cover, stream, station, elapsed, bytes);
        } else {
            myLogW("RadioRecorder produced no usable data, discarding " + (file != null ? file.getAbsolutePath() : "?"));
            if (file != null) {
                //noinspection ResultOfMethodCallIgnored
                file.delete();
            }
            listener.onRecordingFinished(null, name, cover, stream, station, elapsed, bytes);
        }
    }
}
