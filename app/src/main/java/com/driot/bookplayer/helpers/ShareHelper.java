package com.driot.bookplayer.helpers;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.Nullable;
import com.driot.bookplayer.R;
import com.driot.bookplayer.radio.RadioHelper;
import static com.driot.bookplayer.utils.log.LoggerStaticHelper.*;

public class ShareHelper {

    public static void shareContent(Context context, String messageBody, String messageHead, @Nullable Uri imageUri) {
        new Handler(Looper.getMainLooper()).post(() -> {
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            if (imageUri != null) {
                shareIntent.setType("image/*");
                shareIntent.putExtra(Intent.EXTRA_STREAM, imageUri);
                shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                // ClipData is required for reliable permission granting on newer Android
                // versions
                shareIntent.setClipData(android.content.ClipData.newRawUri(null, imageUri));
                // Attach text as well
                shareIntent.putExtra(Intent.EXTRA_TEXT, messageBody);
            } else {
                shareIntent.setType("text/plain");
                shareIntent.putExtra(Intent.EXTRA_TEXT, messageBody);
            }
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, messageHead);

            // If context is not an Activity, we need NEW_TASK
            if (!(context instanceof android.app.Activity)) {
                shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            }

            context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.share_title)));
        });
    }

    public static void handleDeepLink(Context context, Intent intent) {
        myLogD("=== handleDeepLink called ===");

        if (intent == null) {
            myLogI("Intent is NULL, return");
            return;
        }

        myLogD("Intent action: " + intent.getAction());

        Uri data = intent.getData();

        if (data == null) {
            myLogD("URI data is NULL, return");
            return;
        }

        if (data != null) {
            String host = data.getHost();
            String path = data.getPath();

            myLogI("DeepLink: host=[" + host + "] - path=[" + path + "] - data=[" + data.toString() + "]");

            if (host != null) {
                // Handle App Links (HTTPS) with specific paths
                if (path != null) {
                    switch (path) {
                        case "/share/radio":
                            RadioHelper.handleDeepLink(context, data);
                            return;
                    }
                }

                // Handle Custom URI schemes (e.g., bookplayerfull://radio)
                if (host.equals("radio")) {
                    RadioHelper.handleDeepLink(context, data);
                    return;
                }
            }
        }
    }
}