package com.driot.bookplayer.activities;

import android.content.Intent;
import android.os.Bundle;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.navigation.NavController;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;

import com.driot.bookplayer.R;
import com.driot.bookplayer.db.Folder;
import com.driot.bookplayer.global.Intents;
import com.driot.bookplayer.helpers.InsetHelper;
import com.driot.bookplayer.nav.FullActivity;
import com.driot.bookplayer.utils.Tonio;

import java.util.List;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * Single Activity hosting the whole Add Book section as a Jetpack Navigation Component graph
 * (add_book_nav_graph.xml) - mirrors RadioHostActivity/PodcastHostActivity/SettingsHostActivity,
 * see [[radio_deeplink_applinks_fix]] plan for why. Replaced the old GetActivity/GetOtherActivity/
 * GetLibrivoxActivity/GetDirectLinkActivity/etc. Activities (deleted once this migration was
 * confirmed stable on-device) as real back-stack entries.
 *
 * ImportBookSingleActivity/ImportBookMultipleActivity are deliberately NOT folded into this
 * graph - they're reachable from far outside the Add Book tab (system "Open With" file
 * associations, the ongoing-task notification/overlay, mass-import per-candidate sub-import) and
 * already have their own working exit-navigation logic, so they stay standalone real Activities.
 */
@AndroidEntryPoint
public class AddBookHostActivity extends FullActivity {

    /**
     * True when this instance was launched via ModifyFolderActivity's "add new tracks to this
     * folder" button (Intents.EXTRA_ADD_TO_FOLDER) rather than via the bottom nav Add Book tab.
     * In that mode the hub/network-source screens are popped out of the graph entirely (see
     * handleIntentNavigation) and back should simply finish() and reveal ModifyFolderActivity,
     * matching how plain GetOtherActivity used to behave when launched this way (no hub screen
     * existed underneath it either).
     */
    private boolean directLinkMode = false;

    @Override protected int getNavSectionId() { return R.id.nav_add; }
    @Override protected int getLayoutResId() { return R.layout.activity_add_book_host; }
    @Override protected boolean enableOngoingTaskOverlay() { return true; }
    @Override protected boolean isSectionRoot() { return true; }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        InsetHelper.apply(this);

        // Registered after super.onCreate() (where BaseActivity registers its own
        // isSectionRoot()-based callback) so this one - added later, same LifecycleOwner - wins
        // deterministically on back press. See RadioHostActivity/PodcastHostActivity/
        // SettingsHostActivity for the full story on why NavHostFragment's own back callback
        // can't be trusted here.
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                NavController navController = getAddBookNavController();
                if (navController != null && navController.popBackStack()) {
                    return; // popped one level within the add-book graph
                }
                if (directLinkMode) {
                    myLogI("--- user press BACK --- from direct-linked add-book screen -> finish (reveal caller)");
                    finish();
                    return;
                }
                myLogI("--- user press BACK --- from add-book section root -> MainActivity");
                Intent intent = new Intent(AddBookHostActivity.this, MainActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
                overridePendingTransition(0, 0);
            }
        });

        if (savedInstanceState == null && !getIntent().getBooleanExtra("FROM_TAB_SWITCH", false)) {
            // See SettingsHostActivity for why this guard is needed in onCreate too, not just
            // onNewIntent: a fresh instance created via tab switch can still receive a stale,
            // previously-stored NavState Intent for this section.
            handleIntentNavigation(getIntent());
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent.getBooleanExtra("FROM_TAB_SWITCH", false)) {
            if (directLinkMode) {
                // This instance was last left showing a one-off direct-link screen (e.g.
                // ModifyFolderActivity's "add new tracks"), not resident Add-Book-tab state.
                // Tapping the Add Book TAB itself should always land on the tab's own root.
                directLinkMode = false;
                navigateToRoot();
            }
            return;
        }
        handleIntentNavigation(intent);
    }

    /**
     * Navigates straight to the append-mode screen if the Intent carries a target folder
     * (ModifyFolderActivity's "add new tracks" direct link), otherwise (plain tab-root launch,
     * or same-tab reselect via NavHelper) leaves/resets to the graph's start destination - except
     * on "pure", which has no reachable path to LibriVox/Gutenberg/direct-link content (can't be
     * kept kid-safe/content-rating-appropriate) and always skips straight to GetOtherFragment,
     * matching NavHelper.buildSectionRootIntent's old flavor branch.
     */
    private void handleIntentNavigation(Intent intent) {
        if (intent == null) return;

        NavController navController = getAddBookNavController();
        if (navController == null) return;

        Folder folderToAddTo = intent.getParcelableExtra(Intents.EXTRA_ADD_TO_FOLDER);
        if (folderToAddTo != null) {
            directLinkMode = true;
            Bundle args = new Bundle();
            args.putParcelable(Intents.EXTRA_ADD_TO_FOLDER, folderToAddTo);
            NavOptions options = new NavOptions.Builder()
                    .setPopUpTo(navController.getGraph().getStartDestinationId(), true)
                    .build();
            navController.navigate(R.id.getOtherFragment, args, options);
            return;
        }

        if (Tonio.isPure(this) && navController.getCurrentDestination() != null
                && navController.getCurrentDestination().getId() == R.id.getActivityFragment) {
            NavOptions options = new NavOptions.Builder()
                    .setPopUpTo(navController.getGraph().getStartDestinationId(), true)
                    .build();
            navController.navigate(R.id.getOtherFragment, null, options);
        }
    }

    @Nullable
    private NavController getAddBookNavController() {
        FragmentManager fm = getSupportFragmentManager();
        NavHostFragment navHostFragment = (NavHostFragment) fm.findFragmentById(R.id.add_book_nav_host);
        return navHostFragment != null ? navHostFragment.getNavController() : null;
    }

    /**
     * Same-tab "Add Book" click while already inside AddBookHostActivity: reset to the section
     * root instead of re-delivering a plain Intent. Called from NavHelper.handleAppNavBarClick's
     * same-tab branch.
     */
    public void navigateToRoot() {
        NavController navController = getAddBookNavController();
        if (navController == null) return;
        navController.popBackStack(navController.getGraph().getStartDestinationId(), false);
        if (Tonio.isPure(this)) {
            NavOptions options = new NavOptions.Builder()
                    .setPopUpTo(navController.getGraph().getStartDestinationId(), true)
                    .build();
            navController.navigate(R.id.getOtherFragment, null, options);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
            @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // PermissionRequest.with(requireActivity()) (used by GetOtherFragment) requests
        // permissions against this Activity directly (not via Fragment.requestPermissions(),
        // which would self-route), so the system callback lands here and must be forwarded
        // manually to whichever Fragment is currently displayed by the NavHostFragment.
        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.add_book_nav_host);
        if (navHostFragment == null) return;
        List<Fragment> children = navHostFragment.getChildFragmentManager().getFragments();
        if (!children.isEmpty()) {
            children.get(children.size() - 1).onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }
}
