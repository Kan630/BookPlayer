package com.driot.bookplayer.podcasts;

import android.content.Intent;
import android.os.Bundle;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;
import androidx.navigation.NavController;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;

import com.driot.bookplayer.R;
import com.driot.bookplayer.activities.MainActivity;
import com.driot.bookplayer.db.Podcast;
import com.driot.bookplayer.global.Intents;
import com.driot.bookplayer.helpers.InsetHelper;
import com.driot.bookplayer.nav.FullActivity;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * Single Activity hosting the whole Podcast section as a Jetpack Navigation Component
 * graph (podcast_nav_graph.xml) instead of separate Activities per screen. Mirrors
 * RadioHostActivity - see [[radio_deeplink_applinks_fix]] plan for why.
 */
@AndroidEntryPoint
public class PodcastHostActivity extends FullActivity {

    @Override protected int getNavSectionId() { return R.id.nav_podcast; }
    @Override protected int getLayoutResId() { return R.layout.activity_podcast_host; }
    @Override protected boolean enableOngoingTaskOverlay() { return true; }
    @Override protected boolean isSectionRoot() { return true; }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        InsetHelper.apply(this);

        // Registered after super.onCreate() (which is where BaseActivity registers its own
        // isSectionRoot()-based callback) so this one - added later, same LifecycleOwner - wins
        // deterministically on back press. We can't rely on NavHostFragment's own back callback
        // racing correctly against BaseActivity's (Activity vs nested-Fragment LifecycleOwners,
        // observed unreliable in testing) - so this Activity owns the whole decision directly via
        // a plain NavController.popBackStack() call instead.
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                NavController navController = getPodcastNavController();
                if (navController != null && navController.popBackStack()) {
                    return; // popped one level within the podcast graph
                }
                myLogI("--- user press BACK --- from podcast section root -> MainActivity");
                Intent intent = new Intent(PodcastHostActivity.this, MainActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
                overridePendingTransition(0, 0);
            }
        });

        if (savedInstanceState == null && !getIntent().getBooleanExtra("FROM_TAB_SWITCH", false)) {
            // A fresh instance created via tab switch can still receive a stale, previously-stored
            // NavState Intent for this section (e.g. an old episode extra left over from a
            // now-finished direct-link instance) - see SettingsHostActivity for the fuller story.
            // Ignore it and let the Podcast graph start on its normal root instead.
            handleIntentNavigation(getIntent());
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (!intent.getBooleanExtra("FROM_TAB_SWITCH", false)) {
            handleIntentNavigation(intent);
        }
    }

    /**
     * Routes an incoming Intent to the right destination inside the podcast graph:
     * - a "podcast" Parcelable extra (mini-player click, PodcastHelper.openPodcastEpisodeActivityFromActivity,
     *   or picking a result from search/favorites) navigates straight to the episode screen.
     * - EXTRA_START_IN_FAVORITES/HISTORY navigates to favorites.
     * No-op (stays on the graph's start destination) for a plain launch with neither.
     */
    private void handleIntentNavigation(Intent intent) {
        if (intent == null) return;

        NavController navController = getPodcastNavController();
        if (navController == null) return;

        NavOptions singleTop = new NavOptions.Builder().setLaunchSingleTop(true).build();

        Podcast podcast = intent.getParcelableExtra("podcast");
        if (podcast != null) {
            Bundle args = new Bundle();
            args.putParcelable("podcast", podcast);
            myLogI("handleIntentNavigation: navigating to podcastEpisodeFragment, podcast=" + podcast.title);
            navController.navigate(R.id.podcastEpisodeFragment, args, singleTop);
            return;
        }

        boolean startInHistory = intent.getBooleanExtra(Intents.EXTRA_START_IN_HISTORY, false);
        boolean startInFavorites = intent.getBooleanExtra(Intents.EXTRA_START_IN_FAVORITES, false);
        if (startInHistory || startInFavorites) {
            Bundle args = new Bundle();
            args.putBoolean(Intents.EXTRA_START_IN_HISTORY, startInHistory);
            myLogI("handleIntentNavigation: navigating to podcastFavoritesFragment, history=" + startInHistory);
            navController.navigate(R.id.podcastFavoritesFragment, args, singleTop);
        }
    }

    @Nullable
    private NavController getPodcastNavController() {
        FragmentManager fm = getSupportFragmentManager();
        NavHostFragment navHostFragment = (NavHostFragment) fm.findFragmentById(R.id.podcast_nav_host);
        return navHostFragment != null ? navHostFragment.getNavController() : null;
    }

    /**
     * Same-tab "Podcast" click while already inside PodcastHostActivity: reset to the
     * section root instead of re-delivering a plain Intent. Called from
     * NavHelper.handleAppNavBarClick's same-tab branch via PodcastHelper.handlePodcastTabReselected.
     */
    public void navigateToRoot() {
        NavController navController = getPodcastNavController();
        if (navController == null) return;
        navController.popBackStack(navController.getGraph().getStartDestinationId(), false);
    }
}
