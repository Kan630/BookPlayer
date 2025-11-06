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

public class MiniPlayHostFragment extends LoggingFragment {

    private String lastPlayType;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup c, @Nullable Bundle b) {
        // Simple container for the child fragment
        return inf.inflate(R.layout.fragment_mini_play_host_container, c, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle b) {
        PlaybackViewModel vm = new ViewModelProvider(requireActivity()).get(PlaybackViewModel.class);

        /*
        try {
            myLog(vm.getState().getValue().toString());
        } catch (Exception e) {
            myLogE(e.getMessage());
        }

         */

        //Observer
        vm.getPlayMode().observe(getViewLifecycleOwner(), newPlayType -> {
            myLogW("vm.getPlayType().observe: newPlayType=[" + newPlayType + "] - lastPlayType=[" + lastPlayType + "]");
            if (!Objects.equals(newPlayType, lastPlayType)) {
                swapChild(newPlayType);
            } else {
                //check current display
                Fragment current = getChildFragmentManager().findFragmentById(R.id.mini_host_container);
                if (current == null) {
                    myLogW("should not happen, no fragment attached, re-attaching");
                    attachFirstChild(newPlayType);
                } else{
                    if (!Objects.equals(newPlayType, current.getTag())) {
                        myLogW("should not happen, wrong saved play type : [" + current.getTag() + "], swapping");
                        swapChild(newPlayType);
                    }
                }
            }
            lastPlayType = newPlayType;
        });

        // Initial attach (covers first frame before observer fires)
        String firstPlayType = "book";
        if (vm.getState().getValue() != null) {
            firstPlayType = vm.getState().getValue().playMode;
        }

        attachFirstChild(firstPlayType);
        lastPlayType = firstPlayType;
    }

    private void attachFirstChild(String playType) {
        myLogI("attachFirstChild, playType: " + playType);
        final Fragment child;
        if ("radio".equals(playType)) {
            child = new MiniPlayRadioFragment();
        } else if ("podcast".equals(playType)) {
            child = new MiniPlayPodcastFragment();
        } else {
            child = new MiniPlayBookFragment();
        }
        getChildFragmentManager().beginTransaction()
                .replace(R.id.mini_host_container, child, playType)
                .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
                .commitNowAllowingStateLoss();
    }

    private void swapChild(String playType) {
        myLogI("swapChild, playType: " + playType);
        Fragment current = getChildFragmentManager().findFragmentById(R.id.mini_host_container);
        //if (current != null && !Objects.equals(playType, current.getTag())) return;

        Fragment child;
        if ("radio".equals(playType)) {
            child = new MiniPlayRadioFragment();
        } else if ("podcast".equals(playType)) {
            child = new MiniPlayPodcastFragment();
        } else {
            child = new MiniPlayBookFragment();
        }
        getChildFragmentManager().beginTransaction()
                .setReorderingAllowed(true)
                .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
                .replace(R.id.mini_host_container, child, playType)
                .commitAllowingStateLoss();
    }
}
