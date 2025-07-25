package com.driot.bookplayer.activities;

import android.os.Bundle;
import android.text.Html;
import android.widget.TextView;

import com.driot.bookplayer.R;
import com.driot.bookplayer.utils.log.LoggingActivity;

/**
 * created by Antoine Driot -- antoine.driot.com -- on 20/12/20
 */
public class HelpActivity extends LoggingActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_help);

        TextView tv;

        tv = findViewById(R.id.tvHelpText);
        tv.setText(Html.fromHtml(getString(R.string.help_text_general), Html.FROM_HTML_MODE_LEGACY));

        tv = findViewById(R.id.help_text_manual_import);
        tv.setText(Html.fromHtml(getString(R.string.help_text_manual_import), Html.FROM_HTML_MODE_LEGACY));

        tv = findViewById(R.id.help_librivox_text);
        tv.setText(Html.fromHtml(getString(R.string.help_librivox_text), Html.FROM_HTML_MODE_LEGACY));

        tv = findViewById(R.id.help_podcast_text);
        tv.setText(Html.fromHtml(getString(R.string.help_podcast_text), Html.FROM_HTML_MODE_LEGACY));

        tv = findViewById(R.id.help_url_text);
        tv.setText(Html.fromHtml(getString(R.string.help_url_text), Html.FROM_HTML_MODE_LEGACY));


        tv = findViewById(R.id.help_memory_cleaning_text);
        tv.setText(Html.fromHtml(getString(R.string.help_memory_cleaning_text), Html.FROM_HTML_MODE_LEGACY));

        tv = findViewById(R.id.help_storage_text);
        tv.setText(Html.fromHtml(getString(R.string.help_storage_text), Html.FROM_HTML_MODE_LEGACY));

        tv = findViewById(R.id.help_permission_text);
        tv.setText(Html.fromHtml(getString(R.string.help_permission_text), Html.FROM_HTML_MODE_LEGACY));

        tv = findViewById(R.id.help_tellme_text);
        tv.setText(Html.fromHtml(getString(R.string.help_tellme_text), Html.FROM_HTML_MODE_LEGACY));

        tv = findViewById(R.id.help_forum_text);
        tv.setText(Html.fromHtml(getString(R.string.help_forum_text), Html.FROM_HTML_MODE_LEGACY));

    }
}



//// -> To test Android 15, overlapping system bars dy default... (Solution adds to xml : android:fitsSystemWindows="true")
//WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

//Toolbar toolbar = findViewById(R.id.toolbar);
//toolbar.setTitle(R.string.help);