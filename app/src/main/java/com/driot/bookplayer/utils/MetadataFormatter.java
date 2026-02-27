package com.driot.bookplayer.utils;

import android.content.Context;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.StyleSpan;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.driot.bookplayer.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class MetadataFormatter {
    private MetadataFormatter() {}

    /** Order + labels for common keys; the rest will follow alphabetically. */
    private static final String[] PREFERRED_ORDER = {
            "title", "album", "artist", "composer", "year", "genre", "track", "disc",
            "bitrate", "samplerate", "channels", "mime"
    };

    public static @Nullable CharSequence format(@NonNull Context ctx, @Nullable Map<String,String> meta) {
        if (meta == null || meta.isEmpty()) return null;

        SpannableStringBuilder sb = new SpannableStringBuilder();

        // 1) preferred keys
        for (String k : PREFERRED_ORDER) {
            String v = meta.get(k);
            if (isNotEmpty(v)) {
                appendLine(sb, label(ctx, k), v);
            }
        }

        // 2) remaining keys (alpha)
        List<String> rest = new ArrayList<>(meta.keySet());
        for (String k : PREFERRED_ORDER) rest.remove(k);
        Collections.sort(rest, String::compareToIgnoreCase);
        for (String k : rest) {
            String v = meta.get(k);
            if (isNotEmpty(v)) {
                appendLine(sb, prettyKey(k), v);
            }
        }

        return (sb.length() == 0) ? null : sb;
    }

    private static void appendLine(SpannableStringBuilder sb, String label, String value) {
        int start = sb.length();
        sb.append(label).append(": ");
        sb.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), start, start + label.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.append(value).append("\n");
    }

    private static boolean isNotEmpty(String s) { return s != null && !s.trim().isEmpty(); }

    private static String label(Context ctx, String key) {
        String localized = null;
        switch (key) {
            case "title": localized = ctx.getString(R.string.title); break;
            case "artist": localized = ctx.getString(R.string.Artist); break;
            case "album": localized = ctx.getString(R.string.Album); break;
            case "genre": localized = ctx.getString(R.string.Genre); break;
            case "year": localized = ctx.getString(R.string.Year); break;
            case "track": localized = ctx.getString(R.string.Track); break;
            case "disc": localized = ctx.getString(R.string.Disc); break;
            case "bitrate": localized = ctx.getString(R.string.Bitrate); break;
            case "samplerate": localized = ctx.getString(R.string.sample_rate); break;
            case "channels": localized = ctx.getString(R.string.Channels); break;
            default: return prettyKey(key);
        }
        return prettyKey(localized);
    }

    private static String prettyKey(String key) {
        // turn "sample_rate" or "samplerate" into "Sample rate"
        String k = key.replace('_',' ').trim();
        if (k.isEmpty()) return key;
        return Character.toUpperCase(k.charAt(0)) + k.substring(1);
    }
}
