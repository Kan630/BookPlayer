package com.driot.bookplayer.helpers;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;

/**
 * Simple reusable internet connectivity monitor (API 26+).
 * Call start() / stop() from Activity/Fragment lifecycle.
 */
public class NetworkStatusMonitor {

    public interface Listener {
        /**
         * Called on main thread whenever internet connectivity changes.
         *
         * @param hasInternet true if at least one VALIDATED network with INTERNET exists
         */
        @MainThread
        void onStatusChanged(boolean hasInternet);
    }

    private final Context appContext;
    private final ConnectivityManager connectivityManager;
    private final Listener listener;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private boolean lastStatus = false;
    private boolean started = false;

    private final ConnectivityManager.NetworkCallback networkCallback =
            new ConnectivityManager.NetworkCallback() {

                @Override
                public void onAvailable(@NonNull Network network) {
                    notifyIfChanged();
                }

                @Override
                public void onLost(@NonNull Network network) {
                    notifyIfChanged();
                }

                @Override
                public void onCapabilitiesChanged(@NonNull Network network,
                                                  @NonNull NetworkCapabilities nc) {
                    notifyIfChanged();
                }
            };

    public NetworkStatusMonitor(@NonNull Context context,
                                @NonNull Listener listener) {
        this.appContext = context.getApplicationContext();
        this.listener = listener;
        this.connectivityManager = (ConnectivityManager)
                appContext.getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    /**
     * Start listening to connectivity changes.
     * Safe to call from onStart().
     */
    public void start() {
        if (started || connectivityManager == null) return;
        started = true;

        // API 24+ – default network callback for overall connectivity
        connectivityManager.registerDefaultNetworkCallback(networkCallback);

        // Immediately push current state
        final boolean now = hasInternet();
        lastStatus = now;
        mainHandler.post(() -> listener.onStatusChanged(now));
    }

    /**
     * Stop listening. Safe to call from onStop().
     */
    public void stop() {
        if (!started || connectivityManager == null) return;
        started = false;
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback);
        } catch (Exception ignored) {
            // Already unregistered / activity destroyed, ignore
        }
    }

    /**
     * Check current internet state.
     */
    public boolean hasInternet() {
        if (connectivityManager == null) return false;

        Network active = connectivityManager.getActiveNetwork();
        if (active == null) return false;

        NetworkCapabilities caps = connectivityManager.getNetworkCapabilities(active);
        return caps != null
                && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
    }

    private void notifyIfChanged() {
        final boolean now = hasInternet();
        if (now == lastStatus) return;
        lastStatus = now;
        mainHandler.post(() -> listener.onStatusChanged(now));
    }
}
