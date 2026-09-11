package com.driot.bookplayer.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.Nullable;

import com.driot.bookplayer.helpers.FirebaseAnalyticsHelper;
import com.driot.bookplayer.helpers.OpenWithHelper;
import com.driot.bookplayer.helpers.UriHelper;
import com.driot.bookplayer.imports.ImportBookSingleActivity;
import com.driot.bookplayer.utils.log.BaseActivity;

// 2025-07-05


public class OpenWithProxyActivityAll extends BaseActivity {

    private static final int REQUEST_LOAD_OPTIONS = 1642;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        myLog("OpenWithProxyActivityAll");

        Uri uri = null;
        Intent receivedIntent = getIntent();
        String action = receivedIntent.getAction();

        if (Intent.ACTION_VIEW.equals(action)) {
            uri = receivedIntent.getData();
        } else if (Intent.ACTION_SEND.equals(action)) {
            uri = receivedIntent.getParcelableExtra(Intent.EXTRA_STREAM);
        }

        if (uri == null) {
            myToastEE(null,"OpenWithProxyActivityAll: URI is null");
            finish();
            return;
        }

        myLogD("OpenWithProxyActivityAll received uri: " + uri);
        boolean persistPermission = UriHelper.checkLongTermReadable(this, uri);

        //TODO looks like persistPermission=false => false positive, as the file is loaded and can be played...

        FirebaseAnalyticsHelper.tellAnalyticsProxyLoad(uri.toString(), "all", persistPermission);
        FirebaseAnalyticsHelper.setCustomKeyCrashlytics("ImportMode", "proxy-all");
        FirebaseAnalyticsHelper.setCustomKeyCrashlytics("persistPermission", String.valueOf(persistPermission));

        OpenWithHelper.handle(this, uri, persistPermission, (u, forceCopy) -> {
            Intent nextIntent = new Intent(this, ImportBookSingleActivity.class);
            nextIntent.putExtra(ImportBookSingleActivity.EXTRA_URI, u);
            nextIntent.putExtra(ImportBookSingleActivity.EXTRA_FORCE_COPY, forceCopy);
            startActivityForResult(nextIntent, REQUEST_LOAD_OPTIONS);
        });
    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_LOAD_OPTIONS && resultCode == RESULT_OK) {
            //BookLoadingWorkLauncher.launch(this);
            Intent intentActivity = new Intent(this, AddResourceActivity.class);
            startActivity(intentActivity);
        } else {
            myLogW("onActivityResult => not OK");
        }


        finish(); // Close proxy in all cases
    }
}