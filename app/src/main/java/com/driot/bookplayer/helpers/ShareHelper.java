package com.driot.bookplayer.helpers;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import com.driot.bookplayer.R;
import com.driot.bookplayer.global.Intents;
import com.driot.bookplayer.player.StartPlayHelper;
import com.driot.bookplayer.radio.RadioStationActivity;

import static com.driot.bookplayer.utils.log.LoggerStaticHelper.*;

public class ShareHelper {

    public static void shareRadioStation(Context context, String url, String uuid, String name) {
        //String customLink = "bookplayer://share/radio?url=" + Uri.encode(url) + "&uuid=" + Uri.encode(uuid);
        String radioLink = "https://bookplayer.driot.com/share/radio?url=" + Uri.encode(url) + "&uuid=" + Uri.encode(uuid);

        //String sharedMessageBody = "!! BETA TEST !! Check out this radio on BookPlayer: " + customLink + "\n\nOr visit: " + webLink;
        String sharedMessageBody = context.getString(R.string.share_radio_body) + ": \n" + name + "\n" + radioLink;
        String sharedMessageHead = context.getString(R.string.share_radio_head);

        shareContent(context, sharedMessageBody, sharedMessageHead);
    }

    private static void shareContent(Context context, String messageBody, String messageHead) {

        // Create share intent
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT, messageBody);
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, messageHead);

        // Show share dialog
        context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.share_title)));
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
                        String url = data.getQueryParameter("url");
                        String uuid = data.getQueryParameter("uuid");
                        myLog("url=[" + url + "] - uuid=[" + uuid + "]");

                        if (uuid != null) {
                            Intent i = new Intent(context, RadioStationActivity.class);
                            i.putExtra(Intents.EXTRA_STATION_UUID, uuid);
                            i.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                            context.startActivity(i);
                        }
                        if (url != null) {
                            StartPlayHelper.playRadioFromUuidAndUrl(context, uuid, url, "DeepLink");
                        }

                        break;

                }
            }
        }
    }
}