package com.driot.bookplayer.activities;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.IdRes;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.navigation.NavController;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;

import com.driot.bookplayer.R;
import com.driot.bookplayer.global.Pref;
import com.driot.bookplayer.helpers.InsetHelper;
import com.driot.bookplayer.nav.FullActivity;
import com.driot.bookplayer.settings.ui.AutomotiveSettingsFragment;
import com.driot.bookplayer.settings.ui.DesignSettingsFragment;
import com.driot.bookplayer.settings.ui.ImportSettingsFragment;
import com.driot.bookplayer.settings.ui.LanguageSettingsFragment;
import com.driot.bookplayer.settings.ui.MassiveImportSettingsFragment;
import com.driot.bookplayer.settings.ui.NetworkSettingsFragment;
import com.driot.bookplayer.settings.ui.PlayBehaviourSettingsFragment;
import com.driot.bookplayer.settings.ui.PodcastSettingsFragment;
import com.driot.bookplayer.settings.ui.RadioSettingsFragment;
import com.driot.bookplayer.settings.ui.RepositoriesSettingsFragment;
import com.driot.bookplayer.settings.ui.StorageSettingsFragment;
import com.driot.bookplayer.settings.ui.TtsSettingsFragment;
import com.driot.bookplayer.settings.ui.UtilitiesSettingsFragment;

import java.util.HashMap;
import java.util.Map;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * Single Activity hosting the whole Settings section as a Jetpack Navigation Component
 * graph (settings_nav_graph.xml) - the category list is the start destination, each
 * category is a leaf destination. Mirrors RadioHostActivity/PodcastHostActivity - see
 * [[radio_deeplink_applinks_fix]] plan for why. Replaces the former SettingsActivity
 * (category list + its own manual FragmentManager back stack) entirely; this class itself
 * used to reflection-instantiate a single arbitrary Fragment class - now it maps the same
 * known Fragment classes to nav graph destinations instead.
 */
@AndroidEntryPoint
public class SettingsHostActivity extends FullActivity {

    public static final String EXTRA_FRAGMENT_CLASS = "extra_fragment_class";
    public static final String EXTRA_FRAGMENT_ARGS  = "extra_fragment_args";
    public static final String EXTRA_SHOW_LOCAL_TITLE = "extra_show_local_title";
    public static final String EXTRA_ACTIVITY_TITLE_RES = "extra_activity_title_res";
    public static final String EXTRA_ACTIVITY_TITLE_TEXT = "extra_activity_title_text";

    private static final Map<String, Integer> DESTINATION_BY_FRAGMENT_CLASS = new HashMap<>();
    static {
        DESTINATION_BY_FRAGMENT_CLASS.put(LanguageSettingsFragment.class.getName(), R.id.languageSettingsFragment);
        DESTINATION_BY_FRAGMENT_CLASS.put(PlayBehaviourSettingsFragment.class.getName(), R.id.playBehaviourSettingsFragment);
        DESTINATION_BY_FRAGMENT_CLASS.put(DesignSettingsFragment.class.getName(), R.id.designSettingsFragment);
        DESTINATION_BY_FRAGMENT_CLASS.put(StorageSettingsFragment.class.getName(), R.id.storageSettingsFragment);
        DESTINATION_BY_FRAGMENT_CLASS.put(ImportSettingsFragment.class.getName(), R.id.importSettingsFragment);
        DESTINATION_BY_FRAGMENT_CLASS.put(RepositoriesSettingsFragment.class.getName(), R.id.repositoriesSettingsFragment);
        DESTINATION_BY_FRAGMENT_CLASS.put(RadioSettingsFragment.class.getName(), R.id.radioSettingsFragment);
        DESTINATION_BY_FRAGMENT_CLASS.put(PodcastSettingsFragment.class.getName(), R.id.podcastSettingsFragment);
        DESTINATION_BY_FRAGMENT_CLASS.put(TtsSettingsFragment.class.getName(), R.id.ttsSettingsFragment);
        DESTINATION_BY_FRAGMENT_CLASS.put(AutomotiveSettingsFragment.class.getName(), R.id.automotiveSettingsFragment);
        DESTINATION_BY_FRAGMENT_CLASS.put(NetworkSettingsFragment.class.getName(), R.id.networkSettingsFragment);
        DESTINATION_BY_FRAGMENT_CLASS.put(UtilitiesSettingsFragment.class.getName(), R.id.utilitiesSettingsFragment);
        DESTINATION_BY_FRAGMENT_CLASS.put(MassiveImportSettingsFragment.class.getName(), R.id.massiveImportSettingsFragment);
    }

    /**
     * True when this instance was launched via start(...) (a direct link to one category from
     * elsewhere in the app, e.g. the gear icon inside Radio/Podcast) rather than via the bottom
     * nav Settings tab. In that mode the category list is popped out of the graph entirely (see
     * handleIntentNavigation), so back should simply finish() and reveal whatever real Activity
     * launched this one (Radio, Podcast, ...) instead of forcing MainActivity - matching how the
     * old single-fragment SettingsHostActivity behaved (no list screen existed to fall back to).
     */
    private boolean directLinkMode = false;

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

    @Override protected int getNavSectionId() { return R.id.nav_settings; }
    @Override protected int getLayoutResId() { return R.layout.activity_settings_host; }
    @Override protected boolean enableOngoingTaskOverlay() { return true; }
    @Override protected boolean isSectionRoot() { return true; }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        InsetHelper.apply(this);

        // Registered after super.onCreate() (where BaseActivity registers its own
        // isSectionRoot()-based callback) so this one - added later, same LifecycleOwner - wins
        // deterministically on back press. See RadioHostActivity/PodcastHostActivity for the
        // full story on why NavHostFragment's own back callback can't be trusted here.
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                NavController navController = getSettingsNavController();
                if (navController != null && navController.popBackStack()) {
                    return; // popped one level within the settings graph
                }
                if (directLinkMode) {
                    myLogI("--- user press BACK --- from direct-linked settings screen -> finish (reveal caller)");
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                    return;
                }
                myLogI("--- user press BACK --- from settings section root -> MainActivity");
                Intent intent = new Intent(SettingsHostActivity.this, MainActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
                overridePendingTransition(0, 0);
            }
        });

        // Title handling (status bar / toolbar title)
        CharSequence titleText = getIntent().getCharSequenceExtra(EXTRA_ACTIVITY_TITLE_TEXT);
        if (titleText != null) {
            setTitle(titleText);
        } else {
            int titleRes = getIntent().getIntExtra(EXTRA_ACTIVITY_TITLE_RES, 0);
            if (titleRes != 0) setTitle(titleRes);
        }

        if (savedInstanceState == null) {
            handleIntentNavigation(getIntent());
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntentNavigation(intent);
    }

    /**
     * Navigates straight to the requested category if the Intent names one (external
     * deep-link-style callers via start(...) above), otherwise (plain tab-root launch, or
     * same-tab reselect via NavHelper) leaves/resets to the category list.
     */
    private void handleIntentNavigation(Intent intent) {
        if (intent == null) return;

        NavController navController = getSettingsNavController();
        if (navController == null) return;

        String className = intent.getStringExtra(EXTRA_FRAGMENT_CLASS);
        if (className == null || className.isEmpty()) {
            return;
        }

        Integer destinationId = DESTINATION_BY_FRAGMENT_CLASS.get(className);
        if (destinationId == null) {
            myLogEE(null, "handleIntentNavigation: unknown settings fragment class: " + className);
            return;
        }

        Bundle args = intent.getBundleExtra(EXTRA_FRAGMENT_ARGS);
        if (args == null) args = new Bundle();
        // Standard arg key the fragment can read; every caller of start(...) passes true.
        args.putBoolean(EXTRA_SHOW_LOCAL_TITLE, intent.getBooleanExtra(EXTRA_SHOW_LOCAL_TITLE, true));

        navController.navigate(destinationId, args);
    }

    @Nullable
    private NavController getSettingsNavController() {
        FragmentManager fm = getSupportFragmentManager();
        NavHostFragment navHostFragment = (NavHostFragment) fm.findFragmentById(R.id.settings_nav_host);
        return navHostFragment != null ? navHostFragment.getNavController() : null;
    }

    /**
     * Same-tab "Settings" click while already inside SettingsHostActivity: reset to the
     * section root instead of re-delivering a plain Intent. Called from
     * NavHelper.handleAppNavBarClick's same-tab branch.
     */
    public void navigateToRoot() {
        NavController navController = getSettingsNavController();
        if (navController == null) return;
        navController.popBackStack(navController.getGraph().getStartDestinationId(), false);
    }

    @Override
    public void finish() { // needed because of recreate() from MainActivity's overflow-menu Settings item
        if (Pref.getNeedsRecreate()) {
            setResult(Activity.RESULT_OK);
        }
        super.finish();
    }
}
