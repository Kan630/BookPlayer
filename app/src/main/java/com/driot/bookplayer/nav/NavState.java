package com.driot.bookplayer.nav;

import android.content.Intent;
import com.driot.bookplayer.R;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Tracks the current navigation state (which tab is active) and the last used intent for each tab
 * to allow restoring the full activity stack when switching back to a tab.
 */
@Singleton
public class NavState {

    private int currentBottomNavId = R.id.nav_library;
    private final Map<Integer, Intent> lastIntents = new HashMap<>();

    @Inject
    public NavState() {
    }

    public int getCurrentBottomNavId() {
        return currentBottomNavId;
    }

    public void setCurrentBottomNavId(int id) {
        this.currentBottomNavId = id;
    }

    public void setLastIntent(int navId, Intent intent) {
        lastIntents.put(navId, intent);
    }

    public Intent getLastIntent(int navId) {
        return lastIntents.get(navId);
    }

    // Legacy helpers for compatibility during migration
    public Intent getLastRadioIntent() { return getLastIntent(R.id.nav_radio); }
    public Intent getLastPodcastIntent() { return getLastIntent(R.id.nav_podcast); }
}
