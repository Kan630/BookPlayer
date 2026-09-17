package com.driot.bookplayer.radio;

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
import com.driot.bookplayer.global.Intents;
import com.driot.bookplayer.helpers.InsetHelper;
import com.driot.bookplayer.nav.FullActivity;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * Single Activity hosting the whole Radio section as a Jetpack Navigation Component
 * graph (radio_nav_graph.xml) instead of separate Activities per screen. Replaces
 * GetRadioActivity/RadioResultsActivity/RadioFavoritesActivity/GetRadioCardListActivity/
 * RadioStationActivity as real back-stack entries: those still exist as classes (now
 * unused) until the rest of the app is migrated, see [[radio_deeplink_applinks_fix]]
 * plan for the phased rollout.
 */
@AndroidEntryPoint
public class RadioHostActivity extends FullActivity {

    @Override protected int getNavSectionId() { return R.id.nav_radio; }
    @Override protected int getLayoutResId() { return R.layout.activity_radio_host; }
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
                NavController navController = getRadioNavController();
                if (navController != null && navController.popBackStack()) {
                    return; // popped one level within the radio graph
                }
                myLogI("--- user press BACK --- from radio section root -> MainActivity");
                Intent intent = new Intent(RadioHostActivity.this, MainActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
                overridePendingTransition(0, 0);
            }
        });

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
     * Routes an incoming Intent to the right destination inside the radio graph:
     * - a station uuid (deep link, notification tap, or RadioHelper.openRadioStationActivity[FromUuid])
     *   navigates straight to the station detail screen.
     * - EXTRA_START_IN_FAVORITES/HISTORY (notification tap with no specific track, or
     *   RadioHelper.getFavoritesSectionIntent/getHistorySectionIntent callers) navigates to favorites.
     * Uri/uuid parsing itself already happened upstream (ShareHelper/RadioHelper) - this is
     * just "given the result, show it". No-op (stays on the graph's start destination) for a
     * plain launch with none of these extras.
     */
    private void handleIntentNavigation(Intent intent) {
        if (intent == null) return;

        NavController navController = getRadioNavController();
        if (navController == null) return;

        NavOptions singleTop = new NavOptions.Builder().setLaunchSingleTop(true).build();

        String uuid = intent.getStringExtra(Intents.EXTRA_STATION_UUID);
        if (uuid != null && !uuid.isEmpty()) {
            Bundle args = new Bundle();
            args.putString(Intents.EXTRA_STATION_UUID, uuid);
            myLogI("handleIntentNavigation: navigating to radioStationFragment, uuid=" + uuid);
            navController.navigate(R.id.radioStationFragment, args, singleTop);
            return;
        }

        boolean startInHistory = intent.getBooleanExtra(Intents.EXTRA_START_IN_HISTORY, false);
        boolean startInFavorites = intent.getBooleanExtra(Intents.EXTRA_START_IN_FAVORITES, false);
        if (startInHistory || startInFavorites) {
            Bundle args = new Bundle();
            args.putBoolean(Intents.EXTRA_START_IN_HISTORY, startInHistory);
            args.putBoolean(Intents.EXTRA_START_IN_FAVORITES, startInFavorites);
            args.putLong(Intents.EXTRA_OPEN_FROM_TRACK_ID,
                    intent.getLongExtra(Intents.EXTRA_OPEN_FROM_TRACK_ID, -1));
            myLogI("handleIntentNavigation: navigating to radioFavoritesFragment, history=" + startInHistory);
            navController.navigate(R.id.radioFavoritesFragment, args, singleTop);
        }
    }

    @Nullable
    private NavController getRadioNavController() {
        FragmentManager fm = getSupportFragmentManager();
        NavHostFragment navHostFragment = (NavHostFragment) fm.findFragmentById(R.id.radio_nav_host);
        return navHostFragment != null ? navHostFragment.getNavController() : null;
    }

    /**
     * Same-tab "Radio" click while already inside RadioHostActivity: reset to the section
     * root instead of re-delivering a plain Intent (which would no-op and leave whatever
     * sub-screen was showing, since a fresh Intent carries no navigation extras).
     * Called from NavHelper.handleAppNavBarClick's same-tab branch.
     */
    public void navigateToRoot() {
        NavController navController = getRadioNavController();
        if (navController == null) return;
        navController.popBackStack(navController.getGraph().getStartDestinationId(), false);
    }
}
