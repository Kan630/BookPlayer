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
            "title", "artist", "album", "genre", "year", "track", "disc",
            "bitrate", "samplerate", "channels"
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
        // If you later add localized strings for labels, map them here.
        switch (key) {
            case "title": return ctx.getString(R.string.title);
            case "artist": return ctx.getString(R.string.artist);
            case "album": return ctx.getString(R.string.album);
            case "genre": return ctx.getString(R.string.genre);
            case "year": return ctx.getString(R.string.year);
            case "track": return ctx.getString(R.string.track);
            case "disc": return ctx.getString(R.string.disc);
            case "bitrate": return ctx.getString(R.string.bitrate);
            case "samplerate": return ctx.getString(R.string.sample_rate);
            case "channels": return ctx.getString(R.string.channels);
            default: return prettyKey(key);
        }
    }

    private static String prettyKey(String key) {
        // turn "sample_rate" or "samplerate" into "Sample rate"
        String k = key.replace('_',' ').trim();
        if (k.isEmpty()) return key;
        return Character.toUpperCase(k.charAt(0)) + k.substring(1);
    }
}
