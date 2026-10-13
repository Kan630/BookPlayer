package com.driot.bookplayer.video;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.PorterDuff;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.lifecycle.ViewModelProvider;

import com.bumptech.glide.Glide;
import com.driot.bookplayer.R;
import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.db.RadioStation;
import com.driot.bookplayer.radio.RadioFaviconHelper;
import com.driot.bookplayer.global.Intents;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.global.Pref;
import com.driot.bookplayer.helpers.NetworkHelper;
import com.driot.bookplayer.player.PlaybackCommands;
import com.driot.bookplayer.player.PlaybackUiState;
import com.driot.bookplayer.player.PlaybackViewModel;
import com.driot.bookplayer.player.MediaControllerHolder;
import com.driot.bookplayer.player.StreamVideo;
import com.driot.bookplayer.player.UiHelper;
import com.driot.bookplayer.utils.Tonio;
import com.driot.bookplayer.utils.log.BaseActivity;
import com.driot.bookplayer.views.AspectRatioFrameLayout;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * Watches the picture of whatever is playing, when it has one: a radio station streaming video (a
 * TV-style HLS stream) or a video podcast episode (an mp4 in the feed).
 *
 * The sound keeps coming from MediaService as always; this screen only borrows the video: it hands
 * its surface to the engine through {@link StreamVideo} while it is on top, and gives it back when
 * it leaves, which leaves playback going as plain audio on the same connection. Nothing of the
 * picture is decoded while no such screen is open - which is what keeps a 1080p video podcast from
 * being fetched in full for a listener who only wants the sound.
 *
 * Started with a station id for radio (name and logo from the database, and the station is
 * remembered as having video), or without one for a podcast episode, whose title and cover come
 * from the playback state like the mini player's.
 *
 * Its own Activity rather than a nav destination, because it owns the system bars (immersive
 * fullscreen) and the orientation, and keeps the surface across rotation (configChanges).
 */
@AndroidEntryPoint
public class StreamVideoActivity extends BaseActivity {

    /** Radio station row id - the trackId the playback state carries for radio. */
    public static final String EXTRA_STATION_ID = "EXTRA_STATION_ID";

    private static final long CHROME_HIDE_DELAY_MS = 3_000;

    /** Watch a radio station (its row id), or whatever else is playing when there is none (-1). */
    public static void start(@NonNull Context context, long stationId) {
        context.startActivity(new Intent(context, StreamVideoActivity.class)
                .putExtra(EXTRA_STATION_ID, stationId));
    }

    /** Watch the podcast episode being streamed: title and cover come from the playback state. */
    public static void startForPodcast(@NonNull Context context) {
        start(context, -1);
    }

    private PlaybackViewModel vm;

    private SurfaceView videoSurface;
    private AspectRatioFrameLayout videoFrame;
    private View topChrome, bottomChrome, llRecord;
    private ImageButton ibPlayPause, ibFullscreen;
    private ImageView ivStationLogo, ivRecordDot;
    private TextView tvStationName, tvRecordInfo, tvMessage, tvLive, tvTime;
    private View llProgress;
    private com.google.android.material.slider.Slider sbSeek;
    private ProgressBar pbBuffering;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable hideChromeRunnable = () -> setChromeVisible(false);

    private boolean fullscreen = false;
    private boolean chromeVisible = true;
    /** True once a frame has been decoded: before that the screen shows "waiting for the picture". */
    private boolean hasPicture = false;
    /** Set when the data guard was answered (or was not needed) and the surface may be attached. */
    private boolean videoAllowed = false;

    private long stationId;
    /** Cover already loaded for a podcast episode, so Glide is not asked again every second. */
    @Nullable
    private String lastCover;
    @Nullable
    private String stationUuid;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stream_video);

        stationId = getIntent() != null ? getIntent().getLongExtra(EXTRA_STATION_ID, -1) : -1;
        myLogI("StreamVideoActivity - station id [" + stationId + "] (-1 = a podcast episode)");

        videoSurface = findViewById(R.id.videoSurface);
        videoFrame = findViewById(R.id.videoFrame);
        topChrome = findViewById(R.id.topChrome);
        bottomChrome = findViewById(R.id.bottomChrome);
        llRecord = findViewById(R.id.llRecord);
        ibPlayPause = findViewById(R.id.ibPlayPause);
        ibFullscreen = findViewById(R.id.ibFullscreen);
        ivStationLogo = findViewById(R.id.ivStationLogo);
        ivRecordDot = findViewById(R.id.ivRecordDot);
        tvStationName = findViewById(R.id.tvStationName);
        tvRecordInfo = findViewById(R.id.tvRecordInfo);
        tvMessage = findViewById(R.id.tvMessage);
        tvLive = findViewById(R.id.tvLive);
        pbBuffering = findViewById(R.id.pbBuffering);
        llProgress = findViewById(R.id.llProgress);
        sbSeek = findViewById(R.id.sbSeek);
        tvTime = findViewById(R.id.tvTime);

        tvRecordInfo.setVisibility(View.GONE);

        // The video is full-bleed; only the chrome steps aside for the system bars.
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        applyChromeInsets();

        // A picture no one can see is pointless: the screen stays on while this screen is up.
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        findViewById(R.id.ibBack).setOnClickListener(v -> {
            myLogI("--- user leaves the radio video screen ---");
            finish();
        });

        findViewById(R.id.videoRoot).setOnClickListener(v -> setChromeVisible(!chromeVisible));

        ibPlayPause.setOnClickListener(v -> {
            myLogI("---- user press PlayPause (radio video) ----");
            PlaybackCommands.resetLastUserAction(this);
            vm.playPause();
            scheduleChromeHide();
        });

        llRecord.setOnClickListener(v -> {
            myLogI("---- user press RECORD (radio video) ----");
            PlaybackCommands.resetLastUserAction(this);
            PlaybackCommands.toggleRadioRecording(this);
            scheduleChromeHide();
        });

        ibFullscreen.setOnClickListener(v -> setFullscreen(!fullscreen));

        vm = new ViewModelProvider(this).get(PlaybackViewModel.class);
        // Same binding as the mini players and the full player: label, live preview, seek on
        // release, and the "do not fight the user while scrubbing" flag.
        UiHelper.bindSeekBar(sbSeek, tvTime, vm);
        sbSeek.addOnSliderTouchListener(new com.google.android.material.slider.Slider.OnSliderTouchListener() {
            @Override
            public void onStartTrackingTouch(@NonNull com.google.android.material.slider.Slider s) {
                mainHandler.removeCallbacks(hideChromeRunnable); // not while a thumb is held
            }

            @Override
            public void onStopTrackingTouch(@NonNull com.google.android.material.slider.Slider s) {
                scheduleChromeHide();
            }
        });
        vm.getState().observe(this, this::bindState);

        loadStation();
        scheduleChromeHide();
    }

    /** Station name and logo for the top bar; the uuid is also what we remember video on. */
    private void loadStation() {
        if (stationId <= 0) {
            return;
        }
        AppDatabase.databaseReadExecutor.execute(() -> {
            RadioStation station = null;
            try {
                station = AppDatabase.getDatabase(getApplicationContext()).radioStationDao().findById(stationId);
            } catch (Exception e) {
                myLogEE(e, "loadStation failed for id " + stationId);
            }
            final RadioStation rs = station;
            mainHandler.post(() -> {
                if (rs == null || isFinishing()) {
                    return;
                }
                stationUuid = rs.stationuuid;
                tvStationName.setText(rs.name);
                String cover = RadioFaviconHelper.effectiveCover(getApplicationContext(), rs);
                Glide.with(this)
                        .load(cover)
                        .placeholder(R.drawable.ic_radio_24px)
                        .error(R.drawable.ic_radio_24px)
                        .into(ivStationLogo);
            });
        });
    }

    // -------------------------------------------------------------------------
    // Surface: held only while this screen is in front
    // -------------------------------------------------------------------------

    @Override
    protected void onStart() {
        super.onStart();
        MediaControllerHolder.attachTo(this);
        MediaControllerHolder.ensureConnected(getApplicationContext());
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (videoAllowed) {
            attachSurface();
        } else {
            askAboutMobileDataThenAttach();
        }
    }

    @Override
    protected void onPause() {
        // Back to sound only: no picture is decoded while this screen is not in front.
        StreamVideo.setSizeListener(null);
        StreamVideo.detach();
        hasPicture = false;
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        UiHelper.unbindSeekBar(sbSeek);
        mainHandler.removeCallbacks(hideChromeRunnable);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        super.onDestroy();
    }

    /** Watching costs far more data than listening, so ask once on a metered connection. */
    private void askAboutMobileDataThenAttach() {
        if (NetworkHelper.isUnmeteredConnected(this) || Pref.getRadioVideoOnMobileDataAllowed()) {
            videoAllowed = true;
            attachSurface();
            return;
        }
        myLogI("radio video on a metered connection: asking the user");
        new android.app.AlertDialog.Builder(this)
                .setTitle(R.string.radio_video_mobile_data_title)
                .setMessage(R.string.radio_video_mobile_data_message)
                .setCancelable(false)
                .setPositiveButton(R.string.Yes, (d, w) -> {
                    myLogI("user accepts radio video on mobile data (remembered)");
                    Pref.setRadioVideoOnMobileDataAllowed(true);
                    videoAllowed = true;
                    attachSurface();
                })
                .setNegativeButton(R.string.No, (d, w) -> {
                    myLogI("user declines radio video on mobile data");
                    finish();
                })
                .show();
    }

    private void attachSurface() {
        StreamVideo.setSizeListener(this::onVideoSize);
        if (!StreamVideo.attach(videoSurface)) {
            // Nothing is playing: the engine is gone (station stopped, app restarted...).
            tvMessage.setText(R.string.radio_video_not_playing);
            tvMessage.setVisibility(View.VISIBLE);
            pbBuffering.setVisibility(View.GONE);
            return;
        }
        if (!hasPicture) {
            tvMessage.setText(StreamVideo.hasVideo()
                    ? R.string.radio_video_waiting
                    : R.string.radio_video_sound_only);
            tvMessage.setVisibility(View.VISIBLE);
        }
    }

    /** First decoded frame (and every size change): shape the canvas and drop the placeholder. */
    private void onVideoSize(int width, int height) {
        if (width <= 0 || height <= 0) {
            return;
        }
        videoFrame.setAspectRatio((float) width / (float) height);
        hasPicture = true;
        tvMessage.setVisibility(View.GONE);
        pbBuffering.setVisibility(View.GONE);
        // Now we know for sure, so the entry points can offer it before playing next time.
        Pref.addRadioStationWithVideo(stationUuid);
    }

    // -------------------------------------------------------------------------
    // Chrome, fullscreen
    // -------------------------------------------------------------------------

    private void setChromeVisible(boolean visible) {
        chromeVisible = visible;
        int vis = visible ? View.VISIBLE : View.GONE;
        topChrome.setVisibility(vis);
        bottomChrome.setVisibility(vis);
        if (visible) {
            scheduleChromeHide();
        } else {
            mainHandler.removeCallbacks(hideChromeRunnable);
        }
    }

    /** The chrome gets out of the way on its own, but never while there is nothing to look at. */
    private void scheduleChromeHide() {
        mainHandler.removeCallbacks(hideChromeRunnable);
        if (hasPicture) {
            mainHandler.postDelayed(hideChromeRunnable, CHROME_HIDE_DELAY_MS);
        }
    }

    private void setFullscreen(boolean on) {
        fullscreen = on;
        myLogI("radio video fullscreen => " + on);
        ibFullscreen.setImageResource(on
                ? R.drawable.ic_fullscreen_exit_24px
                : R.drawable.ic_fullscreen_24px);

        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        controller.setSystemBarsBehavior(
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        if (on) {
            controller.hide(WindowInsetsCompat.Type.systemBars());
            setChromeVisible(false);
            // A wide picture deserves the long side of the screen - unless the user asked the app
            // to stay in one orientation (BaseActivity honours that setting too).
            if (!Option.getAppOrientationLock() && !Option.getScreenOrientationLock()) {
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
            }
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars());
            setChromeVisible(true);
            if (!Option.getAppOrientationLock() && !Option.getScreenOrientationLock()) {
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
            }
        }
    }

    /** Only the chrome avoids the status/navigation bars (and a cutout): the video keeps the whole
     * screen. The insets are added to the padding the layout already asks for, never replace it,
     * since this runs again on every rotation and on entering/leaving fullscreen. */
    private void applyChromeInsets() {
        View root = findViewById(R.id.videoRoot);
        final int topLeft = topChrome.getPaddingLeft(), topTop = topChrome.getPaddingTop();
        final int topRight = topChrome.getPaddingRight();
        final int botLeft = bottomChrome.getPaddingLeft(), botRight = bottomChrome.getPaddingRight();
        final int botBottom = bottomChrome.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, windowInsets) -> {
            Insets bars = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            topChrome.setPadding(topLeft + bars.left, topTop + bars.top, topRight + bars.right,
                    topChrome.getPaddingBottom());
            bottomChrome.setPadding(botLeft + bars.left, bottomChrome.getPaddingTop(),
                    botRight + bars.right, botBottom + bars.bottom);
            return windowInsets;
        });
    }

    // -------------------------------------------------------------------------
    // Playback state
    // -------------------------------------------------------------------------

    private void bindState(@Nullable PlaybackUiState s) {
        if (s == null) {
            return;
        }
        ibPlayPause.setImageResource(s.playing
                ? R.drawable.ic_media_pause_24
                : R.drawable.ic_media_play_24);

        boolean buffering = s.playing && !s.ready;
        pbBuffering.setVisibility(buffering && !hasPicture ? View.VISIBLE : View.GONE);
        // A podcast episode has a position and an end, so it gets a slider; a live radio stream
        // has neither. UiHelper does the filling, including leaving it alone while scrubbing.
        boolean seekable = com.driot.bookplayer.global.Var.PLAY_MODE_PODCAST.equals(s.playMode);
        llProgress.setVisibility(seekable ? View.VISIBLE : View.GONE);
        if (seekable) {
            UiHelper.FillUiBasic(s, null, null, null, null, tvTime, null, sbSeek, null, null);
        }

        // "Live" is a radio thing; a podcast episode is a file being streamed.
        tvLive.setVisibility(com.driot.bookplayer.global.Var.PLAY_MODE_RADIO.equals(s.playMode)
                ? View.VISIBLE : View.GONE);
        tvLive.setAlpha(s.playing ? 1f : 0.4f);

        Bundle extras = s.extras;
        boolean recordingEnabled = Option.getRadioRecordingEnabled()
                && com.driot.bookplayer.global.Var.PLAY_MODE_RADIO.equals(s.playMode);
        boolean recordingActive = extras != null
                && extras.getBoolean(Intents.EXTRA_RADIO_RECORDING_ACTIVE, false);
        boolean recordingAvailable = extras != null
                && extras.getBoolean(Intents.EXTRA_RADIO_RECORDING_AVAILABLE, false);

        llRecord.setVisibility(recordingEnabled ? View.VISIBLE : View.GONE);
        llRecord.setClickable(recordingEnabled);
        if (recordingEnabled) {
            ivRecordDot.setAlpha((recordingAvailable || recordingActive) ? 1f : 0.4f);
            if (recordingActive) {
                ivRecordDot.setColorFilter(ContextCompat.getColor(this, R.color.red_500),
                        PorterDuff.Mode.SRC_IN);
                long elapsedMs = extras.getLong(Intents.EXTRA_RADIO_RECORDING_ELAPSED_MS, 0);
                long bytes = extras.getLong(Intents.EXTRA_RADIO_RECORDING_BYTES, 0);
                tvRecordInfo.setText(getString(R.string.radio_recording_info,
                        Tonio.formatTime(elapsedMs), Tonio.getReadableSize(bytes)));
                tvRecordInfo.setVisibility(View.VISIBLE);
            } else {
                ivRecordDot.clearColorFilter();
                tvRecordInfo.setVisibility(View.GONE);
            }
        }

        // Only radio and podcast streams can carry a picture; anything else taking over (a book,
        // TTS) means there is nothing left to watch. A null mode is "not known yet", not "nothing
        // playing", so it must not close the screen.
        if (s.playMode != null
                && !com.driot.bookplayer.global.Var.PLAY_MODE_RADIO.equals(s.playMode)
                && !com.driot.bookplayer.global.Var.PLAY_MODE_PODCAST.equals(s.playMode)) {
            myLogI("playback left radio/podcast [" + s.playMode + "] - closing the video screen");
            finish();
            return;
        }

        // A podcast episode has no station row: its title and cover are the ones the mini player
        // shows, straight from the playback state.
        if (stationId <= 0) {
            if (s.title != null && !s.title.isEmpty()) {
                tvStationName.setText(s.title);
            }
            if (s.cover != null && !s.cover.equals(lastCover)) {
                lastCover = s.cover;
                Glide.with(this)
                        .load(s.cover)
                        .placeholder(R.drawable.ic_podcast_24)
                        .error(R.drawable.ic_podcast_24)
                        .into(ivStationLogo);
            }
        }
    }
}
