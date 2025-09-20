package com.driot.bookplayer.objects;

import android.content.Intent;

import androidx.annotation.IdRes;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;

import com.driot.bookplayer.activities.OngoingTaskFragment;

public final class OngoingTaskHost {
    private OngoingTaskHost() {}

    public static void attach(FragmentActivity act, @IdRes int containerId, @Nullable Intent clickIntent) {
        if (act.getSupportFragmentManager().findFragmentByTag("ongoing") == null) {
            act.getSupportFragmentManager().beginTransaction()
                    .replace(containerId, OngoingTaskFragment.newInstance(clickIntent), "ongoing")
                    .commitNowAllowingStateLoss();
        }
    }
}
