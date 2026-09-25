package com.driot.bookplayer.settings.ui;

import android.os.Bundle;

import androidx.annotation.IdRes;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

/**
 * Activity-scoped hand-off from MainActivity to SettingsCategoryListFragment: a settings direct
 * link (MainActivity.startSettings(), e.g. Radio's gear icon) asks the two-pane list to show a
 * category, whether or not the list fragment exists yet. The list consumes the request.
 */
public class SettingsPaneViewModel extends ViewModel {

    public static final class Request {
        @IdRes public final int destinationId;
        @Nullable public final Bundle args;

        Request(@IdRes int destinationId, @Nullable Bundle args) {
            this.destinationId = destinationId;
            this.args = args;
        }
    }

    private final MutableLiveData<Request> request = new MutableLiveData<>();

    public LiveData<Request> getRequest() {
        return request;
    }

    public void show(@IdRes int destinationId, @Nullable Bundle args) {
        request.setValue(new Request(destinationId, args));
    }

    public void consume() {
        request.setValue(null);
    }
}
