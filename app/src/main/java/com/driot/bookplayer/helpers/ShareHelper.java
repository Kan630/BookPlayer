package com.driot.bookplayer.helpers;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

public class ShareHelper {

    public static void shareRadioStation(Context context, String url, String uuid) {
        String customLink = "bookplayer://share/radio?url=" + Uri.encode(url) + "&uuid=" + Uri.encode(uuid);
        String webLink = "https://bookplayer.driot.com/share/radio?url=" + Uri.encode(url) + "&uuid=" + Uri.encode(uuid);

        String sharedMessageBody = "!! BETA TEST !! Check out this radio on BookPlayer: " + customLink + "\n\nOr visit: " + webLink;
        String sharedMessageHead = "!! BETA TEST !!   BookPlayer radio share";

        shareContent(context, sharedMessageBody, sharedMessageHead);
    }

    private static void shareContent(Context context, String messageBody, String messageHead) {

        // Create share intent
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT, messageBody);
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, messageHead);

        // Show share dialog
        context.startActivity(Intent.createChooser(shareIntent, "Share via"));
    }
}