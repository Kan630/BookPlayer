package com.driot.bookplayer.player;

import android.os.Build;
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
import com.driot.bookplayer.radio.MiniPlayRadioFragment;
import com.driot.bookplayer.podcasts.MiniPlayPodcastFragment;

import java.util.Objects;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class MiniPlayHostFragment extends LoggingFragment {

    private View root;

    private String lastPlayType;
    private PlaybackViewModel vm;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup c, @Nullable Bundle b) {
        return inf.inflate(R.layout.fragment_mini_play_host_container, c, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle b) {
        vm = new ViewModelProvider(requireActivity()).get(PlaybackViewModel.class);
        root = v.findViewById(R.id.mini_host_container);

        // Initial state: hide until we have a playMode
        root.setVisibility(View.GONE);

        //Observer
        vm.getState().observe(getViewLifecycleOwner(), newState -> {
            if (newState == null || newState.playMode == null || newState.playMode.isEmpty()) {
                setGone();
                lastPlayType = null;
                return;
            }

            String newPlayType = newState.playMode;

            if (!Objects.equals(newPlayType, lastPlayType)) {
                //myLogD("vm.getPlayType().observe: newPlayType=[" + newPlayType + "] - lastPlayType=[" + lastPlayType + "]");
                swapChild(newPlayType);
            } else {
                //myLogD("vm.getPlayType().observe: same as before newPlayType=[" + newPlayType + "]");

                //check current display
                Fragment current = getChildFragmentManager().findFragmentById(R.id.mini_host_container);
                if (current == null) {
                    //myLogD("same playType, no fragment attached, re-attaching");
                    attachFirstChild(newPlayType);
                } else {
                    boolean ok =
                            ("radio".equals(newPlayType)   && current instanceof MiniPlayRadioFragment) ||
                                    ("podcast".equals(newPlayType) && current instanceof MiniPlayPodcastFragment) ||
                                    (("book".equals(newPlayType) || "tts".equals(newPlayType))
                                            && current instanceof MiniPlayBookFragment);

                    if (!ok) {
                        //myLog("playType=[" + newPlayType + "] but fragment=[" + current.getClass().getSimpleName() + "], swapping");
                        swapChild(newPlayType);
                    }
                }
            }
            lastPlayType = newPlayType;
        });

        // Initial attach (covers first frame before observer fires)
        if (vm.getState().getValue() != null) {
            String firstPlayType = vm.getState().getValue().playMode;
            if (firstPlayType != null && !firstPlayType.isEmpty()) {
                attachFirstChild(firstPlayType);
                lastPlayType = firstPlayType;
            } else {
                setGone();
            }
        } else {
            setGone();
        }
    }

    private void attachFirstChild(String playType) {
        //myLog("attachFirstChild, playType: " + playType);
        final Fragment child;
        if ("radio".equals(playType)) {
            child = new MiniPlayRadioFragment();
        } else if ("podcast".equals(playType)) {
            child = new MiniPlayPodcastFragment();
        } else if ("book".equals(playType) || "tts".equals(playType)) {
            child = new MiniPlayBookFragment();
        } else {
            //myLogD("attachFirstChild - unknown playType: " + playType);
            setGone();
            return;
        }
        getChildFragmentManager().beginTransaction()
                .replace(R.id.mini_host_container, child)
                .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
                .commitNowAllowingStateLoss();
        setVisible();
    }

    private void swapChild(String playType) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && getActivity()!=null) {
            myLog("swapChild, playType: " + playType + "   on activity " + getActivity().getOpPackageName());
        } else {
            myLog("swapChild, playType: " + playType);
        }

        Fragment child;
        if ("radio".equals(playType)) {
            child = new MiniPlayRadioFragment();
        } else if ("podcast".equals(playType)) {
            child = new MiniPlayPodcastFragment();
        } else if ("book".equals(playType) || "tts".equals(playType)) {
            child = new MiniPlayBookFragment();
        } else {
            myLogD("swapChild - unknown playType: " + playType);
            setGone();
            return;
        }
        getChildFragmentManager().beginTransaction()
                .setReorderingAllowed(true)
                .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
                .replace(R.id.mini_host_container, child)
                .commitAllowingStateLoss();
        setVisible();
    }

    private void setGone() {
        if (root.getVisibility()==View.VISIBLE) myLog("---- setGone ----");
        root.setVisibility(View.GONE);
    }
    private void setVisible() {
        if (root.getVisibility()==View.GONE) myLog("---- setVisible ----");
        root.setVisibility(View.VISIBLE);
    }

}
