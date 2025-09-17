package com.driot.bookplayer.services;

import com.driot.bookplayer.R;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.ResultReceiver;
import android.service.notification.StatusBarNotification;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.view.KeyEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.documentfile.provider.DocumentFile;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.media.session.MediaButtonReceiver;

import com.driot.bookplayer.helpers.TtsHelper;
import com.driot.bookplayer.helpers.TextExtractor;
import com.driot.bookplayer.helpers.UriHelper;
import com.driot.bookplayer.helpers.LanguageHelper;
import com.driot.bookplayer.objects.KanMediaPlayer;
import com.driot.bookplayer.objects.VoiceItem;
import com.driot.bookplayer.utils.log.LoggingService;
import com.driot.bookplayer.activities.PlayActivity;
import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.db.ZikFileDao;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.objects.PlayList;
import com.driot.bookplayer.db.Sql;
import com.driot.bookplayer.db.ZikFile;
import com.driot.bookplayer.global.Pref;
import com.driot.bookplayer.utils.Tonio;

import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static com.driot.bookplayer.activities.PlayActivity.SHARED_PREFERENCE_SPEED;
import static com.driot.bookplayer.utils.Tonio.FormatPercentDouble;
import static com.driot.bookplayer.utils.Tonio.fileExists;
import static com.driot.bookplayer.utils.Tonio.formatTime;

/**
 * created by Antoine Driot -- antoine.driot.com -- on 01/11/20
 */

public class AudioService extends LoggingService {

    public static volatile boolean isRunning = false;

    private static final String ID_NOTIFICATION_PLAY_AUDIO_CHANNEL = "audio_channel_of_bookplayer";
    private static final int ID_NOTIFICATION_PLAY_AUDIO_INT = 2;

    public static final String NOTIFICATION_TTS_RANGE = "NOTIFICATION_TTS_RANGE";
    public static final String EXTRA_TTS_START = "EXTRA_TTS_START";
    public static final String EXTRA_TTS_END   = "EXTRA_TTS_END";

    //Play Timer (for Sleep)
    public static final int DELAY_CHECK_TIMER_SLEEP = 1000;
    private Handler sleepCheckHandler;
    private Runnable sleepTimerRunnable;
    private int elapsedSeconds = 0;
    private int customSleepTime = 0;

    //Pause Timer (to free memory)
    public static final int TRIM_MEMORY_THRESHOLD = 20;
    public static final int DELAY_CHECK_TIMER_PAUSE = 60*1000;
    public static final int TRIM_AFTER_PAUSE_MS = 7*24*60*60*1000; // so basically never... 7 days
    //public static final int DELAY_CHECK_TIMER_PAUSE = 2*1000;
    //public static final int TRIM_AFTER_PAUSE_MS = 5*1000; // so basically never... 7 days
    private Handler pauseCheckHandler;
    private Runnable pauseCheckRunnable;

    public static final int[][] REWIND_AFTER_PAUSE = {  // stopped listening since (in min)  ,  rewind delay (in ms)
            {2, 3000},
            {30, 5000},
            {60*12, 10000},
            {60*36, 15000},
            {60*24*3, 20000},
            {60*24*30, 30000},
    };
    long playbackStateCompatAction = PlaybackStateCompat.ACTION_PLAY |
            PlaybackStateCompat.ACTION_PAUSE |
            PlaybackStateCompat.ACTION_STOP |
            PlaybackStateCompat.ACTION_REWIND |
            PlaybackStateCompat.ACTION_FAST_FORWARD |
            PlaybackStateCompat.ACTION_SKIP_TO_NEXT |
            PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS |
            PlaybackStateCompat.ACTION_PLAY_PAUSE
            ;



    private static final boolean LOG_TRACE_ALL = false;

    private final IBinder binder = new BackgroundBinder();
    public static final String TRACKNUMBER = "tracknumber";
    public static final String FROM = "from";
    public static final String ERR_MSG = "err_msg";
    public static final String TIMER_VALUE = "TIMER_VALUE";
    public static final String READY_TO_PLAY = "NOTIFICATION_FILELOADED";
    public static final String NOTIFICATION_NEWTRACK = "NOTIFICATION_NEWTRACK";
    public static final String NOTIFICATION_TRACKFINISHED = "NOTIFICATION_TRACKFINISHED";
    public static final String NOTIFICATION_FILENOTFOUND = "NOTIFICATION_FILENOTFOUND";
    public static final String NOTIFICATION_ERROR = "NOTIFICATION_ERROR";
    public static final String NOTIFICATION_AUDIOFOCUS_LOST = "NOTIFICATION_AUDIOFOCUS_LOST";
    public static final String NOTIFICATION_AUDIOFOCUS_GAIN = "NOTIFICATION_AUDIOFOCUS_GAIN";
    public static final String NOTIFICATION_ZIP_FILE_LOADED = "NOTIFICATION_ZIP_FILE_LOADED";
    public static final String NOTIFICATION_PLAYLISTFINISHED = "NOTIFICATION_PLAYLISTFINISHED";
    public static final String NOTIFICATION_PLAYBACK_MAXTIMEREACH = "NOTIFICATION_PLAYBACK_MAXTIMEREACH";
    public static final String NOTIFICATION_PLAYBACK_TIMER_VALUE = "NOTIFICATION_PLAYBACK_TIMER_VALUE";

    private void sendReadyToPlay(String why) {
        boolean ok = (engine != null && engine.isReady() && !ErrorLoadingFile);
        myLogD("sendReadyToPlay? [" + why + "] ok=" + ok + " ttsMode=" + isTtsMode());
        if (!ok) return;

        Intent i = new Intent(READY_TO_PLAY);
        LocalBroadcastManager.getInstance(this).sendBroadcast(i);
    }


    private interface PlayerEngine {
        void setDataSource(@NonNull Context ctx, @NonNull Uri uri, @NonNull String displayName) throws Exception;

        void prepareAsync();

        void start();

        void pause();

        void stop();

        void reset();

        boolean isPlaying();

        boolean isReady();

        int getCurrentPosition();

        int getDuration();

        int getAudioSessionId();

        void seekTo(int positionMs);

        void setSpeed(float speed);
    }

    private PlayerEngine engine;
    private final Runnable onPrepared = this::onEnginePrepared;

    private final class MediaPlayerEngine implements PlayerEngine {
        private final KanMediaPlayer mp = new KanMediaPlayer();
        private boolean prepared = false;

        MediaPlayerEngine() {
            mp.setListener(new KanMediaPlayer.Listener() {
                @Override public void onCompletion() { onEngineCompletion(); }
                @Override public void onPrepared()    { prepared = true; onPrepared.run(); }
                @Override public void onError(String msg, int what, int extra)  { onEngineError(msg, what, extra); }
                @Override public void onFatalError(String msg, int what, int extra) { onEngineFatal(msg, what, extra); }
            });
        }

        @Override public void setDataSource(Context ctx, Uri uri, String displayName) throws IOException {
            prepared = false;
            mp.reset();
            mp.setDataSource(ctx, uri);
        }

        @Override public void prepareAsync() { mp.prepareAsync(); }
        @Override public void start()        { mp.start(); }
        @Override public void pause()        { mp.pause(); }
        @Override public void stop()         { mp.stop(); }
        @Override public void reset()        { mp.reset(); prepared = false; }
        @Override public boolean isPlaying() { return mp.isPlaying(); }
        @Override public boolean isReady()   { return prepared && !mp.isPreparing(); }
        @Override public int getCurrentPosition() { return mp.getCurrentPosition(); }
        @Override public int getDuration()        { return mp.getDuration(); }
        @Override public int getAudioSessionId()  { return mp.getAudioSessionId(); }
        @Override public void seekTo(int ms)      { mp.seekTo(ms); }
        @Override public void setSpeed(float s) {
            try { mp.setPlaybackParams(mp.getPlaybackParams().setSpeed(s)); } catch (Throwable t) { myLogEE(t,"setSpeed mp"); }
        }
    }

    private final class TtsEngine implements PlayerEngine, TtsHelper.Listener {
        private final TtsHelper tts;
        private String text = "";
        private int resumeOffset = 0;    // char index
        private boolean prepared = false;
        private boolean playing = false;
        private int estDurationMs = 0;   // rough estimate
        private int estPositionMs = 0;
        private java.util.Locale currentLocale = java.util.Locale.getDefault();
        private int lastCharSpoken = 0;     // farthest char index we’ve seen from onUtteranceRange
        private final android.os.Handler ttsH = new android.os.Handler(android.os.Looper.getMainLooper());
        private volatile boolean langReady = false;

        TtsEngine(Context ctx) {
            tts = new TtsHelper(ctx.getApplicationContext(), this);
        }

        private final java.util.concurrent.ConcurrentHashMap<String, WarmupCallback> warmups = new java.util.concurrent.ConcurrentHashMap<>();

        public interface WarmupCallback {
            /**
             * ready==true when synthesis completed; else reason explains why.
             */
            void onResult(boolean ready, @androidx.annotation.IntRange(from = 0, to = 5) int reason);
        }

        private void installUplIfNeeded() {
            tts.setOnUtteranceProgressListener(new android.speech.tts.UtteranceProgressListener() {
                @Override
                public void onStart(String id) {
                    myLog("installUplIfNeeded onStart");
                }

                @Override
                public void onDone(String id) {
                    myLog("installUplIfNeeded Done");
                    WarmupCallback cb = warmups.remove(id);
                    if (cb != null) cb.onResult(true, TtsHelper.READY);
                    deleteTemp(id);
                }

                @Override
                public void onError(String id, int code) {
                    myLogE("installUplIfNeeded onError");
                    WarmupCallback cb = warmups.remove(id);
                    if (cb != null) cb.onResult(false, TtsHelper.SYNTH_FAIL);
                    deleteTemp(id);
                }

                @Override
                public void onError(String id) {
                    onError(id, android.speech.tts.TextToSpeech.ERROR);
                }
            });
        }

        private void deleteTemp(String id) {
            try {
                new java.io.File(getApplicationContext().getCacheDir(), id + ".wav").delete();
            } catch (Throwable ignored) {
            }
        }

        /**
         * Sets the voice, verifies language data, then warms up via synthesizeToFile. Calls back when really ready.
         */
        public void setTtsVoiceByNameAsync(String voiceName, long timeoutMs, WarmupCallback cb) {
            if (tts == null) {
                cb.onResult(false, TtsHelper.ERROR);
                return;
            }
            installUplIfNeeded();

            ttsH.post(() -> {
                try {
                    java.util.Set<android.speech.tts.Voice> voices = tts.getVoices();

                    android.speech.tts.Voice target = null;
                    for (android.speech.tts.Voice v : voices) {
                        if (voiceName != null && voiceName.equals(v.getName())) {
                            target = v;
                            break;
                        }
                    }
                    if (target == null) {
                        cb.onResult(false, TtsHelper.SET_VOICE_FAILED);
                        return;
                    }

                    int rSet = tts.setVoice(target);
                    myLog("setTtsVoiceByName -> " + rSet + " | " + VoiceItem.describeVoice(target));
                    if (rSet != android.speech.tts.TextToSpeech.SUCCESS) {
                        cb.onResult(false, TtsHelper.SET_VOICE_FAILED);
                        return;
                    }

                    // Align language with voice locale; also tells us if data is missing.
                    java.util.Locale loc = target.getLocale();
                    if (loc != null) {
                        int avail = tts.isLanguageAvailable(loc);
                        myLogD("isLanguageAvailable(" + loc + ") -> " + avail);
                        if (avail == android.speech.tts.TextToSpeech.LANG_MISSING_DATA) {
                            cb.onResult(false, TtsHelper.MISSING_DATA);
                            return;
                        }
                        tts.setLanguage(loc);
                    }

                    // Warm up: synthesize a tiny file (silent to user), mark ready on onDone.
                    String id = "warmup-" + android.os.SystemClock.uptimeMillis();
                    warmups.put(id, cb);

                    java.io.File out = new java.io.File(getApplicationContext().getCacheDir(), id + ".wav");
                    int rr = tts.synthesizeToFile("ok", new android.os.Bundle(), out, id);
                    if (rr != android.speech.tts.TextToSpeech.SUCCESS) {
                        warmups.remove(id);
                        cb.onResult(false, TtsHelper.SYNTH_FAIL);
                        deleteTemp(id);
                        return;
                    }

                    // Timeout safeguard
                    ttsH.postDelayed(() -> {
                        WarmupCallback late = warmups.remove(id);
                        if (late != null) {
                            cb.onResult(false, TtsHelper.TIMEOUT);
                            deleteTemp(id);
                        }
                    }, Math.max(1500, timeoutMs)); // e.g., 5_000ms for network voices
                } catch (Throwable t) {
                    myLogEE(t, "setTtsVoiceByNameAsync failed");
                    cb.onResult(false, TtsHelper.ERROR);
                }
            });
        }


        @Override
        public void setDataSource(Context ctx, Uri uri, String displayName) {
            prepared = false;
            playing = false;
            resumeOffset = 0;
            estPositionMs = 0;
            String raw = TextExtractor.getPlainText(ctx, uri, displayName);

            // Normalize line endings (CRLF/CR → LF)
            String norm = raw.replace("\r\n", "\n").replace('\r', '\n');

            // If suspiciously few newlines, add paragraph breaks heuristically
            if (TtsHelper.countNewlines(norm) < 2) {
                norm = TtsHelper.smartParagraphize(norm);
            }

            text = norm;
            estDurationMs = estimateDurationMs(text, currentSpeechRate());
        }

        @Override
        public void prepareAsync() {
            //just after load file
            if (tts.isReady()) {
                applyInitialTtsLanguage();  // will only onPrepared.run() if langReady
            }
        }

        @Override
        public void start() {
            if (!prepared) return;
            playing = true;
            lastCharSpoken = Math.max(0, Math.min(resumeOffset, (text != null) ? text.length() : 0));
            tts.setSpeechRate(currentSpeechRate());
            tts.speakFromOffset(text, resumeOffset);
            updatePlaybackState(PlaybackStateCompat.STATE_PLAYING, getCurrentPosition(), currentSpeechRate());
            startSleepTimer();
        }

        @Override
        public void pause() {
            if (!prepared) return;
            tts.stop();
            playing = false;
            updatePlaybackState(PlaybackStateCompat.STATE_PAUSED, getCurrentPosition(), 0f);
            Pref.setPauseTime();
            stopSleepTimer();
        }

        @Override
        public void stop() {
            tts.stop();
            playing = false;
            updatePlaybackState(PlaybackStateCompat.STATE_STOPPED, 0, 0f);
        }

        @Override
        public void reset() {
            stop();
            prepared = false;
            resumeOffset = 0;
            estPositionMs = 0;
            text = "";
        }

        @Override
        public boolean isPlaying() {
            return playing;
        }

        @Override
        public boolean isReady() {
            return tts.isReady() && prepared && langReady;
        }

        @Override
        public int getCurrentPosition() {
            return estPositionMs;
        }

        @Override
        public int getDuration() {
            return estDurationMs;
        }

        @Override
        public int getAudioSessionId() {
            return 0;
        } // no visualizer for TTS

        @Override
        public void seekTo(int ms) {
            if (estDurationMs <= 0 || text.isEmpty()) return;
            int clamped = Math.max(0, Math.min(ms, estDurationMs));
            int charPos = (int) ((clamped / (double) estDurationMs) * Math.max(1, text.length()));
            resumeOffset = charPos;
            estPositionMs = clamped;
            if (playing) {
                tts.stop();
                tts.speakFromOffset(text, resumeOffset);
            }
        }

        @Override
        public void setSpeed(float s) {
            tts.setSpeechRate(s);
            int old = estDurationMs;
            estDurationMs = estimateDurationMs(text, s);
            if (old > 0) estPositionMs = (int) (estPositionMs * (estDurationMs / (double) old));
            if (playing)
                updatePlaybackState(PlaybackStateCompat.STATE_PLAYING, getCurrentPosition(), s);
        }

        // --- EbookTtsHelper.Listener ---
        @Override
        public void onTtsReady(TextToSpeech t) {
            applyInitialTtsLanguage();
        }

        @Override
        public void onStart(String id) {
            playing = true;
        }

        @Override
        public void onDone(String id) {
            // Have we reached (logically) the end of the text?
            int logicalEnd = logicalTextEndIndex();
            if (lastCharSpoken >= logicalEnd) {
                playing = false;
                onEngineCompletion(); // true end of book/chapter
                return;
            }

            // Not at end yet → queue the remainder from where we left off
            // (post to the main thread to avoid reentrancy)
            ttsH.post(() -> {
                if (!playing) return; // user might have paused meanwhile
                resumeOffset = lastCharSpoken; // continue from last known char
                try {
                    tts.speakFromOffset(text, resumeOffset);
                } catch (Throwable t) {
                    playing = false;
                    onEngineError("TTS continue failed", -1, 0);
                }
            });
        }

        @Override
        public void onError(String id, int code) {
            playing = false;
            onEngineError("TTS error", code, 0);
        }

        @Override
        public void onUtteranceRange(int start, int end) {
            if (!text.isEmpty())
                estPositionMs = (int) ((start / (double) text.length()) * estDurationMs);
            // remember farthest character we actually spoke
            if (end > lastCharSpoken) lastCharSpoken = end;

            updatePlaybackState(playing ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED,
                    getCurrentPosition(), currentSpeechRate());

            Intent i = new Intent(NOTIFICATION_TTS_RANGE)
                    .putExtra(EXTRA_TTS_START, start)
                    .putExtra(EXTRA_TTS_END, Math.min(end, text.length()));
            LocalBroadcastManager.getInstance(AudioService.this).sendBroadcast(i);
        }

        @Override
        public void onWordRange(int s, int e) {
            onUtteranceRange(s, e);
        }

        private float currentSpeechRate() {
            return (float) Math.max(0.1, getSpeed());
        }

        private void applyInitialTtsLanguage() {
            // 1) decide the 2-letter code to use
            String code = Option.getTtsLanguage(); // "system" or ISO-639-1
            ZikFile zf = getCurrentZikFile();
            if (zf != null) {
                String perBook = getBookTtsLanguage(zf.getIdFolder()); // return "" or "system" if unset
                if (perBook != null && !perBook.isEmpty()) code = perBook;
            }

            // 2) map to Locale
            java.util.Locale target;
            if (code.isEmpty() || "system".equalsIgnoreCase(code)) {
                target = java.util.Locale.getDefault();
            } else {
                // Prefer your helper if it exists
                try {
                    java.util.Locale maybe = LanguageHelper.localeFromTwoLetter(code);
                    target = (maybe != null) ? maybe : new java.util.Locale(code.toLowerCase(java.util.Locale.ROOT));
                } catch (Throwable ignored) {
                    target = new java.util.Locale(code.toLowerCase(java.util.Locale.ROOT));
                }
            }

            // 3) apply to engine state + Android TTS
            currentLocale = target;
            int ret = TextToSpeech.LANG_NOT_SUPPORTED;
            try {
                ret = tts.setLanguage(currentLocale);
            } catch (Throwable ignored) {
            }

            // Only mark language ready if not missing/not unsupported
            langReady = (ret != TextToSpeech.LANG_MISSING_DATA && ret != TextToSpeech.LANG_NOT_SUPPORTED);

            // Only consider ourselves 'prepared' when language data is ready
            prepared = langReady;
            // Do NOT call onPrepared if lang isn't ready yet
            if (prepared) onPrepared.run();

            // optional: recompute estimate since language/prosody can change:
            estDurationMs = estimateDurationMs(text, currentSpeechRate());
        }

        public java.util.Locale currentLocale() {
            return currentLocale;
        }


        public void setStartOffsetChars(int charOffset) {
            if (text == null) return;
            int target = Math.max(0, Math.min(charOffset, text.length()));

            resumeOffset = target;

            // update estimated position for seekbar
            if (estDurationMs > 0 && !text.isEmpty()) {
                estPositionMs = (int) ((resumeOffset / (double) text.length()) * estDurationMs);
            } else {
                estPositionMs = 0;
            }

            if (playing && prepared) {
                tts.stop();
                tts.speakFromOffset(text, resumeOffset);
                updatePlaybackState(PlaybackStateCompat.STATE_PLAYING, getCurrentPosition(), currentSpeechRate());
            } else if (prepared) {
                updatePlaybackState(PlaybackStateCompat.STATE_PAUSED, getCurrentPosition(), 0f);
            }
        }

        private int logicalTextEndIndex() {
            if (text == null) return 0;
            int i = text.length();
            while (i > 0) {
                char ch = text.charAt(i - 1);
                // trim common trailing non-spoken characters
                if (Character.isWhitespace(ch) || ch == '\u200B' || ch == '\uFEFF') i--;
                else break;
            }
            return i;
        }


    }
    // ----------------------------------------------------------------
    //  END TTS ENGINE
    // ----------------------------------------------------------------

    private AudioManager audioManager;
    private AudioManager.OnAudioFocusChangeListener afChangeListener;
    private MediaSessionCompat mediaSession;

    public boolean directPlay;


    private final MediaSessionCompat.Callback callback = new MediaSessionCompat.Callback() {

        @Override
        public void onPlay() { // is called by headset button pressed !!!
            myLog("MediaSessionCompat.Callback - onPlay()");
            super.onPlay();
            playPauseAudio();
        }

        @Override
        public void onPause() {
            myLog("MediaSessionCompat.Callback - onPause()");
            super.onPause();
            playPauseAudio();
        }
        @Override
        public void onStop() {
            myLog("MediaSessionCompat.Callback - onStop()");
            super.onStop();
            /*  This was a large source of bugs... by killing mediaPlayer... after a pause... everything shit after...
            mediaPlayerStop();
            stopForeground(false);
             */
        }
        @Override
        public boolean onMediaButtonEvent(Intent mediaButtonIntent) {
            KeyEvent ke = mediaButtonIntent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
            myLog("MediaSessionCompat.Callback - onMediaButtonEvent -- Received command = " + ke);
            // not needed anymore
            //if (ke != null && ke.getAction() == KeyEvent.ACTION_DOWN) handleKeyEvent(ke.getKeyCode());
            return super.onMediaButtonEvent(mediaButtonIntent);
        }

        @Override
        public void onFastForward() {
            super.onFastForward();
            myLog("MediaSessionCompat.Callback - onFastForward()");
        }

        @Override
        public void onCommand(String command, Bundle extras, ResultReceiver cb) {
            myLog("MediaSessionCompat.Callback - onCommand(" + command + "..., Bundle extras, ResultReceiver cb");
            super.onCommand(command, extras, cb);
        }

        @Override
        public void onRewind() {
            myLog("MediaSessionCompat.Callback - onRewind()");
            super.onRewind();
        }

        @Override
        public void onSeekTo(long pos) {
            myLog("MediaSessionCompat.Callback - onSeekTo()");
            super.onSeekTo(pos);
        }

        @Override
        public void onSkipToNext() {
            forwardAudio();
            myLog("MediaSessionCompat.Callback - onSkipToNext()");
            super.onSkipToNext();
        }

        @Override
        public void onSkipToPrevious() {
            backwardAudio();
            myLog("MediaSessionCompat.Callback - onSkipToPrevious()");
            super.onSkipToPrevious();
        }
    };




    private double speed = 1.0;
    private boolean ErrorLoadingFile = false;
    DecimalFormat myDF = new DecimalFormat("#,###.");

    private boolean isTimerRunning = false;

    /********************************************************************************
     *       NATIVE METHODS

     *  Because service always runs in the same process as clients, no need IPC.
     *
     */
// ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    @Override
    public void onCreate() {
        isRunning = true;
        super.onCreate();

        startPauseTimer();

// ///////////////////////
//      PLAYER
// ///////////////////////



// ///////////////////////
//      MEDIA SESSION
// ///////////////////////
        mediaSession = new MediaSessionCompat(this, "BookplayerMediaSession");
        sleepCheckHandler = new Handler(); // for sleep timer

        myLogD("configureMediaSession()");

        // Overridden methods in the MediaSession.Callback class.
        mediaSession.setCallback(callback);
        mediaSession.setActive(true); // Needed for media button handling

        myLogD("onCreate() - END");
    }
// ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private void startPlayWithEngine() {
        audioManager = (AudioManager) AudioService.this.getSystemService(Context.AUDIO_SERVICE);
        afChangeListener = focusChange -> {
            if (focusChange <= 0) {
                myLogI("Audio Focus Lost");
                AudioService.this.pauseAudio();
                Intent intent = new Intent(NOTIFICATION_AUDIOFOCUS_LOST);
                LocalBroadcastManager.getInstance(AudioService.this).sendBroadcast(intent);
            } else {
                myLogI("Audio Focus Gain");
                myKeyFirebase("Audio Focus Gain", "Audio Focus Gain");
                AudioService.this.playAudio();
                mediaSession.setActive(true);
                Intent intent = new Intent(NOTIFICATION_AUDIOFOCUS_GAIN);
                LocalBroadcastManager.getInstance(AudioService.this).sendBroadcast(intent);
            }
        };

        // Rewind-after-pause (works for both engines)
        if (Option.getRewindAfterPause()) {
            ZikFile currentZik = PlayList.getInstance().getZikFile();
            if (currentZik != null && currentZik.lLastAccess != null) {
                long minutes = (System.currentTimeMillis() - currentZik.lLastAccess) / (60 * 1000);
                int rewindMs = 0;
                for (int[] rule : REWIND_AFTER_PAUSE) { if (minutes >= rule[0]) rewindMs = rule[1]; else break; }
                if (rewindMs > 0) { myLogD("Rewind after Pause: " + (rewindMs/1000) + "s"); backwardAudio(rewindMs); }
            }
        }

        doIntroCut();
        myLogD("about to call engine.start()");
        logPauseTime();
        engine.start();
        Pref.setPauseTime(0);
        updatePlaybackState(PlaybackStateCompat.STATE_PLAYING, engine.getCurrentPosition(), (float) getSpeed());
        engine.setSpeed((float) getSpeed());
        if (!mediaSession.isActive()) mediaSession.setActive(true);
        startSleepTimer();
        createNotificationChannel();
        createNotification();
    }

    void nextTrack() {
        myLog("Next track");
        PlayList.getInstance().nextTrack();
        if (engine != null) {
            try { engine.reset(); } catch (Exception ignored) {}
        }
        myLog("loading next track : n°" + PlayList.getInstance().getNumSlashTotal());

        // petit bip
        if (Option.getBeepChapter()) playBeep("1beep");
        directPlay = true;
        loadFile();
        alertNewTrack();
    }

    private void alertNewTrack() {
        LocalBroadcastManager.getInstance(AudioService.this).sendBroadcast(new Intent(NOTIFICATION_NEWTRACK).putExtra(TRACKNUMBER, PlayList.getInstance().getNumZikFile()));
        //createNotification();
        myLog("sendBroadcast alertNewTrack ");
    }

    private void alertError(String from, String errMsg) {
        LocalBroadcastManager.getInstance(AudioService.this).sendBroadcast(new Intent(NOTIFICATION_ERROR)
                .putExtra(TRACKNUMBER, PlayList.getInstance().getNumZikFile())
                .putExtra(FROM, from)
                .putExtra(ERR_MSG, errMsg)
        );
        stopSleepTimer();
        myLogE("sendBroadcast alertError");
    }

    private void alertTrackFinished() {
        LocalBroadcastManager.getInstance(AudioService.this).sendBroadcast(new Intent(NOTIFICATION_TRACKFINISHED));
        myLog("--------------------------------------------------------------------------------- sendBroadcast alertTrackFinished --------------------------------------------------------------------------------");
    }

    private void alertPlaylistFinished() {
        LocalBroadcastManager.getInstance(AudioService.this).sendBroadcast(new Intent(NOTIFICATION_PLAYLISTFINISHED));
        myLog("sendBroadcast alertPlaylistFinished");
    }

    private void handleKeyEvent(int keyCode) {
        switch (keyCode) { //now only for click on Notification
            case KeyEvent.KEYCODE_MEDIA_REWIND:
                myLog("KEYCODE_MEDIA_REWIND pressed");
                // Handle the rewind action
                backwardAudio();
                break;
            case KeyEvent.KEYCODE_MEDIA_PLAY:
                myLog("KEYCODE_MEDIA_PLAY pressed");
                // Handle the play action
                playPauseAudio();
                break;
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
                myLog("KEYCODE_MEDIA_PAUSE pressed");
                // Handle the pause action
                playPauseAudio();
                break;
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                myLog("KEYCODE_MEDIA_PLAY_PAUSE pressed");
                // Handle the pause action
                playPauseAudio();
                break;
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                myLog("KEYCODE_MEDIA_FAST_FORWARD pressed");
                // Handle the fast forward action
                forwardAudio();
                break;
            case KeyEvent.KEYCODE_HEADSETHOOK:
                myLog("KEYCODE_HEADSETHOOK pressed");
                playPauseAudio();
                break;
            case KeyEvent.KEYCODE_MEDIA_NEXT:
                myLog("KEYCODE_MEDIA_NEXT pressed");
                forwardAudio();
                break;
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                myLog("KEYCODE_MEDIA_PREVIOUS pressed");
                backwardAudio();
                break;
            // Add other cases for additional key codes as needed
            default:
                myLogE("Unknown key code: " + keyCode);
                break;
        }
    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        myLog("onStartCommand()"); //is called when user press icons buttons on Notification
        if (intent != null) {
            myLog("onStartCommand() - " + intent);
            if (Objects.equals(intent.getAction(), Intent.ACTION_MEDIA_BUTTON)) {
                if (intent.hasExtra(Intent.EXTRA_KEY_EVENT)) {
                    KeyEvent keyEvent = intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
                    if (keyEvent != null) {
                        int keyCode = keyEvent.getKeyCode();
                        handleKeyEvent(keyCode);
                    }
                }
            }
        }
        return START_STICKY; // usually better for audio playback service
    }

    @Override
    public void onDestroy() {
        isRunning = false;
        myLog("onDestroy()");
        stopSleepTimer();
        stopPauseTimer();
        stopForeground(true);
        if (engine != null) { try { engine.stop(); } catch (Exception ignored) {} }
        if (audioManager != null) { audioManager.abandonAudioFocus(afChangeListener); }
        if (mediaSession != null) { mediaSession.release(); }
        stopSelf();
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        myLog("onBind()");
        return binder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        myLog("onUnBind() - intent.DataString = " + intent.getDataString());
        return super.onUnbind(intent);
    }

    public class BackgroundBinder extends Binder {
        public AudioService getService() {
            return AudioService.this;
        }
    }

    /********************************************************************************
     ***       LOADING FILES
     ********************************************************************************
     */

    // TODO, use openFileDescriptor & remove legacy from manifest

    //called in :
    //* PlayActivity
    //* onPrepare
    //* onError
    public void loadFile() {
        myLogD("loadingFile.......  - Play Audio straight away : " + directPlay);

        if (PlayList.getInstance()==null || PlayList.getInstance().getZikFile()==null) { loadFileKO(); return; }
        ZikFile zf = PlayList.getInstance().getZikFile();

        Uri uriToPlay = null;
        String pathToPlay = null;

        // --- Your existing SAF/file/path resolution (kept) ---
        if (zf.getPath().startsWith("content://")) {
            myLog("New SAF file, content...");
            uriToPlay = UriHelper.buildFileUri(this, zf.getPath(), zf.getName());
            if (uriToPlay == null) { myLogEE(null,"buildFileUri returned null"); loadFileKO(); return; }
            DocumentFile file = DocumentFile.fromSingleUri(this, uriToPlay);
            if (!file.exists() || !file.isFile()) {
                myLogD("Try Single file");
                uriToPlay = Uri.parse(zf.getPath());
                file = DocumentFile.fromSingleUri(this, uriToPlay);
                if (!file.exists() || !file.isFile()) {
                    myLogEE(null,"Invalid SAF Uri: " + uriToPlay);
                    loadFileKO(); return;
                }
            }
        } else if (zf.getPath().startsWith("file://")) {
            Uri fileUri = Uri.parse(zf.getPath());
            String p = fileUri.getPath();
            if (p == null) { myLogEE(null,"Invalid file:// path"); loadFileKO(); return; }
            File f = new File(p);
            if (!f.exists() || !f.isFile()) {
                File maybe = new File(p, zf.getName());
                if (!maybe.exists() || !maybe.isFile()) { myLogEE(null,"File not found: " + p); loadFileKO(); return; }
                f = maybe; fileUri = Uri.fromFile(f);
            }
            uriToPlay = fileUri;
        } else {
            pathToPlay = zf.getPath();
            if (!fileExists(pathToPlay)) {
                myLogEE(null,"File doesn't exist: " + pathToPlay);
                pathToPlay = zf.getPath() + "/" + zf.getName();
                if (!fileExists(pathToPlay)) { myLogEE(null,"Still missing: " + pathToPlay); loadFileKO(); return; }
            }
            uriToPlay = Uri.fromFile(new File(pathToPlay));
        }

        // --- Decide engine: AUDIO vs TTS (text) ---
        final boolean isText =
                (zf.getPath()!=null && zf.getPath().toLowerCase().endsWith(".txt")) ||
                        (zf.getDisplayName()!=null && zf.getDisplayName().toLowerCase().endsWith(".txt"));

        engine = isText ? new TtsEngine(this) : new MediaPlayerEngine();
        ErrorLoadingFile = false;

        try {
            engine.reset();
            engine.setDataSource(this, uriToPlay, zf.getDisplayName());
            engine.prepareAsync();
        } catch (Exception e) {
            myLogEE(e, "ERROR loading source");
            loadFileKO();
        }
    }



    /********************************************************************************
     ***       PLAY-PAUSE
     ********************************************************************************
     */

    public void playAudio() {
        myLog("playAudio() - start");
        if (engine == null) {
            directPlay = true;
            loadFile();
            return;
        }
        if (engine.isPlaying()) {
            myLogE("Engine already playing");
            return;
        }
        if (engine.isReady()) {
            startPlayWithEngine();
        } else {
            myLog("Engine not ready yet; will start on prepared");
            // Don't arm directPlay while TTS is switching language
            boolean ttsSwitching = (engine instanceof TtsEngine) && !engine.isReady();
            if (!ttsSwitching) {
                directPlay = true;
            }
        }
    }



    private void doIntroCut() {
        myLog("doIntroCut");
        int introCut = 0;
        try {
            if (PlayList.getInstance().getZikFile()!=null) {
                introCut = Pref.getIntroCutFromPref(this,PlayList.getInstance().getZikFile().getIdFolder()) * 1000;
            }
        } catch (Exception e) {
            myLogEE(e, "Error getting introCut from Pref - getIdFolder null ?");
        }
        if (introCut > 0) {
            int position = getPosition();
            myLog("position : [" + position + "]  introCut : [" + introCut + "]");
            if (position < introCut) {
                forwardAudioTo(introCut);
                myLogI("=> Intro Cut");
            }
        }
    }

    public void pauseAudio() {
        myLog("pauseAudio()");
        if (engine != null && engine.isPlaying()) {
            mediaPlayerPause(); // this now pauses the engine
            if (mediaSession != null) {
                mediaSession.setActive(false);
            }
            updateZikFileStateInDB(false);
            if (audioManager != null) { audioManager.abandonAudioFocus(afChangeListener); }
            stopSleepTimer();
            createNotification();
        }
    }

    public void playPauseAudio() {
        myLog("playPauseAudio()");
        if (isPlaying()) {
            pauseAudio();
        } else {
            playAudio();
        }
    }
    public void forwardAudio() {
        forwardAudio(Option.get_ForwardSeconds()*1000);
    }
    public void forwardAudio(int lag) {
        myLog("forwardAudio of " + lag);
        int temp = getPosition();
        if ((temp + lag ) <= getDuration()) {
            setPosition(temp + lag );
        }
    }
    public void forwardAudioTo(int lag) {
        myLog("forwardAudio to " + lag);
        if (lag <= getDuration()) {
            setPosition(lag);
        }
    }
    public void backwardAudio() {
        backwardAudio(Option.get_ForwardSeconds()*1000);
    }
    public void backwardAudio(int lag) {
        myLog("backwardAudio() : " + lag);
        int temp = getPosition();
        if ((temp - lag) > 0) {
            setPosition(temp - lag);
        }
    }

    /********************************************************************************
     ***       SPEED - POSITION
     ********************************************************************************
     */

    public void setPosition(int position) {
        myLog("setPosition() : " + myDF.format(position));
        if (engine != null) engine.seekTo(position);
        createNotification();
    }

    public int getPosition() {
        int pos = engine != null ? engine.getCurrentPosition() : 0;
        if (LOG_TRACE_ALL && PlayList.getInstance()!=null && PlayList.getInstance().getZikFile()!=null) {
            int curPosGlobalVar = (int) PlayList.getInstance().getZikFile().getPosition();
            int diff = curPosGlobalVar - pos;
            myLogD("getPosition() Saved/EngineCurrent  " + curPosGlobalVar + "/" + pos + "  -  Diff = " + diff);
        }
        return pos;
    }

    public int getDuration() {
        return engine != null ? engine.getDuration() : 0;
    }

    public boolean isPlaying() {
        return engine != null && engine.isPlaying();
    }

    public int getAudioSessionId() {
        return engine != null ? engine.getAudioSessionId() : 0;
    }

    public void setSpeed(double speed) {
        try {
            this.speed = speed;
            if (engine != null && engine.isPlaying()) engine.setSpeed((float) speed);
            myLog("setSpeed() : " + speed);
        } catch (Exception e) { myLogEE(e,"AudioService Error setting Speed"); }
        ZikFile zf = getCurrentZikFile();
        if (zf != null) saveSpeedToPref(zf.getIdFolder(), speed);
    }


    public double getSpeed() {
        ZikFile zf = getCurrentZikFile();
        if (zf != null) speed = getSpeedFromPref(zf.getIdFolder());
        if (speed == 0) speed = 1.0;
        return speed;
    }

    public boolean isRunning() {
        if (LOG_TRACE_ALL) myLogD("isRunning : " + isRunning);
        return isRunning;
    }

    private @Nullable ZikFile getCurrentZikFile() {
        PlayList pl = PlayList.getInstance();
        return (pl != null) ? pl.getZikFile() : null;
    }

    /********************************************************************************
     ***       TIMER
     ********************************************************************************
     */

    private void startSleepTimer() {
        if (isTimerRunning) {
            myLogD("Timer is already running....");
            return;
            //stopSleepTimer();
        }

        boolean doBeep = Option.getBeepAutoStop();
        int timeBeforeSleep = customSleepTime == 0 ? Option.getTimeBeforeSleep() : customSleepTime;

        elapsedSeconds = 0;
        isTimerRunning = true;

        myLog("----------------------------------------------------------------------------- timer STARTED -- ");
        LocalBroadcastManager.getInstance(AudioService.this).sendBroadcast(new Intent(NOTIFICATION_PLAYBACK_TIMER_VALUE).putExtra(TIMER_VALUE, elapsedSeconds));

        sleepTimerRunnable = new Runnable() {
            @Override
            public void run() {
                myLogD("----------------------------------------------------------------------------- " + elapsedSeconds + "s. since timer started.....      (AutoSleep set to " + timeBeforeSleep + "min.)");
                updateZikFileStateInDB(false);

                // Auto Sleep Option
                if (elapsedSeconds > timeBeforeSleep * 60) {
                    myLog("Max Playback Time Reached -- Stopping Service");
                    stopSleepTimer();

                    // 2 beeps
                    if (doBeep) playBeep("2beeps");

                    LocalBroadcastManager.getInstance(AudioService.this).sendBroadcast(new Intent(NOTIFICATION_PLAYBACK_MAXTIMEREACH));
                    if (engine != null && engine.isPlaying()) {
                        mediaPlayerStop(); // this now stops the engine
                    }
                    stopSelf();
                } else {
                    Intent intent = new Intent(NOTIFICATION_PLAYBACK_TIMER_VALUE);
                    intent.putExtra(TIMER_VALUE, elapsedSeconds);
                    LocalBroadcastManager.getInstance(AudioService.this).sendBroadcast(intent);

                    elapsedSeconds += DELAY_CHECK_TIMER_SLEEP / 1000;

                    //HOHO
                    //createNotification();

                    sleepCheckHandler.postDelayed(this, DELAY_CHECK_TIMER_SLEEP);
                }
            }
        };

        sleepCheckHandler.postDelayed(sleepTimerRunnable, DELAY_CHECK_TIMER_SLEEP);
    }


    public void updateSleepTimer(int customSleepTime) {
        this.customSleepTime = customSleepTime;
        reloadSleepTimer();
    }
    public void reloadSleepTimer() {
        myLog("reloadSleepTimer()");
        if (isTimerRunning) {
            stopSleepTimer();
            startSleepTimer();
        }
    }
    public int getCustomSleepTime() {
        return customSleepTime;
    }

    private void stopSleepTimer() {
        myLog("stopSleepTimer()");
        try {
            if (sleepCheckHandler != null && sleepTimerRunnable != null) {
                sleepCheckHandler.removeCallbacks(sleepTimerRunnable);
            }
            isTimerRunning = false;
            String str;
            if (!(PlayList.getInstance().getZikFilesList()==null)) {
                ZikFile zf = getCurrentZikFile();
                str = zf==null ? "---" : zf.getFolderName() + " : " + Tonio.formatTime(elapsedSeconds*1000);
                myLog("----------------------------------------------------------------------------- " + elapsedSeconds + "s. since timer started -- STOPPED -- " + str );
            } else {
                str = "killTimer : ERROR zikFilePlayList==null";
                myLogE("----------------------------------------------------------------------------- " + elapsedSeconds + "s. since timer started -- STOPPED -- " + str );
            }
        } catch (Exception e) {
            myLogEE(e,"killTimer, nothing to kill ?");
        }
    }


    private void startPauseTimer() {
        myLogD("startPauseTimer");
        pauseCheckHandler = new Handler();
        pauseCheckRunnable = new Runnable() {
            @Override
            public void run() {
                if (Pref.getPauseTime() != 0) {
                    logPauseTime();
                    if (System.currentTimeMillis() - Pref.getPauseTime() > TRIM_AFTER_PAUSE_MS) {
                        myLogW("let's kill it");
                        killService();
                    }
                }
                pauseCheckHandler.postDelayed(this, DELAY_CHECK_TIMER_PAUSE);
            }
        };
        pauseCheckHandler.postDelayed(pauseCheckRunnable, DELAY_CHECK_TIMER_PAUSE); //start
    }
    private void stopPauseTimer() {
        if (pauseCheckHandler != null) {
            pauseCheckHandler.removeCallbacks(pauseCheckRunnable);
        }
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level >= TRIM_MEMORY_THRESHOLD) {
            myLogW("onTrimMemory() - level=[" + level + "] >= " + TRIM_MEMORY_THRESHOLD);
            logPauseTime();
            /*
            if (mediaPlayer != null) {
                try {
                    mediaPlayer.release();
                    mediaPlayer = null;
                    myLog("mediaPlayer released due to memory pressure");
                } catch (Exception e) {
                    myLogEE(e,"onTrimMemory - Error releasing mediaPlayer");
                }
            } else {
                myLog("mediaPlayer was already null");
            }
             */
        }
    }
    public void logPauseTime() {
        if (Pref.getPauseTime() != 0) {
            long pauseTime = (System.currentTimeMillis() - Pref.getPauseTime());
            myLogD("Paused since " + formatTime(pauseTime, true) + ".   MAX is " + formatTime(TRIM_AFTER_PAUSE_MS,false, false));
        }
    }


    /********************************************************************************
     ***       UPDATE DB
     ********************************************************************************
     */
    private void updateZikFileStateInDB(boolean bFinished) {
        ZikFile zf = getCurrentZikFile();
        if (zf==null) {
            myLogEE(null, "updateZikFileState : currentZikFile = null");
            return;
        }
        try {
            if (zf.lFirstAccess == null || zf.lFirstAccess == 0) {
                zf.lFirstAccess = System.currentTimeMillis();
            }
            zf.lLastAccess = System.currentTimeMillis();
            if (bFinished) {
                zf.setPosition(zf.getDuration());
                zf.setPercentdone(100);
                zf.setFinished(true);
            } else {
                zf.setPosition(getPosition());
                zf.setPercentdone(FormatPercentDouble((double) getPosition() / getDuration()));
            }
            new Thread(() -> {
                try {
                    AppDatabase db = AppDatabase.getDatabase(getApplicationContext());
                    ZikFileDao zikFileDao = db.ZikFileDao();
                    int mySqlresponse = zikFileDao.update(zf);
                    if (mySqlresponse > 0) {
                        myLogD("---------- zikFile updated (" + zf.getName() + ")- position : " + myDF.format(zf.getPosition()));
                        Sql.calculateFolderProgress(getApplicationContext(), zf.getIdFolder());
                    } else {
                        myLogE("updateZikFileState - Error sql response ---------- ZikFile NOT updated");
                    }
                } catch (Exception e) {
                    myLogEE(e,"updateZikFileState - Exception while Updating File progress in Thread");
                }
            }).start();
        } catch (Exception e) {
            myLogEE(e,"updateZikFileState - Exception while Updating File progress in Initialization");
        }
    }


    private void saveSpeedToPref(int idFolder, double speed) {
        try {
            SharedPreferences.Editor editor = getSharedPreferences(SHARED_PREFERENCE_SPEED, MODE_PRIVATE).edit();
            editor.putString(String.valueOf(idFolder),Double.toString(speed)).apply();
        } catch (Exception e) {
            myLogEE(e,"error saving speed in prefs");
        }
    }

    private double getSpeedFromPref(int idFolder) {
        try {
            SharedPreferences prefs = getSharedPreferences(SHARED_PREFERENCE_SPEED, MODE_PRIVATE);
            return Double.parseDouble(prefs.getString(String.valueOf(idFolder), "1.0"));
        } catch (Exception e) {
            myLogEE(e,"error getting speed from prefs");
            return 1.0;
        }
    }

    private String getBookTtsLanguage(int idFolder) {
        try {
            return Pref.getBookTtsLanguage(this, idFolder);
        } catch (Exception e) {
            myLogEE(e, "getBookTtsLanguage");
            return Option.getTtsLanguage();
        }
    }


    /********************************************************************************
     ***       NOTIFICATIONS
     ********************************************************************************
     */

    private void createNotification() {
        myLogD("createNotification()");

        final boolean playing = engine != null && engine.isPlaying();

        try {
            // custom addAction only ok on old Android devices... KO with Android 13+
            PendingIntent playPauseAction;
            String actionName;
            int actionIcon;
            if (playing) {
                actionName = "Pause";
                actionIcon = android.R.drawable.ic_media_pause;
                playPauseAction = MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_PAUSE);
                updatePlaybackState(PlaybackStateCompat.STATE_PLAYING,
                        engine != null ? engine.getCurrentPosition() : 0, (float) getSpeed());
            } else {
                actionName = "Play";
                actionIcon = android.R.drawable.ic_media_play;
                playPauseAction = MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_PLAY);
                updatePlaybackState(PlaybackStateCompat.STATE_PAUSED,
                        engine != null ? engine.getCurrentPosition() : 0, (float) getSpeed());
            }

            // Create an intent to open the app when the notification is tapped
            Intent openAppIntent = new Intent(this, PlayActivity.class);
            openAppIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP); // Ensures only one instance
            PendingIntent contentIntent = PendingIntent.getActivity(this, 0, openAppIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            MediaMetadataCompat metadata = new MediaMetadataCompat.Builder()
                    .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, engine != null ? engine.getDuration() : 0)
                    .build();
            mediaSession.setMetadata(metadata);


            ZikFile zf = getCurrentZikFile();
            String contentTitle = zf==null ? "---" : zf.getFolderName();
            String contentText = zf==null ? "---" : zf.getDisplayName();

            NotificationCompat.Builder builder = new NotificationCompat.Builder(this, ID_NOTIFICATION_PLAY_AUDIO_CHANNEL) // channel is used for user to be able to disable all notifications from that channel, starting android 8
                    .setContentTitle(contentTitle)
                    .setContentText(contentText)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentIntent(contentIntent)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .setOnlyAlertOnce(true)
                    .setOngoing(true) //if not, Notification may get destroyed by system
                    // custom addAction only ok on old Android devices... KO with Android 13+
                    .addAction(new NotificationCompat.Action(android.R.drawable.ic_media_rew, "Rewind", MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_REWIND)))
                    .addAction(new NotificationCompat.Action(actionIcon, actionName, playPauseAction))
                    .addAction(new NotificationCompat.Action(android.R.drawable.ic_media_ff, "Forward", MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_FAST_FORWARD)))
                    .setStyle(new androidx.media.app.NotificationCompat.MediaStyle()  //that's the shit that fuck with the progressBar... but yeah...
                            .setMediaSession(mediaSession.getSessionToken())
                            .setShowActionsInCompactView(0,1,2)
                    )
                    ;

            Notification notification = builder.build();

            try {

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { // sdk 29 (28 is Android 9)
                    myLogD("startForeground FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK");
                    startForeground(ID_NOTIFICATION_PLAY_AUDIO_INT, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
                } else {
                    myLogD("startForeground");
                    startForeground(ID_NOTIFICATION_PLAY_AUDIO_INT, notification);
                }
            } catch (Throwable t) {
                myLogEE(t,"Notification startForeground failed");
            }


        } catch (Exception t) {
            myLogEE(t,"Notification creation failed");
        }
    }

    private void createNotificationChannel() {
        myLog("createNotificationChannel()");
        try {
            NotificationChannel channel = new NotificationChannel(
                    ID_NOTIFICATION_PLAY_AUDIO_CHANNEL, "Music Playback",
                    NotificationManager.IMPORTANCE_LOW); //LOW = no sound
            channel.setDescription("Bookplayer Music Playback Controls");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        } catch (Exception e) {
            myLogEE(e,"createNotificationChannel()");
        }
    }

    private void removeNotification() {
        myLogD("removeNotification()");
        try {
            stopForeground(true); // Remove the notification and stop being a foreground service

            if (mediaSession != null) {
                mediaSession.setActive(false); // Deactivate media session
            }

            NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            notificationManager.cancel(ID_NOTIFICATION_PLAY_AUDIO_INT); // ID 1 matches the one used in startForeground()

            myLogI("Notification removed");
        } catch (Exception e) {
            myLogEE(e,"Failed to remove notification");
        }
    }

    private void mediaPlayerPause() {
        myLogD("mediaPlayerPause()");
        if (engine != null) engine.pause();
        updatePlaybackState(PlaybackStateCompat.STATE_PAUSED,
                engine != null ? engine.getCurrentPosition() : 0, 0.0f);
        Pref.setPauseTime();
    }

    private void mediaPlayerStop() {
        myLogD("mediaPlayerStop()");
        if (engine != null) engine.stop();
        updatePlaybackState(PlaybackStateCompat.STATE_STOPPED, 0, 0.0f);
        mediaSession.setActive(false);
        if (Pref.getPauseTime() == 0 ) Pref.setPauseTime();
    }


    private void updatePlaybackState(int playbackState, long position, float playbackSpeed) {
        PlaybackStateCompat playbackStateCompat = new PlaybackStateCompat.Builder()
                .setState(playbackState, position, playbackSpeed)
                .setActions(playbackStateCompatAction)
                .build();
        mediaSession.setPlaybackState(playbackStateCompat);
    }

    @SuppressWarnings("IfCanBeSwitch")
    private void playBeep(String beepType) {
        myLogI("playBeep - argument : " + beepType);
        try {
            if (beepType.equals("1beep")) {
                new ToneGenerator(AudioManager.STREAM_MUSIC, 100).startTone(ToneGenerator.TONE_CDMA_PIP, 150);
            } else if (beepType.equals("2beeps")) {
                new ToneGenerator(AudioManager.STREAM_MUSIC, 50).startTone(ToneGenerator.TONE_DTMF_0, 1000); // actually a long beep
            } else if (beepType.equals("3beeps")) {
                new ToneGenerator(AudioManager.STREAM_MUSIC, 100).startTone(ToneGenerator.TONE_CDMA_PIP, 500);
            } else {
                myLogE("playBeep - wrong argument : " + beepType);
            }
        } catch (Exception e) {
            myLogEE(e,"playBeep(" + beepType + ")");
        }
    }

    // 13-56
    // StatusBarNotification(pkg=com.driot.bookplayer user=UserHandle{0} id=1 tag=null key=0|com.driot.bookplayer|1|null|10333:
    // Notification(channel=audio_channel_of_bookplayer shortcut=null contentView=null vibrate=null sound=null defaults=0
    // flags=ONGOING_EVENT|ONLY_ALERT_ONCE|NO_CLEAR|FOREGROUND_SERVICE color=0x00000000 category=transport actions=3 vis=PUBLIC semFlags=0x0 semPriority=0 semMissedCount=0))
    private boolean isNotificationActive(Context context, int notificationId) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        StatusBarNotification[] notifications = ((NotificationManager) manager).getActiveNotifications();
        for (StatusBarNotification sbn : notifications) {
            myLog(sbn.toString());
            if (sbn.getId() == notificationId) {
                return true;
            }
        }
        return false;
    }

    private void killService() {
        myLogI("killService()");
        isRunning = false;
        stopPauseTimer();
        stopSleepTimer();
        if (engine != null) { try { engine.stop(); } catch (Exception ignored) {} }
        removeNotification();
        stopForeground(true);
        stopSelf();
    }
    private void loadFileKO() {
        myLog("loadFileKO");
        LocalBroadcastManager.getInstance(AudioService.this).sendBroadcast(new Intent(NOTIFICATION_FILENOTFOUND));
        ErrorLoadingFile=true;
        removeNotification();
        stopSelf();
    }

    private static int estimateDurationMs(String text, float speechRate) {
        if (text == null) return 0;
        int words = Math.max(1, text.trim().split("\\s+").length);
        double wpm = 180.0 * Math.max(0.1, speechRate); // 180 wpm baseline
        return (int) Math.round((words / wpm) * 60_000.0);
    }

    private void onEnginePrepared() {
        myLogD("engine prepared");

        try {
            int saved = getSavedResumePosition();
            if (engine != null && saved > 0) {
                engine.seekTo(saved);
                myLogD("Seeked to saved position: " + saved + " ms");
            }
        } catch (Exception e) {
            myLogEE(e, "seekTo(saved) in onEnginePrepared");
        }

        // Only send READY when engine.isReady()==true
        sendReadyToPlay("onEnginePrepared");

        if (directPlay) {
            startPlayWithEngine();
        } else {
            createNotificationChannel();
            createNotification();
        }
    }

    private void onEngineCompletion() {
        if (!ErrorLoadingFile) {
            updateZikFileStateInDB(true);
            alertTrackFinished();
            if (PlayList.getInstance().isLastTrack()) {
                if (Option.getBeepBookEnd()) playBeep("3beeps");
                alertPlaylistFinished();
                stopSleepTimer();
                Pref.setPauseTime();
            } else {
                nextTrack();
            }
        }
    }

    private void onEngineError(String msg, int what, int extra) {
        myLogE("Engine error: " + msg + " (" + what + "," + extra + ")");
        ErrorLoadingFile = true;
        if (msg.startsWith("TTS")) {
            alertError("TTS", msg);
        } else {
            alertError(null, null);
        }
    }

    private void onEngineFatal(String msg, int what, int extra) {
        myLogE("Engine FATAL: " + msg + " (" + what + "," + extra + ")");
        ErrorLoadingFile = true;
        alertError(null, null);
    }

    private int getSavedResumePosition() {
        ZikFile z = getCurrentZikFile();
        if (z == null) return 0;
        int pos = (int) z.getPosition();
        int dur = (int) z.getDuration();
        if (dur > 0) pos = Math.max(0, Math.min(pos, dur));
        return pos;
    }

    public boolean isTtsMode() {
        return engine instanceof TtsEngine;
    }

    public @Nullable String getTtsText() {
        if (engine instanceof TtsEngine) {
            return ((TtsEngine) engine).text;
        }
        return null;
    }

    public void setTtsVoiceByNameAndWarmUp(String voiceName, long timeoutMs, TtsEngine.WarmupCallback cb) {
        if (!(engine instanceof TtsEngine)) { cb.onResult(false, TtsHelper.ERROR); return; }
        myLogD("setTtsVoiceByNameAndWarmUp : " + voiceName);
        engine.pause();
        ((TtsEngine) engine).setTtsVoiceByNameAsync(voiceName, timeoutMs, (ready, reason) -> {
            myLogD("Warmup result ready=" + ready + " reason=" + reason);
            cb.onResult(ready, reason);
            // You decide when to resume; often resume only if ready==true
            if (ready) {
                myLog("ready, resuming");
                //engine.resume();
            }
        });
    }
    public void setTtsStartOffsetChars(int start) {
        if (!(engine instanceof TtsEngine)) return;
        myLogD("setTtsStartOffsetChars : " + start);
        ((TtsEngine) engine).setStartOffsetChars(start);
    }


}