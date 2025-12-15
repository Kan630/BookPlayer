package com.driot.bookplayer.utils;

import android.net.ConnectivityManager;
import android.net.Network;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class NetworkMonitorRepository {

    private final MutableLiveData<NetworkStatus> liveData = new MutableLiveData<>();
    private long offlineSince = -1L;

    @Inject
    public NetworkMonitorRepository(ConnectivityManager cm) {

        cm.registerDefaultNetworkCallback(new ConnectivityManager.NetworkCallback() {

            @Override public void onAvailable(@NonNull Network network) {
                offlineSince = -1L;
                liveData.postValue(new NetworkStatus(true, -1));
            }

            @Override public void onLost(@NonNull Network network) {
                if (offlineSince < 0) {
                    offlineSince = System.currentTimeMillis();
                }
                liveData.postValue(new NetworkStatus(false, offlineSince));
            }
        });
    }

    public LiveData<NetworkStatus> getStatus() {
        return liveData;
    }
}

