package com.driot.bookplayer.di;

import com.driot.bookplayer.tts.AppTtsManager;

import dagger.hilt.EntryPoint;
import dagger.hilt.InstallIn;
import dagger.hilt.components.SingletonComponent;

@EntryPoint
@InstallIn(SingletonComponent.class)
public interface TtsEntryPoint {
    AppTtsManager getAppTtsManager();
}
