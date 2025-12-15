package com.driot.bookplayer.helpers;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LifecycleOwner;

import com.driot.bookplayer.R;
import com.driot.bookplayer.utils.NetworkStatus;
import com.driot.bookplayer.utils.NetworkStatusViewModel;
import com.driot.bookplayer.utils.Tonio;

public class NetworkStatusRowController {

    private final Context context;
    private final View root;
    private final TextView tvStatus;
    private final ImageView ivDot;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private boolean hasInternet = true;
    private long offlineSinceMillis = -1L;

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            updateElapsedText();
            handler.postDelayed(this, 1000);
        }
    };

    public NetworkStatusRowController(
            @NonNull Context context,
            @Nullable View root,
            @NonNull LifecycleOwner owner,
            @NonNull NetworkStatusViewModel vm
    ) {
        this.context = context;
        this.root = root;

        if (root != null) {
            ivDot = root.findViewById(R.id.ivNetworkDot);
            tvStatus = root.findViewById(R.id.tvNetworkStatus);
        } else {
            ivDot = null;
            tvStatus = null;
        }

        vm.getStatus().observe(owner, this::onStatusChanged);
    }

    private void onStatusChanged(NetworkStatus status) {
        hasInternet = status.hasInternet;
        offlineSinceMillis = status.offlineSinceMillis;

        if (hasInternet) {
            root.setVisibility(View.GONE);
            handler.removeCallbacks(ticker);
        } else {
            root.setVisibility(View.VISIBLE);
            handler.post(ticker);
        }

        updateElapsedText();
    }

    private void updateElapsedText() {
        if (tvStatus == null || hasInternet || offlineSinceMillis < 0) return;

        long now = System.currentTimeMillis();
        long segMillis = now - offlineSinceMillis;
        if (segMillis < 0) segMillis = 0;

        String txt = context.getString(R.string.Offline_for) + " " + Tonio.formatTime(segMillis);

        tvStatus.setText(txt);

        if (ivDot != null) {
            ivDot.setImageResource(R.drawable.ic_dot_red);
        }
    }
}
