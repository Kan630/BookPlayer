package com.driot.bookplayer.podcasts;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;

import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.net.NetUtils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.Request;
import okhttp3.Response;

import static com.driot.bookplayer.utils.log.LoggerStaticHelper.*;

/**
 * How big an episode is, when its feed does not say.
 *
 * Most feeds declare the enclosure length and the list shows it straight away; some (TED Talks
 * among them) publish length="0", and then the only way to know is to ask the server. That costs a
 * request, so it is done for the few episodes where the number is actually worth something: the
 * one being played, the ones being downloaded, and the newest few of a list.
 *
 * What is learned is written onto the Episode row, so it is known for good and shows up like any
 * other feed's size. A failed probe is remembered for the session, so a list does not hammer a
 * server that will not answer.
 */
public final class EnclosureSizeProbe {

    private EnclosureSizeProbe() {
    }

    /** How many of a list's newest episodes are worth a request. */
    public static final int NEWEST_TO_PROBE = 3;

    /** url -> bytes; 0 means "asked, got nothing" and is not asked again this session. */
    private static final Map<String, Long> known = new ConcurrentHashMap<>();

    /** One at a time: this is a nicety, not something to flood a CDN for. */
    private static final ExecutorService io = Executors.newSingleThreadExecutor();

    private static final Handler main = new Handler(Looper.getMainLooper());

    /**
     * Learns this episode's size if the feed did not give one, then hands it back on the main
     * thread. Does nothing at all when the size is already known.
     *
     * @param onLearned run on the main thread once the episode's enclosureLength is filled in.
     */
    public static void ensureKnown(Context context, DisplayableEpisode episode,
            @Nullable Runnable onLearned) {
        if (episode == null || episode.enclosureLength > 0) {
            return;
        }
        final String url = episode.enclosureUrl;
        if (url == null || url.isEmpty()) {
            return;
        }
        Long cached = known.get(url);
        if (cached != null) {
            if (cached > 0) {
                episode.enclosureLength = cached;
                if (onLearned != null) {
                    main.post(onLearned);
                }
            }
            return; // 0 = asked already, nothing to be had
        }
        final Context appCtx = context.getApplicationContext();
        io.execute(() -> {
            long bytes = probe(appCtx, url);
            known.put(url, bytes);
            if (bytes <= 0) {
                return;
            }
            episode.enclosureLength = bytes;
            remember(appCtx, url, bytes);
            if (onLearned != null) {
                main.post(onLearned);
            }
        });
    }

    /** Same for an episode we only have the url of (a download being queued). */
    public static void ensureKnown(Context context, @Nullable String url) {
        if (url == null || url.isEmpty() || known.containsKey(url)) {
            return;
        }
        final Context appCtx = context.getApplicationContext();
        io.execute(() -> {
            long bytes = probe(appCtx, url);
            known.put(url, bytes);
            if (bytes > 0) {
                remember(appCtx, url, bytes);
            }
        });
    }

    /** Writes a size onto the episode it belongs to. Used by the probe, and by the download
     * worker, which gets the real length from the server for free. */
    public static void remember(Context context, @Nullable String url, long bytes) {
        if (url == null || url.isEmpty() || bytes <= 0) {
            return;
        }
        known.put(url, bytes);
        final Context appCtx = context.getApplicationContext();
        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                int n = AppDatabase.getDatabase(appCtx).episodeDao()
                        .setEnclosureLengthIfUnknown(url, bytes);
                if (n > 0) {
                    myLogD("episode size learned: " + bytes + " bytes for " + url);
                }
            } catch (Exception e) {
                myLogEE(e, "could not store the episode size");
            }
        });
    }

    /**
     * Asks the server how big the file is: a HEAD first, and a one-byte ranged GET for the servers
     * that do not answer HEAD (the Content-Range carries the total). Returns 0 when neither works.
     */
    private static long probe(Context appCtx, String url) {
        long bytes = contentLength(url, true);
        if (bytes <= 0) {
            bytes = contentLength(url, false);
        }
        if (bytes <= 0) {
            myLogW("no size to be had for " + url);
        }
        return bytes;
    }

    private static long contentLength(String url, boolean head) {
        Request.Builder b = new Request.Builder()
                .url(url)
                .header("User-Agent", Var.USER_AGENT_BOOKPLAYER);
        if (head) {
            b.head();
        } else {
            b.header("Range", "bytes=0-0");
        }
        try (Response r = NetUtils.sharedClient().newCall(b.build()).execute()) {
            if (!r.isSuccessful()) {
                return 0;
            }
            if (head) {
                String len = r.header("Content-Length");
                return parse(len);
            }
            // "bytes 0-0/170161272"
            String range = r.header("Content-Range");
            if (range == null) {
                return 0;
            }
            int slash = range.lastIndexOf('/');
            return slash < 0 ? 0 : parse(range.substring(slash + 1));
        } catch (Exception e) {
            myLogD("size probe failed (" + (head ? "HEAD" : "range") + "): " + e);
            return 0;
        }
    }

    private static long parse(@Nullable String s) {
        if (s == null || s.isEmpty()) {
            return 0;
        }
        try {
            long v = Long.parseLong(s.trim());
            return v > 0 ? v : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
