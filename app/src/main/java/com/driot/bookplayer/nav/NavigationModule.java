package com.driot.bookplayer.nav;

import android.app.Application;
import android.content.res.Resources;
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
    Resources provideResources(Application application) {
        return application.getResources();
    }

    @Provides
    @Singleton
    NavState provideNavState(Resources resources) {
        return new NavState(resources);
    }
}