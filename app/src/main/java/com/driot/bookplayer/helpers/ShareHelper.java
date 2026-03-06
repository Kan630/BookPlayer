package com.driot.bookplayer.helpers;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import com.driot.bookplayer.R;
import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.db.RadioStation;
import com.driot.bookplayer.radio.RadioHelper;
import java.io.File;
import static com.driot.bookplayer.utils.log.LoggerStaticHelper.*;

public class ShareHelper {

    public static void shareRadioStation(Context context, String uuid) {
        Context appCtx = context.getApplicationContext();
        AppDatabase.databaseReadExecutor.execute(() -> {
            RadioStation rs = AppDatabase.getInstance(appCtx).radioStationDao().findByUuid(uuid);
            if (rs == null) {
                myLogE("ShareHelper.shareRadioStation: station not found in DB");
                return;
            }

            String radioLink = "https://bookplayer.driot.com/share/radio?url=" + Uri.encode(rs.url_resolved)
                    + "&uuid=" + Uri.encode(uuid);

            String sharedMessageBody = appCtx.getString(R.string.share_radio_body) + ": \n\n" + rs.name + "\n\n"
                    + radioLink;
            String sharedMessageHead = appCtx.getString(R.string.share_radio_head);

            // Optional image sharing
            Uri imageUri = null;
            if (rs.favicon != null && !rs.favicon.isEmpty()) {
                if (rs.favicon.startsWith("http")) {
                    // Download if it's a URL
                    String fileName = "share_radio_" + rs.stationuuid + ".jpg";
                    String localPath = ImageHelper.downloadAndVerifyImage(appCtx, rs.favicon, fileName, true);
                    if (localPath != null) {
                        try {
                            imageUri = FileProvider.getUriForFile(appCtx, appCtx.getPackageName() + ".FileProvider",
                                    new File(localPath));
                        } catch (Exception e) {
                            myLogEE(e, "shareRadioStation: FileProvider failed for downloaded " + localPath);
                        }
                    }
                } else if (rs.favicon.startsWith("content://")) {
                    // Already a content URI
                    imageUri = Uri.parse(rs.favicon);
                } else {
                    // Assume it's a local absolute path
                    File file = new File(rs.favicon);
                    if (file.exists()) {
                        try {
                            imageUri = FileProvider.getUriForFile(appCtx, appCtx.getPackageName() + ".FileProvider",
                                    file);
                        } catch (Exception e) {
                            myLogEE(e, "shareRadioStation: FileProvider failed for local file " + rs.favicon);
                        }
                    } else {
                        myLogW("shareRadioStation: local image file not found: " + rs.favicon);
                    }
                }
            }

            shareContent(context, sharedMessageBody, sharedMessageHead, imageUri);
        });
    }

    private static void shareContent(Context context, String messageBody, String messageHead, @Nullable Uri imageUri) {
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

                switch (path) {

                    case "/share/radio":
                        RadioHelper.handleDeepLink(context, data);
                        break;

                }
            }
        }
    }
}