package com.driot.bookplayer.player;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.bumptech.glide.Glide;
import com.driot.bookplayer.R;
import com.driot.bookplayer.activities.PlayActivity;
import com.driot.bookplayer.helpers.TitleHelper;
import com.driot.bookplayer.utils.Tonio;
import com.driot.bookplayer.utils.log.LoggingFragment;
import com.google.android.material.slider.Slider;

public class MiniPlayBookFragment extends LoggingFragment {
    private PlaybackViewModel vm;
    private ImageView ivCover;
    private TextView tvTitle, tvSubTitle;
    private Slider sbMiniSeek;
    private ImageButton btnPrev, btnPlayPause, btnNext, btnStop;
    private boolean userSeeking;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup c, @Nullable Bundle b) {
        return inf.inflate(R.layout.fragment_mini_play_book, c, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle b) {
        tvTitle = v.findViewById(R.id.tvTitle);
        tvSubTitle = v.findViewById(R.id.tvSubTitle);
        sbMiniSeek = v.findViewById(R.id.sbMiniSeek);
        btnPrev = v.findViewById(R.id.bMiniBackward);
        btnPlayPause = v.findViewById(R.id.bMiniPlayPause);
        btnNext = v.findViewById(R.id.bMiniForward);
        btnStop = v.findViewById(R.id.btnStop);
        ivCover = v.findViewById(R.id.ivCover);

        btnPrev.setImageResource(R.drawable.ic_media_fast_rewind_24);
        btnNext.setImageResource(R.drawable.ic_media_fast_forward_24);
        btnStop.setImageResource(R.drawable.ic_media_close_24);

        // Show a mm:ss bubble while dragging
        sbMiniSeek.setLabelFormatter(value -> Tonio.formatMmSs((long) value * 1000L));

        sbMiniSeek.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser) {
                // Live preview while scrubbing
                long previewMs = (long) value * 1000L;
                PlaybackUiState s = vm.getState().getValue();
                long dur = (s != null) ? s.durationMs : 0L;
                //tvMiniTime.setText(Tonio.formatMmSs(previewMs) + " / " + Tonio.formatMmSs(dur));
            }
        });

        sbMiniSeek.addOnSliderTouchListener(new Slider.OnSliderTouchListener() {
            @Override public void onStartTrackingTouch(@NonNull Slider slider) { userSeeking = true; }
            @Override public void onStopTrackingTouch(@NonNull Slider slider) {
                userSeeking = false;
                myLogI("---- user finished SLIDER seek ----");
                vm.seekTo((long) slider.getValue() * 1000L);
            }
        });


        vm = new ViewModelProvider(requireActivity()).get(PlaybackViewModel.class);
        vm.getState().observe(getViewLifecycleOwner(), s -> {
            if (s == null) return;
            TitleHelper.setTitleAndSubtitle(tvTitle, tvSubTitle, s.title, s.subTitle);
            if (s.cover != null) {
                ivCover.setVisibility(View.VISIBLE);
                Glide.with(ivCover.getContext()).load(s.cover).into(ivCover);
            } else {
                ivCover.setVisibility(View.GONE);
            }
            if (!userSeeking) {
                //sbMiniSeek.setMax((int) Math.max(1L, s.durationMs));
                //sbMiniSeek.setProgress((int) Math.min(s.positionMs, s.durationMs));
                long pos = s.positionMs;
                long dur = s.durationMs;

                if (dur > 0) {
                    // Use seconds on the slider to avoid float precision issues on long files
                    float durSec = dur / 1000f;
                    float posSec = Math.min(pos, dur) / 1000f;

                    // valueTo must be >= value
                    if (sbMiniSeek.getValueTo() != durSec) sbMiniSeek.setValueTo(durSec);
                    if (sbMiniSeek.getValue() != posSec) sbMiniSeek.setValue(posSec);

                    //tvMiniTime.setText(Tonio.formatMmSs(pos) + " / " + Tonio.formatMmSs(dur));
                } else {
                    sbMiniSeek.setValueTo(1000f);
                    sbMiniSeek.setValue(0f);
                    //tvMiniTime.setText("--:-- / --:--");
                }
            }
            btnPlayPause.setImageResource(s.playing ? R.drawable.ic_media_pause_24 : R.drawable.ic_media_play_24);

            Boolean suppressed = vm.getMiniSuppressed().getValue();
            boolean hideBecauseSuppressed = Boolean.TRUE.equals(suppressed) && !s.playing;
            boolean hasContent = s.playing || s.durationMs > 0;

            v.setVisibility((hasContent && !hideBecauseSuppressed) ? View.VISIBLE : View.GONE);
        });

// also observe the suppression flag to re-evaluate immediately
        vm.getMiniSuppressed().observe(getViewLifecycleOwner(), sup -> {
            PlaybackUiState s = vm.getState().getValue();
            if (s == null) return;
            boolean hideBecauseSuppressed = Boolean.TRUE.equals(sup) && !s.playing;
            boolean hasContent =
                    (s.durationMs > 0) ||
                            (s.title != null && !s.title.isEmpty()) ||
                            (s.subTitle != null && !s.subTitle.isEmpty());
            getView().setVisibility((hasContent && !hideBecauseSuppressed) ? View.VISIBLE : View.GONE);
        });


        v.setOnClickListener(_x -> {
            myLogI("---- user clicks on mini player root ----");
            startActivity(new Intent(requireContext(), PlayActivity.class));
        });

        btnPrev.setOnClickListener(_v -> {
            myLogI("---- user press PREV button ----");
            vm.prev();
        });
        btnPlayPause.setOnClickListener(_v -> {
            myLogI("---- user press PlayPause button ----");
            vm.playPause();
        });
        btnNext.setOnClickListener(_v -> {
            myLogI("---- user press NEXT button ----");
            vm.next();
        });
        btnStop.setOnClickListener(_v -> {
            myLogI("---- user press STOP button ----");
            vm.dismissMini();
        });

    }
}
