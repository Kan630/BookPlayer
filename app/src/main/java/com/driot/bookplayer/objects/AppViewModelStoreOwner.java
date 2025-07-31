package com.driot.bookplayer.objects;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModelStore;
import androidx.lifecycle.ViewModelStoreOwner;

public class AppViewModelStoreOwner implements ViewModelStoreOwner {
    private static final ViewModelStore store = new ViewModelStore();
    private static final AppViewModelStoreOwner instance = new AppViewModelStoreOwner();

    private AppViewModelStoreOwner() {}

    public static AppViewModelStoreOwner getInstance() {
        return instance;
    }

    @NonNull
    @Override
    public ViewModelStore getViewModelStore() {
        return store;
    }

    public static void clear() {
        store.clear(); // This removes *all* ViewModels
    }
}