package com.driot.bookplayer.utils;

import android.content.Context;
import android.net.ConnectivityManager;

import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.android.qualifiers.ApplicationContext;
import dagger.hilt.components.SingletonComponent;
import javax.inject.Singleton;

@Module
@InstallIn(SingletonComponent.class)
public class NetworkModule {

    @Provides
    @Singleton
    ConnectivityManager provideConnectivityManager(
            @ApplicationContext Context context) {
        return (ConnectivityManager)
                context.getSystemService(Context.CONNECTIVITY_SERVICE);
    }
}
