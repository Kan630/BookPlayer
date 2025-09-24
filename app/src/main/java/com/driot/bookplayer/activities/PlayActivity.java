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
import android.os.IBinder;
import android.provider.Settings;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.driot.bookplayer.R;
import com.driot.bookplayer.db.Podcast;
import com.driot.bookplayer.db.ZikFile;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.global.Pref;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.helpers.FirebaseAnalyticsHelper;
import com.driot.bookplayer.helpers.InsetHelper;
import com.driot.bookplayer.helpers.TitleHelper;
import com.driot.bookplayer.helpers.TtsHelper;
import com.driot.bookplayer.objects.PlayList;
import com.driot.bookplayer.services.AudioService;
import com.driot.bookplayer.helpers.ViewHelper;
import com.driot.bookplayer.views.FrequencyVisualizerView;
import com.driot.bookplayer.utils.log.LoggingActivity;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;
import java.util.regex.Pattern;

import static com.driot.bookplayer.global.Var.SLEEP_PRESET_VALUES;
import static com.driot.bookplayer.services.AudioService.TIMER_VALUE;
import static com.driot.bookplayer.utils.PermissionRequest.isReadAudioPermissionGranted;
import static com.driot.bookplayer.utils.PermissionRequest.isRecordAudioPermissionGranted;
import static com.driot.bookplayer.utils.Tonio.FormatPercentStringForSpeed;
import static com.driot.bookplayer.utils.Tonio.formatTime;
import static com.driot.bookplayer.utils.Tonio.getReadableSize;

import androidx.activity.OnBackPressedCallback;
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
    private android.widget.ImageButton bPlayPause;
    List<View> buttonsToLock;
    private SeekBar seekbar;
    private TextView tvSeekBar, tvTotalTime, tvTitle, tvSubTitle, tvSpeed, tvListeningTime, tvTimeLeft;
    private View progressOverlay, messageOverlay;
    private FrequencyVisualizerView frequencyVisualizerView;
    private boolean AnimationNow;
    private Intent intentMusicService;
    private Timer timerRedrawUI;
    private String tvListeningTimeBaseText;
    private boolean forceReload;
    private boolean autoPlay;

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
            ,AudioService.NOTIFICATION_NEWTRACK //useless ?
            ,AudioService.NOTIFICATION_TTS_RANGE
            //,AudioService.NOTIFICATION_TTS_READY
            //,AudioService.NOTIFICATION_TTS_NEEDS_DOWNLOAD
    };

    private long PodcastLastClickTime = 0;
    private static final long PODCAST_DOUBLE_CLICK_THRESHOLD = 300;

    private ImageView imFolderImage;
    private View ttsContainer;
    private Spinner spinnerTtsVoice;
    private TextView tvTtsText;
    private ImageButton btnToggleTtsView;
    private boolean showingTtsText = true;

    private Spannable spannableText;
    private final BackgroundColorSpan ttsBgSpan = new BackgroundColorSpan(0x55FFFF00);
    private final ForegroundColorSpan ttsFgSpan = new ForegroundColorSpan(Color.BLACK);
    private int pendingStart = -1, pendingEnd = -1;
    private final android.os.Handler uiH = new android.os.Handler(android.os.Looper.getMainLooper());
    private boolean highlightScheduled = false;
    private AutoCloseable ttsHandle;
    private String lastSavedTtsVoice;

    /********************************************************************************
     ***       SERVICE
     ********************************************************************************
     */

    private final ServiceConnection audioServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            myLog("onServiceConnected");
            AudioService.BackgroundBinder binder = (AudioService.BackgroundBinder) service;
            audioService = binder.getService();
            audioServiceBound = true;

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
                    myToast(getString(R.string.error_reading_track));
                    lockButtonAndDisplayErrorMessage(err_msg);
                }

            } else if (Objects.equals(action, AudioService.NOTIFICATION_FILENOTFOUND)) {
                Toast.makeText(getApplicationContext(), getString(R.string.error_reading_track) + "\n" + getString(R.string.error_file_not_found), Toast.LENGTH_SHORT).show();
                lockButtonAndDisplayErrorMessage(null);

            } else if (Objects.equals(action, AudioService.NOTIFICATION_PLAYLISTFINISHED)) {
                Toast.makeText(getApplicationContext(), R.string.notification_playlist_finished, Toast.LENGTH_SHORT).show();
                finish();

            } else if (Objects.equals(action, AudioService.NOTIFICATION_PLAYBACK_MAXTIMEREACH)) {
                Toast.makeText(getApplicationContext(), R.string.notification_auto_sleep, Toast.LENGTH_SHORT).show();
                finish();

            } else if (Objects.equals(action, AudioService.NOTIFICATION_PLAYBACK_TIMER_VALUE)) {
                reDrawListeningSince(intent.getIntExtra(TIMER_VALUE,-999));

            } else if (Objects.equals(action, AudioService.READY_TO_PLAY)) {
                DrawUI();
                lockUserActions(false);
            } else if (Objects.equals(action, AudioService.NOTIFICATION_NEWTRACK) || Objects.equals(action, AudioService.NOTIFICATION_TRACKFINISHED)) {
                myLog("ok, nothing to do for this Broadcast");
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
            lockButtonAndDisplayErrorMessage(getString(R.string.error_playlist_null));
            myLogEE(null, "onCreate() -- cancelling since PlayList.getInstance() == null");
            return;
        }

        bPlayPause = findViewById(R.id.ibPlayPause);
        bPlayPause.setImageResource(R.drawable.ic_hourglass_24);
        bPlayPause.setEnabled(false);

        ImageButton bRewind, bForward;
        Button bSpeedUp, bSpeedDown, bSetSleep;
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

        buttonsToLock = Arrays.asList(bPlayPause, bRewind, bForward, bSpeedUp, bSpeedDown, bSetSleep);

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
        frequencyVisualizerView.setOnClickListener(v -> visualizerClick());

        imFolderImage = findViewById(R.id.folderImage);

        ttsContainer = findViewById(R.id.ttsContainer);
        spinnerTtsVoice = findViewById(R.id.spinnerTtsVoice);
        tvTtsText = findViewById(R.id.tvTtsText);
        btnToggleTtsView = findViewById(R.id.btnToggleTtsView);

        btnToggleTtsView.setOnClickListener(v -> {
            showingTtsText = !showingTtsText;
            applyTtsToggleUi();
        });

        playList.setOnMetaLoadedListener((folder, podcast, isPodcast) -> {
            // Voices
            if (folder.playType!=null && folder.playType.equals(Var.PLAY_TYPE_TEXT)) {
                initTtsVoiceSpinner(folder.getId());
            }

            // Playlist objects are all loaded
            if (folder.image != null && !folder.image.isEmpty()) {
                imFolderImage.setImageURI(Uri.parse(folder.image));
                imFolderImage.setVisibility(View.VISIBLE);
                frequencyVisualizerView.setAlpha(0.6f);
                try {
                    File imageFile = new File(folder.image);
                    myLogD("Image found : " + imageFile.getName() + " - " + getReadableSize(imageFile.length()));
                } catch (Exception e) {
                    myLogE("image debug ko");
                }
                if (isPodcast) {
                    myLogD("Is Podcast");
                    tvTitle.setOnClickListener(v -> {
                        myLogI("user clicks Title");
                        handlePodcastClick(podcast);
                    });
                    tvSubTitle.setOnClickListener(v -> {
                        myLogI("user clicks subTitle");
                        handlePodcastClick(podcast);
                    });
                }
            } else {
                imFolderImage.setVisibility(View.GONE);
                frequencyVisualizerView.setAlpha(1f); // fully opaque
            }
            imFolderImage.setOnClickListener(new View.OnClickListener() {
                private static final long DOUBLE_CLICK_TIME_DELTA = 300; // milliseconds
                private long lastClickTime = 0;

                @Override
                public void onClick(View v) {
                    long clickTime = System.currentTimeMillis();
                    if (clickTime - lastClickTime < DOUBLE_CLICK_TIME_DELTA) {
                        myLogI("--- USER dbl Click IMAGE ---");
                        handleDoubleClick(v);
                    } else {
                        myLogI("--- USER Click IMAGE ---");
                        handleSingleClick(v);
                    }
                    lastClickTime = clickTime;
                }

                private void handleSingleClick(View v) {
                    // Your single click action
                }

                private void handleDoubleClick(View v) {
                    // Your double click action
                    if (!audioServiceBound || audioService == null) return;
                    boolean ttsMode = audioService.isTtsMode();
                    if (ttsMode) {
                        showingTtsText = !showingTtsText;
                        applyTtsToggleUi();
                    }
                }
            });

        });

        myLogD("onCreate() -- Launching Music Service");
        launchService();

        // Check if progress bar is at the end and reset if necessary
        if (playList.getZikFile() != null && playList.getZikFile().getPosition() >= playList.getZikFile().getDuration()) {
            playList.getZikFile().setPosition(0);
            tvSeekBar.setText(formatTime(0, true));
            seekbar.setProgress(0);
        }

        //-*******************************************************************************
        //-***       SEEKBAR
        //-*******************************************************************************

        seekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    myLogI("--- USER CLICK SEEK BAR ---- => Change Progress");
                    audioService.setPosition(progress);
                    tvSeekBar.setText(formatTime(progress,true));
                }
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                myLogI("--- USER CLICK BACK --- (system button)");
                finish();
            }
        });
    }

    private void launchService() {
        myLogD("launchService");
        intentMusicService = new Intent(PlayActivity.this, AudioService.class);
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
        String txt = FormatPercentStringForSpeed((double) speed * 100);
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
            myToastEE(null, "DrawUI() -- cancelling since PlayList.getInstance() == null");
            finish();
        } else if (playList.getZikFile() == null) {
            myToastEE(null,"DrawUI() => Cannot get Playlist - PlayList.getInstance().getZikFile() is null");
        } else {
            try {
                ZikFile zf = playList.getZikFile();
                myLogD("DrawUI : " + zf.getName() + " -- " + zf.getPosition() + " -- " + zf.getDisplayName());
                TitleHelper.setTitleAndSubtitle(tvTitle, tvSubTitle, zf.getFolderName(), zf.getDisplayName());
                tvTotalTime.setText(formatTime(zf.getDuration(), true));
                seekbar.setMax((int) zf.getDuration());
                tvSeekBar.setText(formatTime(zf.getPosition(), true));
                seekbar.setProgress((int) zf.getPosition());
                tvSpeed.setText(FormatPercentStringForSpeed(audioService.getSpeed() * 100));
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
                zeText_since = str + " " + formatTime(tempsEcoule*1000,true);
                zeText_left = getString(R.string.tv_TimeLeft) + " : " + formatTime(timeBeforeSleep*1000*60-tempsEcoule*1000,true);
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

    private void runTimerForDisplay() {
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
                tvSeekBar.setText(formatTime(iPosition, true));
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


    private void lockUserActions(boolean doLock) {
        for (View b : buttonsToLock) {
            b.setEnabled(!doLock);
        }
        frequencyVisualizerView.setEnabled(!doLock);
        seekbar.setEnabled(!doLock);
        if (doLock) {
            findViewById(R.id.dim_background).setVisibility(View.VISIBLE);
            ShowMessageOverlay();
        } else {
            findViewById(R.id.dim_background).setVisibility(View.GONE);
            HideMessageOverlay();
        }

    }

    private void lockButtonAndDisplayErrorMessage(String errMessage) {
        myLog("lockButtonAndDisplayErrorMessage");
        LocalBroadcastManager.getInstance(this).unregisterReceiver(broadCastReceiver);
        lockUserActions(true);
        TextView tv = findViewById(R.id.textViewOverlaidMessage);
        TextView tv2 = findViewById(R.id.textViewOverlaidMessageDetails);
        Button b1 = findViewById(R.id.btOverlaid01);
        tv.setText("The source file could not be found or read.\n"); // in case bug later
        try {
            if (errMessage != null) {
                tv.setText(errMessage);
                myLogEE(null,errMessage);
            } else {
                String zePath;
                if (PlayList.getInstance()==null) {
                    myLogEE(null, "lockButtonAndDisplayErrorMessage PlayList_getInstance() == null");
                    zePath = "***";
                } else {
                    if (PlayList.getInstance().getZikFile() == null) {
                        myLogEE(null, "lockButtonAndDisplayErrorMessage PlayList_getInstance()_getZikFile() == null");
                        zePath = "***";
                    } else {
                        zePath = PlayList.getInstance().getZikFile().getPath();
                    }
                }
                String pathText = getString(R.string.source_file_path) + " = \n[" + zePath + "]";
                if (zePath.contains(Var.PATH_CHECK_AUDIO_FILE_INTERNAL)) {
                    tv.setText(getString(R.string.source_not_found));
                    myLog("Source file is inside app memory");
                } else if (isReadAudioPermissionGranted(this)) {
                    tv.setText(getString(R.string.source_not_found_deleted));
                    tv2.setText(pathText);
                } else {
                    tv.setText(getString(R.string.permission_not_set));
                    String msg = getString(R.string.permission_to_set) + "\n" + pathText;
                    tv2.setText(msg);
                    b1.setVisibility(View.VISIBLE);
                    b1.setText(getString(R.string.device_settings));
                    b1.setOnClickListener(v -> openAppSettingsOnPhone());
                }
            }
        } catch (Exception e) {
            myLogEE(e,"lockButtonAndDisplayErrorMessage");
        }
        try {
            unbindService(audioServiceConnection);
        } catch (Exception e) {
            myLogEE(e, "unbindService(audioServiceConnection)");
        }
        killTimerForDisplay();
    }

    private void openAppSettingsOnPhone() {
        myLog("openAppSettingsOnPhone()");
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            Uri uri = Uri.fromParts("package", getPackageName(), null);
            intent.setData(uri);
            startActivity(intent);
        } catch (Exception e) {
            myLogEE(e,"openAppSettingsOnPhone()");
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
        imFolderImage.setVisibility(View.GONE);
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
        tvTtsText.setMovementMethod(android.text.method.ScrollingMovementMethod.getInstance());

        // enable tap / long-press to seek
        setupTtsTextInteractions();
    }

    private void showAudioUi() {
        ttsContainer.setVisibility(View.GONE);
        imFolderImage.setVisibility(View.VISIBLE);
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
                android.text.Layout layout = tvTtsText.getLayout();
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
            imFolderImage.setVisibility(imFolderImage.getDrawable() != null ? View.VISIBLE : View.GONE);
            runVisualizer();
        } else {
            myLogD("UI : TTS Mode");
            // TTS mode: never show the visualizer
            frequencyVisualizerView.setVisibility(View.GONE);
            if (showingTtsText) {
                ttsContainer.setVisibility(View.VISIBLE);
                imFolderImage.setVisibility(View.GONE);
                btnToggleTtsView.setImageResource(android.R.drawable.ic_menu_gallery); // next tap -> image
            } else {
                ttsContainer.setVisibility(View.GONE);
                imFolderImage.setVisibility(View.VISIBLE);
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
            if (ev.getAction() == android.view.MotionEvent.ACTION_UP) {
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
        final android.view.GestureDetector detector =
                new android.view.GestureDetector(this, new android.view.GestureDetector.SimpleOnGestureListener() {
                    @Override public boolean onSingleTapUp(android.view.MotionEvent e) {
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

    private void handleTtsTap(android.view.MotionEvent e) {
        if (spannableText == null || tvTtsText.getLayout() == null) return;

        android.text.Layout layout = tvTtsText.getLayout();

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
