package com.driot.bookplayer.activities;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.method.ScrollingMovementMethod;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.view.GestureDetector;
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

import com.driot.bookplayer.R;
import com.driot.bookplayer.db.Podcast;
import com.driot.bookplayer.db.ZikFile;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.global.Pref;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.helpers.FileHelper;
import com.driot.bookplayer.helpers.FirebaseAnalyticsHelper;
import com.driot.bookplayer.helpers.InsetHelper;
import com.driot.bookplayer.helpers.StorageHelper;
import com.driot.bookplayer.helpers.TitleHelper;
import com.driot.bookplayer.helpers.TtsHelper;
import com.driot.bookplayer.player.PlayList;
import com.driot.bookplayer.player.PlaybackUiState;
import com.driot.bookplayer.player.AudioService;
import com.driot.bookplayer.helpers.ViewHelper;
import com.driot.bookplayer.utils.MetadataUi;
import com.driot.bookplayer.utils.MsgBox;
import com.driot.bookplayer.utils.Tonio;
import com.driot.bookplayer.views.ClickInterceptFrameLayout;
import com.driot.bookplayer.views.FrequencyVisualizerView;
import com.driot.bookplayer.utils.log.LoggingActivity;

import java.io.File;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;

import static com.driot.bookplayer.global.Var.SLEEP_PRESET_VALUES;
import static com.driot.bookplayer.player.AudioService.TIMER_VALUE;
import static com.driot.bookplayer.utils.PermissionRequest.isReadAudioPermissionGranted;
import static com.driot.bookplayer.utils.PermissionRequest.isRecordAudioPermissionGranted;

import androidx.activity.OnBackPressedCallback;
import androidx.lifecycle.Observer;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

/**
 * created by Antoine Driot -- antoine.driot.com -- on 30/10/2020
 * <p>
 * onCreate
 * bindToService
 * getZikFiles
 * initialize
 */
public class PlayActivity extends LoggingActivity {

    public static final String EXTRA_AUTOPLAY = "extra_autoplay";

    private static final int INTERVAL_REDRAW_SEEKBAR = 500; //  because looks like it happens erratically when choosing value of 100 for this constant
    private static final int DELAY_ANIMATION = 200;
    private static final float INCREMENT_SPEED = 0.05f;
    AudioService audioService;
    boolean audioServiceBound = false;
    private ImageButton bPlayPause;
    ImageButton bRewind, bForward;
    Button bSpeedUp, bSpeedDown, bSetSleep;

    private SeekBar seekbar;
    private TextView tvSeekBar, tvTotalTime, tvTitle, tvSubTitle, tvSpeed, tvListeningTime, tvTimeLeft;
    private View progressOverlay, messageOverlay;
    private FrequencyVisualizerView frequencyVisualizerView;
    private boolean AnimationNow;
    private Intent intentMusicService;
    private Timer timerRedrawUI;
    private String tvListeningTimeBaseText;
    private boolean forceReload;

    String[] broadcastNotifications = {
            AudioService.NOTIFICATION_TRACKFINISHED //useless ?
            ,AudioService.NOTIFICATION_AUDIOFOCUS_GAIN //useless ?
            ,AudioService.NOTIFICATION_AUDIOFOCUS_LOST //useless ?
            ,AudioService.READY_TO_PLAY
            ,AudioService.NOTIFICATION_ERROR
            ,AudioService.NOTIFICATION_ZIP_FILE_LOADED //useless ?
            ,AudioService.NOTIFICATION_PLAYLISTFINISHED
            ,AudioService.NOTIFICATION_PLAYBACK_MAXTIMEREACH
            ,AudioService.NOTIFICATION_PLAYBACK_TIMER_VALUE
            ,AudioService.NOTIFICATION_FILENOTFOUND
            ,AudioService.NOTIFICATION_TTS_RANGE
            //,AudioService.NOTIFICATION_TTS_READY
            //,AudioService.NOTIFICATION_TTS_NEEDS_DOWNLOAD
    };

    private long PodcastLastClickTime = 0;
    private static final long PODCAST_DOUBLE_CLICK_THRESHOLD = 300;

    private ImageView ivCover;
    private View ttsContainer;
    private Spinner spinnerTtsVoice;
    private TextView tvTtsText;
    private ImageButton btnToggleTtsView;
    private boolean showingTtsText = true;

    private Spannable spannableText;
    private final BackgroundColorSpan ttsBgSpan = new BackgroundColorSpan(0x55FFFF00);
    private final ForegroundColorSpan ttsFgSpan = new ForegroundColorSpan(Color.BLACK);
    private int pendingStart = -1, pendingEnd = -1;
    private final Handler uiH = new Handler(Looper.getMainLooper());
    private boolean highlightScheduled = false;
    private AutoCloseable ttsHandle;
    private String lastSavedTtsVoice;

    /********************************************************************************
     ***       SERVICE
     ********************************************************************************
     */

    private final ServiceConnection audioServiceConnection = new ServiceConnection() {

        private final Observer<PlaybackUiState> uiObserver = state -> {
            if (state == null) return;

            // same UI updates you were doing in the ACTION_UI_STATE branch:
            if (!state.ready) {
                bPlayPause.setImageResource(R.drawable.ic_hourglass_24);
                bPlayPause.setEnabled(false);
            } else {
                bPlayPause.setEnabled(true);
                bPlayPause.setImageResource(state.playing ? R.drawable.ic_media_pause_24
                        : R.drawable.ic_media_play_24);
            }

            TitleHelper.setTitleAndSubtitle(tvTitle, tvSubTitle, state.title, state.subTitle);

            // Keep your timer-based smooth progress if you like,
            // but also sync hard bounds from the state:
            seekbar.setMax((int) Math.max(1, state.durationMs));
            seekbar.setProgress((int) Math.min(state.positionMs, state.durationMs));
            tvSeekBar.setText(Tonio.formatTime((int) state.positionMs, true));

            // You can access cover path from state.cover if you want to update the image here too.
        };

        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            myLog("onServiceConnected");
            AudioService.BackgroundBinder binder = (AudioService.BackgroundBinder) service;
            audioService = binder.getService();
            audioServiceBound = true;

            audioService.getUiLive().removeObservers(PlayActivity.this);
            audioService.getUiLive().observe(PlayActivity.this, uiObserver);

            boolean auto = Option.getAutoPlayOnMainPlayer();

            // only reload if forced or engine not ready; otherwise just sync UI
            boolean shouldReload = forceReload || !audioService.isReadyToPlay();

            if (shouldReload) {
                try {
                    audioService.pauseAudioNoSave();
                } catch (Throwable t) {
                    myLogEE(t, "audioService.pauseAudioNoSave()");
                }
                audioService.directPlay = auto;
                try {
                    audioService.loadFile();
                } catch (Throwable t) {
                    myLogEE(t, "audioService.loadFile()");
                }
            } else {
                // same engine & track: no reload; optionally resume if auto-play pref says so
                if (auto && !audioService.isPlaying()) {
                    audioService.playAudio();
                } else {
                    audioService.pingUi(); // refresh mini/UI state
                }
            }

            myLogD("onServiceConnected - DrawUI");
            DrawUI();
            // use-once
            forceReload = false;
        }


        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            myLog("OnServiceDisconnected");
            audioServiceBound = false;
        }

    };
    private final BroadcastReceiver broadCastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (!Objects.equals(action, AudioService.NOTIFICATION_PLAYBACK_TIMER_VALUE) && !Objects.equals(action, AudioService.NOTIFICATION_TTS_RANGE)) {
                //if (!Objects.equals(action, AudioService.NOTIFICATION_PLAYBACK_TIMER_VALUE)) {
                myLog("broadcast received : [" + action + "]");
            }

            if (Objects.equals(action, AudioService.NOTIFICATION_ERROR)) {
                String from = intent.getStringExtra(AudioService.FROM);
                String err_msg =  intent.getStringExtra(AudioService.ERR_MSG);
                if (Objects.equals(from, "TTS")) {
                    myLogEE(null, "TTS Error : [" + err_msg + "]");
                    myToast(getString(R.string.toast_tts_not_ready));
                } else {
                    myLogEE(null, "AudioService.NOTIFICATION_ERROR");
                    finishAndShowFatalError(err_msg);
                }

            } else if (Objects.equals(action, AudioService.NOTIFICATION_FILENOTFOUND)) {
                finishAndShowFatalError(null);

            } else if (Objects.equals(action, AudioService.NOTIFICATION_TRACKFINISHED)) {
                myLogD("nothing special to do for that broadcast");

            } else if (Objects.equals(action, AudioService.NOTIFICATION_PLAYLISTFINISHED)) {
                myToast(getString(R.string.notification_playlist_finished));
                finish();

            } else if (Objects.equals(action, AudioService.NOTIFICATION_PLAYBACK_MAXTIMEREACH)) {
                myToast(getString(R.string.notification_auto_sleep));
                finish();

            } else if (Objects.equals(action, AudioService.NOTIFICATION_PLAYBACK_TIMER_VALUE)) {
                reDrawListeningSince(intent.getIntExtra(TIMER_VALUE,-999));

            } else if (Objects.equals(action, AudioService.READY_TO_PLAY)) {
                DrawUI();
            } else if (Objects.equals(action, AudioService.NOTIFICATION_TTS_RANGE)) {
                int s = intent.getIntExtra(AudioService.EXTRA_TTS_START, -1);
                int e = intent.getIntExtra(AudioService.EXTRA_TTS_END, -1);
                if (s >= 0 && e > s) scheduleTtsHighlight(s, e);

            } else {
                myLogEE(null,"Unknown Broadcast : " + action);
            }
        }
    };

    /********************************************************************************
     ***       ON CREATE
     ********************************************************************************
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_play);
        InsetHelper.apply(this);

        if (Option.getScreenOrientationLock()) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);
        }

        //autoPlay = getIntent().getBooleanExtra(EXTRA_AUTOPLAY, false);
        forceReload = getIntent().getBooleanExtra("force_reload", false);
        //myLogD("forceReload = " + forceReload + ", autoPlay = " + autoPlay);

        PlayList playList = PlayList.getInstance();
        if (playList == null) {
            myLogEE(null, "onCreate() -- cancelling since PlayList.getInstance() == null");
            //finishAndShowFatalError(null);
            finish();
            return;
        }

        bPlayPause = findViewById(R.id.ibPlayPause);
        bPlayPause.setImageResource(R.drawable.ic_hourglass_24);
        bPlayPause.setEnabled(false);
        bRewind = findViewById(R.id.ibRewind);
        bForward = findViewById(R.id.ibForward);
        bSpeedUp = findViewById(R.id.bSpeedUp);
        bSpeedDown = findViewById(R.id.bSpeedDown);
        bSetSleep = findViewById(R.id.bSetSleep);

        bPlayPause.setOnClickListener(
                v -> {
                    myLogI("----------> USER PRESSES PLAY BUTTON <----------");
                    FirebaseAnalyticsHelper.tellAnalyticsPressPlay(tvTitle.toString());
                    playMe();
                });

        bForward.setOnClickListener(v -> {
            myLogI("user clicks button Forward");
            forwardMe();
        });
        bRewind.setOnClickListener(v -> {
            myLogI("user clicks button Backard");
            backwardMe();
        });
        bSpeedUp.setOnClickListener(v -> {
            myLogI("user clicks button speed Up");
            SpeedMeUp();
        });
        bSpeedDown.setOnClickListener(v -> {
            myLogI("user clicks button speed Down");
            SpeedMeDown();
        });
        bSetSleep.setOnClickListener(v -> {
            myLogI("user clicks button Sleep");
            setSleep();
        });

        progressOverlay = findViewById(R.id.progress_overlay);
        messageOverlay = findViewById(R.id.message_overlay);

        tvSeekBar = findViewById(R.id.textViewSeekBar);
        tvTotalTime = findViewById(R.id.textViewTempsTotal);
        tvTitle = findViewById(R.id.textviewTitle);
        tvSubTitle = findViewById(R.id.textViewSubTitle);
        tvSpeed = findViewById(R.id.textViewSpeed);
        seekbar = findViewById(R.id.seekBar);
        tvListeningTime = findViewById(R.id.tv_ListeningTime);
        tvListeningTimeBaseText = getString(R.string.tv_ListeningTimeWithNoUserAction);
        tvTimeLeft = findViewById(R.id.tv_TimeLeft);
        frequencyVisualizerView = findViewById(R.id.frequencyVisualizerView);

        ivCover = findViewById(R.id.folderImage);

        ttsContainer = findViewById(R.id.ttsContainer);
        spinnerTtsVoice = findViewById(R.id.spinnerTtsVoice);
        tvTtsText = findViewById(R.id.tvTtsText);
        btnToggleTtsView = findViewById(R.id.btnToggleTtsView);

        btnToggleTtsView.setOnClickListener(v -> {
            showingTtsText = !showingTtsText;
            applyTtsToggleUi();
        });

        ClickInterceptFrameLayout container = findViewById(R.id.coverContainer);
        container.setCallbacks(new ClickInterceptFrameLayout.Callbacks() {
            @Override public void onSingleTap() {
                // Reuse your existing logic (respects the “tap to play/pause” option)
                visualizerClick();
            }

            @Override public void onDoubleTap() {
                // Mirror your previous double-tap on the ImageView (toggle TTS view)
                if (!audioServiceBound || audioService == null) return;
                if (audioService.isTtsMode()) {
                    showingTtsText = !showingTtsText;
                    applyTtsToggleUi();
                }
            }

            @Override public void onLongPress() {
                MetadataUi.showMetadataDialog(PlayActivity.this, PlayList.getInstance().getZikFile());
            }
        });

        PlayList.getMetaLive().observe(this, ms -> {
            if (!ms.loaded || ms.folder == null) {
                myLogW("Meta not loaded yet; show skeleton/keep previous visuals");
                return;
            }
            myLogD("Meta arrived via LiveData; folderId=" + ms.folder.getId() + " isPodcast=" + ms.isPodcast);
            // Your previous onMetaLoaded(...) logic:
            // - update cover, title/sub, flags, etc.
            // - "Is Podcast" branch => use state.podcast != null || state.isPodcast
            //drawMetaOnUIThread(state.folder, state.podcast, state.isPodcast);

            // Voices
            if (ms.folder.playType!=null && ms.folder.playType.equals(Var.PLAY_TYPE_TEXT)) {
                initTtsVoiceSpinner(ms.folder.getId());
            }

            // Playlist objects are all loaded
            if (ms.folder.image != null && !ms.folder.image.isEmpty()) {
                ivCover.setImageURI(Uri.parse(ms.folder.image));
                ivCover.setVisibility(View.VISIBLE);
                frequencyVisualizerView.setAlpha(0.6f);
                try {
                    File imageFile = new File(ms.folder.image);
                    myLogD("Image found : " + imageFile.getName() + " - " + Tonio.getReadableSize(imageFile.length()));
                } catch (Exception e) {
                    myLogE("image debug ko");
                }
                if (ms.isPodcast) {
                    myLogD("Is Podcast");
                    tvTitle.setOnClickListener(v -> {
                        myLogI("user clicks Title");
                        handlePodcastClick(ms.podcast);
                    });
                    tvSubTitle.setOnClickListener(v -> {
                        myLogI("user clicks subTitle");
                        handlePodcastClick(ms.podcast);
                    });
                }
            } else {
                ivCover.setVisibility(View.GONE);
                frequencyVisualizerView.setAlpha(1f); // fully opaque
            }
        });

        myLogD("onCreate() -- Launching Music Service");
        launchService();

        //-*******************************************************************************
        //-***       SEEKBAR
        //-*******************************************************************************

        seekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    myLogI("--- USER CLICK SEEK BAR ---- => Change Progress");
                    audioService.setPosition(progress);
                    tvSeekBar.setText(Tonio.formatTime(progress,true)); //TODO usefull ?
                }
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        // if we return while no audio is playing, we kill the service
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                myLogI("--- USER CLICK BACK --- (system button)");
                if (audioService != null && !audioService.isPlaying()) {
                    startService(new Intent(getApplicationContext(), AudioService.class)
                            .setAction(AudioService.EXTRA_CMD_STOP)
                            .putExtra(Var.EXTRA_CALLER, this.getClass().getSimpleName()));
                }
                finish();
            }
        });
    }

    private void launchService() {
        myLogD("launchService");
        intentMusicService = new Intent(PlayActivity.this, AudioService.class)
                .putExtra(Var.EXTRA_CALLER, this.getClass().getSimpleName());
        startService(intentMusicService);
        audioServiceBound = false;
        try {
            audioServiceBound = bindService(intentMusicService, audioServiceConnection, Context.BIND_AUTO_CREATE);
        } catch (Exception e) {
            myLogEE(e,"ERROR bindService");
        }
        myLog("call start & bind to AudioService in onCreate() - bound result :" + audioServiceBound);
    }

    private void visualizerClick() {
        myLogI("--- USER CLICKS VISUALIZER ---");
        if (Option.getClickVisualizerPlayPause()) {
            playMe();
        }
    }

    private void playMe() {
        if (!audioServiceBound || audioService == null || !audioService.isRunning()) {
            myLogEE(null, "playMe() => service not ready");
            return;
        }

        // If engine isn’t ready yet, keep hourglass & ignore taps
        if (!audioService.isReadyToPlay()) {
            bPlayPause.setImageResource(R.drawable.ic_hourglass_24);
            bPlayPause.setEnabled(false);
            myLog("playMe() while preparing TTS — waiting...");
            return;
        }

        bPlayPause.setEnabled(true);
        if (audioService.isPlaying()) {
            myLog("PlayMe() => pause");
            audioService.pauseAudio();
            tvListeningTimeBaseText = getString(R.string.tv_ListeningTimeWithNoUserAction);
            tvListeningTime.setText("");
            tvTimeLeft.setText("");
        } else {
            myLog("PlayMe() => play");
            audioService.playAudio();
            runVisualizer();
        }
    }

    private void forwardMe() {
        audioService.forwardAudio();
        myLog("Forward");
    }

    private void backwardMe() {
        audioService.backwardAudio();
        myLog("Backward");
    }

    private void SpeedMeUp() {
        setSpeed(audioService.getSpeed() + INCREMENT_SPEED);
        myLog("SpeedUp");
    }
    private void SpeedMeDown() {
        setSpeed(audioService.getSpeed() - INCREMENT_SPEED);
        myLog("SpeedDown");
    }
    private void setSpeed(double speed) {
        audioService.setSpeed(speed);
        String txt = Tonio.formatPercentStringForSpeed((double) speed * 100);
        tvSpeed.setText(txt);
    }

    private void setSleep() {
        // Create an alert dialog builder
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.SleepTimer));

        // Inflate and set the custom layout for the dialog
        LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_set_sleep, null);
        builder.setView(dialogView);

        // Find the EditText and buttons in the dialog layout
        EditText inputMinutes = dialogView.findViewById(R.id.inputMinutes);
        Button btnPreset1 = dialogView.findViewById(R.id.btn_preset_01);
        Button btnPreset2 = dialogView.findViewById(R.id.btn_preset_02);
        Button btnPreset3 = dialogView.findViewById(R.id.btn_preset_03);
        Button btnPreset4 = dialogView.findViewById(R.id.btn_preset_04);
        Button btnPreset5 = dialogView.findViewById(R.id.btn_preset_05);
        Button btnPreset6 = dialogView.findViewById(R.id.btn_preset_06);

        DialogInterface.OnClickListener setSleepAction = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String input = inputMinutes.getText().toString().trim();
                if (!input.isEmpty()) {
                    try {
                        int minutes = Integer.parseInt(input);
                        audioService.updateSleepTimer(minutes);
                    } catch (NumberFormatException e) {
                        myToastE(getString(R.string.SleepTimerWrongInt));
                    } catch (Exception e) {
                        myToastE(getString(R.string.SleepTimerGeneralError));
                    }
                }
            }
        };
        builder.setPositiveButton(getString(R.string.Set), setSleepAction);
        builder.setNegativeButton(getString(R.string.Cancel), (dialog, which) -> dialog.cancel());

        // Show the dialog
        AlertDialog dialog = builder.create();
        dialog.show();

        // Array of buttons
        Button[] presetButtons = {btnPreset1, btnPreset2, btnPreset3, btnPreset4, btnPreset5, btnPreset6};

        // Set labels for buttons based on PRESET_VALUES and set their onClick listeners
        for (int i = 0; i < SLEEP_PRESET_VALUES.length; i++) {
            final int presetValue = SLEEP_PRESET_VALUES[i];
            String buttonText = presetValue + " min";
            presetButtons[i].setText(buttonText);

            // Set button click listener
            presetButtons[i].setOnClickListener(v -> {
                inputMinutes.setText(String.valueOf(presetValue));
                setSleepAction.onClick(dialog, DialogInterface.BUTTON_POSITIVE);
                dialog.cancel();
            });
        }
        // Request focus for the EditText
        inputMinutes.post(inputMinutes::requestFocus);
    }


    /********************************************************************************
     ***       EVENTS
     * Destroy = Fleche Retour Arriere ou Change Inclinaison
     ********************************************************************************
     */
    @Override
    protected void onResume() {
        myLog("onResume()... registering broadCastReceiver");

        for (String broadcastNotification : broadcastNotifications) {
            LocalBroadcastManager.getInstance(this).registerReceiver(broadCastReceiver, new IntentFilter(broadcastNotification));
        }
        PlayList pl = PlayList.getInstance();
        runTimerForDisplay();
        audioServiceBound = bindService(intentMusicService, audioServiceConnection, Context.BIND_AUTO_CREATE);
        super.onResume();
    }

    @Override
    protected void onDestroy() {
        myLog("onDestroy - unregister Broadcast Receiver");
        try {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(broadCastReceiver);
        } catch (Exception e) {
            myLogEE(e,"onDestroy() - unregisterReceiver");
        }
        super.onDestroy();
    }

    @Override
    protected void onPause() {
        myLog("onPause() - killing display timer");
        killTimerForDisplay();
        // c'est l'ecran qui s'eteint.. ca call onPause, on UnBind null, on stop, puis 1min apres ca call on Destroy et plus de son
        // du coup, si on reste bind, on passe pas par destroy...
        if (audioServiceBound && audioService != null) {
            try { audioService.pingUi(); } catch (Throwable ignored) {}
        }
        super.onPause();
    }

    @Override
    protected void onStop() {
        myLogD("onStop()");
        super.onStop();
    }

    /********************************************************************************
     ***       GET FROM DB
     ********************************************************************************
     */
    private void DrawUI() {
        PlayList playList = PlayList.getInstance();
        if (playList == null) {
            finishAndShowFatalError(null);
        } else if (playList.getZikFile() == null) {
            myToastEE(null,"DrawUI() => Cannot get Playlist - PlayList.getInstance().getZikFile() is null");
        } else {
            try {
                ZikFile zf = playList.getZikFile();
                myLogD("DrawUI : " + zf.getName() + " -- " + zf.getPosition() + " -- " + zf.getDisplayName());
                TitleHelper.setTitleAndSubtitle(tvTitle, tvSubTitle, zf.getFolderName(), zf.getDisplayName());
                tvTotalTime.setText(Tonio.formatTime(zf.getDuration(), true));
                seekbar.setMax((int) zf.getDuration());
                tvSeekBar.setText(Tonio.formatTime(zf.getPosition(), true));
                seekbar.setProgress((int) zf.getPosition());
                tvSpeed.setText(Tonio.formatPercentStringForSpeed(audioService.getSpeed() * 100));
                try {
                    if (audioService != null && audioService.isTtsMode()) {
                        showTtsUi();
                    } else {
                        showAudioUi();
                    }
                } catch (Exception e) {
                    myLogEE(e, "DrawUI show/hide TTS UI");
                }
                myLogD("----------------------------- play screen drawn " + zf.getPosition());
            } catch (Exception e) {
                myLogEE(e, ":----------------------------- play screen drawn ERROR");
            }
        }
    }
    private void reDrawListeningSince(int tempsEcoule) { // le call vient d'1 timer dans le service...
        try {
            String zeText_since;
            String zeText_left;
            int timeBeforeSleep = audioService.getCustomSleepTime() == 0 ? Option.getTimeBeforeSleep() : audioService.getCustomSleepTime();
            if (tempsEcoule >= 0) {
                String str = tvListeningTimeBaseText;
                zeText_since = str + " " + Tonio.formatTime(tempsEcoule*1000,true);
                zeText_left = getString(R.string.tv_TimeLeft) + " : " + Tonio.formatTime(timeBeforeSleep*1000*60-tempsEcoule*1000,true);
                tvTimeLeft.setText(zeText_left);
                if (tempsEcoule>0) {
                    tvListeningTime.setText(zeText_since);
                } else {
                    tvListeningTime.setText("");
                }
            } else {
                tvListeningTime.setText("");
                tvTimeLeft.setText("");
            }
        } catch (Exception e) {
            myLogEE(e,"reDrawListeningSince(" + tempsEcoule + ")");
        }
    }


    /********************************************************************************
     ***       UPDATE SEEKBAR
     ********************************************************************************
     */

    private void runTimerForDisplay() {   //TODO remove ? (liveData...)
        killTimerForDisplay();
        timerRedrawUI = new Timer();
        timerRedrawUI.schedule(new TimerTask() {
            @Override
            public void run() {
                runOnUiThread(() -> redrawSeekBar());
            }
        }, 0, INTERVAL_REDRAW_SEEKBAR);

    }
    private void killTimerForDisplay() {
        if (timerRedrawUI != null) {
            timerRedrawUI.cancel();
            timerRedrawUI.purge(); // Optional: removes canceled tasks from the queue
            timerRedrawUI = null;  // Helps with garbage collection
        }
    }

    private void redrawSeekBar() {
        try {
            if (audioService != null && audioService.isRunning()) {

                // NEW: show hourglass while engine is preparing (e.g., TTS warm-up)
                if (!audioService.isReadyToPlay()) {
                    bPlayPause.setImageResource(R.drawable.ic_hourglass_24);
                    // Optional: disable the button while waiting
                    bPlayPause.setEnabled(false);
                    return;
                }

                // Ready: re-enable and show play/pause correctly
                bPlayPause.setEnabled(true);
                if (audioService.isPlaying()) {
                    bPlayPause.setImageResource(R.drawable.ic_media_pause_24);
                } else {
                    bPlayPause.setImageResource(R.drawable.ic_media_play_24);
                }

                int iPosition = audioService.getPosition();
                tvSeekBar.setText(Tonio.formatTime(iPosition, true));
                seekbar.setProgress(iPosition);

            } else {
                bPlayPause.setImageResource(R.drawable.ic_hourglass_24);
                bPlayPause.setEnabled(false);
                myLogD("redrawSeekBar => service KO => drawing hourglass");
            }
        } catch (Exception e) {
            myLogEE(e, "redrawSeekBar");
        }
    }


    /********************************************************************************
     ***       DIVERS FONCTIONS
     ********************************************************************************
     */


    private void ShowMessageOverlay() {
        ViewHelper.animateView(messageOverlay, View.VISIBLE, 1f, DELAY_ANIMATION);
        AnimationNow = true;
    }
    private void HideMessageOverlay() {
        ViewHelper.animateView(messageOverlay, View.GONE, 1f, DELAY_ANIMATION);
        AnimationNow = true;
    }

    private void runVisualizer() {
        if (audioService != null && audioService.isTtsMode()) return;
        if (!Option.getVisualizerOn()) return;
        if (!isRecordAudioPermissionGranted(this)) {
            myLog("Visualizer ON but RECORD_AUDIO not granted");
            return;
        }
        try {
            frequencyVisualizerView.link_toto(audioService.getAudioSessionId());
            frequencyVisualizerView.setVisibility(View.VISIBLE);
        } catch (Exception e) {
            myLogEE(e, "runVisualizer");
        }
    }

    private void finishAndShowFatalError(String errMessage) {
        myLogEE(null, "finishAndShowFatalError() - start - [" + errMessage + "]");
        // 1) Tear down safely
        try {
            if (audioServiceBound) { // only unbind if bound
                unbindService(audioServiceConnection);
                audioServiceBound = false;
            }
        } catch (Throwable t) {
            myLogEE(t, "finishAndShowFatalError() - unbindService");
        }
        try {
            killTimerForDisplay();
        } catch (Throwable t) {
            myLogEE(t, "finishAndShowFatalError() - killTimerForDisplay()");
        }
        try {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(broadCastReceiver);
        } catch (Throwable t) {
            myLogEE(t, "finishAndShowFatalError() unregisterReceiver");
        }

        try {
            // 2) Build path details if available
            String pathText = null;
            String zikFilePath = null;

            PlayList pl = PlayList.getInstance();
            if (pl != null && pl.getZikFile() != null) {
                zikFilePath = pl.getZikFile().getPath();
                pathText = getString(R.string.source_file_path) + " = \n[" + Uri.decode(zikFilePath) + "]";

                boolean exists = FileHelper.exists(zikFilePath);

                if (errMessage==null || errMessage.isEmpty()) {
                    if (!exists) {
                        if (StorageHelper.isInInternalMemory(zikFilePath)) {
                            errMessage = getString(R.string.source_not_found);
                            myLogEE(null, "BAD BUG: file missing inside app private dir [" + zikFilePath + "]");
                        } else {
                            errMessage = getString(R.string.source_not_found_deleted);
                            myLogEE(null, getString(R.string.source_not_found_deleted));
                        }
                    } else {
                        if (StorageHelper.isInInternalMemory(zikFilePath)) {
                            // Should be readable without permissions
                            errMessage = getString(R.string.source_not_found);
                            myLogEE(null, "BAD BUG: file exists in private dir but not readable [" + zikFilePath + "]");
                        } else {
                            // External file case: if permission missing → offer Settings
                            if (!isReadAudioPermissionGranted(this)) {
                                errMessage = getString(R.string.permission_not_set);
                                myLogW(errMessage);

                                MsgBox.alertWithNeutral(
                                        this,
                                        getString(R.string.error_reading_track),
                                        errMessage,
                                        pathText,
                                        getString(R.string.settings),
                                        new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                                .setData(android.net.Uri.fromParts("package", getPackageName(), null))
                                );
                                myLogEE(null, "permission not set - [" + zikFilePath + "]");
                                finish();
                                return;
                            } else {
                                // Permission granted + file exists, but we still failed to read → generic not found
                                errMessage = getString(R.string.source_not_found);
                                myLogEE(null, "BAD BUG: file exists and permission granted [" + zikFilePath + "]");
                            }
                        }
                    }
                }
            } else {
                errMessage = getString(R.string.error_playlist_null);
                myLogEE(null, "finishAndShowFatalError() => error_playlist_null");
            }

            // 3) Default alert (no neutral)
            MsgBox.alert(
                    this,
                    getString(R.string.error_reading_track),
                    errMessage,
                    pathText
            );
            finish();

        } catch (Throwable t) {
            myLogEE(t, "finishAndShowFatalError() - general error - showing Toast");
            myToastEE(t, getString(R.string.error_reading_track));
            finish();
        }
    }



    private void handlePodcastClick(Podcast podcast) {
        long currentTime = System.currentTimeMillis();
        if (currentTime - PodcastLastClickTime < PODCAST_DOUBLE_CLICK_THRESHOLD) {
            startActivity(new Intent(this, PodcastEpisodeActivity.class).putExtra("podcast", podcast));
        }
        PodcastLastClickTime = currentTime;
    }

    private void showTtsUi() {
        ivCover.setVisibility(View.GONE);
        frequencyVisualizerView.setVisibility(View.GONE);
        ttsContainer.setVisibility(View.VISIBLE);

        // text
        String txt = audioService.getTtsText();
        if (txt == null) txt = "";
        int nl = 0; for (int i=0;i<txt.length();i++) if (txt.charAt(i)=='\n') nl++;
        myLogD("  NL count = " + nl);
        //myLogD("TTS text: " + txt);
        SpannableStringBuilder sb = new SpannableStringBuilder(txt);
        tvTtsText.setText(sb, TextView.BufferType.SPANNABLE);
        spannableText = (Spannable) tvTtsText.getText();


        // allow user scrolling
        tvTtsText.setMovementMethod(ScrollingMovementMethod.getInstance());

        // enable tap / long-press to seek
        setupTtsTextInteractions();
    }

    private void showAudioUi() {
        ttsContainer.setVisibility(View.GONE);
        ivCover.setVisibility(View.VISIBLE);
        runVisualizer();
    }

    private void scheduleTtsHighlight(int s, int e) {
        pendingStart = s;
        pendingEnd = e;
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

        // clear & set
        spannableText.removeSpan(ttsBgSpan);
        spannableText.removeSpan(ttsFgSpan);
        spannableText.setSpan(ttsBgSpan, s, e, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        spannableText.setSpan(ttsFgSpan, s, e, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

        // scroll the highlighted word into view
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
    private void applyTtsToggleUi() {
        if (!audioServiceBound || audioService == null) return;
        boolean ttsMode = audioService.isTtsMode();

        // Show the toggle button only in TTS mode
        btnToggleTtsView.setVisibility(ttsMode ? View.VISIBLE : View.GONE);

        if (!ttsMode) {
            myLogD("UI : audio Mode");
            // Audio mode: show visualizer/image as you already do
            ttsContainer.setVisibility(View.GONE);
            frequencyVisualizerView.setVisibility(View.VISIBLE);
            ivCover.setVisibility(ivCover.getDrawable() != null ? View.VISIBLE : View.GONE);
            runVisualizer();
        } else {
            myLogD("UI : TTS Mode");
            // TTS mode: never show the visualizer
            frequencyVisualizerView.setVisibility(View.GONE);
            if (showingTtsText) {
                ttsContainer.setVisibility(View.VISIBLE);
                ivCover.setVisibility(View.GONE);
                btnToggleTtsView.setImageResource(android.R.drawable.ic_menu_gallery); // next tap -> image
            } else {
                ttsContainer.setVisibility(View.GONE);
                ivCover.setVisibility(View.VISIBLE);
                btnToggleTtsView.setImageResource(android.R.drawable.ic_menu_edit); // next tap -> text
            }
        }
    }

    private void initTtsVoiceSpinner(int folderId) {
        myLogD("initTtsVoiceSpinner()");

        try { if (ttsHandle != null) ttsHandle.close(); } catch (Exception ignored) {}

        String thisBookVoice = Pref.getBookTtsVoiceName(this, folderId);
        if (thisBookVoice == null) {
            thisBookVoice = Option.getTtsVoice();
            myLogD("no saved book voice, using options default : " + thisBookVoice);
        } else {
            myLogD("saved book voice : " + thisBookVoice);
        }
        lastSavedTtsVoice = thisBookVoice;


        // Guards
        final boolean[] firstCallback = { true };          // ← skip the very first emission
        final boolean[] userTouched  = { false };          // ← only save after touch (optional but nice)

        // Mark when the user actually interacts with the spinner
        spinnerTtsVoice.setOnTouchListener((v, ev) -> {
            if (ev.getAction() == MotionEvent.ACTION_UP) {
                myLogI("--- user click VOICE spinner --- ");
                userTouched[0] = true;
                v.performClick();
            }
            return false;
        });

        ttsHandle = TtsHelper.setupTtsVoiceSpinner(
                this,
                spinnerTtsVoice,
                lastSavedTtsVoice,
                voice -> {
                    String sel = (voice == null || voice.name == null || voice.name.isEmpty())
                            ? "system" : voice.name;
                    myLog("--- spinner callback --- " + sel);

                    // 1) Ignore the initial programmatic selection
                    if (firstCallback[0]) {
                        firstCallback[0] = false;
                        myLogD("Ignoring initial spinner selection (no save/apply).");
                        return;
                    }

                    // 2) (Optional) Only react if user actually touched the spinner
                    if (!userTouched[0]) {
                        myLogD("Ignoring non-user selection.");
                        return;
                    }

                    if (!sel.equalsIgnoreCase(lastSavedTtsVoice)) {
                        Pref.setBookTtsVoiceName(this, folderId, sel);
                        lastSavedTtsVoice = sel;
                        myLog("TTS book voice set to: " + sel + " (" + (voice != null ? voice.displayName : "system") + ")");

                        if (audioServiceBound && audioService != null && audioService.isTtsMode() && voice != null) {
                            try {
                                bPlayPause.setEnabled(false);
                                audioService.setTtsVoiceByNameAndWarmUp(
                                        voice.name,
                                        5000L,
                                        (ready, reason) -> runOnUiThread(() -> {
                                            if (ready) {
                                                bPlayPause.setEnabled(true);
                                            } else {
                                                switch (reason) {
                                                    case TtsHelper.MISSING_DATA:
                                                        myLogW("TTS data missing for voice locale — prompt install.");
                                                        break;
                                                    case TtsHelper.TIMEOUT:
                                                        myLogW("TTS warm-up timed out. Check network or pick offline voice.");
                                                        break;
                                                    default:
                                                        myLogW("TTS not ready (reason " + reason + ").");
                                                }
                                            }
                                        })
                                );
                            } catch (Throwable ignored) {}
                        }
                    }
                }
        );
    }


    // CLICK DANS LE TEXTE
    private void setupTtsTextInteractions() {
        final GestureDetector detector =
                new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
                    @Override public boolean onSingleTapUp(MotionEvent e) {
                        myLogI("--- USER CLICKS IN THE TEXT ---");
                        handleTtsTap(e);
                        return true;
                    }
                });

        tvTtsText.setOnTouchListener((v, ev) -> {
            detector.onTouchEvent(ev);
            // return false so TextView still handles scrolling
            return false;
        });
    }

    private void handleTtsTap(MotionEvent e) {
        if (spannableText == null || tvTtsText.getLayout() == null) return;

        Layout layout = tvTtsText.getLayout();

        int x = (int) e.getX();
        int y = (int) e.getY();

        // adjust for TextView paddings and scroll
        x -= tvTtsText.getTotalPaddingLeft();
        y -= tvTtsText.getTotalPaddingTop();
        x += tvTtsText.getScrollX();
        y += tvTtsText.getScrollY();

        int line = layout.getLineForVertical(y);
        int off  = layout.getOffsetForHorizontal(line, x);
        off = Math.max(0, Math.min(off, spannableText.length()));

        // expand to word bounds
        int[] word = TtsHelper.findWordBounds(spannableText, off);
        int start = word[0], end = word[1];

        // highlight selection
        try {
            spannableText.removeSpan(ttsBgSpan);
            spannableText.removeSpan(ttsFgSpan);
            spannableText.setSpan(ttsBgSpan, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            spannableText.setSpan(ttsFgSpan, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        } catch (Throwable ignored) {}

        // center the selected line roughly
        tvTtsText.post(() -> {
            try {
                int lineSel = layout.getLineForOffset(start);
                int yTop = layout.getLineTop(lineSel);
                int targetY = Math.max(0, yTop - tvTtsText.getHeight() / 3);
                tvTtsText.scrollTo(0, targetY);
            } catch (Throwable ignored) {}
        });

        // tell the service to start from this position
        if (audioServiceBound && audioService != null && audioService.isTtsMode()) {
            audioService.setTtsStartOffsetChars(start);
            // if currently paused, you might want to auto-start:
            // audioService.playAudio();
        }
    }



}
