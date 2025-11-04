package com.driot.bookplayer.player;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.ViewModelProvider;

import com.driot.bookplayer.R;
import com.driot.bookplayer.utils.log.LoggingFragment;

import java.util.Objects;

public class MiniNowPlayingHostFragment extends LoggingFragment {

    private String lastPlayType;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup c, @Nullable Bundle b) {
        // Simple container for the child fragment
        return inf.inflate(R.layout.fragment_mini_host_container, c, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle b) {
        PlaybackViewModel vm = new ViewModelProvider(requireActivity()).get(PlaybackViewModel.class);

        myLog(vm.toString());

        //Observer
        vm.getPlayMode().observe(getViewLifecycleOwner(), newPlayType -> {
            myLog("vm.getPlayType().observe: " + newPlayType);
            if (!Objects.equals(newPlayType, lastPlayType)) {
                swapChild(newPlayType);
                lastPlayType = newPlayType;
            }
        });

        // Initial attach (covers first frame before observer fires)
        String firstPlayType = vm.getPlayMode().getValue();
        if (firstPlayType != null) {
            lastPlayType = firstPlayType;
        }
        attachFirstChild(firstPlayType);
    }

    private void attachFirstChild(String playType) {
        myLog("attachFirstChild, playType: " + playType);
        final Fragment child;
        if ("radio".equals(playType)) {
            child = new RadioMiniNowPlayingFragment();
        } else {
            child = new MiniNowPlayingFragment();
        }
        getChildFragmentManager().beginTransaction()
                .replace(R.id.mini_host_container, child, playType)
                .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
                .commitNowAllowingStateLoss();
    }

    private void swapChild(String playType) {
        myLog("swapChild, playType: " + playType);
        Fragment current = getChildFragmentManager().findFragmentById(R.id.mini_host_container);
        if (current != null && playType.equals(current.getTag())) return; // already correct

        Fragment child;
        if ("radio".equals(playType)) {
            child = new RadioMiniNowPlayingFragment();
        } else {
            child = new MiniNowPlayingFragment();
        }
        getChildFragmentManager().beginTransaction()
                .setReorderingAllowed(true)
                .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
                .replace(R.id.mini_host_container, child, playType)
                .commitAllowingStateLoss();
    }
}
