package com.driot.bookplayer.helpers;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

public class ShareHelper {

    public static void shareRadioStation(Context context, String url, String uuid) {
        //String deepLink = "bookplayer://" + type + "?url=" + Uri.encode(url);

        String shareUrl = "https://bookplayer.driot.com/share/radio" + "?url=" + Uri.encode(url) + "&uuid=" + Uri.encode(uuid);
        String shareMessage = "!! BETA TEST !!   Check out this radio on BookPlayer: " + shareUrl;

        shareContent(context, shareMessage, "radio station");
    }

    public static void shareLibrivoxBook(Context context, String bookUrl) {
        //shareContent(context, "librivox", bookUrl, "audiobook");
    }

    private static void shareContent(Context context, String shareMessage, String description) {
        // Create deep link
        //shareMessage = "Check out this " + description + " on BookPlayer: " + deepLink;


        // Create share intent
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT, shareMessage);
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, "!! BETA TEST !!   BookPlayer - " + description);

        // Show share dialog
        context.startActivity(Intent.createChooser(shareIntent, "Share via"));
    }
}