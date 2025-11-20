package com.driot.bookplayer.helpers;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.driot.bookplayer.R;

/**
 * Controls the "network status row" view:
 * - monitors internet connectivity (API 26+)
 * - updates dot (green/red)
 * - shows "Online/Offline for Xs · YY%"
 *   where YY is percentage of time online since this controller started
 * - toggles unmetered / roaming icons
 */
public class NetworkStatusRowController {

    private final Context context;
    private final ConnectivityManager connectivityManager;
    private final View root;
    private final ImageView ivDot;
    private final TextView tvStatus;
    private final ImageView ivUnmetered;
    private final ImageView ivRoaming;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private boolean started = false;

    // Current states
    private boolean hasInternet = false;
    private boolean isUnmetered = false;
    private boolean isRoaming = false;

    // Time tracking (millis)
    private long sessionStartMillis = System.currentTimeMillis();
    private long lastOnlineOfflineChangeMillis = sessionStartMillis; // when we last switched online<->offline
    private long onlineAccumMillis = 0L; // online time up to lastOnlineOfflineChangeMillis

    private final ConnectivityManager.NetworkCallback networkCallback =
            new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(@NonNull Network network) {
                    onNetworkStateMayHaveChanged();
                }

                @Override
                public void onLost(@NonNull Network network) {
                    onNetworkStateMayHaveChanged();
                }

                @Override
                public void onCapabilitiesChanged(@NonNull Network network,
                                                  @NonNull NetworkCapabilities nc) {
                    onNetworkStateMayHaveChanged();
                }
            };

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            if (!started) return;
            updateElapsedText();  // seconds + percentage
            mainHandler.postDelayed(this, 1000);
        }
    };

    public NetworkStatusRowController(@NonNull Context context, @Nullable View root) {
        this.context = context.getApplicationContext();
        this.root = root;
        this.connectivityManager = (ConnectivityManager)
                this.context.getSystemService(Context.CONNECTIVITY_SERVICE);

        if (root != null) {
            ivDot = root.findViewById(R.id.ivNetworkDot);
            tvStatus = root.findViewById(R.id.tvNetworkStatus);
            ivUnmetered = root.findViewById(R.id.ivUnmetered);
            ivRoaming = root.findViewById(R.id.ivRoaming);
        } else {
            ivDot = null;
            tvStatus = null;
            ivUnmetered = null;
            ivRoaming = null;
        }
    }

    public void start() {
        if (started || connectivityManager == null) return;
        started = true;

        long now = System.currentTimeMillis();
        sessionStartMillis = now;
        lastOnlineOfflineChangeMillis = now;
        onlineAccumMillis = 0L;

        try {
            // On API 24+ this listens to overall default network
            connectivityManager.registerDefaultNetworkCallback(networkCallback);
        } catch (Exception ignored) {
        }

        // Initial state
        refreshStateFromSystem(/*isInitial=*/true);
        applyToViews();

        // Start ticking
        mainHandler.post(ticker);
    }

    public void stop() {
        if (!started || connectivityManager == null) return;
        started = false;

        try {
            connectivityManager.unregisterNetworkCallback(networkCallback);
        } catch (Exception ignored) {
        }

        mainHandler.removeCallbacks(ticker);
    }

    // Expose to activity if needed
    public boolean hasInternet()  { return hasInternet; }
    public boolean isUnmetered()  { return isUnmetered; }
    public boolean isRoaming()    { return isRoaming; }

    // --- Internal helpers ---

    private void onNetworkStateMayHaveChanged() {
        long now = System.currentTimeMillis();

        // Keep previous "online" status to see if we toggled
        boolean prevOnline = hasInternet;

        // Recompute from system
        refreshStateFromSystem(/*isInitial=*/false);

        // Only when ONLINE <-> OFFLINE changes do we adjust time segments
        if (prevOnline != hasInternet) {
            // Close previous segment
            long delta = now - lastOnlineOfflineChangeMillis;
            if (delta < 0) delta = 0;

            if (prevOnline) {
                // We were online, now going offline => accumulate that online segment
                onlineAccumMillis += delta;
            }

            // Start new segment (online or offline)
            lastOnlineOfflineChangeMillis = now;
        }

        mainHandler.post(this::applyToViews);
    }

    private void refreshStateFromSystem(boolean isInitial) {
        hasInternet = false;
        isUnmetered = false;
        isRoaming = false;

        if (connectivityManager == null) return;

        Network active = connectivityManager.getActiveNetwork();
        if (active == null) {
            // no active network → offline
            return;
        }

        NetworkCapabilities caps = connectivityManager.getNetworkCapabilities(active);
        if (caps == null) {
            return;
        }

        hasInternet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);

        isUnmetered = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED);

        boolean notRoaming = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING);
        isRoaming = hasInternet && !notRoaming;
    }

    private void applyToViews() {
        if (root == null) return;

        // Hide row when online, show only when offline
        root.setVisibility(hasInternet ? View.GONE : View.VISIBLE);

        if (ivDot != null) {
            ivDot.setImageResource(
                    hasInternet ? R.drawable.ic_dot_green : R.drawable.ic_dot_red
            );
            ivDot.setContentDescription(
                    hasInternet
                            ? context.getString(R.string.internet_status)
                            : context.getString(R.string.no_internet_connection)
            );
        }

        updateElapsedText();

        if (ivUnmetered != null) {
            ivUnmetered.setVisibility(isUnmetered ? View.VISIBLE : View.GONE);
        }

        if (ivRoaming != null) {
            ivRoaming.setVisibility(isRoaming ? View.VISIBLE : View.GONE);
        }
    }

    private void updateElapsedText() {
        if (tvStatus == null) return;

        long now = System.currentTimeMillis();

        // Seconds since current online/offline state started
        long segMillis = now - lastOnlineOfflineChangeMillis;
        if (segMillis < 0) segMillis = 0;
        int secondsSinceStatus = (int) (segMillis / 1000L);

        // Total session duration
        long totalMillis = now - sessionStartMillis;
        if (totalMillis < 0) totalMillis = 0;

        // Total online time = past segments + current one if online
        long effectiveOnlineMillis = onlineAccumMillis;
        if (hasInternet) {
            effectiveOnlineMillis += segMillis;
        }

        int percentOnline = 0;
        if (totalMillis > 0) {
            percentOnline = (int) Math.round(
                    (effectiveOnlineMillis * 100.0) / (double) totalMillis
            );
        }

        String txt;
        if (hasInternet) {
            txt = context.getString(
                    R.string.net_status_online_for_percent,
                    secondsSinceStatus,
                    percentOnline
            );
        } else {
            txt = context.getString(
                    R.string.net_status_offline_for_percent,
                    secondsSinceStatus,
                    percentOnline
            );
        }

        tvStatus.setText(txt);
    }
}
