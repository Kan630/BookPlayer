package com.driot.bookplayer.activities;

import static com.driot.bookplayer.global.Pref.getLoadBookTaskState;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.Nullable;

import com.driot.bookplayer.services.AddResourceService;
import com.driot.bookplayer.utils.log.LoggingActivity;

// 2025-07-05


public class OpenWithProxyActivityAll extends LoggingActivity {

    private static final int REQUEST_LOAD_OPTIONS = 1642;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        myLog("OpenWithProxyActivity");

        Uri uri = null;
        Intent receivedIntent = getIntent();
        String action = receivedIntent.getAction();

        if (Intent.ACTION_VIEW.equals(action)) {
            uri = receivedIntent.getData();
        } else if (Intent.ACTION_SEND.equals(action)) {
            uri = receivedIntent.getParcelableExtra(Intent.EXTRA_STREAM);
        }

        if (uri == null) {
            myToastE("OpenWithProxyActivity: URI is null");
            finish();
            return;
        }

        myLogD("OpenWithProxyActivity received uri: " + uri);

        Intent nextIntent = new Intent(this, LoadOptionsActivity.class);
        nextIntent.putExtra(LoadOptionsActivity.EXTRA_URI, uri);
        nextIntent.putExtra(LoadOptionsActivity.EXTRA_TYPE, "File");
        startActivityForResult(nextIntent, REQUEST_LOAD_OPTIONS);


    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_LOAD_OPTIONS && resultCode == RESULT_OK) {
            Intent intentService = new Intent(this, AddResourceService.class);
            intentService.putExtra("LoadBookTaskState", getLoadBookTaskState(this));
            startService(intentService);

            Intent intentActivity = new Intent(this, AddResourceActivity.class);
            intentService.putExtra("LoadBookTaskState", getLoadBookTaskState(this));
            startActivity(intentActivity);
        } else {
            myLogW("onActivityResult => not OK");
        }


        finish(); // Close proxy in all cases
    }
}