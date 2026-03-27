package com.driot.bookplayer.nav;

import android.content.Intent;

/**
 * Singleton to store in-memory navigation state (last used intents for radio and podcasts).
 * This ensures that when switching via the bottom nav bar, we can return to the last-used
 * activity of a section (e.g. search results) instead of resetting to the root activity.
 */
public class NavState {
    private static NavState instance;

    private Intent lastRadioIntent;
    private Intent lastPodcastIntent;

    private int currentBottomNavId;

    private NavState() {}

    public static synchronized NavState getInstance() {
        if (instance == null) {
            instance = new NavState();
        }
        return instance;
    }

    public int getCurrentBottomNavId() {
        return currentBottomNavId;
    }

    public void setCurrentBottomNavId(int currentBottomNavId) {
        this.currentBottomNavId = currentBottomNavId;
    }

    public void setLastIntent(int navId, Intent intent) {
        if (navId == com.driot.bookplayer.R.id.nav_radio) {
            setLastRadioIntent(intent);
        } else if (navId == com.driot.bookplayer.R.id.nav_podcast) {
            setLastPodcastIntent(intent);
        }
    }

    public Intent getLastRadioIntent() {
        return lastRadioIntent;
    }

    public void setLastRadioIntent(Intent intent) {
        this.lastRadioIntent = intent;
    }

    public Intent getLastPodcastIntent() {
        return lastPodcastIntent;
    }

    public void setLastPodcastIntent(Intent intent) {
        this.lastPodcastIntent = intent;
    }
}
