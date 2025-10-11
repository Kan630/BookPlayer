package com.driot.bookplayer.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.Nullable;

import com.driot.bookplayer.helpers.FirebaseAnalyticsHelper;
import com.driot.bookplayer.helpers.UriHelper;
import com.driot.bookplayer.services.BookLoadingWorkLauncher;
import com.driot.bookplayer.utils.log.LoggingActivity;

// 2025-06-09    ---   Used so that the user can enable/disable openWith capability in Options, by enabling/disabling this activity




public class OpenWithProxyActivity extends LoggingActivity {

    private static final int REQUEST_LOAD_OPTIONS = 1641;

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
            myToastEE(null,"OpenWithProxyActivity: URI is null");
            finish();
            return;
        }

        myLogD("OpenWithProxyActivity received uri: " + uri);
        boolean persistPermission = UriHelper.checkLongTermReadable(this, uri);
        FirebaseAnalyticsHelper.tellAnalyticsProxyLoad(uri.toString());

        Intent nextIntent = new Intent(this, LoadBookActivity.class);
        nextIntent.putExtra(LoadBookActivity.EXTRA_URI, uri);
        nextIntent.putExtra(LoadBookActivity.EXTRA_TYPE, "File");
        nextIntent.putExtra(LoadBookActivity.EXTRA_FORCE_COPY, !persistPermission);
        startActivityForResult(nextIntent, REQUEST_LOAD_OPTIONS);

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_LOAD_OPTIONS && resultCode == RESULT_OK) {
            BookLoadingWorkLauncher.launch(this);
            Intent intentActivity = new Intent(this, AddResourceActivity.class);
            startActivity(intentActivity);
        } else {
            myLogW("onActivityResult => not OK");
        }
        finish(); // Close proxy in all cases
    }
}