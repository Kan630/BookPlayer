package com.driot.bookplayer.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.driot.bookplayer.utils.AddResourceService;
import com.driot.bookplayer.utils.KanLogger;

// 2025-06-09    ---   Used so that the user can enable/disable openWith capability in Options, by enabling/disabling this activity




public class OpenWithProxyActivity extends LifecycleLoggingActivity {

    private static final int REQUEST_LOAD_OPTIONS = 1641;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        myLog("OpenWithProxyActivity");

        Uri uri = getIntent().getData();
        if (uri == null) {
            myToastE("OpenWithProxyActivity: URI is null");
            finish();
            return;
        }

        Intent intent = new Intent(this, LoadOptionsActivity.class);
        intent.putExtra(LoadOptionsActivity.EXTRA_URI, uri);
        intent.putExtra(LoadOptionsActivity.EXTRA_TYPE, "File");
        startActivityForResult(intent, REQUEST_LOAD_OPTIONS);

        /*
        // Forward the intent to the real activity
        Intent forwardIntent = new Intent(this, AddResourceActivity.class);
        forwardIntent.setAction(getIntent().getAction());
        forwardIntent.setData(getIntent().getData());
        forwardIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(forwardIntent);
        finish();

         */


    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_LOAD_OPTIONS && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getParcelableExtra("uri");
            String type = data.getStringExtra("type");
            String title = data.getStringExtra("title");
            boolean split = data.getBooleanExtra("split", false);
            boolean copy = data.getBooleanExtra("copy", false);
            boolean delete = data.getBooleanExtra("delete", false);

            // Launch the actual import service
            Intent intentService = new Intent(this, AddResourceService.class);
            intentService.putExtra("uri", uri);
            intentService.putExtra("type", type);
            intentService.putExtra("title", title);
            intentService.putExtra("split", split);
            intentService.putExtra("copy", copy);
            intentService.putExtra("delete", delete);
            startService(intentService);

            // And the activity
            Intent intentActivity = new Intent(this, AddResourceActivity.class);
            intentActivity.putExtra("uri", uri);
            intentActivity.putExtra("type", type);
            intentActivity.putExtra("title", title);
            intentActivity.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intentActivity);
        }

        finish(); // Close proxy in all cases
    }

    //--- LOG --------------------------
    private void myLog(String str) { KanLogger.myLog(this.getClass().getName(), str); }
    private void myLogE(String str) { KanLogger.myLogE(this.getClass().getName(), str); }
    private void myLogD(String str) { KanLogger.myLogD(this.getClass().getName(), str); }
    private void myLogI(String str) { KanLogger.myLogI(this.getClass().getName(), str); }
    private void myToast(String str) { KanLogger.myToast(this.getClass().getName(), str); }
    private void myToastE(String str) { KanLogger.myToastE(this.getClass().getName(), str); }
}