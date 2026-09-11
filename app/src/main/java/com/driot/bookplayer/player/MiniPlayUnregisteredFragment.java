package com.driot.bookplayer.player;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.driot.bookplayer.R;
import com.driot.bookplayer.utils.Tonio;
import com.driot.bookplayer.utils.log.LoggingFragment;
import com.google.android.material.slider.Slider;

/**
 * Mini-player shown at the bottom of the screen while live-previewing a file opened via
 * "Open With" that isn't (yet) registered as a book in the library - lets the user listen to it
 * right away, alongside the classic import screen, before deciding whether to import it. Backed
 * by the same DB-free stream mechanism as radio/podcast (see StartPlayHelper.playPreview()), so
 * there is no ZikFile/Folder/Podcast row behind it and no destination screen to open on tap.
 */
public class MiniPlayUnregisteredFragment extends LoggingFragment {

    private PlaybackViewModel vm;
    private ProgressBar progressBar;
    private ImageView ivCover;
    private TextView tvTitle, tvSubTitle, tvMiniTime;
    private Slider sbMiniSeek;
    private ImageButton btnBackward, btnPlayPause, btnForward, ibClose;

    private UiHelper.SliderBinding sliderBinding;
    private PlaybackUiState lastState;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup c, @Nullable Bundle b) {
        return inf.inflate(R.layout.fragment_mini_play_unregistered, c, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle b) {
        progressBar = v.findViewById(R.id.progress);
        ivCover = v.findViewById(R.id.ivCover);
        tvTitle = v.findViewById(R.id.tvTitle);
        tvSubTitle = v.findViewById(R.id.tvSubTitle);
        sbMiniSeek = v.findViewById(R.id.sbMiniSeek);
        tvMiniTime = v.findViewById(R.id.tvMiniTime);
        btnBackward = v.findViewById(R.id.bMiniBackward);
        btnPlayPause = v.findViewById(R.id.bMiniPlayPause);
        btnForward = v.findViewById(R.id.bMiniForward);
        ibClose = v.findViewById(R.id.ibClose);

        sbMiniSeek.setLabelFormatter(value -> Tonio.formatMmSs((long) value * 1000L));

        sbMiniSeek.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser) {
                long previewMs = (long) value * 1000L;
                PlaybackUiState s = vm.getState().getValue();
                long dur = (s != null) ? s.durationMs : 0L;
                tvMiniTime.setText(Tonio.formatHhMmSs(previewMs) + " / " + Tonio.formatHhMmSs(dur));
            }
        });

        sbMiniSeek.addOnSliderTouchListener(new Slider.OnSliderTouchListener() {
            @Override
            public void onStartTrackingTouch(@NonNull Slider slider) {
            }

            @Override
            public void onStopTrackingTouch(@NonNull Slider slider) {
                myLogI("---- user finished SLIDER seek (preview) ----");
                vm.seekTo((long) slider.getValue() * 1000L);
            }
        });

        vm = new ViewModelProvider(requireActivity()).get(PlaybackViewModel.class);
        sliderBinding = UiHelper.bindSeekBar(sbMiniSeek, tvMiniTime, vm);
        vm.getState().observe(getViewLifecycleOwner(), s -> {
            if (s == null)
                return;
            lastState = s;
            refreshUi();
        });

        btnBackward.setOnClickListener(_v -> {
            myLogI("---- user press BACKWARD button (preview) ----");
            PlaybackCommands.resetLastUserAction(requireContext());
            vm.prev();
        });
        btnPlayPause.setOnClickListener(_v -> {
            myLogI("---- user press PlayPause button (preview) ----");
            PlaybackCommands.resetLastUserAction(requireContext());
            vm.playPause();
        });
        btnForward.setOnClickListener(_v -> {
            myLogI("---- user press FORWARD button (preview) ----");
            PlaybackCommands.resetLastUserAction(requireContext());
            vm.next();
        });
        ibClose.setOnClickListener(_v -> {
            myLogI("---- user press CLOSE button (preview) ----");
            PlaybackCommands.resetLastUserAction(requireContext());
            vm.stop();
        });

        // No destination screen exists for an unregistered file - the row itself isn't
        // clickable (unlike book/radio/podcast, which open their respective detail screens).
    }

    private void refreshUi() {
        if (lastState == null)
            return;
        UiHelper.FillUiBasic(lastState, progressBar, btnPlayPause, tvTitle, tvSubTitle, tvMiniTime, ivCover,
                sbMiniSeek, null, null);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (sbMiniSeek != null) {
            UiHelper.unbindSeekBar(sbMiniSeek);
        }
        sliderBinding = null;
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
