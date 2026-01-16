package com.driot.bookplayer.player;

import android.content.Intent;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.annotation.*;
import androidx.lifecycle.ViewModelProvider;

import com.bumptech.glide.Glide;
import com.driot.bookplayer.R;
import com.driot.bookplayer.radio.GetRadioActivity;
import com.driot.bookplayer.global.Intents;
import com.driot.bookplayer.radio.RadioStationActivity;
import com.driot.bookplayer.utils.NetworkStatusViewModel;
import com.driot.bookplayer.utils.log.LoggingFragment;

public class MiniPlayRadioFragment extends LoggingFragment {
    private PlaybackViewModel vm;
    private ProgressBar progressBar;
    private ImageView ivCover, ivNoInternet;
    private TextView tvTitle, tvSubTitle;
    private ImageButton ibPlayPause, ibClose;

    private PlaybackUiState lastState;
    private Boolean hasInternet = null;

    @Nullable @Override
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

        vm = new ViewModelProvider(requireActivity()).get(PlaybackViewModel.class);
        vm.getState().observe(getViewLifecycleOwner(), s -> {
            if (s == null) {
                myLog("vm.getState().observe : s == null");
                return;
            }

            //myLogD(s.toString());
            //myLogI("vm.getState().observe " + s);

            if (lastState==null || lastState.cover==null || (s.cover!=null && !lastState.cover.equals(s.cover))) {
                myLogD("gliding cover image");
                Glide.with(ivCover.getContext())
                        .load(s.cover)
                        .placeholder(R.drawable.ic_radio_24px)
                        .error(R.drawable.ic_radio_24px)
                        .into(ivCover);
            }

            if (lastState!=null && lastState.playing != s.playing) myLogD("playing changed => " + s.playing);
            lastState = vm.getState().getValue();
            refreshUi();
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

        ibPlayPause.setOnClickListener(_v -> { myLogI("---- user press PlayPause button ----"); vm.playPause(); });
        ibClose.setOnClickListener(_v -> { myLogI("---- user press CLOSE button ----"); vm.stop(); });

        v.setOnClickListener(_x -> {
            myLogI("---- user press mini player ----");
            if (vm.getState() != null && vm.getState().getValue() != null) {
                String radioStationUuid = vm.getState().getValue().radioStationUuid;
                if (radioStationUuid!=null) {
                    myLogD("radioStationUuid = " + radioStationUuid);
                    Intent intent = new Intent(requireContext(), RadioStationActivity.class);
                    intent.putExtra(Intents.EXTRA_STATION_UUID, radioStationUuid);
                    intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP); // dont open it multiple time... NOT SURE CLEAR TOP is really usefull...
                    startActivity(intent);
                } else {
                    myLogE("radioStationUuid is NULL");
                    startActivity(new Intent(requireContext(), GetRadioActivity.class));
                }
            } else {
                myLog("no VM state");
                startActivity(new Intent(requireContext(), GetRadioActivity.class));
            }
        });
    }

    private void refreshUi() {
        if (lastState == null || hasInternet == null) return;
        UiHelper.FillUiBasic(lastState, progressBar, ibPlayPause, tvTitle, tvSubTitle, null, null, null, ivNoInternet, hasInternet);
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
