package com.driot.bookplayer.nav;

import android.content.Context;
import android.content.Intent;
import com.driot.bookplayer.R;
import com.driot.bookplayer.activities.AddResourceActivity;
import com.driot.bookplayer.imports.ImportBookSingleActivity;
import com.driot.bookplayer.imports.ImportBookMultipleActivity;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
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

    private int currentAppNavBarId = R.id.nav_library;
    private final Map<Integer, java.util.Deque<Intent>> navStacks = new HashMap<>();

    @Inject
    public NavState() {
    }

    public int getCurrentAppNavBarId() {
        return currentAppNavBarId;
    }

    public void setCurrentAppNavBarId(int id) {
        this.currentAppNavBarId = id;
    }

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

    /**
     * Removes AddResourceActivity, ImportBookSingleActivity and ImportBookMultipleActivity
     * from the nav_add stack, leaving any other entries intact.
     */
    public void removeAddBookNavSpecial() {
        Deque<Intent> stack = navStacks.get(R.id.nav_add);
        myLogDD("removeAddBookNavSpecial init, stack size=" + (stack == null ? 0 : stack.size()));
        if (stack == null || stack.isEmpty()) return;

        Set<String> toRemove = new HashSet<>(Arrays.asList(
                AddResourceActivity.class.getName(),
                ImportBookSingleActivity.class.getName(),
                ImportBookMultipleActivity.class.getName()
        ));

        stack.removeIf(intent ->
                intent.getComponent() != null
                && toRemove.contains(intent.getComponent().getClassName())
        );
        myLogDD("removeAddBookNavSpecial done, remaining stack size=" + stack.size());
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
