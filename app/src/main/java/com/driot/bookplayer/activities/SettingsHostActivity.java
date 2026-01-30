package com.driot.bookplayer.activities;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;

import com.driot.bookplayer.R;
import com.driot.bookplayer.helpers.InsetHelper;
import com.driot.bookplayer.utils.log.LoggingActivity;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class SettingsHostActivity extends BaseBottomNavActivity {

    public static final String EXTRA_FRAGMENT_CLASS = "extra_fragment_class";
    public static final String EXTRA_FRAGMENT_ARGS  = "extra_fragment_args";
    public static final String EXTRA_SHOW_LOCAL_TITLE = "extra_show_local_title";
    public static final String EXTRA_ACTIVITY_TITLE_RES = "extra_activity_title_res";
    public static final String EXTRA_ACTIVITY_TITLE_TEXT = "extra_activity_title_text";

    /** Convenience: launch with a fragment class and optional args. */
    public static void start(Context ctx,
                             Class<? extends Fragment> fragmentClass,
                             boolean showLocalTitle,
                             @StringRes int activityTitleRes) {
        Intent i = new Intent(ctx, SettingsHostActivity.class);
        i.putExtra(EXTRA_FRAGMENT_CLASS, fragmentClass.getName());
        i.putExtra(EXTRA_SHOW_LOCAL_TITLE, showLocalTitle);
        i.putExtra(EXTRA_ACTIVITY_TITLE_RES, activityTitleRes);
        ctx.startActivity(i);
    }

    /** Overload with custom title text (if you don’t want to use a string resource). */
    public static void start(Context ctx,
                             Class<? extends Fragment> fragmentClass,
                             boolean showLocalTitle,
                             CharSequence activityTitleText) {
        Intent i = new Intent(ctx, SettingsHostActivity.class);
        i.putExtra(EXTRA_FRAGMENT_CLASS, fragmentClass.getName());
        i.putExtra(EXTRA_SHOW_LOCAL_TITLE, showLocalTitle);
        i.putExtra(EXTRA_ACTIVITY_TITLE_TEXT, activityTitleText);
        ctx.startActivity(i);
    }

    /** Overload including a Bundle of fragment arguments. */
    public static void start(Context ctx,
                             Class<? extends Fragment> fragmentClass,
                             Bundle fragmentArgs,
                             boolean showLocalTitle,
                             @StringRes int activityTitleRes) {
        Intent i = new Intent(ctx, SettingsHostActivity.class);
        i.putExtra(EXTRA_FRAGMENT_CLASS, fragmentClass.getName());
        i.putExtra(EXTRA_FRAGMENT_ARGS, fragmentArgs);
        i.putExtra(EXTRA_SHOW_LOCAL_TITLE, showLocalTitle);
        i.putExtra(EXTRA_ACTIVITY_TITLE_RES, activityTitleRes);
        ctx.startActivity(i);
    }

    @Override protected int getNavId() { return R.id.nav_settings; }
    @Override protected int getLayoutResId() { return R.layout.activity_settings_host; }
    @Override protected boolean enableOngoingTaskOverlay() { return true; }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        InsetHelper.apply(this);

        // Title handling (status bar / toolbar title)
        CharSequence titleText = getIntent().getCharSequenceExtra(EXTRA_ACTIVITY_TITLE_TEXT);
        if (titleText != null) {
            setTitle(titleText);
        } else {
            int titleRes = getIntent().getIntExtra(EXTRA_ACTIVITY_TITLE_RES, 0);
            if (titleRes != 0) setTitle(titleRes);
        }

        if (savedInstanceState != null) return;

        String className = getIntent().getStringExtra(EXTRA_FRAGMENT_CLASS);
        if (className == null || className.isEmpty()) {
            finish(); // nothing to show
            return;
        }

        boolean showLocalTitle = getIntent().getBooleanExtra(EXTRA_SHOW_LOCAL_TITLE, true);
        Bundle args = getIntent().getBundleExtra(EXTRA_FRAGMENT_ARGS);
        if (args == null) args = new Bundle();
        // Standard arg key the fragment can read:
        args.putBoolean("ARG_SHOW_LOCAL_TITLE", showLocalTitle);

        Fragment frag;
        try {
            Class<?> clazz = Class.forName(className);
            frag = (Fragment) clazz.newInstance();
        } catch (Exception e) {
            myLogEE(e, "Failed to instantiate fragment: " + className);
            finish();
            return;
        }
        frag.setArguments(args);

        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, frag, "SettingsHostFragment")
                .commit();
    }
}
