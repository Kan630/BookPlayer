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
    private final Map<Integer, java.util.Deque<Intent>> navStacks = new HashMap<>();

    @Inject
    public NavState() {
    }

    public int getCurrentBottomNavId() {
        return currentBottomNavId;
    }

    public void setCurrentBottomNavId(int id) {
        this.currentBottomNavId = id;
    }

    public void push(int navId, Intent intent) {
        navStacks.computeIfAbsent(navId, k -> new java.util.ArrayDeque<>()).push(intent);
    }

    public Intent pop(int navId) {
        java.util.Deque<Intent> stack = navStacks.get(navId);
        if (stack == null || stack.isEmpty()) return null;

        // remove current
        stack.pop();

        // return previous
        return stack.peek();
    }

    public boolean hasBack(int navId) {
        java.util.Deque<Intent> stack = navStacks.get(navId);
        return stack != null && stack.size() > 1;
    }

    public Intent peek(int navId) {
        java.util.Deque<Intent> stack = navStacks.get(navId);
        if (stack == null || stack.isEmpty()) return null;
        return stack.peek();
    }

}
