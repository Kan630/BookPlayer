package com.driot.bookplayer.radio;

import android.content.Intent;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.annotation.*;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.bumptech.glide.Glide;
import com.driot.bookplayer.R;
import com.driot.bookplayer.global.Intents;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.nav.NavHelper;
import com.driot.bookplayer.player.MediaControllerHolder;
import com.driot.bookplayer.player.PlaybackCommands;
import com.driot.bookplayer.player.PlaybackUiState;
import com.driot.bookplayer.player.PlaybackViewModel;
import com.driot.bookplayer.player.UiHelper;
import com.driot.bookplayer.utils.NetworkStatusViewModel;
import com.driot.bookplayer.utils.Tonio;
import com.driot.bookplayer.utils.log.LoggingFragment;

import java.util.Objects;

public class MiniPlayRadioFragment extends LoggingFragment {
    private PlaybackViewModel vm;
    private ProgressBar progressBar;
    private ImageView ivCover, ivNoInternet;
    private TextView tvTitle, tvSubTitle;
    private ImageButton ibPlayPause, ibClose;
    private View llRecord;
    private ImageView ivRecordDot;
    private TextView tvRecordInfo;

    private PlaybackUiState lastState;
    private Boolean hasInternet = null;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup c, @Nullable Bundle b) {
        return inf.inflate(R.layout.fragment_mini_play_radio, c, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle b) {
        progressBar = v.findViewById(R.id.progress);
        ivCover = v.findViewById(R.id.ivCover);
        tvTitle = v.findViewById(R.id.tvTitle);
        tvSubTitle = v.findViewById(R.id.tvSub);
        ibPlayPause = v.findViewById(R.id.bMiniPlayPause);
        ibClose = v.findViewById(R.id.bMiniClose);
        ivNoInternet = v.findViewById(R.id.ivNoInternet);
        llRecord = v.findViewById(R.id.llRecord);
        ivRecordDot = v.findViewById(R.id.ivRecordDot);
        tvRecordInfo = v.findViewById(R.id.tvRecordInfo);

        vm = new ViewModelProvider(requireActivity()).get(PlaybackViewModel.class);
        vm.getState().observe(getViewLifecycleOwner(), s -> {
            if (s == null) {
                myLog("vm.getState().observe : s == null");
                return;
            }

            //COVER
            if (lastState == null || lastState.cover == null || (s.cover != null && !lastState.cover.equals(s.cover))) {
                myLogD("gliding cover image");
                Glide.with(ivCover.getContext())
                        .load(s.cover)
                        .placeholder(R.drawable.ic_radio_24px)
                        .error(R.drawable.ic_radio_24px)
                        .into(ivCover);
            }

            //PLAYING
            if (lastState != null && lastState.playing != s.playing)
                myLogD("playing changed => " + s.playing);
            lastState = vm.getState().getValue();
            refreshUi();

            //LOADING
            if (lastState != null && !Objects.equals(lastState.loadPhase, s.loadPhase))
                myLogD("phase changed => " + s.loadPhase);
            lastState = vm.getState().getValue();
            refreshUi();

            updateRecordingUi(s);
        });

        try {
            NetworkStatusViewModel netVm = new ViewModelProvider(requireActivity()).get(NetworkStatusViewModel.class);
            netVm.getStatus().observe(getViewLifecycleOwner(), s -> {
                hasInternet = s.hasInternet;
                myLogD("internet ok => " + hasInternet);
                refreshUi();
            });
        } catch (Throwable t) {
            myLogEE(t, "hilt shits - NetworkStatusViewModel");
            hasInternet = true;
        }

        ibPlayPause.setOnClickListener(_v -> {
            myLogI("---- user press PlayPause button ----");
            PlaybackCommands.resetLastUserAction(requireContext());
            vm.playPause();
        });
        ibClose.setOnClickListener(_v -> {
            myLogI("---- user press CLOSE button ----");
            PlaybackCommands.resetLastUserAction(requireContext());
            vm.stop();
        });

        llRecord.setOnClickListener(_v -> {
            myLogI("---- user press RECORD button ----");
            PlaybackCommands.resetLastUserAction(requireContext());
            PlaybackCommands.toggleRadioRecording(requireContext());
        });

        v.setOnClickListener(_x -> {
            myLogI("---- user press mini player ----");
            PlaybackCommands.resetLastUserAction(requireContext());
            if (vm.getState() != null && vm.getState().getValue() != null) {
                long trackId = vm.getState().getValue().trackId;
                NavHelper.openRadioStationActivity(requireContext(), (int) trackId);
            } else {
                myLog("no VM state");
                startActivity(new Intent(requireContext(), GetRadioActivity.class));
            }
        });
    }

    private void refreshUi() {
        if (lastState == null || hasInternet == null)
            return;
        UiHelper.FillUiBasic(lastState, progressBar, ibPlayPause, tvTitle, tvSubTitle, null, null, null, ivNoInternet,
                hasInternet);
    }

    private void updateRecordingUi(PlaybackUiState s) {
        boolean recordingFeatureEnabled = Option.getRadioRecordingEnabled();
        Bundle extras = s.extras;
        boolean available = extras != null && extras.getBoolean(Intents.EXTRA_RADIO_RECORDING_AVAILABLE, false);
        boolean active = extras != null && extras.getBoolean(Intents.EXTRA_RADIO_RECORDING_ACTIVE, false);

        // The record dot is gated by the setting; the info text next to it (recording elapsed
        // time/size, or - when idle - the buffered-ahead seconds) is shown regardless, since
        // buffer health is useful even with recording turned off.
        llRecord.setVisibility(View.VISIBLE);
        llRecord.setClickable(recordingFeatureEnabled);
        ivRecordDot.setVisibility(recordingFeatureEnabled ? View.VISIBLE : View.GONE);
        if (recordingFeatureEnabled) {
            ivRecordDot.setAlpha((available || active) ? 1f : 0.4f);
            if (active) {
                int dotColor = ContextCompat.getColor(requireContext(), R.color.red_500);
                ivRecordDot.setColorFilter(dotColor, android.graphics.PorterDuff.Mode.SRC_IN);
            } else {
                // Let the drawable's own ?attr/colorControlNormal tint show, same as the other
                // mini-player buttons (play/pause, close), instead of forcing a gray override.
                ivRecordDot.clearColorFilter();
            }
        }

        if (active) {
            long elapsedMs = extras.getLong(Intents.EXTRA_RADIO_RECORDING_ELAPSED_MS, 0);
            long bytes = extras.getLong(Intents.EXTRA_RADIO_RECORDING_BYTES, 0);
            tvRecordInfo.setText(getString(R.string.radio_recording_info,
                    Tonio.formatTime(elapsedMs), Tonio.getReadableSize(bytes)));
            tvRecordInfo.setVisibility(View.VISIBLE);
        } else {
            long bufferedMs = extras != null ? extras.getLong(Intents.EXTRA_RADIO_BUFFERED_MS, 0) : 0;
            if (bufferedMs > 0) {
                tvRecordInfo.setText(getString(R.string.radio_buffer_info, Tonio.formatTime(bufferedMs)));
                tvRecordInfo.setVisibility(View.VISIBLE);
            } else {
                tvRecordInfo.setVisibility(View.GONE);
            }
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        MediaControllerHolder.attachTo(this.getActivity());
        MediaControllerHolder.ensureConnected(requireContext().getApplicationContext());
    }

    @Override
    public void onStop() {
        MediaControllerHolder.detachFrom(this.getActivity());
        super.onStop();
    }

}
