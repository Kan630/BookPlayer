package com.driot.bookplayer.settings.ui;

import android.Manifest;
import android.os.Bundle;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.checkbox.MaterialCheckBox;
import com.driot.bookplayer.R;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.utils.PermissionRequest;
import com.driot.bookplayer.utils.log.LoggingFragment;

import java.util.concurrent.Executors;

import static com.driot.bookplayer.utils.PermissionRequest.isRecordAudioPermissionGranted;

public class PlayBehaviourSettingsFragment extends LoggingFragment {

    // UI
    private EditText etTimeBeforeSleep;
    private EditText etForwardSeconds;

    private LinearLayout llVisualizerOn, llVisualizerPlayPause;
    private MaterialCheckBox chkVisualizerOn, chkClickVisualizerPlayPause;
    private TextView txVisualizerOn;

    private LinearLayout llRewindAfterPause, llStartNextTrackAtZero, llStopAudioOnClose, llAutoPlayOnMain, llOpenPlayActivity;
    private MaterialCheckBox chkRewindAfterPause, chkStartNextTrackAtZero, chkStopAudioIfUserClosesApp, chkAutoPlayOnMainPlayer, chkOpenPlayActivity;

    private LinearLayout llBeepChapter, llBeepAutostop, llBeepBookend;
    private MaterialCheckBox chkBeepChapter, chkBeepAutostop, chkBeepBookend;

    // Permission helper
    private PermissionRequest mPermissionRequest;

    // Ranges (kept here to mirror existing Activity behavior)
    private static final int MIN_TIME_BEFORE_SLEEP = 1;
    private static final int MAX_TIME_BEFORE_SLEEP = 60 * 24; // 1440

    private static final int MIN_FORWARD_SECONDS = 1;
    private static final int MAX_FORWARD_SECONDS = 300;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_play_behaviour_settings, container, false);

        // Optional local header
        boolean showLocalTitle = true;
        Bundle args = getArguments();
        if (args != null) showLocalTitle = args.getBoolean("ARG_SHOW_LOCAL_TITLE", true);
        View titleContainer = root.findViewById(R.id.ll_title);
        if (titleContainer != null) titleContainer.setVisibility(showLocalTitle ? View.VISIBLE : View.GONE);

        // ---- Bind numeric fields
        etTimeBeforeSleep  = root.findViewById(R.id.etTimeBeforeSleep);
        etForwardSeconds   = root.findViewById(R.id.etForwardSeconds);

        etTimeBeforeSleep.setText(String.valueOf(Option.getTimeBeforeSleep()));
        etForwardSeconds.setText(String.valueOf(Option.get_ForwardSeconds()));

        // ---- Visualizer
        llVisualizerOn              = root.findViewById(R.id.ll_visualizer_on);
        chkVisualizerOn             = root.findViewById(R.id.chk_visualizer_on);
        txVisualizerOn              = root.findViewById(R.id.tx_Visualizer_on);
        llVisualizerPlayPause       = root.findViewById(R.id.ll_visualizer_playpause);
        chkClickVisualizerPlayPause = root.findViewById(R.id.chk_click_visualizer_playpause);

        chkVisualizerOn.setChecked(Option.getVisualizerOn());
        llVisualizerOn.setOnClickListener(v -> chkVisualizerOn.toggle());
        chkVisualizerOn.setOnCheckedChangeListener((button, isChecked) -> {
            Option.setVisualizerOn(isChecked);
            if (isChecked && !isRecordAudioPermissionGranted(requireContext())) {
                requestRecordAudioPermission(root);
            }
            setVisualizerPermissionText();
        });

        chkClickVisualizerPlayPause.setChecked(Option.getClickVisualizerPlayPause());
        llVisualizerPlayPause.setOnClickListener(v -> chkClickVisualizerPlayPause.toggle());
        chkClickVisualizerPlayPause.setOnCheckedChangeListener((button, isChecked) ->
                Option.setClickVisualizerPlayPause(isChecked));

        setVisualizerPermissionText();

        // ---- Beeps
        llBeepChapter = root.findViewById(R.id.ll_beep_chapter);
        chkBeepChapter = root.findViewById(R.id.chk_beep_chapter);
        chkBeepChapter.setChecked(Option.getBeepChapter());
        llBeepChapter.setOnClickListener(v -> chkBeepChapter.toggle());
        chkBeepChapter.setOnCheckedChangeListener((b, isChecked) -> Option.setBeepChapter(isChecked));

        llBeepAutostop = root.findViewById(R.id.ll_beep_autostop);
        chkBeepAutostop = root.findViewById(R.id.chk_beep_autostop);
        chkBeepAutostop.setChecked(Option.getBeepAutoStop());
        llBeepAutostop.setOnClickListener(v -> chkBeepAutostop.toggle());
        chkBeepAutostop.setOnCheckedChangeListener((b, isChecked) -> Option.setBeepAutoStop(isChecked));

        llBeepBookend = root.findViewById(R.id.ll_beep_bookend);
        chkBeepBookend = root.findViewById(R.id.chk_beep_bookend);
        chkBeepBookend.setChecked(Option.getBeepBookEnd());
        llBeepBookend.setOnClickListener(v -> chkBeepBookend.toggle());
        chkBeepBookend.setOnCheckedChangeListener((b, isChecked) -> Option.setBeepBookEnd(isChecked));

        // ---- Play behaviour toggles
        llRewindAfterPause = root.findViewById(R.id.ll_rewind_after_pause);
        chkRewindAfterPause = root.findViewById(R.id.chk_rewind_after_pause);
        chkRewindAfterPause.setChecked(Option.getRewindAfterPause());
        llRewindAfterPause.setOnClickListener(v -> chkRewindAfterPause.toggle());
        chkRewindAfterPause.setOnCheckedChangeListener((b, isChecked) -> Option.setRewindAfterPause(isChecked));

        llStartNextTrackAtZero = root.findViewById(R.id.ll_start_next_track_at_zero);
        chkStartNextTrackAtZero = root.findViewById(R.id.chk_start_next_track_at_zero);
        chkStartNextTrackAtZero.setChecked(Option.getStartAtZeroNextTrack());
        llStartNextTrackAtZero.setOnClickListener(v -> chkStartNextTrackAtZero.toggle());
        chkStartNextTrackAtZero.setOnCheckedChangeListener((b, isChecked) -> Option.setStartAtZeroNextTrack(isChecked));

        llStopAudioOnClose = root.findViewById(R.id.ll_stop_audio_if_user_closes_app);
        chkStopAudioIfUserClosesApp = root.findViewById(R.id.chk_stop_audio_if_user_closes_app);
        chkStopAudioIfUserClosesApp.setChecked(Option.getStopAudioIfUserClosesApp());
        llStopAudioOnClose.setOnClickListener(v -> chkStopAudioIfUserClosesApp.toggle());
        chkStopAudioIfUserClosesApp.setOnCheckedChangeListener((b, isChecked) -> Option.setStopAudioIfUserClosesApp(isChecked));

        llAutoPlayOnMain = root.findViewById(R.id.ll_auto_play_on_main_player);
        chkAutoPlayOnMainPlayer = root.findViewById(R.id.chk_auto_play_on_main_player);
        chkAutoPlayOnMainPlayer.setChecked(Option.getAutoPlayOnMainPlayer());
        llAutoPlayOnMain.setOnClickListener(v -> chkAutoPlayOnMainPlayer.toggle());
        chkAutoPlayOnMainPlayer.setOnCheckedChangeListener((b, isChecked) -> Option.setAutoPlayOnMainPlayer(isChecked));

        llOpenPlayActivity = root.findViewById(R.id.ll_open_play_activity);
        chkOpenPlayActivity = root.findViewById(R.id.chk_open_play_activity);
        chkOpenPlayActivity.setChecked(Option.getOpenPlayActivity());
        llOpenPlayActivity.setOnClickListener(v -> chkOpenPlayActivity.toggle());
        chkOpenPlayActivity.setOnCheckedChangeListener((b, isChecked) -> Option.setOpenPlayActivity(isChecked));

        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
        setVisualizerPermissionText(); // refresh after coming back from Settings, etc.
    }

    @Override
    public void onPause() {
        super.onPause();
        saveEditTextValues();
    }

    private void saveEditTextValues() {
        Executors.newSingleThreadExecutor().execute(() -> {
            if (etTimeBeforeSleep != null) {
                int value = Option.clampInt(
                        requireContext(),
                        etTimeBeforeSleep,
                        MIN_TIME_BEFORE_SLEEP,
                        MAX_TIME_BEFORE_SLEEP,
                        Option.DEFAULT_TIME_BEFORE_SLEEP,
                        getString(R.string.option_timeBeforeSleep)
                );
                Option.setTimeBeforeSleep(value);
            }
            if (etForwardSeconds != null) {
                int value = Option.clampInt(
                        requireContext(),
                        etForwardSeconds,
                        MIN_FORWARD_SECONDS,
                        MAX_FORWARD_SECONDS,
                        Option.DEFAULT_FORWARD_SECONDS,
                        getString(R.string.option_backward_forward_title)
                );
                Option.set_ForwardSeconds(value);
            }
        });
    }

    private void setVisualizerPermissionText() {
        if (txVisualizerOn == null) return;
        String txt = getString(R.string.option_visualizer_text_01)
                + "<br><i>"
                + getString(R.string.option_visualizer_text_02);

        if (isRecordAudioPermissionGranted(requireContext())) {
            txt = txt + ": <font color='green'>" + getString(R.string.option_visualizer_permissions_granted) + "</font></i>";
        } else {
            txt = txt + ": <font color='red'>" + getString(R.string.option_visualizer_permissions_denied_01) + "</font><br>"
                    + getString(R.string.option_visualizer_permissions_denied_02) + "</i>";
        }
        txVisualizerOn.setText(Html.fromHtml(txt, Html.FROM_HTML_MODE_LEGACY));
    }

    private void requestRecordAudioPermission(View root) {
        mPermissionRequest = PermissionRequest
                .with(requireActivity())
                .permissions(Manifest.permission.RECORD_AUDIO)
                .rationale(R.string.permission_record_audio_rationale)
                .denied(R.string.permission_record_audio_denied)
                .snackbar((ViewGroup) root) // show feedback in fragment root
                .callback(new PermissionRequest.Callback() {
                    @Override public void onPermissionsGranted() {
                        myLog("RecordAudio Permission Granted");
                        setVisualizerPermissionText();
                    }
                    @Override public void onPermissionsDenied() {
                        myLog("RecordAudio Permission Denied");
                        setVisualizerPermissionText();
                    }
                })
                .submit();
    }
}
