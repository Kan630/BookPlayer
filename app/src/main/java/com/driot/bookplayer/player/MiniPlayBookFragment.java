package com.driot.bookplayer.player;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.driot.bookplayer.R;
import com.driot.bookplayer.activities.PlayActivity;
import com.driot.bookplayer.utils.log.LoggingFragment;
import com.google.android.material.slider.Slider;

public class MiniPlayBookFragment extends LoggingFragment {
    private PlaybackViewModel vm;
    private ImageView ivCover;
    private TextView tvTitle, tvSubTitle, tvMiniTime;
    private Slider sbMiniSeek;
    private ImageButton btnPrev, btnPlayPause, btnNext, btnStop;

    private UiHelper.SliderBinding sliderBinding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup c, @Nullable Bundle b) {
        return inf.inflate(R.layout.fragment_mini_play_book, c, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle b) {
        tvTitle     = v.findViewById(R.id.tvTitle);
        tvSubTitle  = v.findViewById(R.id.tvSubTitle);
        tvMiniTime  = v.findViewById(R.id.tvMiniTime);
        sbMiniSeek  = v.findViewById(R.id.sbMiniSeek);
        btnPrev     = v.findViewById(R.id.bMiniBackward);
        btnPlayPause= v.findViewById(R.id.bMiniPlayPause);
        btnNext     = v.findViewById(R.id.bMiniForward);
        btnStop     = v.findViewById(R.id.btnStop);
        ivCover     = v.findViewById(R.id.ivCover);

        btnPrev.setImageResource(R.drawable.ic_media_fast_rewind_24);
        btnNext.setImageResource(R.drawable.ic_media_fast_forward_24);
        btnStop.setImageResource(R.drawable.ic_media_close_24);

        vm = new ViewModelProvider(requireActivity()).get(PlaybackViewModel.class);

        sliderBinding = UiHelper.bindSeekBar(sbMiniSeek, tvMiniTime, vm);

        vm.getState().observe(getViewLifecycleOwner(), s -> {
            if (s == null) return;
            UiHelper.FillUiBasic(s, null, btnPlayPause, tvTitle, tvSubTitle, tvMiniTime, ivCover, sbMiniSeek);
        });

        v.setOnClickListener(_x -> {
            myLogI("---- user clicks on mini player root ----");
            startActivity(new Intent(requireContext(), PlayActivity.class));
        });

        btnPrev.setOnClickListener(_v -> { myLogI("---- user press PREV button ----"); vm.prev(); });
        btnPlayPause.setOnClickListener(_v -> { myLogI("---- user press PlayPause button ----"); vm.playPause(); });
        btnNext.setOnClickListener(_v -> { myLogI("---- user press NEXT button ----"); vm.next(); });
        btnStop.setOnClickListener(_v -> { myLogI("---- user press STOP button ----"); vm.stop(); });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (sbMiniSeek != null) {
            UiHelper.unbindSeekBar(sbMiniSeek);
        }
        sliderBinding = null;
    }
}
