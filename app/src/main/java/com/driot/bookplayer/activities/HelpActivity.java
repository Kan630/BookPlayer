package com.driot.bookplayer.activities;

import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.driot.bookplayer.R;
import com.driot.bookplayer.helpers.InsetHelper;
import com.driot.bookplayer.helpers.ViewHelper;
import com.driot.bookplayer.utils.Tonio;
import com.driot.bookplayer.utils.log.BaseActivity;
/**
 * created by Antoine Driot -- antoine.driot.com -- on 20/12/20
 */
public class HelpActivity extends BaseActivity {

    // Resolves <img src="..."> tags in help_*_text strings to app drawables (the copy/link icons
    // in help_text_import), tinted to match the app-wide copy/link colors. Maps known
    // source names directly to R.drawable ids rather than Resources.getIdentifier(name, type,
    // getPackageName()) - the full/pure flavors' applicationId ("com.driot.bookplayerfull"/"pure",
    // plus a ".debug" suffix on debug builds) doesn't match the resource package (fixed to
    // "com.driot.bookplayer" via the module's namespace), so getIdentifier() with getPackageName()
    // always returned 0 and the icons silently failed to render.
    private final Html.ImageGetter drawableNameImageGetter = source -> {
        int resId;
        int tintColor;
        if ("ic_content_copy_24px".equals(source)) {
            resId = R.drawable.ic_content_copy_24px;
            tintColor = androidx.core.content.ContextCompat.getColor(this, R.color.storage_copy_color);
        } else if ("ic_link_2_24px".equals(source)) {
            resId = R.drawable.ic_link_2_24px;
            tintColor = androidx.core.content.ContextCompat.getColor(this, R.color.storage_link_color);
        } else {
            return null;
        }
        Drawable d = androidx.core.content.ContextCompat.getDrawable(this, resId);
        if (d == null)
            return null;
        d = d.mutate();
        d.setColorFilter(tintColor, android.graphics.PorterDuff.Mode.SRC_IN);
        int size = ViewHelper.dp(this, 16);
        d.setBounds(0, 0, size, size);
        return d;
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_help);
        ScrollView scrollView = findViewById(R.id.scroll_view);
        InsetHelper.applyInsetsForScrollableBehindNavBar(this, scrollView);

        TextView tv;
        LinearLayout ll;

        boolean pure = Tonio.isPure(this);

        ll = findViewById(R.id.ll_help_text_general);
        tv = findViewById(R.id.tv_help_text_general);
        ll.setVisibility(LinearLayout.VISIBLE);
        tv.setText(Html.fromHtml(getString(R.string.help_text_general), Html.FROM_HTML_MODE_LEGACY));

        ll = findViewById(R.id.ll_help_text_import);
        tv = findViewById(R.id.tv_help_text_import);
        ll.setVisibility(LinearLayout.VISIBLE);
        tv.setText(Html.fromHtml(getString(R.string.help_text_import), Html.FROM_HTML_MODE_LEGACY,
                drawableNameImageGetter, null));

        ll = findViewById(R.id.ll_help_text_librivox);
        tv = findViewById(R.id.tv_help_text_librivox);
        if (pure) {
            ll.setVisibility(LinearLayout.GONE);
        } else {
            ll.setVisibility(LinearLayout.VISIBLE);
            tv.setText(Html.fromHtml(getString(R.string.help_librivox_text), Html.FROM_HTML_MODE_LEGACY));
        }

        ll = findViewById(R.id.ll_help_text_podcast);
        tv = findViewById(R.id.tv_help_text_podcast);
        if (pure) {
            ll.setVisibility(LinearLayout.GONE);
        } else {
            ll.setVisibility(LinearLayout.VISIBLE);
            tv.setText(Html.fromHtml(getString(R.string.help_podcast_text), Html.FROM_HTML_MODE_LEGACY));
        }

        ll = findViewById(R.id.ll_help_text_url);
        tv = findViewById(R.id.tv_help_text_url);
        if (pure) {
            ll.setVisibility(LinearLayout.GONE);
        } else {
            ll.setVisibility(LinearLayout.VISIBLE);
            tv.setText(Html.fromHtml(getString(R.string.help_url_text), Html.FROM_HTML_MODE_LEGACY));
        }

        ll = findViewById(R.id.ll_help_text_quick_share);
        tv = findViewById(R.id.tv_help_text_quick_share);
        ll.setVisibility(LinearLayout.VISIBLE);
        tv.setText(Html.fromHtml(getString(R.string.help_quick_share_text), Html.FROM_HTML_MODE_LEGACY));

        ll = findViewById(R.id.ll_help_text_radio);
        tv = findViewById(R.id.tv_help_text_radio);
        if (pure) {
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
        if (pure) {
            ll.setVisibility(LinearLayout.GONE);
        } else {
            ll.setVisibility(LinearLayout.VISIBLE);
            tv.setText(Html.fromHtml(getString(R.string.help_forum_text), Html.FROM_HTML_MODE_LEGACY));
            tv.setMovementMethod(LinkMovementMethod.getInstance());
        }

    }
}
