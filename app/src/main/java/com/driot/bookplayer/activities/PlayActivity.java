package com.driot.bookplayer.activities;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
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
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.bumptech.glide.Glide;
import com.driot.bookplayer.R;
import com.driot.bookplayer.db.Folder;
import com.driot.bookplayer.db.Podcast;
import com.driot.bookplayer.db.ZikFile;
import com.driot.bookplayer.global.Intents;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.global.Pref;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.helpers.FileHelper;
import com.driot.bookplayer.helpers.InsetHelper;
import com.driot.bookplayer.helpers.StorageHelper;
import com.driot.bookplayer.helpers.TitleHelper;
import com.driot.bookplayer.player.ErrorUi;
import com.driot.bookplayer.settings.ui.PodcastSettingsFragment;
import com.driot.bookplayer.settings.ui.TtsSettingsFragment;
import com.driot.bookplayer.tts.TtsHelper;
import com.driot.bookplayer.player.AudioService;
import com.driot.bookplayer.player.PlayList;
import com.driot.bookplayer.player.PlaybackUiState;
import com.driot.bookplayer.player.PlaybackViewModel;
import com.driot.bookplayer.utils.MetadataUi;
import com.driot.bookplayer.utils.MsgBox;
import com.driot.bookplayer.utils.Tonio;
import com.driot.bookplayer.utils.log.LoggingActivity;
import com.driot.bookplayer.views.ClickInterceptFrameLayout;
import com.driot.bookplayer.views.FrequencyVisualizerView;

import static com.driot.bookplayer.global.Var.SLEEP_PRESET_VALUES;
import static com.driot.bookplayer.player.AudioService.TIMER_VALUE;
import static com.driot.bookplayer.utils.PermissionRequest.isReadAudioPermissionGranted;
import static com.driot.bookplayer.utils.PermissionRequest.isRecordAudioPermissionGranted;

public class PlayActivity extends LoggingActivity {

    private static final float INCREMENT_SPEED = 0.05f;

    private PlaybackViewModel vm;

    private ImageButton bPlayPause, bRewind, bForward;
    private Button bSpeedUp, bSpeedDown, bSetSleep;
    private SeekBar seekbar;
    private TextView tvSeekBar, tvTotalTime, tvTitle, tvSubTitle, tvSpeed, tvListeningTime, tvTimeLeft;
    private View progressOverlay, messageOverlay;

    private ImageView ivCover;
    private String lastCoverUri = null;

    private FrequencyVisualizerView frequencyVisualizerView;

    private View ttsContainer;
    private TextView tvTtsText;
    private ImageButton btnToggleTtsView;
    private boolean showingTtsText = true;
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

    // --- Broadcasts we still care about at the Activity level (UI only) ---
    private final BroadcastReceiver uiReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            final String action = i.getAction();
            if (AudioService.NOTIFICATION_PLAYBACK_TIMER_VALUE.equals(action)) {
                reDrawListeningSince(i.getIntExtra(TIMER_VALUE, -999));
            } else if (Intents.NOTIFICATION_TTS_RANGE.equals(action)) {
                int s = i.getIntExtra(Intents.EXTRA_TTS_START, -1);
                int e = i.getIntExtra(Intents.EXTRA_TTS_END, -1);
                scheduleTtsHighlight(s, e);
            } else if (AudioService.NOTIFICATION_ERROR.equals(action)) {
                // If it’s a TTS error, it’s recoverable → UI is already driven by phases
                String em = i.getStringExtra(AudioService.ERR_MSG);
                PlaybackUiState s = vm.getState().getValue();
                if (em != null && em.startsWith("TTS")) {
                    // Show non-blocking message overlay via vm.getPhase() observer
                    // Do NOT finish the activity.
                    return;
                }
                // Non-TTS: keep the old fatal path
                finishAndShowFatalError(em);
            } else if (AudioService.NOTIFICATION_FILENOTFOUND.equals(action)) {
                finishAndShowFatalError(null);
            } else if (AudioService.NOTIFICATION_PLAYLISTFINISHED.equals(action)) {
                myToast(getString(R.string.notification_playlist_finished));
                finish();
            } else if (AudioService.NOTIFICATION_PLAYBACK_MAXTIMEREACH.equals(action)) {
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

        tvSeekBar   = findViewById(R.id.textViewSeekBar);
        tvTotalTime = findViewById(R.id.textViewTempsTotal);
        tvTitle     = findViewById(R.id.textviewTitle);
        tvSubTitle  = findViewById(R.id.textViewSubTitle);
        tvSpeed     = findViewById(R.id.textViewSpeed);
        tvListeningTime = findViewById(R.id.tv_ListeningTime);
        tvTimeLeft      = findViewById(R.id.tv_TimeLeft);
        tvListeningTimeBaseText = getString(R.string.tv_ListeningTimeWithNoUserAction);

        seekbar = findViewById(R.id.seekBar);
        ivCover = findViewById(R.id.folderImage);
        frequencyVisualizerView = findViewById(R.id.frequencyVisualizerView);

        ttsContainer   = findViewById(R.id.ttsContainer);
        tvTtsText      = findViewById(R.id.tvTtsText);
        btnToggleTtsView = findViewById(R.id.btnToggleTtsView);

        final TextView progressTitle = progressOverlay.findViewById(R.id.tv_progress_overlay_title);
        final TextView progressMessage = progressOverlay.findViewById(R.id.tv_progress_overlay_message);
        progressTitle.setText(getString(R.string.Text_To_Speech));

        // Clicks
        bPlayPause.setOnClickListener(v -> {vm.playPause();suppressAutoScroll = false;});
        bForward  .setOnClickListener(v -> vm.next());
        bRewind   .setOnClickListener(v -> vm.prev());
        bSpeedUp  .setOnClickListener(v -> setSpeedViaVm(+INCREMENT_SPEED));
        bSpeedDown.setOnClickListener(v -> setSpeedViaVm(-INCREMENT_SPEED));
        bSetSleep .setOnClickListener(v -> showSleepDialog());

        btnToggleTtsView.setOnClickListener(v -> {
            showingTtsText = !showingTtsText;
            applyTtsToggleUi(vm.getState().getValue());
        });

        ImageButton ib_settings = findViewById(R.id.ib_settings);
        ib_settings.setOnClickListener((v) -> {
            myLogI("--- User clicks SETTINGS ---");
            SettingsHostActivity.start(this, TtsSettingsFragment.class, true, R.string.tts_settings);
        });

        ClickInterceptFrameLayout container = findViewById(R.id.coverContainer);
        container.setCallbacks(new ClickInterceptFrameLayout.Callbacks() {
            @Override public void onSingleTap() {
                if (Option.getClickVisualizerPlayPause()) vm.playPause();
            }
            @Override public void onDoubleTap() {
                PlaybackUiState s = vm.getState().getValue();
                if (s != null && s.ttsMode) {
                    showingTtsText = !showingTtsText;
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
            if (s == null) return;

            // Title/sub
            TitleHelper.setTitleAndSubtitle(tvTitle, tvSubTitle, s.title, s.subTitle);

            // Seek/progress
            seekbar.setMax((int) Math.max(1L, s.durationMs));
            seekbar.setProgress((int) Math.min(s.positionMs, s.durationMs));
            tvSeekBar.setText(Tonio.formatTime((int) s.positionMs, true));
            tvTotalTime.setText(Tonio.formatTime((int) s.durationMs, true));

            PlaybackViewModel.PhaseUi p = vm.getPhase().getValue();
            boolean isStarting = (p != null && Intents.PHASE_STARTING.equals(p.phase));

            if (s.ready && !isStarting) {
                bPlayPause.setEnabled(true);
                bPlayPause.setImageResource(s.playing ? R.drawable.ic_media_pause_24 : R.drawable.ic_media_play_24);
            } else {
                bPlayPause.setEnabled(false);
                bPlayPause.setImageResource(R.drawable.ic_hourglass_24);
            }

            if (s.cover != null && !s.cover.isEmpty()) {
                if (!s.cover.equals(lastCoverUri)) {
                    lastCoverUri = s.cover;
                    ivCover.setImageURI(null);
                    ivCover.setImageURI(Uri.parse(s.cover));
                    //Glide.with(ivCover.getContext()).load(StorageHelper.checkAndCleanImagePath(ivCover.getContext(), s.cover)).into(ivCover);
                }
                ivCover.setVisibility(View.VISIBLE);
                frequencyVisualizerView.setAlpha(0.6f);
            } else {
                if (lastCoverUri != null) {
                    lastCoverUri = null;
                    ivCover.setImageDrawable(null); // free memory
                }
                ivCover.setVisibility(View.GONE);
                frequencyVisualizerView.setAlpha(1f);
            }
            // TTS vs Audio UI
            applyTtsToggleUi(s);
        });

        vm.getPhase().observe(this, p -> {
            if (p == null) return;
            myLog("Phase observer : " + p);

            // Pull the latest playback state to know if we’re in TTS or audio mode
            PlaybackUiState s = vm.getState().getValue();
            final boolean tts = (s != null && s.ttsMode);

            // Default: hide overlays for pure audio mode unless we’re in an error phase
            if (!tts) {
                // Show only ERROR message if present
                boolean showError = Intents.PHASE_ERROR.equals(p.phase);
                progressOverlay.setVisibility(View.GONE);
                if (showError) {
                    progressTitle.setText("");
                    progressMessage.setText(p.message != null ? p.message : getString(R.string.error_generic));
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
            switch (p.phase) {
                case Intents.PHASE_LOADING_TEXT: label = getString(R.string.tts_phase_loading_text); break;
                case Intents.PHASE_STARTING:     label = getString(R.string.tts_phase_starting);     break;
                case Intents.PHASE_READY:        label = getString(R.string.tts_phase_ready);        break;
                case Intents.PHASE_SPEAKING:     label = getString(R.string.tts_phase_speaking);     break;
                case Intents.PHASE_ERROR:        label = getString(R.string.tts_phase_error);        break;
                default:                         label = "";                                         break;
            }
            // Prefer explicit message from service if present
            if (p.message != null && !p.message.isEmpty()) label = p.message;
            progressMessage.setText(label);

            // Error message overlay (non-blocking)
            if (Intents.PHASE_ERROR.equals(p.phase)) {
                progressMessage.setText(p.message != null ? p.message : getString(R.string.tts_phase_error));
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


        // Seekbar → VM.seekTo (only acts if VM is bound; otherwise ignored safely)
        seekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            boolean userSeeking;
            @Override public void onProgressChanged(SeekBar sb, int p, boolean fromUser) {}
            @Override public void onStartTrackingTouch(SeekBar sb) { userSeeking = true; }
            @Override public void onStopTrackingTouch(SeekBar sb) {
                userSeeking = false;
                vm.seekTo(sb.getProgress());
            }
        });

        // Meta observer (cover, podcast click handlers, TTS voice spinner init)
        PlayList.getMetaLive().observe(this, ms -> {
            if (ms == null || !ms.loaded) return;

            // Podcast title/sub click → open episodes on double tap
            if (ms.isPodcast) {
                tvTitle.setOnClickListener(v -> handlePodcastClick(ms.podcast));
                tvSubTitle.setOnClickListener(v -> handlePodcastClick(ms.podcast));
            }
        });

        // Back press: if not playing, ask service to stop; then finish.
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                myLogI("--- user press BACK ---");
                PlaybackUiState s = vm.getState().getValue();
                if (s == null || !s.playing) vm.dismissMini(); // sends STOP to service
                finish();
            }
        });

        // Register UI-level broadcasts we still use
        LocalBroadcastManager lb = LocalBroadcastManager.getInstance(this);
        lb.registerReceiver(uiReceiver, new IntentFilter(AudioService.NOTIFICATION_PLAYBACK_TIMER_VALUE)); //for UI displayed Sleep counters
        lb.registerReceiver(uiReceiver, new IntentFilter(Intents.NOTIFICATION_TTS_RANGE));
        lb.registerReceiver(uiReceiver, new IntentFilter(AudioService.NOTIFICATION_ERROR));
        lb.registerReceiver(uiReceiver, new IntentFilter(AudioService.NOTIFICATION_FILENOTFOUND));
        lb.registerReceiver(uiReceiver, new IntentFilter(AudioService.NOTIFICATION_PLAYLISTFINISHED));
        lb.registerReceiver(uiReceiver, new IntentFilter(AudioService.NOTIFICATION_PLAYBACK_MAXTIMEREACH));
    }

    @Override protected void onDestroy() {
        try { LocalBroadcastManager.getInstance(this).unregisterReceiver(uiReceiver); } catch (Throwable ignored) {}
        super.onDestroy();
    }

    // ---------- UI bits that used to call the service directly ----------

    private void setSpeedViaVm(double delta) {
        Double cur = vm.getSpeedOrNull();          // VM tries service if bound; otherwise returns null
        double next = (cur == null ? 1.0 : cur) + delta;
        next = Math.max(0.5, Math.min(3.0, next)); // clamp example
        vm.setSpeed(next);                          // VM proxies to service (no-ops if unbound)
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

    private void reDrawListeningSince(int seconds) {
        try {
            if (seconds >= 0) {
                String since = tvListeningTimeBaseText + " " + Tonio.formatTime(seconds*1000, true);
                tvListeningTime.setText(seconds > 0 ? since : "");
                Integer mins = vm.getCustomSleepMinutesOrNull();
                int timeBeforeSleep = (mins == null || mins == 0) ? Option.getTimeBeforeSleep() : mins;
                String left = getString(R.string.tv_TimeLeft) + " : " +
                        Tonio.formatTime(timeBeforeSleep*60*1000 - seconds*1000, true);
                tvTimeLeft.setText(left);
            } else {
                tvListeningTime.setText("");
                tvTimeLeft.setText("");
            }
        } catch (Throwable t) {
            myLogEE(t, "reDrawListeningSince(" + seconds + ")");
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
        final boolean tts = s.ttsMode;

        btnToggleTtsView.setVisibility(tts ? View.VISIBLE : View.GONE);

        if (!tts) {
            // AUDIO MODE
            ttsContainer.setVisibility(View.GONE);
            ivCover.setVisibility(ivCover.getDrawable()!=null ? View.VISIBLE : View.GONE);

            // Optional visualizer (requires session id → ask VM)
            Integer sessionId = vm.getAudioSessionIdOrNull();
            if (Option.getVisualizerOn() && isRecordAudioPermissionGranted(this) && sessionId != null) {
                try {
                    frequencyVisualizerView.link_toto(sessionId);
                    frequencyVisualizerView.setVisibility(View.VISIBLE);
                } catch (Throwable ignored) {}
            } else {
                frequencyVisualizerView.setVisibility(View.GONE);
            }
        } else {
            // TTS MODE
            frequencyVisualizerView.setVisibility(View.GONE);
            if (showingTtsText) {
                ttsContainer.setVisibility(View.VISIBLE);
                ivCover.setVisibility(View.GONE);

                String txt = vm.getTtsTextOrEmpty();
                if (txt == null) txt = "";

// Only rebuild when text content actually changed
                boolean textChanged = (lastTtsTextString == null) || !lastTtsTextString.equals(txt);
                if (textChanged) {
                    lastTtsTextString = txt;
                    SpannableStringBuilder sb = new SpannableStringBuilder(txt);
                    tvTtsText.setText(sb, TextView.BufferType.SPANNABLE);
                    spannableText = (Spannable) tvTtsText.getText();
                    // (Re)enable movement/scroll once, fine to keep as-is
                    tvTtsText.setMovementMethod(ScrollingMovementMethod.getInstance());
                    tvTtsText.setVerticalScrollBarEnabled(true);
                }
                // Tap-to-seek within text
                final android.view.GestureDetector tapDetector =
                        new android.view.GestureDetector(tvTtsText.getContext(),
                                new android.view.GestureDetector.SimpleOnGestureListener() {
                                    @Override public boolean onDown(MotionEvent e) {
                                        // must return true so we keep receiving events
                                        return true;
                                    }
                                    @Override public boolean onSingleTapUp(MotionEvent e) {
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



                btnToggleTtsView.setImageResource(android.R.drawable.ic_menu_gallery); // next → image
            } else {
                ttsContainer.setVisibility(View.GONE);
                ivCover.setVisibility(View.VISIBLE);
                btnToggleTtsView.setImageResource(android.R.drawable.ic_menu_edit); // next → text
            }
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

        // Re-enable spinner when phase is not busy
        vm.getPhase().observe(this, p -> {
            if (p == null) return;
            boolean busy = p.isBusyPhase(); // you already have this helper
            spinnerTtsVoice.setEnabled(!busy);
        });

        vm.setupTtsVoiceSpinner(
                this,
                spinnerTtsVoice,
                saved,
                voice -> {
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

                    boolean wasPlaying = false;
                    PlaybackUiState s = vm.getState().getValue();
                    if (s != null) wasPlaying = s.playing;

                    final boolean wasPlayingFinal = wasPlaying;
                    final String prevGood = currentVoiceName[0];
                    try {
                        if (vm != null && vm.getState().getValue() != null && vm.getState().getValue().ttsMode) {
                            vm.warmUpTtsVoice(picked, (ready, reason) -> runOnUiThread(() -> {
                                spinnerTtsVoice.setEnabled(true);

                                if (ready) {
                                    currentVoiceName[0] = picked; // commit
                                    if (wasPlayingFinal) {
                                        // If your TtsEngine resumes automatically after warm-up,
                                        // you can omit the toggles below. If not, re-kick play:
                                        vm.playPause(); // pause
                                        vm.playPause(); // play
                                    }
                                } else {
                                    // Roll back visually + persistently
                                    Pref.setBookTtsVoiceName(this, folderId, prevGood);
                                    selectVoiceByNameWithoutCallback(spinnerTtsVoice, prevGood, suppressSelect);
                                    myToast(getString(mapWarmupReasonToMsg(reason)));
                                }
                            }));
                        }
                    } catch (Throwable ignored) {
                        spinnerTtsVoice.setEnabled(true);
                    }

                }
        );
    }
    /** Finds a voice by engine name and selects it without firing the spinner listener. */
    private void selectVoiceByNameWithoutCallback(Spinner spinner, String name, boolean[] suppressFlag) {
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
            spinner.setSelection(target, false);
            spinner.post(() -> suppressFlag[0] = false);
        } catch (Throwable ignored) {}
    }


    private void finishAndShowFatalError(String errMessage) {
        ErrorUi.showPlayAudioErrorMessage(this, errMessage);
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

}
