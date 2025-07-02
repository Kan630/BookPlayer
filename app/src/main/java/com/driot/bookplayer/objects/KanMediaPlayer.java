package com.driot.bookplayer.objects;

import android.media.MediaPlayer;
import android.net.Uri;
import android.content.Context;
import android.os.Build;

import com.driot.bookplayer.utils.KanLogger;

import java.io.IOException;

public class KanMediaPlayer extends MediaPlayer {

    private boolean isPrepared = false;
    private boolean isPreparing = false;

    @Override
    public void prepareAsync() {
        isPrepared = false;
        isPreparing = true;
        super.prepareAsync();
    }

    @Override
    public void reset() {
        isPrepared = false;
        isPreparing = false;
        super.reset();
    }

    @Override
    public void release() {
        isPrepared = false;
        isPreparing = false;
        listener = null;
        super.release();
    }

    public boolean isReady() {
        return isPrepared;
    }

    public boolean isPreparing() {
        return isPreparing;
    }

    public boolean isReleasedOrReset() {
        return !isPrepared && !isPreparing;
    }

    public interface Listener {
        void onCompletion();
        void onPrepared();
        void onError(String errorMsg, int what, int extra);
        void onFatalError(String errorMsg, int what, int extra);
    }

    private Listener listener;

    public void setListener(Listener l) {
        this.listener = l;
    }

    public KanMediaPlayer() {
        super();

        setOnCompletionListener(mp -> {
            myLog("setOnCompletionListener");
            isPrepared = false;
            if (listener != null) listener.onCompletion();
        });

        setOnPreparedListener(mp -> {
            myLog("setOnPreparedListener");
            isPrepared = true;
            isPreparing = false;
            if (listener != null) listener.onPrepared();
        });

        setOnErrorListener((mp, what, extra) -> {
            isPrepared = false;
            isPreparing = false;
            myLogE("setOnErrorListener");
            if (isFatalError(what, extra)) {
                if (listener != null) {
                    listener.onFatalError("MediaPlayer Error", what, extra);
                } else {
                    listener.onError("MediaPlayer Error", what, extra);
                }
            }
            return true; // should always be true
        });



    }

    // maybe next step is to move here the loadFile logic...
    public void loadAndPrepare(Context context, Uri uri) throws IOException {
        reset();
        isPrepared = false;
        setDataSource(context, uri);
        prepareAsync();
    }

    @Override
    public void seekTo(int posMilliSec) {
        if (isPrepared) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                super.seekTo(posMilliSec);
            } else {
                if (PlayList.getInstance().getZikFile() != null && PlayList.getInstance().getZikFile().isM4b()) {
                    super.seekTo(posMilliSec, SEEK_CLOSEST);  //seek_closest needed for m4b...
                    KanLogger.myLogD("SEEK_CLOSEST (m4b)");
                } else {
                    super.seekTo(posMilliSec);
                }
            }
        } else {
            myLogE("seekTo(" + posMilliSec + ") - not prepared");
        }
    }

    @Override
    public int getDuration() {
        if (isPrepared) {
            return super.getDuration();
        } else {
            myLogE("getDuration() - not prepared");
            return 0;
        }
    }

    @Override
    public int getCurrentPosition() {
        if (isPrepared) {
            return super.getCurrentPosition();
        } else {
            myLogE("getCurrentPosition() - not prepared");
            return 0;
        }
    }



    private boolean isFatalError(int what, int extra) {
        String whatString;
        boolean alertUser = true;

        switch (what) {
            case MediaPlayer.MEDIA_ERROR_UNKNOWN:
                whatString = "MEDIA_ERROR_UNKNOWN";
                break;
            case MediaPlayer.MEDIA_ERROR_SERVER_DIED:
                whatString = "MEDIA_ERROR_SERVER_DIED";
                break;
            case MediaPlayer.MEDIA_ERROR_NOT_VALID_FOR_PROGRESSIVE_PLAYBACK:
                whatString = "MEDIA_ERROR_NOT_VALID_FOR_PROGRESSIVE_PLAYBACK";
                break;
            case MediaPlayer.MEDIA_ERROR_IO:
                whatString = "MEDIA_ERROR_IO";
                break;
            case MediaPlayer.MEDIA_ERROR_MALFORMED:
                whatString = "MEDIA_ERROR_MALFORMED";
                break;
            case MediaPlayer.MEDIA_ERROR_UNSUPPORTED:
                whatString = "MEDIA_ERROR_UNSUPPORTED";
                break;
            case MediaPlayer.MEDIA_ERROR_TIMED_OUT:
                whatString = "MEDIA_ERROR_TIMED_OUT";
                break;
            default:
                whatString = "UNKNOWN_CODE_" + what;
                alertUser = false;
        }

        String msg = "MediaPlayer Error:\n" +
                "Type: " + whatString + " (" + what + ")\n" +
                "Extra: " + extra;

        KanLogger.myLogE(msg);
        return alertUser;
    }


    public static void safeRelease(KanMediaPlayer player) {
        if (player != null) {
            try {
                if (player.isPlaying()) player.stop();
            } catch (IllegalStateException e) {
                myLogE("safeRelease() - stop() failed: " + e.getMessage());
            }

            try {
                player.reset();
            } catch (IllegalStateException e) {
                myLogE("safeRelease() - reset() failed: " + e.getMessage());
            }

            try {
                player.setListener(null); // <-- Prevent memory leak
                player.release();
                myLog("safeRelease() - player released");
            } catch (Exception e) {
                myLogEE(e, "safeRelease() - release failed");
            }
        }
    }






    // ----------------------- LOG -----------------------
    private static final String TAG = "KanMediaPlayer";
    private static void myLog(String str) { KanLogger.myLog(TAG, str); }
    private static void myLogD(String str) { KanLogger.myLogD(TAG, str); }
    private static void myLogW(String str) { KanLogger.myLogW(TAG, str); }
    private static void myLogE(String str) { KanLogger.myLogE(TAG, str); }
    private static void myLogEE(Throwable t, String str) { KanLogger.myLogEE(t, TAG, str); }
    private static void myToastEE(Throwable t, String str) { KanLogger.myToastEE(t, TAG, str); }

}
