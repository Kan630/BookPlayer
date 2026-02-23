package com.driot.bookplayer.player;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Layout;
import android.text.Spannable;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;

import com.driot.bookplayer.activities.TtsReaderActivity;
import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.db.PodcastDao;
import com.driot.bookplayer.tts.VoiceItem;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.slider.Slider;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.bumptech.glide.Glide;
import com.driot.bookplayer.R;
import com.driot.bookplayer.activities.PodcastEpisodeActivity;
import com.driot.bookplayer.activities.SettingsHostActivity;
import com.driot.bookplayer.db.Folder;
import com.driot.bookplayer.db.Podcast;
import com.driot.bookplayer.db.ZikFile;
import com.driot.bookplayer.global.Intents;
import com.driot.bookplayer.player.heatmaps.PlayHeatMapView;
import com.driot.bookplayer.player.heatmaps.PlaySession;
import com.driot.bookplayer.player.heatmaps.PlaySessionDao;
import com.driot.bookplayer.player.heatmaps.PlayTickBucket;
import com.driot.bookplayer.player.heatmaps.PlayTickBucketMerger;
import com.driot.bookplayer.player.heatmaps.PlayTickDao;
import com.driot.bookplayer.player.heatmaps.PlayTickHeatMapHelper;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.helpers.InsetHelper;
import com.driot.bookplayer.helpers.ViewHelper;
import com.driot.bookplayer.settings.ui.TtsSettingsFragment;
import com.driot.bookplayer.tts.TtsHelper;
import com.driot.bookplayer.tts.TtsHighlighter;
import com.driot.bookplayer.utils.MetadataUi;
import com.driot.bookplayer.utils.Tonio;
import com.driot.bookplayer.utils.log.BaseActivity;
import com.driot.bookplayer.views.ClickInterceptFrameLayout;
import com.driot.bookplayer.views.FrequencyVisualizerView;

import java.util.List;

import static com.driot.bookplayer.global.Var.SLEEP_PRESET_VALUES;
import static com.driot.bookplayer.utils.PermissionRequest.isRecordAudioPermissionGranted;

public class PlayActivity extends BaseActivity {

    private PlaybackViewModel vm;

    private MaterialButton bPlayPause, bRewind, bForward;
    private Button bSpeedUp, bSpeedDown, bSetSleep;
    private Slider sbSeek;
    private PlayHeatMapView heatMapSeek;
    private UiHelper.SliderBinding sliderBinding;
    private boolean useHeatMapSeek;
    private int lastHeatMapTrackId = -1;
    private long lastHeatMapDurationMs = 0;
    private long lastHeatMapLoadTime = 0;
    /**
     * Match MediaService.DELAY_CHECK_TIMER_SLEEP (1s) so colored bar updates with
     * new PlayTicks.
     */
    private static final long HEATMAP_REFRESH_INTERVAL_MS = 1000;
    /** Position when heatmap drag started (for "AAA → BBB" display). */
    private long heatMapSeekStartPositionMs = 0;
    /**
     * Reset last user action every second while user is dragging seekbar or
     * heatmap.
     */
    private static final long DRAG_RESET_INTERVAL_MS = 1000;
    private final Handler dragResetHandler = new Handler(Looper.getMainLooper());
    private final Runnable dragResetRunnable = new Runnable() {
        @Override
        public void run() {
            PlaybackCommands.resetLastUserAction(PlayActivity.this);
            dragResetHandler.postDelayed(this, DRAG_RESET_INTERVAL_MS);
        }
    };
    private TextView tvCurTime, tvTotalTime, tvTitle, tvSubTitle, tvSpeed, tvListeningTime, tvTimeLeft;
    private View progressOverlay, messageOverlay;

    private ImageView ivCover;
    private String lastCoverUri = null;

    private FrequencyVisualizerView frequencyVisualizerView;

    private View ttsContainer;
    private TextView tvTtsText;
    // State moved to TtsHighlighter

    private long podcastLastClickTime = 0;
    private static final long PODCAST_DOUBLE_CLICK_THRESHOLD = 300;

    private boolean suppressAutoScroll = false;
    private int touchSlop;
    private float downY;

    private boolean screensaverActive = false;
    /**
     * After onResume, don't launch screensaver for this long (avoids launching when
     * user just opened PlayActivity).
     */
    private long resumeScreensaverGraceUntilRealtime = 0L;

    // --- Broadcasts we still care about at the Activity level (UI only) ---
    private final BroadcastReceiver uiReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            final String action = i.getAction();
            if (MediaService.NOTIFICATION_ERROR.equals(action)) {
                // If it’s a TTS error, it’s recoverable → UI is already driven by phases
                String em = i.getStringExtra(MediaService.ERR_MSG);
                if (em != null && em.startsWith("TTS")) {
                    // Show non-blocking message overlay via vm.getPhase() observer
                    // Do NOT finish the activity.
                    return;
                }
                // Non-TTS: keep the old fatal path
                String path = i.getStringExtra(MediaService.TRACK_PATH);
                finishAndShowFatalError(em, path);
            } else if (MediaService.NOTIFICATION_PLAYLISTFINISHED.equals(action)) {
                myToast(getString(R.string.notification_playlist_finished));
                finish();
            } else if (MediaService.NOTIFICATION_PLAYBACK_MAXTIMEREACH.equals(action)) {
                myToast(getString(R.string.notification_auto_sleep));
                finish();
            }
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_play);
        InsetHelper.apply(this);

        if (Option.getScreenOrientationLock()) {
            setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LOCKED);
        }

        if (PlayList.getInstance() == null) {
            finish();
            myLogEE(null, "PlayList.getInstance() == null");
            return;
        }
        Folder folder = PlayList.getInstance().getFolder();
        if (folder == null) {
            finish();
            myLogEE(null, "PlayList.getInstance().getFolder() == null");
            return;
        }

        vm = new ViewModelProvider(this).get(PlaybackViewModel.class);

        // TTS voices (early)
        if (folder.playType != null && folder.playType.equals(Var.PLAY_TYPE_TEXT)) {
            initTtsVoiceSpinner(folder);
        }
        touchSlop = android.view.ViewConfiguration.get(this).getScaledTouchSlop();

        progressOverlay = findViewById(R.id.progress_overlay);
        messageOverlay = findViewById(R.id.message_overlay);

        bPlayPause = findViewById(R.id.ibPlayPause);
        bRewind = findViewById(R.id.mbRewind);
        bForward = findViewById(R.id.mbForward);
        bSpeedUp = findViewById(R.id.bSpeedUp);
        bSpeedDown = findViewById(R.id.bSpeedDown);
        bSetSleep = findViewById(R.id.bSetSleep);

        tvCurTime = findViewById(R.id.textViewSeekBar);
        tvTotalTime = findViewById(R.id.textViewTempsTotal);
        tvTitle = findViewById(R.id.textviewTitle);
        tvSubTitle = findViewById(R.id.textViewSubTitle);
        tvSpeed = findViewById(R.id.textViewSpeed);
        tvListeningTime = findViewById(R.id.tv_ListeningTime);
        tvTimeLeft = findViewById(R.id.tv_TimeLeft);

        sbSeek = findViewById(R.id.sbSeek);
        heatMapSeek = findViewById(R.id.heatMapSeek);
        ivCover = findViewById(R.id.folderImage);
        ivCover.setImageURI(null);
        frequencyVisualizerView = findViewById(R.id.frequencyVisualizerView);

        useHeatMapSeek = Option.getUseHeatmapSeekbarInPlayActivity();
        if (useHeatMapSeek && heatMapSeek != null) {
            sbSeek.setVisibility(View.GONE);
            heatMapSeek.setVisibility(View.VISIBLE);
            setupHeatMapSeek();
        } else {
            if (heatMapSeek != null)
                heatMapSeek.setVisibility(View.GONE);
            sliderBinding = UiHelper.bindSeekBar(sbSeek, tvCurTime, vm);
            sbSeek.addOnSliderTouchListener(new Slider.OnSliderTouchListener() {
                @Override
                public void onStartTrackingTouch(@NonNull Slider slider) {
                    startDragResetTimer();
                }

                @Override
                public void onStopTrackingTouch(@NonNull Slider slider) {
                    stopDragResetTimer();
                    suppressAutoScroll = false;
                    PlaybackCommands.resetLastUserAction(PlayActivity.this);
                }
            });
        }

        ttsContainer = findViewById(R.id.ttsContainer);
        tvTtsText = findViewById(R.id.tvTtsText);
        ttsHighlighter = new TtsHighlighter(this, tvTtsText);

        final TextView progressTitle = progressOverlay.findViewById(R.id.tv_progress_overlay_title);
        final TextView progressMessage = progressOverlay.findViewById(R.id.tv_progress_overlay_message);
        progressTitle.setText(getString(R.string.Text_To_Speech));

        String nbSec = String.valueOf(Option.get_ForwardSeconds());
        bRewind.setText("-" + nbSec + " " + getString(R.string.sec));
        bForward.setText("+" + nbSec + " " + getString(R.string.sec));

        // Clicks
        bPlayPause.setOnClickListener(v -> {
            myLogI("--- user press PLAY/PAUSE ---");
            vm.playPause();
            suppressAutoScroll = false;
        });
        bForward.setOnClickListener(v -> {
            myLogI("--- user press FORWARD ---");
            vm.next();
        });
        bRewind.setOnClickListener(v -> {
            myLogI("--- user press REWIND ---");
            vm.prev();
        });
        bSpeedUp.setOnClickListener(v -> {
            myLogI("--- user press SPEED+ ---");
            setSpeedViaVm(+Var.PLAY_SPEED_STEP);
        });
        bSpeedDown.setOnClickListener(v -> {
            myLogI("--- user press SPEED- ---");
            setSpeedViaVm(-Var.PLAY_SPEED_STEP);
        });
        bSetSleep.setOnClickListener(v -> {
            myLogI("--- user press SLEEP- ---");
            showSleepDialog();
        });

        ImageButton btnToggleTtsViewFullScreen = findViewById(R.id.btnToggleTtsView);
        btnToggleTtsViewFullScreen.setOnClickListener(v -> {
            TtsReaderActivity.start(PlayActivity.this);
        });

        ImageButton ib_settings = findViewById(R.id.ib_settings);
        ib_settings.setOnClickListener((v) -> {
            myLogI("--- User clicks SETTINGS ---");
            SettingsHostActivity.start(this, TtsSettingsFragment.class, true, R.string.tts_settings);
        });

        ClickInterceptFrameLayout container = findViewById(R.id.coverContainer);
        container.setCallbacks(new ClickInterceptFrameLayout.Callbacks() {
            @Override
            public void onSingleTap() {
                myLogD("ClickInterceptFrameLayout - single tap");
                if (Option.getClickMainContainerPlayPause())
                    vm.playPause();
            }

            @Override
            public void onDoubleTap() {
                myLogD("ClickInterceptFrameLayout - double tap");
                PlaybackUiState s = vm.getState().getValue();
                if (s == null) {
                    myLogD("no PlaybackUiState");
                    return;
                }
                if (Var.PLAY_MODE_TTS.equals(s.playMode)) {
                    applyTtsToggleUi(s);
                }
                if (Var.PLAY_MODE_BOOK.equals(s.playMode)) { // open podcast view.
                    AppDatabase.databaseReadExecutor.execute(() -> {
                        PodcastDao dao = AppDatabase.getDatabase(getApplicationContext()).podcastDao();
                        Podcast podcast = dao.getPodcastByFolderId(folder.getId());
                        if (podcast != null) {
                            runOnUiThread(() -> {
                                handlePodcastClick(podcast);
                            });
                        }
                    });
                }
            }

            @Override
            public void onLongPress() {
                myLogD("ClickInterceptFrameLayout - long press");
                ZikFile z = PlayList.getInstance() != null ? PlayList.getInstance().getZikFile() : null;
                if (z != null)
                    MetadataUi.showMetadataDialog(PlayActivity.this, z);
            }
        });

        // Observe playback state (single source of truth)
        vm.getState().observe(this, s -> {
            if (s == null) {
                myLogD("observe : s == null");
                return;
            }
            // myLog("observe : " + s);

            Double speed = null;
            if (s.extras != null && s.extras.containsKey(Intents.EXTRA_SPEED)) {
                speed = s.extras.getDouble(Intents.EXTRA_SPEED);
            }
            if (speed != null) {
                tvSpeed.setText(Tonio.formatPercentStringForSpeed(speed * 100.0));
            }
            reDrawSleepTextViews(vm.getSleepCustomMinutes(s.playMode));

            // Title/sub; when heatmap seek, pass null for slider so time is still updated
            UiHelper.FillUiBasic(s, null, null, tvTitle, tvSubTitle, tvCurTime, null, useHeatMapSeek ? null : sbSeek,
                    null, null);

            // Seek/progress
            tvCurTime.setText(Tonio.formatTime((int) s.positionMs, true));
            tvTotalTime.setText(Tonio.formatTime((int) s.durationMs, true));

            if (useHeatMapSeek && heatMapSeek != null && s.durationMs > 0) {
                float norm = (float) Math.min(s.positionMs, s.durationMs) / s.durationMs;
                heatMapSeek.setPlayingCursor(norm);
                heatMapSeek.setCursors(new float[0]);
                if (s.trackId > 0) {
                    boolean trackOrDurationChanged = (s.trackId != lastHeatMapTrackId
                            || s.durationMs != lastHeatMapDurationMs);
                    boolean refreshDue = (System.currentTimeMillis()
                            - lastHeatMapLoadTime >= HEATMAP_REFRESH_INTERVAL_MS);
                    if (trackOrDurationChanged || refreshDue) {
                        if (trackOrDurationChanged) {
                            lastHeatMapTrackId = s.trackId;
                            lastHeatMapDurationMs = s.durationMs;
                        }
                        lastHeatMapLoadTime = System.currentTimeMillis();
                        loadHeatMapIntensities(s.trackId, s.durationMs);
                    }
                }
            }

            boolean isTts = "tts".equals(s.playMode);
            boolean isStarting = Intents.PHASE_ENGINE_STARTING.equals(s.loadPhase);
            boolean trackChanged = isTts && (s.trackId != ttsHighlighter.getLastTtsTrackId());
            boolean becameReady = isTts
                    && !Intents.PHASE_READY.equals(ttsHighlighter.getLastTtsPhase())
                    && Intents.PHASE_READY.equals(s.loadPhase);

            if (isTts && (trackChanged || becameReady)) {
                suppressAutoScroll = false;
            }

            // Delegate logic to highlighter
            ttsHighlighter.onPlaybackStateChanged(s, vm);

            if (s.ready && !isStarting) {
                bPlayPause.setEnabled(true);
                bPlayPause.setIconResource(s.playing ? R.drawable.ic_media_pause_24 : R.drawable.ic_media_play_24);
            } else {
                bPlayPause.setEnabled(false);
                bPlayPause.setIconResource(R.drawable.ic_hourglass_24);
            }

            // cover
            if (s.cover != null && !s.cover.isEmpty()) {
                if (!s.cover.equals(lastCoverUri)) {
                    lastCoverUri = s.cover;
                    ivCover.setImageURI(null);
                    myLogD("gliding image : " + s.cover);
                    Glide.with(ivCover.getContext()).load(s.cover).into(ivCover);
                }
                ivCover.setVisibility(View.VISIBLE);
                frequencyVisualizerView.setAlpha(0.6f);
            } else {
                myLogD("hiding image");
                if (lastCoverUri != null) {
                    lastCoverUri = null;
                    ivCover.setImageDrawable(null); // free memory
                }
                ivCover.setVisibility(View.GONE);
                frequencyVisualizerView.setAlpha(1f);
            }

            // TTS vs Audio UI
            applyTtsToggleUi(s);

            // Check screensaver activation
            checkAndLaunchScreensaver(s);

            String p = s.loadPhase;
            if (p == null)
                return;
            // myLog("Phase observer : " + p);

            // Pull the latest playback state to know if we’re in TTS or audio mode
            final boolean tts = ("tts".equals(s.playMode));

            // Default: hide overlays for pure audio mode unless we’re in an error phase
            if (!tts) {
                // Show only ERROR message if present
                boolean showError = Intents.PHASE_ERROR.equals(p);
                progressOverlay.setVisibility(View.GONE);
                if (showError) {
                    progressTitle.setText("");
                    // progressMessage.setText(p.message != null ? p.message :
                    // getString(R.string.error_generic));
                    messageOverlay.setVisibility(View.VISIBLE);
                } else {
                    messageOverlay.setVisibility(View.GONE);
                }
                return;
            }

            // TTS mode: show spinner during busy phases, otherwise hide overlays
            // final boolean busy = Intents.PHASE_LOADING_TEXT.equals(p.phase)
            // || Intents.PHASE_STARTING.equals(p.phase);
            // progressOverlay.setVisibility(busy ? View.VISIBLE : View.GONE);

            String label;
            switch (p) {
                case Intents.PHASE_LOADING_TEXT:
                    label = getString(R.string.tts_phase_loading_text);
                    break;
                case Intents.PHASE_ENGINE_STARTING:
                    label = getString(R.string.tts_phase_starting);
                    break;
                case Intents.PHASE_READY:
                    label = getString(R.string.Ready);
                    break;
                case Intents.PHASE_SPEAKING:
                    label = getString(R.string.Speaking);
                    break;
                case Intents.PHASE_ERROR:
                    label = getString(R.string.tts_phase_error);
                    break;
                default:
                    label = "";
                    break;
            }
            // Prefer explicit message from service if present
            // if (p.message != null && !p.message.isEmpty()) label = p.message;
            // progressMessage.setText(label);

            // Error message overlay (non-blocking)
            if (Intents.PHASE_ERROR.equals(p)) {
                // progressMessage.setText(p.message != null ? p.message :
                // getString(R.string.tts_phase_error));
                // messageOverlay.setVisibility(View.VISIBLE);
                myLogW("TTS is in PHASE_ERROR – keeping spinner and controls usable");
                myToast(getString(R.string.tts_phase_error));
            } else {
                // messageOverlay.setVisibility(View.GONE);
            }

            // Optionally soften main controls during busy phases
            /*
             * boolean controlsEnabled = !busy;
             * bRewind.setEnabled(controlsEnabled);
             * bForward.setEnabled(controlsEnabled);
             * seekbar.setEnabled(controlsEnabled);
             */

        });

        vm.getTtsRange().observe(this, p -> {
            // myLog("observe Tts Range : [" + p.first + "/" + p.second + "]");
            if (p != null)
                ttsHighlighter.scheduleHighlight(p.first, p.second);
        });

        vm.getTtsText().observe(this, txt -> {
            // myLog("observe Tts Text : [" + txt + "]");
            ttsHighlighter.onTextReady(txt);
        });

        // Back press: if not playing, ask service to stop; then finish.
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                PlaybackUiState s = vm.getState().getValue();
                if (s == null) {
                    myLog("s == null");
                    vm.stop();
                } else {
                    if (!s.playing) {
                        myLog("!s.playing");
                        vm.stop();
                    }
                }
                finish();
            }
        });

        // Register UI-level broadcasts we still use
        LocalBroadcastManager lb = LocalBroadcastManager.getInstance(this);
        lb.registerReceiver(uiReceiver, new IntentFilter(MediaService.NOTIFICATION_ERROR));
        lb.registerReceiver(uiReceiver, new IntentFilter(MediaService.NOTIFICATION_PLAYLISTFINISHED));
        lb.registerReceiver(uiReceiver, new IntentFilter(MediaService.NOTIFICATION_PLAYBACK_MAXTIMEREACH));
    }

    @Override
    protected void onDestroy() {
        stopDragResetTimer();
        try {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(uiReceiver);
        } catch (Throwable ignored) {
        }
        if (sbSeek != null) {
            UiHelper.unbindSeekBar(sbSeek);
            sliderBinding = null;
        }
        if (ttsHighlighter != null)
            ttsHighlighter.onDestroy();
        super.onDestroy();
    }

    // ---------- Heatmap seek (when Option.getUseHeatmapSeekbarInPlayActivity())
    // ----------

    /**
     * Half-width (px) of the touch zone centered on the cursor; total zone = 2 *
     * this.
     */
    private static final int HEATMAP_TOUCH_ZONE_HALF_WIDTH_DP = 40;

    private void startDragResetTimer() {
        dragResetHandler.removeCallbacks(dragResetRunnable);
        PlaybackCommands.resetLastUserAction(this);
        dragResetHandler.postDelayed(dragResetRunnable, DRAG_RESET_INTERVAL_MS);
    }

    private void stopDragResetTimer() {
        dragResetHandler.removeCallbacks(dragResetRunnable);
    }

    private void setupHeatMapSeek() {
        if (heatMapSeek == null)
            return;
        heatMapSeek.setOnTouchListener((v, event) -> {
            PlaybackUiState s = vm.getState().getValue();
            if (s == null || s.durationMs <= 0)
                return false;
            int w = heatMapSeek.getWidth();
            if (w <= 0)
                return false;
            float x = event.getX();
            float ratio = Math.max(0f, Math.min(1f, x / w));
            long seekMs = (long) (ratio * s.durationMs);
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN: {
                    float cursorX = (s.positionMs / (float) s.durationMs) * w;
                    float halfZonePx = ViewHelper.dp(this, HEATMAP_TOUCH_ZONE_HALF_WIDTH_DP);
                    if (x < cursorX - halfZonePx || x > cursorX + halfZonePx) {
                        return false;
                    }
                    startDragResetTimer();
                    heatMapSeekStartPositionMs = s.positionMs;
                    heatMapSeek.setPlayingCursorDragging(true);
                    vm.setSeekPreview(seekMs);
                    tvCurTime.setText(
                            Tonio.formatHhMmSs(heatMapSeekStartPositionMs) + " → " + Tonio.formatHhMmSs(seekMs));
                    return true;
                }
                case MotionEvent.ACTION_MOVE:
                    heatMapSeek.setPlayingCursorDragging(true);
                    vm.setSeekPreview(seekMs);
                    tvCurTime.setText(
                            Tonio.formatHhMmSs(heatMapSeekStartPositionMs) + " → " + Tonio.formatHhMmSs(seekMs));
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    stopDragResetTimer();
                    heatMapSeek.setPlayingCursorDragging(false);
                    vm.setSeekPreview(null);
                    vm.seekTo(seekMs);
                    suppressAutoScroll = false;
                    PlaybackCommands.resetLastUserAction(this);
                    return true;
                default:
                    return false;
            }
        });
    }

    private void loadHeatMapIntensities(long zikFileId, long durationMs) {
        if (heatMapSeek == null || durationMs <= 0)
            return;
        int nbBuckets = Math.max(1, Math.min((int) durationMs / 1000, Var.HEATMAP_PROGRESSBAR_BUCKET_SIZE));
        long bucketSizeMs = Math.max(1L, durationMs / nbBuckets);
        Context appCtx = getApplicationContext();
        AppDatabase.databaseReadExecutor.execute(() -> {
            PlayTickDao tickDao = AppDatabase.getInstance(appCtx).playTickDao();
            com.driot.bookplayer.player.heatmaps.PlaySessionDao sessionDao = AppDatabase.getInstance(appCtx)
                    .playSessionDao();
            List<PlayTickBucket> tickBuckets = tickDao.getBucketCounts(zikFileId, bucketSizeMs);
            List<PlaySession> sessions = sessionDao.getAllForFile(zikFileId);
            List<PlayTickBucket> sessionBuckets = PlaySessionDao.getBucketCounts(sessions, bucketSizeMs);
            List<PlayTickBucket> buckets = PlayTickBucketMerger.merge(sessionBuckets, tickBuckets);
            final float[] intensities = PlayTickHeatMapHelper.computeIntensities(buckets, durationMs, nbBuckets);
            runOnUiThread(() -> {
                if (heatMapSeek != null && lastHeatMapTrackId == zikFileId && lastHeatMapDurationMs == durationMs) {
                    heatMapSeek.setIntensities(intensities);
                }
            });
        });
    }

    // ---------- UI bits that used to call the service directly ----------

    private void setSpeedViaVm(double delta) {
        // Prefer UI value (quick change) fallback on UiState
        PlaybackUiState s = vm.getState().getValue();
        Double cur = null;
        try {
            cur = Double.parseDouble(tvSpeed.getText().toString().replace('\u00A0', ' ').replaceAll("[^0-9,.\\s]", "")
                    .replace(',', '.').trim()) / 100;
        } catch (Throwable t) {
            myLogEE(t, "could not read speed : [" + tvSpeed.getText() + "]");
        }
        if (cur == null && s != null && s.extras != null && s.extras.containsKey(Intents.EXTRA_SPEED)) {
            cur = s.extras.getDouble(Intents.EXTRA_SPEED);
        }
        myLogD("current speed = " + cur);
        double next = (cur == null ? 1.0 : cur) + delta;
        next = Math.max(Var.PLAY_SPEED_MIN, Math.min(Var.PLAY_SPEED_MAX, next));
        vm.setSpeed(next);
        tvSpeed.setText(Tonio.formatPercentStringForSpeed(next * 100));
    }

    private void showSleepDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(getString(R.string.SleepTimer));

        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_set_sleep, null);
        builder.setView(dialogView);

        EditText inputMinutes = dialogView.findViewById(R.id.inputMinutes);
        Button btn1 = dialogView.findViewById(R.id.btn_preset_01);
        Button btn2 = dialogView.findViewById(R.id.btn_preset_02);
        Button btn3 = dialogView.findViewById(R.id.btn_preset_03);
        Button btn4 = dialogView.findViewById(R.id.btn_preset_04);
        Button btn5 = dialogView.findViewById(R.id.btn_preset_05);
        Button btn6 = dialogView.findViewById(R.id.btn_preset_06);
        Button[] presets = { btn1, btn2, btn3, btn4, btn5, btn6 };

        DialogInterface.OnClickListener setSleepAction = (d, w) -> {
            String txt = inputMinutes.getText().toString().trim();
            if (!txt.isEmpty()) {
                try {
                    vm.updateSleepTimer(Integer.parseInt(txt));
                } catch (NumberFormatException e) {
                    myToastE(getString(R.string.SleepTimerWrongInt));
                } catch (Throwable e) {
                    myToastE(getString(R.string.SleepTimerGeneralError));
                }
            }
        };

        builder.setPositiveButton(getString(R.string.Set), setSleepAction)
                .setNegativeButton(android.R.string.cancel, (d, w) -> d.cancel());

        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(d -> startDragResetTimer());
        dialog.setOnDismissListener(d -> stopDragResetTimer());
        dialog.show();

        for (int i = 0; i < SLEEP_PRESET_VALUES.length; i++) {
            final int m = SLEEP_PRESET_VALUES[i];
            String presetButtonText = m + " " + getString(R.string.min_);
            presets[i].setText(presetButtonText);
            presets[i].setOnClickListener(v -> {
                inputMinutes.setText(String.valueOf(m));
                setSleepAction.onClick(dialog, DialogInterface.BUTTON_POSITIVE);
                dialog.cancel();
            });
        }
        inputMinutes.post(inputMinutes::requestFocus);
    }

    private void reDrawSleepTextViews(int customSleepMinutes) {
        try {
            PlaybackUiState s = vm.getState().getValue();
            if (s == null) {
                tvTimeLeft.setText("");
                tvListeningTime.setText("");
                return;
            }

            long timeLeftMs = s.sleepLeftMS;
            long timePassedMs = (long) customSleepMinutes * 60 * 1000 - timeLeftMs;

            // Only display if timer is running
            boolean showTimer = (timeLeftMs > 0 && customSleepMinutes > 0);

            if (showTimer) {
                // "Time before sleep: 15:30"
                String timeLeftText = getString(R.string.tv_TimeLeft) + " : " + Tonio.formatTime(timeLeftMs, true);
                tvTimeLeft.setText(timeLeftText);

                // "No user action since 04:30"
                String timePassedText = getString(R.string.tv_ListeningTimeWithNoUserAction) + " "
                        + Tonio.formatTime(timePassedMs, true);
                tvListeningTime.setText(timePassedText);
            } else {
                tvTimeLeft.setText("");
                tvListeningTime.setText("");
            }

        } catch (Throwable t) {
            myLogEE(t, "reDrawSleepTextViews(" + customSleepMinutes + ")");
        }
    }

    private void handlePodcastClick(@NonNull Podcast p) {
        long now = System.currentTimeMillis();
        if (now - podcastLastClickTime > PODCAST_DOUBLE_CLICK_THRESHOLD) {
            myLogI("user clicks podcast");
            startActivity(new Intent(this, PodcastEpisodeActivity.class).putExtra("podcast", p));
        } else {
            myLogW("user clicks podcast - bypassing" + now + "-" + podcastLastClickTime);
        }
        podcastLastClickTime = now;
    }

    public void showTtsLoading(boolean show) {
        if (progressOverlay == null) {
            myLogE("showTtsLoading: progressOverlay is null!");
            return;
        }
        myLogD("showTtsLoading: " + show);
        if (show) {
            TextView tv = progressOverlay.findViewById(R.id.tv_progress_overlay_message);
            if (tv != null)
                tv.setText(R.string.loading_voice_3pt);
            progressOverlay.setVisibility(View.VISIBLE);
            progressOverlay.bringToFront(); // Ensure it's on top
        } else {
            progressOverlay.setVisibility(View.GONE);
        }
    }

    private void applyTtsToggleUi(@Nullable PlaybackUiState s) {
        if (s == null)
            return;
        final boolean tts = "tts".equals(s.playMode);

        if (!tts) {
            // AUDIO MODE
            ttsContainer.setVisibility(View.GONE);

            // Optional visualizer (requires session id → ask VM)
            Integer sessionId = null;
            if (s.extras != null && s.extras.containsKey(Intents.EXTRA_AUDIO_SESSION_ID)) {
                sessionId = s.extras.getInt(Intents.EXTRA_AUDIO_SESSION_ID);
            }
            if (Option.getVisualizerOn() && isRecordAudioPermissionGranted(this) && sessionId != null) {
                try {
                    // myLogD("linking visualizer"); //TODO : is RUN every SECOND, check it out....
                    frequencyVisualizerView.setMode(Option.getVisualizerType());
                    frequencyVisualizerView.link_toto(sessionId);
                    frequencyVisualizerView.setVisibility(View.VISIBLE);
                } catch (Throwable ignored) {
                }
            } else {
                frequencyVisualizerView.setVisibility(View.GONE);
            }
            ivCover.setVisibility(View.VISIBLE);

        } else {
            // TTS MODE
            frequencyVisualizerView.setVisibility(View.GONE);
            ttsContainer.setVisibility(View.VISIBLE);
            ivCover.setVisibility(View.GONE);

            if (ttsHighlighter.getLastTtsTextString() == null || ttsHighlighter.getLastTtsTextString().isEmpty()) {
                vm.requestTtsTextOnce();
            }

            // Tap-to-seek within text
            final android.view.GestureDetector tapDetector = new android.view.GestureDetector(tvTtsText.getContext(),
                    new android.view.GestureDetector.SimpleOnGestureListener() {
                        @Override
                        public boolean onDown(@NonNull MotionEvent e) {
                            // must return true so we keep receiving events
                            return true;
                        }

                        @Override
                        public boolean onSingleTapUp(@NonNull MotionEvent e) {
                            // Only on real tap, not on scroll/fling
                            // tap logic
                            Layout layout = tvTtsText.getLayout();
                            Spannable sp = ttsHighlighter.getSpannableText();
                            if (layout == null || sp == null)
                                return false;

                            int x = (int) e.getX() - tvTtsText.getTotalPaddingLeft() + tvTtsText.getScrollX();
                            int y = (int) e.getY() - tvTtsText.getTotalPaddingTop() + tvTtsText.getScrollY();
                            int line = layout.getLineForVertical(y);
                            int off = layout.getOffsetForHorizontal(line, x);
                            off = Math.max(0, Math.min(off, tvTtsText.getText().length()));

                            int[] word = TtsHelper.findWordBounds(sp, off);
                            ttsHighlighter.updateHighlightForManualSeek(word[0], word[1]);

                            vm.setTtsStartOffsetChars(word[0]);
                            return true; // we handled the tap
                        }
                    });
            // Scroll
            tvTtsText.setOnTouchListener((v, ev) -> {
                switch (ev.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downY = ev.getY();
                        v.getParent().requestDisallowInterceptTouchEvent(true);
                        tapDetector.onTouchEvent(ev);
                        return false; // let TextView handle scroll
                    case MotionEvent.ACTION_MOVE:
                        // If user dragged enough, disable auto-scroll
                        if (!suppressAutoScroll && Math.abs(ev.getY() - downY) > touchSlop) {
                            suppressAutoScroll = true;
                        }
                        tapDetector.onTouchEvent(ev);
                        return false;
                    case MotionEvent.ACTION_UP: {
                        boolean tapped = tapDetector.onTouchEvent(ev);
                        v.getParent().requestDisallowInterceptTouchEvent(false);
                        if (tapped) {
                            // Re-enable auto-scroll only when the user *taps* a word
                            suppressAutoScroll = false;
                            // Satisfy accessibility/lint:
                            v.performClick();
                        }
                        return tapped; // consume only real taps
                    }
                    case MotionEvent.ACTION_CANCEL:
                        v.getParent().requestDisallowInterceptTouchEvent(false);
                        return false;
                    default:
                        return false;
                }
            });
        }
    }

    // --- Refactored TTS Highlighter ---
    private TtsHighlighter ttsHighlighter;

    // Callbacks from TtsHighlighter
    public void onTtsHighlightApplied(TextView tv, int startPos) {
        if (suppressAutoScroll)
            return;
        tvTtsText.post(() -> {
            try {
                Layout layout = tvTtsText.getLayout();
                if (layout != null) {
                    int line = layout.getLineForOffset(startPos);
                    int y = layout.getLineTop(line);
                    int targetY = Math.max(0, y - tvTtsText.getHeight() / 3);
                    tvTtsText.scrollTo(0, targetY);
                }
            } catch (Throwable ignored) {
            }
        });
    }

    private void initTtsVoiceSpinner(Folder folder) {
        String saved = folder.ttsVoice;
        if (saved == null) {
            saved = Option.getTtsVoice();
            myLog("initTtsVoiceSpinner - general option voice = [" + saved + "]");
        } else {
            myLog("initTtsVoiceSpinner - book saved voice = [" + saved + "]");
        }
        final String[] currentVoiceName = { saved }; // track last good value

        final boolean[] first = { true };
        final boolean[] touched = { false };
        final boolean[] suppressSelect = { false };

        Spinner spinnerTtsVoice = findViewById(R.id.spinnerTtsVoice);

        spinnerTtsVoice.setOnTouchListener((v, e) -> {
            if (e.getAction() == MotionEvent.ACTION_UP) {
                touched[0] = true;
                v.performClick();
            }
            return false;
        });

        spinnerTtsVoice.setEnabled(true);
        // Re-enable spinner when phase is not busy
        /*
         * vm.getPhase().observe(this, p -> {
         * if (p == null) return;
         * boolean busy = p.isBusyPhase(); // you already have this helper
         * 
         * });
         */

        vm.setupTtsVoiceSpinner(
                this,
                spinnerTtsVoice,
                saved,
                voice -> {
                    myLogD("setupTtsVoiceSpinner callback : first=[" + first[0] + "] - touched=[" + touched[0]
                            + "] - suppressSelect=[" + suppressSelect[0] + "]");
                    if (first[0]) {
                        first[0] = false;
                        return;
                    }
                    if (!touched[0])
                        return;
                    if (suppressSelect[0])
                        return;

                    final String picked = (voice == null || voice.name == null || voice.name.isEmpty())
                            ? Option.DEFAULT_VOICE
                            : voice.name;
                    myLogI("--- user picks a VOICE in SPINNER ---     [" + picked + "]");

                    if (picked.equalsIgnoreCase(currentVoiceName[0])) {
                        myLog("same voice picked");
                        return;
                    }

                    folder.ttsVoice = picked;
                    AppDatabase.databaseWriteExecutor.execute(() -> {
                        AppDatabase.getInstance(PlayActivity.this).folderDao().update(folder);
                    });
                    spinnerTtsVoice.setEnabled(false); // Disable immediately (guard against rapid taps)
                    myLogD("spinnerTtsVoice disabled");

                    boolean wasPlaying = false;
                    PlaybackUiState s = vm.getState().getValue();
                    if (s != null)
                        wasPlaying = s.playing;

                    final boolean wasPlayingFinal = wasPlaying;
                    final String prevGood = currentVoiceName[0];
                    try {
                        if (vm != null) {
                            // ✅ Always warm up the voice, independent of current playMode/state
                            vm.warmUpTtsVoice(picked, (ready, reason) -> runOnUiThread(() -> {
                                spinnerTtsVoice.setEnabled(true);
                                myLogD("spinnerTtsVoice enabled");

                                if (ready) {
                                    currentVoiceName[0] = picked; // commit
                                    if (wasPlayingFinal) {
                                        myLogD("...play");
                                        vm.playPause(); // pause
                                        vm.playPause(); // play
                                    }
                                } else {
                                    myLogD("...rollback");
                                    // Roll back visually + persistently
                                    folder.ttsVoice = prevGood;
                                    AppDatabase.databaseWriteExecutor.execute(() -> {
                                        AppDatabase.getInstance(PlayActivity.this).folderDao().update(folder);
                                    });
                                    selectVoiceByNameWithoutCallback(spinnerTtsVoice, prevGood, suppressSelect);
                                    myToast(getString(mapWarmupReasonToMsg(reason)));
                                }
                            }));
                        } else {
                            // Very defensive: VM somehow null → just re-enable spinner
                            myLogW("setupTtsVoiceSpinner: vm is null, re-enabling spinner");
                            spinnerTtsVoice.setEnabled(true);
                        }
                    } catch (Throwable t) {
                        myLogEE(t, "setupTtsVoiceSpinner");
                        myLogD("spinnerTtsVoice enabled");
                        spinnerTtsVoice.setEnabled(true);
                    }
                });
    }

    /**
     * Finds a voice by engine name and selects it without firing the spinner
     * listener.
     */
    private void selectVoiceByNameWithoutCallback(Spinner spinner, String name, boolean[] suppressFlag) {
        myLog("selectVoiceByNameWithoutCallback");
        try {
            android.widget.SpinnerAdapter a = spinner.getAdapter();
            if (!(a instanceof com.driot.bookplayer.adapter.VoiceSpinnerAdapter))
                return;
            com.driot.bookplayer.adapter.VoiceSpinnerAdapter va = (com.driot.bookplayer.adapter.VoiceSpinnerAdapter) a;

            int target = 0; // 0 = "system"
            for (int i = 0; i < va.getCount(); i++) {
                VoiceItem vi = va.getItem(i);
                String n = (vi == null || vi.name == null || vi.name.isEmpty()) ? Option.DEFAULT_VOICE : vi.name;
                if (n.equalsIgnoreCase(name)) {
                    target = i;
                    break;
                }
            }
            suppressFlag[0] = true;
            myLog("selectVoiceByNameWithoutCallback : " + target);
            spinner.setSelection(target, false);
            va.setSelectedPosition(target);
            spinner.post(() -> suppressFlag[0] = false);
        } catch (Throwable ignored) {
        }
    }

    private void finishAndShowFatalError(String errMessage, String path) {
        ErrorUi.showPlayAudioErrorMessage(this, errMessage, path);
        finish();
    }

    /** Map TTS warm-up reason -> user-friendly message id. */
    private int mapWarmupReasonToMsg(int reason) {
        switch (reason) {
            case TtsHelper.TIMEOUT:
                return R.string.tts_error_warmup_timeout;
            case TtsHelper.SET_VOICE_FAILED:
                return R.string.tts_error_voice_set_failed;
            case TtsHelper.SYNTH_FAIL:
                return R.string.tts_error_synth_failed;
            default:
                return R.string.tts_phase_error; // generic fallback
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Grace period must be set before LiveData delivers to observer (which happens
        // in STARTED state).
        resumeScreensaverGraceUntilRealtime = android.os.SystemClock.elapsedRealtime() + 2000; // 2 s grace
        // Bind controller to this Activity and ensure the browser is up
        MediaControllerHolder.attachTo(this);
        MediaControllerHolder.ensureConnected(getApplicationContext());
    }

    @Override
    protected void onStop() {
        MediaControllerHolder.detachFrom(this);
        super.onStop();
    }

    // ---------- Screensaver logic ----------

    private void checkAndLaunchScreensaver(@Nullable PlaybackUiState s) {
        if (!Option.getScreensaverEnabled() || s == null || screensaverActive) {
            return;
        }
        if (isFinishing()) {
            return; // Don't launch when user is leaving (e.g. BACK press).
        }
        // Don't launch immediately after user opened PlayActivity (e.g. from mini
        // player click).
        if (android.os.SystemClock.elapsedRealtime() < resumeScreensaverGraceUntilRealtime) {
            return;
        }

        // Don't activate if in TTS mode (highlighted text visible)
        if (Var.PLAY_MODE_TTS.equals(s.playMode)) {
            return;
        }

        // Don't activate if not playing
        if (!s.playing) {
            return;
        }

        // Calculate idle time from sleep timer mechanism
        int sleepMinutes = vm.getSleepCustomMinutes(s.playMode);
        if (sleepMinutes <= 0 || s.sleepLeftMS <= 0) {
            return; // No sleep timer active, can't determine idle time
        }

        long totalSleepMs = (long) sleepMinutes * 60 * 1000;
        long idleTimeMs = totalSleepMs - s.sleepLeftMS;
        long screensaverThresholdMs = (long) Option.getScreensaverDelaySeconds() * 1000;

        if (idleTimeMs >= screensaverThresholdMs) {
            launchScreensaver(s);
        }
    }

    private void launchScreensaver(@NonNull PlaybackUiState s) {
        Integer sessionId = null;
        if (s.extras != null && s.extras.containsKey(Intents.EXTRA_AUDIO_SESSION_ID)) {
            sessionId = s.extras.getInt(Intents.EXTRA_AUDIO_SESSION_ID);
        }

        if (sessionId != null && sessionId > 0) {
            screensaverActive = true;
            Intent intent = new Intent(this, ScreensaverActivity.class);
            intent.putExtra(Intents.EXTRA_AUDIO_SESSION_ID, sessionId);
            intent.putExtra("previous_orientation", getResources().getConfiguration().orientation);
            startActivity(intent);
            myLogI("Launching screensaver");
        }
    }

    @Override
    protected void onPause() {
        if (frequencyVisualizerView != null) {
            frequencyVisualizerView.release();
        }
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        screensaverActive = false; // Reset when returning to activity
        PlaybackCommands.resetLastUserAction(this);
        if (vm != null) {
            PlaybackUiState s = vm.getState().getValue();
            if (s != null) {
                applyTtsToggleUi(s);
            }
        }
    }

}
