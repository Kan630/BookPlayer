package com.driot.bookplayer.librivox;

import androidx.annotation.Nullable;

import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.global.Var;

import java.util.ArrayList;
import java.util.Arrays;
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
    private static final String API_SORT_ADDED_DESC = "addeddate desc";

    private final LibrivoxApi directApi;
    @Nullable private final LibrivoxApi cachedApi;
    private final LibrivoxApiService librivoxApi;
    private final Context appContext;

    public LibrivoxRepository(Context context, HttpLoggingInterceptor.Level logLevel) {
        this.appContext = context.getApplicationContext();

        Retrofit directArchive = LibrivoxServiceFactory.createDirectInternetArchiveRetrofit(logLevel);
        this.directApi = directArchive.create(LibrivoxApi.class);

        Retrofit directLibrivox = LibrivoxServiceFactory.createDirectLibrivoxRetrofit(logLevel);
        this.librivoxApi = directLibrivox.create(LibrivoxApiService.class);

        if (Option.getRadioUseCloudflare()) {
            Retrofit cf = LibrivoxServiceFactory.createCloudflareRetrofit(logLevel);
            this.cachedApi = cf.create(LibrivoxApi.class);
        } else {
            this.cachedApi = null;
        }
    }

    // =====================================================================
    // CALLBACK INTERFACES
    // =====================================================================

    public interface PagedResultCallback<T> {
        void onPageReceived(List<T> items, boolean isFinalPage);
        void onError(Throwable t);
    }

    // =====================================================================
    // GENERIC TEXT SEARCH
    // =====================================================================

    public void searchByQueryAndLang(String query, String lang, int limit,
                                     Callback<ArchiveApiResponse> cb) {
        List<String> fields = Arrays.asList("identifier", "title", "date",
                "avg_rating", "num_reviews");

        String fullQuery = "collection:librivoxaudio AND language:(" + lang + ")";
        if (!query.isEmpty()) {
            String normalizedQuery = query.toLowerCase().replace(",", "");
            fullQuery += " AND (title:(" + normalizedQuery + ") OR creator:("
                    + normalizedQuery + "))";
        }

        myLog("Librivox searchByQueryAndLang: [" + fullQuery + "]");

        directApi.search(fullQuery, fields, limit, 1, "json", API_SORT_DOWNLOADS_DESC)
                .enqueue(new LoggingCallback<>(cb, "searchByQueryAndLang"));
    }

    // =====================================================================
    // HOT LISTS (with Cloudflare fallback)
    // =====================================================================

    private void searchHotListWithFallback(String label, String q, List<String> fields,
                                           int limit, int page, String sort,
                                           Callback<ArchiveApiResponse> cb) {
        if (cachedApi == null) {
            myLogD(label + ": no cachedApi → direct only, page=" + page);
            directApi.search(q, fields, limit, page, "json", sort)
                    .enqueue(new LoggingCallback<>(cb, label + "-direct"));
            return;
        }

        Call<ArchiveApiResponse> primaryCall = cachedApi.search(
                q, fields, limit, page, "json", sort);

        primaryCall.enqueue(new Callback<>() {
            @Override
            public void onResponse(Call<ArchiveApiResponse> call,
                                   Response<ArchiveApiResponse> resp) {
                boolean ok = resp.isSuccessful()
                        && resp.body() != null
                        && resp.body().response != null;

                if (ok) {
                    myLog(label + " via Cloudflare → " + resp.code() + " page=" + page);
                    cb.onResponse(call, resp);
                    return;
                }

                myLogEE(null, label + " via Cloudflare failed (code="
                        + resp.code() + ") - falling back to direct");

                Call<ArchiveApiResponse> fallbackCall = directApi.search(
                        q, fields, limit, page, "json", sort);
                fallbackCall.enqueue(new LoggingCallback<>(cb, label + "-direct-fallback"));
            }

            @Override
            public void onFailure(Call<ArchiveApiResponse> call, Throwable t) {
                myLogW(label + " via Cloudflare failure: " + t
                        + " - falling back to direct");

                Call<ArchiveApiResponse> fallbackCall = directApi.search(
                        q, fields, limit, page, "json", sort);
                fallbackCall.enqueue(new LoggingCallback<>(cb, label + "-direct-fallback"));
            }
        });
    }

    /** First page (page 1) only. */
    public void mostDownloadedByLang(String lang, int limit,
                                     Callback<ArchiveApiResponse> cb) {
        mostDownloadedByLang(lang, limit, 1, cb);
    }

    /** With page (1-based) for pagination. */
    public void mostDownloadedByLang(String lang, int limit, int page,
                                     Callback<ArchiveApiResponse> cb) {
        List<String> fields = Arrays.asList("identifier", "title", "date",
                "avg_rating", "num_reviews");
        String q = "collection:librivoxaudio AND language:(" + lang + ")";
        myLog("Librivox mostDownloadedByLang: [" + q + "] page=" + page);
        searchHotListWithFallback("mostDownloadedByLang", q, fields, limit, page,
                API_SORT_DOWNLOADS_DESC, cb);
    }

    public void mostDownloadedByGenre(String lang, String genre, int limit,
                                      Callback<ArchiveApiResponse> cb) {
        List<String> fields = Arrays.asList("identifier", "title", "date",
                "avg_rating", "num_reviews");
        String q = "collection:librivoxaudio AND language:(" + lang + ")";

        if (genre != null && !genre.trim().isEmpty()) {
            String g = genre.trim().toLowerCase();
            q += " AND subject:(\"" + g + "\")";
        }

        myLog("Librivox mostDownloadedByGenre: [" + q + "]");
        searchHotListWithFallback("mostDownloadedByGenre", q, fields, limit, 1,
                API_SORT_DOWNLOADS_DESC, cb);
    }

    public void mostDownloadedByAuthor(String lang, String author, int limit,
                                       Callback<ArchiveApiResponse> cb) {
        List<String> fields = Arrays.asList("identifier", "title", "date",
                "avg_rating", "num_reviews");
        String q = "collection:librivoxaudio AND language:(" + lang + ")";

        if (author != null && !author.trim().isEmpty()) {
            String a = author.trim().toLowerCase();
            q += " AND creator:(" + a + ")";
        }

        myLog("Librivox mostDownloadedByAuthor: [" + q + "]");
        searchHotListWithFallback("mostDownloadedByAuthor", q, fields, limit, 1,
                API_SORT_DOWNLOADS_DESC, cb);
    }

    /** First page (page 1) only. */
    public void mostRecentlyAddedByLang(String lang, int limit,
                                        Callback<ArchiveApiResponse> cb) {
        mostRecentlyAddedByLang(lang, limit, 1, cb);
    }

    /** With page (1-based) for pagination. */
    public void mostRecentlyAddedByLang(String lang, int limit, int page,
                                        Callback<ArchiveApiResponse> cb) {
        List<String> fields = Arrays.asList("identifier", "title", "date",
                "avg_rating", "num_reviews");
        String q = "collection:librivoxaudio AND language:(" + lang + ")";
        myLog("Librivox mostRecentlyAddedByLang: [" + q + "] page=" + page);
        searchHotListWithFallback("mostRecentlyAddedByLang", q, fields, limit, page,
                API_SORT_ADDED_DESC, cb);
    }

    // =====================================================================
    // GENRE SEARCH (LibriVox API with paging)
    // =====================================================================

    public void searchArchiveItemsByGenreAndLangLibrivox(String appLang, boolean filterByLang,
                                                         String genre, int targetCount,
                                                         PagedResultCallback<ArchiveItem> callback) {
        String apiLang = mapToLibriVoxLanguage(appLang);
        final int pageSize = Var.LIBRIVOX_API_PAGE_SIZE;
        final List<ArchiveItem> collected = new ArrayList<>();

        fetchPageGenre(appLang, apiLang, filterByLang, genre, 0, pageSize,
                targetCount, collected, callback);
    }

    private void fetchPageGenre(String appLang, String apiLang, boolean filterByLang,
                                String genre, int offset, int pageSize, int targetCount,
                                List<ArchiveItem> collected,
                                PagedResultCallback<ArchiveItem> callback) {
        myLogD("LibrivoxAPI page (genre): offset=" + offset + " genre=" + genre);

        Call<LibrivoxBooksResponse> call = librivoxApi.getAudiobooks(
                null, genre, null, null, null, 1,
                "{id,title,language,genres,url_iarchive,totaltimesecs,authors,copyright_year}",
                pageSize, offset, "json"
        );

        call.enqueue(new Callback<>() {
            @Override
            public void onResponse(Call<LibrivoxBooksResponse> c,
                                   Response<LibrivoxBooksResponse> resp) {
                if (!resp.isSuccessful() || resp.body() == null) {
                    myLog(collected.size() + " books - API error, sending final");
                    callback.onPageReceived(new ArrayList<>(collected), true);
                    return;
                }

                List<LibrivoxBook> page = resp.body().asList();

                if (page.isEmpty()) {
                    myLog(collected.size() + " books - no more data, sending final");
                    callback.onPageReceived(new ArrayList<>(collected), true);
                    return;
                }

                // Filter by language
                List<LibrivoxBook> langFiltered;
                if (filterByLang) {
                    langFiltered = filterByLanguage(page, appLang, Integer.MAX_VALUE);
                    myLog(langFiltered.size() + " books with correct language ["
                            + appLang + "] / " + page.size() + " returned");
                } else {
                    langFiltered = page;
                    myLog(page.size() + " books returned");
                }

                // Map to ArchiveItem
                for (LibrivoxBook b : langFiltered) {
                    if (b == null) continue;
                    ArchiveItem ai = LibrivoxMapper.toArchiveItem(b);
                    if (ai.identifier != null && !ai.identifier.isEmpty()) {
                        collected.add(ai);
                    }
                }

                // Check if done
                boolean isFinal = collected.size() >= targetCount;

                if (isFinal) {
                    myLog(collected.size() + " books - target reached, sending final");
                    callback.onPageReceived(new ArrayList<>(collected), true);
                    return;
                }

                // Send partial results
                myLog(collected.size() + " books - sending partial");
                callback.onPageReceived(new ArrayList<>(collected), false);

                // Fetch next page
                fetchPageGenre(appLang, apiLang, filterByLang, genre,
                        offset + pageSize, pageSize, targetCount,
                        collected, callback);
            }

            @Override
            public void onFailure(Call<LibrivoxBooksResponse> c, Throwable t) {
                myLogEE(t, "LibrivoxAPI fetch failed at offset " + offset);
                callback.onError(t);
            }
        });
    }

    // =====================================================================
    // HELPER METHODS
    // =====================================================================

    private List<LibrivoxBook> filterByLanguage(List<LibrivoxBook> all,
                                                String langParam, int limit) {
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

    private static String mapToLibriVoxLanguage(String langCodeOrName) {
        if (langCodeOrName == null) return "";
        String s = langCodeOrName.trim();

        // Already a full LibriVox language name?
        if (s.equalsIgnoreCase("English") || s.equalsIgnoreCase("French")
                || s.equalsIgnoreCase("German") || s.equalsIgnoreCase("Spanish")
                || s.equalsIgnoreCase("Italian") || s.equalsIgnoreCase("Portuguese")
                || s.equalsIgnoreCase("Dutch")) {
            return s;
        }

        // Map ISO codes
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
            default:    return s;
        }
    }

    // =====================================================================
    // LOGGING WRAPPER
    // =====================================================================

    private static final class LoggingCallback<T> implements Callback<T> {
        private final Callback<T> delegate;
        private final String label;

        LoggingCallback(Callback<T> d, String l) {
            delegate = d;
            label = l;
        }

        @Override
        public void onResponse(Call<T> call, Response<T> resp) {
            myLog(label + " → " + resp.code());
            delegate.onResponse(call, resp);
        }

        @Override
        public void onFailure(Call<T> call, Throwable t) {
            myLogW(label + " failed: " + t);
            delegate.onFailure(call, t);
        }
    }
}