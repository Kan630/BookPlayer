package com.driot.bookplayer.player;

import androidx.annotation.Nullable;

import com.driot.bookplayer.global.Pref;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static com.driot.bookplayer.utils.log.LoggerStaticHelper.*;

/**
 * What this device knows about a stream from having tried to play it: the only honest answer to
 * "is this station any good", since the directory's own check is just a ping from a server
 * somewhere. A stream that failed here is marked, which is what paints a result row red, and the
 * mark goes away the moment the same stream plays.
 *
 * Kept by url (what is actually played), not by station: the same stream is registered many times
 * over in radio-browser, and one failing url fails under every one of those names.
 *
 * Remembered across restarts, so a station that let you down is still marked tomorrow.
 */
public final class StreamHealth {

    private StreamHealth() {
    }

    /** Insertion-ordered so the oldest marks are the ones dropped when the list gets long. */
    private static final Set<String> failing = new LinkedHashSet<>();

    /** Why it failed, for the row and the toast. This one is not worth persisting. */
    private static final Map<String, String> lastError = new ConcurrentHashMap<>();

    /** A device that cannot play anything would otherwise grow this list forever. */
    private static final int MAX_REMEMBERED = 300;

    private static boolean loaded = false;

    private static synchronized void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        try {
            failing.addAll(Pref.getFailingStreams());
        } catch (Exception e) {
            myLogEE(e, "StreamHealth: could not read the remembered failures");
        }
    }

    /** This stream just failed to play. */
    public static synchronized void markFailed(@Nullable String url, @Nullable String error) {
        String key = key(url);
        if (key.isEmpty()) {
            return;
        }
        ensureLoaded();
        if (error != null) {
            lastError.put(key, error);
        }
        if (!failing.add(key)) {
            return; // already known as failing, nothing to write
        }
        while (failing.size() > MAX_REMEMBERED) {
            failing.remove(failing.iterator().next());
        }
        myLogI("stream marked as failing: " + key);
        Pref.setFailingStreams(failing);
    }

    /** This stream plays: whatever went wrong before is history. */
    public static synchronized void markOk(@Nullable String url) {
        String key = key(url);
        if (key.isEmpty()) {
            return;
        }
        ensureLoaded();
        lastError.remove(key);
        if (failing.remove(key)) {
            myLogI("stream plays again, failure mark cleared: " + key);
            Pref.setFailingStreams(failing);
        }
    }

    /** Whether this stream failed the last time it was tried on this device. */
    public static synchronized boolean isFailing(@Nullable String url) {
        String key = key(url);
        if (key.isEmpty()) {
            return false;
        }
        ensureLoaded();
        return failing.contains(key);
    }

    @Nullable
    public static String lastError(@Nullable String url) {
        return lastError.get(key(url));
    }

    private static String key(@Nullable String url) {
        return url == null ? "" : url.trim().toLowerCase(Locale.ROOT);
    }

    /** For a settings/debug screen: how many streams are currently marked. */
    public static synchronized int failingCount() {
        ensureLoaded();
        return failing.size();
    }

    public static synchronized void forget(Collection<String> urls) {
        ensureLoaded();
        boolean changed = false;
        for (String u : urls) {
            changed |= failing.remove(key(u));
        }
        if (changed) {
            Pref.setFailingStreams(failing);
        }
    }
}
