package com.driot.bookplayer.player;

import android.content.Intent;
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
import com.driot.bookplayer.activities.PodcastEpisodeActivity;
import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.db.Podcast;
import com.driot.bookplayer.utils.Tonio;
import com.driot.bookplayer.utils.log.LoggingFragment;
import com.google.android.material.slider.Slider;

public class MiniPlayPodcastFragment extends LoggingFragment {
    private PlaybackViewModel vm;
    private View root;
    private ProgressBar progressBar;
    private ImageView ivCover;
    //private TextView tvTitle, tvSubTitle;
    private TextView tvMiniTime;
    private Slider sbMiniSeek; // <- change type
    private boolean userSeeking;
    private ImageButton btnPrev, btnPlayPause, btnNext, btnStop;

    private UiHelper.SliderBinding sliderBinding;


    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup c, @Nullable Bundle b) {
        return inf.inflate(R.layout.fragment_mini_play_podcast, c, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle b) {
        root = v.findViewById(R.id.root);
        progressBar = v.findViewById(R.id.progress);
        //tvTitle = v.findViewById(R.id.tvTitle);
        //tvSubTitle = v.findViewById(R.id.tvSubTitle);
        sbMiniSeek = v.findViewById(R.id.sbMiniSeek);
        btnPrev = v.findViewById(R.id.bMiniBackward);
        btnPlayPause = v.findViewById(R.id.bMiniPlayPause);
        btnNext = v.findViewById(R.id.bMiniForward);
        tvMiniTime = v.findViewById(R.id.tvMiniTime);
        //btnStop = v.findViewById(R.id.btnStop);
        ivCover = v.findViewById(R.id.ivCover);

        btnPrev.setImageResource(R.drawable.ic_media_fast_rewind_24);
        btnNext.setImageResource(R.drawable.ic_media_fast_forward_24);
        //btnStop.setImageResource(R.drawable.ic_media_close_24);

        // Show a mm:ss bubble while dragging
        sbMiniSeek.setLabelFormatter(value -> Tonio.formatMmSs((long) value * 1000L));

        sbMiniSeek.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser) {
                // Live preview while scrubbing
                long previewMs = (long) value * 1000L;
                PlaybackUiState s = vm.getState().getValue();
                long dur = (s != null) ? s.durationMs : 0L;
                tvMiniTime.setText(Tonio.formatMmSs(previewMs) + " / " + Tonio.formatMmSs(dur));
            }
        });

        sbMiniSeek.addOnSliderTouchListener(new Slider.OnSliderTouchListener() {
            @Override
            public void onStartTrackingTouch(@NonNull Slider slider) {
                userSeeking = true;
            }

            @Override
            public void onStopTrackingTouch(@NonNull Slider slider) {
                userSeeking = false;
                myLogI("---- user finished SLIDER seek ----");
                vm.seekTo((long) slider.getValue() * 1000L);
            }
        });

        vm = new ViewModelProvider(requireActivity()).get(PlaybackViewModel.class);
        sliderBinding = UiHelper.bindSeekBar(sbMiniSeek, tvMiniTime, vm);
        vm.getState().observe(getViewLifecycleOwner(), s -> {
            if (s == null) return;
            myLogD(s.toString());
            UiHelper.FillUiBasic(s, progressBar, btnPlayPause, null, null, tvMiniTime, ivCover, sbMiniSeek);
        });

        btnPrev.setOnClickListener(_v -> { myLogI("---- user press PREV button ----"); vm.prev(); });
        btnPlayPause.setOnClickListener(_v -> { myLogI("---- user press PlayPause button ----"); vm.playPause(); });
        btnNext.setOnClickListener(_v -> { myLogI("---- user press NEXT button ----"); vm.next(); });
        //btnStop.setOnClickListener(_v -> { myLogI("---- user press STOP button ----"); vm.stop(); });

        v.setOnClickListener(_x -> {
            myLogI("---- user clicks on mini player root ----");
            if (vm.getState() != null && vm.getState().getValue() != null) {
                long idPodcast = vm.getState().getValue().podcastFeedId;
                myLogD("idPodcast = " + idPodcast);
                AppDatabase.databaseReadExecutor.execute(() -> {
                    Podcast podcast = AppDatabase.getDatabase(requireContext()).podcastDao().getPodcastByFeedId(idPodcast);
                    startActivity(new Intent(requireContext(), PodcastEpisodeActivity.class).putExtra("podcast", podcast));
                });
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (sbMiniSeek != null) {
            UiHelper.unbindSeekBar(sbMiniSeek);
        }
        sliderBinding = null;
    }

    @Override public void onStart() {
        super.onStart();
        MediaControllerHolder.attachTo(this.getActivity());
        MediaControllerHolder.ensureConnected(requireContext().getApplicationContext());
    }

    @Override public void onStop() {
        MediaControllerHolder.detachFrom(this.getActivity());
        super.onStop();
    }

}
