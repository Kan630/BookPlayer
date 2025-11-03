package com.driot.bookplayer.player;

import android.content.Intent;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.annotation.*;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.bumptech.glide.Glide;
import com.driot.bookplayer.R;
import com.driot.bookplayer.activities.GetRadioActivity;
import com.driot.bookplayer.activities.PlayActivity;
import com.driot.bookplayer.helpers.TitleHelper;

public class RadioMiniNowPlayingFragment extends Fragment {
    private PlaybackViewModel vm;
    private View root;
    private ImageView ivCover;
    private TextView tvTitle, tvSub;
    private ProgressBar progress;
    private ImageButton btnPlayPause;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup c, @Nullable Bundle b) {
        return inf.inflate(R.layout.fragment_radio_mini, c, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle b) {
        root = v.findViewById(R.id.root);
        ivCover = v.findViewById(R.id.ivCover);
        tvTitle = v.findViewById(R.id.tvTitle);
        tvSub   = v.findViewById(R.id.tvSub);
        progress= v.findViewById(R.id.progress);
        btnPlayPause = v.findViewById(R.id.btnPlayPause);

        root.setVisibility(View.GONE);

        vm = new ViewModelProvider(requireActivity()).get(PlaybackViewModel.class);

        // Observe UI state
        vm.getState().observe(getViewLifecycleOwner(), s -> {
            if (s == null) return;
            TitleHelper.setTitleAndSubtitle(tvTitle, tvSub, s.title, s.subTitle);

            // Play/pause icon
            btnPlayPause.setImageResource(s.playing ? R.drawable.ic_media_pause_24 : R.drawable.ic_media_play_24);

            Glide.with(ivCover.getContext())
                    .load(s.cover)
                    .placeholder(R.drawable.ic_radio_24px)
                    .error(R.drawable.ic_radio_24px)
                    .into(ivCover);
        });

        // Observe phase (to show buffering spinner)
        vm.getPhase().observe(getViewLifecycleOwner(), p -> {
            if (p == null) return;
            boolean busy = p.isBusyPhase();
            progress.setVisibility(busy ? View.VISIBLE : View.GONE);
        });

        // Visibility rule: show for radio as soon as ready OR buffering; hide only if explicitly suppressed
        vm.getIsRadio().observe(getViewLifecycleOwner(), isRadio -> reevaluateVisibility());
        vm.getMiniSuppressed().observe(getViewLifecycleOwner(), sup -> reevaluateVisibility());
        vm.getState().observe(getViewLifecycleOwner(), s -> reevaluateVisibility());
        vm.getPhase().observe(getViewLifecycleOwner(), p -> reevaluateVisibility());

        btnPlayPause.setOnClickListener(_v -> vm.playPause());

        v.setOnClickListener(_x -> startActivity(new Intent(requireContext(), GetRadioActivity.class)));
    }

    private void reevaluateVisibility() {
        PlaybackUiState s = vm.getState().getValue();
        PlaybackViewModel.PhaseUi p = vm.getPhase().getValue();
        Boolean sup = vm.getMiniSuppressed().getValue();
        Boolean isRadio = vm.getIsRadio().getValue();

        if (s == null) { root.setVisibility(View.GONE); return; }

        boolean buffering = (p != null) && p.isBusyPhase();
        boolean showMini =
                (sup == null || !sup) &&
                        (
                                s.playing ||
                                        s.ready ||
                                        (Boolean.TRUE.equals(isRadio) && buffering)
                        );

        root.setVisibility(showMini ? View.VISIBLE : View.GONE);
    }
}
