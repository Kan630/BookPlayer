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

public class MiniNowPlayingHostFragment extends Fragment {

    private PlaybackViewModel vm;
    private boolean lastIsRadio = false; // default

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup c, @Nullable Bundle b) {
        // Simple container for the child fragment
        return inf.inflate(R.layout.fragment_mini_host_container, c, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle b) {
        vm = new ViewModelProvider(requireActivity()).get(PlaybackViewModel.class);

        // If your VM already exposes isRadio:
        vm.getIsRadio().observe(getViewLifecycleOwner(), isRadio -> {
            boolean target = Boolean.TRUE.equals(isRadio);
            if (target != lastIsRadio) {
                swapChild(target);
                lastIsRadio = target;
            }
        });

        // Initial attach (covers first frame before observer fires)
        Boolean isRadio = vm.getIsRadio().getValue();
        if (isRadio != null) {
            lastIsRadio = isRadio;
        }
        attachFirstChild(lastIsRadio);
    }

    private void attachFirstChild(boolean isRadio) {
        final Fragment child = isRadio
                ? new RadioMiniNowPlayingFragment()
                : new MiniNowPlayingFragment();
        getChildFragmentManager().beginTransaction()
                .replace(R.id.mini_host_container, child, isRadio ? "radio" : "general")
                .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
                .commitNowAllowingStateLoss();
    }

    private void swapChild(boolean isRadio) {
        final String wantTag = isRadio ? "radio" : "general";
        Fragment current = getChildFragmentManager().findFragmentById(R.id.mini_host_container);
        if (current != null && wantTag.equals(current.getTag())) return; // already correct

        Fragment child = getChildFragmentManager().findFragmentByTag(wantTag);
        if (child == null) {
            child = isRadio ? new RadioMiniNowPlayingFragment() : new MiniNowPlayingFragment();
        }
        getChildFragmentManager().beginTransaction()
                .setReorderingAllowed(true)
                .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
                .replace(R.id.mini_host_container, child, wantTag)
                .commitAllowingStateLoss();
    }
}
