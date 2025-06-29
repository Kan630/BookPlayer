package com.driot.bookplayer.activities;

import android.os.Bundle;

import com.driot.bookplayer.R;

/**
 * created by Antoine Driot -- antoine.driot.com -- on 20/12/20
 */
public class HelpActivity extends LoggingActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_help);

        //// -> To test Android 15, overlapping system bars dy default... (Solution adds to xml : android:fitsSystemWindows="true")
        //WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        //Toolbar toolbar = findViewById(R.id.toolbar);
        //toolbar.setTitle(R.string.help);

    }
}
