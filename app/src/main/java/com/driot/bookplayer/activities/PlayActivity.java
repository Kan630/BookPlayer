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

import com.driot.bookplayer.R;
import com.driot.bookplayer.db.Podcast;
import com.driot.bookplayer.db.ZikFile;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.global.Pref;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.helpers.FileHelper;
import com.driot.bookplayer.helpers.InsetHelper;
import com.driot.bookplayer.helpers.StorageHelper;
import com.driot.bookplayer.helpers.TitleHelper;
import com.driot.bookplayer.helpers.TtsHelper;
import com.driot.bookplayer.helpers.ViewHelper;
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

import java.io.File;

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
    private FrequencyVisualizerView frequencyVisualizerView;

    private View ttsContainer;
    private Spinner spinnerTtsVoice;
    private TextView tvTtsText;
    private ImageButton btnToggleTtsView;
    private boolean showingTtsText = true;
    private Spannable spannableText;
    private final BackgroundColorSpan ttsBgSpan = new BackgroundColorSpan(0x55FFFF00);
    private final ForegroundColorSpan ttsFgSpan = new ForegroundColorSpan(Color.BLACK);

    private String tvListeningTimeBaseText;

    private long podcastLastClickTime = 0;
    private static final long PODCAST_DOUBLE_CLICK_THRESHOLD = 300;

    // --- Broadcasts we still care about at the Activity level (UI only) ---
    private final BroadcastReceiver uiReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            final String action = i.getAction();
            if (AudioService.NOTIFICATION_PLAYBACK_TIMER_VALUE.equals(action)) {
                reDrawListeningSince(i.getIntExtra(TIMER_VALUE, -999));
            } else if (AudioService.NOTIFICATION_TTS_RANGE.equals(action)) {
                int s = i.getIntExtra(AudioService.EXTRA_TTS_START, -1);
                int e = i.getIntExtra(AudioService.EXTRA_TTS_END, -1);
                scheduleTtsHighlight(s, e);
            } else if (AudioService.NOTIFICATION_ERROR.equals(action)) {
                finishAndShowFatalError(i.getStringExtra(AudioService.ERR_MSG));
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

        if (PlayList.getInstance() == null) { finish(); return; }

        vm = new ViewModelProvider(this).get(PlaybackViewModel.class);

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
        spinnerTtsVoice= findViewById(R.id.spinnerTtsVoice);
        tvTtsText      = findViewById(R.id.tvTtsText);
        btnToggleTtsView = findViewById(R.id.btnToggleTtsView);

        // Clicks
        bPlayPause.setOnClickListener(v -> vm.playPause());
        bForward  .setOnClickListener(v -> vm.next());
        bRewind   .setOnClickListener(v -> vm.prev());
        bSpeedUp  .setOnClickListener(v -> setSpeedViaVm(+INCREMENT_SPEED));
        bSpeedDown.setOnClickListener(v -> setSpeedViaVm(-INCREMENT_SPEED));
        bSetSleep .setOnClickListener(v -> showSleepDialog());

        btnToggleTtsView.setOnClickListener(v -> {
            showingTtsText = !showingTtsText;
            applyTtsToggleUi(vm.getState().getValue());
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

            // Play/Pause icon + readiness
            bPlayPause.setEnabled(s.ready);
            bPlayPause.setImageResource(s.ready
                    ? (s.playing ? R.drawable.ic_media_pause_24 : R.drawable.ic_media_play_24)
                    : R.drawable.ic_hourglass_24);

            // TTS vs Audio UI
            applyTtsToggleUi(s);

            // Cover image via PlayList meta (kept as you had)
            // (Handled below in PlayList meta observer)
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
            if (ms == null || !ms.loaded || ms.folder == null) return;

            // Cover
            if (ms.folder.image != null && !ms.folder.image.isEmpty()) {
                ivCover.setImageURI(Uri.parse(ms.folder.image));
                ivCover.setVisibility(View.VISIBLE);
                frequencyVisualizerView.setAlpha(0.6f);
                try {
                    File imageFile = new File(ms.folder.image);
                    myLogD("Image found : " + imageFile.getName() + " - " + Tonio.getReadableSize(imageFile.length()));
                } catch (Exception ignored) {}
            } else {
                ivCover.setVisibility(View.GONE);
                frequencyVisualizerView.setAlpha(1f);
            }

            // Podcast title/sub click → open episodes on double tap
            if (ms.isPodcast) {
                tvTitle.setOnClickListener(v -> handlePodcastClick(ms.podcast));
                tvSubTitle.setOnClickListener(v -> handlePodcastClick(ms.podcast));
            }

            // TTS voices (if text book)
            if (ms.folder.playType != null && ms.folder.playType.equals(Var.PLAY_TYPE_TEXT)) {
                initTtsVoiceSpinner(ms.folder.getId());
            }
        });

        // Back press: if not playing, ask service to stop; then finish.
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                PlaybackUiState s = vm.getState().getValue();
                if (s == null || !s.playing) vm.dismissMini(); // sends STOP to service
                finish();
            }
        });

        // Kick the service if needed (optional; harmless if already running)
        startService(new Intent(getApplicationContext(), AudioService.class)
                .putExtra(Var.EXTRA_CALLER, this.getClass().getSimpleName()));

        // Register UI-level broadcasts we still use
        LocalBroadcastManager lb = LocalBroadcastManager.getInstance(this);
        lb.registerReceiver(uiReceiver, new IntentFilter(AudioService.NOTIFICATION_PLAYBACK_TIMER_VALUE));
        lb.registerReceiver(uiReceiver, new IntentFilter(AudioService.NOTIFICATION_TTS_RANGE));
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

                // Fill TTS text (VM tries service if bound; else empty)
                String txt = vm.getTtsTextOrEmpty();
                SpannableStringBuilder sb = new SpannableStringBuilder(txt == null ? "" : txt);
                tvTtsText.setText(sb, TextView.BufferType.SPANNABLE);
                spannableText = (Spannable) tvTtsText.getText();
                tvTtsText.setMovementMethod(ScrollingMovementMethod.getInstance());

                // Tap-to-seek within text
                tvTtsText.setOnTouchListener((v, ev) -> {
                    if (ev.getAction() == MotionEvent.ACTION_UP && tvTtsText.getLayout() != null) {
                        Layout layout = tvTtsText.getLayout();
                        int x = (int)ev.getX() - tvTtsText.getTotalPaddingLeft() + tvTtsText.getScrollX();
                        int y = (int)ev.getY() - tvTtsText.getTotalPaddingTop() + tvTtsText.getScrollY();
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
                    }
                    return false; // allow scrolling
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
        uiH.postDelayed(this::applyTtsHighlight, 60);
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
        if (saved == null) saved = Option.getTtsVoice();

        final boolean[] first = {true};
        final boolean[] touched = {false};

        spinnerTtsVoice.setOnTouchListener((v,e) -> {
            if (e.getAction()==MotionEvent.ACTION_UP) { touched[0]=true; v.performClick(); }
            return false;
        });

        vm.setupTtsVoiceSpinner(
                this,
                spinnerTtsVoice,
                saved,
                voice -> {
                    if (first[0]) { first[0]=false; return; }
                    if (!touched[0]) return;
                    //TODO uncomment
                    /*
                    final String name = (voice==null || voice.name==null || voice.name.isEmpty()) ? "system" : voice.name;
                    if (!name.equalsIgnoreCase(saved)) {
                        Pref.setBookTtsVoiceName(this, folderId, name);
                        vm.warmUpTtsVoice(name); // async enable play button on readiness inside VM
                    }

                     */
                }
        );
    }

    private void finishAndShowFatalError(String errMessage) {
        try {
            String pathText = null;
            PlayList pl = PlayList.getInstance();
            if (pl != null && pl.getZikFile() != null) {
                String zikFilePath = pl.getZikFile().getPath();
                pathText = getString(R.string.source_file_path) + " = \n[" + Uri.decode(zikFilePath) + "]";
                boolean exists = FileHelper.exists(zikFilePath);

                if (errMessage == null || errMessage.isEmpty()) {
                    if (!exists) {
                        if (StorageHelper.isInInternalMemory(zikFilePath)) {
                            errMessage = getString(R.string.source_not_found);
                        } else {
                            errMessage = getString(R.string.source_not_found_deleted);
                        }
                    } else {
                        if (!isReadAudioPermissionGranted(this)) {
                            errMessage = getString(R.string.permission_not_set);
                            MsgBox.alertWithNeutral(
                                    this,
                                    getString(R.string.error_reading_track),
                                    errMessage,
                                    pathText,
                                    getString(R.string.settings),
                                    new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                            .setData(android.net.Uri.fromParts("package", getPackageName(), null))
                            );
                            finish();
                            return;
                        } else {
                            errMessage = getString(R.string.source_not_found);
                        }
                    }
                }
            } else {
                errMessage = getString(R.string.error_playlist_null);
            }

            MsgBox.alert(this, getString(R.string.error_reading_track), errMessage, pathText);
        } catch (Throwable t) {
            myToastEE(t, getString(R.string.error_reading_track));
        }
        finish();
    }
}
