package com.driot.bookplayer.nav;

import android.content.Intent;
import android.content.res.Resources;

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

    private int currentAppNavId = R.id.nav_library;
    private final Map<Integer, Intent> lastIntents = new HashMap<>();
    private final Resources resources;

    @Inject
    public NavState(Resources resources) {
        this.resources = resources;
    }

    public int getCurrentAppNavId() {
        return currentAppNavId;
    }

    public void setCurrentAppNavId(int id) {
        this.currentAppNavId = id;
    }

    public void setLastIntent(int navId, Intent intent) {
        lastIntents.put(navId, intent);
    }

    public Intent getLastIntent(int navId) {
        return lastIntents.get(navId);
    }

    private Resources getResources() {
        return resources;
    }

    public String getCurrentNavName() {
        return getNavName(currentAppNavId);
    }

    /**
     * Returns the resource name for any given navigation ID.
     * Useful if you need the name for a specific ID.
     */
    public String getNavName(int navId) {
        try {
            // This gets the actual name defined in res/values/ids.xml or wherever the IDs are declared
            return getResources().getResourceEntryName(navId);
        } catch (Exception e) {
            // Fallback in case the ID is not found
            return "unknown_nav_" + navId;
        }
    }

}
