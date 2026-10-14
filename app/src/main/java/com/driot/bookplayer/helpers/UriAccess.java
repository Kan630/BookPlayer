package com.driot.bookplayer.helpers;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Why a Uri could not be read, and which part of the app was trying.
 *
 * Every "book fails to load / fails to play" ends up here, so this is the one place that decides
 * how such a failure is reported:
 * - it classifies the cause (lost grant, file gone, cloud file not downloaded, disk full...),
 * - it builds the one-line message the caller logs,
 * - it tags Crashlytics with uri_ctx / uri_reason / uri_provider so any report carries the context,
 * - it fires the "uri_access_failed" Analytics event, which is the countable funnel for
 *   "no book should ever fail to load or play".
 *
 * Deliberately does NOT call myLogEE/myLogWA itself: CrashReport.callerStack() keeps the first
 * non-logger frame, so logging from here would collapse every Uri failure in the app into a single
 * Crashlytics issue. Callers log at their own site (see LoggerStaticHelper.myLogEEorWA), which keeps
 * one issue per code location, with this class's custom keys and Analytics event on top.
 */
public final class UriAccess {

    private UriAccess() {
    }

    /** Which code path was reading the Uri. BACKFILL/SCAN are best-effort; the rest the user is waiting on. */
    public enum Ctx {
        /** Import preview screen: metadata, cover, chapter scan, hash. The user is looking at it. */
        IMPORT_PREVIEW,
        /** The actual copy/unzip/split during import. */
        IMPORT_COPY,
        /** Resolving or opening a track to play it. */
        PLAYBACK,
        /** Walking a picked folder to find books. */
        SCAN,
        /** OriginalHashBackfill: a one-off migration over old books, misses are expected and harmless. */
        BACKFILL,
        /** Not attributed yet. */
        OTHER
    }

    public enum Reason {
        /** Had a read grant once, does not any more (one-shot share grant died, or never persistable). */
        GRANT_LOST,
        /** The provider refuses to hand out a persistable grant at all (messenger shares). */
        NOT_PERSISTABLE,
        /** The file itself is gone / was deleted / the provider no longer knows the id. */
        FILE_GONE,
        /** The provider is not installed or not resolvable any more. */
        PROVIDER_GONE,
        /** Cloud file (Drive...) that is not available offline. */
        CLOUD_UNAVAILABLE,
        /** Device storage full. */
        NO_SPACE,
        /** The Uri itself is malformed or an unsupported shape. */
        BAD_URI,
        UNKNOWN
    }

    /** Best-effort: the user is actively waiting on this read, so a failure is a real bug. */
    public static boolean isUserFacing(@Nullable Ctx ctx) {
        return ctx != Ctx.BACKFILL && ctx != Ctx.SCAN;
    }

    /**
     * Classify from the thrown exception when there is one, else from the Uri shape.
     * Order matters: a Drive error can carry ENOSPC, and disk-full is the more actionable cause.
     */
    @NonNull
    public static Reason classify(@Nullable Throwable t, @Nullable Uri uri) {
        String msg = messageOf(t);

        if (contains(msg, "enospc") || contains(msg, "no space left"))
            return Reason.NO_SPACE;
        if (contains(msg, "no persistable permission grants"))
            return Reason.NOT_PERSISTABLE;
        if (contains(msg, "permission denial") || contains(msg, "requires android.permission")
                || contains(msg, "action_open_document") || contains(msg, "not exported from uid")
                || contains(msg, "eacces") || contains(msg, "permission denied"))
            return Reason.GRANT_LOST;
        if (contains(msg, "no content provider") || contains(msg, "unknown authority")
                || contains(msg, "failed to find provider"))
            return Reason.PROVIDER_GONE;
        // Google Drive's Cello layer: "otl: Cello error 2", "sxl: Cello error 31"...
        if (contains(msg, "cello error") || contains(msg, "not available offline"))
            return Reason.CLOUD_UNAVAILABLE;
        if (contains(msg, "missing file for") || contains(msg, "filenotfound") || contains(msg, "enoent")
                || contains(msg, "no such file"))
            return Reason.FILE_GONE;
        if (contains(msg, "invalid uri") || contains(msg, "unsupported uri") || contains(msg, "for input string")
                || contains(msg, "unsupported uri scheme"))
            return Reason.BAD_URI;

        if (t == null && uri != null) {
            // No exception: a null/!exists DocumentFile. A share-style provider almost always means the
            // one-shot grant is gone; anything else is most likely a deleted file.
            return isTransientShareProvider(uri) ? Reason.GRANT_LOST : Reason.FILE_GONE;
        }
        return Reason.UNKNOWN;
    }

    /**
     * Short, stable label for the provider behind the Uri - readable in Analytics and low-cardinality
     * enough to group by (raw authorities would explode into per-app noise).
     */
    @NonNull
    public static String provider(@Nullable Uri uri) {
        if (uri == null)
            return "none";
        return providerOf(uri.getScheme(), uri.getAuthority());
    }

    /** String form so it stays unit-testable: android.net.Uri is not usable in plain JVM tests. */
    @NonNull
    public static String providerOf(@Nullable String scheme, @Nullable String authority) {
        if (scheme == null)
            return "rawpath";
        if ("file".equalsIgnoreCase(scheme))
            return "file";
        if (!"content".equalsIgnoreCase(scheme))
            return scheme;

        String a = authority;
        if (a == null)
            return "content";
        a = a.toLowerCase();

        if (a.contains("telegram"))
            return "telegram";
        if (a.contains("whatsapp"))
            return "whatsapp";
        if (a.contains("com.google.android.gm"))
            return "gmail";
        if (a.contains("com.google.android.apps.docs"))
            return "drive";
        if (a.contains("dropbox"))
            return "dropbox";
        if (a.contains("com.android.providers.downloads"))
            return "downloads";
        if (a.contains("com.android.externalstorage"))
            return "externalstorage";
        if (a.contains("com.android.providers.media"))
            return "mediadocs";
        if ("media".equals(a))
            return "mediastore";
        if (a.startsWith("com.driot.bookplayer"))
            return "self";
        if (a.contains("fileprovider") || a.contains("files"))
            return "otherapp-fileprovider";
        return "other";
    }

    /** Providers whose shares only ever come with a one-shot, non-persistable read grant. */
    public static boolean isTransientShareProvider(@Nullable Uri uri) {
        String p = provider(uri);
        return "telegram".equals(p) || "whatsapp".equals(p) || "gmail".equals(p)
                || "otherapp-fileprovider".equals(p) || "downloads".equals(p);
    }

    /**
     * Tag Crashlytics, count it in Analytics, and return the line for the caller to log.
     * Keep {@code what} short and stable (a method name): it is what Crashlytics shows as the subtitle.
     */
    @NonNull
    public static String describe(@NonNull Ctx ctx, @Nullable Uri uri, @Nullable Throwable t, @NonNull String what) {
        Reason reason = classify(t, uri);
        String provider = provider(uri);
        try {
            FirebaseAnalyticsHelper.setCustomKeyCrashlytics("uri_ctx", ctx.name());
            FirebaseAnalyticsHelper.setCustomKeyCrashlytics("uri_reason", reason.name());
            FirebaseAnalyticsHelper.setCustomKeyCrashlytics("uri_provider", provider);
            if (allowAnalytics(ctx, reason, provider))
                FirebaseAnalyticsHelper.tellUriAccessFailed(ctx.name(), reason.name(), provider,
                        extensionOf(uri), isUserFacing(ctx));
        } catch (Throwable ignored) {
            // Never let reporting break the read path
        }
        return what + " [" + ctx.name() + "/" + reason.name() + "/" + provider + "] "
                + (uri == null ? "null uri" : safeDecode(uri));
    }

    // ---------------- internals ----------------

    private static final long ANALYTICS_DEDUP_MS = 60_000;
    private static final int ANALYTICS_DEDUP_MAX = 200;
    private static final Map<String, Long> recentAnalytics = new ConcurrentHashMap<>();

    /**
     * One event per ctx+reason+provider per minute. A single backfill pass can hit the same wall
     * dozens of times in a second; per-book failure counts come from load_book_failed / load_file_KO.
     */
    private static boolean allowAnalytics(Ctx ctx, Reason reason, String provider) {
        String key = ctx.name() + "|" + reason.name() + "|" + provider;
        long now = System.currentTimeMillis();
        Long last = recentAnalytics.get(key);
        if (last != null && now - last < ANALYTICS_DEDUP_MS)
            return false;
        if (recentAnalytics.size() > ANALYTICS_DEDUP_MAX)
            recentAnalytics.clear();
        recentAnalytics.put(key, now);
        return true;
    }

    @NonNull
    private static String messageOf(@Nullable Throwable t) {
        if (t == null)
            return "";
        StringBuilder sb = new StringBuilder();
        int depth = 0;
        for (Throwable c = t; c != null && depth < 5; c = c.getCause(), depth++) {
            sb.append(c.getClass().getSimpleName()).append(' ').append(c.getMessage()).append(' ');
            if (c.getCause() == c)
                break;
        }
        return sb.toString().toLowerCase();
    }

    private static boolean contains(String haystackLower, String needleLower) {
        return haystackLower.contains(needleLower);
    }

    @NonNull
    private static String extensionOf(@Nullable Uri uri) {
        if (uri == null)
            return "";
        String last = uri.getLastPathSegment();
        if (last == null)
            return "";
        int dot = last.lastIndexOf('.');
        return (dot >= 0 && dot < last.length() - 1) ? last.substring(dot + 1).toLowerCase() : "";
    }

    @NonNull
    private static String safeDecode(@NonNull Uri uri) {
        try {
            String d = Uri.decode(uri.toString());
            return d != null ? d : uri.toString();
        } catch (Exception e) {
            return uri.toString();
        }
    }
}
