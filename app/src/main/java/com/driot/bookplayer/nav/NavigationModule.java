package com.driot.bookplayer.nav;

import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.components.SingletonComponent;
import javax.inject.Singleton;

@Module
@InstallIn(SingletonComponent.class)
public class NavigationModule {

    @Provides
    @Singleton
    public NavState provideNavState() {
        return new NavState();
    }
}
