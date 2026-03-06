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
import android.widget.Button;
import android.widget.LinearLayout;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.ViewModelProvider;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.driot.bookplayer.MyApp;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.R;
import com.driot.bookplayer.adapter.FoldersRVAdapter;
import com.driot.bookplayer.global.Intents;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.helpers.InsetHelper;
import com.driot.bookplayer.helpers.ShareHelper;
import com.driot.bookplayer.helpers.ViewHelper;
import com.driot.bookplayer.nav.BaseBottomNavActivity;
import com.driot.bookplayer.nav.NavHelper;
import com.driot.bookplayer.player.MediaService;
import com.driot.bookplayer.helpers.InfoHelper;
import com.driot.bookplayer.player.PlaybackUiState;
import com.driot.bookplayer.player.PlaybackViewModel;
import com.driot.bookplayer.quickshare.NearbyShareActivity;
import com.driot.bookplayer.utils.InAppMsgManager;
import com.driot.bookplayer.utils.KanMail;

import com.driot.bookplayer.utils.Tonio;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class MainActivity extends BaseBottomNavActivity {

    private RecyclerView recyclerView;
    private View emptyView;
    private FoldersRVAdapter adapter;
    private MainViewModel mainVm;

    Toolbar toolbar;
    private static final int REQUEST_CODE_OPTION = 34343;

    public static final String EXTRA_REQUESTED_NAV_ID = "EXTRA_REQUESTED_NAV_ID";

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

    private final ActivityResultLauncher<Intent> modifyFolderLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null || mainVm == null)
                    return;
                Intent data = result.getData();
                if (data.getIntExtra("deletedFolderId", -1) != -1
                        || data.getIntExtra("deleteInProgressFolderId", -1) != -1) {
                    mainVm.notifyFoldersListChanged();
                } else {
                    int folderId = data.getIntExtra(Intents.EXTRA_FOLDER_ID, -1);
                    if (folderId != -1) {
                        mainVm.notifyFolderChanged(folderId);
                    }
                }
            });

    @Override
    protected int getNavId() { return Tonio.isPure(this) ? -1 : R.id.nav_library; }

    @Override
    protected int getLayoutResId() {
        return R.layout.activity_main;
    }

    @Override
    protected boolean enableOngoingTaskOverlay() {
        return true;
    }

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



        recyclerView = findViewById(R.id.recyclerview_folders);
        emptyView = findViewById(R.id.emptyView);
        if (recyclerView != null) {
            int span = getResources().getInteger(R.integer.classic_grid_span);
            GridLayoutManager glm = new GridLayoutManager(this, span);
            recyclerView.setLayoutManager(glm);
            recyclerView.setHasFixedSize(true);
            recyclerView.addItemDecoration(
                    new ViewHelper.SpacesItemDecoration(ViewHelper.dp(this, 0)));
        }

        adapter = new FoldersRVAdapter(this);
        recyclerView.setAdapter(adapter);
        adapter.setModifyFolderLauncher(modifyFolderLauncher);

        PlaybackViewModel playbackVm = new ViewModelProvider(this).get(PlaybackViewModel.class);
        adapter.connectPlayback(this, playbackVm.getState()); // adapter observe playback (highlight)

        mainVm = new ViewModelProvider(this).get(MainViewModel.class);
        mainVm.getFolders().observe(this, folders -> {
            if (folders == null)
                return;
            boolean isEmpty = folders.isEmpty();
            emptyView.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
            recyclerView.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
            setUpWelcomeMessageView(isEmpty);
            adapter.submitList(folders);
        });

        // One-shot scroll-to-top request
        mainVm.getScrollToTopEvent().observe(this, evt -> {
            if (evt == null)
                return;
            if (evt.getContentIfNotHandled() == null)
                return;
            recyclerView.post(() -> {
                recyclerView.post(() -> {
                    recyclerView.smoothScrollToPosition(0);
                });
            });
        });
        boolean wantScroll = getIntent() != null && getIntent().getBooleanExtra("scrollToTop", false);
        if (wantScroll && mainVm != null) {
            // either emit now or the list observer will run soon; both are fine
            mainVm.requestScrollToTopNow();
        }

        // if we quit app, check option => should let music continue =>if no, kill
        // service
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                myLogI("--- USER CLICK BACK from MAIN --- (system button) -- EXIT APP --");
                if (Option.getStopAudioIfUserClosesApp()) {
                    startService(
                            new Intent(MainActivity.this, MediaService.class)
                                    .setAction(Intents.EXTRA_CMD_STOP)
                                    .putExtra(Intents.EXTRA_CALLER, "press back from MainActivity"));
                }
                finish();
            }
        });

        if (savedInstanceState == null) {
            ShareHelper.handleDeepLink(this, getIntent());
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
        ShareHelper.handleDeepLink(this, getIntent());
    }

    @Override
    protected void onResume() {
        super.onResume();
        handleRequestedNavFromIntent(getIntent());
        sendBroadcast(new Intent(Intents.ACTION_PING_UI));
        LocalBroadcastManager.getInstance(this).registerReceiver(inAppMsgRx,
                new IntentFilter(InAppMsgManager.ACTION_CACHE_UPDATED)); // Et tente immédiatement avec le cache courant
        InAppMsgManager.maybeShowBestMessage(this, getString(R.string.app_name));
        PlaybackViewModel playbackVm = new ViewModelProvider(this).get(PlaybackViewModel.class);
        PlaybackUiState s = playbackVm.getState().getValue();
        if (s != null && s.folderId > 0) {
            mainVm.requestScrollToTopForFolder(s.folderId);
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
        getMenuInflater().inflate(R.menu.action_bar, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.action_menu_three_dot) {
        } else if (itemId == R.id.menu_sort) {
            myLogI("--- USER clicks MENU : SORT ---");
            showSortOrderDialog();
        } else if (itemId == R.id.menu_settings) {
            myLogI("--- USER clicks MENU : SETTINGS ---");
            Option.getSharedPrefs(this).edit()
                    .putBoolean("ACTIVITY_OPTION_HAS_RESULT", false).apply(); // trick to reload MainActivity if color
                                                                              // changed in OptionActivity, by allowing
                                                                              // to set Result=OK only if color is
                                                                              // changed
            startActivityForResult(new Intent(this, SettingsActivity.class), REQUEST_CODE_OPTION);
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
            startActivity(new Intent(this, CleanMemoryActivity.class));
        } else if (itemId == R.id.menu_website) {
            myLogI("--- USER clicks MENU : WEBSITE ---");
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(Var.WEBSITE_URL));
            startActivity(browserIntent);
        } else if (itemId == R.id.menu_open) {
            myLogI("--- USER clicks MENU : OPEN ---");
            if (Tonio.isPure(this)) {
                startActivity(new Intent(getApplicationContext(), GetOtherActivity.class));
            } else {
                startActivity(new Intent(getApplicationContext(), GetActivity.class));
            }
        } else if (itemId == R.id.menu_receive_book) {
            myLogI("--- USER clicks MENU : RECEIVE BOOK ---");
            Intent intent = new Intent(this, NearbyShareActivity.class);
            intent.putExtra("RECEIVE_MODE", true);
            startActivity(intent);
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

        NavHelper.handleBottomNavClick(this, requestedNavId);
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
        String suffix0 = "desc".equals(currentDir) ? " \u25BC" : " \u25B2";
        String suffixAlpha = "desc".equals(currentDir) ? " \u25B2" : " \u25BC";

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

    private void setUpWelcomeMessageView(boolean isEmpty) {
        myLog("no folders, setting up welcome message");
        if (!isEmpty) {
            return;
        }
        Button btnWelcomeAddBook = emptyView.findViewById(R.id.btnWelcomeAddBook);
        btnWelcomeAddBook.setOnClickListener(v -> {
            startActivity(new Intent(getApplicationContext(), GetActivity.class));
        });

        LinearLayout ll_welcome_item_podcasts_radio = findViewById(R.id.ll_welcome_item_podcasts_radio);
        LinearLayout ll_welcome_item_browse = findViewById(R.id.ll_welcome_item_browse);
        if (Tonio.isPure(this)) {
            ll_welcome_item_podcasts_radio.setVisibility(View.GONE);
            ll_welcome_item_browse.setVisibility(View.GONE);
        } else {
            ll_welcome_item_podcasts_radio.setVisibility(View.VISIBLE);
            ll_welcome_item_browse.setVisibility(View.VISIBLE);
        }

    }
    
}
