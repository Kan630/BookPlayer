package com.driot.bookplayer.activities;

import android.os.Bundle;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.driot.bookplayer.R;
import com.driot.bookplayer.helpers.InsetHelper;
import com.driot.bookplayer.utils.log.BaseActivity;
/**
 * created by Antoine Driot -- antoine.driot.com -- on 20/12/20
 */
public class HelpActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_help);
        ScrollView scrollView = findViewById(R.id.scroll_view);
        InsetHelper.applyInsetsForScrollableBehindNavBar(this, scrollView);

        TextView tv;
        LinearLayout ll;

        ll = findViewById(R.id.ll_help_text_general);
        tv = findViewById(R.id.tv_help_text_general);
        ll.setVisibility(LinearLayout.VISIBLE);
        tv.setText(Html.fromHtml(getString(R.string.help_text_general), Html.FROM_HTML_MODE_LEGACY));

        ll = findViewById(R.id.ll_help_text_manual_import);
        tv = findViewById(R.id.tv_help_text_manual_import);
        ll.setVisibility(LinearLayout.VISIBLE);
        tv.setText(Html.fromHtml(getString(R.string.help_text_manual_import), Html.FROM_HTML_MODE_LEGACY));

        ll = findViewById(R.id.ll_help_text_librivox);
        tv = findViewById(R.id.tv_help_text_librivox);
        ll.setVisibility(LinearLayout.VISIBLE);
        tv.setText(Html.fromHtml(getString(R.string.help_librivox_text), Html.FROM_HTML_MODE_LEGACY));

        boolean isPure = getPackageName().contains("com.driot.bookplayerpure");

        ll = findViewById(R.id.ll_help_text_podcast);
        tv = findViewById(R.id.tv_help_text_podcast);
        if (isPure) {
            ll.setVisibility(LinearLayout.GONE);
        } else {
            ll.setVisibility(LinearLayout.VISIBLE);
            tv.setText(Html.fromHtml(getString(R.string.help_podcast_text), Html.FROM_HTML_MODE_LEGACY));
        }

        ll = findViewById(R.id.ll_help_text_url);
        tv = findViewById(R.id.tv_help_text_url);
        ll.setVisibility(LinearLayout.VISIBLE);
        tv.setText(Html.fromHtml(getString(R.string.help_url_text), Html.FROM_HTML_MODE_LEGACY));

        ll = findViewById(R.id.ll_help_text_quick_share);
        tv = findViewById(R.id.tv_help_text_quick_share);
        ll.setVisibility(LinearLayout.VISIBLE);
        tv.setText(Html.fromHtml(getString(R.string.help_quick_share_text), Html.FROM_HTML_MODE_LEGACY));

        ll = findViewById(R.id.ll_help_text_radio);
        tv = findViewById(R.id.tv_help_text_radio);
        if (isPure) {
            ll.setVisibility(LinearLayout.GONE);
        } else {
            ll.setVisibility(LinearLayout.VISIBLE);
            tv.setText(Html.fromHtml(getString(R.string.help_radio_text), Html.FROM_HTML_MODE_LEGACY));
        }

        ll = findViewById(R.id.ll_help_text_tts);
        tv = findViewById(R.id.tv_help_text_tts);
        ll.setVisibility(LinearLayout.VISIBLE);
        tv.setText(Html.fromHtml(getString(R.string.help_tts_text), Html.FROM_HTML_MODE_LEGACY));

        ll = findViewById(R.id.ll_help_text_memory_cleaning);
        tv = findViewById(R.id.tv_help_text_memory_cleaning);
        ll.setVisibility(LinearLayout.VISIBLE);
        tv.setText(Html.fromHtml(getString(R.string.help_memory_cleaning_text), Html.FROM_HTML_MODE_LEGACY));

        ll = findViewById(R.id.ll_help_text_storage);
        tv = findViewById(R.id.tv_help_text_storage);
        ll.setVisibility(LinearLayout.VISIBLE);
        tv.setText(Html.fromHtml(getString(R.string.help_storage_text), Html.FROM_HTML_MODE_LEGACY));

        ll = findViewById(R.id.ll_help_text_permission);
        tv = findViewById(R.id.tv_help_text_permission);
        ll.setVisibility(LinearLayout.VISIBLE);
        tv.setText(Html.fromHtml(getString(R.string.help_permission_text), Html.FROM_HTML_MODE_LEGACY));

        ll = findViewById(R.id.ll_help_text_tellme);
        tv = findViewById(R.id.tv_help_text_tellme);
        ll.setVisibility(LinearLayout.VISIBLE);
        tv.setText(Html.fromHtml(getString(R.string.help_tellme_text), Html.FROM_HTML_MODE_LEGACY));

        ll = findViewById(R.id.ll_help_text_forum);
        tv = findViewById(R.id.tv_help_text_forum);
        ll.setVisibility(LinearLayout.VISIBLE);
        tv.setText(Html.fromHtml(getString(R.string.help_forum_text), Html.FROM_HTML_MODE_LEGACY));
        tv.setMovementMethod(LinkMovementMethod.getInstance());

    }
}
