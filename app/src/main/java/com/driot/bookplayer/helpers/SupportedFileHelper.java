package com.driot.bookplayer.helpers;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;

import com.driot.bookplayer.global.Var;

public class SupportedFileHelper {



    private static String safeString(String s) {
        return s == null ? "" : s;
    }

    private static String getSafeExtension(String fileName) {
        if (fileName == null) return "";
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) return "";
        return fileName.substring(dot + 1).toLowerCase(java.util.Locale.ROOT);
    }

    /** Try several strategies to get a MIME, return "" if unknown. */
    private static String resolveMimeType(@NonNull Context ctx, @NonNull Uri uri, @NonNull String extLower) {
        // 1) ContentResolver
        String mt = null;
        try { mt = ctx.getContentResolver().getType(uri); } catch (Exception ignored) {}
        if (mt != null) return mt;

        // 2) MimeTypeMap from extension
        if (!extLower.isEmpty()) {
            String map = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(extLower);
            if (map != null) return map;
        }

        // 3) Guess from name (cheap heuristic)
        try {
            String guess = java.net.URLConnection.guessContentTypeFromName(uri.toString());
            if (guess != null) return guess;
        } catch (Exception ignored) {}

        // 4) Unknown
        return "";
    }

    private static boolean isAudio(@NonNull String mimeType, @NonNull String extLower) {
        boolean mimeOk = mimeType.startsWith(Var.ONLY_MIME_AUDIO);
        boolean extOk  = Var.SUPPORTED_AUDIO_EXTENSIONS.contains(extLower);
        return mimeOk || extOk;
    }

    private static boolean isVideo(@NonNull String mimeType, @NonNull String extLower) {
        boolean mimeOk = mimeType.startsWith(Var.ONLY_MIME_VIDEO);
        boolean extOk  = Var.SUPPORTED_VIDEO_EXTENSIONS.contains(extLower);
        return mimeOk || extOk;
    }
}
