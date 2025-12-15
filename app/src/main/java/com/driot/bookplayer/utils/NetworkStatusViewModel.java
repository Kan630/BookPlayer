package com.driot.bookplayer.utils;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

@HiltViewModel
public class NetworkStatusViewModel extends ViewModel {

    private final LiveData<NetworkStatus> status;

    @Inject
    public NetworkStatusViewModel(NetworkMonitorRepository repo) {
        status = repo.getStatus();
    }

    public LiveData<NetworkStatus> getStatus() {
        return status;
    }
}
