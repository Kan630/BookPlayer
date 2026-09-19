package com.driot.bookplayer.activities;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.ViewModelProvider;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.navigation.NavController;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;

import com.driot.bookplayer.MyApp;
import com.driot.bookplayer.global.Intents;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.R;
import com.driot.bookplayer.global.Pref;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.helpers.InsetHelper;
import com.driot.bookplayer.helpers.ShareHelper;
import com.driot.bookplayer.nav.FullActivity;
import com.driot.bookplayer.player.MediaService;
import com.driot.bookplayer.helpers.InfoHelper;
import com.driot.bookplayer.player.PlaybackUiState;
import com.driot.bookplayer.player.PlaybackViewModel;
import com.driot.bookplayer.player.StartPlayHelper;
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
import com.driot.bookplayer.utils.InAppMsgManager;
import com.driot.bookplayer.utils.KanMail;

import com.driot.bookplayer.utils.Tonio;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * The app's ONE and ONLY Activity: launcher, App Links deep-link target, voice-search entry
 * point, and host of all 5 bottom-nav sections (Library/Add Book/Radio/Podcast/Settings), each
 * as its own NavHostFragment attached/detached from {@link R.id#nav_host_container} - see
 * selectTab()/navigateDirectLink() below. This replaced RadioHostActivity/PodcastHostActivity/
 * SettingsHostActivity/AddBookHostActivity (each a SEPARATE Activity sharing this one's task),
 * because Android was recreating whichever one wasn't already on top of the shared task on every
 * tab switch (confirmed via adb logcat: ActivityStarter.recycleTask/performClearTaskForReuse),
 * destroying that section's internal navigation state every time. With everything as Fragments
 * inside this single Activity, switching tabs is a Fragment attach/detach - the Fragment
 * instances (and their NavControllers' back stacks) are never destroyed, so each tab's state
 * genuinely persists across tab switches, matching standard bottom-nav app behavior (Instagram,
 * YouTube, etc.) - see [[radio_deeplink_applinks_fix]] plan for the full history.
 *
 * MainViewModel is Activity-scoped (new ViewModelProvider(this)) so the Sort menu item here and
 * MainLibraryFragment's own observers share the same instance.
 */
@AndroidEntryPoint
public class MainActivity extends FullActivity {

    private MainViewModel mainVm;

    Toolbar toolbar;

    public static final String EXTRA_REQUESTED_NAV_ID = "EXTRA_REQUESTED_NAV_ID";

    // Generic cross-tab navigation extras - used by every entry point that needs to select a
    // tab (and optionally navigate within it) from outside this Activity: system "Open With",
    // notification PendingIntents, other standalone Activities (PlayActivity, ModifyFolderActivity,
    // ImportBook*Activity, ...), and this Activity's own Fragments calling back in via
    // startActivity() (cheap and reliable since this Activity is always singleTop and already
    // running once the app has launched once).
    public static final String EXTRA_NAV_TAB_ID = "EXTRA_NAV_TAB_ID";
    public static final String EXTRA_NAV_DEST_ID = "EXTRA_NAV_DEST_ID";
    public static final String EXTRA_NAV_ARGS = "EXTRA_NAV_ARGS";
    public static final String EXTRA_NAV_DIRECT_LINK = "EXTRA_NAV_DIRECT_LINK";
    public static final String EXTRA_NAV_TITLE_RES = "EXTRA_NAV_TITLE_RES";
    public static final String EXTRA_NAV_TITLE_TEXT = "EXTRA_NAV_TITLE_TEXT";

    /** Fragment-argument key some Settings screens read to decide whether to show their own
     * local title bar - preserved from the old SettingsHostActivity for compatibility. */
    public static final String EXTRA_SHOW_LOCAL_TITLE = "extra_show_local_title";

    private static final Map<String, Integer> SETTINGS_DESTINATION_BY_FRAGMENT_CLASS = new HashMap<>();
    static {
        SETTINGS_DESTINATION_BY_FRAGMENT_CLASS.put(LanguageSettingsFragment.class.getName(), R.id.languageSettingsFragment);
        SETTINGS_DESTINATION_BY_FRAGMENT_CLASS.put(PlayBehaviourSettingsFragment.class.getName(), R.id.playBehaviourSettingsFragment);
        SETTINGS_DESTINATION_BY_FRAGMENT_CLASS.put(DesignSettingsFragment.class.getName(), R.id.designSettingsFragment);
        SETTINGS_DESTINATION_BY_FRAGMENT_CLASS.put(StorageSettingsFragment.class.getName(), R.id.storageSettingsFragment);
        SETTINGS_DESTINATION_BY_FRAGMENT_CLASS.put(ImportSettingsFragment.class.getName(), R.id.importSettingsFragment);
        SETTINGS_DESTINATION_BY_FRAGMENT_CLASS.put(RepositoriesSettingsFragment.class.getName(), R.id.repositoriesSettingsFragment);
        SETTINGS_DESTINATION_BY_FRAGMENT_CLASS.put(RadioSettingsFragment.class.getName(), R.id.radioSettingsFragment);
        SETTINGS_DESTINATION_BY_FRAGMENT_CLASS.put(PodcastSettingsFragment.class.getName(), R.id.podcastSettingsFragment);
        SETTINGS_DESTINATION_BY_FRAGMENT_CLASS.put(TtsSettingsFragment.class.getName(), R.id.ttsSettingsFragment);
        SETTINGS_DESTINATION_BY_FRAGMENT_CLASS.put(AutomotiveSettingsFragment.class.getName(), R.id.automotiveSettingsFragment);
        SETTINGS_DESTINATION_BY_FRAGMENT_CLASS.put(NetworkSettingsFragment.class.getName(), R.id.networkSettingsFragment);
        SETTINGS_DESTINATION_BY_FRAGMENT_CLASS.put(UtilitiesSettingsFragment.class.getName(), R.id.utilitiesSettingsFragment);
        SETTINGS_DESTINATION_BY_FRAGMENT_CLASS.put(MassiveImportSettingsFragment.class.getName(), R.id.massiveImportSettingsFragment);
    }

    private static final Map<Integer, String> TAB_TAG = new HashMap<>();
    private static final Map<Integer, Integer> TAB_GRAPH = new HashMap<>();
    static {
        TAB_TAG.put(R.id.nav_library, "tab_library");
        TAB_TAG.put(R.id.nav_add, "tab_add");
        TAB_TAG.put(R.id.nav_radio, "tab_radio");
        TAB_TAG.put(R.id.nav_podcast, "tab_podcast");
        TAB_TAG.put(R.id.nav_settings, "tab_settings");
        TAB_GRAPH.put(R.id.nav_library, R.navigation.main_library_nav_graph);
        TAB_GRAPH.put(R.id.nav_add, R.navigation.add_book_nav_graph);
        TAB_GRAPH.put(R.id.nav_radio, R.navigation.radio_nav_graph);
        TAB_GRAPH.put(R.id.nav_podcast, R.navigation.podcast_nav_graph);
        TAB_GRAPH.put(R.id.nav_settings, R.navigation.settings_nav_graph);
    }

    private int currentNavSectionId = R.id.nav_library;
    /** Tabs to return to (revealing exactly where they were left) when backing out of a
     * direct-linked screen with nothing left to pop - e.g. Radio's gear icon into "Radio
     * Settings" pushes nav_radio here; backing out of Settings with nothing else to pop switches
     * back to Radio instead of falling through to Library. Cleared on any ordinary (non-direct-
     * link) tab selection, since that supersedes any pending "return to caller" expectation. */
    private final Deque<Integer> directLinkReturnStack = new ArrayDeque<>();

    private boolean HasBeenProposedToOpenFile;
    private static boolean infoAlreadyShown = false;

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean("HasBeenProposedToOpenFile", HasBeenProposedToOpenFile);
        // FragmentManager restores each tab's attach/detach state on its own (that's built into
        // its normal saved-instance-state handling), but currentNavSectionId is a plain field -
        // without persisting it, getNavSectionId() would report the wrong tab (reset to the
        // field's default) after a config change/process recreation, even though the actually
        // re-attached Fragment is whatever tab the user was really on.
        outState.putInt("currentNavSectionId", currentNavSectionId);
        int[] returnStack = new int[directLinkReturnStack.size()];
        int i = 0;
        for (int id : directLinkReturnStack) returnStack[i++] = id;
        outState.putIntArray("directLinkReturnStack", returnStack);
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        HasBeenProposedToOpenFile = savedInstanceState.getBoolean("HasBeenProposedToOpenFile", false);
        currentNavSectionId = savedInstanceState.getInt("currentNavSectionId", R.id.nav_library);
        int[] returnStack = savedInstanceState.getIntArray("directLinkReturnStack");
        directLinkReturnStack.clear();
        if (returnStack != null) {
            // Array was filled bottom-to-top from the Deque's iteration order (most-recent
            // first), so push in reverse to restore the same order.
            for (int i = returnStack.length - 1; i >= 0; i--) directLinkReturnStack.push(returnStack[i]);
        }
        updateChromeVisibilityForCurrentTab();
    }

    private final BroadcastReceiver inAppMsgRx = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            myLogD("broadcast received : inAppMsgRx");
            InAppMsgManager.maybeShowBestMessage(MainActivity.this, getString(R.string.app_name));
        }
    };

    @Override
    protected int getNavSectionId() { return Tonio.isPure(this) ? -1 : currentNavSectionId; }

    @Override
    protected int getLayoutResId() {
        return R.layout.activity_main;
    }

    @Override
    protected boolean enableOngoingTaskOverlay() {
        return true;
    }

    @Override protected boolean isSectionRoot() { return true; }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        InsetHelper.apply(this);

        if (savedInstanceState == null && !infoAlreadyShown) {
            InfoHelper.printSomeStuffAboutDevice(this);
            infoAlreadyShown = true;
        }

        // toolbar - only ever shown for the Library tab (Radio/Podcast/Settings/Add Book each
        // draw their own inline header per screen, same as before this merge - they never had a
        // shared system toolbar either).
        toolbar = findViewById(R.id.toolbar);
        try {
            setSupportActionBar(toolbar);
        } catch (Exception e) {
            myLogEE(e, "Action bar - setSupportActionBar - error"); // on a Samsung S20 FE, android 13
        }
        try {
            toolbar.setLogo(R.mipmap.ic_launcher);
        } catch (Exception e) {
            myLogEE(e, "Action bar - setLogo - error");
        }

        mainVm = new ViewModelProvider(this).get(MainViewModel.class);

        // Registered after super.onCreate() (where BaseActivity registers its own
        // isSectionRoot()-based callback) so this one - added later, same LifecycleOwner - wins
        // deterministically on back press.
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                NavController navController = getCurrentTabNavController();
                if (navController != null && navController.popBackStack()) {
                    return; // popped one level within the current tab's graph
                }
                if (!directLinkReturnStack.isEmpty()) {
                    int returnTab = directLinkReturnStack.pop();
                    myLogI("--- user press BACK --- from direct-linked screen -> return to tab " + returnTab);
                    selectTab(returnTab, true);
                    return;
                }
                if (currentNavSectionId != R.id.nav_library) {
                    myLogI("--- user press BACK --- from tab " + currentNavSectionId + " -> Library");
                    selectTab(R.id.nav_library, false);
                    return;
                }
                myLogI("--- USER CLICK BACK from MAIN --- (system button) -- EXIT APP --");
                if (Option.getStopAudioIfUserClosesApp()) {
                    startService(
                            new Intent(MainActivity.this, MediaService.class)
                                    .setAction(Intents.EXTRA_CMD_STOP)
                                    .putExtra(Intents.EXTRA_CALLER, "press back from MainActivity"));
                }
                finishAffinity();
            }
        });

        if (savedInstanceState == null) {
            // First-ever creation: attach the Library tab (start destination) directly.
            attachTab(R.id.nav_library, /*isFirstCreate*/true);
        }

        // Hide the bottom nav bar / mini-player while TtsReaderFragment (fullscreen reading
        // mode) is on top within the Library tab, matching the old standalone TtsReaderActivity's
        // displayAppNavBar()==false. The Library tab's NavHostFragment always exists by this point
        // (attached above on first create, or restored by FragmentManager on config change), and -
        // being attach/detach'd rather than destroyed - never needs this listener re-registered.
        NavHostFragment libraryHost = getTabHost(R.id.nav_library);
        if (libraryHost != null) {
            libraryHost.getNavController().addOnDestinationChangedListener(
                    (controller, destination, arguments) -> {
                        invalidateOptionsMenu();
                        updateChromeVisibilityForCurrentTab();
                    });
        }

        setupTabClickListener();

        if (savedInstanceState == null) {
            ShareHelper.handleDeepLink(this, getIntent());
            handleMediaSearchIntentIfAny(getIntent());
            handleIntentNavigation(getIntent());
        }

        // InAppMsgManager.deleteInAppMsgCache(this);
        MyApp.getPeriodicTaskManager(this).start(); // safe
        InAppMsgManager.maybeShowBestMessage(this, "message");
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleRequestedNavFromIntent(intent); // just routing/navigation, cheap
        if (intent.getBooleanExtra("forceRefresh", false) && mainVm != null) {
            myLog("forceRefresh");
            mainVm.forceRefresh();
        }
        if (intent.getBooleanExtra("scrollToTop", false) && mainVm != null) {
            myLog("scrollToTop");
            mainVm.requestScrollToTopNow();
        }
        ShareHelper.handleDeepLink(this, getIntent());
        handleIntentNavigation(intent);
        handleMediaSearchIntentIfAny(intent);
    }

    /**
     * Routes an incoming Intent to the right tab/destination: EXTRA_NAV_TAB_ID selects the tab
     * (and, combined with EXTRA_NAV_DEST_ID/EXTRA_NAV_ARGS, navigates within it); a bare
     * EXTRA_FOLDER_ID/EXTRA_FOLDER (from the many callers that used to target the old
     * ZikFileActivity) is shorthand for "go to the Library tab's ZikFileFragment". No-op
     * otherwise (plain tab-root launch or same-tab reselect already handled by selectTab()).
     */
    private void handleIntentNavigation(Intent intent) {
        if (intent == null) return;

        if (intent.hasExtra(EXTRA_NAV_TAB_ID)) {
            int tabId = intent.getIntExtra(EXTRA_NAV_TAB_ID, R.id.nav_library);
            int destId = intent.getIntExtra(EXTRA_NAV_DEST_ID, 0);
            Bundle args = intent.getBundleExtra(EXTRA_NAV_ARGS);
            boolean directLink = intent.getBooleanExtra(EXTRA_NAV_DIRECT_LINK, false);

            CharSequence titleText = intent.getCharSequenceExtra(EXTRA_NAV_TITLE_TEXT);
            if (titleText != null) {
                setTitle(titleText);
            } else {
                int titleRes = intent.getIntExtra(EXTRA_NAV_TITLE_RES, 0);
                if (titleRes != 0) setTitle(titleRes);
            }

            if (directLink) {
                navigateDirectLink(tabId, destId, args);
            } else {
                selectTab(tabId, false);
                if (destId != 0) {
                    NavController nc = getCurrentTabNavController();
                    if (nc != null) nc.navigate(destId, args);
                }
            }
            return;
        }

        if (intent.hasExtra(Intents.EXTRA_FOLDER_ID) || intent.hasExtra(Intents.EXTRA_FOLDER)) {
            Bundle args = new Bundle();
            args.putLong(Intents.EXTRA_FOLDER_ID, intent.getLongExtra(Intents.EXTRA_FOLDER_ID, -1));
            args.putParcelable(Intents.EXTRA_FOLDER, intent.getParcelableExtra(Intents.EXTRA_FOLDER));
            args.putBoolean(Intents.EXTRA_ACTIVATE_CHANGE_TRACK_ORDER,
                    intent.getBooleanExtra(Intents.EXTRA_ACTIVATE_CHANGE_TRACK_ORDER, false));
            selectTab(R.id.nav_library, false);
            NavController nc = getCurrentTabNavController();
            if (nc != null) {
                NavOptions options = new NavOptions.Builder()
                        .setPopUpTo(nc.getGraph().getStartDestinationId(), false)
                        .setEnterAnim(R.anim.slide_enter_from_right)
                        .setExitAnim(R.anim.slide_exit_to_left)
                        .setPopEnterAnim(R.anim.slide_pop_enter_from_left)
                        .setPopExitAnim(R.anim.slide_pop_exit_to_right)
                        .build();
                nc.navigate(R.id.zikFileFragment, args, options);
            }
        }
    }

    // ============================================================================
    // Tab hosting: one NavHostFragment per bottom-nav tab, attach/detach to switch.
    // This is what makes each tab's Fragment/NavController state genuinely survive
    // tab switches - the Fragment instances are never destroyed, only their views.
    // ============================================================================

    @Nullable
    private NavHostFragment getTabHost(int tabId) {
        return (NavHostFragment) getSupportFragmentManager().findFragmentByTag(TAB_TAG.get(tabId));
    }

    @Nullable
    private NavController getCurrentTabNavController() {
        NavHostFragment host = getTabHost(currentNavSectionId);
        return host != null ? host.getNavController() : null;
    }

    /** Creates (first time only) and attaches the given tab as the sole visible one, detaching
     * whatever was attached before. Does not touch directLinkReturnStack itself - callers decide. */
    private void attachTab(int tabId, boolean isFirstCreate) {
        FragmentManager fm = getSupportFragmentManager();
        FragmentTransaction ft = fm.beginTransaction();

        if (!isFirstCreate) {
            NavHostFragment currentHost = getTabHost(currentNavSectionId);
            if (currentHost != null) {
                ft.detach(currentHost);
            }
        }

        NavHostFragment targetHost = getTabHost(tabId);
        boolean freshlyCreated = targetHost == null;
        if (freshlyCreated) {
            targetHost = NavHostFragment.create(TAB_GRAPH.get(tabId));
            ft.add(R.id.nav_host_container, targetHost, TAB_TAG.get(tabId));
        } else {
            ft.attach(targetHost);
        }
        ft.setPrimaryNavigationFragment(targetHost);
        ft.setReorderingAllowed(true);
        ft.commitNow();

        currentNavSectionId = tabId;
        selectAppNavItemFromCode(tabId);
        updateChromeVisibilityForCurrentTab();
        invalidateOptionsMenu();

        if (freshlyCreated && tabId == R.id.nav_add && Tonio.isPure(this)) {
            // pure has no reachable path to LibriVox/Gutenberg/direct-link (content that can't
            // be kept kid-safe/content-rating-appropriate) - skip the hub straight to the
            // local-file-only GetOtherFragment, matching the old NavHelper flavor branch.
            NavController nc = targetHost.getNavController();
            NavOptions options = new NavOptions.Builder()
                    .setPopUpTo(nc.getGraph().getStartDestinationId(), true)
                    .build();
            nc.navigate(R.id.getOtherFragment, null, options);
        } else if (freshlyCreated && tabId == R.id.nav_radio) {
            // Radio has a 3-way landing preference (Search/Favorites/History,
            // Option.getRadioLandingScreen()) - matches old RadioHostActivity.handleIntentNavigation's
            // EXTRA_START_IN_FAVORITES/HISTORY branch. No popUpTo: the search root stays beneath in
            // the back stack, same as before this merge.
            int landing = Option.getRadioLandingScreen();
            if (landing == Option.RADIO_LANDING_FAVORITES || landing == Option.RADIO_LANDING_HISTORY) {
                Bundle args = new Bundle();
                args.putBoolean(Intents.EXTRA_START_IN_HISTORY, landing == Option.RADIO_LANDING_HISTORY);
                args.putBoolean(Intents.EXTRA_START_IN_FAVORITES, landing == Option.RADIO_LANDING_FAVORITES);
                targetHost.getNavController().navigate(R.id.radioFavoritesFragment, args);
            }
        } else if (freshlyCreated && tabId == R.id.nav_podcast) {
            // Same as Radio above, for Option.getPodcastLandingScreen() - matches old
            // PodcastHostActivity.handleIntentNavigation's EXTRA_START_IN_HISTORY branch.
            int landing = Option.getPodcastLandingScreen();
            if (landing == Option.PODCAST_LANDING_FAVORITES || landing == Option.PODCAST_LANDING_HISTORY) {
                Bundle args = new Bundle();
                args.putBoolean(Intents.EXTRA_START_IN_HISTORY, landing == Option.PODCAST_LANDING_HISTORY);
                targetHost.getNavController().navigate(R.id.podcastFavoritesFragment, args);
            }
        }
    }

    /** Ordinary tab selection (bottom nav bar tap, or an external Intent naming a tab with no
     * direct-link destination). Same-tab reselect resets that tab's graph to its own start,
     * matching every Host Activity's old navigateToRoot() behavior. */
    public void selectTab(int tabId, boolean isDirectLinkReturn) {
        if (!isDirectLinkReturn) {
            directLinkReturnStack.clear();
        }
        if (tabId == currentNavSectionId) {
            NavHostFragment host = getTabHost(tabId);
            if (host != null) {
                NavController nc = host.getNavController();
                nc.popBackStack(nc.getGraph().getStartDestinationId(), false);
            }
            return;
        }
        attachTab(tabId, false);
    }

    /** Cross-section "direct link" (e.g. a gear icon jumping straight into a specific Settings
     * screen from Radio): remembers the origin tab so back-press reveals it again (exactly where
     * it was left) instead of falling through to Library. */
    private void navigateDirectLink(int tabId, int destId, @Nullable Bundle args) {
        if (tabId != currentNavSectionId) {
            directLinkReturnStack.push(currentNavSectionId);
        }
        selectTab(tabId, true);
        if (destId != 0) {
            NavHostFragment host = getTabHost(tabId);
            if (host != null) {
                NavController nc = host.getNavController();
                NavOptions options = new NavOptions.Builder()
                        .setPopUpTo(nc.getGraph().getStartDestinationId(), true)
                        .build();
                nc.navigate(destId, args, options);
            }
        }
    }

    /** Used by MiniPlayBookFragment's close button to replicate the old
     * "getActivity() instanceof TtsReaderActivity" check without an Activity of that name. */
    public boolean isShowingTtsReaderFragment() {
        if (currentNavSectionId != R.id.nav_library) return false;
        NavController nc = getCurrentTabNavController();
        return nc != null && nc.getCurrentDestination() != null
                && nc.getCurrentDestination().getId() == R.id.ttsReaderFragment;
    }

    /** Used by MiniPlayBookFragment's close button in place of the old
     * getActivity().finish() (which used to close the standalone TtsReaderActivity). */
    public void exitTtsReaderFragment() {
        NavHostFragment host = getTabHost(R.id.nav_library);
        if (host != null) host.getNavController().popBackStack();
    }

    /** True exactly when the Library tab is both the active tab and showing its own root screen
     * (mainLibraryFragment, the book list) - the only place the shared toolbar/menu make sense.
     * Every other Library-tab destination (ZikFileFragment/TtsReaderFragment/CleanMemoryFragment/
     * NearbyShareFragment) draws its own inline header instead, same as they did as standalone
     * Activities before this merge - and every other tab draws its own header too. */
    private boolean isOnLibraryRoot() {
        if (currentNavSectionId != R.id.nav_library) return false;
        NavController nc = getCurrentTabNavController();
        return nc == null || nc.getCurrentDestination() == null
                || nc.getCurrentDestination().getId() == R.id.mainLibraryFragment;
    }

    /** Hides the bottom nav bar / mini-player while TtsReaderFragment (fullscreen reading mode)
     * is on top of the CURRENTLY VISIBLE tab, and the shared toolbar everywhere except the Library
     * tab's own root screen (see isOnLibraryRoot()) - called after every tab switch (attachTab())
     * and every in-Library navigation event (see the Library NavController listener in onCreate()
     * and onRestoreInstanceState()), since any of these can change either of those. */
    private void updateChromeVisibilityForCurrentTab() {
        boolean isTtsReader = isShowingTtsReaderFragment();
        if (appNavBarView != null) {
            appNavBarView.setVisibility(isTtsReader || !displayAppNavBar() ? View.GONE : View.VISIBLE);
        }
        if (isTtsReader) {
            View miniNowPlaying = findViewById(R.id.miniNowPlaying);
            if (miniNowPlaying != null) miniNowPlaying.setVisibility(View.GONE);
        }
        if (toolbar != null) {
            toolbar.setVisibility(isOnLibraryRoot() ? View.VISIBLE : View.GONE);
        }
    }

    /** Replaces the OnItemSelectedListener FullActivity.onCreate() already registered (via its
     * own private setupAppNavBar()) with one that calls selectTab() directly instead of routing
     * through NavHelper/separate Activities - there's only this one Activity now. Must run AFTER
     * super.onCreate() so it's the listener that's actually attached. isNavSelectionFromCode()
     * guards against re-triggering selectTab() when selectAppNavItemFromCode() (called from
     * attachTab() and from FullActivity.onResume()) programmatically updates the selection. */
    private void setupTabClickListener() {
        if (appNavBarView == null) return;
        appNavBarView.setOnItemSelectedListener(item -> {
            if (isNavSelectionFromCode()) return true;
            int itemId = item.getItemId();
            myLogI("--- user click bottom Nav bar ---    item = " + itemId + " - " + item.getTitle());
            selectTab(itemId, false);
            return true;
        });
    }

    // ============================================================================
    // Settings: preserves the old SettingsHostActivity.start(...) public API exactly (same 3
    // overloads, same signatures) so none of its ~9 external callers need to change beyond the
    // class name they call it on.
    // ============================================================================

    public static void startSettings(Context ctx, Class<? extends Fragment> fragmentClass,
            boolean showLocalTitle, @StringRes int activityTitleRes) {
        startSettingsInternal(ctx, fragmentClass, null, showLocalTitle, activityTitleRes, null);
    }

    public static void startSettings(Context ctx, Class<? extends Fragment> fragmentClass,
            boolean showLocalTitle, CharSequence activityTitleText) {
        startSettingsInternal(ctx, fragmentClass, null, showLocalTitle, 0, activityTitleText);
    }

    public static void startSettings(Context ctx, Class<? extends Fragment> fragmentClass,
            Bundle fragmentArgs, boolean showLocalTitle, @StringRes int activityTitleRes) {
        startSettingsInternal(ctx, fragmentClass, fragmentArgs, showLocalTitle, activityTitleRes, null);
    }

    private static void startSettingsInternal(Context ctx, Class<? extends Fragment> fragmentClass,
            @Nullable Bundle fragmentArgs, boolean showLocalTitle, @StringRes int activityTitleRes,
            @Nullable CharSequence activityTitleText) {
        Integer destId = SETTINGS_DESTINATION_BY_FRAGMENT_CLASS.get(fragmentClass.getName());
        if (destId == null) {
            com.driot.bookplayer.utils.log.KanLogger.myLogEE(null, "MainActivity",
                    "startSettings: unknown settings fragment class: " + fragmentClass.getName());
            return;
        }
        Bundle args = fragmentArgs != null ? new Bundle(fragmentArgs) : new Bundle();
        args.putBoolean(EXTRA_SHOW_LOCAL_TITLE, showLocalTitle);

        Intent i = new Intent(ctx, MainActivity.class);
        i.putExtra(EXTRA_NAV_TAB_ID, R.id.nav_settings);
        i.putExtra(EXTRA_NAV_DEST_ID, destId);
        i.putExtra(EXTRA_NAV_ARGS, args);
        i.putExtra(EXTRA_NAV_DIRECT_LINK, true);
        if (activityTitleText != null) {
            i.putExtra(EXTRA_NAV_TITLE_TEXT, activityTitleText);
        } else if (activityTitleRes != 0) {
            i.putExtra(EXTRA_NAV_TITLE_RES, activityTitleRes);
        }
        ctx.startActivity(i);
    }

    // Voice search ("Hey Google, play <query> on BookPlayer") launched as a plain activity
    // intent - e.g. from the phone's Assistant when the app isn't already the active media
    // session. When a session IS already connected (typically Android Auto), the same voice
    // command instead arrives via MediaSessionCompat.Callback.onPlayFromSearch() in MediaService,
    // which shares the same matching/playback logic (StartPlayHelper.carOnPlayFromSearch()).
    private void handleMediaSearchIntentIfAny(Intent intent) {
        if (intent == null
                || !android.provider.MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH.equals(intent.getAction())) {
            return;
        }
        String query = intent.getStringExtra(android.app.SearchManager.QUERY);
        myLog("handleMediaSearchIntentIfAny: query=[" + query + "]");
        StartPlayHelper.carOnPlayFromSearch(this, query, intent.getExtras());
    }

    @Override
    protected void onResume() {
        super.onResume();
        invalidateOptionsMenu(); // refresh Admin menu item visibility after toggling admin mode on the Stats page
        handleRequestedNavFromIntent(getIntent());
        sendBroadcast(new Intent(Intents.ACTION_PING_UI));
        LocalBroadcastManager.getInstance(this).registerReceiver(inAppMsgRx,
                new IntentFilter(InAppMsgManager.ACTION_CACHE_UPDATED)); // Et tente immédiatement avec le cache courant
        InAppMsgManager.maybeShowBestMessage(this, getString(R.string.app_name));
        PlaybackViewModel playbackVm = new ViewModelProvider(this).get(PlaybackViewModel.class);
        PlaybackUiState s = playbackVm.getState().getValue();
        if (s != null && s.folderId > 0 && mainVm != null) {
            mainVm.requestScrollToTopForFolder(s.folderId);
        }
        if (Pref.getNeedsRecreate()) {
            recreate();
            Pref.setNeedsRecreate(false);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(inAppMsgRx);
    }

    @SuppressLint("RestrictedApi")
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        myLogD("onCreateOptionsMenu()");

        // The full Library menu only makes sense on the folder list itself - ZikFileFragment/
        // TtsReaderFragment/CleanMemoryFragment/NearbyShareFragment never had a menu of their own
        // when they were separate Activities (or, for the other 4 tabs, their own inline header/
        // gear icon), so keep that exact behavior now that everything's a Fragment.
        if (!isOnLibraryRoot()) {
            menu.clear();
            return true;
        }

        getMenuInflater().inflate(R.menu.action_bar, menu);

        // Conditionally show Radio/Podcast items in the overflow menu
        MenuItem radioItem = menu.findItem(R.id.menu_radio);
        MenuItem podcastItem = menu.findItem(R.id.menu_podcast);
        boolean showInMenu = !Option.getDisplayAppNavBar() && !Tonio.isPure(this);

        if (radioItem != null) radioItem.setVisible(showInMenu);
        if (podcastItem != null) podcastItem.setVisible(showInMenu);

        // Only show Admin item once admin mode has been unlocked (secret triple-tap on Stats page)
        MenuItem adminItem = menu.findItem(R.id.menu_admin);
        if (adminItem != null) adminItem.setVisible(Tonio.isAdmin());

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.nav_radio || itemId == R.id.nav_podcast) {
            selectTab(itemId, false);
        } else if (itemId == R.id.action_menu_three_dot) {
        } else if (itemId == R.id.menu_sort) {
            myLogI("--- USER clicks MENU : SORT ---");
            showSortOrderDialog();
        } else if (itemId == R.id.menu_settings) {
            myLogI("--- USER clicks MENU : SETTINGS ---");
            selectTab(R.id.nav_settings, false);
        } else if (itemId == R.id.menu_manual) {
            myLogI("--- USER clicks MENU : MANUAL ---");
            startActivity(new Intent(getApplicationContext(), HelpActivity.class));
        } else if (itemId == R.id.menu_stats) {
            myLogI("--- USER clicks MENU : STATS ---");
            startActivity(new Intent(this, StatsActivity.class));
        } else if (itemId == R.id.menu_sendmail) {
            myLogI("--- USER clicks MENU : SEND MAIL ---");
            KanMail.sendDaMail(this, "bookplayer@driot.com", "**Bookplayer**", "Dear developer...\n\n");
        } else if (itemId == R.id.menu_cleanMemory) {
            myLogI("--- USER clicks MENU : CLEAN ---");
            navigateDirectLink(R.id.nav_library, R.id.cleanMemoryFragment, null);
        } else if (itemId == R.id.menu_website) {
            myLogI("--- USER clicks MENU : WEBSITE ---");
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(Var.WEBSITE_URL));
            startActivity(browserIntent);
        } else if (itemId == R.id.menu_open) {
            myLogI("--- USER clicks MENU : OPEN ---");
            selectTab(R.id.nav_add, false);
        } else if (itemId == R.id.menu_receive_book) {
            myLogI("--- USER clicks MENU : RECEIVE BOOK ---");
            Bundle args = new Bundle();
            args.putBoolean("RECEIVE_MODE", true);
            navigateDirectLink(R.id.nav_library, R.id.nearbyShareFragment, args);
        } else if (itemId == R.id.menu_radio) {
            myLogI("--- USER clicks MENU : RADIO ---");
            selectTab(R.id.nav_radio, false);
        } else if (itemId == R.id.menu_podcast) {
            myLogI("--- USER clicks MENU : PODCAST ---");
            selectTab(R.id.nav_podcast, false);
        } else if (itemId == R.id.menu_admin) {
            myLogI("--- USER clicks MENU : ADMIN ---");
            startActivity(new Intent(this, AdminActivity.class));
        } else {
            myLogEE(null, "MainActivity.onOptionsItemSelected : unknown Item selected in Menu");
        }
        return super.onOptionsItemSelected(item);
    }

    private void handleRequestedNavFromIntent(Intent intent) {
        if (intent == null)
            return;

        int requestedNavId = intent.getIntExtra(EXTRA_REQUESTED_NAV_ID, 0);
        if (requestedNavId == 0)
            return; // nothing requested

        // Consume the extra so it won't re-trigger next time
        intent.removeExtra(EXTRA_REQUESTED_NAV_ID);

        selectTab(requestedNavId, false);
    }

    private void showSortOrderDialog() {
        String currentMode = Option.getSortMode();
        String currentDir = Option.getSortDirection();

        // Inflate custom layout
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_sort_order, null);

        MaterialButtonToggleGroup toggleGroup = dialogView.findViewById(R.id.toggle_group_sort);
        MaterialButton btnLastPlayed = dialogView.findViewById(R.id.btn_last_played);
        MaterialButton btnAlpha = dialogView.findViewById(R.id.btn_alpha);
        MaterialButton btnAdded = dialogView.findViewById(R.id.btn_added);

        int checkedId0;
        String suffix0 = "desc".equals(currentDir) ? " ▼" : " ▲";
        String suffixAlpha = "desc".equals(currentDir) ? " ▲" : " ▼";

        if ("last_played".equals(currentMode)) {
            checkedId0 = R.id.btn_last_played;
            btnLastPlayed.setText(getString(R.string.Last_played) + suffix0);
        } else if ("alpha".equals(currentMode) || "alphabetical".equals(currentMode)) {
            checkedId0 = R.id.btn_alpha;
            btnAlpha.setText(getString(R.string.Alphabetically) + suffixAlpha);
        } else { // added / last_added
            checkedId0 = R.id.btn_added;
            btnAdded.setText(getString(R.string.sort_last_added) + suffix0);
        }

        toggleGroup.check(checkedId0);

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .setNegativeButton(android.R.string.cancel, null)
                .create();

        dialog.show();

        View.OnClickListener sortClick = v -> {
            String newMode;

            if (v.getId() == R.id.btn_last_played) {
                newMode = "last_played";
            } else if (v.getId() == R.id.btn_alpha) {
                newMode = "alpha";
            } else if (v.getId() == R.id.btn_added) {
                newMode = "added";
            } else {
                return;
            }

            applySortAndClose(newMode, dialog);
        };

        btnLastPlayed.setOnClickListener(sortClick);
        btnAlpha.setOnClickListener(sortClick);
        btnAdded.setOnClickListener(sortClick);

    }

    private void applySortAndClose(String newMode, AlertDialog dialog) {
        String currentMode = Option.getSortMode();
        String currentDir = Option.getSortDirection();

        String newDir;

        if (newMode.equals(currentMode)) {
            newDir = "asc".equals(currentDir) ? "desc" : "asc";
        } else {
            newDir = "desc";
        }

        Option.setSortMode(newMode);
        Option.setSortDirection(newDir);

        if (mainVm != null) {
            mainVm.forceRefresh();
            mainVm.requestScrollToTopNow();
        }

        dialog.dismiss();

        myLogI("Sort changed → mode=" + newMode + " dir=" + newDir);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
            @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // Some fragments (e.g. PlayBehaviourSettingsFragment, GetOtherFragment) call
        // PermissionRequest.with(requireActivity()) rather than Fragment.requestPermissions(),
        // so the system callback lands here and must be forwarded manually to whichever Fragment
        // is currently displayed by the CURRENT tab's NavHostFragment.
        NavHostFragment navHostFragment = getTabHost(currentNavSectionId);
        if (navHostFragment == null) return;
        List<Fragment> children = navHostFragment.getChildFragmentManager().getFragments();
        if (!children.isEmpty()) {
            children.get(children.size() - 1).onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }
}
