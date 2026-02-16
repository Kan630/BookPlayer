package com.driot.bookplayer.utils;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Build;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.widget.TextView;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.pm.PackageInfoCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import static com.driot.bookplayer.utils.log.LoggerStaticHelper.*;

import com.google.gson.Gson;
import com.driot.bookplayer.global.Pref;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.net.URL;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Locale;
import java.util.Map;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * In-app messages pilotés serveur.
 * - Fetch JSON (HTTPS recommandé), cache & ETag
 * - Sélection & affichage
 * - Bouton "Traduire" sans ajouter de lib (utilise apps installées / web)
 *
 * Intégration rapide :
 * InAppMsgManager.schedule(context); // au démarrage (Application.onCreate)
 * InAppMsgManager.maybeShowBestMessage(activity, getString(R.string.app_name));
 * // après l’UI prête
 */
public final class InAppMsgManager {

    // --- CONFIG À ADAPTER ----------------------------------------------------
    /** URL de l’index JSON. ⚠️ Passe en HTTPS côté serveur si possible. */
    public static String ENDPOINT = "https://bookplayer.driot.com/msg_inapp/index.json";

    /** Nom du unique work périodique. */
    private static final String PERIODIC_WORK_NAME = "InAppMsgPeriodicFetch";

    /** Périodicité du fetch. */
    private static final long PERIODIC_HOURS = 12;
    private static final int IN_APP_MESSAGES_RETRY_DELAY_IN_SEC = 30;

    private static final boolean DEBUG = false;

    /** Package de Google Translate (facultatif). */
    private static final String GOOGLE_TRANSLATE_PKG = "com.google.android.apps.translate";

    /** SharedPreferences. */
    private static final String PREF = "inapp_msgs";
    private static final String KEY_JSON = "json";
    private static final String KEY_ETAG = "etag";
    private static final String KEY_FETCHED_AT = "fetched_at"; // epoch ms

    public static final String ACTION_CACHE_UPDATED = "com.driot.bookplayer.INAPP_MSG_CACHE_UPDATED";

    private InAppMsgManager() {
    }

    // ------------------------------------------------------------------------
    // PUBLIC API
    // ------------------------------------------------------------------------

    /** Planifie un fetch immédiat + un périodique (réseau requis). */
    public static void schedule(Context context) {
        /*
         * if (!NetworkHelper.isNetworkAvailable(context)) {
         * myLogD("no internet => no in-app msg check schedule");
         * return;
         * }
         */
        myLogD("schedule(): enqueue one-shot + periodic fetch");
        Constraints net = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        OneTimeWorkRequest now = new OneTimeWorkRequest.Builder(FetchWorker.class)
                .setConstraints(net)
                .setBackoffCriteria(
                        androidx.work.BackoffPolicy.EXPONENTIAL,
                        IN_APP_MESSAGES_RETRY_DELAY_IN_SEC, TimeUnit.SECONDS)
                .build();
        WorkManager.getInstance(context).enqueueUniqueWork(
                "InAppMsgOneShot", ExistingWorkPolicy.REPLACE, now);
        myLogD("schedule(): one-shot enqueued");

        PeriodicWorkRequest periodic = new PeriodicWorkRequest.Builder(
                FetchWorker.class, PERIODIC_HOURS, TimeUnit.HOURS)
                .setConstraints(net)
                .setBackoffCriteria(
                        androidx.work.BackoffPolicy.EXPONENTIAL,
                        IN_APP_MESSAGES_RETRY_DELAY_IN_SEC, TimeUnit.SECONDS)
                .build();
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, periodic);
        myLogD("schedule(): periodic enqueued every " + PERIODIC_HOURS + "h");
    }

    /**
     * Tente d’afficher le meilleur message (selon cache) si disponible & pas encore
     * vu.
     */
    public static void maybeShowBestMessage(Activity activity, String title) {
        myLogD("maybeShowBestMessage: start");
        InAppMessageIndex idx = loadIndexFromCache(activity);
        if (idx == null || idx.messages == null || idx.messages.isEmpty()) {
            myLogD("maybeShowBestMessage: no messages in cache → nothing to show");
            return;
        }

        InAppMessage best = pickBestMessage(activity, idx);
        if (best == null) {
            myLogD("maybeShowBestMessage: no eligible message (all filtered out or already seen)");
            return;
        }

        myLogD("maybeShowBestMessage: showing id=" + best.id + ", priority=" + best.priority
                + ", start=" + best.start + ", end=" + best.end);
        showMessageDialog(activity, title, best);
    }

    // ------------------------------------------------------------------------
    // AFFICHAGE
    // ------------------------------------------------------------------------

    public static void showMessageDialog(Activity activity, String title, InAppMessage msg) {
        if (activity == null || activity.isFinishing() || msg == null)
            return;

        String lang = safeLang(Locale.getDefault());
        String html = pickLocalized(msg.body_html, lang);
        boolean hasExactLocale = msg.body_html != null && msg.body_html.containsKey(lang);
        boolean showTranslateButton = !hasExactLocale;

        myLogD("showDialog: id=" + msg.id + ", lang=" + lang
                + ", hasExactLocale=" + hasExactLocale
                + ", translateBtn=" + showTranslateButton);

        if (html == null || html.trim().isEmpty()) {
            if (msg.body_html != null && !msg.body_html.isEmpty()) {
                html = msg.body_html.values().iterator().next();
            } else {
                html = "";
            }
        }
        Spanned sp = toSpanned(html);

        AlertDialog dlg = new AlertDialog.Builder(activity)
                .setTitle(title)
                .setMessage(sp)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    markSeen(activity, msg);
                    d.dismiss();
                })
                .create();

        if (showTranslateButton) {
            String finalHtml = html;
            dlg.setButton(AlertDialog.BUTTON_NEUTRAL, "Traduire",
                    (d, w) -> openTranslate(activity, stripToPlainText(finalHtml)));
        }

        Action primary = firstAction(msg);
        if (primary != null) {
            dlg.setButton(AlertDialog.BUTTON_NEGATIVE,
                    safeActionTitle(primary, lang, "Ouvrir"),
                    (d, w) -> {
                        openAction(activity, primary);
                        markSeen(activity, msg);
                    });
        }

        dlg.setOnShowListener(di -> {
            TextView tv = dlg.findViewById(android.R.id.message);
            if (tv != null)
                tv.setMovementMethod(LinkMovementMethod.getInstance());
        });
        dlg.show();
    }

    // ------------------------------------------------------------------------
    // SÉLECTION DU MESSAGE
    // ------------------------------------------------------------------------

    /** Retourne le meilleur message non vu et qui matche les conditions. */
    @Nullable
    public static InAppMessage pickBestMessage(Context ctx, InAppMessageIndex index) {
        List<InAppMessage> eligible = new ArrayList<>();
        int total = (index.messages == null ? 0 : index.messages.size());
        myLogD("pickBestMessage: evaluating " + total + " message(s)");

        for (InAppMessage m : index.messages) {
            String id = (m != null ? m.id : "null");
            if (m == null) {
                myLogW("pickBestMessage: null message skipped");
                continue;
            }

            boolean match = matches(ctx, m); // logs internes (voir ci-dessous)
            boolean seen = wasSeen(ctx, m);

            myLogD("pickBestMessage: id=" + id
                    + " match=" + match
                    + " seen=" + seen
                    + " repeat=" + (m.repeat == null ? "once" : m.repeat)
                    + " priority=" + m.priority
                    + " vc_range=[" + m.vc_min + "," + m.vc_max + "]"
                    + " api_range=[" + m.api_min + "," + m.api_max + "]");

            if (match && !seen)
                eligible.add(m);
        }

        if (eligible.isEmpty()) {
            myLogD("pickBestMessage: 0 eligible");
            return null;
        }

        Collections.sort(eligible, new Comparator<InAppMessage>() {
            @Override
            public int compare(InAppMessage a, InAppMessage b) {
                int pa = a.priority != null ? a.priority : 0;
                int pb = b.priority != null ? b.priority : 0;
                if (pa != pb)
                    return Integer.compare(pb, pa);
                long sa = parseInstant(a.start);
                long sb = parseInstant(b.start);
                if (sa != sb)
                    return Long.compare(sb, sa);
                String ia = a.id != null ? a.id : "";
                String ib = b.id != null ? b.id : "";
                return ia.compareTo(ib);
            }
        });

        InAppMessage pick = eligible.get(0);
        myLogD("pickBestMessage: pick id=" + pick.id + " (eligible=" + eligible.size() + ")");
        return pick;
    }

    /** Vérifie les conditions (dates, api, versionCode, allow/deny). */
    public static boolean matches(Context ctx, InAppMessage m) {
        long now = System.currentTimeMillis();
        String id = (m.id == null ? "null" : m.id);

        // Dates
        long start = parseInstant(m.start);
        long end = parseInstant(m.end);
        if (start > 0 && now < start) {
            myLogD("matches(" + id + "): KO start>now (" + m.start + ")");
            return false;
        }
        if (end > 0 && now > end) {
            myLogD("matches(" + id + "): KO now>end (" + m.end + ")");
            return false;
        }

        // API
        int api = Build.VERSION.SDK_INT;
        if (m.api_min != null && api < m.api_min) {
            myLogD("matches(" + id + "): KO api<api_min (" + api + "<" + m.api_min + ")");
            return false;
        }
        if (m.api_max != null && api > m.api_max) {
            myLogD("matches(" + id + "): KO api>api_max (" + api + ">" + m.api_max + ")");
            return false;
        }

        // VersionCode
        int vc = appVersionCode(ctx);
        if (m.allow_vc != null && !m.allow_vc.isEmpty() && !m.allow_vc.contains(vc)) {
            myLogD("matches(" + id + "): KO allow_vc excludes current vc=" + vc);
            return false;
        }
        if (m.deny_vc != null && m.deny_vc.contains(vc)) {
            myLogD("matches(" + id + "): KO deny_vc contains current vc=" + vc);
            return false;
        }
        if (m.vc_min != null && vc < m.vc_min) {
            myLogD("matches(" + id + "): KO vc<vc_min (" + vc + "<" + m.vc_min + ")");
            return false;
        }
        if (m.vc_max != null && vc > m.vc_max) {
            myLogD("matches(" + id + "): KO vc>vc_max (" + vc + ">" + m.vc_max + ")");
            return false;
        }

        // Ciblage device
        if (m.brand != null && !m.brand.isEmpty()) {
            String b = safeLower(Build.BRAND);
            if (!b.equals(m.brand.toLowerCase(Locale.ROOT))) {
                myLogD("matches(" + id + "): KO brand mismatch (" + b + " != " + m.brand + ")");
                return false;
            }
        }
        if (m.model_regex != null && !m.model_regex.isEmpty()) {
            String model = Build.MODEL != null ? Build.MODEL : "";
            if (!model.matches(m.model_regex)) {
                myLogD("matches(" + id + "): KO model_regex mismatch (MODEL=" + model + ", regex=" + m.model_regex
                        + ")");
                return false;
            }
        }

        // Rollout %
        if (m.rollout_pct != null) {
            int bucket = stableBucket(ctx, m.id);
            if (bucket >= m.rollout_pct) {
                myLogD("matches(" + id + "): KO rollout (" + bucket + " >= " + m.rollout_pct + ")");
                return false;
            }
        }

        myLogD("matches(" + id + "): OK");
        return true;
    }

    // ------------------------------------------------------------------------
    // SEEN / REPEAT
    // ------------------------------------------------------------------------

    public static boolean wasSeen(Context ctx, InAppMessage m) {
        String key = seenKey(ctx, m);
        boolean seen = prefs(ctx).getBoolean(key, false);
        myLogD("wasSeen(" + (m.id == null ? "null" : m.id) + "): " + seen + " [key=" + key + "]");
        return seen;
    }

    public static void markSeen(Context ctx, InAppMessage m) {
        try {
            String key = seenKey(ctx, m);
            prefs(ctx).edit().putBoolean(key, true).apply();
            myLogD("markSeen: id=" + (m.id == null ? "null" : m.id) + " [key=" + key + "]");
        } catch (Throwable t) {
            myLogEE(t, "markSeen failed");
        }
    }

    private static String seenKey(Context ctx, InAppMessage m) {
        String id = m.id != null ? m.id : "noid";
        String repeat = m.repeat != null ? m.repeat : "once";
        if ("always".equalsIgnoreCase(repeat)) {
            return "seen_never_" + id; // jamais vraiment vu
        } else if ("once_per_version".equalsIgnoreCase(repeat)) {
            return "seen_" + id + "_vc" + appVersionCode(ctx);
        } else { // "once" par défaut
            return "seen_" + id;
        }
    }

    // ------------------------------------------------------------------------
    // FETCH JSON + CACHE (ETag)
    // ------------------------------------------------------------------------

    @Nullable
    public static InAppMessageIndex loadIndexFromCache(Context ctx) {
        SharedPreferences sp = prefs(ctx);
        String raw = sp.getString(KEY_JSON, null);
        String etag = sp.getString(KEY_ETAG, null);
        long ts = sp.getLong(KEY_FETCHED_AT, 0L);

        if (raw == null) {
            myLogD("loadIndexFromCache: no JSON cached");
            return null;
        }
        myLogD("loadIndexFromCache: len=" + raw.length() + ", etag=" + etag + ", fetched_at=" + ts
                + ", head=" + preview(raw));

        try {
            String norm = normalizeJsonForParsing(raw);
            myLogD("loadIndexFromCache: normalized head=" + preview(norm));

            // Cas 1: objet { ... }
            String t = ltrim(norm);
            if (!t.isEmpty() && t.charAt(0) == '{') {
                InAppMessageIndex idx = new Gson().fromJson(norm, InAppMessageIndex.class);
                int n = (idx != null && idx.messages != null) ? idx.messages.size() : 0;
                myLogD("loadIndexFromCache: parsed OBJECT, messages=" + n + ", generated_at="
                        + (idx != null ? idx.generated_at : "null"));
                return idx;
            }

            // Cas 2: tableau [ ... ] → on enveloppe dans un index
            if (!t.isEmpty() && t.charAt(0) == '[') {
                InAppMessage[] arr = new Gson().fromJson(norm, InAppMessage[].class);
                InAppMessageIndex idx = new InAppMessageIndex();
                idx.version = 1;
                idx.generated_at = null;
                idx.messages = new ArrayList<>();
                if (arr != null)
                    Collections.addAll(idx.messages, arr);
                myLogW("loadIndexFromCache: parsed ARRAY root (server returned []), messages="
                        + (idx.messages == null ? 0 : idx.messages.size()));
                return idx;
            }

            myLogE("loadIndexFromCache: unsupported JSON root (first char="
                    + (t.isEmpty() ? "empty" : ("'" + t.charAt(0) + "'")) + ")");
            return null;

        } catch (Throwable t) {
            myLogEE(t, "loadIndexFromCache: JSON parse error after normalization");
            return null;
        }
    }

    static void saveIndexToCache(Context ctx, String json, @Nullable String etag) {
        int len = (json == null ? 0 : json.length());
        long now = System.currentTimeMillis();
        prefs(ctx).edit()
                .putString(KEY_JSON, json)
                .putString(KEY_ETAG, etag)
                .putLong(KEY_FETCHED_AT, now)
                .apply();

        // Essaye de compter les messages pour log
        int count = -1;
        try {
            InAppMessageIndex idx = new Gson().fromJson(json, InAppMessageIndex.class);
            count = (idx != null && idx.messages != null) ? idx.messages.size() : 0;
        } catch (Throwable ignored) {
        }
        myLogD("Cache saved: len=" + len + ", etag=" + etag + ", fetched_at=" + now
                + ", messages=" + count);
    }

    static String fetchJson(String url, @Nullable String etag, Context context) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(5000);
        c.setReadTimeout(7000);
        if (etag != null && !etag.isEmpty())
            c.setRequestProperty("If-None-Match", etag);
        c.setRequestProperty("Accept", "application/json");
        c.setRequestProperty("Accept-Encoding", "gzip");

        int code = c.getResponseCode();
        String enc = c.getHeaderField("Content-Encoding");
        String newEtag = c.getHeaderField("ETag");
        String retryAfter = c.getHeaderField("Retry-After"); // peut servir au logging
        myLogD("fetchJson: HTTP " + code + ", ETag=" + newEtag + ", Content-Encoding=" + enc
                + (retryAfter != null ? (", Retry-After=" + retryAfter) : ""));

        if (code == HttpURLConnection.HTTP_NOT_MODIFIED) {
            c.disconnect();
            throw new NotModifiedException();
        }

        if (code == HttpURLConnection.HTTP_OK) {
            // (le reste de la méthode inchangé : lecture flux + saveIndexToCache + return)
        } else if (code == HttpURLConnection.HTTP_NOT_FOUND || code == HttpURLConnection.HTTP_GONE) {
            c.disconnect();
            throw new PermanentHttpException(code, "Permanent HTTP " + code);
        } else if (code >= 400 && code < 500 && code != 429) {
            // Erreurs client non récupérables
            c.disconnect();
            throw new PermanentHttpException(code, "Permanent HTTP " + code);
        } else if (code == 429 || code == 500 || code == 502 || code == 503 || code == 504) {
            // Transitoire / surcharge / maintenance
            c.disconnect();
            throw new RetryableHttpException(code, "Retryable HTTP " + code);
        } else {
            // Par défaut, considère retryable (ex: 521/522/523 Cloudflare, etc.)
            c.disconnect();
            throw new RetryableHttpException(code, "Retryable HTTP " + code);
        }

        try (InputStream rawIn = c.getInputStream();
                InputStream in = (enc != null && enc.toLowerCase(Locale.ROOT).contains("gzip"))
                        ? new java.util.zip.GZIPInputStream(rawIn)
                        : rawIn;
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1)
                out.write(buf, 0, n);
            String json = out.toString("UTF-8");
            myLogD("fetchJson: body head=" + preview(json));
            // myLogD(json);

            // Sauvegarde cache
            saveIndexToCache(context, json, newEtag);
            return json;
        } finally {
            c.disconnect();
        }
    }

    static class NotModifiedException extends Exception {
    }

    static class PermanentHttpException extends Exception {
        final int code;

        PermanentHttpException(int code, String msg) {
            super(msg);
            this.code = code;
        }
    }

    static class RetryableHttpException extends Exception {
        final int code;

        RetryableHttpException(int code, String msg) {
            super(msg);
            this.code = code;
        }
    }

    // ------------------------------------------------------------------------
    // WORKER
    // ------------------------------------------------------------------------

    public static class FetchWorker extends Worker {
        public FetchWorker(@NonNull Context context, @NonNull WorkerParameters params) {
            super(context, params);
        }

        @NonNull
        @Override
        public Result doWork() {
            SharedPreferences p = prefs(getApplicationContext());
            String etag = p.getString(KEY_ETAG, null);
            myLogD("FetchWorker: start, endpoint=" + ENDPOINT + ", etag=" + etag);

            try {
                String before = p.getString(KEY_JSON, null);
                fetchJson(ENDPOINT, etag, getApplicationContext());
                String after = p.getString(KEY_JSON, null);

                if (after != null && !after.equals(before)) {
                    myLogD("FetchWorker: cache updated (len=" + after.length() + ")");
                    LocalBroadcastManager.getInstance(getApplicationContext())
                            .sendBroadcast(new Intent(ACTION_CACHE_UPDATED));
                    myLogD("FetchWorker: broadcast ACTION_CACHE_UPDATED sent");
                } else {
                    myLogD("FetchWorker: cache content unchanged (but not 304 path)");
                }
                return Result.success();

            } catch (NotModifiedException ignore) {
                myLogD("FetchWorker: HTTP 304 Not Modified (etag matched)");
                return Result.success();

            } catch (PermanentHttpException e) {
                myLogW("FetchWorker: " + e.getMessage() + " — not retrying (will rely on next periodic run)");
                return Result.success();

            } catch (RetryableHttpException e) {
                myLogI("FetchWorker: " + e.getMessage() + " — will retry with backoff");
                return Result.retry();

            } catch (java.net.UnknownHostException e) {
                myLogI("FetchWorker: DNS resolution failed (UnknownHost). Will retry.");
                return Result.retry();

            } catch (java.net.SocketTimeoutException e) {
                myLogI("FetchWorker: timeout. Will retry.");
                return Result.retry();

            } catch (java.net.ConnectException e) {
                myLogI("FetchWorker: connect error. Will retry.");
                return Result.retry();

            } catch (java.net.SocketException e) {
                myLogI("FetchWorker: socket error (connection aborted). Will retry.");
                return Result.retry();

            } catch (javax.net.ssl.SSLException e) {
                // Souvent transitoire (handshake, date/heure, réseau capricieux)
                myLogI("FetchWorker: SSL error. Will retry.");
                return Result.retry();

            } catch (Throwable t) {
                myLogEE(t, "FetchWorker error");
                return Result.retry();
            }
        }
    }

    // ------------------------------------------------------------------------
    // OUTILS D’AFFICHAGE & TRADUCTION
    // ------------------------------------------------------------------------

    /** Convertit HTML simple en Spanned. */
    public static Spanned toSpanned(String html) {
        try {
            return androidx.core.text.HtmlCompat.fromHtml(
                    html == null ? "" : html,
                    androidx.core.text.HtmlCompat.FROM_HTML_MODE_LEGACY);
        } catch (Throwable t) {
            // Fallback AOSP
            return android.text.Html.fromHtml(html == null ? "" : html);
        }
    }

    /** Texte brut pour envoi au traducteur. */
    public static String stripToPlainText(String html) {
        try {
            CharSequence cs = toSpanned(html);
            return cs == null ? "" : cs.toString();
        } catch (Throwable t) {
            return html == null ? "" : html.replaceAll("<[^>]+>", " ").trim();
        }
    }

    /** Bouton "Traduire" — app Google Translate → autre app PROCESS_TEXT → web. */
    public static void openTranslate(Context ctx, String englishText) {
        if (ctx == null || englishText == null || englishText.isEmpty())
            return;

        String tl = safeLang(Locale.getDefault());

        // 1) App Google Translate
        try {
            Intent i = new Intent(Intent.ACTION_PROCESS_TEXT)
                    .setType("text/plain")
                    .putExtra(Intent.EXTRA_PROCESS_TEXT, englishText)
                    .putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true)
                    .setPackage(GOOGLE_TRANSLATE_PKG);
            safeStartActivity(ctx, i);
            return;
        } catch (Throwable ignored) {
        }

        // 2) Toute app compatible
        try {
            Intent any = new Intent(Intent.ACTION_PROCESS_TEXT)
                    .setType("text/plain")
                    .putExtra(Intent.EXTRA_PROCESS_TEXT, englishText)
                    .putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true);
            safeStartActivity(ctx, Intent.createChooser(any, "Translate"));
            return;
        } catch (Throwable ignored) {
        }

        // 3) Fallback web
        try {
            String url = "https://translate.google.com/?sl=en&tl="
                    + urlEncode(tl) + "&text=" + urlEncode(englishText) + "&op=translate";
            Intent web = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            safeStartActivity(ctx, web);
        } catch (Throwable ignored) {
        }
    }

    private static void openAction(Context ctx, Action a) {
        try {
            if (a == null)
                return;
            if (a.deeplink != null && !a.deeplink.isEmpty()) {
                Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(a.deeplink));
                safeStartActivity(ctx, i);
                return;
            }
            if (a.url != null && !a.url.isEmpty()) {
                Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(a.url));
                safeStartActivity(ctx, i);
            }
        } catch (Throwable t) {
            myLogEE(t, "openAction failed");
        }
    }

    private static Action firstAction(InAppMessage m) {
        if (m == null || m.actions == null || m.actions.isEmpty())
            return null;
        return m.actions.get(0);
    }

    private static String safeActionTitle(Action a, String lang, String fallback) {
        if (a == null || a.title == null)
            return fallback;
        String t = a.title.get(lang);
        if (t == null)
            t = a.title.get("en");
        return t != null ? t : fallback;
    }

    private static void safeStartActivity(Context ctx, Intent i) {
        if (!(ctx instanceof Activity))
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ctx.startActivity(i);
    }

    private static String urlEncode(String s) {
        try {
            return URLEncoder.encode(s == null ? "" : s, "UTF-8"); // compatible API 26
        } catch (UnsupportedEncodingException e) {
            return "";
        }
    }

    // ------------------------------------------------------------------------
    // DIVERS OUTILS
    // ------------------------------------------------------------------------

    public static String safeLang(Locale locale) {
        String l = (locale != null ? locale.getLanguage() : null);
        return (l == null || l.isEmpty()) ? "en" : l.toLowerCase(Locale.ROOT);
    }

    public static String pickLocalized(@Nullable Map<String, String> map, String lang) {
        if (map == null || map.isEmpty())
            return null;
        if (lang != null && map.containsKey(lang))
            return map.get(lang);
        if (map.containsKey("en"))
            return map.get("en");
        return null;
    }

    private static int appVersionCode(Context ctx) {
        try {
            PackageInfo pi = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0);
            long vc = PackageInfoCompat.getLongVersionCode(pi);
            return (int) vc;
        } catch (Throwable t) {
            return 0;
        }
    }

    private static long parseInstant(@Nullable String iso) {
        if (iso == null || iso.isEmpty())
            return 0L;
        try {
            return Instant.parse(iso).toEpochMilli();
        } catch (Throwable t) {
            return 0L;
        }
    }

    private static String safeLower(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }

    private static int stableBucket(Context ctx, @Nullable String salt) {
        // Bucket stable basé sur ANDROID_ID + salt (hash pauvre suffisant)
        String id = android.provider.Settings.Secure.getString(
                ctx.getContentResolver(), android.provider.Settings.Secure.ANDROID_ID);
        String base = (id == null ? "0" : id) + "|" + (salt == null ? "" : salt);
        int h = base.hashCode();
        if (h == Integer.MIN_VALUE)
            h = 0;
        return Math.abs(h) % 100;
    }

    private static SharedPreferences prefs(Context ctx) {
        return Pref.getInAppMsgPrefs();
    }

    private static Context AppCtx(Context c) {
        return c;
    }

    // ------------------------------------------------------------------------
    // POJOs GSON
    // ------------------------------------------------------------------------

    @Keep
    public static class InAppMessageIndex {
        public Integer version;
        public String generated_at;
        public List<InAppMessage> messages;
    }

    @Keep
    public static class InAppMessage {
        public String id;
        public Integer priority;
        public String start; // ISO 8601 (UTC), ex "2025-09-17T00:00:00Z"
        public String end;

        public Integer vc_min;
        public Integer vc_max;
        public List<Integer> allow_vc;
        public List<Integer> deny_vc;

        public Integer api_min;
        public Integer api_max;

        public Boolean dismissible; // non utilisé ici, mais dispo
        public String repeat; // "always" | "once" | "once_per_version"
        public String channel; // "modal" | "banner" | "inbox"

        public Map<String, String> body_html; // {"en":"...", "fr":"..."}
        public List<Action> actions; // boutons potentiels

        // Ciblage optionnel
        public String brand; // ex: "samsung"
        public String model_regex; // ex: "SM-.*"
        public Integer rollout_pct; // 0..100
    }

    @Keep
    public static class Action {
        public Map<String, String> title; // {"fr":"Mettre à jour","en":"Update"}
        public String url; // http/https/market
        public String deeplink; // ex: bookplayer://help/tts
    }

    /**
     * Retire BOM, découpe espaces, et si la racine est une CHAÎNE JSON, la
     * désérialise.
     */
    private static String normalizeJsonForParsing(String raw) {
        if (raw == null)
            return "";
        // 1) strip BOM
        String s = raw.replaceFirst("^\uFEFF", "");
        // 2) trim
        s = s.trim();
        if (s.isEmpty())
            return s;

        // 3) Si le JSON commence par un guillemet → c'est une chaîne JSON
        if (s.charAt(0) == '"') {
            try {
                // La chaîne contient probablement le vrai JSON (échappé) → on la "déquote"
                String unquoted = new Gson().fromJson(s, String.class);
                if (unquoted != null)
                    return unquoted.trim();
            } catch (Throwable ignore) {
                // On loguera plus loin si ça ne marche pas
            }
        }
        return s;
    }

    private static void myLogD(String msg) {
        if (DEBUG)
            com.driot.bookplayer.utils.log.LoggerStaticHelper.myLogD(msg);
    }

    private static String ltrim(String s) {
        if (s == null)
            return "";
        int i = 0;
        int n = s.length();
        while (i < n && Character.isWhitespace(s.charAt(i)))
            i++;
        return (i == 0) ? s : s.substring(i);
    }

    private static String preview(String s) {
        if (s == null)
            return "null";
        String p = s.replace("\n", "\\n").replace("\r", "\\r");
        return p.length() <= 120 ? p : p.substring(0, 120) + "…";
    }

    // used for tests from MainActivity
    public static void deleteInAppMsgCache(Context ctx) {
        prefs(ctx).edit().clear().apply();
        myLogD("deleteInAppMsgCache: all cached in-app messages cleared");
    }
}
