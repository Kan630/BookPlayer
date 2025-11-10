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
import com.driot.bookplayer.utils.log.LoggingFragment;


public class MiniPlayRadioFragment extends LoggingFragment {
    private PlaybackViewModel vm;
    private View root;
    private ProgressBar progressBar;
    private ImageView ivCover;
    private TextView tvTitle, tvSubTitle;
    private ImageButton btnPlayPause;

    PlaybackUiState lastState;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup c, @Nullable Bundle b) {
        return inf.inflate(R.layout.fragment_mini_play_radio, c, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle b) {
        root = v.findViewById(R.id.root);
        progressBar = v.findViewById(R.id.progress);
        ivCover = v.findViewById(R.id.ivCover);
        tvTitle = v.findViewById(R.id.tvTitle);
        tvSubTitle = v.findViewById(R.id.tvSub);
        btnPlayPause = v.findViewById(R.id.bMiniPlayPause);

        vm = new ViewModelProvider(requireActivity()).get(PlaybackViewModel.class);

        vm.getState().observe(getViewLifecycleOwner(), s -> {
            if (s == null) {
                myLog("vm.getState().observe : s == null");
                return;
            }
            myLogD(s.toString());
            myLogI("vm.getState().observe " + s);
            UiHelper.FillUiBasic(s, progressBar, btnPlayPause, tvTitle, tvSubTitle, null, null, null);

            if (lastState==null || lastState.cover==null || (s.cover!=null && !lastState.cover.equals(s.cover))) {
                myLogD("gliding cover image");
                Glide.with(ivCover.getContext())
                        .load(s.cover)
                        .placeholder(R.drawable.ic_radio_24px)
                        .error(R.drawable.ic_radio_24px)
                        .into(ivCover);
            }

            lastState = vm.getState().getValue();
        });

        btnPlayPause.setOnClickListener(_v -> { myLogI("---- user press PlayPause button ----"); vm.playPause(); });

        v.setOnClickListener(_x -> {
            myLogI("---- user press mini player ----");
            startActivity(new Intent(requireContext(), GetRadioActivity.class));
        });
    }
}
