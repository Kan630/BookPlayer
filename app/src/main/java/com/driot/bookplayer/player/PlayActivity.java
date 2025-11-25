package com.driot.bookplayer.player;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.method.ScrollingMovementMethod;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;

import com.driot.bookplayer.activities.TtsReaderActivity;
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
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.global.Pref;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.helpers.InsetHelper;
import com.driot.bookplayer.settings.ui.TtsSettingsFragment;
import com.driot.bookplayer.tts.TtsHelper;
import com.driot.bookplayer.utils.MetadataUi;
import com.driot.bookplayer.utils.Tonio;
import com.driot.bookplayer.utils.log.LoggingActivity;
import com.driot.bookplayer.views.ClickInterceptFrameLayout;
import com.driot.bookplayer.views.FrequencyVisualizerView;

import static com.driot.bookplayer.global.Var.SLEEP_PRESET_VALUES;
import static com.driot.bookplayer.utils.PermissionRequest.isRecordAudioPermissionGranted;

public class PlayActivity extends LoggingActivity {

    private PlaybackViewModel vm;

    private ImageButton bPlayPause, bRewind, bForward;
    private Button bSpeedUp, bSpeedDown, bSetSleep;
    private Slider sbSeek;
    private UiHelper.SliderBinding sliderBinding;
    private TextView tvCurTime, tvTotalTime, tvTitle, tvSubTitle, tvSpeed, tvListeningTime, tvTimeLeft;
    private View progressOverlay, messageOverlay;

    private ImageView ivCover;
    private String lastCoverUri = null;

    private FrequencyVisualizerView frequencyVisualizerView;

    private View ttsContainer;
    private TextView tvTtsText;
    private Spannable spannableText;
    private final BackgroundColorSpan ttsBgSpan = new BackgroundColorSpan(0x55FFFF00);
    private final ForegroundColorSpan ttsFgSpan = new ForegroundColorSpan(Color.BLACK);

    private String tvListeningTimeBaseText;

    private long podcastLastClickTime = 0;
    private static final long PODCAST_DOUBLE_CLICK_THRESHOLD = 300;

    private boolean suppressAutoScroll = false;
    private int touchSlop;
    private float downY;

    @Nullable private String lastTtsTextString = null;
    private int lastTtsTrackId = -1;
    @Nullable private String lastTtsPlayMode = null;
    @Nullable private String lastTtsPhase = null;


    // --- Broadcasts we still care about at the Activity level (UI only) ---
    private final BroadcastReceiver uiReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
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
                finishAndShowFatalError(em);
            } else if (MediaService.NOTIFICATION_PLAYLISTFINISHED.equals(action)) {
                myToast(getString(R.string.notification_playlist_finished));
                finish();
            } else if (MediaService.NOTIFICATION_PLAYBACK_MAXTIMEREACH.equals(action)) {
                myToast(getString(R.string.notification_auto_sleep));
                finish();
            }
        }
    };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_play);
        InsetHelper.apply(this);

        if (Option.getScreenOrientationLock()) {
            setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LOCKED);
        }

        if (PlayList.getInstance() == null) { finish(); myLogEE(null, "PlayList.getInstance() == null"); return; }
        Folder folder = PlayList.getInstance().getFolder();
        if (folder == null )  { finish(); myLogEE(null, "PlayList.getInstance().getFolder() == null"); return; }

        vm = new ViewModelProvider(this).get(PlaybackViewModel.class);

        // TTS voices (early)
        if (folder.playType != null && folder.playType.equals(Var.PLAY_TYPE_TEXT)) {
            initTtsVoiceSpinner(folder.getId());
        }
        touchSlop = android.view.ViewConfiguration.get(this).getScaledTouchSlop();

        progressOverlay = findViewById(R.id.progress_overlay);
        messageOverlay  = findViewById(R.id.message_overlay);

        bPlayPause = findViewById(R.id.ibPlayPause);
        bRewind    = findViewById(R.id.ibRewind);
        bForward   = findViewById(R.id.ibForward);
        bSpeedUp   = findViewById(R.id.bSpeedUp);
        bSpeedDown = findViewById(R.id.bSpeedDown);
        bSetSleep  = findViewById(R.id.bSetSleep);

        tvCurTime = findViewById(R.id.textViewSeekBar);
        tvTotalTime = findViewById(R.id.textViewTempsTotal);
        tvTitle     = findViewById(R.id.textviewTitle);
        tvSubTitle  = findViewById(R.id.textViewSubTitle);
        tvSpeed     = findViewById(R.id.textViewSpeed);
        tvListeningTime = findViewById(R.id.tv_ListeningTime);
        tvTimeLeft      = findViewById(R.id.tv_TimeLeft);
        tvListeningTimeBaseText = getString(R.string.tv_ListeningTimeWithNoUserAction);

        sbSeek = findViewById(R.id.sbSeek);
        ivCover = findViewById(R.id.folderImage);
        ivCover.setImageURI(null);
        frequencyVisualizerView = findViewById(R.id.frequencyVisualizerView);

        sliderBinding = UiHelper.bindSeekBar(sbSeek, tvCurTime, vm);
// Re-enable TTS auto-follow when user finishes a seek
        sbSeek.addOnSliderTouchListener(new Slider.OnSliderTouchListener() {
            @Override
            public void onStartTrackingTouch(@NonNull Slider slider) {
                // (optional) while user drags, you can temporarily stop auto-scroll if you want
                // suppressAutoScroll = true;
            }
            @Override
            public void onStopTrackingTouch(@NonNull Slider slider) {
                // User picked a new position → let TTS word tracking resume
                suppressAutoScroll = false;
            }
        });

        ttsContainer   = findViewById(R.id.ttsContainer);
        tvTtsText      = findViewById(R.id.tvTtsText);

        final TextView progressTitle = progressOverlay.findViewById(R.id.tv_progress_overlay_title);
        final TextView progressMessage = progressOverlay.findViewById(R.id.tv_progress_overlay_message);
        progressTitle.setText(getString(R.string.Text_To_Speech));

        // Clicks
        bPlayPause.setOnClickListener(v -> {myLogI("--- user press PLAY/PAUSE ---"); vm.playPause(); suppressAutoScroll = false;});
        bForward  .setOnClickListener(v -> {myLogI("--- user press FORWARD ---"); vm.next(); });
        bRewind   .setOnClickListener(v -> {myLogI("--- user press REWIND ---"); vm.prev(); });
        bSpeedUp  .setOnClickListener(v -> {myLogI("--- user press SPEED+ ---"); setSpeedViaVm(+Var.PLAY_SPEED_STEP); });
        bSpeedDown.setOnClickListener(v -> {myLogI("--- user press SPEED- ---"); setSpeedViaVm(-Var.PLAY_SPEED_STEP); });
        bSetSleep .setOnClickListener(v -> {myLogI("--- user press SLEEP- ---"); showSleepDialog(); });

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
            @Override public void onSingleTap() {
                if (Option.getClickMainContainerPlayPause()) vm.playPause();
            }
            @Override public void onDoubleTap() {
                PlaybackUiState s = vm.getState().getValue();
                if (s != null && "tts".equals(s.playMode)) {
                    applyTtsToggleUi(s);
                }
            }
            @Override public void onLongPress() {
                ZikFile z = PlayList.getInstance() != null ? PlayList.getInstance().getZikFile() : null;
                if (z != null) MetadataUi.showMetadataDialog(PlayActivity.this, z);
            }
        });

        // Observe playback state (single source of truth)
        vm.getState().observe(this, s -> {
            if (s == null) {
                myLogD("observe : s == null");
                return;
            }
            //myLog("observe : " + s);

            Double speed = null;
            if (s.extras != null && s.extras.containsKey(Intents.EXTRA_SPEED)) {
                speed = s.extras.getDouble(Intents.EXTRA_SPEED);
            }
            if (speed != null) {
                tvSpeed.setText(Tonio.formatPercentStringForSpeed(speed * 100.0));
            }
            reDrawSleepTextViews(vm.sleepCustomMinutes);

            // Title/sub
            UiHelper.FillUiBasic(s,null, null, tvTitle, tvSubTitle, null, null, sbSeek);

            // Seek/progress: sliderBinding handles slider + current time label
            tvCurTime.setText(Tonio.formatTime((int) s.positionMs, true));
            tvTotalTime.setText(Tonio.formatTime((int) s.durationMs, true));

            String p = s.loadPhase;
            boolean isStarting = (Intents.PHASE_STARTING.equals(p));

            // TTS
            boolean isTts = "tts".equals(s.playMode);
            boolean trackChanged = isTts && (s.trackId != lastTtsTrackId);
            boolean becameReady  = isTts
                    && !Intents.PHASE_READY.equals(lastTtsPhase)
                    && Intents.PHASE_READY.equals(p);

            if (isTts && (trackChanged || becameReady)) {
                suppressAutoScroll = false;
                lastTtsTextString = null;
                vm.requestTtsTextOnce();
            }

            lastTtsTrackId  = s.trackId;
            lastTtsPlayMode = s.playMode;
            lastTtsPhase    = p;

            if (s.ready && !isStarting) {
                bPlayPause.setEnabled(true);
                bPlayPause.setImageResource(s.playing ? R.drawable.ic_media_pause_24 : R.drawable.ic_media_play_24);
            } else {
                bPlayPause.setEnabled(false);
                bPlayPause.setImageResource(R.drawable.ic_hourglass_24);
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

            if (p == null) return;
            //myLog("Phase observer : " + p);

            // Pull the latest playback state to know if we’re in TTS or audio mode
            final boolean tts = ("tts".equals(s.playMode));

            // Default: hide overlays for pure audio mode unless we’re in an error phase
            if (!tts) {
                // Show only ERROR message if present
                boolean showError = Intents.PHASE_ERROR.equals(p);
                progressOverlay.setVisibility(View.GONE);
                if (showError) {
                    progressTitle.setText("");
                    //progressMessage.setText(p.message != null ? p.message : getString(R.string.error_generic));
                    messageOverlay.setVisibility(View.VISIBLE);
                } else {
                    messageOverlay.setVisibility(View.GONE);
                }
                return;
            }

            // TTS mode: show spinner during busy phases, otherwise hide overlays
            //final boolean busy = Intents.PHASE_LOADING_TEXT.equals(p.phase)
            //        || Intents.PHASE_STARTING.equals(p.phase);
            //progressOverlay.setVisibility(busy ? View.VISIBLE : View.GONE);



            String label;
            switch (p) {
                case Intents.PHASE_LOADING_TEXT: label = getString(R.string.tts_phase_loading_text); break;
                case Intents.PHASE_STARTING:     label = getString(R.string.tts_phase_starting);     break;
                case Intents.PHASE_READY:        label = getString(R.string.tts_phase_ready);        break;
                case Intents.PHASE_SPEAKING:     label = getString(R.string.tts_phase_speaking);     break;
                case Intents.PHASE_ERROR:        label = getString(R.string.tts_phase_error);        break;
                default:                         label = "";                                         break;
            }
            // Prefer explicit message from service if present
            //if (p.message != null && !p.message.isEmpty()) label = p.message;
            //progressMessage.setText(label);

            // Error message overlay (non-blocking)
            if (Intents.PHASE_ERROR.equals(p)) {
                //progressMessage.setText(p.message != null ? p.message : getString(R.string.tts_phase_error));
                messageOverlay.setVisibility(View.VISIBLE);
            } else {
                messageOverlay.setVisibility(View.GONE);
            }

            // Optionally soften main controls during busy phases
            /*
            boolean controlsEnabled = !busy;
            bRewind.setEnabled(controlsEnabled);
            bForward.setEnabled(controlsEnabled);
            seekbar.setEnabled(controlsEnabled);
             */

        });

        vm.getTtsRange().observe(this, p -> {
            //myLog("observe Tts Range : [" + p.first + "/" + p.second + "]");
            if (p != null) scheduleTtsHighlight(p.first, p.second);
        });

        vm.getTtsText().observe(this, txt -> {
            //myLog("observe Tts Text : [" + txt + "]");
            if (txt == null) txt = "";
            if (!txt.equals(lastTtsTextString)) {
                lastTtsTextString = txt;
                SpannableStringBuilder sb = new SpannableStringBuilder(txt);
                tvTtsText.setText(sb, TextView.BufferType.SPANNABLE);
                spannableText = (Spannable) tvTtsText.getText();
                tvTtsText.setMovementMethod(ScrollingMovementMethod.getInstance());
                tvTtsText.setVerticalScrollBarEnabled(true);
            }
        });

        // Back press: if not playing, ask service to stop; then finish.
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                myLogI("--- user press BACK ---");
                PlaybackUiState s = vm.getState().getValue();
                if (s == null ) {
                    myLog("s == null");
                    vm.stop();
                } else {
                    if (!s.playing ) {
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

    @Override protected void onDestroy() {
        try { LocalBroadcastManager.getInstance(this).unregisterReceiver(uiReceiver); } catch (Throwable ignored) {}
        if (sbSeek != null) {
            UiHelper.unbindSeekBar(sbSeek);
            sliderBinding = null;
        }
        super.onDestroy();
    }

    // ---------- UI bits that used to call the service directly ----------

    private void setSpeedViaVm(double delta) {
        // Prefer UI value (quick change) fallback on UiState
        PlaybackUiState s = vm.getState().getValue();
        Double cur = null;
        try {
            cur = Double.parseDouble(tvSpeed.getText().toString().replace('\u00A0', ' ').replaceAll("[^0-9,.\\s]", "").replace(',', '.').trim())/100;
        } catch (Throwable t) {
            myLogEE(t, "could not read speed : [" + tvSpeed.getText() + "]");
        }
        if (cur==null && s != null && s.extras != null && s.extras.containsKey(Intents.EXTRA_SPEED)) {
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
        Button[] presets = {btn1,btn2,btn3,btn4,btn5,btn6};

        DialogInterface.OnClickListener setSleepAction = (d, w) -> {
            String txt = inputMinutes.getText().toString().trim();
            if (!txt.isEmpty()) {
                try { vm.updateSleepTimer(Integer.parseInt(txt)); }
                catch (NumberFormatException e) { myToastE(getString(R.string.SleepTimerWrongInt)); }
                catch (Throwable e) { myToastE(getString(R.string.SleepTimerGeneralError)); }
            }
        };

        builder.setPositiveButton(getString(R.string.Set), setSleepAction)
                .setNegativeButton(getString(R.string.Cancel), (d,w) -> d.cancel());

        AlertDialog dialog = builder.create();
        dialog.show();

        for (int i=0;i<SLEEP_PRESET_VALUES.length;i++) {
            final int m = SLEEP_PRESET_VALUES[i];
            presets[i].setText(m + " min");
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
            long timeLeftMs = (s != null ? s.sleepLeftMS : 0);
            long timePassedMs = (long) customSleepMinutes*60*1000 - timeLeftMs;

            //myLog("timeLeftMs : " + timeLeftMs + " - timePassedMs : " + timePassedMs);

            String timeLeftText = getString(R.string.tv_TimeLeft) + " : " + Tonio.formatTime(timeLeftMs, true);
            tvTimeLeft.setText(timeLeftMs>0 && timePassedMs>0 ? timeLeftText : "");

            String timePassedText = tvListeningTimeBaseText + " " + Tonio.formatTime(timePassedMs, true);
            tvListeningTime.setText(timeLeftMs>0 && timePassedMs>0 ? timePassedText : "");

        } catch (Throwable t) {
            myLogEE(t, "reDrawSleepTextViews(" + customSleepMinutes + ")");
        }
    }

    private void handlePodcastClick(@Nullable Podcast p) {
        long now = System.currentTimeMillis();
        if (now - podcastLastClickTime < PODCAST_DOUBLE_CLICK_THRESHOLD && p != null) {
            startActivity(new Intent(this, PodcastEpisodeActivity.class).putExtra("podcast", p));
        }
        podcastLastClickTime = now;
    }

    private void applyTtsToggleUi(@Nullable PlaybackUiState s) {
        if (s == null) return;
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
                    //myLogD("linking visualizer"); //TODO : is RUN every SECOND, check it out....
                    frequencyVisualizerView.setMode(Option.getVisualizerType());
                    frequencyVisualizerView.link_toto(sessionId);
                    frequencyVisualizerView.setVisibility(View.VISIBLE);
                } catch (Throwable ignored) {}
            } else {
                frequencyVisualizerView.setVisibility(View.GONE);
            }
            ivCover.setVisibility(View.VISIBLE);

        } else {
            // TTS MODE
            frequencyVisualizerView.setVisibility(View.GONE);
            ttsContainer.setVisibility(View.VISIBLE);
            ivCover.setVisibility(View.GONE);

            if (lastTtsTextString == null || lastTtsTextString.isEmpty()) {
                vm.requestTtsTextOnce();
            }

            // Tap-to-seek within text
            final android.view.GestureDetector tapDetector =
                    new android.view.GestureDetector(tvTtsText.getContext(),
                            new android.view.GestureDetector.SimpleOnGestureListener() {
                                @Override public boolean onDown(@NonNull MotionEvent e) {
                                    // must return true so we keep receiving events
                                    return true;
                                }
                                @Override public boolean onSingleTapUp(@NonNull MotionEvent e) {
                                    // Only on real tap, not on scroll/fling
                                    Layout layout = tvTtsText.getLayout();
                                    if (layout == null || spannableText == null) return false;

                                    int x = (int)e.getX() - tvTtsText.getTotalPaddingLeft() + tvTtsText.getScrollX();
                                    int y = (int)e.getY() - tvTtsText.getTotalPaddingTop() + tvTtsText.getScrollY();
                                    int line = layout.getLineForVertical(y);
                                    int off  = layout.getOffsetForHorizontal(line, x);
                                    off = Math.max(0, Math.min(off, tvTtsText.getText().length()));

                                    int[] word = TtsHelper.findWordBounds(spannableText, off);
                                    try {
                                        spannableText.removeSpan(ttsBgSpan);
                                        spannableText.removeSpan(ttsFgSpan);
                                        spannableText.setSpan(ttsBgSpan, word[0], word[1], Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                                        spannableText.setSpan(ttsFgSpan, word[0], word[1], Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                                    } catch (Throwable ignored) {}

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

    private int pendingStart = -1, pendingEnd = -1;
    private boolean highlightScheduled = false;
    private final android.os.Handler uiH = new android.os.Handler(android.os.Looper.getMainLooper());

    private void scheduleTtsHighlight(int s, int e) {
        pendingStart = s; pendingEnd = e;
        if (highlightScheduled) return;
        highlightScheduled = true;
        uiH.postDelayed(this::applyTtsHighlight, Option.getTtsHighlightDelayMs());
    }
    private void applyTtsHighlight() {
        highlightScheduled = false;
        if (spannableText == null || pendingStart < 0) return;
        int len = spannableText.length();
        int s = Math.max(0, Math.min(pendingStart, len));
        int e = Math.max(s + 1, Math.min(pendingEnd, len));
        try {
            spannableText.removeSpan(ttsBgSpan);
            spannableText.removeSpan(ttsFgSpan);
            spannableText.setSpan(ttsBgSpan, s, e, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            spannableText.setSpan(ttsFgSpan, s, e, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        } catch (Throwable ignored) {}

        if (suppressAutoScroll) return;
        tvTtsText.post(() -> {
            try {
                Layout layout = tvTtsText.getLayout();
                if (layout != null) {
                    int line = layout.getLineForOffset(s);
                    int y = layout.getLineTop(line);
                    int targetY = Math.max(0, y - tvTtsText.getHeight() / 3);
                    tvTtsText.scrollTo(0, targetY);
                }
            } catch (Throwable ignored) {}
        });
    }

    private void initTtsVoiceSpinner(int folderId) {
        String saved = Pref.getBookTtsVoiceName(this, folderId);
        if (saved == null) {
            saved = Option.getTtsVoice();
            myLog("initTtsVoiceSpinner - general option voice = [" + saved + "]");
        } else {
            myLog("initTtsVoiceSpinner - book saved voice = [" + saved + "]");
        }
        final String[] currentVoiceName = { saved };  // track last good value

        final boolean[] first = {true};
        final boolean[] touched = {false};
        final boolean[] suppressSelect = {false};

        Spinner spinnerTtsVoice= findViewById(R.id.spinnerTtsVoice);

        spinnerTtsVoice.setOnTouchListener((v,e) -> {
            if (e.getAction()==MotionEvent.ACTION_UP) { touched[0]=true; v.performClick(); }
            return false;
        });

        spinnerTtsVoice.setEnabled(true);
        // Re-enable spinner when phase is not busy
        /*
        vm.getPhase().observe(this, p -> {
            if (p == null) return;
            boolean busy = p.isBusyPhase(); // you already have this helper

        });
         */

        vm.setupTtsVoiceSpinner(
                this,
                spinnerTtsVoice,
                saved,
                voice -> {
                    myLogD("setupTtsVoiceSpinner callback : first=[" + first[0] + "] - touched=[" + touched[0] + "] - suppressSelect=[" + suppressSelect[0] + "]");
                    if (first[0]) { first[0]=false; return; }
                    if (!touched[0]) return;
                    if (suppressSelect[0]) return;

                    final String picked = (voice==null || voice.name==null || voice.name.isEmpty()) ? "system" : voice.name;
                    myLogI("--- user picks a VOICE in SPINNER ---     [" + picked + "]");

                    if (picked.equalsIgnoreCase(currentVoiceName[0])) {
                        myLog("same voice picked");
                        return;
                    }

                    Pref.setBookTtsVoiceName(this, folderId, picked);
                    spinnerTtsVoice.setEnabled(false);  // Disable immediately (guard against rapid taps)
                    myLogD("spinnerTtsVoice disabled");

                    boolean wasPlaying = false;
                    PlaybackUiState s = vm.getState().getValue();
                    if (s != null) wasPlaying = s.playing;

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
                                    Pref.setBookTtsVoiceName(this, folderId, prevGood);
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
                }
        );
    }
    /** Finds a voice by engine name and selects it without firing the spinner listener. */
    private void selectVoiceByNameWithoutCallback(Spinner spinner, String name, boolean[] suppressFlag) {
        myLog("selectVoiceByNameWithoutCallback");
        try {
            android.widget.SpinnerAdapter a = spinner.getAdapter();
            if (!(a instanceof com.driot.bookplayer.adapter.VoiceSpinnerAdapter)) return;
            com.driot.bookplayer.adapter.VoiceSpinnerAdapter va = (com.driot.bookplayer.adapter.VoiceSpinnerAdapter) a;

            int target = 0; // 0 = "system"
            for (int i = 0; i < va.getCount(); i++) {
                com.driot.bookplayer.objects.VoiceItem vi = va.getItem(i);
                String n = (vi == null || vi.name == null || vi.name.isEmpty()) ? "system" : vi.name;
                if (n.equalsIgnoreCase(name)) { target = i; break; }
            }
            suppressFlag[0] = true;
            myLog("selectVoiceByNameWithoutCallback : " + target);
            spinner.setSelection(target, false);
            spinner.post(() -> suppressFlag[0] = false);
        } catch (Throwable ignored) {}
    }


    private void finishAndShowFatalError(String errMessage) {
        ErrorUi.showPlayAudioErrorMessage(this, errMessage, null);
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

    @Override protected void onStart() {
        super.onStart();
        // Bind controller to this Activity and ensure the browser is up
        MediaControllerHolder.attachTo(this);
        MediaControllerHolder.ensureConnected(getApplicationContext());
    }

    @Override protected void onStop() {
        MediaControllerHolder.detachFrom(this);
        super.onStop();
    }

}
