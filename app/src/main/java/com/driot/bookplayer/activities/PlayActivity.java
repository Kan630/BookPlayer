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
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.driot.bookplayer.R;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.objects.PlayList;
import com.driot.bookplayer.services.AudioService;
import com.driot.bookplayer.utils.FrequencyVisualizerView;
import com.driot.bookplayer.utils.log.LoggingActivity;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;

import static com.driot.bookplayer.global.Var.PATH_CHECK_APPLICATION;
import static com.driot.bookplayer.global.Var.SLEEP_PRESET_VALUES;
import static com.driot.bookplayer.services.AudioService.NOTIFICATION_AUDIOFOCUS_GAIN;
import static com.driot.bookplayer.services.AudioService.NOTIFICATION_AUDIOFOCUS_LOST;
import static com.driot.bookplayer.services.AudioService.NOTIFICATION_FILENOTFOUND;
import static com.driot.bookplayer.services.AudioService.NOTIFICATION_ERROR;
import static com.driot.bookplayer.services.AudioService.NOTIFICATION_FILELOADED;
import static com.driot.bookplayer.services.AudioService.NOTIFICATION_NEWTRACK;
import static com.driot.bookplayer.services.AudioService.NOTIFICATION_PLAYBACK_MAXTIMEREACH;
import static com.driot.bookplayer.services.AudioService.NOTIFICATION_PLAYBACK_TIMER_VALUE;
import static com.driot.bookplayer.services.AudioService.NOTIFICATION_PLAYLISTFINISHED;
import static com.driot.bookplayer.services.AudioService.NOTIFICATION_TRACKFINISHED;
import static com.driot.bookplayer.services.AudioService.NOTIFICATION_ZIP_FILE_LOADED;
import static com.driot.bookplayer.services.AudioService.TIMER_VALUE;
import static com.driot.bookplayer.utils.PermissionRequest.isReadAudioPermissionGranted;
import static com.driot.bookplayer.utils.PermissionRequest.isRecordAudioPermissionGranted;
import static com.driot.bookplayer.utils.Tonio.formatNameForDisplay;
import static com.driot.bookplayer.utils.Tonio.FormatPercentStringForSpeed;
import static com.driot.bookplayer.utils.Tonio.formatTime;
import static com.driot.bookplayer.utils.Utils.animateView;

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

    public static final String SHARED_PREFERENCE_SPEED="SHARED_PREFERENCE_SPEED";
    private static final int INTERVAL_REDRAW_SEEKBAR = 500; //  because looks like it happens erratically when choosing value of 100 for this constant
    private static final int DELAY_ANIMATION = 200;
    private static final float INCREMENT_SPEED = 0.05f;
    AudioService audioService;
    boolean audioServiceBound = false;
    private Button bPlay;
    List<Button> buttonsToLock;
    private SeekBar seekbar;
    private TextView tvSeekBar, tvTotalTime, tvTitle, tvSubTitle, tvSpeed, tvListeningTime, tvTimeLeft;
    private View progressOverlay, messageOverlay;
    private FrequencyVisualizerView frequencyVisualizerView;
    private boolean AnimationNow;
    private boolean HasBeenInitializedService = false;
    private Intent intentMusicService;
    private Timer timerRedrawUI;
    private String tvListeningTimeBaseText;

    String[] broadcastNotifications = {
            NOTIFICATION_TRACKFINISHED //useless ?
            ,NOTIFICATION_AUDIOFOCUS_GAIN //useless ?
            ,NOTIFICATION_AUDIOFOCUS_LOST //useless ?
            ,NOTIFICATION_FILELOADED
            ,NOTIFICATION_ERROR
            ,NOTIFICATION_ZIP_FILE_LOADED //useless ?
            ,NOTIFICATION_PLAYLISTFINISHED
            ,NOTIFICATION_PLAYBACK_MAXTIMEREACH
            ,NOTIFICATION_PLAYBACK_TIMER_VALUE
            ,NOTIFICATION_FILENOTFOUND
            ,NOTIFICATION_NEWTRACK //useless ?
    };


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

            // Get PlayList
            if (!HasBeenInitializedService) {
                if (!(audioService.isPlaying())) {
                    loadPlayListIntoService();
                }
            }
            HasBeenInitializedService = true;

            // retour de flip ecran
            myLog("onServiceConnected - DrawUI");
            DrawUI(); //utile pour suppression progressBar
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
            if (!Objects.equals(action, NOTIFICATION_PLAYBACK_TIMER_VALUE)) myLog("broadcast received : [" + action + "]");

            if (Objects.equals(action, NOTIFICATION_ERROR)) {
                Toast.makeText(getApplicationContext(), getString(R.string.error_reading_track), Toast.LENGTH_SHORT).show();
                finish();

            } else if (Objects.equals(action, NOTIFICATION_FILENOTFOUND)) {
                Toast.makeText(getApplicationContext(), getString(R.string.error_reading_track) + "\n" + getString(R.string.error_file_not_found), Toast.LENGTH_SHORT).show();
                lockButtonAndDisplayErrorMessage(null);

            } else if (Objects.equals(action, NOTIFICATION_PLAYLISTFINISHED)) {
                Toast.makeText(getApplicationContext(), R.string.notification_playlist_finished, Toast.LENGTH_SHORT).show();
                finish();

            } else if (Objects.equals(action, NOTIFICATION_PLAYBACK_MAXTIMEREACH)) {
                Toast.makeText(getApplicationContext(), R.string.notification_auto_sleep, Toast.LENGTH_SHORT).show();
                finish();

            } else if (Objects.equals(action, NOTIFICATION_PLAYBACK_TIMER_VALUE)) {
                reDrawListeningSince(intent.getIntExtra(TIMER_VALUE,-999));

            } else if (Objects.equals(action, NOTIFICATION_FILELOADED)) {
                DrawUI();
                lockUserActions(false);
            } else if (Objects.equals(action, NOTIFICATION_NEWTRACK) || Objects.equals(action, NOTIFICATION_TRACKFINISHED)) {
                myLog("ok, nothing to do for this Broadcast");
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

        if (Option.getScreenOrientationLock()) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);
        }

        setContentView(R.layout.activity_play);

        bPlay = findViewById(R.id.buttonPlay);

        Button bRewind, bForward, bSpeedUp, bSpeedDown, bSetSleep;
        bRewind = findViewById(R.id.buttonRewind);
        bForward = findViewById(R.id.buttonForward);
        bSpeedUp = findViewById(R.id.bSpeedUp);
        bSpeedDown = findViewById(R.id.bSpeedDown);
        bSetSleep = findViewById(R.id.bSetSleep);

        bPlay.setOnClickListener(v -> playMe());
        bForward.setOnClickListener(v -> forwardMe());
        bRewind.setOnClickListener(v -> backwardMe());
        bSpeedUp.setOnClickListener(v -> SpeedMeUp());
        bSpeedDown.setOnClickListener(v -> SpeedMeDown());
        bSetSleep.setOnClickListener(v -> setSleep());

        buttonsToLock = Arrays.asList(bPlay, bRewind, bForward, bSpeedUp, bSpeedDown, bSetSleep);

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

        myLog("onCreate() -- Launching Music Service");
        launchService();

        // Check if progress bar is at the end and reset if necessary
        if (PlayList.getInstance().getZikFile() != null && PlayList.getInstance().getZikFile().getPosition() >= PlayList.getInstance().getZikFile().getDuration()) {
            PlayList.getInstance().getZikFile().setPosition(0);
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
                    myLog("SeekBar");
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
                myLog("onBackPressed() -- (should be user action)");
                if (audioService.isPlaying()) {
                    playMe();
                }
                //PlayList.getInstance().clear();
                if (audioServiceBound) {
                    try {
                        myLog("unbinding service - unregistering receiver");
                        unbindService(audioServiceConnection);
                        LocalBroadcastManager.getInstance(PlayActivity.this).unregisterReceiver(broadCastReceiver);
                        stopService(intentMusicService);
                    } catch (Exception e) {
                        myLogEE(e,"onBackPressed() - " + e.getMessage());
                    }
                }
                finish();
            }
        });
    }

    private void launchService() {
        myLog("launchService");
        intentMusicService = new Intent(PlayActivity.this, AudioService.class);
        //TODO when flip screen the second time, service is destroyed....
        startService(intentMusicService);
/*
        if (isServiceRunning(AudioService.class)) {
            myLog("Starting Service");
            startService(intentMusicService);
        }
 */
        /*
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) { //min SDK 26
            startForegroundService(intentMusicService);
        } else {
            startService(intentMusicService);
        }

         */

        audioServiceBound = false;
        try {
            audioServiceBound = bindService(intentMusicService, audioServiceConnection, Context.BIND_AUTO_CREATE); //TODO leaked ServiceConnection if user press back
        } catch (Exception e) {
            myLogEE(e,"ERROR bindService");
            myLogEE(e,e.getMessage());
        }
        myLog("call start & bind to AudioService in onCreate() - bound result :" + audioServiceBound);
    }

    private void visualizerClick() {
        myLog("visualizerClick()");
        if (Option.getClickVisualizerPlayPause()) {
            playMe();
        }
    }

    private void playMe() {
        if (audioServiceBound) {
            if (audioService != null && audioService.exist()) {
                if (audioService.isPlaying()) {
                    /////////   PAUSE
                    myLog("PlayMe() => pause");
                    audioService.pauseAudio();
                    tvListeningTimeBaseText = getString(R.string.tv_ListeningTimeWithNoUserAction);
                    tvListeningTime.setText("");
                    tvTimeLeft.setText("");
                    //reDrawListeningSince(0);
                    /////// PLAY
                } else {
                    myLog("PlayMe() => play");
                    audioService.playAudio();
                    runVisualizer();
                }
            } else {
                myLogEE(null,"playMe() => mService KO");
            }
        } else {
            myLogEE(null,"playMe() => mBound False");
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
        builder.setPositiveButton("Set", setSleepAction);
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

        // Show the dialog
        AlertDialog dialog = builder.create();
        dialog.show();

        // Array of buttons
        Button[] presetButtons = {btnPreset1, btnPreset2, btnPreset3, btnPreset4, btnPreset5, btnPreset6};

        // Set labels for buttons based on PRESET_VALUES and set their onClick listeners
        for (int i = 0; i < SLEEP_PRESET_VALUES.length; i++) {
            final int presetValue = SLEEP_PRESET_VALUES[i];
            presetButtons[i].setText(presetValue + " min");

            // Set button click listener
            presetButtons[i].setOnClickListener(v -> {
                inputMinutes.setText(String.valueOf(presetValue));
                setSleepAction.onClick(dialog, DialogInterface.BUTTON_POSITIVE);
                dialog.cancel();
            });
        }


        // Request focus for the EditText
        inputMinutes.post(() -> inputMinutes.requestFocus());
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

        myLog("onResume() - creating new timer for Display");
        runTimerForDisplay();
        myLog("onResume() - bind to service");
        audioServiceBound = bindService(intentMusicService, audioServiceConnection, Context.BIND_AUTO_CREATE);
        super.onResume();
    }

    @Override
    protected void onDestroy() {
        myLog("onDestroy - unregister Broadcast Receiver");
//should let the system continue playing, even if activity is destroyed, by let's say, phone os battery saver routine -- also flip screen...
/*
        if (audioServiceBound) {
            try {
                unbindService(audioServiceConnection);
            } catch (Exception e) {
                myLogEE(e,"onDestroy() - unbindService - " + e.getMessage());
            }
        }

 */
        try {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(broadCastReceiver);
        } catch (Exception e) {
            myLogEE(e,"onDestroy() - unregisterReceiver - " + e.getMessage());
        }

        super.onDestroy();
    }

    @Override
    protected void onPause() {
        myLog("onPause() - killing display timer");
        killTimerForDisplay();
        //unbindService(audioServiceConnection);
        ////// SURTOUT PAS !!!
        // c'est l'ecran qui s'eteint.. ca call onPause, on UnBind null, on stop, puis 1min apres ca call on Destroy et plus de son
        // du coup, si on reste bind, on passe pas par destroy...
        super.onPause();
    }

    @Override
    protected void onStop() {
        myLog("onStop()");
        super.onStop();
    }

    /********************************************************************************
     ***       GET FROM DB
     ********************************************************************************
     */
    private void loadPlayListIntoService() {
        if (PlayList.getInstance().getZikFile() == null) {
            myToastE("Cannot get Playlist - PlayList.getInstance().getZikFile() is null");
            lockButtonAndDisplayErrorMessage("Cannot get Playlist - PlayList.getInstance().getZikFile() is null");
            return;
        }
        myLog("+++++++++ loading PlayList Into Service - GetZikFiles - Folder : " + PlayList.getInstance().getZikFile().getIdFolder());
        new Thread(() -> {
            try {
                audioService.directPlay = false;
                audioService.startAtZero = false;
                audioService.loadFile();
                //ZikFile[] zikFiles = AppDatabase.getDatabase(this).ZikFileDao().getNextZikFiles(PlayList.getInstance().getZikFile().getIdFolder(), PlayList.getInstance().getZikFile().getName());
                //audioService.loadFiles(zikFiles);

            } catch (Exception e) {
                myToastE("Error Loading playlist");
                myLogEE(e,"Error Loading playlist :" + e.getMessage());
            }
        }).start();
    }

    private void DrawUI() {
        if (PlayList.getInstance().getZikFile() == null) {
            myToastE("Cannot get Playlist - PlayList.getInstance().getZikFile() is null");
        }
        try {
            myLog("DrawUI : " + PlayList.getInstance().getZikFile().getName() + " -- " + PlayList.getInstance().getZikFile().getPosition());
            tvSubTitle.setText(formatNameForDisplay(PlayList.getInstance().getZikFile().getName()));
            tvTitle.setText(PlayList.getInstance().getZikFile().getFolderName());
            tvTotalTime.setText(formatTime(PlayList.getInstance().getZikFile().getDuration(),true));
            seekbar.setMax((int) PlayList.getInstance().getZikFile().getDuration());
            tvSeekBar.setText(formatTime(PlayList.getInstance().getZikFile().getPosition(),true));
            seekbar.setProgress((int) PlayList.getInstance().getZikFile().getPosition());
            tvSpeed.setText(FormatPercentStringForSpeed( audioService.getSpeed() * 100));
            myLog("----------------------------- play screen drawn " + PlayList.getInstance().getZikFile().getPosition());
        } catch (Exception e) {
            myLogEE(e,":----------------------------- play screen drawn ERROR");
            myLogEE(e,e.getMessage());
        }
    }
    private void reDrawListeningSince(int tempsEcoule) { // le call vient d'1 timer dans le service...
        try {
            String zeText_since;
            String zeText_left;
            int timeBeforeSleep = audioService.getCustomSleepTime() == 0 ? Option.getTimeBeforeSleep() : audioService.getCustomSleepTime();
            if (tempsEcoule >= 0) {
                //String str = audioService.getCustomSleepTime() == 0 ? getString(R.string.tv_ListeningTimeWithNoUserAction) : getString(R.string.tv_ListeningTimeWithCustomSleep);
                String str = tvListeningTimeBaseText;
                zeText_since = str + " " + formatTime(tempsEcoule*1000,true);
                zeText_left = getString(R.string.tv_TimeLeft) + " : " + formatTime(timeBeforeSleep*1000*60-tempsEcoule*1000,true);
                tvTimeLeft.setText(zeText_left);
                if (tempsEcoule>0) { tvListeningTime.setText(zeText_since); }
            } else {
                tvListeningTime.setText("");
                tvTimeLeft.setText("");
            }
        } catch (Exception e) {
            myLogEE(e,"reDrawListeningSince(" + tempsEcoule + ") - " + e.getMessage());
            myLogEE(e,e.getMessage());
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
        if (audioService != null && audioService.exist()) {
            if (audioService.isPlaying()) {
                bPlay.setText(R.string.pause);
            } else {
                bPlay.setText(R.string.play);
            }
            int iPosition = audioService.getPosition();
            tvSeekBar.setText(formatTime(iPosition,true));
            seekbar.setProgress(iPosition);

        } else {
            bPlay.setText(R.string.pause);
            myLog("redrawSeekBar => service KO => drawing pause button");
        }
    }

    /********************************************************************************
     ***       DIVERS FONCTIONS
     ********************************************************************************
     */


    private void ShowMessageOverlay() {
        animateView(messageOverlay, View.VISIBLE, 1f, DELAY_ANIMATION);
        AnimationNow = true;
    }
    private void HideMessageOverlay() {
        animateView(messageOverlay, View.GONE, 1f, DELAY_ANIMATION);
        AnimationNow = true;
    }

    private void runVisualizer() { // check option + permission
        if (Option.getVisualizerOn()) {
            if (isRecordAudioPermissionGranted(this)) {
                try {
                    //frequencyVisualizerView.setEnabled(false);
                    frequencyVisualizerView.link_toto(audioService.getAudioSessionId());
                } catch (Exception e) {
                    myLogEE(e,"runVisualizer - " + e.getMessage());
                }
            } else {
                myLog("frequencyVisualizerView is On, but permission is not granted");
            }
        }
    }

    private void lockUserActions(boolean doLock) {
        for (Button b : buttonsToLock) {
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
                //b1.setVisibility(View.INVISIBLE);
                String zePath = PlayList.getInstance().getZikFile()==null ? "PlayList.getInstance().getZikFile()==null" : PlayList.getInstance().getZikFile().getPath();
                String pathText = getString(R.string.source_file_path) + " = \n[" + zePath + "]";
                if (zePath.contains(PATH_CHECK_APPLICATION)) {
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
            myLogEE(e,"lockButtonAndDisplayErrorMessage - " + e.getMessage());
        }
        unbindService(audioServiceConnection);
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
            myLogEE(e,"openAppSettingsOnPhone() => " + e.getMessage());
        }
    }

}
