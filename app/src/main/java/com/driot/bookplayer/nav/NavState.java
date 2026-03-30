package com.driot.bookplayer.nav;

import android.content.Context;
import android.content.Intent;
import com.driot.bookplayer.R;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;

import static com.driot.bookplayer.nav.NavHelper.VERBOSE_DEBUG;
import static com.driot.bookplayer.utils.log.LoggerStaticHelper.*;

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

    /*
    public void push(int navId, Intent intent) {
        navStacks.computeIfAbsent(navId, k -> new java.util.ArrayDeque<>()).push(intent);
        myLogDD(navId + " pushing " + intent);
    }
     */

    public void push(int navId, Intent newIntent) {
        Deque<Intent> stack = navStacks.computeIfAbsent(navId, k -> new ArrayDeque<>());

        if (!stack.isEmpty()) {
            Intent top = stack.peek();

            if (top != null && top.getComponent() != null
                    && top.getComponent().equals(newIntent.getComponent())) {

                myLogDD(navId + " same activity already at top → skipping push");
                return;
            }
        }

        stack.push(newIntent);
        myLogDD(navId + " pushed");
    }

    public Intent pop(int navId) {
        java.util.Deque<Intent> stack = navStacks.get(navId);
        if (stack == null || stack.isEmpty()) return null;

        // remove current
        stack.pop();
        myLogDD(navId + " popping");

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

    public void clear(int navId) {
        java.util.Deque<Intent> stack = navStacks.get(navId);
        if (stack != null) stack.clear();
        myLogDD(navId + " clear");
    }

    public String getSectionName(Context ctx, int navId) {
        try {
            return ctx.getResources().getResourceEntryName(navId);
        } catch (Exception e) {
            return "error: " + e.getMessage();
        }
    }

    private static void myLogDD(String txt) {
        if (VERBOSE_DEBUG)
            myLogD(txt);
    }

}
