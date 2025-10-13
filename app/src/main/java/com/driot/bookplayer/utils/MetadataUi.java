package com.driot.bookplayer.utils;

import android.content.Context;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.StyleSpan;

import com.driot.bookplayer.R;
import com.driot.bookplayer.db.ZikFile;
import com.driot.bookplayer.helpers.ViewHelper;

import java.util.Map;

public final class MetadataUi {
    private MetadataUi() {}

    /** Build pretty, spanned metadata body from a ZikFile. Returns null/empty when no metadata. */
    public static CharSequence buildPretty(Context ctx, ZikFile z) {
        if (z == null) return null;
        Map<String,String> meta = MetaJson.fromJson(z.metadataJson);
        return MetadataFormatter.format(ctx, meta);
    }

    /** Show metadata dialog for a ZikFile. Handles empty metadata gracefully. */
    public static void showMetadataDialog(Context ctx, ZikFile z) {
        CharSequence body = buildPretty(ctx, z);
        if (body == null || body.length() == 0) {
            android.widget.Toast.makeText(ctx, R.string.no_metadata_available, android.widget.Toast.LENGTH_SHORT).show();
            return;
        }

        // Header (bold) + body (spans preserved)
        SpannableStringBuilder sb = new SpannableStringBuilder();
        String header = ctx.getString(R.string.metadata) + " :";
        int start = sb.length();
        sb.append(header).append('\n');
        sb.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), start, start + header.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.append(body);

        CharSequence title = (z.getDisplayName() != null && !z.getDisplayName().isEmpty())
                ? z.getDisplayName()
                : ctx.getString(R.string.metadata);

        ViewHelper.showAlertDialogText(ctx, sb, title);
    }
}
