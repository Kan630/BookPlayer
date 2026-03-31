package com.driot.bookplayer.net;

import android.content.Context;

import com.driot.bookplayer.objects.CoverResult;
import com.driot.bookplayer.BuildConfig;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static com.driot.bookplayer.utils.log.LoggerStaticHelper.*;
import com.driot.bookplayer.global.Option;

public class CoverSearchRepository {

    public interface ResultCallback {
        /** Called on a background thread each time a provider returns results. */
        void onPartialResults(List<CoverResult> newResults);
        /** Called on a background thread once all providers have finished. */
        void onComplete();
    }
    private final List<CoverSearchProvider> providers = new ArrayList<>();
    private static final int MIN_PER_PROVIDER = 2; // guarantee at least N from each provider
    private static final int TIMEOUT_MS = 6000; // per-provider hard cap

    // lightweight in-memory cache (optional)
    private static final long CACHE_TTL_MS = 5 * 60_000L;
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    // shared pool for the whole app (avoid spinning threads every time)
    private static final ExecutorService POOL = Executors.newFixedThreadPool(
            Math.max(3, Runtime.getRuntime().availableProcessors() / 2));

    public CoverSearchRepository(Context ctx) {
        if (Option.getUseOpenLibrary()) {
            providers.add(new OpenLibraryProvider());
        }
        if (Option.getUseGoogleBooks()) {
            providers.add(new GoogleBooksProvider());
        }
        if (Option.getUseGoogleImages()) {
            providers.add(new GoogleImageProvider());
        }

        // Cloudflare Worker
        if (Option.getUsePixabay()) {
            String base = BuildConfig.PIXABAY_BASE_URL; // e.g., https://pixabay.driot.com
            String tok = BuildConfig.APP_TOKEN; // optional, can be ""
            if (base != null && !base.isEmpty()) {
                providers.add(new PixabayProxyProvider(base, tok));
            }
        }
    }

    public List<CoverResult> search(Context ctx, String query, int max) {
        final String key = query.trim().toLowerCase(Locale.US) + "#" + max;
        CacheEntry ce = cache.get(key);
        if (ce != null && (System.currentTimeMillis() - ce.when) <= CACHE_TTL_MS) {
            myLogD("CoverSearchRepository: cache hit for '" + query + "'");
            return ce.results;
        }

        long t0 = System.currentTimeMillis();

        // fire all providers in parallel (one request per provider)
        List<Callable<ProvResult>> tasks = new ArrayList<>(providers.size());
        for (CoverSearchProvider p : providers) {
            tasks.add(() -> {
                long t = System.currentTimeMillis();
                List<CoverResult> list = Collections.emptyList();
                try {
                    // ask each provider for 'max' so we can trim/merge locally
                    list = p.search(ctx, query, max);
                } catch (Throwable e) {
                    myLogEE(e, "Provider failed: " + p.getClass().getSimpleName());
                }
                return new ProvResult(p.getClass().getSimpleName(), list, System.currentTimeMillis() - t);
            });
        }

        Map<String, List<CoverResult>> byProv = new LinkedHashMap<>();
        try {
            List<Future<ProvResult>> futures = new ArrayList<>(tasks.size());
            for (Callable<ProvResult> c : tasks)
                futures.add(POOL.submit(c));

            // bounded waits so slow providers don’t block UX
            for (Future<ProvResult> f : futures) {
                try {
                    ProvResult r = f.get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
                    byProv.put(r.name, r.list != null ? r.list : Collections.emptyList());
                    myLogD("Provider " + r.name + " returned " + (r.list == null ? 0 : r.list.size()) + " in "
                            + r.elapsed + "ms");
                } catch (TimeoutException te) {
                    myLogW("Provider timed out after " + TIMEOUT_MS + "ms");
                    f.cancel(true);
                } catch (Exception e) {
                    myLogEE(e, "Provider future failed");
                }
            }
        } catch (Exception e) {
            myLogEE(e, "submit/collect");
        }

        // merge: guarantee MIN_PER_PROVIDER, then fill round-robin
        ArrayList<CoverResult> out = new ArrayList<>(max);
        HashSet<String> seen = new HashSet<>(max * 2);

        // 1) first pass: take up to MIN_PER_PROVIDER from each provider and remember
        // how many we took
        Map<String, Integer> offsets = new LinkedHashMap<>(); // providerName -> next index to try
        for (Map.Entry<String, List<CoverResult>> e : byProv.entrySet()) {
            List<CoverResult> list = e.getValue();
            int took = 0;
            if (list != null) {
                for (int i = 0; i < list.size() && took < MIN_PER_PROVIDER && out.size() < max; i++) {
                    CoverResult r = list.get(i);
                    if (r == null || r.imageUrl == null)
                        continue;
                    if (seen.add(r.imageUrl)) {
                        out.add(r);
                        took++;
                    }
                }
            }
            // next index to try for this provider in the round-robin:
            offsets.put(e.getKey(), Math.min(took, (list == null ? 0 : list.size())));
            if (out.size() >= max)
                break;
        }

        // 2) round-robin the remainder starting from each provider’s offset
        boolean progressed = true;
        while (out.size() < max && progressed) {
            progressed = false;
            int iProv = 0;
            for (Map.Entry<String, List<CoverResult>> e : byProv.entrySet()) {
                List<CoverResult> list = e.getValue();
                if (list == null || list.isEmpty()) {
                    iProv++;
                    continue;
                }

                Integer idxBoxed = offsets.get(e.getKey());
                int idx = (idxBoxed != null) ? idxBoxed : 0;
                if (idx >= list.size()) {
                    iProv++;
                    continue;
                }

                CoverResult r = list.get(idx);
                // advance cursor regardless so we don’t retry same slot next cycle
                offsets.put(e.getKey(), idx + 1);

                if (r != null && r.imageUrl != null && seen.add(r.imageUrl)) {
                    out.add(r);
                    progressed = true;
                    if (out.size() >= max)
                        break;
                }
                iProv++;
            }
        }
        // cache & log
        out.trimToSize();
        cache.put(key, new CacheEntry(System.currentTimeMillis(), out));
        myLogD("CoverSearchRepository: returned " + out.size() + " in " + (System.currentTimeMillis() - t0) + "ms for '"
                + query + "'");
        return out;
    }

    /** Fires all providers in parallel and calls cb.onPartialResults() as each one finishes. */
    public void searchAsync(Context ctx, String query, int max, ResultCallback cb) {
        final String key = query.trim().toLowerCase(Locale.US) + "#" + max;
        CacheEntry ce = cache.get(key);
        if (ce != null && (System.currentTimeMillis() - ce.when) <= CACHE_TTL_MS) {
            myLogD("CoverSearchRepository: cache hit for '" + query + "'");
            cb.onPartialResults(new ArrayList<>(ce.results));
            cb.onComplete();
            return;
        }

        if (providers.isEmpty()) {
            cb.onComplete();
            return;
        }

        final long t0 = System.currentTimeMillis();
        final Set<String> seen = Collections.synchronizedSet(new HashSet<>());
        final List<CoverResult> allResults = Collections.synchronizedList(new ArrayList<>());
        final AtomicInteger remaining = new AtomicInteger(providers.size());

        for (CoverSearchProvider p : providers) {
            POOL.submit(() -> {
                long t = System.currentTimeMillis();
                List<CoverResult> list = Collections.emptyList();
                try {
                    list = p.search(ctx, query, max);
                } catch (Throwable e) {
                    myLogEE(e, "Provider failed: " + p.getClass().getSimpleName());
                }
                myLogD("Provider " + p.getClass().getSimpleName() + " returned "
                        + list.size() + " in " + (System.currentTimeMillis() - t) + "ms");

                // Deduplicate against already-seen results from other providers
                List<CoverResult> newResults = new ArrayList<>();
                for (CoverResult r : list) {
                    if (r != null && r.imageUrl != null && seen.add(r.imageUrl)) {
                        newResults.add(r);
                    }
                }
                if (!newResults.isEmpty()) {
                    allResults.addAll(newResults);
                    cb.onPartialResults(newResults);
                }

                if (remaining.decrementAndGet() == 0) {
                    cache.put(key, new CacheEntry(System.currentTimeMillis(), new ArrayList<>(allResults)));
                    myLogD("CoverSearchRepository: all done in "
                            + (System.currentTimeMillis() - t0) + "ms, total=" + allResults.size()
                            + " for '" + query + "'");
                    cb.onComplete();
                }
            });
        }
    }

    private static final class ProvResult {
        final String name;
        final List<CoverResult> list;
        final long elapsed;

        ProvResult(String n, List<CoverResult> l, long e) {
            name = n;
            list = l;
            elapsed = e;
        }
    }

    private static final class CacheEntry {
        final long when;
        final List<CoverResult> results;

        CacheEntry(long w, List<CoverResult> r) {
            when = w;
            results = r;
        }
    }
}
