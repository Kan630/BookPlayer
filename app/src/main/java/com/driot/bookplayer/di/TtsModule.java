package com.driot.bookplayer.di;

import com.driot.bookplayer.tts.AppTtsManager;

import dagger.Binds;
import dagger.Module;
import dagger.hilt.InstallIn;
import dagger.hilt.components.SingletonComponent;

@Module
@InstallIn(SingletonComponent.class)
public abstract class TtsModule {
    // No binding needed if using @Inject constructor on the class itself
    // and it is not an interface. AppTtsManager is a concrete class.
}
