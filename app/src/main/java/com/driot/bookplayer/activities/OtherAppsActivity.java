package com.driot.bookplayer.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import com.driot.bookplayer.R;
import com.driot.bookplayer.utils.log.LoggingActivity;

/**
 * created by Antoine Driot -- antoine.driot.com -- on 20/12/20
 */
public class OtherAppsActivity extends LoggingActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_otherapps);

        findViewById(R.id.ll_DroitPositif).setOnClickListener(view -> openPlayStoreApp("com.driot.droitpositif"));
        findViewById(R.id.ll_Deces).setOnClickListener(view -> openPlayStoreApp("com.driot.deces"));
        findViewById(R.id.ll_Scanner).setOnClickListener(view -> openPlayStoreApp("com.driot.scanner"));
       /* findViewById(R.id.ll_KanKwiz).setOnClickListener(view -> openPlayStoreApp("com.driot.KanKwiz"));*/
    }

    private void openPlayStoreApp(String appPackageName) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + appPackageName)));
        } catch (android.content.ActivityNotFoundException anfe) {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + appPackageName)));
        }
    }
}
