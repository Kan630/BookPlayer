package com.driot.bookplayer.player;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.bumptech.glide.Glide;
import com.driot.bookplayer.R;
import com.driot.bookplayer.activities.PodcastEpisodeActivity;
import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.db.Podcast;
import com.driot.bookplayer.helpers.TitleHelper;
import com.driot.bookplayer.utils.Tonio;
import com.driot.bookplayer.utils.log.LoggingFragment;

public class MiniPlayPodcastFragment extends LoggingFragment {
    private PlaybackViewModel vm;
    private View root;
    private ProgressBar progress;
    private ImageView ivCover;
    private TextView tvTitle, tvSubTitle, tvMiniTime;
    private SeekBar sbMiniSeek;
    private ImageButton btnPrev, btnPlayPause, btnNext, btnStop;
    private boolean userSeeking;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup c, @Nullable Bundle b) {
        return inf.inflate(R.layout.fragment_mini_play_streaming_player, c, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle b) {
        root = v.findViewById(R.id.root);
        progress= v.findViewById(R.id.progress);
        tvTitle = v.findViewById(R.id.tvTitle);
        tvSubTitle = v.findViewById(R.id.tvSubTitle);
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

        v.setVisibility(View.GONE);

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
                long pos = s.positionMs;
                long dur = s.durationMs;
                if (dur > 0) {
                    //int prog = (int) ((pos * sbMiniSeek.getMax()) / dur);
                    //sbMiniSeek.setProgress(prog);
                    sbMiniSeek.setMax((int) s.durationMs);
                    sbMiniSeek.setProgress((int) Math.min(s.positionMs, s.durationMs));

                    String timeString = Tonio.formatMmSs(pos) + " / " + Tonio.formatMmSs(dur);
                    tvMiniTime.setText(timeString);
                } else {
                    sbMiniSeek.setProgress(0);
                    tvMiniTime.setText("--:-- / --:--");
                }
            }

            btnPlayPause.setImageResource(s.playing ? R.drawable.ic_media_pause_24 : R.drawable.ic_media_play_24);

            reevaluateVisibility();

            vm.getPlayMode().observe(getViewLifecycleOwner(), playType -> reevaluateVisibility());
            vm.getMiniSuppressed().observe(getViewLifecycleOwner(), sup -> reevaluateVisibility());
            vm.getState().observe(getViewLifecycleOwner(), state -> reevaluateVisibility());
            vm.getPhase().observe(getViewLifecycleOwner(), p -> reevaluateVisibility());
        });
/*
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
 */


        v.setOnClickListener(_x -> {
            myLogI("---- user clicks on mini player root ----");
            if (vm.getState() != null && vm.getState().getValue()!=null) {
                long idPodcast = vm.getState().getValue().podcastFeedId;
                myLogD("idPodcast = " + idPodcast);
                AppDatabase.databaseReadExecutor.execute(() -> {
                    Podcast podcast = AppDatabase.getDatabase(requireContext()).podcastDao().getPodcastByFeedId(idPodcast);
                    startActivity(new Intent(requireContext(), PodcastEpisodeActivity.class).putExtra("podcast", podcast));
                });
            }
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
        /*
        btnStop.setOnClickListener(_v -> {
            myLogI("---- user press STOP button ----");
            vm.dismissMini();
        });

         */

        sbMiniSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int p, boolean fromUser) {}
            @Override public void onStartTrackingTouch(SeekBar sb) { userSeeking = true; }
            @Override public void onStopTrackingTouch(SeekBar sb) {
                userSeeking = false;
                myLogI("---- user press SEEK BAR ----");
                vm.seekTo(sb.getProgress());
            }
        });



    }
    private void reevaluateVisibility() {
        PlaybackUiState s = vm.getState().getValue();
        PlaybackViewModel.PhaseUi p = vm.getPhase().getValue();
        Boolean sup = vm.getMiniSuppressed().getValue();
        String playType = vm.getPlayMode().getValue();

        if (s == null) { root.setVisibility(View.GONE); return; }

        boolean buffering = !"READY".equalsIgnoreCase(s.loadPhase);
        progress.setVisibility(buffering ? View.VISIBLE : View.GONE); //spinning loading icon

        boolean showMini =
                (sup == null || !sup) &&
                        (
                                s.playing ||
                                        s.ready ||
                                        ("radio".equals(playType) && buffering)
                        );

        //myLogD("set Visibility :" + showMini);
        root.setVisibility(showMini ? View.VISIBLE : View.GONE);
    }

}
