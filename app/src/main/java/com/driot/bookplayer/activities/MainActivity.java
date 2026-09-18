package com.driot.bookplayer.activities;

import android.annotation.SuppressLint;
import android.app.Activity;
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
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.navigation.NavController;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;

import com.driot.bookplayer.MyApp;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.R;
import com.driot.bookplayer.global.Intents;
import com.driot.bookplayer.global.Pref;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.helpers.InsetHelper;
import com.driot.bookplayer.helpers.ShareHelper;
import com.driot.bookplayer.importexport.AutoBackupSnapshotManager;
import com.driot.bookplayer.nav.FullActivity;
import com.driot.bookplayer.nav.NavHelper;
import com.driot.bookplayer.player.MediaService;
import com.driot.bookplayer.helpers.InfoHelper;
import com.driot.bookplayer.player.PlaybackUiState;
import com.driot.bookplayer.player.PlaybackViewModel;
import com.driot.bookplayer.player.StartPlayHelper;
import com.driot.bookplayer.utils.InAppMsgManager;
import com.driot.bookplayer.utils.KanMail;

import com.driot.bookplayer.utils.Tonio;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import dagger.hilt.android.AndroidEntryPoint;
import javax.inject.Inject;

/**
 * The app's launcher Activity, App Links deep-link target, and voice-search entry point - all
 * untouched by this conversion (manifest entry, intent-filters, onCreate's deep-link/voice-search
 * parsing all keep working exactly as before). Internally, it now also hosts the whole Library
 * section as a Jetpack Navigation Component graph (main_library_nav_graph.xml) via its own
 * NavHostFragment, exactly like RadioHostActivity/PodcastHostActivity/SettingsHostActivity/
 * AddBookHostActivity host their sections - see [[radio_deeplink_applinks_fix]] plan. The former
 * inline folder-list implementation now lives in MainLibraryFragment (the graph's start
 * destination); ZikFileActivity/TtsReaderActivity/CleanMemoryActivity/NearbyShareActivity are now
 * ZikFileFragment/TtsReaderFragment/CleanMemoryFragment/NearbyShareFragment in the same graph.
 *
 * MainViewModel is Activity-scoped (new ViewModelProvider(this)) so the Sort menu item here and
 * MainLibraryFragment's own observers share the same instance.
 */
@AndroidEntryPoint
public class MainActivity extends FullActivity {

    private MainViewModel mainVm;

    Toolbar toolbar;
    private static final int REQUEST_CODE_OPTION = 34343;

    public static final String EXTRA_REQUESTED_NAV_ID = "EXTRA_REQUESTED_NAV_ID";
    public static final String EXTRA_NAVIGATE_TO_TTS_READER = "EXTRA_NAVIGATE_TO_TTS_READER";
    public static final String EXTRA_NAVIGATE_TO_CLEAN_MEMORY = "EXTRA_NAVIGATE_TO_CLEAN_MEMORY";
    public static final String EXTRA_NAVIGATE_TO_NEARBY_SHARE = "EXTRA_NAVIGATE_TO_NEARBY_SHARE";

    private boolean HasBeenProposedToOpenFile;
    private static boolean infoAlreadyShown = false;

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean("HasBeenProposedToOpenFile", HasBeenProposedToOpenFile);
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        HasBeenProposedToOpenFile = savedInstanceState.getBoolean("HasBeenProposedToOpenFile", false);
    }

    private final BroadcastReceiver inAppMsgRx = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            myLogD("broadcast received : inAppMsgRx");
            InAppMsgManager.maybeShowBestMessage(MainActivity.this, getString(R.string.app_name));
        }
    };

    @Override
    protected int getNavSectionId() { return Tonio.isPure(this) ? -1 : R.id.nav_library; }

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

        // toolbar
        toolbar = findViewById(R.id.toolbar);
        try {
            setSupportActionBar(toolbar); // si ca plante, check le color theme saved ???
        } catch (Exception e) {
            myLogEE(e, "Action bar - setSupportActionBar - error"); // on a Samsung S20 FE, android 13
        }
        try {
            toolbar.setLogo(R.mipmap.ic_launcher);
            toolbar.setLogo(R.mipmap.ic_launcher);
        } catch (Exception e) {
            myLogEE(e, "Action bar - setLogo - error");
        }

        mainVm = new ViewModelProvider(this).get(MainViewModel.class);

        // Registered after super.onCreate() (where BaseActivity registers its own
        // isSectionRoot()-based callback) so this one - added later, same LifecycleOwner - wins
        // deterministically on back press, same pattern as Radio/Podcast/Settings/Add Book's Host
        // Activities: pop the Library graph one level if possible, otherwise fall through to the
        // pre-existing "exit app" behavior (MainActivity has always been the true final
        // destination - there is no other section to fall back to).
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                NavController navController = getMainLibraryNavController();
                if (navController != null && navController.popBackStack()) {
                    return; // popped one level within the Library graph
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

        // Hide the bottom nav bar / mini-player while TtsReaderFragment (fullscreen reading
        // mode) is on top, matching the old standalone TtsReaderActivity's
        // displayAppNavBar()==false; restore them for every other destination.
        NavController navController = getMainLibraryNavController();
        if (navController != null) {
            navController.addOnDestinationChangedListener((controller, destination, arguments) -> {
                invalidateOptionsMenu();
                boolean isTtsReader = destination.getId() == R.id.ttsReaderFragment;
                View bottomNav = findViewById(R.id.bottomNav);
                View miniNowPlaying = findViewById(R.id.miniNowPlaying);
                if (bottomNav != null) {
                    bottomNav.setVisibility(isTtsReader || !displayAppNavBar() ? View.GONE : View.VISIBLE);
                }
                if (miniNowPlaying != null && isTtsReader) {
                    miniNowPlaying.setVisibility(View.GONE);
                }
            });
        }

        if (savedInstanceState == null) {
            ShareHelper.handleDeepLink(this, getIntent());
            handleMediaSearchIntentIfAny(getIntent());
            handleIntentNavigation(getIntent());
        }

        // InAppMsgManager.deleteInAppMsgCache(this);
        MyApp.getPeriodicTaskManager(this).start(); // safe
        InAppMsgManager.maybeShowBestMessage(this, "message");

        // NearbyShareReceiverHelper.deleteLegacyTestFolders(this);
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
        // FROM_TAB_SWITCH means NavHelper is just restoring/reordering this Activity to the
        // front for the Library tab - the Intent it replays is whatever was originally
        // pushed for that tab (e.g. a one-time deep link's Intent, verbatim), not a fresh
        // navigation event. Without this guard, re-selecting Library after a deep link
        // re-triggers that same deep link (and its side effects, e.g. restarting radio
        // playback) every time - see [[radio_deeplink_applinks_fix]].
        if (!intent.getBooleanExtra("FROM_TAB_SWITCH", false)) {
            ShareHelper.handleDeepLink(this, getIntent());
            handleIntentNavigation(intent);
        }
        handleMediaSearchIntentIfAny(intent);
    }

    /**
     * Routes an incoming Intent to the right Library sub-screen: a folder id/Parcelable navigates
     * to ZikFileFragment (replaces the old ZikFileActivity as a real back-stack entry - its
     * external callers now target MainActivity with the exact same extras), and the three
     * explicit marker extras navigate to TtsReaderFragment/CleanMemoryFragment/
     * NearbyShareFragment (replacing TtsReaderActivity/CleanMemoryActivity/NearbyShareActivity).
     * No-op (stays on whatever the graph currently shows, normally MainLibraryFragment) otherwise.
     */
    private void handleIntentNavigation(Intent intent) {
        if (intent == null) return;
        NavController navController = getMainLibraryNavController();
        if (navController == null) return;

        NavOptions singleTopOptions = new NavOptions.Builder()
                .setPopUpTo(navController.getGraph().getStartDestinationId(), false)
                .build();

        if (intent.getBooleanExtra(EXTRA_NAVIGATE_TO_TTS_READER, false)) {
            navController.navigate(R.id.ttsReaderFragment, null, singleTopOptions);
        } else if (intent.getBooleanExtra(EXTRA_NAVIGATE_TO_CLEAN_MEMORY, false)) {
            navController.navigate(R.id.cleanMemoryFragment, null, singleTopOptions);
        } else if (intent.getBooleanExtra(EXTRA_NAVIGATE_TO_NEARBY_SHARE, false)) {
            Bundle args = new Bundle();
            args.putParcelable(Intents.EXTRA_FOLDER, intent.getParcelableExtra(Intents.EXTRA_FOLDER));
            args.putBoolean("RECEIVE_MODE", intent.getBooleanExtra("RECEIVE_MODE", false));
            navController.navigate(R.id.nearbyShareFragment, args, singleTopOptions);
        } else if (intent.hasExtra(Intents.EXTRA_FOLDER_ID) || intent.hasExtra(Intents.EXTRA_FOLDER)) {
            Bundle args = new Bundle();
            args.putLong(Intents.EXTRA_FOLDER_ID, intent.getLongExtra(Intents.EXTRA_FOLDER_ID, -1));
            args.putParcelable(Intents.EXTRA_FOLDER, intent.getParcelableExtra(Intents.EXTRA_FOLDER));
            args.putBoolean(Intents.EXTRA_ACTIVATE_CHANGE_TRACK_ORDER,
                    intent.getBooleanExtra(Intents.EXTRA_ACTIVATE_CHANGE_TRACK_ORDER, false));
            navController.navigate(R.id.zikFileFragment, args, singleTopOptions);
        }
    }

    @Nullable
    private NavController getMainLibraryNavController() {
        FragmentManager fm = getSupportFragmentManager();
        NavHostFragment navHostFragment = (NavHostFragment) fm.findFragmentById(R.id.main_library_nav_host);
        return navHostFragment != null ? navHostFragment.getNavController() : null;
    }

    /** Used by MiniPlayBookFragment's close button to replicate the old
     * "getActivity() instanceof TtsReaderActivity" check without an Activity of that name. */
    public boolean isShowingTtsReaderFragment() {
        NavController navController = getMainLibraryNavController();
        return navController != null && navController.getCurrentDestination() != null
                && navController.getCurrentDestination().getId() == R.id.ttsReaderFragment;
    }

    /** Used by MiniPlayBookFragment's close button in place of the old
     * getActivity().finish() (which used to close the standalone TtsReaderActivity). */
    public void exitTtsReaderFragment() {
        NavController navController = getMainLibraryNavController();
        if (navController != null) navController.popBackStack();
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
        // when they were separate Activities, so keep that exact behavior now that they're
        // Fragments sharing this Activity's toolbar.
        NavController navController = getMainLibraryNavController();
        boolean onLibraryRoot = navController == null || navController.getCurrentDestination() == null
                || navController.getCurrentDestination().getId() == R.id.mainLibraryFragment;
        if (!onLibraryRoot) {
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

    @Inject
    NavHelper navHelper;

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.nav_radio || itemId == R.id.nav_podcast) {
            if (navHelper.handleAppNavBarClick(this, itemId)) {
                return true;
            }
        } else if (itemId == R.id.action_menu_three_dot) {
        } else if (itemId == R.id.menu_sort) {
            myLogI("--- USER clicks MENU : SORT ---");
            showSortOrderDialog();
        } else if (itemId == R.id.menu_settings) {
            myLogI("--- USER clicks MENU : SETTINGS ---");
            startActivityForResult(new Intent(this, SettingsHostActivity.class), REQUEST_CODE_OPTION);
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
            NavController navController = getMainLibraryNavController();
            if (navController != null) navController.navigate(R.id.cleanMemoryFragment);
        } else if (itemId == R.id.menu_website) {
            myLogI("--- USER clicks MENU : WEBSITE ---");
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(Var.WEBSITE_URL));
            startActivity(browserIntent);
        } else if (itemId == R.id.menu_open) {
            myLogI("--- USER clicks MENU : OPEN ---");
            startActivity(new Intent(getApplicationContext(), AddBookHostActivity.class));
        } else if (itemId == R.id.menu_receive_book) {
            myLogI("--- USER clicks MENU : RECEIVE BOOK ---");
            NavController navController = getMainLibraryNavController();
            if (navController != null) {
                Bundle args = new Bundle();
                args.putBoolean("RECEIVE_MODE", true);
                navController.navigate(R.id.nearbyShareFragment, args);
            }
        } else if (itemId == R.id.menu_radio) {
            myLogI("--- USER clicks MENU : RADIO ---");
            navHelper.handleAppNavBarClick(this, R.id.nav_radio);
        } else if (itemId == R.id.menu_podcast) {
            myLogI("--- USER clicks MENU : PODCAST ---");
            navHelper.handleAppNavBarClick(this, R.id.nav_podcast);
        } else if (itemId == R.id.menu_admin) {
            myLogI("--- USER clicks MENU : ADMIN ---");
            startActivity(new Intent(this, AdminActivity.class));
        } else {
            myLogEE(null, "MainActivity.onOptionsItemSelected : unknown Item selected in Menu");
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_OPTION) {
            myLog("coming back from OptionActivity - resultCode=[" + resultCode + "] Activity.RESULT_OK=["
                    + Activity.RESULT_OK + "]");
            if (resultCode == Activity.RESULT_OK) {
                recreate();
            }
        }
    }

    private void handleRequestedNavFromIntent(Intent intent) {
        if (intent == null)
            return;

        int requestedNavId = intent.getIntExtra(EXTRA_REQUESTED_NAV_ID, 0);
        if (requestedNavId == 0)
            return; // nothing requested

        // Consume the extra so it won't re-trigger next time
        intent.removeExtra(EXTRA_REQUESTED_NAV_ID);

        navHelper.handleAppNavBarClick(this, requestedNavId);
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

        // Set initial checked button + direction suffix (▲ ▼ = filled triangles, more
        // visible than ↑ ↓)
        // Alphabetical: reversed (▲ = Z→A, ▼ = A→Z) so it matches "list order"
        // intuition
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

}
