package com.driot.bookplayer.activities;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

// 2025-06-09    ---   Used so that the user can enable/disable openWith capability in Options, by enabling/disabling this activity

public class OpenWithProxyActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Forward the intent to the real activity
        Intent forwardIntent = new Intent(this, AddResourceActivity.class);
        forwardIntent.setAction(getIntent().getAction());
        forwardIntent.setData(getIntent().getData());
        forwardIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(forwardIntent);
        finish();
    }
}