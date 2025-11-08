package com.driot.bookplayer.player;

import android.content.Intent;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.annotation.*;
import androidx.lifecycle.ViewModelProvider;

import com.bumptech.glide.Glide;
import com.driot.bookplayer.R;
import com.driot.bookplayer.activities.GetRadioActivity;
import com.driot.bookplayer.helpers.TitleHelper;
import com.driot.bookplayer.utils.log.LoggingFragment;


public class MiniPlayRadioFragment extends LoggingFragment {
    private PlaybackViewModel vm;
    private View root;
    private ProgressBar progress;
    private ImageView ivCover;
    private TextView tvTitle, tvSub;
    private ImageButton btnPlayPause;

    PlaybackUiState lastState;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup c, @Nullable Bundle b) {
        return inf.inflate(R.layout.fragment_mini_play_radio, c, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle b) {
        root = v.findViewById(R.id.root);
        progress= v.findViewById(R.id.progress);
        ivCover = v.findViewById(R.id.ivCover);
        tvTitle = v.findViewById(R.id.tvTitle);
        tvSub   = v.findViewById(R.id.tvSub);
        btnPlayPause = v.findViewById(R.id.bMiniPlayPause);

        vm = new ViewModelProvider(requireActivity()).get(PlaybackViewModel.class);

        // Observe UI state
        vm.getState().observe(getViewLifecycleOwner(), s -> {
            if (s == null) return;
            myLogI("vm.getState().observe " + s.toString());
            TitleHelper.setTitleAndSubtitle(tvTitle, tvSub, s.title, s.subTitle);

            // Play/pause icon
            btnPlayPause.setImageResource(s.playing ? R.drawable.ic_media_pause_24 : R.drawable.ic_media_play_24);

            if (lastState==null || !lastState.cover.equals(s.cover)) {
                myLogD("gliding cover image");
                Glide.with(ivCover.getContext())
                        .load(s.cover)
                        .placeholder(R.drawable.ic_radio_24px)
                        .error(R.drawable.ic_radio_24px)
                        .into(ivCover);
            }

            boolean buffering = !"READY".equalsIgnoreCase(s.loadPhase);
            progress.setVisibility(buffering ? View.VISIBLE : View.GONE); //spinning loading icon

            lastState = vm.getState().getValue();
        });

        btnPlayPause.setOnClickListener(_v -> {
            myLogI("---- user press PlayPause button ----");
            vm.playPause();
        });

        v.setOnClickListener(_x -> {
            myLogI("---- user press mini player ----");
            startActivity(new Intent(requireContext(), GetRadioActivity.class));
        });
    }
}
