package com.driot.bookplayer.helpers;

import android.view.View;
import android.widget.TextView;

import java.util.regex.Pattern;

public class TitleHelper {

    public static void setTitleAndSubtitle(
            TextView tvTitle,
            TextView tvSubTitle,
            String folderName,
            String displayName
    ) {
        if (tvTitle == null || tvSubTitle == null) return;

        // Main title
        tvTitle.setText(folderName);

        // Derive subtitle by stripping folder name from display name
        String subTitle = displayName != null ? displayName : "";
        if (folderName != null && !folderName.isEmpty()) {
            subTitle = subTitle.replaceFirst("^" + Pattern.quote(folderName), "").trim();
        }

        // Show or hide subtitle
        if (subTitle.isEmpty() || subTitle.equals(folderName)) {
            tvSubTitle.setVisibility(View.GONE);

            // If Title is single line, expand to 2 lines
            if (tvTitle.getMaxLines() == 1) {
                tvTitle.setSingleLine(false);
                tvTitle.setMaxLines(2);
            }
        } else {
            tvSubTitle.setText(subTitle);
            tvSubTitle.setVisibility(View.VISIBLE);

            // Reset Title back to single line (if that’s your default)
            tvTitle.setSingleLine(true);
            tvTitle.setMaxLines(1);
        }
    }
}
