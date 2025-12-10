package com.driot.bookplayer.librivox;

import androidx.annotation.Nullable;

import com.driot.bookplayer.global.Option;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;

import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;

import static com.driot.bookplayer.utils.log.LoggerStaticHelper.*;

import android.content.Context;

public class LibrivoxRepository {

    private static final String API_SORT_DOWNLOADS_DESC = "downloads desc";
    private static final String API_SORT_ADDED_DESC     = "addeddate desc";

    // --- archive.org side (unchanged) ---
    private final LibrivoxApi directApi;           // archive.org advancedsearch
    @Nullable private final LibrivoxApi cachedApi; // Cloudflare/front proxy for hot lists

    // --- NEW: real LibriVox JSON API client ---
    private final LibrivoxApiService librivoxApi;  // https://librivox.org/api/feed/audiobooks/

    private final Context appContext;

    public LibrivoxRepository(Context context, HttpLoggingInterceptor.Level logLevel) {
        this.appContext = context.getApplicationContext();

        // archive.org client (for advancedsearch + hot lists)
        Retrofit directArchive = LibrivoxServiceFactory.createDirectInternetArchiveRetrofit(logLevel);
        this.directApi = directArchive.create(LibrivoxApi.class);

        // NEW: LibriVox API client (for genre-aware catalog search)
        Retrofit directLibrivox = LibrivoxServiceFactory.createDirectLibrivoxRetrofit(logLevel);
        this.librivoxApi = directLibrivox.create(LibrivoxApiService.class);

        if (Option.getRadioUseCloudflare()) { // TODO, maybe a top-level option "use Cloudflare"
            Retrofit cf = LibrivoxServiceFactory.createCloudflareRetrofit(logLevel);
            this.cachedApi = cf.create(LibrivoxApi.class);
        } else {
            this.cachedApi = null;
        }
    }

    // archive.org list source (cached or direct)
    private LibrivoxApi listsApi() {
        return (cachedApi != null) ? cachedApi : directApi;
    }

    // ---------------------------------------------------------------------
    // GENERIC TEXT SEARCH
    // ---------------------------------------------------------------------

    public void searchByQueryAndLang(
            String query,
            String lang,
            int limit,
            Callback<LibrivoxApiResponse> cb
    ) {
        List<String> fields = Arrays.asList("identifier", "title", "date", "avg_rating", "num_reviews");

        String fullQuery = "collection:librivoxaudio AND language:(" + lang + ")";
        if (!query.isEmpty()) {
            String normalizedQuery = query.toLowerCase().replace(",", "");
            fullQuery += " AND (title:(" + normalizedQuery + ") OR creator:(" + normalizedQuery + "))";
        }

        myLog("Librivox searchByQueryAndLang: [" + fullQuery + "]");

        directApi.search(
                fullQuery,
                fields,
                limit,
                1,
                "json",
                API_SORT_DOWNLOADS_DESC
        ).enqueue(new LoggingCallback<>(cb, "searchByQueryAndLang"));
    }

    // ---------------------------------------------------------------------
    // HOT LISTS
    // ---------------------------------------------------------------------

    private void searchHotListWithFallback(
            String label                  // e.g. "mostDownloadedByLang"
            ,String q                      // full Solr query
            ,List<String> fields
            ,int limit
            ,String sort
            ,Callback<LibrivoxApiResponse> cb
    ) {
        // If no Cloudflare client, just go direct
        if (cachedApi == null) {
            myLogD(label + ": no cachedApi → direct only");
            directApi.search(
                    q,
                    fields,
                    limit,
                    1,
                    "json",
                    sort
            ).enqueue(new LoggingCallback<>(cb, label + "-direct"));
            return;
        }

        // Primary call: Cloudflare
        Call<LibrivoxApiResponse> primaryCall = cachedApi.search(
                q,
                fields,
                limit,
                1,
                "json",
                sort
        );

        primaryCall.enqueue(new Callback<>() {
            @Override
            public void onResponse(Call<LibrivoxApiResponse> call,
                                   Response<LibrivoxApiResponse> resp) {

                boolean ok = resp.isSuccessful()
                        && resp.body() != null
                        && resp.body().response != null;

                if (ok) {
                    myLog(label + " via Cloudflare → " + resp.code());
                    cb.onResponse(call, resp);
                    return;
                }

                // Cloudflare answered but with error / invalid body
                myLogEE(null, label + " via Cloudflare failed (code="
                        + resp.code()
                        + ") - falling back to direct");

                Call<LibrivoxApiResponse> fallbackCall = directApi.search(
                        q,
                        fields,
                        limit,
                        1,
                        "json",
                        sort
                );
                fallbackCall.enqueue(new LoggingCallback<>(cb, label + "-direct-fallback"));
            }

            @Override
            public void onFailure(Call<LibrivoxApiResponse> call, Throwable t) {
                myLogW(label + " via Cloudflare failure: " + t
                        + " - falling back to direct");

                Call<LibrivoxApiResponse> fallbackCall = directApi.search(
                        q,
                        fields,
                        limit,
                        1,
                        "json",
                        sort
                );
                fallbackCall.enqueue(new LoggingCallback<>(cb, label + "-direct-fallback"));
            }
        });
    }

    /** Most downloaded Librivox audiobooks for a given language. */
    public void mostDownloadedByLang(
            String lang,
            int limit,
            Callback<LibrivoxApiResponse> cb
    ) {
        List<String> fields = Arrays.asList(
                "identifier", "title", "date", "avg_rating", "num_reviews"
        );

        String q = "collection:librivoxaudio AND language:(" + lang + ")";

        myLog("Librivox mostDownloadedByLang: [" + q + "]");

        searchHotListWithFallback("mostDownloadedByLang", q, fields, limit, API_SORT_DOWNLOADS_DESC, cb);
    }

    /** Most downloaded by genre (subject) + language (archive.org, approximate). */
    public void mostDownloadedByGenre(
            String lang,
            String genre,
            int limit,
            Callback<LibrivoxApiResponse> cb
    ) {
        List<String> fields = Arrays.asList("identifier", "title", "date", "avg_rating", "num_reviews");

        String q = "collection:librivoxaudio AND language:(" + lang + ")";
        if (genre != null && !genre.trim().isEmpty()) {
            String g = genre.trim().toLowerCase();
            q += " AND subject:(\"" + g + "\")";
        }

        myLog("Librivox mostDownloadedByGenre: [" + q + "]");

        searchHotListWithFallback("mostDownloadedByGenre", q, fields, limit, API_SORT_DOWNLOADS_DESC, cb);
    }

    /** Most downloaded by author (creator) + language. */
    public void mostDownloadedByAuthor(
            String lang,
            String author,
            int limit,
            Callback<LibrivoxApiResponse> cb
    ) {
        List<String> fields = Arrays.asList("identifier", "title", "date", "avg_rating", "num_reviews");

        String q = "collection:librivoxaudio AND language:(" + lang + ")";
        if (author != null && !author.trim().isEmpty()) {
            String a = author.trim().toLowerCase();
            q += " AND creator:(" + a + ")";
        }

        myLog("Librivox mostDownloadedByAuthor: [" + q + "]");

        searchHotListWithFallback("mostDownloadedByAuthor", q, fields, limit, API_SORT_DOWNLOADS_DESC, cb);
    }

    public void mostRecentlyAddedByLang(
            String lang,
            int limit,
            Callback<LibrivoxApiResponse> cb
    ) {
        List<String> fields = Arrays.asList(
                "identifier", "title", "date", "avg_rating", "num_reviews"
        );

        String q = "collection:librivoxaudio AND language:(" + lang + ")";

        myLog("Librivox mostRecentlyAddedByLang: [" + q + "]");

        searchHotListWithFallback("mostRecentlyAddedByLang", q, fields, limit, API_SORT_ADDED_DESC, cb);
    }

    // ---------------------------------------------------------------------
    // NEW: REAL LIBRIVOX GENRE SEARCH (LibriVox API, not archive.org)
    // ---------------------------------------------------------------------

    public void searchBooksByGenreAndLangLibrivox(
            String appLang,
            String genre,
            int targetCount,
            Callback<List<LibrivoxBook>> cb
    ) {
        searchBooksByGenrePagedLibrivox(appLang, genre, targetCount, cb);
    }

    // ---------------------------------------------------------------------
    // GENRES from your local JSON (unchanged)
    // ---------------------------------------------------------------------
    public void listTopGenres(String lang, int limit, Callback<List<LibrivoxFacetItem>> cb) {
        List<LibrivoxGenre> all = LibrivoxGenreStore.getGenres(appContext);

        // Extract roots
        LinkedHashMap<String, Integer> roots = new LinkedHashMap<>();
        for (LibrivoxGenre g : all) {
            roots.put(g.name, g.count);
        }

        List<LibrivoxFacetItem> items = new ArrayList<>();
        for (var entry : roots.entrySet()) {
            items.add(new LibrivoxFacetItem(entry.getKey(), entry.getValue()));
        }

        cb.onResponse(null, Response.success(items));
    }

    // ---------------------------------------------------------------------
    // Logging wrapper for archive.org calls (unchanged)
    // ---------------------------------------------------------------------
    private static final class LoggingCallback<T> implements Callback<T> {
        private final Callback<T> delegate;
        private final String label;
        LoggingCallback(Callback<T> d, String l) { delegate = d; label = l; }

        @Override public void onResponse(Call<T> call, Response<T> resp) {
            myLog(label + " → " + resp.code());
            delegate.onResponse(call, resp);
        }
        @Override public void onFailure(Call<T> call, Throwable t) {
            myLogW(label + " failed: " + t);
            delegate.onFailure(call, t);
        }
    }
    // ---------------------------------------------------------------------
    // CONVENIENCE WRAPPERS: return ArchiveItem so existing UI keeps working
    // ---------------------------------------------------------------------

    /**
     * Single-genre search using LibriVox API, returning ArchiveItem list.
     */
    public void searchArchiveItemsByGenreAndLangLibrivox(
            String appLang,
            boolean filterByLang,
            String genre,
            int targetCount,
            Callback<List<ArchiveItem>> cb
    ) {
        String apiLang = mapToLibriVoxLanguage(appLang);
        final int pageSize = 200;
        final List<ArchiveItem> collected = new ArrayList<>();

        fetchPageGenre(appLang, apiLang, filterByLang, genre,
                0, pageSize, targetCount, collected, cb);

    }

    private void fetchPageGenre(
            String appLang,
            String apiLang,
            boolean filterByLang,
            String genre,
            int offset,
            int pageSize,
            int targetCount,
            List<ArchiveItem> collected,
            Callback<List<ArchiveItem>> cb
    ) {
        myLogD("LibrivoxAPI page (genre): offset=" + offset + " genre=" + genre);

        Call<LibrivoxBooksResponse> call = librivoxApi.getAudiobooks(
                null, //apiLang,
                genre,
                null, null, null,
                1,
                "{id,title,language,genres,url_iarchive,totaltimesecs,authors,copyright_year}",
                pageSize,
                offset,
                "json"
        );

        call.enqueue(new Callback<>() {
            @Override
            public void onResponse(Call<LibrivoxBooksResponse> c,
                                   Response<LibrivoxBooksResponse> resp) {

                if (!resp.isSuccessful() || resp.body() == null) {
                    // Final flush whatever we have
                    cb.onResponse(null, Response.success(new ArrayList<>(collected)));
                    myLog(collected.size() + " books returned - final flush");
                    return;
                }

                List<LibrivoxBook> page = resp.body().asList();
                if (page.isEmpty()) {
                    // No more data → final flush
                    cb.onResponse(null, Response.success(new ArrayList<>(collected)));
                    myLog("no more - final flush");
                    return;
                }

                // 1) Filter by language (because API doesn't enforce it correctly)
                List<LibrivoxBook> langFiltered;
                if (filterByLang) {
                    langFiltered = filterByLanguage(page, appLang, Integer.MAX_VALUE);
                    myLog(langFiltered.size() + " books with correct language [" + appLang + "] / " + page.size() + " books returned");
                } else {
                    langFiltered = page;
                    myLog(page.size() + " books returned");
                }

                // 2) Map to ArchiveItem and add to collected
                for (LibrivoxBook b : langFiltered) {
                    if (b == null) continue;
                    ArchiveItem ai = LibrivoxMapper.toArchiveItem(b);
                    if (ai.identifier != null && !ai.identifier.isEmpty()) {
                        collected.add(ai);
                    }
                }

                // 3) FLUSH PARTIAL RESULTS RIGHT NOW 👇
                cb.onResponse(null, Response.success(new ArrayList<>(collected)));

                // 4) If we already have enough, stop here
                if (collected.size() >= targetCount) {
                    return;
                }

                // 5) Otherwise, fetch next page
                fetchPageGenre(appLang, apiLang, filterByLang, genre,
                        offset + pageSize,
                        pageSize,
                        targetCount,
                        collected,
                        cb);
            }

            @Override
            public void onFailure(Call<LibrivoxBooksResponse> c, Throwable t) {
                cb.onFailure(null, t);
            }
        });
    }


    private List<LibrivoxBook> filterByLanguage(List<LibrivoxBook> all,
                                                String langParam,
                                                int limit) {
        String wanted = mapToLibriVoxLanguage(langParam);
        List<LibrivoxBook> out = new ArrayList<>();
        if (all == null) return out;

        for (LibrivoxBook b : all) {
            if (b == null || b.language == null) continue;
            if (!b.language.equalsIgnoreCase(wanted)) continue;

            out.add(b);
            if (out.size() >= limit) break;
        }
        return out;
    }

    private List<LibrivoxBook> filterByLanguageAndTwoGenres(List<LibrivoxBook> all,
                                                            String langParam,
                                                            String primary,
                                                            String secondary,
                                                            int limit) {
        String wantedLang = mapToLibriVoxLanguage(langParam);
        List<LibrivoxBook> out = new ArrayList<>();
        if (all == null) return out;

        for (LibrivoxBook b : all) {
            if (b == null || b.language == null) continue;
            if (!b.language.equalsIgnoreCase(wantedLang)) continue;
            if (b.genres == null || b.genres.isEmpty()) continue;

            boolean hasPrimary = false;
            boolean hasSecondary = false;

            for (LibrivoxGenre g : b.genres) {
                if (g == null || g.name == null) continue;
                String name = g.name;
                if (name.equalsIgnoreCase(primary))   hasPrimary = true;
                if (name.equalsIgnoreCase(secondary)) hasSecondary = true;
            }

            if (hasPrimary && hasSecondary) {
                out.add(b);
                if (out.size() >= limit) break;
            }
        }
        return out;
    }


    // ---------------------------------------------------------------------
    // Helpers for language + genre filtering (LibriVox API)
    // ---------------------------------------------------------------------

    private static String mapToLibriVoxLanguage(String langCodeOrName) {
        if (langCodeOrName == null) return "";
        String s = langCodeOrName.trim();

        // Already a full LibriVox language name?
        if (s.equalsIgnoreCase("English")
                || s.equalsIgnoreCase("French")
                || s.equalsIgnoreCase("German")
                || s.equalsIgnoreCase("Spanish")
                || s.equalsIgnoreCase("Italian")
                || s.equalsIgnoreCase("Portuguese")
                || s.equalsIgnoreCase("Dutch")) {
            return s;
        }

        // Common ISO codes used in your app
        switch (s.toLowerCase()) {
            case "eng": return "English";
            case "fre":
            case "fra": return "French";
            case "ger":
            case "deu": return "German";
            case "spa":
            case "es":  return "Spanish";
            case "ita": return "Italian";
            case "por": return "Portuguese";
            case "dut":
            case "nld": return "Dutch";
            default:    return s; // fallback: no mapping
        }
    }

    /**
     * Fetch many pages until we collect enough books for the target language.
     * This handles paging, filtering, and termination conditions.
     */
    public void searchBooksByGenrePagedLibrivox(
            String appLang,
            String genre,
            int targetCount,
            Callback<List<LibrivoxBook>> cb
    ) {
        String apiLang = mapToLibriVoxLanguage(appLang);
        final int pageSize = 200;             // Ask 200 per page
        final List<LibrivoxBook> collected = new ArrayList<>();

        fetchPageGenre(appLang, apiLang, genre, 0, pageSize, targetCount, collected, cb);
    }
    private void fetchPageGenre(
            String appLang,
            String apiLang,
            String genre,
            int offset,
            int pageSize,
            int targetCount,
            List<LibrivoxBook> collected,
            Callback<List<LibrivoxBook>> cb
    ) {
        myLogD("Fetching LibriVox API page: offset=" + offset + " genre=" + genre);

        Call<LibrivoxBooksResponse> call = librivoxApi.getAudiobooks(
                null, //apiLang,
                genre,
                null, null, null,
                1,
                "{id,title,language,genres,url_iarchive,totaltimesecs}",
                pageSize,
                offset,
                "json"
        );

        call.enqueue(new Callback<>() {
            @Override
            public void onResponse(Call<LibrivoxBooksResponse> c,
                                   Response<LibrivoxBooksResponse> resp) {

                if (!resp.isSuccessful() || resp.body() == null) {
                    cb.onResponse(null, Response.success(collected));
                    return;
                }

                List<LibrivoxBook> page = resp.body().asList();
                if (page.isEmpty()) {
                    // No more data
                    cb.onResponse(null, Response.success(collected));
                    return;
                }

                // Filter by language
                List<LibrivoxBook> filtered = filterByLanguage(page, appLang, Integer.MAX_VALUE);
                collected.addAll(filtered);

                // Stop if we have enough
                if (collected.size() >= targetCount) {
                    cb.onResponse(null, Response.success(collected.subList(0, targetCount)));
                    return;
                }

                // Fetch next page
                fetchPageGenre(appLang, apiLang, genre,
                        offset + pageSize,
                        pageSize,
                        targetCount,
                        collected,
                        cb);
            }

            @Override
            public void onFailure(Call<LibrivoxBooksResponse> c, Throwable t) {
                cb.onFailure(null, t);
            }
        });
    }

}
