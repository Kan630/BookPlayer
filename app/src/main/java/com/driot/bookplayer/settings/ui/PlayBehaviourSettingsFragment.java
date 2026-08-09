package com.driot.bookplayer.settings.ui;

import android.Manifest;
import android.os.Bundle;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.driot.bookplayer.global.Var;
import com.google.android.material.button.MaterialButtonToggleGroup;
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
    private EditText etScreensaverDelay;

    private LinearLayout llVisualizerOn, llClickMainContainerPlayPause;
    private MaterialCheckBox chkVisualizerOn, chkClickMainContainerPlayPause;
    private TextView txVisualizerOn;
    private TextView tvScreensaverPermission;

    private LinearLayout llAutoPlayNextChapter;
    private MaterialCheckBox chkAutoPlayNextChapter;

    private LinearLayout llRewindAfterPause, llStartNextTrackAtZero, llStopAudioOnClose, llOpenPlayActivity;
    private MaterialCheckBox chkRewindAfterPause, chkStartNextTrackAtZero, chkStopAudioIfUserClosesApp,
            chkOpenPlayActivity;

    private LinearLayout llBeepChapter, llBeepAutostop, llBeepBookend;
    private MaterialCheckBox chkBeepChapter, chkBeepAutostop, chkBeepBookend;

    private LinearLayout llScreensaverOn, llScreensaverDelay;
    private MaterialCheckBox chkScreensaverOn;

    // Permission helper
    private PermissionRequest mPermissionRequest;

    // Ranges (kept here to mirror existing Activity behavior)

    private static final int MIN_FORWARD_SECONDS = 1;
    private static final int MAX_FORWARD_SECONDS = 300;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_settings_play_behaviour, container, false);

        // Optional local header
        boolean showLocalTitle = true;
        Bundle args = getArguments();
        if (args != null)
            showLocalTitle = args.getBoolean("ARG_SHOW_LOCAL_TITLE", true);
        View titleContainer = root.findViewById(R.id.ll_title);
        if (titleContainer != null)
            titleContainer.setVisibility(showLocalTitle ? View.VISIBLE : View.GONE);

        // ---- Auto-play next chapter
        llAutoPlayNextChapter = root.findViewById(R.id.ll_autoplay_next_chapter);
        chkAutoPlayNextChapter = root.findViewById(R.id.chk_autoplay_next_chapter);
        chkAutoPlayNextChapter.setChecked(Option.getAutoPlayNextChapter());
        llAutoPlayNextChapter.setOnClickListener(v -> chkAutoPlayNextChapter.toggle());
        chkAutoPlayNextChapter.setOnCheckedChangeListener((b, isChecked) -> Option.setAutoPlayNextChapter(isChecked));

        // ---- Bind numeric fields
        etTimeBeforeSleep = root.findViewById(R.id.etTimeBeforeSleep);
        etForwardSeconds = root.findViewById(R.id.etForwardSeconds);

        etTimeBeforeSleep.setText(String.valueOf(Option.getTimeBeforeSleep()));
        etForwardSeconds.setText(String.valueOf(Option.get_ForwardSeconds()));

        // HEAT MAPS
        CheckBox chk_use_heatmap_for_tracks_activity;
        LinearLayout ll_use_heatmap_for_tracks_activity;
        chk_use_heatmap_for_tracks_activity = root.findViewById(R.id.chk_use_heatmap_for_tracks_activity);
        ll_use_heatmap_for_tracks_activity = root.findViewById(R.id.ll_use_heatmap_for_tracks_activity);
        chk_use_heatmap_for_tracks_activity.setChecked(Option.getUseHeatmapForTracksActivity());
        ll_use_heatmap_for_tracks_activity.setOnClickListener(v -> chk_use_heatmap_for_tracks_activity.toggle());
        chk_use_heatmap_for_tracks_activity.setOnCheckedChangeListener(
                (buttonView, isChecked) -> Option.setUseHeatmapForTracksActivity(isChecked));

        // ---- Visualizer
        llVisualizerOn = root.findViewById(R.id.ll_visualizer_on);
        chkVisualizerOn = root.findViewById(R.id.chk_visualizer_on);
        txVisualizerOn = root.findViewById(R.id.tx_Visualizer_on);
        llClickMainContainerPlayPause = root.findViewById(R.id.ll_visualizer_playpause);
        chkClickMainContainerPlayPause = root.findViewById(R.id.chk_click_visualizer_playpause);

        chkVisualizerOn.setChecked(Option.getVisualizerOn());
        llVisualizerOn.setOnClickListener(v -> chkVisualizerOn.toggle());
        chkVisualizerOn.setOnCheckedChangeListener((button, isChecked) -> {
            Option.setVisualizerOn(isChecked);
            if (isChecked && !isRecordAudioPermissionGranted(requireContext())) {
                requestRecordAudioPermission(root);
            }
            setVisualizerPermissionText();
            int vis = isChecked ? View.VISIBLE : View.GONE;
            root.findViewById(R.id.tv_visualizer_beta_note).setVisibility(vis);
            root.findViewById(R.id.groupVisualizerMode).setVisibility(vis);
        });

        MaterialButtonToggleGroup group = root.findViewById(R.id.groupVisualizerMode);
        View tvVisualizerBetaNote = root.findViewById(R.id.tv_visualizer_beta_note);

        boolean visualizerOn = Option.getVisualizerOn();
        int vis = visualizerOn ? View.VISIBLE : View.GONE;
        tvVisualizerBetaNote.setVisibility(vis);
        group.setVisibility(vis);

        String type = Option.getVisualizerType();
        int checkedId = switch (type) {
            case Var.VISUALIZER_TYPE_BARS -> R.id.btnVisualizerBars;
            case Var.VISUALIZER_TYPE_RADIAL -> R.id.btnVisualizerRadial;
            case Var.VISUALIZER_TYPE_WAVE -> R.id.btnVisualizerWave;
            default -> R.id.btnVisualizerLegacy;
        };
        group.check(checkedId); // ← this actually makes one button look "pressed"

        group.addOnButtonCheckedListener((g, checkedId2, isChecked) -> {
            if (!isChecked)
                return;
            if (checkedId2 == R.id.btnVisualizerBars) {
                Option.setVisualizerType(Var.VISUALIZER_TYPE_BARS);
            } else if (checkedId2 == R.id.btnVisualizerRadial) {
                Option.setVisualizerType(Var.VISUALIZER_TYPE_RADIAL);
            } else if (checkedId2 == R.id.btnVisualizerWave) {
                Option.setVisualizerType(Var.VISUALIZER_TYPE_WAVE);
            } else {
                Option.setVisualizerType(Var.VISUALIZER_TYPE_LEGACY);
            }
        });

        chkClickMainContainerPlayPause.setChecked(Option.getClickMainContainerPlayPause());
        llClickMainContainerPlayPause.setOnClickListener(v -> chkClickMainContainerPlayPause.toggle());
        chkClickMainContainerPlayPause
                .setOnCheckedChangeListener((button, isChecked) -> Option.setClickVisualizerPlayPause(isChecked));

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

        // ---- Screensaver
        llScreensaverOn = root.findViewById(R.id.ll_screensaver_enabled);
        chkScreensaverOn = root.findViewById(R.id.chk_screensaver_enabled);
        tvScreensaverPermission = root.findViewById(R.id.tx_screensaver_permission);
        llScreensaverDelay = root.findViewById(R.id.ll_screensaver_delay);
        etScreensaverDelay = root.findViewById(R.id.etScreensaverDelay);
        TextView tvScreensaverDelayMin = root.findViewById(R.id.tvScreensaverDelayMin);
        TextView tvScreensaverDelayMax = root.findViewById(R.id.tvScreensaverDelayMax);
        MaterialButtonToggleGroup ssGroup = root.findViewById(R.id.groupVisualizerScreenSaverMode);
        LinearLayout llForceOrient = root.findViewById(R.id.ll_screensaver_force_orientation);
        MaterialCheckBox chkForceOrient = root.findViewById(R.id.chk_screensaver_force_orientation);
        MaterialButtonToggleGroup groupOrient = root.findViewById(R.id.groupScreensaverOrientation);

        boolean ssEnabled = Option.getScreensaverEnabled();
        updateScreensaverSectionVisibility(ssEnabled,
                llScreensaverDelay, etScreensaverDelay, tvScreensaverDelayMin, tvScreensaverDelayMax,
                ssGroup, llForceOrient, groupOrient);

        String ssType = Option.getScreensaverVisualizerType();
        int ssCheckedId = switch (ssType) {
            case Var.VISUALIZER_TYPE_BARS -> R.id.btnVisualizerScreenSaverBars;
            case Var.VISUALIZER_TYPE_RADIAL -> R.id.btnVisualizerScreenSaverRadial;
            case Var.VISUALIZER_TYPE_WAVE -> R.id.btnVisualizerScreenSaverWave;
            default -> R.id.btnVisualizerScreenSaverLegacy;
        };
        ssGroup.check(ssCheckedId);

        ssGroup.addOnButtonCheckedListener((g, checkedId2, isChecked) -> {
            if (!isChecked)
                return;
            if (checkedId2 == R.id.btnVisualizerScreenSaverBars) {
                Option.setScreensaverVisualizerType(Var.VISUALIZER_TYPE_BARS);
            } else if (checkedId2 == R.id.btnVisualizerScreenSaverRadial) {
                Option.setScreensaverVisualizerType(Var.VISUALIZER_TYPE_RADIAL);
            } else if (checkedId2 == R.id.btnVisualizerScreenSaverWave) {
                Option.setScreensaverVisualizerType(Var.VISUALIZER_TYPE_WAVE);
            } else {
                Option.setScreensaverVisualizerType(Var.VISUALIZER_TYPE_LEGACY);
            }
        });

        chkScreensaverOn.setChecked(ssEnabled);
        etScreensaverDelay.setText(String.valueOf(Option.getScreensaverDelaySeconds()));

        llScreensaverDelay.setEnabled(chkScreensaverOn.isChecked());
        etScreensaverDelay.setEnabled(chkScreensaverOn.isChecked());

        llScreensaverOn.setOnClickListener(v -> chkScreensaverOn.toggle());
        chkScreensaverOn.setOnCheckedChangeListener((b, isChecked) -> {
            Option.setScreensaverEnabled(isChecked);
            if (isChecked && !isRecordAudioPermissionGranted(requireContext())) {
                requestRecordAudioPermission(root);
            }
            setScreensaverPermissionText();
            llScreensaverDelay.setEnabled(isChecked);
            etScreensaverDelay.setEnabled(isChecked);
            updateScreensaverSectionVisibility(isChecked,
                    llScreensaverDelay, etScreensaverDelay, tvScreensaverDelayMin, tvScreensaverDelayMax,
                    ssGroup, llForceOrient, groupOrient);
            if (isChecked) {
                boolean forceOrient = chkForceOrient.isChecked();
                if (forceOrient) {
                    groupOrient.setSelectionRequired(true);
                    String orientMode = Option.getScreensaverOrientationMode();
                    groupOrient.check("PORTRAIT".equals(orientMode)
                            ? R.id.btnScreensaverOrientationPortrait
                            : R.id.btnScreensaverOrientationLandscape);
                } else {
                    groupOrient.setSelectionRequired(false);
                    groupOrient.clearChecked();
                }
                groupOrient.setEnabled(forceOrient);
            }
        });

        updateRecordAudioDependentOptions(root);

        // ---- Screensaver Orientation (landscape/portrait toggle enabled only when force orientation is checked)
        boolean isForceOrient = Option.getScreensaverForceOrientation();
        chkForceOrient.setChecked(isForceOrient);

        // Initial state: when disabled, clear selection so no button looks selected
        if (isForceOrient) {
            groupOrient.setSelectionRequired(true);
            String orientMode = Option.getScreensaverOrientationMode();
            if ("PORTRAIT".equals(orientMode)) {
                groupOrient.check(R.id.btnScreensaverOrientationPortrait);
            } else {
                groupOrient.check(R.id.btnScreensaverOrientationLandscape);
            }
        } else {
            groupOrient.setSelectionRequired(false);
            groupOrient.clearChecked();
        }
        groupOrient.setEnabled(isForceOrient);

        // Checkbox listener
        llForceOrient.setOnClickListener(v -> chkForceOrient.toggle());
        chkForceOrient.setOnCheckedChangeListener((b, isChecked) -> {
            Option.setScreensaverForceOrientation(isChecked);
            if (isChecked) {
                groupOrient.setSelectionRequired(true);
                String orientMode = Option.getScreensaverOrientationMode();
                if ("PORTRAIT".equals(orientMode)) {
                    groupOrient.check(R.id.btnScreensaverOrientationPortrait);
                } else {
                    groupOrient.check(R.id.btnScreensaverOrientationLandscape);
                }
            } else {
                groupOrient.setSelectionRequired(false);
                groupOrient.clearChecked();
            }
            groupOrient.setEnabled(isChecked);
        });

        // Toggle Group listener
        groupOrient.addOnButtonCheckedListener((g, checkedIdOrient, isChecked) -> {
            if (!isChecked)
                return;
            if (checkedIdOrient == R.id.btnScreensaverOrientationPortrait) {
                Option.setScreensaverOrientationMode("PORTRAIT");
            } else {
                Option.setScreensaverOrientationMode("LANDSCAPE");
            }
        });

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
        chkStopAudioIfUserClosesApp
                .setOnCheckedChangeListener((b, isChecked) -> Option.setStopAudioIfUserClosesApp(isChecked));

        llOpenPlayActivity = root.findViewById(R.id.ll_open_play_activity);
        chkOpenPlayActivity = root.findViewById(R.id.chk_open_play_activity);
        chkOpenPlayActivity.setChecked(Option.getOpenPlayActivity());
        llOpenPlayActivity.setOnClickListener(v -> chkOpenPlayActivity.toggle());
        chkOpenPlayActivity.setOnCheckedChangeListener((b, isChecked) -> Option.setOpenPlayActivity(isChecked));

        LinearLayout ll_lock_orientation_play_activity = root.findViewById(R.id.ll_lock_orientation_play_activity);
        CheckBox chk_lock_orientation_play_activity = root.findViewById(R.id.chk_lock_orientation_play_activity);
        chk_lock_orientation_play_activity.setChecked(Option.getScreenOrientationLock());
        ll_lock_orientation_play_activity.setOnClickListener(v -> chk_lock_orientation_play_activity.toggle());
        chk_lock_orientation_play_activity
                .setOnCheckedChangeListener((b, isChecked) -> Option.setScreenOrientationLock(isChecked));

        LinearLayout ll_use_heatmap_seekbar_in_play_activity = root
                .findViewById(R.id.ll_use_heatmap_seekbar_in_play_activity);
        MaterialCheckBox chk_use_heatmap_seekbar_in_play_activity = root
                .findViewById(R.id.chk_use_heatmap_seekbar_in_play_activity);
        chk_use_heatmap_seekbar_in_play_activity.setChecked(Option.getUseHeatmapSeekbarInPlayActivity());
        ll_use_heatmap_seekbar_in_play_activity
                .setOnClickListener(v -> chk_use_heatmap_seekbar_in_play_activity.toggle());
        chk_use_heatmap_seekbar_in_play_activity
                .setOnCheckedChangeListener((b, isChecked) -> Option.setUseHeatmapSeekbarInPlayActivity(isChecked));

        // Set screensaver delay min/max TextViews from constants
        if (tvScreensaverDelayMin != null) {
            tvScreensaverDelayMin
                    .setText(getString(R.string.optionMinEqual) + " " + Option.getMinScreensaverDelaySeconds() + " " + getString(R.string.sec));
        }
        if (tvScreensaverDelayMax != null) {
            tvScreensaverDelayMax
                    .setText(getString(R.string.optionMaxEqual) + " " + Option.getMaxScreensaverDelaySeconds() + " " + getString(R.string.sec));
        }

        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
        setVisualizerPermissionText(); // refresh after coming back from Settings, etc.
        setScreensaverPermissionText();
    }

    @Override
    public void onPause() {
        super.onPause();
        saveEditTextValues();
    }

    private void updateScreensaverSectionVisibility(boolean visible,
            LinearLayout llScreensaverDelay, EditText etScreensaverDelay,
            TextView tvScreensaverDelayMin, TextView tvScreensaverDelayMax,
            MaterialButtonToggleGroup ssGroup, LinearLayout llForceOrient,
            MaterialButtonToggleGroup groupOrient) {
        int vis = visible ? View.VISIBLE : View.GONE;
        if (llScreensaverDelay != null) llScreensaverDelay.setVisibility(vis);
        if (etScreensaverDelay != null) etScreensaverDelay.setVisibility(vis);
        if (tvScreensaverDelayMin != null) tvScreensaverDelayMin.setVisibility(vis);
        if (tvScreensaverDelayMax != null) tvScreensaverDelayMax.setVisibility(vis);
        if (ssGroup != null) ssGroup.setVisibility(vis);
        if (llForceOrient != null) llForceOrient.setVisibility(vis);
        if (groupOrient != null) groupOrient.setVisibility(vis);
    }

    private void saveEditTextValues() {
        // Read & validate on UI thread
        final int tbs = Option.clampInt(requireContext(), etTimeBeforeSleep, Option.MIN_TIME_BEFORE_SLEEP,
                Option.MAX_TIME_BEFORE_SLEEP, Option.DEFAULT_TIME_BEFORE_SLEEP,
                getString(R.string.option_timeBeforeSleep));
        final int fwd = Option.clampInt(requireContext(), etForwardSeconds, MIN_FORWARD_SECONDS, MAX_FORWARD_SECONDS,
                Option.DEFAULT_FORWARD_SECONDS, getString(R.string.option_backward_forward_title));
        final int screensaverDelay = Option.clampInt(requireContext(), etScreensaverDelay,
                Option.getMinScreensaverDelaySeconds(), Option.getMaxScreensaverDelaySeconds(),
                10, getString(R.string.option_screensaver_title));

        // Persist off the UI thread
        Executors.newSingleThreadExecutor().execute(() -> {
            Option.setTimeBeforeSleep(tbs);
            Option.set_ForwardSeconds(fwd);
            Option.setScreensaverDelaySeconds(screensaverDelay);
        });
    }

    private void setVisualizerPermissionText() {
        if (txVisualizerOn == null)
            return;
        String txt = getString(R.string.option_visualizer_text_01)
                + "<br><i>"
                + getString(R.string.option_visualizer_text_02);

        if (isRecordAudioPermissionGranted(requireContext())) {
            txt = txt + ": <font color='green'>" + getString(R.string.granted) + "</font></i>";
        } else {
            txt = txt + ": <font color='red'>" + getString(R.string.denied) + "</font><br>"
                    + getString(R.string.option_visualizer_permissions_denied_02) + "</i>";
        }
        txVisualizerOn.setText(Html.fromHtml(txt, Html.FROM_HTML_MODE_LEGACY));
    }

    private void setScreensaverPermissionText() {
        String txt = "<i>" + getString(R.string.option_visualizer_text_02);
        if (isRecordAudioPermissionGranted(requireContext())) {
            txt = txt + ": <font color='green'>" + getString(R.string.granted) + "</font></i>";
        } else {
            txt = txt + ": <font color='red'>" + getString(R.string.denied) + "</font><br>"
                    + getString(R.string.option_visualizer_permissions_denied_02) + "</i>";
        }
        tvScreensaverPermission.setText(Html.fromHtml(txt, Html.FROM_HTML_MODE_LEGACY));
    }

    /**
     * Updates UI that depends on RECORD_AUDIO permission: visualizer and screensaver permission text.
     * When permission is not granted, screensaver is unchecked and section hidden (state consistent);
     * user can still tap the option to request permission (same as visualizer).
     */
    private void updateRecordAudioDependentOptions(View root) {
        setVisualizerPermissionText();
        setScreensaverPermissionText();
    }

    private void requestRecordAudioPermission(View root) {
        mPermissionRequest = PermissionRequest
                .with(requireActivity())
                .permissions(Manifest.permission.RECORD_AUDIO)
                .rationale(R.string.permission_record_audio_rationale)
                .denied(R.string.permission_record_audio_denied)
                .snackbar((ViewGroup) root) // show feedback in fragment root
                .callback(new PermissionRequest.Callback() {
                    @Override
                    public void onPermissionsGranted() {
                        myLog("RecordAudio Permission Granted");
                        View v = getView();
                        if (v != null) updateRecordAudioDependentOptions(v);
                    }

                    @Override
                    public void onPermissionsDenied() {
                        myLog("RecordAudio Permission Denied");
                        View v = getView();
                        if (v != null) updateRecordAudioDependentOptions(v);
                    }
                })
                .submit();
    }
}
