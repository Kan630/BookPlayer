package com.driot.tonylib;

import static android.content.Context.MODE_PRIVATE;
import static com.driot.tonylib.KanLogger.myToast;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.List;

public class KanMail {
    private static String s_listRecipients;
    private static String s_subject;
    private static String s_body;
    private static Context appContext;

    //cannot get import, maybe because the java class is in another Lib...
    public static final String SHARED_PREFERENCES_OPTIONS = "SHARED_PREFERENCES_OPTIONS"; // shared prefs xml file
    public static final boolean DEFAULT_SEND_MAIL_METHOD_DEFAULT = true;



    public static void sendDaMail(Context c, String listRecipients, String subject, String body) {
        myLog("Preparing mail for " + listRecipients);

        appContext = c;
        s_listRecipients = listRecipients;
        s_body = body;
        s_subject = subject;

        SharedPreferences prefs = appContext.getSharedPreferences(SHARED_PREFERENCES_OPTIONS, MODE_PRIVATE);
        if (prefs.getBoolean("SEND_MAIL_METHOD_DEFAULT", DEFAULT_SEND_MAIL_METHOD_DEFAULT)) {
            sendMail_DefaultApp();
        } else {
            sendMail_andLetUserChooseEmailApp();
        }
    }

    private static void sendMail_DefaultApp() {
        String uriText =
                "mailto:" + s_listRecipients +
                        "?subject=" + Uri.encode(s_subject) +
                        "&body=" + Uri.encode(s_body);
        Uri uri = Uri.parse(uriText);
        Intent sendIntent = new Intent(Intent.ACTION_SENDTO);
        sendIntent.setData(uri);
        appContext.startActivity(Intent.createChooser(sendIntent, "Send email"));
    }

    private static void sendMail_andLetUserChooseAnyApp() {
        myToast("Please select an email app");
        Intent emailIntent = new Intent(Intent.ACTION_SEND);
        emailIntent.setType("message/rfc822");
        emailIntent.putExtra(Intent.EXTRA_EMAIL, new String[]{s_listRecipients});
        emailIntent.putExtra(Intent.EXTRA_SUBJECT, s_subject);
        emailIntent.putExtra(Intent.EXTRA_TEXT, s_body);
        appContext.startActivity(emailIntent);
    }

    private static void sendMail_andLetUserChooseEmailApp() {
        /// try to get Apps that can handle Emails
        Intent emailIntent = new Intent(Intent.ACTION_SEND);
        emailIntent.setData(Uri.parse("mailto:"));

        // Create a list of email apps only
        PackageManager pm = appContext.getPackageManager();
        List<ResolveInfo> emailApps = pm.queryIntentActivities(emailIntent, 0);

        if (!emailApps.isEmpty()) {
            // Create a list of email-specific intent filters
            List<Intent> emailIntents = new ArrayList<>();

            for (ResolveInfo info : emailApps) {
                Intent intent = new Intent(Intent.ACTION_SENDTO);
                intent.setComponent(new ComponentName(info.activityInfo.packageName, info.activityInfo.name));
                intent.setData(Uri.parse("mailto:"));
                intent.putExtra(Intent.EXTRA_EMAIL, new String[]{s_listRecipients});
                intent.putExtra(Intent.EXTRA_SUBJECT, s_subject);
                intent.putExtra(Intent.EXTRA_TEXT, s_body);
                emailIntents.add(intent);
            }

            // Create a chooser dialog with email apps only
            Intent chooser = Intent.createChooser(emailIntents.remove(0), "Send Email");
            chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, emailIntents.toArray(new Parcelable[]{}));
            appContext.startActivity(chooser);

        } else {
            // No email apps found, handle accordingly
            myLogE("no app found to handle 'Intent.ACTION_SENDTO' 'mailto:' - using basic 'Intent.ACTION_SEND'");
            sendMail_andLetUserChooseAnyApp();
        }

    }
    ////////////////////////////////////////////////////////
    ///////// Loggers
    ////////////////////////////////////////////////////////
    private static final String TAG = "KanMail";
    private static void myLog(String str) { KanLogger.myLog(TAG, str); }
    private static void myLogE(String str) { KanLogger.myLogE(TAG, str); }
}
